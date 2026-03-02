package com.prajwal.myfirstapp.notes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  SMART NOTES HELPER — Static utility methods for intelligent note features
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Auto-categorization based on content keywords
 * - Tag suggestions from content analysis
 * - Related notes discovery via scoring
 * - Duplicate/similar note detection (Jaccard similarity)
 * - Word count helper
 */
public class SmartNotesHelper {

    // ─── Stop words excluded from title-overlap scoring ──────────────
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "that", "this", "with", "have", "from",
            "they", "will", "been", "were", "said", "each", "which", "their",
            "time", "about", "would", "make", "than", "into", "could", "more"
    ));

    // ─── Category keyword maps ────────────────────────────────────────
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new HashMap<>();

    static {
        CATEGORY_KEYWORDS.put("Study", new String[]{
                "study", "exam", "lecture", "assignment", "homework", "notes",
                "chapter", "quiz", "test", "university", "college"
        });
        CATEGORY_KEYWORDS.put("Work", new String[]{
                "meeting", "client", "deadline", "project", "report", "office",
                "work", "business", "task", "agenda", "presentation"
        });
        CATEGORY_KEYWORDS.put("Personal", new String[]{
                "recipe", "ingredients", "cook", "food", "journal", "diary",
                "personal", "health", "workout", "fitness"
        });
        CATEGORY_KEYWORDS.put("Ideas", new String[]{
                "idea", "brainstorm", "concept", "plan", "innovation", "creative"
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  AUTO-CATEGORIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyzes title and body keywords to suggest a category.
     * Returns the category with the most keyword hits, or null if no strong match.
     */
    public static String suggestCategory(String title, String body) {
        String combined = ((title != null ? title : "") + " " + (body != null ? body : ""))
                .toLowerCase();

        String bestCategory = null;
        int bestScore = 0;

        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (combined.contains(keyword)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        return bestScore > 0 ? bestCategory : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SMART TAG SUGGESTIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns up to 5 tag suggestions based on content, excluding tags already present.
     */
    public static List<String> suggestTags(String title, String body, List<String> existingTags) {
        String combined = ((title != null ? title : "") + " " + (body != null ? body : ""))
                .toLowerCase();

        Set<String> existing = new HashSet<>();
        if (existingTags != null) {
            for (String t : existingTags) existing.add(t.toLowerCase());
        }

        List<String> suggestions = new ArrayList<>();

        // Programming languages
        String[] langs = {"java", "python", "kotlin", "javascript", "typescript",
                "swift", "cpp", "html", "css", "sql", "rust", "go"};
        boolean hasProgramming = false;
        for (String lang : langs) {
            if (combined.contains(lang) && !existing.contains(lang) && suggestions.size() < 5) {
                suggestions.add(lang);
                hasProgramming = true;
            }
        }
        if (hasProgramming && !existing.contains("programming") && suggestions.size() < 5) {
            suggestions.add(0, "programming");
        }

        // Finance
        if ((combined.contains("finance") || combined.contains("budget") || combined.contains("expense"))
                && suggestions.size() < 5) {
            if (!existing.contains("finance")) suggestions.add("finance");
            if (!existing.contains("budget") && combined.contains("budget") && suggestions.size() < 5)
                suggestions.add("budget");
        }

        // Meeting / project
        if (combined.contains("meeting") && !existing.contains("meeting") && suggestions.size() < 5)
            suggestions.add("meeting");
        if (combined.contains("project") && !existing.contains("project") && suggestions.size() < 5)
            suggestions.add("project");

        // Recipe / food
        if ((combined.contains("recipe") || combined.contains("ingredients") || combined.contains("cook"))
                && suggestions.size() < 5) {
            if (!existing.contains("recipe")) suggestions.add("recipe");
            if (!existing.contains("food") && suggestions.size() < 5) suggestions.add("food");
        }

        // Study
        if ((combined.contains("study") || combined.contains("exam") || combined.contains("lecture"))
                && !existing.contains("study") && suggestions.size() < 5) {
            suggestions.add("study");
        }

        // Trim to 5
        return suggestions.subList(0, Math.min(5, suggestions.size()));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RELATED NOTES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Scores each note relative to currentNote and returns the top maxResults matches.
     * Scoring:
     *   +3 same folder, +2 same category, +2 per shared tag,
     *   +1 per shared significant title word (>4 chars, not a stop word)
     */
    public static List<Note> findRelatedNotes(Note currentNote, List<Note> allNotes, int maxResults) {
        if (currentNote == null || allNotes == null) return new ArrayList<>();

        Set<String> currentTags = new HashSet<>();
        if (currentNote.tags != null) {
            for (String t : currentNote.tags) currentTags.add(t.toLowerCase());
        }
        Set<String> currentTitleWords = significantWords(currentNote.title);

        // Score map: note id -> score
        List<ScoredNote> scored = new ArrayList<>();

        for (Note note : allNotes) {
            if (note.id.equals(currentNote.id)) continue;

            int score = 0;

            // Same folder
            if (currentNote.folderId != null && currentNote.folderId.equals(note.folderId)) score += 3;

            // Same category
            if (currentNote.category != null && currentNote.category.equals(note.category)) score += 2;

            // Shared tags
            if (note.tags != null) {
                for (String tag : note.tags) {
                    if (currentTags.contains(tag.toLowerCase())) score += 2;
                }
            }

            // Title word overlap
            for (String word : significantWords(note.title)) {
                if (currentTitleWords.contains(word)) score += 1;
            }

            if (score > 0) scored.add(new ScoredNote(note, score));
        }

        // Sort descending by score
        scored.sort((a, b) -> b.score - a.score);

        List<Note> result = new ArrayList<>();
        int limit = Math.min(maxResults, scored.size());
        for (int i = 0; i < limit; i++) result.add(scored.get(i).note);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DUPLICATE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns the first note whose title Jaccard similarity to the input exceeds 0.7,
     * or null if no such note exists.
     */
    public static Note findSimilarNote(String title, List<Note> allNotes) {
        if (title == null || allNotes == null) return null;
        for (Note note : allNotes) {
            if (titleSimilarity(title, note.title) > 0.7) return note;
        }
        return null;
    }

    /**
     * Jaccard similarity between two titles: |intersection| / |union| of their word sets.
     * Words are normalized to lowercase and split on spaces/punctuation.
     */
    private static double titleSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.toLowerCase().split("[\\s\\p{Punct}]+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.toLowerCase().split("[\\s\\p{Punct}]+")));
        wordsA.remove("");
        wordsB.remove("");

        if (wordsA.isEmpty() && wordsB.isEmpty()) return 1.0;
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return (double) intersection.size() / union.size();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WORD COUNT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Counts words in plain text, treating consecutive whitespace as one delimiter.
     */
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  KEYWORD EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Extracts the top N keywords from the text by frequency (non-stopword, >3 chars).
     */
    public static List<String> extractKeywords(String text, int maxKeywords) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            if (w.length() > 3 && !STOP_WORDS.contains(w)) {
                freq.put(w, freq.getOrDefault(w, 0) + 1);
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxKeywords, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ENHANCED SIMILARITY — Body-aware
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Find notes similar to the given text (title + body content).
     * Uses keyword overlap scoring — returns notes scoring above threshold.
     */
    public static List<Note> findSimilarNotes(String title, String body, List<Note> allNotes, int maxResults) {
        if (allNotes == null) return new ArrayList<>();
        String combined = (title != null ? title : "") + " " + (body != null ? body : "");
        List<String> sourceKeywords = extractKeywords(combined, 15);
        if (sourceKeywords.isEmpty()) return new ArrayList<>();

        Set<String> sourceSet = new HashSet<>(sourceKeywords);
        List<ScoredNote> scored = new ArrayList<>();

        for (Note note : allNotes) {
            String noteText = (note.title != null ? note.title : "") + " "
                    + (note.plainTextPreview != null ? note.plainTextPreview : "");
            List<String> noteKeywords = extractKeywords(noteText, 15);
            int overlap = 0;
            for (String kw : noteKeywords) {
                if (sourceSet.contains(kw)) overlap++;
            }
            if (overlap >= 2) { // at least 2 shared keywords
                scored.add(new ScoredNote(note, overlap));
            }
        }

        scored.sort((a, b) -> b.score - a.score);
        List<Note> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, scored.size()); i++) {
            result.add(scored.get(i).note);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Returns lowercase words from text that are >4 chars and not stop words. */
    private static Set<String> significantWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null || text.isEmpty()) return words;
        for (String word : text.toLowerCase().split("[\\s\\p{Punct}]+")) {
            if (word.length() > 4 && !STOP_WORDS.contains(word)) words.add(word);
        }
        return words;
    }

    /** Simple pair used internally for scoring. */
    private static class ScoredNote {
        final Note note;
        final int score;

        ScoredNote(Note note, int score) {
            this.note = note;
            this.score = score;
        }
    }
}
