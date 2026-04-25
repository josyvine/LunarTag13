package com.lunartag.app.ui.robot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.lunartag.app.R;
import com.lunartag.app.ui.admin.ManualLocationDialog;

/**
 * The Robot Fragment.
 * Allows the user to select between "Semi-Automatic" and "Full-Automatic" modes.
 * UPDATED: Includes Location Mode (Auto/Manual) moved from Schedule Editor.
 */
public class RobotFragment extends Fragment {

    // Must match LunarTagAccessibilityService constants
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_AUTO_MODE = "automation_mode"; // Values: "semi" or "full"

    // Settings for Location Mode
    private static final String PREFS_SETTINGS = "LunarTagSettings";

    private RadioButton radioSemi;
    private RadioButton radioFull;

    // Location Mode Buttons
    private MaterialButton buttonAutoLocation;
    private MaterialButton buttonManualLocation;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_robot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- 1. ROBOT MODE INITIALIZATION ---
        radioSemi = view.findViewById(R.id.radio_semi);
        radioFull = view.findViewById(R.id.radio_full);

        SharedPreferences robotPrefs = requireContext().getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String currentMode = robotPrefs.getString(KEY_AUTO_MODE, "semi");

        if (currentMode.equals("full")) {
            radioFull.setChecked(true);
            radioSemi.setChecked(false);
        } else {
            radioSemi.setChecked(true);
            radioFull.setChecked(false);
        }

        // --- 2. LOCATION MODE INITIALIZATION ---
        buttonAutoLocation = view.findViewById(R.id.button_auto_location);
        buttonManualLocation = view.findViewById(R.id.button_manual_location);

        // Update visual colors on load
        updateLocationUiState();

        // --- 3. ROBOT LISTENERS (Original Logic) ---

        radioSemi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioFull.setChecked(false);
                robotPrefs.edit().putString(KEY_AUTO_MODE, "semi").apply();
                Toast.makeText(getContext(), "Mode: Semi-Automatic (Human Verified)", Toast.LENGTH_SHORT).show();
            }
        });

        radioFull.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioSemi.setChecked(false);
                robotPrefs.edit().putString(KEY_AUTO_MODE, "full").apply();
                Toast.makeText(getContext(), "Mode: Full-Automatic (Zero Click)", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 4. LOCATION LISTENERS (Moved Logic) ---

        buttonAutoLocation.setOnClickListener(v -> {
            SharedPreferences settingsPrefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
            settingsPrefs.edit().putBoolean(ManualLocationDialog.KEY_LOCATION_MODE_MANUAL, false).apply();
            Toast.makeText(getContext(), "Switched to Automatic Geocoding", Toast.LENGTH_SHORT).show();
            updateLocationUiState();
        });

        buttonManualLocation.setOnClickListener(v -> {
            ManualLocationDialog dialog = new ManualLocationDialog();
            dialog.show(getParentFragmentManager(), "ManualLocationDialog");
            
            // Listener to update UI colors when dialog returns
            getParentFragmentManager().setFragmentResultListener("manual_loc_update", getViewLifecycleOwner(), (requestKey, result) -> {
                updateLocationUiState();
            });
        });
    }

    /**
     * Updates the Location Button Visuals (Colors and Icons)
     * based on the current system setting.
     */
    private void updateLocationUiState() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean isManual = prefs.getBoolean(ManualLocationDialog.KEY_LOCATION_MODE_MANUAL, false);
        
        int activeColor = requireContext().getColor(com.google.android.material.R.color.design_default_color_primary);
        int inactiveColor = android.graphics.Color.GRAY;

        if (isManual) {
            buttonManualLocation.setTextColor(activeColor);
            buttonManualLocation.setIconTintResource(com.google.android.material.R.color.design_default_color_primary);
            
            buttonAutoLocation.setTextColor(inactiveColor);
            buttonAutoLocation.setIconTintResource(android.R.color.darker_gray);
        } else {
            buttonAutoLocation.setTextColor(activeColor);
            buttonAutoLocation.setIconTintResource(com.google.android.material.R.color.design_default_color_primary);
            
            buttonManualLocation.setTextColor(inactiveColor);
            buttonManualLocation.setIconTintResource(android.R.color.darker_gray);
        }
    }
}