package com.lunartag.app.receivers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.lunartag.app.R;

import java.io.File;

/**
 * The "Doorbell" Receiver.
 * Handles scheduled alarm events and presents user notifications to dispatch geotagged photos.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    // Keys to retrieve data from Scheduler
    public static final String EXTRA_FILE_PATH = "com.lunartag.app.EXTRA_FILE_PATH";
    public static final String EXTRA_PHOTO_ID = "com.lunartag.app.EXTRA_PHOTO_ID";

    private static final String CHANNEL_ID = "SendServiceChannel"; 

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm Received! Waking up...");

        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        // Default to current time if ID is missing, ensuring uniqueness
        long photoId = intent.getLongExtra(EXTRA_PHOTO_ID, System.currentTimeMillis());

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file path provided in Alarm Intent.");
            return;
        }

        // 1. Validate File & Get URI (Handles both SD Card & Internal)
        Uri imageUri;
        try {
            if (filePath.startsWith("content://")) {
                // Custom Folder (SD Card / SAF)
                imageUri = Uri.parse(filePath);
            } else {
                // Internal Storage
                File file = new File(filePath);
                if (!file.exists()) {
                    Log.e(TAG, "File missing at: " + filePath);
                    return;
                }
                // Secure File Provider URI
                imageUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        file
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "URI Parse Error: " + e.getMessage());
            return;
        }

        // 2. Post high-priority notification with unique photoId so alerts don't overwrite
        showNotification(context, imageUri, (int) photoId);
    }

    /**
     * Posts the high-priority notification.
     * Uses Intent.createChooser() so the user can select their target messaging app.
     */
    private void showNotification(Context context, Uri imageUri, int notificationId) {
        createNotificationChannel(context);

        // A. The Share Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // B. The Chooser Intent
        Intent chooserIntent = Intent.createChooser(shareIntent, "Select App to Send...");

        // C. The PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId, 
                chooserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // D. The Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_camera) 
                .setContentTitle("Photo Ready to Send")
                .setContentText("Scheduled Upload #" + notificationId)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            Log.d(TAG, "Notification Posted ID: " + notificationId);
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Sends",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for scheduled photo uploads");
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}