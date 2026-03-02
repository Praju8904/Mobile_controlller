package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full file browser for Hub files — browsable by type, folder, smart folder, or project.
 */
public class HubFileBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_FILTER_TYPE = "filter_type";
    public static final String EXTRA_FOLDER_ID = "folder_id";
    public static final String EXTRA_PROJECT_ID = "project_id";
    public static final String EXTRA_SMART_FOLDER_ID = "smart_folder_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_FAVOURITES_ONLY = "favourites_only";
    public static final String EXTRA_RECENT_ONLY = "recent_only";
    public static final String EXTRA_PICK_MODE = "pick_mode";
    public static final String EXTRA_PICKED_FILE_ID = "picked_file_id";
    public static final String EXTRA_COLLECTION_ID = "collectionId";
    public static final String EXTRA_FILTER_SOURCE = "filterSource";
    public static final String EXTRA_FILTER_STALE = "filterStale";

    private static final int VIEW_GRID = 0;
    private static final int VIEW_LIST = 1;
    private static final int VIEW_TIMELINE = 2;
    private static final int GRID_NAME_MAX_CHARS = 14;

    private HubFileRepository repo;

    private TextView tvBrowserTitle;
    private TextView tvBrowserCount;
    private GridView fileGridView;
    private ListView fileListView;
    private android.widget.ScrollView timelineScrollView;
    private LinearLayout timelineContainer;
    private LinearLayout browserEmptyState;
    private LinearLayout multiSelectBottomBar;
    private LinearLayout multiSelectTopBar;
    private TextView tvMultiSelectCount;
    private LinearLayout breadcrumbContainer;

    private List<HubFile> allFiles = new ArrayList<>();
    private List<HubFile> displayedFiles = new ArrayList<>();

    private int currentView = VIEW_GRID;
    private int sortMode = 0; // 0=Name A-Z, 1=Name Z-A, 2=Date Newest, 3=Date Oldest, 4=Size Largest, 5=Size Smallest

    private boolean multiSelectMode = false;
    private Set<String> selectedIds = new HashSet<>();

    private String filterType;
    private String folderId;
    private String projectId;
    private String smartFolderId;
    private String title;
    private boolean favouritesOnly;
    private boolean recentOnly;
    private boolean pickMode;

    private Set<HubFile.FileType> activeTypeFilters = new HashSet<>();
    private Set<HubFile.Source> activeSourceFilters = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_file_browser);

        repo = HubFileRepository.getInstance(this);

        filterType = getIntent().getStringExtra(EXTRA_FILTER_TYPE);
        folderId = getIntent().getStringExtra(EXTRA_FOLDER_ID);
        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        smartFolderId = getIntent().getStringExtra(EXTRA_SMART_FOLDER_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        favouritesOnly = getIntent().getBooleanExtra(EXTRA_FAVOURITES_ONLY, false);
        recentOnly = getIntent().getBooleanExtra(EXTRA_RECENT_ONLY, false);
        pickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);

        bindViews();
        setupClickListeners();
        loadFiles();
        refreshFiles();
    }

    private void bindViews() {
        tvBrowserTitle = findViewById(R.id.tvBrowserTitle);
        tvBrowserCount = findViewById(R.id.tvBrowserCount);
        fileGridView = findViewById(R.id.fileGridView);
        fileListView = findViewById(R.id.fileListView);
        timelineScrollView = findViewById(R.id.timelineScrollView);
        timelineContainer = findViewById(R.id.timelineContainer);
        browserEmptyState = findViewById(R.id.browserEmptyState);
        multiSelectBottomBar = findViewById(R.id.multiSelectBottomBar);
        multiSelectTopBar = findViewById(R.id.multiSelectTopBar);
        tvMultiSelectCount = multiSelectTopBar.findViewById(R.id.tvMultiSelectCount);
        breadcrumbContainer = findViewById(R.id.breadcrumbContainer);

        if (title != null) tvBrowserTitle.setText(title);

        addBreadcrumb(title != null ? title : "Files");
    }

    private void addBreadcrumb(String label) {
        float dp = getResources().getDisplayMetrics().density;
        TextView crumb = new TextView(this);
        crumb.setText(label);
        crumb.setTextColor(Color.parseColor("#8B5CF6"));
        crumb.setTextSize(13);
        crumb.setPadding((int)(8*dp), 0, (int)(8*dp), 0);
        breadcrumbContainer.addView(crumb);
    }

    private void setupClickListeners() {
        findViewById(R.id.btnHubBrowserBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnBrowserSearch).setOnClickListener(v ->
                startActivity(new Intent(this, HubSearchActivity.class)));

        findViewById(R.id.btnBrowserSort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btnBrowserViewToggle).setOnClickListener(v -> cycleViewMode());
        findViewById(R.id.btnBrowserSelect).setOnClickListener(v -> enterMultiSelectMode());
        findViewById(R.id.btnBrowserFilter).setOnClickListener(v -> showFilterDialog());

        // Multi-select bar
        findViewById(R.id.btnMultiSelectCancel).setOnClickListener(v -> exitMultiSelectMode());
        findViewById(R.id.btnMultiSelectAll).setOnClickListener(v -> selectAll());
        findViewById(R.id.btnMultiShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.btnMultiMove).setOnClickListener(v ->
                Toast.makeText(this, "Move — coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnMultiAddToProject).setOnClickListener(v ->
                Toast.makeText(this, "Add to Project — coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnMultiDelete).setOnClickListener(v -> deleteSelected());

        fileGridView.setOnItemClickListener((parent, view, position, id) -> {
            if (multiSelectMode) toggleSelection(displayedFiles.get(position));
            else openFile(displayedFiles.get(position));
        });
        fileGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            enterMultiSelectMode();
            toggleSelection(displayedFiles.get(position));
            return true;
        });

        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            if (multiSelectMode) toggleSelection(displayedFiles.get(position));
            else openFile(displayedFiles.get(position));
        });
        fileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            enterMultiSelectMode();
            toggleSelection(displayedFiles.get(position));
            return true;
        });
    }

    private void loadFiles() {
        allFiles.clear();
        // Check new extras first
        String collectionId = getIntent().getStringExtra(EXTRA_COLLECTION_ID);
        String filterSource = getIntent().getStringExtra(EXTRA_FILTER_SOURCE);
        boolean filterStale = getIntent().getBooleanExtra(EXTRA_FILTER_STALE, false);

        if (collectionId != null) {
            allFiles.addAll(repo.getFilesForCollection(collectionId));
        } else if (filterSource != null) {
            try {
                HubFile.Source src = HubFile.Source.valueOf(filterSource);
                allFiles.addAll(repo.getFilesBySource(src));
            } catch (Exception e) { allFiles.addAll(repo.getAllFiles()); }
        } else if (filterStale) {
            long sixMonthsAgo = System.currentTimeMillis() - 180L * 86_400_000L;
            for (HubFile f : repo.getAllFiles()) {
                if (f.lastAccessedAt < sixMonthsAgo) allFiles.add(f);
            }
        } else if (filterType != null) {
            try {
                HubFile.FileType ft = HubFile.FileType.valueOf(filterType);
                allFiles.addAll(repo.getFilesByType(ft));
            } catch (Exception e) {
                allFiles.addAll(repo.getAllFiles());
            }
        } else if (folderId != null) {
            for (HubFile f : repo.getAllFiles()) {
                if (folderId.equals(f.folderId)) allFiles.add(f);
            }
        } else if (projectId != null) {
            for (HubFile f : repo.getAllFiles()) {
                if (projectId.equals(f.projectId)) allFiles.add(f);
            }
        } else if (smartFolderId != null) {
            for (HubFolder folder : repo.getAllFolders()) {
                if (folder.id.equals(smartFolderId)) {
                    allFiles.addAll(repo.getFilesForSmartFolderExtended(folder));
                    break;
                }
            }
        } else if (favouritesOnly) {
            allFiles.addAll(repo.getFavourites());
        } else if (recentOnly) {
            allFiles.addAll(repo.getRecentFiles(100));
        } else {
            allFiles.addAll(repo.getAllFiles());
        }
    }

    public void refreshFiles() {
        displayedFiles.clear();
        for (HubFile f : allFiles) {
            if (!f.isHidden) {
                if (!activeTypeFilters.isEmpty() && !activeTypeFilters.contains(f.fileType)) continue;
                if (!activeSourceFilters.isEmpty() && !activeSourceFilters.contains(f.source)) continue;
                displayedFiles.add(f);
            }
        }

        // Sort
        Collections.sort(displayedFiles, (a, b) -> {
            String nameA = a.displayName != null ? a.displayName : (a.originalFileName != null ? a.originalFileName : "");
            String nameB = b.displayName != null ? b.displayName : (b.originalFileName != null ? b.originalFileName : "");
            switch (sortMode) {
                case 0: return nameA.compareToIgnoreCase(nameB);
                case 1: return nameB.compareToIgnoreCase(nameA);
                case 2: return Long.compare(b.importedAt, a.importedAt);
                case 3: return Long.compare(a.importedAt, b.importedAt);
                case 4: return Long.compare(b.fileSize, a.fileSize);
                case 5: return Long.compare(a.fileSize, b.fileSize);
                default: return 0;
            }
        });

        tvBrowserCount.setText(displayedFiles.size() + " files");

        if (displayedFiles.isEmpty()) {
            browserEmptyState.setVisibility(View.VISIBLE);
            fileGridView.setVisibility(View.GONE);
            fileListView.setVisibility(View.GONE);
            timelineScrollView.setVisibility(View.GONE);
        } else {
            browserEmptyState.setVisibility(View.GONE);
            updateViewMode();
        }
    }

    private void updateViewMode() {
        if (currentView == VIEW_GRID) {
            fileGridView.setVisibility(View.VISIBLE);
            fileListView.setVisibility(View.GONE);
            timelineScrollView.setVisibility(View.GONE);
            fileGridView.setAdapter(new FileAdapter(this, displayedFiles));
        } else if (currentView == VIEW_LIST) {
            fileGridView.setVisibility(View.GONE);
            fileListView.setVisibility(View.VISIBLE);
            timelineScrollView.setVisibility(View.GONE);
            fileListView.setAdapter(new FileListAdapter(this, displayedFiles));
        } else {
            fileGridView.setVisibility(View.GONE);
            fileListView.setVisibility(View.GONE);
            timelineScrollView.setVisibility(View.VISIBLE);
            buildTimelineView();
        }
    }

    private void buildTimelineView() {
        timelineContainer.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        long now = System.currentTimeMillis();
        long dayMs = 86_400_000L;
        long todayStart = now - (now % dayMs);
        long yesterdayStart = todayStart - dayMs;
        long weekStart = todayStart - 6 * dayMs;
        long monthStart = todayStart - 29 * dayMs;

        String[] groups = {"Today", "Yesterday", "This Week", "This Month", "Older"};
        List<List<HubFile>> buckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) buckets.add(new ArrayList<>());

        for (HubFile f : displayedFiles) {
            if (f.importedAt >= todayStart) buckets.get(0).add(f);
            else if (f.importedAt >= yesterdayStart) buckets.get(1).add(f);
            else if (f.importedAt >= weekStart) buckets.get(2).add(f);
            else if (f.importedAt >= monthStart) buckets.get(3).add(f);
            else buckets.get(4).add(f);
        }

        for (int g = 0; g < groups.length; g++) {
            List<HubFile> bucket = buckets.get(g);
            if (bucket.isEmpty()) continue;

            TextView header = new TextView(this);
            header.setText(groups[g]);
            header.setTextColor(Color.parseColor("#8B5CF6"));
            header.setTextSize(13);
            header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, (int)(12*dp), 0, (int)(6*dp));
            header.setLayoutParams(lp);
            timelineContainer.addView(header);

            for (HubFile f : bucket) {
                LinearLayout row = buildFileRowView(f, dp);
                timelineContainer.addView(row);
            }
        }
    }

    private LinearLayout buildFileRowView(HubFile file, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(10 * dp);
        bg.setColor(Color.parseColor("#1A1A2E"));
        row.setBackground(bg);
        row.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(4*dp));
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setFocusable(true);
        row.setForeground(getDrawable(android.R.attr.selectableItemBackground));

        TextView emoji = new TextView(this);
        emoji.setText(file.getTypeEmoji());
        emoji.setTextSize(22);
        emoji.setLayoutParams(new LinearLayout.LayoutParams((int)(36*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(emoji);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(this);
        name.setText(file.displayName != null ? file.displayName : file.originalFileName);
        name.setTextColor(Color.WHITE);
        name.setTextSize(14);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        TextView meta = new TextView(this);
        meta.setText(file.getFormattedSize() + " · " + formatDate(file.importedAt));
        meta.setTextColor(Color.parseColor("#64748B"));
        meta.setTextSize(11);
        info.addView(meta);

        row.addView(info);

        // Mood tag overlay
        if (file.moodTag != null && !file.moodTag.isEmpty()) {
            TextView tvMood = new TextView(this);
            tvMood.setText(file.moodTag);
            tvMood.setTextSize(14);
            tvMood.setPadding(4, 0, 0, 0);
            row.addView(tvMood);
        }

        row.setOnClickListener(v -> openFile(file));
        return row;
    }

    private void showSortDialog() {
        String[] options = {"Name A–Z", "Name Z–A", "Date Newest", "Date Oldest", "Size Largest", "Size Smallest"};
        new AlertDialog.Builder(this)
                .setTitle("Sort By")
                .setSingleChoiceItems(options, sortMode, (dialog, which) -> {
                    sortMode = which;
                    dialog.dismiss();
                    refreshFiles();
                })
                .show();
    }

    private void showFilterDialog() {
        HubFile.FileType[] types = HubFile.FileType.values();
        String[] typeNames = new String[types.length];
        boolean[] typeChecked = new boolean[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name();
            typeChecked[i] = activeTypeFilters.contains(types[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle("Filter by Type")
                .setMultiChoiceItems(typeNames, typeChecked, (dialog, which, isChecked) -> {
                    if (isChecked) activeTypeFilters.add(types[which]);
                    else activeTypeFilters.remove(types[which]);
                })
                .setPositiveButton("Apply", (dialog, which) -> refreshFiles())
                .setNegativeButton("Clear All", (dialog, which) -> {
                    activeTypeFilters.clear();
                    refreshFiles();
                })
                .show();
    }

    private void cycleViewMode() {
        currentView = (currentView + 1) % 3;
        String[] modeNames = {"Grid", "List", "Timeline"};
        Toast.makeText(this, modeNames[currentView] + " view", Toast.LENGTH_SHORT).show();
        updateViewMode();
    }

    private void enterMultiSelectMode() {
        multiSelectMode = true;
        multiSelectTopBar.setVisibility(View.VISIBLE);
        multiSelectBottomBar.setVisibility(View.VISIBLE);
        findViewById(R.id.normalTopBar).setVisibility(View.GONE);
        updateMultiSelectCount();
    }

    private void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedIds.clear();
        multiSelectTopBar.setVisibility(View.GONE);
        multiSelectBottomBar.setVisibility(View.GONE);
        findViewById(R.id.normalTopBar).setVisibility(View.VISIBLE);
        updateViewMode();
    }

    private void toggleSelection(HubFile file) {
        if (selectedIds.contains(file.id)) selectedIds.remove(file.id);
        else selectedIds.add(file.id);
        updateMultiSelectCount();
    }

    private void selectAll() {
        for (HubFile f : displayedFiles) selectedIds.add(f.id);
        updateMultiSelectCount();
    }

    private void updateMultiSelectCount() {
        tvMultiSelectCount.setText(selectedIds.size() + " selected");
    }

    private void shareSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<android.net.Uri> uris = new ArrayList<>();
        for (HubFile f : displayedFiles) {
            if (selectedIds.contains(f.id) && f.filePath != null && !f.filePath.isEmpty()) {
                try {
                    java.io.File file = new java.io.File(f.filePath);
                    if (file.exists()) {
                        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".provider", file);
                        uris.add(uri);
                    }
                } catch (Exception e) {
                    // skip files that can't be shared
                }
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "No valid file paths", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("*/*");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Files"));
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = selectedIds.size();
        new AlertDialog.Builder(this)
                .setTitle("Delete Files")
                .setMessage("Delete " + count + " file(s)? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (String id : selectedIds) repo.deleteFile(id);
                    Toast.makeText(this, count + " file(s) deleted", Toast.LENGTH_SHORT).show();
                    exitMultiSelectMode();
                    loadFiles();
                    refreshFiles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFile(HubFile file) {
        if (pickMode) {
            Intent result = new Intent();
            result.putExtra(EXTRA_PICKED_FILE_ID, file.id);
            setResult(Activity.RESULT_OK, result);
            finish();
            return;
        }
        Intent intent = new Intent(this, HubFileViewerActivity.class);
        intent.putExtra(HubFileViewerActivity.EXTRA_FILE_ID, file.id);
        startActivity(intent);
    }

    private String formatDate(long ts) {
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(ts));
    }

    // ─── FileAdapter (Grid) ───────────────────────────────────────────────────

    static class FileAdapter extends BaseAdapter {
        private final Context context;
        private final List<HubFile> files;

        FileAdapter(Context context, List<HubFile> files) {
            this.context = context;
            this.files = files;
        }

        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int pos) { return files.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float dp = context.getResources().getDisplayMetrics().density;
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(12 * dp);
            bg.setColor(Color.parseColor("#1A1A2E"));
            cell.setBackground(bg);
            cell.setPadding((int)(8*dp), (int)(12*dp), (int)(8*dp), (int)(10*dp));

            HubFile file = files.get(position);

            TextView emoji = new TextView(context);
            emoji.setText(file.getTypeEmoji());
            emoji.setTextSize(28);
            emoji.setGravity(Gravity.CENTER);
            emoji.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(emoji);

            String display = file.displayName != null ? file.displayName : file.originalFileName;
            if (display != null && display.length() > GRID_NAME_MAX_CHARS) display = display.substring(0, GRID_NAME_MAX_CHARS - 2) + "…";
            TextView name = new TextView(context);
            name.setText(display != null ? display : "File");
            name.setTextColor(Color.parseColor("#E2E8F0"));
            name.setTextSize(11);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(name);

            TextView size = new TextView(context);
            size.setText(file.getFormattedSize());
            size.setTextColor(Color.parseColor("#64748B"));
            size.setTextSize(10);
            size.setGravity(Gravity.CENTER);
            size.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(size);

            return cell;
        }
    }

    // ─── FileListAdapter (List) ───────────────────────────────────────────────

    static class FileListAdapter extends BaseAdapter {
        private final Context context;
        private final List<HubFile> files;

        FileListAdapter(Context context, List<HubFile> files) {
            this.context = context;
            this.files = files;
        }

        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int pos) { return files.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float dp = context.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(10 * dp);
            bg.setColor(Color.parseColor("#1A1A2E"));
            row.setBackground(bg);
            row.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, (int)(4*dp));
            row.setLayoutParams(lp);

            HubFile file = files.get(position);

            TextView emoji = new TextView(context);
            emoji.setText(file.getTypeEmoji());
            emoji.setTextSize(24);
            emoji.setLayoutParams(new LinearLayout.LayoutParams((int)(40*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(emoji);

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(context);
            String display = file.displayName != null ? file.displayName : file.originalFileName;
            name.setText(display != null ? display : "File");
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);

            TextView meta = new TextView(context);
            String typeStr = file.fileType != null ? file.fileType.name() : "FILE";
            meta.setText(typeStr + " · " + file.getFormattedSize());
            meta.setTextColor(Color.parseColor("#64748B"));
            meta.setTextSize(11);
            info.addView(meta);

            row.addView(info);
            return row;
        }
    }
}
