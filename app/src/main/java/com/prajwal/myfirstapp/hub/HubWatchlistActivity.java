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
 * File Watchlist screen.
 *
 * Shows all files the user is watching for changes.
 * Files are added to the watchlist from the file detail / three-dot menu.
 * When a watched file is modified externally the anomaly detector surfaces it.
 *
 * Each row shows:
 *   • File emoji + name
 *   • Last change type (if any)
 *   • Last checked timestamp
 *   • Swipe-to-remove (simulated via long press dialog)
 */
public class HubWatchlistActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listContainer != null) {
            listContainer.removeAllViews();
            populateList();
        }
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
        TextView tvTitle = makeTitle("File Watchlist");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(8));

        TextView tvSub = makeSub("Long-press a file to remove it from the watchlist.");
        root.addView(tvSub);
        root.addView(vspace(24));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        populateList();
    }

    private void populateList() {
        List<HubFile> watched = getWatchedFiles();
        if (watched.isEmpty()) {
            TextView tv = makeSub("No files on your watchlist.\nAdd files via their detail screen → ⋮ → Add to Watchlist.");
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setPadding(0, 48, 0, 0);
            listContainer.addView(tv);
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        for (HubFile f : watched) {
            listContainer.addView(makeWatchRow(f, sdf));
            listContainer.addView(vspace(8));
        }
    }

    private View makeWatchRow(HubFile f, SimpleDateFormat sdf) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(16f);
        card.setBackground(bg);

        LinearLayout row = makeRow();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(f.getTypeEmoji());
        tvEmoji.setTextSize(22);
        row.addView(tvEmoji);
        row.addView(hspace(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name != null && name.length() > 35) name = name.substring(0, 32) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        info.addView(tvName);

        String status = f.modifiedExternally
                ? "⚠️ Modified externally"
                : "Watching — no changes";
        String lastChecked = "Last checked: " + sdf.format(new Date(f.updatedAt));
        TextView tvStatus = makeSub(status + "  •  " + lastChecked);
        tvStatus.setTextColor(f.modifiedExternally
                ? Color.parseColor("#F59E0B") : Color.parseColor("#22C55E"));
        info.addView(tvStatus);

        row.addView(info);
        card.addView(row);

        // Tap to open file
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileViewerActivity.class);
            i.putExtra("fileId", f.id);
            startActivity(i);
        });

        // Long-press to remove from watchlist
        card.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Remove from Watchlist")
                    .setMessage("Stop watching \"" + (f.displayName != null
                            ? f.displayName : f.originalFileName) + "\"?")
                    .setPositiveButton("Remove", (d, w) -> {
                        f.isWatchlisted = false;
                        repo.updateFile(f);
                        listContainer.removeAllViews();
                        populateList();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        return card;
    }

    private List<HubFile> getWatchedFiles() {
        List<HubFile> result = new ArrayList<>();
        for (HubFile f : repo.getAllFiles()) if (f.isWatchlisted) result.add(f);
        return result;
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
        btn.setPadding(20, 10, 20, 10);
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
}
