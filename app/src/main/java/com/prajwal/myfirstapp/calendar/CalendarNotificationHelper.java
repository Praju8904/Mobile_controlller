package com.prajwal.myfirstapp.calendar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/**
 * Notification scheduling service for Calendar events.
 *
 * Supports:
 *   - Multiple reminders per event (each fires independently)
 *   - All-day event reminders (fires at 8am on the day by default)
 *   - Recurring event notification rescheduling
 *   - Birthday and anniversary yearly reminders
 *   - Notification deep linking into event detail screen
 *   - Action buttons: "View Event" and "Snooze 15 mins"
 *   - Fires even when app is closed (via AlarmManager + BroadcastReceiver)
 */
public class CalendarNotificationHelper {

    private static final String TAG = "CalendarNotifHelper";

    // Notification channel
    public static final String CHANNEL_ID = "calendar_reminders";
    public static final String CHANNEL_NAME = "Calendar Reminders";
    public static final String CHANNEL_DAILY = "calendar_daily_agenda";
    public static final String CHANNEL_DAILY_NAME = "Daily Agenda";
    public static final String CHANNEL_WEEKLY = "calendar_weekly_preview";
    public static final String CHANNEL_WEEKLY_NAME = "Weekly Preview";

    // Action constants for the receiver
    public static final String ACTION_EVENT_REMINDER = "com.prajwal.myfirstapp.CALENDAR_EVENT_REMINDER";
    public static final String ACTION_DAILY_AGENDA   = "com.prajwal.myfirstapp.CALENDAR_DAILY_AGENDA";
    public static final String ACTION_WEEKLY_PREVIEW = "com.prajwal.myfirstapp.CALENDAR_WEEKLY_PREVIEW";
    public static final String ACTION_VIEW_EVENT     = "com.prajwal.myfirstapp.CALENDAR_VIEW_EVENT";
    public static final String ACTION_SNOOZE_15      = "com.prajwal.myfirstapp.CALENDAR_SNOOZE_15";

    // Extras
    public static final String EXTRA_EVENT_ID        = "calendar_event_id";
    public static final String EXTRA_EVENT_TITLE     = "calendar_event_title";
    public static final String EXTRA_EVENT_TIME      = "calendar_event_time";
    public static final String EXTRA_EVENT_LOCATION  = "calendar_event_location";
    public static final String EXTRA_REMINDER_IDX    = "calendar_reminder_index";
    public static final String EXTRA_OCCURRENCE_DATE = "calendar_occurrence_date";
    public static final String EXTRA_IS_ALL_DAY      = "calendar_is_all_day";
    public static final String EXTRA_EVENT_TYPE      = "calendar_event_type";
    public static final String EXTRA_EVENT_COLOR     = "calendar_event_color";

    // Default all-day reminder hour
    public static final int ALL_DAY_REMINDER_HOUR = 8;
    public static final int ALL_DAY_REMINDER_MINUTE = 0;

    // ─── Create Notification Channels ────────────────────────────

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;

            // Calendar reminders channel
            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            reminderChannel.setDescription("Calendar event reminders and alerts");
            reminderChannel.enableVibration(true);
            reminderChannel.enableLights(true);
            reminderChannel.setLightColor(0xFF3B82F6);
            nm.createNotificationChannel(reminderChannel);

            // Daily agenda channel
            NotificationChannel agendaChannel = new NotificationChannel(
                    CHANNEL_DAILY, CHANNEL_DAILY_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            agendaChannel.setDescription("Daily morning digest of today's events");
            nm.createNotificationChannel(agendaChannel);

            // Weekly preview channel
            NotificationChannel weeklyChannel = new NotificationChannel(
                    CHANNEL_WEEKLY, CHANNEL_WEEKLY_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            weeklyChannel.setDescription("Weekly preview of upcoming events");
            nm.createNotificationChannel(weeklyChannel);
        }
    }

    // ─── Schedule All Reminders for an Event ─────────────────────

    /**
     * Schedule all reminders for a given event. Cancels existing ones first.
     */
    public static void scheduleEventReminders(Context context, CalendarEvent event) {
        if (event == null || event.isCancelled) return;
        if (event.isCompleted && CalendarEvent.TYPE_REMINDER.equals(event.eventType)) return;

        // Cancel any existing reminders first
        cancelEventReminders(context, event);

        // Schedule each reminder offset
        if (event.reminderOffsets != null && !event.reminderOffsets.isEmpty()) {
            for (int i = 0; i < event.reminderOffsets.size(); i++) {
                int offsetMinutes = event.reminderOffsets.get(i);
                scheduleReminderForOffset(context, event, offsetMinutes, i, event.startDate);
            }
        }

        // For birthday/anniversary events, schedule yearly reminders
        if (event.isBirthdayType() || event.isAnniversaryType()) {
            scheduleYearlyReminder(context, event);
        }
    }

    /**
     * Schedule a reminder for a specific occurrence date of a recurring event.
     */
    public static void scheduleRecurringOccurrenceReminder(Context context, CalendarEvent event, String occurrenceDate) {
        if (event == null || event.isCancelled) return;
        if (event.reminderOffsets != null && !event.reminderOffsets.isEmpty()) {
            for (int i = 0; i < event.reminderOffsets.size(); i++) {
                int offsetMinutes = event.reminderOffsets.get(i);
                scheduleReminderForOffset(context, event, offsetMinutes, i, occurrenceDate);
            }
        }
    }

    // ─── Core Scheduling Methods ─────────────────────────────────

    private static void scheduleReminderForOffset(Context context, CalendarEvent event,
                                                   int offsetMinutes, int reminderIndex,
                                                   String occurrenceDate) {
        long triggerMs;

        if (event.isAllDay || !event.hasStartTime()) {
            // All-day: fire at 8am on the day, minus offset
            Calendar cal = parseDateStr(occurrenceDate);
            if (cal == null) return;
            cal.set(Calendar.HOUR_OF_DAY, ALL_DAY_REMINDER_HOUR);
            cal.set(Calendar.MINUTE, ALL_DAY_REMINDER_MINUTE);
            cal.set(Calendar.SECOND, 0);
            triggerMs = cal.getTimeInMillis() - (long) offsetMinutes * 60 * 1000;
        } else {
            // Timed event: fire at start time minus offset
            Calendar cal = parseDateTimeStr(occurrenceDate, event.startTime);
            if (cal == null) return;
            triggerMs = cal.getTimeInMillis() - (long) offsetMinutes * 60 * 1000;
        }

        // Don't schedule if in the past
        if (triggerMs <= System.currentTimeMillis()) return;

        scheduleExactAlarm(context, event, triggerMs, reminderIndex, occurrenceDate);
    }

    private static void scheduleExactAlarm(Context context, CalendarEvent event, long triggerMs,
                                            int reminderIndex, String occurrenceDate) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_EVENT_REMINDER);
        intent.putExtra(EXTRA_EVENT_ID, event.id);
        intent.putExtra(EXTRA_EVENT_TITLE, event.title);
        intent.putExtra(EXTRA_EVENT_TIME, event.getFormattedTimeRange());
        intent.putExtra(EXTRA_EVENT_LOCATION, event.location != null ? event.location : "");
        intent.putExtra(EXTRA_REMINDER_IDX, reminderIndex);
        intent.putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate);
        intent.putExtra(EXTRA_IS_ALL_DAY, event.isAllDay);
        intent.putExtra(EXTRA_EVENT_TYPE, event.eventType != null ? event.eventType : "personal");
        intent.putExtra(EXTRA_EVENT_COLOR, event.colorHex != null ? event.colorHex : "#3B82F6");

        int requestCode = getRequestCode(event.id, reminderIndex, occurrenceDate);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                Log.w(TAG, "Exact alarms not permitted, using inexact for: " + event.title);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }

        Log.i(TAG, "Alarm scheduled for '" + event.title + "' idx=" + reminderIndex +
                " date=" + occurrenceDate + " at " + new java.util.Date(triggerMs));
    }

    // ─── Birthday / Anniversary Yearly Reminder ──────────────────

    private static void scheduleYearlyReminder(Context context, CalendarEvent event) {
        Calendar eventDate = parseDateStr(event.startDate);
        if (eventDate == null) return;

        // Schedule for next occurrence of this date
        Calendar now = Calendar.getInstance();
        Calendar nextOccurrence = Calendar.getInstance();
        nextOccurrence.set(Calendar.MONTH, eventDate.get(Calendar.MONTH));
        nextOccurrence.set(Calendar.DAY_OF_MONTH, eventDate.get(Calendar.DAY_OF_MONTH));
        nextOccurrence.set(Calendar.HOUR_OF_DAY, ALL_DAY_REMINDER_HOUR);
        nextOccurrence.set(Calendar.MINUTE, 0);
        nextOccurrence.set(Calendar.SECOND, 0);

        // If this year's date has passed, schedule for next year
        if (nextOccurrence.before(now)) {
            nextOccurrence.add(Calendar.YEAR, 1);
        }

        // Schedule 1 day before as well
        Calendar dayBefore = (Calendar) nextOccurrence.clone();
        dayBefore.add(Calendar.DAY_OF_YEAR, -1);
        if (dayBefore.after(now)) {
            String occDate = formatCalendarDate(nextOccurrence);
            scheduleExactAlarm(context, event, dayBefore.getTimeInMillis(), 50, occDate);
        }

        // Schedule on the day at 8am
        String occDate = formatCalendarDate(nextOccurrence);
        scheduleExactAlarm(context, event, nextOccurrence.getTimeInMillis(), 51, occDate);
    }

    // ─── Cancel All Reminders for an Event ───────────────────────

    public static void cancelEventReminders(Context context, CalendarEvent event) {
        if (event == null) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Cancel indexed reminders (up to 15)
        for (int i = 0; i < 15; i++) {
            cancelAlarm(context, am, getRequestCode(event.id, i, event.startDate));
        }

        // Cancel birthday/anniversary codes
        cancelAlarm(context, am, getRequestCode(event.id, 50, event.startDate));
        cancelAlarm(context, am, getRequestCode(event.id, 51, event.startDate));

        Log.i(TAG, "Cancelled all alarms for event: " + event.title);
    }

    private static void cancelAlarm(Context context, AlarmManager am, int requestCode) {
        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
    }

    // ─── Snooze Support ──────────────────────────────────────────

    /**
     * Schedule a snoozed reminder that fires after given delay.
     */
    public static void scheduleSnoozedReminder(Context context, String eventId, String title,
                                                String time, String location, long delayMs) {
        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_EVENT_REMINDER);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putExtra(EXTRA_EVENT_TITLE, title != null ? title : "Event Reminder");
        intent.putExtra(EXTRA_EVENT_TIME, time != null ? time : "");
        intent.putExtra(EXTRA_EVENT_LOCATION, location != null ? location : "");
        intent.putExtra(EXTRA_REMINDER_IDX, 99); // Snooze indicator

        int requestCode = (eventId.hashCode() & 0x7FFFFFFF + 500) & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

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

    // ─── Daily Agenda Notification ───────────────────────────────

    /**
     * Schedule daily agenda notification at the configured time.
     */
    public static void scheduleDailyAgenda(Context context, CalendarSettings settings) {
        if (!settings.dailyAgendaNotification) {
            cancelDailyAgenda(context);
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_DAILY_AGENDA);

        int requestCode = "DAILY_AGENDA".hashCode() & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Parse configured time
        int hour = 8, minute = 0;
        if (settings.dailyAgendaTime != null) {
            try {
                String[] parts = settings.dailyAgendaTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* use defaults */ }
        }

        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);

        // If the time has passed today, schedule for tomorrow
        if (trigger.before(Calendar.getInstance())) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Use repeating alarm for daily
        am.setRepeating(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);

        Log.i(TAG, "Daily agenda scheduled at " + hour + ":" + String.format(java.util.Locale.US, "%02d", minute));
    }

    public static void cancelDailyAgenda(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_DAILY_AGENDA);
        int requestCode = "DAILY_AGENDA".hashCode() & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
    }

    // ─── Weekly Preview Notification ─────────────────────────────

    /**
     * Schedule weekly preview notification at the configured day and time.
     */
    public static void scheduleWeeklyPreview(Context context, CalendarSettings settings) {
        if (!settings.weeklyPreviewNotification) {
            cancelWeeklyPreview(context);
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_WEEKLY_PREVIEW);

        int requestCode = "WEEKLY_PREVIEW".hashCode() & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Parse configured time
        int hour = 19, minute = 0;
        if (settings.weeklyPreviewTime != null) {
            try {
                String[] parts = settings.weeklyPreviewTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* use defaults */ }
        }

        // Parse configured day of week
        int dayOfWeek = Calendar.SUNDAY;
        if ("saturday".equalsIgnoreCase(settings.weeklyPreviewDay)) {
            dayOfWeek = Calendar.SATURDAY;
        } else if ("friday".equalsIgnoreCase(settings.weeklyPreviewDay)) {
            dayOfWeek = Calendar.FRIDAY;
        } else if ("monday".equalsIgnoreCase(settings.weeklyPreviewDay)) {
            dayOfWeek = Calendar.MONDAY;
        }

        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);

        // If the time has passed this week, schedule for next week
        if (trigger.before(Calendar.getInstance())) {
            trigger.add(Calendar.WEEK_OF_YEAR, 1);
        }

        // Use weekly interval (7 days)
        long weeklyInterval = 7 * AlarmManager.INTERVAL_DAY;
        am.setRepeating(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), weeklyInterval, pi);

        Log.i(TAG, "Weekly preview scheduled for " + settings.weeklyPreviewDay + " at " + hour + ":" + 
              String.format(java.util.Locale.US, "%02d", minute));
    }

    public static void cancelWeeklyPreview(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, CalendarReminderReceiver.class);
        intent.setAction(ACTION_WEEKLY_PREVIEW);
        int requestCode = "WEEKLY_PREVIEW".hashCode() & 0x7FFFFFFF;
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);
    }

    // ─── Reschedule All (after boot) ─────────────────────────────

    /**
     * Reschedule all calendar event reminders. Called by BootReceiver.
     */
    public static void rescheduleAllReminders(Context context) {
        CalendarRepository repo = new CalendarRepository(context);
        List<CalendarEvent> allEvents = repo.getAllEvents();

        int scheduled = 0;
        for (CalendarEvent event : allEvents) {
            if (!event.isCancelled && !(event.isCompleted && event.isReminderType())) {
                scheduleEventReminders(context, event);
                scheduled++;
            }
        }

        // Reschedule daily agenda
        CalendarSettings settings = repo.getSettings();
        if (settings.dailyAgendaNotification) {
            scheduleDailyAgenda(context, settings);
        }

        // Reschedule weekly preview
        if (settings.weeklyPreviewNotification) {
            scheduleWeeklyPreview(context, settings);
        }

        Log.i(TAG, "Rescheduled reminders for " + scheduled + " events after boot");
    }

    /**
     * Schedule the next occurrence reminder for a recurring event after one fires.
     */
    public static void scheduleNextRecurringReminder(Context context, CalendarEvent event, String firedDate) {
        if (!event.isRecurring()) return;

        Calendar firedCal = parseDateStr(firedDate);
        if (firedCal == null) return;

        Calendar nextOccurrence = (Calendar) firedCal.clone();

        // Advance to next occurrence
        switch (event.recurrence) {
            case CalendarEvent.RECURRENCE_DAILY:
                nextOccurrence.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case CalendarEvent.RECURRENCE_WEEKLY:
                nextOccurrence.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case CalendarEvent.RECURRENCE_MONTHLY:
                nextOccurrence.add(Calendar.MONTH, 1);
                break;
            case CalendarEvent.RECURRENCE_YEARLY:
                nextOccurrence.add(Calendar.YEAR, 1);
                break;
            default:
                return;
        }

        // Check if still within recurrence bounds
        if (event.recurrenceEndDate != null && !event.recurrenceEndDate.isEmpty()) {
            Calendar endCal = parseDateStr(event.recurrenceEndDate);
            if (endCal != null && nextOccurrence.after(endCal)) return;
        }

        String nextDate = formatCalendarDate(nextOccurrence);
        scheduleRecurringOccurrenceReminder(context, event, nextDate);

        Log.i(TAG, "Scheduled next recurring reminder for '" + event.title +
                "' on " + nextDate);
    }

    // ─── Request Code Generation ─────────────────────────────────

    private static int getRequestCode(String eventId, int reminderIndex, String occurrenceDate) {
        int base = (eventId + occurrenceDate).hashCode() & 0x7FFFFFFF;
        return (base + reminderIndex) & 0x7FFFFFFF;
    }

    // ─── Utility ─────────────────────────────────────────────────

    private static Calendar parseDateStr(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            String[] parts = dateStr.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private static Calendar parseDateTimeStr(String dateStr, String timeStr) {
        Calendar cal = parseDateStr(dateStr);
        if (cal == null) return null;
        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                String[] parts = timeStr.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            } catch (Exception e) { /* ignore */ }
        }
        return cal;
    }

    private static String formatCalendarDate(Calendar cal) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }
}
