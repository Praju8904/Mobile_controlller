package com.prajwal.myfirstapp.tasks;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A custom {@link ReplacementSpan} that draws a strikethrough line that
 * grows from left to right based on a progress value (0.0 → 1.0).
 *
 * <p>Used in the task completion animation (Feature 6) to animate the
 * strikethrough across the task title over ~180 ms.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   SpannableString span = new SpannableString(title);
 *   span.setSpan(new PartialStrikethroughSpan(progress),
 *       0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
 *   textView.setText(span);
 * </pre>
 */
public class PartialStrikethroughSpan extends ReplacementSpan {

    private final float progress; // 0.0 = no line, 1.0 = full strikethrough

    /**
     * @param progress the portion of the text width to strike through, 0.0 to 1.0
     */
    public PartialStrikethroughSpan(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, @Nullable Paint.FontMetricsInt fm) {
        // Return the measured width of the text — we're not changing the text width
        if (fm != null) {
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            fm.top = metrics.top;
            fm.ascent = metrics.ascent;
            fm.descent = metrics.descent;
            fm.bottom = metrics.bottom;
        }
        return Math.round(paint.measureText(text, start, end));
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end,
                     float x, int top, int y, int bottom,
                     @NonNull Paint paint) {

        // Draw the text first, as-is
        canvas.drawText(text, start, end, x, y, paint);

        // Draw the partial strikethrough line
        if (progress > 0f) {
            float textWidth = paint.measureText(text, start, end);
            float lineEnd = x + textWidth * progress;
            float lineY = (top + bottom) / 2f;

            // Save original paint state
            Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f); // thin line
            canvas.drawLine(x, lineY, lineEnd, lineY, paint);

            // Restore
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);
        }
    }
}
