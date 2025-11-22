package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * The Automation Brain.
 * UPDATED: Added LIVE LOGS (Toasts) and aggressive clicking logic for Clones.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "AccessibilityService";
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; 
    private static final String KEY_TARGET_APP_LABEL = "target_app_label"; 

    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        // Configure to listen to everything
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
        
        showDebugToast("🤖 Robot Ready. Waiting for Alarm...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        if (!isJobPending) return;

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, "");
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        // We only care about window state changes or content changes
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        CharSequence packageNameSeq = event.getPackageName();
        String packageName = (packageNameSeq != null) ? packageNameSeq.toString().toLowerCase() : "";

        // --- 1. NOTIFICATION LOGIC ---
        if (mode.equals("full") && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (packageName.contains(getPackageName())) { // Check if it's OUR app
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    showDebugToast("🤖 Found Notification. Clicking...");
                    try {
                        ((Notification) data).contentIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // --- 2. SHARE SHEET / CLONE SELECTOR LOGIC ---
        // Logic: If we are NOT in WhatsApp, we are looking for the App Icon.
        if (mode.equals("full") && !packageName.contains("whatsapp")) {
            
            // A. Try finding the Target App (e.g. "WhatsApp (Clone)")
            // We use 'contains' to be safer.
            if (clickNodeByText(rootNode, targetAppLabel)) {
                showDebugToast("🤖 Clicked App: " + targetAppLabel);
                return;
            }

            // B. Special Case for "WhatsApp" -> "WhatsApp (Clone)" hierarchy
            // If we can't find the clone, but we see the main "WhatsApp", click it to expand options
            if (targetAppLabel.toLowerCase().contains("clone") && clickNodeByText(rootNode, "WhatsApp")) {
                showDebugToast("🤖 Expanding WhatsApp Menu...");
                return;
            }

            // C. If invisible, try scrolling
            performScroll(rootNode);
        }

        // --- 3. WHATSAPP LOGIC ---
        if (packageName.contains("whatsapp")) {

            // A. Look for "Send" Button (Paper Plane)
            // This is the HIGHEST priority. If we see Send, we click it and finish.
            if (clickNodeByContentDescription(rootNode, "Send")) {
                showDebugToast("🤖 SENDING...");
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply(); // Job Done
                showDebugToast("✅ Job Complete");
                return;
            }
            
            // B. Look for Group Name
            if (!targetGroupName.isEmpty()) {
                if (clickNodeByText(rootNode, targetGroupName)) {
                    showDebugToast("🤖 Clicked Group: " + targetGroupName);
                    return;
                } else {
                    // Only scroll if we haven't found the group yet
                    performScroll(rootNode);
                }
            }
        }
    }

    /**
     * Aggressive Finder: Looks for text containing the query (Case Insensitive).
     * Tries to click Node, Parent, or Grandparent.
     */
    private boolean clickNodeByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                // Double check because findByText is fuzzy
                if (node.getText() != null && 
                    node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
                    
                    if (tryClickingHierarchy(node)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds by Content Description (for ImageButtons like Send).
     */
    private boolean clickNodeByContentDescription(AccessibilityNodeInfo root, String desc) {
        if (root == null) return false;
        
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return tryClickingHierarchy(root);
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            if (clickNodeByContentDescription(root.getChild(i), desc)) return true;
        }
        return false;
    }

    /**
     * Tries to click the node. If not clickable, tries parent. Then grandparent.
     */
    private boolean tryClickingHierarchy(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        while (target != null && attempts < 3) {
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

        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            isScrolling = true;
            showDebugToast("🤖 Scrolling List...");
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1500);
        }
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScrollableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    // --- THE LIVE LOG HELPER ---
    private void showDebugToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast t = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
            t.show();
        });
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "Robot Interrupted");
    }
}