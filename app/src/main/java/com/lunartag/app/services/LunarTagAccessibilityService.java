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

public class LunarTagAccessibilityService extends AccessibilityService {

    private static final String PREFS = "LunarTagAccessPrefs";
    private static final String KEY_MODE = "automation_mode";
    private static final String KEY_GROUP = "target_group_name";

    private int state = 0; // 0 = idle, 1 = searching group, 2 = clicking send
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        state = 0;
        toast("LUNARTAG ROBOT READY");
        log("SYSTEM ONLINE");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString().toLowerCase();

        // WHITELIST: ONLY react in share sheet or WhatsApp
        boolean inShareSheet = pkg.contains("android") || pkg.contains("resolver") || pkg.contains("systemui");
        boolean inWhatsApp = pkg.contains("whatsapp");

        if (!inShareSheet && !inWhatsApp) return; // ← Robot sleeps everywhere else

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String mode = prefs.getString(KEY_MODE, "semi");
        String targetGroup = prefs.getString(KEY_GROUP, "").trim();

        if (targetGroup.isEmpty()) {
            log("NO GROUP SAVED!");
            return;
        }

        // FULL AUTO: Click "WhatsApp (clone)" in share sheet
        if (inShareSheet && "full".equals(mode) && state == 0) {
            waitAndClick("CLONE", rootNode -> {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("WhatsApp");
                for (AccessibilityNodeInfo n : nodes) {
                    String label = getTextOrDesc(n);
                    if (label.contains("(clone)")) {
                        if (clickParent(n)) {
                            log("Clicked WhatsApp (clone)");
                            state = 1;
                            return true;
                        }
                    }
                }
                return false;
            }, 8000);
            return;
        }

        // Inside WhatsApp (original or clone)
        if (inWhatsApp) {
            if (state == 0) {
                log("Entered WhatsApp → Starting automation");
                state = 1;
            }

            // Search & click group
            if (state == 1) {
                waitAndClick("GROUP '" + targetGroup + "'", rootNode -> {
                    AccessibilityNodeInfo groupNode = findGroup(rootNode, targetGroup);
                    if (groupNode != null && clickParent(groupNode)) {
                        log("Group clicked: " + targetGroup);
                        state = 2;
                        return true;
                    }
                    return false;
                }, 15000);
            }

            // Click Send button
            if (state == 2) {
                waitAndClick("SEND", rootNode -> {
                    // View ID
                    List<AccessibilityNodeInfo> sendBtn = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
                    if (!sendBtn.isEmpty() && sendBtn.get(0).isEnabled()) {
                        if (clickParent(sendBtn.get(0))) {
                            log("SENT! Job done.");
                            state = 0;
                            return true;
                        }
                    }
                    // Fallback: content description
                    AccessibilityNodeInfo send = findByContentDesc(rootNode, "Send");
                    if (send != null && clickParent(send)) {
                        log("SENT (fallback)!");
                        state = 0;
                        return true;
                    }
                    return false;
                }, 6000);
            }
        }
    }

    // NON-INTRUSIVE POLLING (only runs during sharing)
    private void waitAndClick(String task, Condition condition, long timeout) {
        final long deadline = System.currentTimeMillis() + timeout;
        Runnable check = new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > deadline) {
                    log(task + " → TIMEOUT");
                    state = 0;
                    return;
                }
                String pkg = getRootInActiveWindow() != null ?
                    getRootInActiveWindow().getPackageName().toString().toLowerCase() : "";
                if (!pkg.contains("whatsapp") && !pkg.contains("android") && !pkg.contains("resolver")) {
                    state = 0;
                    return;
                }
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null && condition.test(r)) return;
                handler.postDelayed(this, 180);
            }
        };
        handler.post(check);
    }

    @FunctionalInterface
    interface Condition {
        boolean test(AccessibilityNodeInfo root);
    }

    private String getTextOrDesc(AccessibilityNodeInfo n) {
        if (n.getText() != null) return n.getText().toString();
        if (n.getContentDescription() != null) return n.getContentDescription().toString();
        return "";
    }

    private AccessibilityNodeInfo findGroup(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        String t = getTextOrDesc(node);
        if (t.toLowerCase().contains(text.toLowerCase())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findGroup(node.getChild(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByContentDesc(AccessibilityNodeInfo node, String desc) {
        if (node == null) return null;
        if (desc.equalsIgnoreCase(getTextOrDesc(node))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo f = findByContentDesc(node.getChild(i), desc);
            if (f != null) return f;
        }
        return null;
    }

    private boolean clickParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 10; i++) {
            if (n.isClickable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
            if (n.getParent() == null) break;
            n = n.getParent();
        }
        return false;
    }

    private void toast(String msg) {
        handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private void log(String msg) {
        System.out.println("LUNARTAG: " + msg);
        Intent i = new Intent("com.lunartag.ACTION_LOG_UPDATE");
        i.putExtra("log_msg", msg);
        i.setPackage(getPackageName());
        getApplicationContext().sendBroadcast(i);
    }

    @Override
    public void onInterrupt() {
        log("ROBOT INTERRUPTED");
    }
}