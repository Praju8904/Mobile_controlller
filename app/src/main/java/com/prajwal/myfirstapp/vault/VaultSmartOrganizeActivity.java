package com.prajwal.myfirstapp.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Smart organization activity for the Personal Media Vault.
 *
 * Analyzes unorganized files (albumId == null) and suggests album groupings:
 *   - Date-based: files imported on the same calendar day → "MMM dd, yyyy"
 *   - Type-based: "All Videos", "All Documents" (when ≥2 files of that type exist)
 *
 * The user selects desired groupings via checkboxes, then taps
 * "Create Selected Albums" to create albums and assign the files.
 */
public class VaultSmartOrganizeActivity extends Activity {

    private static final String TAG = "VaultSmartOrganize";
    private static final int MIN_TYPE_GROUP_SIZE = 2;

    // ─── Repository ──────────────────────────────────────────────
    private MediaVaultRepository repo;

    // ─── Suggestions ─────────────────────────────────────────────

    /** One suggested album grouping. */
    private static class Suggestion {
        String albumName;
        List<String> fileIds; // IDs of files to assign
        boolean isTypeGroup;  // true = "All Videos" / "All Documents" style

        Suggestion(String albumName, List<String> fileIds, boolean isTypeGroup) {
            this.albumName = albumName;
            this.fileIds = fileIds;
            this.isTypeGroup = isTypeGroup;
        }
    }

    private List<Suggestion> suggestions = new ArrayList<>();
    private List<CheckBox> checkBoxes = new ArrayList<>();

    // ─── UI ──────────────────────────────────────────────────────
    private LinearLayout suggestionsContainer;
    private TextView emptyStateText;
    private Button createButton;

    // ─── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        repo = MediaVaultRepository.getInstance(this);

        buildUi();
        analyzeAndPopulateAsync();
    }

    // ─── UI construction ─────────────────────────────────────────

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        setContentView(root);

        // Top bar
        root.addView(buildTopBar());

        // Description
        TextView desc = new TextView(this);
        desc.setText("Suggested album groupings for your unorganized files");
        desc.setTextColor(Color.parseColor("#AAAAAA"));
        desc.setTextSize(14);
        desc.setPadding(dp(16), dp(8), dp(16), dp(4));
        root.addView(desc);

        // Divider
        root.addView(makeDivider());

        // Empty state (shown while loading or when nothing to organize)
        emptyStateText = new TextView(this);
        emptyStateText.setText("Analyzing files…");
        emptyStateText.setTextColor(Color.parseColor("#888888"));
        emptyStateText.setTextSize(15);
        emptyStateText.setGravity(Gravity.CENTER);
        emptyStateText.setPadding(dp(24), dp(40), dp(24), dp(40));
        emptyStateText.setVisibility(View.VISIBLE);
        root.addView(emptyStateText);

        // ScrollView for suggestion items (flex 1)
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(svParams);

        suggestionsContainer = new LinearLayout(this);
        suggestionsContainer.setOrientation(LinearLayout.VERTICAL);
        suggestionsContainer.setPadding(dp(8), dp(4), dp(8), dp(8));
        scrollView.addView(suggestionsContainer);
        root.addView(scrollView, svParams);

        // Bottom bar with Create button
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        bottomBar.setPadding(dp(16), dp(10), dp(16), dp(12));

        createButton = new Button(this);
        createButton.setText("Create Selected Albums");
        createButton.setTextColor(Color.WHITE);
        createButton.setBackgroundColor(Color.parseColor("#3B82F6"));
        createButton.setTextSize(15);
        createButton.setEnabled(false);
        createButton.setAlpha(0.5f);
        createButton.setOnClickListener(v -> createSelectedAlbums());
        bottomBar.addView(createButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(bottomBar);
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(10), dp(8), dp(10));

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(20);
        btnBack.setPadding(dp(10), dp(6), dp(16), dp(6));
        btnBack.setClickable(true);
        btnBack.setOnClickListener(v -> finish());
        bar.addView(btnBack);

        TextView title = new TextView(this);
        title.setText("Smart Organize");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        return bar;
    }

    // ─── Analysis ────────────────────────────────────────────────

    private void analyzeAndPopulateAsync() {
        new Thread(() -> {
            List<Suggestion> found = buildSuggestions();
            runOnUiThread(() -> onSuggestionsReady(found));
        }).start();
    }

    private List<Suggestion> buildSuggestions() {
        List<VaultFileItem> allFiles = repo.getAllFiles();

        // Collect unorganized files (no album assigned)
        List<VaultFileItem> unorganized = new ArrayList<>();
        for (VaultFileItem f : allFiles) {
            if (f.albumId == null || f.albumId.isEmpty()) unorganized.add(f);
        }

        List<Suggestion> result = new ArrayList<>();
        if (unorganized.isEmpty()) return result;

        // ── Date-based groupings ──────────────────────────────────
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // LinkedHashMap preserves insertion order (sorted by key below)
        Map<String, List<String>> dateGroups = new LinkedHashMap<>();
        Map<String, String> keyToLabel = new LinkedHashMap<>();

        for (VaultFileItem f : unorganized) {
            String key = dayKey.format(new Date(f.importedAt));
            String label = sdf.format(new Date(f.importedAt));
            if (!dateGroups.containsKey(key)) {
                dateGroups.put(key, new ArrayList<>());
                keyToLabel.put(key, label);
            }
            dateGroups.get(key).add(f.id);
        }

        // Sort keys descending (most recent first) and add suggestions for groups ≥2 files
        List<String> sortedKeys = new ArrayList<>(dateGroups.keySet());
        Collections.sort(sortedKeys, Collections.reverseOrder());
        for (String key : sortedKeys) {
            List<String> ids = dateGroups.get(key);
            if (ids.size() >= 2) {
                result.add(new Suggestion(keyToLabel.get(key), ids, false));
            }
        }

        // ── Type-based groupings ──────────────────────────────────
        List<String> videoIds = new ArrayList<>();
        List<String> docIds   = new ArrayList<>();
        for (VaultFileItem f : unorganized) {
            if (f.fileType == VaultFileItem.FileType.VIDEO) videoIds.add(f.id);
            else if (f.fileType == VaultFileItem.FileType.DOCUMENT) docIds.add(f.id);
        }
        if (videoIds.size() >= MIN_TYPE_GROUP_SIZE) {
            result.add(new Suggestion("All Videos", videoIds, true));
        }
        if (docIds.size() >= MIN_TYPE_GROUP_SIZE) {
            result.add(new Suggestion("All Documents", docIds, true));
        }

        return result;
    }

    private void onSuggestionsReady(List<Suggestion> found) {
        suggestions = found;
        suggestionsContainer.removeAllViews();
        checkBoxes.clear();

        if (found.isEmpty()) {
            emptyStateText.setText("All files are already organized into albums.");
            emptyStateText.setVisibility(View.VISIBLE);
            createButton.setEnabled(false);
            createButton.setAlpha(0.5f);
            return;
        }

        emptyStateText.setVisibility(View.GONE);

        for (Suggestion suggestion : found) {
            View row = buildSuggestionRow(suggestion);
            suggestionsContainer.addView(row);
        }

        // Enable the Create button once items are present
        updateCreateButtonState();
    }

    // ─── Suggestion rows ─────────────────────────────────────────

    private View buildSuggestionRow(Suggestion suggestion) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(10), dp(8), dp(10));
        row.setBackgroundColor(Color.parseColor("#1E1E1E"));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rowParams);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(true); // pre-select all suggestions
        cb.setOnCheckedChangeListener((btn, checked) -> updateCreateButtonState());
        checkBoxes.add(cb);
        row.addView(cb);

        // Album name + file count column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(10), 0, 0, 0);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tcp);

        TextView tvName = new TextView(this);
        tvName.setText(suggestion.albumName);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(15);
        textCol.addView(tvName);

        String typeLabel = suggestion.isTypeGroup ? "Type group" : "By date";
        int count = suggestion.fileIds.size();
        TextView tvMeta = new TextView(this);
        tvMeta.setText(typeLabel + " · " + count + (count == 1 ? " file" : " files"));
        tvMeta.setTextColor(Color.parseColor("#888888"));
        tvMeta.setTextSize(12);
        textCol.addView(tvMeta);

        row.addView(textCol);
        return row;
    }

    // ─── Create albums ────────────────────────────────────────────

    private void createSelectedAlbums() {
        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isChecked()) selectedIndices.add(i);
        }

        if (selectedIndices.isEmpty()) {
            Toast.makeText(this, "No suggestions selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Create Albums")
                .setMessage("Create " + selectedIndices.size() +
                        (selectedIndices.size() == 1 ? " album" : " albums") +
                        " and assign files?")
                .setPositiveButton("Create", (dialog, which) -> performCreate(selectedIndices))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performCreate(List<Integer> selectedIndices) {
        createButton.setEnabled(false);
        new Thread(() -> {
            int created = 0;
            for (int idx : selectedIndices) {
                Suggestion s = suggestions.get(idx);
                try {
                    VaultAlbum album = repo.createAlbum(s.albumName, "#3B82F6");
                    for (String fileId : s.fileIds) {
                        repo.addFileToAlbum(fileId, album.id);
                    }
                    created++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create album: " + s.albumName, e);
                }
            }
            final int finalCreated = created;
            runOnUiThread(() -> {
                Toast.makeText(this,
                        finalCreated + (finalCreated == 1 ? " album created" : " albums created"),
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void updateCreateButtonState() {
        boolean anyChecked = false;
        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked()) { anyChecked = true; break; }
        }
        createButton.setEnabled(anyChecked);
        createButton.setAlpha(anyChecked ? 1f : 0.5f);
    }

    private View makeDivider() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#2A2A2A"));
        d.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return d;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
