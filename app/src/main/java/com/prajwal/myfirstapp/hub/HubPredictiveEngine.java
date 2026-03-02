package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight usage-pattern engine that tracks file access history and surfaces
 * predictive suggestions ("You might need this").
 *
 * Tracking dimensions:
 *   • Day of week (0=Sunday … 6=Saturday)
 *   • Hour of day (0–23)
 *   • Sequential access pairs (file B often opened right after file A)
 *
 * The engine needs at least {@link #MIN_SESSIONS_BEFORE_PREDICTION} sessions
 * worth of data before it produces predictions. Before that threshold the UI
 * shows "Learning your patterns..." instead of suggestions.
 *
 * Data is persisted in SharedPreferences as compact JSON.
 */
public class HubPredictiveEngine {

    private static final String TAG = "HubPredictiveEngine";
    private static final String PREFS_NAME = "hub_predictive";
    private static final String KEY_SESSIONS = "sessions_json";
    private static final String KEY_SESSION_COUNT = "session_count";

    /** Number of distinct access events required before predictions start. */
    public static final int MIN_SESSIONS_BEFORE_PREDICTION = 50;
    /** How many files to surface in the "You might need this" section. */
    private static final int MAX_SUGGESTIONS = 5;
    /** Maximum stored access sessions (rolling window). */
    private static final int MAX_SESSION_ENTRIES = 500;

    // ─── Access session record ────────────────────────────────────────────────

    static class AccessSession {
        String fileId;
        String previousFileId;  // file opened immediately before this one (may be null)
        int dayOfWeek;          // Calendar.DAY_OF_WEEK values (1=Sunday, 7=Saturday)
        int hourOfDay;
        long timestamp;

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("fid", fileId);
                o.put("pfid", previousFileId != null ? previousFileId : "");
                o.put("dow", dayOfWeek);
                o.put("hod", hourOfDay);
                o.put("ts", timestamp);
                return o;
            } catch (Exception e) { return new JSONObject(); }
        }

        static AccessSession fromJson(JSONObject o) {
            if (o == null) return null;
            AccessSession s = new AccessSession();
            s.fileId = o.optString("fid", "");
            s.previousFileId = o.optString("pfid", "");
            if (s.previousFileId.isEmpty()) s.previousFileId = null;
            s.dayOfWeek = o.optInt("dow", 1);
            s.hourOfDay = o.optInt("hod", 0);
            s.timestamp = o.optLong("ts", 0);
            return s;
        }
    }

    // ─── Suggestion ───────────────────────────────────────────────────────────

    public static class Suggestion {
        public final String fileId;
        public final String reason;    // human-readable explanation
        public final float score;

        public Suggestion(String fileId, String reason, float score) {
            this.fileId = fileId;
            this.reason = reason;
            this.score = score;
        }
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    private static HubPredictiveEngine instance;
    private final Context context;
    private final List<AccessSession> sessions = new ArrayList<>();
    private int totalSessionCount = 0;
    private String lastOpenedFileId = null;

    private HubPredictiveEngine(Context context) {
        this.context = context.getApplicationContext();
        load();
    }

    public static synchronized HubPredictiveEngine getInstance(Context context) {
        if (instance == null) instance = new HubPredictiveEngine(context);
        return instance;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Records that the user opened a file. Call this every time a file is opened. */
    public synchronized void recordAccess(String fileId) {
        if (fileId == null || fileId.isEmpty()) return;

        Calendar cal = Calendar.getInstance();
        AccessSession s = new AccessSession();
        s.fileId = fileId;
        s.previousFileId = lastOpenedFileId;
        s.dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        s.hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        s.timestamp = System.currentTimeMillis();

        sessions.add(s);
        totalSessionCount++;
        lastOpenedFileId = fileId;

        // Rolling window
        while (sessions.size() > MAX_SESSION_ENTRIES) sessions.remove(0);

        save();
    }

    /** Returns true when enough data has been collected to make predictions. */
    public synchronized boolean hasEnoughData() {
        return totalSessionCount >= MIN_SESSIONS_BEFORE_PREDICTION;
    }

    /** Returns the total number of recorded sessions. */
    public synchronized int getSessionCount() { return totalSessionCount; }

    /**
     * Returns up to {@link #MAX_SUGGESTIONS} file IDs likely to be useful right now,
     * based on day-of-week, hour-of-day, and recent sequential access patterns.
     *
     * Returns an empty list if not enough data yet.
     */
    public synchronized List<Suggestion> getSuggestions() {
        if (!hasEnoughData()) return new ArrayList<>();

        Calendar now = Calendar.getInstance();
        int dow = now.get(Calendar.DAY_OF_WEEK);
        int hod = now.get(Calendar.HOUR_OF_DAY);

        // Score map: fileId → score
        Map<String, Float> scores = new HashMap<>();
        Map<String, String> reasons = new HashMap<>();

        for (AccessSession s : sessions) {
            float score = 0;
            String reason = null;

            // Same day-of-week match
            if (s.dayOfWeek == dow) {
                score += 2.0f;
                reason = "Usually opened on " + dayName(dow);
            }
            // Same hour-of-day (± 1 hour)
            if (Math.abs(s.hourOfDay - hod) <= 1) {
                score += 1.5f;
                if (reason == null) reason = "Opened at this time before";
            }
            // Sequential pair: current lastOpened → this file
            if (lastOpenedFileId != null && lastOpenedFileId.equals(s.previousFileId)) {
                score += 3.0f;
                reason = "Often opened after current file";
            }

            if (score > 0) {
                Float existing = scores.get(s.fileId);
                scores.put(s.fileId, (existing != null ? existing : 0) + score);
                if (!reasons.containsKey(s.fileId) && reason != null) {
                    reasons.put(s.fileId, reason);
                }
            }
        }

        // Sort by score descending
        List<Map.Entry<String, Float>> ranked = new ArrayList<>(scores.entrySet());
        Collections.sort(ranked, (a, b) -> Float.compare(b.getValue(), a.getValue()));

        List<Suggestion> result = new ArrayList<>();
        for (Map.Entry<String, Float> entry : ranked) {
            if (result.size() >= MAX_SUGGESTIONS) break;
            String reason = reasons.getOrDefault(entry.getKey(), "Matches your pattern");
            result.add(new Suggestion(entry.getKey(), reason, entry.getValue()));
        }
        return result;
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private void load() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            totalSessionCount = prefs.getInt(KEY_SESSION_COUNT, 0);
            String json = prefs.getString(KEY_SESSIONS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                AccessSession s = AccessSession.fromJson(arr.getJSONObject(i));
                if (s != null) sessions.add(s);
            }
        } catch (Exception e) { Log.e(TAG, "load", e); }
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (AccessSession s : sessions) arr.put(s.toJson());
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_SESSIONS, arr.toString())
                    .putInt(KEY_SESSION_COUNT, totalSessionCount)
                    .apply();
        } catch (Exception e) { Log.e(TAG, "save", e); }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String dayName(int calendarDow) {
        switch (calendarDow) {
            case Calendar.MONDAY: return "Mondays";
            case Calendar.TUESDAY: return "Tuesdays";
            case Calendar.WEDNESDAY: return "Wednesdays";
            case Calendar.THURSDAY: return "Thursdays";
            case Calendar.FRIDAY: return "Fridays";
            case Calendar.SATURDAY: return "Saturdays";
            default: return "Sundays";
        }
    }
}
