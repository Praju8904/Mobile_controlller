package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class NetWorthBarChartView extends View {

    private static final float BAR_WIDTH_RATIO = 0.3f;
    private static final float BAR_GAP_RATIO   = 0.05f;

    private double[] incomeData  = new double[0];
    private double[] expenseData = new double[0];
    private String[] labels      = new String[0];
    private float animProgress   = 0f;
    private int selectedGroup    = -1;
    private Paint incomePaint, expensePaint, labelPaint, gridPaint, tooltipBgPaint, tooltipPaint;
    private RectF[][] incomeRects, expenseRects;

    public NetWorthBarChartView(Context context) { super(context); init(); }
    public NetWorthBarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public NetWorthBarChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFF6B7B8D);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1f);

        tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipBgPaint.setColor(0xFF1E293B);

        tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tooltipPaint.setColor(0xFFFFFFFF);
        tooltipPaint.setTextSize(24f);
        tooltipPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(double[] income, double[] expense, String[] xLabels) {
        this.incomeData  = income  != null ? income  : new double[0];
        this.expenseData = expense != null ? expense : new double[0];
        this.labels      = xLabels != null ? xLabels : new String[0];
        this.selectedGroup = -1;

        int n = incomeData.length;
        incomeRects  = new RectF[n][1];
        expenseRects = new RectF[n][1];
        for (int i = 0; i < n; i++) {
            incomeRects[i][0]  = new RectF();
            expenseRects[i][0] = new RectF();
        }

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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = incomeData.length;
        if (n == 0) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float topPad = 20f, bottomPad = 50f, sidePad = 10f;
        float chartH = h - topPad - bottomPad;
        float groupW = (w - 2 * sidePad) / n;
        float barW   = groupW * BAR_WIDTH_RATIO;
        float gap    = groupW * BAR_GAP_RATIO;

        double maxVal = 1;
        for (double v : incomeData)  if (v > maxVal) maxVal = v;
        for (double v : expenseData) if (v > maxVal) maxVal = v;

        // Grid
        for (int i = 1; i <= 3; i++) {
            float y = topPad + chartH * (1f - i / 3f);
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint);
        }

        for (int i = 0; i < n; i++) {
            float groupCx = sidePad + groupW * i + groupW / 2f;

            // Income bar (left of pair)
            float iH = (float) (incomeData[i] / maxVal * chartH * animProgress);
            float iL = groupCx - gap / 2 - barW;
            float iT = topPad + chartH - iH;
            incomeRects[i][0].set(iL, iT, iL + barW, topPad + chartH);
            incomePaint.setShader(new LinearGradient(iL, iT, iL, topPad + chartH,
                    0xFF4ADE80, 0xFF16A34A, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(incomeRects[i][0], 6f, 6f, incomePaint);

            // Expense bar (right of pair)
            float eH = (float) (expenseData[i] / maxVal * chartH * animProgress);
            float eL = groupCx + gap / 2;
            float eT = topPad + chartH - eH;
            expenseRects[i][0].set(eL, eT, eL + barW, topPad + chartH);
            expensePaint.setShader(new LinearGradient(eL, eT, eL, topPad + chartH,
                    0xFFF87171, 0xFFDC2626, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(expenseRects[i][0], 6f, 6f, expensePaint);

            // Label
            String lbl = (labels != null && i < labels.length) ? labels[i] : "";
            canvas.drawText(lbl, groupCx, h - 12f, labelPaint);
        }

        // Tooltip
        if (selectedGroup >= 0 && selectedGroup < n) {
            double inc = incomeData[selectedGroup];
            double exp = expenseData[selectedGroup];
            double sav = inc - exp;
            String line1 = "↑₹" + fmt(inc) + "  ↓₹" + fmt(exp);
            String line2 = "Saved: ₹" + fmt(sav);

            float groupCx = sidePad + groupW * selectedGroup + groupW / 2f;
            float ty = topPad + 10f;
            float bw = 280f, bh = 70f;
            if (groupCx - bw / 2 < 0) groupCx = bw / 2 + 4;
            if (groupCx + bw / 2 > w) groupCx = w - bw / 2 - 4;
            canvas.drawRoundRect(groupCx - bw / 2, ty, groupCx + bw / 2, ty + bh,
                    12f, 12f, tooltipBgPaint);
            canvas.drawText(line1, groupCx, ty + 26f, tooltipPaint);
            tooltipPaint.setColor(sav >= 0 ? 0xFF4ADE80 : 0xFFF87171);
            canvas.drawText(line2, groupCx, ty + 56f, tooltipPaint);
            tooltipPaint.setColor(0xFFFFFFFF);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && incomeData.length > 0) {
            int n = incomeData.length;
            int w = getWidth();
            float sidePad = 10f, groupW = (w - 2 * sidePad) / n;
            float x = event.getX();
            int g = (int) ((x - sidePad) / groupW);
            if (g >= 0 && g < n) {
                selectedGroup = (selectedGroup == g) ? -1 : g;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private String fmt(double v) {
        if (Math.abs(v) >= 100000) return String.format("%.1fL", v / 100000);
        if (Math.abs(v) >= 1000)   return String.format("%.1fk", v / 1000);
        return String.format("%.0f", v);
    }
}
