package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_AUTO_MODE = "automation_mode"; 
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";

    // STATES
    private static final int STATE_IDLE = 0;
    private static final int STATE_SEARCHING_SHARE_SHEET = 1;
    private static final int STATE_SEARCHING_GROUP = 2;
    private static final int STATE_CLICKING_SEND = 3;

    private int currentState = STATE_IDLE;
    private boolean isScrolling = false;
    private boolean isClickingPending = false; 

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK; 
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0; 
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        // Force Start Overlay
        try {
            Intent intent = new Intent(this, OverlayService.class);
            startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        currentState = STATE_IDLE;
        performBroadcastLog("🔴 ROBOT ONLINE. LEARNING MODE READY.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkgName = event.getPackageName().toString().toLowerCase();

        // 1. IGNORE NOISE
        if (pkgName.contains("inputmethod") || pkgName.contains("systemui")) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        // Note: root can be null in some events, but we might need event.getSource() for learning

        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_AUTO_MODE, "semi");

        // READ SETTINGS
        String targetAppName = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp(Clone)");
        String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");

        // ====================================================================
        // 0. TRAINING MODE (TEACH THE ROBOT)
        // ====================================================================
        if (targetAppName.equals("SETUP")) {

            // FIX: CONTEXT CHECK
            // If we don't see "Share" or "Cancel", we are likely still in Settings or Camera.
            // Wait until the actual Share Sheet appears before recording clicks.
            if (root != null && !hasText(root, "Share") && !hasText(root, "Cancel")) {
                return; // Ignore clicks until Share Sheet is detected
            }

            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                AccessibilityNodeInfo clickedNode = event.getSource();
                if (clickedNode != null) {
                    // Extract text from the clicked item or its children
                    String signature = extractTextFromNode(clickedNode);

                    if (signature != null && !signature.isEmpty() && !signature.equalsIgnoreCase("cancel")) {
                        // SAVE THE LEARNED SIGNATURE
                        prefs.edit().putString(KEY_TARGET_APP_LABEL, signature).apply();

                        // VISUAL CONFIRMATION
                        new Handler(Looper.getMainLooper()).post(() -> 
                            Toast.makeText(getApplicationContext(), "✅ Robot Learned: " + signature, Toast.LENGTH_LONG).show());

                        performBroadcastLog("✅ TRAINING COMPLETE. Target set to: " + signature);
                    }
                }
            }
            return; // STOP HERE. Do not auto-click while training.
        }

        // ====================================================================
        // NORMAL OPERATION STARTS HERE
        // ====================================================================

        if (root == null) return;

        // IF WAITING FOR RED LIGHT, FREEZE
        if (isClickingPending) return;

        // 2. SMART CONTEXT DETECTION

        // A. Are we in WhatsApp? (Look for "Send to")
        boolean isWhatsAppUI = hasText(root, "Send to") || hasText(root, "Recent chats");

        // B. Are we on the Share Sheet? (Look for Target OR "Cancel")
        boolean isShareSheet = hasText(root, targetAppName) || hasText(root, "Cancel") || hasText(root, "Share");

        // SAFETY: If neither Context is visible, STOP.
        if (!isWhatsAppUI && !isShareSheet) {
            if (currentState != STATE_IDLE) {
                currentState = STATE_IDLE;
                if (OverlayService.getInstance() != null) OverlayService.getInstance().hideMarker();
            }
            return; 
        }

        // ====================================================================
        // 3. SHARE SHEET LOGIC (Full Auto)
        // ====================================================================
        if (mode.equals("full") && !isWhatsAppUI) {

            // If we see the Target App Name (Learned or Typed), Click it!
            if (findMarkerAndClick(root, targetAppName, true)) {
                performBroadcastLog("✅ Share Sheet: Found '" + targetAppName + "'. RED LIGHT + CLICK.");
                currentState = STATE_SEARCHING_GROUP;
                return;
            }

            // If we see "Cancel" but not the app, Scroll.
            if (hasText(root, "Cancel") && !isScrolling) {
                performScroll(root);
            }
        }

        // ====================================================================
        // 4. WHATSAPP LOGIC (Semi & Full) - UNTOUCHED AS REQUESTED
        // ====================================================================
        if (isWhatsAppUI) {

            // Set State if just arrived
            if (currentState == STATE_IDLE || currentState == STATE_SEARCHING_SHARE_SHEET) {
                performBroadcastLog("⚡ WhatsApp Detected. Searching Group...");
                currentState = STATE_SEARCHING_GROUP;
            }

            // SEARCH FOR GROUP
            if (currentState == STATE_SEARCHING_GROUP) {
                if (targetGroup.isEmpty()) return;

                if (findMarkerAndClick(root, targetGroup, true)) {
                    performBroadcastLog("✅ Group Found. RED LIGHT + CLICK.");
                    currentState = STATE_CLICKING_SEND;
                    return;
                }

                if (!isScrolling) performScroll(root);
            }

            // CLICK SEND
            else if (currentState == STATE_CLICKING_SEND) {
                boolean found = false;

                if (findMarkerAndClickID(root, "com.whatsapp:id/conversation_send_arrow")) found = true;
                if (!found && findMarkerAndClickID(root, "com.whatsapp:id/send")) found = true;
                if (!found && findMarkerAndClick(root, "Send", false)) found = true;

                if (found) {
                    performBroadcastLog("🚀 SENT! Job Done.");
                    currentState = STATE_IDLE;
                }
            }
        }
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    // Helper for Training Mode to find text inside a clicked icon layout
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.getText() != null && !node.getText().toString().isEmpty()) return node.getText().toString();
        if (node.getContentDescription() != null && !node.getContentDescription().toString().isEmpty()) return node.getContentDescription().toString();

        for (int i = 0; i < node.getChildCount(); i++) {
            String childText = extractTextFromNode(node.getChild(i));
            if (childText != null) return childText;
        }
        return null;
    }

    private boolean hasText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;
        // Fuzzy Match: Remove spaces/newlines/brackets to match recorded signatures
        String cleanTarget = cleanString(text);

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(cleanTarget);
        if (nodes != null && !nodes.isEmpty()) return true;

        return recursiveCheckText(root, cleanTarget);
    }

    private boolean recursiveCheckText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && cleanString(node.getText().toString()).contains(text)) return true;
        if (node.getContentDescription() != null && cleanString(node.getContentDescription().toString()).contains(text)) return true;

        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveCheckText(node.getChild(i), text)) return true;
        }
        return false;
    }

    private boolean findMarkerAndClick(AccessibilityNodeInfo root, String text, boolean isTextSearch) {
        if (root == null || text == null || text.isEmpty()) return false;

        // Direct Search
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable() || node.getParent().isClickable()) {
                    executeVisualClick(node);
                    return true;
                }
            }
        }
        // Deep Search
        return recursiveSearchAndClick(root, text);
    }

    private boolean findMarkerAndClickID(AccessibilityNodeInfo root, String viewId) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            executeVisualClick(nodes.get(0));
            return true;
        }
        return false;
    }

    private boolean recursiveSearchAndClick(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        boolean match = false;

        String cleanTarget = cleanString(text);

        if (node.getText() != null && cleanString(node.getText().toString()).contains(cleanTarget)) match = true;
        if (!match && node.getContentDescription() != null && cleanString(node.getContentDescription().toString()).contains(cleanTarget)) match = true;

        if (match) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) {
                clickable = clickable.getParent();
            }
            if (clickable != null) {
                executeVisualClick(clickable);
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveSearchAndClick(node.getChild(i), text)) return true;
        }
        return false;
    }

    // Helper to normalize text for comparison (Fixes "WhatsApp(Clone )" issues)
    private String cleanString(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replace(" ", "")
                .replace("\n", "")
                .trim();
    }

    private void executeVisualClick(AccessibilityNodeInfo node) {
        if (isClickingPending) return;
        isClickingPending = true;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        if (OverlayService.getInstance() != null) {
            OverlayService.getInstance().showMarkerAt(bounds);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            performClick(node);
            isClickingPending = false;
        }, 500); 
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
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 800);
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

    private void performBroadcastLog(String msg) {
        try {
            System.out.println("LUNARTAG_LOG: " + msg);
            Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
            intent.putExtra("log_msg", msg);
            intent.setPackage(getPackageName());
            getApplicationContext().sendBroadcast(intent);
        } catch (Exception e) {}
    }

    @Override
    public void onInterrupt() {
        currentState = STATE_IDLE;
        if (OverlayService.getInstance() != null) OverlayService.getInstance().hideMarker();
    }
}