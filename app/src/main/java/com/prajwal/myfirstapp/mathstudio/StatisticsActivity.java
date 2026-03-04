package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    private List<Double> data = new ArrayList<>();
    private LinearLayout chipsContainer;
    private EditText etDataInput;
    private HistogramView histogramView;

    private TextView tvCount, tvSum, tvMean, tvMedian, tvStdDev, tvVariance,
                     tvMin, tvMax, tvRange, tvIQR, tvQ1, tvQ3, tvMode;
    private ScrollView statsPanel;
    private FrameLayout chartPanel;
    private ScrollView regressionPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        etDataInput = findViewById(R.id.etDataInput);
        chipsContainer = findViewById(R.id.chipsContainer);
        statsPanel = findViewById(R.id.statsPanel);
        chartPanel = findViewById(R.id.chartPanel);
        regressionPanel = findViewById(R.id.regressionPanel);
        histogramView = findViewById(R.id.histogramView);

        tvCount = findViewById(R.id.tvCount);
        tvSum = findViewById(R.id.tvSum);
        tvMean = findViewById(R.id.tvMean);
        tvMedian = findViewById(R.id.tvMedian);
        tvStdDev = findViewById(R.id.tvStdDev);
        tvVariance = findViewById(R.id.tvVariance);
        tvMin = findViewById(R.id.tvMin);
        tvMax = findViewById(R.id.tvMax);
        tvRange = findViewById(R.id.tvRange);
        tvIQR = findViewById(R.id.tvIQR);
        tvQ1 = findViewById(R.id.tvQ1);
        tvQ3 = findViewById(R.id.tvQ3);
        tvMode = findViewById(R.id.tvMode);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnAdd = findViewById(R.id.btnAddData);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> addData());

        View btnClear = findViewById(R.id.btnClearData);
        if (btnClear != null) btnClear.setOnClickListener(v -> clearData());

        setupTabs();
        setupRegression();
    }

    private void addData() {
        if (etDataInput == null) return;
        String input = etDataInput.getText().toString().trim();
        if (input.isEmpty()) return;
        String[] parts = input.split(",");
        for (String part : parts) {
            try {
                double val = Double.parseDouble(part.trim());
                data.add(val);
                addChip(val);
            } catch (NumberFormatException ignored) {}
        }
        etDataInput.setText("");
        updateStats();
    }

    private void addChip(double val) {
        if (chipsContainer == null) return;
        TextView chip = new TextView(this);
        chip.setText(formatNum(val));
        chip.setTextColor(0xFFA5B4FC);
        chip.setTextSize(13f);
        int dp8 = dp(8), dp12 = dp(12);
        chip.setPadding(dp12, dp8 / 2, dp12, dp8 / 2);
        chip.setBackground(getDrawable(R.drawable.math_recent_pill_bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        chip.setLayoutParams(lp);
        chip.setOnLongClickListener(v -> {
            data.remove(Double.valueOf(val));
            chipsContainer.removeView(chip);
            updateStats();
            return true;
        });
        chipsContainer.addView(chip);
    }

    private void clearData() {
        data.clear();
        if (chipsContainer != null) chipsContainer.removeAllViews();
        updateStats();
    }

    private void updateStats() {
        if (data.isEmpty()) {
            setText(tvCount, "0"); setText(tvSum, "0");
            setText(tvMean, "—"); setText(tvMedian, "—");
            setText(tvStdDev, "—"); setText(tvVariance, "—");
            setText(tvMin, "—"); setText(tvMax, "—");
            setText(tvRange, "—"); setText(tvIQR, "—");
            setText(tvQ1, "—"); setText(tvQ3, "—");
            setText(tvMode, "—");
            return;
        }

        int n = data.size();
        double sum = 0;
        for (double d : data) sum += d;
        double mean = sum / n;

        List<Double> sorted = new ArrayList<>(data);
        Collections.sort(sorted);
        double min = sorted.get(0);
        double max = sorted.get(n - 1);
        double median = percentile(sorted, 50);
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;
        double range = max - min;

        double variance = 0;
        for (double d : data) variance += (d - mean) * (d - mean);
        variance /= n;
        double stdDev = Math.sqrt(variance);

        String mode = computeMode(data);

        setText(tvCount, String.valueOf(n));
        setText(tvSum, formatNum(sum));
        setText(tvMean, formatNum(mean));
        setText(tvMedian, formatNum(median));
        setText(tvStdDev, formatNum(stdDev));
        setText(tvVariance, formatNum(variance));
        setText(tvMin, formatNum(min));
        setText(tvMax, formatNum(max));
        setText(tvRange, formatNum(range));
        setText(tvIQR, formatNum(iqr));
        setText(tvQ1, formatNum(q1));
        setText(tvQ3, formatNum(q3));
        setText(tvMode, mode);

        if (histogramView != null) {
            histogramView.setData(data);
            histogramView.invalidate();
        }
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        double pos = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted.get(lo);
        return sorted.get(lo) * (hi - pos) + sorted.get(hi) * (pos - lo);
    }

    private String computeMode(List<Double> data) {
        Map<Double, Integer> freq = new HashMap<>();
        for (double d : data) freq.put(d, freq.getOrDefault(d, 0) + 1);
        int maxFreq = Collections.max(freq.values());
        if (maxFreq == 1) return "none";
        List<Double> modes = new ArrayList<>();
        for (Map.Entry<Double, Integer> e : freq.entrySet())
            if (e.getValue() == maxFreq) modes.add(e.getKey());
        Collections.sort(modes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatNum(modes.get(i)));
        }
        return sb.toString();
    }

    private void setupTabs() {
        TextView tabStats = findViewById(R.id.tabStats);
        TextView tabChart = findViewById(R.id.tabChart);
        TextView tabRegression = findViewById(R.id.tabRegression);
        if (tabStats != null) tabStats.setOnClickListener(v -> { showTab(0); updateTabUI(tabStats, tabChart, tabRegression, 0); });
        if (tabChart != null) tabChart.setOnClickListener(v -> { showTab(1); updateTabUI(tabStats, tabChart, tabRegression, 1); });
        if (tabRegression != null) tabRegression.setOnClickListener(v -> { showTab(2); updateTabUI(tabStats, tabChart, tabRegression, 2); });
    }

    private void showTab(int tab) {
        if (statsPanel != null) statsPanel.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        if (chartPanel != null) chartPanel.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        if (regressionPanel != null) regressionPanel.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateTabUI(TextView t0, TextView t1, TextView t2, int active) {
        int activeRes = R.drawable.math_deg_btn_active_bg;
        int inactiveRes = R.drawable.math_deg_btn_inactive_bg;
        if (t0 != null) t0.setBackground(getDrawable(active == 0 ? activeRes : inactiveRes));
        if (t1 != null) t1.setBackground(getDrawable(active == 1 ? activeRes : inactiveRes));
        if (t2 != null) t2.setBackground(getDrawable(active == 2 ? activeRes : inactiveRes));
    }

    private void setupRegression() {
        View btnRegress = findViewById(R.id.btnRegress);
        if (btnRegress != null) btnRegress.setOnClickListener(v -> computeRegression());
    }

    private void computeRegression() {
        EditText etX = findViewById(R.id.etXValues);
        EditText etY = findViewById(R.id.etYValues);
        TextView tvResult = findViewById(R.id.tvRegressionResult);
        if (etX == null || etY == null || tvResult == null) return;
        try {
            String[] xs = etX.getText().toString().split(",");
            String[] ys = etY.getText().toString().split(",");
            if (xs.length != ys.length || xs.length < 2) {
                tvResult.setText("Need equal number of x and y values (min 2)");
                return;
            }
            int n = xs.length;
            double[] x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = Double.parseDouble(xs[i].trim());
                y[i] = Double.parseDouble(ys[i].trim());
            }
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += x[i]; sumY += y[i];
                sumXY += x[i] * y[i]; sumX2 += x[i] * x[i];
            }
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            double intercept = (sumY - slope * sumX) / n;
            double meanY = sumY / n;
            double ssTot = 0, ssRes = 0;
            for (int i = 0; i < n; i++) {
                ssTot += (y[i] - meanY) * (y[i] - meanY);
                double yhat = slope * x[i] + intercept;
                ssRes += (y[i] - yhat) * (y[i] - yhat);
            }
            double r2 = 1 - ssRes / ssTot;
            String result = String.format(Locale.US,
                "Linear Regression:\n\ny = %.4fx + %.4f\n\nSlope (m): %.6f\nIntercept (b): %.6f\nR²: %.6f\n\n%s",
                slope, intercept, slope, intercept, r2,
                r2 >= 0.9 ? "Strong fit ✓" : r2 >= 0.7 ? "Moderate fit" : "Weak fit ✗");
            tvResult.setText(result);
        } catch (Exception e) {
            if (tvResult != null) tvResult.setText("Invalid input: " + e.getMessage());
        }
    }

    private void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String formatNum(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e10) return String.valueOf((long) v);
        return String.format(Locale.US, "%.4f", v);
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    public static class HistogramView extends View {
        private List<Double> data = new ArrayList<>();
        private final Paint barPaint = new Paint();
        private final Paint bgPaint = new Paint();
        private final Paint textPaint = new Paint();

        public HistogramView(Context context) { super(context); init(); }
        public HistogramView(Context context, android.util.AttributeSet attrs) { super(context, attrs); init(); }

        private void init() {
            barPaint.setColor(0xFF4F46E5);
            barPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xFF0A0F1E);
            bgPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(0xFF6B7280);
            textPaint.setTextSize(24f);
            textPaint.setAntiAlias(true);
        }

        public void setData(List<Double> data) {
            this.data = new ArrayList<>(data);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, bgPaint);
            if (data == null || data.size() < 2) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Add data to see histogram", w / 2f, h / 2f, textPaint);
                return;
            }

            List<Double> sorted = new ArrayList<>(data);
            Collections.sort(sorted);
            double min = sorted.get(0), max = sorted.get(sorted.size() - 1);
            if (min == max) { max = min + 1; }

            int bins = Math.min(10, data.size());
            int[] freq = new int[bins];
            double binW = (max - min) / bins;
            for (double v : data) {
                int bin = (int)((v - min) / binW);
                if (bin >= bins) bin = bins - 1;
                freq[bin]++;
            }

            int maxFreq = 1;
            for (int f : freq) if (f > maxFreq) maxFreq = f;

            float barWidth = (float)(w - 40) / bins;
            float scaleY = (float)(h - 60) / maxFreq;
            int padding = 20;

            for (int i = 0; i < bins; i++) {
                float left = padding + i * barWidth + 2;
                float right = left + barWidth - 4;
                float top = h - padding - freq[i] * scaleY;
                float bottom = h - padding;
                if (freq[i] > 0) {
                    barPaint.setAlpha(180 + (int)(75.0 * i / bins));
                    canvas.drawRoundRect(left, top, right, bottom, 4, 4, barPaint);
                }
                if (i % 2 == 0) {
                    String label = String.format(Locale.US, "%.1f", min + i * binW);
                    canvas.drawText(label, left, h - 2, textPaint);
                }
            }
        }
    }
}
