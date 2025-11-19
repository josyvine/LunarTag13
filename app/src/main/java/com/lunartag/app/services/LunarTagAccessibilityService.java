package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * The Automation Engine.
 * UPDATED: Now uses SharedPreferences "Bridge" logic to persist commands
 * even if the main app is closed or killed.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";
    private static final String WHATSAPP_PACKAGE_NAME = "com.whatsapp";

    // --- Shared Memory Constants (Must match SendService) ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. Safety Check: Only react to WhatsApp
        if (event.getPackageName() == null || !event.getPackageName().toString().equals(WHATSAPP_PACKAGE_NAME)) {
            return;
        }

        // 2. Memory Check: Do we have an order to execute?
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, null);

        if (!isJobPending || targetGroupName == null || targetGroupName.isEmpty()) {
            // No active job, just observe silently.
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        Log.d(TAG, "Processing active job for group: " + targetGroupName);

        // --- PHASE 1: Find the Target Group and Click It ---
        List<AccessibilityNodeInfo> groupNodes = rootNode.findAccessibilityNodeInfosByText(targetGroupName);
        if (groupNodes != null && !groupNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : groupNodes) {
                // WhatsApp lists are often complex views. We must find the CLICKABLE parent.
                AccessibilityNodeInfo parent = node.getParent();
                while (parent != null) {
                    if (parent.isClickable()) {
                        Log.d(TAG, "Found group '" + targetGroupName + "'. Clicking entry.");
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        // We successfully clicked the group. We stay 'Pending' because we still need to click Send.
                        // However, usually returning here allows the UI to update before we try to find the Send button.
                        rootNode.recycle();
                        return; 
                    }
                    parent = parent.getParent();
                }
            }
        }

        // --- PHASE 2: Find the "Send" Button and Click It ---
        // This button appears AFTER we click the group or if the Share Intent took us directly there.
        
        // Note: In English it is "Send". In other languages, this might fail unless we use ID lookups.
        // Since your device is English, "Send" works.
        List<AccessibilityNodeInfo> sendButtonNodes = rootNode.findAccessibilityNodeInfosByText("Send");
        
        // If text search fails, sometimes the ContentDescription (for screen readers) is "Send"
        if (sendButtonNodes == null || sendButtonNodes.isEmpty()) {
             // Attempt fallback search if standard text fails? 
             // For now, stick to standard text as it's most reliable on standard WA.
        }

        if (sendButtonNodes != null && !sendButtonNodes.isEmpty()) {
            for (AccessibilityNodeInfo node : sendButtonNodes) {
                if (node.isClickable()) {
                    Log.d(TAG, "Found 'Send' button. Clicking and Completing Job.");
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                    // --- JOB COMPLETE: Update Memory ---
                    // We mark pending as FALSE so we don't keep clicking send forever.
                    prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                    Log.d(TAG, "Job marked as Complete in Memory.");
                    
                    rootNode.recycle();
                    return;
                }
            }
        }
        
        // Clean up to prevent memory leaks
        rootNode.recycle();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "LunarTag Accessibility Service Connected & Listening.");
    }
}