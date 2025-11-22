package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
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
 * LUNARTAG ROBOT - FINAL LOGIC EDITION
 * 
 * FEATURES:
 * 1. Live Green Log (No Toasts)
 * 2. Semi-Auto: Wakes up only inside WhatsApp to find Group + Send.
 * 3. Full-Auto: Handles Notification -> Share Sheet (Double Click for Clone) -> WhatsApp.
 * 4. Scroll Support: Scrols down to find group name.
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
    private static final int STATE_CONFIRM_SEND = 3; // New State for final button

    // Default state
    private int currentState = STATE_WAITING_FOR_NOTIFICATION;
    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // Listen to ALL events to catch Notifications, Dialogs, and Content Changes
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50; 
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        
        currentState = STATE_WAITING_FOR_NOTIFICATION;
        broadcastLog("🤖 ROBOT ONLINE. Waiting for command...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. GLOBAL CHECK: Is a job pending?
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        if (!isJobPending) {
            currentState = STATE_WAITING_FOR_NOTIFICATION;
            return;
        }

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        
        // Get current package safely
        String pkgName = "";
        if (event.getPackageName() != null) {
            pkgName = event.getPackageName().toString().toLowerCase();
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        // ====================================================================
        // LOGIC FOR SEMI-AUTOMATIC (WAKE UP TRIGGER)
        // ====================================================================
        // If mode is SEMI, we ignore steps 0 and 1. We wait until user opens WhatsApp manually.
        if (mode.equals("semi")) {
            if (pkgName.contains("whatsapp") && currentState < STATE_INSIDE_WHATSAPP) {
                broadcastLog("🤖 Semi-Auto: Detected WhatsApp. Taking control...");
                currentState = STATE_INSIDE_WHATSAPP;
            }
        }

        // ====================================================================
        // STEP 0: HUNTING FOR NOTIFICATION (Full Automatic Only)
        // ====================================================================
        if (mode.equals("full") && currentState == STATE_WAITING_FOR_NOTIFICATION) {
            
            // Method A: Banner Text Scan
            if (root != null) {
                List<AccessibilityNodeInfo> bannerNodes = root.findAccessibilityNodeInfosByText("Photo Ready to Send");
                if (!bannerNodes.isEmpty()) {
                    AccessibilityNodeInfo banner = bannerNodes.get(0);
                    if (performClick(banner)) {
                        broadcastLog("✅ SUCCESS: Clicked Notification Banner.");
                        currentState = STATE_WAITING_FOR_SHARE_SHEET; 
                        return;
                    } else {
                        broadcastLog("❌ ERROR: Found Banner text, but click failed (Not clickable).");
                    }
                }
            }

            // Method B: Notification Event (System Tray)
            if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                if (pkgName.contains(getPackageName())) { 
                    Parcelable data = event.getParcelableData();
                    if (data instanceof Notification) {
                        try {
                            broadcastLog("✅ SUCCESS: Received Notification Event. Opening...");
                            ((Notification) data).contentIntent.send();
                            currentState = STATE_WAITING_FOR_SHARE_SHEET; 
                            return;
                        } catch (Exception e) {
                            broadcastLog("❌ ERROR: Failed to open notification intent.");
                        }
                    }
                }
            }
            return; 
        }

        // ====================================================================
        // STEP 1: SHARE SHEET (Full Automatic Only)
        // ====================================================================
        // In Semi-Auto, the user does this manually.
        if (mode.equals("full") && currentState == STATE_WAITING_FOR_SHARE_SHEET) {
            
            if (root != null) {
                String targetApp = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");
                
                // CLONE LOGIC: PRIORITY CHECK
                // 1. First, look specifically for "WhatsApp (Clone)"
                // If this exists, it means the sub-menu is OPEN.
                if (scanAndClick(root, "WhatsApp (Clone)")) {
                    broadcastLog("✅ SUCCESS: Selected WhatsApp Clone.");
                    currentState = STATE_INSIDE_WHATSAPP;
                    return;
                }
                
                // 2. If we didn't find Clone, look for regular "WhatsApp"
                // Clicking this might just open the sub-menu.
                // IMPORTANT: We do NOT change state here. We wait for the screen to update.
                List<AccessibilityNodeInfo> waNodes = root.findAccessibilityNodeInfosByText("WhatsApp");
                if (!waNodes.isEmpty()) {
                    if (performClick(waNodes.get(0))) {
                        broadcastLog("🤖 Clicked 'WhatsApp'. Waiting for Clone Menu or App...");
                        // We stay in this state. Next event will show Clone button if it exists.
                    }
                    return;
                }

                // 3. Scroll if nothing found
                performScroll(root);
            }
            return;
        }

        // ====================================================================
        // STEP 2: INSIDE WHATSAPP - FIND GROUP (Semi & Full)
        // ====================================================================
        if (currentState == STATE_INSIDE_WHATSAPP && pkgName.contains("whatsapp")) {
            
            if (root == null) return;

            String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");
            if (targetGroup.isEmpty()) {
                broadcastLog("❌ ERROR: No Target Group Name saved in settings!");
                return;
            }

            // 1. Try to find the group text exactly
            if (scanAndClick(root, targetGroup)) {
                broadcastLog("✅ SUCCESS: Found Group '" + targetGroup + "'. Clicking...");
                currentState = STATE_CONFIRM_SEND; // Move to final step
                return;
            }

            // 2. If not found, we need to scroll the list
            broadcastLog("🔎 Searching for group... Scrolling down.");
            performScroll(root);
        }

        // ====================================================================
        // STEP 3: CONFIRM SEND (Semi & Full)
        // ====================================================================
        if (currentState == STATE_CONFIRM_SEND && pkgName.contains("whatsapp")) {
            
            if (root == null) return;

            // Look for the Send Button
            // Usually has contentDescription "Send"
            boolean sent = false;
            
            // Strategy A: Content Description "Send"
            if (scanAndClickContentDesc(root, "Send")) sent = true;
            
            // Strategy B: Text "Send" (Rare but possible)
            if (!sent && scanAndClick(root, "Send")) sent = true;

            // Strategy C: Specific WhatsApp Send Icon ID (Fallback)
            if (!sent) {
                 List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
                 if (!nodes.isEmpty()) {
                     performClick(nodes.get(0));
                     sent = true;
                 }
            }

            if (sent) {
                broadcastLog("🚀 MISSION COMPLETE: Photo Sent!");
                
                // Reset Logic
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                currentState = STATE_WAITING_FOR_NOTIFICATION;
                
                // Optional: Press Back or Home? User said "Robot never interrupt". 
                // So we just stop here.
            }
        }
    }

    // --------------------------------------------------------------------------
    // UTILITIES
    // --------------------------------------------------------------------------

    // REPLACEMENT FOR TOAST: Send log to Activity
    private void broadcastLog(String msg) {
        Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
        intent.putExtra("log_msg", msg);
        // Use minimal package restriction to ensure it goes to our app
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
        // Climb up parents to find a clickable node (e.g. Text might not be clickable, but Row is)
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
            // Add delay to prevent spamming scroll
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
        broadcastLog("⚠️ Robot Interrupted by System.");
    }
}