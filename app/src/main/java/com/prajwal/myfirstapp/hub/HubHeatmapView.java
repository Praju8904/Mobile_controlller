package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GitHub-style contribution heatmap for file access activity.
 *
 * 365 squares arranged in weeks (7 rows × 52 columns).
 * Colour intensity: grey (0 accesses) → bright green (high accesses).
 * Tapping a square shows the exact count and date in a small tooltip.
 */
public class HubHeatmapView extends View {

    /** Map from "yyyy-MM-dd" to access count for that day. */
    private Map<String, Integer> data = new HashMap<>();

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint monthLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int EMPTY_COLOR = Color.parseColor("#1F2937");
    private static final int[] LEVEL_COLORS = {
            Color.parseColor("#1F2937"),   // 0 — empty
            Color.parseColor("#064E3B"),   // 1–2
            Color.parseColor("#047857"),   // 3–5
            Color.parseColor("#10B981"),   // 6–10
            Color.parseColor("#34D399"),   // 11–20
            Color.parseColor("#6EE7B7"),   // 21+
    };

    private static final int WEEKS = 53;
    private static final int DAYS_IN_WEEK = 7;
    private static final float MONTH_LABEL_HEIGHT = 28f;

    private float cellSize;
    private float gap;

    // Touch tooltip state
    private int selectedWeek = -1;
    private int selectedDay = -1;
    private float tooltipX, tooltipY;

    // Pre-built day array for the past year
    private String[] dayLabels; // "yyyy-MM-dd" for each cell [week][day]
    private int[] dayValues;

    public HubHeatmapView(Context context) { super(context); init(); }
    public HubHeatmapView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        tooltipPaint.setColor(Color.parseColor("#111827"));
        tooltipPaint.setStyle(Paint.Style.FILL);
        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(24f);
        monthLabelPaint.setColor(Color.parseColor("#9CA3AF"));
        monthLabelPaint.setTextSize(22f);
        buildDayArray();
    }

    public void setData(Map<String, Integer> accessData) {
        this.data = accessData != null ? accessData : new HashMap<>();
        buildDayValues();
        invalidate();
    }

    // ─── Day array construction ───────────────────────────────────────────────

    private void buildDayArray() {
        // 53 weeks × 7 days = 371 cells; we fill from ~1 year ago to today
        dayLabels = new String[WEEKS * DAYS_IN_WEEK];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        // Start from the Sunday one year ago
        cal.add(Calendar.DAY_OF_YEAR, -(WEEKS * DAYS_IN_WEEK - 1));
        for (int i = 0; i < WEEKS * DAYS_IN_WEEK; i++) {
            dayLabels[i] = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        buildDayValues();
    }

    private void buildDayValues() {
        dayValues = new int[WEEKS * DAYS_IN_WEEK];
        if (dayLabels == null) return;
        for (int i = 0; i < dayLabels.length; i++) {
            dayValues[i] = data.getOrDefault(dayLabels[i], 0);
        }
    }

    private int maxValue() {
        int max = 1;
        if (dayValues == null) return max;
        for (int v : dayValues) if (v > max) max = v;
        return max;
    }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float available = w - 8f;
        cellSize = (available / WEEKS) * 0.85f;
        gap = (available / WEEKS) * 0.15f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (w == 0) w = 1000;
        float available = w - 8f;
        float cs = (available / WEEKS) * 0.85f;
        int h = (int) (MONTH_LABEL_HEIGHT + DAYS_IN_WEEK * (cs + (available / WEEKS) * 0.15f));
        setMeasuredDimension(w, h + 8);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dayLabels == null) return;

        int max = maxValue();
        float startX = 4f;
        float startY = MONTH_LABEL_HEIGHT;
        String lastMonth = "";
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMM", Locale.US);
        SimpleDateFormat parseFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (int w = 0; w < WEEKS; w++) {
            float x = startX + w * (cellSize + gap);
            for (int d = 0; d < DAYS_IN_WEEK; d++) {
                int idx = w * DAYS_IN_WEEK + d;
                if (idx >= dayLabels.length) continue;
                int val = dayValues[idx];
                cellPaint.setColor(colorForValue(val, max));
                float y = startY + d * (cellSize + gap);
                RectF rect = new RectF(x, y, x + cellSize, y + cellSize);
                canvas.drawRoundRect(rect, 2, 2, cellPaint);

                // Month label (top of column if month changed)
                if (d == 0) {
                    try {
                        Date date = parseFmt.parse(dayLabels[idx]);
                        String month = monthFmt.format(date);
                        if (!month.equals(lastMonth)) {
                            canvas.drawText(month, x, MONTH_LABEL_HEIGHT - 4, monthLabelPaint);
                            lastMonth = month;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Tooltip
        if (selectedWeek >= 0 && selectedDay >= 0) {
            int idx = selectedWeek * DAYS_IN_WEEK + selectedDay;
            if (idx < dayLabels.length) {
                int val = dayValues[idx];
                String tip = dayLabels[idx] + ": " + val + " access" + (val == 1 ? "" : "es");
                float tipW = tooltipTextPaint.measureText(tip) + 24;
                float tipH = 44f;
                float tx = Math.min(tooltipX, getWidth() - tipW - 8);
                tx = Math.max(tx, 8);
                float ty = Math.max(tooltipY - tipH - 8, 0);
                RectF rect = new RectF(tx, ty, tx + tipW, ty + tipH);
                canvas.drawRoundRect(rect, 6, 6, tooltipPaint);
                canvas.drawText(tip, tx + 12, ty + 30, tooltipTextPaint);
            }
        }
    }

    private int colorForValue(int val, int max) {
        if (val == 0) return LEVEL_COLORS[0];
        float ratio = (float) val / max;
        int level = (int) (ratio * (LEVEL_COLORS.length - 2)) + 1;
        return LEVEL_COLORS[Math.min(level, LEVEL_COLORS.length - 1)];
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - 4f;
            float y = event.getY() - MONTH_LABEL_HEIGHT;
            int w = (int) (x / (cellSize + gap));
            int d = (int) (y / (cellSize + gap));
            if (w >= 0 && w < WEEKS && d >= 0 && d < DAYS_IN_WEEK) {
                selectedWeek = w;
                selectedDay = d;
                tooltipX = event.getX();
                tooltipY = event.getY();
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
