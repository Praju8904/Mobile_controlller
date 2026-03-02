package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class FinancialHealthRingView extends View {

    private int score = 0;
    private float animProgress = 0f;
    private Paint trackPaint, ringPaint, textPaint, labelPaint;
    private RectF oval;

    public FinancialHealthRingView(Context context) { super(context); init(); }
    public FinancialHealthRingView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public FinancialHealthRingView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(24f);
        trackPaint.setColor(0x22FFFFFF);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(24f);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFFD1D5DB);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        oval = new RectF();
    }

    public void setScore(int score) {
        this.score = Math.max(0, Math.min(100, score));
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private int getRingColor() {
        if (score <= 40) return 0xFFEF4444;
        if (score <= 70) return 0xFFF59E0B;
        return 0xFF22C55E;
    }

    private String getLabel() {
        if (score <= 25) return "Needs Work";
        if (score <= 50) return "Getting There";
        if (score <= 75) return "Good";
        return "Excellent";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) / 2f - 30f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Track
        canvas.drawArc(oval, -210f, 240f, false, trackPaint);

        // Animated ring
        ringPaint.setColor(getRingColor());
        float sweep = 240f * (score / 100f) * animProgress;
        canvas.drawArc(oval, -210f, sweep, false, ringPaint);

        // Score text
        textPaint.setTextSize(radius * 0.5f);
        canvas.drawText(String.valueOf((int)(score * animProgress)),
                cx, cy + textPaint.getTextSize() * 0.35f, textPaint);

        // Label
        labelPaint.setTextSize(radius * 0.18f);
        canvas.drawText(getLabel(), cx, cy + textPaint.getTextSize() * 0.85f, labelPaint);
    }
}
