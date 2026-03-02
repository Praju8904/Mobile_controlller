package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NoteRelationsManager — Bidirectional note linking, backlinks, and @mentions tracking.
 *  - Manages note-to-note relations (A links to B → B backlinks to A)
 *  - Tracks @mentions across all notes for quick backlink resolution
 *  - Resolves mentions: @noteTitle → note ID lookup
 *  - Stored in SharedPreferences as JSON for offline access
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class NoteRelationsManager {

    private static final String PREFS_NAME = "note_relations_prefs";
    private static final String KEY_RELATIONS = "relations";        // noteId → [related noteIds]
    private static final String KEY_MENTIONS = "mentions";          // noteId → [mentioned noteIds]
    private static final String KEY_BACKLINKS = "backlinks_cache";  // noteId → [noteIds that mention this]

    private final SharedPreferences prefs;
    private final Context context;

    // In-memory caches
    private Map<String, Set<String>> relationsMap;   // noteId → set of related noteIds
    private Map<String, Set<String>> mentionsMap;    // noteId → set of mentioned noteIds
    private Map<String, Set<String>> backlinksCache; // noteId → set of noteIds that link-to/mention this

    public NoteRelationsManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadAll();
    }

    // ═══ Relations (explicit bidirectional links) ═══

    /** Add a bidirectional relation between two notes */
    public void addRelation(String noteIdA, String noteIdB) {
        if (noteIdA.equals(noteIdB)) return;
        getOrCreateSet(relationsMap, noteIdA).add(noteIdB);
        getOrCreateSet(relationsMap, noteIdB).add(noteIdA);
        rebuildBacklinks();
        saveAll();
    }

    /** Remove a bidirectional relation */
    public void removeRelation(String noteIdA, String noteIdB) {
        Set<String> setA = relationsMap.get(noteIdA);
        Set<String> setB = relationsMap.get(noteIdB);
        if (setA != null) setA.remove(noteIdB);
        if (setB != null) setB.remove(noteIdA);
        rebuildBacklinks();
        saveAll();
    }

    /** Get all notes directly related to the given note */
    public List<String> getRelatedNoteIds(String noteId) {
        Set<String> set = relationsMap.get(noteId);
        return set != null ? new ArrayList<>(set) : new ArrayList<>();
    }

    /** Check if two notes are related */
    public boolean areRelated(String noteIdA, String noteIdB) {
        Set<String> set = relationsMap.get(noteIdA);
        return set != null && set.contains(noteIdB);
    }

    // ═══ Mentions (@noteTitle links) ═══

    /** Update the mentions for a note (called when note content changes) */
    public void updateMentions(String noteId, List<String> mentionedNoteIds) {
        mentionsMap.put(noteId, new HashSet<>(mentionedNoteIds));
        rebuildBacklinks();
        saveAll();
    }

    /** Get all note IDs that are @mentioned in the given note */
    public List<String> getMentionedNoteIds(String noteId) {
        Set<String> set = mentionsMap.get(noteId);
        return set != null ? new ArrayList<>(set) : new ArrayList<>();
    }

    // ═══ Backlinks (reverse lookup) ═══

    /** Get all note IDs that reference/mention/link to the given note */
    public List<String> getBacklinks(String noteId) {
        Set<String> set = backlinksCache.get(noteId);
        return set != null ? new ArrayList<>(set) : new ArrayList<>();
    }

    /** Rebuild backlinks cache from relations + mentions */
    private void rebuildBacklinks() {
        backlinksCache.clear();

        // From relations (bidirectional → both directions)
        for (Map.Entry<String, Set<String>> entry : relationsMap.entrySet()) {
            String fromId = entry.getKey();
            for (String toId : entry.getValue()) {
                getOrCreateSet(backlinksCache, toId).add(fromId);
            }
        }

        // From mentions
        for (Map.Entry<String, Set<String>> entry : mentionsMap.entrySet()) {
            String fromId = entry.getKey();
            for (String toId : entry.getValue()) {
                getOrCreateSet(backlinksCache, toId).add(fromId);
            }
        }
    }

    // ═══ Mention Resolution ═══

    /** Find note IDs matching a partial title (for @mention autocomplete) */
    public List<Note> findMatchingNotes(String partialTitle, NoteRepository repository) {
        List<Note> all = repository.getAllNotes();
        List<Note> matches = new ArrayList<>();
        String query = partialTitle.toLowerCase().trim();

        for (Note note : all) {
            String title = note.title;
            if (title != null && title.toLowerCase().contains(query)) {
                matches.add(note);
                if (matches.size() >= 10) break; // Limit results
            }
        }
        return matches;
    }

    /** Extract @mentions from block text content */
    public List<String> extractMentions(String text) {
        List<String> mentions = new ArrayList<>();
        if (text == null || text.isEmpty()) return mentions;

        int idx = 0;
        while (idx < text.length()) {
            int atPos = text.indexOf('@', idx);
            if (atPos < 0) break;

            // Find the mention text (enclosed in [[ ]] or until whitespace)
            int start = atPos + 1;
            if (start < text.length() && text.charAt(start) == '[' && start + 1 < text.length() && text.charAt(start + 1) == '[') {
                // [[note title]] style mention
                int end = text.indexOf("]]", start + 2);
                if (end > start) {
                    mentions.add(text.substring(start + 2, end));
                    idx = end + 2;
                    continue;
                }
            }
            idx = start;
        }
        return mentions;
    }

    /** Parse @[[note title]] mentions in all blocks and resolve to note IDs */
    public List<String> resolveBlockMentions(List<ContentBlock> blocks, NoteRepository repository) {
        Set<String> mentionedIds = new HashSet<>();

        for (ContentBlock block : blocks) {
            if (!block.isTextBased()) continue;
            String text = block.getText();
            List<String> mentionTitles = extractMentions(text);

            for (String title : mentionTitles) {
                List<Note> matches = findMatchingNotes(title, repository);
                for (Note match : matches) {
                    if (match.title.equalsIgnoreCase(title)) {
                        mentionedIds.add(match.id);
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(mentionedIds);
    }

    // ═══ Cleanup ═══

    /** Remove all relations and mentions for a deleted note */
    public void removeNote(String noteId) {
        // Remove from relations
        Set<String> related = relationsMap.remove(noteId);
        if (related != null) {
            for (String otherId : related) {
                Set<String> otherSet = relationsMap.get(otherId);
                if (otherSet != null) otherSet.remove(noteId);
            }
        }

        // Remove mentions
        mentionsMap.remove(noteId);

        // Remove from other notes' mentions
        for (Set<String> set : mentionsMap.values()) {
            set.remove(noteId);
        }

        rebuildBacklinks();
        saveAll();
    }

    // ═══ Persistence ═══

    private void loadAll() {
        relationsMap = loadMapOfSets(KEY_RELATIONS);
        mentionsMap = loadMapOfSets(KEY_MENTIONS);
        backlinksCache = loadMapOfSets(KEY_BACKLINKS);

        if (backlinksCache.isEmpty() && (!relationsMap.isEmpty() || !mentionsMap.isEmpty())) {
            rebuildBacklinks();
        }
    }

    private void saveAll() {
        saveMapOfSets(KEY_RELATIONS, relationsMap);
        saveMapOfSets(KEY_MENTIONS, mentionsMap);
        saveMapOfSets(KEY_BACKLINKS, backlinksCache);
    }

    private Map<String, Set<String>> loadMapOfSets(String key) {
        Map<String, Set<String>> map = new HashMap<>();
        String json = prefs.getString(key, null);
        if (json == null) return map;

        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONArray arr = obj.getJSONArray(k);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                map.put(k, set);
            }
        } catch (JSONException ignored) {}
        return map;
    }

    private void saveMapOfSets(String key, Map<String, Set<String>> map) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String val : entry.getValue()) arr.put(val);
                obj.put(entry.getKey(), arr);
            }
        } catch (JSONException ignored) {}
        prefs.edit().putString(key, obj.toString()).apply();
    }

    private Set<String> getOrCreateSet(Map<String, Set<String>> map, String key) {
        Set<String> set = map.get(key);
        if (set == null) {
            set = new HashSet<>();
            map.put(key, set);
        }
        return set;
    }

    /** Get stats for debugging/display */
    public String getStats() {
        int totalRelations = 0;
        for (Set<String> set : relationsMap.values()) totalRelations += set.size();
        int totalMentions = 0;
        for (Set<String> set : mentionsMap.values()) totalMentions += set.size();
        return "Relations: " + totalRelations / 2 + " pairs, Mentions: " + totalMentions
            + ", Backlinks entries: " + backlinksCache.size();
    }
}
