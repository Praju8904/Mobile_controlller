package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Full-screen drawing activity for sketch notes.
 * Launch via {@link #createIntent(Context, String)}.
 * On completion, delivers {@link #EXTRA_RESULT_BITMAP_PATH} via setResult.
 */
public class DrawingNoteActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_BITMAP_PATH = "result_bitmap_path";
    public static final String EXTRA_NOTE_COLOR = "note_color";

    private static final String[] PALETTE_COLORS = {
            "#000000", "#FFFFFF", "#EF4444", "#F97316",
            "#FBBF24", "#34D399", "#3B82F6", "#8B5CF6",
            "#F472B6", "#64748B", "#0F172A", "#1E293B"
    };

    private DrawingCanvasView drawingCanvas;
    private ImageButton btnUndo, btnRedo, btnEraser, btnClear, btnDrawBack;
    private Button btnDrawDone;
    private SeekBar seekBrushSize;
    private LinearLayout colorPaletteContainer;

    private String currentColor = "#000000";

    // ── Factory ──────────────────────────────────────────────────────────────

    public static Intent createIntent(Context ctx, String noteColor) {
        Intent intent = new Intent(ctx, DrawingNoteActivity.class);
        if (noteColor != null) {
            intent.putExtra(EXTRA_NOTE_COLOR, noteColor);
        }
        return intent;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing_note);

        drawingCanvas = findViewById(R.id.drawingCanvas);
        btnDrawBack = findViewById(R.id.btnDrawBack);
        btnDrawDone = findViewById(R.id.btnDrawDone);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnClear = findViewById(R.id.btnClear);
        btnEraser = findViewById(R.id.btnEraser);
        seekBrushSize = findViewById(R.id.seekBrushSize);
        colorPaletteContainer = findViewById(R.id.colorPaletteContainer);

        setupButtons();
        setupBrushSize();
        setupColorPalette();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupButtons() {
        btnDrawBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnDrawDone.setOnClickListener(v -> onDoneClick());

        btnUndo.setOnClickListener(v -> drawingCanvas.undo());

        btnRedo.setOnClickListener(v -> drawingCanvas.redo());

        btnClear.setOnClickListener(v -> drawingCanvas.clear());

        btnEraser.setOnClickListener(v -> {
            boolean nowEraser = !drawingCanvas.isEraserActive();
            drawingCanvas.setEraser(nowEraser);
            btnEraser.setAlpha(nowEraser ? 1.0f : 0.5f);
        });
    }

    private void setupBrushSize() {
        // SeekBar progress 0-4 (5 steps) maps to stroke width 5-50px
        seekBrushSize.setMax(4);
        seekBrushSize.setProgress(1); // default ~14px
        drawingCanvas.setBrushSize(mapBrushSize(1));

        seekBrushSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawingCanvas.setBrushSize(mapBrushSize(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /** Maps seekBar progress (0-4) to stroke width 5-50px linearly. */
    private float mapBrushSize(int progress) {
        return 5f + progress * (45f / 4f);
    }

    private void setupColorPalette() {
        colorPaletteContainer.removeAllViews();
        int sizePx = dpToPx(32);
        int marginPx = dpToPx(4);

        for (String hex : PALETTE_COLORS) {
            ImageButton colorBtn = new ImageButton(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            colorBtn.setLayoutParams(lp);
            colorBtn.setPadding(0, 0, 0, 0);
            colorBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            colorBtn.setBackground(makeCircleDrawable(hex, hex.equals(currentColor)));
            final String color = hex;
            colorBtn.setOnClickListener(v -> selectColor(color));
        }
    }

    private void selectColor(String hex) {
        currentColor = hex;
        drawingCanvas.setEraser(false);
        btnEraser.setAlpha(0.5f);
        drawingCanvas.setColor(Color.parseColor(hex));
        // Refresh palette to show selection ring
        setupColorPalette();
    }

    // ── Done / Save ───────────────────────────────────────────────────────────

    private void onDoneClick() {
        Bitmap bmp = drawingCanvas.getBitmap();
        File cacheDir = getCacheDir();
        String fileName = "sketch_" + System.currentTimeMillis() + ".png";
        File outFile = new File(cacheDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save sketch", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_BITMAP_PATH, outFile.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GradientDrawable makeCircleDrawable(String fillHex, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(fillHex));
        if (selected) {
            gd.setStroke(dpToPx(3), Color.WHITE);
        } else {
            gd.setStroke(dpToPx(1), Color.parseColor("#334155"));
        }
        return gd;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
