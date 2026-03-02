package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Duplicate Manager — view and resolve duplicate file groups.
 */
public class HubDuplicateManagerActivity extends AppCompatActivity {

    private HubFileRepository repo;

    private TextView tvDupGroupCount;
    private TextView tvDupWasted;
    private Button btnDupScan;
    private android.widget.ProgressBar dupScanProgress;
    private TextView tvDupScanStatus;
    private LinearLayout dupGroupsContainer;
    private LinearLayout dupEmptyState;
    private LinearLayout dupBottomBar;
    private TextView tvDupSelectedCount;
    private Button btnDupDeleteSelected;

    private Set<String> selectedGroupIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_duplicate_manager);

        repo = HubFileRepository.getInstance(this);

        tvDupGroupCount = findViewById(R.id.tvDupGroupCount);
        tvDupWasted = findViewById(R.id.tvDupWasted);
        btnDupScan = findViewById(R.id.btnDupScan);
        dupScanProgress = findViewById(R.id.dupScanProgress);
        tvDupScanStatus = findViewById(R.id.tvDupScanStatus);
        dupGroupsContainer = findViewById(R.id.dupGroupsContainer);
        dupEmptyState = findViewById(R.id.dupEmptyState);
        dupBottomBar = findViewById(R.id.dupBottomBar);
        tvDupSelectedCount = findViewById(R.id.tvDupSelectedCount);
        btnDupDeleteSelected = findViewById(R.id.btnDupDeleteSelected);

        findViewById(R.id.btnDupBack).setOnClickListener(v -> finish());

        btnDupScan.setOnClickListener(v -> startScan());
        findViewById(R.id.btnDupAutoResolve).setOnClickListener(v -> showAutoResolveDialog());
        findViewById(R.id.btnDupSelectAll).setOnClickListener(v -> selectAll());
        btnDupDeleteSelected.setOnClickListener(v -> deleteSelected());

        // Game Mode button
        Button btnGameMode = new Button(this);
        btnGameMode.setText("🎮 Game Mode");
        btnGameMode.setTextColor(Color.parseColor("#FFFFFF"));
        btnGameMode.setTextSize(13);
        GradientDrawable gmBg = new GradientDrawable();
        gmBg.setColor(Color.parseColor("#7C3AED")); gmBg.setCornerRadius(20f);
        btnGameMode.setBackground(gmBg);
        btnGameMode.setPadding(24, 12, 24, 12);
        btnGameMode.setOnClickListener(v -> startGameMode());

        // Add to bottom bar or as a standalone button - add after dupBottomBar
        LinearLayout gameRow = new LinearLayout(this);
        gameRow.setOrientation(LinearLayout.HORIZONTAL);
        gameRow.setPadding(0, 8, 0, 0);
        LinearLayout.LayoutParams grLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gameRow.setLayoutParams(grLp);
        gameRow.addView(btnGameMode);

        // Find dupBottomBar parent and add gameRow before it
        ViewGroup parent = (ViewGroup) dupBottomBar.getParent();
        if (parent != null) {
            int idx = parent.indexOfChild(dupBottomBar);
            parent.addView(gameRow, Math.max(0, idx));
        }

        loadGroups();
    }

    private void loadGroups() {
        List<DuplicateGroup> groups = repo.getAllDuplicateGroups();
        tvDupGroupCount.setText(String.valueOf(groups.size()));
        tvDupWasted.setText(formatBytes(repo.getTotalWastedBytes()));
        renderGroups(groups);
    }

    private void renderGroups(List<DuplicateGroup> groups) {
        dupGroupsContainer.removeAllViews();

        if (groups.isEmpty()) {
            dupEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        dupEmptyState.setVisibility(View.GONE);

        float dp = getResources().getDisplayMetrics().density;

        for (DuplicateGroup group : groups) {
            LinearLayout card = buildGroupCard(group, dp);
            dupGroupsContainer.addView(card);
        }
    }

    private LinearLayout buildGroupCard(DuplicateGroup group, float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12 * dp);
        bg.setColor(Color.parseColor("#1A1A2E"));
        card.setBackground(bg);
        card.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(14*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(10*dp));
        card.setLayoutParams(lp);

        // Header row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView emoji = new TextView(this);
        emoji.setText("🔁");
        emoji.setTextSize(20);
        emoji.setLayoutParams(new LinearLayout.LayoutParams((int)(32*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.addView(emoji);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView fname = new TextView(this);
        fname.setText(group.fileName != null ? group.fileName : "Unknown File");
        fname.setTextColor(Color.WHITE);
        fname.setTextSize(14);
        fname.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fname.setSingleLine(true);
        fname.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(fname);

        TextView meta = new TextView(this);
        meta.setText(group.duplicateCount + " copies · " + group.getFormattedWasted() + " wasted");
        meta.setTextColor(Color.parseColor("#EF4444"));
        meta.setTextSize(12);
        info.addView(meta);

        headerRow.addView(info);
        card.addView(headerRow);

        // Expandable file list (collapsed by default)
        LinearLayout fileList = new LinearLayout(this);
        fileList.setOrientation(LinearLayout.VERTICAL);
        fileList.setVisibility(View.GONE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.setMargins(0, (int)(8*dp), 0, 0);
        fileList.setLayoutParams(flp);

        // Load files for this group
        List<HubFile> groupFiles = getFilesForGroup(group.id);
        for (HubFile f : groupFiles) {
            LinearLayout fileRow = new LinearLayout(this);
            fileRow.setOrientation(LinearLayout.HORIZONTAL);
            fileRow.setGravity(Gravity.CENTER_VERTICAL);
            fileRow.setPadding(0, (int)(4*dp), 0, (int)(4*dp));

            TextView fEmoji = new TextView(this);
            fEmoji.setText(f.getTypeEmoji());
            fEmoji.setTextSize(16);
            fEmoji.setLayoutParams(new LinearLayout.LayoutParams((int)(28*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            fileRow.addView(fEmoji);

            TextView fName = new TextView(this);
            String display = f.displayName != null ? f.displayName : f.originalFileName;
            fName.setText(display != null ? display : "File");
            fName.setTextColor(Color.parseColor("#94A3B8"));
            fName.setTextSize(12);
            fName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            fName.setSingleLine(true);
            fName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            fileRow.addView(fName);

            fileList.addView(fileRow);
        }
        card.addView(fileList);

        // Toggle expand on header tap
        headerRow.setClickable(true);
        headerRow.setFocusable(true);
        headerRow.setForeground(getDrawable(android.R.attr.selectableItemBackground));
        headerRow.setOnClickListener(v -> {
            fileList.setVisibility(fileList.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });

        // Action buttons
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, (int)(10*dp), 0, 0);
        buttons.setLayoutParams(blp);

        Button keepNewest = new Button(this);
        keepNewest.setText("Keep Newest");
        keepNewest.setTextColor(Color.WHITE);
        keepNewest.setTextSize(12);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(6 * dp);
        btnBg.setColor(Color.parseColor("#1E3A5F"));
        keepNewest.setBackground(btnBg);
        LinearLayout.LayoutParams kblp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        kblp.setMarginEnd((int)(6*dp));
        keepNewest.setLayoutParams(kblp);
        keepNewest.setOnClickListener(v -> keepNewestInGroup(group, groupFiles));
        buttons.addView(keepNewest);

        Button deleteAll = new Button(this);
        deleteAll.setText("Delete All But One");
        deleteAll.setTextColor(Color.WHITE);
        deleteAll.setTextSize(12);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setShape(GradientDrawable.RECTANGLE);
        delBg.setCornerRadius(6 * dp);
        delBg.setColor(Color.parseColor("#5F1E1E"));
        deleteAll.setBackground(delBg);
        deleteAll.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        deleteAll.setOnClickListener(v -> confirmDeleteAllButOne(group, groupFiles));
        buttons.addView(deleteAll);

        card.addView(buttons);
        return card;
    }

    private List<HubFile> getFilesForGroup(String groupId) {
        List<HubFile> result = new ArrayList<>();
        for (HubFile f : repo.getAllFiles()) {
            if (groupId != null && groupId.equals(f.duplicateGroupId)) result.add(f);
        }
        return result;
    }

    private void keepNewestInGroup(DuplicateGroup group, List<HubFile> files) {
        if (files.isEmpty()) return;
        HubFile newest = files.get(0);
        for (HubFile f : files) {
            if (f.originalModifiedAt > newest.originalModifiedAt
                    || (f.originalModifiedAt == newest.originalModifiedAt
                        && f.id.compareTo(newest.id) > 0)) {
                newest = f;
            }
        }
        final HubFile keep = newest;
        for (HubFile f : files) {
            if (!f.id.equals(keep.id)) repo.deleteFile(f.id);
        }
        Toast.makeText(this, "Kept newest, deleted " + (files.size() - 1) + " copies", Toast.LENGTH_SHORT).show();
        loadGroups();
    }

    private void confirmDeleteAllButOne(DuplicateGroup group, List<HubFile> files) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Duplicates")
                .setMessage("Keep one copy of \"" + group.fileName + "\" and delete the rest?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (!files.isEmpty()) {
                        HubFile keep = files.get(0);
                        for (int i = 1; i < files.size(); i++) repo.deleteFile(files.get(i).id);
                        Toast.makeText(this, "Deleted " + (files.size() - 1) + " copies", Toast.LENGTH_SHORT).show();
                        loadGroups();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startScan() {
        btnDupScan.setEnabled(false);
        dupScanProgress.setVisibility(View.VISIBLE);
        tvDupScanStatus.setVisibility(View.VISIBLE);
        tvDupScanStatus.setText("Scanning files...");

        repo.detectDuplicates(() -> {
            dupScanProgress.setVisibility(View.GONE);
            tvDupScanStatus.setText("Scan complete!");
            btnDupScan.setEnabled(true);
            loadGroups();
        });
    }

    private void showAutoResolveDialog() {
        String[] options = {"Keep Most Recent", "Keep Largest"};
        new AlertDialog.Builder(this)
                .setTitle("Auto-Resolve All")
                .setItems(options, (dialog, which) -> {
                    String strategy = options[which];
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Auto-Resolve")
                            .setMessage("Resolve all duplicates using \"" + strategy + "\" strategy?")
                            .setPositiveButton("Resolve", (d, w) -> autoResolveAll(which))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }

    private void autoResolveAll(int strategy) {
        List<DuplicateGroup> groups = repo.getAllDuplicateGroups();
        int totalDeleted = 0;
        for (DuplicateGroup group : groups) {
            List<HubFile> files = getFilesForGroup(group.id);
            if (files.size() < 2) continue;
            HubFile keep;
            if (strategy == 0) { // Most recent
                keep = files.get(0);
                for (HubFile f : files) if (f.originalModifiedAt > keep.originalModifiedAt) keep = f;
            } else { // Largest
                keep = files.get(0);
                for (HubFile f : files) if (f.fileSize > keep.fileSize) keep = f;
            }
            for (HubFile f : files) {
                if (!f.id.equals(keep.id)) { repo.deleteFile(f.id); totalDeleted++; }
            }
        }
        Toast.makeText(this, "Deleted " + totalDeleted + " duplicate files", Toast.LENGTH_LONG).show();
        loadGroups();
    }

    private void selectAll() {
        for (DuplicateGroup g : repo.getAllDuplicateGroups()) selectedGroupIds.add(g.id);
        updateBottomBar();
    }

    private void deleteSelected() {
        if (selectedGroupIds.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Selected")
                .setMessage("Delete duplicates in " + selectedGroupIds.size() + " group(s)?")
                .setPositiveButton("Delete", (d, w) -> {
                    for (String gid : selectedGroupIds) {
                        List<HubFile> files = getFilesForGroup(gid);
                        if (files.size() > 1) {
                            HubFile keep = files.get(0);
                            for (int i = 1; i < files.size(); i++) repo.deleteFile(files.get(i).id);
                        }
                    }
                    selectedGroupIds.clear();
                    updateBottomBar();
                    loadGroups();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateBottomBar() {
        if (selectedGroupIds.isEmpty()) {
            dupBottomBar.setVisibility(View.GONE);
        } else {
            dupBottomBar.setVisibility(View.VISIBLE);
            tvDupSelectedCount.setText(selectedGroupIds.size() + " groups selected");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ─── Game Mode ────────────────────────────────────────────────────────────

    private int gameGroupIndex = 0;
    private long gameSpaceFreed = 0;
    private List<DuplicateGroup> gameGroups = new ArrayList<>();

    private void startGameMode() {
        gameGroups = repo.getAllDuplicateGroups();
        if (gameGroups.isEmpty()) {
            Toast.makeText(this, "No duplicate groups to resolve!", Toast.LENGTH_SHORT).show();
            return;
        }
        gameGroupIndex = 0;
        gameSpaceFreed = 0;
        showGameScreen();
    }

    private void showGameScreen() {
        if (gameGroupIndex >= gameGroups.size()) {
            showGameComplete();
            return;
        }
        DuplicateGroup group = gameGroups.get(gameGroupIndex);
        List<HubFile> files = getFilesForGroup(group.id);
        if (files.size() < 2) { gameGroupIndex++; showGameScreen(); return; }
        HubFile left = files.get(0); HubFile right = files.get(1);

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(24, 48, 24, 48);
        dialog.setContentView(root);

        // Header
        TextView tvHeader = new TextView(this);
        tvHeader.setText("🎮 Which one to keep?");
        tvHeader.setTextColor(Color.WHITE); tvHeader.setTextSize(20);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setGravity(Gravity.CENTER);
        root.addView(tvHeader);

        TextView tvProgress = new TextView(this);
        tvProgress.setText("Group " + (gameGroupIndex + 1) + " of " + gameGroups.size() +
                "  •  Freed: " + formatBytes(gameSpaceFreed));
        tvProgress.setTextColor(Color.parseColor("#6B7280")); tvProgress.setTextSize(12);
        tvProgress.setGravity(Gravity.CENTER);
        root.addView(tvProgress);

        // Add 16dp space
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32));
        root.addView(sp);

        // Two cards side by side
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        cardRow.addView(buildGameCard(left, 1f));
        cardRow.addView(buildVS());
        cardRow.addView(buildGameCard(right, 1f));
        root.addView(cardRow);

        // Add space
        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24));
        root.addView(sp2);

        // Keep buttons
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        btns.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnLeft = new Button(this);
        btnLeft.setText("◀ Keep Left");
        btnLeft.setTextColor(Color.WHITE);
        GradientDrawable leftBg = new GradientDrawable();
        leftBg.setColor(Color.parseColor("#3B82F6")); leftBg.setCornerRadius(20f);
        btnLeft.setBackground(leftBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMargins(0, 0, 8, 0);
        btnLeft.setLayoutParams(btnLp);
        btnLeft.setOnClickListener(v -> {
            gameSpaceFreed += right.fileSize;
            repo.deleteFile(right.id);
            gameGroupIndex++;
            dialog.dismiss();
            showGameScreen();
        });

        Button btnRight = new Button(this);
        btnRight.setText("Keep Right ▶");
        btnRight.setTextColor(Color.WHITE);
        GradientDrawable rightBg = new GradientDrawable();
        rightBg.setColor(Color.parseColor("#3B82F6")); rightBg.setCornerRadius(20f);
        btnRight.setBackground(rightBg);
        LinearLayout.LayoutParams btnRLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnRLp.setMargins(8, 0, 0, 0);
        btnRight.setLayoutParams(btnRLp);
        btnRight.setOnClickListener(v -> {
            gameSpaceFreed += left.fileSize;
            repo.deleteFile(left.id);
            gameGroupIndex++;
            dialog.dismiss();
            showGameScreen();
        });

        btns.addView(btnLeft); btns.addView(btnRight);
        root.addView(btns);

        // Skip button
        Button btnSkip = new Button(this);
        btnSkip.setText("Skip →");
        btnSkip.setTextColor(Color.parseColor("#6B7280")); btnSkip.setTextSize(12);
        btnSkip.setBackground(null);
        btnSkip.setOnClickListener(v -> { gameGroupIndex++; dialog.dismiss(); showGameScreen(); });
        LinearLayout.LayoutParams skipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        skipLp.setMargins(0, 8, 0, 0);
        btnSkip.setLayoutParams(skipLp);
        root.addView(btnSkip);

        dialog.show();
    }

    private View buildGameCard(HubFile f, float weight) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(14f);
        card.setBackground(bg); card.setPadding(14, 18, 14, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(4, 0, 4, 0); card.setLayoutParams(lp);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(f.getTypeEmoji()); tvEmoji.setTextSize(32);
        tvEmoji.setGravity(Gravity.CENTER);
        card.addView(tvEmoji);

        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name != null && name.length() > 18) name = name.substring(0, 15) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Unknown");
        tvName.setTextColor(Color.WHITE); tvName.setTextSize(12);
        tvName.setGravity(Gravity.CENTER);
        card.addView(tvName);

        TextView tvSize = new TextView(this);
        tvSize.setText(f.getFormattedSize());
        tvSize.setTextColor(Color.parseColor("#6366F1")); tvSize.setTextSize(11);
        tvSize.setGravity(Gravity.CENTER);
        card.addView(tvSize);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yy", java.util.Locale.getDefault());
        TextView tvDate = new TextView(this);
        tvDate.setText(sdf.format(new java.util.Date(f.importedAt)));
        tvDate.setTextColor(Color.parseColor("#6B7280")); tvDate.setTextSize(10);
        tvDate.setGravity(Gravity.CENTER);
        card.addView(tvDate);
        return card;
    }

    private View buildVS() {
        TextView tv = new TextView(this);
        tv.setText("VS"); tv.setTextColor(Color.parseColor("#F59E0B"));
        tv.setTextSize(16); tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(8, 0, 8, 0); tv.setLayoutParams(lp);
        return tv;
    }

    private void showGameComplete() {
        new AlertDialog.Builder(this)
                .setTitle("🎉 All Resolved!")
                .setMessage("You resolved all " + gameGroups.size() + " duplicate groups!\n\n" +
                        "Total space freed: " + formatBytes(gameSpaceFreed))
                .setPositiveButton("🎊 Done", (d, w) -> loadGroups())
                .show();
    }
}
