package com.prajwal.myfirstapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full task detail screen with:
 *   - Read-mode display of all task metadata
 *   - Interactive subtask checkboxes w/ progress
 *   - Focus timer (start/stop, session history)
 *   - Reminders list, activity log
 *   - Edit / Delete / Complete actions
 */
public class TaskDetailActivity extends AppCompatActivity
        implements TaskEditorSheet.TaskEditorListener {

    private static final String TAG = "TaskDetail";
    public static final String EXTRA_TASK_ID = "task_id";

    // ─── Data ────────────────────────────────────────────────────
    private TaskRepository repo;
    private Task task;
    private String taskId;

    // ─── Timer state ─────────────────────────────────────────────
    private boolean timerRunning = false;
    private long timerStartMs = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // ─── Views ───────────────────────────────────────────────────
    private View viewPriorityStrip;
    private TextView tvStatus, tvPriority, tvRecurringBadge, btnStar;
    private TextView tvTitle, tvDescription;
    private TextView tvCategory, tvDueDate, tvRecurrence;
    private LinearLayout detailCategoryRow, detailDueDateRow, detailRecurrenceRow;
    private LinearLayout detailTagsRow, detailTagsContainer;
    private LinearLayout detailDurationRow;
    private TextView tvDuration;
    private LinearLayout detailSubtasksSection, detailSubtasksContainer;
    private TextView tvSubtaskProgress;
    private View viewSubtaskProgressFill;
    private TextView tvTimerDisplay, tvTotalTimeLogged;
    private TextView btnStartTimer, btnStopTimer;
    private LinearLayout timerSessionsContainer;
    private LinearLayout detailAttachmentsSection, detailAttachmentsContainer;
    private LinearLayout detailNotesSection;
    private TextView tvNotes;
    private LinearLayout detailRemindersSection, detailRemindersContainer;
    private TextView tvCreatedAt, tvUpdatedAt, tvCompletedAt;
    private TextView btnComplete, btnSkipRecurrence;

    // ═════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        repo = new TaskRepository(this);
        taskId = getIntent().getStringExtra(EXTRA_TASK_ID);

        if (taskId == null) {
            Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadTask();
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        loadTask();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop timer if running (auto-save session)
        if (timerRunning) {
            stopTimer();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═════════════════════════════════════════════════════════════

    private void initViews() {
        // Header actions
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditTask).setOnClickListener(v -> openEditor());
        findViewById(R.id.btnDeleteTask).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnMoreOptions).setOnClickListener(this::showMoreOptions);

        viewPriorityStrip = findViewById(R.id.viewDetailPriorityStrip);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvPriority = findViewById(R.id.tvDetailPriority);
        tvRecurringBadge = findViewById(R.id.tvDetailRecurringBadge);
        btnStar = findViewById(R.id.btnDetailStar);
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvDescription = findViewById(R.id.tvDetailDescription);

        // Metadata
        detailCategoryRow = findViewById(R.id.detailCategoryRow);
        tvCategory = findViewById(R.id.tvDetailCategory);
        detailDueDateRow = findViewById(R.id.detailDueDateRow);
        tvDueDate = findViewById(R.id.tvDetailDueDate);
        detailRecurrenceRow = findViewById(R.id.detailRecurrenceRow);
        tvRecurrence = findViewById(R.id.tvDetailRecurrence);
        detailTagsRow = findViewById(R.id.detailTagsRow);
        detailTagsContainer = findViewById(R.id.detailTagsContainer);
        detailDurationRow = findViewById(R.id.detailDurationRow);
        tvDuration = findViewById(R.id.tvDetailDuration);

        // Subtasks
        detailSubtasksSection = findViewById(R.id.detailSubtasksSection);
        detailSubtasksContainer = findViewById(R.id.detailSubtasksContainer);
        tvSubtaskProgress = findViewById(R.id.tvDetailSubtaskProgress);
        viewSubtaskProgressFill = findViewById(R.id.viewDetailSubtaskProgressFill);

        // Timer
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        tvTotalTimeLogged = findViewById(R.id.tvTotalTimeLogged);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnStopTimer = findViewById(R.id.btnStopTimer);
        timerSessionsContainer = findViewById(R.id.timerSessionsContainer);

        btnStartTimer.setOnClickListener(v -> startTimer());
        btnStopTimer.setOnClickListener(v -> stopTimer());

        // Attachments
        detailAttachmentsSection = findViewById(R.id.detailAttachmentsSection);
        detailAttachmentsContainer = findViewById(R.id.detailAttachmentsContainer);

        // Notes
        detailNotesSection = findViewById(R.id.detailNotesSection);
        tvNotes = findViewById(R.id.tvDetailNotes);

        // Reminders
        detailRemindersSection = findViewById(R.id.detailRemindersSection);
        detailRemindersContainer = findViewById(R.id.detailRemindersContainer);

        // Activity log
        tvCreatedAt = findViewById(R.id.tvDetailCreatedAt);
        tvUpdatedAt = findViewById(R.id.tvDetailUpdatedAt);
        tvCompletedAt = findViewById(R.id.tvDetailCompletedAt);

        // Bottom bar
        btnComplete = findViewById(R.id.btnDetailComplete);
        btnSkipRecurrence = findViewById(R.id.btnSkipRecurrence);

        btnComplete.setOnClickListener(v -> toggleComplete());
        btnSkipRecurrence.setOnClickListener(v -> skipRecurrence());

        btnStar.setOnClickListener(v -> toggleStar());
    }

    // ═════════════════════════════════════════════════════════════
    // LOAD & DISPLAY
    // ═════════════════════════════════════════════════════════════

    private void loadTask() {
        task = repo.getTaskById(taskId);
        if (task == null) {
            Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        displayTask();
    }

    private void displayTask() {
        // Priority strip
        viewPriorityStrip.setBackgroundColor(task.getPriorityColor());

        // Status badge
        tvStatus.setText(getStatusEmoji(task.status) + " " + getStatusLabel(task.status));
        tvStatus.setVisibility(View.VISIBLE);

        // Priority badge
        String priLabel = task.getPriorityLabel();
        if (!priLabel.isEmpty() && !Task.PRIORITY_NONE.equals(task.priority)) {
            tvPriority.setText(priLabel);
            tvPriority.setVisibility(View.VISIBLE);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(12));
            bg.setColor(task.getPriorityColor());
            tvPriority.setBackground(bg);
        } else {
            tvPriority.setVisibility(View.GONE);
        }

        // Recurring badge
        tvRecurringBadge.setVisibility(task.isRecurring() ? View.VISIBLE : View.GONE);

        // Star
        btnStar.setText(task.isStarred ? "★" : "☆");
        btnStar.setTextColor(task.isStarred ? Color.parseColor("#FBBF24") : Color.parseColor("#4B5563"));

        // Title
        tvTitle.setText(task.title);
        if (task.isCompleted()) {
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(Color.parseColor("#6B7280"));
        } else {
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(Color.parseColor("#F1F5F9"));
        }

        // Description
        if (task.description != null && !task.description.isEmpty()) {
            tvDescription.setText(task.description);
            tvDescription.setVisibility(View.VISIBLE);
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        // Category
        if (task.category != null && !task.category.isEmpty()) {
            String icon = TaskCategory.getIconForCategory(task.category);
            tvCategory.setText(icon + "  " + task.category);
            detailCategoryRow.setVisibility(View.VISIBLE);
        } else {
            detailCategoryRow.setVisibility(View.GONE);
        }

        // Due date
        if (task.hasDueDate()) {
            String dateText = task.getFormattedDueDate();
            tvDueDate.setText(dateText);
            if (task.isOverdue()) {
                tvDueDate.setTextColor(Color.parseColor("#EF4444"));
            } else if (task.isDueToday()) {
                tvDueDate.setTextColor(Color.parseColor("#60A5FA"));
            } else {
                tvDueDate.setTextColor(Color.parseColor("#F1F5F9"));
            }
            detailDueDateRow.setVisibility(View.VISIBLE);
        } else {
            detailDueDateRow.setVisibility(View.GONE);
        }

        // Recurrence
        if (task.isRecurring()) {
            tvRecurrence.setText(task.getRecurrenceLabel());
            detailRecurrenceRow.setVisibility(View.VISIBLE);
        } else {
            detailRecurrenceRow.setVisibility(View.GONE);
        }

        // Tags
        if (task.tags != null && !task.tags.isEmpty()) {
            detailTagsRow.setVisibility(View.VISIBLE);
            detailTagsContainer.removeAllViews();
            for (String tag : task.tags) {
                TextView chip = new TextView(this);
                chip.setText(tag);
                chip.setTextSize(11);
                chip.setTextColor(Color.parseColor("#60A5FA"));
                chip.setPadding(dp(8), dp(3), dp(8), dp(3));
                chip.setBackgroundResource(R.drawable.task_chip_removable_bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(6));
                chip.setLayoutParams(lp);
                detailTagsContainer.addView(chip);
            }
        } else {
            detailTagsRow.setVisibility(View.GONE);
        }

        // Duration
        if (task.estimatedDuration > 0 || task.actualDuration > 0) {
            StringBuilder durationText = new StringBuilder();
            if (task.estimatedDuration > 0) {
                durationText.append("Est: ").append(task.getEstimatedDurationText());
            }
            if (task.actualDuration > 0) {
                if (durationText.length() > 0) durationText.append("  ·  ");
                durationText.append("Actual: ").append(task.getActualDurationText());
            }
            tvDuration.setText(durationText.toString());
            detailDurationRow.setVisibility(View.VISIBLE);
        } else {
            detailDurationRow.setVisibility(View.GONE);
        }

        // Subtasks
        displaySubtasks();

        // Timer
        displayTimerSection();

        // Attachments
        if (task.attachments != null && !task.attachments.isEmpty()) {
            detailAttachmentsSection.setVisibility(View.VISIBLE);
            // Simple text display of attachment paths
            detailAttachmentsContainer.removeAllViews();
            for (String path : task.attachments) {
                TextView tv = new TextView(this);
                tv.setText("📄  " + getFileName(path));
                tv.setTextColor(Color.parseColor("#60A5FA"));
                tv.setTextSize(13);
                tv.setPadding(dp(8), dp(4), dp(8), dp(4));
                detailAttachmentsContainer.addView(tv);
            }
        } else {
            detailAttachmentsSection.setVisibility(View.GONE);
        }

        // Notes
        if (task.notes != null && !task.notes.isEmpty()) {
            detailNotesSection.setVisibility(View.VISIBLE);
            tvNotes.setText(task.notes);
        } else {
            detailNotesSection.setVisibility(View.GONE);
        }

        // Reminders
        if (task.reminderDateTimes != null && !task.reminderDateTimes.isEmpty()) {
            detailRemindersSection.setVisibility(View.VISIBLE);
            detailRemindersContainer.removeAllViews();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US);
            for (Long ts : task.reminderDateTimes) {
                TextView tv = new TextView(this);
                boolean past = ts < System.currentTimeMillis();
                tv.setText((past ? "✓  " : "🔔  ") + sdf.format(new Date(ts)));
                tv.setTextColor(past ? Color.parseColor("#4B5563") : Color.parseColor("#F59E0B"));
                tv.setTextSize(13);
                tv.setPadding(0, dp(2), 0, dp(2));
                detailRemindersContainer.addView(tv);
            }
        } else {
            detailRemindersSection.setVisibility(View.GONE);
        }

        // Activity log
        SimpleDateFormat actSdf = new SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.US);
        tvCreatedAt.setText("Created:  " + actSdf.format(new Date(task.createdAt)));
        tvUpdatedAt.setText("Updated:  " + actSdf.format(new Date(task.updatedAt)));
        if (task.completedAt > 0) {
            tvCompletedAt.setText("Completed:  " + actSdf.format(new Date(task.completedAt)));
            tvCompletedAt.setVisibility(View.VISIBLE);
        } else {
            tvCompletedAt.setVisibility(View.GONE);
        }

        // Bottom bar
        if (task.isCompleted()) {
            btnComplete.setText("↩  Mark Incomplete");
            btnComplete.setTextColor(Color.parseColor("#F1F5F9"));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(14));
            bg.setColor(Color.parseColor("#374151"));
            btnComplete.setBackground(bg);
        } else {
            btnComplete.setText("✓  Mark Complete");
            btnComplete.setTextColor(Color.parseColor("#0A0E21"));
            btnComplete.setBackgroundResource(R.drawable.task_save_btn_bg);
        }

        btnSkipRecurrence.setVisibility(task.isRecurring() ? View.VISIBLE : View.GONE);
    }

    // ─── Subtasks Display ────────────────────────────────────────

    private void displaySubtasks() {
        if (!task.hasSubtasks()) {
            detailSubtasksSection.setVisibility(View.GONE);
            return;
        }

        detailSubtasksSection.setVisibility(View.VISIBLE);
        int completed = task.getSubtaskCompletedCount();
        int total = task.getSubtaskTotalCount();
        tvSubtaskProgress.setText(completed + " / " + total);

        // Progress bar
        float progress = task.getSubtaskProgress();
        viewSubtaskProgressFill.post(() -> {
            ViewGroup parent = (ViewGroup) viewSubtaskProgressFill.getParent();
            int totalWidth = parent.getWidth();
            ViewGroup.LayoutParams lp = viewSubtaskProgressFill.getLayoutParams();
            lp.width = (int) (totalWidth * progress);
            viewSubtaskProgressFill.setLayoutParams(lp);
        });

        // Subtask items
        detailSubtasksContainer.removeAllViews();
        for (SubTask sub : task.subtasks) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_subtask_edit, detailSubtasksContainer, false);
            CheckBox cb = row.findViewById(R.id.cbSubtask);
            TextView tvSubTitle = row.findViewById(R.id.etSubtaskTitle);
            ImageView btnDelete = row.findViewById(R.id.btnDeleteSubtask);

            // Use as read-only interactive checkbox
            tvSubTitle.setFocusable(false);
            tvSubTitle.setClickable(false);
            tvSubTitle.setText(sub.title);
            btnDelete.setVisibility(View.GONE);

            cb.setChecked(sub.isCompleted);
            if (sub.isCompleted) {
                tvSubTitle.setPaintFlags(tvSubTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvSubTitle.setTextColor(Color.parseColor("#4B5563"));
            } else {
                tvSubTitle.setPaintFlags(tvSubTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                tvSubTitle.setTextColor(Color.parseColor("#F1F5F9"));
            }

            cb.setOnCheckedChangeListener((btn, checked) -> {
                sub.isCompleted = checked;
                repo.updateTask(task);
                displaySubtasks(); // refresh progress

                // Check if all done
                if (task.getSubtaskCompletedCount() == task.getSubtaskTotalCount()) {
                    promptAllSubtasksDone();
                }
            });

            detailSubtasksContainer.addView(row);
        }
    }

    private void promptAllSubtasksDone() {
        if (task.isCompleted()) return;
        new AlertDialog.Builder(this)
                .setTitle("All subtasks complete!")
                .setMessage("Would you like to mark the parent task as complete too?")
                .setPositiveButton("Yes", (d, w) -> {
                    repo.completeTask(task.id);
                    loadTask();
                })
                .setNegativeButton("Not yet", null)
                .show();
    }

    // ─── Timer ───────────────────────────────────────────────────

    private void displayTimerSection() {
        // Total logged
        int totalMins = task.getTotalTimerMinutes();
        if (totalMins > 0) {
            tvTotalTimeLogged.setText("Total logged: " + task.getTotalTimerText());
            tvTotalTimeLogged.setVisibility(View.VISIBLE);
        } else {
            tvTotalTimeLogged.setText("No sessions yet");
            tvTotalTimeLogged.setVisibility(View.VISIBLE);
        }

        // Session history
        buildTimerSessionViews();

        // Timer display
        if (!timerRunning) {
            tvTimerDisplay.setText("00:00:00");
        }
    }

    private void startTimer() {
        timerRunning = true;
        timerStartMs = System.currentTimeMillis();
        btnStartTimer.setVisibility(View.GONE);
        btnStopTimer.setVisibility(View.VISIBLE);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!timerRunning) return;
                long elapsed = System.currentTimeMillis() - timerStartMs;
                int secs = (int) (elapsed / 1000);
                int hrs = secs / 3600;
                int mins = (secs % 3600) / 60;
                int sec = secs % 60;
                tvTimerDisplay.setText(String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, sec));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);

        Toast.makeText(this, "Timer started", Toast.LENGTH_SHORT).show();
    }

    private void stopTimer() {
        if (!timerRunning) return;
        timerRunning = false;
        long endMs = System.currentTimeMillis();
        timerHandler.removeCallbacks(timerRunnable);

        btnStartTimer.setVisibility(View.VISIBLE);
        btnStopTimer.setVisibility(View.GONE);

        // Save session
        long duration = endMs - timerStartMs;
        if (duration >= 5000) { // Only save if > 5 seconds
            task.addTimerSession(timerStartMs, endMs);
            repo.updateTask(task);
            displayTimerSection();
            Toast.makeText(this, "Session saved!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Session too short (< 5s)", Toast.LENGTH_SHORT).show();
            tvTimerDisplay.setText("00:00:00");
        }
    }

    private void buildTimerSessionViews() {
        timerSessionsContainer.removeAllViews();
        if (task.timerSessions == null || task.timerSessions.isEmpty()) return;

        SimpleDateFormat dateFmt = new SimpleDateFormat("MMM dd", Locale.US);
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);

        // Show most recent first, max 10
        int count = Math.min(task.timerSessions.size(), 10);
        for (int i = task.timerSessions.size() - 1; i >= task.timerSessions.size() - count; i--) {
            long[] session = task.timerSessions.get(i);
            if (session.length < 2) continue;

            View row = LayoutInflater.from(this).inflate(R.layout.item_time_session, timerSessionsContainer, false);
            TextView tvDate = row.findViewById(R.id.tvSessionDate);
            TextView tvRange = row.findViewById(R.id.tvSessionTimeRange);
            TextView tvDur = row.findViewById(R.id.tvSessionDuration);

            tvDate.setText(dateFmt.format(new Date(session[0])));
            tvRange.setText(timeFmt.format(new Date(session[0])) + " – " + timeFmt.format(new Date(session[1])));

            long durationMs = session[1] - session[0];
            int mins = (int) (durationMs / 60000);
            if (mins >= 60) {
                tvDur.setText((mins / 60) + "h " + (mins % 60) + "m");
            } else {
                tvDur.setText(mins + " min");
            }

            timerSessionsContainer.addView(row);
        }
    }

    // ─── Actions ─────────────────────────────────────────────────

    private void toggleComplete() {
        if (task.isCompleted()) {
            repo.uncompleteTask(task.id);
        } else {
            repo.completeTask(task.id);
            // Handle recurring: create next occurrence
            if (task.isRecurring()) {
                Task next = repo.createNextRecurrence(task.id);
                if (next != null) {
                    Toast.makeText(this, "Next occurrence created", Toast.LENGTH_SHORT).show();
                }
            }
        }
        // Sync to PC
        TaskManagerActivity mgr = TaskManagerActivity.getInstance();
        if (mgr != null) {
            try {
                ConnectionManager cm = new ConnectionManager(getIntent().getStringExtra("server_ip"));
                if (task.isCompleted()) {
                    // Was completed, now uncompleted
                    cm.sendCommand("TASK_UNCOMPLETE:" + task.id);
                } else {
                    cm.sendCommand("TASK_COMPLETE:" + task.id);
                }
            } catch (Exception ignored) {}
        }
        loadTask();
    }

    private void toggleStar() {
        repo.toggleStar(task.id);
        loadTask();
    }

    private void skipRecurrence() {
        if (!task.isRecurring()) return;
        new AlertDialog.Builder(this)
                .setTitle("Skip This Occurrence")
                .setMessage("Skip the current occurrence and create the next one?")
                .setPositiveButton("Skip", (d, w) -> {
                    Task next = repo.createNextRecurrence(task.id);
                    repo.completeTask(task.id); // mark current as done
                    if (next != null) {
                        Toast.makeText(this, "Skipped to next occurrence", Toast.LENGTH_SHORT).show();
                        taskId = next.id;
                        loadTask();
                    } else {
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditor() {
        TaskEditorSheet sheet = TaskEditorSheet.newInstance(task.id);
        sheet.setListener(this);
        sheet.show(getSupportFragmentManager(), "task_editor");
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Move to Trash")
                .setMessage("Move \"" + task.title + "\" to trash?")
                .setPositiveButton("Move to Trash", (d, w) -> {
                    repo.trashTask(task.id);
                    Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── More Options Menu ───────────────────────────────────────

    private void showMoreOptions(View anchor) {
        String[] options = {"📅 Add to Calendar", "📋 Copy Title", "📤 Share Task"};
        new AlertDialog.Builder(this)
                .setTitle("More Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            addToCalendar();
                            break;
                        case 1:
                            copyTitleToClipboard();
                            break;
                        case 2:
                            shareTask();
                            break;
                    }
                })
                .show();
    }

    private void addToCalendar() {
        Intent intent = new Intent(this, CalendarEventDetailActivity.class);
        // Pass task data to create a new calendar event
        intent.putExtra("create_from_task", true);
        intent.putExtra("task_title", task.title);
        intent.putExtra("task_description", task.description != null ? task.description : "");
        intent.putExtra("task_due_date", task.dueDate);
        intent.putExtra("task_priority", task.priority);
        startActivity(intent);
        overridePendingTransition(R.anim.anim_slide_in_right, R.anim.anim_slide_out_left);
        Toast.makeText(this, "Creating calendar event...", Toast.LENGTH_SHORT).show();
    }

    private void copyTitleToClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Task Title", task.title);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Title copied", Toast.LENGTH_SHORT).show();
    }

    private void shareTask() {
        StringBuilder shareText = new StringBuilder();
        shareText.append("📋 ").append(task.title);
        if (task.description != null && !task.description.isEmpty()) {
            shareText.append("\n\n").append(task.description);
        }
        if (task.dueDate != null && !task.dueDate.isEmpty()) {
            shareText.append("\n\n📅 Due: ").append(task.dueDate);
        }
        if (task.priority != null) {
            shareText.append("\n⚡ Priority: ").append(task.priority);
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Task"));
    }

    // ─── Editor Callback ─────────────────────────────────────────

    @Override
    public void onTaskSaved(Task savedTask, boolean isNew) {
        loadTask();
    }

    @Override
    public void onTaskEditorDismissed() {
        // Refresh in case changes were made
        loadTask();
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private String getStatusLabel(String status) {
        if (status == null) return "To Do";
        switch (status) {
            case Task.STATUS_TODO:       return "To Do";
            case Task.STATUS_INPROGRESS: return "In Progress";
            case Task.STATUS_COMPLETED:  return "Completed";
            case Task.STATUS_CANCELLED:  return "Cancelled";
            default:                     return "To Do";
        }
    }

    private String getStatusEmoji(String status) {
        if (status == null) return "📝";
        switch (status) {
            case Task.STATUS_TODO:       return "📝";
            case Task.STATUS_INPROGRESS: return "🔄";
            case Task.STATUS_COMPLETED:  return "✅";
            case Task.STATUS_CANCELLED:  return "❌";
            default:                     return "📝";
        }
    }

    private String getFileName(String path) {
        if (path == null) return "file";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
