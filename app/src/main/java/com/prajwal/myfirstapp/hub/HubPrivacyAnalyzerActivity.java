package com.prajwal.myfirstapp.hub;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class HubPrivacyAnalyzerActivity extends AppCompatActivity {

    private static final String[] SENSITIVE_KEYWORDS = {
            "password", "passwd", "passport", "aadhar", "aadhaar", "pan card", "pan_card",
            "bank", "ssn", "confidential", "private", "secret", "credit card", "debit card",
            "social security", "tax", "salary", "medical", "diagnosis"
    };

    private HubFileRepository repo;
    private LinearLayout resultsContainer;
    private TextView tvScore;
    private TextView tvScoreDesc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 48);
        scroll.addView(root);
        setContentView(scroll);

        // Header
        LinearLayout header = makeRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        TextView tvTitle = makeTitle("🛡️ Privacy Analyzer");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(20));

        // Score card
        LinearLayout scoreCard = makeCard();
        scoreCard.setGravity(Gravity.CENTER);
        tvScore = new TextView(this);
        tvScore.setText("—");
        tvScore.setTextColor(Color.parseColor("#22C55E"));
        tvScore.setTextSize(48);
        tvScore.setTypeface(null, Typeface.BOLD);
        tvScore.setGravity(Gravity.CENTER);
        scoreCard.addView(tvScore);

        tvScoreDesc = new TextView(this);
        tvScoreDesc.setText("Privacy Score");
        tvScoreDesc.setTextColor(Color.parseColor("#9CA3AF"));
        tvScoreDesc.setTextSize(14);
        tvScoreDesc.setGravity(Gravity.CENTER);
        scoreCard.addView(tvScoreDesc);
        root.addView(scoreCard);
        root.addView(vspace(16));

        Button btnScan = makeButton("🔍 Scan Files", "#6366F1", "#FFFFFF");
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnScan.setLayoutParams(scanLp);
        btnScan.setOnClickListener(v -> runScan());
        root.addView(btnScan);
        root.addView(vspace(16));

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsContainer);

        runScan();
    }

    private void runScan() {
        resultsContainer.removeAllViews();
        tvScore.setText("…");
        tvScoreDesc.setText("Scanning…");

        Executors.newSingleThreadExecutor().execute(() -> {
            List<HubFile> all = repo.getAllFiles();
            List<FlaggedFile> flagged = new ArrayList<>();
            long now = System.currentTimeMillis();
            long twoYearsAgo = now - 2L * 365 * 24 * 3600 * 1000L;

            for (HubFile f : all) {
                String nameLower = (f.displayName != null ? f.displayName : f.originalFileName != null ? f.originalFileName : "").toLowerCase();
                String riskType = null;
                for (String kw : SENSITIVE_KEYWORDS) {
                    if (nameLower.contains(kw)) { riskType = "Sensitive keyword: \"" + kw + "\""; break; }
                }
                if (riskType == null && f.importedAt > 0 && f.importedAt < twoYearsAgo) {
                    riskType = "Old personal document (2+ years)";
                }
                if (riskType != null) flagged.add(new FlaggedFile(f, riskType));
            }

            int score = all.isEmpty() ? 100 :
                    Math.max(0, 100 - (int)((flagged.size() * 100.0) / all.size() * 1.5));

            new Handler(Looper.getMainLooper()).post(() -> renderResults(score, flagged));
        });
    }

    private void renderResults(int score, List<FlaggedFile> flagged) {
        String scoreColor = score >= 80 ? "#22C55E" : score >= 50 ? "#F59E0B" : "#EF4444";
        tvScore.setText(score + "%");
        tvScore.setTextColor(Color.parseColor(scoreColor));
        String desc = score >= 80 ? "Good — low privacy risk" :
                score >= 50 ? "Fair — some sensitive files detected" :
                        "Poor — many sensitive files found";
        tvScoreDesc.setText(desc);

        resultsContainer.removeAllViews();
        if (flagged.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("✅ No flagged files found!");
            tv.setTextColor(Color.parseColor("#22C55E")); tv.setTextSize(15);
            resultsContainer.addView(tv);
            repo.logAudit("PRIVACY_SCAN", "Score: " + score + "%, no flags");
            return;
        }

        TextView tvHeader = new TextView(this);
        tvHeader.setText("⚠️ " + flagged.size() + " flagged file(s)");
        tvHeader.setTextColor(Color.parseColor("#F59E0B"));
        tvHeader.setTextSize(15); tvHeader.setTypeface(null, Typeface.BOLD);
        resultsContainer.addView(tvHeader);
        resultsContainer.addView(vspace(10));

        for (FlaggedFile ff : flagged) {
            resultsContainer.addView(buildFlagCard(ff));
            resultsContainer.addView(vspace(6));
        }
        repo.logAudit("PRIVACY_SCAN", "Score: " + score + "%, flagged: " + flagged.size());
    }

    private View buildFlagCard(FlaggedFile ff) {
        LinearLayout card = makeCard();

        LinearLayout titleRow = makeRow();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(ff.file.getTypeEmoji()); tvEmoji.setTextSize(20);
        String name = ff.file.displayName != null ? ff.file.displayName : ff.file.originalFileName;
        if (name != null && name.length() > 30) name = name.substring(0, 27) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Unknown");
        tvName.setTextColor(Color.WHITE); tvName.setTextSize(14);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvEmoji);
        titleRow.addView(hspace(8));
        titleRow.addView(tvName);
        card.addView(titleRow);

        TextView tvRisk = new TextView(this);
        tvRisk.setText("⚠️ " + ff.riskType);
        tvRisk.setTextColor(Color.parseColor("#F59E0B")); tvRisk.setTextSize(12);
        card.addView(tvRisk);
        card.addView(vspace(8));

        Button btnVault = makeButton("🔒 Move to Vault (Suggested)", "#374151", "#E5E7EB");
        btnVault.setOnClickListener(v -> Toast.makeText(this,
                "Open Vault to import: " + ff.file.displayName, Toast.LENGTH_SHORT).show());
        card.addView(btnVault);
        return card;
    }

    private static class FlaggedFile {
        HubFile file; String riskType;
        FlaggedFile(HubFile f, String r) { file = f; riskType = r; }
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(12f);
        card.setBackground(bg); card.setPadding(16, 14, 16, 14);
        return card;
    }
    private LinearLayout makeRow() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r;
    }
    private TextView makeTitle(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE);
        tv.setTextSize(22); tv.setTypeface(null, Typeface.BOLD); return tv;
    }
    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(this); btn.setText(text); btn.setTextColor(Color.parseColor(fg));
        btn.setTextSize(13); btn.setPadding(24, 12, 24, 12);
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.parseColor(bg)); d.setCornerRadius(20f);
        btn.setBackground(d); return btn;
    }
    private View vspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)); return v;
    }
    private View hspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.MATCH_PARENT)); return v;
    }
}
