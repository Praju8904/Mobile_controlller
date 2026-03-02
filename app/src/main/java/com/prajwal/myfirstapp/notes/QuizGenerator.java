package com.prajwal.myfirstapp.notes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  QUIZ GENERATOR — Auto-generates quiz questions from note content.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Generates three types of questions:
 *  • Multiple Choice — from headings, definitions, and key terms
 *  • True/False — from factual statements in the note
 *  • Fill-in-the-Blank — removes key words from sentences
 *
 *  All generation is local/offline — no API calls.
 */
public class QuizGenerator {

    private static final Random RANDOM = new Random();

    // Common distractor words grouped by category for MCQ wrong answers
    private static final String[][] DISTRACTOR_GROUPS = {
            {"increase", "decrease", "maintain", "eliminate"},
            {"always", "never", "sometimes", "rarely"},
            {"primary", "secondary", "tertiary", "quaternary"},
            {"analyze", "synthesize", "evaluate", "describe"},
            {"internal", "external", "systematic", "organic"},
            {"simple", "complex", "moderate", "extreme"},
    };

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Generate a quiz from note content blocks.
     * @param note The source note.
     * @param blocks Content blocks of the note.
     * @param maxQuestions Maximum number of questions to generate.
     * @return List of quiz questions.
     */
    public static List<QuizQuestion> generateQuiz(Note note, List<ContentBlock> blocks, int maxQuestions) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (note == null || blocks == null || blocks.isEmpty()) return questions;

        String noteId = note.id;

        // Collect text content
        List<String> headings = new ArrayList<>();
        List<String> paragraphs = new ArrayList<>();
        List<String> listItems = new ArrayList<>();

        for (ContentBlock block : blocks) {
            String text = block.getText();
            if (text == null || text.trim().isEmpty()) continue;
            text = text.trim();

            if (block.blockType.equals(ContentBlock.TYPE_HEADING1) ||
                    block.blockType.equals(ContentBlock.TYPE_HEADING2) ||
                    block.blockType.equals(ContentBlock.TYPE_HEADING3)) {
                headings.add(text);
            } else if (block.blockType.equals(ContentBlock.TYPE_BULLET) ||
                    block.blockType.equals(ContentBlock.TYPE_NUMBERED) ||
                    block.blockType.equals(ContentBlock.TYPE_CHECKLIST)) {
                listItems.add(text);
            } else if (block.blockType.equals(ContentBlock.TYPE_TEXT)) {
                // Split into sentences
                String[] sentences = text.split("(?<=[.!?])\\s+");
                for (String s : sentences) {
                    if (s.trim().length() > 20) paragraphs.add(s.trim());
                }
            }
        }

        // Generate different question types
        generateHeadingQuestions(questions, headings, paragraphs, blocks, noteId);
        generateFillBlankQuestions(questions, paragraphs, noteId);
        generateTrueFalseQuestions(questions, paragraphs, noteId);
        generateListQuestions(questions, listItems, headings, noteId);

        // Shuffle and limit
        Collections.shuffle(questions);
        if (questions.size() > maxQuestions) {
            questions = new ArrayList<>(questions.subList(0, maxQuestions));
        }

        // Assign difficulty based on position
        for (int i = 0; i < questions.size(); i++) {
            if (i < questions.size() / 3) questions.get(i).setDifficulty(1);
            else if (i < 2 * questions.size() / 3) questions.get(i).setDifficulty(2);
            else questions.get(i).setDifficulty(3);
        }

        return questions;
    }

    /**
     * Generate quiz from flashcards instead of raw content.
     */
    public static List<QuizQuestion> generateFromFlashcards(List<Flashcard> flashcards, int maxQuestions) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (flashcards == null || flashcards.isEmpty()) return questions;

        List<Flashcard> shuffled = new ArrayList<>(flashcards);
        Collections.shuffle(shuffled);

        for (Flashcard card : shuffled) {
            if (questions.size() >= maxQuestions) break;

            if (RANDOM.nextBoolean()) {
                // MCQ: front as question, back as correct answer + distractors
                List<String> options = new ArrayList<>();
                options.add(card.getBack());

                // Generate distractors from other flashcard backs
                List<String> otherBacks = new ArrayList<>();
                for (Flashcard other : flashcards) {
                    if (!other.getId().equals(card.getId()) && other.getBack() != null) {
                        otherBacks.add(other.getBack());
                    }
                }
                Collections.shuffle(otherBacks);
                for (int i = 0; i < Math.min(3, otherBacks.size()); i++) {
                    options.add(otherBacks.get(i));
                }

                // Pad with generic distractors if needed
                while (options.size() < 4) {
                    options.add(getGenericDistractor());
                }

                // Shuffle options
                int correctIdx = 0;
                String correctAnswer = options.get(0);
                Collections.shuffle(options);
                correctIdx = options.indexOf(correctAnswer);

                QuizQuestion q = QuizQuestion.multipleChoice(card.getFront(), options, correctIdx);
                q.setSourceNoteId(card.getNoteId());
                questions.add(q);
            } else {
                // Fill-in-the-blank from the back
                QuizQuestion q = QuizQuestion.fillBlank(card.getFront(), card.getBack());
                q.setSourceNoteId(card.getNoteId());
                questions.add(q);
            }
        }

        return questions;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GENERATION STRATEGIES
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Generate MCQs from headings (heading = answer, question = "What topic covers..."). */
    private static void generateHeadingQuestions(List<QuizQuestion> questions,
                                                  List<String> headings,
                                                  List<String> paragraphs,
                                                  List<ContentBlock> blocks,
                                                  String noteId) {
        for (String heading : headings) {
            if (questions.size() >= 20) break;

            // Find the paragraph that follows this heading
            String context = findFollowingText(heading, blocks);
            if (context == null || context.length() < 20) continue;

            // Create MCQ: "Which topic discusses [context preview]?"
            String preview = context.length() > 80 ? context.substring(0, 80) + "..." : context;

            List<String> options = new ArrayList<>();
            options.add(heading);

            // Other headings as distractors
            for (String other : headings) {
                if (!other.equals(heading) && options.size() < 4) options.add(other);
            }
            while (options.size() < 4) options.add(getGenericDistractor());

            String correctAnswer = options.get(0);
            Collections.shuffle(options);
            int correctIdx = options.indexOf(correctAnswer);

            QuizQuestion q = QuizQuestion.multipleChoice(
                    "Which section discusses: \"" + preview + "\"?",
                    options, correctIdx
            );
            q.setSourceNoteId(noteId);
            q.setExplanation("This content appears under the heading: " + heading);
            questions.add(q);
        }
    }

    /** Generate fill-in-the-blank by removing key words from sentences. */
    private static void generateFillBlankQuestions(List<QuizQuestion> questions,
                                                    List<String> paragraphs,
                                                    String noteId) {
        for (String para : paragraphs) {
            if (questions.size() >= 20) break;

            String[] words = para.split("\\s+");
            if (words.length < 6) continue;

            // Find a significant word to blank out (>5 chars, not too common)
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < words.length; i++) {
                String clean = words[i].replaceAll("[^a-zA-Z]", "");
                if (clean.length() > 5 && !isCommonWord(clean)) {
                    candidates.add(i);
                }
            }

            if (candidates.isEmpty()) continue;
            int blankIdx = candidates.get(RANDOM.nextInt(candidates.size()));
            String answer = words[blankIdx].replaceAll("[^a-zA-Z]", "");

            // Build question with blank
            StringBuilder qText = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i > 0) qText.append(" ");
                if (i == blankIdx) qText.append("_____");
                else qText.append(words[i]);
            }

            QuizQuestion q = QuizQuestion.fillBlank(qText.toString(), answer);
            q.setSourceNoteId(noteId);
            q.setExplanation("The missing word is: " + answer);
            questions.add(q);
        }
    }

    /** Generate True/False from factual statements. */
    private static void generateTrueFalseQuestions(List<QuizQuestion> questions,
                                                    List<String> paragraphs,
                                                    String noteId) {
        for (String para : paragraphs) {
            if (questions.size() >= 20) break;

            String[] words = para.split("\\s+");
            if (words.length < 5 || words.length > 25) continue;

            if (RANDOM.nextBoolean()) {
                // True statement (original sentence)
                QuizQuestion q = QuizQuestion.trueFalse(para, true);
                q.setSourceNoteId(noteId);
                q.setExplanation("This statement is true as stated in your notes.");
                questions.add(q);
            } else {
                // Create a false statement by negating or modifying
                String falsified = falsifySentence(para);
                if (falsified != null) {
                    QuizQuestion q = QuizQuestion.trueFalse(falsified, false);
                    q.setSourceNoteId(noteId);
                    q.setExplanation("Original: " + para);
                    questions.add(q);
                }
            }
        }
    }

    /** Generate questions from list items. */
    private static void generateListQuestions(List<QuizQuestion> questions,
                                               List<String> listItems,
                                               List<String> headings,
                                               String noteId) {
        if (listItems.size() < 2) return;

        for (int i = 0; i < listItems.size() && questions.size() < 20; i++) {
            String item = listItems.get(i);
            if (item.length() < 5) continue;

            List<String> options = new ArrayList<>();
            options.add(item);

            // Other list items as distractors
            for (int j = 0; j < listItems.size() && options.size() < 4; j++) {
                if (j != i) options.add(listItems.get(j));
            }
            while (options.size() < 4) options.add(getGenericDistractor());

            String correctAnswer = options.get(0);
            Collections.shuffle(options);
            int correctIdx = options.indexOf(correctAnswer);

            String questionText = "Which of the following was mentioned in your notes?";
            if (!headings.isEmpty()) {
                questionText = "Which item belongs to \"" + headings.get(RANDOM.nextInt(headings.size())) + "\"?";
            }

            QuizQuestion q = QuizQuestion.multipleChoice(questionText, options, correctIdx);
            q.setSourceNoteId(noteId);
            questions.add(q);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Find the text content following a heading in blocks. */
    private static String findFollowingText(String heading, List<ContentBlock> blocks) {
        boolean found = false;
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (found) {
                if (block.blockType.equals(ContentBlock.TYPE_HEADING1) ||
                        block.blockType.equals(ContentBlock.TYPE_HEADING2) ||
                        block.blockType.equals(ContentBlock.TYPE_HEADING3)) break;
                String t = block.getText();
                if (t != null && !t.trim().isEmpty()) {
                    if (text.length() > 0) text.append(" ");
                    text.append(t.trim());
                }
                if (text.length() > 200) break;
            }
            if (!found && heading.equals(block.getText())) {
                found = true;
            }
        }
        return text.length() > 0 ? text.toString() : null;
    }

    /** Falsify a sentence by negating it or swapping key words. */
    private static String falsifySentence(String sentence) {
        // Strategy 1: add/remove negation
        if (sentence.contains(" is ")) {
            return sentence.replaceFirst(" is ", " is not ");
        }
        if (sentence.contains(" are ")) {
            return sentence.replaceFirst(" are ", " are not ");
        }
        if (sentence.contains(" can ")) {
            return sentence.replaceFirst(" can ", " cannot ");
        }
        if (sentence.contains(" will ")) {
            return sentence.replaceFirst(" will ", " will not ");
        }

        // Strategy 2: swap "always" ↔ "never"
        if (sentence.toLowerCase().contains("always")) {
            return sentence.replaceAll("(?i)always", "never");
        }
        if (sentence.toLowerCase().contains("never")) {
            return sentence.replaceAll("(?i)never", "always");
        }

        return null; // Can't falsify
    }

    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "that", "this", "with", "have", "from",
            "they", "will", "been", "were", "said", "which", "their",
            "about", "would", "there", "these", "other", "could", "after",
            "should", "where", "those", "being", "because", "between",
            "before", "through", "during", "without", "however", "another"
    ));

    private static boolean isCommonWord(String word) {
        return COMMON_WORDS.contains(word.toLowerCase());
    }

    private static String getGenericDistractor() {
        String[] group = DISTRACTOR_GROUPS[RANDOM.nextInt(DISTRACTOR_GROUPS.length)];
        return group[RANDOM.nextInt(group.length)];
    }
}
