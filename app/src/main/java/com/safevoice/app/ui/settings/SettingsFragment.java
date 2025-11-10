package com.safevoice.app.ui.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.safevoice.app.R;
import com.safevoice.app.databinding.FragmentSettingsBinding;
import com.safevoice.app.firebase.FirebaseManager;

import java.io.InputStream;

/**
 * The fragment for the "Settings" screen.
 * It allows the user to manage Firebase configuration and other app preferences.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the ActivityResultLauncher for the file picker.
        // This defines what happens after the user selects a file.
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                saveFirebaseConfigFromUri(uri);
                            }
                        }
                    }
                });

        // Setup button click listeners
        binding.buttonUploadFirebaseJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        binding.buttonShowRules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle the visibility of the rules helper card.
                if (binding.cardRulesHelper.getVisibility() == View.GONE) {
                    binding.cardRulesHelper.setVisibility(View.VISIBLE);
                } else {
                    binding.cardRulesHelper.setVisibility(View.GONE);
                }
            }
        });

        binding.buttonCopyRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyRulesToClipboard();
            }
        });

        // TODO: Implement theme selection and WebRTC toggle logic using SharedPreferences.
    }

    /**
     * Creates and launches an intent to open the system file picker.
     * It filters for files that can be opened, which is suitable for selecting a JSON file.
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow any file type
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    /**
     * Takes the URI of the selected file, opens an InputStream, and passes it
     * to the FirebaseManager to be saved.
     *
     * @param uri The URI of the user-selected google-services.json file.
     */
    private void saveFirebaseConfigFromUri(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                boolean success = FirebaseManager.saveUserFirebaseConfig(requireContext(), inputStream);
                if (success) {
                    showRestartDialog();
                } else {
                    Toast.makeText(getContext(), "Failed to save Firebase configuration.", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error reading the selected file.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Copies the Firestore rule text to the user's clipboard.
     */
    private void copyRulesToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        String ruleText = getString(R.string.settings_firebase_rules_helper_text);
        ClipData clip = ClipData.newPlainText("Firestore Rule", ruleText);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Rule copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows an AlertDialog to inform the user that they must restart the app
     * for the new Firebase configuration to take effect.
     */
    private void showRestartDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Configuration Saved")
                .setMessage("Your new Firebase configuration has been saved. Please close and restart the app for the changes to take effect.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false) // User must acknowledge the dialog.
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
  }
