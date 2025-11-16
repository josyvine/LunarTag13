package com.lunartag.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.lunartag.app.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The main screen of the application, which hosts the bottom navigation and various fragments.
 * It is responsible for requesting all necessary runtime permissions upon creation.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    private ActivityResultLauncher<String[]> permissionLauncher;

    // These are the permissions required for the core functionality of Lunar Tag.
    private final String[] requiredPermissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_activity_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);
        }

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                new ActivityResultCallback<Map<String, Boolean>>() {
                    @Override
                    public void onActivityResult(Map<String, Boolean> results) {
                        boolean allGranted = true;
                        for (Boolean granted : results.values()) {
                            if (!granted) {
                                allGranted = false;
                                break;
                            }
                        }

                        if (allGranted) {
                            Toast.makeText(MainActivity.this, "All permissions granted. Lunar Tag is ready.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Some permissions were denied. Core features may not work.", Toast.LENGTH_LONG).show();
                        }
                    }
                });

        checkAndRequestPermissions();
    }

    /**
     * Checks which of the required permissions are not yet granted and requests them.
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        boolean allPermissionsAlreadyGranted = true;
        for (String permission : requiredPermissions) {
            if (permission != null && ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                allPermissionsAlreadyGranted = false;
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }

        // If all permissions were already granted, we can just inform the user.
        if (allPermissionsAlreadyGranted) {
            // This is a good place for any logic that should run if permissions are already set.
        }
    }
}
