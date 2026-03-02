package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView Adapter for notes displayed in a staggered masonry grid.
 * Supports multi-select mode, locked notes overlay, and variable card heights.
 */
public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteActionListener {
        void onNoteClick(Note note);
        void onNoteLongClick(Note note);
        void onNoteSelectionChanged(String noteId, boolean selected);
    }

    private final Context context;
    private final List<Note> notes;
    private final OnNoteActionListener listener;
    private final boolean isGridView;
    private NoteFolderRepository folderRepository;

    private boolean isMultiSelectMode = false;
    private Set<String> selectedIds = new HashSet<>();

    // Mode flags for archive/trash screens
    private boolean isArchiveMode = false;
    private boolean isTrashMode = false;

    public NotesAdapter(Context context, List<Note> notes, OnNoteActionListener listener, boolean isGridView) {
        this.context = context;
        this.notes = notes;
        this.listener = listener;
        this.isGridView = isGridView;
        try {
            this.folderRepository = new NoteFolderRepository(context, new NoteRepository(context));
        } catch (Exception e) {
            this.folderRepository = null;
        }
    }

    // ─── Mode Setters ────────────────────────────────────────────

    public void setArchiveMode(boolean archiveMode) {
        this.isArchiveMode = archiveMode;
    }

    public void setTrashMode(boolean trashMode) {
        this.isTrashMode = trashMode;
    }

    public void setMultiSelectMode(boolean multiSelect) {
        this.isMultiSelectMode = multiSelect;
        notifyDataSetChanged();
    }

    public void toggleSelection(String noteId) {
        if (selectedIds.contains(noteId)) {
            selectedIds.remove(noteId);
        } else {
            selectedIds.add(noteId);
        }
        notifyDataSetChanged();
    }

    public void clearSelections() {
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    // ─── ViewHolder ──────────────────────────────────────────────

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        LinearLayout cardRoot;
        View leftAccent;
        TextView tvNoteTitle, tvNotePreview, tvNoteDate, tvNoteCategory;
        TextView tvPinIcon, tvReminderIcon;
        TextView tvNoteFolderChip;
        LinearLayout tagsContainer;
        CheckBox cbSelect;
        FrameLayout lockOverlay;

        NoteViewHolder(View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            leftAccent = itemView.findViewById(R.id.leftAccent);
            tvNoteTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvNotePreview = itemView.findViewById(R.id.tvNotePreview);
            tvNoteDate = itemView.findViewById(R.id.tvNoteDate);
            tvNoteCategory = itemView.findViewById(R.id.tvNoteCategory);
            tvPinIcon = itemView.findViewById(R.id.tvPinIcon);
            tvReminderIcon = itemView.findViewById(R.id.tvReminderIcon);
            tvNoteFolderChip = itemView.findViewById(R.id.tvNoteFolderChip);
            tagsContainer = itemView.findViewById(R.id.tagsContainer);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            lockOverlay = itemView.findViewById(R.id.lockOverlay);
        }
    }

    // ─── Adapter Methods ─────────────────────────────────────────

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note_card, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);

        // Title
        holder.tvNoteTitle.setText(note.title.isEmpty() ? "Untitled" : note.title);

        // Preview - vary line count for staggered effect
        String preview = note.plainTextPreview;
        if (preview.isEmpty()) {
            preview = "No content";
            holder.tvNotePreview.setTextColor(0xFF64748B);
        } else {
            holder.tvNotePreview.setTextColor(0xFF94A3B8);
        }
        holder.tvNotePreview.setText(preview);

        // Dynamic max lines based on content length for staggered effect
        if (isGridView) {
            int maxLines = calculateMaxLines(preview.length());
            holder.tvNotePreview.setMaxLines(maxLines);
        } else {
            holder.tvNotePreview.setMaxLines(2);
        }

        // Date
        if (isTrashMode && note.deletedAt > 0) {
            // Show days until permanent deletion
            long daysLeft = 30 - ((System.currentTimeMillis() - note.deletedAt) / (24 * 60 * 60 * 1000));
            holder.tvNoteDate.setText(daysLeft + "d until deletion");
            holder.tvNoteDate.setTextColor(0xFFEF4444);
        } else {
            holder.tvNoteDate.setText(note.getFormattedDate());
            holder.tvNoteDate.setTextColor(0xFF475569);
        }

        // Category badge with color
        if (holder.tvNoteCategory != null) {
            if (note.category != null && !note.category.isEmpty()) {
                holder.tvNoteCategory.setText(note.category);
                holder.tvNoteCategory.setVisibility(View.VISIBLE);
                // Apply category color as pill background
                int catColor = Note.getCategoryColor(note.category);
                float density = context.getResources().getDisplayMetrics().density;
                GradientDrawable catBg = new GradientDrawable();
                catBg.setCornerRadius(12 * density);
                catBg.setColor(blendColors(0xFF0F172A, catColor, 0.3f));
                catBg.setStroke(1, blendColors(0xFF0F172A, catColor, 0.5f));
                holder.tvNoteCategory.setBackground(catBg);
                holder.tvNoteCategory.setTextColor(catColor);
            } else {
                holder.tvNoteCategory.setVisibility(View.GONE);
            }
        }

        // Folder chip
        if (holder.tvNoteFolderChip != null) {
            if (note.folderId != null && folderRepository != null) {
                NoteFolder folder = folderRepository.getFolderById(note.folderId);
                if (folder != null) {
                    holder.tvNoteFolderChip.setText(folder.getIconEmoji() + " " + folder.name);
                    holder.tvNoteFolderChip.setVisibility(View.VISIBLE);
                } else {
                    holder.tvNoteFolderChip.setVisibility(View.GONE);
                }
            } else {
                holder.tvNoteFolderChip.setVisibility(View.GONE);
            }
        }

        // Left accent color
        int accentColor = getAccentColor(note);
        holder.leftAccent.setBackgroundColor(accentColor);

        // Card background tint
        applyCardColorTint(holder.cardRoot, note.colorHex);

        // Pin icon
        holder.tvPinIcon.setVisibility(note.isPinned && !isArchiveMode && !isTrashMode ? 
            View.VISIBLE : View.GONE);

        // Reminder icon
        holder.tvReminderIcon.setVisibility(note.reminderDateTime > 0 ? View.VISIBLE : View.GONE);

        // Tags
        setupTags(holder.tagsContainer, note.tags);

        // Multi-select checkbox
        if (isMultiSelectMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(selectedIds.contains(note.id));
            holder.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedIds.add(note.id);
                } else {
                    selectedIds.remove(note.id);
                }
                if (listener != null) {
                    listener.onNoteSelectionChanged(note.id, checked);
                }
            });
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            holder.cbSelect.setOnCheckedChangeListener(null);
        }

        // Locked overlay
        if (note.isLocked && !isTrashMode) {
            holder.lockOverlay.setVisibility(View.VISIBLE);
            // Hide content preview when locked
            holder.tvNotePreview.setText("••••••••••••••");
            holder.tvNotePreview.setTextColor(0xFF475569);
            holder.tagsContainer.setVisibility(View.GONE);
        } else {
            holder.lockOverlay.setVisibility(View.GONE);
        }

        // Selected state background
        if (selectedIds.contains(note.id)) {
            holder.cardRoot.setBackgroundResource(R.drawable.notes_card_selected_bg);
        } else {
            holder.cardRoot.setBackgroundResource(R.drawable.notes_card_bg);
            applyCardColorTint(holder.cardRoot, note.colorHex);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNoteClick(note);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onNoteLongClick(note);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    // ─── Helper Methods ──────────────────────────────────────────

    private int calculateMaxLines(int contentLength) {
        // Vary card height based on content for masonry effect
        if (contentLength < 50) return 2;
        if (contentLength < 100) return 3;
        if (contentLength < 150) return 4;
        return 5;
    }

    private int getAccentColor(Note note) {
        // Use note color if custom, otherwise use category color
        if (!note.colorHex.equals(Note.NOTE_COLORS[0])) {
            return Note.parseColorSafe(note.colorHex);
        }
        return Note.getCategoryColor(note.category);
    }

    private void applyCardColorTint(View cardRoot, String colorHex) {
        if (colorHex.equals(Note.NOTE_COLORS[0])) {
            // Default - no tint needed
            return;
        }

        try {
            int color = Color.parseColor(colorHex);
            // Apply a subtle tint to the background
            if (cardRoot.getBackground() instanceof GradientDrawable) {
                GradientDrawable bg = (GradientDrawable) cardRoot.getBackground().mutate();
                // Mix color with dark background
                int tintedColor = blendColors(0xFF0F172A, color, 0.15f);
                bg.setColor(tintedColor);
            }
        } catch (Exception e) {
            // Ignore invalid colors
        }
    }

    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        int r = (int) ((Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio));
        int g = (int) ((Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio));
        int b = (int) ((Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio));
        return Color.rgb(r, g, b);
    }

    private void setupTags(LinearLayout container, List<String> tags) {
        container.removeAllViews();
        
        if (tags == null || tags.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);

        // Show max 3 tags to avoid overflow
        int maxTags = Math.min(tags.size(), 3);
        for (int i = 0; i < maxTags; i++) {
            TextView tagChip = createTagChip(tags.get(i));
            container.addView(tagChip);
        }

        // Show +N if more tags
        if (tags.size() > 3) {
            TextView moreChip = createTagChip("+" + (tags.size() - 3));
            moreChip.setTextColor(0xFF64748B);
            container.addView(moreChip);
        }
    }

    private TextView createTagChip(String tag) {
        TextView chip = new TextView(context);
        chip.setText(tag);
        chip.setTextSize(10);
        chip.setTextColor(0xFF94A3B8);
        chip.setBackgroundResource(R.drawable.notes_tag_chip_bg);
        chip.setPadding(16, 6, 16, 6);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(8);
        chip.setLayoutParams(params);

        return chip;
    }
}