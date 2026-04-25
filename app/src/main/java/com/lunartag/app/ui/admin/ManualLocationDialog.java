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

/**
 * A full-screen DialogFragment that allows users to manually define 
 * location metadata for watermarking, bypassing the GPS sensor.
 */
public class ManualLocationDialog extends DialogFragment {

    private static final String PREFS_SETTINGS = "LunarTagSettings";
    
    // Preference Keys
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

    private TextInputEditText editLoc1, editLandmark, editPincode, editLat, editLon, editState, editCountry;
    private SwitchMaterial switchQr; // NEW: View reference for the QR toggle
    private SharedPreferences prefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a full-screen theme
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
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
    }

    private void saveManualData() {
        String loc1 = editLoc1.getText().toString().trim();
        String landmark = editLandmark.getText().toString().trim();
        String pincode = editPincode.getText().toString().trim();
        String lat = editLat.getText().toString().trim();
        String lon = editLon.getText().toString().trim();
        String state = editState.getText().toString().trim();
        String country = editCountry.getText().toString().trim();

        if (loc1.isEmpty() || pincode.isEmpty()) {
            Toast.makeText(getContext(), "Location 1 and Pincode are required", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putBoolean(KEY_LOCATION_MODE_MANUAL, true)
                .putString(KEY_MANUAL_LOC_1, loc1)
                .putString(KEY_MANUAL_LANDMARK, landmark)
                .putString(KEY_MANUAL_PINCODE, pincode)
                .putString(KEY_MANUAL_LAT, lat)
                .putString(KEY_MANUAL_LON, lon)
                .putString(KEY_MANUAL_STATE, state)
                .putString(KEY_MANUAL_COUNTRY, country)
                .putBoolean(KEY_MANUAL_QR_ENABLED, switchQr.isChecked()) // NEW: Save toggle state
                .apply();

        Toast.makeText(getContext(), "Manual Location Overridden", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}