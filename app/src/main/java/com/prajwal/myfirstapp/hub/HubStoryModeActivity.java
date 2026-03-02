package com.prajwal.myfirstapp.hub;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HubStoryModeActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private List<MonthSlide> slides = new ArrayList<>();
    private int currentIndex = 0;
    private LinearLayout slideContainer;
    private TextView tvNav;
    private Button btnPrev, btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setGravity(Gravity.CENTER);
        setContentView(root);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 48, 24, 16);
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("📖 File Story");
        tvTitle.setTextColor(Color.WHITE); tvTitle.setTextSize(22); tvTitle.setTypeface(null, Typeface.BOLD);
        header.addView(tvTitle);
        root.addView(header);

        // Slide content
        slideContainer = new LinearLayout(this);
        slideContainer.setOrientation(LinearLayout.VERTICAL);
        slideContainer.setGravity(Gravity.CENTER);
        slideContainer.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slideContainer.setLayoutParams(slp);
        root.addView(slideContainer);

        // Nav bar
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(24, 16, 24, 48);

        btnPrev = makeButton("← Prev", "#374151", "#E5E7EB");
        btnPrev.setOnClickListener(v -> navigate(-1));
        tvNav = new TextView(this);
        tvNav.setTextColor(Color.parseColor("#6B7280")); tvNav.setTextSize(13);
        tvNav.setGravity(Gravity.CENTER);
        tvNav.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnNext = makeButton("Next →", "#374151", "#E5E7EB");
        btnNext.setOnClickListener(v -> navigate(1));

        navBar.addView(btnPrev);
        navBar.addView(tvNav);
        navBar.addView(btnNext);
        root.addView(navBar);

        buildSlides();
        showSlide(0);
    }

    private void buildSlides() {
        List<HubFile> all = repo.getAllFiles();
        Map<String, List<HubFile>> byMonth = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        // Sort by importedAt
        Collections.sort(all, (a, b) -> Long.compare(a.importedAt, b.importedAt));
        for (HubFile f : all) {
            String key = sdf.format(new Date(f.importedAt));
            if (!byMonth.containsKey(key)) byMonth.put(key, new ArrayList<>());
            byMonth.get(key).add(f);
        }
        slides.clear();
        for (Map.Entry<String, List<HubFile>> entry : byMonth.entrySet()) {
            slides.add(new MonthSlide(entry.getKey(), entry.getValue()));
        }
        if (slides.isEmpty()) slides.add(new MonthSlide("No Files Yet", new ArrayList<>()));
    }

    private void showSlide(int index) {
        if (index < 0 || index >= slides.size()) return;
        currentIndex = index;
        MonthSlide slide = slides.get(index);

        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(180);
        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            public void onAnimationStart(android.view.animation.Animation a) {}
            public void onAnimationRepeat(android.view.animation.Animation a) {}
            public void onAnimationEnd(android.view.animation.Animation a) {
                populateSlide(slide);
                AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
                fadeIn.setDuration(300);
                slideContainer.startAnimation(fadeIn);
            }
        });
        slideContainer.startAnimation(fadeOut);

        tvNav.setText((index + 1) + " / " + slides.size());
        btnPrev.setAlpha(index > 0 ? 1f : 0.4f);
        btnNext.setAlpha(index < slides.size() - 1 ? 1f : 0.4f);
    }

    private void populateSlide(MonthSlide slide) {
        slideContainer.removeAllViews();
        List<HubFile> files = slide.files;

        // Month title
        TextView tvMonth = new TextView(this);
        tvMonth.setText(slide.monthLabel);
        tvMonth.setTextColor(Color.parseColor("#6366F1"));
        tvMonth.setTextSize(32); tvMonth.setTypeface(null, Typeface.BOLD);
        tvMonth.setGravity(Gravity.CENTER);
        slideContainer.addView(tvMonth);
        slideContainer.addView(vspace(8));

        // Count
        TextView tvCount = new TextView(this);
        tvCount.setText(files.size() + " files added");
        tvCount.setTextColor(Color.parseColor("#9CA3AF")); tvCount.setTextSize(16);
        tvCount.setGravity(Gravity.CENTER);
        slideContainer.addView(tvCount);
        slideContainer.addView(vspace(20));

        if (!files.isEmpty()) {
            // Top 3
            TextView tvTop3Label = new TextView(this);
            tvTop3Label.setText("Top Files");
            tvTop3Label.setTextColor(Color.parseColor("#6B7280")); tvTop3Label.setTextSize(12);
            tvTop3Label.setGravity(Gravity.CENTER); tvTop3Label.setAllCaps(true);
            slideContainer.addView(tvTop3Label);
            slideContainer.addView(vspace(8));

            int limit = Math.min(3, files.size());
            for (int i = 0; i < limit; i++) {
                HubFile f = files.get(i);
                String name = f.displayName != null ? f.displayName : f.originalFileName;
                if (name != null && name.length() > 28) name = name.substring(0, 25) + "…";
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, 4, 0, 4);
                TextView tvE = new TextView(this);
                tvE.setText(f.getTypeEmoji()); tvE.setTextSize(20);
                TextView tvN = new TextView(this);
                tvN.setText("  " + (name != null ? name : "Unknown"));
                tvN.setTextColor(Color.WHITE); tvN.setTextSize(15);
                row.addView(tvE); row.addView(tvN);
                slideContainer.addView(row);
            }

            slideContainer.addView(vspace(16));

            // Biggest file
            HubFile biggest = Collections.max(files, (a, b) -> Long.compare(a.fileSize, b.fileSize));
            String bigName = biggest.displayName != null ? biggest.displayName : biggest.originalFileName;
            if (bigName != null && bigName.length() > 28) bigName = bigName.substring(0, 25) + "…";
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1E293B")); bg.setCornerRadius(12f);
            LinearLayout bigCard = new LinearLayout(this);
            bigCard.setOrientation(LinearLayout.VERTICAL);
            bigCard.setBackground(bg); bigCard.setPadding(20, 14, 20, 14);
            bigCard.setGravity(Gravity.CENTER);
            TextView tvBigLabel = new TextView(this);
            tvBigLabel.setText("🏋️ Biggest File");
            tvBigLabel.setTextColor(Color.parseColor("#6B7280")); tvBigLabel.setTextSize(11); tvBigLabel.setGravity(Gravity.CENTER);
            TextView tvBigName = new TextView(this);
            tvBigName.setText(bigName != null ? bigName : "Unknown");
            tvBigName.setTextColor(Color.WHITE); tvBigName.setTextSize(14); tvBigName.setGravity(Gravity.CENTER);
            TextView tvBigSize = new TextView(this);
            tvBigSize.setText(biggest.getFormattedSize());
            tvBigSize.setTextColor(Color.parseColor("#22C55E")); tvBigSize.setTextSize(12); tvBigSize.setGravity(Gravity.CENTER);
            bigCard.addView(tvBigLabel); bigCard.addView(tvBigName); bigCard.addView(tvBigSize);
            slideContainer.addView(bigCard);
        }
    }

    private void navigate(int dir) {
        int next = currentIndex + dir;
        if (next >= 0 && next < slides.size()) showSlide(next);
    }

    private static class MonthSlide {
        String monthLabel; List<HubFile> files;
        MonthSlide(String m, List<HubFile> f) { monthLabel = m; files = f; }
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
