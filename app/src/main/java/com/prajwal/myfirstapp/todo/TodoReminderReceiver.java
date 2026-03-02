package com.prajwal.myfirstapp.todo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BroadcastReceiver for to-do item reminder alarms and notification action buttons.
 *
 * Supported actions:
 *   - ACTION_TODO_REMINDER  : Show a reminder notification for the item.
 *   - ACTION_TODO_COMPLETE  : Mark the item complete via TodoRepository.
 *   - ACTION_TODO_SNOOZE_15 : Reschedule the alarm 15 minutes from now.
 */
public class TodoReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "TodoReminderReceiver";

    public static final String ACTION_TODO_REMINDER  = "com.prajwal.myfirstapp.TODO_REMINDER";
    public static final String ACTION_TODO_COMPLETE  = "com.prajwal.myfirstapp.TODO_COMPLETE";
    public static final String ACTION_TODO_SNOOZE_15 = "com.prajwal.myfirstapp.TODO_SNOOZE_15";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        String itemId    = intent.getStringExtra(TodoNotificationHelper.EXTRA_ITEM_ID);
        String itemTitle = intent.getStringExtra(TodoNotificationHelper.EXTRA_ITEM_TITLE);

        switch (action) {
            case ACTION_TODO_REMINDER:
                handleReminder(context, itemId, itemTitle);
                break;

            case ACTION_TODO_COMPLETE:
                handleComplete(context, itemId);
                break;

            case ACTION_TODO_SNOOZE_15:
                handleSnooze15(context, itemId, itemTitle);
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }
    }

    // ─── Action Handlers ─────────────────────────────────────────

    private void handleReminder(Context context, String itemId, String itemTitle) {
        if (itemId == null) {
            Log.w(TAG, "ACTION_TODO_REMINDER received with null itemId");
            return;
        }

        // Look up the list name for the notification body
        TodoRepository repo = new TodoRepository(context);
        TodoItem item = repo.getItemById(itemId);
        String listName = "";
        if (item != null && item.listId != null) {
            TodoList list = repo.getListById(item.listId);
            if (list != null) listName = list.title;
        }

        String title = (item != null && item.title != null) ? item.title
                : (itemTitle != null ? itemTitle : "To-Do Item");

        TodoNotificationHelper.showReminderNotification(context, itemId, title, listName);
        Log.i(TAG, "Showed reminder notification for item: " + itemId);
    }

    private void handleComplete(Context context, String itemId) {
        if (itemId == null) {
            Log.w(TAG, "ACTION_TODO_COMPLETE received with null itemId");
            return;
        }
        TodoRepository repo = new TodoRepository(context);
        repo.completeItem(itemId);
        Log.i(TAG, "Marked item complete from notification: " + itemId);

        // Dismiss the notification
        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(TodoNotificationHelper.getItemNotifId(itemId));
    }

    private void handleSnooze15(Context context, String itemId, String itemTitle) {
        if (itemId == null) {
            Log.w(TAG, "ACTION_TODO_SNOOZE_15 received with null itemId");
            return;
        }

        long snoozeTime = System.currentTimeMillis() + 15 * 60 * 1000L;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TodoReminderReceiver.class);
        intent.setAction(ACTION_TODO_REMINDER);
        intent.putExtra(TodoNotificationHelper.EXTRA_ITEM_ID, itemId);
        if (itemTitle != null) intent.putExtra(TodoNotificationHelper.EXTRA_ITEM_TITLE, itemTitle);

        int requestCode = ("snooze_" + itemId).hashCode() & 0x7FFFFFFF;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pi);
        }

        // Dismiss the current notification
        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(TodoNotificationHelper.getItemNotifId(itemId));

        Log.i(TAG, "Snoozed item " + itemId + " for 15 minutes");
    }
}
