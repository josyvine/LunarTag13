package com.lunartag.app.utils;

import android.Manifest; 
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * A "Pro" architecture Location Provider.
 * It runs in the background, maintaining a constant "Fresh" GPS lock
 * so the Camera never has to wait.
 * UPDATED: Added GMS check with native Android LocationManager fallback for Huawei devices.
 */
public class LocationProvider {

    private static final String TAG = "LocationProvider";
    private final Context context;
    
    // GMS Client
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    
    // Native LocationManager Fallback (For Huawei / GMS-less)
    private LocationManager systemLocationManager;
    private LocationListener systemLocationListener;

    // Flag to check if GMS is available
    private boolean isGmsAvailable = false;

    // The "Hot" variable that holds the instant coordinate
    private Location currentBestLocation = null;
    
    // Interfaces for status updates (Optional, used to change GPS Icon color)
    private LocationStatusListener statusListener;

    public interface LocationStatusListener {
        void onLocationUpdated(Location location);
    }

    public void setStatusListener(LocationStatusListener listener) {
        this.statusListener = listener;
    }

    public LocationProvider(Context context) {
        this.context = context;
        this.isGmsAvailable = checkGooglePlayServices(context);

        if (isGmsAvailable) {
            this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        } else {
            Log.w(TAG, "GMS unavailable. Falling back to native LocationManager (Huawei Mode).");
            this.systemLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private boolean checkGooglePlayServices(Context context) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    /**
     * STEP 1: Start the Engine.
     * Call this in onResume(). It starts the GPS immediately.
     */
    public void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission missing. Cannot start updates.");
            return;
        }

        if (isGmsAvailable && fusedLocationClient != null) {
            startGmsLocationUpdates();
        } else {
            startNativeLocationUpdates();
        }
    }

    private void startGmsLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 1. INSTANTLY grab the last known location (Cache)
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "Last Known Location recovered (GMS): " + location.toString());
                updateBestLocation(location);
            }
        });

        // 2. Create the Request for FRESH data
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(false)
                .build();

        // 3. Define what happens when a NEW satellite signal arrives
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "Fresh GPS Signal Received (GMS): " + location.toString());
                        updateBestLocation(location);
                    }
                }
            }
        };

        // 4. Start the loop
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "GPS Engine Started (GMS Mode).");
    }

    private void startNativeLocationUpdates() {
        if (systemLocationManager == null) {
            systemLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        if (systemLocationManager == null) {
            Log.e(TAG, "System LocationManager is null. Cannot start updates.");
            return;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 1. Grab last known location from native providers
        Location gpsLast = systemLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLast = systemLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLast != null) {
            Log.d(TAG, "Last Known Location recovered (Native GPS): " + gpsLast.toString());
            updateBestLocation(gpsLast);
        } else if (networkLast != null) {
            Log.d(TAG, "Last Known Location recovered (Native Network): " + networkLast.toString());
            updateBestLocation(networkLast);
        }

        // 2. Setup native LocationListener
        systemLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d(TAG, "Fresh GPS Signal Received (Native): " + location.toString());
                updateBestLocation(location);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        // 3. Request updates from available providers
        try {
            if (systemLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                systemLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2000, 0f, systemLocationListener, Looper.getMainLooper());
            }
            if (systemLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                systemLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 3000, 0f, systemLocationListener, Looper.getMainLooper());
            }
            Log.d(TAG, "GPS Engine Started (Native Huawei Mode).");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start native location updates: " + e.getMessage());
        }
    }

    private void updateBestLocation(Location location) {
        currentBestLocation = location;
        if (statusListener != null) {
            statusListener.onLocationUpdated(location);
        }
    }

    /**
     * STEP 2: Stop the Engine.
     * Call this in onPause() to save battery.
     */
    public void stopLocationUpdates() {
        if (isGmsAvailable && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "GPS Engine Stopped (GMS).");
        }
        if (systemLocationManager != null && systemLocationListener != null) {
            systemLocationManager.removeUpdates(systemLocationListener);
            Log.d(TAG, "GPS Engine Stopped (Native).");
        }
    }

    /**
     * STEP 3: The Instant Getter.
     * Call this when "Capture" is clicked. It returns IMMEDIATELY.
     * No callbacks. No waiting.
     */
    public Location getCurrentLocationFast() {
        if (currentBestLocation != null) {
            return currentBestLocation;
        } else {
            return null;
        }
    }

    /**
     * NEW HELPER: Calculates distance in meters between two points.
     * Used for Logic #2 & #3: detecting workplace mismatch and auto-switching.
     *
     * @param startLat Current GPS Latitude
     * @param startLon Current GPS Longitude
     * @param endLat   Workplace saved Latitude
     * @param endLon   Workplace saved Longitude
     * @return Distance in meters
     */
    public float calculateDistanceInMeters(double startLat, double startLon, double endLat, double endLon) {
        float[] results = new float[1];
        Location.distanceBetween(startLat, startLon, endLat, endLon, results);
        return results[0];
    }
}