package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Album detail screen — shows all files in a specific vault album.
 */
public class VaultAlbumDetailActivity extends AppCompatActivity {

    private MediaVaultRepository repo;
    private VaultAlbum album;
    private VaultFileGridAdapter adapter;
    private List<VaultFileItem> albumFiles;

    private TextView tvAlbumName;
    private TextView tvAlbumFileCount;
    private GridView albumFileGrid;
    private LinearLayout albumEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_vault_album_detail);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        String albumId = getIntent().getStringExtra("album_id");
        if (albumId == null) { finish(); return; }

        // Find album
        for (VaultAlbum a : repo.getAlbums()) {
            if (a.id.equals(albumId)) { album = a; break; }
        }
        if (album == null) { finish(); return; }

        bindViews();
        loadAlbumFiles();
    }

    private void bindViews() {
        tvAlbumName = findViewById(R.id.tvAlbumName);
        tvAlbumFileCount = findViewById(R.id.tvAlbumFileCount);
        albumFileGrid = findViewById(R.id.albumFileGrid);
        albumEmptyState = findViewById(R.id.albumEmptyState);

        tvAlbumName.setText(album.name);

        findViewById(R.id.btnAlbumBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnAlbumMore).setOnClickListener(v -> showAlbumOptions());
        findViewById(R.id.btnAddFilesToAlbum).setOnClickListener(v -> showAddFilesDialog());
    }

    private void loadAlbumFiles() {
        albumFiles = repo.getFilesByAlbum(album.id);
        tvAlbumFileCount.setText(albumFiles.size() + " file" + (albumFiles.size() == 1 ? "" : "s"));
        albumEmptyState.setVisibility(albumFiles.isEmpty() ? View.VISIBLE : View.GONE);

        if (adapter == null) {
            adapter = new VaultFileGridAdapter(this, repo, albumFiles);
            adapter.setClickListener(new VaultFileGridAdapter.OnFileClickListener() {
                @Override
                public void onFileClick(VaultFileItem file, int position) {
                    openFileViewer(file);
                }
                @Override
                public void onFileLongClick(VaultFileItem file, int position) {
                    showFileOptions(file);
                }
            });
            albumFileGrid.setAdapter(adapter);
        } else {
            adapter.updateFiles(albumFiles);
        }
    }

    private void openFileViewer(VaultFileItem file) {
        repo.logFileViewed(file);
        Intent intent;
        switch (file.fileType) {
            case IMAGE:
                intent = new Intent(this, VaultImageViewerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", album.id);
                startActivity(intent);
                break;
            case VIDEO:
                intent = new Intent(this, VaultVideoPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", album.id);
                startActivity(intent);
                break;
            case AUDIO:
                intent = new Intent(this, VaultAudioPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", album.id);
                startActivity(intent);
                break;
            case DOCUMENT:
            default:
                intent = new Intent(this, VaultDocumentViewerActivity.class);
                intent.putExtra("file_id", file.id);
                startActivity(intent);
                break;
        }
    }

    private void showFileOptions(VaultFileItem file) {
        String[] options = {"▶ Open", "⭐ " + (file.isFavourited ? "Unfavourite" : "Favourite"),
                "📤 Export to Device", "❌ Remove from Album", "🗑️ Delete File"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle(file.originalFileName)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            openFileViewer(file);
                            break;
                        case 1:
                            file.isFavourited = !file.isFavourited;
                            repo.updateFile(file);
                            Toast.makeText(this, file.isFavourited ? "Favourited" : "Unfavourited", Toast.LENGTH_SHORT).show();
                            loadAlbumFiles();
                            break;
                        case 2:
                            exportFile(file);
                            break;
                        case 3:
                            file.albumId = null;
                            repo.updateFile(file);
                            loadAlbumFiles();
                            Toast.makeText(this, "Removed from album", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            confirmDelete(file);
                            break;
                    }
                })
                .show();
    }

    private void exportFile(VaultFileItem file) {
        new Thread(() -> {
            java.io.File dest = new java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS),
                    file.originalFileName);
            boolean success = repo.exportFile(file, dest);
            runOnUiThread(() -> Toast.makeText(VaultAlbumDetailActivity.this,
                    success ? "Exported to Downloads" : "Export failed", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void confirmDelete(VaultFileItem file) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete File")
                .setMessage("Permanently delete \"" + file.originalFileName + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(file);
                    loadAlbumFiles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAlbumOptions() {
        String[] options = {"✏️ Rename Album", "🎨 Change Color", "🗑️ Delete Album"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle(album.name)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: showRenameDialog(); break;
                        case 1: showColorPicker(); break;
                        case 2: confirmDeleteAlbum(); break;
                    }
                })
                .show();
    }

    private void showRenameDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(album.name);
        et.setTextColor(Color.parseColor("#F1F5F9"));
        et.setBackgroundColor(Color.parseColor("#1E293B"));
        et.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Rename Album")
                .setView(et)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        album.name = newName;
                        repo.updateAlbum(album);
                        tvAlbumName.setText(album.name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showColorPicker() {
        String[] colorNames = {"Blue", "Purple", "Green", "Amber", "Red", "Teal"};
        String[] colors = {"#3B82F6", "#8B5CF6", "#10B981", "#F59E0B", "#EF4444", "#14B8A6"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Album Color")
                .setItems(colorNames, (d, which) -> {
                    album.colorHex = colors[which];
                    repo.updateAlbum(album);
                })
                .show();
    }

    private void confirmDeleteAlbum() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete Album")
                .setMessage("Delete \"" + album.name + "\"?\n\nFiles will stay in your vault — only the album is removed.")
                .setPositiveButton("Delete Album", (d, w) -> {
                    repo.deleteAlbum(album.id);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddFilesDialog() {
        // Show all vault files not in this album
        List<VaultFileItem> allFiles = repo.getAllFiles();
        List<VaultFileItem> unassigned = new java.util.ArrayList<>();
        for (VaultFileItem f : allFiles) {
            if (!album.id.equals(f.albumId)) {
                unassigned.add(f);
            }
        }
        if (unassigned.isEmpty()) {
            Toast.makeText(this, "All vault files are already in this album", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[unassigned.size()];
        boolean[] checked = new boolean[unassigned.size()];
        for (int i = 0; i < unassigned.size(); i++) names[i] = unassigned.get(i).originalFileName;

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Add Files to Album")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Add Selected", (d, w) -> {
                    int added = 0;
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            repo.addFileToAlbum(unassigned.get(i).id, album.id);
                            added++;
                        }
                    }
                    Toast.makeText(this, added + " files added", Toast.LENGTH_SHORT).show();
                    loadAlbumFiles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
