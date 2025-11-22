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
 * UPDATED: Manual List Scanning and Deep Hierarchy Clicking for WhatsApp.
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
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        showDebugToast("🤖 Robot Ready & Waiting...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        if (!isJobPending) return;

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, "").trim(); // Trim spaces!
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        CharSequence packageNameSeq = event.getPackageName();
        String packageName = (packageNameSeq != null) ? packageNameSeq.toString().toLowerCase() : "";

        // --- 1. NOTIFICATION LOGIC ---
        if (mode.equals("full") && eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (packageName.contains(getPackageName())) { 
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    showDebugToast("🤖 Notification Detected. Opening...");
                    try {
                        ((Notification) data).contentIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // --- 2. SHARE SHEET / CLONE SELECTOR ---
        if (mode.equals("full") && !packageName.contains("whatsapp")) {
            // Try to find the app
            if (scanAndClick(rootNode, targetAppLabel)) {
                showDebugToast("🤖 Clicked App: " + targetAppLabel);
                return;
            }
            // Special Case for Clone Parent
            if (targetAppLabel.toLowerCase().contains("clone") && scanAndClick(rootNode, "WhatsApp")) {
                showDebugToast("🤖 Expanding WhatsApp Menu...");
                return;
            }
            performScroll(rootNode);
        }

        // --- 3. WHATSAPP LOGIC ---
        if (packageName.contains("whatsapp")) {
            // Debug Log to prove we are scanning
            // showDebugToast("🤖 Scanning WhatsApp..."); 

            // A. PRIORITY: SEND BUTTON
            if (scanAndClickContentDesc(rootNode, "Send")) {
                showDebugToast("🤖 SENT! Job Complete.");
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                return;
            }
            
            // B. TARGET GROUP
            if (!targetGroupName.isEmpty()) {
                
                // Strategy 1: Standard Search
                if (scanAndClick(rootNode, targetGroupName)) {
                    showDebugToast("🤖 Found Group (Standard): " + targetGroupName);
                    return;
                }
                
                // Strategy 2: Manual List Scan (Fix for "Blind Robot")
                // We find the list, look at children manually.
                if (scanListItemsManually(rootNode, targetGroupName)) {
                     showDebugToast("🤖 Found Group (Manual Scan): " + targetGroupName);
                     return;
                }

                // Strategy 3: Scroll only if not found
                performScroll(rootNode);
            }
        }
    }

    /**
     * Manual Scanner: Iterates visible list items to find text that Android's search missed.
     */
    private boolean scanListItemsManually(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return false;
        
        // 1. Is this a list? (RecyclerView or ListView)
        if (root.getClassName() != null && 
           (root.getClassName().toString().contains("RecyclerView") || 
            root.getClassName().toString().contains("ListView"))) {
            
            // Iterate children
            for (int i = 0; i < root.getChildCount(); i++) {
                AccessibilityNodeInfo child = root.getChild(i);
                if (recursiveTextCheck(child, targetText)) {
                    return true; // Clicked inside recursive function
                }
            }
        }

        // Recursive search for the List itself
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanListItemsManually(root.getChild(i), targetText)) return true;
        }
        return false;
    }

    /**
     * Checks a node and its children for the text, then clicks the high-level parent.
     */
    private boolean recursiveTextCheck(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;

        // Check Text
        if (node.getText() != null && 
            node.getText().toString().toLowerCase().contains(target.toLowerCase())) {
            // Found it! Now climb up to find the clickable container
            return tryClickingHierarchy(node);
        }
        
        // Check Content Description
        if (node.getContentDescription() != null && 
            node.getContentDescription().toString().toLowerCase().contains(target.toLowerCase())) {
            return tryClickingHierarchy(node);
        }

        // Recurse
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveTextCheck(node.getChild(i), target)) return true;
        }
        return false;
    }

    /**
     * Wrapper for standard text search
     */
    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (tryClickingHierarchy(node)) return true;
            }
        }
        return false;
    }

    /**
     * Wrapper for Content Description search
     */
    private boolean scanAndClickContentDesc(AccessibilityNodeInfo root, String desc) {
        if (root == null) return false;
        if (root.getContentDescription() != null && 
            root.getContentDescription().toString().equalsIgnoreCase(desc)) {
            return tryClickingHierarchy(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanAndClickContentDesc(root.getChild(i), desc)) return true;
        }
        return false;
    }

    /**
     * Tries to click the node. If not clickable, climbs up parents (up to 6 levels).
     */
    private boolean tryClickingHierarchy(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        int attempts = 0;
        // Increased depth to 6 for WhatsApp's complex layouts
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
        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            isScrolling = true;
            showDebugToast("🤖 Scrolling...");
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1500);
        }
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScrollableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    private void showDebugToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onInterrupt() { }
}