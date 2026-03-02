package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class SavingsGaugeView extends View {

    private static final int ZONE_ARC_ALPHA = 40;

    private double savingsRate = 0;
    private float animProgress = 0f;
    private Paint trackPaint, zonePaint, needlePaint, centerPaint, textPaint, pctPaint;
    private RectF oval;

    private static final float START_ANGLE = 150f;  // degrees (7 o'clock)
    private static final float SWEEP       = 240f;  // degrees of arc

    public SavingsGaugeView(Context context) { super(context); init(); }
    public SavingsGaugeView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SavingsGaugeView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(20f);
        trackPaint.setColor(0x22FFFFFF);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(20f);
        zonePaint.setStrokeCap(Paint.Cap.BUTT);

        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setColor(0xFFFFFFFF);
        needlePaint.setStrokeWidth(6f);
        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFFFFFFFF);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        pctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pctPaint.setColor(0xFFD1D5DB);
        pctPaint.setTextAlign(Paint.Align.CENTER);

        oval = new RectF();
    }

    public void setSavingsRate(double rate) {
        this.savingsRate = Math.max(0, Math.min(100, rate));
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private int getZoneColor(double rate) {
        if (rate < 10)  return 0xFFEF4444;
        if (rate < 20)  return 0xFFF59E0B;
        if (rate < 30)  return 0xFF22C55E;
        return 0xFF4ADE80;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h * 0.6f;
        float radius = Math.min(w * 0.4f, h * 0.7f);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Track
        canvas.drawArc(oval, START_ANGLE, SWEEP, false, trackPaint);

        // Zone arcs (0-10%, 10-20%, 20-30%, 30%+)
        float[] zoneEnds  = {10f, 20f, 30f, 100f};
        int[]   zoneColors = {0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF4ADE80};
        float prevEnd = 0f;
        for (int z = 0; z < 4; z++) {
            zonePaint.setColor(zoneColors[z]);
            zonePaint.setAlpha(ZONE_ARC_ALPHA);
            float startDeg = START_ANGLE + SWEEP * prevEnd / 100f;
            float sweepDeg = SWEEP * (zoneEnds[z] - prevEnd) / 100f;
            canvas.drawArc(oval, startDeg, sweepDeg, false, zonePaint);
            prevEnd = zoneEnds[z];
        }

        // Animated needle
        double animRate = savingsRate * animProgress;
        float angle = START_ANGLE + (float) (SWEEP * animRate / 100.0);
        double rad = Math.toRadians(angle);
        float nx = (float) (cx + radius * 0.82f * Math.cos(rad));
        float ny = (float) (cy + radius * 0.82f * Math.sin(rad));

        needlePaint.setColor(getZoneColor(animRate));
        canvas.drawLine(cx, cy, nx, ny, needlePaint);
        canvas.drawCircle(cx, cy, 10f, centerPaint);

        // Percentage text
        textPaint.setTextSize(radius * 0.4f);
        canvas.drawText(String.format("%.0f%%", animRate),
                cx, cy - radius * 0.25f, textPaint);

        // "Savings Rate" label
        pctPaint.setTextSize(radius * 0.16f);
        canvas.drawText("Savings Rate", cx, cy - radius * 0.05f, pctPaint);
    }
}
