package com.prajwal.myfirstapp.vault;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Custom animated circular progress ring for displaying the vault security score.
 */
public class SecurityScoreView extends View {

    private Paint bgPaint, arcPaint, textPaint, labelPaint;
    private RectF arcRect;
    private int targetScore = 0;
    private float animatedScore = 0;
    private int scoreColor = 0xFF10B981;

    public SecurityScoreView(Context context) { super(context); init(); }
    public SecurityScoreView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SecurityScoreView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(14f);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        bgPaint.setColor(0xFF1E293B);

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(14f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(scoreColor);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFF1F5F9);
        textPaint.setTextSize(42f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF94A3B8);
        labelPaint.setTextSize(14f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        arcRect = new RectF();
    }

    public void setScore(int score) {
        this.targetScore = Math.max(0, Math.min(100, score));
        this.scoreColor = VaultCryptoManager.getStrengthColor(targetScore);
        arcPaint.setColor(scoreColor);

        ValueAnimator animator = ValueAnimator.ofFloat(0, targetScore);
        animator.setDuration(1200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            animatedScore = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 20f;

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Background circle
        canvas.drawArc(arcRect, -225, 270, false, bgPaint);

        // Progress arc
        float sweepAngle = (animatedScore / 100f) * 270f;
        canvas.drawArc(arcRect, -225, sweepAngle, false, arcPaint);

        // Score text
        String scoreText = String.valueOf((int) animatedScore);
        canvas.drawText(scoreText, cx, cy + 14f, textPaint);

        // Label
        canvas.drawText("Security Score", cx, cy + 38f, labelPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        );
        size = Math.min(size, 220);
        setMeasuredDimension(size, size);
    }
}
