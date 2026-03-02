package com.prajwal.myfirstapp.hub;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Version History panel for a file.
 *
 * Opened from the file browser when a file has a version chain.
 * Shows all versions in chronological order (oldest → newest) with size, date,
 * and a version badge.
 *
 * Actions:
 *   • Open any version
 *   • "Keep Only Latest" — moves all but the newest to trash
 *   • "Compare" — opens two versions side-by-side in file viewer (placeholder)
 */
public class HubVersionHistoryActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private String chainId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        chainId = getIntent().getStringExtra("chainId");
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
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        header.addView(makeTitle("Version History"));
        root.addView(header);
        root.addView(vspace(24));

        if (chainId == null || chainId.isEmpty()) {
            root.addView(makeSub("No version chain found."));
            return;
        }

        HubVersionChain chain = repo.getVersionChain(chainId);
        if (chain == null || chain.fileIds.isEmpty()) {
            root.addView(makeSub("Version chain not found."));
            return;
        }

        TextView tvBaseName = makeSub("Base: " + chain.baseName);
        root.addView(tvBaseName);
        root.addView(vspace(8));

        TextView tvCount = new TextView(this);
        tvCount.setText(chain.getVersionCount() + " versions detected");
        tvCount.setTextColor(Color.parseColor("#3B82F6"));
        tvCount.setTextSize(15);
        root.addView(tvCount);
        root.addView(vspace(16));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        for (int i = 0; i < chain.fileIds.size(); i++) {
            String fid = chain.fileIds.get(i);
            HubFile f = repo.getFileById(fid);
            if (f == null) continue;
            boolean isLatest = (i == chain.fileIds.size() - 1);
            root.addView(makeVersionRow(f, i + 1, isLatest, sdf));
            root.addView(vspace(8));
        }

        root.addView(vspace(24));

        // Keep Only Latest button
        if (chain.fileIds.size() > 1) {
            Button btnKeepLatest = makeButton("🗑️ Keep Only Latest", "#EF4444", "#FFFFFF");
            btnKeepLatest.setOnClickListener(v -> keepOnlyLatest(chain));
            root.addView(btnKeepLatest);
        }
    }

    private View makeVersionRow(HubFile f, int versionNum, boolean isLatest,
                                 SimpleDateFormat sdf) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(20, 18, 20, 18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isLatest ? Color.parseColor("#1D4ED8") : Color.parseColor("#1E293B"));
        bg.setCornerRadius(16f);
        card.setBackground(bg);

        // Version number circle
        TextView tvNum = new TextView(this);
        tvNum.setText("v" + versionNum);
        tvNum.setTextColor(isLatest ? Color.WHITE : Color.parseColor("#9CA3AF"));
        tvNum.setTextSize(13);
        tvNum.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNum.setMinWidth(60);
        tvNum.setGravity(Gravity.CENTER);
        card.addView(tvNum);
        card.addView(hspace(12));

        // File info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name != null && name.length() > 32) name = name.substring(0, 29) + "…";
        TextView tvName = new TextView(this);
        tvName.setText((isLatest ? "⭐ " : "") + name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        info.addView(tvName);

        String detail = f.getFormattedSize() + "  •  "
                + (f.originalCreatedAt > 0 ? sdf.format(new Date(f.originalCreatedAt))
                : sdf.format(new Date(f.importedAt)));
        if (f.versionLabel != null && !f.versionLabel.isEmpty()) {
            detail = "[" + f.versionLabel + "]  " + detail;
        }
        TextView tvDetail = makeSub(detail);
        info.addView(tvDetail);

        card.addView(info);
        card.addView(hspace(8));

        // Open button
        Button btnOpen = makeButton("Open", "#374151", "#E5E7EB");
        btnOpen.setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileViewerActivity.class);
            i.putExtra("fileId", f.id);
            startActivity(i);
        });
        card.addView(btnOpen);

        return card;
    }

    private void keepOnlyLatest(HubVersionChain chain) {
        if (chain.fileIds.size() < 2) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Keep Only Latest Version?")
                .setMessage("This will delete all older versions (" + (chain.fileIds.size() - 1)
                        + " files). The latest version will be kept.")
                .setPositiveButton("Delete Older Versions", (d, w) -> {
                    // Delete all but the last
                    for (int i = 0; i < chain.fileIds.size() - 1; i++) {
                        repo.deleteFile(chain.fileIds.get(i));
                    }
                    android.widget.Toast.makeText(this,
                            "Older versions deleted.", android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
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
        btn.setPadding(18, 8, 18, 8);
        GradientDrawable bgD = new GradientDrawable();
        bgD.setColor(Color.parseColor(bg));
        bgD.setCornerRadius(14f);
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
}
