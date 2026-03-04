package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;

/**
 * RecyclerView adapter for the Task Manager home screen.
 * Supports two view types: GROUP_HEADER and TASK_CARD.
 * Handles grouped and flat task lists, animations, and all task interactions.
 */
public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_GROUP_HEADER = 0;
    private static final int TYPE_TASK_CARD    = 1;

    private final Context context;
    private final List<Object> items;      // mix of GroupHeader and Task
    private final TaskActionListener listener;
    private int lastAnimatedPosition = -1; // Track animated positions

    // ─── Card state tracking ─────────────────────────────────────
    private final Set<String> expandedIds  = new HashSet<>();  // "focused" expanded cards
    private final Set<String> collapsedIds = new HashSet<>();  // "compact" collapsed cards
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w./?=&%#+-]+");
    private boolean gridMode = false;
    private boolean allSubtasksCollapsed = false; // Feature 10: global subtask collapse toggle
    private final Set<String> expandedSubtaskTasks = new HashSet<>(); // Feature 10: per-task subtask expand
    private boolean perTaskSubtaskMode = false; // true = use per-task expand set

    // ─── Listener Interface ──────────────────────────────────────

    // ─── Swipe Action Constants ─────────────────────────────────
    public static final int SWIPE_COMPLETE = 1;
    public static final int SWIPE_ARCHIVE  = 2;
    private static final int TYPE_TASK_GRID = 2;

    public void setGridMode(boolean grid) {
        this.gridMode = grid;
        notifyDataSetChanged();
    }
    public boolean isGridMode() { return gridMode; }

    /** Feature 10: Toggle collapse/expand all subtask previews globally. */
    public void toggleAllSubtasksCollapsed() {
        allSubtasksCollapsed = !allSubtasksCollapsed;
        perTaskSubtaskMode = false;
        notifyDataSetChanged();
    }
    public boolean isAllSubtasksCollapsed() { return allSubtasksCollapsed; }

    /** Feature 10: Collapse all subtask previews (per-task mode). */
    public void collapseAllSubtasks() {
        expandedSubtaskTasks.clear();
        perTaskSubtaskMode = true;
        allSubtasksCollapsed = false;
        notifyDataSetChanged();
    }

    /** Feature 10: Expand all subtask previews (per-task mode). */
    public void expandAllSubtasks(java.util.List<Task> tasks) {
        expandedSubtaskTasks.clear();
        if (tasks != null) {
            for (Task t : tasks) {
                if (t != null && t.id != null) expandedSubtaskTasks.add(t.id);
            }
        }
        perTaskSubtaskMode = true;
        allSubtasksCollapsed = false;
        notifyDataSetChanged();
    }

    /** Feature 10: Toggle subtask visibility for a single task. */
    public void toggleSubtaskExpand(String taskId) {
        perTaskSubtaskMode = true;
        allSubtasksCollapsed = false;
        if (expandedSubtaskTasks.contains(taskId)) {
            expandedSubtaskTasks.remove(taskId);
        } else {
            expandedSubtaskTasks.add(taskId);
        }
    }

    public boolean isSubtaskExpanded(String taskId) {
        if (allSubtasksCollapsed) return false;
        if (!perTaskSubtaskMode) return true;
        return expandedSubtaskTasks.contains(taskId);
    }

    public interface TaskActionListener {
        void onTaskChecked(Task task, boolean isChecked);
        void onTaskClicked(Task task);
        void onTaskStarToggle(Task task);
        void onTaskMenuClicked(Task task, View anchor);
        void onGroupHeaderClicked(String groupName);
        /** Called when multi-select mode is entered/exited */
        default void onMultiSelectChanged(boolean active, int count) {}
        /** Called when a task card is swiped (right=complete, left=archive) */
        default void onTaskSwiped(Task task, int swipeDirection) {}
        /** Called when a task title has been edited inline. */
        default void onTaskUpdated(Task task) {}
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
    public boolean isInMultiSelectMode() { return multiSelectMode; }

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

    // ─── Compact / Focused Card State ────────────────────────────

    public void toggleCompact(String taskId) {
        expandedIds.remove(taskId);
        if (collapsedIds.contains(taskId)) {
            collapsedIds.remove(taskId);
        } else {
            collapsedIds.add(taskId);
        }
        notifyDataSetChanged();
    }

    public void toggleExpanded(String taskId) {
        collapsedIds.remove(taskId);
        if (expandedIds.contains(taskId)) {
            expandedIds.remove(taskId);
        } else {
            expandedIds.add(taskId);
        }
        notifyDataSetChanged();
    }

    public boolean isExpanded(String taskId) {
        return expandedIds.contains(taskId);
    }

    // ─── Data Binding ────────────────────────────────────────────

    /**
     * Flat list of tasks (no groups).
     */
    public void setTasks(List<Task> tasks) {
        items.clear();
        items.addAll(tasks);
        lastAnimatedPosition = -1;
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
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    public int getTaskCount() {
        int count = 0;
        for (Object item : items) {
            if (item instanceof Task) count++;
        }
        return count;
    }

    /** Returns the Task at adapter position, or null if it's a group header. */
    public Task getTaskAtPosition(int position) {
        if (position < 0 || position >= items.size()) return null;
        Object item = items.get(position);
        return item instanceof Task ? (Task) item : null;
    }

    // ─── ViewHolder Creation ─────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof GroupHeader) return TYPE_GROUP_HEADER;
        return gridMode ? TYPE_TASK_GRID : TYPE_TASK_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_GROUP_HEADER) {
            View view = inflater.inflate(R.layout.item_task_group_header, parent, false);
            return new GroupHeaderViewHolder(view);
        } else if (viewType == TYPE_TASK_GRID) {
            View view = inflater.inflate(R.layout.item_task_card_grid, parent, false);
            return new GridCardViewHolder(view);
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
        } else if (holder instanceof GridCardViewHolder) {
            bindGridCard((GridCardViewHolder) holder, (Task) items.get(position));
        } else if (holder instanceof TaskCardViewHolder) {
            bindTaskCard((TaskCardViewHolder) holder, (Task) items.get(position));
        }
    }

    /**
     * Partial rebind: handles the "countdown" payload from
     * {@link CountdownRefreshScheduler} to only update the countdown badge
     * without doing a full card rebind (avoids flicker).
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof TaskCardViewHolder) {
            for (Object payload : payloads) {
                if (CountdownRefreshScheduler.PAYLOAD_COUNTDOWN.equals(payload)) {
                    Task task = getTaskAtPosition(position);
                    if (task != null) {
                        bindDueChip((TaskCardViewHolder) holder, task);
                    }
                    return; // handled — skip full rebind
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads);
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
        // ── Feature 15D: Private task masking ──
        TaskVaultManager vault = TaskVaultManager.getInstance(h.itemView.getContext());
        if (task.isPrivate && !vault.isUnlocked()) {
            // Show locked placeholder
            h.tvTaskTitle.setText("[Private Task]");
            h.tvTaskTitle.setTextColor(Color.parseColor("#6B7280"));
            h.tvTaskTitle.setTypeface(null, android.graphics.Typeface.ITALIC);
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            if (h.tvTaskDescription != null) h.tvTaskDescription.setVisibility(View.GONE);
            if (h.subtaskPreviewContainer != null) h.subtaskPreviewContainer.setVisibility(View.GONE);
            if (h.timeEnergyRow != null) h.timeEnergyRow.setVisibility(View.GONE);
            if (h.batch4BadgesRow != null) h.batch4BadgesRow.setVisibility(View.GONE);
            h.cbComplete.setVisibility(View.GONE);
            // Tap on locked card → prompt biometric unlock
            h.itemView.setOnClickListener(v -> {
                android.content.Context ctx = h.itemView.getContext();
                if (ctx instanceof androidx.fragment.app.FragmentActivity) {
                    vault.promptUnlock((androidx.fragment.app.FragmentActivity) ctx, () -> {
                        notifyDataSetChanged();
                    });
                }
            });
            h.itemView.setOnLongClickListener(null);
            return; // early return — don't bind rest of card
        }
        // Normal bind — reset any private-mode overrides
        h.tvTaskTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        h.cbComplete.setVisibility(View.VISIBLE);

        // ── Determine card states ──
        boolean isSelected  = multiSelectMode && selectedIds.contains(task.id);
        boolean isExpanded  = expandedIds.contains(task.id);
        boolean isCompact   = collapsedIds.contains(task.id);

        // ── Card state background ──
        if (isSelected) {
            h.itemView.setBackgroundResource(R.drawable.task_card_selected_bg);
        } else if (task.isCompleted()) {
            h.itemView.setBackgroundResource(R.drawable.task_card_completed_bg);
        } else if (task.isOverdue()) {
            h.itemView.setBackgroundResource(R.drawable.task_card_overdue_bg);
        } else if (Task.STATUS_INPROGRESS.equals(task.status)) {
            h.itemView.setBackgroundResource(R.drawable.task_card_inprogress_bg);
        } else {
            h.itemView.setBackground(null);
        }

        // ── Title ──
        h.tvTaskTitle.setText(task.title);
        if (task.isCompleted()) {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTaskTitle.setTextColor(Color.parseColor("#6B7280"));
        } else {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTaskTitle.setTextColor(Color.parseColor("#F1F5F9"));
        }

        // ── Checkbox with spring animation ──
        h.cbComplete.setOnCheckedChangeListener(null);
        h.cbComplete.setChecked(task.isCompleted());
        h.cbComplete.setOnCheckedChangeListener((btn, checked) -> {
            if (listener != null) listener.onTaskChecked(task, checked);
            if (checked) {
                playCompletionAnimation(h);
            } else {
                playUndoCompletionAnimation(h);
            }
        });

        // ── Priority strip — with color morph transition ──
        if (h.lastBoundPriority != null && !h.lastBoundPriority.equals(task.priority)) {
            // Priority changed since last bind → animate morph
            int fromColor = getPriorityStripColor(h.lastBoundPriority);
            int toColor   = getPriorityStripColor(task.priority);
            ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
            colorAnim.setDuration(400);
            colorAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            colorAnim.addUpdateListener(animator -> {
                int animated = (int) animator.getAnimatedValue();
                GradientDrawable strip = new GradientDrawable();
                strip.setShape(GradientDrawable.RECTANGLE);
                strip.setCornerRadius(4f);
                strip.setColor(animated);
                h.viewPriorityStrip.setBackground(strip);
            });
            colorAnim.start();
        } else {
            try {
                GradientDrawable strip = new GradientDrawable();
                strip.setShape(GradientDrawable.RECTANGLE);
                strip.setCornerRadius(4f);
                int[] gradientColors = getPriorityGradientColors(task.priority);
                strip.setColors(gradientColors);
                strip.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                h.viewPriorityStrip.setBackground(strip);
            } catch (Exception ignored) {}
        }
        h.lastBoundPriority = task.priority;

        // ── Priority badge ──
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

        // ── Color Label stripe (Feature 2B) ──
        if (h.viewPriorityStrip != null && task.colorLabel != null && !task.colorLabel.isEmpty()) {
            try {
                GradientDrawable colorStrip = new GradientDrawable();
                colorStrip.setShape(GradientDrawable.RECTANGLE);
                colorStrip.setCornerRadius(4f);
                colorStrip.setColor(Color.parseColor(task.colorLabel));
                h.viewPriorityStrip.setBackground(colorStrip);
            } catch (Exception ignored) {}
        }

        // ── Deferred state (Feature 2B) ──
        if (task.isDeferred()) {
            h.itemView.setAlpha(0.5f);
        } else {
            h.itemView.setAlpha(1.0f);
        }

        // ════════════════════════════════════════════
        // COMPACT MODE: hide everything except title + priority + due
        // ════════════════════════════════════════════
        if (isCompact) {
            hideCompactFields(h);
            bindDueChip(h, task);
            bindCardInteractions(h, task, isSelected, isCompact, isExpanded);
            return;
        }

        // ── Description with URL auto-detection ──
        if (task.description != null && !task.description.isEmpty()) {
            h.tvTaskDescription.setVisibility(View.VISIBLE);
            h.tvTaskDescription.setText(task.description);
            // URL auto-detection
            Linkify.addLinks(h.tvTaskDescription, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            h.tvTaskDescription.setLinkTextColor(Color.parseColor("#60A5FA"));
            h.tvTaskDescription.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            h.tvTaskDescription.setVisibility(View.GONE);
        }

        // ── Link preview (expanded state only) ──
        if (h.linkPreviewContainer != null) {
            if (isExpanded && task.description != null) {
                Matcher urlMatcher = URL_PATTERN.matcher(task.description);
                if (urlMatcher.find()) {
                    String firstUrl = urlMatcher.group(0);
                    h.linkPreviewContainer.setVisibility(View.VISIBLE);
                    h.tvLinkUrl.setText(firstUrl);
                    h.linkPreviewContainer.setOnClickListener(v -> {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(firstUrl));
                            context.startActivity(browserIntent);
                        } catch (Exception ignored) {}
                    });
                } else {
                    h.linkPreviewContainer.setVisibility(View.GONE);
                }
            } else {
                h.linkPreviewContainer.setVisibility(View.GONE);
            }
        }

        // ── Category chip ──
        if (task.category != null && !task.category.isEmpty()) {
            h.chipCategory.setVisibility(View.VISIBLE);
            h.tvCategoryIcon.setText(TaskCategory.getIconForCategory(task.category));
            h.tvCategoryName.setText(task.category);
        } else {
            h.chipCategory.setVisibility(View.GONE);
        }

        // ── Due date/time chip ──
        bindDueChip(h, task);

        // ── Reminder indicator ──
        if (h.chipReminder != null) {
            if (task.reminderDateTimes != null && !task.reminderDateTimes.isEmpty()) {
                h.chipReminder.setVisibility(View.VISIBLE);
                h.tvReminderCount.setText(String.valueOf(task.reminderDateTimes.size()));
            } else {
                h.chipReminder.setVisibility(View.GONE);
            }
        }

        // ── Recurrence indicator ──
        if (h.tvRecurrence != null) {
            if (task.isRecurring()) {
                h.tvRecurrence.setVisibility(View.VISIBLE);
                h.tvRecurrence.setText("🔁 " + task.getRecurrenceLabel());
            } else {
                h.tvRecurrence.setVisibility(View.GONE);
            }
        }

        // ── Attachments count ──
        if (h.chipAttachments != null) {
            if (task.attachments != null && !task.attachments.isEmpty()) {
                h.chipAttachments.setVisibility(View.VISIBLE);
                h.tvAttachmentCount.setText(String.valueOf(task.attachments.size()));
            } else {
                h.chipAttachments.setVisibility(View.GONE);
            }
        }

        // ── Location pin ──
        if (h.tvLocationPin != null) {
            if (task.hasLocationReminder()) {
                h.tvLocationPin.setVisibility(View.VISIBLE);
                h.tvLocationPin.setText("📍 " + task.locationReminderName);
            } else {
                h.tvLocationPin.setVisibility(View.GONE);
            }
        }

        // ── Tags row ──
        if (h.tagsScrollView != null && h.tagsContainer != null) {
            if (task.tags != null && !task.tags.isEmpty()) {
                h.tagsScrollView.setVisibility(View.VISIBLE);
                h.tagsContainer.removeAllViews();
                for (String tag : task.tags) {
                    h.tagsContainer.addView(createTagChip(tag));
                }
            } else {
                h.tagsScrollView.setVisibility(View.GONE);
            }
        }

        // ── Time & Energy row ──
        if (h.timeEnergyRow != null) {
            boolean showEstDuration = task.estimatedDuration > 0;
            boolean showTimeTracked = task.getTotalTimerMinutes() > 0 || task.timerRunning;
            boolean showEnergy = task.hasEnergyLevel();
            boolean showRow = showEstDuration || showTimeTracked || showEnergy;

            h.timeEnergyRow.setVisibility(showRow ? View.VISIBLE : View.GONE);

            if (h.chipEstDuration != null) {
                if (showEstDuration) {
                    h.chipEstDuration.setVisibility(View.VISIBLE);
                    h.tvEstDuration.setText("⏱ ~" + task.getEstimatedDurationText());
                } else {
                    h.chipEstDuration.setVisibility(View.GONE);
                }
            }

            if (h.chipTimeTracked != null) {
                if (showTimeTracked) {
                    h.chipTimeTracked.setVisibility(View.VISIBLE);
                    String tracked = task.getTotalTimerText();
                    String prefix = task.timerRunning ? "▶ " : "⏱ ";
                    h.tvTimeTracked.setText(prefix + (tracked.isEmpty() ? "0min" : tracked));
                    if (task.timerRunning) {
                        h.tvTimeTracked.setTextColor(Color.parseColor("#F59E0B"));
                    } else {
                        h.tvTimeTracked.setTextColor(Color.parseColor("#94A3B8"));
                    }
                } else {
                    h.chipTimeTracked.setVisibility(View.GONE);
                }
            }

            if (h.tvEnergyLevel != null) {
                if (showEnergy) {
                    h.tvEnergyLevel.setVisibility(View.VISIBLE);
                    h.tvEnergyLevel.setText(task.getEnergyLevelLabel());
                    h.tvEnergyLevel.setTextColor(task.getEnergyLevelColor());
                } else {
                    h.tvEnergyLevel.setVisibility(View.GONE);
                }
            }
        }

        // ── Dependency indicator ──
        if (h.chipDependency != null) {
            if (task.hasDependency()) {
                h.chipDependency.setVisibility(View.VISIBLE);
                h.tvDependencyText.setText("🔗 Blocked");
            } else {
                h.chipDependency.setVisibility(View.GONE);
            }
        }

        // ── Star toggle ──
        h.btnStar.setText(task.isStarred ? "★" : "☆");
        h.btnStar.setTextColor(task.isStarred ? Color.parseColor("#FBBF24") : Color.parseColor("#4B5563"));
        h.btnStar.setOnClickListener(v -> {
            if (listener != null) listener.onTaskStarToggle(task);
        });

        // ── Carry-over badge (reschedule count indicator) ──
        if (h.tvCarryOverBadge != null) {
            if (task.rescheduleCount >= 2 && !task.isCompleted()) {
                h.tvCarryOverBadge.setVisibility(View.VISIBLE);
                h.tvCarryOverBadge.setText("↻" + task.rescheduleCount);
                h.tvCarryOverBadge.setContentDescription(
                        "Rescheduled " + task.rescheduleCount + " times");
                if (task.rescheduleCount >= 4) {
                    h.tvCarryOverBadge.setTextColor(Color.parseColor("#F59E0B"));
                } else {
                    h.tvCarryOverBadge.setTextColor(Color.parseColor("#94A3B8"));
                }
            } else {
                h.tvCarryOverBadge.setVisibility(View.GONE);
            }
        }

        // ── Batch 4 Badges Row ──
        if (h.batch4BadgesRow != null) {
            boolean showRow = false;

            // Feature 1: Next Action
            if (h.tvNextActionBadge != null) {
                if (task.isNextAction && !task.isCompleted()) {
                    h.tvNextActionBadge.setVisibility(View.VISIBLE);
                    showRow = true;
                } else {
                    h.tvNextActionBadge.setVisibility(View.GONE);
                }
            }

            // Feature 2: MIT
            if (h.tvMITBadge != null) {
                if (task.isMIT && !task.isCompleted()) {
                    h.tvMITBadge.setVisibility(View.VISIBLE);
                    showRow = true;
                } else {
                    h.tvMITBadge.setVisibility(View.GONE);
                }
            }

            // Feature 3: Context Tag
            if (h.tvContextTag != null) {
                if (task.hasContextTag()) {
                    h.tvContextTag.setVisibility(View.VISIBLE);
                    h.tvContextTag.setText(task.contextTag);
                    h.tvContextTag.setTextColor(Task.getContextTagColor(task.contextTag));
                    showRow = true;
                } else {
                    h.tvContextTag.setVisibility(View.GONE);
                }
            }

            // Feature 5: Waiting-For
            if (h.tvWaitingBadge != null) {
                if (task.isWaiting() && task.waitingFor != null && !task.waitingFor.isEmpty()) {
                    h.tvWaitingBadge.setVisibility(View.VISIBLE);
                    h.tvWaitingBadge.setText("⏳ " + task.waitingFor);
                    showRow = true;
                } else {
                    h.tvWaitingBadge.setVisibility(View.GONE);
                }
            }

            // Feature 6: 2-Minute Rule
            if (h.tv2MinBadge != null) {
                if (task.estimatedDuration > 0 && task.estimatedDuration <= 2 && !task.isCompleted()) {
                    h.tv2MinBadge.setVisibility(View.VISIBLE);
                    showRow = true;
                } else {
                    h.tv2MinBadge.setVisibility(View.GONE);
                }
            }

            // Feature 9: Reading Time
            if (h.tvReadingTime != null) {
                int readMins = task.getReadingTimeMinutes();
                if (readMins > 0) {
                    h.tvReadingTime.setVisibility(View.VISIBLE);
                    h.tvReadingTime.setText("📖 " + readMins + "min read");
                    showRow = true;
                } else {
                    h.tvReadingTime.setVisibility(View.GONE);
                }
            }

            // Feature 7: Assigned To
            if (h.tvAssignedTo != null) {
                if (task.assignedTo != null && !task.assignedTo.isEmpty()) {
                    h.tvAssignedTo.setVisibility(View.VISIBLE);
                    h.tvAssignedTo.setText("👤 " + task.assignedTo);
                    showRow = true;
                } else {
                    h.tvAssignedTo.setVisibility(View.GONE);
                }
            }

            // Feature 15: Private task lock
            if (h.tvPrivateLock != null) {
                if (task.isPrivate) {
                    h.tvPrivateLock.setVisibility(View.VISIBLE);
                    showRow = true;
                } else {
                    h.tvPrivateLock.setVisibility(View.GONE);
                }
            }

            // Feature 8: Watcher count
            if (h.tvWatcherCount != null) {
                if (task.watchers != null && !task.watchers.isEmpty()) {
                    h.tvWatcherCount.setVisibility(View.VISIBLE);
                    h.tvWatcherCount.setText("👁 " + task.watchers.size());
                    showRow = true;
                } else {
                    h.tvWatcherCount.setVisibility(View.GONE);
                }
            }

            h.batch4BadgesRow.setVisibility(showRow ? View.VISIBLE : View.GONE);
        }

        // ── Accessibility content descriptions ──
        h.cbComplete.setContentDescription("Mark " + task.title + " as complete");
        h.btnStar.setContentDescription(task.isStarred ?
                "Unstar task: " + task.title : "Star task: " + task.title);
        h.btnTaskMenu.setContentDescription("More options for " + task.title);
        if (h.tvPriorityBadge.getVisibility() == View.VISIBLE) {
            h.tvPriorityBadge.setContentDescription(task.priority + " priority");
        }
        if (h.chipCategory != null && h.chipCategory.getVisibility() == View.VISIBLE
                && task.category != null) {
            h.chipCategory.setContentDescription("Category: " + task.category);
        }

        // ── Subtask progress bar ──
        if (task.hasSubtasks()) {
            h.subtaskProgressContainer.setVisibility(View.VISIBLE);
            h.tvSubtaskCount.setText(task.getSubtaskCompletedCount() + "/" + task.getSubtaskTotalCount());

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

        // ── Subtask inline previews ──
        if (h.subtaskPreviewContainer != null) {
            boolean subtasksVisible;
            if (allSubtasksCollapsed) {
                subtasksVisible = false;
            } else if (perTaskSubtaskMode) {
                subtasksVisible = expandedSubtaskTasks.contains(task.id);
            } else {
                subtasksVisible = true;
            }

            if (task.hasSubtasks() && subtasksVisible) {
                List<SubTask> subs = task.subtasks;
                h.subtaskPreviewContainer.setVisibility(View.VISIBLE);

                if (isExpanded) {
                    // FOCUSED: show ALL subtasks dynamically
                    bindAllSubtaskPreviews(h, subs);
                } else {
                    // DEFAULT: show first 2 subtasks
                    bindDefaultSubtaskPreviews(h, subs);
                }
            } else {
                h.subtaskPreviewContainer.setVisibility(View.GONE);
            }
        }

        // ── Next recurrence footer ──
        if (h.tvNextRecurrence != null) {
            if (task.isRecurring() && task.hasDueDate()) {
                String nextDate = computeNextOccurrence(task);
                if (nextDate != null && !nextDate.isEmpty()) {
                    h.tvNextRecurrence.setVisibility(View.VISIBLE);
                    h.tvNextRecurrence.setText("↻ Next: " + nextDate);
                } else {
                    h.tvNextRecurrence.setVisibility(View.GONE);
                }
            } else {
                h.tvNextRecurrence.setVisibility(View.GONE);
            }
        }

        // ── Card interactions (click, long-press, double-tap, menu) ──
        bindCardInteractions(h, task, isSelected, isCompact, isExpanded);

        // ── Apply tap feedback to interactive elements ──
        applyTapFeedback(h.btnStar);
        applyTapFeedback(h.cbComplete);
        applyTapFeedback(h.btnTaskMenu);
        if (h.chipCategory != null) applyTapFeedback(h.chipCategory);
        if (h.chipDue != null) applyTapFeedback(h.chipDue);
        if (h.chipReminder != null) applyTapFeedback(h.chipReminder);
        if (h.chipAttachments != null) applyTapFeedback(h.chipAttachments);
        if (h.chipEstDuration != null) applyTapFeedback(h.chipEstDuration);
        if (h.chipTimeTracked != null) applyTapFeedback(h.chipTimeTracked);
        if (h.chipDependency != null) applyTapFeedback(h.chipDependency);
        if (h.tvEnergyLevel != null) applyTapFeedback(h.tvEnergyLevel);
        if (h.tvMoreSubtasks != null) applyTapFeedback(h.tvMoreSubtasks);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Due date chip binding (shared by default + compact)
    // ═══════════════════════════════════════════════════════════════

    private void bindDueChip(TaskCardViewHolder h, Task task) {
        if (task.hasDueDate()) {
            h.chipDue.setVisibility(View.VISIBLE);
            // Use countdown text if available, fallback to formatted date
            String countdownText = TaskUtils.computeCountdownText(task);
            String dueText = countdownText != null ? countdownText
                    : TaskDateFormatter.formatDueDate(task.dueDate, task.dueTime);
            h.tvDueText.setText(dueText);
            if (task.isOverdue()) {
                h.tvDueIcon.setText("⏰");
                h.tvDueText.setTextColor(Color.parseColor("#EF4444"));
            } else if (task.isDueToday()) {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#F59E0B"));
            } else if (task.isDueTomorrow()) {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#3B82F6"));
            } else if (TaskDateFormatter.isDueSoon(task.dueDate)) {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#FBBF24"));
            } else {
                h.tvDueIcon.setText("📅");
                h.tvDueText.setTextColor(Color.parseColor("#94A3B8"));
            }
            // Countdown badge content description
            if (countdownText != null) {
                h.chipDue.setContentDescription("Due " + countdownText);
            }
        } else {
            h.chipDue.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Card interactions — click, double-tap, long-press, menu
    // ═══════════════════════════════════════════════════════════════

    private void bindCardInteractions(TaskCardViewHolder h, Task task,
                                      boolean isSelected, boolean isCompact, boolean isExpanded) {
        // Menu button
        h.btnTaskMenu.setOnClickListener(v -> {
            if (!multiSelectMode && listener != null) listener.onTaskMenuClicked(task, v);
        });

        // Double-tap on title → inline edit; single tap confirmed → card click
        GestureDetector doubleTapDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        h.itemView.performClick();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        enterInlineTitleEdit(h, task);
                        return true;
                    }
                });

        h.tvTaskTitle.setOnTouchListener((v, event) -> {
            doubleTapDetector.onTouchEvent(event);
            return false; // don't consume single taps
        });

        // Card click — multi-select or open detail
        h.itemView.setOnClickListener(v -> {
            if (multiSelectMode) {
                toggleSelection(task.id);
            } else if (listener != null) {
                listener.onTaskClicked(task);
            }
        });

        // Long-press → enter multi-select
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

        // Staggered entrance animation
        int position = h.getAdapterPosition();
        if (position > lastAnimatedPosition) {
            h.itemView.setTranslationY(30f);
            h.itemView.animate()
                    .translationY(0f)
                    .alpha(h.itemView.getAlpha())
                    .setDuration(300)
                    .setStartDelay((long) Math.min(position, 10) * 40)
                    .start();
            lastAnimatedPosition = position;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Hide fields for compact mode
    // ═══════════════════════════════════════════════════════════════

    private void hideCompactFields(TaskCardViewHolder h) {
        h.tvTaskDescription.setVisibility(View.GONE);
        if (h.linkPreviewContainer != null) h.linkPreviewContainer.setVisibility(View.GONE);
        h.chipCategory.setVisibility(View.GONE);
        if (h.chipReminder != null) h.chipReminder.setVisibility(View.GONE);
        if (h.tvRecurrence != null) h.tvRecurrence.setVisibility(View.GONE);
        if (h.chipAttachments != null) h.chipAttachments.setVisibility(View.GONE);
        if (h.tvLocationPin != null) h.tvLocationPin.setVisibility(View.GONE);
        if (h.tagsScrollView != null) h.tagsScrollView.setVisibility(View.GONE);
        if (h.timeEnergyRow != null) h.timeEnergyRow.setVisibility(View.GONE);
        if (h.chipDependency != null) h.chipDependency.setVisibility(View.GONE);
        h.subtaskProgressContainer.setVisibility(View.GONE);
        if (h.subtaskPreviewContainer != null) h.subtaskPreviewContainer.setVisibility(View.GONE);
        if (h.tvNextRecurrence != null) h.tvNextRecurrence.setVisibility(View.GONE);
        if (h.batch4BadgesRow != null) h.batch4BadgesRow.setVisibility(View.GONE);
        // Keep: title, priority badge, due date chip, star, menu, checkbox
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Default subtask previews (first 2 + "N more")
    // ═══════════════════════════════════════════════════════════════

    private void bindDefaultSubtaskPreviews(TaskCardViewHolder h, List<SubTask> subs) {
        // First subtask
        if (subs.size() >= 1) {
            h.subtaskPreview1.setVisibility(View.VISIBLE);
            h.cbSubtask1.setOnCheckedChangeListener(null);
            h.cbSubtask1.setChecked(subs.get(0).isCompleted);
            h.tvSubtask1Title.setText(subs.get(0).title);
            styleSubtaskText(h.tvSubtask1Title, subs.get(0).isCompleted);
        } else {
            h.subtaskPreview1.setVisibility(View.GONE);
        }

        // Second subtask
        if (subs.size() >= 2) {
            h.subtaskPreview2.setVisibility(View.VISIBLE);
            h.cbSubtask2.setOnCheckedChangeListener(null);
            h.cbSubtask2.setChecked(subs.get(1).isCompleted);
            h.tvSubtask2Title.setText(subs.get(1).title);
            styleSubtaskText(h.tvSubtask2Title, subs.get(1).isCompleted);
        } else {
            h.subtaskPreview2.setVisibility(View.GONE);
        }

        // "+N more"
        if (subs.size() > 2) {
            h.tvMoreSubtasks.setVisibility(View.VISIBLE);
            h.tvMoreSubtasks.setText("+" + (subs.size() - 2) + " more");
        } else {
            h.tvMoreSubtasks.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER: Focused — show ALL subtasks dynamically
    // ═══════════════════════════════════════════════════════════════

    private void bindAllSubtaskPreviews(TaskCardViewHolder h, List<SubTask> subs) {
        // Use the static subtask rows for first 2
        if (subs.size() >= 1) {
            h.subtaskPreview1.setVisibility(View.VISIBLE);
            h.cbSubtask1.setOnCheckedChangeListener(null);
            h.cbSubtask1.setChecked(subs.get(0).isCompleted);
            h.tvSubtask1Title.setText(subs.get(0).title);
            styleSubtaskText(h.tvSubtask1Title, subs.get(0).isCompleted);
        } else {
            h.subtaskPreview1.setVisibility(View.GONE);
        }
        if (subs.size() >= 2) {
            h.subtaskPreview2.setVisibility(View.VISIBLE);
            h.cbSubtask2.setOnCheckedChangeListener(null);
            h.cbSubtask2.setChecked(subs.get(1).isCompleted);
            h.tvSubtask2Title.setText(subs.get(1).title);
            styleSubtaskText(h.tvSubtask2Title, subs.get(1).isCompleted);
        } else {
            h.subtaskPreview2.setVisibility(View.GONE);
        }

        // For subtasks 3+, add dynamic rows
        // Remove any previously added dynamic subtask views (index 3+ children in container)
        // subtaskPreviewContainer has: subtaskPreview1, subtaskPreview2, tvMoreSubtasks as static children
        // Remove all views after those 3 static children
        int staticChildCount = 3; // subtaskPreview1, subtaskPreview2, tvMoreSubtasks
        while (h.subtaskPreviewContainer.getChildCount() > staticChildCount) {
            h.subtaskPreviewContainer.removeViewAt(staticChildCount);
        }

        h.tvMoreSubtasks.setVisibility(View.GONE); // hide "+N more" in expanded

        for (int i = 2; i < subs.size(); i++) {
            SubTask sub = subs.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(4), 0, 0, 0);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(24));
            row.setLayoutParams(rowLp);

            CheckBox cb = new CheckBox(context);
            cb.setChecked(sub.isCompleted);
            cb.setScaleX(0.85f);
            cb.setScaleY(0.85f);
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
            cbLp.setMarginEnd(dpToPx(8));
            cb.setLayoutParams(cbLp);

            TextView tv = new TextView(context);
            tv.setText(sub.title);
            tv.setTextSize(12f);
            tv.setMaxLines(1);
            styleSubtaskText(tv, sub.isCompleted);

            row.addView(cb);
            row.addView(tv);
            h.subtaskPreviewContainer.addView(row);
        }
    }

    private void styleSubtaskText(TextView tv, boolean completed) {
        if (completed) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(Color.parseColor("#4B5563"));
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FEATURE 4: Completion Spring Animation
    // ═══════════════════════════════════════════════════════════════

    private void playCompletionAnimation(TaskCardViewHolder h) {
        // Reduced motion check — skip all animations if enabled
        if (TaskUtils.isReducedMotionEnabled(context)) {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTaskTitle.setTextColor(Color.parseColor("#6B7280"));
            h.itemView.setAlpha(0.5f);
            h.itemView.setBackgroundResource(R.drawable.task_card_completed_bg);
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Task task = getTaskAtPosition(h.getAdapterPosition());

        // Light haptic on checkbox tap
        TaskUtils.hapticClick(context);

        // Step 1: Checkbox scale pulse (0ms → 200ms)
        h.cbComplete.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .setDuration(100)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() ->
                    h.cbComplete.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(100)
                            .start()
                ).start();

        // Step 2: Animated strikethrough (0ms → 180ms)
        if (task != null && task.title != null) {
            ValueAnimator strikeAnim = ValueAnimator.ofFloat(0f, 1f);
            strikeAnim.setDuration(180);
            strikeAnim.addUpdateListener(anim -> {
                float progress = (float) anim.getAnimatedValue();
                SpannableString span = new SpannableString(task.title);
                span.setSpan(new PartialStrikethroughSpan(progress),
                        0, task.title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                h.tvTaskTitle.setText(span);
            });
            strikeAnim.start();
        } else {
            h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        // Step 3: Title color fade (0ms → 300ms)
        ObjectAnimator colorFade = ObjectAnimator.ofArgb(h.tvTaskTitle, "textColor",
                Color.WHITE, Color.parseColor("#64748B"));
        colorFade.setDuration(300);
        colorFade.start();

        // Step 4: Heavy haptic at 350ms (checkmark completion feel)
        handler.postDelayed(() -> TaskUtils.hapticHeavy(context), 350);

        // Step 5: Confetti burst for High/Urgent (200ms delay)
        if (task != null && (Task.PRIORITY_URGENT.equals(task.priority)
                || Task.PRIORITY_HIGH.equals(task.priority))) {
            handler.postDelayed(() -> {
                try {
                    int[] loc = new int[2];
                    h.cbComplete.getLocationOnScreen(loc);
                    float cx = loc[0] + h.cbComplete.getWidth() / 2f;
                    float cy = loc[1] + h.cbComplete.getHeight() / 2f;
                    int count = Task.PRIORITY_URGENT.equals(task.priority) ? 20 : 12;
                    int[] palette = Task.PRIORITY_URGENT.equals(task.priority)
                            ? ConfettiView.PALETTE_URGENT : ConfettiView.PALETTE_HIGH;
                    android.app.Activity activity = (android.app.Activity) context;
                    ConfettiView.launch(activity.getWindow().getDecorView(), cx, cy, count, palette);
                } catch (Exception ignored) {}
            }, 200);
        }

        // Step 6: Card exit animation (400ms → 650ms)
        handler.postDelayed(() -> {
            h.itemView.animate()
                    .scaleX(0.95f).scaleY(0.95f)
                    .translationY(-8f)
                    .alpha(0f)
                    .setDuration(250)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        h.itemView.setBackgroundResource(R.drawable.task_card_completed_bg);
                        // Reset for reuse
                        h.itemView.setScaleX(1f);
                        h.itemView.setScaleY(1f);
                        h.itemView.setTranslationY(0f);
                        h.itemView.setAlpha(0.5f);
                    })
                    .start();
        }, 400);
    }

    private void playUndoCompletionAnimation(TaskCardViewHolder h) {
        // Reverse: restore alpha + scale, remove strikethrough, restore bg
        h.tvTaskTitle.setPaintFlags(h.tvTaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        h.tvTaskTitle.setTextColor(Color.parseColor("#F1F5F9"));

        h.itemView.animate()
                .scaleY(1.05f)
                .alpha(1f)
                .setDuration(100)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() ->
                    h.itemView.animate()
                            .scaleY(1f)
                            .setDuration(80)
                            .withEndAction(() -> h.itemView.setBackground(null))
                            .start()
                ).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // FEATURE 6A: Next Recurrence Computation
    // ═══════════════════════════════════════════════════════════════

    private String computeNextOccurrence(Task task) {
        if (task.dueDate == null || task.recurrence == null) return null;
        try {
            String[] parts = task.dueDate.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));

            switch (task.recurrence) {
                case "daily":   cal.add(Calendar.DAY_OF_YEAR, 1); break;
                case "weekly":  cal.add(Calendar.DAY_OF_YEAR, 7); break;
                case "monthly": cal.add(Calendar.MONTH, 1);       break;
                case "custom":
                    return task.recurrenceRule != null ? task.recurrenceRule : "Custom";
                default:
                    return null;
            }

            String nextDateStr = String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            return TaskDateFormatter.formatDueDate(nextDateStr, task.dueTime);
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FEATURE 6C: Tap Scale + Glow Feedback
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("ClickableViewAccessibility")
    private void applyTapFeedback(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                            .setInterpolator(new OvershootInterpolator())
                            .start();
                    break;
            }
            return false; // let the click/check event propagate
        });
    }

    private int[] getPriorityGradientColors(String priority) {
        if (priority == null) return new int[]{Color.parseColor("#6B7280"), Color.parseColor("#4B5563")};
        switch (priority) {
            case "URGENT": return new int[]{Color.parseColor("#EF4444"), Color.parseColor("#DC2626")};
            case "HIGH":   return new int[]{Color.parseColor("#F97316"), Color.parseColor("#EA580C")};
            case "NORMAL": return new int[]{Color.parseColor("#3B82F6"), Color.parseColor("#2563EB")};
            case "LOW":    return new int[]{Color.parseColor("#9CA3AF"), Color.parseColor("#6B7280")};
            default:       return new int[]{Color.parseColor("#6B7280"), Color.parseColor("#4B5563")};
        }
    }

    private int getPriorityStripColor(String priority) {
        if (priority == null) return 0xFF6B7280;
        switch (priority) {
            case "URGENT": return 0xFFEF4444;
            case "HIGH":   return 0xFFF97316;
            case "NORMAL": return 0xFF3B82F6;
            case "LOW":    return 0xFF9CA3AF;
            default:       return 0xFF6B7280;
        }
    }

    /**
     * Creates a pastel tag chip TextView to add into the tags container.
     */
    private TextView createTagChip(String tagText) {
        TextView chip = new TextView(context);
        chip.setText("#" + tagText);
        chip.setTextSize(11f);
        chip.setTextColor(getTagColor(tagText));
        chip.setBackgroundResource(R.drawable.task_tag_chip_bg);

        int hPad = dpToPx(10);
        int vPad = dpToPx(4);
        chip.setPadding(hPad, vPad, hPad, vPad);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dpToPx(6));
        chip.setLayoutParams(lp);

        return chip;
    }

    /** Returns a stable pastel color based on tag text hash. */
    private int getTagColor(String tagText) {
        int[] pastelColors = {
            Color.parseColor("#93C5FD"), // blue
            Color.parseColor("#86EFAC"), // green
            Color.parseColor("#FDE68A"), // yellow
            Color.parseColor("#FCA5A5"), // red
            Color.parseColor("#C4B5FD"), // purple
            Color.parseColor("#FDA4AF"), // pink
            Color.parseColor("#67E8F9"), // cyan
            Color.parseColor("#FDBA74"), // orange
        };
        int hash = Math.abs(tagText.hashCode());
        return pastelColors[hash % pastelColors.length];
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ─── Inline Title Editing ─────────────────────────────────────

    private void enterInlineTitleEdit(TaskCardViewHolder holder, Task task) {
        if (holder.etTitleEdit == null) return;
        holder.tvTaskTitle.setVisibility(View.GONE);
        holder.etTitleEdit.setVisibility(View.VISIBLE);
        holder.etTitleEdit.setText(task.title);
        holder.etTitleEdit.setSelection(holder.etTitleEdit.getText().length());
        holder.etTitleEdit.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
            context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(holder.etTitleEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);

        holder.etTitleEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitInlineTitleEdit(holder, task);
        });
        holder.etTitleEdit.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                commitInlineTitleEdit(holder, task);
                return true;
            }
            return false;
        });
        holder.etTitleEdit.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
                exitInlineTitleEdit(holder, task.title);
                return true;
            }
            return false;
        });
    }

    private void commitInlineTitleEdit(TaskCardViewHolder holder, Task task) {
        if (holder.etTitleEdit == null) return;
        String newTitle = holder.etTitleEdit.getText().toString().trim();
        String displayTitle = newTitle.isEmpty() ? task.title : newTitle;
        exitInlineTitleEdit(holder, displayTitle);
        if (!newTitle.isEmpty() && !newTitle.equals(task.title)) {
            task.title = newTitle;
            task.updatedAt = System.currentTimeMillis();
            if (listener != null) listener.onTaskUpdated(task);
        }
    }

    private void exitInlineTitleEdit(TaskCardViewHolder holder, String displayTitle) {
        if (holder.etTitleEdit == null) return;
        holder.etTitleEdit.setVisibility(View.GONE);
        holder.tvTaskTitle.setVisibility(View.VISIBLE);
        holder.tvTaskTitle.setText(displayTitle);
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
            context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(holder.etTitleEdit.getWindowToken(), 0);
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
        // ── Core fields ──
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

        // ── New Prompt-1 fields ──
        LinearLayout chipReminder, chipAttachments;
        TextView tvReminderCount, tvAttachmentCount;
        TextView tvRecurrence, tvLocationPin;

        // Tags
        android.widget.HorizontalScrollView tagsScrollView;
        LinearLayout tagsContainer;

        // Time & Energy row
        LinearLayout timeEnergyRow, chipEstDuration, chipTimeTracked;
        TextView tvEstDuration, tvTimeTracked;
        TextView tvEnergyLevel;

        // Dependencies
        LinearLayout chipDependency;
        TextView tvDependencyText;

        // Subtask preview
        LinearLayout subtaskPreviewContainer, subtaskPreview1, subtaskPreview2;
        CheckBox cbSubtask1, cbSubtask2;
        TextView tvSubtask1Title, tvSubtask2Title, tvMoreSubtasks;

        // Smart features
        TextView tvNextRecurrence;
        LinearLayout linkPreviewContainer;
        TextView tvLinkUrl;

        // Carry-over badge
        TextView tvCarryOverBadge;

        // Batch 4 badges
        LinearLayout batch4BadgesRow;
        TextView tvNextActionBadge, tvMITBadge, tvContextTag, tvWaitingBadge;
        TextView tv2MinBadge, tvReadingTime, tvAssignedTo, tvPrivateLock;
        TextView tvWatcherCount;

        // Inline title edit
        android.widget.EditText etTitleEdit;

        // Priority morph tracking
        String lastBoundPriority = null;

        TaskCardViewHolder(View v) {
            super(v);
            // Core
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

            // New fields
            chipReminder = v.findViewById(R.id.chipReminder);
            tvReminderCount = v.findViewById(R.id.tvReminderCount);
            chipAttachments = v.findViewById(R.id.chipAttachments);
            tvAttachmentCount = v.findViewById(R.id.tvAttachmentCount);
            tvRecurrence = v.findViewById(R.id.tvRecurrence);
            tvLocationPin = v.findViewById(R.id.tvLocationPin);
            tagsScrollView = v.findViewById(R.id.tagsScrollView);
            tagsContainer = v.findViewById(R.id.tagsContainer);
            timeEnergyRow = v.findViewById(R.id.timeEnergyRow);
            chipEstDuration = v.findViewById(R.id.chipEstDuration);
            tvEstDuration = v.findViewById(R.id.tvEstDuration);
            chipTimeTracked = v.findViewById(R.id.chipTimeTracked);
            tvTimeTracked = v.findViewById(R.id.tvTimeTracked);
            tvEnergyLevel = v.findViewById(R.id.tvEnergyLevel);
            chipDependency = v.findViewById(R.id.chipDependency);
            tvDependencyText = v.findViewById(R.id.tvDependencyText);
            subtaskPreviewContainer = v.findViewById(R.id.subtaskPreviewContainer);
            subtaskPreview1 = v.findViewById(R.id.subtaskPreview1);
            subtaskPreview2 = v.findViewById(R.id.subtaskPreview2);
            cbSubtask1 = v.findViewById(R.id.cbSubtask1);
            cbSubtask2 = v.findViewById(R.id.cbSubtask2);
            tvSubtask1Title = v.findViewById(R.id.tvSubtask1Title);
            tvSubtask2Title = v.findViewById(R.id.tvSubtask2Title);
            tvMoreSubtasks = v.findViewById(R.id.tvMoreSubtasks);

            // Smart features
            tvNextRecurrence = v.findViewById(R.id.tvNextRecurrence);
            linkPreviewContainer = v.findViewById(R.id.linkPreviewContainer);
            tvLinkUrl = v.findViewById(R.id.tvLinkUrl);

            // Carry-over badge
            tvCarryOverBadge = v.findViewById(R.id.tvCarryOverBadge);

            // Batch 4 badges
            batch4BadgesRow = v.findViewById(R.id.batch4BadgesRow);
            tvNextActionBadge = v.findViewById(R.id.tvNextActionBadge);
            tvMITBadge = v.findViewById(R.id.tvMITBadge);
            tvContextTag = v.findViewById(R.id.tvContextTag);
            tvWaitingBadge = v.findViewById(R.id.tvWaitingBadge);
            tv2MinBadge = v.findViewById(R.id.tv2MinBadge);
            tvReadingTime = v.findViewById(R.id.tvReadingTime);
            tvAssignedTo = v.findViewById(R.id.tvAssignedTo);
            tvPrivateLock = v.findViewById(R.id.tvPrivateLock);
            tvWatcherCount = v.findViewById(R.id.tvWatcherCount);

            // Inline title edit
            etTitleEdit = v.findViewById(R.id.etTitleEdit);
        }
    }

    // ─── Grid Card ViewHolder ────────────────────────────────────

    static class GridCardViewHolder extends RecyclerView.ViewHolder {
        View viewPriorityStrip;
        TextView tvTitle, tvDueDate, tvStar;

        GridCardViewHolder(@NonNull View v) {
            super(v);
            viewPriorityStrip = v.findViewById(R.id.viewGridPriorityStrip);
            tvTitle = v.findViewById(R.id.tvGridTitle);
            tvDueDate = v.findViewById(R.id.tvGridDueDate);
            tvStar = v.findViewById(R.id.tvGridStar);
        }
    }

    // ─── Grid Card Binding ───────────────────────────────────────

    private void bindGridCard(GridCardViewHolder h, Task task) {
        // Priority strip color
        String color = getPriorityColor(task.priority);
        if (task.colorLabel != null && !task.colorLabel.isEmpty()) {
            color = task.colorLabel;
        }
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setShape(GradientDrawable.RECTANGLE);
        stripBg.setCornerRadius(dp(2));
        stripBg.setColor(Color.parseColor(color));
        h.viewPriorityStrip.setBackground(stripBg);

        // Title
        h.tvTitle.setText(task.title);
        if (task.isCompleted()) {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(Color.parseColor("#6B7280"));
        } else {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(Color.parseColor("#F1F5F9"));
        }

        // Due date
        if (task.hasDueDate()) {
            h.tvDueDate.setText("📅 " + task.dueDate);
            h.tvDueDate.setVisibility(View.VISIBLE);
        } else {
            h.tvDueDate.setVisibility(View.GONE);
        }

        // Star
        h.tvStar.setText(task.isStarred ? "★" : "☆");
        h.tvStar.setTextColor(task.isStarred ? Color.parseColor("#FBBF24") : Color.parseColor("#4B5563"));
        h.tvStar.setOnClickListener(v -> {
            if (listener != null) listener.onTaskStarToggle(task);
        });

        // Deferred alpha
        h.itemView.setAlpha(task.isDeferred() ? 0.5f : 1.0f);

        // Card click
        h.itemView.setOnClickListener(v -> {
            if (multiSelectMode) {
                toggleSelection(task.id);
            } else if (listener != null) {
                listener.onTaskClicked(task);
            }
        });

        // Long press menu
        h.itemView.setOnLongClickListener(v -> {
            if (!multiSelectMode) {
                enterMultiSelect();
                toggleSelection(task.id);
            }
            return true;
        });

        // Selection highlight
        boolean isSelected = selectedIds.contains(task.id);
        h.itemView.setAlpha(isSelected ? 0.7f : (task.isDeferred() ? 0.5f : 1.0f));
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

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
