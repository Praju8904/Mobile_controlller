package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  CONCEPT MAP VIEW — Interactive mind-map / concept-map of related notes.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Renders a force-directed graph of notes with:
 *  • Central note (selected) in the center
 *  • Related notes connected by lines
 *  • Color-coded by category
 *  • Tap to select a node → callback
 *  • Pinch-to-zoom & pan
 *  • Animated layout (simple spring simulation)
 *
 *  Usage:
 *    conceptMapView.setNotes(currentNote, relatedNotes);
 *    conceptMapView.setOnNodeTapListener(note -> { ... });
 */
public class ConceptMapView extends View {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LISTENER
    // ═══════════════════════════════════════════════════════════════════════════════

    public interface OnNodeTapListener {
        void onNodeTapped(Note note);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NODE MODEL
    // ═══════════════════════════════════════════════════════════════════════════════

    private static class MapNode {
        Note note;
        float x, y;          // Position in canvas coords
        float vx, vy;        // Velocity (for spring simulation)
        float radius;
        int color;
        boolean isCenter;

        MapNode(Note note, float x, float y, boolean isCenter) {
            this.note = note;
            this.x = x;
            this.y = y;
            this.isCenter = isCenter;
            this.radius = isCenter ? 60 : 45;
            this.color = getCategoryColor(note.category);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private List<MapNode> nodes = new ArrayList<>();
    private OnNodeTapListener onNodeTapListener;

    // Paints
    private Paint nodePaint, textPaint, linePaint, labelBgPaint, shadowPaint;

    // Transform (zoom + pan)
    private float scaleFactor = 1f;
    private float translateX = 0, translateY = 0;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // Animation
    private boolean layoutRunning = false;
    private int layoutIterations = 0;
    private static final int MAX_LAYOUT_ITERATIONS = 100;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════════

    public ConceptMapView(Context context) {
        super(context);
        init(context);
    }

    public ConceptMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ConceptMapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Node fill
        nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        nodePaint.setStyle(Paint.Style.FILL);

        // Node titles
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Connection lines
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f);
        linePaint.setColor(Color.parseColor("#334155"));

        // Label background
        labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(Color.parseColor("#1E293B"));

        // Shadow
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.parseColor("#20000000"));

        // Gesture detectors
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.3f, Math.min(3f, scaleFactor));
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                translateX -= distanceX / scaleFactor;
                translateY -= distanceY / scaleFactor;
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Set the central note and its related notes.
     */
    public void setNotes(Note centerNote, List<Note> relatedNotes) {
        nodes.clear();
        if (centerNote == null) {
            invalidate();
            return;
        }

        float cx = getWidth() > 0 ? getWidth() / 2f : 500;
        float cy = getHeight() > 0 ? getHeight() / 2f : 500;

        // Center node
        nodes.add(new MapNode(centerNote, cx, cy, true));

        // Related nodes — arrange in a circle
        int count = relatedNotes != null ? relatedNotes.size() : 0;
        float radius = 200;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i / count) - Math.PI / 2;
            float x = cx + (float) (Math.cos(angle) * radius);
            float y = cy + (float) (Math.sin(angle) * radius);
            nodes.add(new MapNode(relatedNotes.get(i), x, y, false));
        }

        // Start layout animation
        layoutRunning = true;
        layoutIterations = 0;
        translateX = 0;
        translateY = 0;
        scaleFactor = 1f;
        invalidate();
    }

    public void setOnNodeTapListener(OnNodeTapListener listener) {
        this.onNodeTapListener = listener;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DRAWING
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#0A0E21"));

        if (nodes.isEmpty()) {
            // Draw placeholder text
            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(36);
            canvas.drawText("No related notes found", getWidth() / 2f, getHeight() / 2f, textPaint);
            textPaint.setTextSize(28);
            return;
        }

        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scaleFactor, scaleFactor);
        canvas.translate(-getWidth() / 2f + translateX, -getHeight() / 2f + translateY);

        // Draw connections first (behind nodes)
        if (nodes.size() > 1) {
            MapNode center = nodes.get(0);
            for (int i = 1; i < nodes.size(); i++) {
                MapNode node = nodes.get(i);
                linePaint.setColor(node.color);
                linePaint.setAlpha(60);
                linePaint.setStrokeWidth(2f);
                canvas.drawLine(center.x, center.y, node.x, node.y, linePaint);

                // Draw small dot at midpoint
                float mx = (center.x + node.x) / 2f;
                float my = (center.y + node.y) / 2f;
                nodePaint.setColor(node.color);
                nodePaint.setAlpha(40);
                canvas.drawCircle(mx, my, 4, nodePaint);
            }
        }

        // Draw nodes
        for (MapNode node : nodes) {
            drawNode(canvas, node);
        }

        canvas.restore();

        // Run layout simulation
        if (layoutRunning && layoutIterations < MAX_LAYOUT_ITERATIONS) {
            runLayoutStep();
            layoutIterations++;
            postInvalidateDelayed(16);
        } else {
            layoutRunning = false;
        }
    }

    private void drawNode(Canvas canvas, MapNode node) {
        // Shadow
        shadowPaint.setAlpha(40);
        canvas.drawCircle(node.x + 3, node.y + 5, node.radius + 2, shadowPaint);

        // Node circle
        nodePaint.setColor(node.color);
        nodePaint.setAlpha(node.isCenter ? 220 : 180);
        canvas.drawCircle(node.x, node.y, node.radius, nodePaint);

        // Glow ring for center
        if (node.isCenter) {
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(3);
            glowPaint.setColor(node.color);
            glowPaint.setAlpha(100);
            canvas.drawCircle(node.x, node.y, node.radius + 6, glowPaint);
        }

        // Title text (truncated)
        String title = node.note.title != null ? node.note.title : "Untitled";
        if (title.length() > 12) title = title.substring(0, 11) + "…";

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(node.isCenter ? 26 : 22);
        canvas.drawText(title, node.x, node.y + 6, textPaint);

        // Category label below
        if (node.note.category != null) {
            textPaint.setTextSize(16);
            textPaint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawText(node.note.category, node.x, node.y + node.radius + 18, textPaint);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FORCE-DIRECTED LAYOUT
    // ═══════════════════════════════════════════════════════════════════════════════

    private void runLayoutStep() {
        if (nodes.size() <= 1) return;

        float damping = 0.9f;
        float springLength = 200;
        float springK = 0.005f;
        float repulseK = 10000;

        // Repulsion between all pairs
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                MapNode a = nodes.get(i);
                MapNode b = nodes.get(j);

                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;

                float force = repulseK / (dist * dist);
                float fx = (dx / dist) * force;
                float fy = (dy / dist) * force;

                if (!a.isCenter) { a.vx -= fx; a.vy -= fy; }
                if (!b.isCenter) { b.vx += fx; b.vy += fy; }
            }
        }

        // Spring attraction (center to each)
        MapNode center = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            MapNode node = nodes.get(i);
            float dx = node.x - center.x;
            float dy = node.y - center.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 1) dist = 1;

            float displacement = dist - springLength;
            float force = springK * displacement;

            node.vx -= (dx / dist) * force;
            node.vy -= (dy / dist) * force;
        }

        // Apply velocities
        for (MapNode node : nodes) {
            if (node.isCenter) continue;
            node.vx *= damping;
            node.vy *= damping;
            node.x += node.vx;
            node.y += node.vy;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TOUCH
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private void handleTap(float screenX, float screenY) {
        // Transform screen coords to canvas coords
        float cx = (screenX - getWidth() / 2f) / scaleFactor + getWidth() / 2f - translateX;
        float cy = (screenY - getHeight() / 2f) / scaleFactor + getHeight() / 2f - translateY;

        for (MapNode node : nodes) {
            float dx = cx - node.x;
            float dy = cy - node.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= node.radius) {
                if (onNodeTapListener != null) {
                    onNodeTapListener.onNodeTapped(node.note);
                }
                return;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CATEGORY COLORS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static int getCategoryColor(String category) {
        if (category == null) return Color.parseColor("#64748B");
        switch (category) {
            case "Study":    return Color.parseColor("#3B82F6"); // Blue
            case "Work":     return Color.parseColor("#F59E0B"); // Amber
            case "Personal": return Color.parseColor("#10B981"); // Emerald
            case "Ideas":    return Color.parseColor("#A855F7"); // Purple
            case "Finance":  return Color.parseColor("#EF4444"); // Red
            case "Health":   return Color.parseColor("#06B6D4"); // Cyan
            default:         return Color.parseColor("#64748B"); // Gray
        }
    }
}
