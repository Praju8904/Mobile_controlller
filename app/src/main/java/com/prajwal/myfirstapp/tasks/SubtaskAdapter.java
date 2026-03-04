package com.prajwal.myfirstapp.tasks;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.prajwal.myfirstapp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for SubTask items with drag-and-drop support.
 * Gap ref: 4.6
 */
public class SubtaskAdapter extends RecyclerView.Adapter<SubtaskAdapter.SubtaskViewHolder> {

    public interface OnSubtaskCheckedListener {
        void onSubtaskChecked(SubTask subTask, boolean checked);
    }

    public interface OnOrderChangedListener {
        void onSubtaskOrderChanged(List<SubTask> newOrder);
    }

    private final List<SubTask> subtasks;
    private OnSubtaskCheckedListener onSubtaskCheckedListener;
    private OnOrderChangedListener onOrderChangedListener;
    private ItemTouchHelper itemTouchHelper;

    public SubtaskAdapter(List<SubTask> subtasks) {
        this.subtasks = new ArrayList<>(subtasks != null ? subtasks : new ArrayList<>());
    }

    public void setOnSubtaskCheckedListener(OnSubtaskCheckedListener l) {
        this.onSubtaskCheckedListener = l;
    }

    public void setOnOrderChangedListener(OnOrderChangedListener l) {
        this.onOrderChangedListener = l;
    }

    public void setItemTouchHelper(ItemTouchHelper ith) {
        this.itemTouchHelper = ith;
    }

    public void updateList(List<SubTask> newList) {
        subtasks.clear();
        if (newList != null) subtasks.addAll(newList);
        notifyDataSetChanged();
    }

    public void moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= getItemCount() || to >= getItemCount()) return;
        Collections.swap(subtasks, from, to);
        notifyItemMoved(from, to);
    }

    public void commitOrder() {
        if (onOrderChangedListener != null) {
            onOrderChangedListener.onSubtaskOrderChanged(new ArrayList<>(subtasks));
        }
    }

    @NonNull
    @Override
    public SubtaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subtask_edit, parent, false);
        return new SubtaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SubtaskViewHolder holder, int position) {
        SubTask sub = subtasks.get(position);

        // Title: read-only
        holder.tvSubTitle.setFocusable(false);
        holder.tvSubTitle.setClickable(false);
        holder.tvSubTitle.setText(sub.title);
        holder.btnDelete.setVisibility(View.GONE);

        holder.cb.setOnCheckedChangeListener(null);
        holder.cb.setChecked(sub.isCompleted);

        if (sub.isCompleted) {
            holder.tvSubTitle.setPaintFlags(holder.tvSubTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvSubTitle.setTextColor(Color.parseColor("#4B5563"));
        } else {
            holder.tvSubTitle.setPaintFlags(holder.tvSubTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvSubTitle.setTextColor(Color.parseColor("#F1F5F9"));
        }

        holder.cb.setOnCheckedChangeListener((btn, checked) -> {
            sub.isCompleted = checked;
            notifyItemChanged(holder.getAdapterPosition());
            if (onSubtaskCheckedListener != null) {
                onSubtaskCheckedListener.onSubtaskChecked(sub, checked);
            }
        });

        // Drag handle: long-press the item to initiate drag
        holder.itemView.setOnLongClickListener(v -> {
            if (itemTouchHelper != null) {
                itemTouchHelper.startDrag(holder);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return subtasks.size();
    }

    static class SubtaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox cb;
        TextView tvSubTitle;
        ImageView btnDelete;

        SubtaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cb = itemView.findViewById(R.id.cbSubtask);
            tvSubTitle = itemView.findViewById(R.id.etSubtaskTitle);
            btnDelete = itemView.findViewById(R.id.btnDeleteSubtask);
        }
    }
}
