package com.lunartag.app.utils;

import android.location.Location;
import com.lunartag.app.model.ManualLocation;

/**
 * Specialized utility for comparing GPS coordinates against stored workplace profiles.
 * Supports Logic #2 (Mismatch detection) and Logic #3 (Proximity switching).
 */
public class LocationComparator {

    // Threshold in meters. If the user is further than this from the saved
    // workplace, the "Red Warning" logic is triggered.
    private static final float MISMATCH_THRESHOLD_METERS = 200.0f;

    // Threshold for a "Smart Match". If the user is within this distance of 
    // any saved workplace, the app considers it a match for auto-switching.
    private static final float SMART_MATCH_THRESHOLD_METERS = 300.0f;

    /**
     * Logic #2: Checks if the current GPS location is significantly different
     * from the coordinates of the provided ManualLocation profile.
     *
     * @param currentLat Current GPS Latitude
     * @param currentLon Current GPS Longitude
     * @param workplace The currently active ManualLocation profile from the DB
     * @return true if a mismatch is detected (outside 200m)
     */
    public static boolean isWorkplaceMismatched(double currentLat, double currentLon, ManualLocation workplace) {
        if (workplace == null) {
            return false;
        }

        float distance = calculateDistance(currentLat, currentLon, workplace.latitude, workplace.longitude);
        return distance > MISMATCH_THRESHOLD_METERS;
    }

    /**
     * Logic #3: Determines if a workplace from the database is close enough
     * to the user's current position to be considered a "Match" for auto-switching.
     */
    public static boolean isNearbyMatch(double lat1, double lon1, double lat2, double lon2) {
        float distance = calculateDistance(lat1, lon1, lat2, lon2);
        return distance <= SMART_MATCH_THRESHOLD_METERS;
    }

    /**
     * Helper method to calculate the distance between two points using the 
     * Android framework's accurate distanceBetween implementation.
     */
    public static float calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    /**
     * Helper to get a human-readable distance string for logging or UI feedback.
     */
    public static String getDistanceLabel(double lat1, double lon1, double lat2, double lon2) {
        float distance = calculateDistance(lat1, lon1, lat2, lon2);
        if (distance < 1000) {
            return (int) distance + "m";
        } else {
            return String.format("%.1fkm", distance / 1000.0);
        }
    }
}