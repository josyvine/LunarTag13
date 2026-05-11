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
 * UPDATED: Fixed Glitch #3 (Messy Address) and Issue #2 (Brackets).
 * UPDATED: Reinforced Landmark extraction to strictly avoid Plus Codes and Unnamed segments (Glitch #2).
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
     * FIXED GLITCH #3: Improved extraction to match the quality of Automatic Mode.
     * It now avoids Plus Codes, Unnamed roads, and extracts the most specific part of the address line.
     */
    public String extractSmartLandmark(GeocodingUtils.AddressDetails details) {
        if (details == null || details.fullAddress == null || details.fullAddress.isEmpty()) {
            return "General Area";
        }

        String landmark = "";

        // 1. Try to take the specific landmark segments provided by the geocoder (Feature Name)
        if (details.landmark != null && !details.landmark.isEmpty()) {
            // Avoid using technical "Plus Codes" or generic "Unnamed Road" as landmarks
            String raw = details.landmark.toLowerCase();
            if (!raw.contains("+") && !raw.contains("unnamed")) {
                landmark = details.landmark;
            }
        }

        // 2. If landmark is still empty/invalid, parse segments of the full address
        if (landmark.isEmpty()) {
            String[] segments = details.fullAddress.split(",");
            for (String segment : segments) {
                String trimmed = segment.trim();
                String lower = trimmed.toLowerCase();
                
                // Skip pathetic segments: Plus codes, Unnamed roads, or just numeric/short strings
                if (lower.contains("+") || lower.contains("unnamed") || trimmed.length() < 3) {
                    continue;
                }
                
                landmark = trimmed;
                break; // Found the first valid human-readable segment
            }
        }

        // 3. Last Resort: Search for descriptive keywords (Near, Opp, etc.)
        if (landmark.isEmpty() || landmark.length() < 5) {
            String fullAddr = details.fullAddress.toLowerCase();
            String[] keywords = {"near", "opposite", "opp", "behind", "beside", "inside", "at"};
            for (String key : keywords) {
                if (fullAddr.contains(key)) {
                    int startIndex = fullAddr.indexOf(key);
                    int endIndex = fullAddr.indexOf(",", startIndex);
                    if (endIndex == -1) endIndex = fullAddr.length();
                    String descriptivePart = details.fullAddress.substring(startIndex, endIndex).trim();
                    if (descriptivePart.length() > 3) {
                        landmark = descriptivePart;
                        break;
                    }
                }
            }
        }

        // Fallback: City and Pincode without brackets
        if (landmark == null || landmark.isEmpty()) {
            landmark = details.city + " " + details.pincode;
        }

        // FIX ISSUE #2: Final safety check to strip any brackets from the result
        return landmark.replace("(", "").replace(")", "").trim();
    }

    /**
     * Logic #4: Automated Workplace Creation.
     * FIXED GLITCH #2: Uses descriptive landmark as location name to prevent generic duplicates.
     */
    public void createAndActivateNewWorkplace(Location location, GeocodingUtils.AddressDetails details) {
        ManualLocation newLoc = new ManualLocation();
        
        String landmark = extractSmartLandmark(details);
        
        // FIXED GLITCH #2: Set LocationName to the specific landmark (e.g. Canara Bank)
        newLoc.locationName = landmark;
        newLoc.landmark = landmark;
        newLoc.pincode = details.pincode;
        
        // Ensure administrative fields are bracket-free
        newLoc.state = details.state != null ? details.state.replace("(", "").replace(")", "") : "";
        newLoc.country = details.country != null ? details.country.replace("(", "").replace(")", "") : "";
        
        newLoc.latitude = location.getLatitude();
        newLoc.longitude = location.getLongitude();
        newLoc.isActive = true;

        // Perform Database insertion in background
        new Thread(() -> {
            manualLocationDao.deactivateAll();
            manualLocationDao.insertLocation(newLoc);
            Log.d(TAG, "Logic #4: Auto-created workplace profile: " + landmark);
        }).start();

        // Immediately update preferences for the watermark
        updateActivePreferences(newLoc);
    }

    /**
     * Updates the shared preferences so WatermarkUtils uses the new data immediately.
     */
    private void updateActivePreferences(ManualLocation loc) {
        // Ensure all strings are clean of brackets
        String cleanName = loc.locationName != null ? loc.locationName.replace("(", "").replace(")", "") : "Workplace";
        String cleanLandmark = loc.landmark != null ? loc.landmark.replace("(", "").replace(")", "") : "";

        prefs.edit()
                .putString(ManualLocationDialog.KEY_MANUAL_LOC_1, cleanName)
                .putString(ManualLocationDialog.KEY_MANUAL_LANDMARK, cleanLandmark)
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