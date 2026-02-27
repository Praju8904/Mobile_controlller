package com.prajwal.myfirstapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.text.DateFormatSymbols;
import java.util.Calendar;

/**
 * Bar chart showing monthly recurring cost trend for the last N months.
 * Used in the Subscriptions screen to visualize subscription spending growth.
 */
public class SubscriptionTrendChartView extends View {

    private double[] data = new double[6];
    private float animProgress = 0f;
    private Paint barPaint, labelPaint, valuePaint, gridPaint;

    public SubscriptionTrendChartView(Context context) { super(context); init(); }
    public SubscriptionTrendChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SubscriptionTrendChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(0xFFE0E7FF);
        valuePaint.setTextSize(22f);
        valuePaint.setTextAlign(Paint.Align.CENTER);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1f);
    }

    public void setData(double[] values) {
        this.data = values;
        animateIn();
    }

    private void animateIn() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(800);
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
        float chartH = h - topPad - bottomPad;
        float barArea = (w - 2 * sidePad) / data.length;
        float barW = barArea * 0.55f;

        double maxVal = 1;
        for (double d : data) if (d > maxVal) maxVal = d;
        maxVal *= 1.15;

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float y = topPad + chartH * (1f - i / 3f);
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint);
        }

        // Month labels
        String[] months = new DateFormatSymbols().getShortMonths();
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < data.length; i++) {
            int monthIdx = data.length - 1 - i;
            float cx = sidePad + barArea * i + barArea / 2f;

            // Bar
            float barH = (float) (data[monthIdx] / maxVal * chartH * animProgress);
            float top = topPad + chartH - barH;

            // Gradient-like color — more intense for higher values
            float intensity = (float) (data[monthIdx] / maxVal);
            int r = (int) (0x7C + (0xA8 - 0x7C) * intensity);
            int g = (int) (0x3A + (0x55 - 0x3A) * intensity);
            int b = (int) (0xED + (0xF7 - 0xED) * intensity);
            barPaint.setColor(0xFF000000 | (r << 16) | (g << 8) | b);

            RectF rect = new RectF(cx - barW / 2, top, cx + barW / 2, topPad + chartH);
            canvas.drawRoundRect(rect, 8, 8, barPaint);

            // Value on top
            if (animProgress > 0.5f && data[monthIdx] > 0) {
                String val = formatShort(data[monthIdx]);
                canvas.drawText(val, cx, top - 8, valuePaint);
            }

            // Month label
            Calendar monthCal = (Calendar) cal.clone();
            monthCal.add(Calendar.MONTH, -monthIdx);
            canvas.drawText(months[monthCal.get(Calendar.MONTH)], cx, h - 8, labelPaint);
        }
    }

    private String formatShort(double val) {
        if (val >= 100000) return String.format("%.1fL", val / 100000);
        if (val >= 1000) return String.format("%.1fk", val / 1000);
        return String.format("%.0f", val);
    }
}
