package com.prajwal.myfirstapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Re-schedules all pending task alarms after device reboot.
 * 
 * AlarmManager alarms are lost on reboot, so this receiver
 * reloads tasks from SharedPreferences and re-creates alarms
 * for any incomplete tasks that have a future due_date/due_time.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Device booted — rescheduling task reminders");

        // Use new TaskNotificationHelper for v2 tasks
        try {
            TaskNotificationHelper.rescheduleAllReminders(context);
            Log.i(TAG, "Rescheduled v2 task reminders");
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling v2 task alarms: " + e.getMessage());
        }

        // Legacy fallback: reschedule old-format tasks if any exist
        SharedPreferences prefs = context.getSharedPreferences("task_manager_prefs", Context.MODE_PRIVATE);
        String json = prefs.getString("tasks_json", "[]");

        try {
            JSONArray tasks = new JSONArray(json);
            int scheduled = 0;

            for (int i = 0; i < tasks.length(); i++) {
                JSONObject obj = tasks.getJSONObject(i);

                if (obj.optBoolean("completed", false)) continue;

                String dueDate = obj.optString("due_date", null);
                String dueTime = obj.optString("due_time", null);
                if (dueDate == null || "null".equals(dueDate)) continue;
                if (dueTime == null || "null".equals(dueTime)) continue;

                long taskId = obj.optLong("id", -1);
                String title = obj.optString("title", "Task");

                Calendar cal = TaskAlarmHelper.parseDateTime(dueDate, dueTime);
                if (cal == null) continue;

                // Only schedule if in the future
                if (cal.getTimeInMillis() > System.currentTimeMillis()) {
                    TaskAlarmHelper.scheduleAlarm(context, taskId, title, dueDate, dueTime, cal);
                    scheduled++;
                }
            }

            Log.i(TAG, "Rescheduled " + scheduled + " task reminder(s)");

        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling task alarms: " + e.getMessage());
        }

        // Also reschedule note reminders
        try {
            NoteReminderManager noteReminderManager = new NoteReminderManager(context);
            noteReminderManager.rescheduleAllReminders();
            Log.i(TAG, "Rescheduled note reminders");
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling note reminders: " + e.getMessage());
        }

        // Reschedule subscription reminders & expense periodic checks
        try {
            ExpenseNotificationHelper.rescheduleAllSubscriptionReminders(context);
            ExpenseNotificationHelper.schedulePeriodicCheck(context);
            Log.i(TAG, "Rescheduled subscription reminders and periodic expense checks");
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling expense notifications: " + e.getMessage());
        }

        // Reschedule calendar event reminders & daily agenda
        try {
            CalendarNotificationHelper.rescheduleAllReminders(context);
            Log.i(TAG, "Rescheduled calendar event reminders");
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling calendar notifications: " + e.getMessage());
        }
    }
}
