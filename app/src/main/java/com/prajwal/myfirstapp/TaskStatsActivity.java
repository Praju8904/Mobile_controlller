package com.prajwal.myfirstapp;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Productivity / Insights dashboard.
 *
 * Stats displayed:
 *   • Completed today / Due today / Current streak
 *   • Completion rate (ring)
 *   • Last 7 days bar chart
 *   • Average completion time / Most productive day
 *   • Tasks by category (horizontal bars)
 *   • Overdue rate
 */
public class TaskStatsActivity extends AppCompatActivity {

    private TaskRepository repo;

    // Summary cards
    private TextView tvCompletedToday, tvTotalToday, tvStreak;

    // Completion ring
    private TextView tvCompletionPercent, tvCompletionDetail;
    private View viewRingBg;

    // 7-day chart
    private LinearLayout barChartContainer, barChartLabels;

    // Metrics
    private TextView tvAvgTime, tvBestDay;

    // Category chart
    private LinearLayout categoryChartContainer;

    // Overdue
    private TextView tvOverdueRate, tvOverdueDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_stats);

        repo = new TaskRepository(this);

        initViews();
        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        loadStats();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvCompletedToday = findViewById(R.id.tvStatsCompletedToday);
        tvTotalToday = findViewById(R.id.tvStatsTotalToday);
        tvStreak = findViewById(R.id.tvStatsStreak);

        tvCompletionPercent = findViewById(R.id.tvCompletionPercent);
        tvCompletionDetail = findViewById(R.id.tvCompletionDetail);
        viewRingBg = findViewById(R.id.viewCompletionRingBg);

        barChartContainer = findViewById(R.id.barChartContainer);
        barChartLabels = findViewById(R.id.barChartLabels);

        tvAvgTime = findViewById(R.id.tvStatsAvgTime);
        tvBestDay = findViewById(R.id.tvStatsBestDay);

        categoryChartContainer = findViewById(R.id.categoryChartContainer);

        tvOverdueRate = findViewById(R.id.tvOverdueRate);
        tvOverdueDetail = findViewById(R.id.tvOverdueRateDetail);
    }

    private void loadStats() {
        // ── Summary Cards ────────────────────────────────────────
        tvCompletedToday.setText(String.valueOf(repo.getCompletedTodayCount()));
        tvTotalToday.setText(String.valueOf(repo.getTotalTodayCount()));
        tvStreak.setText(String.valueOf(repo.getCurrentStreak()));

        // ── Completion Rate ──────────────────────────────────────
        float rate = repo.getCompletionRate();
        int pct = Math.round(rate * 100);
        tvCompletionPercent.setText(pct + "%");

        int totalCompleted = repo.getTotalCompletedCount();
        int totalActive = repo.getTotalActiveCount();
        tvCompletionDetail.setText(totalCompleted + " completed out of " + totalActive + " total");

        // Draw ring background (circular outline via GradientDrawable)
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(8), Color.parseColor("#1A60A5FA"));
        viewRingBg.setBackground(ring);

        // Overlay progress ring (simplified — full ring = 100%)
        // For a proper circular progress we'd need a custom view;
        // here we tint the ring based on completion
        int ringColor = pct >= 70 ? Color.parseColor("#66BB6A")
                      : pct >= 40 ? Color.parseColor("#F59E0B")
                      : Color.parseColor("#EF5350");
        GradientDrawable progressRing = new GradientDrawable();
        progressRing.setShape(GradientDrawable.OVAL);
        progressRing.setColor(Color.TRANSPARENT);
        progressRing.setStroke(dp(8), ringColor);
        viewRingBg.setBackground(progressRing);

        // ── 7-Day Bar Chart ──────────────────────────────────────
        build7DayChart();

        // ── Avg Time & Best Day ──────────────────────────────────
        int avgMins = repo.getAverageCompletionMinutes();
        if (avgMins > 0) {
            if (avgMins >= 1440) {
                tvAvgTime.setText((avgMins / 1440) + "d " + ((avgMins % 1440) / 60) + "h");
            } else if (avgMins >= 60) {
                tvAvgTime.setText((avgMins / 60) + "h " + (avgMins % 60) + "m");
            } else {
                tvAvgTime.setText(avgMins + " min");
            }
        } else {
            tvAvgTime.setText("—");
        }

        int bestDay = repo.getMostProductiveDay();
        if (bestDay >= 0) {
            String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            tvBestDay.setText(dayNames[bestDay]);
        } else {
            tvBestDay.setText("—");
        }

        // ── Category Chart ───────────────────────────────────────
        buildCategoryChart();

        // ── Overdue Rate ─────────────────────────────────────────
        int overdueCount = repo.getOverdueCount();
        int pendingCount = repo.getPendingCount();
        if (pendingCount > 0) {
            float overdueRate = (float) overdueCount / pendingCount * 100;
            tvOverdueRate.setText(String.format(Locale.US, "%.0f%%", overdueRate));
            tvOverdueDetail.setText(overdueCount + " overdue out of " + pendingCount + " pending");
        } else {
            tvOverdueRate.setText("0%");
            tvOverdueDetail.setText("No pending tasks");
        }
    }

    // ─── 7-Day Bar Chart ─────────────────────────────────────────

    private void build7DayChart() {
        barChartContainer.removeAllViews();
        barChartLabels.removeAllViews();

        int[] data = repo.getCompletedLast7Days();
        int max = 1;
        for (int v : data) if (v > max) max = v;

        // Day labels (Mon, Tue, etc.)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", Locale.US);

        for (int i = 0; i < 7; i++) {
            int value = data[i];
            float ratio = (float) value / max;

            // Bar column
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colLp.setMarginStart(dp(3));
            colLp.setMarginEnd(dp(3));
            col.setLayoutParams(colLp);

            // Count label
            TextView tvCount = new TextView(this);
            tvCount.setText(String.valueOf(value));
            tvCount.setTextSize(10);
            tvCount.setTextColor(Color.parseColor("#94A3B8"));
            tvCount.setGravity(Gravity.CENTER);
            col.addView(tvCount);

            // Bar fill
            View bar = new View(this);
            int barHeight = value > 0 ? Math.max(dp(4), (int) (dp(80) * ratio)) : dp(4);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    dp(24), barHeight);
            barLp.topMargin = dp(4);
            bar.setLayoutParams(barLp);
            bar.setBackgroundResource(value > 0
                    ? R.drawable.task_bar_chart_fill
                    : R.drawable.task_bar_chart_empty);
            col.addView(bar);

            barChartContainer.addView(col);

            // Day label
            TextView label = new TextView(this);
            label.setText(dayFmt.format(cal.getTime()));
            label.setTextSize(10);
            label.setTextColor(i == 6 ? Color.parseColor("#60A5FA") : Color.parseColor("#6B7280"));
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelLp);
            barChartLabels.addView(label);

            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    // ─── Category Chart (horizontal bars) ────────────────────────

    private void buildCategoryChart() {
        categoryChartContainer.removeAllViews();
        LinkedHashMap<String, Integer> catMap = repo.getTasksByCategory();

        if (catMap.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No data yet");
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setTextSize(13);
            categoryChartContainer.addView(empty);
            return;
        }

        int maxVal = 1;
        for (int v : catMap.values()) if (v > maxVal) maxVal = v;

        String[] barColors = {"#3B82F6", "#EF4444", "#10B981", "#F59E0B", "#A855F7", "#06B6D4", "#EC4899", "#64748B"};
        int colorIdx = 0;

        for (Map.Entry<String, Integer> entry : catMap.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            // Category name
            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setTextColor(Color.parseColor("#94A3B8"));
            tvName.setTextSize(13);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(dp(80),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tvName.setLayoutParams(nameLp);
            row.addView(tvName);

            // Bar
            View bar = new View(this);
            float ratio = (float) entry.getValue() / maxVal;
            int barWidth = Math.max(dp(8), (int) (dp(150) * ratio));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(barWidth, dp(14));
            barLp.setMarginEnd(dp(8));
            bar.setLayoutParams(barLp);

            String color = barColors[colorIdx % barColors.length];
            GradientDrawable barBg = new GradientDrawable();
            barBg.setShape(GradientDrawable.RECTANGLE);
            barBg.setCornerRadius(dp(4));
            barBg.setColor(Color.parseColor(color));
            bar.setBackground(barBg);
            row.addView(bar);

            // Count
            TextView tvCount = new TextView(this);
            tvCount.setText(String.valueOf(entry.getValue()));
            tvCount.setTextColor(Color.parseColor("#F1F5F9"));
            tvCount.setTextSize(13);
            tvCount.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(tvCount);

            categoryChartContainer.addView(row);
            colorIdx++;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
