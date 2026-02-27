package com.prajwal.myfirstapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RecyclerView adapter for the Task Manager home screen.
 * Supports two view types: GROUP_HEADER and TASK_CARD.
 * Handles grouped and flat task lists, animations, and all task interactions.
 */
public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_GROUP_HEADER = 0;
    private static final int TYPE_TASK_CARD    = 1;

    private final Context context;
    private final List<Object> items;      // mix of GroupHeader and Task
    private final TaskActionListener listener;

    // ─── Listener Interface ──────────────────────────────────────

    public interface TaskActionListener {
        void onTaskChecked(Task task, boolean isChecked);
        void onTaskClicked(Task task);
        void onTaskStarToggle(Task task);
        void onTaskMenuClicked(Task task, View anchor);
        void onGroupHeaderClicked(String groupName);
        /** Called when multi-select mode is entered/exited */
        default void onMultiSelectChanged(boolean active, int count) {}
    }

    // ─── Group Header model ──────────────────────────────────────

    public static class GroupHeader {
        public String name;
        public int count;
        public boolean isCollapsed;

        public GroupHeader(String name, int count) {
            this.name = name;
            this.count = count;
            this.isCollapsed = false;
        }
    }

    // ─── Constructor ─────────────────────────────────────────────

    public TaskAdapter(Context context, TaskActionListener listener) {
        this.context = context;
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    // ─── Multi-Select Mode ───────────────────────────────────────

    private boolean multiSelectMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public boolean isMultiSelectActive() { return multiSelectMode; }

    public void enterMultiSelect() {
        if (!multiSelectMode) {
            multiSelectMode = true;
            notifyDataSetChanged();
            if (listener != null) listener.onMultiSelectChanged(true, 0);
        }
    }

    public void exitMultiSelect() {
        if (multiSelectMode) {
            multiSelectMode = false;
            selectedIds.clear();
            notifyDataSetChanged();
            if (listener != null) listener.onMultiSelectChanged(false, 0);
        }
    }

    public void toggleSelection(String taskId) {
        if (selectedIds.contains(taskId)) {
            selectedIds.remove(taskId);
        } else {
            selectedIds.add(taskId);
        }
        if (selectedIds.isEmpty()) {
            exitMultiSelect();
        } else {
            notifyDataSetChanged();
            if (listener != null) listener.onMultiSelectChanged(true, selectedIds.size());
        }
    }

    public Set<String> getSelectedIds() { return new HashSet<>(selectedIds); }
    public int getSelectedCount() { return selectedIds.size(); }

    public void selectAll() {
        for (Object item : items) {
            if (item instanceof Task) selectedIds.add(((Task) item).id);
        }
        notifyDataSetChanged();
        if (listener != null) listener.onMultiSelectChanged(true, selectedIds.size());
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onMultiSelectChanged(true, 0);
    }

    // ─── Data Binding ────────────────────────────────────────────

    /**
     * Flat list of tasks (no groups).
     */
    public void setTasks(List<Task> tasks) {
        items.clear();
        items.addAll(tasks);
        notifyDataSetChanged();
    }

    /**
     * Grouped map of tasks (from TaskRepository.groupTasks).
     */
    public void setGroupedTasks(LinkedHashMap<String, List<Task>> groups) {
        items.clear();
        for (Map.Entry<String, List<Task>> entry : groups.entrySet()) {
            GroupHeader header = new GroupHeader(entry.getKey(), entry.getValue().size());
            items.add(header);
            items.addAll(entry.getValue());
        }
        notifyDataSetChanged();
    }

    public int getTaskCount() {
        int count = 0;
        for (Object item : items) {
            if (item instanceof Task) count++;
        }
        return count;
    }

    // ─── ViewHolder Creation ─────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof GroupHeader ? TYPE_GROUP_HEADER : TYPE_TASK_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_GROUP_HEADER) {
            View view = inflater.inflate(R.layout.item_task_group_header, parent, false);
            return new GroupHeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_task_card, parent, false);
            return new TaskCardViewHolder(view);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── Binding ─────────────────────────────────────────────────

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof GroupHeaderViewHolder) {
            bindGroupHeader((GroupHeaderViewHolder) holder, (GroupHeader) items.get(position));
        } else if (holder instanceof TaskCardViewHolder) {
            bindTaskCard((TaskCardViewHolder) holder, (Task) items.get(position));
        }
    }

    // ─── Group Header Binding ────────────────────────────────────

    private void bindGroupHeader(GroupHeaderViewHolder h, GroupHeader group) {
        h.tvGroupName.setText(group.name);
        h.tvGroupCount.setText(String.valueOf(group.count));
        h.tvGroupChevron.setText(group.isCollapsed ? "▸" : "▾");
        h.tvGroupIcon.setText(getGroupIcon(group.name));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGroupHeaderClicked(group.name);
        });
    }

    private String getGroupIcon(String groupName) {
        if (groupName == null) return "📌";
        switch (groupName) {
            case "Overdue":     return "⚠️";
            case "Today":       return "📅";
            case "Tomorrow":    return "🔜";
            case "This Week":   return "📆";
            case "Later":       return "🗓️";
            case "No Date":     return "📋";
            case "URGENT":      return "🔴";
            case "HIGH":        return "🟠";
            case "NORMAL":      return "🔵";
            case "LOW":         return "⚪";
            case "None":        return "⬜";
            case "In Progress": return "🔄";
            case "To Do":       return "📝";
            case "Completed":   return "✅";
            case "Cancelled":   return "❌";
            default:            return TaskCategory.getIconForCategory(groupName);
        }
    }

    // ─── Task Card Binding ───────────────────────────────────────

    private void bindTaskCard(TaskCardViewHolder h, Task task) {
        // ── Multi-select highlight ──
        boolean isSelected = multiSelectMode && selectedIds.contains(task.id);
        h.itemView.setBackground(null); // reset
        if (isSelected) {
            GradientDrawable selBg = new GradientDrawable();
            selBg.setShape(GradientDrawable.RECTANGLE);
            selBg.setCornerRadius(16f);
            selBg.setColor(Color.parseColor("#1A60A5FA")); // blue tint
            selBg.setStroke(2, Color.parseColor("#3B82F6"));
            h.itemView.setBackground(selBg);
        }

        // Title
        h.tvTaskTitle.setText(task.title);
        if (task.isCompleted()) {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTaskTitle.setTextColor(Color.parseColor("#6B7280"));
        } else {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTaskTitle.setTextColor(Color.parseColor("#F1F5F9"));
        }

        // Checkbox
        h.cbComplete.setOnCheckedChangeListener(null);
        h.cbComplete.setChecked(task.isCompleted());
        h.cbComplete.setOnCheckedChangeListener((btn, checked) -> {
            if (listener != null) listener.onTaskChecked(task, checked);
            // Completion animation: fade + strikethrough
            if (checked) {
                ValueAnimator anim = ValueAnimator.ofFloat(1f, 0.5f);
                anim.setDuration(300);
                anim.addUpdateListener(a -> h.itemView.setAlpha((float) a.getAnimatedValue()));
                anim.start();
                h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvTaskTitle.setTextColor(Color.parseColor("#6B7280"));
            }
        });

        // Priority strip color
        try {
            GradientDrawable strip = new GradientDrawable();
            strip.setShape(GradientDrawable.RECTANGLE);
            strip.setCornerRadius(4f);
            strip.setColor(task.getPriorityColor());
            h.viewPriorityStrip.setBackground(strip);
        } catch (Exception ignored) {}

        // Priority badge
        String label = task.getPriorityLabel();
        if (!label.isEmpty() && !Task.PRIORITY_NONE.equals(task.priority)) {
            h.tvPriorityBadge.setVisibility(View.VISIBLE);
            h.tvPriorityBadge.setText(label);
            try {
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.RECTANGLE);
                badgeBg.setCornerRadius(12f);
                badgeBg.setColor(task.getPriorityColor());
                h.tvPriorityBadge.setBackground(badgeBg);
            } catch (Exception ignored) {}
        } else {
            h.tvPriorityBadge.setVisibility(View.GONE);
        }

        // Description
        if (task.description != null && !task.description.isEmpty()) {
            h.tvTaskDescription.setVisibility(View.VISIBLE);
            h.tvTaskDescription.setText(task.description);
        } else {
            h.tvTaskDescription.setVisibility(View.GONE);
        }

        // Category chip
        if (task.category != null && !task.category.isEmpty()) {
            h.chipCategory.setVisibility(View.VISIBLE);
            h.tvCategoryIcon.setText(TaskCategory.getIconForCategory(task.category));
            h.tvCategoryName.setText(task.category);
        } else {
            h.chipCategory.setVisibility(View.GONE);
        }

        // Due date/time chip
        if (task.hasDueDate()) {
            h.chipDue.setVisibility(View.VISIBLE);
            h.tvDueText.setText(task.getFormattedDueDate());
            if (task.isOverdue()) {
                h.tvDueIcon.setText("⏰");
                h.tvDueText.setTextColor(Color.parseColor("#EF4444"));
            } else if (task.isDueToday()) {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#60A5FA"));
            } else {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#94A3B8"));
            }
        } else {
            h.chipDue.setVisibility(View.GONE);
        }

        // Star toggle
        h.btnStar.setText(task.isStarred ? "★" : "☆");
        h.btnStar.setTextColor(task.isStarred ? Color.parseColor("#FBBF24") : Color.parseColor("#4B5563"));
        h.btnStar.setOnClickListener(v -> {
            if (listener != null) listener.onTaskStarToggle(task);
        });

        // Subtask progress
        if (task.hasSubtasks()) {
            h.subtaskProgressContainer.setVisibility(View.VISIBLE);
            h.tvSubtaskCount.setText(task.getSubtaskCompletedCount() + "/" + task.getSubtaskTotalCount());

            // Animate progress bar width
            float progress = task.getSubtaskProgress();
            h.viewSubtaskProgressFill.post(() -> {
                ViewGroup parent = (ViewGroup) h.viewSubtaskProgressFill.getParent();
                int totalWidth = parent.getWidth();
                ViewGroup.LayoutParams lp = h.viewSubtaskProgressFill.getLayoutParams();
                lp.width = (int) (totalWidth * progress);
                h.viewSubtaskProgressFill.setLayoutParams(lp);
            });
        } else {
            h.subtaskProgressContainer.setVisibility(View.GONE);
        }

        // Menu button
        h.btnTaskMenu.setOnClickListener(v -> {
            if (!multiSelectMode && listener != null) listener.onTaskMenuClicked(task, v);
        });

        // Card click — in multi-select mode, toggle selection; otherwise open detail
        h.itemView.setOnClickListener(v -> {
            if (multiSelectMode) {
                toggleSelection(task.id);
            } else if (listener != null) {
                listener.onTaskClicked(task);
            }
        });

        // Long-press → enter multi-select mode
        h.itemView.setOnLongClickListener(v -> {
            if (!multiSelectMode) {
                enterMultiSelect();
                toggleSelection(task.id);
            }
            return true;
        });

        // Dim completed tasks
        if (!isSelected) {
            h.itemView.setAlpha(task.isCompleted() ? 0.5f : 1.0f);
        } else {
            h.itemView.setAlpha(1.0f);
        }
    }

    // ─── ViewHolders ─────────────────────────────────────────────

    static class GroupHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupIcon, tvGroupName, tvGroupCount, tvGroupChevron;

        GroupHeaderViewHolder(View v) {
            super(v);
            tvGroupIcon = v.findViewById(R.id.tvGroupIcon);
            tvGroupName = v.findViewById(R.id.tvGroupName);
            tvGroupCount = v.findViewById(R.id.tvGroupCount);
            tvGroupChevron = v.findViewById(R.id.tvGroupChevron);
        }
    }

    static class TaskCardViewHolder extends RecyclerView.ViewHolder {
        View viewPriorityStrip;
        CheckBox cbComplete;
        TextView tvTaskTitle, tvTaskDescription;
        TextView tvPriorityBadge;
        LinearLayout chipCategory, chipDue, subtaskProgressContainer;
        TextView tvCategoryIcon, tvCategoryName;
        TextView tvDueIcon, tvDueText;
        View viewSubtaskProgressFill;
        TextView tvSubtaskCount;
        TextView btnStar;
        ImageView btnTaskMenu;

        TaskCardViewHolder(View v) {
            super(v);
            viewPriorityStrip = v.findViewById(R.id.viewPriorityStrip);
            cbComplete = v.findViewById(R.id.cbComplete);
            tvTaskTitle = v.findViewById(R.id.tvTaskTitle);
            tvTaskDescription = v.findViewById(R.id.tvTaskDescription);
            tvPriorityBadge = v.findViewById(R.id.tvPriorityBadge);
            chipCategory = v.findViewById(R.id.chipCategory);
            tvCategoryIcon = v.findViewById(R.id.tvCategoryIcon);
            tvCategoryName = v.findViewById(R.id.tvCategoryName);
            chipDue = v.findViewById(R.id.chipDue);
            tvDueIcon = v.findViewById(R.id.tvDueIcon);
            tvDueText = v.findViewById(R.id.tvDueText);
            subtaskProgressContainer = v.findViewById(R.id.subtaskProgressContainer);
            viewSubtaskProgressFill = v.findViewById(R.id.viewSubtaskProgressFill);
            tvSubtaskCount = v.findViewById(R.id.tvSubtaskCount);
            btnStar = v.findViewById(R.id.btnStar);
            btnTaskMenu = v.findViewById(R.id.btnTaskMenu);
        }
    }
}
