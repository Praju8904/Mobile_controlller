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
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
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
    private String selectedColorLabel = null;     // hex string or null
    private String selectedStartDate = null;      // "yyyy-MM-dd" or null
    private int selectedEffortPoints = 0;         // 0=unset, Fibonacci values

    // Batch 4 editing state
    private boolean selectedIsNextAction = false;
    private boolean selectedIsMIT = false;
    private String selectedContextTag = null;
    private String selectedWaitingFor = null;
    private String selectedAssignedTo = null;
    private boolean selectedIsPrivate = false;
    private String selectedProjectId = null;

    // Views
    private EditText etTitle, etDescription, etNotes, etNewSubtask, etTagInput;
    private LinearLayout expandedSection;
    private TextView btnExpand, btnSave, btnCancel;
    private TextView btnPriorityLow, btnPriorityNormal, btnPriorityHigh, btnPriorityUrgent;
    private LinearLayout categoryPickerRow;
    private LinearLayout tagChipsContainer;
    private TextView chipDueDate, chipDueTime, chipRecurrence, tvRecurrenceSummary, chipEstDuration;
    private LinearLayout reminderChipsContainer, subtasksContainer, attachmentsContainer;
    private FrameLayout colorSwatch;
    private TextView tvColorNone, chipStartDate;
    private LinearLayout effortChipsRow;

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
        setupColorLabelRow();
        buildCategoryPicker();
        setupTagInput();
        setupDateTimePickers();
        setupStartDateRow();
        setupReminderSection();
        setupRecurrenceSection();
        setupDurationPicker();
        setupEffortPointsRow();
        setupBatch4Sections();
        setupSubtaskSection();
        setupButtons();
        setupSentimentPriorityDetection();

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

    // ─── Color Label Picker ──────────────────────────────────────

    private void setupColorLabelRow() {
        // Create row programmatically and insert after priority in expandedSection
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = new TextView(requireContext());
        label.setText("Color Label");
        label.setTextSize(13);
        label.setTextColor(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelLp);
        row.addView(label);

        // Color swatch
        colorSwatch = new FrameLayout(requireContext());
        int swatchSize = dp(32);
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatchLp.setMarginEnd(dp(8));
        colorSwatch.setLayoutParams(swatchLp);
        updateColorSwatchVisual();
        colorSwatch.setOnClickListener(v -> showColorPickerSheet());
        row.addView(colorSwatch);

        // "None" clear button
        tvColorNone = new TextView(requireContext());
        tvColorNone.setText("None");
        tvColorNone.setTextSize(12);
        tvColorNone.setTextColor(Color.parseColor("#94A3B8"));
        tvColorNone.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvColorNone.setVisibility(selectedColorLabel != null ? View.VISIBLE : View.GONE);
        tvColorNone.setOnClickListener(v -> {
            selectedColorLabel = null;
            updateColorSwatchVisual();
            tvColorNone.setVisibility(View.GONE);
        });
        row.addView(tvColorNone);

        // Insert into expandedSection after priority section (index 0 or 1)
        if (expandedSection != null) {
            expandedSection.addView(row, 0);
        }
    }

    private void updateColorSwatchVisual() {
        if (colorSwatch == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setCornerRadius(dp(16));
        try {
            gd.setColor(selectedColorLabel != null ? Color.parseColor(selectedColorLabel) : Color.parseColor("#374151"));
        } catch (Exception e) {
            gd.setColor(Color.parseColor("#374151"));
        }
        gd.setStroke(dp(1), Color.parseColor("#FFFFFF30"));
        colorSwatch.setBackground(gd);
    }

    private void showColorPickerSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog picker =
            new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.setBackgroundColor(Color.parseColor("#1A1F35"));

        TextView title = new TextView(requireContext());
        title.setText("Choose a label colour");
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dp(16));
        content.addView(title);

        String[] colors = {
            "#EF4444", "#F97316", "#F59E0B", "#EAB308",
            "#84CC16", "#10B981", "#06B6D4", "#3B82F6",
            "#6366F1", "#A855F7", "#EC4899", "#64748B"
        };

        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(4);
        grid.setRowCount(3);

        for (String hex : colors) {
            FrameLayout swatch = new FrameLayout(requireContext());
            int size = dp(40);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = size;
            glp.height = size;
            glp.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatch.setLayoutParams(glp);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.parseColor(hex));
            swatch.setBackground(gd);

            swatch.setOnClickListener(v -> {
                selectedColorLabel = hex;
                updateColorSwatchVisual();
                if (tvColorNone != null) tvColorNone.setVisibility(View.VISIBLE);
                picker.dismiss();
            });

            grid.addView(swatch);
        }

        content.addView(grid);
        picker.setContentView(content);
        picker.show();
    }

    // ─── Start Date Row ──────────────────────────────────────────

    private void setupStartDateRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = new TextView(requireContext());
        label.setText("Start Date");
        label.setTextSize(13);
        label.setTextColor(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelLp);
        row.addView(label);

        chipStartDate = new TextView(requireContext());
        chipStartDate.setTextSize(13);
        chipStartDate.setPadding(dp(10), dp(6), dp(10), dp(6));
        updateStartDateChip();
        chipStartDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedStartDate != null) {
                try {
                    String[] parts = selectedStartDate.split("-");
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                } catch (Exception ignored) {}
            }
            new DatePickerDialog(requireContext(), (dp2, y, m, d) -> {
                String newDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                // Validation: start date must not be after due date
                if (selectedDueDate != null && newDate.compareTo(selectedDueDate) > 0) {
                    Toast.makeText(requireContext(), "Start date must be before due date", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedStartDate = newDate;
                updateStartDateChip();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        row.addView(chipStartDate);

        // Clear button
        ImageView clearBtn = new ImageView(requireContext());
        clearBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        clearBtn.setColorFilter(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        clearLp.setMarginStart(dp(8));
        clearBtn.setLayoutParams(clearLp);
        clearBtn.setVisibility(selectedStartDate != null ? View.VISIBLE : View.GONE);
        clearBtn.setOnClickListener(v -> {
            selectedStartDate = null;
            updateStartDateChip();
            v.setVisibility(View.GONE);
        });
        row.addView(clearBtn);

        // Find the right position after date/time section
        if (expandedSection != null) {
            // Add after any existing children at a calculated position
            int insertIdx = Math.min(3, expandedSection.getChildCount());
            expandedSection.addView(row, insertIdx);
        }
    }

    private void updateStartDateChip() {
        if (chipStartDate == null) return;
        if (selectedStartDate != null) {
            try {
                String[] parts = selectedStartDate.split("-");
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                chipStartDate.setText("▶  " + sdf.format(cal.getTime()));
                chipStartDate.setTextColor(Color.parseColor("#60A5FA"));
            } catch (Exception e) {
                chipStartDate.setText("▶  " + selectedStartDate);
            }
        } else {
            chipStartDate.setText("▶  Not set");
            chipStartDate.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    // ─── Effort Points (Story Points) Row ────────────────────────

    private void setupEffortPointsRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView label = new TextView(requireContext());
        label.setText("Story Points");
        label.setTextSize(13);
        label.setTextColor(Color.parseColor("#94A3B8"));
        label.setPadding(0, 0, 0, dp(6));
        row.addView(label);

        HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);

        effortChipsRow = new LinearLayout(requireContext());
        effortChipsRow.setOrientation(LinearLayout.HORIZONTAL);

        int[] values = {0, 1, 2, 3, 5, 8, 13};
        String[] labels = {"–", "1", "2", "3", "5", "8", "13"};

        for (int i = 0; i < values.length; i++) {
            final int val = values[i];
            TextView chip = new TextView(requireContext());
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));
            chip.setTag(val);

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.setMarginEnd(dp(6));
            chip.setLayoutParams(chipLp);

            chip.setOnClickListener(v -> {
                selectedEffortPoints = val;
                updateEffortChipsVisuals();
            });

            effortChipsRow.addView(chip);
        }

        scroll.addView(effortChipsRow);
        row.addView(scroll);
        updateEffortChipsVisuals();

        // Insert after duration section
        if (expandedSection != null) {
            int insertIdx = Math.min(expandedSection.getChildCount(), expandedSection.getChildCount());
            expandedSection.addView(row, insertIdx);
        }
    }

    private void updateEffortChipsVisuals() {
        if (effortChipsRow == null) return;
        for (int i = 0; i < effortChipsRow.getChildCount(); i++) {
            View child = effortChipsRow.getChildAt(i);
            if (child instanceof TextView) {
                int val = (int) child.getTag();
                boolean active = val == selectedEffortPoints;
                if (active) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(dp(20));
                    bg.setColor(Task.getPriorityColorFor(selectedPriority));
                    child.setBackground(bg);
                    ((TextView) child).setTextColor(Color.WHITE);
                } else {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(dp(20));
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(dp(1), Color.parseColor("#FFFFFF20"));
                    child.setBackground(bg);
                    ((TextView) child).setTextColor(Color.parseColor("#94A3B8"));
                }
            }
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

    // ─── Batch 4 Sections ──────────────────────────────────────

    private void setupBatch4Sections() {
        if (expandedSection == null) return;

        // ── GTD Row: Next Action + MIT toggles ──
        LinearLayout gtdRow = new LinearLayout(requireContext());
        gtdRow.setOrientation(LinearLayout.HORIZONTAL);
        gtdRow.setPadding(0, dp(10), 0, dp(4));
        gtdRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        CheckBox cbNextAction = new CheckBox(requireContext());
        cbNextAction.setText("Next Action");
        cbNextAction.setTextColor(Color.parseColor("#FBBF24"));
        cbNextAction.setTextSize(13);
        cbNextAction.setChecked(selectedIsNextAction);
        cbNextAction.setOnCheckedChangeListener((btn, checked) -> selectedIsNextAction = checked);
        LinearLayout.LayoutParams naLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cbNextAction.setLayoutParams(naLp);
        gtdRow.addView(cbNextAction);

        CheckBox cbMIT = new CheckBox(requireContext());
        cbMIT.setText("MIT (Most Important)");
        cbMIT.setTextColor(Color.parseColor("#F43F5E"));
        cbMIT.setTextSize(13);
        cbMIT.setChecked(selectedIsMIT);
        cbMIT.setOnCheckedChangeListener((btn, checked) -> selectedIsMIT = checked);
        LinearLayout.LayoutParams mitLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cbMIT.setLayoutParams(mitLp);
        gtdRow.addView(cbMIT);

        expandedSection.addView(gtdRow);

        // ── Context Tag row ──
        LinearLayout ctxRow = new LinearLayout(requireContext());
        ctxRow.setOrientation(LinearLayout.HORIZONTAL);
        ctxRow.setPadding(0, dp(6), 0, dp(6));
        ctxRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView ctxLabel = new TextView(requireContext());
        ctxLabel.setText("Context");
        ctxLabel.setTextSize(13);
        ctxLabel.setTextColor(Color.parseColor("#94A3B8"));
        ctxLabel.setLayoutParams(new LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT));
        ctxRow.addView(ctxLabel);

        String[] contextOptions = {"None", Task.CONTEXT_HOME, Task.CONTEXT_COMPUTER,
                Task.CONTEXT_PHONE, Task.CONTEXT_ERRANDS, Task.CONTEXT_ANYWHERE};
        for (String ctx : contextOptions) {
            TextView chip = new TextView(requireContext());
            chip.setText(ctx.equals("None") ? "None" : ctx);
            chip.setTextSize(11);
            chip.setPadding(dp(8), dp(4), dp(8), dp(4));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.setMarginEnd(dp(4));
            chip.setLayoutParams(chipLp);

            final String ctxVal = ctx.equals("None") ? null : ctx;
            boolean isActive = (selectedContextTag == null && ctxVal == null)
                    || (selectedContextTag != null && selectedContextTag.equals(ctxVal));
            styleContextChip(chip, isActive, ctxVal);

            chip.setOnClickListener(v -> {
                selectedContextTag = ctxVal;
                // Refresh all chips
                for (int i = 1; i < ctxRow.getChildCount(); i++) {
                    View child = ctxRow.getChildAt(i);
                    if (child instanceof TextView) {
                        String val = child.getTag() instanceof String ? (String) child.getTag() : null;
                        boolean active = (selectedContextTag == null && val == null)
                                || (selectedContextTag != null && selectedContextTag.equals(val));
                        styleContextChip((TextView) child, active, val);
                    }
                }
            });
            chip.setTag(ctxVal);
            ctxRow.addView(chip);
        }
        expandedSection.addView(ctxRow);

        // ── Waiting-For row ──
        LinearLayout waitRow = new LinearLayout(requireContext());
        waitRow.setOrientation(LinearLayout.HORIZONTAL);
        waitRow.setPadding(0, dp(6), 0, dp(6));
        waitRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView waitLabel = new TextView(requireContext());
        waitLabel.setText("Waiting For");
        waitLabel.setTextSize(13);
        waitLabel.setTextColor(Color.parseColor("#94A3B8"));
        waitLabel.setLayoutParams(new LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT));
        waitRow.addView(waitLabel);

        EditText etWaitingFor = new EditText(requireContext());
        etWaitingFor.setHint("Person or event...");
        etWaitingFor.setHintTextColor(Color.parseColor("#4B5563"));
        etWaitingFor.setTextColor(Color.parseColor("#F59E0B"));
        etWaitingFor.setTextSize(13);
        etWaitingFor.setBackground(null);
        etWaitingFor.setSingleLine(true);
        if (selectedWaitingFor != null) etWaitingFor.setText(selectedWaitingFor);
        etWaitingFor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String val = s.toString().trim();
                selectedWaitingFor = val.isEmpty() ? null : val;
            }
        });
        etWaitingFor.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        waitRow.addView(etWaitingFor);
        expandedSection.addView(waitRow);

        // ── Assigned To row ──
        LinearLayout assignRow = new LinearLayout(requireContext());
        assignRow.setOrientation(LinearLayout.HORIZONTAL);
        assignRow.setPadding(0, dp(6), 0, dp(6));
        assignRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView assignLabel = new TextView(requireContext());
        assignLabel.setText("Assigned To");
        assignLabel.setTextSize(13);
        assignLabel.setTextColor(Color.parseColor("#94A3B8"));
        assignLabel.setLayoutParams(new LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT));
        assignRow.addView(assignLabel);

        String[] assignOptions = {"None", "Mobile", "PC"};
        for (String opt : assignOptions) {
            TextView chip = new TextView(requireContext());
            chip.setText(opt);
            chip.setTextSize(12);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            aLp.setMarginEnd(dp(6));
            chip.setLayoutParams(aLp);

            final String assignVal = opt.equals("None") ? null : opt;
            boolean isActive = (selectedAssignedTo == null && assignVal == null)
                    || (selectedAssignedTo != null && selectedAssignedTo.equals(assignVal));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            bg.setColor(isActive ? Color.parseColor("#1A8B5CF6") : Color.parseColor("#1A64748B"));
            bg.setStroke(1, isActive ? Color.parseColor("#8B5CF6") : Color.parseColor("#334155"));
            chip.setBackground(bg);
            chip.setTextColor(isActive ? Color.parseColor("#8B5CF6") : Color.parseColor("#94A3B8"));

            chip.setOnClickListener(v -> {
                selectedAssignedTo = assignVal;
                for (int i = 1; i < assignRow.getChildCount(); i++) {
                    View child = assignRow.getChildAt(i);
                    if (child instanceof TextView) {
                        String val = child.getTag() instanceof String ? (String) child.getTag() : null;
                        boolean active = (selectedAssignedTo == null && val == null)
                                || (selectedAssignedTo != null && selectedAssignedTo.equals(val));
                        GradientDrawable cbg = new GradientDrawable();
                        cbg.setCornerRadius(dp(12));
                        cbg.setColor(active ? Color.parseColor("#1A8B5CF6") : Color.parseColor("#1A64748B"));
                        cbg.setStroke(1, active ? Color.parseColor("#8B5CF6") : Color.parseColor("#334155"));
                        child.setBackground(cbg);
                        ((TextView) child).setTextColor(active ? Color.parseColor("#8B5CF6") : Color.parseColor("#94A3B8"));
                    }
                }
            });
            chip.setTag(assignVal);
            assignRow.addView(chip);
        }
        expandedSection.addView(assignRow);

        // ── Private toggle ──
        CheckBox cbPrivate = new CheckBox(requireContext());
        cbPrivate.setText("🔒 Private (Vault)");
        cbPrivate.setTextColor(Color.parseColor("#94A3B8"));
        cbPrivate.setTextSize(13);
        cbPrivate.setPadding(0, dp(6), 0, dp(6));
        cbPrivate.setChecked(selectedIsPrivate);
        cbPrivate.setOnCheckedChangeListener((btn, checked) -> selectedIsPrivate = checked);
        expandedSection.addView(cbPrivate);
    }

    private void styleContextChip(TextView chip, boolean active, String ctxVal) {
        int color = ctxVal != null ? Task.getContextTagColor(ctxVal) : Color.parseColor("#64748B");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (active) {
            bg.setColor(color & 0x33FFFFFF); // 20% alpha
            bg.setStroke(1, color);
            chip.setTextColor(color);
        } else {
            bg.setColor(Color.parseColor("#1A64748B"));
            bg.setStroke(1, Color.parseColor("#334155"));
            chip.setTextColor(Color.parseColor("#64748B"));
        }
        chip.setBackground(bg);
    }

    // ─── Buttons ─────────────────────────────────────────────────

    private void setupButtons() {
        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> saveTask());
    }

    // ─── Feature 12: Sentiment Priority Detection ────────────────

    private android.os.Handler sentimentHandler;
    private Runnable sentimentRunnable;
    private TextView tvPrioritySuggestion;

    private void setupSentimentPriorityDetection() {
        TaskManagerSettings settings = TaskManagerSettings.getInstance(requireContext());
        if (!settings.sentimentPriorityDetection) return;

        sentimentHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (sentimentRunnable != null) sentimentHandler.removeCallbacks(sentimentRunnable);
                sentimentRunnable = () -> checkSentimentPriority(s.toString());
                sentimentHandler.postDelayed(sentimentRunnable, 800);
            }
        });
    }

    private void checkSentimentPriority(String title) {
        if (title == null || title.trim().isEmpty()) return;

        String desc = etDescription != null ? etDescription.getText().toString() : "";
        String suggested = SmartDetectionHelper.suggestPriority(title, desc);
        if (suggested == null) return;

        // Check if suggestion is higher than current priority
        int suggestedWeight = Task.getPriorityWeightFor(suggested);
        int currentWeight = Task.getPriorityWeightFor(selectedPriority);
        if (suggestedWeight <= currentWeight) return;

        // Show suggestion banner below title
        showPrioritySuggestion(suggested);
    }

    private void showPrioritySuggestion(String suggested) {
        if (tvPrioritySuggestion == null) {
            tvPrioritySuggestion = new TextView(requireContext());
            tvPrioritySuggestion.setTextSize(12);
            tvPrioritySuggestion.setTextColor(Color.parseColor("#F59E0B"));
            tvPrioritySuggestion.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(Color.parseColor("#1A1F35"));
            bg.setStroke(dp(1), Color.parseColor("#F59E0B40"));
            tvPrioritySuggestion.setBackground(bg);

            // Insert it after the title field
            if (etTitle.getParent() instanceof android.view.ViewGroup) {
                android.view.ViewGroup parent = (android.view.ViewGroup) etTitle.getParent();
                int idx = parent.indexOfChild(etTitle);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(4), 0, dp(4));
                parent.addView(tvPrioritySuggestion, idx + 1, lp);
            }
        }

        String label = suggested.substring(0, 1).toUpperCase() + suggested.substring(1);
        tvPrioritySuggestion.setText("\u26a0\ufe0f Looks " + label + " priority \u2014 Tap to apply");
        tvPrioritySuggestion.setVisibility(View.VISIBLE);
        tvPrioritySuggestion.setAlpha(0f);
        tvPrioritySuggestion.animate().alpha(1f).setDuration(250).start();

        tvPrioritySuggestion.setOnClickListener(v -> {
            selectedPriority = suggested;
            updatePriorityVisuals();
            tvPrioritySuggestion.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> tvPrioritySuggestion.setVisibility(View.GONE))
                    .start();
        });

        // Auto-dismiss after 8s
        if (sentimentHandler != null) {
            sentimentHandler.postDelayed(() -> {
                if (tvPrioritySuggestion != null && tvPrioritySuggestion.getVisibility() == View.VISIBLE) {
                    tvPrioritySuggestion.animate().alpha(0f).setDuration(200)
                            .withEndAction(() -> tvPrioritySuggestion.setVisibility(View.GONE))
                            .start();
                }
            }, 8000);
        }
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
        task.colorLabel = selectedColorLabel;
        task.startDate = selectedStartDate;
        task.effortPoints = selectedEffortPoints;

        // Batch 4 fields
        task.isNextAction = selectedIsNextAction;
        task.isMIT = selectedIsMIT;
        task.contextTag = selectedContextTag;
        task.waitingFor = selectedWaitingFor;
        task.assignedTo = selectedAssignedTo;
        task.isPrivate = selectedIsPrivate;

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
        selectedColorLabel = task.colorLabel;
        selectedStartDate = task.startDate;
        selectedEffortPoints = task.effortPoints;

        // Batch 4 fields
        selectedIsNextAction = task.isNextAction;
        selectedIsMIT = task.isMIT;
        selectedContextTag = task.contextTag;
        selectedWaitingFor = task.waitingFor;
        selectedAssignedTo = task.assignedTo;
        selectedIsPrivate = task.isPrivate;
        selectedProjectId = task.projectId;

        if (etNotes != null) etNotes.setText(task.notes != null ? task.notes : "");

        // Update all visuals
        updatePriorityVisuals();
        updateColorSwatchVisual();
        if (tvColorNone != null) tvColorNone.setVisibility(selectedColorLabel != null ? View.VISIBLE : View.GONE);
        updateCategoryVisuals();
        buildTagChips();
        updateDateTimeChips();
        updateStartDateChip();
        buildReminderChips();
        updateRecurrenceChip();
        updateDurationChip();
        updateEffortChipsVisuals();
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
