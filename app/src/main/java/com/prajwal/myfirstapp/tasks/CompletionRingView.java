package com.prajwal.myfirstapp.tasks;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom View that draws an animated completion ring/arc chart.
 * Displays a background circle, a progress arc, and a percentage label in the center.
 */
public class CompletionRingView extends View {

    private float progress = 0f;
    private int ringColor = 0xFFFFFFFF;     // solid white
    private int bgColor   = 0x4DFFFFFF;     // ~30% opaque white

    private float animatedProgress = 0f;

    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalBounds = new RectF();

    // ─── Constructors ────────────────────────────────────────────

    public CompletionRingView(Context context) {
        super(context);
        init();
    }

    public CompletionRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CompletionRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density    = getResources().getDisplayMetrics().density;
        float strokeWidth = 8f * density;

        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeWidth);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        bgPaint.setColor(bgColor);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(ringColor);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(10f * density);
        textPaint.setFakeBoldText(true);
    }

    // ─── Public API ──────────────────────────────────────────────

    /** Sets progress (0.0 to 1.0) with a 300ms animated transition. */
    public void setProgress(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        ValueAnimator animator = ValueAnimator.ofFloat(animatedProgress, target);
        animator.setDuration(300);
        animator.addUpdateListener(anim -> {
            animatedProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        animator.start();
        this.progress = target;
    }

    /** @deprecated Use {@link #setProgress(float)} instead. Kept for API compatibility. */
    @Deprecated
    public void setPropgress(float value) {
        setProgress(value);
    }

    public void setRingColor(int color) {
        this.ringColor = color;
        ringPaint.setColor(color);
        invalidate();
    }

    public void setBgColor(int color) {
        this.bgColor = color;
        bgPaint.setColor(color);
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    // ─── Drawing ─────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density    = getResources().getDisplayMetrics().density;
        float strokeWidth = 8f * density;
        float padding    = strokeWidth / 2f + density; // small extra margin so stroke is not clipped

        ovalBounds.set(padding, padding, getWidth() - padding, getHeight() - padding);

        // Background ring (full 360° circle)
        canvas.drawArc(ovalBounds, -90f, 360f, false, bgPaint);

        // Foreground progress arc, starting at 12 o'clock (-90°)
        if (animatedProgress > 0f) {
            canvas.drawArc(ovalBounds, -90f, animatedProgress * 360f, false, ringPaint);
        }

        // Center percentage label
        int percent  = Math.round(animatedProgress * 100f);
        String label = percent + "%";
        float cx = getWidth() / 2f;
        // Vertically center text by offsetting by half the text height
        float cy = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(label, cx, cy, textPaint);
    }
}
