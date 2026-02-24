package com.prajwal.myfirstapp;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Mirror Service — captures phone notifications and
 * sends them to the PC Control Panel via UDP.
 *
 * Protocol:
 *   Phone → PC:  NOTIF_MIRROR:<JSON>
 *   Phone → PC:  NOTIF_DISMISSED:<key>
 *   PC → Phone:  NOTIF_DISMISS:<key>   (handled in ReverseCommandListener)
 *
 * Requires user to grant Notification Access in:
 *   Settings → Apps & Notifications → Special Access → Notification Access
 */
public class NotifMirrorService extends NotificationListenerService {

    private static final String TAG = "NotifMirror";
    private static final int PC_COMMAND_PORT = 5005; // Main server port

    // Static reference so ReverseCommandListener can call dismissNotification()
    private static NotifMirrorService instance;

    // Rate limiting: track last notification time per package
    private final Map<String, Long> lastNotifTime = new HashMap<>();
    private static final long RATE_LIMIT_MS = 500;

    // Server IP (set from MainActivity when connection is established)
    private static String serverIp = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "NotifMirrorService created");
    }

    @Override
    public void onDestroy() {
        instance = null;
        Log.i(TAG, "NotifMirrorService destroyed");
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Notification listener connected — access granted");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Notification listener disconnected");
        // Try to reconnect
        requestRebind(new ComponentName(this, NotifMirrorService.class));
    }

    // ─── CALLED FROM MAINACTIVITY ──────────────────────────────

    public static void setServerIp(String ip) {
        serverIp = ip;
        Log.i(TAG, "Server IP set to: " + ip);
    }

    // ─── NOTIFICATION POSTED ───────────────────────────────────

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (serverIp == null) return;

        String pkg = sbn.getPackageName();

        // Filter out our own notifications to avoid loops
        if (pkg.equals(getPackageName())) return;

        // Filter out ongoing/non-clearable notifications (e.g. music players, foreground services)
        if (!sbn.isClearable()) return;

        // Rate limit per package
        long now = System.currentTimeMillis();
        Long lastTime = lastNotifTime.get(pkg);
        if (lastTime != null && (now - lastTime) < RATE_LIMIT_MS) return;
        lastNotifTime.put(pkg, now);

        try {
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;

            String title = extras.containsKey(Notification.EXTRA_TITLE)
                    ? String.valueOf(extras.get(Notification.EXTRA_TITLE))
                    : "";
            String body = extras.containsKey(Notification.EXTRA_TEXT)
                    ? String.valueOf(extras.get(Notification.EXTRA_TEXT))
                    : "";

            // Skip empty notifications
            if (title.isEmpty() && body.isEmpty()) return;

            String key = sbn.getKey();

            // Build JSON payload
            JSONObject json = new JSONObject();
            json.put("key", key);
            json.put("title", title);
            json.put("body", body);
            json.put("pkg", pkg);
            json.put("time", now);

            String message = "NOTIF_MIRROR:" + json.toString();

            sendToPC(message);
            Log.i(TAG, "Mirrored: [" + pkg + "] " + title);

        } catch (Exception e) {
            Log.e(TAG, "Error mirroring notification: " + e.getMessage());
        }
    }

    // ─── NOTIFICATION REMOVED ──────────────────────────────────

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (serverIp == null) return;

        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName())) return;

        try {
            String key = sbn.getKey();
            sendToPC("NOTIF_DISMISSED:" + key);
            Log.i(TAG, "Notification dismissed on phone: " + key);
        } catch (Exception e) {
            Log.e(TAG, "Error sending dismiss: " + e.getMessage());
        }
    }

    // ─── DISMISS FROM PC ───────────────────────────────────────

    /**
     * Called by ReverseCommandListener when the PC requests a notification dismissal.
     */
    public static void dismissNotification(String key) {
        if (instance != null) {
            try {
                instance.cancelNotification(key);
                Log.i(TAG, "Dismissed notification from PC: " + key);
            } catch (Exception e) {
                Log.e(TAG, "Dismiss error: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "Cannot dismiss — service not running or no notification access");
        }
    }

    /**
     * Check if the service is running and has notification access.
     */
    public static boolean isServiceRunning() {
        return instance != null;
    }

    // ─── SEND TO PC ────────────────────────────────────────────

    private void sendToPC(String message) {
        if (serverIp == null) return;

        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = message.getBytes("UTF-8");
                InetAddress address = InetAddress.getByName(serverIp);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, PC_COMMAND_PORT);
                socket.send(packet);
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "Send to PC error: " + e.getMessage());
            }
        }).start();
    }
}
