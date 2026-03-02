package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Weekly analytics report notification.
 *
 * Fires a weekly summary notification (default: Sunday at 7 PM) that recaps:
 *   • Files added this week
 *   • Storage change (+ / -)
 *   • Duplicates found
 *   • Top accessed file
 *
 * Tapping the notification opens {@link HubAnalyticsActivity}.
 *
 * Schedule is stored in SharedPreferences. Call {@link #schedule(Context, int, int)}
 * with the desired day of week and hour to configure.
 */
public class HubWeeklyReportReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "hub_weekly_report";
    private static final int NOTIF_ID = 8500;
    private static final int REQUEST_CODE = 8501;

    private static final String PREFS_NAME = "hub_weekly_report";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DAY = "day_of_week";     // Calendar.DAY_OF_WEEK
    private static final String KEY_HOUR = "hour_of_day";

    // Default: Sunday (1) at 19:00
    private static final int DEFAULT_DAY = Calendar.SUNDAY;
    private static final int DEFAULT_HOUR = 19;

    // ─── BroadcastReceiver ────────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isEnabled(context)) return;
        sendWeeklyNotification(context);
        // Reschedule for next week
        schedule(context, getDay(context), getHour(context));
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public static void schedule(Context context, int dayOfWeek, int hourOfDay) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_DAY, dayOfWeek)
                .putInt(KEY_HOUR, hourOfDay)
                .apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar next = nextOccurrence(dayOfWeek, hourOfDay);
        Intent intent = new Intent(context, HubWeeklyReportReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply();
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, HubWeeklyReportReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) am.cancel(pi);
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static int getDay(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DAY, DEFAULT_DAY);
    }

    public static int getHour(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_HOUR, DEFAULT_HOUR);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private static void sendWeeklyNotification(Context context) {
        createChannel(context);
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        HubFileRepository repo = HubFileRepository.getInstance(context);
        List<HubFile> recent = repo.getRecentlyAccessedFiles(1);

        long weekAgo = System.currentTimeMillis() - 7L * 86_400_000L;
        List<HubFile> all = repo.getAllFiles();
        int addedThisWeek = 0;
        for (HubFile f : all) if (f.importedAt > weekAgo) addedThisWeek++;

        int dupes = repo.getTotalDuplicateCount();
        String topFile = recent.isEmpty() ? "—"
                : (recent.get(0).displayName != null
                ? recent.get(0).displayName : recent.get(0).originalFileName);

        String title = "📊 Your Weekly File Report";
        String text = "Added " + addedThisWeek + " files this week • "
                + dupes + " duplicates • Top: " + topFile;

        Intent intent = new Intent(context, HubAnalyticsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(NOTIF_ID, builder.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Weekly File Report",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private static Calendar nextOccurrence(int dayOfWeek, int hourOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int daysUntil = (dayOfWeek - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (daysUntil == 0 && c.getTimeInMillis() <= System.currentTimeMillis()) daysUntil = 7;
        c.add(Calendar.DAY_OF_YEAR, daysUntil);
        return c;
    }
}
