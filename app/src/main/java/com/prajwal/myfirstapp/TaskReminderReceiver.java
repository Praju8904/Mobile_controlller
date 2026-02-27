package com.prajwal.myfirstapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * BroadcastReceiver that fires when an AlarmManager alarm triggers
 * for a task reminder, overdue alert, or recurring notification.
 *
 * Supports multiple notification types via TaskNotificationHelper:
 *   - TYPE_REMINDER:  Standard reminder at a specific time
 *   - TYPE_OVERDUE:   Alert when a task passes its due time
 *   - TYPE_RECURRING: Recurring task notifications (daily/weekly/monthly)
 *
 * Shows a high-priority notification and vibrates the device.
 */
public class TaskReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "TaskReminder";
    private static final String CHANNEL_ID = "task_reminders";
    private static final String CHANNEL_OVERDUE_ID = "task_overdue";

    // Legacy extras (kept for backward compatibility)
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_TASK_TITLE = "task_title";
    public static final String EXTRA_TASK_DUE_TIME = "task_due_time";

    // Action constants for notification buttons
    public static final String ACTION_MARK_COMPLETE = "com.prajwal.myfirstapp.ACTION_MARK_COMPLETE";
    public static final String ACTION_SNOOZE_15 = "com.prajwal.myfirstapp.ACTION_SNOOZE_15";
    public static final String ACTION_SNOOZE_1H = "com.prajwal.myfirstapp.ACTION_SNOOZE_1H";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // ── Handle notification action buttons ──
        if (ACTION_MARK_COMPLETE.equals(action)) {
            String completeId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID);
            if (completeId != null) {
                TaskRepository repo = new TaskRepository(context);
                repo.completeTask(completeId);
                Log.i(TAG, "Marked complete from notification: " + completeId);
                // Dismiss the notification
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(completeId.hashCode() & 0x7FFFFFFF);
            }
            return;
        }
        if (ACTION_SNOOZE_15.equals(action) || ACTION_SNOOZE_1H.equals(action)) {
            String snoozeId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID);
            String snoozeTitle = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_TITLE);
            if (snoozeId != null) {
                long delayMs = ACTION_SNOOZE_15.equals(action) ? 15 * 60 * 1000L : 60 * 60 * 1000L;
                TaskNotificationHelper.scheduleSnoozedReminder(context, snoozeId, snoozeTitle, delayMs);
                Log.i(TAG, "Snoozed task " + snoozeId + " for " + (delayMs / 60000) + " min");
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(snoozeId.hashCode() & 0x7FFFFFFF);
            }
            return;
        }

        // ── Standard notification flow ──
        // Read new-style extras (String id from TaskNotificationHelper)
        String taskId = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_ID);
        String title = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_TITLE);
        String dueTime = intent.getStringExtra(TaskNotificationHelper.EXTRA_TASK_DUE_TIME);
        String notifType = intent.getStringExtra(TaskNotificationHelper.EXTRA_NOTIF_TYPE);
        String recurrence = intent.getStringExtra(TaskNotificationHelper.EXTRA_RECURRENCE);

        // Fallback: legacy long-based task_id
        if (taskId == null) {
            long legacyId = intent.getLongExtra(EXTRA_TASK_ID, -1);
            if (legacyId >= 0) taskId = String.valueOf(legacyId);
        }
        if (title == null) title = intent.getStringExtra(EXTRA_TASK_TITLE);
        if (dueTime == null) dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME);
        if (notifType == null) notifType = TaskNotificationHelper.TYPE_REMINDER;

        if (title == null || title.isEmpty()) title = "Task Due";

        Log.i(TAG, "Notification fired: type=" + notifType + " task='" + title + "' id=" + taskId);

        createNotificationChannels(context);

        // Build notification based on type
        String notifTitle;
        String notifBody;
        String channelId;
        int icon;

        switch (notifType) {
            case TaskNotificationHelper.TYPE_OVERDUE:
                notifTitle = "⚠️ Task Overdue";
                notifBody = title + " — was due and is now overdue!";
                channelId = CHANNEL_OVERDUE_ID;
                icon = android.R.drawable.ic_dialog_alert;
                break;

            case TaskNotificationHelper.TYPE_RECURRING:
                String recurrenceLabel = "";
                if (recurrence != null) {
                    switch (recurrence) {
                        case Task.RECURRENCE_DAILY:   recurrenceLabel = "Daily"; break;
                        case Task.RECURRENCE_WEEKLY:  recurrenceLabel = "Weekly"; break;
                        case Task.RECURRENCE_MONTHLY: recurrenceLabel = "Monthly"; break;
                        default: recurrenceLabel = "Recurring"; break;
                    }
                }
                notifTitle = "🔁 " + recurrenceLabel + " Task";
                notifBody = title + (dueTime != null && !dueTime.isEmpty() ? " — due at " + dueTime : "");
                channelId = CHANNEL_ID;
                icon = android.R.drawable.ic_dialog_info;

                // Schedule next occurrence
                scheduleNextRecurrence(context, taskId);
                break;

            default: // TYPE_REMINDER
                notifTitle = "🔔 Task Reminder";
                String body = "Due now";
                if (dueTime != null && !dueTime.isEmpty()) body = "Due at " + dueTime;
                notifBody = title + " — " + body;
                channelId = CHANNEL_ID;
                icon = android.R.drawable.ic_dialog_alert;
                break;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

        // Deep-link: tap notification → open TaskDetailActivity
        if (taskId != null) {
            Intent detailIntent = new Intent(context, TaskDetailActivity.class);
            detailIntent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId);
            detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPi = PendingIntent.getActivity(context,
                    ("detail_" + taskId).hashCode(), detailIntent, pendingFlags);
            builder.setContentIntent(contentPi);

            // Action: Mark Complete
            if (!TaskNotificationHelper.TYPE_RECURRING.equals(notifType)) {
                Intent completeIntent = new Intent(context, TaskReminderReceiver.class);
                completeIntent.setAction(ACTION_MARK_COMPLETE);
                completeIntent.putExtra(TaskNotificationHelper.EXTRA_TASK_ID, taskId);
                PendingIntent completePi = PendingIntent.getBroadcast(context,
                        ("complete_" + taskId).hashCode(), completeIntent, pendingFlags);
                builder.addAction(android.R.drawable.checkbox_on_background, "Complete", completePi);
            }

            // Action: Snooze 15 min
            Intent snooze15 = new Intent(context, TaskReminderReceiver.class);
            snooze15.setAction(ACTION_SNOOZE_15);
            snooze15.putExtra(TaskNotificationHelper.EXTRA_TASK_ID, taskId);
            snooze15.putExtra(TaskNotificationHelper.EXTRA_TASK_TITLE, title);
            PendingIntent snooze15Pi = PendingIntent.getBroadcast(context,
                    ("snooze15_" + taskId).hashCode(), snooze15, pendingFlags);
            builder.addAction(android.R.drawable.ic_popup_reminder, "Snooze 15m", snooze15Pi);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int notifId = taskId != null ? (taskId.hashCode() & 0x7FFFFFFF) : (int) System.currentTimeMillis();
            nm.notify(notifId, builder.build());
        }

        // Vibrate
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            int duration = TaskNotificationHelper.TYPE_OVERDUE.equals(notifType) ? 800 : 500;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    /**
     * When a recurring task fires, schedule its next occurrence.
     */
    private void scheduleNextRecurrence(Context context, String taskId) {
        if (taskId == null) return;
        try {
            TaskRepository repo = new TaskRepository(context);
            Task task = repo.getTaskById(taskId);
            if (task != null && task.isRecurring() && !task.isCompleted() && !task.isTrashed) {
                TaskNotificationHelper.scheduleTaskReminders(context, task);
                Log.i(TAG, "Scheduled next recurrence for: " + task.title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling next recurrence: " + e.getMessage());
        }
    }

    private void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_HIGH);
            reminderChannel.setDescription("Reminders for tasks with due dates");
            reminderChannel.enableVibration(true);
            nm.createNotificationChannel(reminderChannel);

            NotificationChannel overdueChannel = new NotificationChannel(
                    CHANNEL_OVERDUE_ID, "Overdue Tasks", NotificationManager.IMPORTANCE_HIGH);
            overdueChannel.setDescription("Alerts for overdue tasks");
            overdueChannel.enableVibration(true);
            nm.createNotificationChannel(overdueChannel);
        }
    }
}
