package com.prajwal.myfirstapp;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Calendar settings model.
 *
 * Stores user preferences for: first day of week, default view, default reminder,
 * working hours, default event duration, display toggles, notification preferences.
 */
public class CalendarSettings {

    // ─── Fields ──────────────────────────────────────────────────

    public String firstDayOfWeek;          // "monday", "sunday", "saturday"
    public String defaultView;             // "month", "week", "day", "agenda"
    public int defaultReminderMinutes;     // -1 = none, 10, 30, 60, 1440, or custom
    public String workingHoursStart;       // "09:00"
    public String workingHoursEnd;         // "17:00"
    public int defaultEventDurationMinutes; // 30, 60, 120
    public boolean showWeekNumbers;
    public boolean showDeclinedEvents;
    public String notificationSound;       // "default", "silent", or URI
    public boolean notificationSoundEnabled; // Alias for boolean switch compatibility
    public boolean dailyAgendaNotification;
    public String dailyAgendaTime;         // "08:00"
    public boolean weeklyPreviewNotification;
    public String weeklyPreviewDay;        // "sunday", "saturday", "friday"
    public String weeklyPreviewTime;       // "19:00"
    public boolean showRecurringExpenses;  // Show recurring expenses from Expense Tracker

    // ─── Constants ───────────────────────────────────────────────

    public static final String FIRST_DAY_MONDAY   = "monday";
    public static final String FIRST_DAY_SUNDAY   = "sunday";
    public static final String FIRST_DAY_SATURDAY = "saturday";

    public static final String VIEW_MONTH  = "month";
    public static final String VIEW_WEEK   = "week";
    public static final String VIEW_DAY    = "day";
    public static final String VIEW_AGENDA = "agenda";

    // ─── Constructor (Defaults) ──────────────────────────────────

    public CalendarSettings() {
        this.firstDayOfWeek = FIRST_DAY_MONDAY;
        this.defaultView = VIEW_MONTH;
        this.defaultReminderMinutes = 30;
        this.workingHoursStart = "09:00";
        this.workingHoursEnd = "17:00";
        this.defaultEventDurationMinutes = 60;
        this.showWeekNumbers = false;
        this.showDeclinedEvents = true;
        this.notificationSound = "default";
        this.notificationSoundEnabled = true;
        this.dailyAgendaNotification = false;
        this.dailyAgendaTime = "08:00";
        this.weeklyPreviewNotification = false;
        this.weeklyPreviewDay = "sunday";
        this.weeklyPreviewTime = "19:00";
        this.showRecurringExpenses = true;
    }

    // ─── Helper Methods ──────────────────────────────────────────

    /**
     * Get the Calendar.DAY_OF_WEEK constant for the first day of week setting.
     */
    public int getFirstDayOfWeekCalendar() {
        switch (firstDayOfWeek) {
            case FIRST_DAY_SUNDAY:   return java.util.Calendar.SUNDAY;
            case FIRST_DAY_SATURDAY: return java.util.Calendar.SATURDAY;
            default:                 return java.util.Calendar.MONDAY;
        }
    }

    /**
     * Get working hours start as hour (0-23).
     */
    public int getWorkingHoursStartHour() {
        return parseHour(workingHoursStart, 9);
    }

    /**
     * Get working hours end as hour (0-23).
     */
    public int getWorkingHoursEndHour() {
        return parseHour(workingHoursEnd, 17);
    }

    private int parseHour(String time, int defaultHour) {
        if (time == null || time.isEmpty()) return defaultHour;
        try {
            return Integer.parseInt(time.split(":")[0]);
        } catch (Exception e) {
            return defaultHour;
        }
    }

    // ─── JSON Serialization ──────────────────────────────────────

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("firstDayOfWeek", firstDayOfWeek);
            json.put("defaultView", defaultView);
            json.put("defaultReminderMinutes", defaultReminderMinutes);
            json.put("workingHoursStart", workingHoursStart);
            json.put("workingHoursEnd", workingHoursEnd);
            json.put("defaultEventDurationMinutes", defaultEventDurationMinutes);
            json.put("showWeekNumbers", showWeekNumbers);
            json.put("showDeclinedEvents", showDeclinedEvents);
            json.put("notificationSound", notificationSound);
            json.put("dailyAgendaNotification", dailyAgendaNotification);
            json.put("dailyAgendaTime", dailyAgendaTime);
            json.put("weeklyPreviewNotification", weeklyPreviewNotification);
            json.put("weeklyPreviewDay", weeklyPreviewDay);
            json.put("weeklyPreviewTime", weeklyPreviewTime);
            json.put("showRecurringExpenses", showRecurringExpenses);
        } catch (JSONException e) {
            // ignore
        }
        return json;
    }

    public static CalendarSettings fromJson(JSONObject json) {
        if (json == null) return new CalendarSettings();
        CalendarSettings s = new CalendarSettings();
        s.firstDayOfWeek = json.optString("firstDayOfWeek", FIRST_DAY_MONDAY);
        s.defaultView = json.optString("defaultView", VIEW_MONTH);
        s.defaultReminderMinutes = json.optInt("defaultReminderMinutes", 30);
        s.workingHoursStart = json.optString("workingHoursStart", "09:00");
        s.workingHoursEnd = json.optString("workingHoursEnd", "17:00");
        s.defaultEventDurationMinutes = json.optInt("defaultEventDurationMinutes", 60);
        s.showWeekNumbers = json.optBoolean("showWeekNumbers", false);
        s.showDeclinedEvents = json.optBoolean("showDeclinedEvents", true);
        s.notificationSound = json.optString("notificationSound", "default");
        s.dailyAgendaNotification = json.optBoolean("dailyAgendaNotification", false);
        s.dailyAgendaTime = json.optString("dailyAgendaTime", "08:00");
        s.weeklyPreviewNotification = json.optBoolean("weeklyPreviewNotification", false);
        s.weeklyPreviewDay = json.optString("weeklyPreviewDay", "sunday");
        s.weeklyPreviewTime = json.optString("weeklyPreviewTime", "19:00");
        s.showRecurringExpenses = json.optBoolean("showRecurringExpenses", true);
        return s;
    }
}
