package com.prajwal.myfirstapp;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE REMINDER RECEIVER — Handles scheduled reminder broadcasts
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Shows notification when reminder fires
 * - Deep links to the specific note when tapped
 * - Mark as Done action button
 * - Snooze action button (15 min)
 */
public class NoteReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "NoteReminderReceiver";

    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_NOTE_TITLE = "note_title";
    public static final String EXTRA_NOTE_PREVIEW = "note_preview";

    public static final String ACTION_DISMISS = "com.prajwal.myfirstapp.DISMISS_REMINDER";
    public static final String ACTION_SNOOZE = "com.prajwal.myfirstapp.SNOOZE_REMINDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String noteId = intent.getStringExtra(EXTRA_NOTE_ID);
        String noteTitle = intent.getStringExtra(EXTRA_NOTE_TITLE);
        String notePreview = intent.getStringExtra(EXTRA_NOTE_PREVIEW);

        Log.d(TAG, "Received reminder broadcast: " + action + " for note: " + noteId);

        if (ACTION_DISMISS.equals(action)) {
            // Dismiss the notification
            dismissNotification(context, noteId);
            clearNoteReminder(context, noteId);
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            // Snooze for 15 minutes
            dismissNotification(context, noteId);
            snoozeReminder(context, noteId, noteTitle, notePreview);
            return;
        }

        // Default action - show the notification
        showNotification(context, noteId, noteTitle, notePreview);
    }

    private void showNotification(Context context, String noteId, String title, String preview) {
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = noteId != null ? noteId.hashCode() : (int) System.currentTimeMillis();

        // Content intent - opens the note
        Intent contentIntent = new Intent(context, NoteEditorActivity.class);
        contentIntent.putExtra("note_id", noteId);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Dismiss action
        Intent dismissIntent = new Intent(context, NoteReminderReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS);
        dismissIntent.putExtra(EXTRA_NOTE_ID, noteId);

        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Snooze action
        Intent snoozeIntent = new Intent(context, NoteReminderReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_NOTE_ID, noteId);
        snoozeIntent.putExtra(EXTRA_NOTE_TITLE, title);
        snoozeIntent.putExtra(EXTRA_NOTE_PREVIEW, preview);

        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, NoteReminderManager.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title != null ? title : "Note Reminder")
                .setContentText(preview != null ? preview : "You have a note reminder")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(preview != null ? preview : "You have a note reminder")
                        .setBigContentTitle(title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[]{0, 250, 100, 250})
                .setLights(Color.parseColor("#F59E0B"), 500, 500)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Done", dismissPendingIntent)
                .addAction(android.R.drawable.ic_popup_sync, "Snooze 15m", snoozePendingIntent);

        // Show notification
        notificationManager.notify(notificationId, builder.build());

        // Vibrate
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 100, 250}, -1));
            }
        }
    }

    private void dismissNotification(Context context, String noteId) {
        if (noteId == null) return;

        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(noteId.hashCode());
    }

    private void clearNoteReminder(Context context, String noteId) {
        if (noteId == null) return;

        NoteRepository repository = new NoteRepository(context);
        Note note = repository.getNoteById(noteId);

        if (note != null) {
            note.reminderDateTime = 0;
            repository.updateNote(note);
            Log.d(TAG, "Cleared reminder for note: " + noteId);
        }
    }

    private void snoozeReminder(Context context, String noteId, String title, String preview) {
        if (noteId == null) return;

        NoteRepository repository = new NoteRepository(context);
        Note note = repository.getNoteById(noteId);

        if (note != null) {
            // Set reminder for 15 minutes from now
            long snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000);
            note.reminderDateTime = snoozeTime;
            repository.updateNote(note);

            // Reschedule alarm
            NoteReminderManager reminderManager = new NoteReminderManager(context);
            reminderManager.scheduleReminder(note);

            Log.d(TAG, "Snoozed reminder for note: " + noteId + " to " + snoozeTime);
        }
    }
}
