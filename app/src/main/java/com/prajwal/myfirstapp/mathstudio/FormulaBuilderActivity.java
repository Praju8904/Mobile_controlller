package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormulaBuilderActivity extends AppCompatActivity {

    private static final String PREFS_KEY = "math_formulas";
    private static final String PREFS_NAME = "FormulaBuilderPrefs";

    private LinearLayout formulaListContainer;
    private View panelList, panelEditor, panelSolver;

    // Current formulas
    private List<JSONObject> formulas = new ArrayList<>();

    // Currently editing/solving index (-1 = new)
    private int editingIndex = -1;
    private JSONObject solvingFormula = null;

    // Variable input fields for solver
    private Map<String, EditText> varInputs = new LinkedHashMap<>();

    // Pre-loaded formulas
    private static final String[][] BUILTIN_FORMULAS = {
        {"Velocity", "v = u + a * t", "Physics"},
        {"Einstein Energy", "E = m * c^2", "Physics"},
        {"Newton 2nd Law", "F = m * a", "Physics"},
        {"Circle Area", "A = 3.14159 * r^2", "Math"},
        {"Sphere Volume", "V = (4.0/3.0) * 3.14159 * r^3", "Math"},
        {"Electrical Power", "P = I * V", "Physics"},
        {"Ohm's Law (Current)", "I = V / R", "Physics"},
        {"Simple Interest", "SI = P * R * T / 100", "Finance"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_formula_builder);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        formulaListContainer = findViewById(R.id.formulaListContainer);
        panelList = findViewById(R.id.panelList);
        panelEditor = findViewById(R.id.panelEditor);
        panelSolver = findViewById(R.id.panelSolver);

        TextView btnAdd = findViewById(R.id.btnAddFormula);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> openEditor(-1));

        Button btnSave = findViewById(R.id.btnSaveFormula);
        if (btnSave != null) btnSave.setOnClickListener(v -> saveFormula());

        Button btnCancel = findViewById(R.id.btnCancelEditor);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> showList());

        Button btnBackToList = findViewById(R.id.btnBackToList);
        if (btnBackToList != null) btnBackToList.setOnClickListener(v -> showList());

        Button btnSolve = findViewById(R.id.btnSolve);
        if (btnSolve != null) btnSolve.setOnClickListener(v -> solveFormula());

        loadFormulas();
        if (formulas.isEmpty()) seedBuiltinFormulas();
        renderFormulaList();
        showList();
    }

    private void showList() {
        panelList.setVisibility(View.VISIBLE);
        panelEditor.setVisibility(View.GONE);
        panelSolver.setVisibility(View.GONE);
    }

    private void showEditor() {
        panelList.setVisibility(View.GONE);
        panelEditor.setVisibility(View.VISIBLE);
        panelSolver.setVisibility(View.GONE);
    }

    private void showSolver() {
        panelList.setVisibility(View.GONE);
        panelEditor.setVisibility(View.GONE);
        panelSolver.setVisibility(View.VISIBLE);
    }

    // ==================== FORMULA LIST ====================

    private void renderFormulaList() {
        if (formulaListContainer == null) return;
        formulaListContainer.removeAllViews();

        if (formulas.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No formulas yet. Tap + New to add one.");
            tv.setTextColor(0xFF6B7280);
            tv.setTextSize(14);
            formulaListContainer.addView(tv);
            return;
        }

        int dp8 = dp(8);
        int dp12 = dp(12);
        for (int i = 0; i < formulas.size(); i++) {
            final int idx = i;
            JSONObject f = formulas.get(i);
            String name = f.optString("name", "Unnamed");
            String expr = f.optString("formula", "");
            String cat = f.optString("category", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF1A1F3A);
            card.setPadding(dp12, dp12, dp12, dp12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp8);
            card.setLayoutParams(lp);

            // Category chip
            TextView tvCat = new TextView(this);
            tvCat.setText(cat.isEmpty() ? "General" : cat);
            tvCat.setTextColor(0xFFA5B4FC);
            tvCat.setTextSize(11);
            card.addView(tvCat);

            // Formula name
            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(0xFFFFFFFF);
            tvName.setTextSize(16);
            tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            card.addView(tvName);

            // Formula expression
            TextView tvExpr = new TextView(this);
            tvExpr.setText(expr);
            tvExpr.setTextColor(0xFF00FF41);
            tvExpr.setTextSize(14);
            tvExpr.setTypeface(android.graphics.Typeface.MONOSPACE);
            card.addView(tvExpr);

            // Buttons row
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnRowLp.setMargins(0, dp8, 0, 0);
            btnRow.setLayoutParams(btnRowLp);

            Button btnUse = new Button(this);
            btnUse.setText("Solve");
            btnUse.setTextColor(0xFF0A0E21);
            btnUse.setBackgroundColor(0xFFA5B4FC);
            btnUse.setTextSize(13);
            LinearLayout.LayoutParams useLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            useLp.weight = 1;
            useLp.setMarginEnd(dp8);
            btnUse.setLayoutParams(useLp);
            btnUse.setOnClickListener(v -> openSolver(idx));
            btnRow.addView(btnUse);

            Button btnEdit = new Button(this);
            btnEdit.setText("Edit");
            btnEdit.setTextColor(0xFFA5B4FC);
            btnEdit.setBackgroundColor(0xFF252A4A);
            btnEdit.setTextSize(13);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            editLp.weight = 1;
            editLp.setMarginEnd(dp8);
            btnEdit.setLayoutParams(editLp);
            btnEdit.setOnClickListener(v -> openEditor(idx));
            btnRow.addView(btnEdit);

            Button btnDel = new Button(this);
            btnDel.setText("Delete");
            btnDel.setTextColor(0xFFF87171);
            btnDel.setBackgroundColor(0xFF2A1A1A);
            btnDel.setTextSize(13);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            delLp.weight = 1;
            btnDel.setLayoutParams(delLp);
            btnDel.setOnClickListener(v -> deleteFormula(idx));
            btnRow.addView(btnDel);

            card.addView(btnRow);
            formulaListContainer.addView(card);
        }
    }

    private void deleteFormula(int idx) {
        formulas.remove(idx);
        saveFormulas();
        renderFormulaList();
    }

    // ==================== EDITOR ====================

    private void openEditor(int idx) {
        editingIndex = idx;
        EditText etName = findViewById(R.id.etFormulaName);
        EditText etExpr = findViewById(R.id.etFormulaExpr);
        EditText etCat = findViewById(R.id.etFormulaCategory);
        if (etName == null || etExpr == null || etCat == null) return;

        if (idx >= 0 && idx < formulas.size()) {
            JSONObject f = formulas.get(idx);
            etName.setText(f.optString("name", ""));
            etExpr.setText(f.optString("formula", ""));
            etCat.setText(f.optString("category", ""));
        } else {
            etName.setText("");
            etExpr.setText("");
            etCat.setText("");
        }
        showEditor();
    }

    private void saveFormula() {
        EditText etName = findViewById(R.id.etFormulaName);
        EditText etExpr = findViewById(R.id.etFormulaExpr);
        EditText etCat = findViewById(R.id.etFormulaCategory);
        if (etName == null || etExpr == null || etCat == null) return;

        String name = etName.getText().toString().trim();
        String expr = etExpr.getText().toString().trim();
        String cat = etCat.getText().toString().trim();

        if (name.isEmpty()) { Toast.makeText(this, "Enter a formula name", Toast.LENGTH_SHORT).show(); return; }
        if (expr.isEmpty()) { Toast.makeText(this, "Enter a formula expression", Toast.LENGTH_SHORT).show(); return; }

        try {
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("formula", expr);
            f.put("category", cat.isEmpty() ? "General" : cat);
            f.put("variables", new JSONArray(detectVariables(expr)));

            if (editingIndex >= 0 && editingIndex < formulas.size()) {
                formulas.set(editingIndex, f);
            } else {
                formulas.add(f);
            }
            saveFormulas();
            renderFormulaList();
            showList();
        } catch (JSONException e) {
            Toast.makeText(this, "Error saving formula", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== SOLVER ====================

    private void openSolver(int idx) {
        solvingFormula = formulas.get(idx);
        String name = solvingFormula.optString("name", "Formula");
        String expr = solvingFormula.optString("formula", "");

        TextView tvTitle = findViewById(R.id.tvSolverTitle);
        if (tvTitle != null) tvTitle.setText("Solve: " + name);
        TextView tvFormula = findViewById(R.id.tvSolverFormula);
        if (tvFormula != null) tvFormula.setText(expr);

        List<String> vars = detectVariables(expr);

        // Populate solve-for spinner
        Spinner spinner = findViewById(R.id.spinnerSolveFor);
        if (spinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, vars);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        // Build variable input fields
        LinearLayout container = findViewById(R.id.varInputsContainer);
        if (container != null) {
            container.removeAllViews();
            varInputs.clear();
            int dp8 = dp(8);
            for (String var : vars) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp8);
                row.setLayoutParams(rowLp);

                TextView tvLabel = new TextView(this);
                tvLabel.setText(var + " = ");
                tvLabel.setTextColor(0xFFA5B4FC);
                tvLabel.setTextSize(15);
                tvLabel.setMinWidth(dp(40));
                tvLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
                row.addView(tvLabel);

                EditText et = new EditText(this);
                et.setHint("value");
                et.setTextColor(0xFFFFFFFF);
                et.setHintTextColor(0xFF4B5563);
                et.setBackgroundColor(0xFF1A1F3A);
                et.setPadding(dp(8), dp(8), dp(8), dp(8));
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
                etLp.weight = 1;
                et.setLayoutParams(etLp);
                row.addView(et);

                varInputs.put(var, et);
                container.addView(row);
            }
        }

        TextView tvResult = findViewById(R.id.tvSolverResult);
        if (tvResult != null) tvResult.setText("—");
        TextView tvSub = findViewById(R.id.tvSolverSubstitution);
        if (tvSub != null) tvSub.setText("");

        showSolver();
    }

    private void solveFormula() {
        if (solvingFormula == null) return;
        Spinner spinner = findViewById(R.id.spinnerSolveFor);
        TextView tvResult = findViewById(R.id.tvSolverResult);
        TextView tvSub = findViewById(R.id.tvSolverSubstitution);
        if (tvResult == null) return;

        String formula = solvingFormula.optString("formula", "");
        String solveFor = spinner != null ? (String) spinner.getSelectedItem() : null;

        // Collect variable values
        Map<String, Double> values = new LinkedHashMap<>();
        StringBuilder substSb = new StringBuilder("Substituting:\n");
        boolean hasError = false;
        for (Map.Entry<String, EditText> entry : varInputs.entrySet()) {
            String var = entry.getKey();
            if (var.equals(solveFor)) continue;
            String valStr = entry.getValue().getText().toString().trim();
            if (valStr.isEmpty()) {
                tvResult.setText("Enter value for " + var);
                return;
            }
            try {
                double val = Double.parseDouble(valStr);
                values.put(var, val);
                substSb.append(var).append(" = ").append(valStr).append("\n");
            } catch (NumberFormatException e) {
                tvResult.setText("Invalid value for " + var);
                return;
            }
        }

        // Parse and evaluate the RHS with substituted values
        // Formula format: "var = expression" — find the expression side
        String expr = formula;
        // Try to identify the RHS (after the = sign)
        if (formula.contains("=")) {
            String[] parts = formula.split("=", 2);
            String lhsVar = parts[0].trim();
            String rhs = parts[1].trim();
            // Substitute values into RHS
            for (Map.Entry<String, Double> e : values.entrySet()) {
                rhs = rhs.replaceAll("\\b" + Pattern.quote(e.getKey()) + "\\b",
                    "(" + e.getValue() + ")");
            }
            try {
                double result = ExpressionEngine.evaluate(rhs);
                String formatted = formatResult(result);
                tvResult.setText(lhsVar + " = " + formatted);
                substSb.append("Result: ").append(lhsVar).append(" = ").append(formatted);
                if (tvSub != null) tvSub.setText(substSb.toString());
            } catch (Exception e) {
                tvResult.setText("Evaluation error: " + e.getMessage());
            }
        } else {
            tvResult.setText("Formula must contain '='");
        }
    }

    private String formatResult(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return "∞";
        if (val == Math.floor(val) && Math.abs(val) < 1e12) return String.valueOf((long) val);
        String s = String.format("%.8g", val);
        if (s.contains(".") && !s.contains("e")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    // ==================== PERSISTENCE ====================

    private void loadFormulas() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(PREFS_KEY, "[]");
        formulas.clear();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                formulas.add(arr.getJSONObject(i));
            }
        } catch (JSONException e) {
            formulas.clear();
        }
    }

    private void saveFormulas() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (JSONObject f : formulas) arr.put(f);
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply();
    }

    private void seedBuiltinFormulas() {
        for (String[] f : BUILTIN_FORMULAS) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", f[0]);
                obj.put("formula", f[1]);
                obj.put("category", f[2]);
                obj.put("variables", detectVariables(f[1]).toString());
                formulas.add(obj);
            } catch (JSONException ignored) {}
        }
        saveFormulas();
    }

    // Detect single-letter variables in expression (excluding e for euler's number)
    private List<String> detectVariables(String expr) {
        List<String> vars = new ArrayList<>();
        Pattern p = Pattern.compile("(?<![a-zA-Z])([a-zA-Z])(?![a-zA-Z])");
        Matcher m = p.matcher(expr);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        while (m.find()) {
            String v = m.group(1);
            // Skip 'e' used in scientific notation and common constants
            if (!v.equals("e")) seen.add(v);
        }
        vars.addAll(seen);
        return vars;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
