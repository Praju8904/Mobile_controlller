package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.meetings.Meeting;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Task Manager Settings Activity.
 *
 * Sections: General, Notifications, Smart Features, Focus Mode,
 * Display, Meeting, Data.
 */
public class TaskManagerSettingsActivity extends AppCompatActivity {

    private TaskManagerSettings settings;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = TaskManagerSettings.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1A1A2E"));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        buildToolbar();
        buildGeneralSection();
        buildNotificationsSection();
        buildSmartFeaturesSection();
        buildFocusModeSection();
        buildDisplaySection();
        buildMeetingSection();
        buildDataSection();
    }

    // ── Toolbar ───────────────────────────────────────────────────

    private void buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(12), 0, dp(16));

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        backLp.setMarginEnd(dp(12));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> { saveAndFinish(); });
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Task Manager Settings");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        toolbar.addView(tvTitle);

        root.addView(toolbar);
    }

    // ── General ───────────────────────────────────────────────────

    private void buildGeneralSection() {
        root.addView(sectionHeader("General"));

        root.addView(spinnerRow("Default Priority",
                new String[]{"none", "low", "normal", "high", "urgent"},
                settings.defaultPriority,
                val -> settings.defaultPriority = val));

        root.addView(editTextRow("Default Category", settings.defaultCategory,
                val -> settings.defaultCategory = val));

        root.addView(spinnerRow("Default View",
                new String[]{"list", "kanban", "timeblock"},
                settings.defaultView,
                val -> settings.defaultView = val));

        root.addView(spinnerRow("First Day of Week",
                new String[]{"Monday", "Sunday", "Saturday"},
                dayOfWeekName(settings.firstDayOfWeek),
                val -> settings.firstDayOfWeek = parseDayOfWeek(val)));

        root.addView(spinnerRow("Show Completed Tasks",
                new String[]{"always", "collapsed", "hidden"},
                settings.showCompletedTasks,
                val -> settings.showCompletedTasks = val));

        root.addView(toggleRow("Auto-Archive Completed", settings.autoArchiveCompleted,
                v -> settings.autoArchiveCompleted = v));

        root.addView(spinnerRow("Auto-Archive After",
                new String[]{"1", "3", "7", "14", "30"},
                String.valueOf(settings.autoArchiveDays),
                val -> settings.autoArchiveDays = safeInt(val, 7)));

        root.addView(spinnerRow("Trash Auto-Delete (days)",
                new String[]{"7", "14", "30", "60", "90"},
                String.valueOf(settings.trashAutodeleteDays),
                val -> settings.trashAutodeleteDays = safeInt(val, 30)));
    }

    // ── Notifications ─────────────────────────────────────────────

    private void buildNotificationsSection() {
        root.addView(divider());
        root.addView(sectionHeader("Notifications"));

        root.addView(toggleRow("Daily Focus Notification", settings.dailyFocusNotification,
                v -> settings.dailyFocusNotification = v));

        root.addView(spinnerRow("Focus Notification Hour",
                new String[]{"6", "7", "8", "9", "10", "11", "12"},
                String.valueOf(settings.dailyFocusHour),
                val -> settings.dailyFocusHour = safeInt(val, 8)));

        root.addView(toggleRow("Weekly Review Notification", settings.weeklyReviewNotification,
                v -> settings.weeklyReviewNotification = v));

        root.addView(toggleRow("Overdue Alerts", settings.overdueAlerts,
                v -> settings.overdueAlerts = v));

        root.addView(spinnerRow("Overdue Alert Frequency",
                new String[]{"hourly", "daily", "weekly"},
                settings.overdueAlertFrequency,
                val -> settings.overdueAlertFrequency = val));

        root.addView(toggleRow("Meeting Reminders", settings.meetingReminders,
                v -> settings.meetingReminders = v));

        root.addView(spinnerRow("Default Meeting Reminder",
                new String[]{"5", "10", "15", "30", "60"},
                String.valueOf(settings.defaultMeetingReminderMinutes),
                val -> settings.defaultMeetingReminderMinutes = safeInt(val, 15)));

        root.addView(toggleRow("Task Reminders", settings.taskReminders,
                v -> settings.taskReminders = v));
    }

    // ── Smart Features ────────────────────────────────────────────

    private void buildSmartFeaturesSection() {
        root.addView(divider());
        root.addView(sectionHeader("Smart Features"));

        root.addView(toggleRow("Smart Due Date Detection", settings.smartDueDateDetection,
                v -> settings.smartDueDateDetection = v));
        root.addView(toggleRow("Smart Priority Detection", settings.smartPriorityDetection,
                v -> settings.smartPriorityDetection = v));
        root.addView(toggleRow("Smart Category Detection", settings.smartCategoryDetection,
                v -> settings.smartCategoryDetection = v));
        root.addView(toggleRow("Duplicate Detection", settings.duplicateDetection,
                v -> settings.duplicateDetection = v));
        root.addView(toggleRow("Priority Escalation", settings.priorityEscalation,
                v -> settings.priorityEscalation = v));
        root.addView(spinnerRow("Escalation Threshold (days)",
                new String[]{"1", "2", "3", "5", "7"},
                String.valueOf(settings.escalationThresholdDays),
                val -> settings.escalationThresholdDays = safeInt(val, 3)));
    }

    // ── Focus Mode ────────────────────────────────────────────────

    private void buildFocusModeSection() {
        root.addView(divider());
        root.addView(sectionHeader("Focus Mode"));

        root.addView(spinnerRow("Pomodoro Duration (min)",
                new String[]{"15", "20", "25", "30", "45", "60"},
                String.valueOf(settings.pomodoroFocusMinutes),
                val -> settings.pomodoroFocusMinutes = safeInt(val, 25)));

        root.addView(spinnerRow("Short Break (min)",
                new String[]{"3", "5", "10", "15"},
                String.valueOf(settings.shortBreakMinutes),
                val -> settings.shortBreakMinutes = safeInt(val, 5)));

        root.addView(spinnerRow("Long Break (min)",
                new String[]{"10", "15", "20", "30"},
                String.valueOf(settings.longBreakMinutes),
                val -> settings.longBreakMinutes = safeInt(val, 15)));

        root.addView(spinnerRow("Long Break After (pomodoros)",
                new String[]{"2", "3", "4", "5", "6"},
                String.valueOf(settings.longBreakAfterPomodoros),
                val -> settings.longBreakAfterPomodoros = safeInt(val, 4)));

        root.addView(toggleRow("Pomodoro Sound", settings.pomodoroSound,
                v -> settings.pomodoroSound = v));
        root.addView(toggleRow("Auto-Start Next Session", settings.autoStartNextSession,
                v -> settings.autoStartNextSession = v));
    }

    // ── Display ───────────────────────────────────────────────────

    private void buildDisplaySection() {
        root.addView(divider());
        root.addView(sectionHeader("Display"));

        root.addView(spinnerRow("Task Card Density",
                new String[]{"compact", "comfortable", "spacious"},
                settings.taskCardDensity,
                val -> settings.taskCardDensity = val));
        root.addView(toggleRow("Priority Glow", settings.showPriorityGlow,
                v -> settings.showPriorityGlow = v));
        root.addView(toggleRow("Streak Banner", settings.showStreakBanner,
                v -> settings.showStreakBanner = v));
        root.addView(toggleRow("Focus Score Card", settings.showFocusScoreCard,
                v -> settings.showFocusScoreCard = v));
        root.addView(toggleRow("Haptic Feedback", settings.hapticFeedback,
                v -> settings.hapticFeedback = v));
    }

    // ── Meeting ───────────────────────────────────────────────────

    private void buildMeetingSection() {
        root.addView(divider());
        root.addView(sectionHeader("Meeting"));

        root.addView(spinnerRow("Default Duration (min)",
                new String[]{"15", "30", "45", "60", "90", "120"},
                String.valueOf(settings.defaultMeetingDurationMinutes),
                val -> settings.defaultMeetingDurationMinutes = safeInt(val, 30)));

        root.addView(spinnerRow("Default Video Platform",
                Meeting.PLATFORM_OPTIONS,
                settings.defaultVideoPlatform,
                val -> settings.defaultVideoPlatform = val));

        root.addView(spinnerRow("Show Join Button (min before)",
                new String[]{"5", "10", "15", "30"},
                String.valueOf(settings.showJoinButtonMinutesBefore),
                val -> settings.showJoinButtonMinutesBefore = safeInt(val, 10)));

        root.addView(toggleRow("Auto-Complete Meetings", settings.autoCompleteMeetings,
                v -> settings.autoCompleteMeetings = v));
        root.addView(toggleRow("Show Tasks in Calendar", settings.showTasksInCalendar,
                v -> settings.showTasksInCalendar = v));
        root.addView(toggleRow("Show Meetings in Calendar", settings.showMeetingsInCalendar,
                v -> settings.showMeetingsInCalendar = v));
    }

    // ── Data ──────────────────────────────────────────────────────

    private void buildDataSection() {
        root.addView(divider());
        root.addView(sectionHeader("Data"));

        root.addView(actionRow("📥  Import Tasks", "#6C63FF", () -> {
            settings.save();
            startActivity(new Intent(this, TaskImportActivity.class));
        }));

        root.addView(actionRow("📤  Export Tasks", "#34C759", () -> {
            settings.save();
            TaskRepository repo = new TaskRepository(this);
            List<Task> tasks = repo.getAllTasks();
            TaskExportManager.exportToFile(this, tasks, "csv",
                    () -> Toast.makeText(this,
                            "Exported " + tasks.size() + " tasks to Downloads",
                            Toast.LENGTH_SHORT).show(),
                    ex -> Toast.makeText(this,
                            "Export failed: " + ex.getMessage(),
                            Toast.LENGTH_LONG).show());
        }));
        root.addView(actionRow("🔄  Reset to Defaults", "#EF4444", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Reset Settings")
                        .setMessage("Reset all settings to their defaults?")
                        .setPositiveButton("Reset", (d, w) -> {
                            settings.reset();
                            Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()));
    }

    // ── Save on back ──────────────────────────────────────────────

    private void saveAndFinish() {
        settings.save();
        finish();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
        super.onBackPressed();
    }

    // ── Row builders ──────────────────────────────────────────────

    interface StringConsumer { void accept(String value); }
    interface BoolConsumer   { void accept(boolean value); }

    private View spinnerRow(String label, String[] options, String current,
                            StringConsumer onChange) {
        LinearLayout row = rowContainer();

        TextView tvLabel = labelTv(label);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        // Find current index
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(current)) { spinner.setSelection(i); break; }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (first) { first = false; return; }
                onChange.accept(options[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        row.addView(spinner);
        return row;
    }

    private View toggleRow(String label, boolean current, BoolConsumer onChange) {
        LinearLayout row = rowContainer();

        TextView tvLabel = labelTv(label);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        Switch sw = new Switch(this);
        sw.setChecked(current);
        sw.setOnCheckedChangeListener((btn, checked) -> onChange.accept(checked));
        row.addView(sw);
        return row;
    }

    private View editTextRow(String label, String current, StringConsumer onChange) {
        LinearLayout row = rowContainer();

        TextView tvLabel = labelTv(label);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        EditText et = new EditText(this);
        et.setText(current);
        et.setTextColor(Color.WHITE);
        et.setTextSize(14);
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setPadding(dp(8), dp(4), dp(8), dp(4));
        et.setMinWidth(dp(100));
        et.setSingleLine(true);
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) onChange.accept(et.getText().toString().trim());
        });
        row.addView(et);
        return row;
    }

    private View actionRow(String label, String color, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);

        row.setOnClickListener(v -> action.run());
        return row;
    }

    // ── Generic row container ─────────────────────────────────────

    private LinearLayout rowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        return row;
    }

    private TextView labelTv(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        return tv;
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextColor(Color.parseColor("#9E9E9E"));
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(dp(4), dp(16), dp(4), dp(6));
        return tv;
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(8), 0, dp(4));
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.parseColor("#2A2A4A"));
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String dayOfWeekName(int day) {
        switch (day) {
            case java.util.Calendar.SUNDAY:   return "Sunday";
            case java.util.Calendar.SATURDAY: return "Saturday";
            default:                          return "Monday";
        }
    }

    private int parseDayOfWeek(String name) {
        switch (name) {
            case "Sunday":   return java.util.Calendar.SUNDAY;
            case "Saturday": return java.util.Calendar.SATURDAY;
            default:         return java.util.Calendar.MONDAY;
        }
    }

    private int safeInt(String val, int def) {
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
