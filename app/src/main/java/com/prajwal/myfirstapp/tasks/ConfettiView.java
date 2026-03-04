package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A full-screen transparent overlay that renders a burst of confetti particles
 * from a given origin point.
 *
 * <p>Used when a High or Urgent priority task is completed. Particles are rendered
 * as small colored circles that arc outward, are affected by simulated gravity,
 * and fade out over their lifetime.</p>
 *
 * <p>The view self-removes from its parent once all particles have died.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   ConfettiView.launch(activity.getWindow().getDecorView(),
 *       originX, originY, 20, priorityColors);
 * </pre>
 */
public class ConfettiView extends View {

    private static final float GRAVITY = 980f; // dp/s² (simulated)
    private static final long MAX_LIFETIME_MS = 1200L;

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startTime;
    private float density;

    // ── Particle model ──────────────────────────────────────────

    private static class Particle {
        float x, y;       // current position (px)
        float vx, vy;     // velocity (px/s)
        float radius;     // circle radius (px)
        float alpha;      // 0.0 → 1.0
        int color;
        long lifetime;    // total lifetime (ms)
        long elapsed;     // elapsed since spawn (ms)
    }

    // ── Constructor ─────────────────────────────────────────────

    public ConfettiView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        density = context.getResources().getDisplayMetrics().density;
    }

    // ── Static launch helper ────────────────────────────────────

    /**
     * Spawns a confetti burst on the given decor/root view.
     *
     * @param rootView    the root view (typically {@code activity.getWindow().getDecorView()})
     * @param originX     spawn X in screen coordinates
     * @param originY     spawn Y in screen coordinates
     * @param count       number of particles (12 for High, 20 for Urgent)
     * @param palette     array of colors to randomly pick from
     */
    public static void launch(View rootView, float originX, float originY,
                              int count, int[] palette) {
        if (!(rootView instanceof ViewGroup)) return;
        Context ctx = rootView.getContext();

        // Check reduced motion
        if (TaskUtils.isReducedMotionEnabled(ctx)) return;

        ConfettiView confetti = new ConfettiView(ctx);
        ViewGroup parent = (ViewGroup) rootView;
        parent.addView(confetti, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        confetti.spawn(originX, originY, count, palette);
    }

    // ── Particle spawning ───────────────────────────────────────

    private void spawn(float originX, float originY, int count, int[] palette) {
        Random rng = new Random();
        startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = originX;
            p.y = originY;

            // Random outward velocity (250–500 dp/s, fan direction: -120° to -60° = upward arc)
            double angle = Math.toRadians(-60 - rng.nextDouble() * 120); // -60° to -180°
            float speed = (250 + rng.nextFloat() * 250) * density;
            p.vx = (float) (Math.cos(angle) * speed);
            p.vy = (float) (Math.sin(angle) * speed);

            p.radius = (3 + rng.nextFloat() * 4) * density;
            p.alpha = 1f;
            p.color = palette[rng.nextInt(palette.length)];
            p.lifetime = 600 + rng.nextInt(600); // 600-1200 ms
            p.elapsed = 0;

            particles.add(p);
        }

        postInvalidateOnAnimation();
    }

    // ── Drawing ─────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        long dt = now - startTime;

        boolean anyAlive = false;

        for (Particle p : particles) {
            p.elapsed = dt;
            if (p.elapsed >= p.lifetime) continue;

            float t = (float) p.elapsed / p.lifetime;

            // Apply gravity to vertical velocity
            float elapsed_s = p.elapsed / 1000f;
            float currentX = p.x + p.vx * elapsed_s;
            float currentY = p.y + p.vy * elapsed_s + 0.5f * GRAVITY * density * elapsed_s * elapsed_s;

            // Fade out in last 40% of lifetime
            float alpha = t < 0.6f ? 1f : 1f - ((t - 0.6f) / 0.4f);
            alpha = Math.max(0f, Math.min(1f, alpha));

            paint.setColor(p.color);
            paint.setAlpha((int) (alpha * 255));

            canvas.drawCircle(currentX, currentY, p.radius * (1f - t * 0.3f), paint);
            anyAlive = true;
        }

        if (anyAlive) {
            postInvalidateOnAnimation();
        } else {
            // Self-remove from parent
            post(() -> {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) {
                    parent.removeView(this);
                }
            });
        }
    }

    // ── Priority-based color palettes ───────────────────────────

    /** Colors for Urgent priority confetti. */
    public static final int[] PALETTE_URGENT = {
            Color.parseColor("#EF4444"), // crimson
            Color.parseColor("#F97316"), // orange
            Color.parseColor("#FBBF24"), // amber
            Color.parseColor("#FFFFFF"), // white
            Color.parseColor("#F43F5E"), // rose
    };

    /** Colors for High priority confetti. */
    public static final int[] PALETTE_HIGH = {
            Color.parseColor("#F59E0B"), // amber
            Color.parseColor("#FBBF24"), // yellow
            Color.parseColor("#10B981"), // green
            Color.parseColor("#FFFFFF"), // white
    };

    /** Default/generic confetti palette. */
    public static final int[] PALETTE_DEFAULT = {
            Color.parseColor("#3B82F6"), // blue
            Color.parseColor("#10B981"), // green
            Color.parseColor("#8B5CF6"), // purple
            Color.parseColor("#FFFFFF"), // white
    };
}
