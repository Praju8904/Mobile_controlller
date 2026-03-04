package com.prajwal.myfirstapp.tasks;

import com.prajwal.myfirstapp.R;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Eisenhower Matrix — 2×2 quadrant view of tasks.
 * Q1 Do First:  priority >= HIGH  AND starred
 * Q2 Schedule:  priority < HIGH   AND starred
 * Q3 Delegate:  priority >= HIGH  AND !starred
 * Q4 Eliminate: priority < HIGH   AND !starred
 */
public class TaskEisenhowerActivity extends AppCompatActivity {

    private TaskRepository repo;
    private RecyclerView recyclerQ1, recyclerQ2, recyclerQ3, recyclerQ4;
    private TextView tvQ1Count, tvQ2Count, tvQ3Count, tvQ4Count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_eisenhower);
        getWindow().setStatusBarColor(Color.parseColor("#0A0F1E"));

        repo = new TaskRepository(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        tvQ1Count = findViewById(R.id.tvQ1Count);
        tvQ2Count = findViewById(R.id.tvQ2Count);
        tvQ3Count = findViewById(R.id.tvQ3Count);
        tvQ4Count = findViewById(R.id.tvQ4Count);

        recyclerQ1 = findViewById(R.id.recyclerQ1);
        recyclerQ2 = findViewById(R.id.recyclerQ2);
        recyclerQ3 = findViewById(R.id.recyclerQ3);
        recyclerQ4 = findViewById(R.id.recyclerQ4);

        recyclerQ1.setLayoutManager(new LinearLayoutManager(this));
        recyclerQ2.setLayoutManager(new LinearLayoutManager(this));
        recyclerQ3.setLayoutManager(new LinearLayoutManager(this));
        recyclerQ4.setLayoutManager(new LinearLayoutManager(this));

        loadMatrix();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMatrix();
    }

    private void loadMatrix() {
        List<Task> all = repo.getAllTasks();
        List<Task> q1 = new ArrayList<>();
        List<Task> q2 = new ArrayList<>();
        List<Task> q3 = new ArrayList<>();
        List<Task> q4 = new ArrayList<>();

        for (Task t : all) {
            if (t.isTrashed || t.isCompleted() || t.isCancelled()) continue;

            boolean highPriority = "high".equals(t.priority) || "urgent".equals(t.priority);

            if (highPriority && t.isStarred) {
                q1.add(t);
            } else if (!highPriority && t.isStarred) {
                q2.add(t);
            } else if (highPriority) {
                q3.add(t);
            } else {
                q4.add(t);
            }
        }

        tvQ1Count.setText(String.valueOf(q1.size()));
        tvQ2Count.setText(String.valueOf(q2.size()));
        tvQ3Count.setText(String.valueOf(q3.size()));
        tvQ4Count.setText(String.valueOf(q4.size()));

        recyclerQ1.setAdapter(new MiniTaskAdapter(q1, "#EF4444"));
        recyclerQ2.setAdapter(new MiniTaskAdapter(q2, "#3B82F6"));
        recyclerQ3.setAdapter(new MiniTaskAdapter(q3, "#F59E0B"));
        recyclerQ4.setAdapter(new MiniTaskAdapter(q4, "#64748B"));
    }

    // ═══════════════════════════════════════════════════════════════
    // MINI TASK ADAPTER (compact cards for quadrants)
    // ═══════════════════════════════════════════════════════════════
    private class MiniTaskAdapter extends RecyclerView.Adapter<MiniTaskAdapter.MiniVH> {
        private final List<Task> tasks;
        private final String defaultColor;

        MiniTaskAdapter(List<Task> tasks, String defaultColor) {
            this.tasks = tasks;
            this.defaultColor = defaultColor;
        }

        @NonNull
        @Override
        public MiniVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_task_card_mini, parent, false);
            return new MiniVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MiniVH h, int position) {
            Task task = tasks.get(position);

            h.tvTitle.setText(task.title);

            // Priority dot
            String color = getPriorityColor(task.priority);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.parseColor(color));
            h.viewDot.setBackground(dot);

            // Tap → open detail
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(TaskEisenhowerActivity.this, TaskDetailActivity.class);
                intent.putExtra("task_id", task.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return tasks.size();
        }

        class MiniVH extends RecyclerView.ViewHolder {
            View viewDot;
            TextView tvTitle;

            MiniVH(@NonNull View itemView) {
                super(itemView);
                viewDot = itemView.findViewById(R.id.viewPriorityDot);
                tvTitle = itemView.findViewById(R.id.tvMiniTitle);
            }
        }
    }

    private String getPriorityColor(String priority) {
        if (priority == null) return "#3B82F6";
        switch (priority) {
            case "urgent":  return "#EF4444";
            case "high":    return "#F59E0B";
            case "normal":  return "#3B82F6";
            case "low":     return "#64748B";
            default:        return "#3B82F6";
        }
    }
}
