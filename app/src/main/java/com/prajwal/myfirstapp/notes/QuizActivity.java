package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  QUIZ ACTIVITY — Take quizzes generated from notes/flashcards.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class QuizActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private List<QuizQuestion> questions = new ArrayList<>();
    private int currentIndex = 0;
    private int correctCount = 0;
    private long startTime;
    private boolean answered = false;

    private String noteId, noteTitle;

    // Views
    private TextView tvQuizTitle, tvQuizProgress, tvTimer, tvQuestionType, tvQuestion;
    private ProgressBar progressBar;
    private LinearLayout layoutOptions, layoutTrueFalse;
    private TextInputLayout layoutFillBlank;
    private TextInputEditText etFillBlank;
    private MaterialButton[] optionButtons = new MaterialButton[4];
    private MaterialButton btnTrue, btnFalse, btnSubmitFillBlank, btnNext;
    private View cardFeedback;
    private TextView tvFeedbackResult, tvExplanation;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        noteId = getIntent().getStringExtra("noteId");
        noteTitle = getIntent().getStringExtra("noteTitle");

        bindViews();
        setupListeners();
        generateQuiz();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SETUP
    // ═══════════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvQuizTitle = findViewById(R.id.tvQuizTitle);
        tvQuizProgress = findViewById(R.id.tvQuizProgress);
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestionType = findViewById(R.id.tvQuestionType);
        tvQuestion = findViewById(R.id.tvQuestion);
        progressBar = findViewById(R.id.progressBar);
        layoutOptions = findViewById(R.id.layoutOptions);
        layoutTrueFalse = findViewById(R.id.layoutTrueFalse);
        layoutFillBlank = findViewById(R.id.layoutFillBlank);
        etFillBlank = findViewById(R.id.etFillBlank);
        cardFeedback = findViewById(R.id.cardFeedback);
        tvFeedbackResult = findViewById(R.id.tvFeedbackResult);
        tvExplanation = findViewById(R.id.tvExplanation);

        optionButtons[0] = findViewById(R.id.btnOption0);
        optionButtons[1] = findViewById(R.id.btnOption1);
        optionButtons[2] = findViewById(R.id.btnOption2);
        optionButtons[3] = findViewById(R.id.btnOption3);

        btnTrue = findViewById(R.id.btnTrue);
        btnFalse = findViewById(R.id.btnFalse);
        btnSubmitFillBlank = findViewById(R.id.btnSubmitFillBlank);
        btnNext = findViewById(R.id.btnNext);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            optionButtons[i].setOnClickListener(v -> answerMCQ(idx));
        }

        btnTrue.setOnClickListener(v -> answerTrueFalse(0));
        btnFalse.setOnClickListener(v -> answerTrueFalse(1));

        btnSubmitFillBlank.setOnClickListener(v -> {
            String answer = etFillBlank.getText() != null ? etFillBlank.getText().toString() : "";
            answerFillBlank(answer);
        });

        btnNext.setOnClickListener(v -> {
            currentIndex++;
            showQuestion();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  QUIZ GENERATION
    // ═══════════════════════════════════════════════════════════════════════════════

    private void generateQuiz() {
        // Try from flashcards first
        FlashcardManager fm = new FlashcardManager(this);
        QuizGenerator gen = new QuizGenerator();

        if (noteId != null) {
            List<Flashcard> cards = fm.getCardsForNote(noteId);
            if (!cards.isEmpty()) {
                questions = gen.generateFromFlashcards(cards, 10);
            }
        }

        // If no flashcard-based questions, try from note content blocks
        if (questions.isEmpty() && noteId != null) {
            // Load note and try content-based generation
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("smart_notes_data", MODE_PRIVATE);
                String json = prefs.getString("notes_list", "[]");
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    Note note = Note.fromJson(arr.getJSONObject(i));
                    if (note.id.equals(noteId)) {
                        if (note.blocksJson != null) {
                            List<ContentBlock> blocks = ContentBlock.fromJsonArray(note.blocksJson);
                            questions = QuizGenerator.generateQuiz(note, blocks, 10);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (questions.isEmpty()) {
            Toast.makeText(this, "Not enough content to generate a quiz", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvQuizTitle.setText(noteTitle != null ? noteTitle : "Quiz");
        startTime = SystemClock.elapsedRealtime();
        currentIndex = 0;
        correctCount = 0;
        showQuestion();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DISPLAY
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showQuestion() {
        if (currentIndex >= questions.size()) {
            showResults();
            return;
        }

        answered = false;
        QuizQuestion q = questions.get(currentIndex);
        q.reset();

        tvQuizProgress.setText("Question " + (currentIndex + 1) + " of " + questions.size());
        progressBar.setMax(questions.size());
        progressBar.setProgress(currentIndex + 1);

        tvQuestion.setText(q.getQuestion());
        cardFeedback.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);

        // Hide all input types
        layoutOptions.setVisibility(View.GONE);
        layoutTrueFalse.setVisibility(View.GONE);
        layoutFillBlank.setVisibility(View.GONE);
        btnSubmitFillBlank.setVisibility(View.GONE);

        switch (q.getType()) {
            case QuizQuestion.TYPE_MULTIPLE_CHOICE:
                tvQuestionType.setText("Multiple Choice");
                layoutOptions.setVisibility(View.VISIBLE);
                for (int i = 0; i < 4; i++) {
                    if (i < q.getOptions().size()) {
                        optionButtons[i].setVisibility(View.VISIBLE);
                        optionButtons[i].setText(q.getOptions().get(i));
                        optionButtons[i].setEnabled(true);
                        optionButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E293B));
                    } else {
                        optionButtons[i].setVisibility(View.GONE);
                    }
                }
                break;

            case QuizQuestion.TYPE_TRUE_FALSE:
                tvQuestionType.setText("True or False");
                layoutTrueFalse.setVisibility(View.VISIBLE);
                btnTrue.setEnabled(true);
                btnFalse.setEnabled(true);
                btnTrue.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E293B));
                btnFalse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E293B));
                break;

            case QuizQuestion.TYPE_FILL_BLANK:
                tvQuestionType.setText("Fill in the Blank");
                layoutFillBlank.setVisibility(View.VISIBLE);
                btnSubmitFillBlank.setVisibility(View.VISIBLE);
                etFillBlank.setText("");
                break;
        }

        // Update timer
        long elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000;
        tvTimer.setText(String.format("%d:%02d", elapsed / 60, elapsed % 60));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  ANSWERING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void answerMCQ(int selectedIndex) {
        if (answered) return;
        answered = true;

        QuizQuestion q = questions.get(currentIndex);
        boolean correct = q.checkAnswer(selectedIndex);
        if (correct) correctCount++;

        // Color buttons
        for (int i = 0; i < optionButtons.length; i++) {
            optionButtons[i].setEnabled(false);
            if (i == q.getCorrectIndex()) {
                optionButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981));
            } else if (i == selectedIndex && !correct) {
                optionButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
            }
        }

        showFeedback(correct, q);
    }

    private void answerTrueFalse(int selectedIndex) {
        if (answered) return;
        answered = true;

        QuizQuestion q = questions.get(currentIndex);
        boolean correct = q.checkAnswer(selectedIndex);
        if (correct) correctCount++;

        btnTrue.setEnabled(false);
        btnFalse.setEnabled(false);

        if (q.getCorrectIndex() == 0) {
            btnTrue.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981));
            if (!correct) btnFalse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
        } else {
            btnFalse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981));
            if (!correct) btnTrue.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
        }

        showFeedback(correct, q);
    }

    private void answerFillBlank(String answer) {
        if (answered) return;
        answered = true;

        QuizQuestion q = questions.get(currentIndex);
        boolean correct = q.checkTextAnswer(answer);
        if (correct) correctCount++;

        btnSubmitFillBlank.setVisibility(View.GONE);
        showFeedback(correct, q);
    }

    private void showFeedback(boolean correct, QuizQuestion q) {
        cardFeedback.setVisibility(View.VISIBLE);
        btnNext.setVisibility(View.VISIBLE);

        if (correct) {
            tvFeedbackResult.setText("✓ Correct!");
            tvFeedbackResult.setTextColor(0xFF10B981);
        } else {
            String correctAnswer = q.getType() == QuizQuestion.TYPE_FILL_BLANK ?
                    q.getCorrectAnswer() :
                    (q.getOptions() != null && q.getCorrectIndex() < q.getOptions().size() ? q.getOptions().get(q.getCorrectIndex()) : "");
            tvFeedbackResult.setText("✗ Incorrect — " + correctAnswer);
            tvFeedbackResult.setTextColor(0xFFEF4444);
        }

        if (q.getExplanation() != null && !q.getExplanation().isEmpty()) {
            tvExplanation.setVisibility(View.VISIBLE);
            tvExplanation.setText(q.getExplanation());
        } else {
            tvExplanation.setVisibility(View.GONE);
        }

        if (currentIndex == questions.size() - 1) {
            btnNext.setText("See Results");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RESULTS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showResults() {
        long duration = (SystemClock.elapsedRealtime() - startTime) / 1000;

        // Save quiz result
        QuizResultsManager rm = new QuizResultsManager(this);
        QuizResultsManager.QuizResult result = new QuizResultsManager.QuizResult();
        result.noteId = noteId;
        result.noteTitle = noteTitle != null ? noteTitle : "Quiz";
        result.totalQuestions = questions.size();
        result.correctAnswers = correctCount;
        result.wrongAnswers = questions.size() - correctCount;
        result.durationSeconds = (int) duration;
        result.quizType = "auto";
        rm.saveResult(result);

        // Launch results activity
        android.content.Intent intent = new android.content.Intent(this, QuizResultsActivity.class);
        intent.putExtra("totalQuestions", questions.size());
        intent.putExtra("correctAnswers", correctCount);
        intent.putExtra("duration", (int) duration);
        intent.putExtra("noteTitle", noteTitle);
        startActivity(intent);
        finish();
    }
}
