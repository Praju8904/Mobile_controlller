package com.prajwal.myfirstapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE REMINDER MANAGER — Schedules and manages note reminders via local notifications
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Schedule reminders at specific date/time
 * - Cancel existing reminders
 * - Deep link to note when notification tapped
 * - Notification channel setup for Android O+
 */
public class NoteReminderManager {

    private static final String TAG = "NoteReminderManager";
    public static final String CHANNEL_ID = "note_reminders";
    public static final String CHANNEL_NAME = "Note Reminders";

    private final Context context;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;

    public NoteReminderManager(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Schedule a reminder for a note
     */
    public void scheduleReminder(Note note) {
        if (note == null || note.reminderDateTime <= 0) {
            return;
        }

        long triggerTime = note.reminderDateTime;
        
        // Don't schedule if time already passed
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Reminder time already passed for note: " + note.id);
            return;
        }

        Intent intent = new Intent(context, NoteReminderReceiver.class);
        intent.setAction("com.prajwal.myfirstapp.NOTE_REMINDER");
        intent.putExtra(NoteReminderReceiver.EXTRA_NOTE_ID, note.id);
        intent.putExtra(NoteReminderReceiver.EXTRA_NOTE_TITLE, note.title);
        intent.putExtra(NoteReminderReceiver.EXTRA_NOTE_PREVIEW, note.plainTextPreview);

        // Use note ID hash as request code for unique pending intents
        int requestCode = note.id.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            Log.d(TAG, "Scheduled reminder for: " + sdf.format(new Date(triggerTime)));
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to schedule alarm - permission denied", e);
        }
    }

    /**
     * Cancel a scheduled reminder for a note
     */
    public void cancelReminder(Note note) {
        if (note == null) return;

        Intent intent = new Intent(context, NoteReminderReceiver.class);
        intent.setAction("com.prajwal.myfirstapp.NOTE_REMINDER");

        int requestCode = note.id.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled reminder for note: " + note.id);
        }

        // Also dismiss any existing notification
        notificationManager.cancel(requestCode);
    }

    /**
     * Reschedule all reminders (e.g., after device reboot)
     */
    public void rescheduleAllReminders() {
        NoteRepository repository = new NoteRepository(context);
        for (Note note : repository.getAllActiveNotes()) {
            if (note.reminderDateTime > System.currentTimeMillis()) {
                scheduleReminder(note);
            }
        }
        Log.d(TAG, "Rescheduled all active reminders");
    }

    /**
     * Check if a reminder is scheduled for a note
     */
    public boolean hasReminder(Note note) {
        if (note == null) return false;

        Intent intent = new Intent(context, NoteReminderReceiver.class);
        intent.setAction("com.prajwal.myfirstapp.NOTE_REMINDER");

        int requestCode = note.id.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        return pendingIntent != null;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTIFICATION CHANNEL
    // ═══════════════════════════════════════════════════════════════════════════════

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for your notes");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 100, 250});
            channel.enableLights(true);
            channel.setLightColor(0xFFF59E0B); // Amber

            notificationManager.createNotificationChannel(channel);
        }
    }
}
