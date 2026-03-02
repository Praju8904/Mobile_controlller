package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Project detail — shows a project's files, notes, and activity.
 */
public class HubProjectDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "project_id";

    private HubFileRepository repo;
    private HubProject project;
    private List<HubFile> projectFiles = new ArrayList<>();
    private int sortMode = 0;

    private FrameLayout projectDetailHeader;
    private TextView tvProjectDetailName;
    private TextView tvProjectDetailDesc;
    private TextView tvProjectFileCount;
    private TextView tvProjectSize;
    private TextView tvStatFiles;
    private TextView tvStatSize;
    private TextView tvStatModified;
    private LinearLayout fileTypeBreakdownBar;
    private LinearLayout projectFilesContainer;
    private LinearLayout projectFilesEmpty;
    private LinearLayout projectActivityContainer;
    private EditText etProjectNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_project_detail);

        repo = HubFileRepository.getInstance(this);

        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId == null) { finish(); return; }

        for (HubProject p : repo.getAllProjects()) {
            if (p.id.equals(projectId)) { project = p; break; }
        }
        if (project == null) { finish(); return; }

        bindViews();
        setupClickListeners();
        loadData();
    }

    private void bindViews() {
        projectDetailHeader = findViewById(R.id.projectDetailHeader);
        tvProjectDetailName = findViewById(R.id.tvProjectDetailName);
        tvProjectDetailDesc = findViewById(R.id.tvProjectDetailDesc);
        tvProjectFileCount = findViewById(R.id.tvProjectFileCount);
        tvProjectSize = findViewById(R.id.tvProjectSize);
        tvStatFiles = findViewById(R.id.tvStatFiles);
        tvStatSize = findViewById(R.id.tvStatSize);
        tvStatModified = findViewById(R.id.tvStatModified);
        fileTypeBreakdownBar = findViewById(R.id.fileTypeBreakdownBar);
        projectFilesContainer = findViewById(R.id.projectFilesContainer);
        projectFilesEmpty = findViewById(R.id.projectFilesEmpty);
        projectActivityContainer = findViewById(R.id.projectActivityContainer);
        etProjectNotes = findViewById(R.id.etProjectNotes);
    }

    private void setupClickListeners() {
        findViewById(R.id.btnProjectDetailBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnProjectMenu).setOnClickListener(v -> showProjectMenu());
        findViewById(R.id.btnAddFiles).setOnClickListener(v -> {
            Intent intent = new Intent(this, HubFileBrowserActivity.class);
            intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Add to " + project.name);
            startActivity(intent);
        });
        findViewById(R.id.btnProjectViewToggle).setOnClickListener(v -> {
            sortMode = (sortMode + 1) % 3;
            String[] modes = {"Name", "Date", "Size"};
            Toast.makeText(this, "Sorted by " + modes[sortMode], Toast.LENGTH_SHORT).show();
            renderFiles();
        });

        etProjectNotes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String prefsKey = "hub_project_notes_" + project.id;
                getSharedPreferences("hub_project_notes", MODE_PRIVATE).edit()
                        .putString(prefsKey, s.toString()).apply();
            }
        });
    }

    private void loadData() {
        // Load notes
        String prefsKey = "hub_project_notes_" + project.id;
        String savedNotes = getSharedPreferences("hub_project_notes", MODE_PRIVATE)
                .getString(prefsKey, project.description != null ? project.description : "");
        etProjectNotes.setText(savedNotes);

        // Set header gradient
        String colorHex = project.colorHex != null ? project.colorHex : "#8B5CF6";
        try {
            int c = Color.parseColor(colorHex);
            int dark = darkenColor(c, 0.4f);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Color.parseColor("#0F0F14"), c});
            projectDetailHeader.setBackground(bg);
        } catch (Exception e) { /* use default */ }

        tvProjectDetailName.setText(project.name != null ? project.name : "Project");
        tvProjectDetailDesc.setText(project.description != null ? project.description : "");

        // Load files for this project
        projectFiles.clear();
        for (HubFile f : repo.getAllFiles()) {
            if (project.id.equals(f.projectId)) projectFiles.add(f);
        }

        // Compute stats
        long totalSize = 0;
        long lastMod = 0;
        for (HubFile f : projectFiles) {
            totalSize += f.fileSize;
            if (f.updatedAt > lastMod) lastMod = f.updatedAt;
        }

        // Update project counters
        project.fileCount = projectFiles.size();
        project.totalSize = totalSize;

        tvProjectFileCount.setText(project.fileCount + " files");
        tvProjectSize.setText(formatBytes(totalSize));
        tvStatFiles.setText(String.valueOf(project.fileCount));
        tvStatSize.setText(formatBytes(totalSize));
        tvStatModified.setText(lastMod > 0 ?
                new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(lastMod)) : "—");

        buildTypeBreakdownBar();
        renderFiles();
        loadActivity();
    }

    private void buildTypeBreakdownBar() {
        fileTypeBreakdownBar.removeAllViews();
        if (projectFiles.isEmpty()) return;

        Map<HubFile.FileType, Long> typeSizes = new HashMap<>();
        for (HubFile f : projectFiles) {
            HubFile.FileType t = f.fileType != null ? f.fileType : HubFile.FileType.OTHER;
            typeSizes.put(t, typeSizes.getOrDefault(t, 0L) + f.fileSize);
        }

        long total = 0;
        for (long v : typeSizes.values()) total += v;
        if (total == 0) return;

        String[] typeColors = {"#EF4444", "#3B82F6", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16"};
        int colorIdx = 0;

        for (Map.Entry<HubFile.FileType, Long> entry : typeSizes.entrySet()) {
            float fraction = (float) entry.getValue() / total;
            int weightInt = Math.max(1, Math.round(fraction * 1000));

            View seg = new View(this);
            String hexColor = typeColors[colorIdx % typeColors.length];
            try { seg.setBackgroundColor(Color.parseColor(hexColor)); } catch (Exception e) { seg.setBackgroundColor(Color.GRAY); }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weightInt);
            seg.setLayoutParams(lp);
            fileTypeBreakdownBar.addView(seg);
            colorIdx++;
        }
    }

    private void renderFiles() {
        projectFilesContainer.removeAllViews();

        if (projectFiles.isEmpty()) {
            projectFilesEmpty.setVisibility(View.VISIBLE);
            return;
        }
        projectFilesEmpty.setVisibility(View.GONE);

        List<HubFile> sorted = new ArrayList<>(projectFiles);
        Collections.sort(sorted, (a, b) -> {
            String nameA = a.displayName != null ? a.displayName : (a.originalFileName != null ? a.originalFileName : "");
            String nameB = b.displayName != null ? b.displayName : (b.originalFileName != null ? b.originalFileName : "");
            switch (sortMode) {
                case 0: return nameA.compareToIgnoreCase(nameB);
                case 1: return Long.compare(b.importedAt, a.importedAt);
                case 2: return Long.compare(b.fileSize, a.fileSize);
                default: return 0;
            }
        });

        float dp = getResources().getDisplayMetrics().density;

        for (HubFile f : sorted) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(10 * dp);
            bg.setColor(Color.parseColor("#1A1A2E"));
            row.setBackground(bg);
            row.setPadding((int)(12*dp), (int)(10*dp), (int)(8*dp), (int)(10*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, (int)(6*dp));
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setFocusable(true);
            row.setForeground(getDrawable(android.R.attr.selectableItemBackground));

            TextView emoji = new TextView(this);
            emoji.setText(f.getTypeEmoji());
            emoji.setTextSize(22);
            emoji.setLayoutParams(new LinearLayout.LayoutParams((int)(36*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(emoji);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(this);
            String display = f.displayName != null ? f.displayName : f.originalFileName;
            name.setText(display != null ? display : "File");
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);

            TextView meta = new TextView(this);
            meta.setText(f.getFormattedSize());
            meta.setTextColor(Color.parseColor("#64748B"));
            meta.setTextSize(11);
            info.addView(meta);

            row.addView(info);

            Button delete = new Button(this);
            delete.setText("✕");
            delete.setTextColor(Color.parseColor("#64748B"));
            delete.setTextSize(14);
            delete.setBackground(null);
            delete.setLayoutParams(new LinearLayout.LayoutParams((int)(36*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            delete.setPadding(0, 0, 0, 0);
            delete.setOnClickListener(v -> confirmRemoveFile(f));
            row.addView(delete);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, HubFileViewerActivity.class);
                intent.putExtra(HubFileViewerActivity.EXTRA_FILE_ID, f.id);
                startActivity(intent);
            });

            projectFilesContainer.addView(row);
        }
    }

    private void confirmRemoveFile(HubFile file) {
        new AlertDialog.Builder(this)
                .setTitle("Remove File")
                .setMessage("Remove \"" + (file.displayName != null ? file.displayName : file.originalFileName) + "\" from this project?")
                .setPositiveButton("Remove", (d, w) -> {
                    file.projectId = null;
                    repo.updateFile(file);
                    projectFiles.remove(file);
                    renderFiles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadActivity() {
        projectActivityContainer.removeAllViews();
        List<FileActivity> activities = repo.getRecentActivities(50);
        Set<String> fileIds = new HashSet<>();
        for (HubFile f : projectFiles) fileIds.add(f.id);

        float dp = getResources().getDisplayMetrics().density;
        int shown = 0;

        for (FileActivity act : activities) {
            if (!fileIds.contains(act.fileId)) continue;
            if (shown >= 5) break;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int)(6*dp), 0, (int)(6*dp));

            TextView dot = new TextView(this);
            dot.setText("•");
            dot.setTextColor(Color.parseColor("#8B5CF6"));
            dot.setTextSize(16);
            dot.setLayoutParams(new LinearLayout.LayoutParams((int)(20*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(dot);

            TextView text = new TextView(this);
            text.setText(act.fileTypeEmoji + " " + act.fileName + " — " + act.getActionDescription());
            text.setTextColor(Color.parseColor("#94A3B8"));
            text.setTextSize(12);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(text);

            projectActivityContainer.addView(row);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("No recent activity");
            empty.setTextColor(Color.parseColor("#475569"));
            empty.setTextSize(13);
            projectActivityContainer.addView(empty);
        }
    }

    private void showProjectMenu() {
        String[] options = {"Edit Project", "Export Project", "Archive", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(project.name)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showEditDialog(); break;
                        case 1: Toast.makeText(this, "Export feature — ZIP coming", Toast.LENGTH_SHORT).show(); break;
                        case 2: Toast.makeText(this, "Project archived", Toast.LENGTH_SHORT).show(); finish(); break;
                        case 3: confirmDeleteProject(); break;
                    }
                })
                .show();
    }

    private void showEditDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*dp), (int)(8*dp), (int)(20*dp), 0);

        EditText etName = new EditText(this);
        etName.setText(project.name);
        etName.setTextColor(Color.WHITE);
        etName.setHint("Project name");
        etName.setHintTextColor(Color.parseColor("#64748B"));
        layout.addView(etName);

        EditText etDesc = new EditText(this);
        etDesc.setText(project.description);
        etDesc.setTextColor(Color.WHITE);
        etDesc.setHint("Description");
        etDesc.setHintTextColor(Color.parseColor("#64748B"));
        layout.addView(etDesc);

        new AlertDialog.Builder(this)
                .setTitle("Edit Project")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    project.name = etName.getText().toString().trim();
                    project.description = etDesc.getText().toString().trim();
                    repo.updateProject(project);
                    loadData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteProject() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Project")
                .setMessage("Delete project \"" + project.name + "\"? Files will not be deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    // Remove project association from files
                    for (HubFile f : projectFiles) {
                        f.projectId = null;
                        repo.updateFile(f);
                    }
                    // Delete project from repository
                    repo.deleteProject(project.id);
                    Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

