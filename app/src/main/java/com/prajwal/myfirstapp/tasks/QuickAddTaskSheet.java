package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.SmartFeaturesHelper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Minimal quick-add bottom sheet for creating a task in ~2 seconds.
 *
 * Features: date chips (Today / Tomorrow / This Week), cycling priority chip,
 * smart-suggestion banner with 500 ms debounce, and a "More Options" path to
 * open the full {@link TaskEditorSheet} with the current draft pre-filled.
 */
public class QuickAddTaskSheet extends BottomSheetDialogFragment {

    // ─── Interface ───────────────────────────────────────────────

    public interface QuickAddListener {
        void onTaskQuickAdded(Task task);
        void onOpenFullEditor(Task draft);
    }

    // ─── Constants ───────────────────────────────────────────────

    private static final String ARG_DRAFT_TITLE    = "draft_title";
    private static final String ARG_DRAFT_PRIORITY = "draft_priority";
    private static final String ARG_DRAFT_DUE_DATE = "draft_due_date";

    private static final int    DEBOUNCE_MS        = 500;

    private static final int    COLOR_CHIP_SELECTED   = 0xFF6C63FF; // accent purple
    private static final int    COLOR_CHIP_DESELECTED = 0xFF2A2A4A;
    private static final int    COLOR_TEXT_SELECTED   = 0xFFFFFFFF;
    private static final int    COLOR_TEXT_DESELECTED = 0xFF94A3B8;

    /** Ordered priority cycle for the priority chip. */
    private static final String[] PRIORITY_CYCLE = {
        Task.PRIORITY_NONE,
        Task.PRIORITY_LOW,
        Task.PRIORITY_NORMAL,
        Task.PRIORITY_HIGH,
        Task.PRIORITY_URGENT
    };

    // ─── Fields ──────────────────────────────────────────────────

    private QuickAddListener listener;
    private TaskRepository   repo;

    // State
    private String quickPriority = Task.PRIORITY_NORMAL;
    private String quickDueDate  = null;

    // Debounce
    private final Handler  debounceHandler  = new Handler(Looper.getMainLooper());
    private       Runnable debounceRunnable = null;

    // Views
    private EditText     etQuickTaskTitle;
    private TextView     btnQuickSave;
    private TextView     chipToday, chipTomorrow, chipThisWeek, chipPriority;
    private TextView     btnMoreOptions;

    // Smart suggestion banner (added programmatically below etQuickTaskTitle)
    private LinearLayout smartSuggestionBanner;
    private TextView     tvSmartSuggestion;
    private TextView     btnSmartAction;       // "View" button for similar-task case

    // Holds the most recent analysed suggestion so tap can apply it
    private SmartFeaturesHelper.SmartSuggestion pendingSuggestion = null;
    private Task                                 similarTask       = null;

    // ─── Factory Methods ─────────────────────────────────────────

    public static QuickAddTaskSheet newInstance() {
        return new QuickAddTaskSheet();
    }

    /**
     * Creates a sheet pre-populated with the supplied draft (title / priority /
     * dueDate), useful when returning from the full editor without saving.
     */
    public static QuickAddTaskSheet newInstanceWithDraft(Task draft) {
        QuickAddTaskSheet sheet = new QuickAddTaskSheet();
        if (draft != null) {
            Bundle args = new Bundle();
            args.putString(ARG_DRAFT_TITLE,    draft.title);
            args.putString(ARG_DRAFT_PRIORITY, draft.priority);
            args.putString(ARG_DRAFT_DUE_DATE, draft.dueDate);
            sheet.setArguments(args);
        }
        return sheet;
    }

    public void setListener(QuickAddListener listener) {
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
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_quick_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repo = new TaskRepository(requireContext());

        initViews(view);
        buildSmartSuggestionBanner(view);
        setupChips();
        setupButtons();
        setupSmartDebounce();

        // Restore draft values passed via arguments
        Bundle args = getArguments();
        if (args != null) {
            String draftTitle    = args.getString(ARG_DRAFT_TITLE);
            String draftPriority = args.getString(ARG_DRAFT_PRIORITY);
            String draftDueDate  = args.getString(ARG_DRAFT_DUE_DATE);

            if (draftTitle    != null) etQuickTaskTitle.setText(draftTitle);
            if (draftPriority != null) {
                quickPriority = draftPriority;
                updatePriorityChipText();
            }
            if (draftDueDate  != null) {
                quickDueDate = draftDueDate;
                syncDateChipHighlights();
            }
        }

        // Auto-focus + show keyboard
        etQuickTaskTitle.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Expand to full height
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog d = (BottomSheetDialog) getDialog();
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    @Override
    public void onDestroyView() {
        // Cancel any pending debounce to avoid memory leaks
        if (debounceRunnable != null) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
        super.onDestroyView();
    }

    // ─── View Init ───────────────────────────────────────────────

    private void initViews(View v) {
        etQuickTaskTitle = v.findViewById(R.id.etQuickTaskTitle);
        chipToday        = v.findViewById(R.id.chipToday);
        chipTomorrow     = v.findViewById(R.id.chipTomorrow);
        chipThisWeek     = v.findViewById(R.id.chipThisWeek);
        chipPriority     = v.findViewById(R.id.chipPriority);
        btnMoreOptions   = v.findViewById(R.id.btnMoreOptions);
        btnQuickSave     = v.findViewById(R.id.btnQuickSave);
    }

    // ─── Smart Suggestion Banner (programmatic) ──────────────────

    /**
     * Inserts a collapsible suggestion banner between the title EditText and
     * the divider line that follows it in the layout. If the XML already
     * contains a view with id {@code smartSuggestionBanner} we reuse it;
     * otherwise we add it dynamically.
     */
    private void buildSmartSuggestionBanner(View root) {
        // Try to find an existing banner in the XML first
        View existing = root.findViewById(R.id.smartSuggestionBanner);
        if (existing instanceof LinearLayout) {
            smartSuggestionBanner = (LinearLayout) existing;
            tvSmartSuggestion     = root.findViewById(R.id.tvSmartSuggestion);
            btnSmartAction        = root.findViewById(R.id.btnSmartAction);
            smartSuggestionBanner.setVisibility(View.GONE);
            return;
        }

        // Build the banner programmatically
        smartSuggestionBanner = new LinearLayout(requireContext());
        smartSuggestionBanner.setOrientation(LinearLayout.HORIZONTAL);
        smartSuggestionBanner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        smartSuggestionBanner.setVisibility(View.GONE);
        int hPad = dp(16);
        int vPad = dp(10);
        smartSuggestionBanner.setPadding(hPad, vPad, hPad, vPad);

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setShape(GradientDrawable.RECTANGLE);
        bannerBg.setCornerRadius(dp(10));
        bannerBg.setColor(0xFF1A1A3A);
        bannerBg.setStroke(dp(1), COLOR_CHIP_SELECTED);
        smartSuggestionBanner.setBackground(bannerBg);

        // Suggestion text (takes up available width)
        tvSmartSuggestion = new TextView(requireContext());
        tvSmartSuggestion.setTextColor(Color.parseColor("#CBD5E1"));
        tvSmartSuggestion.setTextSize(13f);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvSmartSuggestion.setLayoutParams(tvParams);
        smartSuggestionBanner.addView(tvSmartSuggestion);

        // Action button (e.g. "Apply" / "View")
        btnSmartAction = new TextView(requireContext());
        btnSmartAction.setTextColor(Color.parseColor("#6C63FF"));
        btnSmartAction.setTextSize(13f);
        btnSmartAction.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMarginStart(dp(8));
        btnSmartAction.setLayoutParams(btnParams);
        smartSuggestionBanner.addView(btnSmartAction);

        // Insert into the parent LinearLayout right after etQuickTaskTitle
        if (root instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) root;
            int titleIndex = indexOfChild(parent, etQuickTaskTitle);
            LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bannerParams.setMargins(dp(16), dp(4), dp(16), dp(8));
            smartSuggestionBanner.setLayoutParams(bannerParams);
            parent.addView(smartSuggestionBanner, titleIndex + 1);
        }
    }

    /** Returns the child index of {@code target} within {@code parent}, or -1. */
    private int indexOfChild(LinearLayout parent, View target) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) == target) return i;
        }
        return parent.getChildCount() - 1;
    }

    // ─── Chip Setup ──────────────────────────────────────────────

    private void setupChips() {
        chipToday.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            setQuickDueDate(formatDate(cal));
            syncDateChipHighlights();
        });

        chipTomorrow.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            setQuickDueDate(formatDate(cal));
            syncDateChipHighlights();
        });

        chipThisWeek.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            // Advance to the coming Friday of the current week
            cal.add(Calendar.DAY_OF_YEAR, 1);
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            setQuickDueDate(formatDate(cal));
            syncDateChipHighlights();
        });

        chipPriority.setOnClickListener(v -> cyclePriority());

        // Set initial deselected state for all chips
        setChipSelected(chipToday,     false);
        setChipSelected(chipTomorrow,  false);
        setChipSelected(chipThisWeek,  false);
        setChipSelected(chipPriority,  false);
        updatePriorityChipText();
    }

    // ─── Button Setup ────────────────────────────────────────────

    private void setupButtons() {
        btnQuickSave.setOnClickListener(v -> saveTask());
        btnMoreOptions.setOnClickListener(v -> openFullEditor());
    }

    // ─── Smart Suggestion Debounce ───────────────────────────────

    private void setupSmartDebounce() {
        etQuickTaskTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (debounceRunnable != null) {
                    debounceHandler.removeCallbacks(debounceRunnable);
                }
                debounceRunnable = () -> analyzeAndShowSuggestion(s.toString().trim());
                debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void analyzeAndShowSuggestion(String title) {
        if (title.isEmpty()) {
            hideSuggestionBanner();
            return;
        }

        // Run all analyses and pick the most confident suggestion
        SmartFeaturesHelper.SmartSuggestion dateSuggestion     = SmartFeaturesHelper.analyzeDueDate(title);
        SmartFeaturesHelper.SmartSuggestion prioritySuggestion = SmartFeaturesHelper.analyzePriority(title);
        SmartFeaturesHelper.SmartSuggestion categorySuggestion = SmartFeaturesHelper.analyzCategory(title);
        Task similar = SmartFeaturesHelper.findSimilarTask(requireContext(), title);

        // Similar-task banner takes precedence so the user doesn't create a duplicate
        if (similar != null) {
            similarTask       = similar;
            pendingSuggestion = null;
            showSimilarTaskBanner(similar);
            return;
        }
        similarTask = null;

        // Pick the highest-confidence actionable suggestion
        SmartFeaturesHelper.SmartSuggestion best = highestConfidence(
                dateSuggestion, prioritySuggestion, categorySuggestion);

        if (best != null) {
            pendingSuggestion = best;
            showSuggestionBanner(best);
        } else {
            pendingSuggestion = null;
            hideSuggestionBanner();
        }
    }

    private void showSuggestionBanner(SmartFeaturesHelper.SmartSuggestion suggestion) {
        tvSmartSuggestion.setText("💡 " + suggestion.displayText);
        btnSmartAction.setText("Apply");
        btnSmartAction.setVisibility(View.VISIBLE);

        smartSuggestionBanner.setOnClickListener(v -> applySuggestion(suggestion));
        btnSmartAction.setOnClickListener(v -> applySuggestion(suggestion));

        smartSuggestionBanner.setVisibility(View.VISIBLE);
    }

    private void showSimilarTaskBanner(Task similar) {
        String truncated = similar.title.length() > 30
                ? similar.title.substring(0, 27) + "..."
                : similar.title;
        tvSmartSuggestion.setText("Similar task exists: \"" + truncated + "\"");
        btnSmartAction.setText("View");
        btnSmartAction.setVisibility(View.VISIBLE);

        smartSuggestionBanner.setOnClickListener(null);
        btnSmartAction.setOnClickListener(v -> {
            if (listener != null) {
                Task draft = buildDraftTask();
                dismiss();
                listener.onOpenFullEditor(draft);
            }
        });

        smartSuggestionBanner.setVisibility(View.VISIBLE);
    }

    private void hideSuggestionBanner() {
        smartSuggestionBanner.setVisibility(View.GONE);
    }

    private void applySuggestion(SmartFeaturesHelper.SmartSuggestion suggestion) {
        if (suggestion == null) return;
        switch (suggestion.type) {
            case "date":
                quickDueDate = suggestion.suggestedDate;
                syncDateChipHighlights();
                break;
            case "priority":
                quickPriority = suggestion.suggestedPriority;
                updatePriorityChipText();
                break;
            // "category" is informational only in the quick-add sheet
        }
        hideSuggestionBanner();
    }

    // ─── Save / Open Full Editor ─────────────────────────────────

    private void saveTask() {
        String title = etQuickTaskTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show();
            etQuickTaskTitle.requestFocus();
            return;
        }

        Task task = new Task(title, quickPriority);
        task.dueDate = quickDueDate;

        repo.addTask(task);

        TaskNotificationHelper.scheduleTaskReminders(requireContext(), task);
        if (task.isOverdue()) {
            TaskNotificationHelper.scheduleOverdueAlert(requireContext(), task);
        }

        if (listener != null) listener.onTaskQuickAdded(task);
        dismiss();
    }

    private void openFullEditor() {
        Task draft = buildDraftTask();
        dismiss();
        if (listener != null) listener.onOpenFullEditor(draft);
    }

    /** Builds a transient Task from the current form state (not persisted). */
    private Task buildDraftTask() {
        String title = etQuickTaskTitle.getText().toString().trim();
        Task draft = new Task(title.isEmpty() ? "" : title, quickPriority);
        draft.dueDate = quickDueDate;
        return draft;
    }

    // ─── Chip Highlight Helpers ──────────────────────────────────

    /**
     * Sets the visual state of a chip TextView.
     *
     * @param chip     the chip view to update
     * @param selected true = accent purple background + white text;
     *                 false = dark background + grey text
     */
    private void setChipSelected(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(18));
        if (selected) {
            bg.setColor(COLOR_CHIP_SELECTED);
            chip.setTextColor(COLOR_TEXT_SELECTED);
        } else {
            bg.setColor(COLOR_CHIP_DESELECTED);
            chip.setTextColor(COLOR_TEXT_DESELECTED);
        }
        chip.setBackground(bg);
    }

    /** Sets quickDueDate and toggles the chip that matches today, tomorrow, or this Friday. */
    private void setQuickDueDate(String date) {
        // Tapping the already-selected date chip deselects it
        if (date.equals(quickDueDate)) {
            quickDueDate = null;
        } else {
            quickDueDate = date;
        }
    }

    /** Highlights exactly the chip that corresponds to quickDueDate, deselects others. */
    private void syncDateChipHighlights() {
        String today    = formatDate(Calendar.getInstance());
        Calendar tmCal  = Calendar.getInstance(); tmCal.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrow = formatDate(tmCal);
        Calendar fwCal  = Calendar.getInstance();
        fwCal.add(Calendar.DAY_OF_YEAR, 1);
        while (fwCal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            fwCal.add(Calendar.DAY_OF_YEAR, 1);
        }
        String thisWeekFriday = formatDate(fwCal);

        setChipSelected(chipToday,    today.equals(quickDueDate));
        setChipSelected(chipTomorrow, tomorrow.equals(quickDueDate));
        setChipSelected(chipThisWeek, thisWeekFriday.equals(quickDueDate));
    }

    // ─── Priority Chip Cycle ─────────────────────────────────────

    private void cyclePriority() {
        int currentIndex = indexOfPriority(quickPriority);
        quickPriority = PRIORITY_CYCLE[(currentIndex + 1) % PRIORITY_CYCLE.length];
        updatePriorityChipText();
    }

    private void updatePriorityChipText() {
        String label;
        boolean selected;
        switch (quickPriority) {
            case Task.PRIORITY_NONE:   label = "🔺 Priority"; selected = false; break;
            case Task.PRIORITY_LOW:    label = "🔵 Low";       selected = true;  break;
            case Task.PRIORITY_NORMAL: label = "🟡 Normal";    selected = true;  break;
            case Task.PRIORITY_HIGH:   label = "🟠 High";      selected = true;  break;
            case Task.PRIORITY_URGENT: label = "🔴 Urgent";    selected = true;  break;
            default:                   label = "🔺 Priority"; selected = false; break;
        }
        chipPriority.setText(label);
        setChipSelected(chipPriority, selected);
    }

    private int indexOfPriority(String priority) {
        for (int i = 0; i < PRIORITY_CYCLE.length; i++) {
            if (PRIORITY_CYCLE[i].equals(priority)) return i;
        }
        return 2; // default to NORMAL index
    }

    // ─── Smart Suggestion Utilities ──────────────────────────────

    /** Returns the suggestion with the highest confidence among the non-null candidates. */
    @Nullable
    private SmartFeaturesHelper.SmartSuggestion highestConfidence(
            SmartFeaturesHelper.SmartSuggestion... suggestions) {
        SmartFeaturesHelper.SmartSuggestion best = null;
        for (SmartFeaturesHelper.SmartSuggestion s : suggestions) {
            if (s != null && (best == null || s.confidence > best.confidence)) {
                best = s;
            }
        }
        return best;
    }

    // ─── Date / dp Utilities ─────────────────────────────────────

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static String formatDate(Calendar cal) {
        return DATE_FORMAT.format(cal.getTime());
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
