package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Calendar;

/**
 * Interactive 7-day bar chart with animated bars, tap-to-inspect,
 * rounded corners, and gradient fills.
 */
public class ExpenseChartView extends View {

    private double[] data = new double[7];
    private float animProgress = 0f;
    private int selectedBar = -1;
    private Paint barPaint, labelPaint, valuePaint, gridPaint, selectedPaint;
    private OnBarSelectedListener listener;
    private RectF[] barRects = new RectF[7];

    public interface OnBarSelectedListener {
        void onBarSelected(int dayIndex, double amount);
    }

    public ExpenseChartView(Context context) { super(context); init(); }
    public ExpenseChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ExpenseChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(0xFFA855F7);
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setTextSize(26f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1f);
    }

    public void setData(double[] values) {
        this.data = values;
        selectedBar = -1;
        animateIn();
    }

    public void setOnBarSelectedListener(OnBarSelectedListener l) { this.listener = l; }

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

        float topPad = 40f, bottomPad = 60f, sidePad = 20f;
        float chartH = h - topPad - bottomPad;
        float barAreaW = (w - 2 * sidePad) / 7f;
        float barW = barAreaW * 0.55f;

        double maxVal = 1;
        for (double d : data) if (d > maxVal) maxVal = d;

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float y = topPad + chartH * (1f - i / 3f);
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint);
        }

        // Day labels
        String[] days = getDayLabels();

        for (int i = 0; i < 7; i++) {
            float cx = sidePad + barAreaW * i + barAreaW / 2f;
            float barH = (float) (data[i] / maxVal * chartH * animProgress);
            float left = cx - barW / 2f;
            float top = topPad + chartH - barH;
            float bottom = topPad + chartH;

            barRects[i] = new RectF(left, top, left + barW, bottom);

            // Bar gradient
            if (i == selectedBar) {
                barPaint.setShader(new LinearGradient(left, top, left, bottom,
                        0xFFC084FC, 0xFF7C3AED, Shader.TileMode.CLAMP));
            } else {
                barPaint.setShader(new LinearGradient(left, top, left, bottom,
                        0xFF818CF8, 0xFF4F46E5, Shader.TileMode.CLAMP));
            }
            canvas.drawRoundRect(barRects[i], 8f, 8f, barPaint);

            // Day label
            canvas.drawText(days[i], cx, h - 15f, labelPaint);

            // Value on selected
            if (i == selectedBar && data[i] > 0) {
                canvas.drawText("₹" + formatAmount(data[i]), cx, top - 12f, valuePaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            for (int i = 0; i < 7; i++) {
                if (barRects[i] != null) {
                    float expandedLeft = barRects[i].left - 20f;
                    float expandedRight = barRects[i].right + 20f;
                    if (x >= expandedLeft && x <= expandedRight) {
                        selectedBar = i;
                        if (listener != null) listener.onBarSelected(i, data[i]);
                        invalidate();
                        return true;
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private String[] getDayLabels() {
        String[] labels = new String[7];
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(6 - i));
            labels[i] = dayNames[d.get(Calendar.DAY_OF_WEEK) - 1];
        }
        return labels;
    }

    private String formatAmount(double amount) {
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }
}
