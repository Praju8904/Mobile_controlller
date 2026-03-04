package com.prajwal.myfirstapp.tasks;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Swipe-to-complete (right) and swipe-to-reschedule/archive (left) callback
 * for the task RecyclerView.
 *
 * <p>Draws a colored background with icon behind the card as it slides,
 * then fires {@link TaskAdapter.TaskActionListener#onTaskSwiped} on completion.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Right swipe → complete task (green background, checkmark icon)</li>
 *   <li>Left swipe → archive task (red background, trash icon)</li>
 *   <li>Threshold haptic at 40% swipe progress</li>
 *   <li>Disabled during multi-select mode</li>
 *   <li>Disabled for completed/cancelled tasks and group headers</li>
 * </ul></p>
 */
public class TaskSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private static final int COLOR_COMPLETE   = Color.parseColor("#10B981");
    private static final int COLOR_UNDO       = Color.parseColor("#FBBF24");
    private static final int COLOR_ARCHIVE    = Color.parseColor("#EF4444");
    private static final int COLOR_RESCHEDULE = Color.parseColor("#6366F1");
    private static final float SWIPE_THRESHOLD = 0.4f;

    private final TaskAdapter adapter;
    private final TaskAdapter.TaskActionListener listener;
    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Context context;
    private final TaskManagerSettings settings;

    // Threshold haptic flags — reset in clearView()
    private boolean thresholdHapticFiredRight = false;
    private boolean thresholdHapticFiredLeft = false;

    public TaskSwipeCallback(TaskAdapter adapter, TaskAdapter.TaskActionListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;
        this.context = null;
        this.settings = null;
        iconPaint.setColor(Color.WHITE);
        iconPaint.setTextSize(56f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
    }

    public TaskSwipeCallback(TaskAdapter adapter, TaskAdapter.TaskActionListener listener,
                             Context context, TaskManagerSettings settings) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;
        this.context = context;
        this.settings = settings;
        iconPaint.setColor(Color.WHITE);
        iconPaint.setTextSize(56f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
    }

    // ── Disable drag ────────────────────────────────────────────
    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    // ── Disable swipe when in multi-select or for completed/cancelled tasks ──
    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh) {
        if (adapter.isInMultiSelectMode()) return 0;
        // Only allow swiping task cards, not group headers
        if (!(vh instanceof TaskAdapter.TaskCardViewHolder)) return 0;
        // Disable swipe for completed and cancelled tasks
        Task task = adapter.getTaskAtPosition(vh.getAdapterPosition());
        if (task != null && (task.isCompleted() || task.isCancelled())) return 0;
        return super.getSwipeDirs(rv, vh);
    }

    // ── Swiped ──────────────────────────────────────────────────
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getAdapterPosition();
        Task task = adapter.getTaskAtPosition(pos);
        if (task == null || listener == null) return;

        if (direction == ItemTouchHelper.RIGHT) {
            listener.onTaskSwiped(task, TaskAdapter.SWIPE_COMPLETE);
        } else if (direction == ItemTouchHelper.LEFT) {
            listener.onTaskSwiped(task, TaskAdapter.SWIPE_ARCHIVE);
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        thresholdHapticFiredRight = false;
        thresholdHapticFiredLeft = false;
    }

    // ── Draw background + icon behind card ──────────────────────
    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        View itemView = vh.itemView;
        float density = rv.getContext().getResources().getDisplayMetrics().density;
        float cornerR = 16f * density;
        float iconMargin = 24f * density;
        float iconSize = 28f * density;

        float top    = itemView.getTop();
        float bottom = itemView.getBottom();
        float left   = itemView.getLeft();
        float right  = itemView.getRight();
        float cardWidth = right - left;
        float swipeRatio = Math.abs(dX) / cardWidth;

        // Determine task completion state for right-swipe color
        Task task = adapter.getTaskAtPosition(vh.getAdapterPosition());
        boolean isAlreadyCompleted = (task != null && task.isCompleted());

        if (dX > 0) {
            // ── Swiping RIGHT → Complete / Undo ──
            bgPaint.setColor(isAlreadyCompleted ? COLOR_UNDO : COLOR_COMPLETE);

            // Draw rounded rect only in revealed area
            RectF bgRect = new RectF(left, top, left + dX, bottom);
            Path path = new Path();
            float[] radii = {cornerR, cornerR, 0, 0, 0, 0, cornerR, cornerR};
            path.addRoundRect(bgRect, radii, Path.Direction.CW);
            c.drawPath(path, bgPaint);

            // Draw icon when swipe > 15% of card width
            if (swipeRatio > 0.15f) {
                float iconAlpha = Math.min(1f, (swipeRatio - 0.15f) / 0.25f);
                iconPaint.setAlpha((int) (iconAlpha * 255));
                iconPaint.setTextSize(iconSize);

                String icon = isAlreadyCompleted ? "↩" : "✓";
                float iconX = left + iconMargin + iconSize / 2;
                float iconY = top + (bottom - top) / 2 + iconSize / 3;
                c.drawText(icon, iconX, iconY, iconPaint);
            }

            // Threshold haptic at 40%
            if (swipeRatio >= SWIPE_THRESHOLD && !thresholdHapticFiredRight) {
                thresholdHapticFiredRight = true;
                fireHaptic();
            }

        } else if (dX < 0) {
            // ── Swiping LEFT → Push to Tomorrow ──
            bgPaint.setColor(COLOR_RESCHEDULE);

            RectF bgRect = new RectF(right + dX, top, right, bottom);
            Path path = new Path();
            float[] radii = {0, 0, cornerR, cornerR, cornerR, cornerR, 0, 0};
            path.addRoundRect(bgRect, radii, Path.Direction.CW);
            c.drawPath(path, bgPaint);

            if (swipeRatio > 0.15f) {
                float iconAlpha = Math.min(1f, (swipeRatio - 0.15f) / 0.25f);
                iconPaint.setAlpha((int) (iconAlpha * 255));
                iconPaint.setTextSize(iconSize);

                String icon = "📅";
                float iconX = right - iconMargin - iconSize / 2;
                float iconY = top + (bottom - top) / 2 + iconSize / 3;
                c.drawText(icon, iconX, iconY, iconPaint);

                // Draw "Tomorrow" text below icon
                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(12f * density);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setAlpha((int) (iconAlpha * 255));
                c.drawText("Tomorrow", iconX, iconY + 18f * density, textPaint);
            }

            // Threshold haptic at 40%
            if (swipeRatio >= SWIPE_THRESHOLD && !thresholdHapticFiredLeft) {
                thresholdHapticFiredLeft = true;
                fireHaptic();
            }
        }

        // Reset paint alpha
        iconPaint.setAlpha(255);

        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
    }

    /**
     * Fire a short haptic pulse if haptic feedback is enabled.
     */
    private void fireHaptic() {
        if (settings != null && !settings.hapticFeedback) return;
        if (context == null) return;
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        } catch (Exception ignored) {}
    }
}
