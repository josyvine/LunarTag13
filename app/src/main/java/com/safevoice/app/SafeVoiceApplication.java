package com.safevoice.app;

import android.app.Application;

import com.safevoice.app.firebase.FirebaseManager;

/**
 * The custom Application class for Safe Voice.
 * This is the entry point of the application process.
 * Its main responsibility is to initialize components that are needed globally,
 * such as our dynamic Firebase configuration.
 */
public class SafeVoiceApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize our custom FirebaseManager.
        // This manager will attempt to load a user-provided google-services.json first,
        // and if it doesn't find one, it will fall back to the one bundled with the app.
        // This single line of code enables the dynamic Firebase backend feature.
        FirebaseManager.initialize(this);
    }
}
