/*
   Copyright © 2016 FaMe IT, The Netherlands

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
import org.json.JSONTokener;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import nl.fameit.tide.lib.Coordinate;
import nl.fameit.tide.lib.Extreme;
import nl.fameit.tide.lib.LocationData;

/**
 * This demo retrieves corrected tidal constituents from www.worldtides.info.
 * It uses these constituents to calculate high and low tides and water heights for the next 24 hours.
 * <p/>
 * Syntax: TideDemo.jar <APIKEY> <Latitude> <Longitude>
 * The APIKEY can be obtained from www.worldtides.info.
 * Latitude and Longitude are coordinates anywhere in the world in decimal notation.
 * The demo will retrieve the tidal constituents for the closest sea location
 */

class TideDemo {

    // datum to use: LAT=Lowest Astronomical Tide
    private static final String DATUM = "LAT";

    /**
     * main function which is called when run from teh command line
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        System.out.println("World Tides (www.worldtides.info) Demo Application.\nCopyright (c) 2016 FaMe IT, The Netherlands\n");

        if (args.length < 3) {
            System.out.flush();
            System.err.println("\n*** Too few command line arguments");
            System.err.println("\nTideDemo.jar <APIKEY> <Latitude> <Longitude>");
            System.exit(1);
        }

        // retrieve command line arguments
        final String APIKEY = args[0];
        final Coordinate coordinate = new Coordinate(Float.parseFloat(args[1]), Float.parseFloat(args[2]));

        try {

            // create the URL for the worldtides API:
            // get heights, extremes, datums and corrected constituents
            // use LAT (=Lowest Astronomical Tide) as datum
            final URL website = new URL(
                    "https://www.worldtides.info/api?" +
                            "heights" +
                            "&extremes" +
                            "&datums" +
                            "&correctedconstituents" +
                            "&datum=" + DATUM +
                            "&lat=" + coordinate.latitude +
                            "&lon=" + coordinate.longitude +
                            "&key=" + APIKEY
            );

            // do the remote API call
            final HttpURLConnection connection = (HttpURLConnection) website.openConnection();

            // check for errors
            if (connection.getResponseCode() != 200) {
                System.out.flush();

                // try to get detailed error message
                try {
                    JSONTokener jsonTokener = new JSONTokener(connection.getErrorStream());
                    final JSONObject json = new JSONObject(jsonTokener);
                    System.err.printf("\nServer returned status code %d (%s)\n", json.getInt("status"), json.getString("error"));
                } catch (Exception ignore) {
                    System.err.printf("\nServer returned HTTP response code %d (%s)\n", connection.getResponseCode(), connection.getResponseMessage());
                }
                System.exit(2);
            }

            // parse response as json
            JSONTokener jsonTokener = new JSONTokener(connection.getInputStream());
            final JSONObject json = new JSONObject(jsonTokener);

            // create LocationData from json data
            LocationData locationData = new LocationData(json);
            System.out.printf("Tidal data for location: %s\n%s\n", locationData.toString(), locationData.copyright);

            // Setup date format for display
            SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Z"));

            // Print high/low tides from server response
            System.out.println("\nHigh and low tides (for 48 hour period, referenced to " + DATUM + "); retrieved from server:");
            JSONArray jsonExtremes = json.getJSONArray("extremes");
            for (int i = 0; i < jsonExtremes.length(); i++) {
                JSONObject extreme = jsonExtremes.getJSONObject(i);
                System.out.printf("%16s: %10s %5s\n", DATE_FORMAT.format(extreme.getLong("dt") * 1000), round(extreme.getDouble("height"), 3), extreme.getString("type"));
            }

            // calculate high/low tides from correctedConstituents locally
            List<Extreme> extremes = locationData.calculateExtremes(locationData.epochStart, locationData.epochStart + 48 * 3600, DATUM);

            // Print high/low tides which have been locally calculated
            System.out.println("\nHigh and low tides (for 48 hour period, referenced to " + DATUM + "); locally calculated:");
            for (Extreme extreme : extremes) {
                System.out.printf("%16s: %10s %5s\n", DATE_FORMAT.format(extreme.time * 1000), round(extreme.height, 3), extreme.maximum ? "High" : "Low");
            }

            // Print water heights from server response
            System.out.println("\nWater heights (for 12 hour period, referenced to " + DATUM + "); retrieved from server:");
            JSONArray jsonHeights = json.getJSONArray("heights");
            for (int i = 0; i < jsonHeights.length(); i++) {
                JSONObject height = jsonHeights.getJSONObject(i);
                // only show first 6 hours to keep output compact
                if (height.getLong("dt") > locationData.epochStart + 6 * 3600)
                    break;
                System.out.printf("%16s: %10s\n", DATE_FORMAT.format(height.getLong("dt") * 1000), round(height.getDouble("height"), 3));
            }

            // calculate water heights from correctedConstituents locally
            LinkedHashMap<Long, Double> heights = locationData.calculate(locationData.epochStart, locationData.epochStart + 6 * 3600, 1800, DATUM);

            // Print water heights which have been locally calculated
            System.out.println("\nWater heights (for 12 hour period, referenced to " + DATUM + "); locally calculated:");
            for (Map.Entry<Long, Double> entry : heights.entrySet()) {
                System.out.printf("%16s: %10s\n", DATE_FORMAT.format(entry.getKey() * 1000), round(entry.getValue(), 3));
            }

        } catch (final IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Formats double as number with specified amount of digits
     *
     * @param value  the value which will be rounded
     * @param digits the number of digits to round to
     * @return string formatted with digits after the decimal point
     */
    static String round(double value, int digits) {
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.CEILING).toString();
    }


}
