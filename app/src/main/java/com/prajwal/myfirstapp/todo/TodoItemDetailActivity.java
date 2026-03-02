package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.calendar.CalendarEvent;
import com.prajwal.myfirstapp.calendar.CalendarRepository;
import com.prajwal.myfirstapp.tasks.SubtaskItem;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Task detail screen for a single {@link TodoItem}.
 *
 * Features:
 *   - Full metadata display: title, description, priority badge, due date,
 *     reminder, recurrence, estimated duration, tags
 *   - Inline subtask RecyclerView with toggle-complete; prompts to close parent
 *     when all subtasks are checked
 *   - Focus timer with start/stop, live MM:SS display, per-session persistence
 *   - Timer session history (newest first: "Dec 15 · 23m")
 *   - Mark Complete action with animation
 *   - Edit via TodoItemEditorSheet
 *
 * Uses layout R.layout.activity_todo_item_detail.
 */
public class TodoItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "item_id";

    // ─── Data ────────────────────────────────────────────────────

    private TodoRepository repo;
    private TodoItem currentItem;

    // ─── Timer state ─────────────────────────────────────────────

    private static final long MIN_SESSION_DURATION_SECONDS = 5;
    private static final SimpleDateFormat SDF_DATE =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private boolean  isTimerRunning = false;
    private long     timerStartMs   = 0;
    private final Handler  timerHandler  = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // ─── Views ───────────────────────────────────────────────────

    private TextView     tvTimerDisplay;
    private TextView     tvTotalTime;
    private TextView     tvItemTitle;
    private TextView     tvItemDescription;
    private TextView     tvDueDate;
    private TextView     tvReminder;
    private TextView     tvRecurrence;
    private TextView     tvEstDuration;
    private TextView     tvPriorityBadge;
    private TextView     tvSubtaskCount;
    private RecyclerView rvSubtasks;
    private RecyclerView rvTimerSessions;
    private Button       btnTimer;
    private Button       btnCompleteTask;
    private LinearLayout tagsContainer;

    // ═══════════════════════════════════════════════════════════════
    // Static Intent Factory
    // ═══════════════════════════════════════════════════════════════

    /** Creates a launch intent that opens this activity for the given item id. */
    public static Intent createIntent(Context ctx, String itemId) {
        Intent intent = new Intent(ctx, TodoItemDetailActivity.class);
        intent.putExtra(EXTRA_ITEM_ID, itemId);
        return intent;
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo_item_detail);

        String itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        repo = new TodoRepository(this);
        currentItem = repo.getItemById(itemId);

        if (currentItem == null) {
            Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentItem == null) return;
        repo = new TodoRepository(this);
        currentItem = repo.getItemById(currentItem.id);
        if (currentItem != null) loadData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Save any in-progress timer session before the activity is destroyed
        if (isTimerRunning) {
            savePartialSession();
        }
        timerHandler.removeCallbacksAndMessages(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═══════════════════════════════════════════════════════════════

    private void initViews() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnEditItem = findViewById(R.id.btnEditItem);
        if (btnEditItem != null) btnEditItem.setOnClickListener(v -> openEditor());

        tvItemTitle       = findViewById(R.id.tvItemTitle);
        tvItemDescription = findViewById(R.id.tvItemDescription);
        tvPriorityBadge   = findViewById(R.id.tvPriorityBadge);
        tvDueDate         = findViewById(R.id.tvDueDate);
        tvReminder        = findViewById(R.id.tvReminder);
        tvRecurrence      = findViewById(R.id.tvRecurrence);
        tvEstDuration     = findViewById(R.id.tvEstDuration);
        tvSubtaskCount    = findViewById(R.id.tvSubtaskCount);
        tagsContainer     = findViewById(R.id.tagsContainer);

        rvSubtasks      = findViewById(R.id.rvSubtasks);
        rvTimerSessions = findViewById(R.id.rvTimerSessions);
        tvTimerDisplay  = findViewById(R.id.tvTimerDisplay);
        tvTotalTime     = findViewById(R.id.tvTotalTime);

        btnTimer        = findViewById(R.id.btnTimer);
        btnCompleteTask = findViewById(R.id.btnCompleteTask);

        if (btnTimer        != null) btnTimer.setOnClickListener(v -> toggleTimer());
        if (btnCompleteTask != null) btnCompleteTask.setOnClickListener(v -> completeItem());

        View btnAddToCalendar = findViewById(R.id.btnAddToCalendar);
        if (btnAddToCalendar != null) btnAddToCalendar.setOnClickListener(v -> addTaskToCalendar());
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD DATA
    // ═══════════════════════════════════════════════════════════════

    private void loadData() {
        if (currentItem == null) return;

        // Title
        if (tvItemTitle != null) tvItemTitle.setText(currentItem.title);

        // Description
        if (tvItemDescription != null) {
            boolean hasDesc = currentItem.description != null
                    && !currentItem.description.isEmpty();
            tvItemDescription.setText(hasDesc ? currentItem.description : "No description");
            tvItemDescription.setAlpha(hasDesc ? 1f : 0.5f);
        }

        // Priority badge with colour-tinted background
        if (tvPriorityBadge != null) {
            String priority = currentItem.priority != null
                    ? currentItem.priority : TodoItem.PRIORITY_NONE;
            if (TodoItem.PRIORITY_NONE.equals(priority)) {
                tvPriorityBadge.setVisibility(View.GONE);
            } else {
                tvPriorityBadge.setText(capitalize(priority));
                int priorityColor = currentItem.getPriorityColor();
                float density = getResources().getDisplayMetrics().density;
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(8 * density);
                bg.setColor(withAlpha(priorityColor, 0.2f));
                tvPriorityBadge.setBackground(bg);
                tvPriorityBadge.setTextColor(priorityColor);
                tvPriorityBadge.setVisibility(View.VISIBLE);
            }
        }

        // Due date (relative label; red when overdue)
        if (tvDueDate != null) {
            String rel = currentItem.getRelativeDueDate();
            if (rel != null && !rel.isEmpty()) {
                tvDueDate.setText(rel);
                tvDueDate.setTextColor(currentItem.isOverdue() ? 0xFFEF4444 : 0xFF94A3B8);
                tvDueDate.setVisibility(View.VISIBLE);
            } else {
                tvDueDate.setVisibility(View.GONE);
            }
        }

        // Reminder
        if (tvReminder != null) {
            if (currentItem.reminderDateTime > 0) {
                String formatted = new SimpleDateFormat("MMM d, h:mm a", Locale.US)
                        .format(new Date(currentItem.reminderDateTime));
                tvReminder.setText("🔔 " + formatted);
                tvReminder.setVisibility(View.VISIBLE);
            } else {
                tvReminder.setVisibility(View.GONE);
            }
        }

        // Recurrence
        if (tvRecurrence != null) {
            String label = getRecurrenceLabel(currentItem.recurrence);
            if (label != null) {
                tvRecurrence.setText("🔁 " + label);
                tvRecurrence.setVisibility(View.VISIBLE);
            } else {
                tvRecurrence.setVisibility(View.GONE);
            }
        }

        // Estimated duration
        if (tvEstDuration != null) {
            if (currentItem.estimatedDurationMinutes > 0) {
                tvEstDuration.setText("⏱ Est. "
                        + formatMinutes(currentItem.estimatedDurationMinutes));
                tvEstDuration.setVisibility(View.VISIBLE);
            } else {
                tvEstDuration.setVisibility(View.GONE);
            }
        }

        // Tags as chips
        if (tagsContainer != null) {
            tagsContainer.removeAllViews();
            if (currentItem.tags != null && !currentItem.tags.isEmpty()) {
                for (String tag : currentItem.tags) {
                    tagsContainer.addView(buildTagChip(tag));
                }
                tagsContainer.setVisibility(View.VISIBLE);
            } else {
                tagsContainer.setVisibility(View.GONE);
            }
        }

        // Subtasks
        List<SubtaskItem> subtasks = currentItem.subtasks != null
                ? currentItem.subtasks : new ArrayList<>();
        if (tvSubtaskCount != null) {
            int done = 0;
            for (SubtaskItem s : subtasks) { if (s.isCompleted) done++; }
            tvSubtaskCount.setText(done + "/" + subtasks.size() + " subtasks");
            tvSubtaskCount.setVisibility(subtasks.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (rvSubtasks != null) {
            rvSubtasks.setLayoutManager(new LinearLayoutManager(this));
            rvSubtasks.setAdapter(new SubtaskAdapter(subtasks));
            rvSubtasks.setVisibility(subtasks.isEmpty() ? View.GONE : View.VISIBLE);
        }

        // Timer sessions (newest first)
        List<TimerSession> sessions = currentItem.timerSessions != null
                ? currentItem.timerSessions : new ArrayList<>();
        if (rvTimerSessions != null) {
            rvTimerSessions.setLayoutManager(new LinearLayoutManager(this));
            rvTimerSessions.setAdapter(new TimerSessionAdapter(sessions));
            rvTimerSessions.setVisibility(sessions.isEmpty() ? View.GONE : View.VISIBLE);
        }

        // Total tracked time
        if (tvTotalTime != null) {
            tvTotalTime.setText("Total: " + formatMinutes(currentItem.getTotalTimeTrackedMinutes()));
        }

        // Complete button state
        if (btnCompleteTask != null) {
            btnCompleteTask.setText(currentItem.isCompleted ? "Completed ✓" : "Mark Complete");
            btnCompleteTask.setAlpha(currentItem.isCompleted ? 0.6f : 1f);
        }

        // Keep timer button label in sync if timer is not running
        if (btnTimer != null && !isTimerRunning) {
            btnTimer.setText("Start Timer");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMER
    // ═══════════════════════════════════════════════════════════════

    private void toggleTimer() {
        if (!isTimerRunning) {
            timerStartMs   = System.currentTimeMillis();
            isTimerRunning = true;
            if (btnTimer != null) btnTimer.setText("Stop");
            startTimerTick();
        } else {
            stopTimer();
        }
    }

    private void stopTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacksAndMessages(null);
        if (btnTimer != null) btnTimer.setText("Start Timer");

        long durationSeconds = (System.currentTimeMillis() - timerStartMs) / 1000;
        if (durationSeconds > 0 && currentItem != null) {
            TimerSession session = new TimerSession(
                    currentItem.id, timerStartMs, System.currentTimeMillis());
            repo.addTimerSession(currentItem.id, session);
            currentItem = repo.getItemById(currentItem.id);
        }

        if (tvTimerDisplay != null) tvTimerDisplay.setText("00:00");
        loadData();
    }

    /** Posts a recurring 1-second runnable that updates the live timer display. */
    private void startTimerTick() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTimerRunning) return;
                long elapsed = (System.currentTimeMillis() - timerStartMs) / 1000;
                long mins    = elapsed / 60;
                long secs    = elapsed % 60;
                if (tvTimerDisplay != null) {
                    tvTimerDisplay.setText(
                            String.format(Locale.US, "%02d:%02d", mins, secs));
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    /**
     * Saves the current in-progress timer session before the activity is destroyed.
     * Only persists if the session is longer than 5 seconds.
     */
    private void savePartialSession() {
        long durationSeconds = (System.currentTimeMillis() - timerStartMs) / 1000;
        if (durationSeconds > MIN_SESSION_DURATION_SECONDS && currentItem != null) {
            TimerSession session = new TimerSession(
                    currentItem.id, timerStartMs, System.currentTimeMillis());
            repo.addTimerSession(currentItem.id, session);
        }
        isTimerRunning = false;
        timerHandler.removeCallbacksAndMessages(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPLETE ITEM
    // ═══════════════════════════════════════════════════════════════

    private void completeItem() {
        if (currentItem.isCompleted) {
            Toast.makeText(this, "Task already completed", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.completeItem(currentItem.id);
        currentItem = repo.getItemById(currentItem.id);

        // Bounce animation on the complete button
        if (btnCompleteTask != null) {
            btnCompleteTask.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100)
                    .withEndAction(() ->
                            btnCompleteTask.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(150).start())
                    .start();
        }

        Toast.makeText(this, "Task completed!", Toast.LENGTH_SHORT).show();
        loadData();
        setResult(RESULT_OK);
    }

    // ═══════════════════════════════════════════════════════════════
    // EDITOR
    // ═══════════════════════════════════════════════════════════════

    private void openEditor() {
        TodoItemEditorSheet sheet =
                TodoItemEditorSheet.newInstance(currentItem.listId, currentItem.id);
        sheet.setListener(updated -> {
            repo.updateItem(updated);
            currentItem = repo.getItemById(currentItem.id);
            loadData();
        });
        sheet.show(getSupportFragmentManager(), "TodoItemEditorSheet");
    }

    // ═══════════════════════════════════════════════════════════════
    // INLINE SUBTASK ADAPTER
    // ═══════════════════════════════════════════════════════════════

    private class SubtaskAdapter extends RecyclerView.Adapter<SubtaskAdapter.VH> {

        private final List<SubtaskItem> subtasks;

        SubtaskAdapter(List<SubtaskItem> subtasks) {
            this.subtasks = subtasks;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float density = getResources().getDisplayMetrics().density;
            int dp8  = (int) (8  * density);
            int dp12 = (int) (12 * density);

            LinearLayout row = new LinearLayout(TodoItemDetailActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp12, dp8, dp12, dp8);
            row.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            CheckBox cb = new CheckBox(TodoItemDetailActivity.this);
            cb.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(TodoItemDetailActivity.this);
            tv.setTextSize(14);
            tv.setTextColor(0xFFCBD5E1);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvParams.setMarginStart(dp8);
            tv.setLayoutParams(tvParams);

            row.addView(cb);
            row.addView(tv);
            return new VH(row, cb, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SubtaskItem sub = subtasks.get(position);
            holder.tv.setText(sub.title);
            holder.tv.setAlpha(sub.isCompleted ? 0.5f : 1f);

            // Prevent callback from firing during bind
            holder.cb.setOnCheckedChangeListener(null);
            holder.cb.setChecked(sub.isCompleted);
            holder.cb.setOnCheckedChangeListener((btn, checked) -> {
                sub.isCompleted = checked;
                sub.completedAt = checked ? System.currentTimeMillis() : 0;
                holder.tv.setAlpha(checked ? 0.5f : 1f);
                repo.updateSubtasks(currentItem.id, currentItem.subtasks);

                // If all subtasks are now done, prompt to complete the parent task
                boolean allDone = true;
                for (SubtaskItem s : subtasks) {
                    if (!s.isCompleted) { allDone = false; break; }
                }
                if (allDone && !currentItem.isCompleted) {
                    new AlertDialog.Builder(TodoItemDetailActivity.this)
                            .setTitle("All subtasks done")
                            .setMessage("Mark task as complete?")
                            .setPositiveButton("Complete", (d, w) -> completeItem())
                            .setNegativeButton("Not yet", null)
                            .show();
                }

                loadData();
            });
        }

        @Override
        public int getItemCount() { return subtasks.size(); }

        class VH extends RecyclerView.ViewHolder {
            final CheckBox cb;
            final TextView tv;
            VH(View v, CheckBox cb, TextView tv) { super(v); this.cb = cb; this.tv = tv; }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMER SESSION ADAPTER
    // ═══════════════════════════════════════════════════════════════

    private static class TimerSessionAdapter
            extends RecyclerView.Adapter<TimerSessionAdapter.VH> {

        private final List<TimerSession> sessions;
        private static final SimpleDateFormat DATE_FMT =
                new SimpleDateFormat("MMM d", Locale.US);

        TimerSessionAdapter(List<TimerSession> sessions) {
            this.sessions = sessions;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            int dp12 = (int) (12 * density);
            int dp6  = (int) (6  * density);
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(13);
            tv.setTextColor(0xFF94A3B8);
            tv.setPadding(dp12, dp6, dp12, dp6);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Newest session first
            TimerSession session = sessions.get(sessions.size() - 1 - position);
            String dateStr = DATE_FMT.format(new Date(session.startTime));
            holder.tv.setText(dateStr + " · " + session.getDurationFormatted());
        }

        @Override
        public int getItemCount() { return sessions.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(TextView tv) { super(tv); this.tv = tv; }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private TextView buildTagChip(String tag) {
        float density = getResources().getDisplayMetrics().density;
        int dp6 = (int) (6 * density);
        int dp4 = (int) (4 * density);

        TextView chip = new TextView(this);
        chip.setText("#" + tag);
        chip.setTextSize(12);
        chip.setTextColor(0xFF60A5FA);
        chip.setPadding(dp6, dp4, dp6, dp4);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp6);
        chip.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(6 * density);
        bg.setColor(0x1A60A5FA);
        chip.setBackground(bg);
        return chip;
    }

    private String getRecurrenceLabel(String recurrence) {
        if (recurrence == null || TodoItem.RECURRENCE_NONE.equals(recurrence)) return null;
        switch (recurrence) {
            case TodoItem.RECURRENCE_DAILY:   return "Every day";
            case TodoItem.RECURRENCE_WEEKLY:  return "Every week";
            case TodoItem.RECURRENCE_MONTHLY: return "Every month";
            case TodoItem.RECURRENCE_YEARLY:  return "Every year";
            default:                          return recurrence;
        }
    }

    private String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    /** Creates a calendar event pre-filled with the task details. */
    private void addTaskToCalendar() {
        if (currentItem == null) return;

        CalendarRepository calRepo = new CalendarRepository(this);
        CalendarEvent event = new CalendarEvent();
        event.title = currentItem.title;
        event.description = currentItem.description != null ? currentItem.description : "";
        event.startDate = currentItem.dueDate != null ? currentItem.dueDate : getTodayDate();
        event.endDate = event.startDate;
        event.startTime = currentItem.dueTime != null ? currentItem.dueTime : "09:00";
        event.endTime = event.startTime;
        event.isAllDay = (currentItem.dueTime == null || currentItem.dueTime.isEmpty());
        event.eventType = CalendarEvent.TYPE_PERSONAL;

        // Show confirmation before creating
        String details = event.title + "\n" + event.startDate
                + (event.isAllDay ? " (All day)" : " at " + event.startTime);
        new AlertDialog.Builder(this)
                .setTitle("Add to Calendar")
                .setMessage("Create calendar event:\n\n" + details)
                .setPositiveButton("Create Event", (d, w) -> {
                    calRepo.addEvent(event);
                    Toast.makeText(this, "📅 Event added to Calendar", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getTodayDate() {
        return SDF_DATE.format(new Date());
    }
}
