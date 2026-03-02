package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom View that renders a visual DNA pattern derived from a file's content hash.
 * Each byte of the hash maps to a colored rectangle in a grid.
 */
public class HubFileDnaView extends View {

    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 8;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] colors = new int[GRID_COLS * GRID_ROWS];
    private boolean hasData = false;

    public HubFileDnaView(Context context) { super(context); init(); }
    public HubFileDnaView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public HubFileDnaView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        // Default: fill with gray
        for (int i = 0; i < colors.length; i++) colors[i] = Color.parseColor("#374151");
    }

    /** Set the hash string (hex). Colors are derived from hash bytes. */
    public void setHash(String hash) {
        if (hash == null || hash.isEmpty()) { hasData = false; invalidate(); return; }
        hasData = true;
        // Parse hex bytes
        byte[] bytes = hexToBytes(hash);
        for (int i = 0; i < colors.length; i++) {
            if (bytes.length == 0) { colors[i] = Color.GRAY; continue; }
            int byteVal = bytes[i % bytes.length] & 0xFF;
            colors[i] = byteToColor(byteVal, i);
        }
        invalidate();
    }

    private int byteToColor(int b, int idx) {
        // Map byte to a hue; vary saturation by position
        float hue = (b * 360.0f / 256.0f) % 360f;
        float saturation = 0.5f + (((idx * 17 + b) % 50) / 100.0f);
        float brightness = 0.55f + (((idx * 31 + b) % 30) / 100.0f);
        return Color.HSVToColor(new float[]{hue, saturation, brightness});
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // 120dp x 120dp
        float density = getResources().getDisplayMetrics().density;
        int size = (int)(120 * density);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(); int h = getHeight();
        if (w == 0 || h == 0) return;

        float cellW = (float) w / GRID_COLS;
        float cellH = (float) h / GRID_ROWS;
        float padding = 2f;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = row * GRID_COLS + col;
                paint.setColor(colors[idx]);
                float left = col * cellW + padding;
                float top = row * cellH + padding;
                float right = left + cellW - padding * 2;
                float bottom = top + cellH - padding * 2;

                // Alternate between rect and circle for visual interest
                if ((row + col) % 3 == 0) {
                    canvas.drawCircle((left + right) / 2, (top + bottom) / 2,
                            (right - left) / 2, paint);
                } else {
                    canvas.drawRoundRect(left, top, right, bottom, 4f, 4f, paint);
                }
            }
        }

        // Border
        paint.setColor(Color.parseColor("#374151"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(1, 1, w - 1, h - 1, 8f, 8f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return new byte[0];
        int len = hex.length();
        if (len == 0) return new byte[0];
        // Make sure length is even (append "0" to preserve leading hex digits)
        if (len % 2 != 0) hex = hex + "0";
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            try {
                result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
}
