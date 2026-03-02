package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full task creation / editing bottom sheet.
 * 
 * Quick-add mode: title + description visible initially.
 * Expanded mode: all fields visible after tapping "Add Details".
 *
 * Supports: priority selection, category picker, tags,
 * due date/time, reminders, recurrence, duration,
 * subtasks, notes, and attachments.
 */
public class TaskEditorSheet extends BottomSheetDialogFragment {

    // ─── Interface ───────────────────────────────────────────────

    public interface TaskEditorListener {
        void onTaskSaved(Task task, boolean isNew);
        void onTaskEditorDismissed();
    }

    // ─── Fields ──────────────────────────────────────────────────

    private TaskEditorListener listener;
    private TaskRepository repo;
    private Task editingTask;     // null = creating new
    private boolean isNewTask = true;
    private boolean expanded = false;

    // Editing state
    private String selectedPriority = Task.PRIORITY_NORMAL;
    private String selectedCategory = "Personal";
    private List<String> selectedTags = new ArrayList<>();
    private String selectedDueDate = null;   // "yyyy-MM-dd"
    private String selectedDueTime = null;   // "HH:mm"
    private List<Long> selectedReminders = new ArrayList<>();
    private String selectedRecurrence = Task.RECURRENCE_NONE;
    private String selectedRecurrenceRule = "";
    private int selectedDuration = 0;        // minutes
    private List<SubTask> editSubtasks = new ArrayList<>();
    private List<String> editAttachments = new ArrayList<>();

    // Views
    private EditText etTitle, etDescription, etNotes, etNewSubtask, etTagInput;
    private LinearLayout expandedSection;
    private TextView btnExpand, btnSave, btnCancel;
    private TextView btnPriorityLow, btnPriorityNormal, btnPriorityHigh, btnPriorityUrgent;
    private LinearLayout categoryPickerRow;
    private LinearLayout tagChipsContainer;
    private TextView chipDueDate, chipDueTime, chipRecurrence, tvRecurrenceSummary, chipEstDuration;
    private LinearLayout reminderChipsContainer, subtasksContainer, attachmentsContainer;

    // ─── Factory Methods ─────────────────────────────────────────

    public static TaskEditorSheet newInstance() {
        return new TaskEditorSheet();
    }

    public static TaskEditorSheet newInstance(String taskId) {
        TaskEditorSheet sheet = new TaskEditorSheet();
        Bundle args = new Bundle();
        args.putString("task_id", taskId);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(TaskEditorListener listener) {
        this.listener = listener;
    }

    // ─── Lifecycle ───────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.DarkBottomSheetDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_task_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = new TaskRepository(requireContext());

        initViews(view);
        setupPrioritySelector();
        buildCategoryPicker();
        setupTagInput();
        setupDateTimePickers();
        setupReminderSection();
        setupRecurrenceSection();
        setupDurationPicker();
        setupSubtaskSection();
        setupButtons();

        // Load existing task if editing
        if (getArguments() != null && getArguments().containsKey("task_id")) {
            String taskId = getArguments().getString("task_id");
            editingTask = repo.getTaskById(taskId);
            if (editingTask != null) {
                isNewTask = false;
                populateFromTask(editingTask);
            }
        }

        // Auto-expand for editing
        if (!isNewTask) {
            toggleExpanded(true);
        }

        // Soft keyboard for title
        etTitle.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog d = (BottomSheetDialog) getDialog();
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    // ─── View Init ───────────────────────────────────────────────

    private void initViews(View v) {
        etTitle = v.findViewById(R.id.etEditorTitle);
        etDescription = v.findViewById(R.id.etEditorDescription);
        btnExpand = v.findViewById(R.id.btnExpandEditor);
        expandedSection = v.findViewById(R.id.expandedEditorSection);

        // Priority pills
        btnPriorityLow = v.findViewById(R.id.btnPriorityLow);
        btnPriorityNormal = v.findViewById(R.id.btnPriorityNormal);
        btnPriorityHigh = v.findViewById(R.id.btnPriorityHigh);
        btnPriorityUrgent = v.findViewById(R.id.btnPriorityUrgent);

        // Category
        categoryPickerRow = v.findViewById(R.id.categoryPickerRow);

        // Tags
        tagChipsContainer = v.findViewById(R.id.tagChipsContainer);
        etTagInput = v.findViewById(R.id.etTagInput);

        // Date/Time
        chipDueDate = v.findViewById(R.id.chipDueDate);
        chipDueTime = v.findViewById(R.id.chipDueTime);

        // Reminders
        reminderChipsContainer = v.findViewById(R.id.reminderChipsContainer);
        TextView btnAddReminder = v.findViewById(R.id.btnAddReminder);
        if (btnAddReminder != null) btnAddReminder.setOnClickListener(vv -> addReminder());

        // Recurrence
        chipRecurrence = v.findViewById(R.id.chipRecurrence);
        tvRecurrenceSummary = v.findViewById(R.id.tvRecurrenceSummary);

        // Duration
        chipEstDuration = v.findViewById(R.id.chipEstDuration);

        // Subtasks
        subtasksContainer = v.findViewById(R.id.subtasksEditorContainer);
        etNewSubtask = v.findViewById(R.id.etNewSubtask);
        TextView btnAddSubtask = v.findViewById(R.id.btnAddSubtask);
        if (btnAddSubtask != null) btnAddSubtask.setOnClickListener(vv -> addSubtask());

        // Notes
        etNotes = v.findViewById(R.id.etEditorNotes);

        // Attachments
        attachmentsContainer = v.findViewById(R.id.attachmentsContainer);

        // Buttons
        btnSave = v.findViewById(R.id.btnEditorSave);
        btnCancel = v.findViewById(R.id.btnEditorCancel);
    }

    // ─── Expand / Collapse ───────────────────────────────────────

    private void toggleExpanded(boolean show) {
        expanded = show;
        expandedSection.setVisibility(show ? View.VISIBLE : View.GONE);
        btnExpand.setText(show ? "－ Hide Details" : "＋ Add Details");
    }

    // ─── Priority Selector ───────────────────────────────────────

    private void setupPrioritySelector() {
        View.OnClickListener priorityClick = v -> {
            if (v == btnPriorityLow) selectedPriority = Task.PRIORITY_LOW;
            else if (v == btnPriorityNormal) selectedPriority = Task.PRIORITY_NORMAL;
            else if (v == btnPriorityHigh) selectedPriority = Task.PRIORITY_HIGH;
            else if (v == btnPriorityUrgent) selectedPriority = Task.PRIORITY_URGENT;
            updatePriorityVisuals();
        };
        btnPriorityLow.setOnClickListener(priorityClick);
        btnPriorityNormal.setOnClickListener(priorityClick);
        btnPriorityHigh.setOnClickListener(priorityClick);
        btnPriorityUrgent.setOnClickListener(priorityClick);

        btnExpand.setOnClickListener(v -> toggleExpanded(!expanded));

        updatePriorityVisuals();
    }

    private void updatePriorityVisuals() {
        updatePriorityPill(btnPriorityLow, Task.PRIORITY_LOW);
        updatePriorityPill(btnPriorityNormal, Task.PRIORITY_NORMAL);
        updatePriorityPill(btnPriorityHigh, Task.PRIORITY_HIGH);
        updatePriorityPill(btnPriorityUrgent, Task.PRIORITY_URGENT);
    }

    private void updatePriorityPill(TextView pill, String priority) {
        boolean active = priority.equals(selectedPriority);
        int color = Task.getPriorityColorFor(priority);
        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(20));
            bg.setColor(color);
            pill.setBackground(bg);
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setBackgroundResource(R.drawable.task_priority_pill_selector);
            pill.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    // ─── Category Picker ─────────────────────────────────────────

    private void buildCategoryPicker() {
        categoryPickerRow.removeAllViews();
        List<TaskCategory> categories = repo.getAllCategories();
        for (TaskCategory cat : categories) {
            TextView chip = new TextView(requireContext());
            chip.setText(cat.icon + " " + cat.name);
            chip.setTextSize(13);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setTag(cat.name);

            chip.setOnClickListener(v -> {
                selectedCategory = cat.name;
                updateCategoryVisuals();
            });

            categoryPickerRow.addView(chip);
        }
        updateCategoryVisuals();
    }

    private void updateCategoryVisuals() {
        for (int i = 0; i < categoryPickerRow.getChildCount(); i++) {
            View child = categoryPickerRow.getChildAt(i);
            if (child instanceof TextView) {
                String catName = (String) child.getTag();
                boolean active = catName != null && catName.equals(selectedCategory);
                if (active) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(dp(16));
                    bg.setColor(Color.parseColor("#1A3B82F6"));
                    bg.setStroke(dp(1), Color.parseColor("#3B82F6"));
                    child.setBackground(bg);
                    ((TextView) child).setTextColor(Color.parseColor("#60A5FA"));
                } else {
                    child.setBackgroundResource(R.drawable.task_priority_pill_selector);
                    ((TextView) child).setTextColor(Color.parseColor("#94A3B8"));
                }
            }
        }
    }

    // ─── Tags ────────────────────────────────────────────────────

    private void setupTagInput() {
        etTagInput.setOnEditorActionListener((v, actionId, event) -> {
            String tag = etTagInput.getText().toString().trim();
            if (!tag.isEmpty() && !selectedTags.contains(tag)) {
                selectedTags.add(tag);
                buildTagChips();
                etTagInput.setText("");
            }
            return true;
        });
    }

    private void buildTagChips() {
        tagChipsContainer.removeAllViews();
        for (String tag : selectedTags) {
            TextView chip = new TextView(requireContext());
            chip.setText(tag + "  ✕");
            chip.setTextSize(12);
            chip.setTextColor(Color.parseColor("#60A5FA"));
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackgroundResource(R.drawable.task_chip_removable_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            lp.bottomMargin = dp(4);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedTags.remove(tag);
                buildTagChips();
            });

            tagChipsContainer.addView(chip);
        }
    }

    // ─── Date / Time Pickers ─────────────────────────────────────

    private void setupDateTimePickers() {
        chipDueDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDueDate != null) {
                try {
                    String[] parts = selectedDueDate.split("-");
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(requireContext(), (dp, y, m, d) -> {
                selectedDueDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                updateDateTimeChips();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        chipDueDate.setOnLongClickListener(v -> {
            selectedDueDate = null;
            updateDateTimeChips();
            return true;
        });

        chipDueTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            if (selectedDueTime != null) {
                try {
                    String[] parts = selectedDueTime.split(":");
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                } catch (Exception ignored) {}
            }
            new TimePickerDialog(requireContext(), (tp, h, m) -> {
                selectedDueTime = String.format(Locale.US, "%02d:%02d", h, m);
                updateDateTimeChips();
            }, hour, minute, true).show();
        });

        chipDueTime.setOnLongClickListener(v -> {
            selectedDueTime = null;
            updateDateTimeChips();
            return true;
        });
    }

    private void updateDateTimeChips() {
        if (selectedDueDate != null) {
            try {
                String[] parts = selectedDueDate.split("-");
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                chipDueDate.setText("📅  " + sdf.format(cal.getTime()));
                chipDueDate.setTextColor(Color.parseColor("#60A5FA"));
            } catch (Exception e) {
                chipDueDate.setText("📅  " + selectedDueDate);
            }
        } else {
            chipDueDate.setText("📅  Set Due Date");
            chipDueDate.setTextColor(Color.parseColor("#94A3B8"));
        }

        if (selectedDueTime != null) {
            chipDueTime.setText("🕐  " + formatTime(selectedDueTime));
            chipDueTime.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            chipDueTime.setText("🕐  Set Time");
            chipDueTime.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private String formatTime(String time24) {
        try {
            String[] parts = time24.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String ampm = h >= 12 ? "PM" : "AM";
            if (h > 12) h -= 12;
            if (h == 0) h = 12;
            return String.format(Locale.US, "%d:%02d %s", h, m, ampm);
        } catch (Exception e) {
            return time24;
        }
    }

    // ─── Reminders ───────────────────────────────────────────────

    private void setupReminderSection() {
        buildReminderChips();
    }

    private void addReminder() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            try {
                String[] parts = selectedDueDate.split("-");
                cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            } catch (Exception ignored) {}
        }

        new DatePickerDialog(requireContext(), (dp, y, m, d) -> {
            Calendar dateCal = Calendar.getInstance();
            dateCal.set(y, m, d);

            int hour = dateCal.get(Calendar.HOUR_OF_DAY);
            int minute = dateCal.get(Calendar.MINUTE);

            new TimePickerDialog(requireContext(), (tp, h, min) -> {
                dateCal.set(Calendar.HOUR_OF_DAY, h);
                dateCal.set(Calendar.MINUTE, min);
                dateCal.set(Calendar.SECOND, 0);
                selectedReminders.add(dateCal.getTimeInMillis());
                buildReminderChips();
            }, hour, minute, true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void buildReminderChips() {
        reminderChipsContainer.removeAllViews();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);

        for (int i = 0; i < selectedReminders.size(); i++) {
            long ts = selectedReminders.get(i);
            final int idx = i;

            TextView chip = new TextView(requireContext());
            chip.setText("🔔 " + sdf.format(new Date(ts)) + "  ✕");
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

            chip.setOnClickListener(v -> {
                selectedReminders.remove(idx);
                buildReminderChips();
            });

            reminderChipsContainer.addView(chip);
        }
    }

    // ─── Recurrence ──────────────────────────────────────────────

    private void setupRecurrenceSection() {
        chipRecurrence.setOnClickListener(v -> showRecurrencePicker());
        updateRecurrenceChip();
    }

    private void showRecurrencePicker() {
        String[] options = {"None", "Daily", "Weekly", "Monthly"};
        String[] values = {Task.RECURRENCE_NONE, Task.RECURRENCE_DAILY, Task.RECURRENCE_WEEKLY, Task.RECURRENCE_MONTHLY};

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Repeat")
                .setItems(options, (d, which) -> {
                    selectedRecurrence = values[which];
                    updateRecurrenceChip();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateRecurrenceChip() {
        if (Task.RECURRENCE_NONE.equals(selectedRecurrence)) {
            chipRecurrence.setText("🔁  No Repeat");
            chipRecurrence.setTextColor(Color.parseColor("#94A3B8"));
            if (tvRecurrenceSummary != null) tvRecurrenceSummary.setVisibility(View.GONE);
        } else {
            String label = "";
            switch (selectedRecurrence) {
                case Task.RECURRENCE_DAILY:   label = "Daily"; break;
                case Task.RECURRENCE_WEEKLY:  label = "Weekly"; break;
                case Task.RECURRENCE_MONTHLY: label = "Monthly"; break;
                default:                      label = "Custom"; break;
            }
            chipRecurrence.setText("🔁  " + label);
            chipRecurrence.setTextColor(Color.parseColor("#60A5FA"));
            if (tvRecurrenceSummary != null) {
                tvRecurrenceSummary.setText("Repeats " + label.toLowerCase());
                tvRecurrenceSummary.setVisibility(View.VISIBLE);
            }
        }
    }

    // ─── Duration Picker ─────────────────────────────────────────

    private void setupDurationPicker() {
        chipEstDuration.setOnClickListener(v -> showDurationPicker());
        updateDurationChip();
    }

    private void showDurationPicker() {
        String[] options = {"15 min", "30 min", "45 min", "1 hour", "1.5 hours", "2 hours", "3 hours", "4 hours"};
        int[] values = {15, 30, 45, 60, 90, 120, 180, 240};

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Estimated Duration")
                .setItems(options, (d, which) -> {
                    selectedDuration = values[which];
                    updateDurationChip();
                })
                .setNegativeButton("Clear", (d, w) -> {
                    selectedDuration = 0;
                    updateDurationChip();
                })
                .show();
    }

    private void updateDurationChip() {
        if (selectedDuration > 0) {
            String text = selectedDuration < 60
                    ? selectedDuration + " min"
                    : (selectedDuration / 60) + "h" + (selectedDuration % 60 > 0 ? " " + (selectedDuration % 60) + "m" : "");
            chipEstDuration.setText("⏱  " + text);
            chipEstDuration.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            chipEstDuration.setText("⏱  Est. Duration");
            chipEstDuration.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    // ─── Subtasks ────────────────────────────────────────────────

    private void setupSubtaskSection() {
        etNewSubtask.setOnEditorActionListener((v, actionId, event) -> {
            addSubtask();
            return true;
        });
        buildSubtaskViews();
    }

    private void addSubtask() {
        String title = etNewSubtask.getText().toString().trim();
        if (title.isEmpty()) return;

        SubTask sub = new SubTask(title);
        editSubtasks.add(sub);
        etNewSubtask.setText("");
        buildSubtaskViews();
    }

    private void buildSubtaskViews() {
        subtasksContainer.removeAllViews();
        for (int i = 0; i < editSubtasks.size(); i++) {
            SubTask sub = editSubtasks.get(i);
            final int idx = i;

            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_subtask_edit, subtasksContainer, false);

            CheckBox cb = row.findViewById(R.id.cbSubtask);
            EditText etTitle = row.findViewById(R.id.etSubtaskTitle);
            ImageView btnDelete = row.findViewById(R.id.btnDeleteSubtask);

            cb.setChecked(sub.isCompleted);
            etTitle.setText(sub.title);

            cb.setOnCheckedChangeListener((btn, checked) -> sub.isCompleted = checked);
            etTitle.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override
                public void afterTextChanged(Editable s) {
                    sub.title = s.toString();
                }
            });
            btnDelete.setOnClickListener(v -> {
                editSubtasks.remove(idx);
                buildSubtaskViews();
            });

            subtasksContainer.addView(row);
        }
    }

    // ─── Buttons ─────────────────────────────────────────────────

    private void setupButtons() {
        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }

        Task task;
        if (isNewTask) {
            task = new Task();
        } else {
            task = editingTask;
        }

        task.title = title;
        task.description = etDescription.getText().toString().trim();
        task.priority = selectedPriority;
        task.category = selectedCategory;
        task.tags = new ArrayList<>(selectedTags);
        task.dueDate = selectedDueDate;
        task.dueTime = selectedDueTime;
        task.reminderDateTimes = new ArrayList<>(selectedReminders);
        task.recurrence = selectedRecurrence;
        task.recurrenceRule = selectedRecurrenceRule;
        task.estimatedDuration = selectedDuration;
        task.subtasks = new ArrayList<>(editSubtasks);
        task.attachments = new ArrayList<>(editAttachments);
        task.notes = etNotes.getText().toString().trim();

        if (isNewTask) {
            task.source = "mobile";
            repo.addTask(task);
        } else {
            repo.updateTask(task);
        }

        // Schedule notifications
        TaskNotificationHelper.scheduleTaskReminders(requireContext(), task);
        if (task.hasDueDate()) {
            TaskNotificationHelper.scheduleOverdueAlert(requireContext(), task);
        }

        if (listener != null) listener.onTaskSaved(task, isNewTask);
        dismiss();
    }

    // ─── Populate From Existing Task ─────────────────────────────

    private void populateFromTask(Task task) {
        etTitle.setText(task.title);
        etDescription.setText(task.description);

        selectedPriority = task.priority != null ? task.priority : Task.PRIORITY_NORMAL;
        selectedCategory = task.category != null ? task.category : "Personal";
        selectedTags = task.tags != null ? new ArrayList<>(task.tags) : new ArrayList<>();
        selectedDueDate = task.dueDate;
        selectedDueTime = task.dueTime;
        selectedReminders = task.reminderDateTimes != null ? new ArrayList<>(task.reminderDateTimes) : new ArrayList<>();
        selectedRecurrence = task.recurrence != null ? task.recurrence : Task.RECURRENCE_NONE;
        selectedRecurrenceRule = task.recurrenceRule != null ? task.recurrenceRule : "";
        selectedDuration = task.estimatedDuration;
        editSubtasks = new ArrayList<>();
        if (task.subtasks != null) {
            for (SubTask st : task.subtasks) editSubtasks.add(st.copy());
        }
        editAttachments = task.attachments != null ? new ArrayList<>(task.attachments) : new ArrayList<>();

        if (etNotes != null) etNotes.setText(task.notes != null ? task.notes : "");

        // Update all visuals
        updatePriorityVisuals();
        updateCategoryVisuals();
        buildTagChips();
        updateDateTimeChips();
        buildReminderChips();
        updateRecurrenceChip();
        updateDurationChip();
        buildSubtaskViews();

        btnSave.setText("Update Task");
    }

    // ─── Utility ─────────────────────────────────────────────────

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) listener.onTaskEditorDismissed();
    }
}
