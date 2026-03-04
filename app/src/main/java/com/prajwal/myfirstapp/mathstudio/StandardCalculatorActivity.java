package com.prajwal.myfirstapp.mathstudio;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.LinkedList;
import java.util.Queue;

public class StandardCalculatorActivity extends AppCompatActivity {

    private TextView tvExpression, tvResult, tvHistory1, tvHistory2, tvHistory3;
    private StringBuilder expression = new StringBuilder();
    private double lastAnswer = 0;
    private boolean justEvaluated = false;
    private final LinkedList<String> historyExpressions = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_standard_calculator);

        tvExpression = findViewById(R.id.tvExpression);
        tvResult = findViewById(R.id.tvResult);
        tvHistory1 = findViewById(R.id.tvHistory1);
        tvHistory2 = findViewById(R.id.tvHistory2);
        tvHistory3 = findViewById(R.id.tvHistory3);

        setupButtons();

        tvResult.setOnLongClickListener(v -> {
            String text = tvResult.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("result", text));
            Toast.makeText(this, "Copied: " + text, Toast.LENGTH_SHORT).show();
            return true;
        });

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupButtons() {
        int[] numIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                        R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        String[] nums = {"0","1","2","3","4","5","6","7","8","9"};
        for (int i = 0; i < numIds.length; i++) {
            final String d = nums[i];
            Button b = findViewById(numIds[i]);
            if (b != null) b.setOnClickListener(v -> { haptic(v); appendDigit(d); });
        }

        Button btnDecimal = findViewById(R.id.btnDecimal);
        if (btnDecimal != null) btnDecimal.setOnClickListener(v -> { haptic(v); appendSymbol("."); });

        Button btnClear = findViewById(R.id.btnClear);
        if (btnClear != null) btnClear.setOnClickListener(v -> { haptic(v); clear(); });

        Button btnDelete = findViewById(R.id.btnDelete);
        if (btnDelete != null) btnDelete.setOnClickListener(v -> { haptic(v); deleteLast(); });

        Button btnPlusMinus = findViewById(R.id.btnPlusMinus);
        if (btnPlusMinus != null) btnPlusMinus.setOnClickListener(v -> { haptic(v); toggleSign(); });

        Button btnPercent = findViewById(R.id.btnPercent);
        if (btnPercent != null) btnPercent.setOnClickListener(v -> { haptic(v); appendSymbol("%"); });

        Button btnDivide = findViewById(R.id.btnDivide);
        if (btnDivide != null) btnDivide.setOnClickListener(v -> { haptic(v); appendOperator("/"); });

        Button btnMultiply = findViewById(R.id.btnMultiply);
        if (btnMultiply != null) btnMultiply.setOnClickListener(v -> { haptic(v); appendOperator("*"); });

        Button btnMinus = findViewById(R.id.btnMinus);
        if (btnMinus != null) btnMinus.setOnClickListener(v -> { haptic(v); appendOperator("-"); });

        Button btnPlus = findViewById(R.id.btnPlus);
        if (btnPlus != null) btnPlus.setOnClickListener(v -> { haptic(v); appendOperator("+"); });

        Button btnEquals = findViewById(R.id.btnEquals);
        if (btnEquals != null) btnEquals.setOnClickListener(v -> { haptic(v); evaluate(); });

        Button btnOpenParen = findViewById(R.id.btnOpenParen);
        if (btnOpenParen != null) btnOpenParen.setOnClickListener(v -> { haptic(v); appendSymbol("("); });

        Button btnCloseParen = findViewById(R.id.btnCloseParen);
        if (btnCloseParen != null) btnCloseParen.setOnClickListener(v -> { haptic(v); appendSymbol(")"); });

        Button btnPi = findViewById(R.id.btnPi);
        if (btnPi != null) btnPi.setOnClickListener(v -> { haptic(v); appendSymbol("π"); });

        Button btnAns = findViewById(R.id.btnAns);
        if (btnAns != null) btnAns.setOnClickListener(v -> { haptic(v); insertAns(); });
    }

    private void appendDigit(String digit) {
        if (justEvaluated) {
            expression.setLength(0);
            justEvaluated = false;
        }
        expression.append(digit);
        updateDisplay();
    }

    private void appendSymbol(String sym) {
        if (justEvaluated && !sym.equals("(")) {
            justEvaluated = false;
        }
        expression.append(sym);
        updateDisplay();
    }

    private void appendOperator(String op) {
        if (justEvaluated) justEvaluated = false;
        String current = expression.toString();
        if (current.length() > 0) {
            char last = current.charAt(current.length() - 1);
            if (last == '+' || last == '-' || last == '*' || last == '/') {
                expression.deleteCharAt(expression.length() - 1);
            }
        }
        expression.append(op);
        updateDisplay();
    }

    private void deleteLast() {
        justEvaluated = false;
        if (expression.length() > 0) {
            expression.deleteCharAt(expression.length() - 1);
            updateDisplay();
        }
    }

    private void clear() {
        expression.setLength(0);
        tvResult.setText("0");
        tvExpression.setText("");
        justEvaluated = false;
    }

    private void toggleSign() {
        String current = expression.toString();
        if (current.startsWith("-")) {
            expression.deleteCharAt(0);
        } else {
            expression.insert(0, "-");
        }
        updateDisplay();
    }

    private void insertAns() {
        if (justEvaluated) {
            expression.setLength(0);
            justEvaluated = false;
        }
        expression.append(ExpressionEngine.formatResult(lastAnswer));
        updateDisplay();
    }

    private void evaluate() {
        String expr = expression.toString();
        if (expr.isEmpty()) return;
        try {
            double result = ExpressionEngine.evaluate(expr);
            String resultStr = ExpressionEngine.formatResult(result);
            lastAnswer = result;

            // Save to history
            MathHistoryManager.saveCalculation(this, expr, resultStr, "STD");

            // Update history trail
            historyExpressions.addFirst(expr + " = " + resultStr);
            while (historyExpressions.size() > 3) historyExpressions.removeLast();
            updateHistoryDisplay();

            // Animate result
            animateResult(tvResult.getText().toString(), resultStr);

            expression.setLength(0);
            expression.append(resultStr);
            tvExpression.setText(expr + " =");
            justEvaluated = true;

        } catch (Exception e) {
            shakeView(tvResult);
            tvResult.setText("Error");
        }
    }

    private void updateDisplay() {
        tvExpression.setText(expression.toString());
        // Live preview
        try {
            String expr = expression.toString();
            if (!expr.isEmpty() && !expr.endsWith("+") && !expr.endsWith("-")
                && !expr.endsWith("*") && !expr.endsWith("/") && !expr.endsWith("(")) {
                double result = ExpressionEngine.evaluate(expr);
                tvResult.setText(ExpressionEngine.formatResult(result));
            }
        } catch (Exception ignored) {}
    }

    private void updateHistoryDisplay() {
        String[] texts = historyExpressions.toArray(new String[0]);
        if (tvHistory1 != null) tvHistory1.setText(texts.length > 2 ? texts[2] : "");
        if (tvHistory2 != null) tvHistory2.setText(texts.length > 1 ? texts[1] : "");
        if (tvHistory3 != null) tvHistory3.setText(texts.length > 0 ? texts[0] : "");
    }

    private void animateResult(String fromStr, String toStr) {
        try {
            double from = Double.parseDouble(fromStr.replace(",", ""));
            double to = Double.parseDouble(toStr.replace(",", ""));
            ValueAnimator animator = ValueAnimator.ofFloat((float) from, (float) to);
            animator.setDuration(400);
            animator.addUpdateListener(a -> {
                float val = (float) a.getAnimatedValue();
                tvResult.setText(ExpressionEngine.formatResult(val));
            });
            animator.start();
        } catch (Exception e) {
            tvResult.setText(toStr);
        }
    }

    private void shakeView(View view) {
        view.animate()
            .translationX(15f)
            .setDuration(100)
            .setInterpolator(new CycleInterpolator(3))
            .withEndAction(() -> view.setTranslationX(0))
            .start();
    }

    private void haptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
