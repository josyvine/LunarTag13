package com.lunartag.app.utils;

import android.util.Log;

public class NativeBridge {

    private static final String TAG = "NativeBridge";
    private static boolean isLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("lunartag_native");
            isLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            isLibraryLoaded = false;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error loading native library: " + t.getMessage());
            isLibraryLoaded = false;
        }
    }

    public static String getAdMobBannerId() {
        if (isLibraryLoaded) {
            try {
                return nativeGetAdMobBannerId();
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    public static String getAdMobRewardedId() {
        if (isLibraryLoaded) {
            try {
                return nativeGetAdMobRewardedId();
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    public static String getContactEndpoint() {
        if (isLibraryLoaded) {
            try {
                return nativeGetContactEndpoint();
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    public static String getNominatimBaseUrl() {
        if (isLibraryLoaded) {
            try {
                return nativeGetNominatimBaseUrl();
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    // Native JNI method declarations mapped to native-lib.cpp
    private static native String nativeGetAdMobBannerId();
    private static native String nativeGetAdMobRewardedId();
    private static native String nativeGetContactEndpoint();
    private static native String nativeGetNominatimBaseUrl();
}