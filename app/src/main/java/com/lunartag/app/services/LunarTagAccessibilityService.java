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
 * LunarTag Intelligent Automation Service
 * Supports: Semi-Automatic and Full-Automatic modes.
 * Features: Launcher Protection, Smart Scrolling, Manual List Scanning.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "LunarRobot";

    // --- Shared Memory Constants ---
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    
    // Keys matching your RobotFragment/AppsFragment
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode"; // "semi" or "full"
    private static final String KEY_TARGET_APP_LABEL = "target_app_label"; // e.g., "WhatsApp"

    private boolean isScrolling = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // We listen to Window changes (screen updates), Content changes (text updates), and Notifications.
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                          AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        
        setServiceInfo(info);
        showDebugToast("🤖 LunarTag Robot: Online & Intelligent");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 1. Check if a job is actually pending.
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean isJobPending = prefs.getBoolean(KEY_JOB_PENDING, false);

        // If no job, SLEEP. Do not consume battery or CPU.
        if (!isJobPending) {
            return;
        }

        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroupName = prefs.getString(KEY_TARGET_GROUP, "").trim();
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        // Identify the current app (Package Name)
        CharSequence packageNameSeq = event.getPackageName();
        String packageName = (packageNameSeq != null) ? packageNameSeq.toString().toLowerCase() : "";
        
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // ====================================================================
        // SAFETY LAYER: LAUNCHER & SYSTEM PROTECTION
        // ====================================================================
        // Prevents clicking icons on the Home Screen or interfering with System UI.
        if (isLauncherOrSystemUI(packageName)) {
            // EXCEPTION: In "Full" mode, we MUST listen for the Notification event.
            if (mode.equals("full") && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                // Allow processing to continue strictly for Notification handling
            } else {
                // Otherwise, STOP. Do not scan the home screen. Do not scroll.
                return; 
            }
        }

        // ====================================================================
        // PHASE 1: FULL AUTOMATIC - NOTIFICATION HANDLING
        // ====================================================================
        if (mode.equals("full") && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            // Ensure we only react to LunarTag notifications
            if (packageName.contains(getPackageName())) {
                Parcelable data = event.getParcelableData();
                if (data instanceof Notification) {
                    showDebugToast("🤖 Notification Detected. Clicking...");
                    try {
                        Notification notification = (Notification) data;
                        if (notification.contentIntent != null) {
                            notification.contentIntent.send();
                            // CRITICAL: Return immediately. 
                            // Wait for the Share Sheet to open before doing anything else.
                            return; 
                        }
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "Notification intent canceled", e);
                    }
                }
            }
            // If it's not our notification, ignore it.
            return;
        }

        // ====================================================================
        // PHASE 2: SHARE SHEET / APP CHOOSER (Before WhatsApp)
        // ====================================================================
        // We are in Full Mode, but NOT inside WhatsApp yet. We are looking for the app in the list.
        if (mode.equals("full") && !packageName.contains("whatsapp")) {
            
            // 1. Try to find the App Name (e.g., "WhatsApp") and Click
            if (scanAndClick(rootNode, targetAppLabel)) {
                showDebugToast("🤖 Found Target App: " + targetAppLabel);
                return;
            }

            // 2. Clone Support: If target is "WhatsApp (Clone)" but not found, try "WhatsApp"
            if (targetAppLabel.toLowerCase().contains("clone") || targetAppLabel.toLowerCase().contains("dual")) {
                 if (scanAndClick(rootNode, "WhatsApp")) {
                     showDebugToast("🤖 Clicking Parent App Logic...");
                     return;
                 }
            }
            
            // 3. Intelligent Scroll
            // Only scroll if we are inside the Android System (Share Sheet/Resolver).
            // Do NOT scroll if we are somehow back on the Launcher.
            if (packageName.equals("android") || packageName.contains("resolver") || packageName.contains("chooser")) {
                performScroll(rootNode);
            }
            
            // Stop here. Don't run WhatsApp logic until we are actually IN WhatsApp.
            return;
        }

        // ====================================================================
        // PHASE 3: INSIDE WHATSAPP (Chat List or Conversation)
        // ====================================================================
        if (packageName.contains("whatsapp")) {
            
            // A. HIGH PRIORITY: Check for "Send" Button (Paper Plane)
            // This means we are inside the chat and ready to finish.
            if (scanAndClickContentDesc(rootNode, "Send")) {
                showDebugToast("🤖 Message Sent! Job Complete.");
                // Mark job as done so the robot stops immediately.
                prefs.edit().putBoolean(KEY_JOB_PENDING, false).apply();
                return; 
            }

            // B. Find the Target Group/Contact Name
            if (!targetGroupName.isEmpty()) {
                
                // Strategy 1: Fast Text Search (Visible Screen)
                if (scanAndClick(rootNode, targetGroupName)) {
                    showDebugToast("🤖 Found Group: " + targetGroupName);
                    return;
                }
                
                // Strategy 2: Deep Scan (For RecyclerView items)
                // Sometimes text is hidden inside a complex view hierarchy.
                if (scanListItemsManually(rootNode, targetGroupName)) {
                     showDebugToast("🤖 Found Group (Deep Scan): " + targetGroupName);
                     return;
                }

                // Strategy 3: Scroll
                // Only scroll if the item is definitely NOT on screen.
                performScroll(rootNode);
            }
        }
    }

    // -----------------------------------------------------------------------
    // HELPER METHODS
    // -----------------------------------------------------------------------

    /**
     * Detects if the current app is a Launcher (Home Screen) or System UI.
     * Used to prevent the robot from clicking icons on your desktop.
     */
    private boolean isLauncherOrSystemUI(String packageName) {
        return packageName.contains("launcher") || 
               packageName.contains("home") || 
               packageName.contains("quickstep") || // Android One/Pixel Launcher
               packageName.contains("trebuchet") || // LineageOS
               packageName.contains("nexuslauncher") ||
               packageName.contains("systemui");    // Notification Shade / Status Bar
    }

    /**
     * Manual Scanner: Iterates through list items to find text that standard search might miss.
     */
    private boolean scanListItemsManually(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return false;
        
        // Detect if the node is a container (RecyclerView, ListView, etc.)
        if (root.getClassName() != null && 
           (root.getClassName().toString().contains("RecyclerView") || 
            root.getClassName().toString().contains("ListView") ||
            root.getClassName().toString().contains("ViewGroup"))) {
            
            // Check immediate children
            for (int i = 0; i < root.getChildCount(); i++) {
                AccessibilityNodeInfo child = root.getChild(i);
                if (recursiveTextCheck(child, targetText)) {
                    return true;
                }
            }
        }

        // Continue searching deeper
        for (int i = 0; i < root.getChildCount(); i++) {
            if (scanListItemsManually(root.getChild(i), targetText)) return true;
        }
        return false;
    }

    /**
     * Recursive Check: Looks at Text and ContentDescription.
     */
    private boolean recursiveTextCheck(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;

        // Check Text
        if (node.getText() != null && 
            node.getText().toString().toLowerCase().contains(target.toLowerCase())) {
            return tryClickingHierarchy(node);
        }
        
        // Check Description
        if (node.getContentDescription() != null && 
            node.getContentDescription().toString().toLowerCase().contains(target.toLowerCase())) {
            return tryClickingHierarchy(node);
        }

        // Check Children
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveTextCheck(node.getChild(i), target)) return true;
        }
        return false;
    }

    /**
     * Finds a node by exact or partial text match and clicks it.
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
     * Finds a node by Content Description (e.g., "Send" button) and clicks it.
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
     * Tries to click the node. If not clickable, climbs up to parents (up to 6 levels).
     */
    private boolean tryClickingHierarchy(AccessibilityNodeInfo node) {
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

    /**
     * Scrolls the list forward safely with debouncing to prevent interference.
     */
    private void performScroll(AccessibilityNodeInfo root) {
        if (isScrolling) return; // Prevent rapid-fire scrolling

        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable != null) {
            isScrolling = true;
            // showDebugToast("🤖 Scrolling..."); // Visual feedback
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            
            // Wait 1.5 seconds before allowing another scroll.
            // This gives the UI time to settle and allows the user to interrupt if needed.
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 1500);
        }
    }

    /**
     * Recursively finds the first scrollable element.
     */
    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findScrollableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Displays a small toast message for debugging/status.
     */
    private void showDebugToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }
}