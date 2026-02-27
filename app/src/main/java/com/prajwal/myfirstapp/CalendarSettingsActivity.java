package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * CalendarSettingsActivity - Calendar preference settings.
 *
 * Settings:
 *   - First day of week (Monday/Sunday/Saturday)
 *   - Default view (Month/Week/Day/Agenda)
 *   - Default reminder offset
 *   - Working hours (start/end)
 *   - Default event duration
 *   - Show week numbers
 *   - Show declined events
 *   - Notification sound toggle
 *   - Daily agenda notification toggle + time
 */
public class CalendarSettingsActivity extends AppCompatActivity {

    private static final int COLOR_BG_PRIMARY     = 0xFF0A0E21;
    private static final int COLOR_BG_SURFACE     = 0xFF111827;
    private static final int COLOR_BG_ELEVATED    = 0xFF1E293B;
    private static final int COLOR_TEXT_PRIMARY    = 0xFFF1F5F9;
    private static final int COLOR_TEXT_SECONDARY  = 0xFF94A3B8;
    private static final int COLOR_TEXT_MUTED      = 0xFF64748B;
    private static final int COLOR_ACCENT          = 0xFF3B82F6;
    private static final int COLOR_DIVIDER         = 0xFF1E293B;

    private CalendarRepository repository;
    private CalendarSettings settings;

    // Value display TextViews
    private TextView tvFirstDay, tvDefaultView, tvDefaultReminder;
    private TextView tvWorkStart, tvWorkEnd, tvDefaultDuration;
    private Switch switchWeekNumbers, switchDeclinedEvents;
    private Switch switchNotifSound, switchDailyAgenda;
    private TextView tvAgendaTime;
    private Switch switchWeeklyPreview, switchRecurringExpenses;
    private TextView tvWeeklyPreviewDay, tvWeeklyPreviewTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new CalendarRepository(this);
        settings = repository.getSettings();
        buildUI();
    }

    // =====================================================================
    // UI
    // =====================================================================

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG_PRIMARY);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF0D1117);
        header.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        TextView btnBack = new TextView(this);
        btnBack.setText("\u2190");
        btnBack.setTextColor(COLOR_TEXT_PRIMARY);
        btnBack.setTextSize(20);
        btnBack.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        TextView titleTv = new TextView(this);
        titleTv.setText("\u2699\uFE0F Calendar Settings");
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(18);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setPadding(dpToPx(12), 0, 0, 0);
        header.addView(titleTv);

        mainContainer.addView(header);

        // ---- Display Section ----
        addSectionTitle(mainContainer, "Display");

        LinearLayout displayCard = createSettingsCard();

        // First Day of Week
        tvFirstDay = addPickerRow(displayCard, "First Day of Week",
                getFirstDayLabel(), v -> pickFirstDay());

        addRowDivider(displayCard);

        // Default View
        tvDefaultView = addPickerRow(displayCard, "Default View",
                getDefaultViewLabel(), v -> pickDefaultView());

        addRowDivider(displayCard);

        // Show Week Numbers
        switchWeekNumbers = addSwitchRow(displayCard, "Show Week Numbers",
                settings.showWeekNumbers, (btn, checked) -> {
                    settings.showWeekNumbers = checked;
                    saveSettings();
                });

        addRowDivider(displayCard);

        // Show Declined Events
        switchDeclinedEvents = addSwitchRow(displayCard, "Show Declined Events",
                settings.showDeclinedEvents, (btn, checked) -> {
                    settings.showDeclinedEvents = checked;
                    saveSettings();
                });

        mainContainer.addView(displayCard);

        // ---- Defaults Section ----
        addSectionTitle(mainContainer, "Defaults");

        LinearLayout defaultsCard = createSettingsCard();

        // Default Reminder
        tvDefaultReminder = addPickerRow(defaultsCard, "Default Reminder",
                CalendarEvent.formatReminderOffset(settings.defaultReminderMinutes), v -> pickDefaultReminder());

        addRowDivider(defaultsCard);

        // Default Event Duration
        tvDefaultDuration = addPickerRow(defaultsCard, "Default Event Duration",
                settings.defaultEventDurationMinutes + " minutes", v -> pickDefaultDuration());

        mainContainer.addView(defaultsCard);

        // ---- Working Hours Section ----
        addSectionTitle(mainContainer, "Working Hours");

        LinearLayout workCard = createSettingsCard();

        tvWorkStart = addPickerRow(workCard, "Start Time",
                formatHour(settings.getWorkingHoursStartHour()), v -> pickWorkHour(true));

        addRowDivider(workCard);

        tvWorkEnd = addPickerRow(workCard, "End Time",
                formatHour(settings.getWorkingHoursEndHour()), v -> pickWorkHour(false));

        mainContainer.addView(workCard);

        // ---- Notifications Section ----
        addSectionTitle(mainContainer, "Notifications");

        LinearLayout notifCard = createSettingsCard();

        switchNotifSound = addSwitchRow(notifCard, "Notification Sound",
                settings.notificationSoundEnabled, (btn, checked) -> {
                    settings.notificationSoundEnabled = checked;
                    settings.notificationSound = checked ? "default" : "silent";
                    saveSettings();
                });

        addRowDivider(notifCard);

        switchDailyAgenda = addSwitchRow(notifCard, "Daily Agenda Notification",
                settings.dailyAgendaNotification, (btn, checked) -> {
                    settings.dailyAgendaNotification = checked;
                    saveSettings();
                    if (checked) {
                        CalendarNotificationHelper.scheduleDailyAgenda(this, settings);
                    }
                });

        addRowDivider(notifCard);

        tvAgendaTime = addPickerRow(notifCard, "Daily Agenda Time",
                settings.dailyAgendaTime != null ? settings.dailyAgendaTime : "07:00",
                v -> pickAgendaTime());

        addRowDivider(notifCard);

        switchWeeklyPreview = addSwitchRow(notifCard, "Weekly Preview Notification",
                settings.weeklyPreviewNotification, (btn, checked) -> {
                    settings.weeklyPreviewNotification = checked;
                    saveSettings();
                    if (checked) {
                        CalendarNotificationHelper.scheduleWeeklyPreview(this, settings);
                    }
                });

        addRowDivider(notifCard);

        tvWeeklyPreviewDay = addPickerRow(notifCard, "Weekly Preview Day",
                formatWeekDay(settings.weeklyPreviewDay),
                v -> pickWeeklyPreviewDay());

        addRowDivider(notifCard);

        tvWeeklyPreviewTime = addPickerRow(notifCard, "Weekly Preview Time",
                settings.weeklyPreviewTime != null ? settings.weeklyPreviewTime : "19:00",
                v -> pickWeeklyPreviewTime());

        mainContainer.addView(notifCard);

        // ---- Integration Section ----
        addSectionTitle(mainContainer, "Integrations");

        LinearLayout intCard = createSettingsCard();

        switchRecurringExpenses = addSwitchRow(intCard, "Show Recurring Expenses",
                settings.showRecurringExpenses, (btn, checked) -> {
                    settings.showRecurringExpenses = checked;
                    saveSettings();
                });

        mainContainer.addView(intCard);

        // Bottom spacer
        View bottomSpacer = new View(this);
        bottomSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));
        mainContainer.addView(bottomSpacer);

        scrollView.addView(mainContainer);
        setContentView(scrollView);
    }

    // =====================================================================
    // PICKERS
    // =====================================================================

    private void pickFirstDay() {
        String[] labels = {"Monday", "Sunday", "Saturday"};
        String[] values = {CalendarSettings.FIRST_DAY_MONDAY, CalendarSettings.FIRST_DAY_SUNDAY, CalendarSettings.FIRST_DAY_SATURDAY};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(settings.firstDayOfWeek)) { checked = i; break; }
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("First Day of Week")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.firstDayOfWeek = values[which];
                    tvFirstDay.setText(labels[which]);
                    saveSettings();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDefaultView() {
        String[] labels = {"Month", "Week", "Day", "Agenda"};
        String[] values = {"month", "week", "day", "agenda"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(settings.defaultView)) { checked = i; break; }
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Default View")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.defaultView = values[which];
                    tvDefaultView.setText(labels[which]);
                    saveSettings();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDefaultReminder() {
        int[] presets = CalendarEvent.REMINDER_PRESETS;
        String[] labels = new String[presets.length];
        int checked = 0;
        for (int i = 0; i < presets.length; i++) {
            labels[i] = CalendarEvent.formatReminderOffset(presets[i]);
            if (presets[i] == settings.defaultReminderMinutes) checked = i;
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Default Reminder")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.defaultReminderMinutes = presets[which];
                    tvDefaultReminder.setText(labels[which]);
                    saveSettings();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDefaultDuration() {
        int[] durations = {15, 30, 45, 60, 90, 120};
        String[] labels = {"15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours", "2 hours"};
        int checked = 2; // 60min default
        for (int i = 0; i < durations.length; i++) {
            if (durations[i] == settings.defaultEventDurationMinutes) { checked = i; break; }
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Default Duration")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.defaultEventDurationMinutes = durations[which];
                    tvDefaultDuration.setText(labels[which]);
                    saveSettings();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickWorkHour(boolean isStart) {
        int current = isStart ? settings.getWorkingHoursStartHour() : settings.getWorkingHoursEndHour();
        new android.app.TimePickerDialog(this, R.style.DarkTimePickerDialog,
                (view, hourOfDay, minute) -> {
                    String timeStr = String.format(Locale.US, "%02d:00", hourOfDay);
                    if (isStart) {
                        settings.workingHoursStart = timeStr;
                        tvWorkStart.setText(formatHour(hourOfDay));
                    } else {
                        settings.workingHoursEnd = timeStr;
                        tvWorkEnd.setText(formatHour(hourOfDay));
                    }
                    saveSettings();
                }, current, 0, false).show();
    }

    private void pickAgendaTime() {
        int h = 7, m = 0;
        if (settings.dailyAgendaTime != null) {
            try {
                String[] parts = settings.dailyAgendaTime.split(":");
                h = Integer.parseInt(parts[0]);
                m = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* defaults */ }
        }

        new android.app.TimePickerDialog(this, R.style.DarkTimePickerDialog,
                (view, hourOfDay, minute) -> {
                    settings.dailyAgendaTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                    tvAgendaTime.setText(settings.dailyAgendaTime);
                    saveSettings();
                    if (settings.dailyAgendaNotification) {
                        CalendarNotificationHelper.scheduleDailyAgenda(this, settings);
                    }
                }, h, m, false).show();
    }

    private void pickWeeklyPreviewDay() {
        String[] labels = {"Sunday", "Saturday", "Friday", "Monday"};
        String[] values = {"sunday", "saturday", "friday", "monday"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(settings.weeklyPreviewDay)) { checked = i; break; }
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Weekly Preview Day")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.weeklyPreviewDay = values[which];
                    tvWeeklyPreviewDay.setText(labels[which]);
                    saveSettings();
                    if (settings.weeklyPreviewNotification) {
                        CalendarNotificationHelper.scheduleWeeklyPreview(this, settings);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickWeeklyPreviewTime() {
        int h = 19, m = 0;
        if (settings.weeklyPreviewTime != null) {
            try {
                String[] parts = settings.weeklyPreviewTime.split(":");
                h = Integer.parseInt(parts[0]);
                m = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* defaults */ }
        }

        new android.app.TimePickerDialog(this, R.style.DarkTimePickerDialog,
                (view, hourOfDay, minute) -> {
                    settings.weeklyPreviewTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                    tvWeeklyPreviewTime.setText(settings.weeklyPreviewTime);
                    saveSettings();
                    if (settings.weeklyPreviewNotification) {
                        CalendarNotificationHelper.scheduleWeeklyPreview(this, settings);
                    }
                }, h, m, false).show();
    }

    private String formatWeekDay(String day) {
        if (day == null) return "Sunday";
        switch (day.toLowerCase()) {
            case "sunday": return "Sunday";
            case "saturday": return "Saturday";
            case "friday": return "Friday";
            case "monday": return "Monday";
            default: return "Sunday";
        }
    }

    // =====================================================================
    // SAVE
    // =====================================================================

    private void saveSettings() {
        repository.saveSettings(settings);
    }

    // =====================================================================
    // WIDGET HELPERS
    // =====================================================================

    private LinearLayout createSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4));
        card.setLayoutParams(params);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(14));
        bg.setColor(COLOR_BG_SURFACE);
        card.setBackground(bg);
        return card;
    }

    private TextView addPickerRow(LinearLayout parent, String label, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        row.setOnClickListener(listener);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(COLOR_TEXT_PRIMARY);
        labelTv.setTextSize(14);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(COLOR_ACCENT);
        valueTv.setTextSize(13);
        row.addView(valueTv);

        TextView arrow = new TextView(this);
        arrow.setText(" \u203A");
        arrow.setTextColor(COLOR_TEXT_MUTED);
        arrow.setTextSize(16);
        row.addView(arrow);

        parent.addView(row);
        return valueTv;
    }

    private Switch addSwitchRow(LinearLayout parent, String label, boolean checked,
                                CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(COLOR_TEXT_PRIMARY);
        labelTv.setTextSize(14);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelTv);

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);

        parent.addView(row);
        return toggle;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(COLOR_TEXT_MUTED);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dpToPx(20), dpToPx(16), 0, dpToPx(4));
        parent.addView(tv);
    }

    private void addRowDivider(LinearLayout parent) {
        View div = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        dlp.setMargins(dpToPx(16), 0, dpToPx(16), 0);
        div.setLayoutParams(dlp);
        div.setBackgroundColor(COLOR_DIVIDER);
        parent.addView(div);
    }

    // =====================================================================
    // LABEL HELPERS
    // =====================================================================

    private String getFirstDayLabel() {
        if (settings.firstDayOfWeek == null) return "Monday";
        switch (settings.firstDayOfWeek) {
            case CalendarSettings.FIRST_DAY_SUNDAY: return "Sunday";
            case CalendarSettings.FIRST_DAY_SATURDAY: return "Saturday";
            default: return "Monday";
        }
    }

    private String getDefaultViewLabel() {
        if (settings.defaultView == null) return "Month";
        switch (settings.defaultView) {
            case "week": return "Week";
            case "day": return "Day";
            case "agenda": return "Agenda";
            default: return "Month";
        }
    }

    private String formatHour(int hour) {
        return String.format(Locale.US, "%d:00 %s",
                hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour),
                hour < 12 ? "AM" : "PM");
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
