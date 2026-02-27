package com.prajwal.myfirstapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Grouped bar chart for budget vs actual spending.
 * Shows two bars side by side for each category: budgeted (muted) and actual (colored).
 * Horizontally scrollable when many categories.
 */
public class BudgetComparisonChartView extends View {

    private List<BudgetBar> bars = new ArrayList<>();
    private float animProgress = 0f;
    private Paint budgetPaint, actualPaint, labelPaint, valuePaint, gridPaint;

    public static class BudgetBar {
        public String category;
        public double budgeted;
        public double actual;
        public int color;

        public BudgetBar(String category, double budgeted, double actual, int color) {
            this.category = category;
            this.budgeted = budgeted;
            this.actual = actual;
            this.color = color;
        }
    }

    public BudgetComparisonChartView(Context context) { super(context); init(); }
    public BudgetComparisonChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BudgetComparisonChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        budgetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        actualPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(0xFFE0E7FF);
        valuePaint.setTextSize(20f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1f);
    }

    public void setData(List<BudgetBar> data) {
        this.bars = data;
        // Request minimum width for scrollability
        int minWidth = Math.max(bars.size() * 150, getWidth());
        setMinimumWidth(minWidth);
        animateIn();
    }

    private void animateIn() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.max(bars.size() * 150, MeasureSpec.getSize(widthMeasureSpec));
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height = 400;
        }
        setMeasuredDimension(desiredWidth, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || bars.isEmpty()) return;

        float topPad = 30f, bottomPad = 50f, sidePad = 30f;
        float chartH = h - topPad - bottomPad;
        float groupWidth = (w - 2 * sidePad) / bars.size();
        float barWidth = groupWidth * 0.3f;
        float gap = groupWidth * 0.05f;

        // Find max value
        double maxVal = 1;
        for (BudgetBar b : bars) {
            maxVal = Math.max(maxVal, Math.max(b.budgeted, b.actual));
        }
        maxVal *= 1.1;

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float y = topPad + chartH * (1f - i / 3f);
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint);
        }

        for (int i = 0; i < bars.size(); i++) {
            BudgetBar bar = bars.get(i);
            float cx = sidePad + groupWidth * i + groupWidth / 2f;

            // Budget bar (muted)
            float budgetH = (float) (bar.budgeted / maxVal * chartH * animProgress);
            budgetPaint.setColor(bar.color & 0x40FFFFFF); // 25% alpha
            RectF budgetRect = new RectF(
                cx - barWidth - gap / 2,
                topPad + chartH - budgetH,
                cx - gap / 2,
                topPad + chartH
            );
            canvas.drawRoundRect(budgetRect, 6, 6, budgetPaint);

            // Actual bar (full color)
            float actualH = (float) (bar.actual / maxVal * chartH * animProgress);
            actualPaint.setColor(bar.color);
            RectF actualRect = new RectF(
                cx + gap / 2,
                topPad + chartH - actualH,
                cx + barWidth + gap / 2,
                topPad + chartH
            );
            canvas.drawRoundRect(actualRect, 6, 6, actualPaint);

            // Value labels
            if (animProgress > 0.5f) {
                String budgetStr = formatShort(bar.budgeted);
                String actualStr = formatShort(bar.actual);
                valuePaint.setColor(0xFF6B7B8D);
                canvas.drawText(budgetStr, cx - barWidth / 2 - gap / 2, budgetRect.top - 8, valuePaint);
                valuePaint.setColor(0xFFE0E7FF);
                canvas.drawText(actualStr, cx + barWidth / 2 + gap / 2, actualRect.top - 8, valuePaint);
            }

            // Category label
            String label = bar.category.length() > 6 ? bar.category.substring(0, 6) + "…" : bar.category;
            labelPaint.setTextSize(22f);
            canvas.drawText(label, cx, h - 8, labelPaint);
        }
    }

    private String formatShort(double val) {
        if (val >= 100000) return String.format("%.0fL", val / 100000);
        if (val >= 1000) return String.format("%.0fk", val / 1000);
        return String.format("%.0f", val);
    }
}
