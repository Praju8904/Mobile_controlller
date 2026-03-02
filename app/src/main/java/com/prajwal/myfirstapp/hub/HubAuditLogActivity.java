package com.prajwal.myfirstapp.hub;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HubAuditLogActivity extends AppCompatActivity {

    private static final String[] ACTION_FILTERS = {"All", "SHARE", "VIEW", "DELETE", "MOVE", "SEARCH", "SETTINGS", "PRIVACY_SCAN"};

    private HubFileRepository repo;
    private LinearLayout listContainer;
    private int currentFilter = 0;

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
        TextView tvTitle = makeTitle("📒 Access Audit Log");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(16));

        // Filter row
        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = makeRow();
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < ACTION_FILTERS.length; i++) {
            final int idx = i;
            Button chip = makeChip(ACTION_FILTERS[i], i == currentFilter);
            chip.setOnClickListener(v -> { currentFilter = idx; reloadEntries(); });
            filterRow.addView(chip);
            filterRow.addView(hspace(6));
        }
        filterScroll.addView(filterRow);
        root.addView(filterScroll);
        root.addView(vspace(12));

        // Action buttons
        LinearLayout actRow = makeRow();
        Button btnExport = makeButton("📥 Export CSV", "#374151", "#E5E7EB");
        btnExport.setOnClickListener(v -> exportCsv());
        Button btnClear = makeButton("🗑 Clear Log", "#991B1B", "#FFFFFF");
        btnClear.setOnClickListener(v -> confirmClear());
        actRow.addView(btnExport);
        actRow.addView(hspace(8));
        actRow.addView(btnClear);
        root.addView(actRow);
        root.addView(vspace(16));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        loadEntries();
        repo.logAudit("VIEW", "Opened Audit Log");
    }

    private void reloadEntries() {
        // Reload entries with current filter applied
        loadEntries();
    }

    private void loadEntries() {
        listContainer.removeAllViews();
        List<JSONObject> all = repo.getAuditLog();
        String filter = ACTION_FILTERS[currentFilter];

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
        int count = 0;
        for (JSONObject obj : all) {
            String action = obj.optString("action", "");
            if (!filter.equals("All") && !action.equals(filter)) continue;
            listContainer.addView(buildRow(obj, sdf));
            listContainer.addView(vspace(4));
            count++;
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("No audit entries for this filter.");
            empty.setTextColor(Color.parseColor("#6B7280")); empty.setTextSize(14);
            listContainer.addView(empty);
        }
    }

    private View buildRow(JSONObject obj, SimpleDateFormat sdf) {
        LinearLayout row = makeRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(8f);
        row.setBackground(bg);
        row.setPadding(14, 10, 14, 10);

        String action = obj.optString("action", "—");
        String detail = obj.optString("detail", "—");
        long ts = obj.optLong("timestamp", 0);

        String actionColor = actionColor(action);
        TextView tvAction = new TextView(this);
        tvAction.setText(action);
        tvAction.setTextColor(Color.parseColor(actionColor));
        tvAction.setTextSize(11); tvAction.setTypeface(null, Typeface.BOLD);
        tvAction.setMinWidth(160);
        row.addView(tvAction);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvDetail = new TextView(this);
        String d = detail.length() > 50 ? detail.substring(0, 47) + "…" : detail;
        tvDetail.setText(d); tvDetail.setTextColor(Color.WHITE); tvDetail.setTextSize(12);
        TextView tvTs = new TextView(this);
        tvTs.setText(ts > 0 ? sdf.format(new Date(ts)) : "—");
        tvTs.setTextColor(Color.parseColor("#6B7280")); tvTs.setTextSize(10);
        info.addView(tvDetail); info.addView(tvTs);
        row.addView(info);
        return row;
    }

    private String actionColor(String action) {
        if (action == null) return "#9CA3AF";
        switch (action) {
            case "DELETE": return "#EF4444";
            case "SHARE": return "#3B82F6";
            case "VIEW": return "#22C55E";
            case "SETTINGS": return "#F59E0B";
            case "PRIVACY_SCAN": return "#8B5CF6";
            default: return "#9CA3AF";
        }
    }

    private void exportCsv() {
        try {
            List<JSONObject> all = repo.getAuditLog();
            StringBuilder csv = new StringBuilder("timestamp,action,detail\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            for (JSONObject obj : all) {
                long ts = obj.optLong("timestamp", 0);
                String action = obj.optString("action", "").replace(",", ";");
                String detail = obj.optString("detail", "").replace(",", ";");
                csv.append(sdf.format(new Date(ts))).append(",")
                        .append(action).append(",").append(detail).append("\n");
            }
            File dir = getCacheDir();
            File f = new File(dir, "audit_log.csv");
            FileWriter fw = new FileWriter(f);
            fw.write(csv.toString());
            fw.close();

            android.net.Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Export Audit Log"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Audit Log")
                .setMessage("All audit entries will be permanently deleted. Continue?")
                .setPositiveButton("Clear", (d, w) -> {
                    repo.clearAuditLog();
                    loadEntries();
                    Toast.makeText(this, "Audit log cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
    private Button makeChip(String text, boolean sel) {
        Button btn = new Button(this); btn.setText(text); btn.setTextSize(11);
        btn.setTextColor(sel ? Color.WHITE : Color.parseColor("#9CA3AF")); btn.setPadding(16, 6, 16, 6);
        GradientDrawable d = new GradientDrawable();
        d.setColor(sel ? Color.parseColor("#6366F1") : Color.parseColor("#374151")); d.setCornerRadius(14f);
        btn.setBackground(d); return btn;
    }
    private View vspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)); return v;
    }
    private View hspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.MATCH_PARENT)); return v;
    }
}
