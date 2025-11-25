package com.lunartag.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.lunartag.app.R;

import java.io.File;

public class SendService extends Service {

    private static final String TAG = "SendService";
    private static final String CHANNEL_ID = "SendServiceChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String EXTRA_FILE_PATH = "com.lunartag.app.EXTRA_FILE_PATH";

    // Settings Prefs
    private static final String PREFS_SETTINGS = "LunarTagSettings";
    private static final String KEY_WHATSAPP_GROUP = "whatsapp_group";

    // Bridge Prefs (For Accessibility)
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_GROUP = "target_group_name";
    
    // TOKEN SYSTEM KEYS
    private static final String KEY_JOB_PENDING = "job_is_pending"; // The Permission Ticket
    private static final String KEY_FORCE_RESET = "force_reset_logic"; // The Brain Wipe

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Live Log: Service Start
        showLiveLog("Auto-Send Service Started...");

        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "File path was null.");
            showLiveLog("Error: No File Path provided.");
            stopSelf();
            return START_NOT_STICKY;
        }

        Uri imageUri = null;

        try {
            if (filePath.startsWith("content://")) {
                // It's a Custom Folder URI (SAF)
                imageUri = Uri.parse(filePath);
            } else {
                // It's a standard internal file
                File imageFile = new File(filePath);
                if (!imageFile.exists()) {
                    Log.e(TAG, "Image file missing: " + filePath);
                    showLiveLog("Error: Image File Missing on Disk.");
                    stopSelf();
                    return START_NOT_STICKY;
                }
                imageUri = FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        imageFile
                );
            }
        } catch (Exception e) {
            showLiveLog("Error parsing File URI: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        // --- STEP 1: Arm Accessibility (The Bridge) ---
        // This gives the Robot the Token and the Reset Command
        armAccessibilityService();

        // --- STEP 2: Post Notification ---
        postNotification(imageUri);

        return START_NOT_STICKY;
    }

    /**
     * Helper to show Live Logs (Toasts) on the screen from background.
     */
    private void showLiveLog(String message) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), "SendService: " + message, Toast.LENGTH_SHORT).show()
        );
    }

    private void postNotification(Uri imageUri) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.setPackage("com.whatsapp"); 
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    shareIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Photo Ready for WhatsApp")
                    .setContentText("Tap to Auto-Send to Group")
                    .setSmallIcon(R.drawable.ic_camera) 
                    .setContentIntent(pendingIntent) 
                    .setPriority(NotificationCompat.PRIORITY_HIGH) 
                    .setCategory(NotificationCompat.CATEGORY_ALARM) 
                    .setAutoCancel(true) 
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            
            // Live Log: Success
            showLiveLog("Notification Posted! Tap it to send.");
            
        } catch (Exception e) {
            showLiveLog("Error building notification: " + e.getMessage());
        }
    }

    private void armAccessibilityService() {
        SharedPreferences settings = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String groupName = settings.getString(KEY_WHATSAPP_GROUP, "");

        if (groupName != null && !groupName.isEmpty()) {
            SharedPreferences accessPrefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
            accessPrefs.edit()
                    .putString(KEY_TARGET_GROUP, groupName)
                    .putBoolean(KEY_JOB_PENDING, true)  // GIVE PERMISSION TICKET
                    .putBoolean(KEY_FORCE_RESET, true)  // COMMAND BRAIN WIPE
                    .apply();
            Log.d(TAG, "Bridge Armed for: " + groupName);
        } else {
            showLiveLog("Warning: No WhatsApp Group Name in Settings!");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Send Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Alerts for WhatsApp Auto-Send");
            serviceChannel.enableVibration(true);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}