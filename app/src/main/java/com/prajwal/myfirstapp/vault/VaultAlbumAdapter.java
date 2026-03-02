package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying vault albums in a horizontal strip.
 */
public class VaultAlbumAdapter extends android.widget.BaseAdapter {

    private final Context context;
    private final MediaVaultRepository repo;
    private List<VaultAlbum> albums;
    private boolean showAddButton = true;
    private OnAlbumClickListener listener;

    public interface OnAlbumClickListener {
        void onAlbumClick(VaultAlbum album);
        void onAddAlbumClick();
    }

    public VaultAlbumAdapter(Context context, MediaVaultRepository repo, List<VaultAlbum> albums) {
        this.context = context;
        this.repo = repo;
        this.albums = new ArrayList<>(albums);
    }

    public void setClickListener(OnAlbumClickListener listener) {
        this.listener = listener;
    }

    public void updateAlbums(List<VaultAlbum> newAlbums) {
        this.albums = new ArrayList<>(newAlbums);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return albums.size() + (showAddButton ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        if (position < albums.size()) return albums.get(position);
        return null;
    }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (showAddButton && position == albums.size()) {
            // "+" add album card
            View addView = LayoutInflater.from(context).inflate(R.layout.item_vault_album, parent, false);
            TextView icon = addView.findViewById(R.id.tvAlbumIcon);
            TextView name = addView.findViewById(R.id.tvAlbumName);
            TextView count = addView.findViewById(R.id.tvAlbumCount);
            icon.setText("➕");
            name.setText("New Album");
            count.setVisibility(View.GONE);
            addView.setOnClickListener(v -> { if (listener != null) listener.onAddAlbumClick(); });
            return addView;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.item_vault_album, parent, false);
        VaultAlbum album = albums.get(position);

        ImageView ivCover = view.findViewById(R.id.ivAlbumCover);
        FrameLayout colorBox = view.findViewById(R.id.albumColorBox);
        TextView tvIcon = view.findViewById(R.id.tvAlbumIcon);
        TextView tvName = view.findViewById(R.id.tvAlbumName);
        TextView tvCount = view.findViewById(R.id.tvAlbumCount);

        tvName.setText(album.name);
        tvCount.setText(album.fileCount + " files");

        // Set color background
        try {
            colorBox.setBackgroundColor(Color.parseColor(album.colorHex));
        } catch (Exception ignored) {}

        // Load cover thumbnail
        if (album.coverFileId != null && !album.coverFileId.isEmpty()) {
            VaultFileItem coverFile = getCoverFile(album.coverFileId);
            if (coverFile != null && coverFile.thumbnailPath != null && !coverFile.thumbnailPath.isEmpty()) {
                colorBox.setVisibility(View.GONE);
                ivCover.setVisibility(View.VISIBLE);
                final VaultFileItem finalCover = coverFile;
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                new Thread(() -> {
                    Bitmap bitmap = repo.decryptThumbnail(finalCover);
                    mainHandler.post(() -> { if (bitmap != null) ivCover.setImageBitmap(bitmap); });
                }).start();
            }
        } else {
            ivCover.setVisibility(View.GONE);
            colorBox.setVisibility(View.VISIBLE);
        }

        view.setOnClickListener(v -> { if (listener != null) listener.onAlbumClick(album); });
        return view;
    }

    private VaultFileItem getCoverFile(String fileId) {
        for (VaultFileItem f : repo.getAllFiles()) {
            if (f.id.equals(fileId)) return f;
        }
        return null;
    }
}
