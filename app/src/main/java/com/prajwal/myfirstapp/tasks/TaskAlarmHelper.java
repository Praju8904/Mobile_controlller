package com.prajwal.myfirstapp.tasks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Shared helper for scheduling / cancelling task reminder alarms.
 * Used by TaskManagerActivity (when tasks are created/synced) and
 * BootReceiver (to reschedule after reboot).
 */
public class TaskAlarmHelper {

    private static final String TAG = "TaskAlarmHelper";

    /**
     * Schedule an exact alarm for a task's due date/time.
     */
    public static void scheduleAlarm(Context context, long taskId, String title,
                                     String dueDate, String dueTime, Calendar alarmTime) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_DUE_TIME, dueTime);

        int requestCode = (int) (taskId % Integer.MAX_VALUE);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerMs = alarmTime.getTimeInMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: check canScheduleExactAlarms
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                // Fallback to inexact alarm
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                Log.w(TAG, "Exact alarms not permitted, using inexact for task: " + title);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }

        Log.i(TAG, "Alarm scheduled for task '" + title + "' at " + dueDate + " " + dueTime);
    }

    /**
     * Cancel an existing alarm for a task.
     */
    public static void cancelAlarm(Context context, long taskId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        int requestCode = (int) (taskId % Integer.MAX_VALUE);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.cancel(pi);
        Log.i(TAG, "Alarm cancelled for task id=" + taskId);
    }

    /**
     * Parse "YYYY-MM-DD" and "HH:MM" into a Calendar, or null if invalid.
     */
    public static Calendar parseDateTime(String dueDate, String dueTime) {
        try {
            String[] dateParts = dueDate.split("-");
            String[] timeParts = dueTime.split(":");

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, Integer.parseInt(dateParts[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(dateParts[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateParts[2]));
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
            cal.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse date/time: " + dueDate + " " + dueTime);
            return null;
        }
    }
}
