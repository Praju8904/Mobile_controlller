package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE VERSION MANAGER — Manages version history snapshots for notes
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Saves version snapshot every auto-save
 * - Keeps last 10 versions per note
 * - Restore to any previous version
 * - Diff comparison (future enhancement)
 */
public class NoteVersionManager {

    private static final String TAG = "NoteVersionManager";
    private static final String PREFS_NAME = "note_versions_prefs";
    private static final String KEY_PREFIX = "versions_";
    private static final int MAX_VERSIONS = 10;

    private final SharedPreferences prefs;

    public NoteVersionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  VERSION DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class NoteVersion {
        public String noteId;
        public String title;
        public String body;
        public long timestamp;
        public int wordCount;

        public NoteVersion() {}

        public NoteVersion(Note note) {
            this.noteId = note.id;
            this.title = note.title;
            this.body = note.body;
            this.timestamp = System.currentTimeMillis();
            this.wordCount = note.body != null 
                    ? note.body.trim().split("\\s+").length 
                    : 0;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("noteId", noteId);
            json.put("title", title);
            json.put("body", body);
            json.put("timestamp", timestamp);
            json.put("wordCount", wordCount);
            return json;
        }

        public static NoteVersion fromJson(JSONObject json) throws JSONException {
            NoteVersion version = new NoteVersion();
            version.noteId = json.getString("noteId");
            version.title = json.optString("title", "");
            version.body = json.optString("body", "");
            version.timestamp = json.getLong("timestamp");
            version.wordCount = json.optInt("wordCount", 0);
            return version;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Save a version snapshot for a note
     */
    public void saveVersion(Note note) {
        if (note == null || note.id == null) return;

        String key = KEY_PREFIX + note.id;
        List<NoteVersion> versions = getVersions(note.id);

        // Check if content has changed since last version
        if (!versions.isEmpty()) {
            NoteVersion lastVersion = versions.get(0);
            if (lastVersion.title.equals(note.title) && 
                lastVersion.body.equals(note.body)) {
                // No changes, don't save
                return;
            }
        }

        // Create new version
        NoteVersion newVersion = new NoteVersion(note);

        // Add to beginning (most recent first)
        versions.add(0, newVersion);

        // Trim to max versions
        while (versions.size() > MAX_VERSIONS) {
            versions.remove(versions.size() - 1);
        }

        // Save to prefs
        saveVersions(note.id, versions);
        Log.d(TAG, "Saved version snapshot for note: " + note.id + " (total: " + versions.size() + ")");
    }

    /**
     * Get all versions for a note (most recent first)
     */
    public List<NoteVersion> getVersions(String noteId) {
        List<NoteVersion> versions = new ArrayList<>();

        String key = KEY_PREFIX + noteId;
        String json = prefs.getString(key, null);

        if (json == null) return versions;

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                versions.add(NoteVersion.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading versions", e);
        }

        return versions;
    }

    /**
     * Get a specific version by index (0 = most recent)
     */
    public NoteVersion getVersion(String noteId, int index) {
        List<NoteVersion> versions = getVersions(noteId);
        if (index >= 0 && index < versions.size()) {
            return versions.get(index);
        }
        return null;
    }

    /**
     * Get version count for a note
     */
    public int getVersionCount(String noteId) {
        return getVersions(noteId).size();
    }

    /**
     * Delete all versions for a note
     */
    public void deleteVersions(String noteId) {
        String key = KEY_PREFIX + noteId;
        prefs.edit().remove(key).apply();
        Log.d(TAG, "Deleted all versions for note: " + noteId);
    }

    /**
     * Delete a specific version
     */
    public void deleteVersion(String noteId, int index) {
        List<NoteVersion> versions = getVersions(noteId);
        if (index >= 0 && index < versions.size()) {
            versions.remove(index);
            saveVersions(noteId, versions);
        }
    }

    /**
     * Get versions across all notes, sorted by timestamp (for global history view)
     */
    public List<NoteVersion> getAllRecentVersions(int limit) {
        List<NoteVersion> allVersions = new ArrayList<>();

        // Get all keys
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                String noteId = key.substring(KEY_PREFIX.length());
                allVersions.addAll(getVersions(noteId));
            }
        }

        // Sort by timestamp (most recent first)
        allVersions.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        // Limit results
        if (allVersions.size() > limit) {
            return allVersions.subList(0, limit);
        }

        return allVersions;
    }

    /**
     * Calculate diff between two versions (simple word-based for now)
     */
    public VersionDiff calculateDiff(NoteVersion older, NoteVersion newer) {
        VersionDiff diff = new VersionDiff();

        String[] oldWords = older.body.split("\\s+");
        String[] newWords = newer.body.split("\\s+");

        diff.oldWordCount = oldWords.length;
        diff.newWordCount = newWords.length;
        diff.wordDifference = newWords.length - oldWords.length;
        diff.oldCharCount = older.body.length();
        diff.newCharCount = newer.body.length();
        diff.charDifference = newer.body.length() - older.body.length();

        return diff;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DIFF DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class VersionDiff {
        public int oldWordCount;
        public int newWordCount;
        public int wordDifference;
        public int oldCharCount;
        public int newCharCount;
        public int charDifference;

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (wordDifference > 0) {
                sb.append("+").append(wordDifference).append(" words");
            } else if (wordDifference < 0) {
                sb.append(wordDifference).append(" words");
            } else {
                sb.append("No word change");
            }
            return sb.toString();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void saveVersions(String noteId, List<NoteVersion> versions) {
        try {
            JSONArray array = new JSONArray();
            for (NoteVersion version : versions) {
                array.put(version.toJson());
            }
            prefs.edit().putString(KEY_PREFIX + noteId, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving versions", e);
        }
    }
}
