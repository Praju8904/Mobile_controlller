package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Manages file expiry reminders.
 *
 * Users can set an expiry date on any file ("Remind me to review this file on [date]").
 * On that date a notification is fired with Keep / Archive / Delete actions.
 *
 * The alarm is scheduled via {@link AlarmManager}. The {@link ExpiryReceiver} inner class
 * handles the broadcast and fires the notification.
 */
public class HubFileExpiryManager {

    private static final String CHANNEL_ID = "hub_expiry";
    private static final String EXTRA_FILE_ID = "file_id";
    private static final String EXTRA_FILE_NAME = "file_name";

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Schedules an expiry reminder for a file.
     *
     * @param context   application context
     * @param file      the file to remind about
     * @param reminderAt epoch-ms timestamp for the reminder
     */
    public static void schedule(Context context, HubFile file, long reminderAt) {
        file.expiryReminderAt = reminderAt;
        HubFileRepository.getInstance(context).updateFile(file);
        scheduleAlarm(context, file.id,
                file.displayName != null ? file.displayName : file.originalFileName,
                reminderAt);
    }

    /** Cancels a previously scheduled expiry reminder. */
    public static void cancel(Context context, HubFile file) {
        file.expiryReminderAt = 0;
        HubFileRepository.getInstance(context).updateFile(file);
        cancelAlarm(context, file.id);
    }

    // ─── AlarmManager scheduling ──────────────────────────────────────────────

    private static void scheduleAlarm(Context context, String fileId, String fileName, long atMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = buildIntent(context, fileId, fileName);
        PendingIntent pi = PendingIntent.getBroadcast(context,
                requestCode(fileId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, atMs, pi);
        }
    }

    private static void cancelAlarm(Context context, String fileId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(context,
                requestCode(fileId), buildIntent(context, fileId, ""),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static Intent buildIntent(Context context, String fileId, String fileName) {
        Intent i = new Intent(context, ExpiryReceiver.class);
        i.putExtra(EXTRA_FILE_ID, fileId);
        i.putExtra(EXTRA_FILE_NAME, fileName);
        return i;
    }

    private static int requestCode(String fileId) {
        return (fileId != null ? fileId.hashCode() : 0) & 0x7FFFFFFF;
    }

    // ─── BroadcastReceiver ────────────────────────────────────────────────────

    public static class ExpiryReceiver extends BroadcastReceiver {
        private static final int NOTIF_ID_BASE = 8200;

        @Override
        public void onReceive(Context context, Intent intent) {
            String fileId = intent.getStringExtra(EXTRA_FILE_ID);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            if (fileId == null || fileId.isEmpty()) return;

            createChannel(context);
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent openIntent = new Intent(context, HubFileViewerActivity.class);
            openIntent.putExtra("fileId", fileId);
            PendingIntent openPi = PendingIntent.getActivity(context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = "Time to review: " + (fileName != null ? fileName : "a file");
            String text = "Keep, Archive, or Delete?";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(openPi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            nm.notify(NOTIF_ID_BASE + Math.abs(fileId.hashCode() % 1000), builder.build());
        }

        private void createChannel(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "File Expiry Reminders",
                        NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManager nm = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
            }
        }
    }
}
