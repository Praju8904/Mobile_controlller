package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/**
 * Settings data class for the Task Manager, with SharedPreferences persistence.
 * Singleton — obtain via {@link #getInstance(Context)}.
 */
public class TaskManagerSettings {

    private static final String TAG = "TaskManagerSettings";
    private static final String PREFS_NAME = "task_manager_settings";

    private static TaskManagerSettings instance;

    public static synchronized TaskManagerSettings getInstance(Context context) {
        if (instance == null) {
            instance = new TaskManagerSettings(context.getApplicationContext());
        }
        return instance;
    }

    // ── Fields ────────────────────────────────────────────────────

    // General
    public String defaultPriority = "normal";
    public String defaultCategory = "Personal";
    public String defaultView = "list";
    public int firstDayOfWeek = Calendar.MONDAY;
    public String showCompletedTasks = "collapsed";
    public boolean autoArchiveCompleted = false;
    public int autoArchiveDays = 7;
    public int trashAutodeleteDays = 30;

    // Notifications
    public boolean dailyFocusNotification = true;
    public int dailyFocusHour = 8;
    public int dailyFocusMinute = 0;
    public boolean weeklyReviewNotification = true;
    public boolean overdueAlerts = true;
    public String overdueAlertFrequency = "daily";
    public boolean meetingReminders = true;
    public int defaultMeetingReminderMinutes = 15;
    public boolean taskReminders = true;

    // Smart Features
    public boolean smartDueDateDetection = true;
    public boolean smartPriorityDetection = true;
    public boolean smartCategoryDetection = true;
    public boolean duplicateDetection = true;
    public boolean priorityEscalation = true;
    public int escalationThresholdDays = 3;

    // Focus Mode
    public int pomodoroFocusMinutes = 25;
    public int shortBreakMinutes = 5;
    public int longBreakMinutes = 15;
    public int longBreakAfterPomodoros = 4;
    public boolean pomodoroSound = true;
    public boolean autoStartNextSession = false;

    // Display
    public String taskCardDensity = "comfortable";
    public boolean showPriorityGlow = true;
    public boolean showStreakBanner = true;
    public boolean showFocusScoreCard = true;
    public boolean hapticFeedback = true;

    // Meeting
    public int defaultMeetingDurationMinutes = 30;
    public String defaultVideoPlatform = "Zoom";
    public int showJoinButtonMinutesBefore = 10;
    public boolean autoCompleteMeetings = false;

    // Calendar
    public boolean showTasksInCalendar = true;
    public boolean showMeetingsInCalendar = true;

    // ── Constructor ───────────────────────────────────────────────

    private final SharedPreferences prefs;

    private TaskManagerSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    // ── Load / Save ───────────────────────────────────────────────

    public void load() {
        defaultPriority                = prefs.getString("defaultPriority", defaultPriority);
        defaultCategory                = prefs.getString("defaultCategory", defaultCategory);
        defaultView                    = prefs.getString("defaultView", defaultView);
        firstDayOfWeek                 = prefs.getInt("firstDayOfWeek", firstDayOfWeek);
        showCompletedTasks             = prefs.getString("showCompletedTasks", showCompletedTasks);
        autoArchiveCompleted           = prefs.getBoolean("autoArchiveCompleted", autoArchiveCompleted);
        autoArchiveDays                = prefs.getInt("autoArchiveDays", autoArchiveDays);
        trashAutodeleteDays            = prefs.getInt("trashAutodeleteDays", trashAutodeleteDays);

        dailyFocusNotification         = prefs.getBoolean("dailyFocusNotification", dailyFocusNotification);
        dailyFocusHour                 = prefs.getInt("dailyFocusHour", dailyFocusHour);
        dailyFocusMinute               = prefs.getInt("dailyFocusMinute", dailyFocusMinute);
        weeklyReviewNotification       = prefs.getBoolean("weeklyReviewNotification", weeklyReviewNotification);
        overdueAlerts                  = prefs.getBoolean("overdueAlerts", overdueAlerts);
        overdueAlertFrequency          = prefs.getString("overdueAlertFrequency", overdueAlertFrequency);
        meetingReminders               = prefs.getBoolean("meetingReminders", meetingReminders);
        defaultMeetingReminderMinutes  = prefs.getInt("defaultMeetingReminderMinutes", defaultMeetingReminderMinutes);
        taskReminders                  = prefs.getBoolean("taskReminders", taskReminders);

        smartDueDateDetection          = prefs.getBoolean("smartDueDateDetection", smartDueDateDetection);
        smartPriorityDetection         = prefs.getBoolean("smartPriorityDetection", smartPriorityDetection);
        smartCategoryDetection         = prefs.getBoolean("smartCategoryDetection", smartCategoryDetection);
        duplicateDetection             = prefs.getBoolean("duplicateDetection", duplicateDetection);
        priorityEscalation             = prefs.getBoolean("priorityEscalation", priorityEscalation);
        escalationThresholdDays        = prefs.getInt("escalationThresholdDays", escalationThresholdDays);

        pomodoroFocusMinutes           = prefs.getInt("pomodoroFocusMinutes", pomodoroFocusMinutes);
        shortBreakMinutes              = prefs.getInt("shortBreakMinutes", shortBreakMinutes);
        longBreakMinutes               = prefs.getInt("longBreakMinutes", longBreakMinutes);
        longBreakAfterPomodoros        = prefs.getInt("longBreakAfterPomodoros", longBreakAfterPomodoros);
        pomodoroSound                  = prefs.getBoolean("pomodoroSound", pomodoroSound);
        autoStartNextSession           = prefs.getBoolean("autoStartNextSession", autoStartNextSession);

        taskCardDensity                = prefs.getString("taskCardDensity", taskCardDensity);
        showPriorityGlow               = prefs.getBoolean("showPriorityGlow", showPriorityGlow);
        showStreakBanner               = prefs.getBoolean("showStreakBanner", showStreakBanner);
        showFocusScoreCard             = prefs.getBoolean("showFocusScoreCard", showFocusScoreCard);
        hapticFeedback                 = prefs.getBoolean("hapticFeedback", hapticFeedback);

        defaultMeetingDurationMinutes  = prefs.getInt("defaultMeetingDurationMinutes", defaultMeetingDurationMinutes);
        defaultVideoPlatform           = prefs.getString("defaultVideoPlatform", defaultVideoPlatform);
        showJoinButtonMinutesBefore    = prefs.getInt("showJoinButtonMinutesBefore", showJoinButtonMinutesBefore);
        autoCompleteMeetings           = prefs.getBoolean("autoCompleteMeetings", autoCompleteMeetings);

        showTasksInCalendar            = prefs.getBoolean("showTasksInCalendar", showTasksInCalendar);
        showMeetingsInCalendar         = prefs.getBoolean("showMeetingsInCalendar", showMeetingsInCalendar);

        Log.d(TAG, "Settings loaded");
    }

    public void save() {
        SharedPreferences.Editor e = prefs.edit();

        e.putString("defaultPriority", defaultPriority);
        e.putString("defaultCategory", defaultCategory);
        e.putString("defaultView", defaultView);
        e.putInt("firstDayOfWeek", firstDayOfWeek);
        e.putString("showCompletedTasks", showCompletedTasks);
        e.putBoolean("autoArchiveCompleted", autoArchiveCompleted);
        e.putInt("autoArchiveDays", autoArchiveDays);
        e.putInt("trashAutodeleteDays", trashAutodeleteDays);

        e.putBoolean("dailyFocusNotification", dailyFocusNotification);
        e.putInt("dailyFocusHour", dailyFocusHour);
        e.putInt("dailyFocusMinute", dailyFocusMinute);
        e.putBoolean("weeklyReviewNotification", weeklyReviewNotification);
        e.putBoolean("overdueAlerts", overdueAlerts);
        e.putString("overdueAlertFrequency", overdueAlertFrequency);
        e.putBoolean("meetingReminders", meetingReminders);
        e.putInt("defaultMeetingReminderMinutes", defaultMeetingReminderMinutes);
        e.putBoolean("taskReminders", taskReminders);

        e.putBoolean("smartDueDateDetection", smartDueDateDetection);
        e.putBoolean("smartPriorityDetection", smartPriorityDetection);
        e.putBoolean("smartCategoryDetection", smartCategoryDetection);
        e.putBoolean("duplicateDetection", duplicateDetection);
        e.putBoolean("priorityEscalation", priorityEscalation);
        e.putInt("escalationThresholdDays", escalationThresholdDays);

        e.putInt("pomodoroFocusMinutes", pomodoroFocusMinutes);
        e.putInt("shortBreakMinutes", shortBreakMinutes);
        e.putInt("longBreakMinutes", longBreakMinutes);
        e.putInt("longBreakAfterPomodoros", longBreakAfterPomodoros);
        e.putBoolean("pomodoroSound", pomodoroSound);
        e.putBoolean("autoStartNextSession", autoStartNextSession);

        e.putString("taskCardDensity", taskCardDensity);
        e.putBoolean("showPriorityGlow", showPriorityGlow);
        e.putBoolean("showStreakBanner", showStreakBanner);
        e.putBoolean("showFocusScoreCard", showFocusScoreCard);
        e.putBoolean("hapticFeedback", hapticFeedback);

        e.putInt("defaultMeetingDurationMinutes", defaultMeetingDurationMinutes);
        e.putString("defaultVideoPlatform", defaultVideoPlatform);
        e.putInt("showJoinButtonMinutesBefore", showJoinButtonMinutesBefore);
        e.putBoolean("autoCompleteMeetings", autoCompleteMeetings);

        e.putBoolean("showTasksInCalendar", showTasksInCalendar);
        e.putBoolean("showMeetingsInCalendar", showMeetingsInCalendar);

        e.apply();
        Log.d(TAG, "Settings saved");
    }

    public synchronized void reset() {
        prefs.edit().clear().apply();
        synchronized (TaskManagerSettings.class) {
            instance = null; // force reload on next getInstance()
        }
    }
}
