package com.lunartag.app.ui.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.lunartag.app.R;
import com.lunartag.app.data.AppDatabase;
import com.lunartag.app.data.ManualLocationDao;
import com.lunartag.app.model.ManualLocation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A full-screen DialogFragment that allows users to manually define 
 * location metadata for watermarking, bypassing the GPS sensor.
 * UPDATED: Supports Logic #1 (Multi-Workplace Database) and Logic #3 (Auto-Detection Toggle).
 */
public class ManualLocationDialog extends DialogFragment {

    private static final String PREFS_SETTINGS = "LunarTagSettings";
    
    // Preference Keys (Used for the "Currently Active" workplace)
    public static final String KEY_LOCATION_MODE_MANUAL = "location_mode_manual";
    public static final String KEY_MANUAL_LOC_1 = "manual_location_1";
    public static final String KEY_MANUAL_LANDMARK = "manual_landmark";
    public static final String KEY_MANUAL_PINCODE = "manual_pincode";
    public static final String KEY_MANUAL_LAT = "manual_lat";
    public static final String KEY_MANUAL_LON = "manual_lon";
    public static final String KEY_MANUAL_STATE = "manual_state";
    public static final String KEY_MANUAL_COUNTRY = "manual_country";
    
    // NEW: Key for the QR Code toggle
    public static final String KEY_MANUAL_QR_ENABLED = "manual_qr_enabled";

    // NEW: Logic #3/4 Key for Auto-Detection
    public static final String KEY_AUTO_WORKPLACE_DETECTION = "auto_workplace_detection";

    private TextInputEditText editLoc1, editLandmark, editPincode, editLat, editLon, editState, editCountry;
    private SwitchMaterial switchQr; 
    private SwitchMaterial switchAutoDetect; // NEW: UI reference for Logic #3
    private SharedPreferences prefs;

    // Database Components
    private ManualLocationDao manualLocationDao;
    private ExecutorService executorService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a full-screen theme
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        
        // Initialize Database and Executor
        manualLocationDao = AppDatabase.getDatabase(requireContext()).manualLocationDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_manual_location, container, false);

        // Initialize Views
        Toolbar toolbar = view.findViewById(R.id.toolbar_manual_location);
        editLoc1 = view.findViewById(R.id.edit_manual_location_1);
        editLandmark = view.findViewById(R.id.edit_manual_landmark);
        editPincode = view.findViewById(R.id.edit_manual_pincode);
        editLat = view.findViewById(R.id.edit_manual_lat);
        editLon = view.findViewById(R.id.edit_manual_lon);
        editState = view.findViewById(R.id.edit_manual_state);
        editCountry = view.findViewById(R.id.edit_manual_country);
        
        // NEW: Initialize the QR Switch view
        switchQr = view.findViewById(R.id.switch_manual_qr);
        
        // NEW: Initialize Auto-Detect Switch (Referenced from new XML)
        switchAutoDetect = view.findViewById(R.id.switch_auto_workplace_detect);

        // Setup Toolbar
        toolbar.setNavigationOnClickListener(v -> dismiss());

        // Load Existing Values
        loadSavedData();

        // Save Button Logic
        view.findViewById(R.id.btn_save_manual_location).setOnClickListener(v -> {
            saveManualData();
        });

        return view;
    }

    private void loadSavedData() {
        editLoc1.setText(prefs.getString(KEY_MANUAL_LOC_1, ""));
        editLandmark.setText(prefs.getString(KEY_MANUAL_LANDMARK, ""));
        editPincode.setText(prefs.getString(KEY_MANUAL_PINCODE, ""));
        editLat.setText(prefs.getString(KEY_MANUAL_LAT, ""));
        editLon.setText(prefs.getString(KEY_MANUAL_LON, ""));
        editState.setText(prefs.getString(KEY_MANUAL_STATE, ""));
        editCountry.setText(prefs.getString(KEY_MANUAL_COUNTRY, ""));
        
        // NEW: Load the saved state of the QR toggle (defaults to false)
        if (switchQr != null) {
            switchQr.setChecked(prefs.getBoolean(KEY_MANUAL_QR_ENABLED, false));
        }

        // NEW: Load the saved state of Auto-Detection (Logic #3)
        if (switchAutoDetect != null) {
            switchAutoDetect.setChecked(prefs.getBoolean(KEY_AUTO_WORKPLACE_DETECTION, true));
        }
    }

    /**
     * UPDATED: Implements Logic #1. 
     * Saves the data into the Workplace Database and sets it as the Active watermark profile.
     */
    private void saveManualData() {
        String loc1 = editLoc1.getText().toString().trim();
        String landmark = editLandmark.getText().toString().trim();
        String pincode = editPincode.getText().toString().trim();
        String latStr = editLat.getText().toString().trim();
        String lonStr = editLon.getText().toString().trim();
        String state = editState.getText().toString().trim();
        String country = editCountry.getText().toString().trim();

        if (loc1.isEmpty() || pincode.isEmpty()) {
            Toast.makeText(getContext(), "Location 1 and Pincode are required", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat = 0.0;
        double lon = 0.0;
        try {
            lat = Double.parseDouble(latStr);
            lon = Double.parseDouble(lonStr);
        } catch (Exception e) {
            // Lat/Lon optional in form, defaults to 0.0
        }

        // 1. Logic #1: Save to Database (Workplace Profile)
        ManualLocation workplace = new ManualLocation();
        workplace.locationName = loc1;
        workplace.landmark = landmark;
        workplace.pincode = pincode;
        workplace.latitude = lat;
        workplace.longitude = lon;
        workplace.state = state;
        workplace.country = country;
        workplace.isActive = true;

        executorService.execute(() -> {
            // Mark others as inactive if needed, then insert
            manualLocationDao.insertLocation(workplace);
        });

        // 2. Preserve Logic: Update Active SharedPreferences for the WatermarkUtils
        prefs.edit()
                .putBoolean(KEY_LOCATION_MODE_MANUAL, true)
                .putString(KEY_MANUAL_LOC_1, loc1)
                .putString(KEY_MANUAL_LANDMARK, landmark)
                .putString(KEY_MANUAL_PINCODE, pincode)
                .putString(KEY_MANUAL_LAT, String.valueOf(lat))
                .putString(KEY_MANUAL_LON, String.valueOf(lon))
                .putString(KEY_MANUAL_STATE, state)
                .putString(KEY_MANUAL_COUNTRY, country)
                .putBoolean(KEY_MANUAL_QR_ENABLED, switchQr != null && switchQr.isChecked())
                .putBoolean(KEY_AUTO_WORKPLACE_DETECTION, switchAutoDetect != null && switchAutoDetect.isChecked())
                .apply();

        Toast.makeText(getContext(), "Workplace Saved and Activated", Toast.LENGTH_SHORT).show();
        
        // Notify CameraFragment to refresh its workplace indicator
        getParentFragmentManager().setFragmentResult("manual_loc_update", new Bundle());
        
        dismiss();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}