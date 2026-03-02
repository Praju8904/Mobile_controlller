package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingCanvasView extends View {

    private final List<Path> paths = new ArrayList<>();
    private final List<Paint> paints = new ArrayList<>();
    private final List<Path> undoStack = new ArrayList<>();
    private final List<Paint> undoPaints = new ArrayList<>();

    private Path currentPath;
    private Paint currentPaint;
    private boolean isEraser = false;

    public DrawingCanvasView(Context context) {
        super(context);
        init();
    }

    public DrawingCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null); // required for PorterDuff CLEAR mode
        currentPaint = makePaint(Color.BLACK, 10f, false);
    }

    private Paint makePaint(int color, float strokeWidth, boolean eraser) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeWidth);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (eraser) {
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        return p;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                // snapshot current paint settings into a new Paint for this stroke
                Paint strokePaint = new Paint(currentPaint);
                paths.add(currentPath);
                paints.add(strokePaint);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    invalidate();
                }
                return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < paths.size(); i++) {
            canvas.drawPath(paths.get(i), paints.get(i));
        }
    }

    public void setBrushSize(float size) {
        currentPaint.setStrokeWidth(size);
    }

    public void setColor(int color) {
        isEraser = false;
        currentPaint.setXfermode(null);
        currentPaint.setColor(color);
    }

    public void setEraser(boolean on) {
        isEraser = on;
        if (on) {
            currentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        } else {
            currentPaint.setXfermode(null);
        }
    }

    public boolean isEraserActive() {
        return isEraser;
    }

    public void undo() {
        if (!paths.isEmpty()) {
            undoStack.add(paths.remove(paths.size() - 1));
            undoPaints.add(paints.remove(paints.size() - 1));
            invalidate();
        }
    }

    public void redo() {
        if (!undoStack.isEmpty()) {
            paths.add(undoStack.remove(undoStack.size() - 1));
            paints.add(undoPaints.remove(undoPaints.size() - 1));
            invalidate();
        }
    }

    public void clear() {
        paths.clear();
        paints.clear();
        undoStack.clear();
        undoPaints.clear();
        invalidate();
    }

    public Bitmap getBitmap() {
        Bitmap bmp = Bitmap.createBitmap(
                getWidth() > 0 ? getWidth() : 1,
                getHeight() > 0 ? getHeight() : 1,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        draw(canvas);
        return bmp;
    }
}
