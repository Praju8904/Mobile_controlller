package com.prajwal.myfirstapp.tasks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * Enhanced notification scheduling service for the Task Manager.
 *
 * Supports:
 *   - Single reminders per task
 *   - Multiple reminders per task
 *   - Recurring task notifications (daily/weekly/monthly)
 *   - Overdue task alerts
 *   - Rescheduling all alarms after boot
 *
 * Each reminder gets a unique request code derived from task ID + reminder index.
 * Recurring tasks schedule the next occurrence after each trigger.
 */
public class TaskNotificationHelper {

    private static final String TAG = "TaskNotifHelper";

    // Action constants for the receiver
    public static final String ACTION_REMINDER   = "com.prajwal.myfirstapp.TASK_REMINDER";
    public static final String ACTION_OVERDUE     = "com.prajwal.myfirstapp.TASK_OVERDUE";
    public static final String ACTION_RECURRING   = "com.prajwal.myfirstapp.TASK_RECURRING";

    // Extras
    public static final String EXTRA_TASK_ID       = "task_id";
    public static final String EXTRA_TASK_TITLE    = "task_title";
    public static final String EXTRA_TASK_DUE_TIME = "task_due_time";
    public static final String EXTRA_REMINDER_IDX  = "reminder_index";
    public static final String EXTRA_NOTIF_TYPE    = "notification_type";
    public static final String EXTRA_RECURRENCE    = "recurrence";

    public static final String TYPE_REMINDER  = "reminder";
    public static final String TYPE_OVERDUE   = "overdue";
    public static final String TYPE_RECURRING = "recurring";

    // Overdue check interval: 15 minutes
    private static final long OVERDUE_CHECK_INTERVAL = 15 * 60 * 1000;

    // ─── Schedule All Reminders for a Task ───────────────────────

    /**
     * Schedule all reminders for a given task (cancels existing ones first).
     */
    public static void scheduleTaskReminders(Context context, Task task) {
        if (task == null || task.isTrashed || task.isCompleted()) return;

        // Cancel any existing reminders first
        cancelTaskReminders(context, task);

        // Schedule explicit reminder timestamps
        if (task.reminderDateTimes != null && !task.reminderDateTimes.isEmpty()) {
            for (int i = 0; i < task.reminderDateTimes.size(); i++) {
                long reminderTime = task.reminderDateTimes.get(i);
                if (reminderTime > System.currentTimeMillis()) {
                    scheduleExactAlarm(context, task, reminderTime, i, TYPE_REMINDER);
                }
            }
        }

        // Schedule due date/time reminder (if no explicit reminders and has due date/time)
        if ((task.reminderDateTimes == null || task.reminderDateTimes.isEmpty())
                && task.hasDueDate() && task.hasDueTime()) {
            Calendar cal = TaskAlarmHelper.parseDateTime(task.dueDate, task.dueTime);
            if (cal != null && cal.getTimeInMillis() > System.currentTimeMillis()) {
                scheduleExactAlarm(context, task, cal.getTimeInMillis(), 0, TYPE_REMINDER);
            }
        }

        // Schedule recurring notifications
        if (task.isRecurring() && task.hasDueDate()) {
            scheduleRecurringAlarm(context, task);
        }
    }

    // ─── Schedule a Single Exact Alarm ───────────────────────────

    private static void scheduleExactAlarm(Context context, Task task, long triggerMs,
                                           int reminderIndex, String type) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.id);
        intent.putExtra(EXTRA_TASK_TITLE, task.title);
        intent.putExtra(EXTRA_TASK_DUE_TIME, task.dueTime != null ? task.dueTime : "");
        intent.putExtra(EXTRA_REMINDER_IDX, reminderIndex);
        intent.putExtra(EXTRA_NOTIF_TYPE, type);

        int requestCode = getRequestCode(task.id, reminderIndex);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                Log.w(TAG, "Exact alarms not permitted, using inexact for: " + task.title);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }

        Log.i(TAG, "Alarm scheduled for '" + task.title + "' [" + type + "] idx=" + reminderIndex);
    }

    // ─── Schedule Recurring Alarm ────────────────────────────────

    private static void scheduleRecurringAlarm(Context context, Task task) {
        if (!task.isRecurring() || !task.hasDueDate()) return;

        Calendar nextOccurrence = getNextRecurrenceDate(task);
        if (nextOccurrence == null || nextOccurrence.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.id);
        intent.putExtra(EXTRA_TASK_TITLE, task.title);
        intent.putExtra(EXTRA_TASK_DUE_TIME, task.dueTime != null ? task.dueTime : "");
        intent.putExtra(EXTRA_NOTIF_TYPE, TYPE_RECURRING);
        intent.putExtra(EXTRA_RECURRENCE, task.recurrence);

        int requestCode = getRecurringRequestCode(task.id);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerMs = nextOccurrence.getTimeInMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }

        Log.i(TAG, "Recurring alarm scheduled for '" + task.title + "' [" + task.recurrence + "]");
    }

    // ─── Get Next Recurrence Date ────────────────────────────────

    public static Calendar getNextRecurrenceDate(Task task) {
        if (!task.hasDueDate()) return null;
        try {
            String[] dateParts = task.dueDate.split("-");
            Calendar base = Calendar.getInstance();
            base.set(Integer.parseInt(dateParts[0]), Integer.parseInt(dateParts[1]) - 1, Integer.parseInt(dateParts[2]));
            if (task.hasDueTime()) {
                String[] timeParts = task.dueTime.split(":");
                base.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
                base.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
            } else {
                base.set(Calendar.HOUR_OF_DAY, 9);
                base.set(Calendar.MINUTE, 0);
            }
            base.set(Calendar.SECOND, 0);
            base.set(Calendar.MILLISECOND, 0);

            Calendar now = Calendar.getInstance();
            Calendar next = (Calendar) base.clone();

            // Advance until it's in the future
            while (!next.after(now)) {
                switch (task.recurrence) {
                    case Task.RECURRENCE_DAILY:
                        next.add(Calendar.DAY_OF_YEAR, 1);
                        break;
                    case Task.RECURRENCE_WEEKLY:
                        next.add(Calendar.WEEK_OF_YEAR, 1);
                        break;
                    case Task.RECURRENCE_MONTHLY:
                        next.add(Calendar.MONTH, 1);
                        break;
                    default:
                        return null; // Can't compute next for custom
                }
            }
            return next;
        } catch (Exception e) {
            Log.e(TAG, "Error computing next recurrence: " + e.getMessage());
            return null;
        }
    }

    // ─── Cancel All Reminders for a Task ─────────────────────────

    public static void cancelTaskReminders(Context context, Task task) {
        if (task == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Cancel up to 10 indexed reminders
        for (int i = 0; i < 10; i++) {
            Intent intent = new Intent(context, TaskReminderReceiver.class);
            int requestCode = getRequestCode(task.id, i);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            am.cancel(pi);
        }

        // Cancel recurring alarm
        Intent recurIntent = new Intent(context, TaskReminderReceiver.class);
        int recurCode = getRecurringRequestCode(task.id);
        PendingIntent recurPi = PendingIntent.getBroadcast(
                context, recurCode, recurIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(recurPi);

        // Cancel overdue alarm
        Intent overdueIntent = new Intent(context, TaskReminderReceiver.class);
        int overdueCode = getOverdueRequestCode(task.id);
        PendingIntent overduePi = PendingIntent.getBroadcast(
                context, overdueCode, overdueIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(overduePi);

        Log.i(TAG, "Cancelled all alarms for task: " + task.title);
    }

    // ─── Schedule Overdue Alert ──────────────────────────────────

    /**
     * Schedule an overdue alert: fires shortly after the due time passes.
     */
    public static void scheduleOverdueAlert(Context context, Task task) {
        if (task == null || !task.hasDueDate() || task.isCompleted() || task.isTrashed) return;

        Calendar dueTime;
        if (task.hasDueTime()) {
            dueTime = TaskAlarmHelper.parseDateTime(task.dueDate, task.dueTime);
        } else {
            String[] parts = task.dueDate.split("-");
            dueTime = Calendar.getInstance();
            dueTime.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]),
                    23, 59, 0);
        }

        if (dueTime == null) return;

        // Fire 5 minutes after due time
        long overdueMs = dueTime.getTimeInMillis() + (5 * 60 * 1000);
        if (overdueMs <= System.currentTimeMillis()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.id);
        intent.putExtra(EXTRA_TASK_TITLE, task.title);
        intent.putExtra(EXTRA_NOTIF_TYPE, TYPE_OVERDUE);

        int requestCode = getOverdueRequestCode(task.id);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, overdueMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, overdueMs, pi);
        }

        Log.i(TAG, "Overdue alert scheduled for '" + task.title + "'");
    }

    // ─── Reschedule All (after boot) ─────────────────────────────

    /**
     * Reschedule all reminders for all active tasks. Called by BootReceiver.
     */
    public static void rescheduleAllReminders(Context context) {
        TaskRepository repo = new TaskRepository(context);
        List<Task> active = repo.getActiveNonCompletedTasks();

        int scheduled = 0;
        for (Task task : active) {
            scheduleTaskReminders(context, task);
            scheduleOverdueAlert(context, task);
            scheduled++;
        }

        Log.i(TAG, "Rescheduled reminders for " + scheduled + " tasks after boot");
    }

    // ─── Request Code Generation ─────────────────────────────────

    /**
     * Generate a unique request code from task ID string + reminder index.
     * Uses hashCode to convert String ID to int, adds offset for index.
     */
    private static int getRequestCode(String taskId, int reminderIndex) {
        int base = taskId.hashCode() & 0x7FFFFFFF; // positive
        return (base + reminderIndex) & 0x7FFFFFFF;
    }

    private static int getRecurringRequestCode(String taskId) {
        int base = taskId.hashCode() & 0x7FFFFFFF;
        return (base + 100) & 0x7FFFFFFF;
    }

    private static int getOverdueRequestCode(String taskId) {
        int base = taskId.hashCode() & 0x7FFFFFFF;
        return (base + 200) & 0x7FFFFFFF;
    }

    // ─── Snooze Support ──────────────────────────────────────────

    /**
     * Schedule a snoozed reminder that will fire after the given delay.
     */
    public static void scheduleSnoozedReminder(Context context, String taskId, String title, long delayMs) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        intent.putExtra(EXTRA_TASK_TITLE, title != null ? title : "Task Reminder");
        intent.putExtra(EXTRA_NOTIF_TYPE, TYPE_REMINDER);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        int requestCode = (taskId.hashCode() & 0x7FFFFFFF + 300) & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long triggerAt = System.currentTimeMillis() + delayMs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
    }
}
