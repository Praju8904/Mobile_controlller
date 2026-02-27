package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trash screen — view, restore, or permanently delete trashed tasks.
 * Tasks auto-delete after 30 days. Shows a countdown for each item.
 */
public class TaskTrashActivity extends AppCompatActivity {

    private TaskRepository repo;
    private RecyclerView recyclerTrash;
    private LinearLayout emptyStateTrash;
    private TextView btnClearAll;
    private TrashAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_trash);

        repo = new TaskRepository(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        recyclerTrash = findViewById(R.id.recyclerTrash);
        emptyStateTrash = findViewById(R.id.emptyStateTrash);
        btnClearAll = findViewById(R.id.btnClearAll);

        recyclerTrash.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrashAdapter(this);
        recyclerTrash.setAdapter(adapter);

        btnClearAll.setOnClickListener(v -> confirmClearAll());

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        refreshList();
    }

    private void refreshList() {
        List<Task> trashed = repo.getTrashedTasks();
        adapter.setTasks(trashed);

        if (trashed.isEmpty()) {
            recyclerTrash.setVisibility(View.GONE);
            emptyStateTrash.setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.GONE);
        } else {
            recyclerTrash.setVisibility(View.VISIBLE);
            emptyStateTrash.setVisibility(View.GONE);
            btnClearAll.setVisibility(View.VISIBLE);
        }

        TextView subtitle = findViewById(R.id.tvTrashSubtitle);
        subtitle.setText(trashed.size() + " item" + (trashed.size() != 1 ? "s" : "") + " · Auto-delete after 30 days");
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Trash")
                .setMessage("Permanently delete all trashed tasks? This cannot be undone.")
                .setPositiveButton("Delete All", (d, w) -> {
                    repo.clearTrash();
                    Toast.makeText(this, "Trash cleared", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // ADAPTER
    // ═══════════════════════════════════════════════════════════════

    class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.VH> {
        private final Context ctx;
        private List<Task> tasks = new ArrayList<>();

        TrashAdapter(Context ctx) { this.ctx = ctx; }

        void setTasks(List<Task> tasks) {
            this.tasks = tasks;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_task_trash, parent, false);
            return new VH(v);
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Task task = tasks.get(pos);

            h.tvTrashTitle.setText(task.title);

            // Description
            if (task.description != null && !task.description.isEmpty()) {
                h.tvTrashDescription.setVisibility(View.VISIBLE);
                h.tvTrashDescription.setText(task.description);
            } else {
                h.tvTrashDescription.setVisibility(View.GONE);
            }

            // Priority
            String label = task.getPriorityLabel();
            h.tvTrashPriority.setText(label.isEmpty() ? "None" : label);

            // Days left
            long daysLeft = task.getTrashDaysRemaining();
            h.tvDaysLeft.setText(daysLeft + "d left");
            if (daysLeft <= 3) {
                h.tvDaysLeft.setTextColor(Color.parseColor("#EF4444"));
            } else {
                h.tvDaysLeft.setTextColor(Color.parseColor("#94A3B8"));
            }

            // Deleted date
            if (task.trashedAt > 0) {
                String dateStr = new SimpleDateFormat("MMM dd", Locale.US).format(new Date(task.trashedAt));
                h.tvTrashDeletedDate.setText("Deleted " + dateStr);
            } else {
                h.tvTrashDeletedDate.setText("");
            }

            // Restore
            h.btnRestore.setOnClickListener(v -> {
                repo.restoreFromTrash(task.id);
                Toast.makeText(ctx, "Task restored", Toast.LENGTH_SHORT).show();
                refreshList();
            });

            // Permanent delete
            h.btnDeletePermanent.setOnClickListener(v -> {
                new AlertDialog.Builder(ctx)
                        .setTitle("Delete Permanently")
                        .setMessage("Delete \"" + task.title + "\" forever? This cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> {
                            repo.deleteTaskPermanently(task.id);
                            Toast.makeText(ctx, "Permanently deleted", Toast.LENGTH_SHORT).show();
                            refreshList();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTrashTitle, tvTrashDescription, tvDaysLeft;
            TextView tvTrashPriority, tvTrashDeletedDate;
            TextView btnRestore, btnDeletePermanent;

            VH(View v) {
                super(v);
                tvTrashTitle = v.findViewById(R.id.tvTrashTitle);
                tvTrashDescription = v.findViewById(R.id.tvTrashDescription);
                tvDaysLeft = v.findViewById(R.id.tvDaysLeft);
                tvTrashPriority = v.findViewById(R.id.tvTrashPriority);
                tvTrashDeletedDate = v.findViewById(R.id.tvTrashDeletedDate);
                btnRestore = v.findViewById(R.id.btnRestore);
                btnDeletePermanent = v.findViewById(R.id.btnDeletePermanent);
            }
        }
    }
}
