package com.prajwal.myfirstapp.hub;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated dual-axis line chart for the Analytics screen.
 *
 * Draws two lines:
 *   • Primary (blue)  — file count (left Y-axis)
 *   • Secondary (purple) — storage in GB (right Y-axis)
 *
 * Lines animate drawing from left to right on load.
 * Tapping a data point shows a tooltip with exact values.
 */
public class HubLineChartView extends View {

    public static class DataPoint {
        public final String label;    // x-axis label (e.g. "Jan 10")
        public final float primary;   // file count
        public final float secondary; // storage GB

        public DataPoint(String label, float primary, float secondary) {
            this.label = label;
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    private final Paint primaryLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<DataPoint> dataPoints = new ArrayList<>();
    private float animProgress = 0f;  // 0..1, drives the draw clip
    private int selectedIndex = -1;

    private static final int PRIMARY_COLOR = Color.parseColor("#3B82F6");
    private static final int SECONDARY_COLOR = Color.parseColor("#8B5CF6");
    private static final float STROKE_WIDTH = 3f;
    private static final float DOT_RADIUS = 6f;
    private static final float PAD_LEFT = 60f;
    private static final float PAD_RIGHT = 60f;
    private static final float PAD_TOP = 24f;
    private static final float PAD_BOTTOM = 48f;

    public HubLineChartView(Context context) { super(context); init(); }
    public HubLineChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        primaryLinePaint.setColor(PRIMARY_COLOR);
        primaryLinePaint.setStrokeWidth(STROKE_WIDTH);
        primaryLinePaint.setStyle(Paint.Style.STROKE);
        primaryLinePaint.setStrokeJoin(Paint.Join.ROUND);
        primaryLinePaint.setStrokeCap(Paint.Cap.ROUND);

        secondaryLinePaint.setColor(SECONDARY_COLOR);
        secondaryLinePaint.setStrokeWidth(STROKE_WIDTH);
        secondaryLinePaint.setStyle(Paint.Style.STROKE);
        secondaryLinePaint.setStrokeJoin(Paint.Join.ROUND);
        secondaryLinePaint.setStrokeCap(Paint.Cap.ROUND);

        primaryDotPaint.setColor(PRIMARY_COLOR);
        primaryDotPaint.setStyle(Paint.Style.FILL);
        secondaryDotPaint.setColor(SECONDARY_COLOR);
        secondaryDotPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(Color.parseColor("#374151"));
        axisPaint.setStrokeWidth(1.5f);
        axisPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(Color.parseColor("#9CA3AF"));
        labelPaint.setTextSize(24f);

        tooltipPaint.setColor(Color.parseColor("#1F2937"));
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(26f);

        primaryFillPaint.setStyle(Paint.Style.FILL);
        secondaryFillPaint.setStyle(Paint.Style.FILL);
    }

    /** Set data and trigger animation. */
    public void setData(List<DataPoint> points) {
        this.dataPoints = points != null ? points : new ArrayList<>();
        this.selectedIndex = -1;
        animateIn();
    }

    private void animateIn() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(800);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataPoints.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        float chartW = w - PAD_LEFT - PAD_RIGHT;
        float chartH = h - PAD_TOP - PAD_BOTTOM;

        // Compute max values
        float maxPrimary = 1f, maxSecondary = 1f;
        for (DataPoint dp : dataPoints) {
            if (dp.primary > maxPrimary) maxPrimary = dp.primary;
            if (dp.secondary > maxSecondary) maxSecondary = dp.secondary;
        }

        int n = dataPoints.size();
        float[] px = new float[n];
        float[] py = new float[n];
        float[] sx = new float[n];
        float[] sy = new float[n];

        for (int i = 0; i < n; i++) {
            DataPoint dp = dataPoints.get(i);
            px[i] = PAD_LEFT + (i / (float) (n - 1)) * chartW;
            py[i] = PAD_TOP + chartH - (dp.primary / maxPrimary) * chartH;
            sx[i] = PAD_LEFT + (i / (float) (n - 1)) * chartW;
            sy[i] = PAD_TOP + chartH - (dp.secondary / maxSecondary) * chartH;
        }

        // Draw axis
        canvas.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + chartH, axisPaint);
        canvas.drawLine(PAD_LEFT, PAD_TOP + chartH, w - PAD_RIGHT, PAD_TOP + chartH, axisPaint);

        // Draw grid lines (3 horizontal)
        axisPaint.setAlpha(60);
        for (int g = 1; g <= 3; g++) {
            float gy = PAD_TOP + chartH * g / 4f;
            canvas.drawLine(PAD_LEFT, gy, w - PAD_RIGHT, gy, axisPaint);
        }
        axisPaint.setAlpha(255);

        // Determine how many points to draw based on animation progress
        int drawTo = Math.max(1, (int) (animProgress * n));

        // Fill area under primary line
        if (drawTo >= 2) {
            Path fillPath = new Path();
            fillPath.moveTo(px[0], PAD_TOP + chartH);
            fillPath.lineTo(px[0], py[0]);
            for (int i = 1; i < drawTo; i++) fillPath.lineTo(px[i], py[i]);
            fillPath.lineTo(px[drawTo - 1], PAD_TOP + chartH);
            fillPath.close();
            primaryFillPaint.setShader(new LinearGradient(0, PAD_TOP, 0, PAD_TOP + chartH,
                    Color.argb(80, 59, 130, 246), Color.argb(5, 59, 130, 246), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, primaryFillPaint);
        }

        // Draw primary line
        Path primaryPath = new Path();
        if (drawTo > 0) primaryPath.moveTo(px[0], py[0]);
        for (int i = 1; i < drawTo; i++) primaryPath.lineTo(px[i], py[i]);
        canvas.drawPath(primaryPath, primaryLinePaint);

        // Fill area under secondary line
        if (drawTo >= 2) {
            Path fillPath2 = new Path();
            fillPath2.moveTo(sx[0], PAD_TOP + chartH);
            fillPath2.lineTo(sx[0], sy[0]);
            for (int i = 1; i < drawTo; i++) fillPath2.lineTo(sx[i], sy[i]);
            fillPath2.lineTo(sx[drawTo - 1], PAD_TOP + chartH);
            fillPath2.close();
            secondaryFillPaint.setShader(new LinearGradient(0, PAD_TOP, 0, PAD_TOP + chartH,
                    Color.argb(80, 139, 92, 246), Color.argb(5, 139, 92, 246), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath2, secondaryFillPaint);
        }

        // Draw secondary line
        Path secondaryPath = new Path();
        if (drawTo > 0) secondaryPath.moveTo(sx[0], sy[0]);
        for (int i = 1; i < drawTo; i++) secondaryPath.lineTo(sx[i], sy[i]);
        canvas.drawPath(secondaryPath, secondaryLinePaint);

        // Draw dots
        for (int i = 0; i < drawTo; i++) {
            float dotR = (i == selectedIndex) ? DOT_RADIUS * 1.8f : DOT_RADIUS;
            canvas.drawCircle(px[i], py[i], dotR, primaryDotPaint);
            canvas.drawCircle(sx[i], sy[i], dotR, secondaryDotPaint);
        }

        // X-axis labels (every ~5th point)
        int labelStep = Math.max(1, n / 6);
        for (int i = 0; i < n; i += labelStep) {
            String lbl = dataPoints.get(i).label;
            float textW = labelPaint.measureText(lbl);
            canvas.drawText(lbl, px[i] - textW / 2, h - 8, labelPaint);
        }

        // Tooltip
        if (selectedIndex >= 0 && selectedIndex < n) {
            DataPoint dp = dataPoints.get(selectedIndex);
            String tip = dp.label + "  Files: " + (int) dp.primary
                    + "  " + String.format("%.2f", dp.secondary) + " GB";
            float tipW = tooltipTextPaint.measureText(tip) + 24;
            float tipH = 52f;
            float tipX = Math.min(px[selectedIndex] - tipW / 2, w - tipW - 8);
            tipX = Math.max(tipX, 8);
            float tipY = Math.max(py[selectedIndex] - tipH - 12, PAD_TOP);

            RectF rect = new RectF(tipX, tipY, tipX + tipW, tipY + tipH);
            canvas.drawRoundRect(rect, 8, 8, tooltipPaint);
            canvas.drawText(tip, tipX + 12, tipY + 34, tooltipTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && !dataPoints.isEmpty()) {
            float touchX = event.getX();
            int n = dataPoints.size();
            float chartW = getWidth() - PAD_LEFT - PAD_RIGHT;
            int best = 0;
            float bestDist = Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                float dotX = PAD_LEFT + (i / (float) (n - 1)) * chartW;
                float d = Math.abs(touchX - dotX);
                if (d < bestDist) { bestDist = d; best = i; }
            }
            selectedIndex = best;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
