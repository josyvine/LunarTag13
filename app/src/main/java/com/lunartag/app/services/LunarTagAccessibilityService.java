package com.lunartag.app.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * LUNARTAG ROBOT - "SAFE & SECURE" EDITION
 * 
 * FINAL FIXES:
 * 1. PERSONAL SAFETY LOCK: Robot ignores WhatsApp if opened from Home Screen (Launcher).
 * 2. SHARE SHEET SCROLL: Fixes hidden icons by scrolling and waiting.
 * 3. CLONE TARGETING: Searches for "Clone" to avoid ambiguity.
 * 4. TERRITORY LOCK: Instant freeze if user switches to unrelated apps.
 */
public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_AUTO_MODE = "automation_mode"; 
    private static final String KEY_TARGET_GROUP = "target_group_name";

    // STATES
    private static final int STATE_IDLE = 0;
    private static final int STATE_SEARCHING_SHARE_SHEET = 1;
    private static final int STATE_SEARCHING_GROUP = 2;
    private static final int STATE_CLICKING_SEND = 3;

    private int currentState = STATE_IDLE;
    private boolean isScrolling = false;
    
    // SAFETY TRACKING
    private String lastExternalPackage = ""; // Tracks where we came from (Launcher vs App)

    // TARGET KEYWORDS
    private static final String KEYWORD_CLONE = "Clone"; 
    private static final String KEYWORD_SEND_HEADER = "Send to"; 
    private static final String KEYWORD_RECENT = "Recent chats";

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

        currentState = STATE_IDLE;
        lastExternalPackage = "";

        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(getApplicationContext(), "🛡️ ROBOT SAFE MODE: ACTIVE", Toast.LENGTH_LONG).show());

        performBroadcastLog("🔴 SYSTEM READY. Waiting for job...");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkgName = event.getPackageName().toString().toLowerCase();
        
        // IGNORE SYSTEM NOISE (Keyboard, System UI, etc.) so we don't lose track of the real previous app
        if (pkgName.contains("inputmethod") || pkgName.contains("systemui")) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_AUTO_MODE, "semi");

        boolean isWhatsApp = pkgName.contains("whatsapp");
        boolean isSystemShare = pkgName.equals("android") || pkgName.contains("ui") || pkgName.contains("resolver");
        boolean isMyApp = pkgName.contains("lunartag");

        // ====================================================================
        // 1. SOURCE TRACKING (THE PERSONAL SAFETY FIX)
        // ====================================================================
        // We track the last app used BEFORE WhatsApp or Share Sheet opened.
        if (!isWhatsApp && !isSystemShare) {
            lastExternalPackage = pkgName;
            // If user goes to Home Screen (Launcher), robot marks this as "Unsafe/Personal"
        }

        // ====================================================================
        // 2. SECURITY GATEKEEPER (TERRITORY LOCK)
        // ====================================================================
        // If not in WhatsApp, Share Sheet, or Our App -> FREEZE.
        if (!isWhatsApp && !isSystemShare && !isMyApp) {
            if (currentState != STATE_IDLE) {
                performBroadcastLog("🛑 Personal Activity Detected (" + pkgName + "). Robot Sleeping.");
                currentState = STATE_IDLE;
            }
            return; 
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // ====================================================================
        // 3. FULL AUTOMATIC: SYSTEM SHARE SHEET
        // ====================================================================
        if (mode.equals("full") && isSystemShare) {
            
            // Only activate if we came from our App (or background service)
            // If user clicked "Share" from Gallery, lastExternalPackage would be "gallery" -> We Ignore.
            if (!lastExternalPackage.contains("lunartag") && currentState == STATE_IDLE) {
                 return; // Ignore shares from other apps
            }

            if (currentState == STATE_IDLE) {
                currentState = STATE_SEARCHING_SHARE_SHEET;
            }

            if (currentState == STATE_SEARCHING_SHARE_SHEET) {
                // A. LOOK FOR "CLONE"
                if (scanAndClick(root, KEYWORD_CLONE)) {
                    performBroadcastLog("✅ Full Auto: Clicked 'WhatsApp(Clone)'");
                    currentState = STATE_SEARCHING_GROUP; 
                    return;
                }

                // B. VISUAL SCROLL & RETRY (Fix for Hidden Icons)
                performBroadcastLog("👀 Icon hidden. Scrolling Share List...");
                performScroll(root);
            }
        }

        // ====================================================================
        // 4. WHATSAPP LOGIC (SAFEGUARDED)
        // ====================================================================
        if (isWhatsApp) {

            // SAFETY CHECK: DID WE COME FROM THE APP OR SHARE SHEET?
            // If last app was "Launcher" (Home Screen), we DO NOT run.
            // We only run if previous app was "lunartag" or "android" (Share Sheet).
            boolean cameFromAuthorizedSource = lastExternalPackage.contains("lunartag") || lastExternalPackage.equals("android");
            
            // If we are IDLE, we check authorization before starting
            if (currentState == STATE_IDLE && !cameFromAuthorizedSource) {
                // This is Personal Use. Do nothing.
                return;
            }

            // A. TRIGGER: TEXT DETECTION
            if (currentState == STATE_IDLE || currentState == STATE_SEARCHING_SHARE_SHEET) {
                if (hasTextOnScreen(root, KEYWORD_SEND_HEADER) || hasTextOnScreen(root, KEYWORD_RECENT)) {
                    performBroadcastLog("⚡ Authorized Job Detected. Starting Search...");
                    currentState = STATE_SEARCHING_GROUP;
                }
            }

            // B. FIND GROUP
            if (currentState == STATE_SEARCHING_GROUP) {
                String targetGroup = prefs.getString(KEY_TARGET_GROUP, "");
                if (targetGroup.isEmpty()) return;

                if (scanAndClick(root, targetGroup)) {
                    performBroadcastLog("✅ Found Group: " + targetGroup);
                    currentState = STATE_CLICKING_SEND;
                    return;
                }

                performBroadcastLog("🔎 Searching list for group...");
                performScroll(root);
            }

            // C. CLICK SEND
            else if (currentState == STATE_CLICKING_SEND) {
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
                    performBroadcastLog("🚀 SENT! Job Done. Sleeping.");
                    currentState = STATE_IDLE; 
                    // Reset safety to prevent looping
                    lastExternalPackage = "finished"; 
                }
            }
        }
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    private void performBroadcastLog(String msg) {
        try {
            System.out.println("LUNARTAG_LOG: " + msg);
            Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
            intent.putExtra("log_msg", msg);
            intent.setPackage(getPackageName());
            getApplicationContext().sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean hasTextOnScreen(AccessibilityNodeInfo root, String text) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        return nodes != null && !nodes.isEmpty();
    }

    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (performClick(node)) return true;
            }
        }
        return recursiveSearch(root, text);
    }

    private boolean recursiveSearch(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
            return performClick(node);
        }
        if (node.getContentDescription() != null && node.getContentDescription().toString().toLowerCase().contains(text.toLowerCase())) {
            return performClick(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveSearch(node.getChild(i), text)) return true;
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
        if (node == null) return false;
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
            // VISUAL WAIT: Essential for seeing the scroll and loading hidden items
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScrolling = false, 600);
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
        currentState = STATE_IDLE;
    }
}