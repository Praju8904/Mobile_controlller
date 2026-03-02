package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.tasks.SubtaskItem;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-featured to-do list detail screen.
 *
 * Displays all items for a single {@link TodoList} with:
 *   - Completion progress summary card
 *   - Overdue badge and priority breakdown counts
 *   - Filter chips: All / Active / Completed / Overdue / High Priority
 *   - Multi-select bar (Complete All, Delete)
 *   - Contextual task menu: Edit, Duplicate, Set Reminder, Move to List, Delete
 *   - Stats bottom sheet and share sheet
 *
 * Uses layout R.layout.activity_todo_list_detail.
 */
public class TodoListDetailActivity extends AppCompatActivity
        implements TodoItemAdapter.OnTaskActionListener {

    public static final String EXTRA_LIST_ID = "list_id";

    // ─── Data ────────────────────────────────────────────────────

    private TodoRepository repo;
    private TodoList currentList;
    private List<TodoItem> allItems      = new ArrayList<>();
    private List<TodoItem> displayedItems = new ArrayList<>();
    private TodoItemAdapter adapter;
    private String currentFilter = "all"; // "all","active","completed","overdue","high_priority"

    // ─── Views ───────────────────────────────────────────────────

    private RecyclerView        rvTodoItems;
    private ProgressBar         pbCompletion;
    private TextView            tvCompletionText;
    private TextView            tvOverdueBadge;
    private TextView            tvUrgentCount, tvHighCount, tvNormalCount;
    private LinearLayout        filterChipsContainer;
    private LinearLayout        multiSelectBar;
    private LinearLayout        emptyState;
    private FloatingActionButton fabAddTask;

    // ─── Filter configuration ────────────────────────────────────

    private static final String[] FILTER_KEYS   =
            {"all", "active", "completed", "overdue", "high_priority"};

    // Chip resource IDs must match R.id.chip* in the layout
    private static final int[] FILTER_CHIP_IDS  = {
            R.id.chipAll, R.id.chipActive, R.id.chipCompleted,
            R.id.chipOverdue, R.id.chipHighPriority
    };

    // ─── ActivityResult launcher ─────────────────────────────────

    private final ActivityResultLauncher<Intent> detailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            reloadRepo();
                            loadAndDisplay();
                        }
                    });

    // ═══════════════════════════════════════════════════════════════
    // Static Intent Factory
    // ═══════════════════════════════════════════════════════════════

    /** Creates a launch intent for this activity. */
    public static Intent createIntent(Context ctx, String listId) {
        Intent intent = new Intent(ctx, TodoListDetailActivity.class);
        intent.putExtra(EXTRA_LIST_ID, listId);
        return intent;
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo_list_detail);

        String listId = getIntent().getStringExtra(EXTRA_LIST_ID);
        repo = new TodoRepository(this);
        currentList = repo.getListById(listId);

        if (currentList == null) {
            Toast.makeText(this, "List not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViews();
        setupHeader();
        setupFilterChips();
        setupRecyclerView();
        setupFab();
        loadAndDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadRepo();
        loadAndDisplay();
    }

    @Override
    public void onBackPressed() {
        if (isInMultiSelectMode()) {
            exitMultiSelect();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═══════════════════════════════════════════════════════════════

    private void findViews() {
        rvTodoItems          = findViewById(R.id.rvTodoItems);
        pbCompletion         = findViewById(R.id.pbCompletion);
        tvCompletionText     = findViewById(R.id.tvCompletionText);
        tvOverdueBadge       = findViewById(R.id.tvOverdueBadge);
        tvUrgentCount        = findViewById(R.id.tvUrgentCount);
        tvHighCount          = findViewById(R.id.tvHighCount);
        tvNormalCount        = findViewById(R.id.tvNormalCount);
        filterChipsContainer = findViewById(R.id.filterChipsContainer);
        multiSelectBar       = findViewById(R.id.multiSelectBar);
        emptyState           = findViewById(R.id.emptyState);
        fabAddTask           = findViewById(R.id.fabAddTask);

        View btnBack = findViewById(R.id.btnTodoBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnStats = findViewById(R.id.btnTodoStats);
        if (btnStats != null) btnStats.setOnClickListener(v -> showStatsSheet());

        View btnShare = findViewById(R.id.btnTodoShare);
        if (btnShare != null) btnShare.setOnClickListener(v -> shareList());

        // Multi-select bar
        View btnCompleteAll    = findViewById(R.id.btnCompleteAll);
        View btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        if (btnCompleteAll    != null) btnCompleteAll.setOnClickListener(v -> completeSelected());
        if (btnDeleteSelected != null) btnDeleteSelected.setOnClickListener(v -> deleteSelected());
    }

    // ═══════════════════════════════════════════════════════════════
    // HEADER
    // ═══════════════════════════════════════════════════════════════

    private void setupHeader() {
        TextView tvListTitle = findViewById(R.id.tvTodoListTitle);
        TextView tvListIcon  = findViewById(R.id.tvTodoListIcon);

        if (tvListTitle != null) tvListTitle.setText(currentList.title);
        if (tvListIcon  != null) tvListIcon.setText(currentList.iconIdentifier);
    }

    // ═══════════════════════════════════════════════════════════════
    // FILTER CHIPS
    // ═══════════════════════════════════════════════════════════════

    private void setupFilterChips() {
        if (filterChipsContainer == null) return;
        for (int i = 0; i < FILTER_CHIP_IDS.length; i++) {
            final String key = FILTER_KEYS[i];
            TextView chip = filterChipsContainer.findViewById(FILTER_CHIP_IDS[i]);
            if (chip == null) continue;
            chip.setOnClickListener(v -> {
                currentFilter = key;
                highlightActiveChip();
                loadAndDisplay();
            });
        }
        highlightActiveChip();
    }

    private void highlightActiveChip() {
        if (filterChipsContainer == null) return;
        for (int i = 0; i < FILTER_CHIP_IDS.length; i++) {
            TextView chip = filterChipsContainer.findViewById(FILTER_CHIP_IDS[i]);
            if (chip == null) continue;
            boolean active = FILTER_KEYS[i].equals(currentFilter);
            chip.setAlpha(active ? 1.0f : 0.5f);
            chip.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RECYCLERVIEW
    // ═══════════════════════════════════════════════════════════════

    private void setupRecyclerView() {
        displayedItems = new ArrayList<>();
        adapter = new TodoItemAdapter(this, displayedItems, this);
        rvTodoItems.setLayoutManager(new LinearLayoutManager(this));
        rvTodoItems.setAdapter(adapter);
    }

    // ═══════════════════════════════════════════════════════════════
    // FAB
    // ═══════════════════════════════════════════════════════════════

    private void setupFab() {
        if (fabAddTask == null) return;
        fabAddTask.setOnClickListener(v -> {
            TodoItemEditorSheet sheet =
                    TodoItemEditorSheet.newInstance(currentList.id, null);
            sheet.setListener(item -> {
                repo.addItem(item);
                loadAndDisplay();
            });
            sheet.show(getSupportFragmentManager(), "TodoItemEditorSheet");
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // LOAD & DISPLAY
    // ═══════════════════════════════════════════════════════════════

    private void loadAndDisplay() {
        if (currentList == null) return;

        switch (currentFilter) {
            case "active":
                allItems = repo.getActiveItems(currentList.id);
                break;
            case "completed":
                allItems = repo.getCompletedItems(currentList.id);
                break;
            case "overdue":
                allItems = repo.getOverdueItems(currentList.id);
                break;
            case "high_priority":
                allItems = repo.getHighPriorityItems(currentList.id);
                break;
            default:
                allItems = repo.getItemsByListId(currentList.id);
                break;
        }

        displayedItems.clear();
        displayedItems.addAll(allItems);
        adapter.notifyDataSetChanged();

        boolean isEmpty = displayedItems.isEmpty();
        if (emptyState  != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (rvTodoItems != null) rvTodoItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        updateSummaryCard();
        updatePriorityBreakdown();
    }

    private void updateSummaryCard() {
        List<TodoItem> all = repo.getItemsByListId(currentList.id);
        int total     = all.size();
        int completed = 0;
        for (TodoItem item : all) { if (item.isCompleted) completed++; }
        int percent = total > 0 ? (int) Math.round((completed * 100.0) / total) : 0;

        if (tvCompletionText != null) {
            tvCompletionText.setText(completed + "/" + total + " completed");
        }
        if (pbCompletion != null) {
            pbCompletion.setMax(100);
            pbCompletion.setProgress(percent);
        }

        int overdueCount = repo.getOverdueCount(currentList.id);
        if (tvOverdueBadge != null) {
            if (overdueCount > 0) {
                tvOverdueBadge.setVisibility(View.VISIBLE);
                tvOverdueBadge.setText(overdueCount + " overdue");
            } else {
                tvOverdueBadge.setVisibility(View.GONE);
            }
        }
    }

    private void updatePriorityBreakdown() {
        Map<String, Integer> breakdown = repo.getPriorityBreakdown(currentList.id);
        // These are display columns, not 1-to-1 priority names:
        // urgentColumn = urgent + high items; highColumn = medium; normalColumn = low + none
        int urgentColumn = safeGet(breakdown, TodoItem.PRIORITY_URGENT)
                         + safeGet(breakdown, TodoItem.PRIORITY_HIGH);
        int highColumn   = safeGet(breakdown, TodoItem.PRIORITY_MEDIUM);
        int normalColumn = safeGet(breakdown, TodoItem.PRIORITY_LOW)
                         + safeGet(breakdown, TodoItem.PRIORITY_NONE);

        if (tvUrgentCount != null) tvUrgentCount.setText(String.valueOf(urgentColumn));
        if (tvHighCount   != null) tvHighCount.setText(String.valueOf(highColumn));
        if (tvNormalCount != null) tvNormalCount.setText(String.valueOf(normalColumn));
    }

    // ═══════════════════════════════════════════════════════════════
    // OnTaskActionListener
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onTaskClick(TodoItem item) {
        Intent intent = TodoItemDetailActivity.createIntent(this, item.id);
        detailLauncher.launch(intent);
    }

    @Override
    public void onTaskComplete(TodoItem item) {
        repo.completeItem(item.id);
        loadAndDisplay();

        // Prompt to complete parent task if all subtasks are now done
        TodoItem updated = repo.getItemById(item.id);
        if (updated != null && updated.subtasks != null && !updated.subtasks.isEmpty()) {
            boolean allDone = true;
            for (SubtaskItem sub : updated.subtasks) {
                if (!sub.isCompleted) { allDone = false; break; }
            }
            if (allDone && !updated.isCompleted) {
                new AlertDialog.Builder(this)
                        .setTitle("All subtasks done")
                        .setMessage("Mark \"" + updated.title + "\" as complete?")
                        .setPositiveButton("Complete", (d, w) -> {
                            repo.completeItem(updated.id);
                            loadAndDisplay();
                        })
                        .setNegativeButton("Not yet", null)
                        .show();
            }
        }
    }

    @Override
    public void onTaskDelete(TodoItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete task")
                .setMessage("Delete \"" + item.title + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteItem(item.id);
                    loadAndDisplay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onTaskMenuClick(TodoItem item, View anchor) {
        // Long-press: enter multi-select, then show the popup
        if (!isInMultiSelectMode()) {
            enterMultiSelect(item);
        }
        showTaskContextMenu(item, anchor);
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTEXT MENU
    // ═══════════════════════════════════════════════════════════════

    private void showTaskContextMenu(TodoItem item, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, "Edit");
        popup.getMenu().add(0, 1, 1, "Duplicate");
        popup.getMenu().add(0, 2, 2, "Set Reminder");
        popup.getMenu().add(0, 3, 3, "Move to List");
        popup.getMenu().add(0, 4, 4, "Delete");
        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 0: openEditor(item);           return true;
                case 1: duplicateItem(item);        return true;
                case 2: showReminderPicker(item);   return true;
                case 3: showMoveToListDialog(item); return true;
                case 4: onTaskDelete(item);         return true;
            }
            return false;
        });
        popup.show();
    }

    private void openEditor(TodoItem item) {
        TodoItemEditorSheet sheet =
                TodoItemEditorSheet.newInstance(currentList.id, item.id);
        sheet.setListener(updated -> {
            repo.updateItem(updated);
            loadAndDisplay();
        });
        sheet.show(getSupportFragmentManager(), "TodoItemEditorSheet");
    }

    private void duplicateItem(TodoItem original) {
        TodoItem clone = new TodoItem(original.listId, original.title + " (copy)");
        clone.description              = original.description;
        clone.priority                 = original.priority;
        clone.dueDate                  = original.dueDate;
        clone.dueTime                  = original.dueTime;
        clone.recurrence               = original.recurrence;
        clone.tags                     = new ArrayList<>(original.tags != null
                ? original.tags : new ArrayList<>());
        clone.estimatedDurationMinutes = original.estimatedDurationMinutes;
        repo.addItem(clone);
        loadAndDisplay();
        Toast.makeText(this, "Task duplicated", Toast.LENGTH_SHORT).show();
    }

    private void showReminderPicker(TodoItem item) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) ->
                new TimePickerDialog(this, (tv, hour, minute) -> {
                    Calendar reminder = Calendar.getInstance();
                    reminder.set(year, month, day, hour, minute, 0);
                    item.reminderDateTime = reminder.getTimeInMillis();
                    repo.updateItem(item);
                    Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show();
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show(),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void showMoveToListDialog(TodoItem item) {
        List<TodoList> lists = repo.getAllLists();
        String[] titles = new String[lists.size()];
        for (int i = 0; i < lists.size(); i++) titles[i] = lists.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("Move to list")
                .setItems(titles, (d, which) -> {
                    item.listId = lists.get(which).id;
                    repo.updateItem(item);
                    loadAndDisplay();
                    Toast.makeText(this,
                            "Moved to " + lists.get(which).title, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // MULTI-SELECT
    // ═══════════════════════════════════════════════════════════════

    private boolean isInMultiSelectMode() {
        return multiSelectBar != null && multiSelectBar.getVisibility() == View.VISIBLE;
    }

    private void enterMultiSelect(TodoItem firstItem) {
        adapter.setMultiSelectMode(true);
        adapter.toggleSelection(firstItem.id);
        if (multiSelectBar != null) multiSelectBar.setVisibility(View.VISIBLE);
    }

    private void exitMultiSelect() {
        adapter.setMultiSelectMode(false);
        if (multiSelectBar != null) multiSelectBar.setVisibility(View.GONE);
    }

    private void completeSelected() {
        Set<String> selected = adapter.getSelectedIds();
        for (String id : selected) repo.completeItem(id);
        int count = selected.size();
        exitMultiSelect();
        loadAndDisplay();
        Toast.makeText(this, count + " tasks completed", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelected() {
        Set<String> selected = adapter.getSelectedIds();
        new AlertDialog.Builder(this)
                .setTitle("Delete tasks")
                .setMessage("Delete " + selected.size() + " selected tasks?")
                .setPositiveButton("Delete", (d, w) -> {
                    for (String id : selected) repo.deleteItem(id);
                    exitMultiSelect();
                    loadAndDisplay();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // STATS SHEET
    // ═══════════════════════════════════════════════════════════════

    private void showStatsSheet() {
        float completionRate    = repo.getCompletionRate(currentList.id);
        int completedThisWeek   = repo.getCompletedThisWeek(currentList.id).size();
        int totalTrackedMinutes = repo.getTotalTimeTrackedMinutes(currentList.id);
        int overdueCount        = repo.getOverdueCount(currentList.id);

        String message = "Completion rate: " + (int) (completionRate * 100) + "%\n"
                + "Completed this week: " + completedThisWeek + "\n"
                + "Overdue tasks: " + overdueCount + "\n"
                + "Total time tracked: " + formatMinutes(totalTrackedMinutes);

        new AlertDialog.Builder(this)
                .setTitle(currentList.iconIdentifier + "  " + currentList.title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // SHARE
    // ═══════════════════════════════════════════════════════════════

    private void shareList() {
        List<TodoItem> listItems = repo.getItemsByListId(currentList.id);
        StringBuilder sb = new StringBuilder();
        sb.append(currentList.iconIdentifier)
          .append(" *").append(currentList.title).append("*\n\n");
        for (TodoItem item : listItems) {
            sb.append(item.isCompleted ? "✅ " : "⬜ ").append(item.title);
            if (item.dueDate != null && !item.dueDate.isEmpty()) {
                sb.append(" (").append(item.dueDate).append(")");
            }
            sb.append("\n");
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share list"));
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void reloadRepo() {
        if (currentList == null) return;
        repo = new TodoRepository(this);
        currentList = repo.getListById(currentList.id);
    }

    private String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (((c1 >> 16) & 0xFF) * inv + ((c2 >> 16) & 0xFF) * ratio);
        int g = (int) (((c1 >>  8) & 0xFF) * inv + ((c2 >>  8) & 0xFF) * ratio);
        int b = (int) ((c1         & 0xFF) * inv + (c2         & 0xFF) * ratio);
        return Color.rgb(r, g, b);
    }

    /** Null-safe map get for Integer values. */
    private int safeGet(Map<String, Integer> map, String key) {
        Integer val = map.get(key);
        return val != null ? val : 0;
    }
}
