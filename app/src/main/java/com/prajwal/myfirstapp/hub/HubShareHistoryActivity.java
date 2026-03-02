package com.prajwal.myfirstapp.hub;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HubShareHistoryActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private LinearLayout listContainer;
    private List<JSONObject> allHistory = new ArrayList<>();

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
        TextView tvTitle = makeTitle("📋 Share History");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(16));

        // Search
        EditText etSearch = new EditText(this);
        etSearch.setHint("Search by file name or method...");
        etSearch.setTextColor(Color.WHITE);
        etSearch.setHintTextColor(Color.parseColor("#6B7280"));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#1E293B"));
        searchBg.setCornerRadius(12f);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(16, 12, 16, 12);
        root.addView(etSearch);
        root.addView(vspace(16));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        allHistory = repo.getShareHistory();
        renderHistory(allHistory);

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s.toString().toLowerCase();
                if (q.isEmpty()) { renderHistory(allHistory); return; }
                List<JSONObject> filtered = new ArrayList<>();
                for (JSONObject obj : allHistory) {
                    String fn = obj.optString("fileName", "").toLowerCase();
                    String m = obj.optString("method", "").toLowerCase();
                    if (fn.contains(q) || m.contains(q)) filtered.add(obj);
                }
                renderHistory(filtered);
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    private void renderHistory(List<JSONObject> list) {
        listContainer.removeAllViews();
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No share history yet.");
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setTextSize(14);
            listContainer.addView(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
        String lastLabel = "";
        for (JSONObject obj : list) {
            long ts = obj.optLong("timestamp", 0);
            String dateLabel = sdf.format(new Date(ts)).substring(0, 6); // "Jan 1,"
            if (!dateLabel.equals(lastLabel)) {
                lastLabel = dateLabel;
                TextView tvDate = new TextView(this);
                tvDate.setText(sdf.format(new Date(ts)).substring(0, 12));
                tvDate.setTextColor(Color.parseColor("#6366F1"));
                tvDate.setTextSize(11);
                tvDate.setTypeface(null, Typeface.BOLD);
                listContainer.addView(tvDate);
                listContainer.addView(vspace(4));
            }
            listContainer.addView(buildHistoryRow(obj, sdf));
            listContainer.addView(vspace(6));
        }
    }

    private View buildHistoryRow(JSONObject obj, SimpleDateFormat sdf) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(10f);
        card.setBackground(bg);
        card.setPadding(16, 12, 16, 12);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText("📤"); tvEmoji.setTextSize(20);
        card.addView(tvEmoji);
        card.addView(hspace(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String fn = obj.optString("fileName", "Unknown");
        if (fn.length() > 35) fn = fn.substring(0, 32) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(fn); tvName.setTextColor(Color.WHITE); tvName.setTextSize(14);
        info.addView(tvName);

        long ts = obj.optLong("timestamp", 0);
        long size = obj.optLong("fileSize", 0);
        String method = obj.optString("method", "—");
        String meta = method + "  •  " + formatSize(size) + "  •  " +
                (ts > 0 ? sdf.format(new Date(ts)) : "—");
        TextView tvMeta = new TextView(this);
        tvMeta.setText(meta); tvMeta.setTextColor(Color.parseColor("#6B7280")); tvMeta.setTextSize(11);
        info.addView(tvMeta);

        card.addView(info);
        return card;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
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
