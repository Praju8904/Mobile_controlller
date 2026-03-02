package com.prajwal.myfirstapp.meetings;


import com.prajwal.myfirstapp.R;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Full meeting detail screen with:
 *   - Read-mode display of all meeting metadata and header gradient
 *   - Interactive agenda checkboxes with progress counter
 *   - Live meeting timer (count-up, red when over schedule)
 *   - Auto-saving meeting notes with "Saved" indicator
 *   - Action item checkboxes
 *   - Post-meeting summary section (shown when COMPLETED)
 *   - Overflow menu: Edit / Duplicate / Cancel / Complete / Share / Delete
 */
public class MeetingDetailActivity extends AppCompatActivity {

    private static final String TAG = "MeetingDetail";

    public static final String EXTRA_MEETING_ID = "meeting_id";
    public static final String EXTRA_OPEN_NOTES = "open_notes";

    private static final int AUTO_SAVE_DELAY_MS = 30_000;

    // ─── Data ────────────────────────────────────────────────────
    private MeetingRepository repo;
    private Meeting meeting;
    private String meetingId;

    // ─── Timer state ─────────────────────────────────────────────
    private boolean timerRunning = false;
    private long timerStartMs = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // ─── Notes auto-save ─────────────────────────────────────────
    private boolean notesDirty = false;
    private Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private Runnable autoSaveRunnable;

    // ─── Views ───────────────────────────────────────────────────
    private LinearLayout headerLayout;
    private TextView tvMeetingTitle;
    private ImageView ivMeetingTypeIcon;
    private TextView tvStatusBadge;
    private TextView btnStar;
    private ImageView btnOverflow;
    private TextView btnJoinMeeting;

    private TextView tvDateTimeRange;
    private TextView tvDuration;
    private TextView tvPlatformBadge;
    private TextView tvLocation;

    private LinearLayout attendeesRow;
    private TextView btnInvite;

    private TextView agendaProgressText;
    private LinearLayout agendaContainer;
    private TextView btnAddAgendaItem;

    private TextView tvLiveTimer;
    private TextView btnTimerControl;

    private EditText etMeetingNotes;
    private TextView tvNotesSaved;

    private LinearLayout actionItemsContainer;
    private TextView btnAddActionItem;

    private LinearLayout summarySection;
    private TextView tvSummaryStats;
    private TextView btnShareSummary;

    // Named TextWatcher so we can detach it around programmatic setText calls
    private final TextWatcher notesWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s) {
            notesDirty = true;
            tvNotesSaved.setVisibility(View.GONE);
            scheduleAutoSave();
        }
    };

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meeting_detail);

        repo = MeetingRepository.getInstance(this);
        meetingId = getIntent().getStringExtra(EXTRA_MEETING_ID);

        if (meetingId == null) {
            Toast.makeText(this, "Meeting not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadMeeting();

        if (getIntent().getBooleanExtra(EXTRA_OPEN_NOTES, false)) {
            etMeetingNotes.post(() -> etMeetingNotes.requestFocus());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        loadMeeting();
        // Auto-start timer if the meeting is currently happening
        if (meeting != null && meeting.isHappeningNow() && !timerRunning) {
            startTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNotesIfDirty();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (autoSaveRunnable != null) autoSaveHandler.removeCallbacks(autoSaveRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
        autoSaveHandler.removeCallbacksAndMessages(null);
    }

    // ═════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═════════════════════════════════════════════════════════════

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        headerLayout      = findViewById(R.id.headerLayout);
        tvMeetingTitle    = findViewById(R.id.tvMeetingTitle);
        ivMeetingTypeIcon = findViewById(R.id.ivMeetingTypeIcon);
        tvStatusBadge     = findViewById(R.id.tvStatusBadge);
        btnStar           = findViewById(R.id.btnStar);
        btnOverflow       = findViewById(R.id.btnOverflow);
        btnJoinMeeting    = findViewById(R.id.btnJoinMeeting);

        tvDateTimeRange  = findViewById(R.id.tvDateTimeRange);
        tvDuration       = findViewById(R.id.tvDuration);
        tvPlatformBadge  = findViewById(R.id.tvPlatformBadge);
        tvLocation       = findViewById(R.id.tvLocation);

        attendeesRow     = findViewById(R.id.attendeesRow);
        btnInvite        = findViewById(R.id.btnInvite);

        agendaProgressText = findViewById(R.id.agendaProgressText);
        agendaContainer    = findViewById(R.id.agendaContainer);
        btnAddAgendaItem   = findViewById(R.id.btnAddAgendaItem);

        tvLiveTimer      = findViewById(R.id.tvLiveTimer);
        btnTimerControl  = findViewById(R.id.btnTimerControl);

        etMeetingNotes   = findViewById(R.id.etMeetingNotes);
        tvNotesSaved     = findViewById(R.id.tvNotesSaved);

        actionItemsContainer = findViewById(R.id.actionItemsContainer);
        btnAddActionItem     = findViewById(R.id.btnAddActionItem);

        summarySection   = findViewById(R.id.summarySection);
        tvSummaryStats   = findViewById(R.id.tvSummaryStats);
        btnShareSummary  = findViewById(R.id.btnShareSummary);

        // Click listeners
        btnStar.setOnClickListener(v -> toggleStar());
        btnOverflow.setOnClickListener(this::showOverflowMenu);
        btnJoinMeeting.setOnClickListener(v -> joinMeeting());
        btnInvite.setOnClickListener(v -> showInviteDialog());
        btnAddAgendaItem.setOnClickListener(v -> showAddAgendaItemDialog());
        btnAddActionItem.setOnClickListener(v -> showAddActionItemDialog());
        btnTimerControl.setOnClickListener(v -> toggleTimer());
        btnShareSummary.setOnClickListener(v -> shareTextSummary());

        etMeetingNotes.addTextChangedListener(notesWatcher);
    }

    // ═════════════════════════════════════════════════════════════
    // LOAD & BIND
    // ═════════════════════════════════════════════════════════════

    private void loadMeeting() {
        meeting = repo.getMeeting(meetingId);
        if (meeting == null) {
            Toast.makeText(this, "Meeting not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bindMeetingData();
    }

    private void bindMeetingData() {
        // Title
        tvMeetingTitle.setText(meeting.title);

        // Header gradient using meeting color
        try {
            int color = Color.parseColor(
                    meeting.colorHex != null ? meeting.colorHex : Meeting.DEFAULT_COLOR);
            int dimColor = adjustAlpha(color, 0.30f);
            GradientDrawable grad = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{dimColor, Color.parseColor("#1E1E3A")});
            headerLayout.setBackground(grad);
        } catch (IllegalArgumentException ignored) {}

        // Type icon content description (📹/📞/🏢/🔵 conveyed as description)
        ivMeetingTypeIcon.setContentDescription(meeting.getTypeLabel());

        // Status badge
        tvStatusBadge.setText(meeting.getStatusLabel());
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dp(12));
        badgeBg.setColor(statusBadgeColor(meeting.status));
        tvStatusBadge.setBackground(badgeBg);

        // Star
        refreshStarButton();

        // Join Meeting button: visible only when link exists and meeting is imminent/live
        boolean showJoin = meeting.hasMeetingLink()
                && (meeting.isHappeningNow() || meeting.isStartingSoon());
        btnJoinMeeting.setVisibility(showJoin ? View.VISIBLE : View.GONE);

        // Date/time range
        tvDateTimeRange.setText(meeting.getFormattedDateRange());

        // Duration chip
        String durText = meeting.getDurationText();
        if (!durText.isEmpty()) {
            tvDuration.setText(durText);
            tvDuration.setVisibility(View.VISIBLE);
        } else {
            tvDuration.setVisibility(View.GONE);
        }

        // Platform badge
        if (meeting.platform != null && !meeting.platform.isEmpty()) {
            tvPlatformBadge.setText(meeting.platform);
            tvPlatformBadge.setVisibility(View.VISIBLE);
        } else {
            tvPlatformBadge.setVisibility(View.GONE);
        }

        // Location
        if (meeting.hasLocation()) {
            tvLocation.setText(meeting.location);
            tvLocation.setVisibility(View.VISIBLE);
        } else {
            tvLocation.setVisibility(View.GONE);
        }

        bindAttendees();
        bindAgenda();
        bindNotes();
        bindTimer();
        bindActionItems();

        // Post-meeting summary
        if (Meeting.STATUS_COMPLETED.equals(meeting.status)) {
            showPostMeetingSummary();
        } else {
            summarySection.setVisibility(View.GONE);
        }
    }

    // ─── Attendees ───────────────────────────────────────────────

    private void bindAttendees() {
        attendeesRow.removeAllViews();
        if (meeting.attendees == null || meeting.attendees.isEmpty()) return;

        int max = Math.min(meeting.attendees.size(), 6);
        for (int i = 0; i < max; i++) {
            Attendee a = meeting.attendees.get(i);
            TextView avatar = new TextView(this);
            avatar.setText(a.getInitials());
            avatar.setTextSize(12);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            avatar.setContentDescription(a.name);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            try {
                circle.setColor(Color.parseColor(a.getAvatarColor()));
            } catch (IllegalArgumentException ignored) {
                circle.setColor(Color.parseColor("#3949AB"));
            }
            avatar.setBackground(circle);

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMarginEnd(dp(4));
            avatar.setLayoutParams(lp);
            attendeesRow.addView(avatar);
        }

        // "+N more" overflow badge
        if (meeting.attendees.size() > 6) {
            int extra = meeting.attendees.size() - 6;
            TextView more = new TextView(this);
            more.setText("+" + extra);
            more.setTextSize(11);
            more.setTextColor(Color.parseColor("#94A3B8"));
            more.setGravity(Gravity.CENTER);

            GradientDrawable moreBg = new GradientDrawable();
            moreBg.setShape(GradientDrawable.OVAL);
            moreBg.setColor(Color.parseColor("#2D2D4E"));
            more.setBackground(moreBg);
            more.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
            attendeesRow.addView(more);
        }
    }

    // ─── Agenda ──────────────────────────────────────────────────

    private void bindAgenda() {
        agendaContainer.removeAllViews();
        updateAgendaProgress();

        if (meeting.agenda == null || meeting.agenda.isEmpty()) return;

        for (AgendaItem item : meeting.agenda) {
            View row = LayoutInflater.from(this).inflate(
                    R.layout.item_agenda_detail, agendaContainer, false);

            CheckBox cb          = row.findViewById(R.id.cbAgendaItem);
            TextView tvTitle     = row.findViewById(R.id.tvAgendaDetailTitle);
            TextView tvDur       = row.findViewById(R.id.tvAgendaDetailDuration);
            TextView tvPresenter = row.findViewById(R.id.tvAgendaDetailPresenter);

            tvTitle.setText(item.title);
            cb.setChecked(item.isCompleted);
            applyStrikethrough(tvTitle, item.isCompleted);

            String itemDur = item.getDurationText();
            if (!itemDur.isEmpty()) {
                tvDur.setText(itemDur);
                tvDur.setVisibility(View.VISIBLE);
            } else {
                tvDur.setVisibility(View.GONE);
            }

            if (item.hasPresenter()) {
                tvPresenter.setText(item.presenter);
                tvPresenter.setVisibility(View.VISIBLE);
            } else {
                tvPresenter.setVisibility(View.GONE);
            }

            cb.setOnCheckedChangeListener((btn, checked) -> {
                item.isCompleted = checked;
                applyStrikethrough(tvTitle, checked);
                repo.saveMeeting(meeting);
                updateAgendaProgress();
            });

            agendaContainer.addView(row);
        }
    }

    private void updateAgendaProgress() {
        if (meeting.agenda == null || meeting.agenda.isEmpty()) {
            agendaProgressText.setText("");
            return;
        }
        int done = 0;
        for (AgendaItem item : meeting.agenda) {
            if (item.isCompleted) done++;
        }
        agendaProgressText.setText(done + "/" + meeting.agenda.size() + " agenda items");
    }

    // ─── Notes ───────────────────────────────────────────────────

    private void bindNotes() {
        // Detach watcher while we pre-fill to avoid marking notes dirty
        etMeetingNotes.removeTextChangedListener(notesWatcher);
        etMeetingNotes.setText(meeting.notes != null ? meeting.notes : "");
        notesDirty = false;
        etMeetingNotes.addTextChangedListener(notesWatcher);
        tvNotesSaved.setVisibility(View.GONE);
    }

    private void scheduleAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveRunnable = () -> saveNotesIfDirty();
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS);
    }

    private void saveNotesIfDirty() {
        if (!notesDirty || meeting == null) return;
        meeting.notes = etMeetingNotes.getText().toString();
        repo.saveMeeting(meeting);
        notesDirty = false;
        tvNotesSaved.setVisibility(View.VISIBLE);
        tvNotesSaved.postDelayed(() -> tvNotesSaved.setVisibility(View.GONE), 2500);
    }

    // ─── Timer ───────────────────────────────────────────────────

    private void bindTimer() {
        if (!timerRunning) {
            tvLiveTimer.setText("00:00:00");
            tvLiveTimer.setTextColor(Color.parseColor("#F1F5F9"));
            btnTimerControl.setText("▶");
            btnTimerControl.setTextColor(Color.parseColor("#66BB6A"));
        }
    }

    private void toggleTimer() {
        if (timerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        timerRunning = true;
        // If the meeting is live, count from its actual start time
        if (meeting.isHappeningNow() && meeting.startDateTime > 0) {
            timerStartMs = meeting.startDateTime;
        } else if (timerStartMs == 0) {
            timerStartMs = System.currentTimeMillis();
        }
        btnTimerControl.setText("⏸");
        btnTimerControl.setTextColor(Color.parseColor("#F59E0B"));

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!timerRunning) return;
                long elapsed  = System.currentTimeMillis() - timerStartMs;
                int totalSecs = (int) (elapsed / 1000);
                int hrs  = totalSecs / 3600;
                int mins = (totalSecs % 3600) / 60;
                int secs = totalSecs % 60;
                tvLiveTimer.setText(
                        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs));

                // Red when meeting has run over its scheduled end time
                boolean overTime = meeting.endDateTime > 0
                        && System.currentTimeMillis() > meeting.endDateTime;
                tvLiveTimer.setTextColor(
                        Color.parseColor(overTime ? "#EF4444" : "#F1F5F9"));

                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void pauseTimer() {
        timerRunning = false;
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        btnTimerControl.setText("▶");
        btnTimerControl.setTextColor(Color.parseColor("#66BB6A"));
        // Preserve timerStartMs so that resuming continues the same count
    }

    // ─── Action Items ────────────────────────────────────────────

    private void bindActionItems() {
        actionItemsContainer.removeAllViews();
        if (meeting.actionItems == null || meeting.actionItems.isEmpty()) return;

        for (ActionItem ai : meeting.actionItems) {
            View row = LayoutInflater.from(this).inflate(
                    R.layout.item_action_item, actionItemsContainer, false);

            CheckBox  cb         = row.findViewById(R.id.cbActionItem);
            TextView  tvTitle    = row.findViewById(R.id.tvActionItemTitle);
            TextView  tvAssignee = row.findViewById(R.id.tvActionItemAssignee);
            TextView  tvDueDate  = row.findViewById(R.id.tvActionItemDueDate);
            ImageView ivLink     = row.findViewById(R.id.ivActionItemTaskLink);

            tvTitle.setText(ai.title);
            cb.setChecked(ai.isCompleted);
            applyStrikethrough(tvTitle, ai.isCompleted);

            tvAssignee.setText(ai.hasAssignee() ? "👤 " + ai.assigneeName : "Unassigned");

            if (ai.hasDueDate()) {
                tvDueDate.setText("📅 " + ai.dueDate);
                tvDueDate.setVisibility(View.VISIBLE);
            } else {
                tvDueDate.setVisibility(View.GONE);
            }

            ivLink.setVisibility(ai.isLinkedToTask() ? View.VISIBLE : View.GONE);

            cb.setOnCheckedChangeListener((btn, checked) -> {
                ai.isCompleted = checked;
                applyStrikethrough(tvTitle, checked);
                repo.saveMeeting(meeting);
            });

            actionItemsContainer.addView(row);
        }
    }

    // ─── Post-Meeting Summary ────────────────────────────────────

    private void showPostMeetingSummary() {
        summarySection.setVisibility(View.VISIBLE);

        String durText       = meeting.getDurationText();
        int agendaTotal      = meeting.agenda      != null ? meeting.agenda.size()      : 0;
        int actionItemsTotal = meeting.actionItems != null ? meeting.actionItems.size() : 0;
        int agendaDone = 0;
        if (meeting.agenda != null) {
            for (AgendaItem item : meeting.agenda) {
                if (item.isCompleted) agendaDone++;
            }
        }

        StringBuilder stats = new StringBuilder();
        if (!durText.isEmpty()) {
            stats.append("Duration: ").append(durText);
        }
        if (agendaTotal > 0) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append("Agenda: ").append(agendaDone)
                 .append("/").append(agendaTotal).append(" covered");
        }
        if (actionItemsTotal > 0) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append("Action items: ").append(actionItemsTotal).append(" created");
        }
        tvSummaryStats.setText(stats.toString());
    }

    // ═════════════════════════════════════════════════════════════
    // USER ACTIONS
    // ═════════════════════════════════════════════════════════════

    private void toggleStar() {
        meeting.isStarred = !meeting.isStarred;
        repo.saveMeeting(meeting);
        refreshStarButton();
    }

    private void refreshStarButton() {
        btnStar.setText(meeting.isStarred ? "⭐" : "☆");
        btnStar.setTextColor(meeting.isStarred
                ? Color.parseColor("#FBBF24")
                : Color.parseColor("#4B5563"));
    }

    private void joinMeeting() {
        if (Meeting.TYPE_VIDEO_CALL.equals(meeting.type) && meeting.hasMeetingLink()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(meeting.meetingLink)));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        } else if (Meeting.TYPE_PHONE_CALL.equals(meeting.type)) {
            String number = meeting.meetingLink != null
                    ? meeting.meetingLink.replaceAll("[^+0-9]", "") : "";
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open dialer", Toast.LENGTH_SHORT).show();
            }
        } else if (Meeting.TYPE_IN_PERSON.equals(meeting.type)) {
            showLocationDialog();
        } else if (meeting.hasMeetingLink()) {
            new AlertDialog.Builder(this)
                    .setTitle("Meeting Link")
                    .setMessage(meeting.meetingLink)
                    .setPositiveButton("Copy", (d, w) ->
                            copyToClipboard("Meeting Link", meeting.meetingLink))
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private void showLocationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📍 Location")
                .setMessage(meeting.location)
                .setPositiveButton("Copy", (d, w) ->
                        copyToClipboard("Location", meeting.location))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showInviteDialog() {
        StringBuilder msg = new StringBuilder();
        msg.append("You're invited to: ").append(meeting.title).append("\n");
        msg.append("📅 ").append(meeting.getFormattedDateRange()).append("\n");
        if (meeting.hasMeetingLink()) {
            msg.append("🔗 ").append(meeting.meetingLink).append("\n");
        }
        if (meeting.hasLocation()) {
            msg.append("📍 ").append(meeting.location).append("\n");
        }
        final String inviteText = msg.toString();

        new AlertDialog.Builder(this)
                .setTitle("Invite to Meeting")
                .setMessage(inviteText)
                .setPositiveButton("Share Invite", (d, w) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                            "Meeting Invite: " + meeting.title);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, inviteText);
                    startActivity(Intent.createChooser(shareIntent, "Send Invite"));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showAddAgendaItemDialog() {
        EditText input = makeDialogEditText("Agenda item title");

        new AlertDialog.Builder(this)
                .setTitle("Add Agenda Item")
                .setView(wrapInPadding(input))
                .setPositiveButton("Add", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    // 0 duration = unscheduled; user can edit later
                    AgendaItem item = new AgendaItem(title, 0);
                    item.meetingId  = meeting.id;
                    item.sortOrder  = meeting.agenda != null ? meeting.agenda.size() : 0;
                    if (meeting.agenda == null) meeting.agenda = new ArrayList<>();
                    meeting.agenda.add(item);
                    repo.saveMeeting(meeting);
                    bindAgenda();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddActionItemDialog() {
        EditText etTitle    = makeDialogEditText("Action item title *");
        EditText etAssignee = makeDialogEditText("Assignee name");
        EditText etDueDate  = makeDialogEditText("Due date (yyyy-MM-dd)");
        etDueDate.setInputType(InputType.TYPE_CLASS_DATETIME);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        etTitle.setLayoutParams(lp);
        etAssignee.setLayoutParams(lp);
        etDueDate.setLayoutParams(lp);

        container.addView(etTitle);
        container.addView(etAssignee);
        container.addView(etDueDate);

        new AlertDialog.Builder(this)
                .setTitle("Add Action Item")
                .setView(container)
                .setPositiveButton("Add", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String assignee = etAssignee.getText().toString().trim();
                    String dueDate  = etDueDate.getText().toString().trim();
                    ActionItem ai = new ActionItem(
                            title,
                            assignee.isEmpty()  ? null : assignee,
                            dueDate.isEmpty()   ? null : dueDate);
                    ai.meetingId = meeting.id;
                    if (meeting.actionItems == null) meeting.actionItems = new ArrayList<>();
                    meeting.actionItems.add(ai);
                    repo.saveMeeting(meeting);
                    bindActionItems();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Overflow Menu ───────────────────────────────────────────

    private void showOverflowMenu(View anchor) {
        final String[] items = {
                "✏️  Edit",
                "📋  Duplicate",
                "❌  Cancel Meeting",
                "✅  Complete Meeting",
                "📤  Share",
                "🗑️  Delete"
        };
        new AlertDialog.Builder(this)
                .setTitle("Meeting Options")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: openEditMeeting();  break;
                        case 1: duplicateMeeting(); break;
                        case 2: cancelMeeting();    break;
                        case 3: completeMeeting();  break;
                        case 4: shareTextSummary(); break;
                        case 5: confirmDelete();    break;
                    }
                })
                .show();
    }

    private void openEditMeeting() {
        Intent intent = new Intent(this, CreateMeetingActivity.class);
        intent.putExtra(CreateMeetingActivity.EXTRA_MEETING_ID, meeting.id);
        startActivity(intent);
    }

    private void duplicateMeeting() {
        Meeting copy = meeting.duplicate();
        repo.saveMeeting(copy);
        Toast.makeText(this, "Duplicated", Toast.LENGTH_SHORT).show();
    }

    private void cancelMeeting() {
        meeting.status = Meeting.STATUS_CANCELLED;
        repo.saveMeeting(meeting);
        bindMeetingData();
        Toast.makeText(this, "Meeting cancelled", Toast.LENGTH_SHORT).show();
    }

    private void completeMeeting() {
        saveNotesIfDirty();
        meeting.status = Meeting.STATUS_COMPLETED;
        repo.saveMeeting(meeting);
        bindMeetingData();
        showPostMeetingSummary();
        Toast.makeText(this, "Meeting marked complete", Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Meeting")
                .setMessage("Delete \"" + meeting.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    timerHandler.removeCallbacksAndMessages(null);
                    autoSaveHandler.removeCallbacksAndMessages(null);
                    repo.deleteMeeting(meeting.id);
                    Toast.makeText(this, "Meeting deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Share / Summary ─────────────────────────────────────────

    private void shareTextSummary() {
        String text = buildShareText();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, meeting.title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Share Meeting"));
    }

    private String buildShareText() {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(meeting.title).append("\n");
        sb.append("Type: ").append(meeting.getTypeLabel()).append("\n");
        sb.append("When: ").append(meeting.getFormattedDateRange()).append("\n");

        if (!meeting.getDurationText().isEmpty()) {
            sb.append("Duration: ").append(meeting.getDurationText()).append("\n");
        }
        if (meeting.platform != null && !meeting.platform.isEmpty()) {
            sb.append("Platform: ").append(meeting.platform).append("\n");
        }
        if (meeting.hasMeetingLink()) {
            sb.append("Link: ").append(meeting.meetingLink).append("\n");
        }
        if (meeting.hasLocation()) {
            sb.append("Location: ").append(meeting.location).append("\n");
        }

        // Attendees
        if (meeting.hasAttendees()) {
            sb.append("\n👥 Attendees:\n");
            for (Attendee a : meeting.attendees) {
                sb.append("  • ").append(a.name);
                if (a.email != null && !a.email.isEmpty()) {
                    sb.append(" (").append(a.email).append(")");
                }
                sb.append("\n");
            }
        }

        // Agenda
        if (meeting.hasAgenda()) {
            sb.append("\n📋 Agenda:\n");
            for (AgendaItem item : meeting.agenda) {
                sb.append(item.isCompleted ? "  ✅ " : "  ☐ ").append(item.title);
                String dur = item.getDurationText();
                if (!dur.isEmpty()) sb.append(" (").append(dur).append(")");
                sb.append("\n");
            }
        }

        // Notes excerpt (up to 300 chars)
        if (meeting.notes != null && !meeting.notes.trim().isEmpty()) {
            sb.append("\n📝 Notes:\n");
            String notes = meeting.notes.trim();
            if (notes.length() > 300) notes = notes.substring(0, 300) + "…";
            sb.append(notes).append("\n");
        }

        // Action items
        if (meeting.hasActionItems()) {
            sb.append("\n✅ Action Items:\n");
            for (ActionItem ai : meeting.actionItems) {
                sb.append(ai.isCompleted ? "  ✅ " : "  ☐ ").append(ai.title);
                if (ai.hasAssignee()) sb.append(" → ").append(ai.assigneeName);
                if (ai.hasDueDate())  sb.append(" (due ").append(ai.dueDate).append(")");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════

    private void applyStrikethrough(TextView tv, boolean strike) {
        if (strike) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(Color.parseColor("#4B5563"));
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(Color.parseColor("#F1F5F9"));
        }
    }

    private int statusBadgeColor(String status) {
        if (status == null) return Color.parseColor("#3B82F6");
        switch (status) {
            case Meeting.STATUS_IN_PROGRESS: return Color.parseColor("#22C55E");
            case Meeting.STATUS_COMPLETED:   return Color.parseColor("#64748B");
            case Meeting.STATUS_CANCELLED:   return Color.parseColor("#EF4444");
            case Meeting.STATUS_POSTPONED:   return Color.parseColor("#F59E0B");
            default:                         return Color.parseColor("#3B82F6");
        }
    }

    /** Returns a color with adjusted alpha (factor in [0, 1]). */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    /** Creates a styled EditText for use inside AlertDialogs. */
    private EditText makeDialogEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.parseColor("#F1F5F9"));
        et.setHintTextColor(Color.parseColor("#4A4A6A"));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return et;
    }

    /** Wraps a view in a LinearLayout with standard dialog padding. */
    private LinearLayout wrapInPadding(View child) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));
        container.addView(child);
        return container;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
