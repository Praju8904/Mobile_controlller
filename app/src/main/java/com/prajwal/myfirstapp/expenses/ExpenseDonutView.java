package com.prajwal.myfirstapp.expenses;

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
import java.util.Map;

/**
 * Animated donut/pie chart with category-colored segments,
 * center total display, and legend labels.
 */
public class ExpenseDonutView extends View {

    private List<Segment> segments = new ArrayList<>();
    private double total = 0;
    private float animProgress = 0f;
    private Paint arcPaint, centerTextPaint, centerLabelPaint, legendPaint, legendDotPaint;
    private RectF arcRect = new RectF();

    private static class Segment {
        String label;
        double amount;
        int color;
        float sweepAngle;
    }

    public ExpenseDonutView(Context context) { super(context); init(); }
    public ExpenseDonutView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ExpenseDonutView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        centerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerTextPaint.setColor(0xFFFFFFFF);
        centerTextPaint.setTextAlign(Paint.Align.CENTER);
        centerTextPaint.setFakeBoldText(true);

        centerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLabelPaint.setColor(0xFF6B7B8D);
        centerLabelPaint.setTextAlign(Paint.Align.CENTER);
        centerLabelPaint.setTextSize(28f);

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setColor(0xFFB0B8CC);
        legendPaint.setTextSize(26f);

        legendDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setData(Map<String, Double> categoryData) {
        segments.clear();
        total = 0;
        for (Map.Entry<String, Double> entry : categoryData.entrySet()) {
            total += entry.getValue();
        }
        if (total == 0) { invalidate(); return; }

        for (Map.Entry<String, Double> entry : categoryData.entrySet()) {
            Segment s = new Segment();
            s.label = entry.getKey();
            s.amount = entry.getValue();
            s.color = Expense.getCategoryColor(entry.getKey());
            s.sweepAngle = (float) (entry.getValue() / total * 360);
            segments.add(s);
        }
        // Sort by amount descending
        segments.sort((a, b) -> Double.compare(b.amount, a.amount));
        animateIn();
    }

    private void animateIn() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
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
        if (w == 0 || h == 0 || segments.isEmpty()) return;

        // Donut area — left portion
        float donutSize = Math.min(w * 0.5f, h * 0.8f);
        float strokeW = donutSize * 0.18f;
        arcPaint.setStrokeWidth(strokeW);
        float cx = w * 0.28f, cy = h * 0.45f;
        float radius = donutSize / 2f - strokeW / 2f;
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float startAngle = -90f;
        for (Segment s : segments) {
            arcPaint.setColor(s.color);
            float sweep = s.sweepAngle * animProgress;
            if (sweep > 0.5f) {
                canvas.drawArc(arcRect, startAngle, sweep - 1.5f, false, arcPaint);
            }
            startAngle += sweep;
        }

        // Center text
        centerTextPaint.setTextSize(donutSize * 0.18f);
        canvas.drawText("₹" + formatAmount(total), cx, cy + 8f, centerTextPaint);
        canvas.drawText("This Month", cx, cy + 38f, centerLabelPaint);

        // Legend — right side
        float legendX = w * 0.55f;
        float legendY = h * 0.15f;
        float legendSpacing = Math.min(40f, (h * 0.7f) / Math.max(segments.size(), 1));

        int maxLegend = Math.min(segments.size(), 6);
        for (int i = 0; i < maxLegend; i++) {
            Segment s = segments.get(i);
            float y = legendY + i * legendSpacing;
            legendDotPaint.setColor(s.color);
            canvas.drawCircle(legendX, y, 8f, legendDotPaint);
            String pct = String.format("%.0f%%", s.amount / total * 100);
            legendPaint.setColor(0xFFB0B8CC);
            canvas.drawText(s.label + "  " + pct, legendX + 20f, y + 8f, legendPaint);
        }
    }

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }
}
