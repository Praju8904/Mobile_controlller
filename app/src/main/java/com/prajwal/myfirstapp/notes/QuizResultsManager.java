package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  QUIZ RESULTS MANAGER — Stores and retrieves quiz results for progress tracking.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class QuizResultsManager {

    private static final String PREFS_NAME = "quiz_results_data";
    private static final String KEY_RESULTS = "quiz_results";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class QuizResult {
        public String id;
        public String noteId;
        public String noteTitle;
        public int totalQuestions;
        public int correctAnswers;
        public int wrongAnswers;
        public long completedAt;
        public long durationSeconds;   // How long the quiz took
        public String quizType;        // "note_quiz", "flashcard_quiz", "mixed"

        public int getScorePercent() {
            if (totalQuestions == 0) return 0;
            return (int) ((correctAnswers / (float) totalQuestions) * 100);
        }

        public String getGrade() {
            int pct = getScorePercent();
            if (pct >= 90) return "A+";
            if (pct >= 80) return "A";
            if (pct >= 70) return "B";
            if (pct >= 60) return "C";
            if (pct >= 50) return "D";
            return "F";
        }

        public String getGradeEmoji() {
            int pct = getScorePercent();
            if (pct >= 90) return "🏆";
            if (pct >= 80) return "🌟";
            if (pct >= 70) return "👍";
            if (pct >= 60) return "📝";
            if (pct >= 50) return "💪";
            return "📚";
        }

        public String getFormattedDate() {
            return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(new Date(completedAt));
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("noteId", noteId != null ? noteId : "");
                json.put("noteTitle", noteTitle != null ? noteTitle : "");
                json.put("totalQuestions", totalQuestions);
                json.put("correctAnswers", correctAnswers);
                json.put("wrongAnswers", wrongAnswers);
                json.put("completedAt", completedAt);
                json.put("durationSeconds", durationSeconds);
                json.put("quizType", quizType != null ? quizType : "note_quiz");
            } catch (JSONException e) { e.printStackTrace(); }
            return json;
        }

        public static QuizResult fromJson(JSONObject json) {
            QuizResult r = new QuizResult();
            r.id = json.optString("id", java.util.UUID.randomUUID().toString());
            r.noteId = json.optString("noteId", "");
            r.noteTitle = json.optString("noteTitle", "");
            r.totalQuestions = json.optInt("totalQuestions", 0);
            r.correctAnswers = json.optInt("correctAnswers", 0);
            r.wrongAnswers = json.optInt("wrongAnswers", 0);
            r.completedAt = json.optLong("completedAt", 0);
            r.durationSeconds = json.optLong("durationSeconds", 0);
            r.quizType = json.optString("quizType", "note_quiz");
            return r;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private final Context context;

    public QuizResultsManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SAVE / LOAD
    // ═══════════════════════════════════════════════════════════════════════════════

    public void saveResult(QuizResult result) {
        if (result.id == null) result.id = java.util.UUID.randomUUID().toString();
        if (result.completedAt == 0) result.completedAt = System.currentTimeMillis();

        List<QuizResult> all = getAllResults();
        all.add(result);

        // Keep last 200
        while (all.size() > 200) all.remove(0);
        saveAll(all);
    }

    public List<QuizResult> getAllResults() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_RESULTS, "[]");
        List<QuizResult> results = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                results.add(QuizResult.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) { e.printStackTrace(); }
        return results;
    }

    public List<QuizResult> getResultsForNote(String noteId) {
        List<QuizResult> all = getAllResults();
        List<QuizResult> filtered = new ArrayList<>();
        for (QuizResult r : all) {
            if (noteId != null && noteId.equals(r.noteId)) filtered.add(r);
        }
        return filtered;
    }

    private void saveAll(List<QuizResult> results) {
        JSONArray arr = new JSONArray();
        for (QuizResult r : results) arr.put(r.toJson());
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_RESULTS, arr.toString())
                .apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════════

    public int getTotalQuizzesTaken() {
        return getAllResults().size();
    }

    public int getAverageScore() {
        List<QuizResult> all = getAllResults();
        if (all.isEmpty()) return 0;
        int totalPct = 0;
        for (QuizResult r : all) totalPct += r.getScorePercent();
        return totalPct / all.size();
    }

    public QuizResult getBestResult() {
        List<QuizResult> all = getAllResults();
        QuizResult best = null;
        for (QuizResult r : all) {
            if (best == null || r.getScorePercent() > best.getScorePercent()) best = r;
        }
        return best;
    }

    /** Get results from last 7 days for trend analysis. */
    public List<QuizResult> getRecentResults(int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24 * 60 * 60 * 1000);
        List<QuizResult> recent = new ArrayList<>();
        for (QuizResult r : getAllResults()) {
            if (r.completedAt >= cutoff) recent.add(r);
        }
        return recent;
    }

    public void clearAll() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_RESULTS)
                .apply();
    }
}
