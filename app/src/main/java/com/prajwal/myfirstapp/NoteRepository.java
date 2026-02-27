package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for notes data — handles persistence, CRUD, filtering,
 * search, tag management, and migration from the old tree-based format.
 */
public class NoteRepository {

    private static final String TAG = "NoteRepository";
    private static final String PREFS_NAME = "notes_v2_prefs";
    private static final String NOTES_KEY = "notes_data";
    private static final String RECENT_SEARCHES_KEY = "recent_searches";
    private static final String CUSTOM_CATEGORIES_KEY = "custom_categories";
    private static final String VIEW_MODE_KEY = "view_mode"; // "grid" or "list"
    private static final String OLD_PREFS_NAME = "notes_prefs";
    private static final String OLD_NOTES_KEY = "notes_json";
    private static final String MIGRATION_DONE_KEY = "migration_v2_done";
    private static final long TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final Context context;
    private ArrayList<Note> notes;

    public NoteRepository(Context context) {
        this.context = context;
        this.notes = new ArrayList<>();
        loadNotes();
        migrateOldNotesIfNeeded();
        purgeExpiredTrash();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ─────────────────────────────────────────────

    private void loadNotes() {
        notes.clear();
        String json = getPrefs().getString(NOTES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Note note = Note.fromJson(array.getJSONObject(i));
                if (note != null) notes.add(note);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load notes: " + e.getMessage());
        }
    }

    private void saveNotes() {
        JSONArray array = new JSONArray();
        for (Note note : notes) {
            array.put(note.toJson());
        }
        getPrefs().edit().putString(NOTES_KEY, array.toString()).apply();
    }

    // ─── CRUD Operations ─────────────────────────────────────────

    public void addNote(Note note) {
        notes.add(0, note);
        saveNotes();
    }

    public void updateNote(Note note) {
        note.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.set(i, note);
                break;
            }
        }
        saveNotes();
    }

    public Note getNoteById(String id) {
        for (Note note : notes) {
            if (note.id.equals(id)) return note;
        }
        return null;
    }

    public void deleteNotePermanently(String id) {
        notes.removeIf(n -> n.id.equals(id));
        saveNotes();
    }

    // ─── Pin / Archive / Trash ───────────────────────────────────

    public void togglePin(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isPinned = !note.isPinned;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void archiveNote(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isArchived = true;
            note.isPinned = false;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void unarchiveNote(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isArchived = false;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void trashNote(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isTrashed = true;
            note.isPinned = false;
            note.isArchived = false;
            note.deletedAt = System.currentTimeMillis();
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void restoreFromTrash(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isTrashed = false;
            note.deletedAt = 0;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void toggleLock(String noteId) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.isLocked = !note.isLocked;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void setNoteColor(String noteId, String colorHex) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.colorHex = colorHex;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    public void setNoteCategory(String noteId, String category) {
        Note note = getNoteById(noteId);
        if (note != null) {
            note.category = category;
            note.updatedAt = System.currentTimeMillis();
            saveNotes();
        }
    }

    // ─── Batch Operations ────────────────────────────────────────

    public void batchPin(List<String> noteIds, boolean pin) {
        for (String id : noteIds) {
            Note note = getNoteById(id);
            if (note != null) {
                note.isPinned = pin;
                note.updatedAt = System.currentTimeMillis();
            }
        }
        saveNotes();
    }

    public void batchArchive(List<String> noteIds) {
        for (String id : noteIds) {
            Note note = getNoteById(id);
            if (note != null) {
                note.isArchived = true;
                note.isPinned = false;
                note.updatedAt = System.currentTimeMillis();
            }
        }
        saveNotes();
    }

    public void batchTrash(List<String> noteIds) {
        for (String id : noteIds) {
            Note note = getNoteById(id);
            if (note != null) {
                note.isTrashed = true;
                note.isPinned = false;
                note.isArchived = false;
                note.deletedAt = System.currentTimeMillis();
                note.updatedAt = System.currentTimeMillis();
            }
        }
        saveNotes();
    }

    public void batchSetColor(List<String> noteIds, String colorHex) {
        for (String id : noteIds) {
            Note note = getNoteById(id);
            if (note != null) {
                note.colorHex = colorHex;
                note.updatedAt = System.currentTimeMillis();
            }
        }
        saveNotes();
    }

    public void batchSetCategory(List<String> noteIds, String category) {
        for (String id : noteIds) {
            Note note = getNoteById(id);
            if (note != null) {
                note.category = category;
                note.updatedAt = System.currentTimeMillis();
            }
        }
        saveNotes();
    }

    // ─── Query — Active Notes ────────────────────────────────────

    public ArrayList<Note> getActiveNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (!n.isTrashed && !n.isArchived) result.add(n);
        }
        sortNotes(result);
        return result;
    }

    public ArrayList<Note> getPinnedNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (n.isPinned && !n.isTrashed && !n.isArchived) result.add(n);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    public ArrayList<Note> getUnpinnedNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (!n.isPinned && !n.isTrashed && !n.isArchived) result.add(n);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    // ─── Query — Archive & Trash ─────────────────────────────────

    public ArrayList<Note> getArchivedNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (n.isArchived && !n.isTrashed) result.add(n);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    public ArrayList<Note> getTrashedNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (n.isTrashed) result.add(n);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.deletedAt, a.deletedAt));
        return result;
    }

    public void emptyTrash() {
        notes.removeIf(n -> n.isTrashed);
        saveNotes();
    }

    private void purgeExpiredTrash() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.isTrashed && n.deletedAt > 0 && (now - n.deletedAt) > TRASH_RETENTION_MS) {
                notes.remove(i);
                changed = true;
            }
        }
        if (changed) saveNotes();
    }

    // ─── Enhanced Search ─────────────────────────────────────────

    /**
     * Search result grouping for enhanced search display
     */
    public static class SearchResults {
        public ArrayList<Note> titleMatches = new ArrayList<>();
        public ArrayList<Note> contentMatches = new ArrayList<>();
        public ArrayList<Note> tagMatches = new ArrayList<>();
        public ArrayList<Note> categoryMatches = new ArrayList<>();
        
        public boolean isEmpty() {
            return titleMatches.isEmpty() && contentMatches.isEmpty() 
                    && tagMatches.isEmpty() && categoryMatches.isEmpty();
        }
        
        public int getTotalCount() {
            return titleMatches.size() + contentMatches.size() 
                    + tagMatches.size() + categoryMatches.size();
        }
        
        public ArrayList<Note> getAllResults() {
            // Deduplicated combined list
            ArrayList<Note> all = new ArrayList<>();
            Set<String> addedIds = new HashSet<>();
            
            for (Note n : titleMatches) {
                if (addedIds.add(n.id)) all.add(n);
            }
            for (Note n : contentMatches) {
                if (addedIds.add(n.id)) all.add(n);
            }
            for (Note n : tagMatches) {
                if (addedIds.add(n.id)) all.add(n);
            }
            for (Note n : categoryMatches) {
                if (addedIds.add(n.id)) all.add(n);
            }
            return all;
        }
    }

    /**
     * Enhanced search that groups results by match type
     */
    public SearchResults searchNotesGrouped(String query) {
        SearchResults results = new SearchResults();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        
        for (Note note : notes) {
            if (note.isTrashed || note.isArchived) continue;
            
            // Check title match
            if (note.title != null && note.title.toLowerCase().contains(lowerQuery)) {
                results.titleMatches.add(note);
                continue; // Don't add to multiple groups
            }
            
            // Check content match
            if ((note.body != null && note.body.toLowerCase().contains(lowerQuery)) ||
                (note.plainTextPreview != null && note.plainTextPreview.toLowerCase().contains(lowerQuery))) {
                results.contentMatches.add(note);
                continue;
            }
            
            // Check tag match
            if (note.tags != null) {
                boolean tagMatch = false;
                for (String tag : note.tags) {
                    if (tag.toLowerCase().contains(lowerQuery)) {
                        tagMatch = true;
                        break;
                    }
                }
                if (tagMatch) {
                    results.tagMatches.add(note);
                    continue;
                }
            }
            
            // Check category match
            if (note.category != null && note.category.toLowerCase().contains(lowerQuery)) {
                results.categoryMatches.add(note);
            }
        }
        
        // Sort each group by updatedAt
        sortNotes(results.titleMatches);
        sortNotes(results.contentMatches);
        sortNotes(results.tagMatches);
        sortNotes(results.categoryMatches);
        
        return results;
    }

    // ─── Active Notes ────────────────────────────────────────────

    /**
     * Get all active (non-archived, non-trashed) notes
     */
    public ArrayList<Note> getAllActiveNotes() {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (!n.isTrashed && !n.isArchived) {
                result.add(n);
            }
        }
        return result;
    }

    // ─── Filtering ───────────────────────────────────────────────

    public ArrayList<Note> filterNotes(String categoryFilter, String searchQuery) {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : notes) {
            if (n.isTrashed || n.isArchived) continue;
            if (!n.matchesFilter(categoryFilter)) continue;
            if (searchQuery != null && !searchQuery.isEmpty() && !n.matchesSearch(searchQuery)) continue;
            result.add(n);
        }
        sortNotes(result);
        return result;
    }

    public ArrayList<Note> filterPinnedNotes(String categoryFilter, String searchQuery) {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : filterNotes(categoryFilter, searchQuery)) {
            if (n.isPinned) result.add(n);
        }
        return result;
    }

    public ArrayList<Note> filterUnpinnedNotes(String categoryFilter, String searchQuery) {
        ArrayList<Note> result = new ArrayList<>();
        for (Note n : filterNotes(categoryFilter, searchQuery)) {
            if (!n.isPinned) result.add(n);
        }
        return result;
    }

    private void sortNotes(ArrayList<Note> list) {
        Collections.sort(list, (a, b) -> {
            if (a.isPinned != b.isPinned) return a.isPinned ? -1 : 1;
            return Long.compare(b.updatedAt, a.updatedAt);
        });
    }

    // ─── Tag Operations ──────────────────────────────────────────

    public List<String> getAllTags() {
        Set<String> tagSet = new HashSet<>();
        for (Note n : notes) {
            if (!n.isTrashed && n.tags != null) {
                tagSet.addAll(n.tags);
            }
        }
        List<String> sorted = new ArrayList<>(tagSet);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    public Map<String, Integer> getTagCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Note n : notes) {
            if (!n.isTrashed && n.tags != null) {
                for (String tag : n.tags) {
                    counts.put(tag, counts.getOrDefault(tag, 0) + 1);
                }
            }
        }
        return counts;
    }

    public void renameTag(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        for (Note n : notes) {
            if (n.tags != null) {
                for (int i = 0; i < n.tags.size(); i++) {
                    if (n.tags.get(i).equals(oldName)) {
                        n.tags.set(i, newName);
                    }
                }
            }
        }
        saveNotes();
    }

    public void deleteTag(String tagName) {
        for (Note n : notes) {
            if (n.tags != null) {
                n.tags.remove(tagName);
            }
        }
        saveNotes();
    }

    // ─── Category Operations ─────────────────────────────────────

    public List<String> getAllCategories() {
        Set<String> cats = new HashSet<>();
        for (String c : Note.DEFAULT_CATEGORIES) cats.add(c);
        // Add custom categories
        for (Note n : notes) {
            if (!n.isTrashed && n.category != null && !n.category.isEmpty()) {
                cats.add(n.category);
            }
        }
        // Add saved custom categories
        String customJson = getPrefs().getString(CUSTOM_CATEGORIES_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(customJson);
            for (int i = 0; i < arr.length(); i++) cats.add(arr.getString(i));
        } catch (JSONException e) { /* ignore */ }

        List<String> sorted = new ArrayList<>(cats);
        // Keep defaults first in order, then customs sorted
        List<String> result = new ArrayList<>();
        for (String def : Note.DEFAULT_CATEGORIES) {
            if (sorted.contains(def)) {
                result.add(def);
                sorted.remove(def);
            }
        }
        // Add special filters
        if (!result.contains("Pinned")) result.add(result.size() > 0 ? 1 : 0, "Pinned");
        if (!result.contains("Locked")) result.add("Locked");
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        result.addAll(sorted);
        return result;
    }

    public void addCustomCategory(String category) {
        String customJson = getPrefs().getString(CUSTOM_CATEGORIES_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(customJson);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getString(i).equals(category)) return; // already exists
            }
            arr.put(category);
            getPrefs().edit().putString(CUSTOM_CATEGORIES_KEY, arr.toString()).apply();
        } catch (JSONException e) { /* ignore */ }
    }

    // ─── Recent Searches ─────────────────────────────────────────

    public List<String> getRecentSearches() {
        List<String> searches = new ArrayList<>();
        String json = getPrefs().getString(RECENT_SEARCHES_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                searches.add(arr.getString(i));
            }
        } catch (JSONException e) { /* ignore */ }
        return searches;
    }

    public void addRecentSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        query = query.trim();
        List<String> searches = getRecentSearches();
        searches.remove(query); // remove duplicate
        searches.add(0, query); // add to front
        if (searches.size() > 10) searches = searches.subList(0, 10);
        JSONArray arr = new JSONArray();
        for (String s : searches) arr.put(s);
        getPrefs().edit().putString(RECENT_SEARCHES_KEY, arr.toString()).apply();
    }

    public void clearRecentSearches() {
        getPrefs().edit().putString(RECENT_SEARCHES_KEY, "[]").apply();
    }

    // ─── View Mode ───────────────────────────────────────────────

    public String getViewMode() {
        return getPrefs().getString(VIEW_MODE_KEY, "grid");
    }

    public void setViewMode(String mode) {
        getPrefs().edit().putString(VIEW_MODE_KEY, mode).apply();
    }

    // ─── Counts ──────────────────────────────────────────────────

    public int getActiveCount() {
        int count = 0;
        for (Note n : notes) if (!n.isTrashed && !n.isArchived) count++;
        return count;
    }

    public int getArchivedCount() {
        int count = 0;
        for (Note n : notes) if (n.isArchived && !n.isTrashed) count++;
        return count;
    }

    public int getTrashedCount() {
        int count = 0;
        for (Note n : notes) if (n.isTrashed) count++;
        return count;
    }

    // ─── Migration from Old Format ───────────────────────────────

    private void migrateOldNotesIfNeeded() {
        if (getPrefs().getBoolean(MIGRATION_DONE_KEY, false)) return;

        SharedPreferences oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE);
        String oldJson = oldPrefs.getString(OLD_NOTES_KEY, null);
        if (oldJson == null) {
            getPrefs().edit().putBoolean(MIGRATION_DONE_KEY, true).apply();
            return;
        }

        try {
            JSONObject oldTree = new JSONObject(oldJson);
            JSONArray items = oldTree.optJSONArray("items");
            if (items != null) {
                migrateItems(items, "Personal");
            }
            saveNotes();
            Log.i(TAG, "Migrated " + notes.size() + " notes from old format");
        } catch (JSONException e) {
            Log.e(TAG, "Migration failed: " + e.getMessage());
        }

        getPrefs().edit().putBoolean(MIGRATION_DONE_KEY, true).apply();
    }

    private void migrateItems(JSONArray items, String defaultCategory) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject obj = items.getJSONObject(i);
                String type = obj.optString("type", "note");

                if (type.equals("folder")) {
                    // Recurse into folder children, using folder name as category
                    String folderName = obj.optString("name", defaultCategory);
                    JSONArray children = obj.optJSONArray("children");
                    if (children != null) {
                        migrateItems(children, folderName);
                    }
                } else {
                    Note note = new Note();
                    note.id = obj.optString("id", note.id);
                    note.title = obj.optString("name", "Untitled");
                    note.body = obj.optString("content", "");
                    note.category = defaultCategory;
                    note.isPinned = obj.optBoolean("pinned", false);

                    String created = obj.optString("created", "");
                    String modified = obj.optString("modified", "");
                    note.createdAt = parseTimestamp(created);
                    note.updatedAt = parseTimestamp(modified);
                    if (note.updatedAt == 0) note.updatedAt = note.createdAt;
                    if (note.createdAt == 0) note.createdAt = System.currentTimeMillis();

                    note.updatePlainTextPreview();
                    notes.add(note);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to migrate item: " + e.getMessage());
            }
        }
    }

    private long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            java.util.Date date = sdf.parse(ts);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ─── Sync Support ────────────────────────────────────────────

    public ArrayList<Note> getAllNotes() {
        return new ArrayList<>(notes);
    }

    public void replaceAllNotes(ArrayList<Note> newNotes) {
        this.notes = new ArrayList<>(newNotes);
        saveNotes();
    }

    public String exportToJson() {
        JSONArray array = new JSONArray();
        for (Note note : notes) {
            array.put(note.toJson());
        }
        return array.toString();
    }

    public void importFromJson(String jsonStr) {
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                Note note = Note.fromJson(array.getJSONObject(i));
                if (note != null) {
                    // Check for duplicate IDs
                    Note existing = getNoteById(note.id);
                    if (existing != null) {
                        // Keep the newer version
                        if (note.updatedAt > existing.updatedAt) {
                            notes.remove(existing);
                            notes.add(note);
                        }
                    } else {
                        notes.add(note);
                    }
                }
            }
            saveNotes();
        } catch (JSONException e) {
            Log.e(TAG, "Import failed: " + e.getMessage());
        }
    }

    public void reload() {
        loadNotes();
    }
}
