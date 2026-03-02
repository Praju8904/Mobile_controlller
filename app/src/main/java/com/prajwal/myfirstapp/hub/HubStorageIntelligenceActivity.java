package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Storage Intelligence screen — health score, reclaim opportunities, breakdowns.
 */
public class HubStorageIntelligenceActivity extends AppCompatActivity {

    private HubFileRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_storage_intelligence);
        repo = HubFileRepository.getInstance(this);
        buildUI();
    }

    private void buildUI() {
        LinearLayout root = findViewById(R.id.storageIntelRoot);

        // Back
        Button btnBack = findViewById(R.id.btnStorageIntelBack);
        btnBack.setOnClickListener(v -> finish());

        int score = repo.computeStorageHealthScore();
        int scoreColor = score >= 70 ? Color.parseColor("#22C55E")
                : score >= 40 ? Color.parseColor("#F59E0B")
                : Color.parseColor("#EF4444");

        // Health score card
        TextView tvScore = findViewById(R.id.tvHealthScore);
        tvScore.setText(String.valueOf(score));
        tvScore.setTextColor(scoreColor);

        TextView tvScoreLabel = findViewById(R.id.tvHealthScoreLabel);
        tvScoreLabel.setText(score >= 70 ? "Good — keep it up!" : score >= 40 ? "Fair — some cleanup needed" : "Poor — action required");
        tvScoreLabel.setTextColor(scoreColor);

        // Score breakdown
        LinearLayout breakdownContainer = findViewById(R.id.scoreBreakdownContainer);
        int total = repo.getTotalFileCount();
        List<HubFile> allFiles = repo.getAllFiles();

        int organized = 0;
        for (HubFile f : allFiles) if (f.folderId != null || f.projectId != null) organized++;
        int unorganized = total - organized;

        addBreakdownRow(breakdownContainer, "📁 Organized files",
                organized + " / " + total, Color.parseColor("#22C55E"), false);
        addBreakdownRow(breakdownContainer, "🗂️ Unorganized files",
                unorganized + " files", Color.parseColor("#F59E0B"), unorganized > 0);

        int dupes = repo.getTotalDuplicateCount();
        long wastedBytes = repo.getTotalWastedBytes();
        addBreakdownRow(breakdownContainer, "⚠️ Duplicate files",
                dupes > 0 ? dupes + " duplicates (-" + formatSize(wastedBytes) + " wasted)" : "None detected",
                dupes > 0 ? Color.parseColor("#EF4444") : Color.parseColor("#22C55E"), dupes > 0);

        int inbox = repo.getPendingInboxCount();
        addBreakdownRow(breakdownContainer, "📥 Unreviewed inbox",
                inbox + " items pending",
                inbox > 5 ? Color.parseColor("#F59E0B") : Color.parseColor("#22C55E"), inbox > 5);

        // Space reclaim section
        LinearLayout reclaimContainer = findViewById(R.id.reclaimContainer);
        if (dupes > 0) {
            addReclaimRow(reclaimContainer,
                    dupes + " duplicate files — free up " + formatSize(wastedBytes),
                    "Review", () -> startActivity(new Intent(this, HubDuplicateManagerActivity.class)));
        }

        List<HubFile> largeFiles = repo.getLargestFiles(100);
        long largeBytes = 0;
        int largeCount = 0;
        for (HubFile f : largeFiles) {
            if (f.fileSize > 50L * 1024 * 1024) { largeBytes += f.fileSize; largeCount++; }
        }
        if (largeCount > 0) {
            final int fc = largeCount;
            addReclaimRow(reclaimContainer,
                    fc + " large files — " + formatSize(largeBytes) + " total",
                    "Review", () -> {
                        Intent i = new Intent(this, HubFileBrowserActivity.class);
                        i.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Large Files");
                        startActivity(i);
                    });
        }

        if (dupes == 0 && largeCount == 0) {
            addInfoRow(reclaimContainer, "✅ No immediate reclaim opportunities found.");
        }

        // File age distribution
        LinearLayout ageContainer = findViewById(R.id.ageDistContainer);
        buildAgeDistribution(ageContainer, allFiles);

        // File type distribution
        LinearLayout typeContainer = findViewById(R.id.typeDistContainer);
        buildTypeDistribution(typeContainer);

        // Source breakdown
        LinearLayout sourceContainer = findViewById(R.id.sourceBreakdownContainer);
        buildSourceBreakdown(sourceContainer, allFiles);

        // Largest files
        LinearLayout largestContainer = findViewById(R.id.largestFilesContainer);
        buildLargestFiles(largestContainer);

        // Auto backup readiness
        List<HubFile> noBackup = repo.getFilesWithNoBackup();
        TextView tvBackup = findViewById(R.id.tvBackupReadiness);
        if (noBackup.isEmpty()) {
            tvBackup.setText("✅ All files appear backed up or externally sourced.");
            tvBackup.setTextColor(Color.parseColor("#22C55E"));
        } else {
            tvBackup.setText("⚠️ " + noBackup.size() + " file(s) with no detected backup.");
            tvBackup.setTextColor(Color.parseColor("#F59E0B"));
        }

        // ── Anomaly Alerts ──────────────────────────────────────────────────
        LinearLayout anomalyContainer = buildAnomalySection(root, allFiles);
        root.addView(anomalyContainer);
    }

    private LinearLayout buildAnomalySection(LinearLayout root, List<HubFile> allFiles) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(20f);
        card.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⚠️ File Anomaly Alerts");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvTitle);

        List<HubAnomalyDetector.Anomaly> anomalies = HubAnomalyDetector.detect(allFiles);

        // Save externally-modified flags back to repo
        for (HubFile f : allFiles) if (f.modifiedExternally) repo.updateFile(f);

        if (anomalies.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("✅ No anomalies detected.");
            tv.setTextColor(Color.parseColor("#22C55E"));
            tv.setTextSize(13);
            tv.setPadding(0, 12, 0, 0);
            card.addView(tv);
        } else {
            for (HubAnomalyDetector.Anomaly a : anomalies) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, 10, 0, 10);

                TextView tvLabel = new TextView(this);
                tvLabel.setText(a.getEmoji() + "  " + a.title);
                tvLabel.setTextColor(Color.parseColor("#F59E0B"));
                tvLabel.setTextSize(13);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(tvLabel);

                TextView tvDetail = new TextView(this);
                tvDetail.setText(a.detail);
                tvDetail.setTextColor(Color.parseColor("#D1D5DB"));
                tvDetail.setTextSize(12);
                row.addView(tvDetail);

                card.addView(row);
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#374151"));
                card.addView(divider);
            }
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(24, 24, 24, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void buildAgeDistribution(LinearLayout container, List<HubFile> allFiles) {
        Calendar cal = Calendar.getInstance();
        long now = System.currentTimeMillis();
        long[] monthCounts = new long[3];
        String[] monthLabels = new String[3];
        SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

        for (int m = 0; m < 3; m++) {
            cal.setTimeInMillis(now);
            cal.add(Calendar.MONTH, -m);
            monthLabels[m] = sdf.format(cal.getTime());
            long start = getStartOfMonth(cal);
            cal.add(Calendar.MONTH, 1);
            long end = getStartOfMonth(cal);
            for (HubFile f : allFiles) {
                if (f.importedAt >= start && f.importedAt < end) monthCounts[m]++;
            }
        }

        for (int m = 2; m >= 0; m--) {
            addInfoRow(container, monthLabels[m] + ": " + monthCounts[m] + " file(s)");
        }
    }

    private long getStartOfMonth(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void buildTypeDistribution(LinearLayout container) {
        Map<HubFile.FileType, Long> breakdown = repo.getStorageBreakdown();
        long total = repo.getTotalTrackedBytes();
        if (total == 0) { addInfoRow(container, "No files tracked yet."); return; }
        for (Map.Entry<HubFile.FileType, Long> entry : breakdown.entrySet()) {
            if (entry.getValue() == 0) continue;
            int pct = (int) (entry.getValue() * 100 / total);
            addInfoRow(container, entry.getKey().name() + ": " + pct + "% (" + formatSize(entry.getValue()) + ")");
        }
    }

    private void buildSourceBreakdown(LinearLayout container, List<HubFile> allFiles) {
        Map<HubFile.Source, Integer> counts = new java.util.HashMap<>();
        for (HubFile.Source s : HubFile.Source.values()) counts.put(s, 0);
        for (HubFile f : allFiles) if (f.source != null) counts.put(f.source, counts.get(f.source) + 1);
        for (Map.Entry<HubFile.Source, Integer> e : counts.entrySet()) {
            if (e.getValue() == 0) continue;
            addInfoRow(container, e.getKey().name() + ": " + e.getValue() + " file(s)");
        }
        if (allFiles.isEmpty()) addInfoRow(container, "No files tracked yet.");
    }

    private void buildLargestFiles(LinearLayout container) {
        List<HubFile> largest = repo.getLargestFiles(10);
        if (largest.isEmpty()) { addInfoRow(container, "No files tracked yet."); return; }
        for (HubFile f : largest) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 8, 0, 8);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            String name = f.displayName != null && !f.displayName.isEmpty() ? f.displayName : f.originalFileName;
            tvName.setText(f.getTypeEmoji() + " " + name);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(13f);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView tvSize = new TextView(this);
            tvSize.setText(f.getFormattedSize());
            tvSize.setTextColor(Color.parseColor("#94A3B8"));
            tvSize.setTextSize(12f);
            tvSize.setPadding(8, 0, 8, 0);

            row.addView(tvName);
            row.addView(tvSize);
            container.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(Color.parseColor("#1E293B"));
            container.addView(divider);
        }
    }

    private void addBreakdownRow(LinearLayout container, String label, String value, int color, boolean highlighted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        row.setLayoutParams(lp);
        row.setPadding(12, 10, 12, 10);
        if (highlighted) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setCornerRadius(8f);
            row.setBackground(bg);
        }

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#CBD5E1"));
        tvLabel.setTextSize(13f);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(color);
        tvValue.setTextSize(13f);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    private void addReclaimRow(LinearLayout container, String text, String btnText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        row.setLayoutParams(lp);
        row.setPadding(12, 12, 12, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(10f);
        row.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13f);

        Button btn = new Button(this);
        btn.setText(btnText);
        btn.setTextSize(12f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7C3AED")));
        btn.setPadding(16, 4, 16, 4);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(8, 0, 0, 0);
        btn.setLayoutParams(btnLp);
        btn.setOnClickListener(v -> action.run());

        row.addView(tv);
        row.addView(btn);
        container.addView(row);
    }

    private void addInfoRow(LinearLayout container, String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(13f);
        container.addView(tv);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
