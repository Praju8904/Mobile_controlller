package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Secure search screen with filter chips and real-time results.
 */
public class VaultSearchActivity extends Activity {

    private static final String PREFS_SEARCH = "vault_search_prefs";
    private static final String PREF_RECENT = "recent_searches";
    private static final int MAX_RECENT = 5;

    private MediaVaultRepository repo;

    private EditText etSearch;
    private TextView btnBack;
    private TextView chipAll, chipImages, chipVideos, chipAudio, chipDocs, chipFavourites;
    private LinearLayout resultsContainer;
    private TextView tvEmptyState;
    private TextView chipHasNote;

    private String activeFilter = "all"; // all, image, video, audio, document, has_note, favourites
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        setContentView(R.layout.activity_vault_search);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        etSearch = findViewById(R.id.etSearch);
        btnBack = findViewById(R.id.btnBack);
        chipAll = findViewById(R.id.chipAll);
        chipImages = findViewById(R.id.chipImages);
        chipVideos = findViewById(R.id.chipVideos);
        chipAudio = findViewById(R.id.chipAudio);
        chipDocs = findViewById(R.id.chipDocs);
        chipHasNote = findViewById(R.id.chipHasNote);
        chipFavourites = findViewById(R.id.chipFavourites);
        resultsContainer = findViewById(R.id.resultsContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        btnBack.setOnClickListener(v -> finish());

        setupChips();
        setupSearchBar();
        showRecentSearches();
    }

    private void setupChips() {
        View.OnClickListener chipListener = v -> {
            if (v == chipAll) activeFilter = "all";
            else if (v == chipImages) activeFilter = "image";
            else if (v == chipVideos) activeFilter = "video";
            else if (v == chipAudio) activeFilter = "audio";
            else if (v == chipDocs) activeFilter = "document";
            else if (v == chipHasNote) activeFilter = "has_note";
            else if (v == chipFavourites) activeFilter = "favourites";

            updateChipHighlights();
            triggerSearch(etSearch.getText().toString());
        };
        chipAll.setOnClickListener(chipListener);
        chipImages.setOnClickListener(chipListener);
        chipVideos.setOnClickListener(chipListener);
        chipAudio.setOnClickListener(chipListener);
        chipDocs.setOnClickListener(chipListener);
        chipHasNote.setOnClickListener(chipListener);
        chipFavourites.setOnClickListener(chipListener);
    }

    private void updateChipHighlights() {
        int active = 0xFF6C63FF;
        int inactive = 0xFF1E293B;
        int activeText = 0xFFFFFFFF;
        int inactiveText = 0xFF94A3B8;

        TextView[] chips = {chipAll, chipImages, chipVideos, chipAudio, chipDocs, chipHasNote, chipFavourites};
        String[] filters = {"all", "image", "video", "audio", "document", "has_note", "favourites"};

        for (int i = 0; i < chips.length; i++) {
            boolean selected = activeFilter.equals(filters[i]);
            chips[i].setBackgroundColor(selected ? active : inactive);
            chips[i].setTextColor(selected ? activeText : inactiveText);
        }
    }

    private void setupSearchBar() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> triggerSearch(s.toString().trim());
                searchHandler.postDelayed(pendingSearch, 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void triggerSearch(String query) {
        List<VaultFileItem> results;

        if (query.isEmpty() && activeFilter.equals("all")) {
            showRecentSearches();
            return;
        }

        if (!query.isEmpty()) saveRecentSearch(query);

        if (activeFilter.equals("favourites")) {
            results = repo.getFavourites();
            if (!query.isEmpty()) {
                List<VaultFileItem> filtered = new ArrayList<>();
                for (VaultFileItem f : results) {
                    if (f.originalFileName != null &&
                            f.originalFileName.toLowerCase().contains(query.toLowerCase())) {
                        filtered.add(f);
                    }
                }
                results = filtered;
            }
        } else if (activeFilter.equals("has_note")) {
            List<VaultFileItem> allWithNotes = new ArrayList<>();
            for (VaultFileItem f : repo.getAllFiles()) {
                if (f.notes != null && !f.notes.isEmpty()) allWithNotes.add(f);
            }
            if (!query.isEmpty()) {
                List<VaultFileItem> filtered = new ArrayList<>();
                String q = query.toLowerCase();
                for (VaultFileItem f : allWithNotes) {
                    if ((f.originalFileName != null && f.originalFileName.toLowerCase().contains(q))
                            || f.notes.toLowerCase().contains(q)) {
                        filtered.add(f);
                    }
                }
                results = filtered;
            } else {
                results = allWithNotes;
            }
        } else if (query.isEmpty()) {
            results = getFilteredList();
        } else {
            results = repo.searchFiles(query);
            results = applyTypeFilter(results);
        }

        displayResults(results, query);
    }

    private List<VaultFileItem> getFilteredList() {
        switch (activeFilter) {
            case "image": return repo.getFilesByType(VaultFileItem.FileType.IMAGE);
            case "video": return repo.getFilesByType(VaultFileItem.FileType.VIDEO);
            case "audio": return repo.getFilesByType(VaultFileItem.FileType.AUDIO);
            case "document": return repo.getFilesByType(VaultFileItem.FileType.DOCUMENT);
            case "has_note": {
                List<VaultFileItem> r = new ArrayList<>();
                for (VaultFileItem f : repo.getAllFiles()) {
                    if (f.notes != null && !f.notes.isEmpty()) r.add(f);
                }
                return r;
            }
            default: return repo.getAllFiles();
        }
    }

    private List<VaultFileItem> applyTypeFilter(List<VaultFileItem> list) {
        if (activeFilter.equals("all") || activeFilter.equals("favourites")) return list;
        List<VaultFileItem> filtered = new ArrayList<>();
        if (activeFilter.equals("has_note")) {
            for (VaultFileItem f : list) {
                if (f.notes != null && !f.notes.isEmpty()) filtered.add(f);
            }
            return filtered;
        }
        VaultFileItem.FileType target = null;
        switch (activeFilter) {
            case "image": target = VaultFileItem.FileType.IMAGE; break;
            case "video": target = VaultFileItem.FileType.VIDEO; break;
            case "audio": target = VaultFileItem.FileType.AUDIO; break;
            case "document": target = VaultFileItem.FileType.DOCUMENT; break;
        }
        if (target == null) return list;
        for (VaultFileItem f : list) {
            if (f.fileType == target) filtered.add(f);
        }
        return filtered;
    }

    private void displayResults(List<VaultFileItem> results, String query) {
        // Remove all result views (keep emptyState)
        for (int i = resultsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = resultsContainer.getChildAt(i);
            if (child != tvEmptyState) resultsContainer.removeViewAt(i);
        }

        if (results.isEmpty()) {
            tvEmptyState.setText(query.isEmpty() ? "Search for files in your vault" : "No results found");
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);

        // Group by type when showing mixed results; flat list when a single type is active.
        boolean grouping = activeFilter.equals("all") || activeFilter.equals("has_note");
        if (grouping) {
            addGroupedResults(results);
        } else {
            for (VaultFileItem file : results) {
                resultsContainer.addView(buildResultRow(file));
            }
        }
    }

    private void addGroupedResults(List<VaultFileItem> results) {
        // Collect groups in a stable order using a linked map
        java.util.LinkedHashMap<VaultFileItem.FileType, List<VaultFileItem>> groups =
                new java.util.LinkedHashMap<>();
        for (VaultFileItem f : results) {
            List<VaultFileItem> bucket = groups.get(f.fileType);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(f.fileType, bucket);
            }
            bucket.add(f);
        }
        for (java.util.Map.Entry<VaultFileItem.FileType, List<VaultFileItem>> entry : groups.entrySet()) {
            String label = typeLabelPlural(entry.getKey()) + " (" + entry.getValue().size() + ")";
            resultsContainer.addView(buildSectionHeader(label));
            for (VaultFileItem f : entry.getValue()) {
                resultsContainer.addView(buildResultRow(f));
            }
        }
    }

    private View buildSectionHeader(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFF94A3B8);
        tv.setTextSize(12);
        tv.setPadding(16, 16, 16, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String typeLabelPlural(VaultFileItem.FileType type) {
        switch (type) {
            case IMAGE: return "Images";
            case VIDEO: return "Videos";
            case AUDIO: return "Audio";
            case DOCUMENT: return "Documents";
            default: return "Other";
        }
    }

    private View buildResultRow(VaultFileItem file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 12, 16, 12);
        row.setBackgroundColor(0xFF1E293B);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 2);
        row.setLayoutParams(rowParams);

        // Thumbnail
        ImageView thumb = new ImageView(this);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(56, 56);
        thumbParams.setMarginEnd(12);
        thumb.setLayoutParams(thumbParams);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xFF0F172A);
        thumb.setImageResource(android.R.drawable.ic_menu_gallery);

        if (file.thumbnailPath != null && !file.thumbnailPath.isEmpty()) {
            new Thread(() -> {
                Bitmap bm = repo.decryptThumbnail(file);
                if (bm != null) runOnUiThread(() -> thumb.setImageBitmap(bm));
            }).start();
        }

        // Info layout
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(file.originalFileName);
        tvName.setTextColor(0xFFF1F5F9);
        tvName.setTextSize(14);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(file.getFormattedSize() + " · " + file.fileType.name().toLowerCase());
        tvMeta.setTextColor(0xFF94A3B8);
        tvMeta.setTextSize(12);

        info.addView(tvName);
        info.addView(tvMeta);

        row.addView(thumb);
        row.addView(info);

        row.setOnClickListener(v -> openFile(file));
        return row;
    }

    private void openFile(VaultFileItem file) {
        Intent intent;
        switch (file.fileType) {
            case IMAGE:
                intent = new Intent(this, VaultImageViewerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "image");
                break;
            case VIDEO:
                intent = new Intent(this, VaultVideoPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "video");
                break;
            case AUDIO:
                intent = new Intent(this, VaultAudioPlayerActivity.class);
                intent.putExtra("file_id", file.id);
                intent.putExtra("file_list_type", "audio");
                break;
            case DOCUMENT:
            default:
                intent = new Intent(this, VaultDocumentViewerActivity.class);
                intent.putExtra("file_id", file.id);
                break;
        }
        startActivity(intent);
    }

    private void showRecentSearches() {
        List<String> recents = getRecentSearches();
        for (int i = resultsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = resultsContainer.getChildAt(i);
            if (child != tvEmptyState) resultsContainer.removeViewAt(i);
        }
        if (recents.isEmpty()) {
            tvEmptyState.setText("Search for files in your vault");
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvEmptyState.setVisibility(View.GONE);
        TextView header = new TextView(this);
        header.setText("Recent Searches");
        header.setTextColor(0xFF94A3B8);
        header.setTextSize(12);
        header.setPadding(16, 8, 16, 4);
        resultsContainer.addView(header, 0);

        for (String recent : recents) {
            TextView chip = new TextView(this);
            chip.setText("🕐  " + recent);
            chip.setTextColor(0xFFF1F5F9);
            chip.setTextSize(14);
            chip.setPadding(16, 12, 16, 12);
            chip.setBackgroundColor(0xFF1E293B);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 2);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                etSearch.setText(recent);
                etSearch.setSelection(recent.length());
            });
            resultsContainer.addView(chip);
        }
    }

    private void saveRecentSearch(String query) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE);
        List<String> list = getRecentSearches();
        list.remove(query);
        list.add(0, query);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("\u001F");
            sb.append(list.get(i));
        }
        prefs.edit().putString(PREF_RECENT, sb.toString()).apply();
    }

    private List<String> getRecentSearches() {
        SharedPreferences prefs = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE);
        String saved = prefs.getString(PREF_RECENT, "");
        if (saved.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(java.util.Arrays.asList(saved.split("\u001F")));
    }
}
