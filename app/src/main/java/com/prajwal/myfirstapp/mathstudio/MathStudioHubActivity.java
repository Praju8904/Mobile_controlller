package com.prajwal.myfirstapp.mathstudio;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.List;

public class MathStudioHubActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_math_studio_hub);

        setupCardClicks();
        loadRecentCalculations();
        animateCards();
    }

    private void setupCardClicks() {
        View cardStandard = findViewById(R.id.cardModeStandard);
        View cardScientific = findViewById(R.id.cardModeScientific);
        View cardGraphing = findViewById(R.id.cardModeGraphing);
        View cardMatrix = findViewById(R.id.cardModeMatrix);
        View cardStats = findViewById(R.id.cardModeStatistics);
        View cardProgrammer = findViewById(R.id.cardModeProgrammer);
        View cardConverter = findViewById(R.id.cardModeConverter);
        View cardAI = findViewById(R.id.cardModeAI);
        View cardAptitude = findViewById(R.id.cardModeAptitude);
        View cardFormulas = findViewById(R.id.cardModeFormulas);
        View btnBack = findViewById(R.id.btnHubHistory);

        if (cardStandard != null) cardStandard.setOnClickListener(v ->
            startActivity(new Intent(this, StandardCalculatorActivity.class)));
        if (cardScientific != null) cardScientific.setOnClickListener(v ->
            startActivity(new Intent(this, ScientificCalculatorActivity.class)));
        if (cardGraphing != null) cardGraphing.setOnClickListener(v ->
            startActivity(new Intent(this, GraphingActivity.class)));
        if (cardMatrix != null) cardMatrix.setOnClickListener(v ->
            startActivity(new Intent(this, MatrixActivity.class)));
        if (cardStats != null) cardStats.setOnClickListener(v ->
            startActivity(new Intent(this, StatisticsActivity.class)));
        if (cardProgrammer != null) cardProgrammer.setOnClickListener(v ->
            startActivity(new Intent(this, ProgrammerCalculatorActivity.class)));
        if (cardConverter != null) cardConverter.setOnClickListener(v ->
            startActivity(new Intent(this, ConverterActivity.class)));
        if (cardAI != null) cardAI.setOnClickListener(v ->
            startActivity(new Intent(this, EquationSolverActivity.class)));
        if (cardAptitude != null) cardAptitude.setOnClickListener(v ->
            startActivity(new Intent(this, AptitudeActivity.class)));
        if (cardFormulas != null) cardFormulas.setOnClickListener(v ->
            startActivity(new Intent(this, FormulaBuilderActivity.class)));

        if (btnBack != null) btnBack.setOnClickListener(v -> showHistoryDialog());

        View btnHistory = findViewById(R.id.btnQuickHistory);
        View btnFormulas = findViewById(R.id.btnQuickFormulas);
        View btnConstants = findViewById(R.id.btnQuickConstants);
        if (btnHistory != null) btnHistory.setOnClickListener(v -> showHistoryDialog());
        if (btnFormulas != null) btnFormulas.setOnClickListener(v ->
            startActivity(new Intent(this, FormulaBuilderActivity.class)));
        if (btnConstants != null) btnConstants.setOnClickListener(v -> showConstantsDialog());
    }

    private void loadRecentCalculations() {
        LinearLayout container = findViewById(R.id.recentCalcContainer);
        if (container == null) return;
        container.removeAllViews();

        List<MathHistoryManager.HistoryEntry> history = MathHistoryManager.getHistory(this);
        int count = Math.min(6, history.size());
        if (count == 0) {
            TextView tv = new TextView(this);
            tv.setText("No recent calculations");
            tv.setTextColor(0xFF6B7280);
            tv.setTextSize(14f);
            tv.setPadding(8, 0, 8, 0);
            container.addView(tv);
            return;
        }

        for (int i = 0; i < count; i++) {
            MathHistoryManager.HistoryEntry entry = history.get(i);
            TextView pill = new TextView(this);
            pill.setText(entry.expression + " = " + entry.result);
            pill.setTextColor(0xFFA5B4FC);
            pill.setTextSize(13f);
            int dp8 = (int)(8 * getResources().getDisplayMetrics().density);
            int dp12 = (int)(12 * getResources().getDisplayMetrics().density);
            pill.setPadding(dp12, dp8, dp12, dp8);
            pill.setBackground(getDrawable(R.drawable.math_recent_pill_bg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp8);
            pill.setLayoutParams(lp);
            pill.setSingleLine(true);
            container.addView(pill);
        }
    }

    private void animateCards() {
        int[] cardIds = {
            R.id.cardModeStandard, R.id.cardModeScientific, R.id.cardModeGraphing,
            R.id.cardModeMatrix, R.id.cardModeStatistics, R.id.cardModeProgrammer,
            R.id.cardModeConverter, R.id.cardModeAI, R.id.cardModeAptitude, R.id.cardModeFormulas
        };
        for (int i = 0; i < cardIds.length; i++) {
            View card = findViewById(cardIds[i]);
            if (card != null) {
                card.setAlpha(0f);
                card.setTranslationY(40f);
                int delay = i * 60;
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(350)
                    .start();
            }
        }
    }

    private void showComingSoon(String feature) {
        android.widget.Toast.makeText(this, feature + " coming soon!", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showHistoryDialog() {
        List<MathHistoryManager.HistoryEntry> history = MathHistoryManager.getHistory(this);
        if (history.isEmpty()) {
            android.widget.Toast.makeText(this, "No history yet", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(10, history.size()); i++) {
            MathHistoryManager.HistoryEntry e = history.get(i);
            sb.append(e.expression).append(" = ").append(e.result).append("\n");
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Recent History")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .setNegativeButton("Clear All", (d, w) -> {
                MathHistoryManager.clearHistory(this);
                loadRecentCalculations();
            })
            .show();
    }

    private void showConstantsDialog() {
        String msg = "π = 3.14159265358979\n" +
                     "e = 2.71828182845905\n" +
                     "φ = 1.61803398874989\n" +
                     "√2 = 1.41421356237310\n" +
                     "√3 = 1.73205080756888\n" +
                     "ln(2) = 0.69314718055995\n" +
                     "ln(10) = 2.30258509299405";
        new android.app.AlertDialog.Builder(this)
            .setTitle("Mathematical Constants")
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
