package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  STUDY PROGRESS TRACKER — Aggregated analytics for study performance.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Combines data from FlashcardManager, StudySessionManager, and QuizResultsManager
 *  into unified progress metrics and visualizable data.
 *
 *  Metrics:
 *  • Daily/weekly/monthly study time
 *  • Cards mastered over time
 *  • Quiz score trends
 *  • Retention curve
 *  • Strengths and weaknesses (by topic/deck)
 *  • Achievement badges
 */
public class StudyProgressTracker {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class ProgressSnapshot {
        public int totalCards;
        public int masteredCards;
        public int dueCards;
        public int overallRetention;   // %
        public int totalQuizzes;
        public int avgQuizScore;       // %
        public int totalFocusMinutes;
        public int dailyStreak;
        public int totalStudySessions;
        public List<DayProgress> weeklyProgress;  // Last 7 days
        public List<Achievement> achievements;

        public float getMasteryPercent() {
            if (totalCards == 0) return 0;
            return (masteredCards / (float) totalCards) * 100;
        }
    }

    public static class DayProgress {
        public String date;       // "Mon", "Tue", etc.
        public String fullDate;   // "2024-01-15"
        public int focusMinutes;
        public int cardsReviewed;
        public int quizScore;     // Average % or 0 if none

        public DayProgress(String date, String fullDate) {
            this.date = date;
            this.fullDate = fullDate;
        }
    }

    public static class Achievement {
        public String id;
        public String title;
        public String description;
        public String icon;
        public boolean unlocked;
        public long unlockedAt;

        public Achievement(String id, String title, String description, String icon) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
        }
    }

    public static class TopicStrength {
        public String topic;       // Deck name or category
        public int totalCards;
        public int masteredCards;
        public int avgRetention;
        public String strengthLevel;  // "Strong", "Moderate", "Weak"

        public TopicStrength(String topic) {
            this.topic = topic;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PREFS
    // ═══════════════════════════════════════════════════════════════════════════════
    private static final String PREFS_NAME = "study_progress_data";
    private static final String KEY_ACHIEVEMENTS = "achievements";

    private final Context context;

    public StudyProgressTracker(Context context) {
        this.context = context.getApplicationContext();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MAIN SNAPSHOT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Build a complete progress snapshot from all managers.
     */
    public ProgressSnapshot getSnapshot(FlashcardManager flashcardManager,
                                         StudySessionManager sessionManager,
                                         QuizResultsManager quizManager) {
        ProgressSnapshot snap = new ProgressSnapshot();

        // Flashcard stats
        snap.totalCards = flashcardManager.getTotalCount();
        snap.masteredCards = flashcardManager.getMasteredCount();
        snap.dueCards = flashcardManager.getDueCount();
        snap.overallRetention = flashcardManager.getOverallRetention();

        // Quiz stats
        snap.totalQuizzes = quizManager.getTotalQuizzesTaken();
        snap.avgQuizScore = quizManager.getAverageScore();

        // Session stats
        snap.totalFocusMinutes = sessionManager.getTotalFocusMinutes();
        snap.dailyStreak = sessionManager.getDailyStreak();
        snap.totalStudySessions = sessionManager.getSessionHistory().size();

        // Weekly progress
        snap.weeklyProgress = buildWeeklyProgress(sessionManager, quizManager);

        // Achievements
        snap.achievements = checkAchievements(snap);

        return snap;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WEEKLY PROGRESS
    // ═══════════════════════════════════════════════════════════════════════════════

    private List<DayProgress> buildWeeklyProgress(StudySessionManager sessionManager,
                                                    QuizResultsManager quizManager) {
        List<DayProgress> week = new ArrayList<>();
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", Locale.US);
        SimpleDateFormat fullFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6); // Start from 6 days ago

        List<StudySessionManager.StudySession> sessions = sessionManager.getSessionHistory();
        List<QuizResultsManager.QuizResult> quizzes = quizManager.getRecentResults(7);

        for (int i = 0; i < 7; i++) {
            String fullDate = fullFmt.format(cal.getTime());
            DayProgress day = new DayProgress(dayFmt.format(cal.getTime()), fullDate);

            // Sum focus minutes for this day
            for (StudySessionManager.StudySession s : sessions) {
                String sDate = fullFmt.format(new Date(s.startTime));
                if (fullDate.equals(sDate)) {
                    day.focusMinutes += s.focusMinutes;
                    day.cardsReviewed += s.cardsReviewed;
                }
            }

            // Average quiz score for this day
            int quizTotal = 0, quizCount = 0;
            for (QuizResultsManager.QuizResult q : quizzes) {
                String qDate = fullFmt.format(new Date(q.completedAt));
                if (fullDate.equals(qDate)) {
                    quizTotal += q.getScorePercent();
                    quizCount++;
                }
            }
            if (quizCount > 0) day.quizScore = quizTotal / quizCount;

            week.add(day);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        return week;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TOPIC STRENGTHS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze strengths/weaknesses by deck/topic.
     */
    public List<TopicStrength> getTopicStrengths(FlashcardManager flashcardManager) {
        Map<String, TopicStrength> topics = new HashMap<>();

        for (Flashcard card : flashcardManager.getAllCards()) {
            String deck = card.getDeckName() != null ? card.getDeckName() : "General";
            TopicStrength ts = topics.get(deck);
            if (ts == null) {
                ts = new TopicStrength(deck);
                topics.put(deck, ts);
            }
            ts.totalCards++;
            if (card.getInterval() >= 21) ts.masteredCards++;
            ts.avgRetention += card.getRetentionRate();
        }

        List<TopicStrength> result = new ArrayList<>();
        for (TopicStrength ts : topics.values()) {
            if (ts.totalCards > 0) ts.avgRetention /= ts.totalCards;
            if (ts.avgRetention >= 80) ts.strengthLevel = "Strong";
            else if (ts.avgRetention >= 50) ts.strengthLevel = "Moderate";
            else ts.strengthLevel = "Weak";
            result.add(ts);
        }

        // Sort: weakest first (so user sees what needs work)
        result.sort((a, b) -> a.avgRetention - b.avgRetention);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ACHIEVEMENTS
    // ═══════════════════════════════════════════════════════════════════════════════

    private List<Achievement> checkAchievements(ProgressSnapshot snap) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedJson = prefs.getString(KEY_ACHIEVEMENTS, "{}");
        JSONObject saved;
        try { saved = new JSONObject(savedJson); } catch (JSONException e) { saved = new JSONObject(); }

        List<Achievement> achievements = new ArrayList<>();

        // Define achievements
        achievements.add(new Achievement("first_card", "First Flashcard", "Create your first flashcard", "🃏"));
        achievements.add(new Achievement("ten_cards", "Card Collector", "Create 10 flashcards", "🎴"));
        achievements.add(new Achievement("fifty_cards", "Flashcard Pro", "Create 50 flashcards", "🏅"));
        achievements.add(new Achievement("first_quiz", "Quiz Starter", "Complete your first quiz", "📝"));
        achievements.add(new Achievement("perfect_quiz", "Perfect Score", "Get 100% on a quiz", "💯"));
        achievements.add(new Achievement("streak_3", "3-Day Streak", "Study 3 days in a row", "🔥"));
        achievements.add(new Achievement("streak_7", "Week Warrior", "Study 7 days in a row", "⚡"));
        achievements.add(new Achievement("streak_30", "Monthly Master", "Study 30 days in a row", "🏆"));
        achievements.add(new Achievement("focus_60", "Hour Hero", "Accumulate 60 minutes of focus", "⏱️"));
        achievements.add(new Achievement("focus_300", "Focus Master", "Accumulate 5 hours of focus", "🧠"));
        achievements.add(new Achievement("mastered_10", "10 Mastered", "Master 10 flashcards", "⭐"));
        achievements.add(new Achievement("mastered_50", "Knowledge King", "Master 50 flashcards", "👑"));

        // Check each
        for (Achievement a : achievements) {
            boolean wasUnlocked = saved.optBoolean(a.id, false);
            boolean nowUnlocked = false;

            switch (a.id) {
                case "first_card": nowUnlocked = snap.totalCards >= 1; break;
                case "ten_cards": nowUnlocked = snap.totalCards >= 10; break;
                case "fifty_cards": nowUnlocked = snap.totalCards >= 50; break;
                case "first_quiz": nowUnlocked = snap.totalQuizzes >= 1; break;
                case "perfect_quiz": nowUnlocked = snap.avgQuizScore >= 100; break; // simplified
                case "streak_3": nowUnlocked = snap.dailyStreak >= 3; break;
                case "streak_7": nowUnlocked = snap.dailyStreak >= 7; break;
                case "streak_30": nowUnlocked = snap.dailyStreak >= 30; break;
                case "focus_60": nowUnlocked = snap.totalFocusMinutes >= 60; break;
                case "focus_300": nowUnlocked = snap.totalFocusMinutes >= 300; break;
                case "mastered_10": nowUnlocked = snap.masteredCards >= 10; break;
                case "mastered_50": nowUnlocked = snap.masteredCards >= 50; break;
            }

            a.unlocked = wasUnlocked || nowUnlocked;
            if (a.unlocked && !wasUnlocked) {
                a.unlockedAt = System.currentTimeMillis();
                try { saved.put(a.id, true); } catch (JSONException e) { }
            }
        }

        // Save updated achievements
        prefs.edit().putString(KEY_ACHIEVEMENTS, saved.toString()).apply();
        return achievements;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RETENTION CURVE DATA
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get data points for a retention curve visualization.
     * Returns a list of [day_number, retention_percent] pairs.
     */
    public List<int[]> getRetentionCurveData(FlashcardManager flashcardManager) {
        List<int[]> curve = new ArrayList<>();

        // Group cards by their current interval to build curve
        Map<Integer, List<Integer>> intervalRetention = new HashMap<>();
        for (Flashcard card : flashcardManager.getAllCards()) {
            int interval = card.getInterval();
            // Bucket into day ranges
            int bucket;
            if (interval <= 1) bucket = 1;
            else if (interval <= 3) bucket = 3;
            else if (interval <= 7) bucket = 7;
            else if (interval <= 14) bucket = 14;
            else if (interval <= 30) bucket = 30;
            else bucket = 60;

            List<Integer> retentions = intervalRetention.get(bucket);
            if (retentions == null) {
                retentions = new ArrayList<>();
                intervalRetention.put(bucket, retentions);
            }
            retentions.add(card.getRetentionRate());
        }

        // Average retention at each bucket
        int[] buckets = {1, 3, 7, 14, 30, 60};
        for (int bucket : buckets) {
            List<Integer> retentions = intervalRetention.get(bucket);
            if (retentions != null && !retentions.isEmpty()) {
                int avg = 0;
                for (int r : retentions) avg += r;
                avg /= retentions.size();
                curve.add(new int[]{bucket, avg});
            }
        }

        return curve;
    }
}
