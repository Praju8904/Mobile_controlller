package com.prajwal.myfirstapp.notes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE INSIGHTS MANAGER — Aggregated analytics across all notes.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Generates insights like:
 *  • Most productive writing time
 *  • Most used categories and tags
 *  • Writing velocity trends
 *  • Content type distribution
 *  • Note creation frequency
 *  • Longest notes, shortest notes
 *  • Tag cloud data
 *  • Activity heatmap data
 */
public class NoteInsightsManager {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class Insights {
        public int totalNotes;
        public int totalWords;
        public int totalBlocks;
        public int avgWordsPerNote;
        public String mostProductiveTime;    // "Morning", "Afternoon", etc.
        public String mostUsedCategory;
        public String mostUsedTag;
        public List<CategoryStat> categoryStats;
        public List<TagStat> tagStats;
        public List<ActivityDay> activityHeatmap;   // Last 30 days
        public String writingPace;            // "Prolific", "Steady", "Occasional"
    }

    public static class CategoryStat {
        public String category;
        public int count;
        public String icon;

        public CategoryStat(String category, int count) {
            this.category = category;
            this.count = count;
        }
    }

    public static class TagStat {
        public String tag;
        public int count;

        public TagStat(String tag, int count) {
            this.tag = tag;
            this.count = count;
        }
    }

    public static class ActivityDay {
        public String date;    // "2024-01-15"
        public int noteCount;  // Notes created/edited that day
        public int intensity;  // 0-4 for heatmap coloring

        public ActivityDay(String date, int noteCount) {
            this.date = date;
            this.noteCount = noteCount;
            if (noteCount == 0) intensity = 0;
            else if (noteCount <= 2) intensity = 1;
            else if (noteCount <= 5) intensity = 2;
            else if (noteCount <= 10) intensity = 3;
            else intensity = 4;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GENERATE INSIGHTS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Generate comprehensive insights from all notes.
     */
    public static Insights generateInsights(List<Note> allNotes) {
        Insights insights = new Insights();
        if (allNotes == null || allNotes.isEmpty()) {
            insights.totalNotes = 0;
            insights.categoryStats = new ArrayList<>();
            insights.tagStats = new ArrayList<>();
            insights.activityHeatmap = new ArrayList<>();
            insights.writingPace = "Getting started";
            return insights;
        }

        insights.totalNotes = allNotes.size();

        // ── Word count & block count ──
        int totalWords = 0;
        int totalBlocks = 0;
        for (Note note : allNotes) {
            if (note.plainTextPreview != null) {
                totalWords += SmartNotesHelper.countWords(note.plainTextPreview);
            }
            if (note.blocksJson != null) {
                try {
                    List<ContentBlock> blocks = ContentBlock.fromJsonArray(note.blocksJson);
                    totalBlocks += blocks.size();
                } catch (Exception e) { /* ignore */ }
            }
        }
        insights.totalWords = totalWords;
        insights.totalBlocks = totalBlocks;
        insights.avgWordsPerNote = allNotes.size() > 0 ? totalWords / allNotes.size() : 0;

        // ── Category analysis ──
        Map<String, Integer> catCount = new HashMap<>();
        for (Note note : allNotes) {
            String cat = note.category != null ? note.category : "Uncategorized";
            catCount.put(cat, catCount.getOrDefault(cat, 0) + 1);
        }
        insights.categoryStats = new ArrayList<>();
        String topCat = null; int topCatCount = 0;
        for (Map.Entry<String, Integer> e : catCount.entrySet()) {
            CategoryStat stat = new CategoryStat(e.getKey(), e.getValue());
            stat.icon = getCategoryIcon(e.getKey());
            insights.categoryStats.add(stat);
            if (e.getValue() > topCatCount) {
                topCatCount = e.getValue();
                topCat = e.getKey();
            }
        }
        insights.categoryStats.sort((a, b) -> b.count - a.count);
        insights.mostUsedCategory = topCat;

        // ── Tag analysis ──
        Map<String, Integer> tagCount = new HashMap<>();
        for (Note note : allNotes) {
            if (note.tags != null) {
                for (String tag : note.tags) {
                    tagCount.put(tag, tagCount.getOrDefault(tag, 0) + 1);
                }
            }
        }
        insights.tagStats = new ArrayList<>();
        String topTag = null; int topTagCount = 0;
        for (Map.Entry<String, Integer> e : tagCount.entrySet()) {
            insights.tagStats.add(new TagStat(e.getKey(), e.getValue()));
            if (e.getValue() > topTagCount) {
                topTagCount = e.getValue();
                topTag = e.getKey();
            }
        }
        insights.tagStats.sort((a, b) -> b.count - a.count);
        if (insights.tagStats.size() > 20) {
            insights.tagStats = insights.tagStats.subList(0, 20);
        }
        insights.mostUsedTag = topTag;

        // ── Most productive time ──
        int[] hourBuckets = new int[4]; // Morning(5-12), Afternoon(12-17), Evening(17-21), Night(21-5)
        for (Note note : allNotes) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(note.createdAt);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (hour >= 5 && hour < 12) hourBuckets[0]++;
            else if (hour >= 12 && hour < 17) hourBuckets[1]++;
            else if (hour >= 17 && hour < 21) hourBuckets[2]++;
            else hourBuckets[3]++;
        }
        String[] timeLabels = {"Morning ☀️", "Afternoon 🌤️", "Evening 🌅", "Night 🌙"};
        int maxBucket = 0;
        for (int i = 1; i < 4; i++) if (hourBuckets[i] > hourBuckets[maxBucket]) maxBucket = i;
        insights.mostProductiveTime = timeLabels[maxBucket];

        // ── Activity heatmap (last 30 days) ──
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        Map<String, Integer> dailyActivity = new HashMap<>();
        for (Note note : allNotes) {
            String day = fmt.format(new java.util.Date(note.updatedAt));
            dailyActivity.put(day, dailyActivity.getOrDefault(day, 0) + 1);
        }
        insights.activityHeatmap = new ArrayList<>();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -29);
        for (int i = 0; i < 30; i++) {
            String dateStr = fmt.format(cal.getTime());
            int count = dailyActivity.getOrDefault(dateStr, 0);
            insights.activityHeatmap.add(new ActivityDay(dateStr, count));
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        // ── Writing pace ──
        int activeDays = 0;
        for (ActivityDay ad : insights.activityHeatmap) {
            if (ad.noteCount > 0) activeDays++;
        }
        if (activeDays >= 20) insights.writingPace = "Prolific 🔥";
        else if (activeDays >= 10) insights.writingPace = "Steady ✍️";
        else if (activeDays >= 3) insights.writingPace = "Occasional 📝";
        else insights.writingPace = "Getting started 🌱";

        return insights;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static String getCategoryIcon(String category) {
        if (category == null) return "📝";
        switch (category) {
            case "Study": return "📚";
            case "Work": return "💼";
            case "Personal": return "🏠";
            case "Ideas": return "💡";
            case "Finance": return "💰";
            case "Health": return "🏥";
            case "Travel": return "✈️";
            default: return "📝";
        }
    }
}
