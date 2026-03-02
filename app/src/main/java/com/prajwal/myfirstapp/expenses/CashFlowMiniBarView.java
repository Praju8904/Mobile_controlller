package com.prajwal.myfirstapp.expenses;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class CashFlowMiniBarView extends View {

    private double[] data = new double[0];
    private float animProgress = 0f;
    private Paint positivePaint, negativePaint, zeroPaint;

    public CashFlowMiniBarView(Context context) { super(context); init(); }
    public CashFlowMiniBarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CashFlowMiniBarView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        positivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        negativePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zeroPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zeroPaint.setColor(0x30FFFFFF);
        zeroPaint.setStrokeWidth(1f);
    }

    public void setData(double[] values) {
        this.data = values != null ? values : new double[0];
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(700);
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
        int n = data.length;
        if (n == 0) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float barW  = (w - (n - 1) * 2f) / n;
        float halfH = h / 2f;

        double maxAbs = 1;
        for (double d : data) if (Math.abs(d) > maxAbs) maxAbs = Math.abs(d);

        // Zero line
        canvas.drawLine(0, halfH, w, halfH, zeroPaint);

        for (int i = 0; i < n; i++) {
            float left  = i * (barW + 2f);
            float barH  = (float) (Math.abs(data[i]) / maxAbs * halfH * 0.9f * animProgress);
            RectF rect;

            if (data[i] >= 0) {
                rect = new RectF(left, halfH - barH, left + barW, halfH);
                positivePaint.setShader(new LinearGradient(left, halfH - barH, left, halfH,
                        0xFF4ADE80, 0xFF16A34A, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, 3f, 3f, positivePaint);
            } else {
                rect = new RectF(left, halfH, left + barW, halfH + barH);
                negativePaint.setShader(new LinearGradient(left, halfH, left, halfH + barH,
                        0xFFDC2626, 0xFFF87171, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, 3f, 3f, negativePaint);
            }
        }
    }
}
