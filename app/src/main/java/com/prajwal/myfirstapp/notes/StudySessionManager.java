package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  STUDY SESSION MANAGER — Pomodoro-based study sessions with break tracking.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Features:
 *  • Configurable focus / short break / long break durations
 *  • Pomodoro cycle tracking (4 focus periods → long break)
 *  • Session history persistence (SharedPreferences)
 *  • Listener callbacks for timer ticks, session state changes
 *  • Auto-start next phase option
 *  • Daily streak tracking
 */
public class StudySessionManager {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════════
    public static final int STATE_IDLE               = 0;
    public static final int STATE_FOCUS              = 1;
    public static final int STATE_SHORT_BREAK        = 2;
    public static final int STATE_LONG_BREAK         = 3;
    public static final int STATE_PAUSED             = 4;

    private static final String PREFS_NAME = "study_session_data";
    private static final String KEY_HISTORY = "session_history";
    private static final String KEY_FOCUS_DURATION = "focus_duration";
    private static final String KEY_SHORT_BREAK = "short_break_duration";
    private static final String KEY_LONG_BREAK = "long_break_duration";
    private static final String KEY_POMODOROS_BEFORE_LONG = "pomodoros_before_long";
    private static final String KEY_DAILY_STREAK = "daily_streak";
    private static final String KEY_LAST_STUDY_DATE = "last_study_date";
    private static final String KEY_TOTAL_FOCUS_MINUTES = "total_focus_minutes";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class StudySession {
        public long startTime;
        public long endTime;
        public int focusMinutes;
        public int pomodorosCompleted;
        public String noteId;       // Which note was being studied
        public String noteTitle;
        public int cardsReviewed;
        public int correctAnswers;

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("startTime", startTime);
                json.put("endTime", endTime);
                json.put("focusMinutes", focusMinutes);
                json.put("pomodorosCompleted", pomodorosCompleted);
                json.put("noteId", noteId != null ? noteId : "");
                json.put("noteTitle", noteTitle != null ? noteTitle : "");
                json.put("cardsReviewed", cardsReviewed);
                json.put("correctAnswers", correctAnswers);
            } catch (JSONException e) { e.printStackTrace(); }
            return json;
        }

        public static StudySession fromJson(JSONObject json) {
            StudySession s = new StudySession();
            s.startTime = json.optLong("startTime", 0);
            s.endTime = json.optLong("endTime", 0);
            s.focusMinutes = json.optInt("focusMinutes", 0);
            s.pomodorosCompleted = json.optInt("pomodorosCompleted", 0);
            s.noteId = json.optString("noteId", "");
            s.noteTitle = json.optString("noteTitle", "");
            s.cardsReviewed = json.optInt("cardsReviewed", 0);
            s.correctAnswers = json.optInt("correctAnswers", 0);
            return s;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LISTENER
    // ═══════════════════════════════════════════════════════════════════════════════

    public interface StudySessionListener {
        void onTimerTick(long remainingMillis, int state);
        void onStateChanged(int newState, int pomodoroCount);
        void onSessionCompleted(StudySession session);
        void onPomodoroCompleted(int pomodoroNumber);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    private final Context context;
    private StudySessionListener listener;

    // Timer
    private CountDownTimer countDownTimer;
    private int currentState = STATE_IDLE;
    private int previousState = STATE_IDLE; // For pause/resume
    private long remainingMillis = 0;

    // Pomodoro tracking
    private int pomodoroCount = 0;
    private int totalFocusSecondsThisSession = 0;

    // Current session
    private StudySession currentSession;

    // Config (defaults: 25/5/15 minutes, long break after 4)
    private int focusDurationMinutes = 25;
    private int shortBreakMinutes = 5;
    private int longBreakMinutes = 15;
    private int pomodorosBeforeLongBreak = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════════════

    public StudySessionManager(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
    }

    public void setListener(StudySessionListener listener) {
        this.listener = listener;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONFIG
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadConfig() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        focusDurationMinutes = prefs.getInt(KEY_FOCUS_DURATION, 25);
        shortBreakMinutes = prefs.getInt(KEY_SHORT_BREAK, 5);
        longBreakMinutes = prefs.getInt(KEY_LONG_BREAK, 15);
        pomodorosBeforeLongBreak = prefs.getInt(KEY_POMODOROS_BEFORE_LONG, 4);
    }

    public void saveConfig(int focusMin, int shortBreakMin, int longBreakMin, int pomosBeforeLong) {
        this.focusDurationMinutes = focusMin;
        this.shortBreakMinutes = shortBreakMin;
        this.longBreakMinutes = longBreakMin;
        this.pomodorosBeforeLongBreak = pomosBeforeLong;

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FOCUS_DURATION, focusMin)
                .putInt(KEY_SHORT_BREAK, shortBreakMin)
                .putInt(KEY_LONG_BREAK, longBreakMin)
                .putInt(KEY_POMODOROS_BEFORE_LONG, pomosBeforeLong)
                .apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TIMER CONTROLS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Start a focus period. */
    public void startFocus(String noteId, String noteTitle) {
        if (currentSession == null) {
            currentSession = new StudySession();
            currentSession.startTime = System.currentTimeMillis();
            currentSession.noteId = noteId;
            currentSession.noteTitle = noteTitle;
        }
        currentState = STATE_FOCUS;
        startTimer(focusDurationMinutes * 60 * 1000L);
        if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
    }

    /** Start a break (auto-selects short or long based on pomodoro count). */
    public void startBreak() {
        if (pomodoroCount > 0 && pomodoroCount % pomodorosBeforeLongBreak == 0) {
            currentState = STATE_LONG_BREAK;
            startTimer(longBreakMinutes * 60 * 1000L);
        } else {
            currentState = STATE_SHORT_BREAK;
            startTimer(shortBreakMinutes * 60 * 1000L);
        }
        if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
    }

    /** Pause the current timer. */
    public void pause() {
        if (countDownTimer != null && currentState != STATE_IDLE && currentState != STATE_PAUSED) {
            countDownTimer.cancel();
            previousState = currentState;
            currentState = STATE_PAUSED;
            if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
        }
    }

    /** Resume from pause. */
    public void resume() {
        if (currentState == STATE_PAUSED && remainingMillis > 0) {
            currentState = previousState;
            startTimer(remainingMillis);
            if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
        }
    }

    /** Stop session completely. */
    public void stop() {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = null;

        if (currentSession != null) {
            currentSession.endTime = System.currentTimeMillis();
            currentSession.focusMinutes = totalFocusSecondsThisSession / 60;
            currentSession.pomodorosCompleted = pomodoroCount;
            saveSession(currentSession);
            updateStreak();
            if (listener != null) listener.onSessionCompleted(currentSession);
        }

        currentState = STATE_IDLE;
        remainingMillis = 0;
        pomodoroCount = 0;
        totalFocusSecondsThisSession = 0;
        currentSession = null;
        if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
    }

    /** Skip current phase (jump to next). */
    public void skip() {
        if (countDownTimer != null) countDownTimer.cancel();
        onTimerFinished();
    }

    private void startTimer(long durationMillis) {
        if (countDownTimer != null) countDownTimer.cancel();
        remainingMillis = durationMillis;

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                if (currentState == STATE_FOCUS) {
                    totalFocusSecondsThisSession++;
                }
                if (listener != null) listener.onTimerTick(millisUntilFinished, currentState);
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                onTimerFinished();
            }
        }.start();
    }

    private void onTimerFinished() {
        if (currentState == STATE_FOCUS) {
            pomodoroCount++;
            if (listener != null) listener.onPomodoroCompleted(pomodoroCount);
            // Auto-start break
            startBreak();
        } else if (currentState == STATE_SHORT_BREAK || currentState == STATE_LONG_BREAK) {
            // Break is over — notify, but don't auto-start focus
            currentState = STATE_IDLE;
            if (listener != null) listener.onStateChanged(currentState, pomodoroCount);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SESSION HISTORY
    // ═══════════════════════════════════════════════════════════════════════════════

    private void saveSession(StudySession session) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(historyJson);
            arr.put(session.toJson());
            // Keep last 100 sessions
            while (arr.length() > 100) arr.remove(0);
            prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (JSONException e) { e.printStackTrace(); }

        // Update total focus minutes
        int totalMinutes = prefs.getInt(KEY_TOTAL_FOCUS_MINUTES, 0);
        prefs.edit().putInt(KEY_TOTAL_FOCUS_MINUTES, totalMinutes + session.focusMinutes).apply();
    }

    public List<StudySession> getSessionHistory() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String historyJson = prefs.getString(KEY_HISTORY, "[]");
        List<StudySession> sessions = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(historyJson);
            for (int i = 0; i < arr.length(); i++) {
                sessions.add(StudySession.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) { e.printStackTrace(); }
        return sessions;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  STREAK TRACKING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void updateStreak() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        String lastDate = prefs.getString(KEY_LAST_STUDY_DATE, "");
        int streak = prefs.getInt(KEY_DAILY_STREAK, 0);

        if (today.equals(lastDate)) {
            // Already studied today
            return;
        }

        // Check if yesterday
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        String yesterday = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(cal.getTime());

        if (yesterday.equals(lastDate)) {
            streak++;
        } else {
            streak = 1; // Reset
        }

        prefs.edit()
                .putString(KEY_LAST_STUDY_DATE, today)
                .putInt(KEY_DAILY_STREAK, streak)
                .apply();
    }

    public int getDailyStreak() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DAILY_STREAK, 0);
    }

    public int getTotalFocusMinutes() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TOTAL_FOCUS_MINUTES, 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════════════════════════════════════

    public int getCurrentState() { return currentState; }
    public long getRemainingMillis() { return remainingMillis; }
    public int getPomodoroCount() { return pomodoroCount; }
    public int getFocusDurationMinutes() { return focusDurationMinutes; }
    public int getShortBreakMinutes() { return shortBreakMinutes; }
    public int getLongBreakMinutes() { return longBreakMinutes; }
    public int getPomodorosBeforeLongBreak() { return pomodorosBeforeLongBreak; }

    public void addReviewedCard(boolean correct) {
        if (currentSession != null) {
            currentSession.cardsReviewed++;
            if (correct) currentSession.correctAnswers++;
        }
    }

    public String getStateLabel() {
        switch (currentState) {
            case STATE_FOCUS: return "🎯 Focus";
            case STATE_SHORT_BREAK: return "☕ Short Break";
            case STATE_LONG_BREAK: return "🌴 Long Break";
            case STATE_PAUSED: return "⏸ Paused";
            default: return "Ready";
        }
    }

    public void destroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
