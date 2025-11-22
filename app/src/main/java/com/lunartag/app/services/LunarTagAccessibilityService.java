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
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String TAG = "LunarTagRobot";

    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_GROUP = "target_group_name";
    private static final String KEY_JOB_PENDING = "job_is_pending";
    private static final String KEY_AUTO_MODE = "automation_mode";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";
    private static final String KEY_JOB_START_TIME = "job_start_time"; // For timeout

    private enum State {
        IDLE,
        WAITING_FOR_SHARE_SHEET,  // Full auto: After firing intent
        IN_SHARE_SHEET,
        IN_WHATSAPP_CHAT_LIST,
        IN_GROUP_CHAT,
        JOB_COMPLETE
    }

    private State currentState = State.IDLE;
    private long lastScrollTime = 0;
    private static final long SCROLL_COOLDOWN = 1200; // Faster for WhatsApp
    private static final long JOB_TIMEOUT = 30000; // 30s max per job
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        showToast("LunarTag Robot v2 | Full Auto FIXED");
        resetToIdle();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        boolean jobPending = prefs.getBoolean(KEY_JOB_PENDING, false);
        if (!jobPending) {
            if (currentState != State.IDLE) resetToIdle();
            return;
        }

        // Job timeout check
        long startTime = prefs.getLong(KEY_JOB_START_TIME, 0);
        if (System.currentTimeMillis() - startTime > JOB_TIMEOUT) {
            showToast("Job timed out - resetting");
            finishJob();
            return;
        }

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        root.refresh(); // FIX: Refresh for fresh WhatsApp nodes

        String packageName = event.getPackageName() != null ? event.getPackageName().toString().toLowerCase() : "";
        String mode = prefs.getString(KEY_AUTO_MODE, "semi");
        String targetGroup = prefs.getString(KEY_TARGET_GROUP, "").trim();
        String targetAppLabel = prefs.getString(KEY_TARGET_APP_LABEL, "WhatsApp");

        // FULL AUTO: Fire share intent if waiting (replaces unreliable notif click)
        if (mode.equals("full") && currentState == State.WAITING_FOR_SHARE_SHEET) {
            fireShareIntent(); // Triggers share sheet directly
            return;
        }

        // Handle notification as fallback (semi/full)
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && packageName.contains(getPackageName())) {
            clickOurNotification(event);
            return;
        }

        if (packageName.contains("whatsapp")) {
            handleWhatsApp(root, targetGroup);
        } else if (isShareSheetActive(root)) {
            currentState = State.IN_SHARE_SHEET;
            handleShareSheet(root, targetAppLabel);
        } else {
            if (currentState != State.WAITING_FOR_SHARE_SHEET) currentState = State.IDLE;
        }
    }

    // NEW: Reliable full auto share trigger
    private void fireShareIntent() {
        // Simulate share from our app - direct intent (works on A15+)
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Automated share from LunarTag");
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(shareIntent);
            showToast("Full Auto: Share sheet opened");
            currentState = State.IN_SHARE_SHEET;
        } catch (Exception e) {
            Log.e(TAG, "Share intent failed", e);
            // Fallback: Global action to open notifications
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            mainHandler.postDelayed(this::performGlobalAction, 500, GLOBAL_ACTION_QUICK_SETTINGS);
        }
    }

    private void handleWhatsApp(AccessibilityNodeInfo root, String targetGroup) {
        currentState = State.IN_WHATSAPP_CHAT_LIST;

        if (clickSendButton(root)) {
            showToast("Message SENT! Job Done");
            finishJob();
            return;
        }

        if (isInChatTypingMode(root)) {
            currentState = State.IN_GROUP_CHAT;
            showToast("In group chat. Waiting for send...");
            return;
        }

        if (!targetGroup.isEmpty()) {
            // Scan first - your manual logic
            if (scanAndClick(root, targetGroup) || scanListItemsManually(root, targetGroup)) {
                showToast("Group opened: " + targetGroup);
                delayStateChange(State.IN_GROUP_CHAT, 1400);
                return;
            }
            // Only scroll if not visible
            if (!isGroupPartiallyVisible(root, targetGroup)) {
                smartScroll(root);
            } else {
                showToast("Group visible - no scroll needed");
            }
        }
    }

    private void handleShareSheet(AccessibilityNodeInfo root, String targetAppLabel) {
        boolean clicked = scanAndClick(root, targetAppLabel);

        if (!clicked && (targetAppLabel.toLowerCase().contains("clone") ||
                         targetAppLabel.toLowerCase().contains("dual") ||
                         targetAppLabel.toLowerCase().contains("2") ||
                         targetAppLabel.toLowerCase().contains("business"))) {
            clicked = scanAndClick(root, "WhatsApp");
        }

        if (clicked) {
            showToast("WhatsApp Selected");
            delayStateChange(State.IN_WHATSAPP_CHAT_LIST, 1500);
        } else {
            smartScroll(root);
        }
    }

    private void clickOurNotification(AccessibilityEvent event) {
        Parcelable data = event.getParcelableData();
        if (data instanceof Notification) {
            Notification n = (Notification) data;
            if (n.contentIntent != null) {
                try {
                    n.contentIntent.send();
                    showToast("Notif clicked - opening share");
                    currentState = State.IN_SHARE_SHEET;
                } catch (Exception e) {
                    Log.e(TAG, "Notif send failed", e);
                    fireShareIntent(); // Fallback
                }
            }
        }
    }

    // IMPROVED SCROLL: Check visibility first
    private void smartScroll(AccessibilityNodeInfo root) {
        if (System.currentTimeMillis() - lastScrollTime < SCROLL_COOLDOWN) return;
        AccessibilityNodeInfo scrollable = findScrollableNode(root);
        if (scrollable == null) return;

        // Try forward, fallback backward if at bottom
        boolean scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if (!scrolled) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        showToast("Scrolling list...");
        lastScrollTime = System.currentTimeMillis();
    }

    // NEW: Quick visibility check before scroll
    private boolean isGroupPartiallyVisible(AccessibilityNodeInfo root, String target) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isVisibleToUser()) return true;
            }
        }
        return recursiveTextCheck(root, target); // Your manual check
    }

    private boolean isShareSheetActive(AccessibilityNodeInfo root) {
        if (root.getPackageName() == null) return false;
        String pkg = root.getPackageName().toString();
        return pkg.contains("systemui") ||
               findNodeByViewId(root, "android:id/chooser_recycler_view") != null ||
               hasText(root, "Share with") ||
               hasText(root, "Choose app");
    }

    private boolean clickSendButton(AccessibilityNodeInfo root) {
        return scanAndClickContentDesc(root, "Send") ||
               clickByViewId(root, "com.whatsapp:id/send");
    }

    private boolean isInChatTypingMode(AccessibilityNodeInfo root) {
        return findNodeByViewId(root, "com.whatsapp:id/entry") != null;
    }

    private boolean hasText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(text);
        return list != null && !list.isEmpty();
    }

    private void delayStateChange(State next, long delay) {
        mainHandler.postDelayed(() -> currentState = next, delay);
    }

    private void finishJob() {
        getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_JOB_PENDING, false).apply();
        resetToIdle();
        showToast("Job Complete!");
    }

    private void resetToIdle() {
        currentState = State.IDLE;
        lastScrollTime = 0;
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ——— YOUR ORIGINAL HELPERS (ENHANCED WITH REFRESH) ———
    private boolean scanAndClick(AccessibilityNodeInfo root, String text) {
        if (root == null || text.isEmpty()) return false;
        root.refresh();
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null) for (AccessibilityNodeInfo n : nodes) if (tryClickingHierarchy(n)) return true;
        return false;
    }

    private boolean scanAndClickContentDesc(AccessibilityNodeInfo node, String desc) {
        if (node == null) return false;
        node.refresh();
        if (node.getContentDescription() != null &&
            node.getContentDescription().toString().toLowerCase().contains(desc.toLowerCase())) {
            return tryClickingHierarchy(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (scanAndClickContentDesc(node.getChild(i), desc)) return true;
        }
        return false;
    }

    private boolean scanListItemsManually(AccessibilityNodeInfo root, String target) {
        if (root == null) return false;
        root.refresh();
        String className = root.getClassName() != null ? root.getClassName().toString() : "";
        if (className.contains("RecyclerView") || className.contains("ListView")) {
            for (int i = 0; i < root.getChildCount(); i++) {
                AccessibilityNodeInfo child = root.getChild(i);
                if (child != null && recursiveTextCheck(child, target)) return true;
            }
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null && scanListItemsManually(child, target)) return true;
        }
        return false;
    }

    private boolean recursiveTextCheck(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;
        node.refresh();
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if (t != null && t.toString().toLowerCase().contains(target.toLowerCase())) return tryClickingHierarchy(node);
        if (d != null && d.toString().toLowerCase().contains(target.toLowerCase())) return tryClickingHierarchy(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            if (recursiveTextCheck(node.getChild(i), target)) return true;
        }
        return false;
    }

    private boolean tryClickingHierarchy(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 8 && n != null; i++) {
            if (n.isClickable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        node.refresh();
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollableNode(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByViewId(AccessibilityNodeInfo root, String id) {
        root.refresh();
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private boolean clickByViewId(AccessibilityNodeInfo root, String id) {
        AccessibilityNodeInfo n = findNodeByViewId(root, id);
        return n != null && tryClickingHierarchy(n);
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, "LunarTag: " + msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onInterrupt() {
        resetToIdle();
    }
}