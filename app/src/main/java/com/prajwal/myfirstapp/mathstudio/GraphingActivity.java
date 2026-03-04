package com.prajwal.myfirstapp.mathstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.prajwal.myfirstapp.R;

public class GraphingActivity extends AppCompatActivity {

    private GraphView graphView;
    private EditText etFunc1, etFunc2, etFunc3, etFunc4;
    private EditText etXMin, etXMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);

        graphView = findViewById(R.id.graphView);
        etFunc1 = findViewById(R.id.etFunc1);
        etFunc2 = findViewById(R.id.etFunc2);
        etFunc3 = findViewById(R.id.etFunc3);
        etFunc4 = findViewById(R.id.etFunc4);
        etXMin = findViewById(R.id.etXMin);
        etXMax = findViewById(R.id.etXMax);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnPlot = findViewById(R.id.btnPlot);
        if (btnPlot != null) btnPlot.setOnClickListener(v -> plotFunctions());

        View btnReset = findViewById(R.id.btnReset);
        if (btnReset != null) btnReset.setOnClickListener(v -> {
            if (graphView != null) {
                graphView.resetView();
                graphView.invalidate();
            }
        });

        // Plot default
        if (etFunc1 != null) etFunc1.setText("sin(x)");
        plotFunctions();
    }

    private void plotFunctions() {
        if (graphView == null) return;
        String[] funcs = new String[4];
        if (etFunc1 != null) funcs[0] = etFunc1.getText().toString().trim();
        if (etFunc2 != null) funcs[1] = etFunc2.getText().toString().trim();
        if (etFunc3 != null) funcs[2] = etFunc3.getText().toString().trim();
        if (etFunc4 != null) funcs[3] = etFunc4.getText().toString().trim();

        try {
            float xMin = Float.parseFloat(etXMin != null ? etXMin.getText().toString() : "-10");
            float xMax = Float.parseFloat(etXMax != null ? etXMax.getText().toString() : "10");
            graphView.setXRange(xMin, xMax);
        } catch (NumberFormatException ignored) {}

        graphView.setFunctions(funcs);
        graphView.invalidate();
    }

    public static class GraphView extends View {
        private float xMin = -10f, xMax = 10f, yMin = -10f, yMax = 10f;
        private String[] functions = new String[4];
        private final int[] colors = {0xFF3B82F6, 0xFF8B5CF6, 0xFF06B6D4, 0xFFF59E0B};
        private final Paint bgPaint = new Paint();
        private final Paint gridPaint = new Paint();
        private final Paint axisPaint = new Paint();
        private final Paint[] funcPaints = new Paint[4];
        private final Paint labelPaint = new Paint();

        private ScaleGestureDetector scaleDetector;
        private GestureDetector gestureDetector;
        private float lastTouchX, lastTouchY;
        private boolean isScaling = false;

        public GraphView(Context context) {
            super(context);
            init(context);
        }

        public GraphView(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public GraphView(Context context, android.util.AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init(context);
        }

        private void init(Context context) {
            bgPaint.setColor(0xFF0A0F1E);
            bgPaint.setStyle(Paint.Style.FILL);

            gridPaint.setColor(0xFF1E2A40);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(1f);

            axisPaint.setColor(0xFF374151);
            axisPaint.setStyle(Paint.Style.STROKE);
            axisPaint.setStrokeWidth(2f);

            labelPaint.setColor(0xFF6B7280);
            labelPaint.setTextSize(24f);
            labelPaint.setAntiAlias(true);

            for (int i = 0; i < 4; i++) {
                funcPaints[i] = new Paint();
                funcPaints[i].setColor(colors[i]);
                funcPaints[i].setStyle(Paint.Style.STROKE);
                funcPaints[i].setStrokeWidth(3f);
                funcPaints[i].setAntiAlias(true);
                funcPaints[i].setStrokeCap(Paint.Cap.ROUND);
                funcPaints[i].setStrokeJoin(Paint.Join.ROUND);
            }

            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float scale = 1f / detector.getScaleFactor();
                    float cx = (xMin + xMax) / 2f;
                    float cy = (yMin + yMax) / 2f;
                    float halfW = (xMax - xMin) / 2f * scale;
                    float halfH = (yMax - yMin) / 2f * scale;
                    xMin = cx - halfW; xMax = cx + halfW;
                    yMin = cy - halfH; yMax = cy + halfH;
                    invalidate();
                    return true;
                }
                @Override public boolean onScaleBegin(ScaleGestureDetector d) { isScaling = true; return true; }
                @Override public void onScaleEnd(ScaleGestureDetector d) { isScaling = false; }
            });

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (!isScaling) {
                        float w = getWidth(), h = getHeight();
                        if (w > 0 && h > 0) {
                            float panX = dx / w * (xMax - xMin);
                            float panY = -dy / h * (yMax - yMin);
                            xMin += panX; xMax += panX;
                            yMin += panY; yMax += panY;
                            invalidate();
                        }
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        }

        public void setFunctions(String[] funcs) {
            this.functions = funcs.clone();
        }

        public void setXRange(float min, float max) {
            this.xMin = min;
            this.xMax = max;
        }

        public void resetView() {
            xMin = -10f; xMax = 10f; yMin = -10f; yMax = 10f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;

            canvas.drawRect(0, 0, w, h, bgPaint);
            drawGrid(canvas, w, h);
            drawAxes(canvas, w, h);
            for (int i = 0; i < 4; i++) {
                if (functions != null && i < functions.length
                    && functions[i] != null && !functions[i].isEmpty()) {
                    drawFunction(canvas, functions[i], funcPaints[i], w, h);
                }
            }
        }

        private void drawGrid(Canvas canvas, int w, int h) {
            float xStep = niceStep(xMax - xMin);
            float yStep = niceStep(yMax - yMin);
            float xStart = (float)(Math.ceil(xMin / xStep) * xStep);
            float yStart = (float)(Math.ceil(yMin / yStep) * yStep);

            for (float x = xStart; x <= xMax; x += xStep) {
                float px = toScreenX(x, w);
                canvas.drawLine(px, 0, px, h, gridPaint);
            }
            for (float y = yStart; y <= yMax; y += yStep) {
                float py = toScreenY(y, h);
                canvas.drawLine(0, py, w, py, gridPaint);
            }
        }

        private void drawAxes(Canvas canvas, int w, int h) {
            float axisX = toScreenX(0, w);
            float axisY = toScreenY(0, h);
            canvas.drawLine(axisX, 0, axisX, h, axisPaint);
            canvas.drawLine(0, axisY, w, axisY, axisPaint);

            // Labels
            float xStep = niceStep(xMax - xMin);
            float xStart = (float)(Math.ceil(xMin / xStep) * xStep);
            for (float x = xStart; x <= xMax; x += xStep) {
                if (Math.abs(x) < xStep * 0.01f) continue;
                float px = toScreenX(x, w);
                String label = ExpressionEngine.formatResult(x);
                canvas.drawText(label, px + 3, axisY - 5, labelPaint);
            }
        }

        private void drawFunction(Canvas canvas, String func, Paint paint, int w, int h) {
            int points = 400;
            float step = (xMax - xMin) / points;
            Path path = new Path();
            boolean started = false;

            for (int i = 0; i <= points; i++) {
                float x = xMin + i * step;
                try {
                    String expr = func.replace("x", "(" + x + ")");
                    double y = ExpressionEngine.evaluate(expr, ExpressionEngine.AngleMode.RAD);
                    if (Double.isNaN(y) || Double.isInfinite(y)) {
                        started = false;
                        continue;
                    }
                    float px = toScreenX(x, w);
                    float py = toScreenY((float) y, h);
                    if (!started) {
                        path.moveTo(px, py);
                        started = true;
                    } else {
                        // Check for discontinuity
                        if (Math.abs(y) > (yMax - yMin) * 5) {
                            started = false;
                            continue;
                        }
                        path.lineTo(px, py);
                    }
                } catch (Exception e) {
                    started = false;
                }
            }
            canvas.drawPath(path, paint);
        }

        private float toScreenX(float x, int w) {
            return (x - xMin) / (xMax - xMin) * w;
        }

        private float toScreenY(float y, int h) {
            return (1 - (y - yMin) / (yMax - yMin)) * h;
        }

        private float niceStep(float range) {
            float raw = range / 10f;
            float mag = (float) Math.pow(10, Math.floor(Math.log10(raw)));
            float norm = raw / mag;
            if (norm < 1.5f) return mag;
            if (norm < 3.5f) return 2 * mag;
            if (norm < 7.5f) return 5 * mag;
            return 10 * mag;
        }
    }
}
