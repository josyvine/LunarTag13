package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * LUNARTAG ROBOT - FULL AUTOMATIC FIX
 * 
 * UPDATES:
 * 1. Verbose Logging for Notification Detection (No more silent failures).
 * 2. direct Intent firing for Notifications (More reliable than clicking banners).
 * 3. Double-Check logic for "Photo Ready".
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    // --- Configuration ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; // "semi" or "full"
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";

    // --- State Machine ---
    private static final int STATE_WAITING_FOR_NOTIFICATION = 0;
    private static final int STATE_WAITING_FOR_SHARE_SHEET = 1;
    private static final int STATE_INSIDE_WHATSAPP = 2;
    private static final int STATE_CONFIRM_SEND = 3; 

    // Default state
    private int currentState = STATE_WAITING_FOR_NOTIFICATION;
    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50; 
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        
        currentState = STATE_WAITING_FOR_NOTIFICATION;
        broadcastLog("🤖 ROBOT RESET. Mode: CHECKING...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);
        String mode = prefs.getString(KEY_AUTO_MODE, "semi");

        if (!isJobPending) {
            currentState = STATE_WAITING_FOR_NOTIFICATION;
            return;
        }

        // Safety: Get package name
        String pkgName = "unknown";
        if (event.getPackageName() != null) {
            pkgName = event.getPackageName().toString().toLowerCase();
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        // ====================================================================
        // SEMI-AUTO WAKE UP (Works well, keeping as is)
        // ====================================================================
        if (mode.equals("semi")) {
            if (pkgName.contains("whatsapp") && currentState < STATE_INSIDE_WHATSAPP) {
                broadcastLog("🤖 Semi-Auto: WhatsApp Detected. Taking over...");
                currentState = STATE_INSIDE_WHATSAPP;
            }
        }

        // ====================================================================
        // STEP 0: NOTIFICATION DETECTION (THE FIX)
        // ====================================================================
        if (mode.equals("full") && currentState == STATE_WAITING_FOR_NOTIFICATION) {

            // 1. Log ANY Notification Event so you can see it works
            if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                broadcastLog("🔔 Notification Detected from: " + pkgName);
                
                // Check the text inside the notification
                List<CharSequence> texts = event.getText();
                StringBuilder sb = new StringBuilder();
                for (CharSequence text : texts) {
                    sb.append(text).append(" ");
                }
                String notificationText = sb.toString().toLowerCase();
                broadcastLog("   📝 Content: " + notificationText);

                // 2. Check for "Photo Ready"
                if (notificationText.contains("photo ready")) {
                    broadcastLog("✅ TARGET MATCHED! Attempting to open...");
                    
                    Parcelable data = event.getParcelableData();
                    if (data instanceof Notification) {
                        Notification notification = (Notification) data;
                        try {
                            if (notification.contentIntent != null) {
                                notification.contentIntent.send();
                                broadcastLog("🚀 Fired ContentIntent. Moving to Share Sheet...");
                                currentState = STATE_WAITING_FOR_SHARE_SHEET;
                                return;
                            } else {
                                broadcastLog("❌ ERROR: Notification has no Intent to click!");
                            }
                        } catch (PendingIntent.CanceledException e) {
                            broadcastLog("❌ ERROR: Intent Canceled: " + e.getMessage());
                        }
                    } else {
                        broadcastLog("❌ ERROR: Event data is not a Notification object.");
                    }
                }
            }

            // 3. Backup: Check Banner UI on Screen (Visual Scan)
            if (root != null) {
                List<AccessibilityNodeInfo> banners = root.findAccessibilityNodeInfosByText("Photo Ready to Send");
                if (!banners.isEmpty()) {
                    AccessibilityNodeInfo banner = banners.get(0);
                    if (banner.isClickable()) {
                        broadcastLog("👁️ Visual Banner Found. Clicking...");
                        if (performClick(banner)) {
                            currentState = STATE_WAITING_FOR_SHARE_SHEET;
                            return;
                        }
                    } else {
                        // Try parent
                        AccessibilityNodeInfo parent = banner.getParent();
                        if (parent != null && parent.isClickable()) {
                            broadcastLog("👁️ Clicking Banner Parent...");
                            if (performClick(parent)) {
                                currentState = STATE_WAITING_FOR_SHARE_SHEET;
                                return;
                            }
                        }
                    }
                }
            }
            return; // Don't proceed to other steps
        }

        // ====================================================================
        // STEP 1: SHARE SHEET (Full Auto)
        // ====================================================================
        if (mode.equals("full") && currentState == STATE_WAITING_FOR_SHARE_SHEET) {
            
            if (root != null) {
                // 1. Look for CLONE first (The Popup)
                if (scanAndClick(root, "WhatsApp (Clone)")) {
                    broadcastLog("✅ CLONE SELECTED. Moving to WhatsApp...");
                    currentState = STATE_INSIDE_WHATSAPP;
                    return;
                }

                // 2. Look for Regular WhatsApp
                // We verify we are NOT inside WhatsApp yet to prevent false positives
                if (!pkgName.contains("whatsapp")) { 
                    List<AccessibilityNodeInfo> waNodes = root.findAccessibilityNodeInfosByText("WhatsApp");
                    if (!waNodes.isEmpty()) {
                        AccessibilityNodeInfo waNode = waNodes.get(0);
                        if (waNode.isVisibleToUser()) {
                            if (performClick(waNode)) {
                                broadcastLog("👆 Clicked WhatsApp Icon. Waiting for Dialog...");
                                // DO NOT CHANGE STATE. Wait for next event (Clone Dialog).
                            }
                            return;
                        }
                    }
                }
                
                // 3. Scroll Logic (Only if we haven't found anything)
                performScroll(root);
            }
            return;
        }

        // ====================================================================
        // STEP 2: INSIDE WHATSAPP (Semi & Full)
        // ====================================================================
        if (currentState == STATE_INSIDE_WHATSAPP && pkgName.contains("whatsapp")) {
            if (root == null) return;

            String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");
            
            // Log only once per batch to avoid spam
            // broadcastLog("🔍 Searching for Group: " + targetGroup);

            if (scanAndClick(root, targetGroup)) {
                broadcastLog("✅ FOUND GROUP: " + targetGroup + ". Clicking...");
                currentState = STATE_CONFIRM_SEND;
                return;
            }

            // Scroll and Retry
            performScroll(root);
        }

        // ====================================================================
        // STEP 3: FINAL SEND
        // ====================================================================
        if (currentState == STATE_CONFIRM_SEND && pkgName.contains("whatsapp")) {
            if (root == null) return;

            // Try finding send button by Description or ID
            boolean sent = false;
            if (scanAndClickContentDesc(root, "Send")) sent = true;
            if (!sent) {
                 List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
                 if (!nodes.isEmpty()) {
                     performClick(nodes.get(0));
                     sent = true;
                 }
            }

            if (sent) {
                broadcastLog("🚀 PHOTO SENT! Job Done.");
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                currentState = STATE_WAITING_FOR_NOTIFICATION;
            }
        }
    }

    // --- UTILITIES ---

    private void broadcastLog(String msg) {
        Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
        intent.putExtra("log_msg", msg);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClick(node)) return true;
            }
        }
        return false;
    }

    private boolean scanAndClickContentDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null || desc == null) return false;
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return performClick(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanAndClickContentDesc(root.getChild(i), desc)) return true;
        }
        return false;
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        while (target != null && attempts < 6) {
            if (target.isClickable()) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            target = target.getParent();
            attempts++;
        }
        return false;
    }

    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return;
        AccessibilityNodeInfo scrollable = findScrollable(root);
        if (scrollable != null) {
            isScrolling = true;
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1000);
        }
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo res = findScrollable(node.getChild(i));
            if (res != null) return res;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        broadcastLog("⚠️ Robot Interrupted");
    }
}