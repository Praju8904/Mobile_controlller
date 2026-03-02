package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class NetWorthLineChartView extends View {

    private static final float TOUCH_TOLERANCE_PX = 60f;

    private double[] data = new double[0];
    private String[] labels = new String[0];
    private float animProgress = 0f;
    private int selectedPoint = -1;
    private Paint linePaint, fillPaint, dotPaint, selectedDotPaint, labelPaint, tooltipPaint, tooltipBgPaint, trendPaint;

    public NetWorthLineChartView(Context context) { super(context); init(); }
    public NetWorthLineChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public NetWorthLineChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF818CF8);
        linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFF818CF8);

        selectedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedDotPaint.setColor(0xFFC084FC);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipBgPaint.setColor(0xFF1E293B);

        tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipPaint.setColor(0xFFFFFFFF);
        tooltipPaint.setTextSize(26f);
        tooltipPaint.setTextAlign(Paint.Align.CENTER);
        tooltipPaint.setFakeBoldText(true);

        trendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trendPaint.setTextSize(28f);
        trendPaint.setFakeBoldText(true);
    }

    public void setData(double[] values, String[] xLabels) {
        this.data = values != null ? values : new double[0];
        this.labels = xLabels != null ? xLabels : new String[0];
        this.selectedPoint = -1;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.length < 2) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float topPad = 40f, bottomPad = 50f, sidePad = 30f;
        float chartW = w - 2 * sidePad;
        float chartH = h - topPad - bottomPad;

        double minVal = data[0], maxVal = data[0];
        for (double d : data) { if (d < minVal) minVal = d; if (d > maxVal) maxVal = d; }
        double range = maxVal - minVal;
        if (range == 0) range = 1;

        int n = data.length;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = sidePad + chartW * i / (n - 1);
            ys[i] = topPad + chartH * (float) (1.0 - (data[i] - minVal) / range);
        }

        // Build paths up to animProgress
        int animN = Math.max(2, (int) (n * animProgress));
        if (animN > n) animN = n;

        Path linePath = new Path();
        Path fillPath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        fillPath.moveTo(xs[0], topPad + chartH);
        fillPath.lineTo(xs[0], ys[0]);

        for (int i = 1; i < animN; i++) {
            float cpx = (xs[i - 1] + xs[i]) / 2f;
            linePath.cubicTo(cpx, ys[i - 1], cpx, ys[i], xs[i], ys[i]);
            fillPath.cubicTo(cpx, ys[i - 1], cpx, ys[i], xs[i], ys[i]);
        }
        fillPath.lineTo(xs[animN - 1], topPad + chartH);
        fillPath.close();

        // Gradient fill
        fillPaint.setShader(new LinearGradient(0, topPad, 0, topPad + chartH,
                0x55818CF8, 0x00818CF8, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Dots
        for (int i = 0; i < animN; i++) {
            Paint p = (i == selectedPoint) ? selectedDotPaint : dotPaint;
            canvas.drawCircle(xs[i], ys[i], i == selectedPoint ? 12f : 6f, p);
        }

        // Tooltip
        if (selectedPoint >= 0 && selectedPoint < n) {
            String valStr = formatAmount(data[selectedPoint]);
            float tx = xs[selectedPoint];
            float ty = ys[selectedPoint] - 50f;
            float bw = 160f, bh = 50f;
            if (tx - bw / 2 < 0) tx = bw / 2;
            if (tx + bw / 2 > w) tx = w - bw / 2;
            canvas.drawRoundRect(tx - bw / 2, ty - bh / 2, tx + bw / 2, ty + bh / 2,
                    12f, 12f, tooltipBgPaint);
            canvas.drawText("₹" + valStr, tx, ty + tooltipPaint.getTextSize() * 0.35f, tooltipPaint);
        }

        // Labels (show only first, middle, last)
        if (labels != null && labels.length == n) {
            int[] showIdx = {0, n / 2, n - 1};
            for (int idx : showIdx) {
                canvas.drawText(labels[idx], xs[idx], h - 10f, labelPaint);
            }
        }

        // Trend label
        if (animProgress >= 1f && n >= 2) {
            boolean up = data[n - 1] >= data[0];
            trendPaint.setColor(up ? 0xFF22C55E : 0xFFF59E0B);
            canvas.drawText(up ? "↑ Trending Up" : "↓ Trending Down",
                    w / 2f, topPad - 10f, trendPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && data != null && data.length > 1) {
            int w = getWidth();
            float sidePad = 30f, chartW = w - 2 * sidePad;
            float touchX = event.getX();
            int n = data.length;
            float minDist = Float.MAX_VALUE;
            int nearest = -1;
            for (int i = 0; i < n; i++) {
                float px = sidePad + chartW * i / (n - 1);
                float dist = Math.abs(touchX - px);
                if (dist < minDist) { minDist = dist; nearest = i; }
            }
            if (minDist < TOUCH_TOLERANCE_PX) {
                selectedPoint = (selectedPoint == nearest) ? -1 : nearest;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000)   return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }
}
