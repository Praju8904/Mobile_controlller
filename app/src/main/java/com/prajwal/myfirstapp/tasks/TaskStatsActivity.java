package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Productivity Dashboard — Complete Redesign.
 *
 * Stats displayed:
 *   • Focus Score (with breakdown)
 *   • Completed today / Due today / Current streak
 *   • Last 7 days bar chart
 *   • Task status distribution
 *   • Priority distribution
 *   • Average completion time / Most productive day
 *   • Tasks by category (horizontal bars with progress)
 *   • Overdue rate
 *   • Personal records
 *   • Daily activity heatmap
 */
public class TaskStatsActivity extends AppCompatActivity {

    private TaskRepository repo;

    // Summary cards
    private TextView tvCompletedToday, tvTotalToday, tvStreak;

    // Focus Score / Completion ring
    private TextView tvCompletionPercent, tvCompletionDetail;
    private View viewRingBg;
    private LinearLayout scoreBreakdownContainer;

    // 7-day chart
    private LinearLayout barChartContainer, barChartLabels;

    // Status & Priority distribution
    private LinearLayout statusDistributionContainer, priorityDistributionContainer;

    // Metrics
    private TextView tvAvgTime, tvBestDay;

    // Category chart
    private LinearLayout categoryChartContainer;

    // Overdue
    private TextView tvOverdueRate, tvOverdueDetail;

    // Personal Records & Heatmap
    private LinearLayout personalRecordsContainer, heatmapContainer;

    // Date range
    private LinearLayout dateRangeContainer;

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
        scoreBreakdownContainer = findViewById(R.id.scoreBreakdownContainer);

        barChartContainer = findViewById(R.id.barChartContainer);
        barChartLabels = findViewById(R.id.barChartLabels);

        statusDistributionContainer = findViewById(R.id.statusDistributionContainer);
        priorityDistributionContainer = findViewById(R.id.priorityDistributionContainer);

        tvAvgTime = findViewById(R.id.tvStatsAvgTime);
        tvBestDay = findViewById(R.id.tvStatsBestDay);

        categoryChartContainer = findViewById(R.id.categoryChartContainer);

        tvOverdueRate = findViewById(R.id.tvOverdueRate);
        tvOverdueDetail = findViewById(R.id.tvOverdueRateDetail);

        personalRecordsContainer = findViewById(R.id.personalRecordsContainer);
        heatmapContainer = findViewById(R.id.heatmapContainer);
        dateRangeContainer = findViewById(R.id.dateRangeContainer);

        buildDateRangeSelector();
    }

    private void buildDateRangeSelector() {
        if (dateRangeContainer == null) return;
        dateRangeContainer.removeAllViews();

        String[] ranges = {"Today", "This Week", "This Month", "All Time"};
        for (int i = 0; i < ranges.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(ranges[i]);
            chip.setTextSize(12);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            if (i == 2) { // Default: This Month
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setShape(GradientDrawable.RECTANGLE);
                activeBg.setCornerRadius(dp(20));
                activeBg.setColors(new int[]{Color.parseColor("#3B82F6"), Color.parseColor("#6366F1")});
                activeBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                chip.setBackground(activeBg);
                chip.setTextColor(Color.WHITE);
            } else {
                GradientDrawable inactiveBg = new GradientDrawable();
                inactiveBg.setShape(GradientDrawable.RECTANGLE);
                inactiveBg.setCornerRadius(dp(20));
                inactiveBg.setColor(Color.parseColor("#1A1F2E"));
                inactiveBg.setStroke(1, Color.parseColor("#20FFFFFF"));
                chip.setBackground(inactiveBg);
                chip.setTextColor(Color.parseColor("#94A3B8"));
            }

            dateRangeContainer.addView(chip);
        }
    }

    private void loadStats() {
        // ── Summary Cards ────────────────────────────────────────
        tvCompletedToday.setText(String.valueOf(repo.getCompletedTodayCount()));
        tvTotalToday.setText(String.valueOf(repo.getTotalTodayCount()));
        tvStreak.setText(String.valueOf(repo.getCurrentStreak()));

        // ── Focus Score (replaces simple completion rate) ────────
        int focusScore = calculateFocusScore();
        float completionRate = repo.getCompletionRate();
        int pct = Math.round(completionRate * 100);

        // Animate the score number
        ValueAnimator scoreAnim = ValueAnimator.ofInt(0, focusScore);
        scoreAnim.setDuration(800);
        scoreAnim.addUpdateListener(a -> tvCompletionPercent.setText(String.valueOf(a.getAnimatedValue())));
        scoreAnim.start();

        int totalCompleted = repo.getTotalCompletedCount();
        int totalActive = repo.getTotalActiveCount();
        int streak = repo.getCurrentStreak();
        int overdueCount = repo.getOverdueCount();

        // Human-readable assessment
        String assessment;
        if (pct >= 80) assessment = "Outstanding! You're completing " + pct + "% of tasks on time.";
        else if (pct >= 60) assessment = "Great work! " + pct + "% completion rate — keep it up!";
        else if (pct >= 40) assessment = "You're at " + pct + "% completion — try to stay on top!";
        else assessment = "Room to improve — " + pct + "% completion rate.";
        tvCompletionDetail.setText(assessment);

        // Draw ring
        int ringColor = focusScore >= 70 ? Color.parseColor("#10B981")
                       : focusScore >= 40 ? Color.parseColor("#F59E0B")
                       : Color.parseColor("#EF4444");
        GradientDrawable progressRing = new GradientDrawable();
        progressRing.setShape(GradientDrawable.OVAL);
        progressRing.setColor(Color.TRANSPARENT);
        progressRing.setStroke(dp(10), ringColor);
        viewRingBg.setBackground(progressRing);

        // Score breakdown
        if (scoreBreakdownContainer != null) {
            scoreBreakdownContainer.removeAllViews();
            addScoreBreakdownRow("Completion Rate", pct + "%", Color.parseColor("#10B981"));
            addScoreBreakdownRow("Streak Bonus", "+" + Math.min(streak * 5, 25), Color.parseColor("#F59E0B"));
            if (totalActive > 0) {
                float overdueRate = (float) overdueCount / totalActive;
                addScoreBreakdownRow("Overdue Penalty", "-" + Math.round(overdueRate * 25), Color.parseColor("#EF4444"));
            }
            addScoreBreakdownRow(totalCompleted + " completed / " + totalActive + " total", "", Color.parseColor("#6B7280"));
        }

        // ── 7-Day Bar Chart ──────────────────────────────────────
        build7DayChart();

        // ── Status Distribution ──────────────────────────────────
        buildStatusDistribution();

        // ── Priority Distribution ────────────────────────────────
        buildPriorityDistribution();

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
        int pendingCount = repo.getPendingCount();
        if (pendingCount > 0) {
            float overdRate = (float) overdueCount / pendingCount * 100;
            tvOverdueRate.setText(String.format(Locale.US, "%.0f%%", overdRate));
            tvOverdueDetail.setText(overdueCount + " overdue out of " + pendingCount + " pending");
        } else {
            tvOverdueRate.setText("0%");
            tvOverdueDetail.setText("No pending tasks");
        }

        // ── Personal Records ─────────────────────────────────────
        buildPersonalRecords();

        // ── Daily Heatmap ────────────────────────────────────────
        buildDailyHeatmap();
    }

    private int calculateFocusScore() {
        float completionRate = repo.getCompletionRate();
        int totalActive = repo.getTotalActiveCount();
        int overdueCount = repo.getOverdueCount();
        int streak = repo.getCurrentStreak();

        int score = Math.round(completionRate * 50);
        score += Math.min(streak * 5, 25);
        if (totalActive > 0) {
            float overdueRate = (float) overdueCount / totalActive;
            score -= Math.round(overdueRate * 25);
        }
        return Math.max(0, Math.min(100, score));
    }

    private void addScoreBreakdownRow(String label, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#94A3B8"));
        tvLabel.setTextSize(12);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        if (!value.isEmpty()) {
            TextView tvValue = new TextView(this);
            tvValue.setText(value);
            tvValue.setTextColor(color);
            tvValue.setTextSize(12);
            tvValue.setTypeface(null, Typeface.BOLD);
            row.addView(tvValue);
        }

        scoreBreakdownContainer.addView(row);
    }

    // ─── 7-Day Bar Chart ─────────────────────────────────────────

    private void build7DayChart() {
        barChartContainer.removeAllViews();
        barChartLabels.removeAllViews();

        int[] data = repo.getCompletedLast7Days();
        int max = 1;
        for (int v : data) if (v > max) max = v;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", Locale.US);

        for (int i = 0; i < 7; i++) {
            int value = data[i];
            float ratio = (float) value / max;

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

            // Bar fill with gradient
            View bar = new View(this);
            int barHeight = value > 0 ? Math.max(dp(4), (int) (dp(80) * ratio)) : dp(4);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(24), barHeight);
            barLp.topMargin = dp(4);
            bar.setLayoutParams(barLp);

            GradientDrawable barBg = new GradientDrawable();
            barBg.setShape(GradientDrawable.RECTANGLE);
            barBg.setCornerRadius(dp(4));
            if (i == 6) {
                // Today — brighter green
                barBg.setColors(new int[]{Color.parseColor("#34D399"), Color.parseColor("#10B981")});
            } else if (value > 0) {
                barBg.setColors(new int[]{Color.parseColor("#10B981"), Color.parseColor("#059669")});
            } else {
                barBg.setColor(Color.parseColor("#1A1F2E"));
            }
            barBg.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
            bar.setBackground(barBg);
            col.addView(bar);

            barChartContainer.addView(col);

            // Day label
            TextView label = new TextView(this);
            label.setText(dayFmt.format(cal.getTime()));
            label.setTextSize(10);
            label.setTextColor(i == 6 ? Color.parseColor("#10B981") : Color.parseColor("#6B7280"));
            label.setGravity(Gravity.CENTER);
            if (i == 6) label.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelLp);
            barChartLabels.addView(label);

            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    // ─── Status Distribution ─────────────────────────────────────

    private void buildStatusDistribution() {
        if (statusDistributionContainer == null) return;
        statusDistributionContainer.removeAllViews();

        int active = repo.getTotalActiveCount() - repo.getOverdueCount();
        int overdue = repo.getOverdueCount();
        int completed = repo.getTotalCompletedCount();
        int starred = repo.getStarredCount();
        int total = active + overdue + completed;
        if (total == 0) total = 1;

        String[][] items = {
                {"Active", String.valueOf(Math.max(0, active)), "#3B82F6"},
                {"Overdue", String.valueOf(overdue), "#EF4444"},
                {"Completed", String.valueOf(completed), "#10B981"},
                {"Starred", String.valueOf(starred), "#F59E0B"}
        };

        for (String[] item : items) {
            int val = Integer.parseInt(item[1]);
            float pct = (float) val / total;
            addDistributionBar(statusDistributionContainer, item[0], val, pct, item[2]);
        }
    }

    // ─── Priority Distribution ───────────────────────────────────

    private void buildPriorityDistribution() {
        if (priorityDistributionContainer == null) return;
        priorityDistributionContainer.removeAllViews();

        List<Task> activeTasks = repo.filterTasks("All");
        int urgent = 0, high = 0, medium = 0, low = 0, none = 0;
        for (Task t : activeTasks) {
            if (t.isCompleted()) continue;
            switch (t.priority != null ? t.priority : "") {
                case "URGENT": urgent++; break;
                case "HIGH": high++; break;
                case "NORMAL": medium++; break;
                case "LOW": low++; break;
                default: none++; break;
            }
        }

        int total = urgent + high + medium + low + none;
        if (total == 0) total = 1;

        addDistributionBar(priorityDistributionContainer, "Urgent", urgent, (float) urgent / total, "#EF4444");
        addDistributionBar(priorityDistributionContainer, "High", high, (float) high / total, "#F97316");
        addDistributionBar(priorityDistributionContainer, "Medium", medium, (float) medium / total, "#3B82F6");
        addDistributionBar(priorityDistributionContainer, "Low", low, (float) low / total, "#9CA3AF");
        addDistributionBar(priorityDistributionContainer, "None", none, (float) none / total, "#4B5563");
    }

    private void addDistributionBar(LinearLayout container, String label, int count, float pct, String color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        // Label
        TextView tvName = new TextView(this);
        tvName.setText(label);
        tvName.setTextColor(Color.parseColor("#94A3B8"));
        tvName.setTextSize(12);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(dp(70),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvName);

        // Progress bar
        LinearLayout barContainer = new LinearLayout(this);
        barContainer.setLayoutParams(new LinearLayout.LayoutParams(0,
                dp(12), 1f));
        barContainer.setPadding(0, 0, dp(8), 0);

        View bar = new View(this);
        int barWidth = Math.max(dp(4), (int) (dp(150) * pct));
        bar.setLayoutParams(new LinearLayout.LayoutParams(barWidth, dp(12)));

        GradientDrawable barBg = new GradientDrawable();
        barBg.setShape(GradientDrawable.RECTANGLE);
        barBg.setCornerRadius(dp(6));
        barBg.setColor(Color.parseColor(color));
        bar.setBackground(barBg);
        barContainer.addView(bar);
        row.addView(barContainer);

        // Count
        TextView tvCount = new TextView(this);
        tvCount.setText(String.valueOf(count));
        tvCount.setTextColor(Color.parseColor("#F1F5F9"));
        tvCount.setTextSize(12);
        tvCount.setTypeface(null, Typeface.BOLD);
        tvCount.setLayoutParams(new LinearLayout.LayoutParams(dp(30),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(tvCount);

        container.addView(row);
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
            float ratio = (float) entry.getValue() / maxVal;
            addDistributionBar(categoryChartContainer, entry.getKey(), entry.getValue(), ratio,
                    barColors[colorIdx % barColors.length]);
            colorIdx++;
        }
    }

    // ─── Personal Records ────────────────────────────────────────

    private void buildPersonalRecords() {
        if (personalRecordsContainer == null) return;
        personalRecordsContainer.removeAllViews();

        int totalCompleted = repo.getTotalCompletedCount();
        int streak = repo.getCurrentStreak();
        int completionPct = Math.round(repo.getCompletionRate() * 100);

        addRecordCard("🏆", "Total Done", String.valueOf(totalCompleted));
        addRecordCard("🔥", "Best Streak", streak + " days");
        addRecordCard("📊", "Completion", completionPct + "%");
    }

    private void addRecordCard(String icon, String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.task_record_card_bg);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cardLp.setMarginEnd(dp(8));
        card.setLayoutParams(cardLp);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(20);
        tvIcon.setGravity(Gravity.CENTER);
        card.addView(tvIcon);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(Color.parseColor("#F1F5F9"));
        tvValue.setTextSize(18);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valLp.topMargin = dp(4);
        tvValue.setLayoutParams(valLp);
        card.addView(tvValue);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#6B7280"));
        tvLabel.setTextSize(11);
        tvLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblLp.topMargin = dp(2);
        tvLabel.setLayoutParams(lblLp);
        card.addView(tvLabel);

        personalRecordsContainer.addView(card);
    }

    // ─── Daily Heatmap ───────────────────────────────────────────

    private void buildDailyHeatmap() {
        if (heatmapContainer == null) return;
        heatmapContainer.removeAllViews();

        int[] last7 = repo.getCompletedLast7Days();

        // Build a simple 4-week heatmap (28 days, 7 columns × 4 rows)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -27); // Start from 28 days ago

        // Day labels row
        LinearLayout dayLabels = new LinearLayout(this);
        dayLabels.setOrientation(LinearLayout.HORIZONTAL);
        dayLabels.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String d : days) {
            TextView tv = new TextView(this);
            tv.setText(d);
            tv.setTextColor(Color.parseColor("#4B5563"));
            tv.setTextSize(9);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            dayLabels.addView(tv);
        }
        heatmapContainer.addView(dayLabels);

        // Heatmap grid (4 rows × 7 columns)
        for (int week = 0; week < 4; week++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(3);
            row.setLayoutParams(rowLp);

            for (int day = 0; day < 7; day++) {
                View cell = new View(this);
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(20), 1f);
                cellLp.setMarginStart(dp(2));
                cellLp.setMarginEnd(dp(2));
                cell.setLayoutParams(cellLp);

                // Use the 7-day data for the last week, show empty for older weeks
                int completions = 0;
                if (week == 3) {
                    completions = last7[day];
                }

                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setShape(GradientDrawable.RECTANGLE);
                cellBg.setCornerRadius(dp(3));

                if (completions == 0) {
                    cellBg.setColor(Color.parseColor("#1A1F2E"));
                } else if (completions <= 1) {
                    cellBg.setColor(Color.parseColor("#064E3B"));
                } else if (completions <= 3) {
                    cellBg.setColor(Color.parseColor("#059669"));
                } else {
                    cellBg.setColor(Color.parseColor("#34D399"));
                }
                cell.setBackground(cellBg);
                row.addView(cell);
            }
            heatmapContainer.addView(row);
        }

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        legendLp.topMargin = dp(8);
        legend.setLayoutParams(legendLp);

        TextView tvLess = new TextView(this);
        tvLess.setText("Less");
        tvLess.setTextColor(Color.parseColor("#4B5563"));
        tvLess.setTextSize(9);
        tvLess.setPadding(0, 0, dp(4), 0);
        legend.addView(tvLess);

        int[] legendColors = {0xFF1A1F2E, 0xFF064E3B, 0xFF059669, 0xFF34D399};
        for (int c : legendColors) {
            View swatch = new View(this);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(12), dp(12));
            swLp.setMarginEnd(dp(2));
            swatch.setLayoutParams(swLp);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.RECTANGLE);
            swBg.setCornerRadius(dp(2));
            swBg.setColor(c);
            swatch.setBackground(swBg);
            legend.addView(swatch);
        }

        TextView tvMore = new TextView(this);
        tvMore.setText("More");
        tvMore.setTextColor(Color.parseColor("#4B5563"));
        tvMore.setTextSize(9);
        tvMore.setPadding(dp(4), 0, 0, 0);
        legend.addView(tvMore);

        heatmapContainer.addView(legend);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
