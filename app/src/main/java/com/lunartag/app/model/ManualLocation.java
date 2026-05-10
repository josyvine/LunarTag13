package com.lunartag.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Data model representing a saved Manual Workplace.
 * This entity supports Logic #1 by allowing multiple locations to be stored.
 */
@Entity(tableName = "manual_locations")
public class ManualLocation {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Logic #1: Descriptive name for the workplace (e.g., "Mala Office")
    public String locationName;

    // Logic #3: Descriptive landmark (e.g., "Near Supplyco")
    public String landmark;

    public String pincode;

    // Logic #2 & #3: Geofencing coordinates for auto-detection
    public double latitude;
    public double longitude;

    public String state;
    public String country;

    // Flag to indicate if this is the currently selected workplace
    public boolean isActive;

    public long createdAt;

    public ManualLocation() {
        this.createdAt = System.currentTimeMillis();
    }

    // --- Getters and Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getLandmark() {
        return landmark;
    }

    public void setLandmark(String landmark) {
        this.landmark = landmark;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}