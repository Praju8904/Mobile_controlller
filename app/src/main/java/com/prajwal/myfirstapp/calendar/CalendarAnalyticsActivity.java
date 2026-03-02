package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CalendarAnalyticsActivity - Deep statistical insights into calendar usage.
 * 
 * Features:
 * - Events This Month / This Year summary
 * - Completion Rate (if using status)
 * - Average Events per Day/Week
 * - Current Streak & Longest Streak
 * - Most Used Category
 * - Event Duration Analysis
 * - Customizable date range selector
 */
public class CalendarAnalyticsActivity extends AppCompatActivity {

    //  Theme Colors 
    private static final int BG_PRIMARY    = 0xFF0A0E21;
    private static final int BG_SURFACE    = 0xFF111827;
    private static final int BG_ELEVATED   = 0xFF1E293B;
    private static final int TEXT_PRIMARY   = 0xFFF1F5F9;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_MUTED     = 0xFF64748B;
    private static final int ACCENT_BLUE   = 0xFF3B82F6;
    private static final int ACCENT_GREEN  = 0xFF10B981;
    private static final int ACCENT_AMBER  = 0xFFF59E0B;
    private static final int DANGER_RED    = 0xFFEF4444;
    private static final int ACCENT_PURPLE = 0xFF8B5CF6;
    private static final int ACCENT_PINK   = 0xFFEC4899;
    private static final int DIVIDER_CLR   = 0xFF1E293B;

    private CalendarRepository repository;
    private LinearLayout contentContainer;
    
    // Date range for analysis
    private Calendar rangeStart;
    private Calendar rangeEnd;
    private TextView dateRangeLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_PRIMARY);
        getWindow().setNavigationBarColor(BG_PRIMARY);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        repository = new CalendarRepository(this);
        
        // Default range: this month
        rangeStart = Calendar.getInstance();
        rangeStart.set(Calendar.DAY_OF_MONTH, 1);
        rangeStart.set(Calendar.HOUR_OF_DAY, 0);
        rangeStart.set(Calendar.MINUTE, 0);
        
        rangeEnd = Calendar.getInstance();
        rangeEnd.set(Calendar.DAY_OF_MONTH, rangeEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        rangeEnd.set(Calendar.HOUR_OF_DAY, 23);
        rangeEnd.set(Calendar.MINUTE, 59);

        buildUI();
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right);
    }

    // 
    //  UI BUILD
    // 

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_PRIMARY);

        //  Header 
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(BG_SURFACE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(10), dp(16), dp(10));

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        backBtn.setColorFilter(TEXT_PRIMARY);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        backBtn.setOnClickListener(v -> onBackPressed());
        header.addView(backBtn);

        TextView headerTitle = new TextView(this);
        headerTitle.setText(" Analytics");
        headerTitle.setTextColor(TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(headerTitle);

        root.addView(header);

        //  Date Range Selector 
        LinearLayout rangeBar = new LinearLayout(this);
        rangeBar.setOrientation(LinearLayout.HORIZONTAL);
        rangeBar.setBackgroundColor(BG_SURFACE);
        rangeBar.setGravity(Gravity.CENTER_VERTICAL);
        rangeBar.setPadding(dp(16), dp(8), dp(16), dp(12));

        dateRangeLabel = new TextView(this);
        updateRangeLabel();
        dateRangeLabel.setTextColor(TEXT_PRIMARY);
        dateRangeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dateRangeLabel.setTypeface(null, Typeface.BOLD);
        dateRangeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        dateRangeLabel.setOnClickListener(v -> showRangePicker());
        rangeBar.addView(dateRangeLabel);

        // Quick range buttons
        rangeBar.addView(rangeChip("This Month", () -> setRangeThisMonth()));
        rangeBar.addView(rangeChip("This Year", () -> setRangeThisYear()));
        rangeBar.addView(rangeChip("All Time", () -> setRangeAllTime()));

        root.addView(rangeBar);

        //  Scrollable Content 
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(16), dp(12), dp(16), dp(32));

        scroll.addView(contentContainer);
        root.addView(scroll);
        setContentView(root);

        rebuildContent();
    }

    private View rangeChip(String label, Runnable onClick) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(TEXT_SECONDARY);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(BG_ELEVATED);
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(6);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private void setRangeThisMonth() {
        rangeStart = Calendar.getInstance();
        rangeStart.set(Calendar.DAY_OF_MONTH, 1);
        rangeEnd = Calendar.getInstance();
        rangeEnd.set(Calendar.DAY_OF_MONTH, rangeEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        updateRangeLabel();
        rebuildContent();
    }

    private void setRangeThisYear() {
        rangeStart = Calendar.getInstance();
        rangeStart.set(Calendar.MONTH, Calendar.JANUARY);
        rangeStart.set(Calendar.DAY_OF_MONTH, 1);
        rangeEnd = Calendar.getInstance();
        rangeEnd.set(Calendar.MONTH, Calendar.DECEMBER);
        rangeEnd.set(Calendar.DAY_OF_MONTH, 31);
        updateRangeLabel();
        rebuildContent();
    }

    private void setRangeAllTime() {
        rangeStart = Calendar.getInstance();
        rangeStart.set(Calendar.YEAR, 2020);
        rangeStart.set(Calendar.MONTH, Calendar.JANUARY);
        rangeStart.set(Calendar.DAY_OF_MONTH, 1);
        rangeEnd = Calendar.getInstance();
        rangeEnd.add(Calendar.YEAR, 1);
        updateRangeLabel();
        rebuildContent();
    }

    private void showRangePicker() {
        // Start date picker
        new DatePickerDialog(this, (dp, y, m, d) -> {
            rangeStart.set(y, m, d);
            // Then end date
            new DatePickerDialog(this, (dp2, y2, m2, d2) -> {
                rangeEnd.set(y2, m2, d2);
                updateRangeLabel();
                rebuildContent();
            }, rangeEnd.get(Calendar.YEAR), rangeEnd.get(Calendar.MONTH), 
               rangeEnd.get(Calendar.DAY_OF_MONTH)).show();
        }, rangeStart.get(Calendar.YEAR), rangeStart.get(Calendar.MONTH),
           rangeStart.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateRangeLabel() {
        String start = new SimpleDateFormat("MMM d", Locale.US).format(rangeStart.getTime());
        String end = new SimpleDateFormat("MMM d, yyyy", Locale.US).format(rangeEnd.getTime());
        dateRangeLabel.setText(" " + start + " — " + end);
    }

    // 
    //  CONTENT BUILD
    // 

    private void rebuildContent() {
        contentContainer.removeAllViews();

        List<CalendarEvent> allEvents = repository.getAllEvents();
        List<CalendarEvent> rangeEvents = filterByRange(allEvents);

        // 1. Summary Stats Row
        contentContainer.addView(buildSummaryStats(rangeEvents, allEvents));

        // 2. Completion Rate (if tracking completed events)
        contentContainer.addView(buildCompletionCard(rangeEvents));

        // 3. Category Breakdown
        contentContainer.addView(sectionTitle("Category Breakdown"));
        contentContainer.addView(buildCategoryBreakdown(rangeEvents));

        // 4. Event Duration Analysis
        contentContainer.addView(sectionTitle("Event Durations"));
        contentContainer.addView(buildDurationAnalysis(rangeEvents));

        // 5. Events Over Time (by month or week)
        contentContainer.addView(sectionTitle("Events Over Time"));
        contentContainer.addView(buildEventsOverTime(allEvents));

        // 6. Streak Analysis
        contentContainer.addView(sectionTitle("Activity Streaks"));
        contentContainer.addView(buildStreakCard(allEvents));

        // 7. Top Event Types
        contentContainer.addView(sectionTitle("Event Types"));
        contentContainer.addView(buildEventTypesChart(rangeEvents));
    }

    private List<CalendarEvent> filterByRange(List<CalendarEvent> events) {
        List<CalendarEvent> result = new ArrayList<>();
        String startStr = fmtCalDate(rangeStart);
        String endStr = fmtCalDate(rangeEnd);
        for (CalendarEvent ev : events) {
            if (ev.date != null && ev.date.compareTo(startStr) >= 0 && ev.date.compareTo(endStr) <= 0) {
                result.add(ev);
            }
        }
        return result;
    }

    // 
    //  SUMMARY STATS
    // 

    private View buildSummaryStats(List<CalendarEvent> rangeEvents, List<CalendarEvent> allEvents) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(16);
        container.setLayoutParams(lp);

        // Calculate stats
        int totalInRange = rangeEvents.size();
        
        // Average events per day in range
        long daysBetween = Math.max(1, 
            (rangeEnd.getTimeInMillis() - rangeStart.getTimeInMillis()) / (1000 * 60 * 60 * 24));
        double avgPerDay = totalInRange / (double) daysBetween;
        
        // Average per week
        double avgPerWeek = avgPerDay * 7;

        // Total all time
        int totalAllTime = allEvents.size();

        container.addView(statCard(String.valueOf(totalInRange), "In Range", ACCENT_BLUE));
        container.addView(statCard(String.format(Locale.US, "%.1f", avgPerDay), "Per Day", ACCENT_GREEN));
        container.addView(statCard(String.format(Locale.US, "%.0f", avgPerWeek), "Per Week", ACCENT_AMBER));
        container.addView(statCard(String.valueOf(totalAllTime), "All Time", ACCENT_PURPLE));

        return container;
    }

    private View statCard(String value, String label, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(BG_SURFACE);
        card.setBackground(bg);

        TextView valTv = new TextView(this);
        valTv.setText(value);
        valTv.setTextColor(color);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        valTv.setTypeface(null, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        card.addView(valTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(TEXT_MUTED);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelTv.setGravity(Gravity.CENTER);
        card.addView(labelTv);

        return card;
    }
    // 
    //  COMPLETION CARD
    // 

    private View buildCompletionCard(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        // Count completed vs total (using status field)
        int completed = 0;
        int total = 0;
        for (CalendarEvent ev : events) {
            // Only count past events
            Calendar evCal = parseDate(ev.date);
            if (evCal != null && evCal.before(Calendar.getInstance())) {
                total++;
                if ("completed".equalsIgnoreCase(ev.status) || 
                    "done".equalsIgnoreCase(ev.status)) {
                    completed++;
                }
            }
        }

        if (total == 0) {
            TextView empty = new TextView(this);
            empty.setText("No past events to analyze");
            empty.setTextColor(TEXT_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            card.addView(empty);
            return card;
        }

        int pct = (completed * 100) / total;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Circular progress (simplified as text)
        LinearLayout circleHolder = new LinearLayout(this);
        circleHolder.setGravity(Gravity.CENTER);
        circleHolder.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(70)));
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setStroke(dp(4), ACCENT_GREEN);
        circleBg.setColor(0x2010B981);
        circleHolder.setBackground(circleBg);

        TextView pctTv = new TextView(this);
        pctTv.setText(pct + "%");
        pctTv.setTextColor(ACCENT_GREEN);
        pctTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        pctTv.setTypeface(null, Typeface.BOLD);
        circleHolder.addView(pctTv);
        row.addView(circleHolder);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(this);
        title.setText("Completion Rate");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, Typeface.BOLD);
        info.addView(title);

        TextView detail = new TextView(this);
        detail.setText(completed + " of " + total + " events marked complete");
        detail.setTextColor(TEXT_MUTED);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.addView(detail);

        row.addView(info);
        card.addView(row);

        return card;
    }

    // 
    //  CATEGORY BREAKDOWN
    // 

    private View buildCategoryBreakdown(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        Map<String, Integer> catCounts = new LinkedHashMap<>();
        Map<String, Integer> catColors = new LinkedHashMap<>();
        for (CalendarEvent ev : events) {
            String cat = ev.category != null && !ev.category.isEmpty() ? ev.category : "Uncategorized";
            catCounts.put(cat, catCounts.getOrDefault(cat, 0) + 1);
            if (!catColors.containsKey(cat)) {
                catColors.put(cat, safeColor(ev.colorHex));
            }
        }

        if (catCounts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No events in this range");
            empty.setTextColor(TEXT_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            card.addView(empty);
            return card;
        }

        int total = events.size();

        // Sort by count desc
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(catCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            card.addView(categoryRow(entry.getKey(), entry.getValue(), total, 
                catColors.getOrDefault(entry.getKey(), ACCENT_BLUE)));
        }

        return card;
    }

    private View categoryRow(String name, int count, int total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(color);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(10);
        topRow.addView(dot, dotLp);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(TEXT_PRIMARY);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        topRow.addView(nameTv);

        int pct = (count * 100) / total;
        TextView countTv = new TextView(this);
        countTv.setText(count + " (" + pct + "%)");
        countTv.setTextColor(TEXT_MUTED);
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        topRow.addView(countTv);

        row.addView(topRow);

        // Progress bar
        FrameLayout barContainer = new FrameLayout(this);
        barContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(6)));
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(3));
        trackBg.setColor(BG_ELEVATED);
        barContainer.setBackground(trackBg);

        View fill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(3));
        fillBg.setColor(color);
        fill.setBackground(fillBg);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, -1);
        fill.setLayoutParams(fillLp);
        barContainer.addView(fill);

        // Animate width
        fill.post(() -> {
            int maxWidth = barContainer.getWidth();
            int targetWidth = (int)(maxWidth * (pct / 100f));
            fill.getLayoutParams().width = targetWidth;
            fill.requestLayout();
        });

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(6));
        barLp.topMargin = dp(6);
        row.addView(barContainer, barLp);

        return row;
    }

    // 
    //  DURATION ANALYSIS
    // 

    private View buildDurationAnalysis(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        // Buckets: <30min, 30-60min, 1-2h, 2-4h, >4h
        int[] buckets = new int[5];
        String[] bucketNames = {"< 30 min", "30-60 min", "1-2 hours", "2-4 hours", "> 4 hours"};

        for (CalendarEvent ev : events) {
            if (ev.isAllDay || ev.startTime == null || ev.endTime == null) continue;
            int mins = calcDurationMins(ev.startTime, ev.endTime);
            if (mins < 30) buckets[0]++;
            else if (mins < 60) buckets[1]++;
            else if (mins < 120) buckets[2]++;
            else if (mins < 240) buckets[3]++;
            else buckets[4]++;
        }

        int maxBucket = 1;
        for (int b : buckets) if (b > maxBucket) maxBucket = b;

        int[] colors = {ACCENT_GREEN, ACCENT_BLUE, ACCENT_AMBER, ACCENT_PURPLE, DANGER_RED};
        LinearLayout barsRow = new LinearLayout(this);
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        barsRow.setGravity(Gravity.BOTTOM);

        int barMaxH = dp(80);
        for (int i = 0; i < 5; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, barMaxH + dp(30), 1f));

            TextView countTv = new TextView(this);
            countTv.setText(String.valueOf(buckets[i]));
            countTv.setTextColor(colors[i]);
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            countTv.setTypeface(null, Typeface.BOLD);
            countTv.setGravity(Gravity.CENTER);
            col.addView(countTv);

            int barH = (int)((buckets[i] / (float)maxBucket) * barMaxH);
            barH = Math.max(dp(4), barH);
            View bar = new View(this);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadii(new float[]{dp(4), dp(4), dp(4), dp(4), 0, 0, 0, 0});
            barBg.setColor(colors[i]);
            bar.setBackground(barBg);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(28), barH);
            barLp.topMargin = dp(4);
            col.addView(bar, barLp);

            TextView label = new TextView(this);
            label.setText(bucketNames[i]);
            label.setTextColor(TEXT_MUTED);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            col.addView(label);

            barsRow.addView(col);
        }
        card.addView(barsRow);

        return card;
    }

    private int calcDurationMins(String start, String end) {
        try {
            String[] sParts = start.split(":");
            String[] eParts = end.split(":");
            int sH = Integer.parseInt(sParts[0]);
            int sM = Integer.parseInt(sParts[1]);
            int eH = Integer.parseInt(eParts[0]);
            int eM = Integer.parseInt(eParts[1]);
            return (eH * 60 + eM) - (sH * 60 + sM);
        } catch (Exception e) {
            return 60;
        }
    }
    // 
    //  EVENTS OVER TIME
    // 

    private View buildEventsOverTime(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        // Count events by month for the past 6 months
        Map<String, Integer> monthCounts = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        for (int i = 5; i >= 0; i--) {
            Calendar month = (Calendar) cal.clone();
            month.add(Calendar.MONTH, -i);
            String key = new SimpleDateFormat("MMM", Locale.US).format(month.getTime());
            String prefix = new SimpleDateFormat("yyyy-MM", Locale.US).format(month.getTime());
            int count = 0;
            for (CalendarEvent ev : events) {
                if (ev.date != null && ev.date.startsWith(prefix)) count++;
            }
            monthCounts.put(key, count);
        }

        int maxCount = 1;
        for (int c : monthCounts.values()) if (c > maxCount) maxCount = c;

        LinearLayout barsRow = new LinearLayout(this);
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        barsRow.setGravity(Gravity.BOTTOM);
        int barMaxH = dp(70);

        int idx = 0;
        for (Map.Entry<String, Integer> entry : monthCounts.entrySet()) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, barMaxH + dp(30), 1f));

            TextView countTv = new TextView(this);
            countTv.setText(String.valueOf(entry.getValue()));
            countTv.setTextColor(ACCENT_BLUE);
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            countTv.setGravity(Gravity.CENTER);
            col.addView(countTv);

            int barH = (int)((entry.getValue() / (float)maxCount) * barMaxH);
            barH = Math.max(dp(4), barH);
            View bar = new View(this);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadii(new float[]{dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
            // Current month highlighted
            barBg.setColor(idx == monthCounts.size() - 1 ? ACCENT_BLUE : 0x603B82F6);
            bar.setBackground(barBg);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(22), barH);
            barLp.topMargin = dp(4);
            col.addView(bar, barLp);

            TextView label = new TextView(this);
            label.setText(entry.getKey());
            label.setTextColor(TEXT_MUTED);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(6), 0, 0);
            col.addView(label);

            barsRow.addView(col);
            idx++;
        }
        card.addView(barsRow);

        return card;
    }

    // 
    //  STREAK ANALYSIS
    // 

    private View buildStreakCard(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        // Find current streak (consecutive days with events)
        Set<String> eventDates = new HashSet<>();
        for (CalendarEvent ev : events) {
            if (ev.date != null) eventDates.add(ev.date);
        }

        int currentStreak = 0;
        int longestStreak = 0;
        int tempStreak = 0;

        Calendar today = Calendar.getInstance();
        Calendar check = (Calendar) today.clone();

        // Current streak (counting back from today)
        while (eventDates.contains(fmtCalDate(check))) {
            currentStreak++;
            check.add(Calendar.DAY_OF_YEAR, -1);
        }

        // Longest streak (scan all dates)
        List<String> sortedDates = new ArrayList<>(eventDates);
        Collections.sort(sortedDates);

        for (int i = 0; i < sortedDates.size(); i++) {
            if (i == 0) {
                tempStreak = 1;
            } else {
                Calendar prev = parseDate(sortedDates.get(i - 1));
                Calendar curr = parseDate(sortedDates.get(i));
                if (prev != null && curr != null) {
                    prev.add(Calendar.DAY_OF_YEAR, 1);
                    if (isSameDay(prev, curr)) {
                        tempStreak++;
                    } else {
                        tempStreak = 1;
                    }
                }
            }
            longestStreak = Math.max(longestStreak, tempStreak);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Current streak
        LinearLayout currentCol = new LinearLayout(this);
        currentCol.setOrientation(LinearLayout.VERTICAL);
        currentCol.setGravity(Gravity.CENTER);
        currentCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView fire = new TextView(this);
        fire.setText("");
        fire.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        fire.setGravity(Gravity.CENTER);
        currentCol.addView(fire);

        TextView currentVal = new TextView(this);
        currentVal.setText(currentStreak + " days");
        currentVal.setTextColor(ACCENT_AMBER);
        currentVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        currentVal.setTypeface(null, Typeface.BOLD);
        currentVal.setGravity(Gravity.CENTER);
        currentCol.addView(currentVal);

        TextView currentLabel = new TextView(this);
        currentLabel.setText("Current Streak");
        currentLabel.setTextColor(TEXT_MUTED);
        currentLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        currentLabel.setGravity(Gravity.CENTER);
        currentCol.addView(currentLabel);

        row.addView(currentCol);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(DIVIDER_CLR);
        div.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(60)));
        row.addView(div);

        // Longest streak
        LinearLayout longestCol = new LinearLayout(this);
        longestCol.setOrientation(LinearLayout.VERTICAL);
        longestCol.setGravity(Gravity.CENTER);
        longestCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView trophy = new TextView(this);
        trophy.setText("");
        trophy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        trophy.setGravity(Gravity.CENTER);
        longestCol.addView(trophy);

        TextView longestVal = new TextView(this);
        longestVal.setText(longestStreak + " days");
        longestVal.setTextColor(ACCENT_GREEN);
        longestVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        longestVal.setTypeface(null, Typeface.BOLD);
        longestVal.setGravity(Gravity.CENTER);
        longestCol.addView(longestVal);

        TextView longestLabel = new TextView(this);
        longestLabel.setText("Longest Streak");
        longestLabel.setTextColor(TEXT_MUTED);
        longestLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        longestLabel.setGravity(Gravity.CENTER);
        longestCol.addView(longestLabel);

        row.addView(longestCol);
        card.addView(row);

        return card;
    }

    // 
    //  EVENT TYPES CHART
    // 

    private View buildEventTypesChart(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (CalendarEvent ev : events) {
            String type = ev.eventType != null && !ev.eventType.isEmpty() 
                ? ev.eventType : CalendarEvent.TYPE_EVENT;
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }

        if (typeCounts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No events");
            empty.setTextColor(TEXT_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            card.addView(empty);
            return card;
        }

        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView icon = new TextView(this);
            icon.setText(CalendarEvent.getEventTypeIcon(entry.getKey()));
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            icon.setPadding(0, 0, dp(12), 0);
            row.addView(icon);

            TextView label = new TextView(this);
            label.setText(CalendarEvent.getEventTypeLabel(entry.getKey()));
            label.setTextColor(TEXT_PRIMARY);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(label);

            TextView count = new TextView(this);
            count.setText(String.valueOf(entry.getValue()));
            count.setTextColor(ACCENT_BLUE);
            count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            count.setTypeface(null, Typeface.BOLD);
            row.addView(count);

            card.addView(row);
        }

        return card;
    }

    // 
    //  HELPER METHODS
    // 

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TEXT_PRIMARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(BG_SURFACE);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    private int safeColor(String hex) {
        if (hex == null || hex.isEmpty()) return ACCENT_BLUE;
        try {
            String clean = hex.startsWith("#") ? hex : "#" + hex;
            return Color.parseColor(clean);
        } catch (Exception e) {
            return ACCENT_BLUE;
        }
    }

    private String fmtCalDate(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    private Calendar parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}