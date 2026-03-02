package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen Activity for creating and editing meetings.
 *
 * Supports: meeting type selection, platform chips, date/time pickers,
 * attendee management, agenda building with duration totals,
 * recurrence, reminders, color picker, and notes.
 *
 * Pass {@link #EXTRA_MEETING_ID} to enter edit mode.
 * Pass {@link #EXTRA_PREFILL_TITLE} / {@link #EXTRA_PREFILL_DATE} to pre-fill fields
 * (e.g. when converting a Task to a Meeting).
 */
public class CreateMeetingActivity extends AppCompatActivity {

    // ─── Extras ──────────────────────────────────────────────────

    public static final String EXTRA_MEETING_ID    = "meeting_id";
    public static final String EXTRA_PREFILL_TITLE = "prefill_title";
    public static final String EXTRA_PREFILL_DATE  = "prefill_date";

    // ─── Fields ──────────────────────────────────────────────────

    private MeetingRepository repo;
    private Meeting editingMeeting;
    private boolean isNewMeeting = true;

    // State
    private String selectedType          = Meeting.TYPE_VIDEO_CALL;
    private String selectedPlatform      = Meeting.PLATFORM_MEET;
    private String selectedDate          = null;   // "yyyy-MM-dd"
    private long   selectedStartTime     = 0;      // millis (hour/min only via Calendar)
    private long   selectedEndTime       = 0;
    private List<Attendee>   editAttendees    = new ArrayList<>();
    private List<AgendaItem> editAgendaItems  = new ArrayList<>();
    private List<Long>       editReminderOffsets = new ArrayList<>(); // minutes before start
    private String selectedColorHex      = "#4A90E2";
    private boolean isAllDay             = false;
    private String selectedRecurrence    = Meeting.RECURRENCE_NONE;

    // Views
    private EditText  etTitle, etMeetingLink, etLocation, etMeetingNotes;
    private TextView  tvMeetingHeader;
    private TextView  chipDateBtn, chipStartTime, chipEndTime, tvDurationCalc;
    private TextView  tvRecurrenceLabel;
    private TextView  tvTotalAgendaDuration;
    private View      viewColorDot;
    private TextView  tvColorHint, tvCategoryLabel;
    private LinearLayout layoutPlatformSelector, layoutMeetingLink, layoutLocationField;
    private LinearLayout attendeesContainer, agendaContainer, remindersContainer;
    private CardView  cardTypeInPerson, cardTypeVideo, cardTypePhone, cardTypeOther;
    private TextView  chipPlatformZoom, chipPlatformMeet, chipPlatformTeams,
                      chipPlatformWebex, chipPlatformOther;
    private Switch    switchAllDay;

    // Accent/highlight color for selected elements
    private static final int COLOR_SELECTED_BORDER = 0xFF6C63FF;
    private static final int COLOR_SELECTED_BG     = 0x1A6C63FF;
    private static final int COLOR_UNSELECTED_TEXT  = 0xFF94A3B8;

    // Color palette for the color picker
    private static final String[] COLOR_PALETTE = {
        "#4A90E2", "#6C63FF", "#43A047", "#F4511E",
        "#E53935", "#FB8C00", "#00897B", "#8E24AA"
    };

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_meeting);

        repo = MeetingRepository.getInstance(this);

        initViews();

        String meetingId = getIntent().getStringExtra(EXTRA_MEETING_ID);
        if (meetingId != null) {
            editingMeeting = repo.getMeeting(meetingId);
            if (editingMeeting != null) {
                isNewMeeting = false;
                populateFromMeeting(editingMeeting);
            } else {
                Toast.makeText(this, "Meeting not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // Pre-fill from intent (e.g. "Convert to Meeting")
        if (isNewMeeting) {
            String prefillTitle = getIntent().getStringExtra(EXTRA_PREFILL_TITLE);
            if (prefillTitle != null && !prefillTitle.isEmpty()) {
                etTitle.setText(prefillTitle);
            }
            String prefillDate = getIntent().getStringExtra(EXTRA_PREFILL_DATE);
            if (prefillDate != null && !prefillDate.isEmpty()) {
                selectedDate = prefillDate;
                updateDateTimeChips();
            }
        }

        tvMeetingHeader.setText(isNewMeeting ? "New Meeting" : "Edit Meeting");

        setupMeetingTypeSelector();
        setupPlatformChips();
        setupDateTimePickers();
        setupAttendeeSection();
        setupAgendaSection();
        setupRecurrenceRow();
        setupReminderSection();
        setupColorPicker();
        setupButtons();

        updateMeetingTypeUI();
        updatePlatformUI();
    }

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges()) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes. Discard them?")
                    .setPositiveButton("Discard", (d, w) -> finish())
                    .setNegativeButton("Keep editing", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═════════════════════════════════════════════════════════════

    private void initViews() {
        tvMeetingHeader = findViewById(R.id.tvMeetingHeader);

        etTitle        = findViewById(R.id.etMeetingTitle);
        etMeetingLink  = findViewById(R.id.etMeetingLink);
        etLocation     = findViewById(R.id.etLocation);
        etMeetingNotes = findViewById(R.id.etMeetingNotes);

        cardTypeInPerson = findViewById(R.id.cardTypeInPerson);
        cardTypeVideo    = findViewById(R.id.cardTypeVideo);
        cardTypePhone    = findViewById(R.id.cardTypePhone);
        cardTypeOther    = findViewById(R.id.cardTypeOther);

        layoutPlatformSelector = findViewById(R.id.layoutPlatformSelector);
        layoutMeetingLink      = findViewById(R.id.layoutMeetingLink);
        layoutLocationField    = findViewById(R.id.layoutLocationField);

        chipPlatformZoom  = findViewById(R.id.chipPlatformZoom);
        chipPlatformMeet  = findViewById(R.id.chipPlatformMeet);
        chipPlatformTeams = findViewById(R.id.chipPlatformTeams);
        chipPlatformWebex = findViewById(R.id.chipPlatformWebex);
        chipPlatformOther = findViewById(R.id.chipPlatformOther);

        chipDateBtn    = findViewById(R.id.chipDateBtn);
        chipStartTime  = findViewById(R.id.chipStartTime);
        chipEndTime    = findViewById(R.id.chipEndTime);
        tvDurationCalc = findViewById(R.id.tvDurationCalc);
        switchAllDay   = findViewById(R.id.switchAllDay);

        attendeesContainer    = findViewById(R.id.attendeesContainer);
        agendaContainer       = findViewById(R.id.agendaContainer);
        tvTotalAgendaDuration = findViewById(R.id.tvTotalAgendaDuration);
        remindersContainer    = findViewById(R.id.remindersContainer);
        tvRecurrenceLabel     = findViewById(R.id.tvRecurrenceLabel);

        viewColorDot   = findViewById(R.id.viewColorDot);
        tvColorHint    = findViewById(R.id.tvColorHint);
        tvCategoryLabel = findViewById(R.id.tvCategoryLabel);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // Top save button
        TextView btnSaveMeetingTop = findViewById(R.id.btnSaveMeetingTop);
        if (btnSaveMeetingTop != null) {
            btnSaveMeetingTop.setOnClickListener(v -> saveMeeting());
        }

        // Test link button
        TextView btnTestLink = findViewById(R.id.btnTestLink);
        if (btnTestLink != null) {
            btnTestLink.setOnClickListener(v -> testLink());
        }

        // All-day switch
        if (switchAllDay != null) {
            switchAllDay.setOnCheckedChangeListener((btn, checked) -> {
                isAllDay = checked;
                chipStartTime.setVisibility(checked ? View.GONE : View.VISIBLE);
                chipEndTime.setVisibility(checked ? View.GONE : View.VISIBLE);
                tvDurationCalc.setVisibility(checked ? View.GONE : View.VISIBLE);
            });
        }

        // Bottom save button
        TextView btnSaveMeeting = findViewById(R.id.btnSaveMeeting);
        if (btnSaveMeeting != null) {
            btnSaveMeeting.setOnClickListener(v -> saveMeeting());
        }
    }

    // ═════════════════════════════════════════════════════════════
    // MEETING TYPE SELECTOR
    // ═════════════════════════════════════════════════════════════

    private void setupMeetingTypeSelector() {
        View.OnClickListener typeClick = v -> {
            if (v == cardTypeInPerson)   selectedType = Meeting.TYPE_IN_PERSON;
            else if (v == cardTypeVideo) selectedType = Meeting.TYPE_VIDEO_CALL;
            else if (v == cardTypePhone) selectedType = Meeting.TYPE_PHONE_CALL;
            else if (v == cardTypeOther) selectedType = Meeting.TYPE_OTHER;
            updateMeetingTypeUI();
        };
        cardTypeInPerson.setOnClickListener(typeClick);
        cardTypeVideo.setOnClickListener(typeClick);
        cardTypePhone.setOnClickListener(typeClick);
        cardTypeOther.setOnClickListener(typeClick);
    }

    private void updateMeetingTypeUI() {
        updateTypeCardHighlight(cardTypeInPerson,  Meeting.TYPE_IN_PERSON);
        updateTypeCardHighlight(cardTypeVideo,     Meeting.TYPE_VIDEO_CALL);
        updateTypeCardHighlight(cardTypePhone,     Meeting.TYPE_PHONE_CALL);
        updateTypeCardHighlight(cardTypeOther,     Meeting.TYPE_OTHER);

        switch (selectedType) {
            case Meeting.TYPE_VIDEO_CALL:
                layoutPlatformSelector.setVisibility(View.VISIBLE);
                layoutLocationField.setVisibility(View.GONE);
                break;
            case Meeting.TYPE_IN_PERSON:
                layoutPlatformSelector.setVisibility(View.GONE);
                layoutLocationField.setVisibility(View.VISIBLE);
                break;
            case Meeting.TYPE_PHONE_CALL:
                // Repurpose location field as phone number input
                layoutPlatformSelector.setVisibility(View.GONE);
                layoutLocationField.setVisibility(View.VISIBLE);
                etLocation.setHint("Phone number");
                etLocation.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                break;
            default:
                layoutPlatformSelector.setVisibility(View.GONE);
                layoutLocationField.setVisibility(View.VISIBLE);
                etLocation.setHint("Location (optional)");
                etLocation.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                break;
        }
    }

    private void updateTypeCardHighlight(CardView card, String type) {
        boolean active = type.equals(selectedType);
        if (active) {
            GradientDrawable stroke = new GradientDrawable();
            stroke.setShape(GradientDrawable.RECTANGLE);
            stroke.setCornerRadius(dp(12));
            stroke.setColor(COLOR_SELECTED_BG);
            stroke.setStroke(dp(2), COLOR_SELECTED_BORDER);
            card.setForeground(stroke);
        } else {
            card.setForeground(null);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // PLATFORM CHIPS
    // ═════════════════════════════════════════════════════════════

    private void setupPlatformChips() {
        View.OnClickListener platformClick = v -> {
            if (v == chipPlatformZoom)       selectedPlatform = Meeting.PLATFORM_ZOOM;
            else if (v == chipPlatformMeet)  selectedPlatform = Meeting.PLATFORM_MEET;
            else if (v == chipPlatformTeams) selectedPlatform = Meeting.PLATFORM_TEAMS;
            else if (v == chipPlatformWebex) selectedPlatform = Meeting.PLATFORM_WEBEX;
            else if (v == chipPlatformOther) selectedPlatform = Meeting.PLATFORM_OTHER;
            updatePlatformUI();
        };
        chipPlatformZoom.setOnClickListener(platformClick);
        chipPlatformMeet.setOnClickListener(platformClick);
        chipPlatformTeams.setOnClickListener(platformClick);
        chipPlatformWebex.setOnClickListener(platformClick);
        chipPlatformOther.setOnClickListener(platformClick);
    }

    private void updatePlatformUI() {
        updatePlatformChipActive(chipPlatformZoom,  Meeting.PLATFORM_ZOOM);
        updatePlatformChipActive(chipPlatformMeet,  Meeting.PLATFORM_MEET);
        updatePlatformChipActive(chipPlatformTeams, Meeting.PLATFORM_TEAMS);
        updatePlatformChipActive(chipPlatformWebex, Meeting.PLATFORM_WEBEX);
        updatePlatformChipActive(chipPlatformOther, Meeting.PLATFORM_OTHER);

        // Show link field for every platform except "In-Person"
        boolean showLink = !Meeting.PLATFORM_IN_PERSON.equals(selectedPlatform);
        if (layoutMeetingLink != null) {
            layoutMeetingLink.setVisibility(showLink ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePlatformChipActive(TextView chip, String platform) {
        boolean active = platform.equals(selectedPlatform);
        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(16));
            bg.setColor(COLOR_SELECTED_BG);
            bg.setStroke(dp(1), COLOR_SELECTED_BORDER);
            chip.setBackground(bg);
            chip.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            chip.setBackgroundResource(R.drawable.task_priority_pill_selector);
            chip.setTextColor(COLOR_UNSELECTED_TEXT);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // DATE / TIME PICKERS
    // ═════════════════════════════════════════════════════════════

    private void setupDateTimePickers() {
        chipDateBtn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDate != null) {
                try {
                    String[] p = selectedDate.split("-");
                    cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(this, (dp, y, m, d) -> {
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                updateDateTimeChips();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        chipDateBtn.setOnLongClickListener(v -> {
            selectedDate = null;
            updateDateTimeChips();
            return true;
        });

        chipStartTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            if (selectedStartTime > 0) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(selectedStartTime);
                hour   = start.get(Calendar.HOUR_OF_DAY);
                minute = start.get(Calendar.MINUTE);
            }
            new TimePickerDialog(this, (tp, h, min) -> {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, h);
                c.set(Calendar.MINUTE, min);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                selectedStartTime = c.getTimeInMillis();
                updateDateTimeChips();
                updateDurationCalc();
            }, hour, minute, false).show();
        });

        chipEndTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY) + 1;
            int minute = cal.get(Calendar.MINUTE);
            if (selectedEndTime > 0) {
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(selectedEndTime);
                hour   = end.get(Calendar.HOUR_OF_DAY);
                minute = end.get(Calendar.MINUTE);
            }
            new TimePickerDialog(this, (tp, h, min) -> {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, h);
                c.set(Calendar.MINUTE, min);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                selectedEndTime = c.getTimeInMillis();
                updateDateTimeChips();
                updateDurationCalc();
            }, hour, minute, false).show();
        });

        updateDateTimeChips();
    }

    private void updateDateTimeChips() {
        if (selectedDate != null) {
            try {
                String[] p = selectedDate.split("-");
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.US);
                chipDateBtn.setText("📅  " + sdf.format(cal.getTime()));
                chipDateBtn.setTextColor(Color.parseColor("#60A5FA"));
            } catch (Exception e) {
                chipDateBtn.setText("📅  " + selectedDate);
            }
        } else {
            chipDateBtn.setText("📅  Set Date");
            chipDateBtn.setTextColor(COLOR_UNSELECTED_TEXT);
        }

        if (selectedStartTime > 0) {
            chipStartTime.setText("🕐  " + formatTimestamp(selectedStartTime));
            chipStartTime.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            chipStartTime.setText("🕐  Start Time");
            chipStartTime.setTextColor(COLOR_UNSELECTED_TEXT);
        }

        if (selectedEndTime > 0) {
            chipEndTime.setText("🕑  " + formatTimestamp(selectedEndTime));
            chipEndTime.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            chipEndTime.setText("🕑  End Time");
            chipEndTime.setTextColor(COLOR_UNSELECTED_TEXT);
        }
    }

    private void updateDurationCalc() {
        if (selectedStartTime > 0 && selectedEndTime > 0 && selectedEndTime > selectedStartTime) {
            long diffMin = (selectedEndTime - selectedStartTime) / 60000;
            String text;
            if (diffMin < 60) {
                text = diffMin + " min";
            } else {
                long h = diffMin / 60;
                long m = diffMin % 60;
                text = m > 0 ? h + " hr " + m + " min" : h + " hr";
            }
            tvDurationCalc.setText("⏱  " + text);
            tvDurationCalc.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            tvDurationCalc.setText("⏱  Duration");
            tvDurationCalc.setTextColor(COLOR_UNSELECTED_TEXT);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ATTENDEES
    // ═════════════════════════════════════════════════════════════

    private void setupAttendeeSection() {
        View btnAddAttendee = findViewById(R.id.btnAddAttendee);
        if (btnAddAttendee != null) {
            btnAddAttendee.setOnClickListener(v -> showAddAttendeeDialog());
        }
    }

    private void showAddAttendeeDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(android.R.layout.simple_list_item_1, null);

        EditText etName  = new EditText(this);
        etName.setHint("Attendee name");
        etName.setTextColor(Color.parseColor("#F1F5F9"));
        etName.setHintTextColor(Color.parseColor("#4A4A6A"));
        etName.setPadding(dp(16), dp(12), dp(16), dp(12));

        EditText etEmail = new EditText(this);
        etEmail.setHint("Email (optional)");
        etEmail.setTextColor(Color.parseColor("#F1F5F9"));
        etEmail.setHintTextColor(Color.parseColor("#4A4A6A"));
        etEmail.setPadding(dp(16), dp(4), dp(16), dp(12));
        etEmail.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(8), dp(8), dp(0));
        container.addView(etName);
        container.addView(etEmail);

        new AlertDialog.Builder(this)
                .setTitle("Add Attendee")
                .setView(container)
                .setPositiveButton("Add", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Attendee a = new Attendee(name, etEmail.getText().toString().trim(),
                            Attendee.ROLE_REQUIRED);
                    editAttendees.add(a);
                    addAttendeeView(a);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addAttendeeView(Attendee a) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_attendee_chip, attendeesContainer, false);

        TextView tvInitials = row.findViewById(R.id.tvAttendeeInitials);
        TextView tvName     = row.findViewById(R.id.tvAttendeeName);
        TextView tvEmail    = row.findViewById(R.id.tvAttendeeEmail);
        TextView tvRole     = row.findViewById(R.id.tvAttendeeRole);
        TextView btnRemove  = row.findViewById(R.id.btnRemoveAttendee);

        tvInitials.setText(a.getInitials());
        tvName.setText(a.name);
        tvEmail.setText(a.email != null && !a.email.isEmpty() ? a.email : "");
        tvEmail.setVisibility(a.email != null && !a.email.isEmpty() ? View.VISIBLE : View.GONE);
        tvRole.setText(a.getRoleLabel());

        // Color the avatar circle
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        try {
            circleBg.setColor(Color.parseColor(a.getAvatarColor()));
        } catch (Exception ignored) {
            circleBg.setColor(Color.parseColor("#4A90E2"));
        }
        tvInitials.setBackground(circleBg);

        btnRemove.setOnClickListener(v -> {
            editAttendees.remove(a);
            attendeesContainer.removeView(row);
        });

        attendeesContainer.addView(row);
    }

    // ═════════════════════════════════════════════════════════════
    // AGENDA
    // ═════════════════════════════════════════════════════════════

    private void setupAgendaSection() {
        View btnAddAgenda = findViewById(R.id.btnAddAgendaItem);
        if (btnAddAgenda != null) {
            btnAddAgenda.setOnClickListener(v -> {
                AgendaItem item = new AgendaItem("", 0);
                item.sortOrder = editAgendaItems.size();
                editAgendaItems.add(item);
                addAgendaEditRow(item);
                updateTotalAgendaDuration();
            });
        }
    }

    private void addAgendaEditRow(AgendaItem item) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_agenda_edit, agendaContainer, false);

        EditText etAgendaTitle    = row.findViewById(R.id.etAgendaTitle);
        EditText etAgendaDuration = row.findViewById(R.id.etAgendaDuration);
        EditText etAgendaPresenter = row.findViewById(R.id.etAgendaPresenter);
        TextView btnDelete        = row.findViewById(R.id.btnDeleteAgendaItem);

        etAgendaTitle.setText(item.title);
        if (item.durationMinutes > 0) etAgendaDuration.setText(String.valueOf(item.durationMinutes));
        etAgendaPresenter.setText(item.presenter);

        etAgendaTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { item.title = s.toString(); }
        });

        etAgendaDuration.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    item.durationMinutes = s.toString().isEmpty() ? 0 : Integer.parseInt(s.toString());
                } catch (NumberFormatException ignored) {
                    item.durationMinutes = 0;
                }
                updateTotalAgendaDuration();
            }
        });

        etAgendaPresenter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { item.presenter = s.toString(); }
        });

        btnDelete.setOnClickListener(v -> {
            editAgendaItems.remove(item);
            agendaContainer.removeView(row);
            updateTotalAgendaDuration();
        });

        agendaContainer.addView(row);
    }

    private void updateTotalAgendaDuration() {
        int total = 0;
        for (AgendaItem item : editAgendaItems) total += item.durationMinutes;
        if (tvTotalAgendaDuration == null) return;
        if (total > 0) {
            tvTotalAgendaDuration.setText("Total: " + AgendaItem.formatAgendaDuration(total));
            tvTotalAgendaDuration.setVisibility(View.VISIBLE);
        } else {
            tvTotalAgendaDuration.setVisibility(View.GONE);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // RECURRENCE
    // ═════════════════════════════════════════════════════════════

    private void setupRecurrenceRow() {
        View recurrenceRow = tvRecurrenceLabel != null ? tvRecurrenceLabel : null;
        if (tvRecurrenceLabel != null) {
            tvRecurrenceLabel.setOnClickListener(v -> showRecurrencePicker());
        }
        updateRecurrenceLabel();
    }

    private void showRecurrencePicker() {
        String[] options = {"None", "Daily", "Weekly", "Monthly"};
        String[] values  = {
            Meeting.RECURRENCE_NONE, Meeting.RECURRENCE_DAILY,
            Meeting.RECURRENCE_WEEKLY, Meeting.RECURRENCE_MONTHLY
        };
        new AlertDialog.Builder(this)
                .setTitle("Repeat")
                .setItems(options, (d, which) -> {
                    selectedRecurrence = values[which];
                    updateRecurrenceLabel();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateRecurrenceLabel() {
        if (tvRecurrenceLabel == null) return;
        if (Meeting.RECURRENCE_NONE.equals(selectedRecurrence)) {
            tvRecurrenceLabel.setText("🔁  No Repeat");
            tvRecurrenceLabel.setTextColor(COLOR_UNSELECTED_TEXT);
        } else {
            String label;
            switch (selectedRecurrence) {
                case Meeting.RECURRENCE_DAILY:   label = "Daily";   break;
                case Meeting.RECURRENCE_WEEKLY:  label = "Weekly";  break;
                case Meeting.RECURRENCE_MONTHLY: label = "Monthly"; break;
                default:                         label = "Custom";  break;
            }
            tvRecurrenceLabel.setText("🔁  " + label);
            tvRecurrenceLabel.setTextColor(Color.parseColor("#60A5FA"));
        }
    }

    // ═════════════════════════════════════════════════════════════
    // REMINDERS
    // ═════════════════════════════════════════════════════════════

    private void setupReminderSection() {
        View btnAddReminder = findViewById(R.id.btnAddReminder);
        if (btnAddReminder != null) {
            btnAddReminder.setOnClickListener(v -> showReminderPicker());
        }
        buildReminderChips();
    }

    private void showReminderPicker() {
        String[] options = {"At start", "15 minutes before", "1 hour before",
                            "1 day before", "Custom (minutes)"};
        long[]   values  = {0L, 15L, 60L, 1440L, -1L};

        new AlertDialog.Builder(this)
                .setTitle("Add Reminder")
                .setItems(options, (d, which) -> {
                    long offset = values[which];
                    if (offset == -1L) {
                        showCustomReminderDialog();
                    } else {
                        if (!editReminderOffsets.contains(offset)) {
                            editReminderOffsets.add(offset);
                            buildReminderChips();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomReminderDialog() {
        EditText et = new EditText(this);
        et.setHint("Minutes before");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setPadding(dp(20), dp(12), dp(20), dp(12));

        new AlertDialog.Builder(this)
                .setTitle("Custom reminder")
                .setView(et)
                .setPositiveButton("Add", (d, w) -> {
                    try {
                        long minutes = Long.parseLong(et.getText().toString().trim());
                        if (minutes >= 0 && !editReminderOffsets.contains(minutes)) {
                            editReminderOffsets.add(minutes);
                            buildReminderChips();
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildReminderChips() {
        if (remindersContainer == null) return;
        remindersContainer.removeAllViews();

        for (int i = 0; i < editReminderOffsets.size(); i++) {
            long offset = editReminderOffsets.get(i);

            String label;
            if (offset == 0)         label = "At start";
            else if (offset == 15)   label = "15 min before";
            else if (offset == 60)   label = "1 hr before";
            else if (offset == 1440) label = "1 day before";
            else                     label = offset + " min before";

            TextView chip = new TextView(this);
            chip.setText("🔔 " + label + "  ✕");
            chip.setTextSize(12);
            chip.setTextColor(Color.parseColor("#F59E0B"));
            chip.setPadding(dp(10), dp(5), dp(10), dp(5));
            chip.setBackgroundResource(R.drawable.task_chip_removable_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            lp.bottomMargin = dp(4);
            chip.setLayoutParams(lp);

            final long offsetToRemove = offset;
            chip.setOnClickListener(v -> {
                editReminderOffsets.remove(offsetToRemove);
                buildReminderChips();
            });

            remindersContainer.addView(chip);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // COLOR PICKER
    // ═════════════════════════════════════════════════════════════

    private void setupColorPicker() {
        updateColorDot();
        if (viewColorDot != null) {
            viewColorDot.setOnClickListener(v -> showColorPickerDialog());
        }
        if (tvColorHint != null) {
            tvColorHint.setOnClickListener(v -> showColorPickerDialog());
        }
    }

    private void showColorPickerDialog() {
        String[] colorNames = {
            "Blue", "Purple", "Green", "Deep Orange",
            "Red", "Orange", "Teal", "Violet"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick a Color");

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setPadding(dp(16), dp(16), dp(16), dp(8));

        for (int i = 0; i < COLOR_PALETTE.length; i++) {
            String hex = COLOR_PALETTE[i];
            View dot = new View(this);
            int size = dp(36);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            dot.setLayoutParams(lp);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            try { circle.setColor(Color.parseColor(hex)); }
            catch (Exception ignored) {}

            if (hex.equals(selectedColorHex)) {
                circle.setStroke(dp(3), Color.WHITE);
            }
            dot.setBackground(circle);

            final String chosenHex = hex;
            dot.setOnClickListener(v -> {
                selectedColorHex = chosenHex;
                updateColorDot();
            });

            grid.addView(dot);
        }

        builder.setView(grid)
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void updateColorDot() {
        if (viewColorDot == null) return;
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        try { circle.setColor(Color.parseColor(selectedColorHex)); }
        catch (Exception ignored) { circle.setColor(Color.parseColor(Meeting.DEFAULT_COLOR)); }
        viewColorDot.setBackground(circle);
    }

    // ═════════════════════════════════════════════════════════════
    // BUTTONS
    // ═════════════════════════════════════════════════════════════

    private void setupButtons() {
        // Buttons are wired in initViews() for save; back handled via onBackPressed()
    }

    private void testLink() {
        String link = etMeetingLink.getText().toString().trim();
        if (link.isEmpty()) {
            Toast.makeText(this, "No link entered", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!link.startsWith("http://") && !link.startsWith("https://")) {
                link = "https://" + link;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // SAVE
    // ═════════════════════════════════════════════════════════════

    private void saveMeeting() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }

        Meeting meeting = isNewMeeting ? new Meeting() : editingMeeting;

        meeting.title       = title;
        meeting.description = ""; // description not directly exposed in this layout
        meeting.type        = selectedType;
        meeting.platform    = selectedPlatform;
        meeting.meetingLink = etMeetingLink.getText().toString().trim();
        meeting.location    = etLocation.getText().toString().trim();
        meeting.notes       = etMeetingNotes.getText().toString().trim();
        meeting.colorHex    = selectedColorHex;
        meeting.recurrence  = selectedRecurrence;
        meeting.isStarred   = isNewMeeting ? false : editingMeeting.isStarred;

        // Build startDateTime and endDateTime from date + time selections
        meeting.startDateTime = buildDateTime(selectedDate, selectedStartTime);
        meeting.endDateTime   = buildDateTime(selectedDate, selectedEndTime);

        // Set attendees and agenda
        for (Attendee a : editAttendees) a.meetingId = meeting.id;
        meeting.attendees = new ArrayList<>(editAttendees);

        for (int i = 0; i < editAgendaItems.size(); i++) {
            editAgendaItems.get(i).meetingId  = meeting.id;
            editAgendaItems.get(i).sortOrder  = i;
        }
        meeting.agenda = new ArrayList<>(editAgendaItems);

        if (meeting.actionItems == null) meeting.actionItems = new ArrayList<>();

        meeting.reminderOffsets = new ArrayList<>(editReminderOffsets);

        repo.saveMeeting(meeting);
        MeetingNotificationHelper.scheduleMeetingReminders(this, meeting);

        Toast.makeText(this, isNewMeeting ? "Meeting created" : "Meeting updated",
                Toast.LENGTH_SHORT).show();

        setResult(RESULT_OK);
        finish();
    }

    /**
     * Combines a "yyyy-MM-dd" date string with a time-only millis value
     * into a full Unix timestamp.  Returns 0 if either is missing.
     */
    private long buildDateTime(String date, long timeMillis) {
        if (date == null || timeMillis == 0) return 0;
        try {
            String[] p = date.split("-");
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar t = Calendar.getInstance();
            t.setTimeInMillis(timeMillis);
            c.set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY));
            c.set(Calendar.MINUTE,      t.get(Calendar.MINUTE));
            c.set(Calendar.SECOND,      0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return timeMillis;
        }
    }

    // ═════════════════════════════════════════════════════════════
    // POPULATE FROM EXISTING MEETING
    // ═════════════════════════════════════════════════════════════

    private void populateFromMeeting(Meeting m) {
        etTitle.setText(m.title);
        etMeetingLink.setText(m.meetingLink != null ? m.meetingLink : "");
        etLocation.setText(m.location != null ? m.location : "");
        etMeetingNotes.setText(m.notes != null ? m.notes : "");

        selectedType      = m.type      != null ? m.type      : Meeting.TYPE_VIDEO_CALL;
        selectedPlatform  = m.platform  != null ? m.platform  : Meeting.PLATFORM_MEET;
        selectedColorHex  = m.colorHex  != null ? m.colorHex  : Meeting.DEFAULT_COLOR;
        selectedRecurrence = m.recurrence != null ? m.recurrence : Meeting.RECURRENCE_NONE;

        if (m.startDateTime > 0) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(m.startDateTime);
            selectedDate       = String.format(Locale.US, "%04d-%02d-%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            selectedStartTime  = m.startDateTime;
        }
        if (m.endDateTime > 0) {
            selectedEndTime = m.endDateTime;
        }

        editAttendees    = m.attendees   != null ? new ArrayList<>(m.attendees)   : new ArrayList<>();
        editAgendaItems  = m.agenda      != null ? new ArrayList<>(m.agenda)      : new ArrayList<>();
        editReminderOffsets = m.reminderOffsets != null
                ? new ArrayList<>(m.reminderOffsets) : new ArrayList<>();

        // Rebuild views
        for (Attendee a : editAttendees)    addAttendeeView(a);
        for (AgendaItem i : editAgendaItems) addAgendaEditRow(i);
        buildReminderChips();
        updateTotalAgendaDuration();
        updateColorDot();
        updateDateTimeChips();
        updateDurationCalc();
        updateRecurrenceLabel();

        // Update header and save button labels
        tvMeetingHeader.setText("Edit Meeting");
        TextView btnSave = findViewById(R.id.btnSaveMeeting);
        if (btnSave != null) btnSave.setText("Update Meeting");
        TextView btnSaveTop = findViewById(R.id.btnSaveMeetingTop);
        if (btnSaveTop != null) btnSaveTop.setText("Update");
    }

    // ═════════════════════════════════════════════════════════════
    // UNSAVED CHANGES DETECTION
    // ═════════════════════════════════════════════════════════════

    private boolean hasUnsavedChanges() {
        if (isNewMeeting) {
            return !etTitle.getText().toString().trim().isEmpty()
                    || !editAttendees.isEmpty()
                    || !editAgendaItems.isEmpty();
        }
        // For edit mode, always warn on back press
        return true;
    }

    // ═════════════════════════════════════════════════════════════
    // UTILITY
    // ═════════════════════════════════════════════════════════════

    private String formatTimestamp(long millis) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(millis);
            int h = c.get(Calendar.HOUR_OF_DAY);
            int min = c.get(Calendar.MINUTE);
            String ampm = h >= 12 ? "PM" : "AM";
            if (h > 12) h -= 12;
            if (h == 0) h = 12;
            return String.format(Locale.US, "%d:%02d %s", h, min, ampm);
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
