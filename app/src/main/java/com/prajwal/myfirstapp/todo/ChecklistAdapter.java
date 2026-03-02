package com.prajwal.myfirstapp.todo;


import com.prajwal.myfirstapp.R;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RecyclerView adapter for interactive checklist items in NoteEditorActivity.
 *
 * Each row has a CheckBox and EditText. Checking an item animates the checkmark,
 * applies strikethrough to the text, and dims the row. An "Add item..." placeholder
 * row is shown at the bottom (represented by an item with empty text and id==null).
 */
public class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.ChecklistViewHolder> {

    // ─── Inner model ────────────────────────────────────────────────────────────

    public static class ChecklistItem {
        public String id;
        public String text;
        public boolean checked;

        public ChecklistItem(String text, boolean checked) {
            this.id = UUID.randomUUID().toString();
            this.text = text;
            this.checked = checked;
        }

        /** Placeholder row sentinel — id is null, text is empty */
        public static ChecklistItem placeholder() {
            ChecklistItem item = new ChecklistItem("", false);
            item.id = null; // sentinel
            return item;
        }

        public boolean isPlaceholder() {
            return id == null;
        }
    }

    // ─── Adapter state ──────────────────────────────────────────────────────────

    private final List<ChecklistItem> items = new ArrayList<>();

    public ChecklistAdapter() {
        items.add(ChecklistItem.placeholder());
    }

    // ─── Public API ─────────────────────────────────────────────────────────────

    /** Add a new empty item before the placeholder row. */
    public void addItem() {
        int insertPos = Math.max(0, items.size() - 1);
        items.add(insertPos, new ChecklistItem("", false));
        notifyItemInserted(insertPos);
    }

    /** Add a new item with the given text before the placeholder row. */
    public void addItem(String text, boolean checked) {
        int insertPos = Math.max(0, items.size() - 1);
        items.add(insertPos, new ChecklistItem(text, checked));
        notifyItemInserted(insertPos);
    }

    /**
     * Serialize items to multi-line text format:
     *   [x] completed item
     *   [ ] pending item
     * The placeholder row is excluded.
     */
    public String getChecklistAsText() {
        StringBuilder sb = new StringBuilder();
        for (ChecklistItem item : items) {
            if (item.isPlaceholder()) continue;
            sb.append(item.checked ? "[x] " : "[ ] ");
            sb.append(item.text);
            sb.append("\n");
        }
        // Trim trailing newline
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Parse lines of the form "[ ] text" or "[x] text" into ChecklistItem list.
     * Lines that don't match either prefix are skipped.
     */
    public static List<ChecklistItem> parseFromText(String body) {
        List<ChecklistItem> result = new ArrayList<>();
        if (body == null || body.isEmpty()) return result;

        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.startsWith("[ ] ")) {
                result.add(new ChecklistItem(line.substring(4), false));
            } else if (line.startsWith("[x] ")) {
                result.add(new ChecklistItem(line.substring(4), true));
            }
        }
        return result;
    }

    /**
     * Replace current items with the given list and append the placeholder row.
     */
    public void setItems(List<ChecklistItem> newItems) {
        items.clear();
        items.addAll(newItems);
        items.add(ChecklistItem.placeholder());
        notifyDataSetChanged();
    }

    // ─── RecyclerView.Adapter ───────────────────────────────────────────────────

    @NonNull
    @Override
    public ChecklistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checklist_row, parent, false);
        return new ChecklistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChecklistViewHolder holder, int position) {
        ChecklistItem item = items.get(position);

        // Detach watcher before setting text to avoid feedback loops
        if (holder.textWatcher != null) {
            holder.etText.removeTextChangedListener(holder.textWatcher);
        }

        if (item.isPlaceholder()) {
            holder.cbCheck.setVisibility(View.INVISIBLE);
            holder.etText.setText("");
            holder.etText.setHint("Add item...");
            holder.etText.setAlpha(1f);
            holder.etText.setPaintFlags(
                    holder.etText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.etText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // Post to avoid calling notify during RecyclerView layout pass
                    v.post(() -> {
                        int insertPos = Math.max(0, items.size() - 1);
                        ChecklistItem newItem = new ChecklistItem("", false);
                        items.add(insertPos, newItem);
                        notifyItemInserted(insertPos);
                        if (insertPos + 1 < items.size()) {
                            notifyItemChanged(insertPos + 1);
                        }
                    });
                }
            });
        } else {
            holder.cbCheck.setVisibility(View.VISIBLE);
            holder.etText.setHint("");
            holder.etText.setOnFocusChangeListener(null);

            // Checkbox state
            holder.cbCheck.setOnCheckedChangeListener(null);
            holder.cbCheck.setChecked(item.checked);
            applyCheckedStyle(holder, item.checked);

            holder.cbCheck.setOnCheckedChangeListener((btn, checked) -> {
                item.checked = checked;
                // Animate the style change
                holder.etText.animate()
                        .alpha(checked ? 0.5f : 1f)
                        .setDuration(150)
                        .start();
                applyCheckedStyle(holder, checked);
            });

            holder.etText.setText(item.text);

            // Attach a fresh text watcher to keep item.text in sync
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    item.text = s.toString();
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            holder.etText.addTextChangedListener(holder.textWatcher);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void applyCheckedStyle(ChecklistViewHolder holder, boolean checked) {
        if (checked) {
            holder.etText.setPaintFlags(
                    holder.etText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.etText.setAlpha(0.5f);
        } else {
            holder.etText.setPaintFlags(
                    holder.etText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.etText.setAlpha(1f);
        }
    }

    // ─── ViewHolder ─────────────────────────────────────────────────────────────

    static class ChecklistViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbCheck;
        EditText etText;
        TextWatcher textWatcher;

        ChecklistViewHolder(View itemView) {
            super(itemView);
            cbCheck = itemView.findViewById(R.id.cbChecklistItem);
            etText  = itemView.findViewById(R.id.etChecklistItemText);
        }
    }
}
