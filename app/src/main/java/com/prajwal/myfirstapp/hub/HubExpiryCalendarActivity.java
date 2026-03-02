package com.prajwal.myfirstapp.hub;

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

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class HubExpiryCalendarActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private LinearLayout listContainer;

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
        TextView tvTitle = makeTitle("📅 Expiry Calendar");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(16));

        // Info
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Files with expiry reminders, sorted by date.");
        tvInfo.setTextColor(Color.parseColor("#6B7280")); tvInfo.setTextSize(13);
        root.addView(tvInfo);
        root.addView(vspace(16));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        loadFiles();
        repo.logAudit("VIEW", "Opened Expiry Calendar");
    }

    private void loadFiles() {
        listContainer.removeAllViews();
        List<HubFile> all = repo.getAllFiles();
        List<HubFile> withExpiry = new ArrayList<>();
        for (HubFile f : all) {
            if (f.expiryReminderAt > 0) withExpiry.add(f);
        }
        Collections.sort(withExpiry, (a, b) -> Long.compare(a.expiryReminderAt, b.expiryReminderAt));

        if (withExpiry.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No files with expiry reminders set.\nUse the file viewer to set expiry dates.");
            empty.setTextColor(Color.parseColor("#6B7280")); empty.setTextSize(14);
            listContainer.addView(empty);
            return;
        }

        SimpleDateFormat monthSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        SimpleDateFormat daySdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        String lastMonth = "";
        long now = System.currentTimeMillis();

        for (HubFile f : withExpiry) {
            String month = monthSdf.format(new Date(f.expiryReminderAt));
            if (!month.equals(lastMonth)) {
                lastMonth = month;
                TextView tvMonth = new TextView(this);
                tvMonth.setText(month);
                tvMonth.setTextColor(Color.parseColor("#6366F1"));
                tvMonth.setTextSize(12); tvMonth.setTypeface(null, Typeface.BOLD);
                tvMonth.setAllCaps(false);
                listContainer.addView(tvMonth);
                listContainer.addView(vspace(6));
            }
            listContainer.addView(buildFileRow(f, now, daySdf));
            listContainer.addView(vspace(6));
        }
    }

    private View buildFileRow(HubFile f, long now, SimpleDateFormat sdf) {
        LinearLayout card = makeRow();
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(10f);
        card.setBackground(bg); card.setPadding(16, 12, 16, 12);
        card.setClickable(true); card.setFocusable(true);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(f.getTypeEmoji()); tvEmoji.setTextSize(20);
        card.addView(tvEmoji);
        card.addView(hspace(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name != null && name.length() > 32) name = name.substring(0, 29) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Unknown");
        tvName.setTextColor(Color.WHITE); tvName.setTextSize(14);
        info.addView(tvName);

        long diffMs = f.expiryReminderAt - now;
        long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);
        String expiryText;
        String expiryColor;
        if (diffDays > 0) {
            expiryText = "Expires in " + diffDays + " day" + (diffDays == 1 ? "" : "s") +
                    " (" + sdf.format(new Date(f.expiryReminderAt)) + ")";
            expiryColor = diffDays <= 7 ? "#F59E0B" : "#22C55E";
        } else {
            long absDays = Math.abs(diffDays);
            expiryText = "Expired " + absDays + " day" + (absDays == 1 ? "" : "s") + " ago";
            expiryColor = "#EF4444";
        }
        TextView tvExpiry = new TextView(this);
        tvExpiry.setText(expiryText); tvExpiry.setTextColor(Color.parseColor(expiryColor));
        tvExpiry.setTextSize(11);
        info.addView(tvExpiry);
        card.addView(info);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, HubFileBrowserActivity.class);
            intent.putExtra(HubFileBrowserActivity.EXTRA_TITLE, "Files");
            startActivity(intent);
        });
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
