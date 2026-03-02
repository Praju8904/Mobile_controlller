package com.prajwal.myfirstapp.hub;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File-system browser for internal and external (SD card) storage.
 * Shows folders and files in a navigable list, similar to Google Files.
 */
public class StorageFileBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_ROOT_PATH = "root_path";
    public static final String EXTRA_TITLE = "title";

    private HubFileRepository repo;
    private TextView tvCurrentPath;
    private ListView listView;
    private File currentDir;
    private File rootDir;
    private final List<File> currentItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = HubFileRepository.getInstance(this);

        String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (rootPath == null) rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        rootDir = new File(rootPath);

        buildUI(title != null ? title : "Storage");
        loadDirectory(rootDir);
    }

    private void buildUI(String title) {
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F0F14"));
        setContentView(root);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#1A1A2E"));
        topBar.setPadding((int) (4 * dp), 0, (int) (8 * dp), 0);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * dp)));
        root.addView(topBar);

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(22);
        btnBack.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        btnBack.setOnClickListener(v -> navigateUp());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(tvTitle);

        // Current path bar
        tvCurrentPath = new TextView(this);
        tvCurrentPath.setTextColor(Color.parseColor("#64748B"));
        tvCurrentPath.setTextSize(11);
        tvCurrentPath.setPadding((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp));
        tvCurrentPath.setBackgroundColor(Color.parseColor("#12121E"));
        root.addView(tvCurrentPath);

        listView = new ListView(this);
        listView.setBackgroundColor(Color.parseColor("#0F0F14"));
        listView.setDivider(null);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(listView);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File item = currentItems.get(position);
            if (item.isDirectory()) {
                loadDirectory(item);
            } else {
                showFileOptions(item);
            }
        });
    }

    private void loadDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "Directory not accessible", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dir.canRead()) {
            Toast.makeText(this, "Permission denied — cannot read this directory", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDir = dir;

        File[] files = dir.listFiles();
        currentItems.clear();
        if (files != null) {
            List<File> list = new ArrayList<>(Arrays.asList(files));
            Collections.sort(list, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : list) {
                if (!f.getName().startsWith(".")) {
                    currentItems.add(f);
                }
            }
        }

        tvCurrentPath.setText(dir.getAbsolutePath());
        listView.setAdapter(new FileListAdapter());
        listView.setSelection(0);
    }

    private void navigateUp() {
        if (currentDir != null && !currentDir.equals(rootDir)) {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.canRead()) {
                loadDirectory(parent);
                return;
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        navigateUp();
    }

    private void showFileOptions(File file) {
        String[] options = {"📂 Import to File Hub", "📱 Open with App"};
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) importFile(file);
                    else openFileWithApp(file);
                })
                .show();
    }

    private void importFile(File file) {
        String ext = file.getName().contains(".")
                ? file.getName().substring(file.getName().lastIndexOf('.') + 1) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        if (mime == null) mime = "application/octet-stream";

        HubFile hubFile = new HubFile();
        hubFile.originalFileName = file.getName();
        hubFile.displayName = file.getName();
        hubFile.filePath = file.getAbsolutePath();
        hubFile.fileSize = file.length();
        hubFile.mimeType = mime;
        hubFile.fileType = HubFile.fileTypeFromMime(mime, ext);
        hubFile.source = HubFile.Source.INTERNAL;
        hubFile.fileExtension = ext;

        repo.addFile(hubFile);
        Toast.makeText(this, "Imported: " + file.getName(), Toast.LENGTH_SHORT).show();
    }

    private void openFileWithApp(File file) {
        try {
            String ext = file.getName().contains(".")
                    ? file.getName().substring(file.getName().lastIndexOf('.') + 1) : "";
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            if (mime == null) mime = "*/*";

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    private class FileListAdapter extends BaseAdapter {
        @Override public int getCount() { return currentItems.size(); }
        @Override public Object getItem(int pos) { return currentItems.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float dp = getResources().getDisplayMetrics().density;

            LinearLayout row = new LinearLayout(StorageFileBrowserActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));

            File item = currentItems.get(position);

            TextView icon = new TextView(StorageFileBrowserActivity.this);
            icon.setText(item.isDirectory() ? "📁" : getFileEmoji(item.getName()));
            icon.setTextSize(24);
            icon.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) (40 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(icon);

            LinearLayout info = new LinearLayout(StorageFileBrowserActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(StorageFileBrowserActivity.this);
            name.setText(item.getName());
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);

            TextView meta = new TextView(StorageFileBrowserActivity.this);
            if (item.isDirectory()) {
                String[] children = item.list();
                int count = children != null ? children.length : 0;
                meta.setText(count + " item" + (count == 1 ? "" : "s"));
            } else {
                meta.setText(formatBytes(item.length()) + "  ·  "
                        + new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        .format(new Date(item.lastModified())));
            }
            meta.setTextColor(Color.parseColor("#64748B"));
            meta.setTextSize(11);
            info.addView(meta);

            row.addView(info);

            if (item.isDirectory()) {
                TextView arrow = new TextView(StorageFileBrowserActivity.this);
                arrow.setText("›");
                arrow.setTextColor(Color.parseColor("#64748B"));
                arrow.setTextSize(20);
                row.addView(arrow);
            }

            return row;
        }
    }

    private String getFileEmoji(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return "📕";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp")) return "🖼️";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov")) return "🎬";
        if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav")
                || lower.endsWith(".flac") || lower.endsWith(".ogg")) return "🎵";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")
                || lower.endsWith(".tar") || lower.endsWith(".gz")) return "🗜️";
        if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")
                || lower.endsWith(".rtf")) return "📝";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "📊";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📽️";
        if (lower.endsWith(".apk")) return "📦";
        return "📄";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
