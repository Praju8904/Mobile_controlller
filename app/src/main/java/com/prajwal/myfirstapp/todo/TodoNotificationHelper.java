package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Helper for scheduling and showing to-do item reminder notifications.
 *
 * Supports:
 *   - Scheduling an exact alarm for an item's reminderDateTime
 *   - Canceling a scheduled alarm by item id
 *   - Showing a rich reminder notification with action buttons
 *   - Showing an overdue notification
 */
public class TodoNotificationHelper {

    private static final String TAG = "TodoNotifHelper";

    public static final String CHANNEL_ID = "todo_reminders";

    // Action constants used by TodoReminderReceiver
    public static final String ACTION_TODO_COMPLETE  = "com.prajwal.myfirstapp.TODO_COMPLETE";
    public static final String ACTION_TODO_SNOOZE_15 = "com.prajwal.myfirstapp.TODO_SNOOZE_15";

    // Extras
    public static final String EXTRA_ITEM_ID    = "item_id";
    public static final String EXTRA_ITEM_TITLE = "item_title";
    public static final String EXTRA_LIST_NAME  = "list_name";

    // ─── Notification Channel ────────────────────────────────────

    /** Creates the "To-Do Reminders" notification channel (no-op below API 26). */
    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "To-Do Reminders", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminders for to-do items with due dates");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }

    // ─── Schedule / Cancel Alarm ─────────────────────────────────

    /**
     * Schedules an exact alarm for item.reminderDateTime.
     * Does nothing if reminderDateTime is 0 or already in the past.
     */
    public static void scheduleReminder(Context ctx, TodoItem item) {
        if (item == null || item.reminderDateTime <= 0) return;
        if (item.reminderDateTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Reminder time is in the past for item: " + item.id);
            return;
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(ctx, TodoReminderReceiver.class);
        intent.setAction(TodoReminderReceiver.ACTION_TODO_REMINDER);
        intent.putExtra(EXTRA_ITEM_ID, item.id);
        intent.putExtra(EXTRA_ITEM_TITLE, item.title);

        int requestCode = item.id.hashCode() & 0x7FFFFFFF;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.reminderDateTime, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, item.reminderDateTime, pi);
                Log.w(TAG, "Exact alarms not permitted, using inexact for: " + item.title);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.reminderDateTime, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, item.reminderDateTime, pi);
        }

        Log.i(TAG, "Scheduled reminder for '" + item.title + "' at " + item.reminderDateTime);
    }

    /** Cancels the alarm previously scheduled for the given item id. */
    public static void cancelReminder(Context ctx, String itemId) {
        if (itemId == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(ctx, TodoReminderReceiver.class);
        intent.setAction(TodoReminderReceiver.ACTION_TODO_REMINDER);

        int requestCode = itemId.hashCode() & 0x7FFFFFFF;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);
        am.cancel(pi);

        Log.i(TAG, "Cancelled reminder for item: " + itemId);
    }

    // ─── Show Notifications ──────────────────────────────────────

    /**
     * Shows a reminder notification for a to-do item.
     *
     * Title:   "[title] is due"
     * Text:    "From: [listName]"
     * Actions: "Mark Complete" and "Snooze 15min"
     * Tap:     Opens TodoItemDetailActivity
     */
    public static void showReminderNotification(Context ctx, String itemId, String title,
                                                String listName) {
        createChannel(ctx);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        // Tap intent → TodoItemDetailActivity
        Intent detailIntent = new Intent(ctx, TodoItemDetailActivity.class);
        detailIntent.putExtra(EXTRA_ITEM_ID, itemId);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                ctx, ("todo_detail_" + itemId).hashCode(), detailIntent, pendingFlags);

        // Action: Mark Complete
        Intent completeIntent = new Intent(ctx, TodoReminderReceiver.class);
        completeIntent.setAction(ACTION_TODO_COMPLETE);
        completeIntent.putExtra(EXTRA_ITEM_ID, itemId);
        PendingIntent completePi = PendingIntent.getBroadcast(
                ctx, ("todo_complete_" + itemId).hashCode(), completeIntent, pendingFlags);

        // Action: Snooze 15 min
        Intent snoozeIntent = new Intent(ctx, TodoReminderReceiver.class);
        snoozeIntent.setAction(ACTION_TODO_SNOOZE_15);
        snoozeIntent.putExtra(EXTRA_ITEM_ID, itemId);
        snoozeIntent.putExtra(EXTRA_ITEM_TITLE, title);
        PendingIntent snoozePi = PendingIntent.getBroadcast(
                ctx, ("todo_snooze_" + itemId).hashCode(), snoozeIntent, pendingFlags);

        String safeTitle    = title    != null ? title    : "To-Do Item";
        String safeListName = listName != null ? listName : "";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(safeTitle + " is due")
                .setContentText("From: " + safeListName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentPi)
                .addAction(android.R.drawable.checkbox_on_background, "Mark Complete", completePi)
                .addAction(android.R.drawable.ic_popup_reminder, "Snooze 15min", snoozePi);

        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(getItemNotifId(itemId), builder.build());
        }
    }

    /**
     * Shows an overdue notification for a to-do item.
     *
     * Title: "⚠️ Overdue: [title]"
     * Text:  "[daysOverdue] day(s) past due"
     */
    public static void showOverdueNotification(Context ctx, String itemId, String title,
                                               int daysOverdue) {
        createChannel(ctx);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        Intent detailIntent = new Intent(ctx, TodoItemDetailActivity.class);
        detailIntent.putExtra(EXTRA_ITEM_ID, itemId);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                ctx, ("todo_overdue_" + itemId).hashCode(), detailIntent, pendingFlags);

        String safeTitle = title != null ? title : "To-Do Item";
        String bodyText  = daysOverdue == 1
                ? "1 day past due"
                : daysOverdue + " days past due";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ Overdue: " + safeTitle)
                .setContentText(bodyText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentPi);

        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int notifId = ("overdue_" + itemId).hashCode() & 0x7FFFFFFF;
            nm.notify(notifId, builder.build());
        }
    }

    // ─── Shared Notification ID ──────────────────────────────────

    /** Returns the notification ID used for the primary reminder notification for an item. */
    static int getItemNotifId(String itemId) {
        return itemId != null ? itemId.hashCode() & 0x7FFFFFFF : 0;
    }
}
