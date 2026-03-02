package com.prajwal.myfirstapp.tasks;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Swipe-to-complete (right) and swipe-to-archive (left) callback
 * for the task RecyclerView.
 *
 * <p>Draws a colored background with icon behind the card as it slides,
 * then fires {@link TaskAdapter.TaskActionListener#onTaskSwiped} on completion.</p>
 */
public class TaskSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private static final int COLOR_COMPLETE   = Color.parseColor("#10B981");
    private static final int COLOR_UNDO       = Color.parseColor("#FBBF24");
    private static final int COLOR_ARCHIVE    = Color.parseColor("#EF4444");
    private static final float CORNER_RADIUS  = 16f * 2.5f; // ~16dp at mdpi — will be scaled

    private final TaskAdapter adapter;
    private final TaskAdapter.TaskActionListener listener;
    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TaskSwipeCallback(TaskAdapter adapter, TaskAdapter.TaskActionListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.listener = listener;
        iconPaint.setColor(Color.WHITE);
        iconPaint.setTextSize(56f); // will be scaled with density
        iconPaint.setTextAlign(Paint.Align.CENTER);
    }

    // ── Disable drag ────────────────────────────────────────────
    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    // ── Disable swipe when in multi-select ──────────────────────
    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh) {
        if (adapter.isInMultiSelectMode()) return 0;
        // Only allow swiping task cards, not group headers
        if (!(vh instanceof TaskAdapter.TaskCardViewHolder)) return 0;
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

        // Determine task completion state for right-swipe color
        Task task = adapter.getTaskAtPosition(vh.getAdapterPosition());
        boolean isAlreadyCompleted = (task != null && task.isCompleted());

        if (dX > 0) {
            // ── Swiping RIGHT → Complete / Undo ──
            bgPaint.setColor(isAlreadyCompleted ? COLOR_UNDO : COLOR_COMPLETE);

            // Draw rounded rect only in revealed area
            RectF bgRect = new RectF(left, top, left + dX, bottom);
            Path path = new Path();
            float[] radii = {cornerR, cornerR, 0, 0, 0, 0, cornerR, cornerR}; // TL, TR, BR, BL
            path.addRoundRect(bgRect, radii, Path.Direction.CW);
            c.drawPath(path, bgPaint);

            // Draw icon when swipe > 40% of card width
            float swipeRatio = Math.abs(dX) / cardWidth;
            if (swipeRatio > 0.15f) {
                float iconAlpha = Math.min(1f, (swipeRatio - 0.15f) / 0.25f);
                iconPaint.setAlpha((int) (iconAlpha * 255));
                iconPaint.setTextSize(iconSize);

                String icon = isAlreadyCompleted ? "↩" : "✓";
                float iconX = left + iconMargin + iconSize / 2;
                float iconY = top + (bottom - top) / 2 + iconSize / 3;
                c.drawText(icon, iconX, iconY, iconPaint);
            }

        } else if (dX < 0) {
            // ── Swiping LEFT → Archive ──
            bgPaint.setColor(COLOR_ARCHIVE);

            RectF bgRect = new RectF(right + dX, top, right, bottom);
            Path path = new Path();
            float[] radii = {0, 0, cornerR, cornerR, cornerR, cornerR, 0, 0}; // TL, TR, BR, BL
            path.addRoundRect(bgRect, radii, Path.Direction.CW);
            c.drawPath(path, bgPaint);

            float swipeRatio = Math.abs(dX) / cardWidth;
            if (swipeRatio > 0.15f) {
                float iconAlpha = Math.min(1f, (swipeRatio - 0.15f) / 0.25f);
                iconPaint.setAlpha((int) (iconAlpha * 255));
                iconPaint.setTextSize(iconSize);

                String icon = "🗑";
                float iconX = right - iconMargin - iconSize / 2;
                float iconY = top + (bottom - top) / 2 + iconSize / 3;
                c.drawText(icon, iconX, iconY, iconPaint);
            }
        }

        // Reset paint alpha
        iconPaint.setAlpha(255);

        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
    }
}
