package com.lunartag.app.utils;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Utility class to handle robust geocoding with retry logic and OSM fallback.
 */
public class GeocodingUtils {

    private static final String TAG = "GeocodingUtils";

    /**
     * Attempts to get an address using Native Geocoder (with retries) 
     * and falls back to OpenStreetMap if native fails.
     */
    public static String getAddressWithFallback(Context context, Location location) {
        if (location == null) return "Location Unknown";

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        // --- ATTEMPT 1: NATIVE ANDROID GEOCODER WITH RETRY LOOP ---
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        int maxRetries = 3;

        for (int i = 1; i <= maxRetries; i++) {
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String addressLine = addresses.get(0).getAddressLine(0);
                    if (addressLine != null && !addressLine.isEmpty()) {
                        broadcastLog(context, "System: Native Geocoder Success on attempt " + i, "info");
                        return addressLine;
                    }
                }
            } catch (Exception e) {
                broadcastLog(context, "Warning: Native Geocoder Attempt " + i + " failed (" + e.getMessage() + ")", "error");
                // Brief pause before retry
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        // --- ATTEMPT 2: OPENSTREETMAP (NOMINATIM) FALLBACK ---
        broadcastLog(context, "System: Native Geocoder Exhausted. Switching to OSM Fallback...", "error");
        
        try {
            // OSM Nominatim API URL
            String urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lon + "&zoom=18&addressdetails=1";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // OSM requires a User-Agent
            conn.setRequestProperty("User-Agent", "LunarTagApp/1.0"); 
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonObject = new JSONObject(response.toString());
                if (jsonObject.has("display_name")) {
                    String osmAddress = jsonObject.getString("display_name");
                    broadcastLog(context, "System: OSM Fallback Success.", "info");
                    return osmAddress;
                }
            } else {
                broadcastLog(context, "Error: OSM API returned code " + conn.getResponseCode(), "error");
            }
        } catch (Exception e) {
            broadcastLog(context, "Error: OSM Fallback failed (" + e.getMessage() + ")", "error");
        }

        return "Address Not Found";
    }

    /**
     * Helper to broadcast messages to the MainActivity Live Log.
     */
    private static void broadcastLog(Context context, String message, String type) {
        try {
            Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
            intent.putExtra("log_msg", message);
            intent.putExtra("log_type", type);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast log: " + e.getMessage());
        }
    }
}