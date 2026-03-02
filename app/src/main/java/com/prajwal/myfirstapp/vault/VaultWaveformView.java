package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Custom View that draws an animated waveform when playing.
 */
public class VaultWaveformView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] bars = new int[32];
    private final int[] targets = new int[32];
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean playing = false;

    private static final int BAR_COUNT = 32;
    private static final int ACCENT_COLOR = 0xFF6C63FF;
    private static final long FRAME_MS = 80;

    private final Runnable animateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            for (int i = 0; i < BAR_COUNT; i++) {
                if (Math.abs(bars[i] - targets[i]) < 3) {
                    targets[i] = 10 + random.nextInt(90);
                }
                bars[i] += (targets[i] > bars[i]) ? 4 : -4;
            }
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public VaultWaveformView(Context context) {
        super(context);
        init();
    }

    public VaultWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VaultWaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(ACCENT_COLOR);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BAR_COUNT; i++) {
            bars[i] = 20 + random.nextInt(60);
            targets[i] = 10 + random.nextInt(90);
        }
    }

    public void setPlaying(boolean isPlaying) {
        this.playing = isPlaying;
        if (isPlaying) {
            handler.post(animateRunnable);
        } else {
            handler.removeCallbacks(animateRunnable);
            // settle bars to low positions
            for (int i = 0; i < BAR_COUNT; i++) targets[i] = 10;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float barWidth = (float) w / (BAR_COUNT * 2 - 1);
        float gap = barWidth;
        float step = barWidth + gap;

        for (int i = 0; i < BAR_COUNT; i++) {
            float barH = (bars[i] / 100f) * h;
            float left = i * step;
            float top = (h - barH) / 2f;
            float right = left + barWidth;
            float bottom = top + barH;
            // rounded corners effect
            float radius = barWidth / 2f;
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(animateRunnable);
    }
}
