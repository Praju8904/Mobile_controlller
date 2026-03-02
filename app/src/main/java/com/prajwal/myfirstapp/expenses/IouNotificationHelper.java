package com.prajwal.myfirstapp.expenses;


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

import java.util.ArrayList;

public class IouNotificationHelper extends BroadcastReceiver {

    private static final String CHANNEL_ID = "iou_reminders";

    public static final String ACTION_IOU_OVERDUE  = "com.prajwal.myfirstapp.ACTION_IOU_OVERDUE";
    public static final String ACTION_IOU_REMINDER = "com.prajwal.myfirstapp.ACTION_IOU_REMINDER";
    public static final String ACTION_IOU_SETTLED  = "com.prajwal.myfirstapp.ACTION_IOU_SETTLED";
    public static final String ACTION_CHECK_IOU    = "com.prajwal.myfirstapp.ACTION_CHECK_IOU";

    public static final String EXTRA_RECORD_ID   = "iou_record_id";
    public static final String EXTRA_PERSON_NAME = "iou_person_name";
    public static final String EXTRA_AMOUNT      = "iou_amount";
    public static final String EXTRA_CURRENCY    = "iou_currency";

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);
        String action = intent.getAction();
        if (action == null) action = ACTION_CHECK_IOU;

        switch (action) {
            case ACTION_IOU_OVERDUE:
                handleOverdue(context, intent);
                break;
            case ACTION_IOU_REMINDER:
                handleReminder(context, intent);
                break;
            case ACTION_IOU_SETTLED:
                handleSettled(context, intent);
                break;
            case ACTION_CHECK_IOU:
            default:
                checkAndFireOverdueNotifications(context);
                break;
        }
    }

    // ─── Static send helpers ─────────────────────────────────

    public static void sendOverdueNotification(Context context, MoneyRecord record) {
        createNotificationChannel(context);
        String title = "⚠️ Overdue IOU";
        String body;
        if (MoneyRecord.TYPE_LENT.equals(record.type)) {
            body = record.personName + " was supposed to return "
                    + record.currency + String.format("%.0f", record.getOutstandingAmount())
                    + " — it's overdue!";
        } else {
            body = "You were supposed to return "
                    + record.currency + String.format("%.0f", record.getOutstandingAmount())
                    + " to " + record.personName + " — it's overdue!";
        }

        Intent tap = new Intent(context, BorrowLendActivity.class);
        tap.putExtra(EXTRA_RECORD_ID, record.id);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(context,
                ("iou_overdue_" + record.id).hashCode() & 0x7FFFFFFF,
                tap, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(0xFFEF4444);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("iou_od_" + record.id).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    public static void sendReminderNotification(Context context, MoneyRecord record) {
        createNotificationChannel(context);
        long daysAgo = (System.currentTimeMillis() - record.date) / (1000L * 60 * 60 * 24);
        String title = "💸 IOU Reminder";
        String body;
        if (MoneyRecord.TYPE_LENT.equals(record.type)) {
            body = "Reminder: You lent " + record.currency
                    + String.format("%.0f", record.getOutstandingAmount())
                    + " to " + record.personName + " " + daysAgo + " day(s) ago — still outstanding.";
        } else {
            body = "Reminder: You borrowed " + record.currency
                    + String.format("%.0f", record.getOutstandingAmount())
                    + " from " + record.personName + " " + daysAgo + " day(s) ago — still outstanding.";
        }

        Intent tap = new Intent(context, MoneyRecordDetailActivity.class);
        tap.putExtra(EXTRA_RECORD_ID, record.id);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(context,
                ("iou_remind_" + record.id).hashCode() & 0x7FFFFFFF,
                tap, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(0xFFF59E0B);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("iou_rem_" + record.id).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    public static void sendSettledNotification(Context context, MoneyRecord record) {
        createNotificationChannel(context);
        String body = "✅ " + record.currency
                + String.format("%.0f", record.amount)
                + (MoneyRecord.TYPE_LENT.equals(record.type)
                    ? " from " + record.personName + " has been settled!"
                    : " to " + record.personName + " has been settled!");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("IOU Settled!")
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setColor(0xFF22C55E);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(("iou_settled_" + record.id).hashCode() & 0x7FFFFFFF, builder.build());
        }
    }

    /** Check all records, update overdue statuses, fire notifications. */
    public static void checkAndFireOverdueNotifications(Context context) {
        MoneyRecordRepository repo = new MoneyRecordRepository(context);
        repo.updateOverdueStatuses();

        ArrayList<MoneyRecord> overdue = repo.getOverdueRecords();
        for (MoneyRecord r : overdue) {
            if (MoneyRecord.STATUS_OVERDUE.equals(r.status)) {
                sendOverdueNotification(context, r);
            }
        }

        // Fire reminder notifications for reminder-enabled active records
        long nowMs = System.currentTimeMillis();
        long oneDayMs = 1000L * 60 * 60 * 24;
        for (MoneyRecord r : repo.loadAll()) {
            if (r.reminderEnabled && !MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                if (r.reminderFrequencyDays > 0) {
                    // Use updatedAt as the last interaction point; compare days elapsed
                    long lastCheck = Math.max(r.createdAt, r.updatedAt);
                    long daysSinceLast = (nowMs - lastCheck) / oneDayMs;
                    if (daysSinceLast >= r.reminderFrequencyDays) {
                        sendReminderNotification(context, r);
                    }
                }
            }
        }
    }

    /** Schedule a daily repeating alarm for IOU checks. */
    public static void schedulePeriodicCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, IouNotificationHelper.class);
        intent.setAction(ACTION_CHECK_IOU);

        PendingIntent pi = PendingIntent.getBroadcast(context,
                88001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long intervalMs = 24 * 60 * 60 * 1000L;
        long triggerAt = System.currentTimeMillis() + intervalMs;

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                intervalMs,
                pi);
    }

    // ─── Private handlers ────────────────────────────────────

    private void handleOverdue(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_RECORD_ID);
        if (id == null) return;
        MoneyRecordRepository repo = new MoneyRecordRepository(context);
        MoneyRecord record = repo.getById(id);
        if (record != null) sendOverdueNotification(context, record);
    }

    private void handleReminder(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_RECORD_ID);
        if (id == null) return;
        MoneyRecordRepository repo = new MoneyRecordRepository(context);
        MoneyRecord record = repo.getById(id);
        if (record != null) sendReminderNotification(context, record);
    }

    private void handleSettled(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_RECORD_ID);
        if (id == null) return;
        MoneyRecordRepository repo = new MoneyRecordRepository(context);
        MoneyRecord record = repo.getById(id);
        if (record != null) sendSettledNotification(context, record);
    }

    // ─── Channel setup ───────────────────────────────────────

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "IOU Reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for outstanding IOUs and debt reminders");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }
    }
}
