package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  QUIZ RESULTS ACTIVITY — Displays score, grade, and question review after a quiz.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class QuizResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_results);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        int total = getIntent().getIntExtra("totalQuestions", 0);
        int correct = getIntent().getIntExtra("correctAnswers", 0);
        int duration = getIntent().getIntExtra("duration", 0);
        String noteTitle = getIntent().getStringExtra("noteTitle");

        int wrong = total - correct;
        int percent = total > 0 ? (correct * 100 / total) : 0;

        // Grade
        String grade;
        int gradeColor;
        String emoji;
        if (percent >= 95) { grade = "A+"; gradeColor = 0xFF10B981; emoji = "🏆"; }
        else if (percent >= 85) { grade = "A"; gradeColor = 0xFF10B981; emoji = "🎯"; }
        else if (percent >= 75) { grade = "B"; gradeColor = 0xFF3B82F6; emoji = "👍"; }
        else if (percent >= 60) { grade = "C"; gradeColor = 0xFFF59E0B; emoji = "📝"; }
        else if (percent >= 40) { grade = "D"; gradeColor = 0xFFF97316; emoji = "📚"; }
        else { grade = "F"; gradeColor = 0xFFEF4444; emoji = "💪"; }

        // Bind views
        ((TextView) findViewById(R.id.tvScoreEmoji)).setText(emoji);

        TextView tvGrade = findViewById(R.id.tvGrade);
        tvGrade.setText(grade);
        tvGrade.setTextColor(gradeColor);

        ((TextView) findViewById(R.id.tvScore)).setText(correct + " / " + total + " correct");
        ((TextView) findViewById(R.id.tvScorePercent)).setText(percent + "%");

        // Time
        String timeStr = String.format("%d:%02d", duration / 60, duration % 60);
        ((TextView) findViewById(R.id.tvTimeTaken)).setText(timeStr);
        ((TextView) findViewById(R.id.tvCorrectCount)).setText(String.valueOf(correct));
        ((TextView) findViewById(R.id.tvWrongCount)).setText(String.valueOf(wrong));

        // Buttons
        ((MaterialButton) findViewById(R.id.btnRetry)).setOnClickListener(v -> {
            // Go back to quiz with same params
            setResult(RESULT_OK);
            finish();
        });

        ((MaterialButton) findViewById(R.id.btnDone)).setOnClickListener(v -> finish());
    }
}
