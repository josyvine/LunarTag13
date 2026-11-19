package com.lunartag.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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

import com.lunartag.app.MainActivity;
import com.lunartag.app.R;

import java.io.File;

public class SendService extends Service {

    private static final String TAG = "SendService";
    private static final String CHANNEL_ID = "SendServiceChannel";
    private static final int NOTIFICATION_ID = 101;

    public static final String EXTRA_FILE_PATH = "com.lunartag.app.EXTRA_FILE_PATH";
    public static final String ACTION_SHARE_IMAGE = "com.lunartag.app.ACTION_SHARE_IMAGE";
    public static final String EXTRA_SHARE_IMAGE_URI = "share_image_uri";

    private final Handler safetyHandler = new Handler(Looper.getMainLooper());
    private Runnable safetyTimeoutRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showLiveLog("Photo Send Service Started...");

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
                // Custom Folder URI (SAF)
                imageUri = Uri.parse(filePath);
            } else {
                // Standard internal file
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

        // Post Foreground Notification for user to tap and send
        postNotification(imageUri);

        // Safety Timeout: Self-terminate after 60 seconds if unattended
        setupSafetyTimeout();

        return START_NOT_STICKY;
    }

    private void showLiveLog(String message) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), "SendService: " + message, Toast.LENGTH_SHORT).show()
        );
    }

    private void postNotification(Uri imageUri) {
        try {
            Intent clickIntent = new Intent(this, MainActivity.class);
            clickIntent.setAction(ACTION_SHARE_IMAGE);
            clickIntent.putExtra(EXTRA_SHARE_IMAGE_URI, imageUri.toString());
            clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Photo Ready to Send")
                    .setContentText("Tap to Send to Target")
                    .setSmallIcon(R.drawable.ic_camera) 
                    .setContentIntent(pendingIntent) 
                    .setPriority(NotificationCompat.PRIORITY_HIGH) 
                    .setCategory(NotificationCompat.CATEGORY_ALARM) 
                    .setAutoCancel(true) 
                    .build();

            // Android 14+ (API 34+) & Android 16 targetSdk compliance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            
            showLiveLog("Notification Posted! Tap it to send.");
            
        } catch (Exception e) {
            showLiveLog("Error building notification: " + e.getMessage());
        }
    }

    private void setupSafetyTimeout() {
        if (safetyTimeoutRunnable != null) {
            safetyHandler.removeCallbacks(safetyTimeoutRunnable);
        }
        safetyTimeoutRunnable = () -> {
            try {
                Log.d(TAG, "Unattended send timeout. Stopping foreground service.");
                stopForeground(true);
                stopSelf();
            } catch (Exception e) {
                // Already terminated
            }
        };
        safetyHandler.postDelayed(safetyTimeoutRunnable, 60000);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Send Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Alerts for Photo Auto-Send");
            serviceChannel.enableVibration(true);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (safetyTimeoutRunnable != null) {
            safetyHandler.removeCallbacks(safetyTimeoutRunnable);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}