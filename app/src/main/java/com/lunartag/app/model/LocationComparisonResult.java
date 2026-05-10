package com.lunartag.app.model;

/**
 * A wrapper class that holds the result of the location comparison logic.
 * Used by CameraFragment to update the UI (Red Blink vs. Green) and 
 * trigger auto-switching or auto-creation.
 */
public class LocationComparisonResult {

    /**
     * Enum representing the possible states of the workplace sync.
     */
    public enum ComparisonStatus {
        MATCH,      // GPS matches the currently active workplace (Within 200m)
        MISMATCH,   // GPS does not match the active workplace (Trigger Red Warning)
        AUTO_SWITCH, // Found a different saved workplace nearby (Perform Logic #3)
        NEW_AREA     // No match found in database (Perform Logic #4)
    }

    private ComparisonStatus status;
    private float distance;
    private ManualLocation matchedWorkplace;

    public LocationComparisonResult(ComparisonStatus status, float distance) {
        this.status = status;
        this.distance = distance;
    }

    public LocationComparisonResult(ComparisonStatus status, float distance, ManualLocation workplace) {
        this.status = status;
        this.distance = distance;
        this.matchedWorkplace = workplace;
    }

    // --- Getters and Setters ---

    public ComparisonStatus getStatus() {
        return status;
    }

    public void setStatus(ComparisonStatus status) {
        this.status = status;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    /**
     * Gets the workplace identified during the DB search.
     * Only populated if status is AUTO_SWITCH.
     */
    public ManualLocation getMatchedWorkplace() {
        return matchedWorkplace;
    }

    public void setMatchedWorkplace(ManualLocation matchedWorkplace) {
        this.matchedWorkplace = matchedWorkplace;
    }

    /**
     * Helper to check if the UI should trigger the Red Warning Blink.
     */
    public boolean shouldTriggerWarning() {
        return status == ComparisonStatus.MISMATCH || 
               status == ComparisonStatus.AUTO_SWITCH || 
               status == ComparisonStatus.NEW_AREA;
    }

    @Override
    public String toString() {
        return "LocationComparisonResult{" +
                "status=" + status +
                ", distance=" + distance + "m" +
                ", workplace=" + (matchedWorkplace != null ? matchedWorkplace.locationName : "null") +
                '}';
    }
}