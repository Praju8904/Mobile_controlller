package com.prajwal.myfirstapp.notes;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  SMART WRITING ASSISTANT — AI-like writing helpers that work 100% offline.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Features:
 *  • Auto-complete suggestions (phrase-level)
 *  • Grammar & style hints (passive voice, run-on sentences, weak words)
 *  • Tone detection (formal, casual, academic, creative)
 *  • Readability scoring (Flesch-Kincaid)
 *  • Text statistics (word count, sentence count, avg sentence length, reading time)
 *  • Summary generation (extractive — picks key sentences)
 *  • Vocabulary enhancement suggestions
 */
public class SmartWritingAssistant {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class WritingStats {
        public int wordCount;
        public int sentenceCount;
        public int paragraphCount;
        public int characterCount;
        public double avgSentenceLength;   // words per sentence
        public double readingTimeMinutes;  // at 200 wpm
        public String readabilityLevel;    // "Easy", "Medium", "Hard", "Academic"
        public double fleschScore;         // 0-100 scale
    }

    public static class StyleSuggestion {
        public String type;        // "passive_voice", "weak_word", "run_on", "repetition", "wordy"
        public String original;    // The problematic text
        public String suggestion;  // Replacement text or advice
        public int startPos;       // Position in text
        public String icon;        // Emoji icon

        public StyleSuggestion(String type, String original, String suggestion, int startPos, String icon) {
            this.type = type;
            this.original = original;
            this.suggestion = suggestion;
            this.startPos = startPos;
            this.icon = icon;
        }
    }

    public static class AutoCompleteSuggestion {
        public String text;
        public float confidence;

        public AutoCompleteSuggestion(String text, float confidence) {
            this.text = text;
            this.confidence = confidence;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PATTERN DATABASES
    // ═══════════════════════════════════════════════════════════════════════════════

    // Passive voice patterns
    private static final Pattern PASSIVE_PATTERN = Pattern.compile(
            "\\b(was|were|is|are|been|being|be)\\s+(\\w+ed|\\w+en)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Weak words to suggest replacements for
    private static final Map<String, String[]> WEAK_WORD_SUGGESTIONS = new HashMap<>();
    static {
        WEAK_WORD_SUGGESTIONS.put("very", new String[]{"extremely", "remarkably", "exceptionally"});
        WEAK_WORD_SUGGESTIONS.put("really", new String[]{"truly", "genuinely", "indeed"});
        WEAK_WORD_SUGGESTIONS.put("good", new String[]{"excellent", "outstanding", "superb"});
        WEAK_WORD_SUGGESTIONS.put("bad", new String[]{"poor", "terrible", "dreadful"});
        WEAK_WORD_SUGGESTIONS.put("big", new String[]{"enormous", "massive", "substantial"});
        WEAK_WORD_SUGGESTIONS.put("small", new String[]{"tiny", "minute", "compact"});
        WEAK_WORD_SUGGESTIONS.put("nice", new String[]{"pleasant", "delightful", "wonderful"});
        WEAK_WORD_SUGGESTIONS.put("thing", new String[]{"aspect", "element", "factor"});
        WEAK_WORD_SUGGESTIONS.put("stuff", new String[]{"material", "content", "items"});
        WEAK_WORD_SUGGESTIONS.put("get", new String[]{"obtain", "acquire", "receive"});
        WEAK_WORD_SUGGESTIONS.put("got", new String[]{"obtained", "acquired", "received"});
        WEAK_WORD_SUGGESTIONS.put("a lot", new String[]{"numerous", "many", "several"});
        WEAK_WORD_SUGGESTIONS.put("said", new String[]{"stated", "mentioned", "explained"});
        WEAK_WORD_SUGGESTIONS.put("went", new String[]{"traveled", "proceeded", "ventured"});
    }

    // Wordy phrases with concise alternatives
    private static final Map<String, String> WORDY_PHRASES = new HashMap<>();
    static {
        WORDY_PHRASES.put("in order to", "to");
        WORDY_PHRASES.put("due to the fact that", "because");
        WORDY_PHRASES.put("at this point in time", "now");
        WORDY_PHRASES.put("in the event that", "if");
        WORDY_PHRASES.put("for the purpose of", "to");
        WORDY_PHRASES.put("on a daily basis", "daily");
        WORDY_PHRASES.put("is able to", "can");
        WORDY_PHRASES.put("has the ability to", "can");
        WORDY_PHRASES.put("in spite of the fact that", "although");
        WORDY_PHRASES.put("at the present time", "currently");
        WORDY_PHRASES.put("with regard to", "regarding");
        WORDY_PHRASES.put("make a decision", "decide");
        WORDY_PHRASES.put("take into consideration", "consider");
        WORDY_PHRASES.put("a large number of", "many");
        WORDY_PHRASES.put("the majority of", "most");
    }

    // Tone-indicator word sets
    private static final Set<String> FORMAL_WORDS = new HashSet<>(Arrays.asList(
            "therefore", "furthermore", "consequently", "whereas", "hereby",
            "henceforth", "nevertheless", "notwithstanding", "pursuant", "accordingly"
    ));
    private static final Set<String> CASUAL_WORDS = new HashSet<>(Arrays.asList(
            "gonna", "wanna", "gotta", "kinda", "sorta", "yeah", "nah",
            "cool", "awesome", "lol", "btw", "tbh", "imo"
    ));
    private static final Set<String> ACADEMIC_WORDS = new HashSet<>(Arrays.asList(
            "hypothesis", "methodology", "empirical", "theoretical", "paradigm",
            "quantitative", "qualitative", "systematic", "correlation", "variable",
            "analysis", "synthesis", "abstract", "conclusion", "research"
    ));

    // Common phrase completions
    private static final Map<String, String[]> PHRASE_COMPLETIONS = new HashMap<>();
    static {
        PHRASE_COMPLETIONS.put("in conclusion", new String[]{
                "in conclusion, ", "in conclusion, the key takeaway is ",
                "in conclusion, we can observe that "
        });
        PHRASE_COMPLETIONS.put("for example", new String[]{
                "for example, ", "for example, consider the case of ",
                "for example, a common scenario is "
        });
        PHRASE_COMPLETIONS.put("on the other hand", new String[]{
                "on the other hand, ", "on the other hand, it could be argued that "
        });
        PHRASE_COMPLETIONS.put("it is important", new String[]{
                "it is important to note that ", "it is important to consider ",
                "it is important to understand "
        });
        PHRASE_COMPLETIONS.put("the purpose of", new String[]{
                "the purpose of this ", "the purpose of this note is to "
        });
        PHRASE_COMPLETIONS.put("as a result", new String[]{
                "as a result, ", "as a result of this, "
        });
        PHRASE_COMPLETIONS.put("according to", new String[]{
                "according to ", "according to the data, ", "according to the analysis, "
        });
        PHRASE_COMPLETIONS.put("in addition", new String[]{
                "in addition, ", "in addition to this, "
        });
        PHRASE_COMPLETIONS.put("to summarize", new String[]{
                "to summarize, ", "to summarize the key points, "
        });
        PHRASE_COMPLETIONS.put("first of all", new String[]{
                "first of all, ", "first of all, it is necessary to "
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEXT STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Compute comprehensive writing statistics for the given text.
     */
    public static WritingStats computeStats(String text) {
        WritingStats stats = new WritingStats();
        if (text == null || text.trim().isEmpty()) return stats;

        String trimmed = text.trim();

        // Character count (excluding spaces)
        stats.characterCount = trimmed.replaceAll("\\s", "").length();

        // Word count
        String[] words = trimmed.split("\\s+");
        stats.wordCount = words.length;

        // Sentence count (split on . ! ?)
        String[] sentences = trimmed.split("[.!?]+");
        stats.sentenceCount = 0;
        for (String s : sentences) {
            if (s.trim().length() > 0) stats.sentenceCount++;
        }
        if (stats.sentenceCount == 0) stats.sentenceCount = 1;

        // Paragraph count
        String[] paragraphs = trimmed.split("\\n\\s*\\n");
        stats.paragraphCount = 0;
        for (String p : paragraphs) {
            if (p.trim().length() > 0) stats.paragraphCount++;
        }
        if (stats.paragraphCount == 0) stats.paragraphCount = 1;

        // Average sentence length
        stats.avgSentenceLength = (double) stats.wordCount / stats.sentenceCount;

        // Reading time (200 wpm average)
        stats.readingTimeMinutes = stats.wordCount / 200.0;

        // Flesch-Kincaid readability
        int totalSyllables = 0;
        for (String word : words) {
            totalSyllables += countSyllables(word);
        }
        double asl = (double) stats.wordCount / stats.sentenceCount;
        double asw = (double) totalSyllables / stats.wordCount;
        stats.fleschScore = 206.835 - (1.015 * asl) - (84.6 * asw);
        stats.fleschScore = Math.max(0, Math.min(100, stats.fleschScore));

        if (stats.fleschScore >= 70) stats.readabilityLevel = "Easy";
        else if (stats.fleschScore >= 50) stats.readabilityLevel = "Medium";
        else if (stats.fleschScore >= 30) stats.readabilityLevel = "Hard";
        else stats.readabilityLevel = "Academic";

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  STYLE SUGGESTIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze text for style issues (passive voice, weak words, wordy phrases, run-ons).
     * Returns a list of suggestions, limited to 10.
     */
    public static List<StyleSuggestion> analyzeStyle(String text) {
        List<StyleSuggestion> suggestions = new ArrayList<>();
        if (text == null || text.isEmpty()) return suggestions;

        String lower = text.toLowerCase();

        // 1. Passive voice detection
        Matcher pm = PASSIVE_PATTERN.matcher(text);
        while (pm.find() && suggestions.size() < 10) {
            suggestions.add(new StyleSuggestion(
                    "passive_voice", pm.group(),
                    "Consider using active voice",
                    pm.start(), "🔄"
            ));
        }

        // 2. Weak words
        for (Map.Entry<String, String[]> entry : WEAK_WORD_SUGGESTIONS.entrySet()) {
            String weak = entry.getKey();
            int idx = lower.indexOf(weak);
            if (idx >= 0 && suggestions.size() < 10) {
                String[] replacements = entry.getValue();
                String suggestion = "Try: " + String.join(", ", replacements);
                suggestions.add(new StyleSuggestion(
                        "weak_word", weak, suggestion, idx, "💪"
                ));
            }
        }

        // 3. Wordy phrases
        for (Map.Entry<String, String> entry : WORDY_PHRASES.entrySet()) {
            int idx = lower.indexOf(entry.getKey());
            if (idx >= 0 && suggestions.size() < 10) {
                suggestions.add(new StyleSuggestion(
                        "wordy", entry.getKey(),
                        "Replace with: \"" + entry.getValue() + "\"",
                        idx, "✂️"
                ));
            }
        }

        // 4. Run-on sentence detection (sentences > 35 words)
        String[] sentences = text.split("[.!?]+");
        int pos = 0;
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) { pos += sentence.length() + 1; continue; }
            int wc = trimmed.split("\\s+").length;
            if (wc > 35 && suggestions.size() < 10) {
                suggestions.add(new StyleSuggestion(
                        "run_on", trimmed.substring(0, Math.min(50, trimmed.length())) + "...",
                        "This sentence has " + wc + " words. Consider splitting it.",
                        pos, "📏"
                ));
            }
            pos += sentence.length() + 1;
        }

        // 5. Word repetition (same content word used > 3 times)
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String w : lower.split("\\s+")) {
            String cleaned = w.replaceAll("[^a-z]", "");
            if (cleaned.length() > 4 && SmartNotesHelper.countWords(cleaned + " ") == 0) {
                // Not a stop word — just count
            }
            if (cleaned.length() > 4) {
                wordFreq.put(cleaned, wordFreq.getOrDefault(cleaned, 0) + 1);
            }
        }
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            if (entry.getValue() > 3 && suggestions.size() < 10) {
                suggestions.add(new StyleSuggestion(
                        "repetition", entry.getKey(),
                        "\"" + entry.getKey() + "\" appears " + entry.getValue() + " times. Use synonyms.",
                        lower.indexOf(entry.getKey()), "🔁"
                ));
            }
        }

        return suggestions;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TONE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Analyze the tone of the text.
     * @return Map with keys: "tone" (String), "formalScore", "casualScore", "academicScore" (Integer)
     */
    public static Map<String, Object> detectTone(String text) {
        Map<String, Object> result = new HashMap<>();
        if (text == null || text.isEmpty()) {
            result.put("tone", "Neutral");
            result.put("formalScore", 0);
            result.put("casualScore", 0);
            result.put("academicScore", 0);
            return result;
        }

        String lower = text.toLowerCase();
        String[] words = lower.split("\\s+");
        int formalScore = 0, casualScore = 0, academicScore = 0;

        for (String word : words) {
            String cleaned = word.replaceAll("[^a-z]", "");
            if (FORMAL_WORDS.contains(cleaned)) formalScore++;
            if (CASUAL_WORDS.contains(cleaned)) casualScore++;
            if (ACADEMIC_WORDS.contains(cleaned)) academicScore++;
        }

        // Check for contractions (casual indicator)
        if (lower.contains("don't") || lower.contains("can't") || lower.contains("won't") ||
                lower.contains("wouldn't") || lower.contains("shouldn't") || lower.contains("i'm") ||
                lower.contains("it's") || lower.contains("they're") || lower.contains("we're")) {
            casualScore += 2;
        }

        // Determine tone
        String tone;
        if (academicScore > formalScore && academicScore > casualScore) tone = "Academic";
        else if (formalScore > casualScore) tone = "Formal";
        else if (casualScore > formalScore) tone = "Casual";
        else tone = "Neutral";

        result.put("tone", tone);
        result.put("formalScore", formalScore);
        result.put("casualScore", casualScore);
        result.put("academicScore", academicScore);
        return result;
    }

    /**
     * Get tone emoji for display.
     */
    public static String getToneEmoji(String tone) {
        if (tone == null) return "📝";
        switch (tone) {
            case "Formal": return "🎩";
            case "Casual": return "😊";
            case "Academic": return "🎓";
            default: return "📝";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  AUTO-COMPLETE
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Suggest completions based on the last few words typed.
     * @param currentText The full text so far.
     * @return Up to 3 suggestions sorted by confidence.
     */
    public static List<AutoCompleteSuggestion> suggestCompletions(String currentText) {
        List<AutoCompleteSuggestion> results = new ArrayList<>();
        if (currentText == null || currentText.trim().isEmpty()) return results;

        String lower = currentText.trim().toLowerCase();

        // Check phrase completions
        for (Map.Entry<String, String[]> entry : PHRASE_COMPLETIONS.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                for (String completion : entry.getValue()) {
                    // Remove the matching prefix from the completion
                    String suffix = completion.substring(entry.getKey().length());
                    if (!suffix.isEmpty()) {
                        results.add(new AutoCompleteSuggestion(suffix, 0.85f));
                    }
                }
                break;
            }
        }

        // Check partial phrase matches (user is typing a phrase)
        if (results.isEmpty()) {
            String lastWords = getLastNWords(lower, 3);
            for (Map.Entry<String, String[]> entry : PHRASE_COMPLETIONS.entrySet()) {
                if (entry.getKey().startsWith(lastWords) && !entry.getKey().equals(lastWords)) {
                    String remaining = entry.getKey().substring(lastWords.length());
                    results.add(new AutoCompleteSuggestion(remaining, 0.7f));
                }
            }
        }

        // Bullet/list continuation
        if (lower.endsWith("\n- ") || lower.endsWith("\n• ")) {
            results.add(new AutoCompleteSuggestion("", 0.5f)); // continue list
        }

        // Limit to 3
        if (results.size() > 3) results = results.subList(0, 3);
        return results;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  EXTRACTIVE SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Generate an extractive summary by selecting the most important sentences.
     * Uses sentence scoring based on word frequency, position, and length.
     * @param text The full text to summarize.
     * @param maxSentences Maximum number of sentences in the summary.
     * @return Summary text.
     */
    public static String generateSummary(String text, int maxSentences) {
        if (text == null || text.trim().isEmpty()) return "";

        // Split into sentences
        String[] rawSentences = text.split("(?<=[.!?])\\s+");
        if (rawSentences.length <= maxSentences) return text;

        // Build word frequency map (excluding stop words)
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String sentence : rawSentences) {
            for (String word : sentence.toLowerCase().split("\\s+")) {
                String cleaned = word.replaceAll("[^a-z]", "");
                if (cleaned.length() > 3) {
                    wordFreq.put(cleaned, wordFreq.getOrDefault(cleaned, 0) + 1);
                }
            }
        }

        // Score each sentence
        class ScoredSentence {
            int index;
            String text;
            double score;
            ScoredSentence(int i, String t, double s) { index = i; text = t; score = s; }
        }

        List<ScoredSentence> scored = new ArrayList<>();
        for (int i = 0; i < rawSentences.length; i++) {
            String sentence = rawSentences[i].trim();
            if (sentence.isEmpty()) continue;

            double score = 0;

            // Word frequency score
            for (String word : sentence.toLowerCase().split("\\s+")) {
                String cleaned = word.replaceAll("[^a-z]", "");
                score += wordFreq.getOrDefault(cleaned, 0);
            }

            // Normalize by sentence length to avoid bias toward long sentences
            int wordCount = sentence.split("\\s+").length;
            if (wordCount > 0) score /= wordCount;

            // Position bonus (first and last sentences are usually important)
            if (i == 0) score *= 1.5;
            else if (i == rawSentences.length - 1) score *= 1.3;
            else if (i < rawSentences.length * 0.2) score *= 1.2; // early sentences

            // Length penalty for very short sentences
            if (wordCount < 4) score *= 0.5;

            scored.add(new ScoredSentence(i, sentence, score));
        }

        // Sort by score descending, pick top N
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredSentence> selected = scored.subList(0, Math.min(maxSentences, scored.size()));

        // Re-sort by original position for coherent output
        selected.sort((a, b) -> a.index - b.index);

        StringBuilder summary = new StringBuilder();
        for (ScoredSentence s : selected) {
            if (summary.length() > 0) summary.append(" ");
            summary.append(s.text);
        }

        return summary.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  VOCABULARY ENHANCEMENT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get synonym/replacement suggestions for a specific word.
     */
    public static List<String> suggestSynonyms(String word) {
        List<String> synonyms = new ArrayList<>();
        if (word == null) return synonyms;

        String lower = word.toLowerCase().trim();
        String[] replacements = WEAK_WORD_SUGGESTIONS.get(lower);
        if (replacements != null) {
            synonyms.addAll(Arrays.asList(replacements));
        }
        return synonyms;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Simple syllable counter for English words (heuristic). */
    private static int countSyllables(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.isEmpty()) return 1;
        if (word.length() <= 3) return 1;

        int count = 0;
        boolean prevVowel = false;
        for (int i = 0; i < word.length(); i++) {
            boolean isVowel = "aeiouy".indexOf(word.charAt(i)) >= 0;
            if (isVowel && !prevVowel) count++;
            prevVowel = isVowel;
        }

        // Silent e
        if (word.endsWith("e") && count > 1) count--;
        // Ensure at least 1
        return Math.max(1, count);
    }

    /** Get last N words from text. */
    private static String getLastNWords(String text, int n) {
        String[] words = text.split("\\s+");
        if (words.length <= n) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - n; i < words.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
