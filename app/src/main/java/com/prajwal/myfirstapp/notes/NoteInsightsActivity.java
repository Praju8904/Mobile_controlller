package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE INSIGHTS ACTIVITY — Analytics dashboard for all notes.
 *  Shows writing stats, activity heatmap, category distribution, and top tags.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class NoteInsightsActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private NoteRepository noteRepository;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_insights);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        noteRepository = new NoteRepository(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadInsights();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadInsights() {
        NoteInsightsManager.Insights insights = NoteInsightsManager.generateInsights(noteRepository.getAllNotes());

        // ── Summary card ──
        TextView tvTotalNotes = findViewById(R.id.tvTotalNotes);
        TextView tvTotalWords = findViewById(R.id.tvTotalWords);
        TextView tvAvgLength = findViewById(R.id.tvAvgWords);
        tvTotalNotes.setText(String.valueOf(insights.totalNotes));
        tvTotalWords.setText(formatNumber(insights.totalWords));
        tvAvgLength.setText(formatNumber(insights.avgWordsPerNote));

        // ── Writing Pace ──
        TextView tvWritingPace = findViewById(R.id.tvWritingPace);
        tvWritingPace.setText(insights.writingPace);

        // ── Most productive time ──
        TextView tvProductiveTime = findViewById(R.id.tvProductiveTime);
        tvProductiveTime.setText(insights.mostProductiveTime);

        // ── Activity Heatmap (30 days) ──
        LinearLayout heatmapContainer = findViewById(R.id.layoutHeatmap);
        heatmapContainer.removeAllViews();
        buildHeatmap(heatmapContainer, insights.activityHeatmap);

        // ── Categories ──
        RecyclerView rvCategories = findViewById(R.id.rvCategories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(new CategoryStatAdapter(insights.categoryStats));

        // ── Top Tags ──
        ChipGroup chipGroupTags = findViewById(R.id.chipGroupTags);
        chipGroupTags.removeAllViews();
        for (NoteInsightsManager.TagStat tag : insights.tagStats) {
            Chip chip = new Chip(this);
            chip.setText(tag.tag + " (" + tag.count + ")");
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(android.R.color.white);
            chip.setTextColor(0xFFF1F5F9);
            chip.setTextSize(12f);
            chip.setClickable(false);
            chipGroupTags.addView(chip);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HEATMAP
    // ═══════════════════════════════════════════════════════════════════════════════

    private void buildHeatmap(LinearLayout container, List<NoteInsightsManager.ActivityDay> days) {
        // Find max activity for color scaling
        int maxActivity = 1;
        for (NoteInsightsManager.ActivityDay d : days) {
            if (d.noteCount > maxActivity) maxActivity = d.noteCount;
        }

        // Create a horizontal row of small squares
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());

        for (int i = 0; i < days.size(); i++) {
            NoteInsightsManager.ActivityDay day = days.get(i);
            View cell = new View(this);
            int size = (int) (16 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            int margin = (int) (2 * getResources().getDisplayMetrics().density);
            lp.setMargins(margin, margin, margin, margin);
            cell.setLayoutParams(lp);
            cell.setBackgroundColor(getHeatmapColor(day.noteCount, maxActivity));

            // Tooltip-like info
            cell.setOnClickListener(v -> {
                android.widget.Toast.makeText(this, day.date + ": " + day.noteCount + " note(s)", android.widget.Toast.LENGTH_SHORT).show();
            });

            row.addView(cell);

            // Wrap to new line every 7 days
            if ((i + 1) % 7 == 0 || i == days.size() - 1) {
                container.addView(row);
                if (i < days.size() - 1) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER);
                }
            }
        }
    }

    private int getHeatmapColor(int count, int max) {
        if (count == 0) return 0xFF1E293B; // empty
        float ratio = Math.min(1f, (float) count / max);
        // Gradient from dark amber to bright amber
        int r = (int) (30 + ratio * (245 - 30));
        int g = (int) (41 + ratio * (158 - 41));
        int b = (int) (59 + ratio * (11 - 59));
        return Color.rgb(r, g, b);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UTILS
    // ═══════════════════════════════════════════════════════════════════════════════

    private String formatNumber(int n) {
        if (n >= 1000) return String.format(Locale.getDefault(), "%.1fk", n / 1000f);
        return String.valueOf(n);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CATEGORY STAT ADAPTER
    // ═══════════════════════════════════════════════════════════════════════════════

    private class CategoryStatAdapter extends RecyclerView.Adapter<CategoryStatAdapter.VH> {
        private final List<NoteInsightsManager.CategoryStat> stats;

        CategoryStatAdapter(List<NoteInsightsManager.CategoryStat> stats) {
            this.stats = stats;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_stat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            NoteInsightsManager.CategoryStat stat = stats.get(position);
            holder.colorDot.setBackgroundColor(getCategoryColor(stat.category));
            holder.tvName.setText(stat.category);
            holder.tvCount.setText(String.valueOf(stat.count));

            // Calculate percentage
            int total = 0;
            for (NoteInsightsManager.CategoryStat s : stats) total += s.count;
            int pct = total > 0 ? (stat.count * 100 / total) : 0;
            holder.tvPercentage.setText(pct + "%");
        }

        @Override
        public int getItemCount() { return stats.size(); }

        class VH extends RecyclerView.ViewHolder {
            View colorDot;
            TextView tvName, tvCount, tvPercentage;
            VH(View v) {
                super(v);
                colorDot = v.findViewById(R.id.viewCategoryColor);
                tvName = v.findViewById(R.id.tvCategoryName);
                tvCount = v.findViewById(R.id.tvCategoryCount);
                tvPercentage = v.findViewById(R.id.tvCategoryPercent);
            }
        }
    }

    private int getCategoryColor(String category) {
        if (category == null) return 0xFF94A3B8;
        switch (category.toLowerCase()) {
            case "study": return 0xFF3B82F6;
            case "work": return 0xFFF59E0B;
            case "personal": return 0xFF10B981;
            case "ideas": return 0xFF8B5CF6;
            case "finance": return 0xFFEF4444;
            case "health": return 0xFF06B6D4;
            default: return 0xFF94A3B8;
        }
    }
}
