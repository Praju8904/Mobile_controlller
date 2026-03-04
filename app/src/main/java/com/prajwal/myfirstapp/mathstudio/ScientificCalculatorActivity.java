package com.prajwal.myfirstapp.mathstudio;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.CycleInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;

public class ScientificCalculatorActivity extends AppCompatActivity {

    private TextView tvExpression, tvResult, tvMemory, tvInvHyp;
    private StringBuilder expression = new StringBuilder();
    private ExpressionEngine.AngleMode angleMode = ExpressionEngine.AngleMode.DEG;
    private boolean invMode = false;
    private boolean hypMode = false;
    private double memory = 0;
    private double lastAnswer = 0;
    private boolean justEvaluated = false;

    // Trig button refs for label toggling
    private Button btnSin, btnCos, btnTan;
    private Button btnInv, btnHyp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scientific_calculator);

        tvExpression = findViewById(R.id.tvExpression);
        tvResult = findViewById(R.id.tvResult);
        tvMemory = findViewById(R.id.tvMemory);
        tvInvHyp = findViewById(R.id.tvInvHyp);
        btnSin = findViewById(R.id.btnSin);
        btnCos = findViewById(R.id.btnCos);
        btnTan = findViewById(R.id.btnTan);
        btnInv = findViewById(R.id.btnInv);
        btnHyp = findViewById(R.id.btnHyp);

        setupButtons();

        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());
    }

    private void setupButtons() {
        // Angle mode
        setupAngleBtn(R.id.btnDeg, ExpressionEngine.AngleMode.DEG);
        setupAngleBtn(R.id.btnRad, ExpressionEngine.AngleMode.RAD);
        setupAngleBtn(R.id.btnGrad, ExpressionEngine.AngleMode.GRAD);

        // INV toggle
        if (btnInv != null) btnInv.setOnClickListener(v -> {
            invMode = !invMode;
            updateTrigLabels();
            tvInvHyp.setText(invMode ? (hypMode ? "INV+HYP" : "INV") : (hypMode ? "HYP" : ""));
        });

        // HYP toggle
        if (btnHyp != null) btnHyp.setOnClickListener(v -> {
            hypMode = !hypMode;
            updateTrigLabels();
            tvInvHyp.setText(invMode ? (hypMode ? "INV+HYP" : "INV") : (hypMode ? "HYP" : ""));
        });

        // Trig
        if (btnSin != null) btnSin.setOnClickListener(v -> { haptic(v); appendFunction(getTrigName("sin")); });
        if (btnCos != null) btnCos.setOnClickListener(v -> { haptic(v); appendFunction(getTrigName("cos")); });
        if (btnTan != null) btnTan.setOnClickListener(v -> { haptic(v); appendFunction(getTrigName("tan")); });

        // Log/power functions
        btnClick(R.id.btnLog, () -> appendFunction("log"));
        btnClick(R.id.btnLn, () -> appendFunction("ln"));
        btnClick(R.id.btnLog2, () -> appendFunction("log2"));
        btnClick(R.id.btnSq, () -> appendSuffix("^2"));
        btnClick(R.id.btnCube, () -> appendSuffix("^3"));
        btnClick(R.id.btnPow, () -> appendSymbol("^"));
        btnClick(R.id.btnSqrt, () -> appendFunction("sqrt"));
        btnClick(R.id.btnCbrt, () -> appendFunction("cbrt"));
        btnClick(R.id.btnAbs, () -> appendFunction("abs"));
        btnClick(R.id.btnExp, () -> appendFunction("exp"));
        btnClick(R.id.btnTenPow, () -> { appendSymbol("10^"); });
        btnClick(R.id.btnTwoPow, () -> { appendSymbol("2^"); });

        // Constants
        btnClick(R.id.btnPi, () -> appendSymbol("π"));
        btnClick(R.id.btnE, () -> appendSymbol("e"));
        btnClick(R.id.btnPhi, () -> appendSymbol("φ"));

        // Special operations
        btnClick(R.id.btnFact, () -> appendSuffix("!"));
        btnClick(R.id.btnNPr, () -> { appendSymbol("nPr"); Toast.makeText(this,"Use: fact(n)/fact(n-r)", Toast.LENGTH_SHORT).show(); });
        btnClick(R.id.btnNCr, () -> { appendSymbol("nCr"); Toast.makeText(this,"Use: fact(n)/(fact(r)*fact(n-r))", Toast.LENGTH_SHORT).show(); });
        btnClick(R.id.btnMod, () -> appendSymbol("%"));
        btnClick(R.id.btnRand, () -> { appendSymbol(ExpressionEngine.formatResult(Math.random())); });

        // Memory
        btnClick(R.id.btnMC, () -> { memory = 0; tvMemory.setText(""); });
        btnClick(R.id.btnMR, () -> { insertText(ExpressionEngine.formatResult(memory)); });
        btnClick(R.id.btnMPlus, () -> {
            try { memory += ExpressionEngine.evaluate(expression.toString(), angleMode); tvMemory.setText("M=" + ExpressionEngine.formatResult(memory)); } catch(Exception ignored){}
        });
        btnClick(R.id.btnMMinus, () -> {
            try { memory -= ExpressionEngine.evaluate(expression.toString(), angleMode); tvMemory.setText("M=" + ExpressionEngine.formatResult(memory)); } catch(Exception ignored){}
        });

        // Digits
        int[] numIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                        R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        String[] nums = {"0","1","2","3","4","5","6","7","8","9"};
        for (int i = 0; i < numIds.length; i++) {
            final String d = nums[i];
            Button b = findViewById(numIds[i]);
            if (b != null) b.setOnClickListener(v -> { haptic(v); appendDigit(d); });
        }
        btnClick(R.id.btnDecimal, () -> appendSymbol("."));
        btnClick(R.id.btnClear, () -> clear());
        btnClick(R.id.btnDelete, () -> deleteLast());
        btnClick(R.id.btnDelete2, () -> deleteLast());
        btnClick(R.id.btnPlusMinus, () -> toggleSign());
        btnClick(R.id.btnPercent, () -> appendSymbol("%"));
        btnClick(R.id.btnDivide, () -> appendOperator("/"));
        btnClick(R.id.btnMultiply, () -> appendOperator("*"));
        btnClick(R.id.btnMinus, () -> appendOperator("-"));
        btnClick(R.id.btnPlus, () -> appendOperator("+"));
        btnClick(R.id.btnEquals, () -> evaluate());
        btnClick(R.id.btnOpenParen, () -> appendSymbol("("));
        btnClick(R.id.btnCloseParen, () -> appendSymbol(")"));
        btnClick(R.id.btnAns, () -> insertText(ExpressionEngine.formatResult(lastAnswer)));
        btnClick(R.id.btnEExp, () -> appendSymbol("E"));
    }

    private void setupAngleBtn(int id, ExpressionEngine.AngleMode mode) {
        TextView btn = findViewById(id);
        if (btn != null) btn.setOnClickListener(v -> {
            angleMode = mode;
            updateAngleBtns();
        });
    }

    private void updateAngleBtns() {
        int activeRes = R.drawable.math_deg_btn_active_bg;
        int inactiveRes = R.drawable.math_deg_btn_inactive_bg;
        TextView deg = findViewById(R.id.btnDeg);
        TextView rad = findViewById(R.id.btnRad);
        TextView grad = findViewById(R.id.btnGrad);
        if (deg != null) { deg.setBackground(getDrawable(angleMode == ExpressionEngine.AngleMode.DEG ? activeRes : inactiveRes)); deg.setTextColor(angleMode == ExpressionEngine.AngleMode.DEG ? 0xFFFFFFFF : 0xFFA5B4FC); }
        if (rad != null) { rad.setBackground(getDrawable(angleMode == ExpressionEngine.AngleMode.RAD ? activeRes : inactiveRes)); rad.setTextColor(angleMode == ExpressionEngine.AngleMode.RAD ? 0xFFFFFFFF : 0xFFA5B4FC); }
        if (grad != null) { grad.setBackground(getDrawable(angleMode == ExpressionEngine.AngleMode.GRAD ? activeRes : inactiveRes)); grad.setTextColor(angleMode == ExpressionEngine.AngleMode.GRAD ? 0xFFFFFFFF : 0xFFA5B4FC); }
    }

    private String getTrigName(String base) {
        if (invMode && hypMode) return "a" + base + "h"; // inverse hyperbolic: asinh, acosh, atanh
        if (invMode) return "a" + base;
        if (hypMode) return base + "h"; // sinh, cosh, tanh
        return base;
    }

    private void updateTrigLabels() {
        if (btnSin != null) btnSin.setText(getTrigLabel("sin"));
        if (btnCos != null) btnCos.setText(getTrigLabel("cos"));
        if (btnTan != null) btnTan.setText(getTrigLabel("tan"));
    }

    private String getTrigLabel(String base) {
        if (invMode && hypMode) return "a" + base + "h";
        if (invMode) return "a" + base;
        if (hypMode) return base + "h";
        return base;
    }

    private void btnClick(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(view -> { haptic(view); action.run(); });
    }

    private void appendDigit(String d) {
        if (justEvaluated) { expression.setLength(0); justEvaluated = false; }
        expression.append(d);
        updateDisplay();
    }

    private void appendSymbol(String s) {
        if (justEvaluated) justEvaluated = false;
        expression.append(s);
        updateDisplay();
    }

    private void appendFunction(String fn) {
        if (justEvaluated) { justEvaluated = false; }
        expression.append(fn).append("(");
        updateDisplay();
    }

    private void appendSuffix(String s) {
        expression.append(s);
        updateDisplay();
    }

    private void appendOperator(String op) {
        if (justEvaluated) justEvaluated = false;
        String cur = expression.toString();
        if (cur.length() > 0) {
            char last = cur.charAt(cur.length() - 1);
            if (last == '+' || last == '-' || last == '*' || last == '/') {
                expression.deleteCharAt(expression.length() - 1);
            }
        }
        expression.append(op);
        updateDisplay();
    }

    private void insertText(String text) {
        if (justEvaluated) { expression.setLength(0); justEvaluated = false; }
        expression.append(text);
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
        String cur = expression.toString();
        if (cur.startsWith("-")) expression.deleteCharAt(0);
        else expression.insert(0, "-");
        updateDisplay();
    }

    private void evaluate() {
        String expr = expression.toString();
        if (expr.isEmpty()) return;
        try {
            double result = ExpressionEngine.evaluate(expr, angleMode);
            String resultStr = ExpressionEngine.formatResult(result);
            lastAnswer = result;
            MathHistoryManager.saveCalculation(this, expr, resultStr, angleMode.name());
            tvResult.setText(resultStr);
            tvExpression.setText(expr + " =");
            expression.setLength(0);
            expression.append(resultStr);
            justEvaluated = true;
        } catch (Exception e) {
            shakeView(tvResult);
            tvResult.setText("Error");
        }
    }

    private void updateDisplay() {
        tvExpression.setText(expression.toString());
        try {
            String expr = expression.toString();
            if (!expr.isEmpty() && !expr.endsWith("(") && !expr.endsWith("+")
                && !expr.endsWith("-") && !expr.endsWith("*") && !expr.endsWith("/")) {
                double result = ExpressionEngine.evaluate(expr, angleMode);
                tvResult.setText(ExpressionEngine.formatResult(result));
            }
        } catch (Exception ignored) {}
    }

    private void shakeView(View view) {
        view.animate().translationX(15f).setDuration(100)
            .setInterpolator(new CycleInterpolator(3))
            .withEndAction(() -> view.setTranslationX(0)).start();
    }

    private void haptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
