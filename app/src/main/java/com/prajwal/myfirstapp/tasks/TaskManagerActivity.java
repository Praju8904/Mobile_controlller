package com.prajwal.myfirstapp.tasks;

import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.connectivity.ConnectionManager;
import com.prajwal.myfirstapp.meetings.CreateMeetingActivity;
import com.prajwal.myfirstapp.meetings.Meeting;
import com.prajwal.myfirstapp.meetings.MeetingDetailActivity;
import com.prajwal.myfirstapp.meetings.MeetingRepository;
import com.prajwal.myfirstapp.meetings.MeetingsListActivity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.animation.ValueAnimator;
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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Task Manager Activity — Complete redesign (Part 1).
 *
 * Features: - Stats dashboard (today/done/overdue/starred) - Overdue alert
 * banner - Today's Focus horizontal strip - Quick add bar - Filter chips (All,
 * Today, Upcoming, Overdue, Starred, Completed) - Grouped RecyclerView with
 * rich task cards - Sort / Group by menus - Search - PC↔Phone sync via
 * ConnectionManager
 *
 * Protocol (sent to PC): TASK_ADD:title:priority:due_date:due_time
 * TASK_COMPLETE:id TASK_UNCOMPLETE:id TASK_DELETE:id TASK_SYNC
 *
 * Protocol (received from PC via ReverseCommandListener): TASKS_SYNC:{json}
 * TASK_NOTIFY_ADDED:id:title TASK_NOTIFY_COMPLETED:id:title
 * TASK_NOTIFY_DELETED:id
 */
public class TaskManagerActivity extends AppCompatActivity
        implements TaskAdapter.TaskActionListener, TaskEditorSheet.TaskEditorListener {

    private static final String TAG = "TaskManager";
    private static final String CHANNEL_ID = "task_notifications";

    // ─── Singleton ───────────────────────────────────────────────
    private static TaskManagerActivity instance;

    public static TaskManagerActivity getInstance() {
        return instance;
    }

    // ─── Data ────────────────────────────────────────────────────
    private TaskRepository repo;
    private MeetingRepository meetingRepo;
    private ConnectionManager connectionManager;
    private String serverIp;

    // ─── State ───────────────────────────────────────────────────
    private String currentFilter = "All";
    private String currentSortMode;
    private String currentGroupMode;
    private String searchQuery = "";
    private boolean isSearchVisible = false;
    private TaskManagerSettings settings;
    private CountdownRefreshScheduler countdownScheduler;
    private TaskUndoManager undoManager;
    private String projectIdFilter = null; // Feature 17E: project-scoped view

    // ─── Views ───────────────────────────────────────────────────
    private TextView tvDateSummary;
    private TextView tvStatTodayCount, tvStatCompletedCount, tvStatOverdueCount, tvStatStarredCount;
    private TextView tvStatFocusScore;
    private LinearLayout overdueAlertBanner;
    private TextView tvOverdueMessage;
    private LinearLayout todayFocusSection, todayFocusContainer;
    private TextView tvFocusCount;
    private EditText etQuickAdd;
    private LinearLayout filterChipContainer;
    private TextView tvResultLabel, tvResultCount, btnGroupBy, btnSortBy;
    private RecyclerView recyclerTasks;
    private LinearLayout emptyStateContainer;
    private TextView tvEmptyIcon, tvEmptyTitle, tvEmptySubtitle;
    private LinearLayout searchBarContainer;
    private EditText etSearch;
    private TaskAdapter taskAdapter;
    private DrawerLayout drawerLayout;
    private ImageView btnToggleView;

    // Streak banner views
    private LinearLayout streakBanner;
    private TextView tvStreakIcon, tvStreakTitle, tvStreakSubtitle;

    // Bulk actions (multi-select)
    private LinearLayout bulkActionBar;
    private TextView tvBulkCount;

    // Meetings section
    private LinearLayout meetingsSectionHeader;
    private LinearLayout meetingsStripContainer;

    // ─── Filter chip names ───────────────────────────────────────
    private static final String[] FILTER_NAMES = {
        "All", "Today", "Upcoming", "Overdue", "Starred", "Inbox",
        "Next Actions", "MIT", "Waiting", "Someday",
        "Completed", "Priority", "Meetings"
    };

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_manager);
        instance = this;
        TaskNotificationHelper.createNotificationChannel(this);

        serverIp = getIntent().getStringExtra("server_ip");
        if (serverIp == null) {
            serverIp = "10.190.76.54";
        }
        connectionManager = new ConnectionManager(serverIp);

        repo = new TaskRepository(this);
        settings = TaskManagerSettings.getInstance(this);
        undoManager = new TaskUndoManager(findViewById(android.R.id.content),
                new TaskUndoManager.UndoCallback() {
                    @Override public void onUndoPerformed() { refreshAll(); }
                    @Override public void onWriteCommitted() { /* no-op */ }
                });
        currentSortMode = repo.getSavedSortMode();
        currentGroupMode = repo.getSavedGroupMode();

        createNotificationChannel();
        initViews();
        buildFilterChips();

        meetingRepo = MeetingRepository.getInstance(this);
        TextView btnViewAllMeetings = findViewById(R.id.btnViewAllMeetings);
        TextView btnAddMeeting = findViewById(R.id.btnAddMeeting);
        if (btnViewAllMeetings != null) {
            btnViewAllMeetings.setOnClickListener(v
                    -> startActivity(new Intent(this, MeetingsListActivity.class)));
        }
        if (btnAddMeeting != null) {
            btnAddMeeting.setOnClickListener(v
                    -> startActivity(new Intent(this, CreateMeetingActivity.class)));
        }
        loadMeetingsStrip();

        // Feature 17E: handle project-scoped intent
        projectIdFilter = getIntent().getStringExtra("projectId");
        String projectName = getIntent().getStringExtra("projectName");
        if (projectIdFilter != null && projectName != null) {
            // Override title to show project name
            if (tvResultLabel != null) {
                tvResultLabel.setText("\uD83D\uDCC1 " + projectName);
            }
        }

        refreshAll();

        // Sync on open
        connectionManager.sendCommand("TASK_SYNC");

        // Show onboarding tour on first launch
        CoachMarkOverlay.showIfNeeded(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        repo.reload();
        if (meetingRepo != null) {
            loadMeetingsStrip();
        }
        refreshAll();

        // Start countdown badge refresher
        if (countdownScheduler == null && recyclerTasks != null && taskAdapter != null) {
            countdownScheduler = new CountdownRefreshScheduler(recyclerTasks);
        }
        if (countdownScheduler != null) {
            countdownScheduler.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (countdownScheduler != null) {
            countdownScheduler.stop();
        }
        // Feature 15G: auto-lock vault when app goes to background
        TaskVaultManager.getInstance(this).lock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Commit any pending undo writes so they are not lost in background
        if (undoManager != null) {
            undoManager.commitPendingWrite();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VIEW INIT
    // ═══════════════════════════════════════════════════════════════
    private void initViews() {
        // Drawer
        drawerLayout = findViewById(R.id.drawerLayout);

        // Drawer hamburger button
        ImageView btnDrawer = findViewById(R.id.btnDrawer);
        btnDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

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

        // Grid / List toggle
        btnToggleView = findViewById(R.id.btnToggleView);
        btnToggleView.setOnClickListener(v -> {
            String current = settings.taskViewMode;
            setViewMode("grid".equals(current) ? "list" : "grid");
        });

        // Search bar
        searchBarContainer = findViewById(R.id.searchBarContainer);
        etSearch = findViewById(R.id.etSearch);
        ImageView btnCloseSearch = findViewById(R.id.btnCloseSearch);
        btnCloseSearch.setOnClickListener(v -> toggleSearch());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

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
        tvStatFocusScore = findViewById(R.id.tvStatFocusScore);

        // Stat card click handlers — filter task list by tapping cards
        findViewById(R.id.statToday).setOnClickListener(v -> selectFilter("Today"));
        findViewById(R.id.statOverdue).setOnClickListener(v -> selectFilter("Overdue"));
        findViewById(R.id.statStarred).setOnClickListener(v -> selectFilter("Starred"));

        // Tap on completed stat → open productivity dashboard
        findViewById(R.id.statCompleted).setOnClickListener(v -> {
            Intent statsIntent = new Intent(this, TaskStatsActivity.class);
            startActivity(statsIntent);
        });

        // Focus score card → open productivity dashboard
        findViewById(R.id.statFocusScore).setOnClickListener(v -> {
            Intent statsIntent = new Intent(this, TaskStatsActivity.class);
            startActivity(statsIntent);
        });

        // Streak banner
        streakBanner = findViewById(R.id.streakBanner);
        tvStreakIcon = findViewById(R.id.tvStreakIcon);
        tvStreakTitle = findViewById(R.id.tvStreakTitle);
        tvStreakSubtitle = findViewById(R.id.tvStreakSubtitle);
        streakBanner.setOnClickListener(v -> {
            Intent statsIntent = new Intent(this, TaskStatsActivity.class);
            startActivity(statsIntent);
        });

        // Overdue banner
        overdueAlertBanner = findViewById(R.id.overdueAlertBanner);
        tvOverdueMessage = findViewById(R.id.tvOverdueMessage);
        ImageView btnDismissOverdue = findViewById(R.id.btnDismissOverdue);
        btnDismissOverdue.setOnClickListener(v -> overdueAlertBanner.setVisibility(View.GONE));

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

        // Quick add focus glow effect
        etQuickAdd.setOnFocusChangeListener((v, hasFocus) -> {
            LinearLayout quickAddContainer = findViewById(R.id.quickAddContainer);
            if (quickAddContainer != null) {
                quickAddContainer.setBackgroundResource(hasFocus
                        ? R.drawable.task_quick_add_focused_bg
                        : R.drawable.task_quick_add_bg);
            }
        });

        // Filter chips
        filterChipContainer = findViewById(R.id.filterChipContainer);

        // Sort / Group / Count
        tvResultLabel = findViewById(R.id.tvResultLabel);
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

        // Apply saved view mode (grid / list)
        setViewMode(settings.taskViewMode != null ? settings.taskViewMode : "list");

        // Swipe gestures (right=complete, left=archive) with haptic feedback
        TaskSwipeCallback swipeCb = new TaskSwipeCallback(taskAdapter, this, this, settings);
        new ItemTouchHelper(swipeCb).attachToRecyclerView(recyclerTasks);

        // Empty state
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyIcon = findViewById(R.id.tvEmptyIcon);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);

        // Empty state Add Task button
        TextView btnEmptyAddTask = findViewById(R.id.btnEmptyAddTask);
        if (btnEmptyAddTask != null) {
            btnEmptyAddTask.setOnClickListener(v -> {
                TaskEditorSheet sheet = TaskEditorSheet.newInstance();
                sheet.setListener(this);
                sheet.show(getSupportFragmentManager(), "task_editor");
            });
        }

        // FAB → open Task Editor bottom sheet
        TextView fabAddTask = findViewById(R.id.fabAddTask);
        fabAddTask.setOnClickListener(v -> {
            TaskEditorSheet sheet = TaskEditorSheet.newInstance();
            sheet.setListener(this);
            sheet.show(getSupportFragmentManager(), "task_editor");
        });
        // Long-press FAB to open bulk import
        fabAddTask.setOnLongClickListener(v -> {
            BulkImportSheet bulkSheet = BulkImportSheet.newInstance();
            bulkSheet.setListener(tasks -> {
                for (Task t : tasks) {
                    repo.addTask(t);
                }
                refreshTaskList();
            });
            bulkSheet.show(getSupportFragmentManager(), "bulk_import");
            return true;
        });

        // Meetings section views
        meetingsSectionHeader = findViewById(R.id.meetingsSectionHeader);
        meetingsStripContainer = findViewById(R.id.meetingsStripContainer);

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
        if ("Meetings".equals(filter)) {
            startActivity(new Intent(this, MeetingsListActivity.class));
            return;
        }
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

                if (active) {
                    // Active chip gets a colored gradient background
                    GradientDrawable activeBg = new GradientDrawable();
                    activeBg.setShape(GradientDrawable.RECTANGLE);
                    activeBg.setCornerRadius(dp(20));
                    int[] colors = getChipGradientColors(tag);
                    activeBg.setColors(colors);
                    activeBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                    child.setBackground(activeBg);
                    ((TextView) child).setTextColor(Color.WHITE);
                } else {
                    child.setBackgroundResource(R.drawable.task_filter_chip_inactive_bg);
                    ((TextView) child).setTextColor(Color.parseColor("#94A3B8"));
                }
            }
        }

        // Update filter active indicator dot
        View filterDot = findViewById(R.id.filterActiveDot);
        if (filterDot != null) {
            boolean hasActiveFilter = !"All".equals(currentFilter);
            filterDot.setVisibility(hasActiveFilter ? View.VISIBLE : View.GONE);
            if (hasActiveFilter) {
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(Color.parseColor("#3B82F6"));
                filterDot.setBackground(dotBg);
            }
        }
    }

    private int[] getChipGradientColors(String chipName) {
        if (chipName == null) {
            return new int[]{Color.parseColor("#3B82F6"), Color.parseColor("#6366F1")};
        }
        switch (chipName) {
            case "All":
                return new int[]{Color.parseColor("#3B82F6"), Color.parseColor("#6366F1")};
            case "Today":
                return new int[]{Color.parseColor("#2563EB"), Color.parseColor("#3B82F6")};
            case "Upcoming":
                return new int[]{Color.parseColor("#7C3AED"), Color.parseColor("#8B5CF6")};
            case "Overdue":
                return new int[]{Color.parseColor("#DC2626"), Color.parseColor("#EF4444")};
            case "Starred":
                return new int[]{Color.parseColor("#D97706"), Color.parseColor("#F59E0B")};
            case "Inbox":
                return new int[]{Color.parseColor("#0369A1"), Color.parseColor("#0EA5E9")};
            case "Completed":
                return new int[]{Color.parseColor("#059669"), Color.parseColor("#10B981")};
            case "Priority":
                return new int[]{Color.parseColor("#EA580C"), Color.parseColor("#F97316")};
            case "Meetings":
                return new int[]{Color.parseColor("#5B21B6"), Color.parseColor("#6C63FF")};
            case "Next Actions":
                return new int[]{Color.parseColor("#D97706"), Color.parseColor("#FBBF24")};
            case "MIT":
                return new int[]{Color.parseColor("#BE123C"), Color.parseColor("#F43F5E")};
            case "Waiting":
                return new int[]{Color.parseColor("#B45309"), Color.parseColor("#F59E0B")};
            case "Someday":
                return new int[]{Color.parseColor("#4338CA"), Color.parseColor("#6366F1")};
            default:
                return new int[]{Color.parseColor("#3B82F6"), Color.parseColor("#6366F1")};
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
            if (imm != null) {
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            etSearch.setText("");
            searchQuery = "";
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
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
        // Feature 10: toggle subtask collapse
        String subtaskLabel = taskAdapter.isAllSubtasksCollapsed()
                ? "▶ Expand Subtasks" : "▼ Collapse Subtasks";
        popup.getMenu().add(subtaskLabel);
        popup.setOnMenuItemClickListener(item -> {
            String label = item.getTitle().toString();
            if (label.contains("Subtasks")) {
                taskAdapter.toggleAllSubtasksCollapsed();
                return true;
            }
            switch (label) {
                case "Priority":
                    currentSortMode = TaskRepository.SORT_PRIORITY;
                    break;
                case "Due Date":
                    currentSortMode = TaskRepository.SORT_DUE_DATE;
                    break;
                case "Created":
                    currentSortMode = TaskRepository.SORT_CREATED;
                    break;
                case "Title":
                    currentSortMode = TaskRepository.SORT_TITLE;
                    break;
                case "Status":
                    currentSortMode = TaskRepository.SORT_STATUS;
                    break;
            }
            repo.saveSortMode(currentSortMode);
            refreshAll();
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
                case "None":
                    currentGroupMode = TaskRepository.GROUP_NONE;
                    break;
                case "Due Date":
                    currentGroupMode = TaskRepository.GROUP_DUE_DATE;
                    break;
                case "Priority":
                    currentGroupMode = TaskRepository.GROUP_PRIORITY;
                    break;
                case "Category":
                    currentGroupMode = TaskRepository.GROUP_CATEGORY;
                    break;
                case "Status":
                    currentGroupMode = TaskRepository.GROUP_STATUS;
                    break;
            }
            repo.saveGroupMode(currentGroupMode);
            refreshAll();
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
        if (imm != null) {
            imm.hideSoftInputFromWindow(etQuickAdd.getWindowToken(), 0);
        }

        Toast.makeText(this, "Task added!", Toast.LENGTH_SHORT).show();
        refreshAll();
    }

    // ═══════════════════════════════════════════════════════════════
    // REFRESH
    // ═══════════════════════════════════════════════════════════════
    private void refreshAll() {
        refreshStats();
        refreshStreakBanner();
        refreshOverdueBanner();
        refreshTodayFocus();
        refreshTaskList();
        refreshDrawer();
        updateSortGroupLabels();
    }

    private void refreshStats() {
        int todayCount = repo.getTotalTodayCount();
        int completedCount = repo.getCompletedTodayCount();
        int overdueCount = repo.getOverdueCount();
        int starredCount = repo.getStarredCount();

        // Animate count-up for non-zero values
        animateStatCount(tvStatTodayCount, todayCount);
        animateStatCount(tvStatCompletedCount, completedCount);
        animateStatCount(tvStatOverdueCount, overdueCount);
        animateStatCount(tvStatStarredCount, starredCount);

        // Calculate focus score
        int focusScore = calculateFocusScore();
        animateStatCount(tvStatFocusScore, focusScore);

        // Feature 14D: XP Profile Card (below streak banner)
        refreshXpProfileCard();
    }

    /**
     * Feature 14D: Builds or updates the XP profile card shown when showFocusScoreCard is true.
     * Programmatically created since the layout is already built dynamically.
     */
    private void refreshXpProfileCard() {
        // Find or create the XP card container
        android.view.View existingCard = findViewById(0x14D00001); // unique ID
        if (settings == null || !settings.showFocusScoreCard) {
            if (existingCard != null) existingCard.setVisibility(android.view.View.GONE);
            return;
        }

        TaskXpManager xpManager = TaskXpManager.getInstance(this);
        int level = xpManager.getCurrentLevel();
        String levelName = xpManager.getLevelName();
        int xpInLevel = xpManager.getXpForCurrentLevel();
        int xpNeeded = xpManager.getXpToNextLevel();
        float progress = xpManager.getLevelProgress();
        int streak = repo.getCurrentStreak();

        if (existingCard instanceof LinearLayout) {
            // Update existing card
            LinearLayout card = (LinearLayout) existingCard;
            updateXpCardContents(card, level, levelName, xpInLevel, xpNeeded, progress, streak);
            card.setVisibility(android.view.View.VISIBLE);
        } else {
            // Create new card — find where to insert it (after streak banner)
            if (streakBanner != null && streakBanner.getParent() instanceof LinearLayout) {
                LinearLayout parent = (LinearLayout) streakBanner.getParent();
                int index = parent.indexOfChild(streakBanner) + 1;

                LinearLayout card = new LinearLayout(this);
                card.setId(0x14D00001);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.topMargin = dp(8);
                cardLp.leftMargin = dp(12);
                cardLp.rightMargin = dp(12);
                card.setLayoutParams(cardLp);

                // Gradient background
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Color.parseColor("#252B45"), Color.parseColor("#1A1F35")});
                bg.setCornerRadius(dp(12));
                card.setBackground(bg);

                // Tap to open stats
                card.setOnClickListener(v -> {
                    Intent intent = new Intent(this, TaskStatsActivity.class);
                    startActivity(intent);
                });

                updateXpCardContents(card, level, levelName, xpInLevel, xpNeeded, progress, streak);
                parent.addView(card, index);
            }
        }
    }

    private void updateXpCardContents(LinearLayout card, int level, String levelName,
                                       int xpInLevel, int xpNeeded, float progress, int streak) {
        card.removeAllViews();

        // Row 1: Level info + XP numbers
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvLevel = new TextView(this);
        tvLevel.setText("\u2B50 Level " + level + " \u00B7 " + levelName);
        tvLevel.setTextColor(Color.WHITE);
        tvLevel.setTextSize(14);
        tvLevel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams levelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLevel.setLayoutParams(levelLp);
        row1.addView(tvLevel);

        TextView tvXpNums = new TextView(this);
        tvXpNums.setText(String.format(java.util.Locale.US, "%,d / %,d XP", xpInLevel, xpNeeded));
        tvXpNums.setTextColor(Color.parseColor("#94A3B8"));
        tvXpNums.setTextSize(11);
        row1.addView(tvXpNums);

        card.addView(row1);

        // Row 2: Progress bar + streak
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dp(6);
        row2.setLayoutParams(row2Lp);

        // Progress bar
        android.widget.FrameLayout progressContainer = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(0, dp(8), 1f);
        pcLp.setMarginEnd(dp(8));
        progressContainer.setLayoutParams(pcLp);

        // Track
        android.view.View track = new android.view.View(this);
        android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(Color.parseColor("#1E293B"));
        trackBg.setCornerRadius(dp(4));
        track.setBackground(trackBg);
        track.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        progressContainer.addView(track);

        // Fill
        android.view.View fill = new android.view.View(this);
        android.graphics.drawable.GradientDrawable fillBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#F59E0B"), Color.parseColor("#FCD34D")});
        fillBg.setCornerRadius(dp(4));
        fill.setBackground(fillBg);
        int pctInt = Math.round(progress * 100);
        android.widget.FrameLayout.LayoutParams fillLp = new android.widget.FrameLayout.LayoutParams(
                0, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        fill.setLayoutParams(fillLp);
        progressContainer.addView(fill);

        // Post to measure and set fill width
        progressContainer.post(() -> {
            int totalWidth = progressContainer.getWidth();
            int fillWidth = Math.round(totalWidth * progress);
            android.widget.FrameLayout.LayoutParams fLp = (android.widget.FrameLayout.LayoutParams) fill.getLayoutParams();
            fLp.width = fillWidth;
            fill.setLayoutParams(fLp);
        });

        row2.addView(progressContainer);

        // Percentage + streak
        TextView tvPct = new TextView(this);
        tvPct.setText(pctInt + "%");
        tvPct.setTextColor(Color.parseColor("#94A3B8"));
        tvPct.setTextSize(11);
        tvPct.setPadding(0, 0, dp(8), 0);
        row2.addView(tvPct);

        if (streak > 0) {
            TextView tvStreak = new TextView(this);
            tvStreak.setText("\uD83D\uDD25 " + streak + " Day Streak");
            tvStreak.setTextColor(Color.parseColor("#F59E0B"));
            tvStreak.setTextSize(12);
            row2.addView(tvStreak);
        }

        card.addView(row2);
    }

    private void animateStatCount(TextView textView, int targetValue) {
        if (textView == null) {
            return;
        }
        if (targetValue == 0) {
            textView.setText("0");
            textView.setAlpha(0.5f);
            return;
        }
        textView.setAlpha(1.0f);
        ValueAnimator animator = ValueAnimator.ofInt(0, targetValue);
        animator.setDuration(600);
        animator.addUpdateListener(a -> textView.setText(String.valueOf(a.getAnimatedValue())));
        animator.start();
    }

    private int calculateFocusScore() {
        float completionRate = repo.getCompletionRate();
        int totalActive = repo.getTotalActiveCount();
        int overdueCount = repo.getOverdueCount();
        int streak = repo.getCurrentStreak();

        // Base score from completion rate (0-50 points)
        int score = Math.round(completionRate * 50);

        // Streak bonus (0-25 points)
        score += Math.min(streak * 5, 25);

        // Overdue penalty
        if (totalActive > 0) {
            float overdueRate = (float) overdueCount / totalActive;
            score -= Math.round(overdueRate * 25);
        }

        return Math.max(0, Math.min(100, score));
    }

    private void refreshStreakBanner() {
        int streak = repo.getCurrentStreak();

        // Feature 14C: award streak XP (deduplicates per day internally)
        if (streak > 0) {
            TaskXpManager.getInstance(this).onStreakDay(streak);
        }

        if (streak > 0) {
            streakBanner.setBackgroundResource(R.drawable.task_streak_banner_bg);
            tvStreakIcon.setText("🔥");
            tvStreakTitle.setText(streak + " Day Streak");
            tvStreakSubtitle.setText("Keep it up!");
            tvStreakTitle.setTextColor(Color.WHITE);
            tvStreakSubtitle.setTextColor(Color.parseColor("#E0FFFFFF"));
        } else {
            streakBanner.setBackgroundResource(R.drawable.task_streak_banner_muted_bg);
            tvStreakIcon.setText("💪");
            tvStreakTitle.setText("Start Your Streak");
            tvStreakSubtitle.setText("Complete a task today to begin!");
            tvStreakTitle.setTextColor(Color.parseColor("#F59E0B"));
            tvStreakSubtitle.setTextColor(Color.parseColor("#6B7280"));
        }
    }

    private void updateSortGroupLabels() {
        if (btnGroupBy != null) {
            String groupLabel = "None";
            if (currentGroupMode != null) {
                switch (currentGroupMode) {
                    case TaskRepository.GROUP_DUE_DATE:
                        groupLabel = "Due Date";
                        break;
                    case TaskRepository.GROUP_PRIORITY:
                        groupLabel = "Priority";
                        break;
                    case TaskRepository.GROUP_CATEGORY:
                        groupLabel = "Category";
                        break;
                    case TaskRepository.GROUP_STATUS:
                        groupLabel = "Status";
                        break;
                    default:
                        groupLabel = "None";
                        break;
                }
            }
            btnGroupBy.setText("Group: " + groupLabel + " ▾");
        }
        if (btnSortBy != null) {
            String sortLabel = "Priority";
            if (currentSortMode != null) {
                switch (currentSortMode) {
                    case TaskRepository.SORT_PRIORITY:
                        sortLabel = "Priority";
                        break;
                    case TaskRepository.SORT_DUE_DATE:
                        sortLabel = "Due Date";
                        break;
                    case TaskRepository.SORT_CREATED:
                        sortLabel = "Created";
                        break;
                    case TaskRepository.SORT_TITLE:
                        sortLabel = "Title";
                        break;
                    case TaskRepository.SORT_STATUS:
                        sortLabel = "Status";
                        break;
                    default:
                        sortLabel = "Priority";
                        break;
                }
            }
            btnSortBy.setText("Sort: " + sortLabel + " ▾");
        }
    }

    private void refreshOverdueBanner() {
        int overdueCount = repo.getOverdueCount();
        if (overdueCount > 0) {
            overdueAlertBanner.setVisibility(View.VISIBLE);
            tvOverdueMessage.setText("You have " + overdueCount + " task"
                    + (overdueCount > 1 ? "s" : "") + " past their due date");
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
            } catch (Exception ignored) {
            }

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
            // Map chip name "Priority" to repo filter "By Priority"
            String repoFilter = "Priority".equals(currentFilter) ? "By Priority" : currentFilter;
            taskList = repo.filterTasks(repoFilter);
        }

        // Feature 17E: apply project scope filter
        if (projectIdFilter != null) {
            java.util.Iterator<Task> it = taskList.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (!projectIdFilter.equals(t.projectId)) it.remove();
            }
        }

        // For Priority filter, auto-sort by priority
        if ("Priority".equals(currentFilter)) {
            repo.sortTasks(taskList, TaskRepository.SORT_PRIORITY);
        } else {
            repo.sortTasks(taskList, currentSortMode);
        }

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
            tvResultLabel.setText("Results for \"" + searchQuery + "\"");
            tvResultCount.setText(String.valueOf(count));
        } else {
            String label;
            if ("Starred".equals(currentFilter)) {
                label = "⭐ Focus List";
            } else if ("Inbox".equals(currentFilter)) {
                label = "📥 Inbox";
            } else {
                label = currentFilter.equals("All") ? "All Tasks" : currentFilter;
            }
            tvResultLabel.setText(label);
            tvResultCount.setText(count + " task" + (count != 1 ? "s" : ""));
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
        updateFocusBanner();
    }

    private long computeTodayFocusSeconds() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDay = c.getTimeInMillis();
        long total = 0L;
        for (Task t : repo.getAllTasks()) {
            if (t.timerSessions == null) continue;
            for (long[] session : t.timerSessions) {
                if (session.length >= 2 && session[0] >= startOfDay) {
                    total += (session[1] - session[0]) / 1000L;
                }
            }
        }
        return total;
    }

    private String formatFocusDuration(long totalSecs) {
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    private void updateFocusBanner() {
        long totalSecs = computeTodayFocusSeconds();
        View focusBanner = findViewById(R.id.layout_focus_banner);
        if (focusBanner == null) {
            if (totalSecs > 0) {
                Log.d(TAG, "Focus today: " + formatFocusDuration(totalSecs) + " (banner not in layout)");
            }
            return;
        }
        if (totalSecs > 0) {
            focusBanner.setVisibility(View.VISIBLE);
            TextView tvFocusTime = focusBanner.findViewById(R.id.tvFocusTime);
            if (tvFocusTime != null) tvFocusTime.setText("Focus today: " + formatFocusDuration(totalSecs));
        } else {
            focusBanner.setVisibility(View.GONE);
        }
    }

    private void updateEmptyState() {
        if (!searchQuery.isEmpty()) {
            tvEmptyIcon.setText("🔍");
            tvEmptyTitle.setText("No results");
            tvEmptySubtitle.setText("No tasks match \"" + searchQuery + "\"");
        } else {
            switch (currentFilter) {
                case "Today":
                    tvEmptyIcon.setText("☀️");
                    tvEmptyTitle.setText("Nothing due today");
                    tvEmptySubtitle.setText("Enjoy your day or add new tasks!");
                    break;
                case "Upcoming":
                    tvEmptyIcon.setText("🗓️");
                    tvEmptyTitle.setText("No upcoming tasks");
                    tvEmptySubtitle.setText("Schedule tasks for the future");
                    break;
                case "Overdue":
                    tvEmptyIcon.setText("🎉");
                    tvEmptyTitle.setText("Nothing overdue!");
                    tvEmptySubtitle.setText("You're on top of everything — great job!");
                    break;
                case "Starred":
                    tvEmptyIcon.setText("⭐");
                    tvEmptyTitle.setText("No focus tasks");
                    tvEmptySubtitle.setText("Star tasks or set high/urgent priority to see them here");
                    break;
                case "Inbox":
                    tvEmptyIcon.setText("📥");
                    tvEmptyTitle.setText("Inbox is empty");
                    tvEmptySubtitle.setText("Uncategorised and Personal tasks appear here");
                    break;
                case "Completed":
                    tvEmptyIcon.setText("🏆");
                    tvEmptyTitle.setText("No completed tasks yet");
                    tvEmptySubtitle.setText("Complete your first task to see it here");
                    break;
                case "Priority":
                    tvEmptyIcon.setText("🔥");
                    tvEmptyTitle.setText("No high priority tasks");
                    tvEmptySubtitle.setText("Set priorities to focus on what matters");
                    break;
                case "Next Actions":
                    tvEmptyIcon.setText("⚡");
                    tvEmptyTitle.setText("No next actions");
                    tvEmptySubtitle.setText("Mark tasks as 'Next Action' to see them here");
                    break;
                case "MIT":
                    tvEmptyIcon.setText("🎯");
                    tvEmptyTitle.setText("No Most Important Tasks");
                    tvEmptySubtitle.setText("Mark tasks as MIT to focus on what matters most");
                    break;
                case "Waiting":
                    tvEmptyIcon.setText("⏳");
                    tvEmptyTitle.setText("Not waiting on anything");
                    tvEmptySubtitle.setText("Tasks waiting on others will appear here");
                    break;
                case "Someday":
                    tvEmptyIcon.setText("💭");
                    tvEmptyTitle.setText("No someday/maybe items");
                    tvEmptySubtitle.setText("Park ideas here for later review");
                    break;
                default:
                    tvEmptyIcon.setText("🚀");
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
            awardXpForCompletion(task);
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
        popup.getMenu().add("Share");
        popup.getMenu().add("Move to Category");
        popup.getMenu().add("Change Priority");
        if (task.isCompleted()) {
            popup.getMenu().add("Mark Incomplete");
        } else {
            popup.getMenu().add("Mark Complete");
        }
        popup.getMenu().add("Move to Trash");
        popup.getMenu().add("Copy Link");

        // GTD actions
        popup.getMenu().add(task.isNextAction ? "Remove Next Action" : "Mark as Next Action");
        popup.getMenu().add(task.isMIT ? "Remove MIT" : "Mark as MIT");
        popup.getMenu().add("Move to Someday/Maybe");
        if (!task.isWaiting()) {
            popup.getMenu().add("Set Waiting For...");
        } else {
            popup.getMenu().add("Clear Waiting For");
        }

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
                case "Share":
                    TaskExportManager.shareTaskAsText(this, task);
                    break;
                case "Move to Category":
                    showCategoryPicker(task);
                    break;
                case "Change Priority":
                    showPriorityPicker(task);
                    break;
                case "Mark Complete":
                    {
                        Task snapshot = task.deepCopy();
                        repo.completeTask(task.id);
                        refreshAll();
                        undoManager.record(snapshot, "complete", "Task completed ✓",
                                () -> {
                                    repo.uncompleteTask(task.id);
                                    connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
                                },
                                () -> connectionManager.sendCommand("TASK_COMPLETE:" + task.id));
                    }
                    break;
                case "Mark Incomplete":
                    repo.uncompleteTask(task.id);
                    connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
                    refreshAll();
                    break;
                case "Move to Trash":
                    {
                        Task snapshot = task.deepCopy();
                        repo.trashTask(task.id);
                        refreshAll();
                        undoManager.record(snapshot, "trash", "Moved to trash",
                                () -> {
                                    repo.restoreFromTrash(task.id);
                                    connectionManager.sendCommand("TASK_ADD_JSON:" + task.toJson());
                                },
                                () -> connectionManager.sendCommand("TASK_DELETE:" + task.id));
                    }
                    break;
                case "Copy Link":
                    String deeplink = "myfirstapp://task/" + task.id;
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Task Link", deeplink));
                    Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show();
                    break;

                // GTD menu actions
                case "Mark as Next Action":
                    repo.setNextAction(task.id, true);
                    Toast.makeText(this, "Marked as Next Action", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Remove Next Action":
                    repo.setNextAction(task.id, false);
                    Toast.makeText(this, "Removed Next Action flag", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Mark as MIT":
                    repo.setMIT(task.id, true);
                    Toast.makeText(this, "Marked as MIT", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Remove MIT":
                    repo.setMIT(task.id, false);
                    Toast.makeText(this, "Removed MIT flag", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Move to Someday/Maybe":
                    repo.setStatus(task.id, Task.STATUS_SOMEDAY);
                    Toast.makeText(this, "Moved to Someday/Maybe", Toast.LENGTH_SHORT).show();
                    refreshAll();
                    break;
                case "Set Waiting For...":
                    showWaitingForDialog(task);
                    break;
                case "Clear Waiting For":
                    repo.setWaitingFor(task.id, null);
                    Toast.makeText(this, "Waiting cleared", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onTaskSwiped(Task task, int swipeDirection) {
        if (swipeDirection == TaskAdapter.SWIPE_COMPLETE) {
            // Toggle completion
            if (task.isCompleted()) {
                repo.uncompleteTask(task.id);
                connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
                Toast.makeText(this, "Task marked incomplete", Toast.LENGTH_SHORT).show();
                refreshAll();
            } else {
                Task snapshot = task.deepCopy();
                repo.completeTask(task.id);
                refreshAll();
                undoManager.record(snapshot, "complete", "Task completed ✓",
                        () -> {
                            repo.uncompleteTask(task.id);
                            connectionManager.sendCommand("TASK_UNCOMPLETE:" + task.id);
                        },
                        () -> connectionManager.sendCommand("TASK_COMPLETE:" + task.id));
            }
        } else if (swipeDirection == TaskAdapter.SWIPE_ARCHIVE) {
            // Push to Tomorrow + undo snackbar
            Task snapshot = task.deepCopy();

            // Compute tomorrow's date
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            String tomorrow = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.getTime());

            // Push the task
            task.dueDate = tomorrow;
            task.rescheduleCount++;
            task.updatedAt = System.currentTimeMillis();
            repo.updateTask(task);
            refreshAll();

            // Snackbar with undo
            com.google.android.material.snackbar.Snackbar snackbar =
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "Pushed to tomorrow", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
            snackbar.setAction("UNDO", v -> {
                task.dueDate = snapshot.dueDate;
                task.rescheduleCount = snapshot.rescheduleCount;
                task.updatedAt = System.currentTimeMillis();
                repo.updateTask(task);
                refreshAll();
            });
            snackbar.show();
        }
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

    private void showWaitingForDialog(Task task) {
        EditText input = new EditText(this);
        input.setHint("Person or event...");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (task.waitingFor != null) input.setText(task.waitingFor);
        new AlertDialog.Builder(this)
                .setTitle("Waiting For")
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    String val = input.getText().toString().trim();
                    repo.setWaitingFor(task.id, val.isEmpty() ? null : val);
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // GAMIFICATION — XP awarding on task completion (Feature 14)
    // ═══════════════════════════════════════════════════════════════
    private void awardXpForCompletion(Task task) {
        TaskXpManager xpManager = TaskXpManager.getInstance(this);
        int xpResult = xpManager.onTaskCompleted(task);
        repo.addKarma(1);

        // Also keep legacy repo-level XP in sync
        int xpAmount = xpResult < 0 ? 15 : xpResult;
        repo.addXp(xpAmount);

        // Show XP floater if focus score card is enabled
        if (settings != null && settings.showFocusScoreCard) {
            if (xpResult < 0) {
                // Level-up!
                int newLevel = Math.abs(xpResult);
                showXpFloater("Level Up! Lv. " + newLevel, true);
            } else if (xpResult > 0) {
                showXpFloater("+" + xpResult + " XP", false);
            }
        }
    }

    /**
     * Feature 14B: Floating XP label that animates upward and fades.
     */
    private void showXpFloater(String text, boolean isLevelUp) {
        try {
            android.view.ViewGroup rootLayout = findViewById(android.R.id.content);
            if (rootLayout == null) return;

            TextView tvXp = new TextView(this);
            tvXp.setText(text);
            tvXp.setTextSize(isLevelUp ? 20 : 16);
            tvXp.setTypeface(null, android.graphics.Typeface.BOLD);
            tvXp.setTextColor(Color.parseColor(isLevelUp ? "#FFFFFF" : "#F59E0B"));
            tvXp.setGravity(android.view.Gravity.CENTER);

            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
            lp.topMargin = dp(200);
            tvXp.setLayoutParams(lp);

            rootLayout.addView(tvXp);

            // Animate translateY -120dp + alpha fade
            float translationPx = -dp(120);
            android.animation.ObjectAnimator translateAnim = android.animation.ObjectAnimator.ofFloat(
                    tvXp, "translationY", 0f, translationPx);
            translateAnim.setDuration(800);

            android.animation.ObjectAnimator alphaAnim = android.animation.ObjectAnimator.ofFloat(
                    tvXp, "alpha", 1f, 0f);
            alphaAnim.setDuration(800);

            android.animation.AnimatorSet animSet = new android.animation.AnimatorSet();
            animSet.playTogether(translateAnim, alphaAnim);
            animSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    rootLayout.removeView(tvXp);
                }
            });
            animSet.start();
        } catch (Exception e) {
            android.util.Log.w(TAG, "XP floater error: " + e.getMessage());
        }
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
            if (bulkActionBar != null) {
                bulkActionBar.setVisibility(View.VISIBLE);
            }
            if (tvBulkCount != null) {
                tvBulkCount.setText(count + " selected");
            }
        } else if (!active) {
            if (bulkActionBar != null) {
                bulkActionBar.setVisibility(View.GONE);
            }
        } else {
            if (tvBulkCount != null) {
                tvBulkCount.setText("0 selected");
            }
        }
    }

    @Override
    public void onTaskUpdated(Task task) {
        repo.updateTask(task);
        refreshTaskList();
    }

    private void setupBulkActions() {
        if (bulkActionBar == null) {
            return;
        }
        bulkActionBar.setVisibility(View.GONE);

        // Find bulk action buttons by tag or add them dynamically
        TextView btnBulkComplete = findViewById(R.id.btnBulkComplete);
        TextView btnBulkPriority = findViewById(R.id.btnBulkPriority);
        TextView btnBulkCategory = findViewById(R.id.btnBulkCategory);
        TextView btnBulkStar = findViewById(R.id.btnBulkStar);
        TextView btnBulkTrash = findViewById(R.id.btnBulkTrash);
        TextView btnBulkCancel = findViewById(R.id.btnBulkCancel);

        if (btnBulkComplete != null) {
            btnBulkComplete.setOnClickListener(v -> {
                List<String> ids = new ArrayList<>(taskAdapter.getSelectedIds());
                List<Task> snapshots = new ArrayList<>();
                for (String id : ids) {
                    Task t = repo.getTaskById(id);
                    if (t != null) snapshots.add(t.deepCopy());
                    repo.completeTask(id);
                }
                taskAdapter.exitMultiSelect();
                refreshAll();
                undoManager.recordBulk(snapshots, "bulk_complete",
                        ids.size() + " tasks completed",
                        () -> {
                            for (Task snap : snapshots) {
                                repo.uncompleteTask(snap.id);
                                connectionManager.sendCommand("TASK_UNCOMPLETE:" + snap.id);
                            }
                        },
                        () -> {
                            for (String id : ids) {
                                connectionManager.sendCommand("TASK_COMPLETE:" + id);
                            }
                        });
            });
        }

        if (btnBulkPriority != null) {
            btnBulkPriority.setOnClickListener(v -> {
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
        }

        if (btnBulkCategory != null) {
            btnBulkCategory.setOnClickListener(v -> {
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
        }

        if (btnBulkStar != null) {
            btnBulkStar.setOnClickListener(v -> {
                repo.bulkStar(new ArrayList<>(taskAdapter.getSelectedIds()));
                taskAdapter.exitMultiSelect();
                refreshAll();
            });
        }

        if (btnBulkTrash != null) {
            btnBulkTrash.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Move to Trash")
                        .setMessage("Trash " + taskAdapter.getSelectedCount() + " tasks?")
                        .setPositiveButton("Trash", (d, w) -> {
                            List<String> ids = new ArrayList<>(taskAdapter.getSelectedIds());
                            List<Task> snapshots = new ArrayList<>();
                            for (String id : ids) {
                                Task t = repo.getTaskById(id);
                                if (t != null) snapshots.add(t.deepCopy());
                                repo.trashTask(id);
                            }
                            taskAdapter.exitMultiSelect();
                            refreshAll();
                            undoManager.recordBulk(snapshots, "bulk_trash",
                                    ids.size() + " tasks trashed",
                                    () -> {
                                        for (Task snap : snapshots) {
                                            repo.restoreFromTrash(snap.id);
                                        }
                                    },
                                    () -> {
                                        for (String id : ids) {
                                            connectionManager.sendCommand("TASK_DELETE:" + id);
                                        }
                                    });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        if (btnBulkCancel != null) {
            btnBulkCancel.setOnClickListener(v -> {
                taskAdapter.exitMultiSelect();
            });
        }

        // Setup navigation drawer
        setupDrawer();
    }

    // ═══════════════════════════════════════════════════════════════
    // GRID / LIST VIEW MODE
    // ═══════════════════════════════════════════════════════════════
    private void setViewMode(String mode) {
        settings.taskViewMode = mode;
        settings.save();

        if ("grid".equals(mode)) {
            GridLayoutManager glm = new GridLayoutManager(this, 2);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return taskAdapter.getItemViewType(position) == TaskAdapter.TYPE_GROUP_HEADER
                            ? 2 : 1;
                }
            });
            recyclerTasks.setLayoutManager(glm);
            if (btnToggleView != null)
                btnToggleView.setImageResource(android.R.drawable.ic_menu_agenda);
        } else {
            recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
            if (btnToggleView != null)
                btnToggleView.setImageResource(android.R.drawable.ic_dialog_dialer);
        }

        taskAdapter.setGridMode("grid".equals(mode));
        taskAdapter.notifyDataSetChanged();
    }

    // ═══════════════════════════════════════════════════════════════
    // NAVIGATION DRAWER
    // ═══════════════════════════════════════════════════════════════
    private void setupDrawer() {
        // Manage Categories button
        TextView btnManageCategories = findViewById(R.id.btnDrawerManageCategories);
        if (btnManageCategories != null) {
            btnManageCategories.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, TaskCategoriesActivity.class));
            });
        }

        // ── Extra navigation items appended below Manage Categories ──
        View drawerRoot = findViewById(R.id.navDrawer);
        if (drawerRoot instanceof LinearLayout) {
            LinearLayout drawerLinear = (LinearLayout) drawerRoot;
            // Check if we already added the extra container
            if (drawerLinear.findViewWithTag("extra_nav") == null) {
                LinearLayout extraContainer = new LinearLayout(this);
                extraContainer.setTag("extra_nav");
                extraContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                extraContainer.setLayoutParams(lp);
                drawerLinear.addView(extraContainer);

                addDrawerNavItem(extraContainer, "�", "Projects", ProjectListActivity.class);
                addDrawerNavItem(extraContainer, "�📋", "Templates", TaskTemplatesActivity.class);
                addDrawerNavItem(extraContainer, "📥", "Import Tasks", TaskImportActivity.class);
                addDrawerNavItem(extraContainer, "📊", "Kanban View", KanbanBoardActivity.class);
                addDrawerNavItem(extraContainer, "🕐", "Time Block", TimeBlockActivity.class);
                addDrawerNavItem(extraContainer, "🎯", "Focus Mode", FocusModeActivity.class);
                addDrawerNavItem(extraContainer, "⏱", "Time Report", TaskTimeReportActivity.class);
                addDrawerNavItem(extraContainer, "🧭", "Eisenhower Matrix", TaskEisenhowerActivity.class);
                addDrawerNavItem(extraContainer, "⚙️", "Settings", TaskManagerSettingsActivity.class);

                // Export actions
                addDrawerActionItem(extraContainer, "📅", "Export to Calendar (.ics)", () -> {
                    TaskExportManager.exportToIcs(this, repo.getAllTasks());
                });
                addDrawerActionItem(extraContainer, "📄", "Export to PDF", () -> {
                    TaskExportManager.exportToPdf(this, repo.getAllTasks());
                });
            }
        }
    }

    private void addDrawerNavItem(LinearLayout container, String icon, String label,
            Class<?> activityClass) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        tvIcon.setPadding(0, 0, dp(12), 0);
        row.addView(tvIcon);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#D1D5DB"));
        tvLabel.setTextSize(14);
        row.addView(tvLabel);

        row.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, activityClass));
        });

        container.addView(row);
    }

    private void addDrawerActionItem(LinearLayout container, String icon, String label,
            Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(16);
        tvIcon.setPadding(0, 0, dp(12), 0);
        row.addView(tvIcon);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#D1D5DB"));
        tvLabel.setTextSize(14);
        row.addView(tvLabel);

        row.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            action.run();
        });

        container.addView(row);
    }

    private void refreshDrawer() {
        if (drawerLayout == null) {
            return;
        }

        // Update active task count
        TextView tvDrawerTaskCount = findViewById(R.id.tvDrawerTaskCount);
        if (tvDrawerTaskCount != null) {
            int activeCount = repo.getTotalActiveCount();
            tvDrawerTaskCount.setText(activeCount + " active task" + (activeCount != 1 ? "s" : ""));
        }

        // Build views section
        LinearLayout viewsContainer = findViewById(R.id.drawerViewsContainer);
        if (viewsContainer != null) {
            viewsContainer.removeAllViews();

            String[][] viewItems = {
                {"\uD83D\uDCCB", "All Tasks", String.valueOf(repo.getTotalActiveCount()), "#60A5FA"},
                {"\uD83D\uDCC5", "Today", String.valueOf(repo.getTotalTodayCount()), "#3B82F6"},
                {"\uD83D\uDDD3\uFE0F", "Upcoming", "", "#8B5CF6"},
                {"\u26A0\uFE0F", "Overdue", String.valueOf(repo.getOverdueCount()), "#EF4444"},
                {"\u2B50", "Starred", String.valueOf(repo.getStarredCount()), "#F59E0B"},
                {"\uD83D\uDCE5", "Inbox", String.valueOf(repo.getInboxCount()), "#60A5FA"},
                {"\u26A1", "Next Actions", String.valueOf(repo.getNextActionCount()), "#FBBF24"},
                {"\uD83C\uDFAF", "MIT", String.valueOf(repo.getMITCount()), "#F43F5E"},
                {"\u23F3", "Waiting", String.valueOf(repo.getWaitingCount()), "#F59E0B"},
                {"\uD83D\uDCAD", "Someday", String.valueOf(repo.getSomedayCount()), "#6366F1"},
                {"\u2705", "Completed", String.valueOf(repo.getTotalCompletedCount()), "#10B981"},
                {"\uD83D\uDDD1\uFE0F", "Trash", String.valueOf(repo.getTrashCount()), "#6B7280"}
            };

            // Feature 15F: conditionally add Private Tasks view if any exist
            int privateCount = repo.getPrivateCount();
            if (privateCount > 0) {
                // Insert before Completed entry
                String[][] extendedItems = new String[viewItems.length + 1][];
                int insertIdx = viewItems.length - 2; // before Completed and Trash
                System.arraycopy(viewItems, 0, extendedItems, 0, insertIdx);
                extendedItems[insertIdx] = new String[]{"\uD83D\uDD12", "Private Tasks",
                        String.valueOf(privateCount), "#A855F7"};
                System.arraycopy(viewItems, insertIdx, extendedItems, insertIdx + 1,
                        viewItems.length - insertIdx);
                viewItems = extendedItems;
            }

            for (String[] item : viewItems) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                boolean isActive = item[1].equals(currentFilter)
                        || (item[1].equals("All Tasks") && "All".equals(currentFilter));
                row.setBackgroundResource(isActive
                        ? R.drawable.task_drawer_item_active_bg
                        : R.drawable.task_drawer_item_bg);

                // Icon
                TextView icon = new TextView(this);
                icon.setText(item[0]);
                icon.setTextSize(16);
                icon.setPadding(0, 0, dp(12), 0);
                row.addView(icon);

                // Name
                TextView name = new TextView(this);
                name.setText(item[1]);
                name.setTextColor(isActive ? Color.parseColor("#60A5FA") : Color.parseColor("#D1D5DB"));
                name.setTextSize(14);
                name.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(name);

                // Count badge
                if (!item[2].isEmpty() && !"0".equals(item[2])) {
                    TextView count = new TextView(this);
                    count.setText(item[2]);
                    count.setTextColor(Color.parseColor(item[3]));
                    count.setTextSize(12);
                    count.setTypeface(null, android.graphics.Typeface.BOLD);
                    row.addView(count);
                }

                final String filterName = item[1].equals("All Tasks") ? "All" : item[1];
                row.setOnClickListener(v -> {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    if ("Trash".equals(filterName)) {
                        startActivity(new Intent(this, TaskTrashActivity.class));
                    } else if ("Private Tasks".equals(filterName)) {
                        // Feature 15F: prompt biometric before showing private tasks
                        TaskVaultManager.getInstance(this).promptUnlock(this, () -> {
                            selectFilter("Private");
                        });
                    } else if ("Completed".equals(filterName)) {
                        selectFilter("Completed");
                    } else {
                        selectFilter(filterName);
                    }
                });

                viewsContainer.addView(row);
            }
        }

        // Build categories section
        LinearLayout categoriesContainer = findViewById(R.id.drawerCategoriesContainer);
        if (categoriesContainer != null) {
            categoriesContainer.removeAllViews();
            List<TaskCategory> categories = repo.getAllCategories();

            for (TaskCategory cat : categories) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));

                // Color dot
                View dot = new View(this);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
                dotLp.setMarginEnd(dp(10));
                dot.setLayoutParams(dotLp);
                try {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(cat.getColorInt());
                    dot.setBackground(gd);
                } catch (Exception ignored) {
                }
                row.addView(dot);

                // Icon
                TextView icon = new TextView(this);
                icon.setText(cat.icon);
                icon.setTextSize(14);
                icon.setPadding(0, 0, dp(8), 0);
                row.addView(icon);

                // Name
                TextView name = new TextView(this);
                name.setText(cat.name);
                name.setTextColor(Color.parseColor("#B0BEC5"));
                name.setTextSize(13);
                name.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(name);

                // Count
                int taskCount = repo.getTaskCountByCategory(cat.name);
                if (taskCount > 0) {
                    TextView count = new TextView(this);
                    count.setText(String.valueOf(taskCount));
                    count.setTextColor(Color.parseColor("#6B7280"));
                    count.setTextSize(12);
                    row.addView(count);
                }

                categoriesContainer.addView(row);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Close drawer first if open
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
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
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
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
    // MEETINGS STRIP
    // ═══════════════════════════════════════════════════════════════
    private void loadMeetingsStrip() {
        if (meetingsStripContainer == null || meetingRepo == null) {
            return;
        }
        meetingsStripContainer.removeAllViews();
        List<Meeting> upcomingMeetings = meetingRepo.getUpcomingMeetings();
        int limit = Math.min(5, upcomingMeetings.size());
        for (int i = 0; i < limit; i++) {
            addMeetingStripCard(upcomingMeetings.get(i));
        }
        addAddMeetingCard();
        int visibility = upcomingMeetings.isEmpty() ? View.GONE : View.VISIBLE;
        if (meetingsSectionHeader != null) {
            meetingsSectionHeader.setVisibility(visibility);
        }
        View stripScroll = findViewById(R.id.meetingsStripScroll);
        if (stripScroll != null) {
            stripScroll.setVisibility(visibility);
        }
    }

    private void addMeetingStripCard(Meeting m) {
        View card = LayoutInflater.from(this).inflate(
                R.layout.item_meeting_card_strip, meetingsStripContainer, false);

        TextView tvTitle = card.findViewById(R.id.tvStripTitle);
        TextView tvTime = card.findViewById(R.id.tvStripTime);
        TextView tvDuration = card.findViewById(R.id.tvStripDuration);
        TextView tvPlatform = card.findViewById(R.id.tvStripPlatformBadge);
        TextView tvAttendees = card.findViewById(R.id.tvStripAttendeeCount);
        TextView btnJoin = card.findViewById(R.id.btnStripJoin);

        tvTitle.setText(m.title);
        tvTime.setText(m.getFormattedDateRange());

        String dur = m.getDurationText();
        tvDuration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
        tvDuration.setText(dur);

        if (m.platform != null && !m.platform.isEmpty()) {
            tvPlatform.setText(m.platform);
            tvPlatform.setVisibility(View.VISIBLE);
        }

        int count = m.getAttendeeCount();
        tvAttendees.setText(count + " " + (count == 1 ? "person" : "people"));

        if ((m.isHappeningNow() || m.isStartingSoon())
                && m.meetingLink != null && !m.meetingLink.trim().isEmpty()) {
            btnJoin.setVisibility(View.VISIBLE);
            btnJoin.setOnClickListener(v
                    -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(m.meetingLink))));
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, MeetingDetailActivity.class);
            intent.putExtra(MeetingDetailActivity.EXTRA_MEETING_ID, m.id);
            startActivity(intent);
        });

        meetingsStripContainer.addView(card);
    }

    private void addAddMeetingCard() {
        TextView addCard = new TextView(this);
        addCard.setText("+");
        addCard.setTextColor(Color.parseColor("#6C63FF"));
        addCard.setTextSize(28);
        addCard.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.parseColor("#1E1E3A"));
        addCard.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(80));
        lp.setMarginEnd(dp(10));
        addCard.setLayoutParams(lp);

        addCard.setOnClickListener(v
                -> startActivity(new Intent(this, CreateMeetingActivity.class)));
        meetingsStripContainer.addView(addCard);
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
