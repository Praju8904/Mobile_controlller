package com.prajwal.myfirstapp.calendar;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.Note;
import com.prajwal.myfirstapp.notes.NoteEditorActivity;
import com.prajwal.myfirstapp.notes.NoteRepository;
import com.prajwal.myfirstapp.tasks.Task;
import com.prajwal.myfirstapp.tasks.TaskRepository;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CalendarEventDetailActivity â€” Premium event creation, editing, and viewing.
 * Prompt 2: Full form UI, recurring event management, multiple reminders,
 * attachments, cross-feature Task integration.
 */
public class CalendarEventDetailActivity extends AppCompatActivity {

    //  Request Codes 
    private static final int REQUEST_ATTACH_FILE = 1001;

    //  Theme Colors 
    private static final int BG_PRIMARY    = 0xFF0A0E21;
    private static final int BG_SURFACE    = 0xFF111827;
    private static final int BG_ELEVATED   = 0xFF1E293B;
    private static final int BG_INPUT      = 0xFF1A2332;
    private static final int TEXT_PRIMARY   = 0xFFF1F5F9;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int TEXT_MUTED     = 0xFF64748B;
    private static final int ACCENT_BLUE   = 0xFF3B82F6;
    private static final int ACCENT_GREEN  = 0xFF10B981;
    private static final int ACCENT_AMBER  = 0xFFF59E0B;
    private static final int DANGER_RED    = 0xFFEF4444;
    private static final int DIVIDER_CLR   = 0xFF1E293B;

    //  All 12 event colors 
    private static final String[] ALL_COLORS = {
        CalendarEvent.COLOR_BLUE,   CalendarEvent.COLOR_INDIGO, CalendarEvent.COLOR_PURPLE,
        CalendarEvent.COLOR_PINK,   CalendarEvent.COLOR_RED,    CalendarEvent.COLOR_ORANGE,
        CalendarEvent.COLOR_AMBER,  CalendarEvent.COLOR_YELLOW, CalendarEvent.COLOR_GREEN,
        CalendarEvent.COLOR_TEAL,   CalendarEvent.COLOR_CYAN,   CalendarEvent.COLOR_GREY
    };

    //  Repository & State 
    private CalendarRepository repository;
    private CalendarEvent event;
    private boolean isNewEvent  = true;
    private boolean isViewMode  = false;
    private String preSelectedDate;
    private String occurrenceDate;

    //  Form State 
    private String  selStartDate, selStartTime, selEndDate, selEndTime;
    private boolean selAllDay         = false;
    private String  selCategoryId     = EventCategory.CAT_PERSONAL;
    private String  selEventType      = CalendarEvent.TYPE_PERSONAL;
    private String  selColor          = CalendarEvent.COLOR_BLUE;
    private String  selRecurrence     = CalendarEvent.RECURRENCE_NONE;
    private String  selRecurrenceRule;
    private String  selRecurrenceEndDate;
    private int     selRecurrenceCount = 0;
    private List<Integer> selReminders   = new ArrayList<>();
    private List<String>  selAttachments = new ArrayList<>();
    private boolean selStarred         = false;
    private String  selDescription     = "";
    private String  selNotes           = "";
    private String  selLocation        = "";

    //  Form UI References 
    private EditText titleInput, locationInput, descriptionInput, notesInput;
    private TextView startDateChip, startTimeChip, endDateChip, endTimeChip, durationLabel;
    private Switch   allDaySwitch;
    private LinearLayout categoryChipCont, eventTypeCont;
    private LinearLayout colorRow1, colorRow2;
    private LinearLayout reminderChipCont, attachmentChipCont;
    private TextView recurrenceBtn, recurrenceSummaryView;
    private LinearLayout colorPreviewCard;
    private ImageView starToggle;
    private ScrollView mainScroll;
    private LinearLayout rootContent;

    // 
    //  LIFECYCLE
    // 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_PRIMARY);
        getWindow().setNavigationBarColor(BG_PRIMARY);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        repository = new CalendarRepository(this);

        String mode    = getIntent().getStringExtra("mode");
        String eventId = getIntent().getStringExtra("event_id");
        preSelectedDate = getIntent().getStringExtra("selected_date");
        occurrenceDate  = getIntent().getStringExtra("occurrence_date");

        if ("view".equals(mode) && eventId != null) {
            event = repository.getEventById(eventId);
            if (event != null) { isNewEvent = false; isViewMode = true; loadState(); }
            else { finish(); return; }
        } else if ("edit".equals(mode) && eventId != null) {
            event = repository.getEventById(eventId);
            if (event != null) { isNewEvent = false; isViewMode = false; loadState(); }
            else { finish(); return; }
        } else {
            isNewEvent = true; isViewMode = false; initDefaults();
            // Handle "Add to Calendar" from Task Manager
            if (getIntent().getBooleanExtra("create_from_task", false)) {
                prefillFromTask();
            }
        }
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
        if (!isViewMode && categoryChipCont != null) refreshCategoryChips();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ATTACH_FILE && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                    selAttachments.add(uri.toString());
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                selAttachments.add(uri.toString());
            }
            if (attachmentChipCont != null) refreshAttachmentChips();
        }
    }

    // 
    //  INITIALIZATION
    // 

    private void initDefaults() {
        CalendarSettings settings = repository.getSettings();
        Calendar now = Calendar.getInstance();
        now.add(Calendar.HOUR_OF_DAY, 1);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);

        selStartDate = preSelectedDate != null && !preSelectedDate.isEmpty()
                ? preSelectedDate : fmtCalDate(now);
        selStartTime = String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));

        Calendar end = (Calendar) now.clone();
        end.add(Calendar.MINUTE, settings.defaultEventDurationMinutes);
        selEndDate = selStartDate;
        selEndTime = String.format(Locale.US, "%02d:%02d",
                end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE));

        if (settings.defaultReminderMinutes > 0)
            selReminders.add(settings.defaultReminderMinutes);
    }

    private void loadState() {
        selStartDate       = event.startDate;
        selStartTime       = event.startTime;
        selEndDate         = event.endDate;
        selEndTime         = event.endTime;
        selAllDay          = event.isAllDay;
        selCategoryId      = event.categoryId    != null ? event.categoryId   : EventCategory.CAT_PERSONAL;
        selEventType       = event.eventType     != null ? event.eventType    : CalendarEvent.TYPE_PERSONAL;
        selColor           = event.colorHex      != null ? event.colorHex     : CalendarEvent.COLOR_BLUE;
        selRecurrence      = event.recurrence    != null ? event.recurrence   : CalendarEvent.RECURRENCE_NONE;
        selRecurrenceRule  = event.recurrenceRule;
        selRecurrenceEndDate = event.recurrenceEndDate;
        selRecurrenceCount   = event.recurrenceCount;
        selReminders       = event.reminderOffsets  != null ? new ArrayList<>(event.reminderOffsets) : new ArrayList<>();
        selAttachments     = event.attachmentPaths  != null ? new ArrayList<>(event.attachmentPaths) : new ArrayList<>();
        selStarred         = event.isStarred;
        selDescription     = event.description != null ? event.description : "";
        selNotes           = event.notes       != null ? event.notes       : "";
        selLocation        = event.location    != null ? event.location    : "";
    }

    private void prefillFromTask() {
        // Pre-populate from Task Manager "Add to Calendar" action
        Intent intent = getIntent();
        taskPrefillTitle = intent.getStringExtra("task_title");
        taskPrefillDescription = intent.getStringExtra("task_description");
        String taskDueDate = intent.getStringExtra("task_due_date");
        String taskPriority = intent.getStringExtra("task_priority");

        // If task has a due date, use it
        if (taskDueDate != null && !taskDueDate.isEmpty()) {
            try {
                // Task due date format is typically "yyyy-MM-dd"
                selStartDate = taskDueDate;
                selEndDate = taskDueDate;
            } catch (Exception ignored) {}
        }

        // Set description/notes from task
        if (taskPrefillDescription != null && !taskPrefillDescription.isEmpty()) {
            selDescription = taskPrefillDescription;
        }

        // Map task priority to event type or starred
        if ("high".equalsIgnoreCase(taskPriority) || "urgent".equalsIgnoreCase(taskPriority)) {
            selStarred = true;
        }

        // Set category to personal/task type
        selCategoryId = EventCategory.CAT_WORK;
        selEventType = CalendarEvent.TYPE_BUSINESS;
    }

    // Fields for task prefill (to be applied after UI is built)
    private String taskPrefillTitle;
    private String taskPrefillDescription;

    private void buildUI() {
        if (isViewMode) buildViewMode(); else buildFormMode();
    }

    // 
    //  VIEW MODE
    // 

    private void buildViewMode() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_PRIMARY);

        //  Header 
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(BG_SURFACE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(10), dp(4), dp(10));

        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        backBtn.setColorFilter(TEXT_PRIMARY);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        backBtn.setOnClickListener(v -> onBackPressed());
        header.addView(backBtn);

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Event Details");
        headerTitle.setTextColor(TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        headerTitle.setGravity(Gravity.CENTER);
        header.addView(headerTitle);

        ImageButton menuBtn = new ImageButton(this);
        menuBtn.setImageResource(android.R.drawable.ic_menu_more);
        menuBtn.setColorFilter(TEXT_SECONDARY);
        menuBtn.setBackgroundColor(Color.TRANSPARENT);
        menuBtn.setPadding(dp(8), dp(12), dp(8), dp(12));
        menuBtn.setOnClickListener(this::showViewMenu);
        header.addView(menuBtn);

        TextView editBtn = new TextView(this);
        editBtn.setText("Edit");
        editBtn.setTextColor(ACCENT_BLUE);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editBtn.setTypeface(null, Typeface.BOLD);
        editBtn.setPadding(dp(16), dp(12), dp(12), dp(12));
        editBtn.setOnClickListener(v -> { isViewMode = false; buildUI(); });
        header.addView(editBtn);
        root.addView(header);

        //  Color Accent Bar 
        View colorBar = new View(this);
        colorBar.setBackgroundColor(safeColor(selColor));
        colorBar.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(4)));
        root.addView(colorBar);

        //  Scrollable Content 
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(32));

        //  Main Card 
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(16));
        cardBg.setColor(BG_SURFACE);
        card.setBackground(cardBg);

        // Title row with star
        LinearLayout titleRow = hRow(Gravity.CENTER_VERTICAL);
        if (event.isStarred) {
            TextView star = new TextView(this);
            star.setText("\u2B50");
            star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            star.setPadding(0, 0, dp(8), 0);
            titleRow.addView(star);
        }
        TextView titleView = new TextView(this);
        titleView.setText(event.title != null ? event.title : "Untitled Event");
        titleView.setTextColor(TEXT_PRIMARY);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(titleView);

        if (event.isCompleted) titleRow.addView(makeBadge("\u2713 Completed", ACCENT_GREEN));
        else if (event.isCancelled) titleRow.addView(makeBadge("Cancelled", DANGER_RED));
        card.addView(titleRow);

        // Badge row
        LinearLayout badges = hRow(Gravity.START);
        badges.setPadding(0, dp(10), 0, dp(4));
        EventCategory cat = repository.getCategoryById(selCategoryId);
        if (cat != null) { badges.addView(makeBadge(cat.name, cat.getColor())); spacer(badges); }
        String typeLabel = CalendarEvent.getEventTypeLabel(selEventType);
        if (typeLabel != null) {
            badges.addView(makeBadge(CalendarEvent.getEventTypeIcon(selEventType) + " " + typeLabel, TEXT_MUTED));
        }
        if (event.isRecurring()) { spacer(badges); badges.addView(makeBadge("\uD83D\uDD04 " + event.getRecurrenceLabel(), TEXT_MUTED)); }
        card.addView(badges);
        card.addView(divider());

        // Date & Time
        LinearLayout dateSec = new LinearLayout(this);
        dateSec.setOrientation(LinearLayout.VERTICAL);
        dateSec.setPadding(0, dp(12), 0, dp(12));

        String dateDisplay = "";
        String dayOfWeek = getDow(event.startDate);
        if (dayOfWeek != null) dateDisplay = dayOfWeek + ", ";
        dateDisplay += fmtDateDisplay(event.startDate);
        if (event.endDate != null && !event.endDate.equals(event.startDate))
            dateDisplay += " \u2014 " + fmtDateDisplay(event.endDate);

        LinearLayout dateRow = hRow(Gravity.CENTER_VERTICAL);
        addIcon(dateRow, "\uD83D\uDCC5");
        TextView dateText = bodyText(dateDisplay);
        dateRow.addView(dateText);
        if (event.isAllDay) {
            TextView adBadge = makeBadge("All Day", ACCENT_AMBER);
            LinearLayout.LayoutParams adLp = new LinearLayout.LayoutParams(-2, -2);
            adLp.setMarginStart(dp(8));
            dateRow.addView(adBadge, adLp);
        }
        dateSec.addView(dateRow);

        if (!event.isAllDay && event.startTime != null) {
            LinearLayout timeRow = hRow(Gravity.CENTER_VERTICAL);
            timeRow.setPadding(0, dp(6), 0, 0);
            addIcon(timeRow, "\uD83D\uDD50");
            String timeTxt = fmtTimeDisplay(event.startTime);
            if (event.endTime != null && !event.endTime.isEmpty())
                timeTxt += " \u2014 " + fmtTimeDisplay(event.endTime);
            timeRow.addView(bodyText(timeTxt));
            String dur = event.getDurationLabel();
            if (dur != null && !dur.isEmpty()) {
                TextView durTv = new TextView(this);
                durTv.setText("(" + dur + ")");
                durTv.setTextColor(TEXT_MUTED);
                durTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                durTv.setPadding(dp(8), 0, 0, 0);
                timeRow.addView(durTv);
            }
            dateSec.addView(timeRow);
        }
        card.addView(dateSec);

        // Location
        if (event.location != null && !event.location.isEmpty()) {
            LinearLayout locRow = hRow(Gravity.CENTER_VERTICAL);
            locRow.setPadding(0, dp(2), 0, dp(12));
            addIcon(locRow, "\uD83D\uDCCD");
            locRow.addView(bodyText(event.location));
            card.addView(locRow);
        }

        // Color dot
        LinearLayout colRow = hRow(Gravity.CENTER_VERTICAL);
        colRow.setPadding(0, dp(2), 0, dp(8));
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(safeColor(selColor));
        dot.setBackground(dotBg);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(12), dp(12)));
        colRow.addView(dot);
        TextView colLbl = new TextView(this);
        colLbl.setText("Event color");
        colLbl.setTextColor(TEXT_MUTED);
        colLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        colLbl.setPadding(dp(8), 0, 0, 0);
        colRow.addView(colLbl);
        card.addView(colRow);

        // Recurrence
        if (event.isRecurring()) {
            card.addView(divider());
            LinearLayout recSec = new LinearLayout(this);
            recSec.setOrientation(LinearLayout.VERTICAL);
            recSec.setPadding(0, dp(12), 0, dp(12));
            LinearLayout recRow = hRow(Gravity.CENTER_VERTICAL);
            addIcon(recRow, "\uD83D\uDD04");
            TextView recTxt = bodyText(event.getRecurrenceSummary());
            recTxt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            recRow.addView(recTxt);
            recSec.addView(recRow);

            TextView viewAll = new TextView(this);
            viewAll.setText("View all occurrences \u2192");
            viewAll.setTextColor(ACCENT_BLUE);
            viewAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            viewAll.setPadding(dp(26), dp(8), 0, 0);
            viewAll.setOnClickListener(v -> showAllOccurrences());
            recSec.addView(viewAll);
            card.addView(recSec);
        }

        // Reminders
        if (event.reminderOffsets != null && !event.reminderOffsets.isEmpty()) {
            card.addView(divider());
            LinearLayout remSec = new LinearLayout(this);
            remSec.setOrientation(LinearLayout.VERTICAL);
            remSec.setPadding(0, dp(12), 0, dp(12));
            remSec.addView(sectionLabel("\uD83D\uDD14  Reminders"));
            for (int offset : event.reminderOffsets) {
                TextView item = new TextView(this);
                item.setText("  \u2022  " + CalendarEvent.formatReminderOffset(offset));
                item.setTextColor(TEXT_PRIMARY);
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                item.setPadding(dp(16), dp(4), 0, dp(4));
                remSec.addView(item);
            }
            card.addView(remSec);
        }

        // Description
        if (event.description != null && !event.description.isEmpty()) {
            card.addView(divider());
            LinearLayout sec = new LinearLayout(this); sec.setOrientation(LinearLayout.VERTICAL);
            sec.setPadding(0, dp(12), 0, dp(12));
            sec.addView(sectionLabel("\uD83D\uDCDD  Description"));
            TextView txt = bodyText(event.description);
            txt.setPadding(0, dp(6), 0, 0);
            txt.setLineSpacing(dp(4), 1f);
            sec.addView(txt);
            card.addView(sec);
        }

        // Notes
        if (event.notes != null && !event.notes.isEmpty()) {
            card.addView(divider());
            LinearLayout sec = new LinearLayout(this); sec.setOrientation(LinearLayout.VERTICAL);
            sec.setPadding(0, dp(12), 0, dp(12));
            sec.addView(sectionLabel("\uD83D\uDCCB  Notes"));
            TextView txt = bodyText(event.notes);
            txt.setPadding(0, dp(6), 0, 0);
            txt.setLineSpacing(dp(4), 1f);
            sec.addView(txt);
            card.addView(sec);
        }

        // Attachments
        if (event.attachmentPaths != null && !event.attachmentPaths.isEmpty()) {
            card.addView(divider());
            LinearLayout sec = new LinearLayout(this); sec.setOrientation(LinearLayout.VERTICAL);
            sec.setPadding(0, dp(12), 0, dp(12));
            sec.addView(sectionLabel("\uD83D\uDCCE  Attachments"));
            FlexWrapLayout wrap = new FlexWrapLayout(this);
            wrap.setPadding(0, dp(8), 0, 0);
            for (String p : event.attachmentPaths) wrap.addView(makeAttachViewChip(p));
            sec.addView(wrap);
            card.addView(sec);
        }

        // Timestamps
        card.addView(divider());
        LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(0, dp(12), 0, dp(4));
        if (event.createdAt > 0) {
            TextView c = new TextView(this);
            c.setText("Created: " + fmtMillis(event.createdAt));
            c.setTextColor(TEXT_MUTED); c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            meta.addView(c);
        }
        if (event.updatedAt > 0 && event.updatedAt != event.createdAt) {
            TextView u = new TextView(this);
            u.setText("Modified: " + fmtMillis(event.updatedAt));
            u.setTextColor(TEXT_MUTED); u.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            u.setPadding(0, dp(2), 0, 0);
            meta.addView(u);
        }
        card.addView(meta);
        content.addView(card);

        // Action row
        LinearLayout actions = hRow(Gravity.CENTER_HORIZONTAL);
        actions.setPadding(0, dp(20), 0, dp(8));
        actions.addView(makeActionBtn("\uD83D\uDCCB Duplicate", v -> duplicateEvent()));
        spacer(actions); spacer(actions);
        actions.addView(makeActionBtn("\uD83D\uDCE4 Share", v -> shareEvent()));
        spacer(actions); spacer(actions);
        actions.addView(makeActionBtn("\uD83D\uDCCC Create Task", v -> createTaskFromEvent()));

        // Linked Note chip
        if (event != null && event.linkedNoteId != null && !event.linkedNoteId.isEmpty()) {
            NoteRepository noteRepo = new NoteRepository(this);
            Note linkedNote = noteRepo.getNoteById(event.linkedNoteId);
            if (linkedNote != null) {
                LinearLayout noteChipRow = new LinearLayout(this);
                noteChipRow.setOrientation(LinearLayout.HORIZONTAL);
                noteChipRow.setPadding(0, dp(8), 0, dp(4));
                noteChipRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView noteChip = new TextView(this);
                noteChip.setText("📝 Linked Note: " + (linkedNote.title.isEmpty() ? "Untitled" : linkedNote.title));
                noteChip.setTextColor(0xFF60A5FA);
                noteChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                noteChip.setPadding(dp(12), dp(8), dp(12), dp(8));
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setColor(0x1A60A5FA);
                chipBg.setCornerRadius(dp(12));
                noteChip.setBackground(chipBg);
                noteChip.setOnClickListener(v2 -> {
                    Intent noteIntent = new Intent(this, NoteEditorActivity.class);
                    noteIntent.putExtra("note_id", linkedNote.id);
                    startActivity(noteIntent);
                });
                noteChipRow.addView(noteChip);
                content.addView(noteChipRow);
            }
        }

        content.addView(actions);

        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
    }

    private void showAllOccurrences() {
        if (event == null || !event.isRecurring()) return;
        List<String> dates = repository.getUpcomingOccurrencesOfEvent(event.id, 30);
        if (dates.isEmpty()) { Toast.makeText(this, "No upcoming occurrences", Toast.LENGTH_SHORT).show(); return; }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(12), dp(20), dp(12));
        for (String d : dates) {
            TextView item = new TextView(this);
            String dow = getDow(d);
            item.setText((dow != null ? dow + ", " : "") + fmtDateDisplay(d));
            item.setTextColor(TEXT_PRIMARY);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            item.setPadding(dp(4), dp(10), dp(4), dp(10));
            list.addView(item);
        }
        ScrollView sv = new ScrollView(this); sv.addView(list);
        sv.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(400)));
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Upcoming Occurrences (" + dates.size() + ")")
                .setView(sv).setPositiveButton("Close", null).show();
    }

    private void showViewMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "\u270F\uFE0F Edit");
        popup.getMenu().add(0, 2, 0, "\uD83D\uDCCB Duplicate");
        popup.getMenu().add(0, 3, 0, "\uD83D\uDCE4 Share");
        popup.getMenu().add(0, 4, 0, "\uD83D\uDCCC Create Task");
        popup.getMenu().add(0, 5, 0, "\uD83D\uDDD1\uFE0F Delete");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: isViewMode = false; buildUI(); return true;
                case 2: duplicateEvent(); return true;
                case 3: shareEvent(); return true;
                case 4: createTaskFromEvent(); return true;
                case 5: confirmDelete(); return true;
            }
            return false;
        });
        popup.show();
    }
    // 
    //  FORM MODE
    // 

    private void buildFormMode() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_PRIMARY);

        //  Header 
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(BG_SURFACE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(10), dp(4), dp(10));

        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(TEXT_PRIMARY);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        closeBtn.setOnClickListener(v -> onBackPressed());
        header.addView(closeBtn);

        TextView headerTitle = new TextView(this);
        headerTitle.setText(isNewEvent ? "New Event" : "Edit Event");
        headerTitle.setTextColor(TEXT_PRIMARY);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        headerTitle.setGravity(Gravity.CENTER);
        header.addView(headerTitle);

        // Star toggle
        starToggle = new ImageView(this);
        updateStarIcon();
        starToggle.setPadding(dp(12), dp(12), dp(4), dp(12));
        starToggle.setOnClickListener(v -> { selStarred = !selStarred; updateStarIcon(); });
        header.addView(starToggle, new LinearLayout.LayoutParams(dp(44), dp(44)));

        // Save button
        TextView saveBtn = new TextView(this);
        saveBtn.setText("Save");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setPadding(dp(20), dp(8), dp(20), dp(8));
        saveBtn.setGravity(Gravity.CENTER);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setCornerRadius(dp(20));
        saveBg.setColor(ACCENT_BLUE);
        saveBtn.setBackground(saveBg);
        saveBtn.setOnClickListener(v -> saveEvent());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-2, -2);
        saveLp.setMarginEnd(dp(8));
        header.addView(saveBtn, saveLp);
        root.addView(header);

        //  Scrollable Form 
        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        mainScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        rootContent = new LinearLayout(this);
        rootContent.setOrientation(LinearLayout.VERTICAL);
        rootContent.setPadding(dp(20), dp(16), dp(20), dp(80));

        //  TITLE FIELD 
        titleInput = new EditText(this);
        titleInput.setHint("Add title");
        titleInput.setHintTextColor(TEXT_MUTED);
        titleInput.setTextColor(TEXT_PRIMARY);
        titleInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleInput.setTypeface(null, Typeface.BOLD);
        titleInput.setBackgroundColor(Color.TRANSPARENT);
        titleInput.setPadding(0, dp(8), 0, dp(16));
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleInput.setSingleLine(true);
        if (!isNewEvent && event != null && event.title != null) {
            titleInput.setText(event.title);
        } else if (taskPrefillTitle != null && !taskPrefillTitle.isEmpty()) {
            titleInput.setText(taskPrefillTitle);
        }
        rootContent.addView(titleInput);
        rootContent.addView(formDivider());

        //  DATE & TIME SECTION 
        buildDateTimeSection(rootContent);
        rootContent.addView(formDivider());

        //  LOCATION 
        LinearLayout locRow = hRow(Gravity.CENTER_VERTICAL);
        locRow.setPadding(0, dp(8), 0, dp(8));
        addIcon(locRow, "\uD83D\uDCCD");
        locationInput = new EditText(this);
        locationInput.setHint("Add location");
        locationInput.setHintTextColor(TEXT_MUTED);
        locationInput.setTextColor(TEXT_PRIMARY);
        locationInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        locationInput.setBackgroundColor(Color.TRANSPARENT);
        locationInput.setSingleLine(true);
        locationInput.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        if (!selLocation.isEmpty()) locationInput.setText(selLocation);
        locRow.addView(locationInput);
        rootContent.addView(locRow);
        rootContent.addView(formDivider());

        //  CATEGORY CHIPS 
        rootContent.addView(secHeader("Category"));
        categoryChipCont = new LinearLayout(this);
        rootContent.addView(hScroll(categoryChipCont));
        refreshCategoryChips();
        rootContent.addView(formDivider());

        //  EVENT TYPE PILLS 
        rootContent.addView(secHeader("Event Type"));
        eventTypeCont = new LinearLayout(this);
        rootContent.addView(hScroll(eventTypeCont));
        refreshEventTypeChips();
        rootContent.addView(formDivider());

        //  COLOR PICKER 
        rootContent.addView(secHeader("Color"));
        buildColorSection(rootContent);
        rootContent.addView(formDivider());

        //  REMINDERS 
        rootContent.addView(secHeader("Reminders"));
        reminderChipCont = new LinearLayout(this);
        reminderChipCont.setOrientation(LinearLayout.VERTICAL);
        rootContent.addView(reminderChipCont);
        refreshReminderChips();
        rootContent.addView(formDivider());

        //  RECURRENCE 
        rootContent.addView(secHeader("Repeat"));
        buildRecurrenceSection(rootContent);
        rootContent.addView(formDivider());

        //  DESCRIPTION 
        rootContent.addView(secHeader("Description"));
        descriptionInput = new EditText(this);
        descriptionInput.setHint("Add description...");
        descriptionInput.setHintTextColor(TEXT_MUTED);
        descriptionInput.setTextColor(TEXT_PRIMARY);
        descriptionInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        descriptionInput.setMinLines(2);
        descriptionInput.setMaxLines(6);
        descriptionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        styleInput(descriptionInput);
        if (!selDescription.isEmpty()) descriptionInput.setText(selDescription);
        rootContent.addView(descriptionInput);
        rootContent.addView(formDivider());

        //  NOTES 
        rootContent.addView(secHeader("Notes"));
        notesInput = new EditText(this);
        notesInput.setHint("Add notes...");
        notesInput.setHintTextColor(TEXT_MUTED);
        notesInput.setTextColor(TEXT_PRIMARY);
        notesInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        notesInput.setMinLines(2);
        notesInput.setMaxLines(6);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        styleInput(notesInput);
        if (!selNotes.isEmpty()) notesInput.setText(selNotes);
        rootContent.addView(notesInput);
        rootContent.addView(formDivider());

        //  ATTACHMENTS 
        rootContent.addView(secHeader("Attachments"));
        attachmentChipCont = new LinearLayout(this);
        attachmentChipCont.setOrientation(LinearLayout.VERTICAL);
        rootContent.addView(attachmentChipCont);
        refreshAttachmentChips();

        //  DELETE BUTTON (edit only) 
        if (!isNewEvent) {
            rootContent.addView(formDivider());
            TextView delBtn = new TextView(this);
            delBtn.setText("\uD83D\uDDD1\uFE0F  Delete Event");
            delBtn.setTextColor(DANGER_RED);
            delBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            delBtn.setTypeface(null, Typeface.BOLD);
            delBtn.setGravity(Gravity.CENTER);
            delBtn.setPadding(0, dp(14), 0, dp(14));
            GradientDrawable delBg = new GradientDrawable();
            delBg.setCornerRadius(dp(12));
            delBg.setStroke(dp(1), DANGER_RED);
            delBg.setColor(Color.TRANSPARENT);
            delBtn.setBackground(delBg);
            delBtn.setOnClickListener(v -> confirmDelete());
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-1, -2);
            delLp.topMargin = dp(16);
            rootContent.addView(delBtn, delLp);
        }

        mainScroll.addView(rootContent);
        root.addView(mainScroll);
        setContentView(root);

        if (isNewEvent) {
            titleInput.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    //  Date & Time Section 

    private void buildDateTimeSection(LinearLayout parent) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(0, dp(8), 0, dp(8));

        // All Day
        LinearLayout allDayRow = hRow(Gravity.CENTER_VERTICAL);
        allDayRow.setPadding(0, 0, 0, dp(12));
        TextView allDayLbl = new TextView(this);
        allDayLbl.setText("All Day");
        allDayLbl.setTextColor(TEXT_PRIMARY);
        allDayLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        allDayLbl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        allDayRow.addView(allDayLbl);
        allDaySwitch = new Switch(this);
        allDaySwitch.setChecked(selAllDay);
        allDaySwitch.setOnCheckedChangeListener((b, checked) -> { selAllDay = checked; updateTimeVis(); });
        allDayRow.addView(allDaySwitch);
        sec.addView(allDayRow);

        // Start row
        LinearLayout startRow = hRow(Gravity.CENTER_VERTICAL);
        startRow.setPadding(0, dp(4), 0, dp(4));
        startRow.addView(rowLabel("Start"));
        startDateChip = dtChip();
        startDateChip.setOnClickListener(v -> pickDate(true));
        startRow.addView(startDateChip);
        startTimeChip = dtChip();
        startTimeChip.setOnClickListener(v -> pickTime(true));
        startRow.addView(startTimeChip);
        sec.addView(startRow);

        // End row
        LinearLayout endRow = hRow(Gravity.CENTER_VERTICAL);
        endRow.setPadding(0, dp(4), 0, dp(4));
        endRow.addView(rowLabel("End"));
        endDateChip = dtChip();
        endDateChip.setOnClickListener(v -> pickDate(false));
        endRow.addView(endDateChip);
        endTimeChip = dtChip();
        endTimeChip.setOnClickListener(v -> pickTime(false));
        endRow.addView(endTimeChip);
        sec.addView(endRow);

        // Duration
        durationLabel = new TextView(this);
        durationLabel.setTextColor(TEXT_MUTED);
        durationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        durationLabel.setPadding(dp(50), dp(4), 0, dp(4));
        sec.addView(durationLabel);

        parent.addView(sec);
        updateDtChips();
        updateTimeVis();
    }

    //  Color Section 

    private void buildColorSection(LinearLayout parent) {
        colorPreviewCard = new LinearLayout(this);
        colorPreviewCard.setOrientation(LinearLayout.VERTICAL);
        colorPreviewCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(-1, -2);
        prevLp.bottomMargin = dp(12);
        updateColorPreview();
        parent.addView(colorPreviewCard, prevLp);

        colorRow1 = new LinearLayout(this);
        colorRow1.setOrientation(LinearLayout.HORIZONTAL);
        colorRow1.setGravity(Gravity.CENTER);
        colorRow1.setPadding(0, dp(4), 0, dp(4));

        colorRow2 = new LinearLayout(this);
        colorRow2.setOrientation(LinearLayout.HORIZONTAL);
        colorRow2.setGravity(Gravity.CENTER);
        colorRow2.setPadding(0, dp(4), 0, dp(4));

        populateColorSwatches();
        parent.addView(colorRow1);
        parent.addView(colorRow2);
    }

    private void populateColorSwatches() {
        colorRow1.removeAllViews();
        colorRow2.removeAllViews();
        for (int i = 0; i < ALL_COLORS.length; i++) {
            View sw = makeColorSwatch(ALL_COLORS[i]);
            if (i < 6) colorRow1.addView(sw);
            else colorRow2.addView(sw);
        }
    }

    //  Recurrence Section 

    private void buildRecurrenceSection(LinearLayout parent) {
        LinearLayout recCont = new LinearLayout(this);
        recCont.setOrientation(LinearLayout.VERTICAL);

        LinearLayout recRow = hRow(Gravity.CENTER_VERTICAL);
        recRow.setPadding(0, dp(4), 0, dp(4));
        recurrenceBtn = new TextView(this);
        recurrenceBtn.setTextColor(TEXT_PRIMARY);
        recurrenceBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        recurrenceBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable recBtnBg = new GradientDrawable();
        recBtnBg.setCornerRadius(dp(10));
        recBtnBg.setColor(BG_ELEVATED);
        recurrenceBtn.setBackground(recBtnBg);
        recurrenceBtn.setOnClickListener(v -> showRecurrenceMenu());
        recurrenceBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        recRow.addView(recurrenceBtn);
        recCont.addView(recRow);

        recurrenceSummaryView = new TextView(this);
        recurrenceSummaryView.setTextColor(TEXT_MUTED);
        recurrenceSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        recurrenceSummaryView.setPadding(dp(4), dp(6), 0, dp(2));
        recurrenceSummaryView.setVisibility(View.GONE);
        recCont.addView(recurrenceSummaryView);

        parent.addView(recCont);
        updateRecurrenceUI();
    }
    // 
    //  DATE / TIME PICKERS
    // 

    private void pickDate(boolean isStart) {
        String cur = isStart ? selStartDate : selEndDate;
        Calendar cal = parseDate(cur);
        if (cal == null) cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this, R.style.DarkDatePickerDialog,
                (view, year, month, day) -> {
                    String d = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                    if (isStart) {
                        selStartDate = d;
                        if (selEndDate != null && selEndDate.compareTo(selStartDate) < 0)
                            selEndDate = selStartDate;
                    } else {
                        selEndDate = d;
                    }
                    updateDtChips();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    private void pickTime(boolean isStart) {
        int[] hm = parseTime(isStart ? selStartTime : selEndTime);
        TimePickerDialog dlg = new TimePickerDialog(this, R.style.DarkTimePickerDialog,
                (view, hour, minute) -> {
                    String t = String.format(Locale.US, "%02d:%02d", hour, minute);
                    if (isStart) {
                        selStartTime = t;
                        if (selStartDate.equals(selEndDate) && selEndTime != null && selEndTime.compareTo(t) <= 0) {
                            Calendar ec = Calendar.getInstance();
                            ec.set(Calendar.HOUR_OF_DAY, hour);
                            ec.set(Calendar.MINUTE, minute);
                            ec.add(Calendar.MINUTE, repository.getSettings().defaultEventDurationMinutes);
                            selEndTime = String.format(Locale.US, "%02d:%02d",
                                    ec.get(Calendar.HOUR_OF_DAY), ec.get(Calendar.MINUTE));
                        }
                    } else {
                        selEndTime = t;
                    }
                    updateDtChips();
                },
                hm[0], hm[1], false);
        dlg.show();
    }

    // 
    //  REMINDER MANAGEMENT
    // 

    private void showAddReminderMenu() {
        String[] labels = {
            "At time of event", "5 minutes before", "10 minutes before",
            "15 minutes before", "30 minutes before", "1 hour before",
            "2 hours before", "1 day before", "2 days before", "1 week before",
            "Custom..."
        };
        int[] vals = { 0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080, -1 };

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Add Reminder")
                .setItems(labels, (dialog, which) -> {
                    if (vals[which] == -1) { showCustomReminderInput(); return; }
                    int v = vals[which];
                    if (!selReminders.contains(v)) {
                        selReminders.add(v);
                        Collections.sort(selReminders);
                        refreshReminderChips();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showCustomReminderInput() {
        LinearLayout lay = hRow(Gravity.CENTER_VERTICAL);
        lay.setPadding(dp(24), dp(16), dp(24), dp(8));

        EditText numIn = new EditText(this);
        numIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        numIn.setHint("30");
        numIn.setHintTextColor(TEXT_MUTED);
        numIn.setTextColor(TEXT_PRIMARY);
        numIn.setLayoutParams(new LinearLayout.LayoutParams(dp(80), -2));
        styleInput(numIn);
        lay.addView(numIn);

        final String[] units = { "minutes", "hours", "days" };
        final int[] mult = { 1, 60, 1440 };
        final int[] selUnit = { 0 };

        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, units);
        sp.setAdapter(ad);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { selUnit[0] = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, -2, 1f);
        spLp.setMarginStart(dp(12));
        lay.addView(sp, spLp);

        TextView bef = new TextView(this);
        bef.setText("  before");
        bef.setTextColor(TEXT_SECONDARY);
        bef.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lay.addView(bef);

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Custom Reminder")
                .setView(lay)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        int amount = Integer.parseInt(numIn.getText().toString().trim());
                        int minutes = amount * mult[selUnit[0]];
                        if (minutes >= 0 && !selReminders.contains(minutes)) {
                            selReminders.add(minutes);
                            Collections.sort(selReminders);
                            refreshReminderChips();
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null).show();
    }

    // 
    //  RECURRENCE
    // 

    private void showRecurrenceMenu() {
        String[] options = {
            "Does not repeat", "Every day", "Every week",
            "Every month", "Every year", "Every weekday (Mon\u2013Fri)", "Custom..."
        };
        String[] vals = {
            CalendarEvent.RECURRENCE_NONE, CalendarEvent.RECURRENCE_DAILY,
            CalendarEvent.RECURRENCE_WEEKLY, CalendarEvent.RECURRENCE_MONTHLY,
            CalendarEvent.RECURRENCE_YEARLY, "weekday", CalendarEvent.RECURRENCE_CUSTOM
        };

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Repeat")
                .setItems(options, (dialog, which) -> {
                    String val = vals[which];
                    if ("weekday".equals(val)) {
                        selRecurrence = CalendarEvent.RECURRENCE_CUSTOM;
                        try {
                            JSONObject rule = new JSONObject();
                            rule.put("interval", 1);
                            rule.put("unit", "weeks");
                            rule.put("daysOfWeek", new JSONArray(Arrays.asList(2, 3, 4, 5, 6)));
                            selRecurrenceRule = rule.toString();
                        } catch (JSONException ignored) {}
                        selRecurrenceEndDate = null; selRecurrenceCount = 0;
                        updateRecurrenceUI();
                    } else if (CalendarEvent.RECURRENCE_CUSTOM.equals(val)) {
                        showCustomRecurrenceBuilder();
                    } else {
                        selRecurrence = val;
                        selRecurrenceRule = null; selRecurrenceEndDate = null; selRecurrenceCount = 0;
                        updateRecurrenceUI();
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showCustomRecurrenceBuilder() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        // Interval row
        LinearLayout intRow = hRow(Gravity.CENTER_VERTICAL);
        intRow.setPadding(0, 0, 0, dp(12));
        TextView evLbl = new TextView(this); evLbl.setText("Every "); evLbl.setTextColor(TEXT_PRIMARY);
        evLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        intRow.addView(evLbl);

        EditText intIn = new EditText(this);
        intIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        intIn.setText("1");
        intIn.setTextColor(TEXT_PRIMARY);
        intIn.setGravity(Gravity.CENTER);
        intIn.setLayoutParams(new LinearLayout.LayoutParams(dp(60), -2));
        styleInput(intIn);
        intRow.addView(intIn);

        final String[] unitOpts = { "days", "weeks", "months", "years" };
        final int[] selUnitIdx = { 0 };
        Spinner unitSp = new Spinner(this);
        unitSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, unitOpts));
        unitSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { selUnitIdx[0] = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(0, -2, 1f);
        uLp.setMarginStart(dp(8));
        intRow.addView(unitSp, uLp);
        layout.addView(intRow);

        // Day-of-week selectors
        TextView dowHdr = new TextView(this);
        dowHdr.setText("Repeat on");
        dowHdr.setTextColor(TEXT_SECONDARY);
        dowHdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dowHdr.setPadding(0, dp(4), 0, dp(8));
        layout.addView(dowHdr);

        LinearLayout dowRow = hRow(Gravity.CENTER);
        dowRow.setPadding(0, 0, 0, dp(16));
        String[] dayLbls = { "S", "M", "T", "W", "T", "F", "S" };
        int[] dayVals = { 1, 2, 3, 4, 5, 6, 7 };
        boolean[] daySel = new boolean[7];
        Calendar sc = parseDate(selStartDate);
        if (sc != null) {
            int dow = sc.get(Calendar.DAY_OF_WEEK);
            for (int i = 0; i < 7; i++) if (dayVals[i] == dow) daySel[i] = true;
        }
        TextView[] dayBtns = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            dayBtns[i] = new TextView(this);
            dayBtns[i].setText(dayLbls[i]);
            dayBtns[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            dayBtns[i].setTypeface(null, Typeface.BOLD);
            dayBtns[i].setGravity(Gravity.CENTER);
            dayBtns[i].setMinWidth(dp(38));
            dayBtns[i].setMinHeight(dp(38));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(38), dp(38));
            dLp.setMargins(dp(3), 0, dp(3), 0);
            styleDayBtn(dayBtns[i], daySel[idx]);
            dayBtns[i].setOnClickListener(v -> { daySel[idx] = !daySel[idx]; styleDayBtn(dayBtns[idx], daySel[idx]); });
            dowRow.addView(dayBtns[i], dLp);
        }
        layout.addView(dowRow);

        // End condition
        TextView endHdr = new TextView(this);
        endHdr.setText("Ends");
        endHdr.setTextColor(TEXT_SECONDARY);
        endHdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        endHdr.setPadding(0, dp(4), 0, dp(8));
        layout.addView(endHdr);

        final int[] selEnd = { 0 };
        final String[] endDateVal = { null };

        RadioGroup endGroup = new RadioGroup(this);
        endGroup.setOrientation(RadioGroup.VERTICAL);
        String[] endLabels = { "Never", "On date", "After N occurrences" };
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(endLabels[i]);
            rb.setTextColor(TEXT_PRIMARY);
            rb.setId(View.generateViewId());
            if (i == 0) rb.setChecked(true);
            endGroup.addView(rb);
        }

        EditText endDateIn = new EditText(this);
        endDateIn.setHint("Tap to select date");
        endDateIn.setHintTextColor(TEXT_MUTED);
        endDateIn.setTextColor(TEXT_PRIMARY);
        endDateIn.setVisibility(View.GONE);
        endDateIn.setFocusable(false);
        endDateIn.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance(); c.add(Calendar.MONTH, 3);
            new DatePickerDialog(this, R.style.DarkDatePickerDialog, (vv, y, m, dd) -> {
                endDateVal[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, dd);
                endDateIn.setText(endDateVal[0]);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        styleInput(endDateIn);

        EditText endCountIn = new EditText(this);
        endCountIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        endCountIn.setText("10");
        endCountIn.setTextColor(TEXT_PRIMARY);
        endCountIn.setVisibility(View.GONE);
        styleInput(endCountIn);

        endGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int idx = 0;
            for (int i = 0; i < group.getChildCount(); i++)
                if (group.getChildAt(i).getId() == checkedId) { idx = i; break; }
            selEnd[0] = idx;
            endDateIn.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
            endCountIn.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        });

        layout.addView(endGroup);
        layout.addView(endDateIn);
        layout.addView(endCountIn);

        ScrollView sv = new ScrollView(this); sv.addView(layout);
        sv.setLayoutParams(new ViewGroup.LayoutParams(-1, dp(450)));

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Custom Recurrence")
                .setView(sv)
                .setPositiveButton("Done", (d, w) -> {
                    selRecurrence = CalendarEvent.RECURRENCE_CUSTOM;
                    try {
                        int interval = 1;
                        try { interval = Math.max(1, Integer.parseInt(intIn.getText().toString().trim())); } catch (Exception e) {}
                        JSONObject rule = new JSONObject();
                        rule.put("interval", interval);
                        rule.put("unit", unitOpts[selUnitIdx[0]]);
                        if ("weeks".equals(unitOpts[selUnitIdx[0]])) {
                            JSONArray dArr = new JSONArray();
                            for (int i = 0; i < 7; i++) if (daySel[i]) dArr.put(dayVals[i]);
                            if (dArr.length() > 0) rule.put("daysOfWeek", dArr);
                        }
                        if (selEnd[0] == 1 && endDateVal[0] != null) {
                            rule.put("endDate", endDateVal[0]);
                            selRecurrenceEndDate = endDateVal[0]; selRecurrenceCount = 0;
                        } else if (selEnd[0] == 2) {
                            try {
                                int cnt = Integer.parseInt(endCountIn.getText().toString().trim());
                                rule.put("endCount", cnt); selRecurrenceCount = cnt;
                            } catch (Exception e) {}
                            selRecurrenceEndDate = null;
                        } else {
                            selRecurrenceEndDate = null; selRecurrenceCount = 0;
                        }
                        selRecurrenceRule = rule.toString();
                    } catch (JSONException ignored) {}
                    updateRecurrenceUI();
                })
                .setNegativeButton("Cancel", null).show();
    }
    // 
    //  SAVE / DELETE / ACTIONS
    // 

    private void saveEvent() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) { titleInput.setError("Title is required"); titleInput.requestFocus(); return; }

        if (!isNewEvent && event != null && event.isRecurring()) {
            showRecurringSaveScope(title);
            return;
        }
        performSave(title, isNewEvent ? "new" : "all");
    }

    private void showRecurringSaveScope(String title) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Edit recurring event")
                .setItems(new String[]{"This event only", "This and future events", "All events in series"},
                        (dialog, which) -> {
                            String[] scopes = {"single", "future", "all"};
                            performSave(title, scopes[which]);
                        })
                .setNegativeButton("Cancel", null).show();
    }

    private void performSave(String title, String scope) {
        CalendarEvent ev = isNewEvent ? new CalendarEvent() : event;
        ev.title = title;
        ev.description = descriptionInput != null ? descriptionInput.getText().toString().trim() : "";
        ev.location = locationInput != null ? locationInput.getText().toString().trim() : "";
        ev.notes = notesInput != null ? notesInput.getText().toString().trim() : "";
        ev.startDate = selStartDate;
        ev.startTime = selAllDay ? null : selStartTime;
        ev.endDate = selEndDate;
        ev.endTime = selAllDay ? null : selEndTime;
        ev.isAllDay = selAllDay;
        ev.categoryId = selCategoryId;
        ev.eventType = selEventType;
        ev.colorHex = selColor;
        ev.recurrence = selRecurrence;
        ev.recurrenceRule = selRecurrenceRule;
        ev.recurrenceEndDate = selRecurrenceEndDate;
        ev.recurrenceCount = selRecurrenceCount;
        ev.reminderOffsets = new ArrayList<>(selReminders);
        ev.attachmentPaths = new ArrayList<>(selAttachments);
        ev.isStarred = selStarred;
        ev.updatedAt = System.currentTimeMillis();
        if (isNewEvent) ev.createdAt = ev.updatedAt;

        CalendarActivity calAct = CalendarActivity.getInstance();
        if (isNewEvent) {
            repository.addEvent(ev);
            CalendarNotificationHelper.scheduleEventReminders(this, ev);
            if (calAct != null) calAct.syncEventToServer(ev);
            Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show();
        } else {
            switch (scope) {
                case "single": repository.editSingleOccurrence(ev.id, occurrenceDate, ev); break;
                case "future": repository.editFutureOccurrences(ev.id, occurrenceDate, ev); break;
                default: repository.updateEvent(ev); break;
            }
            CalendarNotificationHelper.scheduleEventReminders(this, ev);
            if (calAct != null) calAct.syncEventUpdateToServer(ev);
            Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right);
    }

    private void confirmDelete() {
        if (event == null) return;
        if (event.isRecurring()) {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete recurring event")
                    .setItems(new String[]{"This event only", "This and future events", "All events in series"},
                            (dialog, which) -> {
                                CalendarActivity calAct = CalendarActivity.getInstance();
                                switch (which) {
                                    case 0: repository.deleteSingleOccurrence(event.id, occurrenceDate); break;
                                    case 1: repository.deleteFutureOccurrences(event.id, occurrenceDate); break;
                                    case 2:
                                        repository.deleteEvent(event.id);
                                        CalendarNotificationHelper.cancelEventReminders(this, event);
                                        if (calAct != null) calAct.syncEventDeleteToServer(event.id);
                                        break;
                                }
                                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                                finish();
                                overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right);
                            })
                    .setNegativeButton("Cancel", null).show();
        } else {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Delete Event")
                    .setMessage("Delete \"" + event.title + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repository.deleteEvent(event.id);
                        CalendarNotificationHelper.cancelEventReminders(this, event);
                        CalendarActivity calAct = CalendarActivity.getInstance();
                        if (calAct != null) calAct.syncEventDeleteToServer(event.id);
                        Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show();
                        finish();
                        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_right);
                    })
                    .setNegativeButton("Cancel", null).show();
        }
    }

    private void shareEvent() {
        if (event == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCC5 ").append(event.title).append("\n");
        if (event.startDate != null) {
            String dow = getDow(event.startDate);
            sb.append("\uD83D\uDCC6 ").append(dow != null ? dow + ", " : "").append(fmtDateDisplay(event.startDate));
            if (event.endDate != null && !event.endDate.equals(event.startDate))
                sb.append(" \u2014 ").append(fmtDateDisplay(event.endDate));
            sb.append("\n");
        }
        if (!event.isAllDay && event.startTime != null) {
            sb.append("\uD83D\uDD50 ").append(fmtTimeDisplay(event.startTime));
            if (event.endTime != null) sb.append(" \u2014 ").append(fmtTimeDisplay(event.endTime));
            sb.append("\n");
        }
        if (event.location != null && !event.location.isEmpty())
            sb.append("\uD83D\uDCCD ").append(event.location).append("\n");
        if (event.isRecurring())
            sb.append("\uD83D\uDD04 ").append(event.getRecurrenceSummary()).append("\n");
        if (event.description != null && !event.description.isEmpty())
            sb.append("\uD83D\uDCDD ").append(event.description).append("\n");

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, event.title);
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Share Event"));
    }

    private void createTaskFromEvent() {
        if (event == null) return;
        try {
            Task task = new Task();
            task.title = event.title;
            task.description = event.description != null ? event.description : "";
            task.dueDate = event.startDate;
            task.dueTime = event.startTime;
            task.source = "calendar";
            TaskRepository taskRepo = new TaskRepository(this);
            taskRepo.addTask(task);
            Toast.makeText(this, "\u2713 Task created: " + event.title, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to create task", Toast.LENGTH_SHORT).show();
        }
    }

    private void duplicateEvent() {
        if (event == null) return;
        CalendarEvent dup = event.duplicate();
        dup.title = event.title + " (Copy)";
        repository.addEvent(dup);
        CalendarActivity calAct = CalendarActivity.getInstance();
        if (calAct != null) calAct.syncEventToServer(dup);
        Toast.makeText(this, "Event duplicated", Toast.LENGTH_SHORT).show();
    }

    // 
    //  UI UPDATE HELPERS
    // 

    private void updateDtChips() {
        if (startDateChip != null) startDateChip.setText("\uD83D\uDCC5 " + fmtDateChip(selStartDate));
        if (startTimeChip != null) startTimeChip.setText("\uD83D\uDD50 " + fmtTimeDisplay(selStartTime));
        if (endDateChip != null)   endDateChip.setText("\uD83D\uDCC5 " + fmtDateChip(selEndDate));
        if (endTimeChip != null)   endTimeChip.setText("\uD83D\uDD50 " + fmtTimeDisplay(selEndTime));
        updateDurLabel();
    }

    private void updateTimeVis() {
        int vis = selAllDay ? View.GONE : View.VISIBLE;
        if (startTimeChip != null) startTimeChip.setVisibility(vis);
        if (endTimeChip != null) endTimeChip.setVisibility(vis);
        updateDurLabel();
    }

    private void updateDurLabel() {
        if (durationLabel == null) return;
        if (selAllDay) {
            int days = daysBetween(selStartDate, selEndDate);
            durationLabel.setText((days <= 0 ? 1 : days + 1) + " day" + (days > 0 ? "s" : ""));
            durationLabel.setVisibility(View.VISIBLE);
        } else {
            int mins = minsBetween(selStartDate, selStartTime, selEndDate, selEndTime);
            if (mins <= 0) { durationLabel.setVisibility(View.GONE); return; }
            durationLabel.setVisibility(View.VISIBLE);
            if (mins < 60) durationLabel.setText(mins + " minutes");
            else {
                int h = mins / 60, m = mins % 60;
                String t = h + " hour" + (h > 1 ? "s" : "");
                if (m > 0) t += " " + m + " min";
                durationLabel.setText(t);
            }
        }
    }

    private void updateColorPreview() {
        if (colorPreviewCard == null) return;
        colorPreviewCard.removeAllViews();
        int ec = safeColor(selColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(adjustAlpha(ec, 0.15f));
        bg.setStroke(dp(1), adjustAlpha(ec, 0.3f));
        colorPreviewCard.setBackground(bg);

        LinearLayout row = hRow(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable dBg = new GradientDrawable();
        dBg.setShape(GradientDrawable.OVAL); dBg.setColor(ec);
        dot.setBackground(dBg);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
        row.addView(dot);

        String prevTitle = titleInput != null && titleInput.getText().length() > 0
                ? titleInput.getText().toString() : "Event Preview";
        TextView pt = new TextView(this);
        pt.setText(prevTitle); pt.setTextColor(TEXT_PRIMARY);
        pt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pt.setTypeface(null, Typeface.BOLD);
        pt.setPadding(dp(8), 0, 0, 0);
        row.addView(pt);
        colorPreviewCard.addView(row);

        TextView pTime = new TextView(this);
        pTime.setText(fmtDateChip(selStartDate) + ", " + fmtTimeDisplay(selStartTime));
        pTime.setTextColor(TEXT_SECONDARY);
        pTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pTime.setPadding(dp(18), dp(2), 0, 0);
        colorPreviewCard.addView(pTime);
    }

    private void refreshCategoryChips() {
        if (categoryChipCont == null) return;
        categoryChipCont.removeAllViews();
        for (EventCategory c : repository.getAllCategories()) {
            boolean sel = c.id.equals(selCategoryId);
            TextView chip = selChip(c.name, c.getColor(), sel);
            chip.setOnClickListener(v -> { selCategoryId = c.id; refreshCategoryChips(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            categoryChipCont.addView(chip, lp);
        }
        TextView addChip = new TextView(this);
        addChip.setText("+ Add"); addChip.setTextColor(ACCENT_BLUE);
        addChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        addChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable aBg = new GradientDrawable();
        aBg.setCornerRadius(dp(16)); aBg.setStroke(dp(1), ACCENT_BLUE); aBg.setColor(Color.TRANSPARENT);
        addChip.setBackground(aBg);
        addChip.setOnClickListener(v -> startActivity(new Intent(this, CalendarCategoriesActivity.class)));
        categoryChipCont.addView(addChip);
    }

    private void refreshEventTypeChips() {
        if (eventTypeCont == null) return;
        eventTypeCont.removeAllViews();
        for (int i = 0; i < CalendarEvent.EVENT_TYPES.length; i++) {
            String type = CalendarEvent.EVENT_TYPES[i];
            String lbl = CalendarEvent.EVENT_TYPE_ICONS[i] + " " + CalendarEvent.EVENT_TYPE_LABELS[i];
            boolean sel = type.equals(selEventType);
            TextView pill = typePill(lbl, sel);
            pill.setOnClickListener(v -> {
                selEventType = type;
                refreshEventTypeChips();
                if ((CalendarEvent.TYPE_BIRTHDAY.equals(type) || CalendarEvent.TYPE_ANNIVERSARY.equals(type))
                        && CalendarEvent.RECURRENCE_NONE.equals(selRecurrence)) {
                    selRecurrence = CalendarEvent.RECURRENCE_YEARLY;
                    updateRecurrenceUI();
                    Toast.makeText(this, "Set to repeat yearly", Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(dp(8));
            eventTypeCont.addView(pill, lp);
        }
    }

    private void refreshReminderChips() {
        if (reminderChipCont == null) return;
        reminderChipCont.removeAllViews();
        FlexWrapLayout wrap = new FlexWrapLayout(this);
        for (int off : selReminders) wrap.addView(makeDismissRemChip(off));
        TextView addBtn = new TextView(this);
        addBtn.setText("+ Add Reminder"); addBtn.setTextColor(ACCENT_BLUE);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        addBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable aBg = new GradientDrawable();
        aBg.setCornerRadius(dp(16)); aBg.setStroke(dp(1), ACCENT_BLUE); aBg.setColor(Color.TRANSPARENT);
        addBtn.setBackground(aBg);
        addBtn.setOnClickListener(v -> showAddReminderMenu());
        wrap.addView(addBtn);
        reminderChipCont.addView(wrap);
    }

    private void refreshAttachmentChips() {
        if (attachmentChipCont == null) return;
        attachmentChipCont.removeAllViews();
        FlexWrapLayout wrap = new FlexWrapLayout(this);
        for (int i = 0; i < selAttachments.size(); i++) {
            String p = selAttachments.get(i); final int idx = i;
            wrap.addView(makeDismissAttChip(p, idx));
        }
        TextView addBtn = new TextView(this);
        addBtn.setText("\uD83D\uDCCE Attach File"); addBtn.setTextColor(ACCENT_BLUE);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        addBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable aBg = new GradientDrawable();
        aBg.setCornerRadius(dp(16)); aBg.setStroke(dp(1), ACCENT_BLUE); aBg.setColor(Color.TRANSPARENT);
        addBtn.setBackground(aBg);
        addBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_ATTACH_FILE);
        });
        wrap.addView(addBtn);
        attachmentChipCont.addView(wrap);
    }

    private void updateRecurrenceUI() {
        if (recurrenceBtn == null) return;
        if (CalendarEvent.RECURRENCE_NONE.equals(selRecurrence)) {
            recurrenceBtn.setText("Does not repeat \u25BC");
            recurrenceSummaryView.setVisibility(View.GONE);
        } else {
            String lbl;
            switch (selRecurrence) {
                case CalendarEvent.RECURRENCE_DAILY:   lbl = "Every day"; break;
                case CalendarEvent.RECURRENCE_WEEKLY:  lbl = "Every week"; break;
                case CalendarEvent.RECURRENCE_MONTHLY: lbl = "Every month"; break;
                case CalendarEvent.RECURRENCE_YEARLY:  lbl = "Every year"; break;
                case CalendarEvent.RECURRENCE_CUSTOM:  lbl = "Custom"; break;
                default: lbl = selRecurrence; break;
            }
            recurrenceBtn.setText(lbl + " \u25BC");
            if (CalendarEvent.RECURRENCE_CUSTOM.equals(selRecurrence) && selRecurrenceRule != null) {
                CalendarEvent tmp = new CalendarEvent();
                tmp.recurrence = selRecurrence; tmp.recurrenceRule = selRecurrenceRule;
                tmp.recurrenceEndDate = selRecurrenceEndDate; tmp.recurrenceCount = selRecurrenceCount;
                tmp.startDate = selStartDate;
                recurrenceSummaryView.setText(tmp.getRecurrenceSummary());
                recurrenceSummaryView.setVisibility(View.VISIBLE);
            } else {
                recurrenceSummaryView.setVisibility(View.GONE);
            }
        }
    }

    private void updateStarIcon() {
        if (starToggle == null) return;
        if (selStarred) {
            starToggle.setImageResource(android.R.drawable.btn_star_big_on);
            starToggle.setColorFilter(ACCENT_AMBER);
        } else {
            starToggle.setImageResource(android.R.drawable.btn_star_big_off);
            starToggle.setColorFilter(TEXT_MUTED);
        }
    }
    // 
    //  UI CREATION HELPERS
    // 

    private View divider() {
        View v = new View(this); v.setBackgroundColor(DIVIDER_CLR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(8); lp.bottomMargin = dp(8); v.setLayoutParams(lp); return v;
    }

    private View formDivider() {
        View v = new View(this); v.setBackgroundColor(DIVIDER_CLR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(12); lp.bottomMargin = dp(12); v.setLayoutParams(lp); return v;
    }

    private TextView secHeader(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(TEXT_SECONDARY); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(null, Typeface.BOLD); tv.setPadding(0, dp(4), 0, dp(8));
        tv.setAllCaps(true); tv.setLetterSpacing(0.05f); return tv;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(TEXT_SECONDARY); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(null, Typeface.BOLD); return tv;
    }

    private TextView makeBadge(String text, int color) {
        TextView b = new TextView(this); b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); b.setTypeface(null, Typeface.BOLD);
        b.setPadding(dp(10), dp(4), dp(10), dp(4)); b.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12)); bg.setColor(adjustAlpha(color, 0.2f)); b.setBackground(bg); return b;
    }

    private void spacer(LinearLayout row) {
        View s = new View(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(6), dp(1))); row.addView(s);
    }

    private LinearLayout hRow(int gravity) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(gravity); return row;
    }

    private void addIcon(LinearLayout row, String icon) {
        TextView tv = new TextView(this); tv.setText(icon);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setPadding(0, 0, dp(10), 0); row.addView(tv);
    }

    private TextView bodyText(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(TEXT_PRIMARY); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); return tv;
    }

    private TextView rowLabel(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(TEXT_SECONDARY); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setMinWidth(dp(50)); return tv;
    }

    private TextView dtChip() {
        TextView chip = new TextView(this);
        chip.setTextColor(TEXT_PRIMARY); chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setPadding(dp(14), dp(10), dp(14), dp(10)); chip.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10)); bg.setColor(BG_ELEVATED); chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(8)); chip.setLayoutParams(lp); return chip;
    }

    private TextView selChip(String text, int accent, boolean selected) {
        TextView chip = new TextView(this); chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); chip.setTypeface(null, Typeface.BOLD);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(16));
        if (selected) {
            bg.setColor(adjustAlpha(accent, 0.25f)); chip.setTextColor(accent); bg.setStroke(dp(1), accent);
        } else {
            bg.setColor(BG_ELEVATED); chip.setTextColor(TEXT_SECONDARY);
        }
        chip.setBackground(bg); return chip;
    }

    private TextView typePill(String text, boolean selected) {
        TextView pill = new TextView(this); pill.setText(text);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pill.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(20));
        if (selected) {
            bg.setColor(adjustAlpha(ACCENT_BLUE, 0.2f)); pill.setTextColor(ACCENT_BLUE);
            bg.setStroke(dp(1), ACCENT_BLUE); pill.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(BG_ELEVATED); pill.setTextColor(TEXT_SECONDARY);
        }
        pill.setBackground(bg); return pill;
    }

    private View makeColorSwatch(String hex) {
        boolean sel = hex.equals(selColor);
        int col = safeColor(hex);
        FrameLayout frame = new FrameLayout(this);
        int size = dp(40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));

        View sw = new View(this);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setCornerRadius(dp(10)); sBg.setColor(col);
        if (sel) sBg.setStroke(dp(3), Color.WHITE);
        sw.setBackground(sBg);
        sw.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        frame.addView(sw);

        if (sel) {
            TextView ck = new TextView(this); ck.setText("\u2713");
            ck.setTextColor(Color.WHITE); ck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            ck.setTypeface(null, Typeface.BOLD); ck.setGravity(Gravity.CENTER);
            ck.setLayoutParams(new FrameLayout.LayoutParams(-1, -1)); frame.addView(ck);
        }
        frame.setLayoutParams(lp);
        frame.setOnClickListener(v -> { selColor = hex; populateColorSwatches(); updateColorPreview(); });
        return frame;
    }

    private TextView makeDismissRemChip(int offset) {
        TextView chip = new TextView(this);
        chip.setText("\uD83D\uDD14 " + CalendarEvent.formatReminderOffset(offset) + "  \u00D7");
        chip.setTextColor(TEXT_PRIMARY); chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16)); bg.setColor(BG_ELEVATED); chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), dp(8), dp(4)); chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> { selReminders.remove(Integer.valueOf(offset)); refreshReminderChips(); });
        return chip;
    }

    private View makeDismissAttChip(String path, int index) {
        TextView chip = new TextView(this);
        chip.setText("\uD83D\uDCCE " + getFileName(path) + "  \u00D7");
        chip.setTextColor(TEXT_PRIMARY); chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16)); bg.setColor(BG_ELEVATED); chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), dp(8), dp(4)); chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> { selAttachments.remove(index); refreshAttachmentChips(); });
        return chip;
    }

    private View makeAttachViewChip(String path) {
        TextView chip = new TextView(this);
        chip.setText("\uD83D\uDCCE " + getFileName(path));
        chip.setTextColor(ACCENT_BLUE); chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16)); bg.setColor(BG_ELEVATED); chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), dp(8), dp(4)); chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(path), "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) { Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show(); }
        });
        return chip;
    }

    private TextView makeActionBtn(String text, View.OnClickListener listener) {
        TextView btn = new TextView(this); btn.setText(text);
        btn.setTextColor(TEXT_PRIMARY); btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD); btn.setPadding(dp(16), dp(12), dp(16), dp(12));
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12)); bg.setColor(BG_ELEVATED); btn.setBackground(bg);
        btn.setOnClickListener(listener); return btn;
    }

    private HorizontalScrollView hScroll(LinearLayout content) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(content); return hsv;
    }

    private void styleInput(EditText et) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10)); bg.setColor(BG_INPUT); bg.setStroke(dp(1), DIVIDER_CLR);
        et.setBackground(bg); et.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private void styleDayBtn(TextView btn, boolean sel) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (sel) { bg.setColor(ACCENT_BLUE); btn.setTextColor(Color.WHITE); }
        else { bg.setColor(BG_ELEVATED); btn.setTextColor(TEXT_SECONDARY); }
        btn.setBackground(bg);
    }

    // 
    //  FORMAT / UTILITY
    // 

    private String fmtDateDisplay(String d) {
        if (d == null) return "";
        try { return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)); }
        catch (ParseException e) { return d; }
    }

    private String fmtDateChip(String d) {
        if (d == null) return "";
        try { return new SimpleDateFormat("MMM d", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)); }
        catch (ParseException e) { return d; }
    }

    private String fmtTimeDisplay(String t) {
        if (t == null || t.isEmpty()) return "";
        try { return new SimpleDateFormat("h:mm a", Locale.US).format(new SimpleDateFormat("HH:mm", Locale.US).parse(t)); }
        catch (ParseException e) { return t; }
    }

    private String fmtTimestamp(String ts) {
        if (ts == null) return "";
        try { return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(ts)); }
        catch (ParseException e) { return ts; }
    }

    private String fmtMillis(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(new Date(millis));
    }

    private String getDow(String d) {
        if (d == null) return null;
        try { return new SimpleDateFormat("EEEE", Locale.US).format(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)); }
        catch (ParseException e) { return null; }
    }

    private String fmtCalDate(Calendar cal) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private Calendar parseDate(String d) {
        if (d == null || d.isEmpty()) return null;
        try {
            String[] p = d.split("-");
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0); return c;
        } catch (Exception e) { return null; }
    }

    private int[] parseTime(String t) {
        if (t == null || t.isEmpty()) return new int[]{9, 0};
        try { String[] p = t.split(":"); return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
        catch (Exception e) { return new int[]{9, 0}; }
    }

    private int daysBetween(String d1, String d2) {
        Calendar c1 = parseDate(d1), c2 = parseDate(d2);
        if (c1 == null || c2 == null) return 0;
        return (int) ((c2.getTimeInMillis() - c1.getTimeInMillis()) / (24 * 60 * 60 * 1000));
    }

    private int minsBetween(String d1, String t1, String d2, String t2) {
        Calendar c1 = parseDate(d1), c2 = parseDate(d2);
        if (c1 == null || c2 == null) return 0;
        int[] hm1 = parseTime(t1), hm2 = parseTime(t2);
        c1.set(Calendar.HOUR_OF_DAY, hm1[0]); c1.set(Calendar.MINUTE, hm1[1]);
        c2.set(Calendar.HOUR_OF_DAY, hm2[0]); c2.set(Calendar.MINUTE, hm2[1]);
        return (int) ((c2.getTimeInMillis() - c1.getTimeInMillis()) / 60000);
    }

    private String getFileName(String uriStr) {
        if (uriStr == null) return "File";
        try {
            Uri uri = Uri.parse(uriStr);
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {}
        if (uriStr.contains("/")) return uriStr.substring(uriStr.lastIndexOf("/") + 1);
        return "File";
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private int safeColor(String hex) {
        try { return Color.parseColor(hex); } catch (Exception e) { return ACCENT_BLUE; }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    // 
    //  FLEX WRAP LAYOUT
    // 

    private static class FlexWrapLayout extends ViewGroup {
        public FlexWrapLayout(Context context) { super(context); }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int maxW = View.MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, lineH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                measureChild(child, widthSpec, heightSpec);
                int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
                if (x + cw > maxW && x > 0) { x = 0; y += lineH; lineH = 0; }
                x += cw; lineH = Math.max(lineH, ch);
            }
            setMeasuredDimension(View.MeasureSpec.getSize(widthSpec),
                    y + lineH + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();
            int x = getPaddingLeft(), y = getPaddingTop(), lineH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
                if (x - getPaddingLeft() + cw > maxW && x > getPaddingLeft()) {
                    x = getPaddingLeft(); y += lineH; lineH = 0;
                }
                child.layout(x, y, x + cw, y + ch);
                x += cw; lineH = Math.max(lineH, ch);
            }
        }
    }
}
