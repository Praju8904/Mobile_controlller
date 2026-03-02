package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Animated circular progress ring for budget health score.
 * Shows a colored ring (green/amber/red) with score text in center.
 */
public class BudgetHealthRingView extends View {

    private float score = 0f;    // 0–100
    private float animProgress = 0f;
    private Paint ringBgPaint, ringPaint, scorePaint, labelPaint;
    private RectF arcRect = new RectF();
    private String label = "On Track";
    private int ringColor = 0xFF22C55E;

    public BudgetHealthRingView(Context context) { super(context); init(); }
    public BudgetHealthRingView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BudgetHealthRingView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringBgPaint.setStrokeCap(Paint.Cap.ROUND);
        ringBgPaint.setColor(0x20FFFFFF);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(0xFFFFFFFF);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setScore(float score, String label, int color) {
        this.score = Math.max(0f, Math.min(100f, score));
        this.label = label;
        this.ringColor = color;
        ringPaint.setColor(color);
        animateIn();
    }

    private void animateIn() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200);
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

        float size = Math.min(w, h);
        float strokeWidth = size * 0.1f;
        ringBgPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeWidth(strokeWidth);

        float pad = strokeWidth / 2f + 8f;
        arcRect.set(pad, pad, size - pad, size - pad);

        // Background ring
        canvas.drawArc(arcRect, -90, 360, false, ringBgPaint);

        // Animated progress ring
        float sweep = (score / 100f) * 360f * animProgress;
        canvas.drawArc(arcRect, -90, sweep, false, ringPaint);

        // Center score text
        float cx = size / 2f;
        float cy = size / 2f;
        scorePaint.setTextSize(size * 0.22f);
        int displayScore = (int) (score * animProgress);
        canvas.drawText(displayScore + "%", cx, cy, scorePaint);

        // Label below score
        labelPaint.setTextSize(size * 0.1f);
        canvas.drawText(label, cx, cy + size * 0.15f, labelPaint);
    }
}
