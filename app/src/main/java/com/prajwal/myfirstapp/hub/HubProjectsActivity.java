package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Projects home — shows all projects in a 2-column grid.
 */
public class HubProjectsActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private LinearLayout projectsGridContainer;
    private LinearLayout projectsEmptyState;

    private List<HubProject> projects = new ArrayList<>();
    private int sortMode = 0; // 0=Recent, 1=Name, 2=Size, 3=File Count

    private static final String[] PROJECT_COLORS = {
            "#8B5CF6", "#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#EC4899"
    };
    private static final String[] PROJECT_EMOJIS = {
            "💼", "📁", "🎨", "🚀", "📊", "🎯", "🔬", "🎵", "📸", "💡"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_projects);

        repo = HubFileRepository.getInstance(this);

        projectsGridContainer = findViewById(R.id.projectsGridContainer);
        projectsEmptyState = findViewById(R.id.projectsEmptyState);

        findViewById(R.id.btnProjectsBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnProjectsSort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.fabNewProject).setOnClickListener(v -> showNewProjectDialog());

        buildQuickAccess();
        loadAndRender();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndRender();
    }

    private void loadAndRender() {
        projects.clear();
        projects.addAll(repo.getAllProjects());
        sortProjects();
        renderGrid();
    }

    private void sortProjects() {
        Collections.sort(projects, (a, b) -> {
            switch (sortMode) {
                case 0: return Long.compare(b.updatedAt, a.updatedAt);
                case 1: return (a.name != null ? a.name : "").compareToIgnoreCase(b.name != null ? b.name : "");
                case 2: return Long.compare(b.totalSize, a.totalSize);
                case 3: return Integer.compare(b.fileCount, a.fileCount);
                default: return 0;
            }
        });
    }

    private void renderGrid() {
        projectsGridContainer.removeAllViews();

        if (projects.isEmpty()) {
            projectsEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        projectsEmptyState.setVisibility(View.GONE);

        float dp = getResources().getDisplayMetrics().density;
        int rowCount = (projects.size() + 1) / 2;

        for (int r = 0; r < rowCount; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, (int)(12*dp));
            row.setLayoutParams(rlp);

            int idx1 = r * 2;
            int idx2 = r * 2 + 1;

            row.addView(buildProjectCard(projects.get(idx1), dp));

            if (idx2 < projects.size()) {
                LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams((int)(12*dp), 1);
                View space = new View(this);
                space.setLayoutParams(spacer);
                row.addView(space);
                row.addView(buildProjectCard(projects.get(idx2), dp));
            } else {
                // Empty placeholder to keep grid aligned
                LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams((int)(12*dp), 1);
                View space = new View(this);
                space.setLayoutParams(spacer);
                row.addView(space);
                View placeholder = new View(this);
                placeholder.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
                row.addView(placeholder);
            }

            projectsGridContainer.addView(row);
        }
    }

    private LinearLayout buildProjectCard(HubProject project, float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)(16*dp), (int)(16*dp), (int)(16*dp), (int)(16*dp));
        card.setClickable(true);
        card.setFocusable(true);
        card.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(160*dp), 1));

        String color = project.colorHex != null ? project.colorHex : "#8B5CF6";
        try {
            int c = Color.parseColor(color);
            int dark = darkenColor(c, 0.5f);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{c, dark});
            bg.setCornerRadius(16 * dp);
            card.setBackground(bg);
        } catch (Exception e) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1A1A2E"));
            bg.setCornerRadius(16 * dp);
            card.setBackground(bg);
        }
        card.setForeground(getDrawable(android.R.attr.selectableItemBackground));

        TextView icon = new TextView(this);
        icon.setText(project.iconIdentifier != null ? project.iconIdentifier : "💼");
        icon.setTextSize(32);
        icon.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(icon);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1));
        card.addView(spacer);

        TextView name = new TextView(this);
        name.setText(project.name != null ? project.name : "Project");
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        if (project.description != null && !project.description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(project.description);
            desc.setTextColor(Color.parseColor("#CBD5E1"));
            desc.setTextSize(11);
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(desc);
        }

        TextView meta = new TextView(this);
        meta.setText(project.fileCount + " files · " + project.getFormattedSize());
        meta.setTextColor(Color.parseColor("#CBD5E1"));
        meta.setTextSize(11);
        meta.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(meta);

        card.setOnClickListener(v -> openProject(project));
        return card;
    }

    private int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    private void openProject(HubProject project) {
        Intent intent = new Intent(this, HubProjectDetailActivity.class);
        intent.putExtra(HubProjectDetailActivity.EXTRA_PROJECT_ID, project.id);
        startActivity(intent);
    }

    private void showSortDialog() {
        String[] options = {"Recent", "Name", "Size", "File Count"};
        new AlertDialog.Builder(this)
                .setTitle("Sort By")
                .setSingleChoiceItems(options, sortMode, (dialog, which) -> {
                    sortMode = which;
                    dialog.dismiss();
                    loadAndRender();
                })
                .show();
    }

    private void showNewProjectDialog() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*dp), (int)(8*dp), (int)(20*dp), 0);

        EditText etName = new EditText(this);
        etName.setHint("Project name");
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(Color.parseColor("#64748B"));
        layout.addView(etName);

        EditText etDesc = new EditText(this);
        etDesc.setHint("Description (optional)");
        etDesc.setTextColor(Color.WHITE);
        etDesc.setHintTextColor(Color.parseColor("#64748B"));
        layout.addView(etDesc);

        // Color picker label
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Pick Color:");
        colorLabel.setTextColor(Color.parseColor("#94A3B8"));
        colorLabel.setTextSize(13);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, (int)(12*dp), 0, (int)(6*dp));
        colorLabel.setLayoutParams(clp);
        layout.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selectedColor = {PROJECT_COLORS[0]};

        for (String hex : PROJECT_COLORS) {
            Button btn = new Button(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            try { circle.setColor(Color.parseColor(hex)); } catch (Exception e) { circle.setColor(Color.GRAY); }
            btn.setBackground(circle);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int)(32*dp), (int)(32*dp));
            blp.setMargins(0, 0, (int)(8*dp), 0);
            btn.setLayoutParams(blp);
            btn.setPadding(0, 0, 0, 0);
            btn.setOnClickListener(v -> selectedColor[0] = hex);
            colorRow.addView(btn);
        }
        layout.addView(colorRow);

        // Emoji picker
        TextView emojiLabel = new TextView(this);
        emojiLabel.setText("Pick Icon:");
        emojiLabel.setTextColor(Color.parseColor("#94A3B8"));
        emojiLabel.setTextSize(13);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.setMargins(0, (int)(12*dp), 0, (int)(6*dp));
        emojiLabel.setLayoutParams(elp);
        layout.addView(emojiLabel);

        LinearLayout emojiRow = new LinearLayout(this);
        emojiRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selectedEmoji = {PROJECT_EMOJIS[0]};

        for (String em : PROJECT_EMOJIS) {
            Button btn = new Button(this);
            btn.setText(em);
            btn.setTextSize(18);
            btn.setBackground(null);
            LinearLayout.LayoutParams bblp = new LinearLayout.LayoutParams((int)(36*dp), (int)(36*dp));
            btn.setLayoutParams(bblp);
            btn.setPadding(0, 0, 0, 0);
            btn.setOnClickListener(v -> selectedEmoji[0] = em);
            emojiRow.addView(btn);
        }
        layout.addView(emojiRow);

        new AlertDialog.Builder(this)
                .setTitle("New Project")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter a project name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    HubProject project = new HubProject(
                            name,
                            etDesc.getText().toString().trim(),
                            selectedColor[0],
                            selectedEmoji[0]
                    );
                    repo.addProject(project);
                    Toast.makeText(this, "Project created!", Toast.LENGTH_SHORT).show();
                    loadAndRender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildQuickAccess() {
        LinearLayout qa = findViewById(R.id.projectsQuickAccess);
        if (qa == null) return;
        float dp = getResources().getDisplayMetrics().density;

        String[][] items = {{"All Files", "📄"}, {"Recent Files", "🕐"}, {"Favourites", "⭐"}};
        for (String[] item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(10 * dp);
            bg.setColor(Color.parseColor("#1A1A2E"));
            row.setBackground(bg);
            row.setPadding((int)(14*dp), (int)(12*dp), (int)(14*dp), (int)(12*dp));
            row.setClickable(true);
            row.setFocusable(true);
            row.setForeground(getDrawable(android.R.attr.selectableItemBackground));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, (int)(6*dp));
            row.setLayoutParams(rlp);

            TextView emojiTv = new TextView(this);
            emojiTv.setText(item[1]);
            emojiTv.setTextSize(20);
            emojiTv.setLayoutParams(new LinearLayout.LayoutParams((int)(32*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(emojiTv);

            TextView label = new TextView(this);
            label.setText(item[0]);
            label.setTextColor(Color.WHITE);
            label.setTextSize(14);
            row.addView(label);

            final String itemTitle = item[0];
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, HubFileBrowserActivity.class);
                intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, itemTitle);
                if ("Favourites".equals(itemTitle)) intent.putExtra(HubFileBrowserActivity.EXTRA_FAVOURITES_ONLY, true);
                if ("Recent Files".equals(itemTitle)) intent.putExtra(HubFileBrowserActivity.EXTRA_RECENT_ONLY, true);
                startActivity(intent);
            });
            qa.addView(row);
        }
    }
}
