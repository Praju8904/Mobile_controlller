package com.prajwal.myfirstapp.hub;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;

/**
 * Advanced Analytics Dashboard — the most data-rich screen in the File Hub.
 *
 * Sections:
 *   1. File Growth Timeline   — dual-axis animated line chart
 *   2. Source Contribution    — storage per source per month (stacked bar)
 *   3. Access Heatmap         — GitHub-style year heatmap
 *   4. Top Files Leaderboard  — most accessed / largest / newest / most tagged
 *   5. Organisation Score Trend — health score over time
 *   6. Wasted Space Analysis  — recoverable storage breakdown
 *   7. File Discovery Insights — rotating smart observations
 *   8. Weekly Report toggle
 */
public class HubAnalyticsActivity extends AppCompatActivity {

    private static final int PERIOD_1M = 0, PERIOD_3M = 1, PERIOD_6M = 2, PERIOD_1Y = 3;
    private static final String[] PERIOD_LABELS = {"1M", "3M", "6M", "1Y"};

    private HubFileRepository repo;
    private HubLineChartView lineChart;
    private HubHeatmapView heatmap;

    private int currentPeriod = PERIOD_3M;
    private int leaderboardTab = 0; // 0=Most Accessed, 1=Largest, 2=Oldest, 3=Newest

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        buildUI();
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 48);
        scroll.addView(root);
        setContentView(scroll);

        // ── Back button + title ──────────────────────────────────────────────
        LinearLayout header = makeRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        TextView tvTitle = makeTitle("Analytics Dashboard");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(24));

        // ── 1. File Growth Timeline ──────────────────────────────────────────
        root.addView(makeSectionCard("📈 File Growth Timeline", c -> {
            // Period selector
            LinearLayout periodRow = makeRow();
            periodRow.setGravity(Gravity.CENTER);
            for (int i = 0; i < PERIOD_LABELS.length; i++) {
                final int period = i;
                Button btn = makeChip(PERIOD_LABELS[i], i == currentPeriod);
                btn.setOnClickListener(v -> {
                    currentPeriod = period;
                    lineChart.setData(buildGrowthData(periodDays(period)));
                    buildUI(); // rebuild to update chip states
                });
                periodRow.addView(btn);
                if (i < PERIOD_LABELS.length - 1) periodRow.addView(hspace(8));
            }
            c.addView(periodRow);
            c.addView(vspace(12));

            lineChart = new HubLineChartView(this);
            lineChart.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 400));
            lineChart.setData(buildGrowthData(periodDays(currentPeriod)));
            c.addView(lineChart);
            c.addView(vspace(8));

            // Legend
            LinearLayout legend = makeRow();
            legend.addView(makeDot(Color.parseColor("#3B82F6")));
            legend.addView(hspace(6));
            legend.addView(makeLabel("File count", "#3B82F6"));
            legend.addView(hspace(24));
            legend.addView(makeDot(Color.parseColor("#8B5CF6")));
            legend.addView(hspace(6));
            legend.addView(makeLabel("Storage (GB)", "#8B5CF6"));
            c.addView(legend);
        }));
        root.addView(vspace(16));

        // ── 2. Access Heatmap ────────────────────────────────────────────────
        root.addView(makeSectionCard("🗓️ File Access Heatmap", c -> {
            TextView sub = makeSubtitle("Your file access activity over the past year");
            c.addView(sub);
            c.addView(vspace(12));
            heatmap = new HubHeatmapView(this);
            heatmap.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            heatmap.setData(buildHeatmapData());
            c.addView(heatmap);
        }));
        root.addView(vspace(16));

        // ── 3. Top Files Leaderboard ─────────────────────────────────────────
        root.addView(makeSectionCard("🏆 Top Files", c -> {
            String[] tabLabels = {"Most Accessed", "Largest", "Oldest", "Newest"};
            LinearLayout tabs = makeRow();
            for (int i = 0; i < tabLabels.length; i++) {
                final int tab = i;
                Button btn = makeChip(tabLabels[i], i == leaderboardTab);
                btn.setOnClickListener(v -> {
                    leaderboardTab = tab;
                    buildUI();
                });
                tabs.addView(btn);
                if (i < tabLabels.length - 1) tabs.addView(hspace(6));
            }
            HorizontalScrollView tabScroll = new HorizontalScrollView(this);
            tabScroll.setHorizontalScrollBarEnabled(false);
            tabScroll.addView(tabs);
            c.addView(tabScroll);
            c.addView(vspace(12));

            List<HubFile> ranked = getLeaderboard(leaderboardTab);
            for (int i = 0; i < Math.min(10, ranked.size()); i++) {
                c.addView(makeLeaderboardRow(i + 1, ranked.get(i)));
                c.addView(vspace(6));
            }
            if (ranked.isEmpty()) {
                c.addView(makeSubtitle("No files yet."));
            }
        }));
        root.addView(vspace(16));

        // ── 4. Wasted Space Analysis ─────────────────────────────────────────
        root.addView(makeSectionCard("🗑️ Wasted Space Analysis", c -> {
            long dupeBytes = repo.getTotalWastedBytes();
            int dupeCount = repo.getTotalDuplicateCount();

            List<HubFile> all = repo.getAllFiles();
            long staleBytes = 0;
            int staleCount = 0;
            int zeroCount = 0;
            long sixMonthsAgo = System.currentTimeMillis() - 180L * 86_400_000L;
            for (HubFile f : all) {
                if (f.fileSize == 0) zeroCount++;
                if (f.lastAccessedAt < sixMonthsAgo) {
                    staleBytes += f.fileSize;
                    staleCount++;
                }
            }

            long totalRecoverable = dupeBytes + staleBytes;

            TextView tvTotal = new TextView(this);
            tvTotal.setText("Potentially recoverable: " + formatSize(totalRecoverable));
            tvTotal.setTextColor(Color.parseColor("#22C55E"));
            tvTotal.setTextSize(20);
            tvTotal.setTypeface(null, android.graphics.Typeface.BOLD);
            c.addView(tvTotal);
            c.addView(vspace(12));

            addWasteRow(c, "⚠️ Duplicate files",
                    dupeCount + " files — " + formatSize(dupeBytes),
                    "Review", () -> startActivity(new Intent(this, HubDuplicateManagerActivity.class)));

            addWasteRow(c, "📂 Unused files (6+ months)",
                    staleCount + " files — " + formatSize(staleBytes),
                    "Review", () -> {
                        Intent i = new Intent(this, HubFileBrowserActivity.class);
                        i.putExtra("filterStale", true);
                        startActivity(i);
                    });

            if (zeroCount > 0) {
                addWasteRow(c, "⛔ Zero-byte empty files",
                        zeroCount + " empty files", null, null);
            }
        }));
        root.addView(vspace(16));

        // ── 5. File Discovery Insights ───────────────────────────────────────
        root.addView(makeSectionCard("💡 File Insights", c -> {
            List<String[]> insights = buildInsights(); // {emoji, headline, detail}
            for (String[] insight : insights) {
                c.addView(makeInsightCard(insight[0], insight[1], insight[2]));
                c.addView(vspace(8));
            }
        }));
        root.addView(vspace(16));

        // ── 6. Weekly Report toggle ───────────────────────────────────────────
        root.addView(makeSectionCard("📅 Weekly Report", c -> {
            boolean enabled = HubWeeklyReportReceiver.isEnabled(this);
            TextView tvStatus = makeSubtitle(enabled
                    ? "Weekly report is ON — every Sunday at 7 PM"
                    : "Weekly report is OFF");
            c.addView(tvStatus);
            c.addView(vspace(12));
            Button btnToggle = makeButton(
                    enabled ? "Turn Off Weekly Report" : "Turn On Weekly Report",
                    enabled ? "#EF4444" : "#22C55E", "#FFFFFF");
            btnToggle.setOnClickListener(v -> {
                if (HubWeeklyReportReceiver.isEnabled(this)) {
                    HubWeeklyReportReceiver.cancel(this);
                } else {
                    HubWeeklyReportReceiver.schedule(this, Calendar.SUNDAY, 19);
                }
                buildUI();
            });
            c.addView(btnToggle);
        }));
        root.addView(vspace(16));

        // ── 7. File Story Mode ────────────────────────────────────────────────
        root.addView(makeSectionCard("📖 File Story", c -> {
            c.addView(makeSubtitle("Relive your file journey month by month, with animated slides."));
            c.addView(vspace(12));
            Button btnStory = makeButton("📖 View File Story", "#6366F1", "#FFFFFF");
            btnStory.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, HubStoryModeActivity.class)));
            c.addView(btnStory);
        }));
        root.addView(vspace(16));

        // ── 8. Share Frequency ────────────────────────────────────────────────
        root.addView(makeSectionCard("📤 Share Frequency", c -> {
            List<org.json.JSONObject> history = repo.getShareHistory();
            if (history.isEmpty()) {
                c.addView(makeSubtitle("No share history yet."));
                return;
            }
            // Count shares per month
            java.text.SimpleDateFormat monthSdf = new java.text.SimpleDateFormat("MMM yy", java.util.Locale.getDefault());
            java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (int i = history.size() - 1; i >= 0; i--) {
                long ts = history.get(i).optLong("timestamp", 0);
                String m = ts > 0 ? monthSdf.format(new java.util.Date(ts)) : "?";
                counts.put(m, counts.getOrDefault(m, 0) + 1);
            }
            int max = 1;
            for (int v : counts.values()) if (v > max) max = v;
            final int maxCount = max;
            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                LinearLayout barRow = makeRow();
                barRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                barRow.setPadding(0, 4, 0, 4);
                TextView tvMonth = makeLabel(entry.getKey(), "#9CA3AF");
                tvMonth.setMinWidth(120);
                barRow.addView(tvMonth);
                // Bar using weight
                LinearLayout barBg = makeRow();
                barBg.setLayoutParams(new LinearLayout.LayoutParams(0, 24, 1f));
                android.graphics.drawable.GradientDrawable bgD = new android.graphics.drawable.GradientDrawable();
                bgD.setColor(Color.parseColor("#1E293B")); bgD.setCornerRadius(6f);
                barBg.setBackground(bgD);
                // Filled portion
                android.view.View barFill = new android.view.View(this);
                float pct = (float) entry.getValue() / maxCount;
                barFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct));
                android.graphics.drawable.GradientDrawable barD = new android.graphics.drawable.GradientDrawable();
                barD.setColor(Color.parseColor("#6366F1")); barD.setCornerRadius(6f);
                barFill.setBackground(barD);
                // Empty portion
                android.view.View barEmpty = new android.view.View(this);
                barEmpty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - pct));
                barBg.addView(barFill); barBg.addView(barEmpty);
                barRow.addView(barBg);
                barRow.addView(hspace(8));
                TextView tvCount = makeLabel(entry.getValue() + "", "#6366F1");
                barRow.addView(tvCount);
                c.addView(barRow);
            }
        }));
    }

    // ─── Data builders ────────────────────────────────────────────────────────

    private List<HubLineChartView.DataPoint> buildGrowthData(int days) {
        List<HubFile> all = repo.getAllFiles();
        long now = System.currentTimeMillis();
        int step = Math.max(1, days / 20); // ~20 data points
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.US);
        List<HubLineChartView.DataPoint> points = new ArrayList<>();

        for (int d = days; d >= 0; d -= step) {
            long ts = now - (long) d * 86_400_000L;
            int count = 0;
            long bytes = 0;
            for (HubFile f : all) {
                if (f.importedAt <= ts) {
                    count++;
                    bytes += f.fileSize;
                }
            }
            String label = sdf.format(new java.util.Date(ts));
            points.add(new HubLineChartView.DataPoint(label, count, bytes / (1024f * 1024 * 1024)));
        }
        return points;
    }

    private Map<String, Integer> buildHeatmapData() {
        List<HubFile> all = repo.getAllFiles();
        Map<String, Integer> map = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long yearAgo = System.currentTimeMillis() - 365L * 86_400_000L;
        for (HubFile f : all) {
            if (f.lastAccessedAt < yearAgo) continue;
            String day = sdf.format(new java.util.Date(f.lastAccessedAt));
            map.put(day, map.getOrDefault(day, 0) + 1);
        }
        return map;
    }

    private List<HubFile> getLeaderboard(int tab) {
        List<HubFile> list = new ArrayList<>(repo.getAllFiles());
        switch (tab) {
            case 0: list.sort((a, b) -> Integer.compare(b.accessCount, a.accessCount)); break;
            case 1: list.sort((a, b) -> Long.compare(b.fileSize, a.fileSize)); break;
            case 2: list.sort((a, b) -> Long.compare(a.originalCreatedAt, b.originalCreatedAt)); break;
            case 3: list.sort((a, b) -> Long.compare(b.importedAt, a.importedAt)); break;
        }
        return list;
    }

    private List<String[]> buildInsights() {
        List<HubFile> all = repo.getAllFiles();
        List<String[]> insights = new ArrayList<>();
        long now = System.currentTimeMillis();
        long monthAgo = now - 30L * 86_400_000L;
        long sixMonthsAgo = now - 180L * 86_400_000L;

        int addedThisMonth = 0;
        long pdfBytesThisMonth = 0;
        int waCount = 0, shotCount = 0;
        long waBytesThisMonth = 0;
        long staleCount = 0;
        for (HubFile f : all) {
            if (f.importedAt > monthAgo) {
                addedThisMonth++;
                if (f.source == HubFile.Source.WHATSAPP) {
                    waCount++;
                    waBytesThisMonth += f.fileSize;
                }
                if (f.fileType == HubFile.FileType.SCREENSHOT) shotCount++;
                if (f.fileType == HubFile.FileType.PDF) pdfBytesThisMonth += f.fileSize;
            }
            if (f.lastAccessedAt < sixMonthsAgo) staleCount++;
        }

        if (waCount > 0) insights.add(new String[]{"💬",
                "WhatsApp sent " + waCount + " files this month",
                "That's " + formatSize(waBytesThisMonth) + " of WhatsApp content added."});
        if (pdfBytesThisMonth > 0) insights.add(new String[]{"📕",
                "PDF collection grew by " + formatSize(pdfBytesThisMonth) + " this month",
                "You added " + addedThisMonth + " files in the last 30 days."});
        if (staleCount > 0) insights.add(new String[]{"🕰️",
                staleCount + " files not accessed in 6+ months",
                "Consider reviewing and cleaning them up."});
        if (shotCount > 3) insights.add(new String[]{"📸",
                "Screenshots are growing fast",
                shotCount + " screenshots added this month."});
        if (insights.isEmpty()) insights.add(new String[]{"✨",
                "Looking good!",
                "No unusual patterns detected this month."});
        return insights;
    }

    private int periodDays(int period) {
        switch (period) {
            case PERIOD_1M: return 30;
            case PERIOD_3M: return 90;
            case PERIOD_6M: return 180;
            case PERIOD_1Y: return 365;
            default: return 90;
        }
    }

    // ─── Widget helpers ───────────────────────────────────────────────────────

    interface CardBuilder { void build(LinearLayout container); }

    private LinearLayout makeSectionCard(String title, CardBuilder builder) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(20f);
        card.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvTitle);
        card.addView(vspace(12));

        builder.build(card);
        return card;
    }

    private LinearLayout makeLeaderboardRow(int rank, HubFile file) {
        LinearLayout row = makeRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        TextView tvRank = new TextView(this);
        tvRank.setText("#" + rank);
        tvRank.setTextColor(rank == 1 ? Color.parseColor("#F59E0B")
                : rank == 2 ? Color.parseColor("#9CA3AF")
                : Color.parseColor("#6B7280"));
        tvRank.setTextSize(15);
        tvRank.setMinWidth(60);
        row.addView(tvRank);

        String typeEmoji = file.getTypeEmoji();
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(typeEmoji);
        tvEmoji.setTextSize(20);
        row.addView(tvEmoji);
        row.addView(hspace(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = file.displayName != null ? file.displayName : file.originalFileName;
        if (name != null && name.length() > 30) name = name.substring(0, 27) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        info.addView(tvName);

        String detail = "Accessed " + file.accessCount + "×  •  " + file.getFormattedSize();
        TextView tvDetail = new TextView(this);
        tvDetail.setText(detail);
        tvDetail.setTextColor(Color.parseColor("#9CA3AF"));
        tvDetail.setTextSize(12);
        info.addView(tvDetail);

        row.addView(info);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileViewerActivity.class);
            i.putExtra("fileId", file.id);
            startActivity(i);
        });
        return row;
    }

    private View makeInsightCard(String emoji, String headline, String detail) {
        LinearLayout card = makeRow();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(16, 16, 16, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111827"));
        bg.setCornerRadius(12f);
        card.setBackground(bg);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(24);
        card.addView(tvEmoji);
        card.addView(hspace(16));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView tvH = new TextView(this);
        tvH.setText(headline);
        tvH.setTextColor(Color.WHITE);
        tvH.setTextSize(14);
        tvH.setTypeface(null, android.graphics.Typeface.BOLD);
        text.addView(tvH);

        TextView tvD = new TextView(this);
        tvD.setText(detail);
        tvD.setTextColor(Color.parseColor("#9CA3AF"));
        tvD.setTextSize(12);
        text.addView(tvD);

        card.addView(text);
        return card;
    }

    private void addWasteRow(LinearLayout parent, String label, String detail,
                              String action, Runnable onClick) {
        LinearLayout row = makeRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(14);
        info.addView(tvLabel);

        TextView tvDetail = new TextView(this);
        tvDetail.setText(detail);
        tvDetail.setTextColor(Color.parseColor("#9CA3AF"));
        tvDetail.setTextSize(12);
        info.addView(tvDetail);

        row.addView(info);

        if (action != null && onClick != null) {
            Button btn = makeButton(action, "#3B82F6", "#FFFFFF");
            btn.setOnClickListener(v -> onClick.run());
            row.addView(btn);
        }
        parent.addView(row);
    }

    // ─── Low-level UI primitives ──────────────────────────────────────────────

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private TextView makeTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView makeSubtitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setTextSize(13);
        return tv;
    }

    private TextView makeLabel(String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(13);
        return tv;
    }

    private Button makeButton(String text, String bgColor, String textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(textColor));
        btn.setTextSize(13);
        btn.setPadding(24, 12, 24, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgColor));
        bg.setCornerRadius(20f);
        btn.setBackground(bg);
        return btn;
    }

    private Button makeChip(String text, boolean selected) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(selected ? Color.WHITE : Color.parseColor("#9CA3AF"));
        btn.setTextSize(12);
        btn.setPadding(20, 8, 20, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? Color.parseColor("#3B82F6") : Color.parseColor("#1F2937"));
        bg.setCornerRadius(16f);
        btn.setBackground(bg);
        return btn;
    }

    private View makeDot(int color) {
        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        dot.setBackground(bg);
        return dot;
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
