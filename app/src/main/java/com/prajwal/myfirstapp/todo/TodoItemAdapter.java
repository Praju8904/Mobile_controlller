package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.tasks.SubtaskItem;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for tasks in a TodoList detail screen.
 * Supports multi-select mode, animated checkbox completion, priority left-border,
 * due date chip (red when overdue), subtask chip, and recurrence icon.
 */
public class TodoItemAdapter extends RecyclerView.Adapter<TodoItemAdapter.TaskViewHolder> {

    // ─── Listener Interface ──────────────────────────────────────

    public interface OnTaskActionListener {
        void onTaskClick(TodoItem item);
        void onTaskComplete(TodoItem item);
        void onTaskDelete(TodoItem item);
        void onTaskMenuClick(TodoItem item, View anchor);
    }

    // ─── Fields ──────────────────────────────────────────────────

    private final Context              context;
    private final List<TodoItem>       items;
    private final OnTaskActionListener listener;

    private boolean     isMultiSelectMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    // ─── Constructor ─────────────────────────────────────────────

    public TodoItemAdapter(Context ctx, List<TodoItem> items,
                           OnTaskActionListener listener) {
        this.context  = ctx;
        this.items    = items;
        this.listener = listener;
    }

    // ─── ViewHolder ──────────────────────────────────────────────

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout  cardRoot;
        final View          ivPriorityBorder;  // left colored border strip
        final FrameLayout   checkboxContainer; // animated circular checkbox
        final TextView      tvCheckMark;       // checkmark symbol inside the circle
        final TextView      tvTitle;
        final TextView      tvPriority;        // priority chip label
        final TextView      tvDueDate;         // due date chip
        final TextView      tvSubtasks;        // "2/4 subtasks" chip
        final TextView      tvRecurring;       // recurrence icon

        TaskViewHolder(LinearLayout card, View border, FrameLayout checkbox,
                       TextView checkMark, TextView title, TextView priority,
                       TextView dueDate, TextView subtasks, TextView recurring) {
            super(card);
            this.cardRoot          = card;
            this.ivPriorityBorder  = border;
            this.checkboxContainer = checkbox;
            this.tvCheckMark       = checkMark;
            this.tvTitle           = title;
            this.tvPriority        = priority;
            this.tvDueDate         = dueDate;
            this.tvSubtasks        = subtasks;
            this.tvRecurring       = recurring;
        }
    }

    // ─── Adapter Overrides ───────────────────────────────────────

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 0; // single view type
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return createTaskViewHolder();
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TodoItem item = items.get(position);
        bindTaskCard(holder, item);
    }

    // ─── Multi-Select ────────────────────────────────────────────

    public void setMultiSelectMode(boolean enabled) {
        isMultiSelectMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(String itemId) {
        if (selectedIds.contains(itemId)) {
            selectedIds.remove(itemId);
        } else {
            selectedIds.add(itemId);
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public void clearSelections() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    // ─── ViewHolder Factory ──────────────────────────────────────

    private TaskViewHolder createTaskViewHolder() {
        float density = context.getResources().getDisplayMetrics().density;
        int dp2  = (int) (2  * density);
        int dp4  = (int) (4  * density);
        int dp6  = (int) (6  * density);
        int dp8  = (int) (8  * density);
        int dp12 = (int) (12 * density);
        int dp16 = (int) (16 * density);
        int dp24 = (int) (24 * density);
        int dp28 = (int) (28 * density);
        int dp32 = (int) (32 * density);

        // ── Root card: horizontal LinearLayout ──
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        RecyclerView.LayoutParams cardParams =
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp8;
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(12 * density);
        cardBg.setColor(0xFF1E293B);
        card.setBackground(cardBg);

        // ── Left priority border ──
        View border = new View(context);
        LinearLayout.LayoutParams borderParams =
                new LinearLayout.LayoutParams(dp4, ViewGroup.LayoutParams.MATCH_PARENT);
        borderParams.setMarginEnd(dp8);
        border.setLayoutParams(borderParams);
        GradientDrawable borderBg = new GradientDrawable();
        borderBg.setShape(GradientDrawable.RECTANGLE);
        borderBg.setCornerRadius(4 * density);
        border.setBackground(borderBg);

        // ── Animated checkbox circle ──
        FrameLayout checkbox = new FrameLayout(context);
        LinearLayout.LayoutParams cbParams =
                new LinearLayout.LayoutParams(dp28, dp28);
        cbParams.setMarginEnd(dp12);
        checkbox.setLayoutParams(cbParams);
        GradientDrawable cbBg = new GradientDrawable();
        cbBg.setShape(GradientDrawable.OVAL);
        cbBg.setColor(0x00000000);
        cbBg.setStroke(dp2, 0xFF64748B);
        checkbox.setBackground(cbBg);

        TextView checkMark = new TextView(context);
        checkMark.setText("✓");
        checkMark.setTextSize(14);
        checkMark.setTextColor(0xFFFFFFFF);
        checkMark.setGravity(Gravity.CENTER);
        checkMark.setAlpha(0f);
        FrameLayout.LayoutParams cmParams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        checkMark.setLayoutParams(cmParams);
        checkbox.addView(checkMark);

        // ── Content column ──
        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setPadding(0, dp8, dp8, dp8);
        LinearLayout.LayoutParams colParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        contentCol.setLayoutParams(colParams);

        // Task title
        TextView tvTitle = new TextView(context);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(0xFFE2E8F0);
        tvTitle.setTypeface(null, Typeface.NORMAL);
        tvTitle.setMaxLines(2);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        tvTitle.setLayoutParams(titleParams);
        contentCol.addView(tvTitle);

        // ── Chips row ──
        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipsParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp4;
        chipsRow.setLayoutParams(chipsParams);

        // Priority chip
        TextView tvPriority = new TextView(context);
        tvPriority.setTextSize(10);
        tvPriority.setTextColor(0xFFFFFFFF);
        tvPriority.setVisibility(View.GONE);
        tvPriority.setPadding(dp6, dp2, dp6, dp2);
        LinearLayout.LayoutParams prioParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        prioParams.setMarginEnd(dp4);
        tvPriority.setLayoutParams(prioParams);
        chipsRow.addView(tvPriority);

        // Due date chip
        TextView tvDueDate = new TextView(context);
        tvDueDate.setTextSize(10);
        tvDueDate.setTextColor(0xFF94A3B8);
        tvDueDate.setVisibility(View.GONE);
        tvDueDate.setPadding(dp6, dp2, dp6, dp2);
        LinearLayout.LayoutParams dueDateParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        dueDateParams.setMarginEnd(dp4);
        tvDueDate.setLayoutParams(dueDateParams);
        GradientDrawable dueDateBg = new GradientDrawable();
        dueDateBg.setShape(GradientDrawable.RECTANGLE);
        dueDateBg.setCornerRadius(6 * density);
        dueDateBg.setColor(0x1A94A3B8);
        tvDueDate.setBackground(dueDateBg);
        chipsRow.addView(tvDueDate);

        // Subtasks chip
        TextView tvSubtasks = new TextView(context);
        tvSubtasks.setTextSize(10);
        tvSubtasks.setTextColor(0xFF60A5FA);
        tvSubtasks.setVisibility(View.GONE);
        tvSubtasks.setPadding(dp6, dp2, dp6, dp2);
        LinearLayout.LayoutParams subtasksParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        subtasksParams.setMarginEnd(dp4);
        tvSubtasks.setLayoutParams(subtasksParams);
        GradientDrawable subtasksBg = new GradientDrawable();
        subtasksBg.setShape(GradientDrawable.RECTANGLE);
        subtasksBg.setCornerRadius(6 * density);
        subtasksBg.setColor(0x1A60A5FA);
        tvSubtasks.setBackground(subtasksBg);
        chipsRow.addView(tvSubtasks);

        // Recurring icon
        TextView tvRecurring = new TextView(context);
        tvRecurring.setText("🔁");
        tvRecurring.setTextSize(11);
        tvRecurring.setVisibility(View.GONE);
        LinearLayout.LayoutParams recurParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        tvRecurring.setLayoutParams(recurParams);
        chipsRow.addView(tvRecurring);

        contentCol.addView(chipsRow);

        // ── Assemble root ──
        card.addView(border);
        card.addView(checkbox);
        card.addView(contentCol);

        return new TaskViewHolder(card, border, checkbox, checkMark,
                tvTitle, tvPriority, tvDueDate, tvSubtasks, tvRecurring);
    }

    // ─── Bind ────────────────────────────────────────────────────

    private void bindTaskCard(TaskViewHolder h, TodoItem item) {
        float density = context.getResources().getDisplayMetrics().density;

        boolean completed = item.isCompleted;
        boolean isSelected = selectedIds.contains(item.id);

        // ── Card background ──
        if (h.cardRoot.getBackground() instanceof GradientDrawable) {
            GradientDrawable cardBg = (GradientDrawable) h.cardRoot.getBackground().mutate();
            cardBg.setColor(isSelected ? 0xFF2D3F55 : 0xFF1E293B);
        }

        // ── Alpha for completed tasks ──
        h.cardRoot.setAlpha(completed ? 0.6f : 1.0f);

        // ── Priority border ──
        int priorityColor = item.getPriorityColor();
        if (h.ivPriorityBorder.getBackground() instanceof GradientDrawable) {
            GradientDrawable borderBg = (GradientDrawable) h.ivPriorityBorder.getBackground().mutate();
            borderBg.setColor(priorityColor);
        }

        // ── Checkbox state ──
        if (h.checkboxContainer.getBackground() instanceof GradientDrawable) {
            GradientDrawable cbBg = (GradientDrawable) h.checkboxContainer.getBackground().mutate();
            if (completed) {
                cbBg.setColor(priorityColor);
                cbBg.setStroke(0, Color.TRANSPARENT);
                h.tvCheckMark.setAlpha(1f);
            } else {
                cbBg.setColor(0x00000000);
                cbBg.setStroke((int) (2 * density), 0xFF64748B);
                h.tvCheckMark.setAlpha(0f);
            }
        }

        // ── Title (with strikethrough if completed) ──
        h.tvTitle.setText(item.title != null ? item.title : "");
        if (completed) {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(0xFF64748B);
        } else {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(0xFFE2E8F0);
        }

        // ── Priority chip (only show if not NONE) ──
        if (!TodoItem.PRIORITY_NONE.equals(item.priority)) {
            h.tvPriority.setText(capitalize(item.priority));
            h.tvPriority.setTextColor(0xFFFFFFFF);
            GradientDrawable prioBg = new GradientDrawable();
            prioBg.setShape(GradientDrawable.RECTANGLE);
            prioBg.setCornerRadius(6 * density);
            prioBg.setColor(withAlpha(priorityColor, 0.25f));
            h.tvPriority.setBackground(prioBg);
            h.tvPriority.setVisibility(View.VISIBLE);
        } else {
            h.tvPriority.setVisibility(View.GONE);
        }

        // ── Due date chip ──
        String relDue = item.getRelativeDueDate();
        if (relDue != null && !relDue.isEmpty()) {
            h.tvDueDate.setText(relDue);
            boolean overdue = item.isOverdue();
            h.tvDueDate.setTextColor(overdue ? 0xFFEF4444 : 0xFF94A3B8);
            if (h.tvDueDate.getBackground() instanceof GradientDrawable) {
                GradientDrawable dueBg = (GradientDrawable) h.tvDueDate.getBackground().mutate();
                dueBg.setColor(overdue ? 0x1AEF4444 : 0x1A94A3B8);
            }
            h.tvDueDate.setVisibility(View.VISIBLE);
        } else {
            h.tvDueDate.setVisibility(View.GONE);
        }

        // ── Subtasks chip ──
        if (item.subtasks != null && !item.subtasks.isEmpty()) {
            int totalSubs    = item.subtasks.size();
            int completedSubs = 0;
            for (SubtaskItem sub : item.subtasks) {
                if (sub.isCompleted) completedSubs++;
            }
            h.tvSubtasks.setText(completedSubs + "/" + totalSubs + " subtasks");
            h.tvSubtasks.setVisibility(View.VISIBLE);
        } else {
            h.tvSubtasks.setVisibility(View.GONE);
        }

        // ── Recurrence icon ──
        boolean isRecurring = item.recurrence != null
                && !TodoItem.RECURRENCE_NONE.equals(item.recurrence);
        h.tvRecurring.setVisibility(isRecurring ? View.VISIBLE : View.GONE);

        // ── Click: open detail ──
        h.cardRoot.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                toggleSelection(item.id);
            } else if (listener != null) {
                listener.onTaskClick(item);
            }
        });

        // ── Long press: enter multi-select or show menu ──
        h.cardRoot.setOnLongClickListener(v -> {
            if (listener != null) listener.onTaskMenuClick(item, v);
            return true;
        });

        // ── Checkbox click: animate then complete ──
        h.checkboxContainer.setOnClickListener(v -> animateCheckboxAndComplete(h, item));
    }

    // ─── Checkbox Animation ──────────────────────────────────────

    /** Plays a bounce animation on the checkbox circle, then notifies the listener. */
    private void animateCheckboxAndComplete(TaskViewHolder h, TodoItem item) {
        if (item.isCompleted) {
            // Already completed — just notify (for undo / re-open support)
            if (listener != null) listener.onTaskComplete(item);
            return;
        }

        // Scale down then back up (bounce effect)
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(h.checkboxContainer, View.SCALE_X, 1f, 0.6f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(h.checkboxContainer, View.SCALE_Y, 1f, 0.6f);
        ObjectAnimator scaleUpX   = ObjectAnimator.ofFloat(h.checkboxContainer, View.SCALE_X, 0.6f, 1f);
        ObjectAnimator scaleUpY   = ObjectAnimator.ofFloat(h.checkboxContainer, View.SCALE_Y, 0.6f, 1f);

        AnimatorSet downSet = new AnimatorSet();
        downSet.playTogether(scaleDownX, scaleDownY);
        downSet.setDuration(100);

        AnimatorSet upSet = new AnimatorSet();
        upSet.playTogether(scaleUpX, scaleUpY);
        upSet.setDuration(150);

        AnimatorSet bounce = new AnimatorSet();
        bounce.playSequentially(downSet, upSet);
        bounce.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                // Fill the circle and show checkmark mid-animation
                if (h.checkboxContainer.getBackground() instanceof GradientDrawable) {
                    GradientDrawable cbBg = (GradientDrawable)
                            h.checkboxContainer.getBackground().mutate();
                    cbBg.setColor(item.getPriorityColor());
                    cbBg.setStroke(0, Color.TRANSPARENT);
                }
                h.tvCheckMark.animate().alpha(1f).setDuration(100).start();
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (listener != null) listener.onTaskComplete(item);
            }
        });
        bounce.start();
    }

    // ─── Color Helpers ───────────────────────────────────────────

    private int withAlpha(int color, float alpha) {
        int a = Math.round(alpha * 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
