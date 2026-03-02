package com.prajwal.myfirstapp.timecapsule;


import com.prajwal.myfirstapp.R;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  TIME CAPSULE NOTIFICATION RECEIVER — Notifies users when time capsules are ready
 *  to be opened. Triggered by AlarmManager on the capsule's open date.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class TimeCapsuleNotificationReceiver extends BroadcastReceiver {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static final String CHANNEL_ID = "time_capsule_channel";
    private static final String CHANNEL_NAME = "Time Capsule Notifications";
    private static final int NOTIFICATION_ID = 9901;

    public static final String ACTION_CHECK_CAPSULES = "com.prajwal.myfirstapp.ACTION_CHECK_CAPSULES";
    public static final String EXTRA_CAPSULE_ID = "capsule_id";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RECEIVER
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onReceive(Context context, Intent intent) {
        TimeCapsuleManager manager = new TimeCapsuleManager(context);
        List<TimeCapsuleManager.TimeCapsule> readyCapsules = manager.getReadyCapsules();

        if (readyCapsules.isEmpty()) return;

        createNotificationChannel(context);

        // Build notification
        String title = "🎁 Time Capsule Ready!";
        String message;
        if (readyCapsules.size() == 1) {
            message = "A time capsule is ready to be opened!";
        } else {
            message = readyCapsules.size() + " time capsules are ready to be opened!";
        }

        // Intent to open TimeCapsuleActivity
        Intent openIntent = new Intent(context, TimeCapsuleActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTIFICATION CHANNEL
    // ═══════════════════════════════════════════════════════════════════════════════

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications when time capsules are ready to open");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ALARM SCHEDULING — Call from TimeCapsuleManager or activity
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Schedule a notification for when a time capsule becomes ready.
     */
    public static void scheduleNotification(Context context, String capsuleId, long openDateMillis) {
        Intent intent = new Intent(context, TimeCapsuleNotificationReceiver.class);
        intent.setAction(ACTION_CHECK_CAPSULES);
        intent.putExtra(EXTRA_CAPSULE_ID, capsuleId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                capsuleId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.AlarmManager alarmManager =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, openDateMillis, pendingIntent);
        }
    }
}
