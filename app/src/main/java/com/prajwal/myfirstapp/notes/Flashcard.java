package com.prajwal.myfirstapp.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  FLASHCARD MODEL — Persisted card with SM-2 spaced-repetition fields.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Each flashcard has a front (question) and back (answer), along with
 *  SM-2 algorithm parameters (easeFactor, interval, repetitions) that
 *  control when the card is next due for review.
 *
 *  Cards can be auto-generated from note content or manually created.
 */
public class Flashcard {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SM-2 QUALITY RATINGS
    // ═══════════════════════════════════════════════════════════════════════════════
    public static final int QUALITY_BLACKOUT   = 0; // Complete blackout
    public static final int QUALITY_WRONG      = 1; // Wrong, but recognized answer
    public static final int QUALITY_HARD_WRONG  = 2; // Wrong, but answer was easy to recall
    public static final int QUALITY_HARD        = 3; // Correct but with serious difficulty
    public static final int QUALITY_GOOD        = 4; // Correct with minor hesitation
    public static final int QUALITY_EASY        = 5; // Perfect recall

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    private String id;
    private String noteId;         // Source note
    private String folderId;       // For organizing
    private String deckName;       // Logical grouping (e.g., "Biology", "History")
    private String front;          // Question / prompt
    private String back;           // Answer / explanation
    private String tags;           // Comma-separated tags

    // SM-2 spaced-repetition fields
    private double easeFactor;     // >= 1.3, default 2.5
    private int interval;          // Days until next review
    private int repetitions;       // Consecutive correct answers
    private long nextReviewDate;   // Epoch ms
    private long lastReviewedDate; // Epoch ms, 0 = never

    // Stats
    private int totalReviews;
    private int correctCount;
    private int wrongCount;

    // Timestamps
    private long createdAt;
    private long updatedAt;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════════

    public Flashcard() {
        this.id = java.util.UUID.randomUUID().toString();
        this.easeFactor = 2.5;
        this.interval = 0;
        this.repetitions = 0;
        this.nextReviewDate = System.currentTimeMillis(); // Due immediately
        this.lastReviewedDate = 0;
        this.totalReviews = 0;
        this.correctCount = 0;
        this.wrongCount = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.deckName = "General";
        this.tags = "";
    }

    public Flashcard(String noteId, String front, String back) {
        this();
        this.noteId = noteId;
        this.front = front;
        this.back = back;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SM-2 ALGORITHM
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Apply SM-2 algorithm after a review.
     * @param quality Rating 0-5 (see QUALITY_* constants)
     */
    public void review(int quality) {
        quality = Math.max(0, Math.min(5, quality));
        totalReviews++;
        lastReviewedDate = System.currentTimeMillis();
        updatedAt = System.currentTimeMillis();

        if (quality >= 3) {
            // Correct response
            correctCount++;
            if (repetitions == 0) {
                interval = 1;
            } else if (repetitions == 1) {
                interval = 6;
            } else {
                interval = (int) Math.round(interval * easeFactor);
            }
            repetitions++;
        } else {
            // Incorrect response — reset
            wrongCount++;
            repetitions = 0;
            interval = 1;
        }

        // Update ease factor
        easeFactor = easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        if (easeFactor < 1.3) easeFactor = 1.3;

        // Schedule next review
        nextReviewDate = System.currentTimeMillis() + ((long) interval * 24 * 60 * 60 * 1000);
    }

    /**
     * Check if this card is due for review now.
     */
    public boolean isDue() {
        return System.currentTimeMillis() >= nextReviewDate;
    }

    /**
     * Get the retention rate as a percentage (0-100).
     */
    public int getRetentionRate() {
        if (totalReviews == 0) return 0;
        return (int) ((correctCount / (float) totalReviews) * 100);
    }

    /**
     * Get difficulty label based on ease factor.
     */
    public String getDifficultyLabel() {
        if (easeFactor >= 2.5) return "Easy";
        if (easeFactor >= 2.0) return "Medium";
        if (easeFactor >= 1.6) return "Hard";
        return "Very Hard";
    }

    /**
     * Get the number of days until next review.
     */
    public int getDaysUntilReview() {
        long diff = nextReviewDate - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) (diff / (24 * 60 * 60 * 1000));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  JSON SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("noteId", noteId != null ? noteId : "");
            json.put("folderId", folderId != null ? folderId : "");
            json.put("deckName", deckName != null ? deckName : "General");
            json.put("front", front != null ? front : "");
            json.put("back", back != null ? back : "");
            json.put("tags", tags != null ? tags : "");
            json.put("easeFactor", easeFactor);
            json.put("interval", interval);
            json.put("repetitions", repetitions);
            json.put("nextReviewDate", nextReviewDate);
            json.put("lastReviewedDate", lastReviewedDate);
            json.put("totalReviews", totalReviews);
            json.put("correctCount", correctCount);
            json.put("wrongCount", wrongCount);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Flashcard fromJson(JSONObject json) {
        Flashcard card = new Flashcard();
        card.id = json.optString("id", card.id);
        card.noteId = json.optString("noteId", "");
        card.folderId = json.optString("folderId", "");
        card.deckName = json.optString("deckName", "General");
        card.front = json.optString("front", "");
        card.back = json.optString("back", "");
        card.tags = json.optString("tags", "");
        card.easeFactor = json.optDouble("easeFactor", 2.5);
        card.interval = json.optInt("interval", 0);
        card.repetitions = json.optInt("repetitions", 0);
        card.nextReviewDate = json.optLong("nextReviewDate", System.currentTimeMillis());
        card.lastReviewedDate = json.optLong("lastReviewedDate", 0);
        card.totalReviews = json.optInt("totalReviews", 0);
        card.correctCount = json.optInt("correctCount", 0);
        card.wrongCount = json.optInt("wrongCount", 0);
        card.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        card.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        return card;
    }

    public static String toJsonArray(List<Flashcard> cards) {
        JSONArray arr = new JSONArray();
        for (Flashcard c : cards) arr.put(c.toJson());
        return arr.toString();
    }

    public static List<Flashcard> fromJsonArray(String jsonStr) {
        List<Flashcard> cards = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return cards;
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                cards.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return cards;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════════════════════

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public String getDeckName() { return deckName; }
    public void setDeckName(String deckName) { this.deckName = deckName; }

    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; this.updatedAt = System.currentTimeMillis(); }

    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; this.updatedAt = System.currentTimeMillis(); }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public double getEaseFactor() { return easeFactor; }
    public int getInterval() { return interval; }
    public int getRepetitions() { return repetitions; }
    public long getNextReviewDate() { return nextReviewDate; }
    public long getLastReviewedDate() { return lastReviewedDate; }
    public int getTotalReviews() { return totalReviews; }
    public int getCorrectCount() { return correctCount; }
    public int getWrongCount() { return wrongCount; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}
