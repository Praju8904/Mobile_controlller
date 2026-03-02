package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.tasks.SubtaskItem;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
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
 * BottomSheetDialogFragment for creating and editing {@link TodoItem}s.
 *
 * <p>Quick mode shows title + "Show more" toggle. Expanded mode reveals
 * description, priority, due date/time, reminder, recurrence, tags,
 * estimated duration, and subtasks.
 */
public class TodoItemEditorSheet extends BottomSheetDialogFragment {

    // ─── Constants ───────────────────────────────────────────────

    public static final String TAG = "TodoItemEditorSheet";
    public static final String ARG_LIST_ID = "list_id";
    public static final String ARG_ITEM_ID = "item_id"; // null for new items

    // ─── Interface ───────────────────────────────────────────────

    public interface OnItemSavedListener {
        void onItemSaved(TodoItem item);
    }

    // ─── Fields ──────────────────────────────────────────────────

    private OnItemSavedListener savedListener;
    private TodoRepository repo;
    private TodoItem editingItem;
    private boolean isNewItem = true;
    private boolean expanded = false;

    // Editing state
    private String selectedPriority = TodoItem.PRIORITY_NONE;
    private List<String> selectedTags = new ArrayList<>();
    private String selectedDueDate = null;   // "yyyy-MM-dd"
    private String selectedDueTime = null;   // "HH:mm"
    private long selectedReminderMs = 0;
    private String selectedRecurrence = TodoItem.RECURRENCE_NONE;
    private int selectedDurationMinutes = 0;
    private List<SubtaskItem> editSubtasks = new ArrayList<>();

    // Views — built programmatically
    private EditText etTitle;
    private EditText etDescription;
    private TextView btnExpand;
    private LinearLayout detailContainer;

    // Priority pills
    private TextView pillNone, pillLow, pillMedium, pillHigh, pillUrgent;

    // Date / time
    private TextView btnDueDate;
    private TextView btnDueTime;

    // Reminder
    private TextView btnReminder;

    // Recurrence
    private Spinner spinnerRecurrence;

    // Tags
    private EditText etTagInput;
    private LinearLayout tagChipsContainer;

    // Duration
    private TextView chipDuration30m, chipDuration1h, chipDuration2h, chipDurationCustom;

    // Subtasks
    private EditText etNewSubtask;
    private LinearLayout subtasksContainer;

    // Save
    private TextView btnSave;

    // ─── Factory Methods ─────────────────────────────────────────

    /** Creates a sheet for adding a new item to the given list. */
    public static TodoItemEditorSheet newInstance(String listId) {
        return newInstance(listId, null);
    }

    /**
     * Creates a sheet for the given list. Pass {@code null} for {@code itemId}
     * to create a new item, or a valid id to edit an existing one.
     */
    public static TodoItemEditorSheet newInstance(String listId, @Nullable String itemId) {
        TodoItemEditorSheet sheet = new TodoItemEditorSheet();
        Bundle args = new Bundle();
        args.putString(ARG_LIST_ID, listId);
        if (itemId != null) args.putString(ARG_ITEM_ID, itemId);
        sheet.setArguments(args);
        return sheet;
    }

    /** Creates a sheet for editing an existing item. */
    public static TodoItemEditorSheet newInstanceEdit(String listId, String itemId) {
        return newInstance(listId, itemId);
    }

    public void setListener(OnItemSavedListener listener) {
        this.savedListener = listener;
    }

    public void setOnItemSavedListener(OnItemSavedListener listener) {
        this.savedListener = listener;
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
        return buildUi();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = new TodoRepository(requireContext());

        String itemId = getArguments() != null ? getArguments().getString(ARG_ITEM_ID) : null;
        if (itemId != null) {
            editingItem = repo.getItemById(itemId);
            if (editingItem != null) {
                isNewItem = false;
                populateFromItem(editingItem);
                toggleExpanded(true);
            }
        }

        etTitle.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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

    // ─── UI Builder ──────────────────────────────────────────────

    private View buildUi() {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setBackgroundColor(Color.parseColor("#1E293B"));

        // Handle bar
        View handle = new View(requireContext());
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setShape(GradientDrawable.RECTANGLE);
        handleBg.setCornerRadius(dp(2));
        handleBg.setColor(Color.parseColor("#475569"));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(16);
        handle.setLayoutParams(handleLp);
        root.addView(handle);

        // Title
        etTitle = new EditText(requireContext());
        etTitle.setHint("Task title");
        etTitle.setHintTextColor(Color.parseColor("#64748B"));
        etTitle.setTextColor(Color.parseColor("#F1F5F9"));
        etTitle.setTextSize(20);
        etTitle.setTypeface(null, Typeface.BOLD);
        etTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etTitle.setBackground(makeDarkFieldBg());
        etTitle.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(12);
        etTitle.setLayoutParams(titleLp);
        root.addView(etTitle);

        // Expand toggle
        btnExpand = new TextView(requireContext());
        btnExpand.setText("Show more ▼");
        btnExpand.setTextColor(Color.parseColor("#60A5FA"));
        btnExpand.setTextSize(13);
        btnExpand.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams expandLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        expandLp.bottomMargin = dp(12);
        btnExpand.setLayoutParams(expandLp);
        btnExpand.setOnClickListener(v -> toggleExpanded(!expanded));
        root.addView(btnExpand);

        // ── Detail container ──────────────────────────────────
        detailContainer = new LinearLayout(requireContext());
        detailContainer.setOrientation(LinearLayout.VERTICAL);
        detailContainer.setVisibility(View.GONE);
        root.addView(detailContainer);

        // Description
        etDescription = new EditText(requireContext());
        etDescription.setHint("Description (optional)");
        etDescription.setHintTextColor(Color.parseColor("#64748B"));
        etDescription.setTextColor(Color.parseColor("#CBD5E1"));
        etDescription.setTextSize(14);
        etDescription.setMinLines(2);
        etDescription.setGravity(Gravity.TOP);
        etDescription.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etDescription.setBackground(makeDarkFieldBg());
        etDescription.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.bottomMargin = dp(14);
        etDescription.setLayoutParams(descLp);
        detailContainer.addView(etDescription);

        // Priority section label
        detailContainer.addView(makeSectionLabel("Priority"));
        detailContainer.addView(buildPriorityRow());

        // Due date / time
        detailContainer.addView(makeSectionLabel("Due Date"));
        btnDueDate = makePillButton("📅  No due date", "#64748B");
        btnDueDate.setOnClickListener(v -> setupDueDatePicker());
        detailContainer.addView(btnDueDate);

        btnDueTime = makePillButton("🕐  No time", "#64748B");
        btnDueTime.setVisibility(View.GONE);
        btnDueTime.setOnClickListener(v -> setupDueTimePicker());
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(6);
        timeLp.bottomMargin = dp(14);
        btnDueTime.setLayoutParams(timeLp);
        detailContainer.addView(btnDueTime);

        // Reminder
        detailContainer.addView(makeSectionLabel("Reminder"));
        btnReminder = makePillButton("🔔  No reminder", "#64748B");
        btnReminder.setOnClickListener(v -> setupReminderPicker());
        detailContainer.addView(btnReminder);

        // Recurrence
        detailContainer.addView(makeSectionLabel("Repeat"));
        spinnerRecurrence = new Spinner(requireContext());
        String[] recurrenceOptions = {
            "Does not repeat", "Daily", "Weekly", "Monthly", "Yearly"
        };
        ArrayAdapter<String> recAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, recurrenceOptions);
        recAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRecurrence.setAdapter(recAdapter);
        spinnerRecurrence.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        spinLp.bottomMargin = dp(14);
        spinnerRecurrence.setLayoutParams(spinLp);
        detailContainer.addView(spinnerRecurrence);

        // Tags
        detailContainer.addView(makeSectionLabel("Tags"));
        detailContainer.addView(buildTagInputRow());
        tagChipsContainer = new LinearLayout(requireContext());
        tagChipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tagChipsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagChipsLp.bottomMargin = dp(14);
        tagChipsContainer.setLayoutParams(tagChipsLp);
        detailContainer.addView(tagChipsContainer);

        // Duration
        detailContainer.addView(makeSectionLabel("Estimated Duration"));
        detailContainer.addView(buildDurationRow());

        // Subtasks
        detailContainer.addView(makeSectionLabel("Subtasks"));
        detailContainer.addView(buildSubtaskInputRow());
        subtasksContainer = new LinearLayout(requireContext());
        subtasksContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams subtasksLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtasksLp.bottomMargin = dp(16);
        subtasksContainer.setLayoutParams(subtasksLp);
        detailContainer.addView(subtasksContainer);

        // ── Save button ───────────────────────────────────────
        btnSave = new TextView(requireContext());
        btnSave.setText("Save Task");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(16);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setCornerRadius(dp(12));
        saveBg.setColor(Color.parseColor("#3B82F6"));
        btnSave.setBackground(saveBg);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        saveLp.topMargin = dp(8);
        btnSave.setLayoutParams(saveLp);
        btnSave.setPadding(0, 0, 0, 0);
        btnSave.setOnClickListener(v -> saveItem());
        root.addView(btnSave);

        scroll.addView(root);
        return scroll;
    }

    // ─── Priority Picker ─────────────────────────────────────────

    private View buildPriorityRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(14);
        row.setLayoutParams(rowLp);

        pillNone   = makePriorityPill("None",   "#374151");
        pillLow    = makePriorityPill("Low",    "#3B82F6");
        pillMedium = makePriorityPill("Medium", "#F97316");
        pillHigh   = makePriorityPill("High",   "#EF4444");
        pillUrgent = makePriorityPill("Urgent", "#EF4444");

        row.addView(pillNone);
        row.addView(pillLow);
        row.addView(pillMedium);
        row.addView(pillHigh);
        row.addView(pillUrgent);

        setupPriorityPicker();

        return row;
    }

    private TextView makePriorityPill(String label, String colorHex) {
        TextView pill = new TextView(requireContext());
        pill.setText(label);
        pill.setTextSize(12);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(4));
        pill.setLayoutParams(lp);
        pill.setTag(colorHex);
        return pill;
    }

    private void setupPriorityPicker() {
        View.OnClickListener click = v -> {
            if (v == pillNone)   selectedPriority = TodoItem.PRIORITY_NONE;
            else if (v == pillLow)    selectedPriority = TodoItem.PRIORITY_LOW;
            else if (v == pillMedium) selectedPriority = TodoItem.PRIORITY_MEDIUM;
            else if (v == pillHigh)   selectedPriority = TodoItem.PRIORITY_HIGH;
            else if (v == pillUrgent) selectedPriority = TodoItem.PRIORITY_URGENT;
            updatePriorityPills();
        };
        pillNone.setOnClickListener(click);
        pillLow.setOnClickListener(click);
        pillMedium.setOnClickListener(click);
        pillHigh.setOnClickListener(click);
        pillUrgent.setOnClickListener(click);
        updatePriorityPills();
    }

    private void updatePriorityPills() {
        updatePill(pillNone,   TodoItem.PRIORITY_NONE,   "#374151");
        updatePill(pillLow,    TodoItem.PRIORITY_LOW,    "#3B82F6");
        updatePill(pillMedium, TodoItem.PRIORITY_MEDIUM, "#F97316");
        updatePill(pillHigh,   TodoItem.PRIORITY_HIGH,   "#EF4444");
        updatePill(pillUrgent, TodoItem.PRIORITY_URGENT, "#EF4444");
    }

    private void updatePill(TextView pill, String priority, String colorHex) {
        boolean active = priority.equals(selectedPriority);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        if (active) {
            bg.setColor(Color.parseColor(colorHex));
            pill.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setStroke(dp(1), Color.parseColor("#334155"));
            pill.setTextColor(Color.parseColor("#94A3B8"));
        }
        pill.setBackground(bg);
    }

    // ─── Due Date Picker ─────────────────────────────────────────

    private void setupDueDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            try {
                String[] p = selectedDueDate.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {}
        }
        new DatePickerDialog(requireContext(), (dp2, y, m, d) -> {
            selectedDueDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            updateDateTimeButtons();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ─── Due Time Picker ─────────────────────────────────────────

    private void setupDueTimePicker() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        if (selectedDueTime != null) {
            try {
                String[] p = selectedDueTime.split(":");
                hour = Integer.parseInt(p[0]);
                minute = Integer.parseInt(p[1]);
            } catch (Exception ignored) {}
        }
        new TimePickerDialog(requireContext(), (tp, h, min) -> {
            selectedDueTime = String.format(Locale.US, "%02d:%02d", h, min);
            updateDateTimeButtons();
        }, hour, minute, true).show();
    }

    private void updateDateTimeButtons() {
        if (selectedDueDate != null) {
            try {
                String[] p = selectedDueDate.split("-");
                Calendar c = Calendar.getInstance();
                c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                String formatted = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(c.getTime());
                btnDueDate.setText("📅  " + formatted);
                btnDueDate.setTextColor(Color.parseColor("#60A5FA"));
            } catch (Exception e) {
                btnDueDate.setText("📅  " + selectedDueDate);
                btnDueDate.setTextColor(Color.parseColor("#60A5FA"));
            }
            btnDueTime.setVisibility(View.VISIBLE);
        } else {
            btnDueDate.setText("📅  No due date");
            btnDueDate.setTextColor(Color.parseColor("#64748B"));
            btnDueTime.setVisibility(View.GONE);
            selectedDueTime = null;
        }

        if (selectedDueTime != null) {
            btnDueTime.setText("🕐  " + formatTime(selectedDueTime));
            btnDueTime.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            btnDueTime.setText("🕐  No time");
            btnDueTime.setTextColor(Color.parseColor("#64748B"));
        }
    }

    // ─── Reminder Picker ─────────────────────────────────────────

    private void setupReminderPicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            try {
                String[] p = selectedDueDate.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {}
        }
        new DatePickerDialog(requireContext(), (dp2, y, m, d) -> {
            Calendar dateCal = Calendar.getInstance();
            dateCal.set(y, m, d);
            new TimePickerDialog(requireContext(), (tp, h, min) -> {
                dateCal.set(Calendar.HOUR_OF_DAY, h);
                dateCal.set(Calendar.MINUTE, min);
                dateCal.set(Calendar.SECOND, 0);
                selectedReminderMs = dateCal.getTimeInMillis();
                updateReminderButton();
            }, dateCal.get(Calendar.HOUR_OF_DAY), dateCal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateReminderButton() {
        if (selectedReminderMs > 0) {
            String formatted = new SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                    .format(new Date(selectedReminderMs));
            btnReminder.setText("🔔  " + formatted);
            btnReminder.setTextColor(Color.parseColor("#F59E0B"));
        } else {
            btnReminder.setText("🔔  No reminder");
            btnReminder.setTextColor(Color.parseColor("#64748B"));
        }
    }

    // ─── Tags ────────────────────────────────────────────────────

    private View buildTagInputRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(6);
        row.setLayoutParams(rowLp);

        etTagInput = new EditText(requireContext());
        etTagInput.setHint("Add tag…");
        etTagInput.setHintTextColor(Color.parseColor("#64748B"));
        etTagInput.setTextColor(Color.parseColor("#CBD5E1"));
        etTagInput.setTextSize(13);
        etTagInput.setBackground(makeDarkFieldBg());
        etTagInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etLp.setMarginEnd(dp(8));
        etTagInput.setLayoutParams(etLp);
        row.addView(etTagInput);

        TextView addBtn = new TextView(requireContext());
        addBtn.setText("Add");
        addBtn.setTextColor(Color.parseColor("#60A5FA"));
        addBtn.setTextSize(13);
        addBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        addBtn.setBackground(makeDarkFieldBg());
        addBtn.setOnClickListener(v -> {
            String tag = etTagInput.getText().toString().trim();
            if (!tag.isEmpty()) addTag(tag);
        });
        row.addView(addBtn);

        etTagInput.setOnEditorActionListener((v, actionId, event) -> {
            String tag = etTagInput.getText().toString().trim();
            if (!tag.isEmpty()) addTag(tag);
            return true;
        });

        return row;
    }

    private void addTag(String tag) {
        if (tag.isEmpty() || selectedTags.contains(tag)) return;
        selectedTags.add(tag);
        etTagInput.setText("");
        buildTagChips();
    }

    private void buildTagChips() {
        tagChipsContainer.removeAllViews();
        for (String tag : selectedTags) {
            TextView chip = new TextView(requireContext());
            chip.setText(tag + "  ✕");
            chip.setTextSize(12);
            chip.setTextColor(Color.parseColor("#60A5FA"));
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setShape(GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(dp(14));
            chipBg.setColor(Color.parseColor("#1A3B82F6"));
            chipBg.setStroke(dp(1), Color.parseColor("#3B82F6"));
            chip.setBackground(chipBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                selectedTags.remove(tag);
                buildTagChips();
            });
            tagChipsContainer.addView(chip);
        }
    }

    // ─── Duration Chips ──────────────────────────────────────────

    private View buildDurationRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(14);
        row.setLayoutParams(rowLp);

        chipDuration30m      = makeDurationChip("30m",    30);
        chipDuration1h       = makeDurationChip("1h",     60);
        chipDuration2h       = makeDurationChip("2h",    120);
        chipDurationCustom   = makeDurationChip("Custom", -1);

        row.addView(chipDuration30m);
        row.addView(chipDuration1h);
        row.addView(chipDuration2h);
        row.addView(chipDurationCustom);

        chipDurationCustom.setOnClickListener(v -> showCustomDurationDialog());

        updateDurationChips();
        return row;
    }

    private TextView makeDurationChip(String label, int minutes) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        if (minutes > 0) {
            chip.setOnClickListener(v -> {
                selectedDurationMinutes = minutes;
                updateDurationChips();
            });
        }
        return chip;
    }

    private void showCustomDurationDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes");
        input.setTextColor(Color.parseColor("#F1F5F9"));
        input.setHintTextColor(Color.parseColor("#64748B"));
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Custom Duration (minutes)")
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int mins = Integer.parseInt(input.getText().toString().trim());
                        if (mins > 0) {
                            selectedDurationMinutes = mins;
                            chipDurationCustom.setText(mins < 60
                                    ? mins + "m" : (mins / 60) + "h" + (mins % 60 > 0 ? (mins % 60) + "m" : ""));
                            updateDurationChips();
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateDurationChips() {
        styleInactiveDurationChip(chipDuration30m);
        styleInactiveDurationChip(chipDuration1h);
        styleInactiveDurationChip(chipDuration2h);
        styleInactiveDurationChip(chipDurationCustom);

        if (selectedDurationMinutes == 30) styleActiveDurationChip(chipDuration30m);
        else if (selectedDurationMinutes == 60) styleActiveDurationChip(chipDuration1h);
        else if (selectedDurationMinutes == 120) styleActiveDurationChip(chipDuration2h);
        else if (selectedDurationMinutes > 0) styleActiveDurationChip(chipDurationCustom);
    }

    private void styleActiveDurationChip(TextView chip) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#3B82F6"));
        chip.setBackground(bg);
        chip.setTextColor(Color.WHITE);
    }

    private void styleInactiveDurationChip(TextView chip) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        chip.setBackground(bg);
        chip.setTextColor(Color.parseColor("#94A3B8"));
    }

    // ─── Subtasks ────────────────────────────────────────────────

    private View buildSubtaskInputRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);

        etNewSubtask = new EditText(requireContext());
        etNewSubtask.setHint("Add subtask…");
        etNewSubtask.setHintTextColor(Color.parseColor("#64748B"));
        etNewSubtask.setTextColor(Color.parseColor("#CBD5E1"));
        etNewSubtask.setTextSize(13);
        etNewSubtask.setBackground(makeDarkFieldBg());
        etNewSubtask.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etLp.setMarginEnd(dp(8));
        etNewSubtask.setLayoutParams(etLp);
        row.addView(etNewSubtask);

        TextView addBtn = new TextView(requireContext());
        addBtn.setText("Add");
        addBtn.setTextColor(Color.parseColor("#60A5FA"));
        addBtn.setTextSize(13);
        addBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        addBtn.setBackground(makeDarkFieldBg());
        addBtn.setOnClickListener(v -> {
            String title = etNewSubtask.getText().toString().trim();
            if (!title.isEmpty()) addSubtask(title);
        });
        row.addView(addBtn);

        etNewSubtask.setOnEditorActionListener((v, actionId, event) -> {
            String title = etNewSubtask.getText().toString().trim();
            if (!title.isEmpty()) addSubtask(title);
            return true;
        });

        return row;
    }

    private void addSubtask(String title) {
        String parentId = (editingItem != null) ? editingItem.id : "";
        SubtaskItem sub = new SubtaskItem(parentId, title);
        editSubtasks.add(sub);
        etNewSubtask.setText("");
        buildSubtaskViews();
    }

    private void buildSubtaskViews() {
        subtasksContainer.removeAllViews();
        for (int i = 0; i < editSubtasks.size(); i++) {
            SubtaskItem sub = editSubtasks.get(i);
            final int idx = i;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            row.setLayoutParams(rowLp);

            CheckBox cb = new CheckBox(requireContext());
            cb.setChecked(sub.isCompleted);
            cb.setOnCheckedChangeListener((btn, checked) -> sub.isCompleted = checked);
            row.addView(cb);

            EditText etSubTitle = new EditText(requireContext());
            etSubTitle.setText(sub.title);
            etSubTitle.setTextColor(Color.parseColor("#CBD5E1"));
            etSubTitle.setTextSize(13);
            etSubTitle.setBackground(null);
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            etSubTitle.setLayoutParams(etLp);
            etSubTitle.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) { sub.title = s.toString(); }
            });
            row.addView(etSubTitle);

            TextView btnDelete = new TextView(requireContext());
            btnDelete.setText("✕");
            btnDelete.setTextColor(Color.parseColor("#EF4444"));
            btnDelete.setTextSize(14);
            btnDelete.setPadding(dp(8), dp(4), dp(4), dp(4));
            btnDelete.setOnClickListener(v -> {
                editSubtasks.remove(idx);
                buildSubtaskViews();
            });
            row.addView(btnDelete);

            subtasksContainer.addView(row);
        }
    }

    // ─── Expand / Collapse ───────────────────────────────────────

    private void toggleExpanded(boolean show) {
        expanded = show;
        detailContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        btnExpand.setText(show ? "Show less ▲" : "Show more ▼");
    }

    // ─── Save ────────────────────────────────────────────────────

    private void saveItem() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }

        String listId = getArguments() != null ? getArguments().getString(ARG_LIST_ID, "") : "";

        TodoItem item;
        if (isNewItem) {
            item = new TodoItem(listId, title);
        } else {
            item = editingItem;
            item.title = title;
        }

        item.description = etDescription.getText().toString().trim();
        item.priority = selectedPriority;
        item.dueDate = selectedDueDate != null ? selectedDueDate : "";
        item.dueTime = selectedDueTime != null ? selectedDueTime : "";
        item.reminderDateTime = selectedReminderMs;
        item.recurrence = recurrenceValueForSpinnerPos(spinnerRecurrence.getSelectedItemPosition());
        item.tags = new ArrayList<>(selectedTags);
        item.estimatedDurationMinutes = selectedDurationMinutes;
        item.subtasks = new ArrayList<>(editSubtasks);
        item.updatedAt = System.currentTimeMillis();

        if (isNewItem) {
            repo.addItem(item);
        } else {
            repo.updateItem(item);
        }

        if (selectedReminderMs > 0) {
            TodoNotificationHelper.scheduleReminder(requireContext(), item);
        }

        if (savedListener != null) savedListener.onItemSaved(item);
        dismiss();
    }

    private String recurrenceValueForSpinnerPos(int pos) {
        switch (pos) {
            case 1: return TodoItem.RECURRENCE_DAILY;
            case 2: return TodoItem.RECURRENCE_WEEKLY;
            case 3: return TodoItem.RECURRENCE_MONTHLY;
            case 4: return TodoItem.RECURRENCE_YEARLY;
            default: return TodoItem.RECURRENCE_NONE;
        }
    }

    private int spinnerPosForRecurrence(String recurrence) {
        if (recurrence == null) return 0;
        switch (recurrence) {
            case TodoItem.RECURRENCE_DAILY:   return 1;
            case TodoItem.RECURRENCE_WEEKLY:  return 2;
            case TodoItem.RECURRENCE_MONTHLY: return 3;
            case TodoItem.RECURRENCE_YEARLY:  return 4;
            default: return 0;
        }
    }

    // ─── Populate from existing item ─────────────────────────────

    private void populateFromItem(TodoItem item) {
        etTitle.setText(item.title);
        etDescription.setText(item.description != null ? item.description : "");

        selectedPriority = item.priority != null ? item.priority : TodoItem.PRIORITY_NONE;
        selectedDueDate = (item.dueDate != null && !item.dueDate.isEmpty()) ? item.dueDate : null;
        selectedDueTime = (item.dueTime != null && !item.dueTime.isEmpty()) ? item.dueTime : null;
        selectedReminderMs = item.reminderDateTime;
        selectedRecurrence = item.recurrence != null ? item.recurrence : TodoItem.RECURRENCE_NONE;
        selectedDurationMinutes = item.estimatedDurationMinutes;
        selectedTags = item.tags != null ? new ArrayList<>(item.tags) : new ArrayList<>();
        editSubtasks = new ArrayList<>();
        if (item.subtasks != null) {
            editSubtasks.addAll(item.subtasks);
        }

        updatePriorityPills();
        updateDateTimeButtons();
        updateReminderButton();
        spinnerRecurrence.setSelection(spinnerPosForRecurrence(selectedRecurrence));
        buildTagChips();
        updateDurationChips();
        buildSubtaskViews();

        btnSave.setText("Update Task");
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private TextView makeSectionLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makePillButton(String text, String textColorHex) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor(textColorHex));
        tv.setTextSize(13);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(makeDarkFieldBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        tv.setLayoutParams(lp);
        return tv;
    }

    private GradientDrawable makeDarkFieldBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.parseColor("#0F172A"));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        return bg;
    }

    private String formatTime(String time24) {
        try {
            String[] p = time24.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            String ampm = h >= 12 ? "PM" : "AM";
            if (h > 12) h -= 12;
            if (h == 0) h = 12;
            return String.format(Locale.US, "%d:%02d %s", h, m, ampm);
        } catch (Exception e) {
            return time24;
        }
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }
}
