package com.lunartag.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.lunartag.app.data.AppDatabase;
import com.lunartag.app.data.ManualLocationDao;
import com.lunartag.app.model.ManualLocation;
import com.lunartag.app.ui.admin.ManualLocationDialog;

/**
 * Orchestrates the Smart Workplace logic.
 * Handles Logic #3 (Landmark Extraction) and Logic #4 (Auto-Creation).
 */
public class WorkplaceManager {

    private static final String TAG = "WorkplaceManager";
    private static final String PREFS_SETTINGS = "LunarTagSettings";
    
    private final Context context;
    private final ManualLocationDao manualLocationDao;
    private final SharedPreferences prefs;

    public WorkplaceManager(Context context) {
        this.context = context;
        this.manualLocationDao = AppDatabase.getDatabase(context).manualLocationDao();
        this.prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
    }

    /**
     * Logic #3: Smart Landmark Extraction.
     * Extracts descriptive landmarks (e.g., "Near Supplyco") from a global address.
     * It identifies parts of the address that are local/descriptive and not administrative.
     */
    public String extractSmartLandmark(GeocodingUtils.AddressDetails details) {
        if (details == null || details.fullAddress == null || details.fullAddress.isEmpty()) {
            return "General Area";
        }

        // The logic: Start with the landmark identified by the Geocoder (Feature Name)
        String landmark = details.landmark;

        // Enhancement: If the landmark field is empty or just a house number, 
        // we parse the full address string to find descriptive prepositions 
        // like "Near", "Opposite", "Behind", etc., which works globally.
        String fullAddr = details.fullAddress.toLowerCase();
        String[] keywords = {"near", "opposite", "opp", "behind", "beside", "inside", "at"};

        for (String key : keywords) {
            if (fullAddr.contains(key)) {
                int startIndex = fullAddr.indexOf(key);
                // Cut the string from the keyword until the next major separator (comma)
                int endIndex = fullAddr.indexOf(",", startIndex);
                if (endIndex == -1) endIndex = fullAddr.length();
                
                String descriptivePart = details.fullAddress.substring(startIndex, endIndex).trim();
                // If this descriptive part is found, it becomes our primary landmark
                if (descriptivePart.length() > 3) {
                    landmark = descriptivePart;
                    break;
                }
            }
        }

        // Fallback: If no descriptive keywords, use City + Pincode logic
        if (landmark == null || landmark.isEmpty()) {
            landmark = details.city + " (" + details.pincode + ")";
        }

        return landmark;
    }

    /**
     * Logic #4: Automated Workplace Creation.
     * Silently creates and activates a new profile if no match is found.
     */
    public void createAndActivateNewWorkplace(Location location, GeocodingUtils.AddressDetails details) {
        ManualLocation newLoc = new ManualLocation();
        
        // Use the extracted landmark and city for the profile name
        String landmark = extractSmartLandmark(details);
        newLoc.locationName = details.city.isEmpty() ? "New Workplace" : details.city;
        newLoc.landmark = landmark;
        newLoc.pincode = details.pincode;
        newLoc.state = details.state;
        newLoc.country = details.country;
        newLoc.latitude = location.getLatitude();
        newLoc.longitude = location.getLongitude();
        newLoc.isActive = true;

        // Perform Database insertion in background
        new Thread(() -> {
            manualLocationDao.deactivateAll();
            manualLocationDao.insertLocation(newLoc);
            Log.d(TAG, "Logic #4: Auto-created workplace at " + landmark);
        }).start();

        // Immediately update preferences for the watermark
        updateActivePreferences(newLoc);
    }

    /**
     * Updates the shared preferences so WatermarkUtils uses the new data immediately.
     */
    private void updateActivePreferences(ManualLocation loc) {
        prefs.edit()
                .putString(ManualLocationDialog.KEY_MANUAL_LOC_1, loc.locationName)
                .putString(ManualLocationDialog.KEY_MANUAL_LANDMARK, loc.landmark)
                .putString(ManualLocationDialog.KEY_MANUAL_PINCODE, loc.pincode)
                .putString(ManualLocationDialog.KEY_MANUAL_LAT, String.valueOf(loc.latitude))
                .putString(ManualLocationDialog.KEY_MANUAL_LON, String.valueOf(loc.longitude))
                .putString(ManualLocationDialog.KEY_MANUAL_STATE, loc.state)
                .putString(ManualLocationDialog.KEY_MANUAL_COUNTRY, loc.country)
                .apply();
    }

    /**
     * Logic #2: Threshold comparison.
     * Determines if a mismatch exists (default threshold 200m).
     */
    public boolean isMismatched(Location currentGps) {
        String savedLatStr = prefs.getString(ManualLocationDialog.KEY_MANUAL_LAT, "0.0");
        String savedLonStr = prefs.getString(ManualLocationDialog.KEY_MANUAL_LON, "0.0");
        
        double savedLat = Double.parseDouble(savedLatStr);
        double savedLon = Double.parseDouble(savedLonStr);

        float[] results = new float[1];
        Location.distanceBetween(currentGps.getLatitude(), currentGps.getLongitude(), savedLat, savedLon, results);
        
        return results[0] > 200; // Return true if mismatch > 200 meters
    }
}