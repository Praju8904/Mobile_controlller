package com.prajwal.myfirstapp.tasks;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ItemTouchHelper callback for drag-and-drop reordering of subtasks.
 * Gap ref: 4.6
 */
public class SubtaskDragCallback extends ItemTouchHelper.SimpleCallback {

    public SubtaskDragCallback() {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder dragged,
                          @NonNull RecyclerView.ViewHolder target) {
        int fromPos = dragged.getAdapterPosition();
        int toPos   = target.getAdapterPosition();
        if (fromPos < 0 || toPos < 0) return false;
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter instanceof SubtaskAdapter) {
            ((SubtaskAdapter) adapter).moveItem(fromPos, toPos);
        }
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
        // Swipe disabled — no-op
    }

    @Override
    public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
        super.clearView(rv, vh);
        vh.itemView.setElevation(0f);
        vh.itemView.setScaleX(1f);
        vh.itemView.setScaleY(1f);
        vh.itemView.setAlpha(1f);
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter instanceof SubtaskAdapter) {
            ((SubtaskAdapter) adapter).commitOrder();
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                             @NonNull RecyclerView.ViewHolder vh,
                             float dX, float dY, int action, boolean active) {
        if (active) {
            vh.itemView.setElevation(16f);
            vh.itemView.setScaleX(1.03f);
            vh.itemView.setScaleY(1.03f);
            vh.itemView.setAlpha(0.92f);
        }
        super.onChildDraw(c, rv, vh, dX, dY, action, active);
    }
}
