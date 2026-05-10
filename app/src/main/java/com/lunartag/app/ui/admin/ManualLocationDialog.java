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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.lunartag.app.R;
import com.lunartag.app.data.AppDatabase;
import com.lunartag.app.data.ManualLocationDao;
import com.lunartag.app.model.ManualLocation;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A full-screen DialogFragment that allows users to manage multiple workplace profiles.
 * This file integrates the Save logic and the List Selection logic (Logic #1).
 */
public class ManualLocationDialog extends DialogFragment {

    private static final String PREFS_SETTINGS = "LunarTagSettings";
    
    // Preference Keys for current active profile
    public static final String KEY_LOCATION_MODE_MANUAL = "location_mode_manual";
    public static final String KEY_MANUAL_LOC_1 = "manual_location_1";
    public static final String KEY_MANUAL_LANDMARK = "manual_landmark";
    public static final String KEY_MANUAL_PINCODE = "manual_pincode";
    public static final String KEY_MANUAL_LAT = "manual_lat";
    public static final String KEY_MANUAL_LON = "manual_lon";
    public static final String KEY_MANUAL_STATE = "manual_state";
    public static final String KEY_MANUAL_COUNTRY = "manual_country";
    public static final String KEY_MANUAL_QR_ENABLED = "manual_qr_enabled";
    public static final String KEY_AUTO_WORKPLACE_DETECTION = "auto_workplace_detection";

    private TextInputEditText editLoc1, editLandmark, editPincode, editLat, editLon, editState, editCountry;
    private SwitchMaterial switchQr, switchAutoDetect;
    private SharedPreferences prefs;

    // Workplace List Components
    private RecyclerView recyclerView;
    private ManualLocationAdapter adapter;
    private ManualLocationDao manualLocationDao;
    private ExecutorService executorService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        
        manualLocationDao = AppDatabase.getDatabase(requireContext()).manualLocationDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_manual_location, container, false);

        // 1. Initialize List Components
        recyclerView = view.findViewById(R.id.recycler_saved_workplaces);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new ManualLocationAdapter(location -> {
            // User selected an existing workplace from the list
            activateExistingWorkplace(location);
        });
        recyclerView.setAdapter(adapter);

        // 2. Initialize Form Views
        Toolbar toolbar = view.findViewById(R.id.toolbar_manual_location);
        editLoc1 = view.findViewById(R.id.edit_manual_location_1);
        editLandmark = view.findViewById(R.id.edit_manual_landmark);
        editPincode = view.findViewById(R.id.edit_manual_pincode);
        editLat = view.findViewById(R.id.edit_manual_lat);
        editLon = view.findViewById(R.id.edit_manual_lon);
        editState = view.findViewById(R.id.edit_manual_state);
        editCountry = view.findViewById(R.id.edit_manual_country);
        switchQr = view.findViewById(R.id.switch_manual_qr);
        switchAutoDetect = view.findViewById(R.id.switch_auto_workplace_detect);

        toolbar.setNavigationOnClickListener(v -> dismiss());

        loadSavedData();
        loadWorkplaceList();

        view.findViewById(R.id.btn_save_manual_location).setOnClickListener(v -> saveNewWorkplace());

        return view;
    }

    private void loadWorkplaceList() {
        executorService.execute(() -> {
            List<ManualLocation> locations = manualLocationDao.getAllLocations();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.setLocations(locations));
            }
        });
    }

    private void loadSavedData() {
        editLoc1.setText(prefs.getString(KEY_MANUAL_LOC_1, ""));
        editLandmark.setText(prefs.getString(KEY_MANUAL_LANDMARK, ""));
        editPincode.setText(prefs.getString(KEY_MANUAL_PINCODE, ""));
        editLat.setText(prefs.getString(KEY_MANUAL_LAT, ""));
        editLon.setText(prefs.getString(KEY_MANUAL_LON, ""));
        editState.setText(prefs.getString(KEY_MANUAL_STATE, ""));
        editCountry.setText(prefs.getString(KEY_MANUAL_COUNTRY, ""));
        if (switchQr != null) switchQr.setChecked(prefs.getBoolean(KEY_MANUAL_QR_ENABLED, false));
        if (switchAutoDetect != null) switchAutoDetect.setChecked(prefs.getBoolean(KEY_AUTO_WORKPLACE_DETECTION, true));
    }

    private void saveNewWorkplace() {
        String loc1 = editLoc1.getText().toString().trim();
        String pin = editPincode.getText().toString().trim();
        
        if (loc1.isEmpty() || pin.isEmpty()) {
            Toast.makeText(getContext(), "Name and Pincode required", Toast.LENGTH_SHORT).show();
            return;
        }

        ManualLocation workplace = new ManualLocation();
        workplace.locationName = loc1;
        workplace.landmark = editLandmark.getText().toString().trim();
        workplace.pincode = pin;
        workplace.state = editState.getText().toString().trim();
        workplace.country = editCountry.getText().toString().trim();
        try {
            workplace.latitude = Double.parseDouble(editLat.getText().toString());
            workplace.longitude = Double.parseDouble(editLon.getText().toString());
        } catch (Exception e) { workplace.latitude = 0.0; workplace.longitude = 0.0; }
        workplace.isActive = true;

        executorService.execute(() -> {
            manualLocationDao.deactivateAll();
            manualLocationDao.insertLocation(workplace);
            applyToPreferences(workplace);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "New Workplace Added", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        });
    }

    private void activateExistingWorkplace(ManualLocation loc) {
        executorService.execute(() -> {
            manualLocationDao.setActiveWorkplace(loc.id);
            applyToPreferences(loc);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Switched to: " + loc.locationName, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        });
    }

    private void applyToPreferences(ManualLocation loc) {
        prefs.edit()
                .putBoolean(KEY_LOCATION_MODE_MANUAL, true)
                .putString(KEY_MANUAL_LOC_1, loc.locationName)
                .putString(KEY_MANUAL_LANDMARK, loc.landmark)
                .putString(KEY_MANUAL_PINCODE, loc.pincode)
                .putString(KEY_MANUAL_LAT, String.valueOf(loc.latitude))
                .putString(KEY_MANUAL_LON, String.valueOf(loc.longitude))
                .putString(KEY_MANUAL_STATE, loc.state)
                .putString(KEY_MANUAL_COUNTRY, loc.country)
                .putBoolean(KEY_MANUAL_QR_ENABLED, switchQr.isChecked())
                .putBoolean(KEY_AUTO_WORKPLACE_DETECTION, switchAutoDetect.isChecked())
                .apply();
        
        getParentFragmentManager().setFragmentResult("manual_loc_update", new Bundle());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}