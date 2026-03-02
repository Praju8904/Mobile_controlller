package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/**
 * A speedometer-style arc chart showing storage usage percentage.
 * - Green gradient: under 60%
 * - Amber gradient: 60–80%
 * - Red gradient: above 80%
 */
public class StorageArcView extends View {

    private Paint trackPaint;
    private Paint arcPaint;
    private Paint textPaint;
    private Paint subTextPaint;

    private float percentage = 0f;
    private String centerLabel = "0%";
    private String subLabel = "";

    private static final float START_ANGLE = 150f;
    private static final float SWEEP_TOTAL = 240f;
    private static final float STROKE_WIDTH_DP = 18f;

    public StorageArcView(Context context) {
        super(context);
        init(context);
    }

    public StorageArcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StorageArcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float strokeWidth = STROKE_WIDTH_DP * density;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#1E293B"));

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(36 * density);
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(Color.parseColor("#94A3B8"));
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(11 * density);
    }

    public void setPercentage(float percent, String centerLabel, String subLabel) {
        this.percentage = Math.max(0f, Math.min(100f, percent));
        this.centerLabel = centerLabel != null ? centerLabel : "0%";
        this.subLabel = subLabel != null ? subLabel : "";
        updateArcPaint();
        invalidate();
    }

    private int[] getArcColors() {
        if (percentage < 60f) {
            return new int[]{Color.parseColor("#10B981"), Color.parseColor("#34D399")};
        } else if (percentage < 80f) {
            return new int[]{Color.parseColor("#F59E0B"), Color.parseColor("#FCD34D")};
        } else {
            return new int[]{Color.parseColor("#EF4444"), Color.parseColor("#F87171")};
        }
    }

    private void updateArcPaint() {
        int[] colors = getArcColors();
        arcPaint.setColor(colors[0]);
        arcPaint.setShader(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = STROKE_WIDTH_DP * density;
        float padding = strokeWidth / 2f + 4 * density;

        float cx = w / 2f;
        float cy = h / 2f + (float) (strokeWidth * 0.3);

        float radius = Math.min(cx, cy) - padding;
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        // Track arc
        canvas.drawArc(oval, START_ANGLE, SWEEP_TOTAL, false, trackPaint);

        // Filled arc
        float sweep = SWEEP_TOTAL * (percentage / 100f);
        if (sweep > 0) {
            int[] colors = getArcColors();
            int startColor = colors[0];
            int endColor = colors[1];
            SweepGradient gradient = new SweepGradient(cx, cy,
                    new int[]{startColor, endColor, startColor},
                    new float[]{0f, sweep / 360f, 1f});
            arcPaint.setShader(gradient);
            canvas.save();
            canvas.rotate(START_ANGLE, cx, cy);
            canvas.drawArc(oval, 0, sweep, false, arcPaint);
            canvas.restore();
            arcPaint.setShader(null);
        }

        // Center text
        float textY = cy + strokeWidth * 0.1f;
        canvas.drawText(centerLabel, cx, textY, textPaint);

        // Sub label below
        if (subLabel != null && !subLabel.isEmpty()) {
            canvas.drawText(subLabel, cx, textY + subTextPaint.getTextSize() * 1.6f, subTextPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (160 * getResources().getDisplayMetrics().density);
        int w = resolveSize(size, widthMeasureSpec);
        int h = resolveSize((int) (size * 0.85f), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }
}
