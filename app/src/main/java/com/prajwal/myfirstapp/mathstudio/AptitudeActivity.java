package com.prajwal.myfirstapp.mathstudio;

import android.os.Bundle;
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
import java.math.BigInteger;

public class AptitudeActivity extends AppCompatActivity {

    private View panelPC, panelProb, panelInterest, panelPercent, panelTSD;
    private TextView tabPC, tabProb, tabInterest, tabPercent, tabTSD;

    // P&C state
    private boolean isPermutation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aptitude);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        initTabs();
        initPCTab();
        initProbTab();
        initInterestTab();
        initPercentTab();
        initTSDTab();

        showTab(0);
    }

    private void initTabs() {
        tabPC = findViewById(R.id.tabPC);
        tabProb = findViewById(R.id.tabProb);
        tabInterest = findViewById(R.id.tabInterest);
        tabPercent = findViewById(R.id.tabPercent);
        tabTSD = findViewById(R.id.tabTSD);
        panelPC = findViewById(R.id.panelPC);
        panelProb = findViewById(R.id.panelProb);
        panelInterest = findViewById(R.id.panelInterest);
        panelPercent = findViewById(R.id.panelPercent);
        panelTSD = findViewById(R.id.panelTSD);

        if (tabPC != null) tabPC.setOnClickListener(v -> showTab(0));
        if (tabProb != null) tabProb.setOnClickListener(v -> showTab(1));
        if (tabInterest != null) tabInterest.setOnClickListener(v -> showTab(2));
        if (tabPercent != null) tabPercent.setOnClickListener(v -> showTab(3));
        if (tabTSD != null) tabTSD.setOnClickListener(v -> showTab(4));
    }

    private void showTab(int idx) {
        if (panelPC != null) panelPC.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (panelProb != null) panelProb.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        if (panelInterest != null) panelInterest.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if (panelPercent != null) panelPercent.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        if (panelTSD != null) panelTSD.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);

        int active = 0xFFA5B4FC;
        int inactive = 0xFF6B7280;
        if (tabPC != null) tabPC.setTextColor(idx == 0 ? active : inactive);
        if (tabProb != null) tabProb.setTextColor(idx == 1 ? active : inactive);
        if (tabInterest != null) tabInterest.setTextColor(idx == 2 ? active : inactive);
        if (tabPercent != null) tabPercent.setTextColor(idx == 3 ? active : inactive);
        if (tabTSD != null) tabTSD.setTextColor(idx == 4 ? active : inactive);
    }

    // ==================== P&C TAB ====================

    private void initPCTab() {
        Button btnNPR = findViewById(R.id.btnNPR);
        Button btnNCR = findViewById(R.id.btnNCR);
        Button btnCalc = findViewById(R.id.btnCalcPC);

        if (btnNPR != null) btnNPR.setOnClickListener(v -> {
            isPermutation = true;
            btnNPR.setBackgroundColor(0xFFA5B4FC);
            btnNPR.setTextColor(0xFF0A0E21);
            if (btnNCR != null) { btnNCR.setBackgroundColor(0xFF1A1F3A); btnNCR.setTextColor(0xFFA5B4FC); }
        });
        if (btnNCR != null) btnNCR.setOnClickListener(v -> {
            isPermutation = false;
            btnNCR.setBackgroundColor(0xFFA5B4FC);
            btnNCR.setTextColor(0xFF0A0E21);
            if (btnNPR != null) { btnNPR.setBackgroundColor(0xFF1A1F3A); btnNPR.setTextColor(0xFFA5B4FC); }
        });
        if (btnCalc != null) btnCalc.setOnClickListener(v -> calculatePC());
    }

    private void calculatePC() {
        EditText etN = findViewById(R.id.etPCn);
        EditText etR = findViewById(R.id.etPCr);
        TextView tvResult = findViewById(R.id.tvPCResult);
        TextView tvFormula = findViewById(R.id.tvPCFormula);
        if (etN == null || etR == null || tvResult == null) return;

        String nStr = etN.getText().toString().trim();
        String rStr = etR.getText().toString().trim();
        if (nStr.isEmpty() || rStr.isEmpty()) { Toast.makeText(this, "Enter both n and r", Toast.LENGTH_SHORT).show(); return; }
        try {
            int n = Integer.parseInt(nStr);
            int r = Integer.parseInt(rStr);
            if (n < 0 || r < 0 || r > n) { tvResult.setText("Invalid: need 0 ≤ r ≤ n"); return; }
            if (isPermutation) {
                BigInteger result = permutation(n, r);
                tvResult.setText("nPr = " + result.toString());
                tvFormula.setText("nPr = n! / (n-r)! = " + n + "! / " + (n - r) + "!");
            } else {
                BigInteger result = combination(n, r);
                tvResult.setText("nCr = " + result.toString());
                tvFormula.setText("nCr = n! / (r! × (n-r)!) = " + n + "! / (" + r + "! × " + (n-r) + "!)");
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    private BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) result = result.multiply(BigInteger.valueOf(i));
        return result;
    }

    private BigInteger permutation(int n, int r) {
        return factorial(n).divide(factorial(n - r));
    }

    private BigInteger combination(int n, int r) {
        return factorial(n).divide(factorial(r).multiply(factorial(n - r)));
    }

    // ==================== PROBABILITY TAB ====================

    private void initProbTab() {
        Button btnCalc = findViewById(R.id.btnCalcProb);
        if (btnCalc != null) btnCalc.setOnClickListener(v -> calculateProbability());
    }

    private void calculateProbability() {
        EditText etA = findViewById(R.id.etProbA);
        EditText etB = findViewById(R.id.etProbB);
        TextView tvResult = findViewById(R.id.tvProbResult);
        if (etA == null || etB == null || tvResult == null) return;
        try {
            double pA = Double.parseDouble(etA.getText().toString().trim());
            double pB = Double.parseDouble(etB.getText().toString().trim());
            if (pA < 0 || pA > 1 || pB < 0 || pB > 1) {
                tvResult.setText("Probabilities must be between 0 and 1"); return;
            }
            double pAandB = pA * pB;       // independent events
            double pAorB = pA + pB - pAandB;
            double pNotA = 1 - pA;
            double pNotB = 1 - pB;
            tvResult.setText(
                String.format("P(A)       = %.4f\n", pA) +
                String.format("P(B)       = %.4f\n", pB) +
                String.format("P(A∩B)     = %.4f  (A and B)\n", pAandB) +
                String.format("P(A∪B)     = %.4f  (A or B)\n", pAorB) +
                String.format("P(A')      = %.4f  (not A)\n", pNotA) +
                String.format("P(B')      = %.4f  (not B)", pNotB)
            );
        } catch (NumberFormatException e) {
            tvResult.setText("Invalid input");
        }
    }

    // ==================== INTEREST TAB ====================

    private void initInterestTab() {
        Spinner spinFreq = findViewById(R.id.spinnerFreq);
        if (spinFreq != null) {
            String[] freqs = {"Annually (n=1)", "Semi-annually (n=2)", "Quarterly (n=4)",
                              "Monthly (n=12)", "Daily (n=365)"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, freqs);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinFreq.setAdapter(adapter);
        }
        Button btnCalc = findViewById(R.id.btnCalcInterest);
        if (btnCalc != null) btnCalc.setOnClickListener(v -> calculateInterest());
    }

    private void calculateInterest() {
        EditText etP = findViewById(R.id.etPrincipal);
        EditText etR = findViewById(R.id.etRate);
        EditText etT = findViewById(R.id.etTime);
        Spinner spinFreq = findViewById(R.id.spinnerFreq);
        TextView tvResult = findViewById(R.id.tvInterestResult);
        if (etP == null || etR == null || etT == null || tvResult == null) return;
        try {
            double P = Double.parseDouble(etP.getText().toString().trim());
            double R = Double.parseDouble(etR.getText().toString().trim()) / 100.0;
            double T = Double.parseDouble(etT.getText().toString().trim());
            int[] freqValues = {1, 2, 4, 12, 365};
            int n = freqValues[spinFreq != null ? spinFreq.getSelectedItemPosition() : 0];

            // Compound
            double compoundAmount = P * Math.pow(1 + R / n, n * T);
            double compoundInterest = compoundAmount - P;

            // Simple
            double simpleInterest = P * R * T;
            double simpleAmount = P + simpleInterest;

            tvResult.setText(
                String.format("Principal:          %.2f\n", P) +
                String.format("Rate:               %.2f%% p.a.\n", R * 100) +
                String.format("Time:               %.2f years\n\n", T) +
                String.format("Compound Interest:  %.2f\n", compoundInterest) +
                String.format("Amount (CI):        %.2f\n\n", compoundAmount) +
                String.format("Simple Interest:    %.2f\n", simpleInterest) +
                String.format("Amount (SI):        %.2f\n\n", simpleAmount) +
                String.format("Difference (CI-SI): %.2f", compoundInterest - simpleInterest)
            );
        } catch (NumberFormatException e) {
            tvResult.setText("Invalid input");
        }
    }

    // ==================== PERCENTAGE TAB ====================

    private void initPercentTab() {
        Button btnPctOfY = findViewById(R.id.btnPctOfY);
        Button btnPctChange = findViewById(R.id.btnPctChange);
        Button btnProfitLoss = findViewById(R.id.btnProfitLoss);

        if (btnPctOfY != null) btnPctOfY.setOnClickListener(v -> calcPctOfY());
        if (btnPctChange != null) btnPctChange.setOnClickListener(v -> calcPctChange());
        if (btnProfitLoss != null) btnProfitLoss.setOnClickListener(v -> calcProfitLoss());
    }

    private void calcPctOfY() {
        EditText etX = findViewById(R.id.etPctX);
        EditText etY = findViewById(R.id.etPctY);
        TextView tv = findViewById(R.id.tvPctOfYResult);
        if (etX == null || etY == null || tv == null) return;
        try {
            double x = Double.parseDouble(etX.getText().toString().trim());
            double y = Double.parseDouble(etY.getText().toString().trim());
            double result = x / 100.0 * y;
            tv.setText(String.format("%.4g%% of %.4g = %.4g", x, y, result));
        } catch (NumberFormatException e) { tv.setText("Invalid input"); }
    }

    private void calcPctChange() {
        EditText etFrom = findViewById(R.id.etPctFrom);
        EditText etTo = findViewById(R.id.etPctTo);
        TextView tv = findViewById(R.id.tvPctChangeResult);
        if (etFrom == null || etTo == null || tv == null) return;
        try {
            double from = Double.parseDouble(etFrom.getText().toString().trim());
            double to = Double.parseDouble(etTo.getText().toString().trim());
            if (from == 0) { tv.setText("Cannot divide by zero"); return; }
            double change = (to - from) / from * 100.0;
            tv.setText(String.format("%.4g%%  (%s)", change, change >= 0 ? "Increase" : "Decrease"));
        } catch (NumberFormatException e) { tv.setText("Invalid input"); }
    }

    private void calcProfitLoss() {
        EditText etCost = findViewById(R.id.etCostPrice);
        EditText etSell = findViewById(R.id.etSellingPrice);
        TextView tv = findViewById(R.id.tvProfitLossResult);
        if (etCost == null || etSell == null || tv == null) return;
        try {
            double cost = Double.parseDouble(etCost.getText().toString().trim());
            double sell = Double.parseDouble(etSell.getText().toString().trim());
            double diff = sell - cost;
            double pct = cost == 0 ? 0 : diff / cost * 100;
            String label = diff >= 0 ? "Profit" : "Loss";
            tv.setText(String.format("%s: %.4g  (%.4g%%)", label, Math.abs(diff), Math.abs(pct)));
        } catch (NumberFormatException e) { tv.setText("Invalid input"); }
    }

    // ==================== TSD TAB ====================

    private void initTSDTab() {
        Spinner spinType = findViewById(R.id.spinnerTSDType);
        if (spinType != null) {
            String[] types = {"Find Time (D/S)", "Find Distance (S×T)", "Find Speed (D/T)",
                              "Average Speed (2 speeds)", "Relative Speed (same dir)", "Relative Speed (opp dir)"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, types);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinType.setAdapter(adapter);
        }
        Button btnCalc = findViewById(R.id.btnCalcTSD);
        if (btnCalc != null) btnCalc.setOnClickListener(v -> calculateTSD());
    }

    private void calculateTSD() {
        Spinner spinType = findViewById(R.id.spinnerTSDType);
        EditText etSpeed = findViewById(R.id.etTSDSpeed);
        EditText etDist = findViewById(R.id.etTSDDistance);
        EditText etTime = findViewById(R.id.etTSDTime);
        TextView tvResult = findViewById(R.id.tvTSDResult);
        if (tvResult == null) return;

        int type = spinType != null ? spinType.getSelectedItemPosition() : 0;
        String speedStr = etSpeed != null ? etSpeed.getText().toString().trim() : "";
        String distStr = etDist != null ? etDist.getText().toString().trim() : "";
        String timeStr = etTime != null ? etTime.getText().toString().trim() : "";

        try {
            switch (type) {
                case 0: { // Time = D/S
                    double d = Double.parseDouble(distStr);
                    double s = Double.parseDouble(speedStr);
                    tvResult.setText(String.format("Time = D/S = %.4g / %.4g = %.4g hours\nFormula: T = D / S", d, s, d / s));
                    break;
                }
                case 1: { // Distance = S*T
                    double s = Double.parseDouble(speedStr);
                    double t = Double.parseDouble(timeStr);
                    tvResult.setText(String.format("Distance = S×T = %.4g × %.4g = %.4g km\nFormula: D = S × T", s, t, s * t));
                    break;
                }
                case 2: { // Speed = D/T
                    double d = Double.parseDouble(distStr);
                    double t = Double.parseDouble(timeStr);
                    tvResult.setText(String.format("Speed = D/T = %.4g / %.4g = %.4g km/h\nFormula: S = D / T", d, t, d / t));
                    break;
                }
                case 3: { // Average speed: 2*s1*s2/(s1+s2)
                    double s1 = Double.parseDouble(speedStr);
                    double s2 = Double.parseDouble(distStr); // reuse distance field for s2
                    double avg = 2 * s1 * s2 / (s1 + s2);
                    tvResult.setText(String.format("Avg Speed = 2×S1×S2/(S1+S2)\n= 2×%.4g×%.4g/(%.4g+%.4g)\n= %.4g km/h", s1, s2, s1, s2, avg));
                    break;
                }
                case 4: { // Relative speed same dir
                    double s1 = Double.parseDouble(speedStr);
                    double s2 = Double.parseDouble(distStr);
                    tvResult.setText(String.format("Relative Speed (same dir) = |S1-S2|\n= |%.4g - %.4g| = %.4g km/h", s1, s2, Math.abs(s1 - s2)));
                    break;
                }
                case 5: { // Relative speed opposite dir
                    double s1 = Double.parseDouble(speedStr);
                    double s2 = Double.parseDouble(distStr);
                    tvResult.setText(String.format("Relative Speed (opp dir) = S1+S2\n= %.4g + %.4g = %.4g km/h", s1, s2, s1 + s2));
                    break;
                }
            }
        } catch (NumberFormatException e) {
            tvResult.setText("Please fill the required fields.\n(For Avg/Relative: use Speed and Distance fields for S1 and S2)");
        }
    }
}
