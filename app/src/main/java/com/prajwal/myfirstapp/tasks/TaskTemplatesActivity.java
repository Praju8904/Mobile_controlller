package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Shows a list of task templates (built-in + custom).
 * Tapping a template creates a task from it and opens TaskEditorSheet.
 */
public class TaskTemplatesActivity extends AppCompatActivity
        implements TaskEditorSheet.TaskEditorListener {

    private TaskTemplatesManager manager;
    private TaskRepository repo;
    private RecyclerView recyclerView;
    private List<TaskTemplatesManager.TaskTemplate> templates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        manager = new TaskTemplatesManager(this);
        repo = new TaskRepository(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A2E"));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#252545"));
        toolbar.setPadding(dp(8), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams toolbarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toolbar.setLayoutParams(toolbarLp);

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Templates");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(dp(8), 0, 0, 0);
        toolbar.addView(tvTitle);

        root.addView(toolbar);

        // ── Subtitle ──────────────────────────────────────────────
        TextView tvSub = new TextView(this);
        tvSub.setText("Tap a template to create a task");
        tvSub.setTextColor(Color.parseColor("#9E9E9E"));
        tvSub.setTextSize(13);
        tvSub.setPadding(dp(16), dp(8), dp(16), dp(4));
        root.addView(tvSub);

        // ── RecyclerView ──────────────────────────────────────────
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setPadding(dp(8), dp(4), dp(8), dp(16));
        recyclerView.setClipToPadding(false);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        recyclerView.setLayoutParams(rvLp);
        root.addView(recyclerView);

        setContentView(root);
        loadTemplates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTemplates();
    }

    private void loadTemplates() {
        templates = manager.getAllTemplates();
        recyclerView.setAdapter(new TemplateAdapter());
    }

    // ── Adapter ───────────────────────────────────────────────────

    private class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.VH> {

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(TaskTemplatesActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#252545"));
            bg.setCornerRadius(dp(12));
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), dp(6), dp(4), dp(6));
            card.setLayoutParams(lp);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            TaskTemplatesManager.TaskTemplate t = templates.get(position);
            holder.tvName.setText(t.name);
            holder.tvDescription.setText(t.description.isEmpty() ? t.category : t.description);
            int count = t.subtaskTitles.size();
            holder.tvCount.setText(count + " subtask" + (count != 1 ? "s" : ""));
            holder.tvBadge.setText(t.isCustom ? "Custom" : "Built-in");
            holder.tvBadge.setTextColor(t.isCustom
                    ? Color.parseColor("#F59E0B") : Color.parseColor("#6C63FF"));

            // Priority colour strip
            int priorityColor = Task.getPriorityColorFor(t.priority);
            GradientDrawable strip = new GradientDrawable();
            strip.setColor(priorityColor);
            strip.setCornerRadius(dp(3));
            holder.priorityStrip.setBackground(strip);

            holder.itemView.setOnClickListener(v -> openTemplate(t));
        }

        @Override
        public int getItemCount() { return templates.size(); }

        class VH extends RecyclerView.ViewHolder {
            View priorityStrip;
            TextView tvName, tvDescription, tvCount, tvBadge;

            VH(LinearLayout card) {
                super(card);

                LinearLayout row = new LinearLayout(TaskTemplatesActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);

                priorityStrip = new View(TaskTemplatesActivity.this);
                LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(dp(4), dp(44));
                stripLp.setMarginEnd(dp(12));
                priorityStrip.setLayoutParams(stripLp);
                row.addView(priorityStrip);

                LinearLayout textCol = new LinearLayout(TaskTemplatesActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                tvName = new TextView(TaskTemplatesActivity.this);
                tvName.setTextColor(Color.WHITE);
                tvName.setTextSize(16);
                tvName.setTypeface(null, Typeface.BOLD);
                textCol.addView(tvName);

                tvDescription = new TextView(TaskTemplatesActivity.this);
                tvDescription.setTextColor(Color.parseColor("#9E9E9E"));
                tvDescription.setTextSize(13);
                tvDescription.setPadding(0, dp(2), 0, 0);
                textCol.addView(tvDescription);

                row.addView(textCol);

                LinearLayout rightCol = new LinearLayout(TaskTemplatesActivity.this);
                rightCol.setOrientation(LinearLayout.VERTICAL);
                rightCol.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

                tvBadge = new TextView(TaskTemplatesActivity.this);
                tvBadge.setTextSize(11);
                tvBadge.setTypeface(null, Typeface.BOLD);
                rightCol.addView(tvBadge);

                tvCount = new TextView(TaskTemplatesActivity.this);
                tvCount.setTextColor(Color.parseColor("#9E9E9E"));
                tvCount.setTextSize(11);
                tvCount.setPadding(0, dp(2), 0, 0);
                rightCol.addView(tvCount);

                row.addView(rightCol);
                card.addView(row);
            }
        }
    }

    private void openTemplate(TaskTemplatesManager.TaskTemplate template) {
        // Create the task, save it, then open the editor so the user can customise it
        Task task = TaskTemplatesManager.applyTemplate(template);
        repo.addTask(task);
        TaskEditorSheet sheet = TaskEditorSheet.newInstance(task.id);
        sheet.setListener(this);
        sheet.show(getSupportFragmentManager(), "editor");
        Toast.makeText(this, "Task created from template — edit to customise", Toast.LENGTH_SHORT).show();
    }

    // ── TaskEditorSheet.TaskEditorListener ───────────────────────

    @Override
    public void onTaskSaved(Task task, boolean isNew) {
        // already saved
    }

    @Override
    public void onTaskEditorDismissed() {}

    // ── dp helper ─────────────────────────────────────────────────

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
