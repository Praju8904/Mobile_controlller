package com.prajwal.myfirstapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Task Manager Activity — Complete redesign (Part 1).
 *
 * Features:
 *   - Stats dashboard (today/done/overdue/starred)
 *   - Overdue alert banner
 *   - Today's Focus horizontal strip
 *   - Quick add bar
 *   - Filter chips (All, Today, Upcoming, Overdue, Starred, Completed)
 *   - Grouped RecyclerView with rich task cards
 *   - Sort / Group by menus
 *   - Search
 *   - PC↔Phone sync via ConnectionManager
 *
 * Protocol (sent to PC):
 *   TASK_ADD:title:priority:due_date:due_time
 *   TASK_COMPLETE:id       TASK_UNCOMPLETE:id
 *   TASK_DELETE:id          TASK_SYNC
 *
 * Protocol (received from PC via ReverseCommandListener):
 *   TASKS_SYNC:{json}  TASK_NOTIFY_ADDED:id:title
 *   TASK_NOTIFY_COMPLETED:id:title  TASK_NOTIFY_DELETED:id
 */
public class TaskManagerActivity extends AppCompatActivity
        implements TaskAdapter.TaskActionListener, TaskEditorSheet.TaskEditorListener {

    private static final String TAG = "TaskManager";
    private static final String CHANNEL_ID = "task_notifications";

    // ─── Singleton ───────────────────────────────────────────────
    private static TaskManagerActivity instance;
    public static TaskManagerActivity getInstance() { return instance; }

    // ─── Data ────────────────────────────────────────────────────
    private TaskRepository repo;
    private ConnectionManager connectionManager;
    private String serverIp;

    // ─── State ───────────────────────────────────────────────────
    private String currentFilter = "All";
    private String currentSortMode;
    private String currentGroupMode;
    private String searchQuery = "";
    private boolean isSearchVisible = false;

    // ─── Views ───────────────────────────────────────────────────
    private TextView tvDateSummary;
    private TextView tvStatTodayCount, tvStatCompletedCount, tvStatOverdueCount, tvStatStarredCount;
    private LinearLayout overdueAlertBanner;
    private TextView tvOverdueMessage;
    private LinearLayout todayFocusSection, todayFocusContainer;
    private TextView tvFocusCount;
    private EditText etQuickAdd;
    private LinearLayout filterChipContainer;
    private TextView tvResultCount, btnGroupBy, btnSortBy;
    private RecyclerView recyclerTasks;
    private LinearLayout emptyStateContainer;
    private TextView tvEmptyIcon, tvEmptyTitle, tvEmptySubtitle;
    private LinearLayout searchBarContainer;
    private EditText etSearch;
    private TaskAdapter taskAdapter;

    // Bulk actions (multi-select)
    private LinearLayout bulkActionBar;
    private TextView tvBulkCount;

    // ─── Filter chip names ───────────────────────────────────────
    private static final String[] FILTER_NAMES = {
        "All", "Today", "Upcoming", "Overdue", "Starred", "Completed"
    };

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_manager);
        instance = this;

        serverIp = getIntent().getStringExtra("server_ip");
        if (serverIp == null) serverIp = "10.190.76.54";
        connectionManager = new ConnectionManager(serverIp);

        repo = new TaskRepository(this);
        currentSortMode = repo.getSavedSortMode();
        currentGroupMode = repo.getSavedGroupMode();

        createNotificationChannel();
        initViews();
        buildFilterChips();
        refreshAll();

        // Sync on open
        connectionManager.sendCommand("TASK_SYNC");
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        repo.reload();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    // ═══════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═══════════════════════════════════════════════════════════════

    private void initViews() {
        // Header
        tvDateSummary = findViewById(R.id.tvDateSummary);
        tvDateSummary.setText(new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date()));

        ImageView btnSearch = findViewById(R.id.btnSearch);
        ImageView btnCategories = findViewById(R.id.btnCategories);
        ImageView btnTrash = findViewById(R.id.btnTrash);

        btnSearch.setOnClickListener(v -> {
            // Open advanced search activity
            Intent searchIntent = new Intent(this, TaskSearchActivity.class);
            startActivity(searchIntent);
        });
        btnSearch.setOnLongClickListener(v -> {
            // Long-press: inline search toggle
            toggleSearch();
            return true;
        });
        btnCategories.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskCategoriesActivity.class);
            startActivity(intent);
        });
        btnTrash.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskTrashActivity.class);
            startActivity(intent);
        });

        // Search bar
        searchBarContainer = findViewById(R.id.searchBarContainer);
        etSearch = findViewById(R.id.etSearch);
        ImageView btnCloseSearch = findViewById(R.id.btnCloseSearch);
        btnCloseSearch.setOnClickListener(v -> toggleSearch());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                refreshTaskList();
            }
        });

        // Stats
        tvStatTodayCount = findViewById(R.id.tvStatTodayCount);
        tvStatCompletedCount = findViewById(R.id.tvStatCompletedCount);
        tvStatOverdueCount = findViewById(R.id.tvStatOverdueCount);
        tvStatStarredCount = findViewById(R.id.tvStatStarredCount);

        // Tap on completed stat → open productivity dashboard
        View statCompleted = tvStatCompletedCount.getParent() instanceof View
                ? (View) tvStatCompletedCount.getParent() : null;
        if (statCompleted != null) {
            statCompleted.setOnClickListener(v -> {
                Intent statsIntent = new Intent(this, TaskStatsActivity.class);
                startActivity(statsIntent);
            });
        }

        // Overdue banner
        overdueAlertBanner = findViewById(R.id.overdueAlertBanner);
        tvOverdueMessage = findViewById(R.id.tvOverdueMessage);
        ImageView btnDismissOverdue = findViewById(R.id.btnDismissOverdue);
        btnDismissOverdue.setOnClickListener(v -> overdueAlertBanner.setVisibility(View.GONE));

        findViewById(R.id.statOverdue).setOnClickListener(v -> selectFilter("Overdue"));

        // Today's focus
        todayFocusSection = findViewById(R.id.todayFocusSection);
        todayFocusContainer = findViewById(R.id.todayFocusContainer);
        tvFocusCount = findViewById(R.id.tvFocusCount);

        // Quick add
        etQuickAdd = findViewById(R.id.etQuickAdd);
        TextView btnQuickAdd = findViewById(R.id.btnQuickAdd);
        btnQuickAdd.setOnClickListener(v -> quickAddTask());
        etQuickAdd.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                quickAddTask();
                return true;
            }
            return false;
        });

        // Filter chips
        filterChipContainer = findViewById(R.id.filterChipContainer);

        // Sort / Group / Count
        tvResultCount = findViewById(R.id.tvResultCount);
        btnGroupBy = findViewById(R.id.btnGroupBy);
        btnSortBy = findViewById(R.id.btnSortBy);
        btnGroupBy.setOnClickListener(v -> showGroupMenu());
        btnSortBy.setOnClickListener(v -> showSortMenu());

        // RecyclerView
        recyclerTasks = findViewById(R.id.recyclerTasks);
        recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(this, this);
        recyclerTasks.setAdapter(taskAdapter);

        // Empty state
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyIcon = findViewById(R.id.tvEmptyIcon);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);

        // FAB → open Task Editor bottom sheet
        TextView fabAddTask = findViewById(R.id.fabAddTask);
        fabAddTask.setOnClickListener(v -> {
            TaskEditorSheet sheet = TaskEditorSheet.newInstance();
            sheet.setListener(this);
            sheet.show(getSupportFragmentManager(), "task_editor");
        });

        // Bulk action bar
        bulkActionBar = findViewById(R.id.bulkActionBar);
        tvBulkCount = findViewById(R.id.tvBulkCount);
        setupBulkActions();
    }

    // ═══════════════════════════════════════════════════════════════
    // FILTER CHIPS
    // ═══════════════════════════════════════════════════════════════

    private void buildFilterChips() {
        filterChipContainer.removeAllViews();
        for (String name : FILTER_NAMES) {
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(13);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setTag(name);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> selectFilter(name));

            filterChipContainer.addView(chip);
        }
        updateFilterChipVisuals();
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        updateFilterChipVisuals();
        refreshTaskList();
    }

    private void updateFilterChipVisuals() {
        for (int i = 0; i < filterChipContainer.getChildCount(); i++) {
            View child = filterChipContainer.getChildAt(i);
            if (child instanceof TextView) {
                String tag = (String) child.getTag();
                boolean active = tag != null && tag.equals(currentFilter);
                child.setBackgroundResource(active
                        ? R.drawable.task_filter_chip_active_bg
                        : R.drawable.task_filter_chip_inactive_bg);
                ((TextView) child).setTextColor(active
                        ? Color.WHITE : Color.parseColor("#94A3B8"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════════════════════════════

    private void toggleSearch() {
        isSearchVisible = !isSearchVisible;
        searchBarContainer.setVisibility(isSearchVisible ? View.VISIBLE : View.GONE);
        if (isSearchVisible) {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        } else {
            etSearch.setText("");
            searchQuery = "";
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            refreshTaskList();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SORT / GROUP MENUS
    // ═══════════════════════════════════════════════════════════════

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(this, btnSortBy);
        popup.getMenu().add("Priority");
        popup.getMenu().add("Due Date");
        popup.getMenu().add("Created");
        popup.getMenu().add("Title");
        popup.getMenu().add("Status");
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            switch (label) {
                case "Priority": currentSortMode = TaskRepository.SORT_PRIORITY; break;
                case "Due Date": currentSortMode = TaskRepository.SORT_DUE_DATE; break;
                case "Created":  currentSortMode = TaskRepository.SORT_CREATED; break;
                case "Title":    currentSortMode = TaskRepository.SORT_TITLE; break;
                case "Status":   currentSortMode = TaskRepository.SORT_STATUS; break;
            }
            repo.saveSortMode(currentSortMode);
            refreshTaskList();
            return true;
        });
        popup.show();
    }

    private void showGroupMenu() {
        PopupMenu popup = new PopupMenu(this, btnGroupBy);
        popup.getMenu().add("None");
        popup.getMenu().add("Due Date");
        popup.getMenu().add("Priority");
        popup.getMenu().add("Category");
        popup.getMenu().add("Status");
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            switch (label) {
                case "None":     currentGroupMode = TaskRepository.GROUP_NONE; break;
                case "Due Date": currentGroupMode = TaskRepository.GROUP_DUE_DATE; break;
                case "Priority": currentGroupMode = TaskRepository.GROUP_PRIORITY; break;
                case "Category": currentGroupMode = TaskRepository.GROUP_CATEGORY; break;
                case "Status":   currentGroupMode = TaskRepository.GROUP_STATUS; break;
            }
            repo.saveGroupMode(currentGroupMode);
            refreshTaskList();
            return true;
        });
        popup.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // QUICK ADD
    // ═══════════════════════════════════════════════════════════════

    private void quickAddTask() {
        String title = etQuickAdd.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Enter a task title", Toast.LENGTH_SHORT).show();
            return;
        }

        Task task = new Task(title, Task.PRIORITY_NORMAL);
        task.source = "mobile";
        repo.addTask(task);

        // Sync to PC
        connectionManager.sendCommand("TASK_ADD:" + title + ":" + task.priority);

        etQuickAdd.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etQuickAdd.getWindowToken(), 0);

        Toast.makeText(this, "Task added!", Toast.LENGTH_SHORT).show();
        refreshAll();
    }

    // ═══════════════════════════════════════════════════════════════
    // REFRESH
    // ═══════════════════════════════════════════════════════════════

    private void refreshAll() {
        refreshStats();
        refreshOverdueBanner();
        refreshTodayFocus();
        refreshTaskList();
    }

    private void refreshStats() {
        tvStatTodayCount.setText(String.valueOf(repo.getTotalTodayCount()));
        tvStatCompletedCount.setText(String.valueOf(repo.getCompletedTodayCount()));
        tvStatOverdueCount.setText(String.valueOf(repo.getOverdueCount()));
        tvStatStarredCount.setText(String.valueOf(repo.getStarredCount()));
    }

    private void refreshOverdueBanner() {
        int overdueCount = repo.getOverdueCount();
        if (overdueCount > 0) {
            overdueAlertBanner.setVisibility(View.VISIBLE);
            tvOverdueMessage.setText("You have " + overdueCount + " task" +
                    (overdueCount > 1 ? "s" : "") + " past their due date");
        } else {
            overdueAlertBanner.setVisibility(View.GONE);
        }
    }

    private void refreshTodayFocus() {
        List<Task> focusTasks = repo.getTodayFocusTasks();
        todayFocusContainer.removeAllViews();

        if (focusTasks.isEmpty()) {
            todayFocusSection.setVisibility(View.GONE);
            return;
        }

        todayFocusSection.setVisibility(View.VISIBLE);
        tvFocusCount.setText(String.valueOf(focusTasks.size()));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Task task : focusTasks) {
            View card = inflater.inflate(R.layout.item_today_focus_card, todayFocusContainer, false);
            TextView tvTitle = card.findViewById(R.id.tvFocusTitle);
            TextView tvCategory = card.findViewById(R.id.tvFocusCategory);
            TextView tvTime = card.findViewById(R.id.tvFocusDueTime);
            View dot = card.findViewById(R.id.viewFocusPriority);

            tvTitle.setText(task.title);
            tvCategory.setText(task.category != null ? task.category : "");
            tvTime.setText(task.hasDueTime() ? task.getFormattedDueTime() : "All day");

            // Priority dot color
            try {
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(task.getPriorityColor());
                gd.setSize(dp(8), dp(8));
                dot.setBackground(gd);
            } catch (Exception ignored) {}

            card.setOnClickListener(v -> {
                Intent detailIntent = new Intent(this, TaskDetailActivity.class);
                detailIntent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id);
                startActivity(detailIntent);
            });

            todayFocusContainer.addView(card);
        }
    }

    private void refreshTaskList() {
        // Get filtered tasks
        List<Task> taskList;
        if (!searchQuery.isEmpty()) {
            taskList = repo.searchTasks(searchQuery);
        } else {
            taskList = repo.filterTasks(currentFilter);
        }

        // Sort
        repo.sortTasks(taskList, currentSortMode);

        // Group
        if (!TaskRepository.GROUP_NONE.equals(currentGroupMode)) {
            LinkedHashMap<String, List<Task>> groups = repo.groupTasks(taskList, currentGroupMode);
            taskAdapter.setGroupedTasks(groups);
        } else {
            taskAdapter.setTasks(taskList);
        }

        // Result count text
        int count = taskAdapter.getTaskCount();
        if (!searchQuery.isEmpty()) {
            tvResultCount.setText(count + " result" + (count != 1 ? "s" : "") + " for \"" + searchQuery + "\"");
        } else {
            tvResultCount.setText(currentFilter + " · " + count + " task" + (count != 1 ? "s" : ""));
        }

        // Empty state
        if (count == 0) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            recyclerTasks.setVisibility(View.GONE);
            updateEmptyState();
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            recyclerTasks.setVisibility(View.VISIBLE);
        }
    }

    private void updateEmptyState() {
        if (!searchQuery.isEmpty()) {
            tvEmptyIcon.setText("🔍");
            tvEmptyTitle.setText("No results");
            tvEmptySubtitle.setText("Try a different search term");
        } else {
            switch (currentFilter) {
                case "Today":
                    tvEmptyIcon.setText("☀️");
                    tvEmptyTitle.setText("Nothing due today");
                    tvEmptySubtitle.setText("Enjoy your free day or add new tasks");
                    break;
                case "Upcoming":
                    tvEmptyIcon.setText("📅");
                    tvEmptyTitle.setText("No upcoming tasks");
                    tvEmptySubtitle.setText("Schedule tasks for the future");
                    break;
                case "Overdue":
                    tvEmptyIcon.setText("✅");
                    tvEmptyTitle.setText("All caught up!");
                    tvEmptySubtitle.setText("No overdue tasks");
                    break;
                case "Starred":
                    tvEmptyIcon.setText("⭐");
                    tvEmptyTitle.setText("No starred tasks");
                    tvEmptySubtitle.setText("Star important tasks to find them quickly");
                    break;
                case "Completed":
                    tvEmptyIcon.setText("📋");
                    tvEmptyTitle.setText("No completed tasks");
                    tvEmptySubtitle.setText("Complete tasks to see them here");
                    break;
                default:
                    tvEmptyIcon.setText("📋");
                    tvEmptyTitle.setText("No tasks yet");
                    tvEmptySubtitle.setText("Add your first task to get started");
                    break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TASK ADAPTER CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onTaskChecked(Task task, boolean isChecked) {
        if (isChecked) {
            repo.completeTask(task.id);
            connectionManager.sendCommand("TASK_COMPLETE:" + task.id);
        } else {
            repo.uncompleteTask(task.id);
            connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
        }
        refreshAll();
    }

    @Override
    public void onTaskClicked(Task task) {
        Intent intent = new Intent(this, TaskDetailActivity.class);
        intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id);
        startActivity(intent);
    }

    @Override
    public void onTaskStarToggle(Task task) {
        repo.toggleStar(task.id);
        refreshAll();
    }

    @Override
    public void onTaskMenuClicked(Task task, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Duplicate");
        popup.getMenu().add("Move to Category");
        popup.getMenu().add("Change Priority");
        if (task.isCompleted()) {
            popup.getMenu().add("Mark Incomplete");
        } else {
            popup.getMenu().add("Mark Complete");
        }
        popup.getMenu().add("Move to Trash");

        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            switch (label) {
                case "Edit":
                    TaskEditorSheet editSheet = TaskEditorSheet.newInstance(task.id);
                    editSheet.setListener(TaskManagerActivity.this);
                    editSheet.show(getSupportFragmentManager(), "task_editor");
                    break;
                case "Duplicate":
                    repo.duplicateTask(task.id);
                    Toast.makeText(this, "Task duplicated!", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Move to Category":
                    showCategoryPicker(task);
                    break;
                case "Change Priority":
                    showPriorityPicker(task);
                    break;
                case "Mark Complete":
                    repo.completeTask(task.id);
                    connectionManager.sendCommand("TASK_COMPLETE:" + task.id);
                    refreshAll();
                    break;
                case "Mark Incomplete":
                    repo.uncompleteTask(task.id);
                    connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
                    refreshAll();
                    break;
                case "Move to Trash":
                    repo.trashTask(task.id);
                    connectionManager.sendCommand("TASK_DELETE:" + task.id);
                    Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onGroupHeaderClicked(String groupName) {
        // For now just show a toast; collapsible groups could be added
        Toast.makeText(this, groupName, Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════
    // PICKERS (from task context menu)
    // ═══════════════════════════════════════════════════════════════

    private void showCategoryPicker(Task task) {
        List<String> categories = repo.getAllCategoryNames();
        String[] items = categories.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Move to Category")
                .setItems(items, (d, which) -> {
                    repo.moveToCategory(task.id, items[which]);
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPriorityPicker(Task task) {
        String[] priorities = {"Urgent", "High", "Normal", "Low", "None"};
        String[] values = {Task.PRIORITY_URGENT, Task.PRIORITY_HIGH,
                Task.PRIORITY_NORMAL, Task.PRIORITY_LOW, Task.PRIORITY_NONE};
        new AlertDialog.Builder(this)
                .setTitle("Change Priority")
                .setItems(priorities, (d, which) -> {
                    Task t = repo.getTaskById(task.id);
                    if (t != null) {
                        t.priority = values[which];
                        repo.updateTask(t);
                        refreshAll();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // SYNC FROM PC (called by ReverseCommandListener)
    // ═══════════════════════════════════════════════════════════════

    public void onTasksSyncReceived(String tasksJson) {
        repo.onSyncReceived(tasksJson);
        runOnUiThread(() -> {
            refreshAll();
            Toast.makeText(this, "Tasks synced from PC!", Toast.LENGTH_SHORT).show();
        });
        Log.i(TAG, "Task sync received from PC");
    }

    public void onTaskNotifyAdded(String taskId, String title) {
        runOnUiThread(() -> {
            showLocalNotification("New Task Added", title + " (from PC)");
            Toast.makeText(this, "📋 New task: " + title, Toast.LENGTH_SHORT).show();
            connectionManager.sendCommand("TASK_SYNC");
        });
    }

    public void onTaskNotifyCompleted(String taskId, String title) {
        runOnUiThread(() -> {
            showLocalNotification("Task Completed", "✅ " + title);
            Toast.makeText(this, "✅ Completed: " + title, Toast.LENGTH_SHORT).show();
            connectionManager.sendCommand("TASK_SYNC");
        });
    }

    public void onTaskNotifyDeleted(String taskId) {
        runOnUiThread(() -> {
            showLocalNotification("Task Deleted", "A task was removed from PC");
            connectionManager.sendCommand("TASK_SYNC");
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // TASK EDITOR CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onTaskSaved(Task task, boolean isNew) {
        if (isNew) {
            connectionManager.sendCommand("TASK_ADD:" + task.title + ":" + task.priority);
            Toast.makeText(this, "Task created!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Task updated!", Toast.LENGTH_SHORT).show();
        }
        refreshAll();
    }

    @Override
    public void onTaskEditorDismissed() {
        // No-op, just refresh in case anything changed
        refreshAll();
    }

    // ═══════════════════════════════════════════════════════════════
    // MULTI-SELECT & BULK ACTIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onMultiSelectChanged(boolean active, int count) {
        if (active && count > 0) {
            if (bulkActionBar != null) bulkActionBar.setVisibility(View.VISIBLE);
            if (tvBulkCount != null) tvBulkCount.setText(count + " selected");
        } else if (!active) {
            if (bulkActionBar != null) bulkActionBar.setVisibility(View.GONE);
        } else {
            if (tvBulkCount != null) tvBulkCount.setText("0 selected");
        }
    }

    private void setupBulkActions() {
        if (bulkActionBar == null) return;
        bulkActionBar.setVisibility(View.GONE);

        // Find bulk action buttons by tag or add them dynamically
        TextView btnBulkComplete = findViewById(R.id.btnBulkComplete);
        TextView btnBulkPriority = findViewById(R.id.btnBulkPriority);
        TextView btnBulkCategory = findViewById(R.id.btnBulkCategory);
        TextView btnBulkStar = findViewById(R.id.btnBulkStar);
        TextView btnBulkTrash = findViewById(R.id.btnBulkTrash);
        TextView btnBulkCancel = findViewById(R.id.btnBulkCancel);

        if (btnBulkComplete != null) btnBulkComplete.setOnClickListener(v -> {
            for (String id : taskAdapter.getSelectedIds()) {
                repo.completeTask(id);
                connectionManager.sendCommand("TASK_COMPLETE:" + id);
            }
            taskAdapter.exitMultiSelect();
            refreshAll();
            Toast.makeText(this, "Tasks completed!", Toast.LENGTH_SHORT).show();
        });

        if (btnBulkPriority != null) btnBulkPriority.setOnClickListener(v -> {
            String[] priorities = {"Urgent", "High", "Normal", "Low"};
            String[] values = {Task.PRIORITY_URGENT, Task.PRIORITY_HIGH, Task.PRIORITY_NORMAL, Task.PRIORITY_LOW};
            new AlertDialog.Builder(this)
                    .setTitle("Set Priority")
                    .setItems(priorities, (d, which) -> {
                        repo.bulkUpdatePriority(new ArrayList<>(taskAdapter.getSelectedIds()), values[which]);
                        taskAdapter.exitMultiSelect();
                        refreshAll();
                    })
                    .show();
        });

        if (btnBulkCategory != null) btnBulkCategory.setOnClickListener(v -> {
            List<String> cats = repo.getAllCategoryNames();
            String[] items = cats.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Move to Category")
                    .setItems(items, (d, which) -> {
                        repo.bulkUpdateCategory(new ArrayList<>(taskAdapter.getSelectedIds()), items[which]);
                        taskAdapter.exitMultiSelect();
                        refreshAll();
                    })
                    .show();
        });

        if (btnBulkStar != null) btnBulkStar.setOnClickListener(v -> {
            repo.bulkStar(new ArrayList<>(taskAdapter.getSelectedIds()));
            taskAdapter.exitMultiSelect();
            refreshAll();
        });

        if (btnBulkTrash != null) btnBulkTrash.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Move to Trash")
                    .setMessage("Trash " + taskAdapter.getSelectedCount() + " tasks?")
                    .setPositiveButton("Trash", (d, w) -> {
                        for (String id : taskAdapter.getSelectedIds()) {
                            repo.trashTask(id);
                            connectionManager.sendCommand("TASK_DELETE:" + id);
                        }
                        taskAdapter.exitMultiSelect();
                        refreshAll();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        if (btnBulkCancel != null) btnBulkCancel.setOnClickListener(v -> {
            taskAdapter.exitMultiSelect();
        });
    }

    @Override
    public void onBackPressed() {
        // Exit multi-select mode on back press
        if (taskAdapter != null && taskAdapter.isMultiSelectActive()) {
            taskAdapter.exitMultiSelect();
            return;
        }
        super.onBackPressed();
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Task Notifications", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for task sync");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void showLocalNotification(String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
