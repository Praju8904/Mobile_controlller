package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Meeting analytics screen.
 */
public class MeetingAnalyticsActivity extends AppCompatActivity {

    private MeetingRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = MeetingRepository.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1A1A2E"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, 0, 0, dp(20));

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        backLp.setMarginEnd(dp(12));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Meeting Analytics");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(22);
        tvTitle.setTypeface(null, Typeface.BOLD);
        toolbar.addView(tvTitle);

        root.addView(toolbar);

        // ── Compute stats ─────────────────────────────────────────
        List<Meeting> all = repo.getAllMeetings();
        long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long weekStart = cal.getTimeInMillis();

        Calendar calMonth = Calendar.getInstance();
        calMonth.set(Calendar.DAY_OF_MONTH, 1);
        calMonth.set(Calendar.HOUR_OF_DAY, 0);
        calMonth.set(Calendar.MINUTE, 0);
        calMonth.set(Calendar.SECOND, 0);
        calMonth.set(Calendar.MILLISECOND, 0);
        long monthStart = calMonth.getTimeInMillis();

        int weekCount = 0, monthCount = 0;
        long totalDurationMonthMs = 0;
        long totalDurationAll = 0;
        int durationCount = 0;
        Map<String, Integer> typeCount = new HashMap<>();
        int totalActionItems = 0, completedActionItems = 0;

        for (Meeting m : all) {
            if (Meeting.STATUS_CANCELLED.equals(m.status)) continue;
            if (m.startDateTime >= weekStart && m.startDateTime <= now) weekCount++;
            if (m.startDateTime >= monthStart && m.startDateTime <= now) {
                monthCount++;
                if (m.endDateTime > m.startDateTime) {
                    totalDurationMonthMs += (m.endDateTime - m.startDateTime);
                }
            }
            if (m.endDateTime > m.startDateTime) {
                totalDurationAll += (m.endDateTime - m.startDateTime);
                durationCount++;
            }
            // Type breakdown
            String type = m.type != null ? m.type : Meeting.TYPE_OTHER;
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
            // Action items
            if (m.actionItems != null) {
                for (ActionItem ai : m.actionItems) {
                    totalActionItems++;
                    if (ai.isCompleted) completedActionItems++;
                }
            }
        }

        long avgDurationMs = durationCount > 0 ? totalDurationAll / durationCount : 0;
        long totalMinutesMonth = totalDurationMonthMs / 60_000;
        long avgMinutes = avgDurationMs / 60_000;

        // ── Stats cards ───────────────────────────────────────────
        root.addView(sectionHeader("Overview"));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statsRow.addView(statCard("This Week", String.valueOf(weekCount), "meetings", "#6C63FF"));
        statsRow.addView(statCard("This Month", String.valueOf(monthCount), "meetings", "#007AFF"));
        root.addView(statsRow);

        root.addView(metricCard("Average Duration",
                formatDuration(avgMinutes), "#FF9500"));
        root.addView(metricCard("Total Time This Month",
                formatDurationLong(totalMinutesMonth), "#34C759"));

        // ── Action item completion ────────────────────────────────
        root.addView(sectionHeader("Productivity"));
        String completionText = totalActionItems > 0
                ? completedActionItems + " / " + totalActionItems + " ("
                  + (int) Math.round(completedActionItems * 100.0 / totalActionItems) + "%)"
                : "No action items";
        root.addView(metricCard("Action Item Completion", completionText, "#F59E0B"));

        // ── Type breakdown ────────────────────────────────────────
        root.addView(sectionHeader("Meeting Types"));
        int total = all.size();
        String[] types = {Meeting.TYPE_VIDEO_CALL, Meeting.TYPE_IN_PERSON,
                          Meeting.TYPE_PHONE_CALL, Meeting.TYPE_OTHER};
        String[] typeLabels = {"Video Call", "In-Person", "Phone", "Other"};
        String[] typeColors = {"#6C63FF", "#34C759", "#FF9500", "#8E8E93"};
        for (int i = 0; i < types.length; i++) {
            int cnt = typeCount.getOrDefault(types[i], 0);
            int pct = total > 0 ? cnt * 100 / total : 0;
            root.addView(typeRow(typeLabels[i], cnt, pct, typeColors[i]));
        }
    }

    // ── Builders ──────────────────────────────────────────────────

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9E9E9E"));
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setPadding(dp(4), dp(20), dp(4), dp(8));
        return tv;
    }

    private LinearLayout statCard(String label, String value, String sub, String color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(Color.parseColor(color));
        tvVal.setTextSize(32);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setGravity(Gravity.CENTER);
        card.addView(tvVal);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(13);
        tvLabel.setGravity(Gravity.CENTER);
        card.addView(tvLabel);

        TextView tvSub = new TextView(this);
        tvSub.setText(sub);
        tvSub.setTextColor(Color.parseColor("#9E9E9E"));
        tvSub.setTextSize(11);
        tvSub.setGravity(Gravity.CENTER);
        card.addView(tvSub);

        return card;
    }

    private LinearLayout metricCard(String label, String value, String color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.setMargins(0, 0, dp(12), 0);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(color));
        dot.setBackground(dotBg);
        card.addView(dot);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#9E9E9E"));
        tvLabel.setTextSize(14);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setTextSize(14);
        tvValue.setTypeface(null, Typeface.BOLD);
        card.addView(tvValue);

        return card;
    }

    private LinearLayout typeRow(String label, int count, int pct, String color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(8));
        row.setBackground(bg);

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor(color));
        dot.setBackground(dotBg);
        row.addView(dot);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(14);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        TextView tvPct = new TextView(this);
        tvPct.setText(pct + "% (" + count + ")");
        tvPct.setTextColor(Color.parseColor("#9E9E9E"));
        tvPct.setTextSize(13);
        row.addView(tvPct);

        return row;
    }

    private String formatDuration(long minutes) {
        if (minutes <= 0) return "—";
        if (minutes < 60) return minutes + " min";
        return (minutes / 60) + " hr " + (minutes % 60 > 0 ? (minutes % 60) + " min" : "");
    }

    private String formatDurationLong(long minutes) {
        if (minutes <= 0) return "0 minutes";
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours == 0) return mins + " minutes";
        return hours + " hour" + (hours != 1 ? "s" : "") +
                (mins > 0 ? " " + mins + " minutes" : "");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
