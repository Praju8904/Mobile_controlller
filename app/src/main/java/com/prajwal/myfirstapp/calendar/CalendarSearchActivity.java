package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CalendarSearchActivity — Powerful search with grouped results, filter chips,
 * recent searches, and enhanced empty states.
 */
public class CalendarSearchActivity extends AppCompatActivity {

    //  Theme 
    private static final int BG_PRIMARY    = 0xFF0A0E21;
    private static final int BG_SURFACE    = 0xFF111827;
    private static final int BG_ELEVATED   = 0xFF1E293B;
    private static final int BG_INPUT      = 0xFF1A2332;
    private static final int TEXT_PRIMARY   = 0xFFF1F5F9;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_MUTED     = 0xFF64748B;
    private static final int ACCENT_BLUE   = 0xFF3B82F6;
    private static final int ACCENT_AMBER  = 0xFFF59E0B;
    private static final int DANGER_RED    = 0xFFEF4444;
    private static final int DIVIDER_CLR   = 0xFF1E293B;

    //  Filter Types 
    private static final int FILTER_ALL        = 0;
    private static final int FILTER_THIS_MONTH = 1;
    private static final int FILTER_THIS_YEAR  = 2;
    private static final int FILTER_CATEGORY   = 3;
    private static final int FILTER_EVENT_TYPE = 4;

    //  State 
    private CalendarRepository repository;
    private EditText searchInput;
    private LinearLayout resultsContainer;
    private LinearLayout filterChipContainer;
    private LinearLayout recentSearchContainer;
    private LinearLayout recentSearchList;
    private int activeFilter = FILTER_ALL;
    private String filterCategoryId = null;
    private String filterEventType = null;

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

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_PRIMARY);

        //  Header 
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(BG_SURFACE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(8), dp(8), dp(8));

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        backBtn.setColorFilter(TEXT_PRIMARY);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        backBtn.setOnClickListener(v -> onBackPressed());
        header.addView(backBtn);

        // Search field
        searchInput = new EditText(this);
        searchInput.setHint("\uD83D\uDD0D Search events...");
        searchInput.setHintTextColor(TEXT_MUTED);
        searchInput.setTextColor(TEXT_PRIMARY);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setCornerRadius(dp(12)); sBg.setColor(BG_ELEVATED);
        searchInput.setBackground(sBg);
        searchInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { performSearch(s.toString().trim()); }
        });
        header.addView(searchInput);
        root.addView(header);

        //  Filter Chips 
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        filterScroll.setBackgroundColor(BG_SURFACE);
        filterScroll.setPadding(dp(12), dp(4), dp(12), dp(10));

        filterChipContainer = new LinearLayout(this);
        filterChipContainer.setOrientation(LinearLayout.HORIZONTAL);
        filterChipContainer.setGravity(Gravity.CENTER_VERTICAL);
        refreshFilterChips();
        filterScroll.addView(filterChipContainer);
        root.addView(filterScroll);

        //  Scrollable Body 
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(32));

        // Recent searches section
        recentSearchContainer = new LinearLayout(this);
        recentSearchContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(recentSearchContainer);

        // Results container
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(resultsContainer);

        scroll.addView(body);
        root.addView(scroll);
        setContentView(root);

        // Auto-focus search
        searchInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        // Show recent searches initially
        showRecentSearches();
    }

    // 
    //  FILTER CHIPS
    // 

    private void refreshFilterChips() {
        filterChipContainer.removeAllViews();
        String[] labels = { "All", "This Month", "This Year", "By Category", "By Event Type" };
        int[] filters = { FILTER_ALL, FILTER_THIS_MONTH, FILTER_THIS_YEAR, FILTER_CATEGORY, FILTER_EVENT_TYPE };

        for (int i = 0; i < labels.length; i++) {
            final int f = filters[i];
            String displayLabel = labels[i];
            if (f == FILTER_CATEGORY && filterCategoryId != null) {
                EventCategory cat = repository.getCategoryById(filterCategoryId);
                if (cat != null) displayLabel = cat.name;
            }
            if (f == FILTER_EVENT_TYPE && filterEventType != null) {
                displayLabel = CalendarEvent.getEventTypeLabel(filterEventType);
                if (displayLabel == null) displayLabel = "Type";
            }

            boolean sel = activeFilter == f;
            TextView chip = new TextView(this);
            chip.setText(displayLabel);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            if (sel) {
                bg.setColor(adjustAlpha(ACCENT_BLUE, 0.2f));
                chip.setTextColor(ACCENT_BLUE);
                bg.setStroke(dp(1), ACCENT_BLUE);
            } else {
                bg.setColor(BG_ELEVATED);
                chip.setTextColor(TEXT_SECONDARY);
            }
            chip.setBackground(bg);
            chip.setOnClickListener(v -> {
                if (f == FILTER_CATEGORY) { showCategoryFilterPicker(); return; }
                if (f == FILTER_EVENT_TYPE) { showEventTypeFilterPicker(); return; }
                activeFilter = f;
                filterCategoryId = null;
                filterEventType = null;
                refreshFilterChips();
                performSearch(searchInput.getText().toString().trim());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            filterChipContainer.addView(chip, lp);
        }
    }

    private void showCategoryFilterPicker() {
        List<EventCategory> cats = repository.getAllCategories();
        String[] names = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) names[i] = cats.get(i).name;

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Filter by Category")
                .setItems(names, (dialog, which) -> {
                    activeFilter = FILTER_CATEGORY;
                    filterCategoryId = cats.get(which).id;
                    filterEventType = null;
                    refreshFilterChips();
                    performSearch(searchInput.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEventTypeFilterPicker() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Filter by Event Type")
                .setItems(CalendarEvent.EVENT_TYPE_LABELS, (dialog, which) -> {
                    activeFilter = FILTER_EVENT_TYPE;
                    filterEventType = CalendarEvent.EVENT_TYPES[which];
                    filterCategoryId = null;
                    refreshFilterChips();
                    performSearch(searchInput.getText().toString().trim());
                })
                .setNegativeButton("Cancel", null).show();
    }

    // 
    //  RECENT SEARCHES
    // 

    private void showRecentSearches() {
        recentSearchContainer.removeAllViews();
        List<String> recent = repository.getRecentSearches();
        if (recent.isEmpty()) return;

        // Header with clear button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(4), dp(8), dp(4), dp(8));

        TextView title = new TextView(this);
        title.setText("Recent Searches");
        title.setTextColor(TEXT_SECONDARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        headerRow.addView(title);

        TextView clearBtn = new TextView(this);
        clearBtn.setText("Clear All");
        clearBtn.setTextColor(ACCENT_BLUE);
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clearBtn.setOnClickListener(v -> {
            repository.clearRecentSearches();
            showRecentSearches();
        });
        headerRow.addView(clearBtn);
        recentSearchContainer.addView(headerRow);

        for (String query : recent) {
            TextView item = new TextView(this);
            item.setText("\uD83D\uDD50  " + query);
            item.setTextColor(TEXT_PRIMARY);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            item.setPadding(dp(12), dp(12), dp(12), dp(12));
            item.setOnClickListener(v -> {
                searchInput.setText(query);
                searchInput.setSelection(query.length());
            });
            recentSearchContainer.addView(item);
        }
    }

    // 
    //  SEARCH EXECUTION
    // 

    private void performSearch(String query) {
        resultsContainer.removeAllViews();

        if (query.isEmpty()) {
            recentSearchContainer.setVisibility(View.VISIBLE);
            showRecentSearches();
            return;
        }
        recentSearchContainer.setVisibility(View.GONE);

        // Get all matching events
        List<CalendarEvent> results = repository.searchEvents(query);

        // Apply filter
        results = applyFilter(results);

        if (results.isEmpty()) {
            showEmptyState(query);
            return;
        }

        // Save to recent searches
        repository.addRecentSearch(query);

        // Separate into upcoming and past
        String today = todayStr();
        List<CalendarEvent> upcoming = new ArrayList<>();
        List<CalendarEvent> past = new ArrayList<>();

        for (CalendarEvent ev : results) {
            if (ev.startDate != null && ev.startDate.compareTo(today) >= 0)
                upcoming.add(ev);
            else
                past.add(ev);
        }

        // Show count
        TextView countLabel = new TextView(this);
        countLabel.setText(results.size() + " result" + (results.size() != 1 ? "s" : "") + " found");
        countLabel.setTextColor(TEXT_MUTED);
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countLabel.setPadding(dp(4), dp(4), 0, dp(12));
        resultsContainer.addView(countLabel);

        // Upcoming section
        if (!upcoming.isEmpty()) {
            resultsContainer.addView(groupHeader("Upcoming (" + upcoming.size() + ")", ACCENT_BLUE));
            for (CalendarEvent ev : upcoming) resultsContainer.addView(buildResultCard(ev));
        }

        // Past section
        if (!past.isEmpty()) {
            if (!upcoming.isEmpty()) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(16)));
                resultsContainer.addView(spacer);
            }
            resultsContainer.addView(groupHeader("Past (" + past.size() + ")", TEXT_MUTED));
            for (CalendarEvent ev : past) resultsContainer.addView(buildResultCard(ev));
        }
    }

    private List<CalendarEvent> applyFilter(List<CalendarEvent> events) {
        if (activeFilter == FILTER_ALL) return events;

        List<CalendarEvent> filtered = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        String thisMonth = String.format(Locale.US, "%04d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1);
        String thisYear = String.format(Locale.US, "%04d", now.get(Calendar.YEAR));

        for (CalendarEvent ev : events) {
            switch (activeFilter) {
                case FILTER_THIS_MONTH:
                    if (ev.startDate != null && ev.startDate.startsWith(thisMonth)) filtered.add(ev);
                    break;
                case FILTER_THIS_YEAR:
                    if (ev.startDate != null && ev.startDate.startsWith(thisYear)) filtered.add(ev);
                    break;
                case FILTER_CATEGORY:
                    if (filterCategoryId != null && filterCategoryId.equals(ev.categoryId)) filtered.add(ev);
                    break;
                case FILTER_EVENT_TYPE:
                    if (filterEventType != null && filterEventType.equals(ev.eventType)) filtered.add(ev);
                    break;
            }
        }
        return filtered;
    }

    // 
    //  RESULT CARDS
    // 

    private View buildResultCard(CalendarEvent ev) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(6);
        card.setLayoutParams(cardLp);

        // Color bar
        View colorBar = new View(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(2));
        barBg.setColor(safeColor(ev.colorHex));
        colorBar.setBackground(barBg);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), -1);
        barLp.setMarginEnd(dp(12));
        barLp.topMargin = dp(4); barLp.bottomMargin = dp(4);
        card.addView(colorBar, barLp);

        // Content
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        content.setPadding(0, dp(8), 0, dp(8));

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        if (ev.isStarred) {
            TextView star = new TextView(this);
            star.setText("\u2B50 ");
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            titleRow.addView(star);
        }

        TextView titleTv = new TextView(this);
        titleTv.setText(ev.title);
        titleTv.setTextColor(TEXT_PRIMARY);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        titleTv.setSingleLine(true);
        titleRow.addView(titleTv);

        if (ev.isRecurring()) {
            TextView recIcon = new TextView(this);
            recIcon.setText(" \uD83D\uDD04");
            recIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            titleRow.addView(recIcon);
        }
        content.addView(titleRow);

        // Date & time
        StringBuilder dtSb = new StringBuilder();
        if (ev.startDate != null) {
            String dow = getDow(ev.startDate);
            if (dow != null) dtSb.append(dow).append(", ");
            dtSb.append(fmtDateDisplay(ev.startDate));
        }
        if (!ev.isAllDay && ev.startTime != null) {
            dtSb.append("  \u2022  ").append(fmtTimeDisplay(ev.startTime));
            if (ev.endTime != null && !ev.endTime.isEmpty())
                dtSb.append(" \u2013 ").append(fmtTimeDisplay(ev.endTime));
        }
        if (ev.isAllDay) dtSb.append("  \u2022  All Day");

        if (dtSb.length() > 0) {
            TextView dtTv = new TextView(this);
            dtTv.setText(dtSb.toString());
            dtTv.setTextColor(TEXT_SECONDARY);
            dtTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            dtTv.setPadding(0, dp(2), 0, 0);
            content.addView(dtTv);
        }

        // Location
        if (ev.location != null && !ev.location.isEmpty()) {
            TextView locTv = new TextView(this);
            locTv.setText("\uD83D\uDCCD " + ev.location);
            locTv.setTextColor(TEXT_MUTED);
            locTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            locTv.setPadding(0, dp(2), 0, 0);
            locTv.setSingleLine(true);
            content.addView(locTv);
        }

        // Category / event type badge row
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setPadding(0, dp(4), 0, 0);
        EventCategory cat = repository.getCategoryById(ev.categoryId);
        if (cat != null) {
            badgeRow.addView(makeBadge(cat.name, cat.getColor()));
            View sp = new View(this);
            sp.setLayoutParams(new LinearLayout.LayoutParams(dp(6), dp(1)));
            badgeRow.addView(sp);
        }
        String typeLbl = CalendarEvent.getEventTypeLabel(ev.eventType);
        if (typeLbl != null) {
            badgeRow.addView(makeBadge(CalendarEvent.getEventTypeIcon(ev.eventType) + " " + typeLbl, TEXT_MUTED));
        }
        if (badgeRow.getChildCount() > 0) content.addView(badgeRow);

        card.addView(content);

        // Card background
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(12));
        cardBg.setColor(BG_SURFACE);
        card.setBackground(cardBg);
        card.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, CalendarEventDetailActivity.class);
            intent.putExtra("mode", "view");
            intent.putExtra("event_id", ev.id);
            startActivity(intent);
            overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
        });
        return card;
    }

    // 
    //  UI HELPERS
    // 

    private void showEmptyState(String query) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(32), dp(60), dp(32), dp(32));

        TextView icon = new TextView(this);
        icon.setText("\uD83D\uDD0D");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView msg = new TextView(this);
        msg.setText("No events found for \"" + query + "\"");
        msg.setTextColor(TEXT_SECONDARY);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(16), 0, dp(8));
        empty.addView(msg);

        TextView hint = new TextView(this);
        hint.setText("Try different keywords or adjust the filter");
        hint.setTextColor(TEXT_MUTED);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setGravity(Gravity.CENTER);
        empty.addView(hint);

        resultsContainer.addView(empty);
    }

    private TextView groupHeader(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(8), 0, dp(8));
        return tv;
    }

    private TextView makeBadge(String text, int color) {
        TextView b = new TextView(this); b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); b.setTypeface(null, Typeface.BOLD);
        b.setPadding(dp(8), dp(3), dp(8), dp(3)); b.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10)); bg.setColor(adjustAlpha(color, 0.18f)); b.setBackground(bg);
        return b;
    }

    private String fmtDateDisplay(String d) {
        if (d == null) return "";
        try { return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)); }
        catch (ParseException e) { return d; }
    }

    private String fmtTimeDisplay(String t) {
        if (t == null || t.isEmpty()) return "";
        try { return new SimpleDateFormat("h:mm a", Locale.US).format(new SimpleDateFormat("HH:mm", Locale.US).parse(t)); }
        catch (ParseException e) { return t; }
    }

    private String getDow(String d) {
        if (d == null) return null;
        try { return new SimpleDateFormat("EEE", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)); }
        catch (ParseException e) { return null; }
    }

    private String todayStr() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private int safeColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return ACCENT_BLUE; }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
