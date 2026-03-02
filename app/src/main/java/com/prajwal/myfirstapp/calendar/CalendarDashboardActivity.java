package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CalendarDashboardActivity - Premium visual data-driven calendar overview.
 * 
 * Features:
 * - Today at a Glance with mini timeline
 * - This Week heatmap overview
 * - Upcoming Events with countdown
 * - Monthly Heatmap
 * - Category Distribution donut chart
 * - Busiest Times/Days charts
 * - Free Time Finder
 * - Birthday & Anniversary widget
 */
public class CalendarDashboardActivity extends AppCompatActivity {

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
    private static final int ACCENT_PINK   = 0xFFEC4899;
    private static final int DIVIDER_CLR   = 0xFF1E293B;

    // Heatmap colors (from dark to bright using accent blue)
    private static final int[] HEATMAP_COLORS = {
        0x20FFFFFF, 0x403B82F6, 0x703B82F6, 0xA03B82F6, 0xFF3B82F6
    };

    private CalendarRepository repository;
    private ScrollView mainScroll;
    private LinearLayout contentContainer;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_PRIMARY);
        getWindow().setNavigationBarColor(BG_PRIMARY);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        repository = new CalendarRepository(this);
        buildUI();
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildContent();
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
        header.setPadding(dp(4), dp(10), dp(12), dp(10));

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        backBtn.setColorFilter(TEXT_PRIMARY);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        backBtn.setOnClickListener(v -> onBackPressed());
        header.addView(backBtn);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Dashboard");
        headerTitle.setTextColor(TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(headerTitle);

        TextView analyticsBtn = new TextView(this);
        analyticsBtn.setText(" Analytics");
        analyticsBtn.setTextColor(ACCENT_BLUE);
        analyticsBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        analyticsBtn.setTypeface(null, Typeface.BOLD);
        analyticsBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        analyticsBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, CalendarAnalyticsActivity.class));
            overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
        });
        header.addView(analyticsBtn);
        root.addView(header);

        //  Scrollable Content 
        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        mainScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(16), dp(12), dp(16), dp(32));

        mainScroll.addView(contentContainer);
        root.addView(mainScroll);
        setContentView(root);

        rebuildContent();
    }

    private void rebuildContent() {
        contentContainer.removeAllViews();

        Calendar today = Calendar.getInstance();
        String todayStr = fmtCalDate(today);
        List<CalendarEvent> todayEvents = repository.getEventsForDate(todayStr);
        List<CalendarEvent> upcoming = repository.getUpcomingEvents(15);

        // 1. Today at a Glance
        contentContainer.addView(buildTodayCard(today, todayEvents));

        // 2. This Week Overview
        contentContainer.addView(sectionTitle("This Week"));
        contentContainer.addView(buildWeekOverview(today));

        // 3. Upcoming Birthdays & Anniversaries
        contentContainer.addView(buildBirthdaySection());

        // 4. Upcoming Events
        contentContainer.addView(sectionTitle("Upcoming Events"));
        contentContainer.addView(buildUpcomingList(upcoming));

        // 5. Monthly Heatmap
        contentContainer.addView(sectionTitle("Monthly Activity"));
        contentContainer.addView(buildMonthHeatmap(today));

        // 6. Category Distribution
        contentContainer.addView(sectionTitle("This Month by Category"));
        contentContainer.addView(buildCategoryDonut(today));

        // 7. Busiest Times
        contentContainer.addView(sectionTitle("Busiest Hours"));
        contentContainer.addView(buildBusiestHoursChart());

        // 8. Busiest Days
        contentContainer.addView(sectionTitle("Busiest Days"));
        contentContainer.addView(buildBusiestDaysChart());

        // 9. Free Time Finder
        contentContainer.addView(sectionTitle("Free Time This Week"));
        contentContainer.addView(buildFreeTimeFinder(today));
    }

    // 
    //  TODAY AT A GLANCE
    // 

    private View buildTodayCard(Calendar today, List<CalendarEvent> events) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(16));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(16);
        card.setLayoutParams(cardLp);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(16));
        int[] gradColors = {0xFF1E3A5F, 0xFF0F2847};
        cardBg.setColors(gradColors);
        cardBg.setOrientation(GradientDrawable.Orientation.TL_BR);
        card.setBackground(cardBg);

        // Date line
        TextView dateLine = new TextView(this);
        String dayName = new SimpleDateFormat("EEEE", Locale.US).format(today.getTime());
        String dateStr = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(today.getTime());
        dateLine.setText(dayName);
        dateLine.setTextColor(TEXT_SECONDARY);
        dateLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dateLine.setTypeface(null, Typeface.BOLD);
        card.addView(dateLine);

        TextView bigDate = new TextView(this);
        bigDate.setText(dateStr);
        bigDate.setTextColor(TEXT_PRIMARY);
        bigDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        bigDate.setTypeface(null, Typeface.BOLD);
        bigDate.setPadding(0, dp(2), 0, dp(8));
        card.addView(bigDate);

        // Summary
        TextView summary = new TextView(this);
        if (events.isEmpty()) {
            summary.setText(" Free day â€” nothing scheduled");
            summary.setTextColor(ACCENT_GREEN);
        } else {
            summary.setText(" You have " + events.size() + " event" + (events.size() > 1 ? "s" : "") + " today");
            summary.setTextColor(TEXT_PRIMARY);
        }
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        summary.setPadding(0, dp(4), 0, dp(12));
        card.addView(summary);

        // Next upcoming within 3 hours
        CalendarEvent nextUp = findNextWithin3Hours(events);
        if (nextUp != null) {
            LinearLayout nextRow = new LinearLayout(this);
            nextRow.setOrientation(LinearLayout.HORIZONTAL);
            nextRow.setGravity(Gravity.CENTER_VERTICAL);
            nextRow.setPadding(0, 0, 0, dp(12));

            TextView nextLabel = new TextView(this);
            nextLabel.setText("Coming up: ");
            nextLabel.setTextColor(TEXT_MUTED);
            nextLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            nextRow.addView(nextLabel);

            TextView nextTitle = new TextView(this);
            nextTitle.setText(nextUp.title + (nextUp.startTime != null ? " at " + fmtTime(nextUp.startTime) : ""));
            nextTitle.setTextColor(ACCENT_AMBER);
            nextTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            nextTitle.setTypeface(null, Typeface.BOLD);
            nextRow.addView(nextTitle);
            card.addView(nextRow);
        }

        // Mini timeline (8am to 10pm)
        if (!events.isEmpty()) {
            card.addView(buildMiniTimeline(events));
        }

        return card;
    }

    private View buildMiniTimeline(List<CalendarEvent> events) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(8), 0, 0);

        TextView label = new TextView(this);
        label.setText("Today's Timeline");
        label.setTextColor(TEXT_MUTED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setPadding(0, 0, 0, dp(6));
        container.addView(label);

        // Timeline bar (8am-10pm = 14 hours)
        FrameLayout timeline = new FrameLayout(this);
        int barHeight = dp(24);
        timeline.setLayoutParams(new LinearLayout.LayoutParams(-1, barHeight));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(4));
        barBg.setColor(0x30FFFFFF);
        timeline.setBackground(barBg);

        // Add event blocks
        for (CalendarEvent ev : events) {
            if (ev.isAllDay || ev.startTime == null) continue;
            View block = createTimeBlock(ev, barHeight);
            if (block != null) timeline.addView(block);
        }

        container.addView(timeline);

        // Time labels
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        labels.setPadding(0, dp(2), 0, 0);
        String[] times = {"8am", "12pm", "4pm", "8pm", "10pm"};
        for (int i = 0; i < times.length; i++) {
            TextView t = new TextView(this);
            t.setText(times[i]);
            t.setTextColor(TEXT_MUTED);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            t.setGravity(i == times.length - 1 ? Gravity.END : Gravity.START);
            labels.addView(t);
        }
        container.addView(labels);

        return container;
    }

    private View createTimeBlock(CalendarEvent ev, int barHeight) {
        int startHour = parseHour(ev.startTime);
        int endHour = ev.endTime != null ? parseHour(ev.endTime) : startHour + 1;
        if (startHour < 8) startHour = 8;
        if (endHour > 22) endHour = 22;
        if (startHour >= 22 || endHour <= 8) return null;

        float startPct = (startHour - 8) / 14f;
        float endPct = (endHour - 8) / 14f;
        float widthPct = endPct - startPct;
        if (widthPct < 0.02f) widthPct = 0.02f;

        View block = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(3));
        bg.setColor(safeColor(ev.colorHex));
        block.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, barHeight - dp(4));
        lp.topMargin = dp(2);
        // Will set width via post measurement
        block.setLayoutParams(lp);
        block.setTag(new float[]{startPct, widthPct});

        block.post(() -> {
            float[] vals = (float[]) block.getTag();
            int parentWidth = ((View)block.getParent()).getWidth();
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) block.getLayoutParams();
            p.leftMargin = (int)(parentWidth * vals[0]);
            p.width = Math.max(dp(4), (int)(parentWidth * vals[1]));
            block.setLayoutParams(p);
        });

        return block;
    }

    private CalendarEvent findNextWithin3Hours(List<CalendarEvent> events) {
        Calendar now = Calendar.getInstance();
        Calendar limit = (Calendar) now.clone();
        limit.add(Calendar.HOUR_OF_DAY, 3);

        for (CalendarEvent ev : events) {
            if (ev.startTime == null || ev.isAllDay) continue;
            Calendar evTime = parseDateTime(fmtCalDate(now), ev.startTime);
            if (evTime != null && evTime.after(now) && evTime.before(limit)) {
                return ev;
            }
        }
        return null;
    }
    // 
    //  WEEK OVERVIEW
    // 

    private View buildWeekOverview(Calendar today) {
        LinearLayout card = createCardContainer();

        // Determine week start (Monday)
        Calendar weekStart = (Calendar) today.clone();
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        if (weekStart.after(today)) weekStart.add(Calendar.WEEK_OF_YEAR, -1);

        // Day names row
        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dayNames = {"M", "T", "W", "T", "F", "S", "S"};
        for (String d : dayNames) {
            TextView tv = new TextView(this);
            tv.setText(d);
            tv.setTextColor(TEXT_MUTED);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            daysRow.addView(tv);
        }
        card.addView(daysRow);

        // Date numbers row
        LinearLayout numsRow = new LinearLayout(this);
        numsRow.setOrientation(LinearLayout.HORIZONTAL);
        numsRow.setPadding(0, dp(6), 0, dp(6));
        Calendar day = (Calendar) weekStart.clone();
        for (int i = 0; i < 7; i++) {
            int dateNum = day.get(Calendar.DAY_OF_MONTH);
            boolean isToday = isSameDay(day, today);

            TextView tv = new TextView(this);
            tv.setText(String.valueOf(dateNum));
            tv.setTextColor(isToday ? BG_PRIMARY : TEXT_PRIMARY);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTypeface(null, isToday ? Typeface.BOLD : Typeface.NORMAL);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            tv.setLayoutParams(lp);

            if (isToday) {
                GradientDrawable circleBg = new GradientDrawable();
                circleBg.setShape(GradientDrawable.OVAL);
                circleBg.setColor(ACCENT_BLUE);
                tv.setBackground(circleBg);
            }
            numsRow.addView(tv);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        card.addView(numsRow);

        // Event dots row (heatmap)
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setPadding(0, 0, 0, dp(4));

        day = (Calendar) weekStart.clone();
        for (int i = 0; i < 7; i++) {
            String dateStr = fmtCalDate(day);
            List<CalendarEvent> dayEvents = repository.getEventsForDate(dateStr);
            int count = dayEvents.size();
            int heat = Math.min(count, 4);

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, dp(10), 1f));

            // Show up to 3 colored dots
            int maxDots = Math.min(count, 3);
            for (int d = 0; d < maxDots; d++) {
                View dot = new View(this);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(safeColor(dayEvents.get(d).colorHex));
                dot.setBackground(dotBg);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
                dotLp.setMargins(dp(1), 0, dp(1), 0);
                cell.addView(dot, dotLp);
            }
            if (count > 3) {
                TextView plus = new TextView(this);
                plus.setText("+" + (count - 3));
                plus.setTextColor(TEXT_MUTED);
                plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
                cell.addView(plus);
            }
            dotsRow.addView(cell);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        card.addView(dotsRow);

        // Busyness summary
        day = (Calendar) weekStart.clone();
        int totalEvents = 0;
        int busiestCount = 0;
        String busiestDay = "";
        for (int i = 0; i < 7; i++) {
            String dateStr = fmtCalDate(day);
            int count = repository.getEventsForDate(dateStr).size();
            totalEvents += count;
            if (count > busiestCount) {
                busiestCount = count;
                busiestDay = new SimpleDateFormat("EEEE", Locale.US).format(day.getTime());
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }

        View div = new View(this);
        div.setBackgroundColor(DIVIDER_CLR);
        div.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        card.addView(div);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(0, dp(8), 0, 0);

        statsRow.addView(miniStat(String.valueOf(totalEvents), "events"));
        statsRow.addView(miniStat(busiestDay, "busiest"));

        card.addView(statsRow);

        return card;
    }

    private View miniStat(String value, String label) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.CENTER);
        stat.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView valTv = new TextView(this);
        valTv.setText(value.isEmpty() ? "â€”" : value);
        valTv.setTextColor(ACCENT_BLUE);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valTv.setTypeface(null, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        stat.addView(valTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(TEXT_MUTED);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelTv.setGravity(Gravity.CENTER);
        stat.addView(labelTv);

        return stat;
    }

    // 
    //  BIRTHDAY & ANNIVERSARY SECTION
    // 

    private View buildBirthdaySection() {
        List<CalendarEvent> celebrations = new ArrayList<>();
        List<CalendarEvent> allEvents = repository.getAllEvents();
        Calendar today = Calendar.getInstance();
        Calendar limit = (Calendar) today.clone();
        limit.add(Calendar.DAY_OF_YEAR, 30);

        for (CalendarEvent ev : allEvents) {
            if (CalendarEvent.TYPE_BIRTHDAY.equals(ev.eventType) ||
                CalendarEvent.TYPE_ANNIVERSARY.equals(ev.eventType)) {
                // Check if upcoming within 30 days
                if (ev.date != null) {
                    Calendar evCal = parseDate(ev.date);
                    if (evCal != null) {
                        evCal.set(Calendar.YEAR, today.get(Calendar.YEAR));
                        if (evCal.before(today)) evCal.add(Calendar.YEAR, 1);
                        if (!evCal.after(limit)) {
                            celebrations.add(ev);
                        }
                    }
                }
            }
        }

        if (celebrations.isEmpty()) {
            return new Space(this);
        }

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        section.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(" Upcoming Celebrations");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        section.addView(title);

        LinearLayout card = createCardContainer();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = (GradientDrawable) card.getBackground();
        bg.setColor(0xFF2D1B4E); // Purple tint for celebrations

        // Sort by days until
        celebrations.sort((a, b) -> {
            return daysUntilCelebration(a.date, today) - daysUntilCelebration(b.date, today);
        });

        int shown = 0;
        for (CalendarEvent ev : celebrations) {
            if (shown >= 3) break;
            int daysUntil = daysUntilCelebration(ev.date, today);
            card.addView(celebrationRow(ev, daysUntil));
            shown++;
        }
        section.addView(card);
        return section;
    }

    private int daysUntilCelebration(String dateStr, Calendar today) {
        if (dateStr == null) return 999;
        Calendar evCal = parseDate(dateStr);
        if (evCal == null) return 999;
        evCal.set(Calendar.YEAR, today.get(Calendar.YEAR));
        if (evCal.before(today)) evCal.add(Calendar.YEAR, 1);
        long diff = evCal.getTimeInMillis() - today.getTimeInMillis();
        return (int)(diff / (1000 * 60 * 60 * 24));
    }

    private View celebrationRow(CalendarEvent ev, int daysUntil) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView icon = new TextView(this);
        icon.setText(CalendarEvent.TYPE_BIRTHDAY.equals(ev.eventType) ? "" : "");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        icon.setPadding(0, 0, dp(10), 0);
        row.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(ev.title);
        titleTv.setTextColor(TEXT_PRIMARY);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTypeface(null, Typeface.BOLD);
        info.addView(titleTv);

        String dateInfo = fmtReadableDate(ev.date);
        TextView dateTv = new TextView(this);
        dateTv.setText(dateInfo);
        dateTv.setTextColor(TEXT_MUTED);
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.addView(dateTv);
        row.addView(info);

        TextView countTv = new TextView(this);
        if (daysUntil == 0) {
            countTv.setText("Today! ");
            countTv.setTextColor(ACCENT_AMBER);
        } else if (daysUntil == 1) {
            countTv.setText("Tomorrow");
            countTv.setTextColor(ACCENT_AMBER);
        } else {
            countTv.setText(daysUntil + " days");
            countTv.setTextColor(TEXT_SECONDARY);
        }
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countTv.setTypeface(null, Typeface.BOLD);
        row.addView(countTv);

        return row;
    }
    // 
    //  UPCOMING EVENTS LIST
    // 

    private View buildUpcomingList(List<CalendarEvent> events) {
        LinearLayout card = createCardContainer();

        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No upcoming events");
            empty.setTextColor(TEXT_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            card.addView(empty);
            return card;
        }

        // Show max 5 events
        int shown = 0;
        Calendar today = Calendar.getInstance();
        for (CalendarEvent ev : events) {
            if (shown >= 5) break;
            card.addView(upcomingEventRow(ev, today));
            shown++;
        }

        // "See all" link
        if (events.size() > 5) {
            TextView seeAll = new TextView(this);
            seeAll.setText("See all " + events.size() + " upcoming events ");
            seeAll.setTextColor(ACCENT_BLUE);
            seeAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            seeAll.setPadding(0, dp(12), 0, 0);
            seeAll.setOnClickListener(v -> {
                finish();
            });
            card.addView(seeAll);
        }

        return card;
    }

    private View upcomingEventRow(CalendarEvent ev, Calendar today) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        final CalendarEvent fEv = ev;
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, CalendarEventDetailActivity.class);
            i.putExtra("event_id", fEv.id);
            startActivity(i);
        });

        // Color indicator
        View colorBar = new View(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(2));
        barBg.setColor(safeColor(ev.colorHex));
        colorBar.setBackground(barBg);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(36));
        barLp.rightMargin = dp(12);
        row.addView(colorBar, barLp);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(ev.title);
        titleTv.setTextColor(TEXT_PRIMARY);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(1);
        info.addView(titleTv);

        String timeInfo = fmtReadableDate(ev.date);
        if (!ev.isAllDay && ev.startTime != null) {
            timeInfo += "  " + fmtTime(ev.startTime);
        }
        TextView timeTv = new TextView(this);
        timeTv.setText(timeInfo);
        timeTv.setTextColor(TEXT_MUTED);
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.addView(timeTv);
        row.addView(info);

        // Countdown
        int daysUntil = daysUntilEvent(ev.date, today);
        TextView countTv = new TextView(this);
        if (daysUntil == 0) {
            countTv.setText("Today");
            countTv.setTextColor(ACCENT_GREEN);
        } else if (daysUntil == 1) {
            countTv.setText("Tomorrow");
            countTv.setTextColor(ACCENT_AMBER);
        } else {
            countTv.setText(daysUntil + "d");
            countTv.setTextColor(TEXT_SECONDARY);
        }
        countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countTv.setTypeface(null, Typeface.BOLD);
        row.addView(countTv);

        return row;
    }

    private int daysUntilEvent(String dateStr, Calendar today) {
        if (dateStr == null) return 0;
        Calendar evCal = parseDate(dateStr);
        if (evCal == null) return 0;
        long diff = evCal.getTimeInMillis() - today.getTimeInMillis();
        return Math.max(0, (int)(diff / (1000 * 60 * 60 * 24)));
    }

    // 
    //  MONTHLY HEATMAP
    // 

    private View buildMonthHeatmap(Calendar today) {
        LinearLayout card = createCardContainer();

        Calendar first = (Calendar) today.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int startDow = first.get(Calendar.DAY_OF_WEEK); // 1=Sun, 2=Mon, etc.
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Month name
        String monthName = new SimpleDateFormat("MMMM yyyy", Locale.US).format(today.getTime());
        TextView monthTv = new TextView(this);
        monthTv.setText(monthName);
        monthTv.setTextColor(TEXT_PRIMARY);
        monthTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        monthTv.setTypeface(null, Typeface.BOLD);
        monthTv.setPadding(0, 0, 0, dp(8));
        card.addView(monthTv);

        // Day headers
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] heads = {"S", "M", "T", "W", "T", "F", "S"};
        for (String h : heads) {
            TextView tv = new TextView(this);
            tv.setText(h);
            tv.setTextColor(TEXT_MUTED);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            headRow.addView(tv);
        }
        card.addView(headRow);

        // Grid rows
        int dayNum = 1;
        int cellsBeforeStart = (startDow == Calendar.SUNDAY) ? 0 : (startDow - Calendar.SUNDAY);
        int totalCells = cellsBeforeStart + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(2), 0, dp(2));

            for (int c = 0; c < 7; c++) {
                int cellIndex = r * 7 + c;
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(Gravity.CENTER);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, dp(28), 1f));

                if (cellIndex < cellsBeforeStart || dayNum > daysInMonth) {
                    // Empty cell
                } else {
                    Calendar day = (Calendar) first.clone();
                    day.set(Calendar.DAY_OF_MONTH, dayNum);
                    String dateStr = fmtCalDate(day);
                    int count = repository.getEventsForDate(dateStr).size();
                    int heat = Math.min(count, 4);
                    boolean isT = isSameDay(day, today);

                    View heatCell = new View(this);
                    GradientDrawable heatBg = new GradientDrawable();
                    heatBg.setCornerRadius(dp(4));
                    heatBg.setColor(HEATMAP_COLORS[heat]);
                    if (isT) {
                        heatBg.setStroke(dp(2), ACCENT_BLUE);
                    }
                    heatCell.setBackground(heatBg);
                    heatCell.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
                    cell.addView(heatCell);

                    dayNum++;
                }
                row.addView(cell);
            }
            card.addView(row);
        }

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, dp(8), 0, 0);

        TextView lessLabel = new TextView(this);
        lessLabel.setText("Less");
        lessLabel.setTextColor(TEXT_MUTED);
        lessLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        lessLabel.setPadding(0, 0, dp(6), 0);
        legend.addView(lessLabel);

        for (int i = 0; i < 5; i++) {
            View box = new View(this);
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(2));
            boxBg.setColor(HEATMAP_COLORS[i]);
            box.setBackground(boxBg);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(12), dp(12));
            boxLp.setMargins(dp(2), 0, dp(2), 0);
            legend.addView(box, boxLp);
        }

        TextView moreLabel = new TextView(this);
        moreLabel.setText("More");
        moreLabel.setTextColor(TEXT_MUTED);
        moreLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        moreLabel.setPadding(dp(6), 0, 0, 0);
        legend.addView(moreLabel);

        card.addView(legend);

        return card;
    }

    // 
    //  CATEGORY DONUT CHART
    // 

    private View buildCategoryDonut(Calendar today) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        container.setLayoutParams(lp);

        // Get this month's events
        String monthStart = fmtCalDate(firstOfMonth(today));
        String monthEnd = fmtCalDate(lastOfMonth(today));
        List<CalendarEvent> monthEvents = repository.getEventsForMonth(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1);

        // Count by category
        Map<String, Integer> catCounts = new LinkedHashMap<>();
        Map<String, Integer> catColors = new LinkedHashMap<>();
        for (CalendarEvent ev : monthEvents) {
            String cat = ev.category != null ? ev.category : "Uncategorized";
            catCounts.put(cat, catCounts.getOrDefault(cat, 0) + 1);
            if (!catColors.containsKey(cat)) {
                catColors.put(cat, safeColor(ev.colorHex));
            }
        }

        if (catCounts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No events this month");
            empty.setTextColor(TEXT_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(16), 0, dp(16));
            container.addView(empty);
            return container;
        }

        // Donut chart
        DonutChartView donut = new DonutChartView(this, catCounts, catColors);
        LinearLayout.LayoutParams donutLp = new LinearLayout.LayoutParams(dp(100), dp(100));
        donutLp.rightMargin = dp(16);
        container.addView(donut, donutLp);

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        int total = monthEvents.size();
        for (Map.Entry<String, Integer> entry : catCounts.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(2), 0, dp(2));

            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(catColors.get(entry.getKey()));
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
            dotLp.rightMargin = dp(8);
            row.addView(dot, dotLp);

            TextView nameTv = new TextView(this);
            nameTv.setText(entry.getKey());
            nameTv.setTextColor(TEXT_PRIMARY);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(nameTv);

            int pct = (entry.getValue() * 100) / total;
            TextView pctTv = new TextView(this);
            pctTv.setText(entry.getValue() + " (" + pct + "%)");
            pctTv.setTextColor(TEXT_MUTED);
            pctTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            row.addView(pctTv);

            legend.addView(row);
        }
        container.addView(legend);

        return container;
    }

    // Custom donut chart View
    private class DonutChartView extends View {
        private Map<String, Integer> data;
        private Map<String, Integer> colors;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF oval = new RectF();

        DonutChartView(android.content.Context ctx, Map<String, Integer> data, Map<String, Integer> colors) {
            super(ctx);
            this.data = data;
            this.colors = colors;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int strokeW = dp(16);
            oval.set(strokeW / 2f, strokeW / 2f, w - strokeW / 2f, h - strokeW / 2f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeW);
            paint.setStrokeCap(Paint.Cap.ROUND);

            int total = 0;
            for (int v : data.values()) total += v;

            float startAngle = -90f;
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                float sweep = (entry.getValue() / (float) total) * 360f;
                paint.setColor(colors.get(entry.getKey()));
                canvas.drawArc(oval, startAngle, sweep - 2, false, paint);
                startAngle += sweep;
            }
        }
    }
    // 
    //  BUSIEST HOURS CHART
    // 

    private View buildBusiestHoursChart() {
        LinearLayout card = createCardContainer();

        // Count events by hour (for all events in the last 30 days)
        int[] hourCounts = new int[24];
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_YEAR, -30);
        List<CalendarEvent> events = repository.getAllEvents();

        for (CalendarEvent ev : events) {
            if (ev.startTime != null && !ev.isAllDay) {
                int hour = parseHour(ev.startTime);
                if (hour >= 0 && hour < 24) hourCounts[hour]++;
            }
        }

        int maxCount = 1;
        for (int c : hourCounts) if (c > maxCount) maxCount = c;

        // Display 8am-10pm (index 8-21)
        LinearLayout barsRow = new LinearLayout(this);
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        barsRow.setGravity(Gravity.BOTTOM);
        int barMaxHeight = dp(60);

        for (int h = 8; h <= 21; h++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, barMaxHeight + dp(16), 1f));

            int barH = (int)((hourCounts[h] / (float)maxCount) * barMaxHeight);
            barH = Math.max(dp(2), barH);

            View bar = new View(this);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadii(new float[]{dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
            barBg.setColor(hourCounts[h] == maxCount ? ACCENT_BLUE : 0x603B82F6);
            bar.setBackground(barBg);
            bar.setLayoutParams(new LinearLayout.LayoutParams(dp(12), barH));
            col.addView(bar);

            TextView label = new TextView(this);
            label.setText(String.valueOf(h));
            label.setTextColor(TEXT_MUTED);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            col.addView(label);

            barsRow.addView(col);
        }
        card.addView(barsRow);

        // Peak hour note
        int peakHour = 9;
        int peakVal = 0;
        for (int h = 8; h <= 21; h++) {
            if (hourCounts[h] > peakVal) {
                peakVal = hourCounts[h];
                peakHour = h;
            }
        }
        TextView peakNote = new TextView(this);
        peakNote.setText("Peak hour: " + formatHour(peakHour));
        peakNote.setTextColor(TEXT_MUTED);
        peakNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        peakNote.setPadding(0, dp(8), 0, 0);
        card.addView(peakNote);

        return card;
    }

    private String formatHour(int h) {
        if (h == 0) return "12 AM";
        if (h < 12) return h + " AM";
        if (h == 12) return "12 PM";
        return (h - 12) + " PM";
    }

    // 
    //  BUSIEST DAYS CHART
    // 

    private View buildBusiestDaysChart() {
        LinearLayout card = createCardContainer();

        // Count events by day of week
        int[] dayCounts = new int[7]; // 0=Sun, 1=Mon, etc.
        List<CalendarEvent> events = repository.getAllEvents();

        for (CalendarEvent ev : events) {
            Calendar cal = parseDate(ev.date);
            if (cal != null) {
                int dow = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sun
                dayCounts[dow]++;
            }
        }

        int maxCount = 1;
        for (int c : dayCounts) if (c > maxCount) maxCount = c;

        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        LinearLayout barsRow = new LinearLayout(this);
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        barsRow.setGravity(Gravity.BOTTOM);
        int barMaxHeight = dp(60);

        for (int d = 0; d < 7; d++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, barMaxHeight + dp(20), 1f));

            int barH = (int)((dayCounts[d] / (float)maxCount) * barMaxHeight);
            barH = Math.max(dp(2), barH);

            View bar = new View(this);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadii(new float[]{dp(3), dp(3), dp(3), dp(3), 0, 0, 0, 0});
            barBg.setColor(dayCounts[d] == maxCount ? ACCENT_GREEN : 0x6010B981);
            bar.setBackground(barBg);
            bar.setLayoutParams(new LinearLayout.LayoutParams(dp(24), barH));
            col.addView(bar);

            TextView label = new TextView(this);
            label.setText(dayNames[d]);
            label.setTextColor(TEXT_MUTED);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            col.addView(label);

            barsRow.addView(col);
        }
        card.addView(barsRow);

        // Busiest day note
        int busiestDay = 0;
        int busiestVal = 0;
        for (int d = 0; d < 7; d++) {
            if (dayCounts[d] > busiestVal) {
                busiestVal = dayCounts[d];
                busiestDay = d;
            }
        }
        TextView busiestNote = new TextView(this);
        busiestNote.setText("Busiest: " + dayNames[busiestDay] + " (" + busiestVal + " events)");
        busiestNote.setTextColor(TEXT_MUTED);
        busiestNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        busiestNote.setPadding(0, dp(8), 0, 0);
        card.addView(busiestNote);

        return card;
    }

    // 
    //  FREE TIME FINDER
    // 

    private View buildFreeTimeFinder(Calendar today) {
        LinearLayout card = createCardContainer();

        // Find free blocks this week (working hours 9-17)
        List<String[]> freeBlocks = new ArrayList<>(); // [date, startHour, endHour]

        Calendar day = (Calendar) today.clone();
        for (int d = 0; d < 7; d++) {
            String dateStr = fmtCalDate(day);
            List<CalendarEvent> dayEvents = repository.getEventsForDate(dateStr);

            // Mark busy hours
            boolean[] busy = new boolean[24];
            for (CalendarEvent ev : dayEvents) {
                if (ev.isAllDay) {
                    for (int h = 9; h < 17; h++) busy[h] = true;
                } else if (ev.startTime != null) {
                    int sH = parseHour(ev.startTime);
                    int eH = ev.endTime != null ? parseHour(ev.endTime) : sH + 1;
                    for (int h = sH; h < eH && h < 24; h++) busy[h] = true;
                }
            }

            // Find free segments within 9-17
            int startFree = -1;
            for (int h = 9; h <= 17; h++) {
                if (h < 17 && !busy[h]) {
                    if (startFree == -1) startFree = h;
                } else {
                    if (startFree != -1) {
                        int duration = h - startFree;
                        if (duration >= 1) {
                            String dayLabel = new SimpleDateFormat("EEE, MMM d", Locale.US).format(day.getTime());
                            freeBlocks.add(new String[]{dayLabel, formatHour(startFree) + " - " + formatHour(h), duration + "h"});
                        }
                        startFree = -1;
                    }
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Sort by duration desc
        freeBlocks.sort((a, b) -> Integer.compare(
            Integer.parseInt(b[2].replace("h", "")),
            Integer.parseInt(a[2].replace("h", ""))
        ));

        if (freeBlocks.isEmpty()) {
            TextView noFree = new TextView(this);
            noFree.setText("No free blocks found (9am-5pm)");
            noFree.setTextColor(TEXT_MUTED);
            noFree.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            noFree.setGravity(Gravity.CENTER);
            noFree.setPadding(0, dp(16), 0, dp(16));
            card.addView(noFree);
            return card;
        }

        // Show top 3 free blocks
        int shown = 0;
        for (String[] block : freeBlocks) {
            if (shown >= 3) break;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView icon = new TextView(this);
            icon.setText("");
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            icon.setPadding(0, 0, dp(12), 0);
            row.addView(icon);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

            TextView dayTv = new TextView(this);
            dayTv.setText(block[0]);
            dayTv.setTextColor(TEXT_PRIMARY);
            dayTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            dayTv.setTypeface(null, Typeface.BOLD);
            info.addView(dayTv);

            TextView timeTv = new TextView(this);
            timeTv.setText(block[1]);
            timeTv.setTextColor(TEXT_MUTED);
            timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            info.addView(timeTv);
            row.addView(info);

            TextView durTv = new TextView(this);
            durTv.setText(block[2]);
            durTv.setTextColor(ACCENT_GREEN);
            durTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            durTv.setTypeface(null, Typeface.BOLD);
            row.addView(durTv);

            card.addView(row);
            shown++;
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

    private Calendar parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || timeStr == null) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr + " " + timeStr));
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private int parseHour(String timeStr) {
        if (timeStr == null) return 9;
        try {
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 9;
        }
    }

    private String fmtTime(String timeStr) {
        if (timeStr == null) return "";
        try {
            String[] parts = timeStr.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String amPm = h < 12 ? "AM" : "PM";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return String.format(Locale.US, "%d:%02d %s", h12, m, amPm);
        } catch (Exception e) {
            return timeStr;
        }
    }

    private String fmtReadableDate(String dateStr) {
        Calendar cal = parseDate(dateStr);
        if (cal == null) return dateStr != null ? dateStr : "";
        Calendar today = Calendar.getInstance();
        if (isSameDay(cal, today)) return "Today";
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (isSameDay(cal, tomorrow)) return "Tomorrow";
        return new SimpleDateFormat("EEE, MMM d", Locale.US).format(cal.getTime());
    }

    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private Calendar firstOfMonth(Calendar cal) {
        Calendar result = (Calendar) cal.clone();
        result.set(Calendar.DAY_OF_MONTH, 1);
        return result;
    }

    private Calendar lastOfMonth(Calendar cal) {
        Calendar result = (Calendar) cal.clone();
        result.set(Calendar.DAY_OF_MONTH, result.getActualMaximum(Calendar.DAY_OF_MONTH));
        return result;
    }
}
