package com.prajwal.myfirstapp.tasks;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.prajwal.myfirstapp.R;

import java.util.List;

/**
 * Feature 17D: Project list Activity showing all projects with progress cards.
 * Tap to open filtered task list, long-press for context menu.
 */
public class ProjectListActivity extends AppCompatActivity {

    private ProjectRepository projectRepo;
    private TaskRepository taskRepo;
    private LinearLayout projectsContainer;
    private LinearLayout emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dark theme root
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0A0F1E"));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // Toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(16), dp(12), dp(12));
        toolbar.setBackgroundColor(Color.parseColor("#0D1321"));

        TextView btnBack = new TextView(this);
        btnBack.setText("\u2190");
        btnBack.setTextSize(22);
        btnBack.setTextColor(Color.WHITE);
        btnBack.setPadding(dp(8), dp(4), dp(16), dp(4));
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Projects");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(tvTitle);

        // FAB-like "+ New" button
        TextView btnNew = new TextView(this);
        btnNew.setText("+ New");
        btnNew.setTextSize(14);
        btnNew.setTextColor(Color.WHITE);
        btnNew.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#6366F1"));
        btnBg.setCornerRadius(dp(20));
        btnNew.setBackground(btnBg);
        btnNew.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnNew.setOnClickListener(v -> showCreateProjectDialog());
        toolbar.addView(btnNew);

        root.addView(toolbar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(16));

        // Projects container
        projectsContainer = new LinearLayout(this);
        projectsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(projectsContainer);

        // Empty state
        emptyState = new LinearLayout(this);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(32), dp(64), dp(32), dp(64));
        emptyState.setVisibility(View.GONE);

        TextView tvEmptyIcon = new TextView(this);
        tvEmptyIcon.setText("\uD83D\uDCC1");
        tvEmptyIcon.setTextSize(48);
        tvEmptyIcon.setGravity(Gravity.CENTER);
        emptyState.addView(tvEmptyIcon);

        TextView tvEmptyTitle = new TextView(this);
        tvEmptyTitle.setText("No Projects");
        tvEmptyTitle.setTextSize(18);
        tvEmptyTitle.setTextColor(Color.WHITE);
        tvEmptyTitle.setTypeface(null, Typeface.BOLD);
        tvEmptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(12);
        tvEmptyTitle.setLayoutParams(titleLp);
        emptyState.addView(tvEmptyTitle);

        TextView tvEmptyDesc = new TextView(this);
        tvEmptyDesc.setText("Track bounded efforts like campaigns, events, and initiatives.");
        tvEmptyDesc.setTextSize(13);
        tvEmptyDesc.setTextColor(Color.parseColor("#6B7280"));
        tvEmptyDesc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(4);
        tvEmptyDesc.setLayoutParams(descLp);
        emptyState.addView(tvEmptyDesc);

        TextView btnCreate = new TextView(this);
        btnCreate.setText("+ Create Project");
        btnCreate.setTextSize(14);
        btnCreate.setTextColor(Color.parseColor("#6366F1"));
        btnCreate.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = dp(16);
        btnCreate.setLayoutParams(createLp);
        btnCreate.setOnClickListener(v -> showCreateProjectDialog());
        emptyState.addView(btnCreate);

        content.addView(emptyState);
        scrollView.addView(content);
        root.addView(scrollView);

        setContentView(root);

        // Init repos
        projectRepo = new ProjectRepository(this);
        taskRepo = new TaskRepository(this);

        loadProjects();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    private void loadProjects() {
        projectsContainer.removeAllViews();
        List<Project> projects = projectRepo.getActiveProjects();

        if (projects.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            projectsContainer.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        projectsContainer.setVisibility(View.VISIBLE);

        for (Project project : projects) {
            addProjectCard(project);
        }
    }

    private void addProjectCard(Project project) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#1A1F35"));
        cardBg.setCornerRadius(dp(12));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);

        // Left accent strip
        View accent = new View(this);
        String accentColor = project.colorHex != null ? project.colorHex :
                (project.color != null ? project.color : "#6366F1");
        GradientDrawable accentBg = new GradientDrawable();
        try {
            accentBg.setColor(Color.parseColor(accentColor));
        } catch (Exception e) {
            accentBg.setColor(Color.parseColor("#6366F1"));
        }
        float[] radii = new float[]{dp(12), dp(12), 0, 0, 0, 0, dp(12), dp(12)};
        accentBg.setCornerRadii(radii);
        accent.setBackground(accentBg);
        accent.setLayoutParams(new LinearLayout.LayoutParams(dp(4),
                LinearLayout.LayoutParams.MATCH_PARENT));
        card.addView(accent);

        // Content
        LinearLayout contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setPadding(dp(12), dp(12), dp(12), dp(12));
        contentCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Name row
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(project.name);
        tvName.setTextSize(16);
        tvName.setTextColor(Color.WHITE);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        nameRow.addView(tvName);

        if (project.goalDeadline != null && !project.goalDeadline.isEmpty()) {
            TextView tvDeadline = new TextView(this);
            tvDeadline.setText("Due " + project.goalDeadline);
            tvDeadline.setTextSize(10);
            tvDeadline.setTextColor(Color.parseColor("#F59E0B"));
            nameRow.addView(tvDeadline);
        }

        contentCol.addView(nameRow);

        // Description
        if (project.description != null && !project.description.isEmpty()) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(project.description);
            tvDesc.setTextSize(12);
            tvDesc.setTextColor(Color.parseColor("#6B7280"));
            tvDesc.setMaxLines(1);
            tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = dp(2);
            tvDesc.setLayoutParams(descLp);
            contentCol.addView(tvDesc);
        }

        // Task count
        int totalTasks = getTaskCountForProject(project.id);
        int completedTasks = getCompletedTaskCountForProject(project.id);

        TextView tvCount = new TextView(this);
        tvCount.setText(completedTasks + " / " + totalTasks + " tasks");
        tvCount.setTextSize(11);
        tvCount.setTextColor(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = dp(6);
        tvCount.setLayoutParams(countLp);
        contentCol.addView(tvCount);

        // Progress bar
        if (totalTasks > 0) {
            android.widget.FrameLayout progressFrame = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams pfLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
            pfLp.topMargin = dp(4);
            progressFrame.setLayoutParams(pfLp);

            View trackView = new View(this);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(Color.parseColor("#FFFFFF0D".length() == 9 ?
                    "#1AFFFFFF" : "#1E293B"));
            trackBg.setCornerRadius(dp(2));
            trackView.setBackground(trackBg);
            trackView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            progressFrame.addView(trackView);

            View fillView = new View(this);
            GradientDrawable fillBg = new GradientDrawable();
            try {
                fillBg.setColor(Color.parseColor(accentColor));
            } catch (Exception e) {
                fillBg.setColor(Color.parseColor("#6366F1"));
            }
            fillBg.setCornerRadius(dp(2));
            fillView.setBackground(fillBg);
            fillView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    0, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            progressFrame.addView(fillView);

            float progress = (float) completedTasks / totalTasks;
            progressFrame.post(() -> {
                int width = Math.round(progressFrame.getWidth() * progress);
                android.widget.FrameLayout.LayoutParams flp =
                        (android.widget.FrameLayout.LayoutParams) fillView.getLayoutParams();
                flp.width = width;
                fillView.setLayoutParams(flp);
            });

            contentCol.addView(progressFrame);
        }

        card.addView(contentCol);

        // Click — open project tasks
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskManagerActivity.class);
            intent.putExtra("projectId", project.id);
            intent.putExtra("projectName", project.name);
            startActivity(intent);
        });

        // Long-click — context menu
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle(project.name)
                    .setItems(new String[]{"Edit", "Archive", "Delete"}, (d, which) -> {
                        switch (which) {
                            case 0: // Edit
                                showEditProjectDialog(project);
                                break;
                            case 1: // Archive
                                projectRepo.archiveProject(project.id);
                                loadProjects();
                                break;
                            case 2: // Delete
                                new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                                        .setTitle("Delete Project?")
                                        .setMessage("This cannot be undone. Tasks won't be deleted.")
                                        .setPositiveButton("Delete", (dd, ww) -> {
                                            projectRepo.deleteProject(project.id);
                                            loadProjects();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                                break;
                        }
                    })
                    .show();
            return true;
        });

        projectsContainer.addView(card);
    }

    private int getTaskCountForProject(String projectId) {
        int count = 0;
        for (Task t : taskRepo.getAllTasks()) {
            if (!t.isTrashed && projectId.equals(t.projectId)) count++;
        }
        return count;
    }

    private int getCompletedTaskCountForProject(String projectId) {
        int count = 0;
        for (Task t : taskRepo.getAllTasks()) {
            if (!t.isTrashed && t.isCompleted() && projectId.equals(t.projectId)) count++;
        }
        return count;
    }

    private void showCreateProjectDialog() {
        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("Project name");
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(Color.parseColor("#6B7280"));
        etName.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("New Project")
                .setView(etName)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        Project p = new Project();
                        p.name = name;
                        projectRepo.addProject(p);
                        loadProjects();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditProjectDialog(Project project) {
        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setText(project.name);
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(Color.parseColor("#6B7280"));
        etName.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Edit Project")
                .setView(etName)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        project.name = name;
                        projectRepo.updateProject(project);
                        loadProjects();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
