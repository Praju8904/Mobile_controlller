package com.prajwal.myfirstapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.text.DateFormatSymbols;
import java.util.Calendar;

/**
 * Smooth animated line chart showing monthly spending trends.
 * Filled area with gradient, dotted grid, and month labels.
 */
public class ExpenseLineChartView extends View {

    private double[] data = new double[6];
    private int monthCount = 6;
    private float animProgress = 0f;
    private Paint linePaint, fillPaint, dotPaint, labelPaint, gridPaint, valuePaint;

    public ExpenseLineChartView(Context context) { super(context); init(); }
    public ExpenseLineChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ExpenseLineChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF7C3AED);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFFA855F7);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(26f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1f);

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(0xFFE0E7FF);
        valuePaint.setTextSize(22f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(double[] values) {
        this.monthCount = values.length;
        this.data = values;
        animateIn();
    }

    private void animateIn() {
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
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float topPad = 30f, bottomPad = 50f, sidePad = 30f;
        float chartW = w - 2 * sidePad;
        float chartH = h - topPad - bottomPad;

        double maxVal = 1;
        for (double d : data) if (d > maxVal) maxVal = d;
        maxVal *= 1.15;

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float y = topPad + chartH * (1f - i / 3f);
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint);
        }

        // Month labels (reversed: data[0] = current month)
        String[] monthNames = new DateFormatSymbols().getShortMonths();
        Calendar cal = Calendar.getInstance();

        // Calculate points (reversed so oldest is left)
        float[] px = new float[monthCount];
        float[] py = new float[monthCount];

        for (int i = 0; i < monthCount; i++) {
            int dataIdx = monthCount - 1 - i; // reverse order
            px[i] = sidePad + (chartW / (monthCount - 1)) * i;
            float val = (float) (data[dataIdx] / maxVal * chartH * animProgress);
            py[i] = topPad + chartH - val;

            // Month label
            Calendar mc = (Calendar) cal.clone();
            mc.add(Calendar.MONTH, -dataIdx);
            canvas.drawText(monthNames[mc.get(Calendar.MONTH)], px[i], h - 12f, labelPaint);
        }

        // Fill gradient path
        Path fillPath = new Path();
        fillPath.moveTo(px[0], topPad + chartH);
        for (int i = 0; i < monthCount; i++) fillPath.lineTo(px[i], py[i]);
        fillPath.lineTo(px[monthCount - 1], topPad + chartH);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, topPad, 0, topPad + chartH,
                0x407C3AED, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // Line
        Path linePath = new Path();
        linePath.moveTo(px[0], py[0]);
        for (int i = 1; i < monthCount; i++) {
            // Smooth curve with cubic bezier
            float midX = (px[i - 1] + px[i]) / 2f;
            linePath.cubicTo(midX, py[i - 1], midX, py[i], px[i], py[i]);
        }
        canvas.drawPath(linePath, linePaint);

        // Dots and values
        for (int i = 0; i < monthCount; i++) {
            int dataIdx = monthCount - 1 - i;
            canvas.drawCircle(px[i], py[i], 7f, dotPaint);
            if (data[dataIdx] > 0) {
                canvas.drawText("₹" + formatAmount(data[dataIdx]),
                        px[i], py[i] - 16f, valuePaint);
            }
        }
    }

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }
}
