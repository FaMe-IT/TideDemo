/*
   Copyright © 2014-2015 FaMe IT, The Netherlands

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.fameit.tide.demo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.EnumMap;

import nl.fameit.tide.ComplexFloat;
import nl.fameit.tide.LatLon;
import nl.fameit.tide.TidalConstant;
import nl.fameit.tide.TideCalculator;

/**
 * This demo retrieves tidal constituents from www.worldtides.info.
 * It uses these constituents to calculate High and Low tides and water hieghts for the next 24 hours.
 * <p>
 * Syntax: TideDemo.jar <APIKEY> <Latitude> <Longitude>
 * The APIKEY can be obtained from www.worldtides.info.
 * Latitude and Longitude are coordinates anywhere in the world in decimal notation.
 * The demo will retrieve the tidal constituents for the closest sea location
 */

public class TideDemo {

    public static void main(String[] args) {
        System.out.println("World Tides (www.wordtides.info) Demo Application.\nCopyright © 2014-2015 FaMe IT, The Netherlands");

        if (args.length < 3) {
            System.out.flush();
            System.err.println("\n*** Too few command line arguments");
            System.err.println("\nTideDemo.jar <APIKEY> <Latitude> <Longitude>");
            System.exit(1);
        }

        // retrieve command line arguments
        String APIKEY = args[0];
        LatLon latlon = new LatLon(Float.parseFloat(args[1]), Float.parseFloat(args[2]));

        // default start time is now and length is 24 hours
        long start = new Date().getTime();
        long length = 3600L * 24L * 1000L;

        try {

            // create the URL from fixed components, latitude, longitude and the APIKEY
            URL website = new URL("https://www.worldtides.info/api?constituents" +
                    "&lat=" + latlon.lat +
                    "&lon=" + latlon.lon +
                    "&key=" + APIKEY
            );

            // do the remote API call and check for errors
            HttpURLConnection connection = (HttpURLConnection) website.openConnection();
            if (connection.getResponseCode() != 200) {
                System.out.flush();
                System.err.printf("\nServer returned HTTP response code %d (%s)\n", connection.getResponseCode(), connection.getResponseMessage());

                BufferedReader streamReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));

                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    System.err.println(inputStr);
                System.exit(2);
            }

            // read the response and store it as a JSONObject
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder responseStrBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                responseStrBuilder.append(inputStr);

            JSONObject json = new JSONObject(responseStrBuilder.toString());

            // get and print general response properties
            System.out.println(json.getString("copyright"));
            System.out.printf("\nFor location: %f %f\n", json.getDouble("responseLat"), json.getDouble("responseLon"));

            // get the constituents and put them in a map
            JSONArray jsonConstituents = json.getJSONArray("constituents");

            EnumMap<TidalConstant, ComplexFloat> tidalConstituents = new EnumMap<>(TidalConstant.class);
            for (int i = 0; i < jsonConstituents.length(); i++) {
                JSONObject jsonConstituent = jsonConstituents.getJSONObject(i);
                TidalConstant tidalConstant = TidalConstant.valueFromName(jsonConstituent.getString("name"));
                ComplexFloat tidalConstituent = new ComplexFloat((float) jsonConstituent.getDouble("real"), (float) jsonConstituent.getDouble("imaginary"));
                tidalConstituents.put(tidalConstant, tidalConstituent);
            }

            // initialize the TideCalculator with the constituents
            TideCalculator tideCalculator = new TideCalculator(tidalConstituents);

            // calculate extremes and print them
            System.out.println("\nHigh and Low Tides:");
            final TideCalculator.Extreme[] extremes = tideCalculator.calculateExtremes(new Date().getTime(), length);
            for (final TideCalculator.Extreme extreme : extremes) {
                System.out.println(extreme);
            }

            // calculate water heights and print them
            System.out.println("\nWater Heights:");
            for (long time = start; time < start + length; time += 1800L * 1000L) {
                final double height = tideCalculator.calculateTide(time, true);
                System.out.printf("%tc %+.2f\n", time, height);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
