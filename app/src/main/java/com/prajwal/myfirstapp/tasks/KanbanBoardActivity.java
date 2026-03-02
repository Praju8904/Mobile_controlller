package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal Kanban board with 4 columns: To Do, In Progress, Completed, Cancelled.
 */
public class KanbanBoardActivity extends AppCompatActivity {

    private static final int COL_WIDTH_DP = 280;

    private static final String[] COLUMN_STATUSES = {
            Task.STATUS_TODO, Task.STATUS_INPROGRESS,
            Task.STATUS_COMPLETED, Task.STATUS_CANCELLED
    };
    private static final String[] COLUMN_LABELS = {
            "To Do", "In Progress", "Completed", "Cancelled"
    };
    private static final String[] COLUMN_COLORS = {
            "#007AFF", "#FF9500", "#34C759", "#8E8E93"
    };

    private TaskRepository repo;
    private final List<List<Task>> columnData = new ArrayList<>();
    private final List<ColumnAdapter> adapters = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new TaskRepository(this);
        for (int i = 0; i < 4; i++) columnData.add(new ArrayList<>());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A2E"));

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#252545"));
        toolbar.setPadding(dp(8), dp(12), dp(16), dp(12));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Kanban Board");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(dp(8), 0, 0, 0);
        toolbar.addView(tvTitle);

        root.addView(toolbar);

        // ── Board ─────────────────────────────────────────────────
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setBackgroundColor(Color.parseColor("#1A1A2E"));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        hScroll.setLayoutParams(hLp);

        LinearLayout boardRow = new LinearLayout(this);
        boardRow.setOrientation(LinearLayout.HORIZONTAL);
        boardRow.setPadding(dp(8), dp(8), dp(8), dp(8));

        for (int i = 0; i < 4; i++) {
            boardRow.addView(buildColumn(i));
        }

        hScroll.addView(boardRow);
        root.addView(hScroll);

        setContentView(root);
        loadTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
    }

    private void loadTasks() {
        for (List<Task> list : columnData) list.clear();
        for (Task t : repo.getAllTasks()) {
            if (t.isTrashed) continue;
            for (int i = 0; i < COLUMN_STATUSES.length; i++) {
                if (COLUMN_STATUSES[i].equals(t.status)) {
                    columnData.get(i).add(t);
                    break;
                }
            }
        }
        for (int i = 0; i < adapters.size(); i++) {
            adapters.get(i).notifyDataSetChanged();
        }
        // Update count badges
        for (int i = 0; i < countBadges.size(); i++) {
            countBadges.get(i).setText(String.valueOf(columnData.get(i).size()));
        }
    }

    private final List<TextView> countBadges = new ArrayList<>();

    private LinearLayout buildColumn(int colIndex) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int colWidthPx = dp(COL_WIDTH_DP);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(colWidthPx,
                LinearLayout.LayoutParams.MATCH_PARENT);
        colLp.setMargins(dp(4), 0, dp(4), 0);
        col.setLayoutParams(colLp);

        GradientDrawable colBg = new GradientDrawable();
        colBg.setColor(Color.parseColor("#252545"));
        colBg.setCornerRadius(dp(12));
        col.setBackground(colBg);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(12), dp(10));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.parseColor(COLUMN_COLORS[colIndex]));
        headerBg.setCornerRadii(new float[]{dp(12), dp(12), dp(12), dp(12), 0, 0, 0, 0});
        header.setBackground(headerBg);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(COLUMN_LABELS[colIndex]);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(15);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvLabel);

        TextView tvCount = new TextView(this);
        tvCount.setText("0");
        tvCount.setTextColor(Color.WHITE);
        tvCount.setTextSize(13);
        tvCount.setTypeface(null, Typeface.BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#33FFFFFF"));
        badgeBg.setCornerRadius(dp(10));
        tvCount.setBackground(badgeBg);
        tvCount.setPadding(dp(8), dp(2), dp(8), dp(2));
        header.addView(tvCount);
        countBadges.add(tvCount);

        col.addView(header);

        // RecyclerView for cards
        RecyclerView rv = new RecyclerView(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        rv.setPadding(dp(6), dp(6), dp(6), dp(6));
        rv.setClipToPadding(false);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);

        ColumnAdapter adapter = new ColumnAdapter(columnData.get(colIndex));
        adapters.add(adapter);
        rv.setAdapter(adapter);
        col.addView(rv);

        return col;
    }

    // ── Column Adapter ────────────────────────────────────────────

    private class ColumnAdapter extends RecyclerView.Adapter<ColumnAdapter.VH> {

        private final List<Task> tasks;

        ColumnAdapter(List<Task> tasks) { this.tasks = tasks; }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(KanbanBoardActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1A1A2E"));
            bg.setCornerRadius(dp(8));
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, dp(4));
            card.setLayoutParams(lp);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Task t = tasks.get(position);
            holder.tvTitle.setText(t.title);
            holder.tvPriority.setText(Task.getPriorityLabelFor(t.priority));
            holder.tvPriority.setTextColor(Task.getPriorityColorFor(t.priority));
            holder.tvDate.setText(t.hasDueDate() ? t.getFormattedDueDate() : "");
            holder.tvDate.setVisibility(t.hasDueDate() ? View.VISIBLE : View.GONE);

            // Tap → TaskDetailActivity
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(KanbanBoardActivity.this, TaskDetailActivity.class);
                intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, t.id);
                startActivity(intent);
            });

            // Long press hint
            holder.itemView.setOnLongClickListener(v -> {
                Toast.makeText(KanbanBoardActivity.this,
                        "Long press: drag between columns (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvPriority, tvDate;
            VH(LinearLayout card) {
                super(card);
                tvTitle = new TextView(KanbanBoardActivity.this);
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(14);
                tvTitle.setTypeface(null, Typeface.BOLD);
                card.addView(tvTitle);

                LinearLayout meta = new LinearLayout(KanbanBoardActivity.this);
                meta.setOrientation(LinearLayout.HORIZONTAL);
                meta.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                metaLp.topMargin = dp(4);
                meta.setLayoutParams(metaLp);

                tvPriority = new TextView(KanbanBoardActivity.this);
                tvPriority.setTextSize(10);
                tvPriority.setTypeface(null, Typeface.BOLD);
                tvPriority.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                meta.addView(tvPriority);

                tvDate = new TextView(KanbanBoardActivity.this);
                tvDate.setTextColor(Color.parseColor("#9E9E9E"));
                tvDate.setTextSize(10);
                meta.addView(tvDate);

                card.addView(meta);
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
