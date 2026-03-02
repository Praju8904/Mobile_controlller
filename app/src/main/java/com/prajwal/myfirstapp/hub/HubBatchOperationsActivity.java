package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Batch Operations Engine.
 *
 * Lets the user:
 *   1. Select a source set of files (all files, by type, by source, etc.)
 *   2. Preview the matched files (count + first 10 names)
 *   3. Choose an action: Rename with pattern, Move to Folder, Add Tags,
 *      Apply Color Label, Add to Project, Move to Trash
 *   4. Preview the operation (shows first 10 changes)
 *   5. Confirm & execute — progress bar while running
 *   6. Undo for 30 seconds after completion
 */
public class HubBatchOperationsActivity extends AppCompatActivity {

    private static final String[] SOURCE_TYPES = {
            "All Files", "PDFs", "Images", "Videos", "Audio",
            "Code Files", "Documents", "Archives",
            "WhatsApp Files", "Downloads", "Screenshots"
    };
    private static final String[] ACTIONS = {
            "Add Tags", "Apply Color Label", "Add to Project",
            "Move to Trash", "Rename: Add Prefix", "Rename: Add Suffix"
    };
    /** Milliseconds after a batch completes during which the Undo button is available. */
    private static final long UNDO_TIMEOUT_MS = 30_000L;

    private HubFileRepository repo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI
    private Spinner sourceSpinner;
    private Spinner actionSpinner;
    private EditText etActionValue;
    private LinearLayout previewContainer;
    private TextView tvMatchCount;
    private ProgressBar progressBar;
    private Button btnExecute;
    private Button btnUndo;
    private TextView tvOperationStatus;

    // State
    private int selectedSourceIndex = 0;
    private int selectedActionIndex = 0;
    private String actionValue = "";
    private List<HubFile> matchedFiles = new ArrayList<>();
    private List<HubFile> undoSnapshot = new ArrayList<>(); // file states before operation
    private boolean undoAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        buildUI();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 48);
        scroll.addView(root);
        setContentView(scroll);

        // Header
        LinearLayout header = makeRow();
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        header.addView(makeTitle("Batch Operations"));
        root.addView(header);
        root.addView(vspace(24));

        // Step 1: Source
        root.addView(makeStepLabel("Step 1: Select files"));
        sourceSpinner = makeSpinner(SOURCE_TYPES);
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedSourceIndex = pos;
                refreshPreview();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(sourceSpinner);
        root.addView(vspace(16));

        // Step 2: Preview matched files
        root.addView(makeStepLabel("Step 2: Matched files"));
        LinearLayout matchCard = makeCard();
        tvMatchCount = makeSub("Tap spinner to preview");
        matchCard.addView(tvMatchCount);
        matchCard.addView(vspace(8));
        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.VERTICAL);
        matchCard.addView(previewContainer);
        root.addView(matchCard);
        root.addView(vspace(16));

        // Step 3: Action
        root.addView(makeStepLabel("Step 3: Choose action"));
        actionSpinner = makeSpinner(ACTIONS);
        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedActionIndex = pos;
                updateActionValueHint();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(actionSpinner);
        root.addView(vspace(8));

        etActionValue = new EditText(this);
        etActionValue.setHint("Action value (e.g. tag name, prefix text)");
        etActionValue.setHintTextColor(Color.parseColor("#6B7280"));
        etActionValue.setTextColor(Color.WHITE);
        etActionValue.setTextSize(14);
        etActionValue.setPadding(20, 14, 20, 14);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(Color.parseColor("#1E293B"));
        etBg.setCornerRadius(12f);
        etActionValue.setBackground(etBg);
        root.addView(etActionValue);
        root.addView(vspace(24));

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        // Status
        tvOperationStatus = makeSub("");
        tvOperationStatus.setVisibility(View.GONE);
        root.addView(tvOperationStatus);
        root.addView(vspace(12));

        // Execute button
        btnExecute = makeButton("▶ Execute Batch Operation", "#8B5CF6", "#FFFFFF");
        btnExecute.setOnClickListener(v -> confirmAndExecute());
        root.addView(btnExecute);
        root.addView(vspace(8));

        // Undo button (hidden initially)
        btnUndo = makeButton("↩ Undo (30s)", "#F59E0B", "#000000");
        btnUndo.setVisibility(View.GONE);
        btnUndo.setOnClickListener(v -> undoOperation());
        root.addView(btnUndo);

        refreshPreview();
    }

    private void refreshPreview() {
        matchedFiles = getMatchingFiles(selectedSourceIndex);
        tvMatchCount.setText(matchedFiles.size() + " files match");
        previewContainer.removeAllViews();
        for (int i = 0; i < Math.min(10, matchedFiles.size()); i++) {
            HubFile f = matchedFiles.get(i);
            String name = f.displayName != null ? f.displayName : f.originalFileName;
            TextView tv = new TextView(this);
            tv.setText(f.getTypeEmoji() + "  " + name);
            tv.setTextColor(Color.parseColor("#D1D5DB"));
            tv.setTextSize(13);
            tv.setPadding(0, 3, 0, 3);
            previewContainer.addView(tv);
        }
        if (matchedFiles.size() > 10) {
            TextView tv = makeSub("… and " + (matchedFiles.size() - 10) + " more");
            previewContainer.addView(tv);
        }
    }

    private void confirmAndExecute() {
        actionValue = etActionValue.getText().toString().trim();
        if (matchedFiles.isEmpty()) { showToast("No files matched"); return; }
        String actionName = ACTIONS[selectedActionIndex];
        String msg = "Apply \"" + actionName + "\"" +
                (actionValue.isEmpty() ? "" : " with value \"" + actionValue + "\"")
                + " to " + matchedFiles.size() + " files?";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm Batch Operation")
                .setMessage(msg)
                .setPositiveButton("Execute", (d, w) -> executeBatch())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeBatch() {
        // Take snapshot for undo
        undoSnapshot.clear();
        for (HubFile f : matchedFiles) {
            HubFile copy = HubFile.fromJson(f.toJson());
            if (copy != null) undoSnapshot.add(copy);
        }

        progressBar.setVisibility(View.VISIBLE);
        tvOperationStatus.setVisibility(View.VISIBLE);
        tvOperationStatus.setText("Running…");
        btnExecute.setEnabled(false);
        btnUndo.setVisibility(View.GONE);

        List<HubFile> toProcess = new ArrayList<>(matchedFiles);
        int[] progressHolder = {0};

        executor.execute(() -> {
            int total = toProcess.size();
            for (HubFile f : toProcess) {
                applyAction(f);
                progressHolder[0]++;
                final int p = progressHolder[0];
                mainHandler.post(() -> progressBar.setProgress(p * 100 / total));
            }
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                tvOperationStatus.setText("✅ Done! " + total + " files processed.");
                btnExecute.setEnabled(true);
                undoAvailable = true;
                btnUndo.setVisibility(View.VISIBLE);
                // Auto-hide undo after 30 seconds
                mainHandler.postDelayed(() -> {
                    btnUndo.setVisibility(View.GONE);
                    undoAvailable = false;
                }, UNDO_TIMEOUT_MS);
                refreshPreview();
            });
        });
    }

    private void applyAction(HubFile f) {
        String action = ACTIONS[selectedActionIndex];
        switch (action) {
            case "Add Tags":
                if (!actionValue.isEmpty() && f.tags != null && !f.tags.contains(actionValue)) {
                    f.tags.add(actionValue);
                    repo.updateFile(f);
                }
                break;
            case "Move to Trash":
                repo.deleteFile(f.id);
                break;
            case "Rename: Add Prefix":
                if (!actionValue.isEmpty()) {
                    String oldName = f.displayName != null ? f.displayName : f.originalFileName;
                    f.displayName = actionValue + oldName;
                    repo.updateFile(f);
                }
                break;
            case "Rename: Add Suffix":
                if (!actionValue.isEmpty()) {
                    String oldName = f.displayName != null ? f.displayName : f.originalFileName;
                    int dot = oldName.lastIndexOf('.');
                    if (dot > 0) {
                        f.displayName = oldName.substring(0, dot) + actionValue + oldName.substring(dot);
                    } else {
                        f.displayName = oldName + actionValue;
                    }
                    repo.updateFile(f);
                }
                break;
            case "Apply Color Label":
                try {
                    f.colorLabel = HubFile.ColorLabel.valueOf(actionValue.toUpperCase());
                    repo.updateFile(f);
                } catch (Exception ignored) {}
                break;
        }
    }

    private void undoOperation() {
        if (!undoAvailable || undoSnapshot.isEmpty()) return;
        for (HubFile snapshot : undoSnapshot) {
            repo.updateFile(snapshot);
        }
        showToast("Undo complete — " + undoSnapshot.size() + " files restored");
        btnUndo.setVisibility(View.GONE);
        tvOperationStatus.setText("↩ Undone.");
        undoAvailable = false;
        refreshPreview();
    }

    // ─── Source matching ──────────────────────────────────────────────────────

    private List<HubFile> getMatchingFiles(int sourceIndex) {
        switch (sourceIndex) {
            case 0: return repo.getAllFiles();
            case 1: return repo.getFilesByType(HubFile.FileType.PDF);
            case 2: return repo.getFilesByType(HubFile.FileType.IMAGE);
            case 3: return repo.getFilesByType(HubFile.FileType.VIDEO);
            case 4: return repo.getFilesByType(HubFile.FileType.AUDIO);
            case 5: return repo.getFilesByType(HubFile.FileType.CODE);
            case 6: return repo.getFilesByType(HubFile.FileType.DOCUMENT);
            case 7: return repo.getFilesByType(HubFile.FileType.ARCHIVE);
            case 8: return repo.getFilesBySource(HubFile.Source.WHATSAPP);
            case 9: return repo.getFilesBySource(HubFile.Source.DOWNLOADS);
            case 10: return repo.getFilesBySource(HubFile.Source.SCREENSHOTS);
            default: return new ArrayList<>();
        }
    }

    private void updateActionValueHint() {
        String[] hints = {
                "Tag name (e.g. study)",
                "Color: RED, GREEN, BLUE, YELLOW, ORANGE, PURPLE",
                "Project ID",
                "(no value needed)",
                "Prefix text (e.g. Study_)",
                "Suffix text (e.g. _2026)"
        };
        int idx = selectedActionIndex;
        if (idx < hints.length) etActionValue.setHint(hints[idx]);
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private Spinner makeSpinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackgroundColor(Color.parseColor("#1E293B"));
        return s;
    }

    private TextView makeStepLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F59E0B"));
        tv.setTextSize(15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(16f);
        card.setBackground(bg);
        return card;
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView makeTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(20);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView makeSub(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setTextSize(13);
        return tv;
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(fg));
        btn.setTextSize(13);
        btn.setPadding(24, 12, 24, 12);
        GradientDrawable bgD = new GradientDrawable();
        bgD.setColor(Color.parseColor(bg));
        bgD.setCornerRadius(16f);
        btn.setBackground(bgD);
        return btn;
    }

    private View vspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp));
        return v;
    }

    private View hspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp,
                LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
