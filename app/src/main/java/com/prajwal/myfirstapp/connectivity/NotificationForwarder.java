package com.prajwal.myfirstapp.connectivity;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class NotificationForwarder extends NotificationListenerService {

    private static final String TAG = "NotifForwarder";
    private ConnectionManager connectionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // We need the IP to send data. We'll grab it from SharedPreferences or similar global state.
        // For now, we assume ConnectionManager can be instantiated or we broadcast to MainActivity to send.
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isConnected()) return;

        try {
            String packageName = sbn.getPackageName();
            Bundle extras = sbn.getNotification().extras;

            // Get Title & Text
            String title = extras.getString(Notification.EXTRA_TITLE);
            CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
            String text = (textChar != null) ? textChar.toString() : "";

            // Filter out ongoing system notifications (like "USB debugging connected")
            if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                return;
            }

            // Don't mirror our own app notifications to avoid loops!
            if (packageName.equals(getPackageName())) return;

            if (title != null && !title.isEmpty()) {
                Log.i(TAG, "Mirroring: " + title);
                sendToPC(title, text, packageName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isConnected() {
        // Simple check: do we have a saved IP?
        // In a real app, check ConnectionManager.isConnected flag.
        SharedPreferences prefs = getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        return prefs.contains("last_ip");
    }

    private void sendToPC(String title, String body, String pkg) {
        // We use a broadcast to let MainActivity handle the networking (cleaner architecture)
        Intent intent = new Intent("com.prajwal.myfirstapp.SEND_NOTIF");
        intent.putExtra("data", "NOTIF_MIRROR:" + title + "|" + body + "|" + pkg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}