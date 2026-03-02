package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for displaying vault files in a grid or list view.
 * Decrypts thumbnails asynchronously — never caches unencrypted data to disk.
 */
public class VaultFileGridAdapter extends BaseAdapter {

    private final Context context;
    private final MediaVaultRepository repo;
    private List<VaultFileItem> files;
    private boolean isMultiSelectMode = false;
    private final Set<String> selectedIds = new HashSet<>();
    private OnFileClickListener clickListener;

    public interface OnFileClickListener {
        void onFileClick(VaultFileItem file, int position);
        void onFileLongClick(VaultFileItem file, int position);
    }

    public VaultFileGridAdapter(Context context, MediaVaultRepository repo, List<VaultFileItem> files) {
        this.context = context;
        this.repo = repo;
        this.files = new ArrayList<>(files);
    }

    public void setClickListener(OnFileClickListener listener) {
        this.clickListener = listener;
    }

    public void updateFiles(List<VaultFileItem> newFiles) {
        this.files = new ArrayList<>(newFiles);
        notifyDataSetChanged();
    }

    public void setMultiSelectMode(boolean enabled) {
        this.isMultiSelectMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public void toggleSelection(String fileId) {
        if (selectedIds.contains(fileId)) selectedIds.remove(fileId);
        else selectedIds.add(fileId);
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (VaultFileItem f : files) selectedIds.add(f.id);
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    @Override
    public int getCount() { return files.size(); }

    @Override
    public VaultFileItem getItem(int position) { return files.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_vault_file_grid, parent, false);
            // Make square cells based on parent width
            int cellSize = parent.getMeasuredWidth() / 3 - 4;
            if (cellSize <= 0) cellSize = 120;
            ViewGroup.LayoutParams lp = convertView.getLayoutParams();
            if (lp != null) { lp.height = cellSize; convertView.setLayoutParams(lp); }

            holder = new ViewHolder();
            holder.ivThumbnail = convertView.findViewById(R.id.ivFileThumbnail);
            holder.tvTypeIcon = convertView.findViewById(R.id.tvFileTypeIcon);
            holder.tvDuration = convertView.findViewById(R.id.tvDuration);
            holder.tvPlayIcon = convertView.findViewById(R.id.tvPlayIcon);
            holder.tvFavourite = convertView.findViewById(R.id.tvFavourite);
            holder.selectionOverlay = convertView.findViewById(R.id.selectionOverlay);
            holder.progressBar = convertView.findViewById(R.id.thumbLoadingProgress);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        VaultFileItem file = files.get(position);

        // Reset state
        holder.ivThumbnail.setImageBitmap(null);
        holder.tvTypeIcon.setVisibility(View.GONE);
        holder.tvDuration.setVisibility(View.GONE);
        holder.tvPlayIcon.setVisibility(View.GONE);
        holder.tvFavourite.setVisibility(View.GONE);
        holder.progressBar.setVisibility(View.GONE);

        // Show favourite
        if (file.isFavourited) holder.tvFavourite.setVisibility(View.VISIBLE);

        // Show duration for video/audio
        if (file.duration > 0) {
            holder.tvDuration.setText(file.getFormattedDuration());
            holder.tvDuration.setVisibility(View.VISIBLE);
        }

        // Show play icon for video
        if (file.fileType == VaultFileItem.FileType.VIDEO) {
            holder.tvPlayIcon.setVisibility(View.VISIBLE);
        }

        // Show type icon for audio/document/other
        if (file.fileType == VaultFileItem.FileType.AUDIO ||
                file.fileType == VaultFileItem.FileType.DOCUMENT ||
                file.fileType == VaultFileItem.FileType.OTHER) {
            holder.tvTypeIcon.setText(getTypeEmoji(file.fileType));
            holder.tvTypeIcon.setVisibility(View.VISIBLE);
        }

        // Load thumbnail asynchronously
        if (file.thumbnailPath != null && !file.thumbnailPath.isEmpty()) {
            holder.progressBar.setVisibility(View.VISIBLE);
            convertView.setTag(R.id.ivFileThumbnail, file.id);
            final View taggedView = convertView;
            final String fileId = file.id;
            final ViewHolder finalHolder = holder;
            final VaultFileItem fileToLoad = file;
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            new Thread(() -> {
                Bitmap bitmap = repo.decryptThumbnail(fileToLoad);
                mainHandler.post(() -> {
                    finalHolder.progressBar.setVisibility(View.GONE);
                    Object currentTag = taggedView.getTag(R.id.ivFileThumbnail);
                    if (fileId.equals(currentTag) && bitmap != null) {
                        finalHolder.ivThumbnail.setImageBitmap(bitmap);
                        finalHolder.tvTypeIcon.setVisibility(View.GONE);
                    }
                });
            }).start();
        }

        // Multi-select state
        if (isMultiSelectMode && selectedIds.contains(file.id)) {
            holder.selectionOverlay.setVisibility(View.VISIBLE);
        } else {
            holder.selectionOverlay.setVisibility(View.GONE);
        }

        // Click handlers
        final int pos = position;
        convertView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onFileClick(file, pos);
        });
        convertView.setOnLongClickListener(v -> {
            if (clickListener != null) clickListener.onFileLongClick(file, pos);
            return true;
        });

        return convertView;
    }

    private String getTypeEmoji(VaultFileItem.FileType type) {
        switch (type) {
            case AUDIO: return "🎵";
            case DOCUMENT: return "📄";
            default: return "📎";
        }
    }

    static class ViewHolder {
        ImageView ivThumbnail;
        TextView tvTypeIcon, tvDuration, tvPlayIcon, tvFavourite;
        FrameLayout selectionOverlay;
        ProgressBar progressBar;
    }
}
