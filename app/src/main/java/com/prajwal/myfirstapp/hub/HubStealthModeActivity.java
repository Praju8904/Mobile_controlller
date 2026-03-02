package com.prajwal.myfirstapp.hub;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Stealth Mode — disguised as a calculator.
 * When the user types the configured secret code and presses =, launches SmartFileHubActivity.
 */
public class HubStealthModeActivity extends AppCompatActivity {

    private static final String PREFS = "hub_settings";

    private TextView tvDisplay;
    private StringBuilder expression = new StringBuilder();
    private String lastResult = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(0, 0, 0, 0);
        setContentView(root);

        // Display
        LinearLayout displayArea = new LinearLayout(this);
        displayArea.setOrientation(LinearLayout.VERTICAL);
        displayArea.setPadding(24, 80, 24, 24);
        displayArea.setGravity(Gravity.BOTTOM | Gravity.END);
        displayArea.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f));
        displayArea.setBackgroundColor(Color.parseColor("#0F172A"));

        tvDisplay = new TextView(this);
        tvDisplay.setText("0");
        tvDisplay.setTextColor(Color.WHITE);
        tvDisplay.setTextSize(56);
        tvDisplay.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvDisplay.setGravity(Gravity.END);
        tvDisplay.setMaxLines(2);
        tvDisplay.setSingleLine(false);
        displayArea.addView(tvDisplay);
        root.addView(displayArea);

        // Keypad
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(5);
        grid.setBackgroundColor(Color.parseColor("#1E293B"));
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f));
        grid.setPadding(4, 4, 4, 4);

        String[][] buttons = {
                {"C", "±", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"0", ".", "⌫", "="}
        };

        for (String[] row : buttons) {
            for (String label : row) {
                Button btn = buildCalcButton(label);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = 0;
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                lp.setMargins(2, 2, 2, 2);
                btn.setLayoutParams(lp);
                grid.addView(btn);
            }
        }
        root.addView(grid);
    }

    private Button buildCalcButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(22);
        btn.setTypeface(null, Typeface.NORMAL);

        boolean isOperator = label.equals("÷") || label.equals("×") || label.equals("−") || label.equals("+") || label.equals("=");
        boolean isSpecial = label.equals("C") || label.equals("±") || label.equals("%") || label.equals("⌫");

        if (label.equals("=")) {
            btn.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#6366F1")); bg.setCornerRadius(16f);
            btn.setBackground(bg);
        } else if (isOperator) {
            btn.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F59E0B")); bg.setCornerRadius(16f);
            btn.setBackground(bg);
        } else if (isSpecial) {
            btn.setTextColor(Color.parseColor("#1E293B"));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#94A3B8")); bg.setCornerRadius(16f);
            btn.setBackground(bg);
        } else {
            btn.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#374151")); bg.setCornerRadius(16f);
            btn.setBackground(bg);
        }

        btn.setOnClickListener(v -> handleKey(label));
        return btn;
    }

    private void handleKey(String key) {
        switch (key) {
            case "C":
                expression.setLength(0);
                lastResult = "";
                tvDisplay.setText("0");
                break;
            case "⌫":
                if (expression.length() > 0) expression.deleteCharAt(expression.length() - 1);
                tvDisplay.setText(expression.length() > 0 ? expression.toString() : "0");
                break;
            case "=":
                String input = expression.toString();
                String secretCode = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString("stealth_code", "1337");
                if (input.equals(secretCode)) {
                    // Launch the hub
                    startActivity(new Intent(this, SmartFileHubActivity.class));
                    expression.setLength(0);
                    tvDisplay.setText("0");
                } else {
                    // Evaluate
                    String result = evaluate(input);
                    tvDisplay.setText(result);
                    expression.setLength(0);
                    expression.append(result);
                    lastResult = result;
                }
                break;
            case "±":
                if (expression.length() > 0) {
                    if (expression.charAt(0) == '-') expression.deleteCharAt(0);
                    else expression.insert(0, '-');
                    tvDisplay.setText(expression.toString());
                }
                break;
            default:
                // Map display symbols to actual operators
                String append = key.equals("÷") ? "/" : key.equals("×") ? "*" :
                        key.equals("−") ? "-" : key;
                expression.append(append);
                tvDisplay.setText(expression.toString()
                        .replace("/", "÷").replace("*", "×").replace("-", "−"));
                break;
        }
    }

    private String evaluate(String expr) {
        try {
            // Simple evaluation: handle +, -, *, /
            // Replace display chars
            expr = expr.replace("÷", "/").replace("×", "*").replace("−", "-");
            // Use javax.script if available, else simple parser
            double result = simpleEval(expr);
            if (result == (long) result) return String.valueOf((long) result);
            return String.format(java.util.Locale.getDefault(), "%.6f", result)
                    .replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (Exception e) {
            return "Error";
        }
    }

    private double simpleEval(String expr) {
        // Minimal left-to-right evaluator (ignores operator precedence for simplicity)
        expr = expr.trim();
        // Find last + or - (not in exponent)
        int idx = -1;
        for (int i = expr.length() - 1; i > 0; i--) {
            char c = expr.charAt(i);
            if ((c == '+' || c == '-') && i > 0) { idx = i; break; }
        }
        if (idx > 0) {
            double left = simpleEval(expr.substring(0, idx));
            double right = Double.parseDouble(expr.substring(idx + 1));
            return expr.charAt(idx) == '+' ? left + right : left - right;
        }
        // Find last * or /
        for (int i = expr.length() - 1; i > 0; i--) {
            char c = expr.charAt(i);
            if (c == '*' || c == '/') {
                double left = simpleEval(expr.substring(0, i));
                double right = Double.parseDouble(expr.substring(i + 1));
                return c == '*' ? left * right : left / right;
            }
        }
        return Double.parseDouble(expr);
    }
}
