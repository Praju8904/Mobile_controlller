package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE DNA VIEW — Custom View that renders a note's DNA fingerprint.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Wraps NoteDNAGenerator and draws the bitmap into a circular view.
 *  Usage in XML:
 *    <com.prajwal.myfirstapp.NoteDNAView
 *        android:layout_width="48dp"
 *        android:layout_height="48dp" />
 *
 *  Then in code:
 *    noteDnaView.setNoteContent("Note title + body text");
 */
public class NoteDNAView extends View {

    private Bitmap dnaBitmap;
    private Paint bitmapPaint;
    private String currentContent;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════════

    public NoteDNAView(Context context) {
        super(context);
        init();
    }

    public NoteDNAView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NoteDNAView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Set the note content and regenerate the DNA pattern.
     * @param content Plain text content (title + body).
     */
    public void setNoteContent(String content) {
        if (content != null && content.equals(currentContent)) return;
        currentContent = content;
        regenerateDNA();
        invalidate();
    }

    /**
     * Set a pre-generated bitmap directly.
     */
    public void setBitmap(Bitmap bitmap) {
        this.dnaBitmap = bitmap;
        invalidate();
    }

    /**
     * Clear the DNA display.
     */
    public void clear() {
        dnaBitmap = null;
        currentContent = null;
        invalidate();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RENDERING
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dnaBitmap == null || dnaBitmap.isRecycled()) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        // Scale bitmap to fit view, centered
        int size = Math.min(viewWidth, viewHeight);
        float left = (viewWidth - size) / 2f;
        float top = (viewHeight - size) / 2f;

        canvas.drawBitmap(dnaBitmap, null,
                new android.graphics.RectF(left, top, left + size, top + size),
                bitmapPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        regenerateDNA();
    }

    private void regenerateDNA() {
        if (currentContent == null) return;
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) size = 128;

        // Generate at correct size
        dnaBitmap = NoteDNAGenerator.generateDNA(currentContent);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Default to 48dp if no size specified
        int defaultSize = (int) (48 * getResources().getDisplayMetrics().density);

        int width = resolveSize(defaultSize, widthMeasureSpec);
        int height = resolveSize(defaultSize, heightMeasureSpec);

        // Keep square
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }
}
