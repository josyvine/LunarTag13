package com.lunartag.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.lunartag.app.model.ManualLocation;

import java.util.List;

/**
 * Data Access Object for the manual_locations table.
 * Supports Logic #1 (Multi-Location), Logic #3 (Auto-Switch), and Logic #4 (Auto-Create).
 * UPDATED: Refined proximity query to resolve Glitch #2 (Persistent Red Blink).
 */
@Dao
public interface ManualLocationDao {

    /**
     * Logic #1: Inserts a new workplace profile. 
     * Uses REPLACE strategy to ensure updates to existing names are handled.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertLocation(ManualLocation location);

    /**
     * Logic #1: Retrieves all saved workplaces for the selection list.
     */
    @Query("SELECT * FROM manual_locations ORDER BY createdAt DESC")
    List<ManualLocation> getAllLocations();

    /**
     * Retrieves the currently active workplace profile.
     */
    @Query("SELECT * FROM manual_locations WHERE isActive = 1 LIMIT 1")
    ManualLocation getActiveLocation();

    /**
     * Logic #3: The Smart Search query.
     * FIXED GLITCH #2: Improved delta matching and added distance ordering.
     * This searches for workplaces within a broader delta and selects the closest match.
     */
    @Query("SELECT * FROM manual_locations " +
           "WHERE (latitude BETWEEN :lat - 0.005 AND :lat + 0.005) " +
           "AND (longitude BETWEEN :lon - 0.005 AND :lon + 0.005) " +
           "ORDER BY ABS(latitude - :lat) + ABS(longitude - :lon) ASC " +
           "LIMIT 1")
    ManualLocation findClosestLocation(double lat, double lon);

    /**
     * Logic #1: Helper to clear active status before selecting a new primary workplace.
     */
    @Query("UPDATE manual_locations SET isActive = 0")
    void deactivateAll();

    /**
     * Updates an existing workplace profile.
     */
    @Update
    void updateLocation(ManualLocation location);

    /**
     * Deletes a specific workplace profile.
     */
    @Query("DELETE FROM manual_locations WHERE id = :id")
    void deleteLocationById(long id);

    /**
     * Logic #1: A transaction to safely switch the primary workplace.
     */
    @Transaction
    default void setActiveWorkplace(long id) {
        deactivateAll();
        updateActiveStatus(id, true);
    }

    @Query("UPDATE manual_locations SET isActive = :active WHERE id = :id")
    void updateActiveStatus(long id, boolean active);
}