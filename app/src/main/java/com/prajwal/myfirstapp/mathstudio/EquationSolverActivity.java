package com.prajwal.myfirstapp.mathstudio;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.Locale;

public class EquationSolverActivity extends AppCompatActivity {

    private static final double EPSILON = 1e-12;
    private int activeTab = 0; // 0=Linear, 1=Quadratic, 2=System

    // Tab panels
    private View panelLinear, panelQuadratic, panelSystem;
    private TextView tabLinear, tabQuadratic, tabSystem;

    // Linear inputs
    private EditText etLinearA, etLinearB, etLinearC;

    // Quadratic inputs
    private EditText etQuadA, etQuadB, etQuadC;

    // System inputs
    private EditText etSysA1, etSysB1, etSysC1;
    private EditText etSysA2, etSysB2, etSysC2;

    // Result views
    private TextView tvLinearResult, tvLinearSteps;
    private TextView tvQuadResult, tvQuadSteps;
    private TextView tvSysResult, tvSysSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equation_solver);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        initTabs();
        initLinearPanel();
        initQuadraticPanel();
        initSystemPanel();

        showTab(0);
    }

    private void initTabs() {
        tabLinear = findViewById(R.id.tabLinear);
        tabQuadratic = findViewById(R.id.tabQuadratic);
        tabSystem = findViewById(R.id.tabSystem);
        panelLinear = findViewById(R.id.panelLinear);
        panelQuadratic = findViewById(R.id.panelQuadratic);
        panelSystem = findViewById(R.id.panelSystem);

        if (tabLinear != null) tabLinear.setOnClickListener(v -> { haptic(v); showTab(0); });
        if (tabQuadratic != null) tabQuadratic.setOnClickListener(v -> { haptic(v); showTab(1); });
        if (tabSystem != null) tabSystem.setOnClickListener(v -> { haptic(v); showTab(2); });
    }

    private void showTab(int idx) {
        activeTab = idx;
        if (panelLinear != null) panelLinear.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (panelQuadratic != null) panelQuadratic.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        if (panelSystem != null) panelSystem.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);

        int active = 0xFF3B82F6;
        int inactive = 0xFFA5B4FC;
        if (tabLinear != null) {
            tabLinear.setTextColor(idx == 0 ? 0xFFFFFFFF : inactive);
            tabLinear.setBackgroundColor(idx == 0 ? active : 0x00000000);
        }
        if (tabQuadratic != null) {
            tabQuadratic.setTextColor(idx == 1 ? 0xFFFFFFFF : inactive);
            tabQuadratic.setBackgroundColor(idx == 1 ? active : 0x00000000);
        }
        if (tabSystem != null) {
            tabSystem.setTextColor(idx == 2 ? 0xFFFFFFFF : inactive);
            tabSystem.setBackgroundColor(idx == 2 ? active : 0x00000000);
        }
    }

    // ====================== LINEAR ======================

    private void initLinearPanel() {
        etLinearA = findViewById(R.id.etLinearA);
        etLinearB = findViewById(R.id.etLinearB);
        etLinearC = findViewById(R.id.etLinearC);
        tvLinearResult = findViewById(R.id.tvLinearResult);
        tvLinearSteps = findViewById(R.id.tvLinearSteps);

        View btnSolve = findViewById(R.id.btnSolveLinear);
        View btnClear = findViewById(R.id.btnClearLinear);
        if (btnSolve != null) btnSolve.setOnClickListener(v -> { haptic(v); solveLinear(); });
        if (btnClear != null) btnClear.setOnClickListener(v -> { haptic(v); clearLinear(); });
    }

    private void solveLinear() {
        String aStr = etLinearA != null ? etLinearA.getText().toString().trim() : "";
        String bStr = etLinearB != null ? etLinearB.getText().toString().trim() : "";
        String cStr = etLinearC != null ? etLinearC.getText().toString().trim() : "";

        if (aStr.isEmpty()) aStr = "1";
        if (bStr.isEmpty()) bStr = "0";
        if (cStr.isEmpty()) cStr = "0";

        try {
            double a = Double.parseDouble(aStr);
            double b = Double.parseDouble(bStr);
            double c = Double.parseDouble(cStr);

            String eqDisplay = formatLinearEq(a, b, c);
            StringBuilder steps = new StringBuilder();
            String result;

            steps.append("Given: ").append(eqDisplay).append("\n\n");

            if (a == 0) {
                if (Math.abs(b - c) < EPSILON) {
                    result = "Infinite solutions (identity: " + fmt(b) + " = " + fmt(c) + ")";
                    steps.append("Since a = 0, the equation becomes:\n");
                    steps.append(fmt(b)).append(" = ").append(fmt(c)).append("\n");
                    steps.append("This is always true → infinite solutions.");
                } else {
                    result = "No solution (contradiction: " + fmt(b) + " ≠ " + fmt(c) + ")";
                    steps.append("Since a = 0, the equation becomes:\n");
                    steps.append(fmt(b)).append(" = ").append(fmt(c)).append("\n");
                    steps.append("This is never true → no solution.");
                }
            } else {
                double x = (c - b) / a;
                result = "x = " + fmt(x);

                steps.append("Step 1: Subtract ").append(fmt(b)).append(" from both sides\n");
                steps.append("  → ").append(fmt(a)).append("x = ").append(fmt(c)).append(" − ").append(fmt(b))
                     .append(" = ").append(fmt(c - b)).append("\n\n");
                steps.append("Step 2: Divide both sides by ").append(fmt(a)).append("\n");
                steps.append("  → x = ").append(fmt(c - b)).append(" ÷ ").append(fmt(a)).append("\n\n");
                steps.append("✓  x = ").append(fmt(x));

                MathHistoryManager.saveCalculation(this, eqDisplay, result, "EQN");
            }

            if (tvLinearResult != null) tvLinearResult.setText(result);
            if (tvLinearSteps != null) tvLinearSteps.setText(steps.toString());

        } catch (NumberFormatException e) {
            if (tvLinearResult != null) tvLinearResult.setText("Invalid input");
            if (tvLinearSteps != null) tvLinearSteps.setText("");
        }
    }

    private void clearLinear() {
        if (etLinearA != null) etLinearA.setText("");
        if (etLinearB != null) etLinearB.setText("");
        if (etLinearC != null) etLinearC.setText("");
        if (tvLinearResult != null) tvLinearResult.setText("");
        if (tvLinearSteps != null) tvLinearSteps.setText("");
    }

    private String formatLinearEq(double a, double b, double c) {
        String aStr = (a == 1) ? "" : (a == -1 ? "-" : fmt(a));
        String bPart;
        if (b == 0) {
            bPart = "";
        } else if (b > 0) {
            bPart = " + " + fmt(b);
        } else {
            bPart = " − " + fmt(-b);
        }
        return aStr + "x" + bPart + " = " + fmt(c);
    }

    // ====================== QUADRATIC ======================

    private void initQuadraticPanel() {
        etQuadA = findViewById(R.id.etQuadA);
        etQuadB = findViewById(R.id.etQuadB);
        etQuadC = findViewById(R.id.etQuadC);
        tvQuadResult = findViewById(R.id.tvQuadResult);
        tvQuadSteps = findViewById(R.id.tvQuadSteps);

        View btnSolve = findViewById(R.id.btnSolveQuad);
        View btnClear = findViewById(R.id.btnClearQuad);
        if (btnSolve != null) btnSolve.setOnClickListener(v -> { haptic(v); solveQuadratic(); });
        if (btnClear != null) btnClear.setOnClickListener(v -> { haptic(v); clearQuadratic(); });
    }

    private void solveQuadratic() {
        String aStr = etQuadA != null ? etQuadA.getText().toString().trim() : "";
        String bStr = etQuadB != null ? etQuadB.getText().toString().trim() : "";
        String cStr = etQuadC != null ? etQuadC.getText().toString().trim() : "";

        if (aStr.isEmpty()) aStr = "1";
        if (bStr.isEmpty()) bStr = "0";
        if (cStr.isEmpty()) cStr = "0";

        try {
            double a = Double.parseDouble(aStr);
            double b = Double.parseDouble(bStr);
            double c = Double.parseDouble(cStr);

            StringBuilder steps = new StringBuilder();
            String result;
            String eqDisplay = formatQuadEq(a, b, c);

            steps.append("Given: ").append(eqDisplay).append("\n\n");

            if (a == 0) {
                // Degenerate case — treat as linear
                steps.append("a = 0, solving as linear: ").append(fmt(b)).append("x + ").append(fmt(c)).append(" = 0\n\n");
                if (b == 0) {
                    result = c == 0 ? "Infinite solutions" : "No solution";
                    steps.append(result);
                } else {
                    double x = -c / b;
                    result = "x = " + fmt(x);
                    steps.append("x = −").append(fmt(c)).append(" ÷ ").append(fmt(b)).append(" = ").append(fmt(x));
                    MathHistoryManager.saveCalculation(this, eqDisplay, result, "QUAD");
                }
            } else {
                double disc = b * b - 4 * a * c;
                steps.append("Step 1: Calculate discriminant D = b² − 4ac\n");
                steps.append("  D = (").append(fmt(b)).append(")² − 4 × (").append(fmt(a))
                     .append(") × (").append(fmt(c)).append(")\n");
                steps.append("  D = ").append(fmt(b * b)).append(" − ").append(fmt(4 * a * c))
                     .append(" = ").append(fmt(disc)).append("\n\n");

                if (disc > 0) {
                    double sqrtDisc = Math.sqrt(disc);
                    double x1 = (-b + sqrtDisc) / (2 * a);
                    double x2 = (-b - sqrtDisc) / (2 * a);
                    result = "x₁ = " + fmt(x1) + ",  x₂ = " + fmt(x2);
                    steps.append("D > 0 → Two distinct real roots\n\n");
                    steps.append("Step 2: x = (−b ± √D) / (2a)\n");
                    steps.append("  x₁ = (").append(fmt(-b)).append(" + ").append(fmt(sqrtDisc))
                         .append(") / ").append(fmt(2 * a)).append(" = ").append(fmt(x1)).append("\n");
                    steps.append("  x₂ = (").append(fmt(-b)).append(" − ").append(fmt(sqrtDisc))
                         .append(") / ").append(fmt(2 * a)).append(" = ").append(fmt(x2)).append("\n\n");
                    steps.append("✓  x₁ = ").append(fmt(x1)).append(",  x₂ = ").append(fmt(x2));
                    MathHistoryManager.saveCalculation(this, eqDisplay, result, "QUAD");
                } else if (disc == 0) {
                    double x = -b / (2 * a);
                    result = "x = " + fmt(x) + "  (repeated root)";
                    steps.append("D = 0 → One repeated root\n\n");
                    steps.append("Step 2: x = −b / (2a)\n");
                    steps.append("  x = ").append(fmt(-b)).append(" / ").append(fmt(2 * a))
                         .append(" = ").append(fmt(x)).append("\n\n");
                    steps.append("✓  x = ").append(fmt(x)).append("  (double root)");
                    MathHistoryManager.saveCalculation(this, eqDisplay, result, "QUAD");
                } else {
                    // Complex roots
                    double realPart = -b / (2 * a);
                    double imagPart = Math.sqrt(-disc) / (2 * a);
                    result = "x = " + fmt(realPart) + " ± " + fmt(imagPart) + "i";
                    steps.append("D < 0 → Two complex conjugate roots\n\n");
                    steps.append("Step 2: Real part = −b/(2a) = ").append(fmt(realPart)).append("\n");
                    steps.append("Step 3: Imaginary part = √|D|/(2a) = ").append(fmt(imagPart)).append("\n\n");
                    steps.append("✓  x₁ = ").append(fmt(realPart)).append(" + ").append(fmt(imagPart)).append("i\n");
                    steps.append("   x₂ = ").append(fmt(realPart)).append(" − ").append(fmt(imagPart)).append("i");
                    MathHistoryManager.saveCalculation(this, eqDisplay, result, "QUAD");
                }
            }

            if (tvQuadResult != null) tvQuadResult.setText(result);
            if (tvQuadSteps != null) tvQuadSteps.setText(steps.toString());

        } catch (NumberFormatException e) {
            if (tvQuadResult != null) tvQuadResult.setText("Invalid input");
            if (tvQuadSteps != null) tvQuadSteps.setText("");
        }
    }

    private void clearQuadratic() {
        if (etQuadA != null) etQuadA.setText("");
        if (etQuadB != null) etQuadB.setText("");
        if (etQuadC != null) etQuadC.setText("");
        if (tvQuadResult != null) tvQuadResult.setText("");
        if (tvQuadSteps != null) tvQuadSteps.setText("");
    }

    private String formatQuadEq(double a, double b, double c) {
        String aStr = (a == 1) ? "" : (a == -1 ? "−" : fmt(a));
        String bPart = "";
        if (b != 0) {
            bPart = b > 0 ? " + " + (b == 1 ? "" : fmt(b)) + "x" : " − " + (b == -1 ? "" : fmt(-b)) + "x";
        }
        String cPart = "";
        if (c != 0) {
            cPart = c > 0 ? " + " + fmt(c) : " − " + fmt(-c);
        }
        return aStr + "x²" + bPart + cPart + " = 0";
    }

    // ====================== SYSTEM ======================

    private void initSystemPanel() {
        etSysA1 = findViewById(R.id.etSysA1);
        etSysB1 = findViewById(R.id.etSysB1);
        etSysC1 = findViewById(R.id.etSysC1);
        etSysA2 = findViewById(R.id.etSysA2);
        etSysB2 = findViewById(R.id.etSysB2);
        etSysC2 = findViewById(R.id.etSysC2);
        tvSysResult = findViewById(R.id.tvSysResult);
        tvSysSteps = findViewById(R.id.tvSysSteps);

        View btnSolve = findViewById(R.id.btnSolveSys);
        View btnClear = findViewById(R.id.btnClearSys);
        if (btnSolve != null) btnSolve.setOnClickListener(v -> { haptic(v); solveSystem(); });
        if (btnClear != null) btnClear.setOnClickListener(v -> { haptic(v); clearSystem(); });
    }

    private void solveSystem() {
        String a1s = getVal(etSysA1, "1"), b1s = getVal(etSysB1, "0"), c1s = getVal(etSysC1, "0");
        String a2s = getVal(etSysA2, "0"), b2s = getVal(etSysB2, "1"), c2s = getVal(etSysC2, "0");

        try {
            double a1 = Double.parseDouble(a1s), b1 = Double.parseDouble(b1s), c1 = Double.parseDouble(c1s);
            double a2 = Double.parseDouble(a2s), b2 = Double.parseDouble(b2s), c2 = Double.parseDouble(c2s);

            StringBuilder steps = new StringBuilder();
            String result;

            steps.append("System:\n");
            steps.append("  ").append(fmt(a1)).append("x + ").append(fmt(b1)).append("y = ").append(fmt(c1)).append("\n");
            steps.append("  ").append(fmt(a2)).append("x + ").append(fmt(b2)).append("y = ").append(fmt(c2)).append("\n\n");

            double D = a1 * b2 - a2 * b1;
            steps.append("Step 1: Determinant D = a₁b₂ − a₂b₁\n");
            steps.append("  D = (").append(fmt(a1)).append(")(").append(fmt(b2))
                 .append(") − (").append(fmt(a2)).append(")(").append(fmt(b1))
                 .append(") = ").append(fmt(D)).append("\n\n");

            if (Math.abs(D) < EPSILON) {
                result = "No unique solution (D = 0)";
                steps.append("D = 0 → system is dependent or inconsistent.\n");
                steps.append("No unique solution exists.");
            } else {
                double x = (c1 * b2 - c2 * b1) / D;
                double y = (a1 * c2 - a2 * c1) / D;

                steps.append("Step 2: x = (c₁b₂ − c₂b₁) / D\n");
                steps.append("  x = ((").append(fmt(c1)).append(")(").append(fmt(b2))
                     .append(") − (").append(fmt(c2)).append(")(").append(fmt(b1))
                     .append(")) / ").append(fmt(D)).append(" = ").append(fmt(x)).append("\n\n");

                steps.append("Step 3: y = (a₁c₂ − a₂c₁) / D\n");
                steps.append("  y = ((").append(fmt(a1)).append(")(").append(fmt(c2))
                     .append(") − (").append(fmt(a2)).append(")(").append(fmt(c1))
                     .append(")) / ").append(fmt(D)).append(" = ").append(fmt(y)).append("\n\n");

                steps.append("✓  x = ").append(fmt(x)).append(",  y = ").append(fmt(y));
                result = "x = " + fmt(x) + ",  y = " + fmt(y);
                MathHistoryManager.saveCalculation(this,
                    fmt(a1)+"x+"+fmt(b1)+"y="+fmt(c1)+", "+fmt(a2)+"x+"+fmt(b2)+"y="+fmt(c2),
                    result, "SYS");
            }

            if (tvSysResult != null) tvSysResult.setText(result);
            if (tvSysSteps != null) tvSysSteps.setText(steps.toString());

        } catch (NumberFormatException e) {
            if (tvSysResult != null) tvSysResult.setText("Invalid input");
            if (tvSysSteps != null) tvSysSteps.setText("");
        }
    }

    private void clearSystem() {
        EditText[] fields = {etSysA1, etSysB1, etSysC1, etSysA2, etSysB2, etSysC2};
        for (EditText et : fields) { if (et != null) et.setText(""); }
        if (tvSysResult != null) tvSysResult.setText("");
        if (tvSysSteps != null) tvSysSteps.setText("");
    }

    // ====================== HELPERS ======================

    private String getVal(EditText et, String def) {
        if (et == null) return def;
        String s = et.getText().toString().trim();
        return s.isEmpty() ? def : s;
    }

    private String fmt(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "−∞";
        if (v == Math.floor(v) && Math.abs(v) < 1e12) {
            return String.valueOf((long) v);
        }
        String s = String.format(Locale.US, "%.6g", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private void haptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
