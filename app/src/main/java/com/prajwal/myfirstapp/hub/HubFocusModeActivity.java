package com.prajwal.myfirstapp.hub;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HubFocusModeActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private long focusStartTime;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView tvElapsed;
    private LinearLayout filesContainer;
    private boolean timerRunning = false;
    private String focusContext = "";

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
        btnBack.setOnClickListener(v -> { stopTimer(); finish(); });
        header.addView(btnBack);
        header.addView(hspace(16));
        TextView tvTitle = makeTitle("🎯 Focus Mode");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(20));

        // Context input
        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("What are you focusing on?");
        tvPrompt.setTextColor(Color.parseColor("#9CA3AF")); tvPrompt.setTextSize(14);
        root.addView(tvPrompt);
        root.addView(vspace(8));

        EditText etContext = new EditText(this);
        etContext.setHint("Project name or context label…");
        etContext.setTextColor(Color.WHITE);
        etContext.setHintTextColor(Color.parseColor("#6B7280"));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(Color.parseColor("#1E293B")); etBg.setCornerRadius(12f);
        etContext.setBackground(etBg); etContext.setPadding(16, 12, 16, 12);
        root.addView(etContext);
        root.addView(vspace(12));

        Button btnStart = makeButton("▶ Start Focus", "#6366F1", "#FFFFFF");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnStart.setLayoutParams(lp);
        root.addView(btnStart);
        root.addView(vspace(20));

        // Timer display (hidden until started)
        LinearLayout timerCard = makeCard();
        timerCard.setGravity(Gravity.CENTER);
        timerCard.setVisibility(View.GONE);
        tvElapsed = new TextView(this);
        tvElapsed.setText("00:00:00");
        tvElapsed.setTextColor(Color.parseColor("#6366F1"));
        tvElapsed.setTextSize(40); tvElapsed.setTypeface(null, Typeface.BOLD);
        tvElapsed.setGravity(Gravity.CENTER);
        TextView tvFocusLabel = new TextView(this);
        tvFocusLabel.setText("Focus time");
        tvFocusLabel.setTextColor(Color.parseColor("#9CA3AF")); tvFocusLabel.setTextSize(13);
        tvFocusLabel.setGravity(Gravity.CENTER);
        timerCard.addView(tvElapsed); timerCard.addView(tvFocusLabel);
        root.addView(timerCard);
        root.addView(vspace(16));

        // End Focus button (hidden until started)
        Button btnEnd = makeButton("⏹ End Focus", "#991B1B", "#FFFFFF");
        btnEnd.setLayoutParams(lp);
        btnEnd.setVisibility(View.GONE);
        btnEnd.setOnClickListener(v -> { stopTimer(); finish(); });
        root.addView(btnEnd);
        root.addView(vspace(16));

        // Files section
        TextView tvFilesHeader = new TextView(this);
        tvFilesHeader.setText("RELATED FILES");
        tvFilesHeader.setTextColor(Color.parseColor("#6366F1")); tvFilesHeader.setTextSize(11);
        tvFilesHeader.setTypeface(null, Typeface.BOLD); tvFilesHeader.setAllCaps(true);
        root.addView(tvFilesHeader);
        root.addView(vspace(8));

        filesContainer = new LinearLayout(this);
        filesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(filesContainer);

        btnStart.setOnClickListener(v -> {
            String ctx = etContext.getText().toString().trim();
            if (ctx.isEmpty()) { Toast.makeText(this, "Enter a context", Toast.LENGTH_SHORT).show(); return; }
            focusContext = ctx;
            focusStartTime = System.currentTimeMillis();
            startTimer();
            timerCard.setVisibility(View.VISIBLE);
            btnEnd.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            etContext.setEnabled(false);
            loadRelatedFiles(ctx);
            repo.logAudit("VIEW", "Started Focus Mode: " + ctx);
        });
    }

    private void loadRelatedFiles(String ctx) {
        filesContainer.removeAllViews();
        List<HubFile> all = repo.getAllFiles();
        List<HubFile> related = new ArrayList<>();
        String lower = ctx.toLowerCase();
        for (HubFile f : all) {
            String name = (f.displayName != null ? f.displayName : f.originalFileName != null ? f.originalFileName : "").toLowerCase();
            String note = (f.notes != null ? f.notes : "").toLowerCase();
            if (name.contains(lower) || note.contains(lower) ||
                    (f.projectId != null && f.projectId.toLowerCase().contains(lower))) {
                related.add(f);
            }
        }
        if (related.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No files match this context yet.");
            tv.setTextColor(Color.parseColor("#6B7280")); tv.setTextSize(13);
            filesContainer.addView(tv);
            return;
        }
        for (HubFile f : related) {
            filesContainer.addView(buildFileRow(f));
            filesContainer.addView(vspace(4));
        }
    }

    private View buildFileRow(HubFile f) {
        LinearLayout row = makeRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(8f);
        row.setBackground(bg); row.setPadding(14, 10, 14, 10);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(f.getTypeEmoji()); tvEmoji.setTextSize(18);
        row.addView(tvEmoji);
        row.addView(hspace(10));

        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name != null && name.length() > 35) name = name.substring(0, 32) + "…";
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "Unknown");
        tvName.setTextColor(Color.WHITE); tvName.setTextSize(13);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvName);

        TextView tvSize = new TextView(this);
        tvSize.setText(f.getFormattedSize());
        tvSize.setTextColor(Color.parseColor("#6B7280")); tvSize.setTextSize(11);
        row.addView(tvSize);
        return row;
    }

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;
            long elapsed = System.currentTimeMillis() - focusStartTime;
            long secs = elapsed / 1000;
            long h = secs / 3600; long m = (secs % 3600) / 60; long s = secs % 60;
            tvElapsed.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void startTimer() { timerRunning = true; timerHandler.post(timerTick); }
    private void stopTimer() { timerRunning = false; timerHandler.removeCallbacks(timerTick); }

    @Override
    protected void onDestroy() { super.onDestroy(); stopTimer(); }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(16f);
        card.setBackground(bg); card.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp); return card;
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
