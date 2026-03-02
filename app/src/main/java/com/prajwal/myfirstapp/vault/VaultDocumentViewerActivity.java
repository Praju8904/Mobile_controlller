package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Secure document viewer. Shows text files inline, opens others via external app.
 */
public class VaultDocumentViewerActivity extends Activity {

    private static final String EXTRA_FILE_ID = "file_id";

    private MediaVaultRepository repo;
    private VaultFileItem currentFile;

    private TextView tvFileName, btnBack, btnMore;
    private TextView tvFileInfo, tvTextContent, tvDocType, btnOpenExternal;
    private ScrollView scrollViewText;
    private LinearLayout layoutOpenExternal;

    private File tempFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        setContentView(R.layout.activity_vault_document_viewer);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        tvFileName = findViewById(R.id.tvFileName);
        btnBack = findViewById(R.id.btnBack);
        btnMore = findViewById(R.id.btnMore);
        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvTextContent = findViewById(R.id.tvTextContent);
        tvDocType = findViewById(R.id.tvDocType);
        btnOpenExternal = findViewById(R.id.btnOpenExternal);
        scrollViewText = findViewById(R.id.scrollViewText);
        layoutOpenExternal = findViewById(R.id.layoutOpenExternal);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        if (fileId == null) { finish(); return; }

        currentFile = findFileById(fileId);
        if (currentFile == null) { finish(); return; }

        tvFileName.setText(currentFile.originalFileName);
        String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(new Date(currentFile.importedAt));
        tvFileInfo.setText(currentFile.originalFileName + " · " + currentFile.getFormattedSize() + " · " + date);
        repo.logFileViewed(currentFile);

        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> showMoreMenu());
        btnOpenExternal.setOnClickListener(v -> openWithExternalApp());

        loadDocument();
    }

    private VaultFileItem findFileById(String id) {
        List<VaultFileItem> all = repo.getAllFiles();
        for (VaultFileItem f : all) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }

    private void loadDocument() {
        if (isTextFile()) {
            new Thread(() -> {
                try {
                    tempFile = File.createTempFile("vault_doc_", ".tmp", getCacheDir());
                    boolean ok = repo.exportFile(currentFile, tempFile);
                    if (!ok) throw new Exception("export failed");
                    byte[] bytes = readFile(tempFile);
                    final String text = new String(bytes, StandardCharsets.UTF_8);
                    runOnUiThread(() -> {
                        scrollViewText.setVisibility(View.VISIBLE);
                        layoutOpenExternal.setVisibility(View.GONE);
                        tvTextContent.setText(text);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to load document", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } else {
            scrollViewText.setVisibility(View.GONE);
            layoutOpenExternal.setVisibility(View.VISIBLE);
            String ext = getExtension(currentFile.originalFileName).toUpperCase(Locale.ROOT);
            tvDocType.setText(ext.isEmpty() ? "Document" : ext + " File");
        }
    }

    private boolean isTextFile() {
        String mime = currentFile.mimeType;
        if (mime != null && mime.startsWith("text/")) return true;
        String ext = getExtension(currentFile.originalFileName).toLowerCase(Locale.ROOT);
        return ext.equals("txt") || ext.equals("md") || ext.equals("log") || ext.equals("csv");
    }

    private String getExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }

    private byte[] readFile(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        return data;
    }

    private void openWithExternalApp() {
        new Thread(() -> {
            try {
                String originalName = currentFile.originalFileName;
                tempFile = File.createTempFile("vault_open_", "_" + originalName, getCacheDir());
                boolean ok = repo.exportFile(currentFile, tempFile);
                if (!ok) throw new Exception("export failed");
                runOnUiThread(() -> {
                    try {
                        String mime = currentFile.mimeType != null ? currentFile.mimeType : "*/*";
                        android.net.Uri uri = FileProvider.getUriForFile(
                                this, getPackageName() + ".provider", tempFile);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, mime);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception ex) {
                        Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
                        deleteTempFile();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showMoreMenu() {
        String favLabel = currentFile.isFavourited ? "Remove from Favourites" : "Add to Favourites";
        String[] items = {favLabel, "Export to Downloads", "Delete"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: toggleFavourite(); break;
                        case 1: exportToDownloads(); break;
                        case 2: confirmDelete(); break;
                    }
                }).show();
    }

    private void toggleFavourite() {
        currentFile.isFavourited = !currentFile.isFavourited;
        repo.updateFile(currentFile);
        Toast.makeText(this, currentFile.isFavourited ? "Added to favourites" : "Removed from favourites",
                Toast.LENGTH_SHORT).show();
    }

    private void exportToDownloads() {
        new Thread(() -> {
            try {
                File dest = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), currentFile.originalFileName);
                boolean ok = repo.exportFile(currentFile, dest);
                runOnUiThread(() -> Toast.makeText(this,
                        ok ? "Exported to Downloads" : "Export failed", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete \"" + currentFile.originalFileName + "\" permanently?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(currentFile);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void deleteTempFile() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
            tempFile = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteTempFile();
    }
}
