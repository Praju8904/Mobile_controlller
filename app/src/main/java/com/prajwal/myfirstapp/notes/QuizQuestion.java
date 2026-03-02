package com.prajwal.myfirstapp.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  QUIZ QUESTION MODEL — Multiple-choice, true/false, and fill-in-the-blank questions
 *  auto-generated from note content for quiz mode.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class QuizQuestion {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  QUESTION TYPES
    // ═══════════════════════════════════════════════════════════════════════════════
    public static final int TYPE_MULTIPLE_CHOICE = 0;
    public static final int TYPE_TRUE_FALSE      = 1;
    public static final int TYPE_FILL_BLANK      = 2;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    private String id;
    private String question;               // The question text
    private List<String> options;           // MCQ options (2-4)
    private int correctIndex;              // Index into options
    private String correctAnswer;          // Text answer (for fill-blank)
    private int type;                      // TYPE_* constant
    private String sourceNoteId;           // Which note this was generated from
    private String sourceBlockId;          // Specific block (optional)
    private String explanation;            // Why this answer is correct
    private int difficulty;                // 1 = easy, 2 = medium, 3 = hard
    private boolean answered;              // Has user answered this?
    private boolean answeredCorrectly;     // Was the answer correct?
    private int userSelectedIndex;         // What did user pick? (-1 = none)

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════════

    public QuizQuestion() {
        this.id = java.util.UUID.randomUUID().toString();
        this.options = new ArrayList<>();
        this.type = TYPE_MULTIPLE_CHOICE;
        this.difficulty = 1;
        this.answered = false;
        this.answeredCorrectly = false;
        this.userSelectedIndex = -1;
    }

    /** Quick constructor for MCQ */
    public static QuizQuestion multipleChoice(String question, List<String> options, int correctIndex) {
        QuizQuestion q = new QuizQuestion();
        q.question = question;
        q.options = new ArrayList<>(options);
        q.correctIndex = correctIndex;
        q.correctAnswer = options.get(correctIndex);
        q.type = TYPE_MULTIPLE_CHOICE;
        return q;
    }

    /** Quick constructor for True/False */
    public static QuizQuestion trueFalse(String statement, boolean isTrue) {
        QuizQuestion q = new QuizQuestion();
        q.question = statement;
        q.options = new ArrayList<>();
        q.options.add("True");
        q.options.add("False");
        q.correctIndex = isTrue ? 0 : 1;
        q.correctAnswer = isTrue ? "True" : "False";
        q.type = TYPE_TRUE_FALSE;
        return q;
    }

    /** Quick constructor for Fill-in-the-blank */
    public static QuizQuestion fillBlank(String questionWithBlank, String answer) {
        QuizQuestion q = new QuizQuestion();
        q.question = questionWithBlank;
        q.correctAnswer = answer;
        q.type = TYPE_FILL_BLANK;
        return q;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LOGIC
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Check if the selected option index is correct (for MCQ / True-False).
     */
    public boolean checkAnswer(int selectedIndex) {
        answered = true;
        userSelectedIndex = selectedIndex;
        answeredCorrectly = (selectedIndex == correctIndex);
        return answeredCorrectly;
    }

    /**
     * Check if a text answer is correct (for fill-in-the-blank).
     * Uses case-insensitive, trimmed comparison.
     */
    public boolean checkTextAnswer(String userAnswer) {
        answered = true;
        if (userAnswer == null || correctAnswer == null) {
            answeredCorrectly = false;
        } else {
            answeredCorrectly = userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
        }
        return answeredCorrectly;
    }

    /**
     * Reset answer state (for re-quiz).
     */
    public void reset() {
        answered = false;
        answeredCorrectly = false;
        userSelectedIndex = -1;
    }

    /**
     * Shuffle MCQ options (preserving correctIndex mapping).
     */
    public void shuffleOptions() {
        if (type == TYPE_FILL_BLANK || options == null || options.size() < 2) return;

        String correctOption = options.get(correctIndex);
        Collections.shuffle(options);
        correctIndex = options.indexOf(correctOption);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  JSON SERIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("question", question != null ? question : "");
            JSONArray opts = new JSONArray();
            if (options != null) for (String o : options) opts.put(o);
            json.put("options", opts);
            json.put("correctIndex", correctIndex);
            json.put("correctAnswer", correctAnswer != null ? correctAnswer : "");
            json.put("type", type);
            json.put("sourceNoteId", sourceNoteId != null ? sourceNoteId : "");
            json.put("sourceBlockId", sourceBlockId != null ? sourceBlockId : "");
            json.put("explanation", explanation != null ? explanation : "");
            json.put("difficulty", difficulty);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static QuizQuestion fromJson(JSONObject json) {
        QuizQuestion q = new QuizQuestion();
        q.id = json.optString("id", q.id);
        q.question = json.optString("question", "");
        q.correctIndex = json.optInt("correctIndex", 0);
        q.correctAnswer = json.optString("correctAnswer", "");
        q.type = json.optInt("type", TYPE_MULTIPLE_CHOICE);
        q.sourceNoteId = json.optString("sourceNoteId", "");
        q.sourceBlockId = json.optString("sourceBlockId", "");
        q.explanation = json.optString("explanation", "");
        q.difficulty = json.optInt("difficulty", 1);

        q.options = new ArrayList<>();
        JSONArray opts = json.optJSONArray("options");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                q.options.add(opts.optString(i, ""));
            }
        }
        return q;
    }

    public static String toJsonArray(List<QuizQuestion> questions) {
        JSONArray arr = new JSONArray();
        for (QuizQuestion q : questions) arr.put(q.toJson());
        return arr.toString();
    }

    public static List<QuizQuestion> fromJsonArray(String jsonStr) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return questions;
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                questions.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return questions;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════════════════════

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getSourceNoteId() { return sourceNoteId; }
    public void setSourceNoteId(String sourceNoteId) { this.sourceNoteId = sourceNoteId; }

    public String getSourceBlockId() { return sourceBlockId; }
    public void setSourceBlockId(String sourceBlockId) { this.sourceBlockId = sourceBlockId; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public boolean isAnswered() { return answered; }
    public boolean isAnsweredCorrectly() { return answeredCorrectly; }
    public int getUserSelectedIndex() { return userSelectedIndex; }
}
