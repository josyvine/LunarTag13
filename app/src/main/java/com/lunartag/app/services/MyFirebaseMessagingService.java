package com.lunartag.app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.lunartag.app.MainActivity;
import com.lunartag.app.R;

/**
 * Service to handle incoming Firebase Cloud Messages (FCM).
 * This service ensures that notifications are displayed even when the app is in the foreground.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        // Log the message for debugging
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            
            // By default, FCM does not show notifications if the app is in the foreground.
            // We must manually trigger it here.
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            
            // Fallback for null titles
            if (title == null) {
                title = getString(R.string.app_name);
            }

            sendNotification(title, body);
        }
    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        // If you need to manage this app's subscriptions on the server side, 
        // send the Instance ID token to your app server.
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param title       FCM Message Title.
     * @param messageBody FCM Message Body.
     */
    private void sendNotification(String title, String messageBody) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // FLAG_IMMUTABLE is required for Android 12+ (API 31+)
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = "fcm_default_channel";
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.lunartag) // Using app icon
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        // PRIORITY_HIGH makes it a "Heads Up" notification on Android 7.1 and below
                        .setPriority(NotificationCompat.PRIORITY_HIGH) 
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since Android Oreo (API 26), notification channels are needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH makes it a "Heads Up" notification on Android 8.0+
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Default Channel",
                    NotificationManager.IMPORTANCE_HIGH); 
            channel.setDescription("Default Notification Channel");
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }
}