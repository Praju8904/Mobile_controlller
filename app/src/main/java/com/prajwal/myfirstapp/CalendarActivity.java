package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Complete Calendar Home Screen with Month/Week/Day/Agenda views.
 *
 * Features:
 *   - Rich header with month/year navigation, Today button, search, view switcher
 *   - Month View with event indicator dots, swipe navigation, today/selected highlights
 *   - Week View with 7-column time grid, colored event blocks, current time line
 *   - Day View with full-width timeline, mini calendar
 *   - Agenda View with chronological event list, date grouping
 *   - Selected Date Panel with week strip, rich event cards, empty state
 *   - Quick-add FAB
 *   - Full sync with Python backend via ConnectionManager
 *   - Local persistence via CalendarRepository
 *   - Notification scheduling for all events
 */
public class CalendarActivity extends AppCompatActivity {

    private static final String TAG = "CalendarActivity";
    private static CalendarActivity instance;

    // Connection & Data
    private String serverIp;
    private ConnectionManager connectionManager;
    private CalendarRepository repository;
    private CalendarSettings settings;

    // State
    private int currentYear, currentMonth; // month is 1-based
    private String selectedDate; // "YYYY-MM-DD"
    private String currentView = "month"; // month, week, day, agenda
    private Map<String, List<CalendarEvent>> monthEventMap = new HashMap<>();
    private List<CalendarEvent> selectedDateEvents = new ArrayList<>();

    // Views
    private TextView tvMonthYear, tvEventCountPill, tvViewName;
    private LinearLayout calendarGrid, dayHeaderRow;
    private LinearLayout datePanelContainer, weekStripContainer;
    private LinearLayout eventListContainer;
    private ScrollView mainScrollView;
    private LinearLayout weekViewContainer, dayViewContainer, agendaViewContainer;
    private LinearLayout monthViewSection;
    private FrameLayout calendarContentFrame;
    private TextView btnToday;

    // Swipe
    private float touchStartX = 0;
    private static final int SWIPE_THRESHOLD = 100;

    // Color Constants
    private static final int COLOR_BG_PRIMARY     = 0xFF0A0E21;
    private static final int COLOR_BG_SURFACE     = 0xFF111827;
    private static final int COLOR_BG_ELEVATED    = 0xFF1E293B;
    private static final int COLOR_TEXT_PRIMARY    = 0xFFF1F5F9;
    private static final int COLOR_TEXT_SECONDARY  = 0xFF94A3B8;
    private static final int COLOR_TEXT_MUTED      = 0xFF64748B;
    private static final int COLOR_ACCENT          = 0xFF3B82F6;
    private static final int COLOR_TODAY           = 0xFF3B82F6;
    private static final int COLOR_SELECTED        = 0xFF1D4ED8;
    private static final int COLOR_WEEKEND_MUTED   = 0xFF0F172A;
    private static final int COLOR_CURRENT_TIME    = 0xFFEF4444;
    private static final int COLOR_DIVIDER         = 0xFF1E293B;

    // Date Formatting
    private final SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE", Locale.US);
    private final SimpleDateFormat sdfMonthDay = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
    private final SimpleDateFormat sdfShortDay = new SimpleDateFormat("EEE", Locale.US);
    private final SimpleDateFormat sdfDayNum = new SimpleDateFormat("d", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        serverIp = getIntent().getStringExtra("server_ip");
        connectionManager = ConnectionManager.getInstance(this);
        repository = new CalendarRepository(this);
        settings = repository.getSettings();

        CalendarNotificationHelper.createNotificationChannels(this);

        Calendar now = Calendar.getInstance();
        currentYear = now.get(Calendar.YEAR);
        currentMonth = now.get(Calendar.MONTH) + 1;

        String intentDate = getIntent().getStringExtra("selected_date");
        if (intentDate != null && !intentDate.isEmpty()) {
            selectedDate = intentDate;
            try {
                String[] parts = intentDate.split("-");
                currentYear = Integer.parseInt(parts[0]);
                currentMonth = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* use current */ }
        } else {
            selectedDate = formatDate(now);
        }

        currentView = settings.defaultView != null ? settings.defaultView : "month";

        buildUI();
        loadAndRenderCurrentView();

        if (serverIp != null && !serverIp.isEmpty()) {
            requestCalendarSync();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        repository = new CalendarRepository(this);
        settings = repository.getSettings();
        loadAndRenderCurrentView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    public static CalendarActivity getInstance() {
        return instance;
    }

    // =====================================================================
    // UI CONSTRUCTION
    // =====================================================================

    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG_PRIMARY);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mainScrollView = new ScrollView(this);
        mainScrollView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainScrollView.setFillViewport(true);
        mainScrollView.setVerticalScrollBarEnabled(false);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mainContainer.addView(buildHeaderSection());

        calendarContentFrame = new FrameLayout(this);
        calendarContentFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        monthViewSection = buildMonthViewSection();
        weekViewContainer = buildViewContainer();
        dayViewContainer = buildViewContainer();
        agendaViewContainer = buildViewContainer();

        calendarContentFrame.addView(monthViewSection);
        calendarContentFrame.addView(weekViewContainer);
        calendarContentFrame.addView(dayViewContainer);
        calendarContentFrame.addView(agendaViewContainer);

        mainContainer.addView(calendarContentFrame);

        datePanelContainer = buildDatePanelSection();
        mainContainer.addView(datePanelContainer);

        mainScrollView.addView(mainContainer);
        root.addView(mainScrollView);

        // FAB
        FloatingActionButton fab = new FloatingActionButton(this);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, dpToPx(20), dpToPx(20));
        fab.setLayoutParams(fabParams);
        fab.setImageResource(android.R.drawable.ic_input_add);
        fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        fab.setColorFilter(Color.WHITE);
        fab.setOnClickListener(v -> launchEventCreation());
        root.addView(fab);

        setupSwipeGesture();
        setContentView(root);
    }

    // --- Header Section ---

    private LinearLayout buildHeaderSection() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0D1117);
        header.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(8));

        // Top bar: back + title + icons
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnBack = new TextView(this);
        btnBack.setText("\u2190");
        btnBack.setTextColor(COLOR_TEXT_PRIMARY);
        btnBack.setTextSize(20);
        btnBack.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView titleTv = new TextView(this);
        titleTv.setText("\uD83D\uDCC5 Calendar");
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(20);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setPadding(dpToPx(8), 0, 0, 0);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topBar.addView(titleTv);

        // Event count pill
        tvEventCountPill = new TextView(this);
        tvEventCountPill.setTextColor(COLOR_TEXT_PRIMARY);
        tvEventCountPill.setTextSize(11);
        tvEventCountPill.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dpToPx(12));
        pillBg.setColor(COLOR_ACCENT);
        tvEventCountPill.setBackground(pillBg);
        tvEventCountPill.setOnClickListener(v -> scrollToTodayEvents());
        topBar.addView(tvEventCountPill);

        addSpacer(topBar, 8);

        // Search icon
        TextView btnSearch = new TextView(this);
        btnSearch.setText("\uD83D\uDD0D");
        btnSearch.setTextSize(18);
        btnSearch.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnSearch.setOnClickListener(v -> launchSearch());
        topBar.addView(btnSearch);

        // Menu icon
        TextView btnMenu = new TextView(this);
        btnMenu.setText("\u22EE");
        btnMenu.setTextSize(22);
        btnMenu.setTextColor(COLOR_TEXT_SECONDARY);
        btnMenu.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        btnMenu.setOnClickListener(v -> showMenuPopup());
        topBar.addView(btnMenu);

        header.addView(topBar);
        addVerticalSpacer(header, 8);

        // Month/Year row with navigation
        LinearLayout monthNavRow = new LinearLayout(this);
        monthNavRow.setOrientation(LinearLayout.HORIZONTAL);
        monthNavRow.setGravity(Gravity.CENTER_VERTICAL);
        monthNavRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Previous
        TextView prevArrow = new TextView(this);
        prevArrow.setText("\u25C0");
        prevArrow.setTextColor(COLOR_TEXT_SECONDARY);
        prevArrow.setTextSize(16);
        prevArrow.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        prevArrow.setOnClickListener(v -> navigatePrevious());
        monthNavRow.addView(prevArrow);

        // Month/Year title
        tvMonthYear = new TextView(this);
        tvMonthYear.setTextColor(COLOR_TEXT_PRIMARY);
        tvMonthYear.setTextSize(22);
        tvMonthYear.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvMonthYear.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvMonthYear.setGravity(Gravity.CENTER);
        monthNavRow.addView(tvMonthYear);

        // Next
        TextView nextArrow = new TextView(this);
        nextArrow.setText("\u25B6");
        nextArrow.setTextColor(COLOR_TEXT_SECONDARY);
        nextArrow.setTextSize(16);
        nextArrow.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        nextArrow.setOnClickListener(v -> navigateNext());
        monthNavRow.addView(nextArrow);

        // Today button
        btnToday = new TextView(this);
        btnToday.setText("Today");
        btnToday.setTextColor(COLOR_ACCENT);
        btnToday.setTextSize(13);
        btnToday.setTypeface(null, Typeface.BOLD);
        btnToday.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        GradientDrawable todayBg = new GradientDrawable();
        todayBg.setCornerRadius(dpToPx(8));
        todayBg.setStroke(dpToPx(1), COLOR_ACCENT);
        btnToday.setBackground(todayBg);
        btnToday.setOnClickListener(v -> goToToday());
        monthNavRow.addView(btnToday);

        addSpacer(monthNavRow, 8);

        // View switcher
        LinearLayout viewSwitcherBtn = new LinearLayout(this);
        viewSwitcherBtn.setOrientation(LinearLayout.HORIZONTAL);
        viewSwitcherBtn.setGravity(Gravity.CENTER_VERTICAL);
        viewSwitcherBtn.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        GradientDrawable viewBg = new GradientDrawable();
        viewBg.setCornerRadius(dpToPx(8));
        viewBg.setColor(COLOR_BG_ELEVATED);
        viewSwitcherBtn.setBackground(viewBg);
        viewSwitcherBtn.setOnClickListener(v -> cycleView());

        TextView viewIcon = new TextView(this);
        viewIcon.setText("\uD83D\uDCCB");
        viewIcon.setTextSize(14);
        viewSwitcherBtn.addView(viewIcon);

        tvViewName = new TextView(this);
        tvViewName.setTextColor(COLOR_TEXT_SECONDARY);
        tvViewName.setTextSize(11);
        tvViewName.setPadding(dpToPx(4), 0, 0, 0);
        viewSwitcherBtn.addView(tvViewName);

        monthNavRow.addView(viewSwitcherBtn);
        header.addView(monthNavRow);

        return header;
    }

    // --- Month View Section ---

    private LinearLayout buildMonthViewSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dayHeaderRow = new LinearLayout(this);
        dayHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        dayHeaderRow.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(4));
        dayHeaderRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(dayHeaderRow);

        calendarGrid = new LinearLayout(this);
        calendarGrid.setOrientation(LinearLayout.VERTICAL);
        calendarGrid.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        calendarGrid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(calendarGrid);

        return section;
    }

    private LinearLayout buildViewContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setVisibility(View.GONE);
        return container;
    }

    // --- Date Panel Section (Below Month Grid) ---

    private LinearLayout buildDatePanelSection() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(80));
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(COLOR_BG_SURFACE);
        panelBg.setCornerRadii(new float[]{dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20), 0, 0, 0, 0});
        panel.setBackground(panelBg);

        // Handle bar
        View handleBar = new View(this);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dpToPx(4);
        handleParams.bottomMargin = dpToPx(8);
        handleBar.setLayoutParams(handleParams);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dpToPx(2));
        handleBg.setColor(COLOR_TEXT_MUTED);
        handleBar.setBackground(handleBg);
        panel.addView(handleBar);

        weekStripContainer = new LinearLayout(this);
        weekStripContainer.setOrientation(LinearLayout.VERTICAL);
        weekStripContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(weekStripContainer);

        eventListContainer = new LinearLayout(this);
        eventListContainer.setOrientation(LinearLayout.VERTICAL);
        eventListContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(eventListContainer);

        return panel;
    }

    // =====================================================================
    // VIEW MANAGEMENT
    // =====================================================================

    private void loadAndRenderCurrentView() {
        updateHeaderInfo();

        monthViewSection.setVisibility(View.GONE);
        weekViewContainer.setVisibility(View.GONE);
        dayViewContainer.setVisibility(View.GONE);
        agendaViewContainer.setVisibility(View.GONE);
        datePanelContainer.setVisibility(View.GONE);

        switch (currentView) {
            case "month":
                monthViewSection.setVisibility(View.VISIBLE);
                datePanelContainer.setVisibility(View.VISIBLE);
                renderMonthView();
                renderDatePanel();
                break;
            case "week":
                weekViewContainer.setVisibility(View.VISIBLE);
                renderWeekView();
                break;
            case "day":
                dayViewContainer.setVisibility(View.VISIBLE);
                renderDayView();
                break;
            case "agenda":
                agendaViewContainer.setVisibility(View.VISIBLE);
                renderAgendaView();
                break;
        }
        updateViewSwitcherLabel();
    }

    private void updateHeaderInfo() {
        tvMonthYear.setText(getMonthName(currentMonth) + " " + currentYear);
        int todayCount = repository.getTodayEventCount();
        tvEventCountPill.setText(todayCount + " event" + (todayCount != 1 ? "s" : "") + " today");
    }

    private void updateViewSwitcherLabel() {
        switch (currentView) {
            case "month":  tvViewName.setText("Month");  break;
            case "week":   tvViewName.setText("Week");   break;
            case "day":    tvViewName.setText("Day");    break;
            case "agenda": tvViewName.setText("Agenda"); break;
        }
    }

    // =====================================================================
    // MONTH VIEW RENDERING
    // =====================================================================

    private void renderMonthView() {
        monthEventMap = repository.getEventMapForMonth(currentYear, currentMonth);
        renderDayHeaders();
        renderMonthGrid();
    }

    private void renderDayHeaders() {
        dayHeaderRow.removeAllViews();
        String[] dayNames = getDayNamesForSettings();

        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(this);
            tv.setText(dayNames[i]);
            tv.setTextColor(i >= 5 ? COLOR_TEXT_MUTED : COLOR_TEXT_SECONDARY);
            tv.setTextSize(12);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            dayHeaderRow.addView(tv);
        }
    }

    private void renderMonthGrid() {
        calendarGrid.removeAllViews();

        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth - 1, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int startOffset = getStartOffset(firstDayOfWeek);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String todayStr = formatDate(Calendar.getInstance());

        int day = 1;
        for (int row = 0; row < 6 && day <= daysInMonth; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(58)));

            for (int col = 0; col < 7; col++) {
                LinearLayout cellLayout = new LinearLayout(this);
                cellLayout.setOrientation(LinearLayout.VERTICAL);
                cellLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                cellParams.setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1));
                cellLayout.setLayoutParams(cellParams);

                if ((row == 0 && col < startOffset) || day > daysInMonth) {
                    rowLayout.addView(cellLayout);
                    continue;
                }

                final String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                        currentYear, currentMonth, day);
                boolean isToday = dateStr.equals(todayStr);
                boolean isSelected = dateStr.equals(selectedDate);
                boolean isWeekend = col >= 5;
                List<CalendarEvent> dayEvents = monthEventMap.get(dateStr);
                boolean hasEvents = dayEvents != null && !dayEvents.isEmpty();

                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setCornerRadius(dpToPx(10));
                if (isSelected) {
                    cellBg.setColor(COLOR_SELECTED);
                } else if (isToday) {
                    cellBg.setStroke(dpToPx(2), COLOR_TODAY);
                    cellBg.setColor(Color.TRANSPARENT);
                } else if (isWeekend) {
                    cellBg.setColor(COLOR_WEEKEND_MUTED);
                } else {
                    cellBg.setColor(Color.TRANSPARENT);
                }
                cellLayout.setBackground(cellBg);
                cellLayout.setPadding(0, dpToPx(4), 0, dpToPx(2));

                TextView dayTv = new TextView(this);
                dayTv.setText(String.valueOf(day));
                dayTv.setTextSize(14);
                dayTv.setGravity(Gravity.CENTER);
                if (isToday && !isSelected) {
                    dayTv.setTextColor(COLOR_TODAY);
                    dayTv.setTypeface(null, Typeface.BOLD);
                } else if (isSelected) {
                    dayTv.setTextColor(Color.WHITE);
                    dayTv.setTypeface(null, Typeface.BOLD);
                } else {
                    dayTv.setTextColor(hasEvents ? COLOR_TEXT_PRIMARY : 0xFFCBD5E1);
                    dayTv.setTypeface(null, hasEvents ? Typeface.BOLD : Typeface.NORMAL);
                }
                cellLayout.addView(dayTv);

                // Event indicator dots (up to 3)
                if (hasEvents) {
                    LinearLayout dotsRow = new LinearLayout(this);
                    dotsRow.setOrientation(LinearLayout.HORIZONTAL);
                    dotsRow.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    dotsParams.topMargin = dpToPx(2);
                    dotsRow.setLayoutParams(dotsParams);

                    int dotCount = Math.min(dayEvents.size(), 3);
                    for (int d = 0; d < dotCount; d++) {
                        View dot = new View(this);
                        LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dpToPx(5), dpToPx(5));
                        dotP.setMargins(dpToPx(1), 0, dpToPx(1), 0);
                        dot.setLayoutParams(dotP);
                        GradientDrawable dotBg = new GradientDrawable();
                        dotBg.setShape(GradientDrawable.OVAL);
                        try {
                            dotBg.setColor(isSelected ? 0xFFBFDBFE :
                                    Color.parseColor(dayEvents.get(d).colorHex));
                        } catch (Exception e) {
                            dotBg.setColor(COLOR_ACCENT);
                        }
                        dot.setBackground(dotBg);
                        dotsRow.addView(dot);
                    }
                    cellLayout.addView(dotsRow);

                    if (dayEvents.size() > 3) {
                        TextView moreTv = new TextView(this);
                        moreTv.setText("+" + (dayEvents.size() - 3));
                        moreTv.setTextSize(8);
                        moreTv.setTextColor(isSelected ? 0xFFBFDBFE : COLOR_TEXT_MUTED);
                        moreTv.setGravity(Gravity.CENTER);
                        cellLayout.addView(moreTv);
                    }
                }

                cellLayout.setOnClickListener(v -> {
                    selectedDate = dateStr;
                    renderMonthGrid();
                    renderDatePanel();
                });

                rowLayout.addView(cellLayout);
                day++;
            }
            calendarGrid.addView(rowLayout);
        }
    }

    // =====================================================================
    // SELECTED DATE PANEL
    // =====================================================================

    private void renderDatePanel() {
        weekStripContainer.removeAllViews();
        eventListContainer.removeAllViews();

        // Date header
        try {
            Calendar cal = parseDateStr(selectedDate);
            if (cal != null) {
                TextView dateHeader = new TextView(this);
                String dayName = sdfDayName.format(cal.getTime());
                String fullDate = sdfMonthDay.format(cal.getTime());
                dateHeader.setText(dayName + ", " + fullDate);
                dateHeader.setTextColor(COLOR_TEXT_PRIMARY);
                dateHeader.setTextSize(18);
                dateHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                dateHeader.setPadding(0, dpToPx(4), 0, dpToPx(8));
                weekStripContainer.addView(dateHeader);
            }
        } catch (Exception e) { /* ignore */ }

        renderWeekStrip();

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        divider.setBackgroundColor(COLOR_DIVIDER);
        eventListContainer.addView(divider);
        addVerticalSpacer(eventListContainer, 8);

        selectedDateEvents = repository.getEventsForDate(selectedDate);

        if (selectedDateEvents.isEmpty()) {
            renderEmptyState();
        } else {
            renderEventCards(selectedDateEvents);
        }
    }

    private void renderWeekStrip() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setHorizontalScrollBarEnabled(false);

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, dpToPx(4), 0, dpToPx(8));

        Calendar center = parseDateStr(selectedDate);
        if (center == null) center = Calendar.getInstance();

        Calendar start = (Calendar) center.clone();
        start.add(Calendar.DAY_OF_YEAR, -3);

        String todayStr = formatDate(Calendar.getInstance());

        for (int i = 0; i < 7; i++) {
            Calendar dayCal = (Calendar) start.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, i);
            String dateStr = formatDate(dayCal);
            boolean isSelected = dateStr.equals(selectedDate);
            boolean isToday = dateStr.equals(todayStr);
            List<CalendarEvent> dayEvents = repository.getEventsForDate(dateStr);

            LinearLayout dayItem = new LinearLayout(this);
            dayItem.setOrientation(LinearLayout.VERTICAL);
            dayItem.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(dpToPx(48), ViewGroup.LayoutParams.WRAP_CONTENT);
            dayParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dayItem.setLayoutParams(dayParams);
            dayItem.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6));

            GradientDrawable dayBg = new GradientDrawable();
            dayBg.setCornerRadius(dpToPx(12));
            if (isSelected) {
                dayBg.setColor(COLOR_ACCENT);
            } else if (isToday) {
                dayBg.setStroke(dpToPx(1), COLOR_ACCENT);
                dayBg.setColor(Color.TRANSPARENT);
            } else {
                dayBg.setColor(COLOR_BG_ELEVATED);
            }
            dayItem.setBackground(dayBg);

            TextView dayNameTv = new TextView(this);
            dayNameTv.setText(sdfShortDay.format(dayCal.getTime()));
            dayNameTv.setTextColor(isSelected ? Color.WHITE : COLOR_TEXT_SECONDARY);
            dayNameTv.setTextSize(10);
            dayNameTv.setGravity(Gravity.CENTER);
            dayItem.addView(dayNameTv);

            TextView dayNumTv = new TextView(this);
            dayNumTv.setText(sdfDayNum.format(dayCal.getTime()));
            dayNumTv.setTextColor(isSelected ? Color.WHITE : COLOR_TEXT_PRIMARY);
            dayNumTv.setTextSize(16);
            dayNumTv.setTypeface(null, Typeface.BOLD);
            dayNumTv.setGravity(Gravity.CENTER);
            dayItem.addView(dayNumTv);

            if (!dayEvents.isEmpty()) {
                View dot = new View(this);
                LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dpToPx(4), dpToPx(4));
                dotP.gravity = Gravity.CENTER;
                dotP.topMargin = dpToPx(2);
                dot.setLayoutParams(dotP);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(isSelected ? Color.WHITE : COLOR_ACCENT);
                dot.setBackground(dotBg);
                dayItem.addView(dot);
            }

            final String clickDate = dateStr;
            dayItem.setOnClickListener(v -> {
                selectedDate = clickDate;
                renderMonthGrid();
                renderDatePanel();
            });

            strip.addView(dayItem);
        }

        scrollView.addView(strip);
        weekStripContainer.addView(scrollView);
    }

    // =====================================================================
    // EVENT CARDS
    // =====================================================================

    private void renderEventCards(List<CalendarEvent> events) {
        repository.sortEventsByStartTime(events);
        for (CalendarEvent event : events) {
            eventListContainer.addView(buildEventCard(event));
            addVerticalSpacer(eventListContainer, 8);
        }
    }

    private LinearLayout buildEventCard(CalendarEvent event) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dpToPx(12));
        cardBg.setColor(event.getBackgroundColor());
        card.setBackground(cardBg);

        // Colored left accent border
        View leftBorder = new View(this);
        LinearLayout.LayoutParams borderParams = new LinearLayout.LayoutParams(
                dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT);
        borderParams.setMargins(0, 0, dpToPx(12), 0);
        leftBorder.setLayoutParams(borderParams);
        GradientDrawable borderBg = new GradientDrawable();
        borderBg.setCornerRadius(dpToPx(2));
        borderBg.setColor(event.getAccentColor());
        leftBorder.setBackground(borderBg);
        card.addView(leftBorder);

        // Content area
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText(event.title);
        titleTv.setTextColor(COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(15);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(titleTv);

        if (event.isStarred) {
            TextView starTv = new TextView(this);
            starTv.setText("\u2B50");
            starTv.setTextSize(14);
            starTv.setPadding(dpToPx(4), 0, 0, 0);
            titleRow.addView(starTv);
        }

        if (event.isRecurring()) {
            TextView recurTv = new TextView(this);
            recurTv.setText("\uD83D\uDD04");
            recurTv.setTextSize(12);
            recurTv.setPadding(dpToPx(4), 0, 0, 0);
            titleRow.addView(recurTv);
        }

        content.addView(titleRow);

        // Time row
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(0, dpToPx(3), 0, 0);

        if (event.isAllDay || !event.hasStartTime()) {
            TextView allDayBadge = new TextView(this);
            allDayBadge.setText("ALL DAY");
            allDayBadge.setTextColor(event.getAccentColor());
            allDayBadge.setTextSize(10);
            allDayBadge.setTypeface(null, Typeface.BOLD);
            allDayBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dpToPx(4));
            badgeBg.setStroke(dpToPx(1), event.getAccentColor());
            allDayBadge.setBackground(badgeBg);
            timeRow.addView(allDayBadge);
        } else {
            TextView timeTv = new TextView(this);
            timeTv.setText("\uD83D\uDD50 " + event.getFormattedTimeRange());
            timeTv.setTextColor(COLOR_TEXT_SECONDARY);
            timeTv.setTextSize(12);
            timeRow.addView(timeTv);
        }

        content.addView(timeRow);

        // Location
        if (event.hasLocation()) {
            TextView locTv = new TextView(this);
            locTv.setText("\uD83D\uDCCD " + event.location);
            locTv.setTextColor(COLOR_TEXT_MUTED);
            locTv.setTextSize(11);
            locTv.setPadding(0, dpToPx(2), 0, 0);
            locTv.setMaxLines(1);
            locTv.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(locTv);
        }

        // Category badge
        EventCategory category = repository.getCategoryById(event.categoryId);
        if (category != null) {
            LinearLayout catRow = new LinearLayout(this);
            catRow.setOrientation(LinearLayout.HORIZONTAL);
            catRow.setGravity(Gravity.CENTER_VERTICAL);
            catRow.setPadding(0, dpToPx(4), 0, 0);

            TextView catBadge = new TextView(this);
            catBadge.setText(category.iconIdentifier + " " + category.name);
            catBadge.setTextColor(category.getColor());
            catBadge.setTextSize(10);
            catBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            GradientDrawable catBg = new GradientDrawable();
            catBg.setCornerRadius(dpToPx(8));
            catBg.setColor(category.getBackgroundColor());
            catBadge.setBackground(catBg);
            catRow.addView(catBadge);

            content.addView(catRow);
        }

        card.addView(content);

        // Three-dot menu
        TextView menuBtn = new TextView(this);
        menuBtn.setText("\u22EE");
        menuBtn.setTextColor(COLOR_TEXT_SECONDARY);
        menuBtn.setTextSize(18);
        menuBtn.setPadding(dpToPx(8), 0, 0, 0);
        menuBtn.setOnClickListener(v -> showEventCardMenu(event));
        card.addView(menuBtn);

        card.setOnClickListener(v -> openEventDetail(event));

        return card;
    }

    private void renderEmptyState() {
        LinearLayout emptyContainer = new LinearLayout(this);
        emptyContainer.setOrientation(LinearLayout.VERTICAL);
        emptyContainer.setGravity(Gravity.CENTER);
        emptyContainer.setPadding(dpToPx(20), dpToPx(40), dpToPx(20), dpToPx(40));

        TextView emptyIcon = new TextView(this);
        emptyIcon.setText("\uD83D\uDCED");
        emptyIcon.setTextSize(48);
        emptyIcon.setGravity(Gravity.CENTER);
        emptyContainer.addView(emptyIcon);

        TextView emptyTitle = new TextView(this);
        emptyTitle.setText("No events");
        emptyTitle.setTextColor(COLOR_TEXT_SECONDARY);
        emptyTitle.setTextSize(16);
        emptyTitle.setTypeface(null, Typeface.BOLD);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setPadding(0, dpToPx(8), 0, dpToPx(4));
        emptyContainer.addView(emptyTitle);

        TextView emptySub = new TextView(this);
        emptySub.setText("Nothing scheduled for this day.\nTap + to add an event.");
        emptySub.setTextColor(COLOR_TEXT_MUTED);
        emptySub.setTextSize(13);
        emptySub.setGravity(Gravity.CENTER);
        emptyContainer.addView(emptySub);

        eventListContainer.addView(emptyContainer);
    }

    // =====================================================================
    // WEEK VIEW
    // =====================================================================

    private void renderWeekView() {
        weekViewContainer.removeAllViews();

        Calendar weekStart = parseDateStr(selectedDate);
        if (weekStart == null) weekStart = Calendar.getInstance();
        int firstDay = settings.getFirstDayOfWeekCalendar();
        while (weekStart.get(Calendar.DAY_OF_WEEK) != firstDay) {
            weekStart.add(Calendar.DAY_OF_YEAR, -1);
        }

        String todayStr = formatDate(Calendar.getInstance());

        // Week header with day columns
        LinearLayout weekHeader = new LinearLayout(this);
        weekHeader.setOrientation(LinearLayout.HORIZONTAL);
        weekHeader.setPadding(dpToPx(50), dpToPx(8), dpToPx(4), dpToPx(4));
        weekHeader.setBackgroundColor(0xFF0D1117);

        // All-day strip
        LinearLayout allDayStrip = new LinearLayout(this);
        allDayStrip.setOrientation(LinearLayout.HORIZONTAL);
        allDayStrip.setPadding(dpToPx(50), dpToPx(2), dpToPx(4), dpToPx(4));
        boolean hasAnyAllDay = false;

        for (int d = 0; d < 7; d++) {
            Calendar dayCal = (Calendar) weekStart.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, d);
            String dateStr = formatDate(dayCal);
            boolean isToday = dateStr.equals(todayStr);
            boolean isSelected = dateStr.equals(selectedDate);

            LinearLayout dayCol = new LinearLayout(this);
            dayCol.setOrientation(LinearLayout.VERTICAL);
            dayCol.setGravity(Gravity.CENTER);
            dayCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView dayNameTv = new TextView(this);
            dayNameTv.setText(sdfShortDay.format(dayCal.getTime()).toUpperCase());
            dayNameTv.setTextColor(isToday ? COLOR_TODAY : COLOR_TEXT_MUTED);
            dayNameTv.setTextSize(10);
            dayNameTv.setGravity(Gravity.CENTER);
            dayCol.addView(dayNameTv);

            TextView dayNumTv = new TextView(this);
            dayNumTv.setText(String.valueOf(dayCal.get(Calendar.DAY_OF_MONTH)));
            dayNumTv.setTextSize(14);
            dayNumTv.setGravity(Gravity.CENTER);
            if (isToday) {
                dayNumTv.setTextColor(Color.WHITE);
                dayNumTv.setTypeface(null, Typeface.BOLD);
                GradientDrawable todayCircle = new GradientDrawable();
                todayCircle.setShape(GradientDrawable.OVAL);
                todayCircle.setColor(COLOR_TODAY);
                dayNumTv.setBackground(todayCircle);
                dayNumTv.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            } else {
                dayNumTv.setTextColor(isSelected ? COLOR_ACCENT : COLOR_TEXT_PRIMARY);
            }
            dayCol.addView(dayNumTv);

            final String clickDate = dateStr;
            dayCol.setOnClickListener(v -> {
                selectedDate = clickDate;
                loadAndRenderCurrentView();
            });
            weekHeader.addView(dayCol);

            // All-day events for this day
            List<CalendarEvent> dayEvents = repository.getEventsForDate(dateStr);
            LinearLayout allDayCol = new LinearLayout(this);
            allDayCol.setOrientation(LinearLayout.VERTICAL);
            allDayCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            allDayCol.setPadding(dpToPx(1), 0, dpToPx(1), 0);

            for (CalendarEvent event : dayEvents) {
                if (event.isAllDay || !event.hasStartTime()) {
                    hasAnyAllDay = true;
                    TextView allDayTv = new TextView(this);
                    allDayTv.setText(event.title);
                    allDayTv.setTextColor(Color.WHITE);
                    allDayTv.setTextSize(9);
                    allDayTv.setMaxLines(1);
                    allDayTv.setPadding(dpToPx(3), dpToPx(1), dpToPx(3), dpToPx(1));
                    GradientDrawable adBg = new GradientDrawable();
                    adBg.setCornerRadius(dpToPx(3));
                    adBg.setColor(event.getAccentColor());
                    allDayTv.setBackground(adBg);
                    allDayTv.setOnClickListener(v -> openEventDetail(event));
                    allDayCol.addView(allDayTv);
                }
            }
            allDayStrip.addView(allDayCol);
        }

        weekViewContainer.addView(weekHeader);
        if (hasAnyAllDay) {
            weekViewContainer.addView(allDayStrip);
        }

        addDivider(weekViewContainer);

        // Time grid
        ScrollView timeScroll = new ScrollView(this);
        timeScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(500)));

        FrameLayout timeGridFrame = new FrameLayout(this);
        LinearLayout timeGrid = new LinearLayout(this);
        timeGrid.setOrientation(LinearLayout.VERTICAL);

        int startHour = Math.max(0, settings.getWorkingHoursStartHour() - 1);

        for (int hour = startHour; hour < 24; hour++) {
            LinearLayout hourRow = new LinearLayout(this);
            hourRow.setOrientation(LinearLayout.HORIZONTAL);
            hourRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));

            TextView timeLbl = new TextView(this);
            timeLbl.setText(formatHour(hour));
            timeLbl.setTextColor(COLOR_TEXT_MUTED);
            timeLbl.setTextSize(10);
            timeLbl.setGravity(Gravity.END | Gravity.TOP);
            timeLbl.setPadding(dpToPx(4), 0, dpToPx(4), 0);
            timeLbl.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(46), ViewGroup.LayoutParams.MATCH_PARENT));
            hourRow.addView(timeLbl);

            for (int d = 0; d < 7; d++) {
                View cell = new View(this);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                boolean isWorkingHour = hour >= settings.getWorkingHoursStartHour() &&
                        hour < settings.getWorkingHoursEndHour();
                GradientDrawable cellBg = new GradientDrawable();
                cellBg.setColor(isWorkingHour ? 0xFF0F1629 : 0xFF0A0E1A);
                cellBg.setStroke(1, 0xFF1E293B);
                cell.setBackground(cellBg);

                final int clickHour = hour;
                Calendar clickDayCal = (Calendar) weekStart.clone();
                clickDayCal.add(Calendar.DAY_OF_YEAR, d);
                final String clickDate = formatDate(clickDayCal);
                cell.setOnClickListener(v -> launchQuickAdd(clickDate, String.format(Locale.US, "%02d:00", clickHour)));

                hourRow.addView(cell);
            }
            timeGrid.addView(hourRow);
        }

        timeGridFrame.addView(timeGrid);
        overlayWeekEventBlocks(timeGridFrame, weekStart, startHour);
        addCurrentTimeLine(timeGridFrame, weekStart, startHour);

        timeScroll.addView(timeGridFrame);
        weekViewContainer.addView(timeScroll);

        // Auto-scroll to working hours
        final int fStartHour = startHour;
        timeScroll.post(() -> {
            int scrollHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int scrollTo = dpToPx((scrollHour - fStartHour - 1) * 60);
            if (scrollTo > 0) timeScroll.smoothScrollTo(0, scrollTo);
        });
    }

    private void overlayWeekEventBlocks(FrameLayout frame, Calendar weekStart, int startHour) {
        for (int d = 0; d < 7; d++) {
            Calendar dayCal = (Calendar) weekStart.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, d);
            String dateStr = formatDate(dayCal);
            List<CalendarEvent> dayEvents = repository.getEventsForDate(dateStr);

            List<CalendarEvent> timedEvents = new ArrayList<>();
            for (CalendarEvent ev : dayEvents) {
                if (!ev.isAllDay && ev.hasStartTime()) timedEvents.add(ev);
            }

            int columnWidth = (getScreenWidth() - dpToPx(50)) / 7;
            int columnOffset = dpToPx(50) + d * columnWidth;

            for (int e = 0; e < timedEvents.size(); e++) {
                CalendarEvent event = timedEvents.get(e);
                int eStartMin = parseTimeToMinutes(event.startTime) - startHour * 60;
                int eEndMin = event.hasEndTime() ? parseTimeToMinutes(event.endTime) - startHour * 60
                        : eStartMin + settings.defaultEventDurationMinutes;
                if (eStartMin < 0) eStartMin = 0;
                int duration = Math.max(eEndMin - eStartMin, 15);

                int overlapCount = countOverlaps(timedEvents, event);
                int overlapIndex = getOverlapIndex(timedEvents.subList(0, e + 1), event);
                int eventWidth = columnWidth / Math.max(1, overlapCount);
                int eventLeft = columnOffset + overlapIndex * eventWidth;

                TextView block = new TextView(this);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                        eventWidth - dpToPx(2), dpToPx(duration) - dpToPx(1));
                bp.topMargin = dpToPx(eStartMin);
                bp.leftMargin = eventLeft;
                block.setLayoutParams(bp);
                block.setText(event.title);
                block.setTextColor(Color.WHITE);
                block.setTextSize(9);
                block.setPadding(dpToPx(3), dpToPx(2), dpToPx(2), dpToPx(1));
                block.setMaxLines(dpToPx(duration) > dpToPx(30) ? 2 : 1);
                block.setEllipsize(TextUtils.TruncateAt.END);

                GradientDrawable blockBg = new GradientDrawable();
                blockBg.setCornerRadius(dpToPx(4));
                blockBg.setColor(event.getAccentColor());
                block.setBackground(blockBg);
                block.setOnClickListener(v -> openEventDetail(event));

                frame.addView(block);
            }
        }
    }

    // =====================================================================
    // DAY VIEW
    // =====================================================================

    private void renderDayView() {
        dayViewContainer.removeAllViews();

        // Mini calendar toggle
        LinearLayout miniCalSection = new LinearLayout(this);
        miniCalSection.setOrientation(LinearLayout.VERTICAL);
        miniCalSection.setBackgroundColor(0xFF0D1117);
        miniCalSection.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        LinearLayout miniGridWrapper = new LinearLayout(this);
        miniGridWrapper.setOrientation(LinearLayout.VERTICAL);
        miniGridWrapper.setVisibility(View.GONE);

        TextView toggleMiniCal = new TextView(this);
        toggleMiniCal.setText("\uD83D\uDCC5 " + getMonthName(currentMonth) + " " + currentYear + "  \u25BC");
        toggleMiniCal.setTextColor(COLOR_TEXT_SECONDARY);
        toggleMiniCal.setTextSize(12);
        toggleMiniCal.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        toggleMiniCal.setOnClickListener(v -> {
            if (miniGridWrapper.getVisibility() == View.GONE) {
                miniGridWrapper.setVisibility(View.VISIBLE);
                toggleMiniCal.setText("\uD83D\uDCC5 " + getMonthName(currentMonth) + " " + currentYear + "  \u25B2");
            } else {
                miniGridWrapper.setVisibility(View.GONE);
                toggleMiniCal.setText("\uD83D\uDCC5 " + getMonthName(currentMonth) + " " + currentYear + "  \u25BC");
            }
        });

        buildMiniMonthGrid(miniGridWrapper);
        miniCalSection.addView(toggleMiniCal);
        miniCalSection.addView(miniGridWrapper);
        dayViewContainer.addView(miniCalSection);

        // Day header
        Calendar dayCal = parseDateStr(selectedDate);
        if (dayCal == null) dayCal = Calendar.getInstance();

        TextView dayHeader = new TextView(this);
        dayHeader.setText(sdfDayName.format(dayCal.getTime()) + ", " + sdfMonthDay.format(dayCal.getTime()));
        dayHeader.setTextColor(COLOR_TEXT_PRIMARY);
        dayHeader.setTextSize(18);
        dayHeader.setTypeface(null, Typeface.BOLD);
        dayHeader.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4));
        dayViewContainer.addView(dayHeader);

        // Separate all-day vs timed
        List<CalendarEvent> dayEvents = repository.getEventsForDate(selectedDate);
        List<CalendarEvent> allDayEvents = new ArrayList<>();
        List<CalendarEvent> timedEvents = new ArrayList<>();
        for (CalendarEvent ev : dayEvents) {
            if (ev.isAllDay || !ev.hasStartTime()) allDayEvents.add(ev);
            else timedEvents.add(ev);
        }

        // All-day bar
        if (!allDayEvents.isEmpty()) {
            LinearLayout allDaySection = new LinearLayout(this);
            allDaySection.setOrientation(LinearLayout.HORIZONTAL);
            allDaySection.setPadding(dpToPx(50), dpToPx(4), dpToPx(8), dpToPx(4));
            allDaySection.setBackgroundColor(0xFF0F1629);
            for (CalendarEvent ev : allDayEvents) {
                TextView allDayTv = new TextView(this);
                allDayTv.setText(ev.title);
                allDayTv.setTextColor(Color.WHITE);
                allDayTv.setTextSize(11);
                allDayTv.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
                LinearLayout.LayoutParams adp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                adp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
                allDayTv.setLayoutParams(adp);
                GradientDrawable adBg = new GradientDrawable();
                adBg.setCornerRadius(dpToPx(4));
                adBg.setColor(ev.getAccentColor());
                allDayTv.setBackground(adBg);
                allDayTv.setOnClickListener(v -> openEventDetail(ev));
                allDaySection.addView(allDayTv);
            }
            dayViewContainer.addView(allDaySection);
        }

        // Day timeline
        ScrollView dayScroll = new ScrollView(this);
        dayScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(500)));

        FrameLayout dayGridFrame = new FrameLayout(this);
        LinearLayout dayGrid = new LinearLayout(this);
        dayGrid.setOrientation(LinearLayout.VERTICAL);

        int startHour = Math.max(0, settings.getWorkingHoursStartHour() - 1);

        for (int hour = startHour; hour < 24; hour++) {
            LinearLayout hourRow = new LinearLayout(this);
            hourRow.setOrientation(LinearLayout.HORIZONTAL);
            hourRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)));

            TextView timeLbl = new TextView(this);
            timeLbl.setText(formatHour(hour));
            timeLbl.setTextColor(COLOR_TEXT_MUTED);
            timeLbl.setTextSize(10);
            timeLbl.setGravity(Gravity.END | Gravity.TOP);
            timeLbl.setPadding(dpToPx(4), 0, dpToPx(8), 0);
            timeLbl.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(46), ViewGroup.LayoutParams.MATCH_PARENT));
            hourRow.addView(timeLbl);

            View cell = new View(this);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            boolean isWork = hour >= settings.getWorkingHoursStartHour() && hour < settings.getWorkingHoursEndHour();
            GradientDrawable cellBg = new GradientDrawable();
            cellBg.setColor(isWork ? 0xFF0F1629 : 0xFF0A0E1A);
            cellBg.setStroke(1, 0xFF1E293B);
            cell.setBackground(cellBg);

            final int clickHour = hour;
            cell.setOnClickListener(v -> launchQuickAdd(selectedDate, String.format(Locale.US, "%02d:00", clickHour)));
            hourRow.addView(cell);
            dayGrid.addView(hourRow);
        }

        dayGridFrame.addView(dayGrid);

        // Overlay event blocks (full width)
        int contentWidth = getScreenWidth() - dpToPx(50);
        for (int e = 0; e < timedEvents.size(); e++) {
            CalendarEvent event = timedEvents.get(e);
            int eStartMin = parseTimeToMinutes(event.startTime) - startHour * 60;
            int eEndMin = event.hasEndTime() ? parseTimeToMinutes(event.endTime) - startHour * 60
                    : eStartMin + settings.defaultEventDurationMinutes;
            if (eStartMin < 0) eStartMin = 0;
            int duration = Math.max(eEndMin - eStartMin, 15);

            int overlapCount = countOverlaps(timedEvents, event);
            int overlapIndex = getOverlapIndex(timedEvents.subList(0, e + 1), event);
            int eventWidth = contentWidth / Math.max(1, overlapCount);

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                    eventWidth - dpToPx(4), dpToPx(duration) - dpToPx(2));
            bp.topMargin = dpToPx(eStartMin);
            bp.leftMargin = dpToPx(50) + overlapIndex * eventWidth;
            block.setLayoutParams(bp);
            block.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));

            GradientDrawable blockBg = new GradientDrawable();
            blockBg.setCornerRadius(dpToPx(6));
            blockBg.setColor(event.getAccentColor());
            block.setBackground(blockBg);

            TextView titleTv = new TextView(this);
            titleTv.setText(event.title);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTextSize(12);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setMaxLines(1);
            block.addView(titleTv);

            if (dpToPx(duration) > dpToPx(30)) {
                TextView timeTv = new TextView(this);
                timeTv.setText(event.getFormattedTimeRange());
                timeTv.setTextColor(0xBBFFFFFF);
                timeTv.setTextSize(10);
                block.addView(timeTv);
            }

            if (dpToPx(duration) > dpToPx(50) && event.hasLocation()) {
                TextView locTv = new TextView(this);
                locTv.setText("\uD83D\uDCCD " + event.location);
                locTv.setTextColor(0x99FFFFFF);
                locTv.setTextSize(9);
                block.addView(locTv);
            }

            block.setOnClickListener(v -> openEventDetail(event));
            dayGridFrame.addView(block);
        }

        // Current time line for today
        addCurrentTimeLineForDay(dayGridFrame, startHour);

        dayScroll.addView(dayGridFrame);
        dayViewContainer.addView(dayScroll);

        final int fStartHour = startHour;
        dayScroll.post(() -> {
            int scrollHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (!timedEvents.isEmpty()) {
                scrollHour = parseTimeToMinutes(timedEvents.get(0).startTime) / 60;
            }
            int scrollTo = dpToPx((scrollHour - fStartHour - 1) * 60);
            if (scrollTo > 0) dayScroll.smoothScrollTo(0, scrollTo);
        });
    }

    private void buildMiniMonthGrid(LinearLayout container) {
        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth - 1, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int startOffset = getStartOffset(firstDayOfWeek);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String todayStr = formatDate(Calendar.getInstance());

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dayNames = getDayNamesForSettings();
        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(this);
            tv.setText(dayNames[i].substring(0, 1));
            tv.setTextColor(COLOR_TEXT_MUTED);
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            headerRow.addView(tv);
        }
        container.addView(headerRow);

        int day = 1;
        for (int row = 0; row < 6 && day <= daysInMonth; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(28)));

            for (int col = 0; col < 7; col++) {
                TextView tv = new TextView(this);
                tv.setTextSize(11);
                tv.setGravity(Gravity.CENTER);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                if ((row == 0 && col < startOffset) || day > daysInMonth) {
                    tv.setText("");
                } else {
                    tv.setText(String.valueOf(day));
                    String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth, day);
                    boolean isToday = dateStr.equals(todayStr);
                    boolean isSelected = dateStr.equals(selectedDate);

                    if (isSelected) {
                        tv.setTextColor(Color.WHITE);
                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.OVAL);
                        bg.setColor(COLOR_ACCENT);
                        tv.setBackground(bg);
                    } else if (isToday) {
                        tv.setTextColor(COLOR_TODAY);
                        tv.setTypeface(null, Typeface.BOLD);
                    } else {
                        tv.setTextColor(COLOR_TEXT_SECONDARY);
                    }

                    final String clickDate = dateStr;
                    tv.setOnClickListener(v -> {
                        selectedDate = clickDate;
                        loadAndRenderCurrentView();
                    });
                    day++;
                }
                rowLayout.addView(tv);
            }
            container.addView(rowLayout);
        }
    }

    // =====================================================================
    // AGENDA VIEW
    // =====================================================================

    private void renderAgendaView() {
        agendaViewContainer.removeAllViews();

        // Jump to Date button
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView jumpBtn = new TextView(this);
        jumpBtn.setText("\uD83D\uDCC5 Jump to Date");
        jumpBtn.setTextColor(COLOR_ACCENT);
        jumpBtn.setTextSize(13);
        jumpBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        GradientDrawable jumpBg = new GradientDrawable();
        jumpBg.setCornerRadius(dpToPx(8));
        jumpBg.setStroke(dpToPx(1), COLOR_ACCENT);
        jumpBtn.setBackground(jumpBg);
        jumpBtn.setOnClickListener(v -> showDatePicker());
        topBar.addView(jumpBtn);

        agendaViewContainer.addView(topBar);

        List<CalendarEvent> upcoming = repository.getUpcomingEvents(0);

        if (upcoming.isEmpty()) {
            LinearLayout emptyState = new LinearLayout(this);
            emptyState.setOrientation(LinearLayout.VERTICAL);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(dpToPx(20), dpToPx(60), dpToPx(20), dpToPx(60));

            TextView emptyIcon = new TextView(this);
            emptyIcon.setText("\uD83D\uDCED");
            emptyIcon.setTextSize(48);
            emptyIcon.setGravity(Gravity.CENTER);
            emptyState.addView(emptyIcon);

            TextView emptyText = new TextView(this);
            emptyText.setText("No upcoming events");
            emptyText.setTextColor(COLOR_TEXT_SECONDARY);
            emptyText.setTextSize(16);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dpToPx(8), 0, 0);
            emptyState.addView(emptyText);

            agendaViewContainer.addView(emptyState);
            return;
        }

        // Group by date
        LinkedHashMap<String, List<CalendarEvent>> groups = new LinkedHashMap<>();
        for (CalendarEvent event : upcoming) {
            String date = event.startDate;
            if (!groups.containsKey(date)) {
                groups.put(date, new ArrayList<>());
            }
            groups.get(date).add(event);
        }

        String todayStr = formatDate(Calendar.getInstance());
        Calendar tmrw = Calendar.getInstance();
        tmrw.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrowStr = formatDate(tmrw);

        for (Map.Entry<String, List<CalendarEvent>> entry : groups.entrySet()) {
            String dateStr = entry.getKey();
            List<CalendarEvent> events = entry.getValue();

            Calendar dateCal = parseDateStr(dateStr);
            if (dateCal == null) continue;

            String dateLabel;
            if (dateStr.equals(todayStr)) {
                dateLabel = "Today, " + sdfMonthDay.format(dateCal.getTime());
            } else if (dateStr.equals(tomorrowStr)) {
                dateLabel = "Tomorrow, " + sdfMonthDay.format(dateCal.getTime());
            } else {
                dateLabel = sdfDayName.format(dateCal.getTime()) + ", " + sdfMonthDay.format(dateCal.getTime());
            }

            TextView dateHeaderTv = new TextView(this);
            dateHeaderTv.setText(dateLabel);
            dateHeaderTv.setTextColor(COLOR_TEXT_PRIMARY);
            dateHeaderTv.setTextSize(14);
            dateHeaderTv.setTypeface(null, Typeface.BOLD);
            dateHeaderTv.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(6));
            dateHeaderTv.setBackgroundColor(COLOR_BG_PRIMARY);
            agendaViewContainer.addView(dateHeaderTv);

            LinearLayout eventsSection = new LinearLayout(this);
            eventsSection.setOrientation(LinearLayout.VERTICAL);
            eventsSection.setPadding(dpToPx(16), 0, dpToPx(16), 0);

            for (CalendarEvent event : events) {
                eventsSection.addView(buildEventCard(event));
                addVerticalSpacer(eventsSection, 6);
            }
            agendaViewContainer.addView(eventsSection);
        }

        // Footer
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(80));
        TextView footerTv = new TextView(this);
        footerTv.setText("\u2014 No more events \u2014");
        footerTv.setTextColor(COLOR_TEXT_MUTED);
        footerTv.setTextSize(12);
        footer.addView(footerTv);
        agendaViewContainer.addView(footer);
    }

    // =====================================================================
    // NAVIGATION & ACTIONS
    // =====================================================================

    private void navigatePrevious() {
        switch (currentView) {
            case "month":
                if (currentMonth == 1) { currentMonth = 12; currentYear--; }
                else currentMonth--;
                break;
            case "week":
                Calendar wc = parseDateStr(selectedDate);
                if (wc != null) {
                    wc.add(Calendar.WEEK_OF_YEAR, -1);
                    selectedDate = formatDate(wc);
                    currentMonth = wc.get(Calendar.MONTH) + 1;
                    currentYear = wc.get(Calendar.YEAR);
                }
                break;
            case "day":
                Calendar dc = parseDateStr(selectedDate);
                if (dc != null) {
                    dc.add(Calendar.DAY_OF_YEAR, -1);
                    selectedDate = formatDate(dc);
                    currentMonth = dc.get(Calendar.MONTH) + 1;
                    currentYear = dc.get(Calendar.YEAR);
                }
                break;
        }
        loadAndRenderCurrentView();
    }

    private void navigateNext() {
        switch (currentView) {
            case "month":
                if (currentMonth == 12) { currentMonth = 1; currentYear++; }
                else currentMonth++;
                break;
            case "week":
                Calendar wc = parseDateStr(selectedDate);
                if (wc != null) {
                    wc.add(Calendar.WEEK_OF_YEAR, 1);
                    selectedDate = formatDate(wc);
                    currentMonth = wc.get(Calendar.MONTH) + 1;
                    currentYear = wc.get(Calendar.YEAR);
                }
                break;
            case "day":
                Calendar dc = parseDateStr(selectedDate);
                if (dc != null) {
                    dc.add(Calendar.DAY_OF_YEAR, 1);
                    selectedDate = formatDate(dc);
                    currentMonth = dc.get(Calendar.MONTH) + 1;
                    currentYear = dc.get(Calendar.YEAR);
                }
                break;
        }
        loadAndRenderCurrentView();
    }

    private void goToToday() {
        Calendar now = Calendar.getInstance();
        currentYear = now.get(Calendar.YEAR);
        currentMonth = now.get(Calendar.MONTH) + 1;
        selectedDate = formatDate(now);
        loadAndRenderCurrentView();
    }

    private void cycleView() {
        String[] views = {"month", "week", "day", "agenda"};
        String[] labels = {"Month", "Week", "Day", "Agenda"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Calendar View");
        builder.setItems(labels, (dialog, which) -> {
            currentView = views[which];
            loadAndRenderCurrentView();
        });
        builder.show();
    }

    private void scrollToTodayEvents() {
        String todayStr = formatDate(Calendar.getInstance());
        selectedDate = todayStr;
        currentYear = Calendar.getInstance().get(Calendar.YEAR);
        currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
        loadAndRenderCurrentView();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (datePanelContainer.getVisibility() == View.VISIBLE) {
                mainScrollView.smoothScrollTo(0, datePanelContainer.getTop());
            }
        }, 200);
    }

    private void setupSwipeGesture() {
        calendarContentFrame.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getX();
                    return true;
                case MotionEvent.ACTION_UP:
                    float deltaX = event.getX() - touchStartX;
                    if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                        if (deltaX > 0) navigatePrevious();
                        else navigateNext();
                        return true;
                    }
                    break;
            }
            return false;
        });
    }

    // =====================================================================
    // ACTIONS: MENUS, SEARCH, CREATE, DETAIL
    // =====================================================================

    private void showMenuPopup() {
        String[] options = {
            "\uD83D\uDCCA Dashboard", 
            "\uD83D\uDCC8 Analytics",
            "\uD83D\uDCC1 Categories", 
            "\u2699\uFE0F Settings", 
            "\uD83D\uDD04 Sync with PC"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    startActivity(new Intent(this, CalendarDashboardActivity.class));
                    overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
                    break;
                case 1:
                    startActivity(new Intent(this, CalendarAnalyticsActivity.class));
                    overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
                    break;
                case 2:
                    startActivity(new Intent(this, CalendarCategoriesActivity.class));
                    break;
                case 3:
                    startActivity(new Intent(this, CalendarSettingsActivity.class));
                    break;
                case 4:
                    requestCalendarSync();
                    Toast.makeText(this, "Syncing calendar...", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        builder.show();
    }

    private void launchSearch() {
        startActivity(new Intent(this, CalendarSearchActivity.class));
    }

    private void launchEventCreation() {
        Intent intent = new Intent(this, CalendarEventDetailActivity.class);
        intent.putExtra("mode", "create");
        intent.putExtra("selected_date", selectedDate);
        startActivity(intent);
    }

    private void launchQuickAdd(String date, String time) {
        Intent intent = new Intent(this, CalendarEventDetailActivity.class);
        intent.putExtra("mode", "create");
        intent.putExtra("selected_date", date);
        intent.putExtra("start_time", time);
        startActivity(intent);
    }

    private void openEventDetail(CalendarEvent event) {
        Intent intent = new Intent(this, CalendarEventDetailActivity.class);
        intent.putExtra("mode", "view");
        intent.putExtra("event_id", event.id);
        intent.putExtra("selected_date", event.startDate);
        startActivity(intent);
    }

    private void showEventCardMenu(CalendarEvent event) {
        String[] options = {"\u270F\uFE0F Edit", "\uD83D\uDCCB Duplicate", "\uD83D\uDDD1\uFE0F Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    Intent intent = new Intent(this, CalendarEventDetailActivity.class);
                    intent.putExtra("mode", "edit");
                    intent.putExtra("event_id", event.id);
                    startActivity(intent);
                    break;
                case 1:
                    CalendarEvent copy = repository.duplicateEvent(event.id);
                    if (copy != null) {
                        Toast.makeText(this, "Event duplicated", Toast.LENGTH_SHORT).show();
                        loadAndRenderCurrentView();
                    }
                    break;
                case 2:
                    confirmDelete(event);
                    break;
            }
        });
        builder.show();
    }

    private void confirmDelete(CalendarEvent event) {
        if (event.isRecurring()) {
            String[] options = {"This event only", "This and future events", "All events in series"};
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete recurring event")
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0: repository.deleteSingleOccurrence(event.id, event.startDate); break;
                            case 1: repository.deleteFutureOccurrences(event.id, event.startDate); break;
                            case 2: repository.deleteAllOccurrences(event.id); break;
                        }
                        CalendarNotificationHelper.cancelEventReminders(this, event);
                        syncEventDeleteToServer(event.id);
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        loadAndRenderCurrentView();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete Event")
                    .setMessage("Delete '" + event.title + "'?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repository.deleteEvent(event.id);
                        CalendarNotificationHelper.cancelEventReminders(this, event);
                        syncEventDeleteToServer(event.id);
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        loadAndRenderCurrentView();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showDatePicker() {
        Calendar cal = parseDateStr(selectedDate);
        if (cal == null) cal = Calendar.getInstance();
        android.app.DatePickerDialog picker = new android.app.DatePickerDialog(
                this, R.style.DarkDatePickerDialog,
                (view, year, month, dayOfMonth) -> {
                    currentYear = year;
                    currentMonth = month + 1;
                    selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    loadAndRenderCurrentView();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    // =====================================================================
    // SERVER SYNC (Encrypted via ConnectionManager)
    // =====================================================================

    private void requestCalendarSync() {
        if (connectionManager != null) {
            connectionManager.sendDataCommand(this, "CAL_SYNC");
        }
    }

    public void syncEventToServer(CalendarEvent event) {
        if (connectionManager != null) {
            try {
                String json = event.toLegacyJson().toString();
                connectionManager.sendDataCommand(this, "CAL_ADD:" + json);
            } catch (Exception e) {
                Log.e(TAG, "Sync add error", e);
            }
        }
    }

    public void syncEventUpdateToServer(CalendarEvent event) {
        if (connectionManager != null) {
            try {
                String json = event.toLegacyJson().toString();
                connectionManager.sendDataCommand(this, "CAL_UPDATE:" + json);
            } catch (Exception e) {
                Log.e(TAG, "Sync update error", e);
            }
        }
    }

    public void syncEventDeleteToServer(String eventId) {
        if (connectionManager != null) {
            connectionManager.sendDataCommand(this, "CAL_DELETE:" + eventId);
        }
    }

    // --- Sync Callbacks (from ReverseCommandListener) ---

    public void onCalendarSyncReceived(String calendarJson) {
        Log.i(TAG, "Calendar sync received, length=" + calendarJson.length());
        try {
            JSONArray serverEvents = new JSONArray(calendarJson);
            repository.replaceAllFromServerSync(serverEvents);
            runOnUiThread(() -> {
                loadAndRenderCurrentView();
                Toast.makeText(this, "Calendar synced \u2713", Toast.LENGTH_SHORT).show();
            });
        } catch (JSONException e) {
            Log.e(TAG, "Calendar sync parse error", e);
        }
    }

    public void onCalendarEventReceived(String action, String eventId) {
        Log.i(TAG, "Calendar event notification: " + action + " id=" + eventId);
        requestCalendarSync();
    }

    // =====================================================================
    // UTILITY METHODS
    // =====================================================================

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private String getMonthName(int month) {
        String[] months = {"", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return month >= 1 && month <= 12 ? months[month] : "";
    }

    private String formatDate(Calendar cal) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private Calendar parseDateStr(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            String[] parts = dateStr.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatHour(int hour) {
        return String.format(Locale.US, "%d %s",
                hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour),
                hour < 12 ? "AM" : "PM");
    }

    private String[] getDayNamesForSettings() {
        switch (settings.firstDayOfWeek) {
            case CalendarSettings.FIRST_DAY_SUNDAY:
                return new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            case CalendarSettings.FIRST_DAY_SATURDAY:
                return new String[]{"Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"};
            default:
                return new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        }
    }

    private int getStartOffset(int firstDayOfWeek) {
        switch (settings.firstDayOfWeek) {
            case CalendarSettings.FIRST_DAY_SUNDAY:
                return firstDayOfWeek - Calendar.SUNDAY;
            case CalendarSettings.FIRST_DAY_SATURDAY:
                return (firstDayOfWeek - Calendar.SATURDAY + 7) % 7;
            default:
                return (firstDayOfWeek + 5) % 7;
        }
    }

    private int parseTimeToMinutes(String time) {
        if (time == null || time.isEmpty()) return 0;
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private int countOverlaps(List<CalendarEvent> events, CalendarEvent target) {
        int count = 0;
        int tStart = parseTimeToMinutes(target.startTime);
        int tEnd = target.hasEndTime() ? parseTimeToMinutes(target.endTime) : tStart + 60;
        for (CalendarEvent e : events) {
            if (e.isAllDay || !e.hasStartTime()) continue;
            int eStart = parseTimeToMinutes(e.startTime);
            int eEnd = e.hasEndTime() ? parseTimeToMinutes(e.endTime) : eStart + 60;
            if (eStart < tEnd && eEnd > tStart) count++;
        }
        return count;
    }

    private int getOverlapIndex(List<CalendarEvent> eventsUpTo, CalendarEvent target) {
        int index = 0;
        int tStart = parseTimeToMinutes(target.startTime);
        int tEnd = target.hasEndTime() ? parseTimeToMinutes(target.endTime) : tStart + 60;
        for (CalendarEvent e : eventsUpTo) {
            if (e == target) break;
            if (e.isAllDay || !e.hasStartTime()) continue;
            int eStart = parseTimeToMinutes(e.startTime);
            int eEnd = e.hasEndTime() ? parseTimeToMinutes(e.endTime) : eStart + 60;
            if (eStart < tEnd && eEnd > tStart) index++;
        }
        return index;
    }

    private void addCurrentTimeLine(FrameLayout frame, Calendar weekStart, int startHour) {
        Calendar now = Calendar.getInstance();
        String nowDateStr = formatDate(now);
        for (int d = 0; d < 7; d++) {
            Calendar dayCal = (Calendar) weekStart.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, d);
            if (formatDate(dayCal).equals(nowDateStr)) {
                int minutesSinceStart = (now.get(Calendar.HOUR_OF_DAY) - startHour) * 60 + now.get(Calendar.MINUTE);
                if (minutesSinceStart >= 0) {
                    int topPx = dpToPx(minutesSinceStart);
                    View line = new View(this);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(2));
                    lp.topMargin = topPx;
                    line.setLayoutParams(lp);
                    line.setBackgroundColor(COLOR_CURRENT_TIME);
                    frame.addView(line);

                    View dot = new View(this);
                    FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(dpToPx(8), dpToPx(8));
                    dp.topMargin = topPx - dpToPx(3);
                    dp.leftMargin = dpToPx(46);
                    dot.setLayoutParams(dp);
                    GradientDrawable dotBg = new GradientDrawable();
                    dotBg.setShape(GradientDrawable.OVAL);
                    dotBg.setColor(COLOR_CURRENT_TIME);
                    dot.setBackground(dotBg);
                    frame.addView(dot);
                }
                break;
            }
        }
    }

    private void addCurrentTimeLineForDay(FrameLayout frame, int startHour) {
        Calendar now = Calendar.getInstance();
        if (formatDate(now).equals(selectedDate)) {
            int minutesSinceStart = (now.get(Calendar.HOUR_OF_DAY) - startHour) * 60 + now.get(Calendar.MINUTE);
            if (minutesSinceStart >= 0) {
                int topPx = dpToPx(minutesSinceStart);
                View line = new View(this);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(2));
                lp.topMargin = topPx;
                line.setLayoutParams(lp);
                line.setBackgroundColor(COLOR_CURRENT_TIME);
                frame.addView(line);

                View dot = new View(this);
                FrameLayout.LayoutParams dp2 = new FrameLayout.LayoutParams(dpToPx(8), dpToPx(8));
                dp2.topMargin = topPx - dpToPx(3);
                dp2.leftMargin = dpToPx(46);
                dot.setLayoutParams(dp2);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(COLOR_CURRENT_TIME);
                dot.setBackground(dotBg);
                frame.addView(dot);
            }
        }
    }

    private void addSpacer(LinearLayout parent, int widthDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(widthDp), dpToPx(1)));
        parent.addView(spacer);
    }

    private void addVerticalSpacer(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(heightDp)));
        parent.addView(spacer);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        divider.setBackgroundColor(COLOR_DIVIDER);
        parent.addView(divider);
    }
}
