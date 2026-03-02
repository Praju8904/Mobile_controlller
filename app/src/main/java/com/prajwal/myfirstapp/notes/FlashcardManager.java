package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  FLASHCARD MANAGER — CRUD operations for flashcards with SM-2 spaced repetition.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Persistence: SharedPreferences (JSON array of Flashcard objects)
 *
 *  Operations:
 *  • Save / Delete / Update cards
 *  • Get cards due for review (sorted by due date)
 *  • Get cards by note, deck, or folder
 *  • Auto-generate flashcards from note content (heading→body, list items, key terms)
 *  • Deck management
 *  • Statistics (total, due, mastered, retention rate)
 */
public class FlashcardManager {

    private static final String PREFS_NAME = "flashcard_data";
    private static final String KEY_CARDS = "cards";

    private final Context context;
    private List<Flashcard> cards;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════════════

    public FlashcardManager(Context context) {
        this.context = context.getApplicationContext();
        loadCards();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadCards() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_CARDS, "[]");
        cards = Flashcard.fromJsonArray(json);
    }

    private void saveCards() {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_CARDS, Flashcard.toJsonArray(cards));
        editor.apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CRUD
    // ═══════════════════════════════════════════════════════════════════════════════

    public void addCard(Flashcard card) {
        cards.add(card);
        saveCards();
    }

    public void addCards(List<Flashcard> newCards) {
        cards.addAll(newCards);
        saveCards();
    }

    public void updateCard(Flashcard card) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getId().equals(card.getId())) {
                cards.set(i, card);
                saveCards();
                return;
            }
        }
    }

    public void deleteCard(String cardId) {
        cards.removeIf(c -> c.getId().equals(cardId));
        saveCards();
    }

    public void deleteCardsForNote(String noteId) {
        cards.removeIf(c -> noteId.equals(c.getNoteId()));
        saveCards();
    }

    public Flashcard getCard(String cardId) {
        for (Flashcard c : cards) {
            if (c.getId().equals(cardId)) return c;
        }
        return null;
    }

    public List<Flashcard> getAllCards() {
        return new ArrayList<>(cards);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  QUERIES
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Get all cards due for review now, sorted so most overdue first. */
    public List<Flashcard> getDueCards() {
        List<Flashcard> due = new ArrayList<>();
        for (Flashcard c : cards) {
            if (c.isDue()) due.add(c);
        }
        due.sort((a, b) -> Long.compare(a.getNextReviewDate(), b.getNextReviewDate()));
        return due;
    }

    /** Get cards for a specific note. */
    public List<Flashcard> getCardsForNote(String noteId) {
        List<Flashcard> result = new ArrayList<>();
        for (Flashcard c : cards) {
            if (noteId.equals(c.getNoteId())) result.add(c);
        }
        return result;
    }

    /** Get cards in a specific deck. */
    public List<Flashcard> getCardsForDeck(String deckName) {
        List<Flashcard> result = new ArrayList<>();
        for (Flashcard c : cards) {
            if (deckName.equals(c.getDeckName())) result.add(c);
        }
        return result;
    }

    /** Get distinct deck names. */
    public List<String> getDeckNames() {
        List<String> decks = new ArrayList<>();
        for (Flashcard c : cards) {
            if (c.getDeckName() != null && !decks.contains(c.getDeckName())) {
                decks.add(c.getDeckName());
            }
        }
        Collections.sort(decks);
        return decks;
    }

    /** Get the number of cards due for review. */
    public int getDueCount() {
        int count = 0;
        for (Flashcard c : cards) if (c.isDue()) count++;
        return count;
    }

    /** Get number of mastered cards (interval >= 21 days). */
    public int getMasteredCount() {
        int count = 0;
        for (Flashcard c : cards) if (c.getInterval() >= 21) count++;
        return count;
    }

    /** Overall retention rate. */
    public int getOverallRetention() {
        int totalCorrect = 0, totalReviews = 0;
        for (Flashcard c : cards) {
            totalCorrect += c.getCorrectCount();
            totalReviews += c.getTotalReviews();
        }
        if (totalReviews == 0) return 0;
        return (int) ((totalCorrect / (float) totalReviews) * 100);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  AUTO-GENERATION FROM NOTE CONTENT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Auto-generate flashcards from a note's content blocks.
     * Strategies:
     *   1. Heading → next paragraph = Q&A pair
     *   2. Bullet list items → individual cards (item ↔ list title)
     *   3. Toggle blocks → front/back pairs
     *   4. Bold key terms → definition cards
     */
    public List<Flashcard> generateFromNote(Note note, List<ContentBlock> blocks) {
        List<Flashcard> generated = new ArrayList<>();
        if (note == null || blocks == null || blocks.isEmpty()) return generated;

        String noteId = note.id;

        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock block = blocks.get(i);

            // Strategy 1: Heading + following text = Q&A
            if (isHeadingType(block.blockType)) {
                String heading = block.getText();
                if (heading == null || heading.trim().isEmpty()) continue;

                // Find next text block
                StringBuilder answer = new StringBuilder();
                for (int j = i + 1; j < blocks.size() && j <= i + 3; j++) {
                    ContentBlock next = blocks.get(j);
                    if (isHeadingType(next.blockType)) break;
                    String text = next.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        if (answer.length() > 0) answer.append("\n");
                        answer.append(text.trim());
                    }
                }
                if (answer.length() > 10) {
                    Flashcard card = new Flashcard(noteId, "What is " + heading.trim() + "?", answer.toString());
                    card.setDeckName(note.category != null ? note.category : "General");
                    generated.add(card);
                }
            }

            // Strategy 2: Bullet list items
            if (block.blockType.equals(ContentBlock.TYPE_BULLET)) {
                String text = block.getText();
                if (text != null && !text.trim().isEmpty() && text.trim().length() > 5) {
                    // Look for a preceding heading as context
                    String context = "This note";
                    for (int j = i - 1; j >= 0; j--) {
                        if (isHeadingType(blocks.get(j).blockType)) {
                            String hText = blocks.get(j).getText();
                            if (hText != null && !hText.isEmpty()) context = hText.trim();
                            break;
                        }
                    }
                    Flashcard card = new Flashcard(noteId,
                            "Regarding \"" + context + "\", explain: " + text.trim(),
                            text.trim());
                    card.setDeckName(note.category != null ? note.category : "General");
                    generated.add(card);
                }
            }

            // Strategy 3: Toggle = front/back
            if (block.blockType.equals(ContentBlock.TYPE_TOGGLE)) {
                String front = block.getText();
                // Toggle content could be stored in extra data or next block
                if (front != null && !front.trim().isEmpty() && i + 1 < blocks.size()) {
                    String back = blocks.get(i + 1).getText();
                    if (back != null && !back.trim().isEmpty()) {
                        Flashcard card = new Flashcard(noteId, front.trim(), back.trim());
                        card.setDeckName(note.category != null ? note.category : "General");
                        generated.add(card);
                    }
                }
            }
        }

        // Strategy 4: Key terms from bold markers in text blocks
        for (ContentBlock block : blocks) {
            if (block.blockType.equals(ContentBlock.TYPE_TEXT)) {
                String text = block.getText();
                if (text == null) continue;
                // Find **bold** or <b>bold</b> terms
                java.util.regex.Matcher boldMatcher = java.util.regex.Pattern.compile(
                        "\\*\\*(.+?)\\*\\*|<b>(.+?)</b>", java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(text);
                while (boldMatcher.find()) {
                    String term = boldMatcher.group(1) != null ? boldMatcher.group(1) : boldMatcher.group(2);
                    if (term.trim().length() > 2) {
                        Flashcard card = new Flashcard(noteId,
                                "Define: " + term.trim(),
                                "Found in: " + text.trim());
                        card.setDeckName(note.category != null ? note.category : "General");
                        generated.add(card);
                    }
                }
            }
        }

        return generated;
    }

    private boolean isHeadingType(String type) {
        return ContentBlock.TYPE_HEADING1.equals(type) ||
                ContentBlock.TYPE_HEADING2.equals(type) ||
                ContentBlock.TYPE_HEADING3.equals(type);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  REVIEW SESSION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Review a card with quality rating and save.
     */
    public void reviewCard(String cardId, int quality) {
        Flashcard card = getCard(cardId);
        if (card != null) {
            card.review(quality);
            updateCard(card);
        }
    }

    /**
     * Get the total number of cards.
     */
    public int getTotalCount() {
        return cards.size();
    }
}
