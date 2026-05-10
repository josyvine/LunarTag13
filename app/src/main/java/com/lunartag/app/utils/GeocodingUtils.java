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
 * UPDATED: Added Detailed Parsing to extract Landmarks and Pincodes globally.
 */
public class GeocodingUtils {

    private static final String TAG = "GeocodingUtils";

    /**
     * Data structure to hold parsed address components for the Workplace database.
     */
    public static class AddressDetails {
        public String fullAddress = "";
        public String landmark = ""; // e.g., "Near Supplyco" or "Canara Bank"
        public String city = "";
        public String state = "";
        public String pincode = "";
        public String country = "";
        public double latitude = 0.0;
        public double longitude = 0.0;
    }

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
     * NEW: Performs reverse geocoding and parses components into an AddressDetails object.
     * This supports Logic #3 and #4 by identifying the Landmark before State/Pincode.
     */
    public static AddressDetails getDetailedAddress(Context context, Location location) {
        AddressDetails details = new AddressDetails();
        if (location == null) return details;

        details.latitude = location.getLatitude();
        details.longitude = location.getLongitude();

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                
                details.fullAddress = addr.getAddressLine(0);
                details.pincode = addr.getPostalCode() != null ? addr.getPostalCode() : "";
                details.state = addr.getAdminArea() != null ? addr.getAdminArea() : "";
                details.country = addr.getCountryName() != null ? addr.getCountryName() : "";
                details.city = addr.getLocality() != null ? addr.getLocality() : "";

                // Logic #3: Landmark extraction (Everything before City/State)
                StringBuilder landmarkBuilder = new StringBuilder();
                if (addr.getFeatureName() != null) landmarkBuilder.append(addr.getFeatureName());
                if (addr.getSubLocality() != null && !addr.getSubLocality().equals(addr.getFeatureName())) {
                    if (landmarkBuilder.length() > 0) landmarkBuilder.append(", ");
                    landmarkBuilder.append(addr.getSubLocality());
                }
                details.landmark = landmarkBuilder.toString();
                
                return details;
            }
        } catch (Exception e) {
            Log.e(TAG, "Native Detailed Geocode failed, trying OSM fallback...");
        }

        // Fallback to OSM for detailed JSON parsing if Native fails
        try {
            String urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + 
                               location.getLatitude() + "&lon=" + location.getLongitude() + "&zoom=18&addressdetails=1";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "LunarTagApp/1.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                details.fullAddress = json.optString("display_name", "");
                
                JSONObject addrJson = json.optJSONObject("address");
                if (addrJson != null) {
                    details.pincode = addrJson.optString("postcode", "");
                    details.state = addrJson.optString("state", "");
                    details.country = addrJson.optString("country", "");
                    details.city = addrJson.optString("city", addrJson.optString("town", ""));
                    
                    // Logic #3: Landmark extraction for OSM (amenity, shop, or road)
                    String poi = addrJson.optString("amenity", addrJson.optString("shop", ""));
                    String road = addrJson.optString("road", "");
                    details.landmark = (poi.isEmpty()) ? road : poi + (road.isEmpty() ? "" : ", " + road);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Detailed Geocode failed: " + e.getMessage());
        }

        return details;
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