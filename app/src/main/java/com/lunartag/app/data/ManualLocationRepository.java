package com.lunartag.app.data;

import android.content.Context;

import com.lunartag.app.model.ManualLocation;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository class to handle data operations for Manual Workplaces.
 * Supports Logic #1, #3, and #4.
 */
public class ManualLocationRepository {

    private final ManualLocationDao manualLocationDao;
    private final ExecutorService executorService;

    public ManualLocationRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.manualLocationDao = db.manualLocationDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Retrieves all saved workplace profiles from the database.
     */
    public void getAllLocations(OnLocationsLoadedListener listener) {
        executorService.execute(() -> {
            List<ManualLocation> locations = manualLocationDao.getAllLocations();
            listener.onLoaded(locations);
        });
    }

    /**
     * Logic #3: Searches for an existing workplace profile near the current GPS.
     */
    public void findClosestWorkplace(double lat, double lon, OnLocationFoundListener listener) {
        executorService.execute(() -> {
            ManualLocation match = manualLocationDao.findClosestLocation(lat, lon);
            listener.onFound(match);
        });
    }

    /**
     * Logic #1 & #4: Inserts a new workplace or updates an existing one.
     */
    public void insertOrUpdateWorkplace(ManualLocation location) {
        executorService.execute(() -> {
            manualLocationDao.insertLocation(location);
        });
    }

    /**
     * Logic #1: Sets a workplace as the active profile and deactivates others.
     */
    public void activateWorkplace(long id) {
        executorService.execute(() -> {
            manualLocationDao.setActiveWorkplace(id);
        });
    }

    /**
     * Logic #1: Removes a workplace profile from the database.
     */
    public void deleteWorkplace(long id) {
        executorService.execute(() -> {
            manualLocationDao.deleteLocationById(id);
        });
    }

    // --- Interfaces for callbacks ---

    public interface OnLocationsLoadedListener {
        void onLoaded(List<ManualLocation> locations);
    }

    public interface OnLocationFoundListener {
        void onFound(ManualLocation location);
    }
}