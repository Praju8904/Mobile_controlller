package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository for note folders — handles persistence, CRUD, and tree navigation.
 */
public class NoteFolderRepository {

    private static final String TAG = "NoteFolderRepository";
    private static final String PREFS_NAME = "note_folders_prefs";
    private static final String FOLDERS_KEY = "folders_data";
    private static final String SEEDED_KEY = "folders_seeded_v1";
    private static final String HOME_MODE_KEY = "notes_home_mode"; // "folders" or "all"

    // Smart categorization keywords (static to avoid recreation on every call)
    private static final String[] STUDY_KEYWORDS = {"lecture", "exam", "assignment", "professor", "study", "class",
            "homework", "test", "quiz", "course", "textbook", "notes", "semester", "grade",
            "calculus", "algebra", "chemistry", "biology", "physics", "history"};
    private static final String[] WORK_KEYWORDS = {"meeting", "deadline", "client", "project", "manager", "report",
            "schedule", "agenda", "presentation", "budget", "revenue", "quarterly", "task",
            "sprint", "standup", "stakeholder", "deliverable"};
    private static final String[] PERSONAL_KEYWORDS = {"family", "friend", "weekend", "vacation", "birthday",
            "diary", "journal", "feelings", "personal", "health", "gym", "workout", "recipe"};
    private static final String[] IDEAS_KEYWORDS = {"idea", "startup", "invention", "concept", "brainstorm",
            "inspiration", "creative", "design", "product", "plan", "vision"};

    // Smart tag suggestion keyword arrays (static to avoid recreation on every call)
    private static final String[][] TAG_KEYWORDS = {
        {"mathematics", "math", "calculus", "algebra", "equation", "formula"},
        {"programming", "code", "software", "algorithm", "function"},
        {"meeting", "agenda", "minutes"},
        {"personal", "diary", "journal"},
        {"idea", "ideas", "brainstorm"},
        {"todo", "task", "tasks", "checklist"},
        {"recipe", "cooking", "ingredients"},
        {"travel", "trip", "vacation", "hotel", "flight"},
        {"finance", "budget", "expense", "money"},
        {"health", "fitness", "workout", "gym"},
        {"reading", "book", "chapter"},
        {"research", "study", "reference"}
    };
    private static final String[] TAG_NAMES = {
        "mathematics", "programming", "meeting", "personal",
        "ideas", "todo", "recipe", "travel", "finance", "health", "reading", "research"
    };

    private final Context context;
    private final NoteRepository noteRepository;
    private ArrayList<NoteFolder> folders;

    public NoteFolderRepository(Context context, NoteRepository noteRepository) {
        this.context = context;
        this.noteRepository = noteRepository;
        this.folders = new ArrayList<>();
        loadFolders();
        seedDefaultFoldersIfNeeded();
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Persistence ────────────────────────────────────────────────

    private void loadFolders() {
        folders.clear();
        String json = getPrefs().getString(FOLDERS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                NoteFolder folder = NoteFolder.fromJson(array.getJSONObject(i));
                if (folder != null) folders.add(folder);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load folders: " + e.getMessage());
        }
    }

    private void saveFolders() {
        JSONArray array = new JSONArray();
        for (NoteFolder folder : folders) {
            array.put(folder.toJson());
        }
        getPrefs().edit().putString(FOLDERS_KEY, array.toString()).apply();
    }

    // ─── Seeding ─────────────────────────────────────────────────────

    private void seedDefaultFoldersIfNeeded() {
        if (getPrefs().getBoolean(SEEDED_KEY, false)) return;

        // Create 5 default root folders
        NoteFolder personal = new NoteFolder(NoteFolder.PERSONAL_FOLDER_NAME, "#3B82F6", "person", null, 0);
        personal.sortOrder = 0;
        NoteFolder work = new NoteFolder(NoteFolder.WORK_FOLDER_NAME, "#6366F1", "briefcase", null, 0);
        work.sortOrder = 1;
        NoteFolder study = new NoteFolder(NoteFolder.STUDY_FOLDER_NAME, "#8B5CF6", "book", null, 0);
        study.sortOrder = 2;
        NoteFolder ideas = new NoteFolder(NoteFolder.IDEAS_FOLDER_NAME, "#F59E0B", "lightbulb", null, 0);
        ideas.sortOrder = 3;
        NoteFolder quickNotes = new NoteFolder(NoteFolder.QUICK_NOTES_FOLDER_NAME, "#10B981", "rocket", null, 0);
        quickNotes.sortOrder = 4;

        folders.add(personal);
        folders.add(work);
        folders.add(study);
        folders.add(ideas);
        folders.add(quickNotes);
        saveFolders();

        getPrefs().edit().putBoolean(SEEDED_KEY, true).apply();
    }

    // ─── CRUD ────────────────────────────────────────────────────────

    public void addFolder(NoteFolder folder) {
        folders.add(folder);
        saveFolders();
    }

    public void updateFolder(NoteFolder folder) {
        folder.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < folders.size(); i++) {
            if (folders.get(i).id.equals(folder.id)) {
                folders.set(i, folder);
                break;
            }
        }
        saveFolders();
    }

    public NoteFolder getFolderById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (NoteFolder f : folders) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }

    public NoteFolder getFolderByName(String name) {
        for (NoteFolder f : folders) {
            if (f.name.equals(name)) return f;
        }
        return null;
    }

    /**
     * Delete a folder and all its descendant subfolders. Notes in the folder are moved to root (folderId = null).
     */
    public void deleteFolder(String folderId) {
        // Collect all descendant folder IDs
        Set<String> toDelete = new HashSet<>();
        toDelete.add(folderId);
        collectDescendantIds(folderId, toDelete);

        // Move notes to root
        for (Note note : noteRepository.getAllNotes()) {
            if (note.folderId != null && toDelete.contains(note.folderId)) {
                note.folderId = null;
                noteRepository.updateNote(note);
            }
        }

        // Remove folders
        folders.removeIf(f -> toDelete.contains(f.id));
        saveFolders();
    }

    private void collectDescendantIds(String parentId, Set<String> result) {
        for (NoteFolder f : folders) {
            if (parentId.equals(f.parentFolderId)) {
                result.add(f.id);
                collectDescendantIds(f.id, result);
            }
        }
    }

    // ─── Tree Navigation ──────────────────────────────────────────────

    /** Returns root-level folders sorted by sortOrder */
    public ArrayList<NoteFolder> getRootFolders() {
        ArrayList<NoteFolder> result = new ArrayList<>();
        for (NoteFolder f : folders) {
            if (f.parentFolderId == null || f.parentFolderId.isEmpty()) {
                result.add(f);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
        return result;
    }

    /** Returns direct children of a specific folder, sorted by sortOrder */
    public ArrayList<NoteFolder> getSubfolders(String parentFolderId) {
        ArrayList<NoteFolder> result = new ArrayList<>();
        for (NoteFolder f : folders) {
            if (parentFolderId.equals(f.parentFolderId)) {
                result.add(f);
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.sortOrder, b.sortOrder));
        return result;
    }

    /** Returns all folders */
    public ArrayList<NoteFolder> getAllFolders() {
        return new ArrayList<>(folders);
    }

    /** Get the path from root to the given folder as a list (root first) */
    public List<NoteFolder> getFolderPath(String folderId) {
        List<NoteFolder> path = new ArrayList<>();
        NoteFolder current = getFolderById(folderId);
        while (current != null) {
            path.add(0, current);
            current = getFolderById(current.parentFolderId);
        }
        return path;
    }

    // ─── Move Operations ──────────────────────────────────────────────

    /** Move a folder to a new parent (null = root). Returns false if would create cycle. */
    public boolean moveFolder(String folderId, String newParentFolderId) {
        if (folderId == null) return false;

        // Prevent moving into own descendant
        if (newParentFolderId != null && isDescendant(newParentFolderId, folderId)) {
            return false;
        }

        NoteFolder folder = getFolderById(folderId);
        if (folder == null) return false;

        folder.parentFolderId = (newParentFolderId == null || newParentFolderId.isEmpty()) ? null : newParentFolderId;
        // Recalculate depth
        folder.depth = (folder.parentFolderId == null) ? 0 : getDepth(folder.parentFolderId) + 1;
        folder.updatedAt = System.currentTimeMillis();

        // Update depths of all descendants
        updateDescendantDepths(folderId, folder.depth);
        saveFolders();
        return true;
    }

    /** Returns true if potentialDescendant is a descendant of ancestorId */
    private boolean isDescendant(String potentialDescendant, String ancestorId) {
        if (potentialDescendant == null || potentialDescendant.isEmpty()) return false;
        if (potentialDescendant.equals(ancestorId)) return true;
        NoteFolder f = getFolderById(potentialDescendant);
        if (f == null || f.parentFolderId == null) return false;
        return isDescendant(f.parentFolderId, ancestorId);
    }

    private int getDepth(String folderId) {
        NoteFolder f = getFolderById(folderId);
        if (f == null) return 0;
        if (f.parentFolderId == null) return 0;
        return 1 + getDepth(f.parentFolderId);
    }

    private void updateDescendantDepths(String parentId, int parentDepth) {
        for (NoteFolder f : folders) {
            if (parentId.equals(f.parentFolderId)) {
                f.depth = parentDepth + 1;
                updateDescendantDepths(f.id, f.depth);
            }
        }
    }

    // ─── Note Count (recursive) ───────────────────────────────────────

    /** Count notes directly in this folder + all descendant subfolders */
    public int getNoteCountRecursive(String folderId) {
        if (folderId == null) return 0;
        Set<String> folderIds = new HashSet<>();
        folderIds.add(folderId);
        collectDescendantIds(folderId, folderIds);

        int count = 0;
        for (Note note : noteRepository.getAllNotes()) {
            if (!note.isTrashed && !note.isArchived && note.folderId != null && folderIds.contains(note.folderId)) {
                count++;
            }
        }
        return count;
    }

    /** Get a few note titles from this folder for preview */
    public List<String> getPreviewNoteTitles(String folderId, int maxCount) {
        List<String> titles = new ArrayList<>();
        for (Note note : noteRepository.getAllNotes()) {
            if (!note.isTrashed && !note.isArchived && folderId.equals(note.folderId)) {
                String title = note.title.isEmpty() ? "Untitled" : note.title;
                titles.add(title);
                if (titles.size() >= maxCount) break;
            }
        }
        return titles;
    }

    // ─── Notes in Folder ──────────────────────────────────────────────

    /** Get notes directly in a specific folder (not recursive) */
    public ArrayList<Note> getNotesInFolder(String folderId) {
        ArrayList<Note> result = new ArrayList<>();
        for (Note note : noteRepository.getAllNotes()) {
            if (!note.isTrashed && !note.isArchived) {
                if (folderId == null && (note.folderId == null || note.folderId.isEmpty())) {
                    result.add(note);
                } else if (folderId != null && folderId.equals(note.folderId)) {
                    result.add(note);
                }
            }
        }
        Collections.sort(result, (a, b) -> {
            if (a.isPinned != b.isPinned) return a.isPinned ? -1 : 1;
            return Long.compare(b.updatedAt, a.updatedAt);
        });
        return result;
    }

    /** Move a note to a folder (null = root/All Notes) */
    public void moveNoteToFolder(String noteId, String folderId) {
        Note note = noteRepository.getNoteById(noteId);
        if (note != null) {
            note.folderId = (folderId == null || folderId.isEmpty()) ? null : folderId;
            noteRepository.updateNote(note);
        }
    }

    /** Bulk move notes to a folder */
    public void bulkMoveNotesToFolder(List<String> noteIds, String folderId) {
        for (String id : noteIds) {
            moveNoteToFolder(id, folderId);
        }
    }

    // ─── Smart Auto-Categorization ────────────────────────────────────

    /**
     * Suggest a folder for a note based on its content keywords.
     * Returns the folder ID or null if no suggestion.
     */
    public NoteFolder suggestFolderForNote(Note note) {
        String content = ((note.title != null ? note.title : "") + " " +
                          (note.plainTextPreview != null ? note.plainTextPreview : "")).toLowerCase();

        int studyScore = countKeywordMatches(content, STUDY_KEYWORDS);
        int workScore = countKeywordMatches(content, WORK_KEYWORDS);
        int personalScore = countKeywordMatches(content, PERSONAL_KEYWORDS);
        int ideasScore = countKeywordMatches(content, IDEAS_KEYWORDS);

        int maxScore = Math.max(Math.max(studyScore, workScore), Math.max(personalScore, ideasScore));
        if (maxScore == 0) return null;

        String suggestedName;
        if (maxScore == studyScore) suggestedName = NoteFolder.STUDY_FOLDER_NAME;
        else if (maxScore == workScore) suggestedName = NoteFolder.WORK_FOLDER_NAME;
        else if (maxScore == personalScore) suggestedName = NoteFolder.PERSONAL_FOLDER_NAME;
        else suggestedName = NoteFolder.IDEAS_FOLDER_NAME;

        return getFolderByName(suggestedName);
    }

    private int countKeywordMatches(String content, String[] keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (content.contains(kw)) count++;
        }
        return count;
    }

    /**
     * Suggest tags for a note based on content keywords.
     */
    public List<String> suggestTags(Note note) {
        List<String> suggestions = new ArrayList<>();
        String content = ((note.title != null ? note.title : "") + " " +
                          (note.plainTextPreview != null ? note.plainTextPreview : "")).toLowerCase();

        Set<String> existingTags = new HashSet<>(note.tags != null ? note.tags : new ArrayList<>());

        for (int i = 0; i < TAG_KEYWORDS.length; i++) {
            if (existingTags.contains(TAG_NAMES[i])) continue;
            for (String kw : TAG_KEYWORDS[i]) {
                if (content.contains(kw)) {
                    suggestions.add(TAG_NAMES[i]);
                    break;
                }
            }
        }
        return suggestions;
    }

    /**
     * Find related notes for a given note (same folder, shared tags, similar keywords).
     * Returns up to maxCount related notes.
     */
    public List<Note> getRelatedNotes(Note note, int maxCount) {
        List<Note> all = noteRepository.getAllNotes();
        List<NoteScore> scored = new ArrayList<>();

        for (Note other : all) {
            if (other.id.equals(note.id) || other.isTrashed || other.isArchived) continue;

            int score = 0;
            // Same folder
            if (note.folderId != null && note.folderId.equals(other.folderId)) score += 3;
            // Same category
            if (note.category != null && note.category.equals(other.category)) score += 2;
            // Shared tags
            if (note.tags != null && other.tags != null) {
                for (String tag : note.tags) {
                    if (other.tags.contains(tag)) score += 2;
                }
            }
            // Keyword overlap in title
            if (note.title != null && other.title != null && !note.title.isEmpty()) {
                String[] noteWords = note.title.toLowerCase().split("\\s+");
                String lowerOther = other.title.toLowerCase();
                for (String w : noteWords) {
                    if (w.length() > 3 && lowerOther.contains(w)) score++;
                }
            }

            if (score > 0) {
                scored.add(new NoteScore(other, score));
            }
        }

        Collections.sort(scored, (a, b) -> b.score - a.score);

        List<Note> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxCount, scored.size()); i++) {
            result.add(scored.get(i).note);
        }
        return result;
    }

    private static class NoteScore {
        Note note;
        int score;
        NoteScore(Note note, int score) {
            this.note = note;
            this.score = score;
        }
    }

    /**
     * Find notes with similar titles (for duplicate detection).
     */
    public List<Note> findSimilarNotes(String title) {
        List<Note> similar = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) return similar;
        String lower = title.toLowerCase().trim();

        for (Note note : noteRepository.getAllNotes()) {
            if (note.isTrashed) continue;
            if (note.title != null && !note.title.isEmpty()) {
                String otherLower = note.title.toLowerCase().trim();
                if (isSimilarTitle(lower, otherLower)) {
                    similar.add(note);
                }
            }
        }
        return similar;
    }

    private boolean isSimilarTitle(String a, String b) {
        if (a.equals(b)) return true;
        if (a.contains(b) || b.contains(a)) return true;
        // Simple word overlap check
        String[] aWords = a.split("\\s+");
        String[] bWords = b.split("\\s+");
        if (aWords.length == 0 || bWords.length == 0) return false;
        int overlap = 0;
        Set<String> bSet = new HashSet<>();
        for (String w : bWords) if (w.length() > 2) bSet.add(w);
        for (String w : aWords) if (w.length() > 2 && bSet.contains(w)) overlap++;
        int minLen = Math.min(aWords.length, bWords.length);
        return minLen > 0 && (double) overlap / minLen > 0.6;
    }

    // ─── Recently Viewed ──────────────────────────────────────────────

    private static final String RECENTLY_VIEWED_KEY = "recently_viewed_notes";

    public void recordNoteView(String noteId) {
        List<String> viewed = getRecentlyViewedIds();
        viewed.remove(noteId);
        viewed.add(0, noteId);
        if (viewed.size() > 10) viewed = new ArrayList<>(viewed.subList(0, 10));
        JSONArray arr = new JSONArray();
        for (String id : viewed) arr.put(id);
        getPrefs().edit().putString(RECENTLY_VIEWED_KEY, arr.toString()).apply();
    }

    public List<String> getRecentlyViewedIds() {
        List<String> ids = new ArrayList<>();
        String json = getPrefs().getString(RECENTLY_VIEWED_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) ids.add(arr.getString(i));
        } catch (JSONException e) { /* ignore */ }
        return ids;
    }

    public List<Note> getRecentlyViewedNotes(int maxCount) {
        List<Note> result = new ArrayList<>();
        for (String id : getRecentlyViewedIds()) {
            Note note = noteRepository.getNoteById(id);
            if (note != null && !note.isTrashed && !note.isArchived) {
                result.add(note);
                if (result.size() >= maxCount) break;
            }
        }
        return result;
    }

    // ─── Home Mode ─────────────────────────────────────────────────────

    public String getHomeMode() {
        return getPrefs().getString(HOME_MODE_KEY, "folders");
    }

    public void setHomeMode(String mode) {
        getPrefs().edit().putString(HOME_MODE_KEY, mode).apply();
    }
}
