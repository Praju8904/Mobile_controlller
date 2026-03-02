package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Vault File Browser — browse vault files by type with grid/list toggle,
 * sort, filter, and multi-select actions.
 */
public class VaultFileBrowserActivity extends AppCompatActivity {

    private MediaVaultRepository repo;
    private VaultFileItem.FileType currentType = null;
    private String sortBy = "date_added";
    private boolean isGridView = true;
    private boolean isMultiSelectMode = false;

    private VaultFileGridAdapter gridAdapter;
    private List<VaultFileItem> currentFiles = new ArrayList<>();

    // Views
    private TextView tvBrowserTitle;
    private TextView tvBrowserCount;
    private GridView fileGridView;
    private ListView fileListView;
    private LinearLayout browserEmptyState;
    private LinearLayout multiSelectTopBar;
    private LinearLayout multiSelectBottomBar;
    private TextView tvSelectedCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_vault_file_browser);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) {
            finish();
            return;
        }

        // Read intent params
        String typeStr = getIntent().getStringExtra("file_type");
        if (typeStr != null) {
            try { currentType = VaultFileItem.FileType.valueOf(typeStr); } catch (Exception ignored) {}
        }

        bindViews();
        loadFiles();
    }

    private void bindViews() {
        tvBrowserTitle = findViewById(R.id.tvBrowserTitle);
        tvBrowserCount = findViewById(R.id.tvBrowserCount);
        fileGridView = findViewById(R.id.fileGridView);
        fileListView = findViewById(R.id.fileListView);
        browserEmptyState = findViewById(R.id.browserEmptyState);
        multiSelectTopBar = findViewById(R.id.multiSelectTopBar);
        multiSelectBottomBar = findViewById(R.id.multiSelectBottomBar);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);

        // Title
        if (currentType != null) {
            tvBrowserTitle.setText(getTypeLabel(currentType));
        } else {
            tvBrowserTitle.setText("All Files");
        }

        // Toolbar buttons
        findViewById(R.id.btnBrowserBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnSort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btnViewToggle).setOnClickListener(v -> toggleViewMode());
        findViewById(R.id.btnSelect).setOnClickListener(v -> enterMultiSelectMode());

        // Multi-select top bar
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> {
            gridAdapter.selectAll();
            updateSelectionCount();
        });
        findViewById(R.id.btnExitSelect).setOnClickListener(v -> exitMultiSelectMode());

        // Multi-select bottom bar
        findViewById(R.id.btnActionAlbum).setOnClickListener(v -> addSelectedToAlbum());
        findViewById(R.id.btnActionFavourite).setOnClickListener(v -> toggleSelectedFavourites());
        findViewById(R.id.btnActionExport).setOnClickListener(v -> exportSelected());
        findViewById(R.id.btnActionShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.btnActionDelete).setOnClickListener(v -> deleteSelected());
    }

    private void loadFiles() {
        if (currentType != null) {
            currentFiles = repo.getFilesByType(currentType);
        } else {
            currentFiles = repo.getAllFiles();
        }
        currentFiles = repo.sortFiles(currentFiles, sortBy);

        tvBrowserCount.setText(currentFiles.size() + " item" + (currentFiles.size() == 1 ? "" : "s"));
        browserEmptyState.setVisibility(currentFiles.isEmpty() ? View.VISIBLE : View.GONE);

        if (gridAdapter == null) {
            gridAdapter = new VaultFileGridAdapter(this, repo, currentFiles);
            gridAdapter.setClickListener(new VaultFileGridAdapter.OnFileClickListener() {
                @Override
                public void onFileClick(VaultFileItem file, int position) {
                    if (gridAdapter.isMultiSelectMode()) {
                        gridAdapter.toggleSelection(file.id);
                        updateSelectionCount();
                    } else {
                        openFileViewer(file);
                    }
                }

                @Override
                public void onFileLongClick(VaultFileItem file, int position) {
                    if (!gridAdapter.isMultiSelectMode()) {
                        enterMultiSelectMode();
                    }
                    gridAdapter.toggleSelection(file.id);
                    updateSelectionCount();
                }
            });
            fileGridView.setAdapter(gridAdapter);
        } else {
            gridAdapter.updateFiles(currentFiles);
        }
    }

    private void toggleViewMode() {
        isGridView = !isGridView;
        fileGridView.setVisibility(isGridView ? View.VISIBLE : View.GONE);
        fileListView.setVisibility(isGridView ? View.GONE : View.VISIBLE);
        // Simple list view shows file names
        if (!isGridView) {
            android.widget.ArrayAdapter<String> listAdapter = new android.widget.ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1);
            for (VaultFileItem f : currentFiles) {
                listAdapter.add(f.originalFileName + "  (" + f.getFormattedSize() + ")");
            }
            fileListView.setAdapter(listAdapter);
            fileListView.setOnItemClickListener((parent, view, pos, id) -> {
                if (pos < currentFiles.size()) {
                    VaultFileItem file = currentFiles.get(pos);
                    if (isMultiSelectMode) {
                        gridAdapter.toggleSelection(file.id);
                        updateSelectionCount();
                    } else {
                        repo.logFileViewed(file);
                        showFilePreviewOptions(file);
                    }
                }
            });
        }
    }

    private void enterMultiSelectMode() {
        isMultiSelectMode = true;
        gridAdapter.setMultiSelectMode(true);
        multiSelectTopBar.setVisibility(View.VISIBLE);
        multiSelectBottomBar.setVisibility(View.VISIBLE);
        updateSelectionCount();
    }

    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        gridAdapter.setMultiSelectMode(false);
        multiSelectTopBar.setVisibility(View.GONE);
        multiSelectBottomBar.setVisibility(View.GONE);
    }

    private void updateSelectionCount() {
        int count = gridAdapter.getSelectedCount();
        tvSelectedCount.setText(count + " selected");
    }

    private void showSortDialog() {
        String[] options = {"Date Added (newest first)", "Date Taken", "Name A–Z", "File Size", "Duration"};
        String[] keys = {"date_added", "date_taken", "name", "size", "duration"};
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Sort By")
                .setItems(options, (d, which) -> {
                    sortBy = keys[which];
                    loadFiles();
                })
                .show();
    }

    private void openFileViewer(VaultFileItem file) {
        repo.logFileViewed(file);
        String listType = currentType != null ? currentType.name() : "all";
        Intent intent;
        switch (file.fileType) {
            case IMAGE:
                intent = new Intent(this, VaultImageViewerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", listType);
                startActivity(intent);
                break;
            case VIDEO:
                intent = new Intent(this, VaultVideoPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", listType);
                startActivity(intent);
                break;
            case AUDIO:
                intent = new Intent(this, VaultAudioPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", listType);
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

    private void showFilePreviewOptions(VaultFileItem file) {
        String[] options = {"▶ Open", "⭐ " + (file.isFavourited ? "Unfavourite" : "Favourite"),
                "📁 Add to Album", "📤 Export to Device", "ℹ️ File Info", "🗑️ Delete"};
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
                            Toast.makeText(this, file.isFavourited ? "Added to favourites" : "Removed from favourites", Toast.LENGTH_SHORT).show();
                            loadFiles();
                            break;
                        case 2:
                            addSingleToAlbum(file);
                            break;
                        case 3:
                            exportSingleFile(file);
                            break;
                        case 4:
                            showFileInfo(file);
                            break;
                        case 5:
                            confirmDeleteSingle(file);
                            break;
                    }
                })
                .show();
    }

    private void showFileInfo(VaultFileItem file) {
        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(file.originalFileName).append("\n");
        info.append("Type: ").append(file.fileType).append("\n");
        info.append("Size: ").append(file.getFormattedSize()).append("\n");
        if (file.duration > 0) info.append("Duration: ").append(file.getFormattedDuration()).append("\n");
        if (file.width > 0) info.append("Dimensions: ").append(file.width).append("×").append(file.height).append("\n");
        info.append("Imported: ").append(new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(file.importedAt)));

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("File Info")
                .setMessage(info.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void addSingleToAlbum(VaultFileItem file) {
        List<VaultAlbum> albums = repo.getAlbums();
        if (albums.isEmpty()) {
            Toast.makeText(this, "No albums yet — create one from vault home", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) names[i] = albums.get(i).name;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Add to Album")
                .setItems(names, (d, which) -> {
                    repo.addFileToAlbum(file.id, albums.get(which).id);
                    Toast.makeText(this, "Added to " + albums.get(which).name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void exportSingleFile(VaultFileItem file) {
        new Thread(() -> {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File dest = new File(downloads, file.originalFileName);
            boolean success = repo.exportFile(file, dest);
            runOnUiThread(() -> Toast.makeText(VaultFileBrowserActivity.this,
                    success ? "Exported to Downloads folder" : "Export failed",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void confirmDeleteSingle(VaultFileItem file) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete File")
                .setMessage("Permanently delete \"" + file.originalFileName + "\"?\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(file);
                    loadFiles();
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Multi-select Actions ─────────────────────────────────────

    private void addSelectedToAlbum() {
        Set<String> ids = gridAdapter.getSelectedIds();
        if (ids.isEmpty()) { Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show(); return; }
        List<VaultAlbum> albums = repo.getAlbums();
        if (albums.isEmpty()) { Toast.makeText(this, "No albums — create one first", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) names[i] = albums.get(i).name;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Add to Album")
                .setItems(names, (d, which) -> {
                    for (String id : ids) repo.addFileToAlbum(id, albums.get(which).id);
                    Toast.makeText(this, ids.size() + " files added to " + albums.get(which).name, Toast.LENGTH_SHORT).show();
                    exitMultiSelectMode();
                })
                .show();
    }

    private void toggleSelectedFavourites() {
        Set<String> ids = gridAdapter.getSelectedIds();
        for (VaultFileItem f : currentFiles) {
            if (ids.contains(f.id)) {
                f.isFavourited = !f.isFavourited;
                repo.updateFile(f);
            }
        }
        Toast.makeText(this, "Updated " + ids.size() + " files", Toast.LENGTH_SHORT).show();
        exitMultiSelectMode();
        loadFiles();
    }

    private void exportSelected() {
        Set<String> ids = gridAdapter.getSelectedIds();
        if (ids.isEmpty()) return;
        new Thread(() -> {
            int count = 0;
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            for (VaultFileItem f : currentFiles) {
                if (ids.contains(f.id)) {
                    File dest = new File(downloads, f.originalFileName);
                    if (repo.exportFile(f, dest)) count++;
                }
            }
            final int finalCount = count;
            runOnUiThread(() -> {
                Toast.makeText(VaultFileBrowserActivity.this, finalCount + " files exported to Downloads", Toast.LENGTH_SHORT).show();
                exitMultiSelectMode();
            });
        }).start();
    }

    private void shareSelected() {
        Set<String> ids = gridAdapter.getSelectedIds();
        if (ids.isEmpty()) return;
        Toast.makeText(this, "Decrypting files for sharing...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            List<Uri> uris = new ArrayList<>();
            File cache = getCacheDir();
            for (VaultFileItem f : currentFiles) {
                if (ids.contains(f.id)) {
                    File tmp = new File(cache, f.originalFileName);
                    if (repo.exportFile(f, tmp)) {
                        uris.add(androidx.core.content.FileProvider.getUriForFile(
                                VaultFileBrowserActivity.this,
                                getPackageName() + ".provider", tmp));
                    }
                }
            }
            runOnUiThread(() -> {
                if (uris.isEmpty()) return;
                Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.setType("*/*");
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Files"));
                exitMultiSelectMode();
            });
        }).start();
    }

    private void deleteSelected() {
        Set<String> ids = gridAdapter.getSelectedIds();
        if (ids.isEmpty()) return;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete Files")
                .setMessage("Permanently delete " + ids.size() + " file" + (ids.size() == 1 ? "" : "s") + "?\nThis cannot be undone.")
                .setPositiveButton("Delete All", (d, w) -> {
                    List<VaultFileItem> toDelete = new ArrayList<>();
                    for (VaultFileItem f : currentFiles) if (ids.contains(f.id)) toDelete.add(f);
                    for (VaultFileItem f : toDelete) repo.deleteFile(f);
                    exitMultiSelectMode();
                    loadFiles();
                    Toast.makeText(this, ids.size() + " files deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getTypeLabel(VaultFileItem.FileType type) {
        switch (type) {
            case IMAGE: return "Photos";
            case VIDEO: return "Videos";
            case AUDIO: return "Audio";
            case DOCUMENT: return "Documents";
            default: return "Files";
        }
    }
}
