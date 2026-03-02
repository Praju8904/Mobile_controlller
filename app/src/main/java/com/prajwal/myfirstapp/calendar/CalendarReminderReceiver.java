package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * BroadcastReceiver for calendar event reminders.
 *
 * Handles:
 *   - Event reminder notifications with "View Event" and "Snooze 15 min" actions
 *   - Daily agenda digest notifications
 *   - Recurring event rescheduling after each notification fires
 *   - Deep linking into CalendarEventDetailActivity
 */
public class CalendarReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "CalendarReminderRecv";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);

        // Ensure notification channels exist
        CalendarNotificationHelper.createNotificationChannels(context);

        if (CalendarNotificationHelper.ACTION_EVENT_REMINDER.equals(action)) {
            handleEventReminder(context, intent);
        } else if (CalendarNotificationHelper.ACTION_DAILY_AGENDA.equals(action)) {
            handleDailyAgenda(context);
        } else if (CalendarNotificationHelper.ACTION_WEEKLY_PREVIEW.equals(action)) {
            handleWeeklyPreview(context);
        } else if (CalendarNotificationHelper.ACTION_VIEW_EVENT.equals(action)) {
            handleViewEvent(context, intent);
        } else if (CalendarNotificationHelper.ACTION_SNOOZE_15.equals(action)) {
            handleSnooze(context, intent);
        }
    }

    // ─── Event Reminder ──────────────────────────────────────────

    private void handleEventReminder(Context context, Intent intent) {
        String eventId = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_ID);
        String title = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_TITLE);
        String time = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_TIME);
        String location = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_LOCATION);
        String occurrenceDate = intent.getStringExtra(CalendarNotificationHelper.EXTRA_OCCURRENCE_DATE);
        boolean isAllDay = intent.getBooleanExtra(CalendarNotificationHelper.EXTRA_IS_ALL_DAY, false);
        String eventType = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_TYPE);
        String colorHex = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_COLOR);

        if (title == null || title.isEmpty()) title = "Calendar Event";

        // Build notification content
        StringBuilder contentText = new StringBuilder();
        if (isAllDay) {
            contentText.append("All Day");
        } else if (time != null && !time.isEmpty()) {
            contentText.append(time);
        }
        if (location != null && !location.isEmpty()) {
            if (contentText.length() > 0) contentText.append(" • ");
            contentText.append("📍 ").append(location);
        }

        // Icon based on event type
        String typeIcon = getTypeIcon(eventType);

        // Deep link intent to event detail
        Intent viewIntent = new Intent(context, CalendarActivity.class);
        viewIntent.putExtra("event_id", eventId);
        viewIntent.putExtra("selected_date", occurrenceDate);
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent viewPi = PendingIntent.getActivity(
                context, eventId.hashCode(),
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "View Event" action
        Intent viewActionIntent = new Intent(context, CalendarReminderReceiver.class);
        viewActionIntent.setAction(CalendarNotificationHelper.ACTION_VIEW_EVENT);
        viewActionIntent.putExtra(CalendarNotificationHelper.EXTRA_EVENT_ID, eventId);
        viewActionIntent.putExtra(CalendarNotificationHelper.EXTRA_OCCURRENCE_DATE, occurrenceDate);
        PendingIntent viewActionPi = PendingIntent.getBroadcast(
                context, (eventId + "_view").hashCode() & 0x7FFFFFFF,
                viewActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // "Snooze 15 min" action
        Intent snoozeIntent = new Intent(context, CalendarReminderReceiver.class);
        snoozeIntent.setAction(CalendarNotificationHelper.ACTION_SNOOZE_15);
        snoozeIntent.putExtra(CalendarNotificationHelper.EXTRA_EVENT_ID, eventId);
        snoozeIntent.putExtra(CalendarNotificationHelper.EXTRA_EVENT_TITLE, title);
        snoozeIntent.putExtra(CalendarNotificationHelper.EXTRA_EVENT_TIME, time);
        snoozeIntent.putExtra(CalendarNotificationHelper.EXTRA_EVENT_LOCATION, location);
        PendingIntent snoozePi = PendingIntent.getBroadcast(
                context, (eventId + "_snooze").hashCode() & 0x7FFFFFFF,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Parse color
        int notifColor = 0xFF3B82F6;
        try {
            if (colorHex != null) notifColor = android.graphics.Color.parseColor(colorHex);
        } catch (Exception e) { /* use default */ }

        // Build notification
        // Special celebration style for birthday/anniversary
        boolean isCelebration = CalendarEvent.TYPE_BIRTHDAY.equals(eventType) || 
                                CalendarEvent.TYPE_ANNIVERSARY.equals(eventType);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CalendarNotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(isCelebration ? typeIcon + " " + title + " 🎉" : typeIcon + " " + title)
                .setContentText(isCelebration ? getCelebrationMessage(eventType, title) : contentText.toString())
                .setColor(isCelebration ? 0xFFEC4899 : notifColor)  // Pink for celebrations
                .setAutoCancel(true)
                .setContentIntent(viewPi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .addAction(android.R.drawable.ic_menu_view, "View Event", viewActionPi)
                .addAction(android.R.drawable.ic_menu_recent_history, isCelebration ? "Send Wishes" : "Snooze 15 min", snoozePi);

        // Show notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int notifId = eventId.hashCode() & 0x7FFFFFFF;
            nm.notify(notifId, builder.build());
        }

        // Schedule next occurrence for recurring events
        CalendarRepository repo = new CalendarRepository(context);
        CalendarEvent event = repo.getEventById(eventId);
        if (event != null && event.isRecurring() && occurrenceDate != null) {
            CalendarNotificationHelper.scheduleNextRecurringReminder(context, event, occurrenceDate);
        }

        Log.i(TAG, "Showed reminder notification for: " + title);
    }

    // ─── Daily Agenda ────────────────────────────────────────────

    private void handleDailyAgenda(Context context) {
        CalendarRepository repo = new CalendarRepository(context);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        List<CalendarEvent> todayEvents = repo.getEventsForDate(today);

        if (todayEvents.isEmpty()) return; // Don't notify if no events

        String title = "📅 Today's Agenda • " + todayEvents.size() + " event" +
                (todayEvents.size() != 1 ? "s" : "");

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < Math.min(todayEvents.size(), 5); i++) {
            CalendarEvent event = todayEvents.get(i);
            if (i > 0) body.append("\n");
            body.append("• ").append(event.title);
            if (event.hasStartTime()) {
                body.append(" (").append(CalendarEvent.formatTime(event.startTime)).append(")");
            } else {
                body.append(" (All Day)");
            }
        }
        if (todayEvents.size() > 5) {
            body.append("\n+ ").append(todayEvents.size() - 5).append(" more");
        }

        // Tap to open calendar
        Intent tapIntent = new Intent(context, CalendarActivity.class);
        tapIntent.putExtra("selected_date", today);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                context, "daily_agenda_tap".hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CalendarNotificationHelper.CHANNEL_DAILY)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setColor(0xFF3B82F6)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify("daily_agenda".hashCode() & 0x7FFFFFFF, builder.build());
        }

        Log.i(TAG, "Showed daily agenda: " + todayEvents.size() + " events");
    }

    // ─── Weekly Preview ──────────────────────────────────────────

    private void handleWeeklyPreview(Context context) {
        CalendarRepository repo = new CalendarRepository(context);
        java.util.Calendar startCal = java.util.Calendar.getInstance();
        startCal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        java.util.Calendar endCal = (java.util.Calendar) startCal.clone();
        endCal.add(java.util.Calendar.DAY_OF_YEAR, 7);

        String startDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(startCal.getTime());
        String endDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(endCal.getTime());

        List<CalendarEvent> weekEvents = repo.getUpcomingEvents(20);
        // Filter to the next 7 days
        java.util.List<CalendarEvent> filteredEvents = new java.util.ArrayList<>();
        for (CalendarEvent ev : weekEvents) {
            if (ev.date != null && ev.date.compareTo(startDate) >= 0 && ev.date.compareTo(endDate) <= 0) {
                filteredEvents.add(ev);
            }
        }

        if (filteredEvents.isEmpty()) {
            // Show "light week" notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CalendarNotificationHelper.CHANNEL_WEEKLY)
                    .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                    .setContentTitle("🗓️ Week Ahead")
                    .setContentText("You have a light week coming up!")
                    .setColor(0xFF10B981)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify("weekly_preview".hashCode() & 0x7FFFFFFF, builder.build());
            }
            return;
        }

        String title = "🗓️ Week Ahead • " + filteredEvents.size() + " event" +
                (filteredEvents.size() != 1 ? "s" : "");

        // Group by day
        java.util.Map<String, java.util.List<CalendarEvent>> byDay = new java.util.LinkedHashMap<>();
        for (CalendarEvent ev : filteredEvents) {
            byDay.computeIfAbsent(ev.date, k -> new java.util.ArrayList<>()).add(ev);
        }

        StringBuilder body = new StringBuilder();
        int dayCount = 0;
        for (java.util.Map.Entry<String, java.util.List<CalendarEvent>> entry : byDay.entrySet()) {
            if (dayCount >= 4) {
                body.append("\n+ more days...");
                break;
            }
            String dayLabel = formatDayLabel(entry.getKey());
            body.append(dayLabel).append(": ").append(entry.getValue().size()).append(" event(s)\n");
            dayCount++;
        }

        // Tap to open calendar
        Intent tapIntent = new Intent(context, CalendarActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                context, "weekly_preview_tap".hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CalendarNotificationHelper.CHANNEL_WEEKLY)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText("Preview your upcoming events")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString().trim()))
                .setColor(0xFF3B82F6)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify("weekly_preview".hashCode() & 0x7FFFFFFF, builder.build());
        }

        Log.i(TAG, "Showed weekly preview: " + filteredEvents.size() + " events");
    }

    private String formatDayLabel(String dateStr) {
        try {
            java.util.Date date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateStr);
            return new java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US).format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    // ─── View Event Action ───────────────────────────────────────

    private void handleViewEvent(Context context, Intent intent) {
        String eventId = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_ID);
        String occurrenceDate = intent.getStringExtra(CalendarNotificationHelper.EXTRA_OCCURRENCE_DATE);

        // Dismiss notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && eventId != null) {
            nm.cancel(eventId.hashCode() & 0x7FFFFFFF);
        }

        // Open event detail
        Intent viewIntent = new Intent(context, CalendarActivity.class);
        viewIntent.putExtra("event_id", eventId);
        viewIntent.putExtra("selected_date", occurrenceDate);
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(viewIntent);
    }

    // ─── Snooze 15 Minutes ───────────────────────────────────────

    private void handleSnooze(Context context, Intent intent) {
        String eventId = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_ID);
        String title = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_TITLE);
        String time = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_TIME);
        String location = intent.getStringExtra(CalendarNotificationHelper.EXTRA_EVENT_LOCATION);

        // Dismiss current notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && eventId != null) {
            nm.cancel(eventId.hashCode() & 0x7FFFFFFF);
        }

        // Schedule snoozed reminder at +15 minutes
        CalendarNotificationHelper.scheduleSnoozedReminder(
                context, eventId, title, time, location, 15 * 60 * 1000
        );

        Log.i(TAG, "Snoozed reminder for 15 min: " + title);
    }

    // ─── Utility ─────────────────────────────────────────────────

    private String getTypeIcon(String eventType) {
        if (eventType == null) return "📅";
        switch (eventType) {
            case CalendarEvent.TYPE_PERSONAL:    return "👤";
            case CalendarEvent.TYPE_WORK:        return "💼";
            case CalendarEvent.TYPE_STUDY:       return "📚";
            case CalendarEvent.TYPE_HEALTH:      return "💪";
            case CalendarEvent.TYPE_SOCIAL:      return "🎉";
            case CalendarEvent.TYPE_REMINDER:    return "⏰";
            case CalendarEvent.TYPE_BIRTHDAY:    return "🎂";
            case CalendarEvent.TYPE_ANNIVERSARY: return "💍";
            case CalendarEvent.TYPE_HOLIDAY:     return "🎄";
            default:                             return "📅";
        }
    }

    private String getCelebrationMessage(String eventType, String title) {
        if (CalendarEvent.TYPE_BIRTHDAY.equals(eventType)) {
            return "Time to celebrate! 🎁 Don't forget to send your wishes!";
        } else if (CalendarEvent.TYPE_ANNIVERSARY.equals(eventType)) {
            return "A special day to remember! 💕 Celebrate together!";
        }
        return "Reminder for " + title;
    }
}
