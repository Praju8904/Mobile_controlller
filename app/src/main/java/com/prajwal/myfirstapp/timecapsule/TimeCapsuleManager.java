package com.prajwal.myfirstapp.timecapsule;


import com.prajwal.myfirstapp.notes.NoteContextTracker;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  TIME CAPSULE MANAGER — Schedule notes to appear at a future date.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  A "time capsule" is a note that the user locks away until a specific date.
 *  When the date arrives, a notification reveals the note.
 *  This is great for:
 *  • Letters to future self
 *  • Goal check-ins
 *  • Surprise reminders
 *  • Reflection comparisons
 */
public class TimeCapsuleManager {

    private static final String PREFS_NAME = "time_capsule_data";
    private static final String KEY_CAPSULES = "capsules";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class TimeCapsule {
        public String id;
        public String noteId;
        public String noteTitle;
        public String message;            // Optional message to show when opened
        public long createdAt;
        public long openDate;             // When the capsule can be opened
        public boolean isOpened;
        public long openedAt;
        public String createdContext;     // e.g., "Created on a rainy Monday evening"

        public boolean canOpen() {
            return !isOpened && System.currentTimeMillis() >= openDate;
        }

        public int getDaysUntilOpen() {
            if (isOpened || canOpen()) return 0;
            long diff = openDate - System.currentTimeMillis();
            return (int) (diff / (24 * 60 * 60 * 1000)) + 1;
        }

        public String getFormattedOpenDate() {
            return new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date(openDate));
        }

        public String getFormattedCreatedDate() {
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date(createdAt));
        }

        public String getStatusEmoji() {
            if (isOpened) return "📭";         // Opened
            if (canOpen()) return "🎁";        // Ready to open!
            int days = getDaysUntilOpen();
            if (days <= 7) return "⏳";        // Almost there
            return "🔒";                       // Locked
        }

        public String getStatusText() {
            if (isOpened) return "Opened on " + new SimpleDateFormat("MMM dd", Locale.US).format(new Date(openedAt));
            if (canOpen()) return "Ready to open!";
            int days = getDaysUntilOpen();
            if (days == 1) return "Opens tomorrow";
            return "Opens in " + days + " days";
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("noteId", noteId != null ? noteId : "");
                json.put("noteTitle", noteTitle != null ? noteTitle : "");
                json.put("message", message != null ? message : "");
                json.put("createdAt", createdAt);
                json.put("openDate", openDate);
                json.put("isOpened", isOpened);
                json.put("openedAt", openedAt);
                json.put("createdContext", createdContext != null ? createdContext : "");
            } catch (JSONException e) { e.printStackTrace(); }
            return json;
        }

        public static TimeCapsule fromJson(JSONObject json) {
            TimeCapsule tc = new TimeCapsule();
            tc.id = json.optString("id", java.util.UUID.randomUUID().toString());
            tc.noteId = json.optString("noteId", "");
            tc.noteTitle = json.optString("noteTitle", "");
            tc.message = json.optString("message", "");
            tc.createdAt = json.optLong("createdAt", System.currentTimeMillis());
            tc.openDate = json.optLong("openDate", System.currentTimeMillis());
            tc.isOpened = json.optBoolean("isOpened", false);
            tc.openedAt = json.optLong("openedAt", 0);
            tc.createdContext = json.optString("createdContext", "");
            return tc;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private final Context context;
    private List<TimeCapsule> capsules;

    public TimeCapsuleManager(Context context) {
        this.context = context.getApplicationContext();
        loadCapsules();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadCapsules() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CAPSULES, "[]");
        capsules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                capsules.add(TimeCapsule.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) { e.printStackTrace(); }
    }

    private void saveCapsules() {
        JSONArray arr = new JSONArray();
        for (TimeCapsule tc : capsules) arr.put(tc.toJson());
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CAPSULES, arr.toString())
                .apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Create a time capsule for a note.
     * @param noteId   The note to encapsulate.
     * @param noteTitle The note's title.
     * @param openDate When it should be openable (epoch ms).
     * @param message  Optional message to display when opened.
     * @return The created capsule.
     */
    public TimeCapsule createCapsule(String noteId, String noteTitle, long openDate, String message) {
        TimeCapsule tc = new TimeCapsule();
        tc.id = java.util.UUID.randomUUID().toString();
        tc.noteId = noteId;
        tc.noteTitle = noteTitle;
        tc.openDate = openDate;
        tc.message = message;
        tc.createdAt = System.currentTimeMillis();
        tc.isOpened = false;

        // Build context string
        NoteContextTracker.NoteContext ctx = NoteContextTracker.captureContext(context);
        if (ctx != null) {
            StringBuilder ctxStr = new StringBuilder("Created ");
            if (ctx.timeOfDay != null) ctxStr.append("on a ").append(ctx.timeOfDay).append(" ");
            if (ctx.dayOfWeek != null) ctxStr.append(ctx.dayOfWeek);
            tc.createdContext = ctxStr.toString().trim();
        }

        capsules.add(tc);
        saveCapsules();
        return tc;
    }

    /**
     * Quick create with preset durations.
     */
    public TimeCapsule createCapsuleIn(String noteId, String noteTitle, int days, String message) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, days);
        cal.set(Calendar.HOUR_OF_DAY, 9); // Open at 9 AM
        cal.set(Calendar.MINUTE, 0);
        return createCapsule(noteId, noteTitle, cal.getTimeInMillis(), message);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Open a capsule (marks it as opened). */
    public boolean openCapsule(String capsuleId) {
        for (TimeCapsule tc : capsules) {
            if (tc.id.equals(capsuleId) && tc.canOpen()) {
                tc.isOpened = true;
                tc.openedAt = System.currentTimeMillis();
                saveCapsules();
                return true;
            }
        }
        return false;
    }

    /** Delete a capsule. */
    public void deleteCapsule(String capsuleId) {
        capsules.removeIf(tc -> tc.id.equals(capsuleId));
        saveCapsules();
    }

    /** Get capsule by ID. */
    public TimeCapsule getCapsule(String capsuleId) {
        for (TimeCapsule tc : capsules) {
            if (tc.id.equals(capsuleId)) return tc;
        }
        return null;
    }

    /** Get capsule for a specific note. */
    public TimeCapsule getCapsuleForNote(String noteId) {
        for (TimeCapsule tc : capsules) {
            if (noteId.equals(tc.noteId) && !tc.isOpened) return tc;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  QUERIES
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Get all capsules. */
    public List<TimeCapsule> getAllCapsules() {
        return new ArrayList<>(capsules);
    }

    /** Get capsules that are ready to open but haven't been opened yet. */
    public List<TimeCapsule> getReadyCapsules() {
        List<TimeCapsule> ready = new ArrayList<>();
        for (TimeCapsule tc : capsules) {
            if (tc.canOpen()) ready.add(tc);
        }
        return ready;
    }

    /** Get unopened, locked capsules. */
    public List<TimeCapsule> getLockedCapsules() {
        List<TimeCapsule> locked = new ArrayList<>();
        for (TimeCapsule tc : capsules) {
            if (!tc.isOpened && !tc.canOpen()) locked.add(tc);
        }
        locked.sort((a, b) -> Long.compare(a.openDate, b.openDate));
        return locked;
    }

    /** Get previously opened capsules. */
    public List<TimeCapsule> getOpenedCapsules() {
        List<TimeCapsule> opened = new ArrayList<>();
        for (TimeCapsule tc : capsules) {
            if (tc.isOpened) opened.add(tc);
        }
        opened.sort((a, b) -> Long.compare(b.openedAt, a.openedAt));
        return opened;
    }

    /** Check if a note has an active (unopened) capsule. */
    public boolean hasActiveCapsule(String noteId) {
        for (TimeCapsule tc : capsules) {
            if (noteId.equals(tc.noteId) && !tc.isOpened) return true;
        }
        return false;
    }

    /** Get total count of active (unopened) capsules. */
    public int getActiveCapsuleCount() {
        int count = 0;
        for (TimeCapsule tc : capsules) if (!tc.isOpened) count++;
        return count;
    }

    /** Preset duration options for UI. */
    public static String[] DURATION_LABELS = {
            "1 Week", "1 Month", "3 Months", "6 Months", "1 Year", "Custom"
    };
    public static int[] DURATION_DAYS = {
            7, 30, 90, 180, 365, -1
    };
}
