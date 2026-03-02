package com.prajwal.myfirstapp.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Premium image editor activity for the Personal Media Vault.
 *
 * Receives a file_id via Intent extra "file_id", decrypts the image to memory,
 * and provides crop, rotate, flip, brightness, contrast, and saturation editing tools.
 *
 * NOTE: MediaVaultRepository requires an importFileFromBytes(byte[], String, String) method.
 * If not yet present, use the local helper importEditedFile() below which writes to a temp
 * file and calls the existing encryption + saveFile path directly.
 */
public class VaultImageEditorActivity extends Activity {

    private static final String TAG = "VaultImageEditor";
    private static final String EXTRA_FILE_ID = "file_id";
    private static final int MAX_UNDO_STACK = 10;

    // ─── Repository / model ──────────────────────────────────────
    private MediaVaultRepository repo;
    private VaultFileItem fileItem;

    // ─── Bitmaps ─────────────────────────────────────────────────
    private Bitmap originalBitmap;   // never mutated – used for Reset
    private Bitmap currentBitmap;    // current editing state

    // ─── Undo / Redo stacks ──────────────────────────────────────
    private final Deque<Bitmap> undoStack = new ArrayDeque<>();
    private final Deque<Bitmap> redoStack = new ArrayDeque<>();

    // ─── Adjustment state ────────────────────────────────────────
    private int brightness  = 0;   // -100 … +100
    private int contrast    = 0;   // -100 … +100
    private int saturation  = 100; // 0 … 200  (100 = neutral)

    // ─── UI ──────────────────────────────────────────────────────
    private ImageView imageView;
    private TextView btnUndo, btnRedo;
    private LinearLayout adjustPanel;
    private SeekBar seekBrightness, seekContrast, seekSaturation;

    // ─── Tool tabs ───────────────────────────────────────────────
    private static final int TAB_NONE    = -1;
    private static final int TAB_CROP    = 0;
    private static final int TAB_ROTATE  = 1;
    private static final int TAB_FLIP    = 2;
    private static final int TAB_ADJUST  = 3;
    private int activeTab = TAB_NONE;

    // ─── Lifecycle ───────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        repo = MediaVaultRepository.getInstance(this);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        if (fileId == null || fileId.isEmpty()) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Find the file item
        for (VaultFileItem f : repo.getAllFiles()) {
            if (fileId.equals(f.id)) { fileItem = f; break; }
        }
        if (fileItem == null) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildUi();
        loadImageAsync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recycleBitmaps();
    }

    // ─── UI construction ─────────────────────────────────────────

    private void buildUi() {
        // Root
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // Top bar
        LinearLayout topBar = buildTopBar();
        root.addView(topBar);

        // ImageView (flex 1)
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(imageView, ivParams);

        // Adjustment panel (hidden until TAB_ADJUST)
        adjustPanel = buildAdjustPanel();
        adjustPanel.setVisibility(View.GONE);
        root.addView(adjustPanel);

        // Bottom tool bar
        HorizontalScrollView toolScroll = buildToolBar();
        root.addView(toolScroll);
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        // Back
        TextView btnBack = makeTextButton("←", v -> finish());
        bar.addView(btnBack);

        // Title (flex)
        TextView title = new TextView(this);
        title.setText("Edit");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, tp);

        // Undo
        btnUndo = makeTextButton("↩", v -> performUndo());
        bar.addView(btnUndo);

        // Redo
        btnRedo = makeTextButton("↪", v -> performRedo());
        bar.addView(btnRedo);

        // Reset
        TextView btnReset = makeTextButton("↺", v -> confirmReset());
        bar.addView(btnReset);

        // Save
        TextView btnSave = makeTextButton("Save", v -> showSaveOptions());
        bar.addView(btnSave);

        return bar;
    }

    private HorizontalScrollView buildToolBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setBackgroundColor(Color.parseColor("#1A1A1A"));
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));

        String[] labels = {"Crop", "Rotate", "Flip", "Adjust"};
        int[] tabs = {TAB_CROP, TAB_ROTATE, TAB_FLIP, TAB_ADJUST};
        for (int i = 0; i < labels.length; i++) {
            final int tab = tabs[i];
            TextView btn = makeTextButton(labels[i], v -> onTabSelected(tab));
            btn.setPadding(dp(18), dp(10), dp(18), dp(10));
            row.addView(btn);
        }

        hsv.addView(row);
        return hsv;
    }

    private LinearLayout buildAdjustPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#111111"));
        panel.setPadding(dp(16), dp(8), dp(16), dp(8));

        panel.addView(buildLabeledSeekBar("Brightness", 0, 200, 100,
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) { brightness = progress - 100; applyAdjustments(); }
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar) {}
                    @Override public void onStopTrackingTouch(SeekBar bar) {}
                }));

        panel.addView(buildLabeledSeekBar("Contrast", 0, 200, 100,
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) { contrast = progress - 100; applyAdjustments(); }
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar) {}
                    @Override public void onStopTrackingTouch(SeekBar bar) {}
                }));

        panel.addView(buildLabeledSeekBar("Saturation", 0, 200, 100,
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) { saturation = progress; applyAdjustments(); }
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar) {}
                    @Override public void onStopTrackingTouch(SeekBar bar) {}
                }));

        return panel;
    }

    private LinearLayout buildLabeledSeekBar(String label, int min, int max, int initial,
                                              SeekBar.OnSeekBarChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.LTGRAY);
        lbl.setTextSize(13);
        lbl.setMinWidth(dp(90));
        row.addView(lbl);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        seek.setOnSeekBarChangeListener(listener);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(seek, sp);

        return row;
    }

    // ─── Image loading ────────────────────────────────────────────

    private void loadImageAsync() {
        new Thread(() -> {
            byte[] bytes = repo.decryptFileToMemory(fileItem);
            if (bytes == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not decrypt image", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not decode image", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            runOnUiThread(() -> {
                originalBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false);
                setCurrentBitmap(bmp.copy(Bitmap.Config.ARGB_8888, true));
            });
        }).start();
    }

    // ─── Tab handling ────────────────────────────────────────────

    private void onTabSelected(int tab) {
        if (tab == TAB_CROP) {
            showCropDialog();
        } else if (tab == TAB_ROTATE) {
            showRotateDialog();
        } else if (tab == TAB_FLIP) {
            showFlipDialog();
        } else if (tab == TAB_ADJUST) {
            boolean showing = adjustPanel.getVisibility() == View.VISIBLE;
            adjustPanel.setVisibility(showing ? View.GONE : View.VISIBLE);
            activeTab = showing ? TAB_NONE : TAB_ADJUST;
        }
    }

    // ─── Crop ────────────────────────────────────────────────────

    private void showCropDialog() {
        if (currentBitmap == null) return;
        String[] options = {"Original", "1:1", "4:3", "16:9", "3:4"};
        new AlertDialog.Builder(this)
                .setTitle("Crop Ratio")
                .setItems(options, (dialog, which) -> {
                    float[] ratio = getRatio(which);
                    applyCrop(ratio[0], ratio[1]);
                })
                .show();
    }

    private float[] getRatio(int idx) {
        switch (idx) {
            case 1: return new float[]{1f, 1f};
            case 2: return new float[]{4f, 3f};
            case 3: return new float[]{16f, 9f};
            case 4: return new float[]{3f, 4f};
            default: return new float[]{0f, 0f}; // original – no crop
        }
    }

    private void applyCrop(float ratioW, float ratioH) {
        if (currentBitmap == null) return;
        pushUndo();
        if (ratioW == 0f && ratioH == 0f) {
            // Reset to original aspect – nothing to do
            refreshImageView();
            return;
        }
        int srcW = currentBitmap.getWidth();
        int srcH = currentBitmap.getHeight();
        int newW, newH;
        if ((float) srcW / srcH > ratioW / ratioH) {
            newH = srcH;
            newW = (int) (srcH * ratioW / ratioH);
        } else {
            newW = srcW;
            newH = (int) (srcW * ratioH / ratioW);
        }
        int x = (srcW - newW) / 2;
        int y = (srcH - newH) / 2;
        Bitmap cropped = Bitmap.createBitmap(currentBitmap, x, y, newW, newH);
        setCurrentBitmap(cropped);
    }

    // ─── Rotate ──────────────────────────────────────────────────

    private void showRotateDialog() {
        if (currentBitmap == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Rotate")
                .setItems(new String[]{"Rotate Left (−90°)", "Rotate Right (+90°)"}, (dialog, which) -> {
                    if (which == 0) applyRotate(-90f);
                    else applyRotate(90f);
                })
                .show();
    }

    private void applyRotate(float degrees) {
        if (currentBitmap == null) return;
        pushUndo();
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), m, true);
        setCurrentBitmap(rotated);
    }

    // ─── Flip ────────────────────────────────────────────────────

    private void showFlipDialog() {
        if (currentBitmap == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Flip")
                .setItems(new String[]{"Flip Horizontal", "Flip Vertical"}, (dialog, which) -> {
                    if (which == 0) applyFlip(true, false);
                    else applyFlip(false, true);
                })
                .show();
    }

    private void applyFlip(boolean horizontal, boolean vertical) {
        if (currentBitmap == null) return;
        pushUndo();
        Matrix m = new Matrix();
        m.preScale(horizontal ? -1f : 1f, vertical ? -1f : 1f);
        Bitmap flipped = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), m, true);
        setCurrentBitmap(flipped);
    }

    // ─── Brightness / Contrast / Saturation ──────────────────────

    /**
     * Applies brightness, contrast, and saturation adjustments to currentBitmap
     * and updates the ImageView via a ColorMatrixColorFilter (non-destructive preview).
     */
    private void applyAdjustments() {
        if (imageView == null || currentBitmap == null) return;

        // Saturation
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(saturation / 100f);

        // Brightness: translate each channel
        ColorMatrix bright = new ColorMatrix();
        float b = brightness * 1.5f; // scale to visible range
        bright.set(new float[]{
                1, 0, 0, 0, b,
                0, 1, 0, 0, b,
                0, 0, 1, 0, b,
                0, 0, 0, 1, 0
        });

        // Contrast: scale channels around mid-point
        float c = (contrast + 100f) / 100f; // 0..2, 1=neutral
        float translate = 128f * (1f - c);
        ColorMatrix cont = new ColorMatrix();
        cont.set(new float[]{
                c, 0, 0, 0, translate,
                0, c, 0, 0, translate,
                0, 0, c, 0, translate,
                0, 0, 0, 1, 0
        });

        cm.postConcat(bright);
        cm.postConcat(cont);

        imageView.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    /**
     * Bakes the current ColorMatrix adjustments into a new Bitmap and pushes to undo stack.
     * Call before switching tools so that subsequent edits start from the adjusted state.
     */
    private void bakeAdjustments() {
        if (currentBitmap == null) return;
        if (brightness == 0 && contrast == 0 && saturation == 100) return; // nothing to bake

        pushUndo();
        android.graphics.Canvas canvas = new android.graphics.Canvas(currentBitmap);
        android.graphics.Paint paint = new android.graphics.Paint();

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(saturation / 100f);

        float b = brightness * 1.5f;
        ColorMatrix bright = new ColorMatrix();
        bright.set(new float[]{1,0,0,0,b, 0,1,0,0,b, 0,0,1,0,b, 0,0,0,1,0});

        float c2 = (contrast + 100f) / 100f;
        float t = 128f * (1f - c2);
        ColorMatrix cont = new ColorMatrix();
        cont.set(new float[]{c2,0,0,0,t, 0,c2,0,0,t, 0,0,c2,0,t, 0,0,0,1,0});

        cm.postConcat(bright);
        cm.postConcat(cont);

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(currentBitmap, 0, 0, paint);

        imageView.clearColorFilter();
        brightness = 0; contrast = 0; saturation = 100;
        refreshSeekBars();
    }

    private void refreshSeekBars() {
        LinearLayout panel = adjustPanel;
        if (panel == null) return;
        // Reset seekbars to neutral (progress = 100 for -100…+100 range)
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    if (row.getChildAt(j) instanceof SeekBar) {
                        ((SeekBar) row.getChildAt(j)).setProgress(100);
                    }
                }
            }
        }
    }

    // ─── Undo / Redo ─────────────────────────────────────────────

    private void pushUndo() {
        bakeAdjustments(); // bake any in-progress adjustments first
        if (currentBitmap == null) return;
        // Enforce limit before pushing: drop the oldest (bottom) entry if at capacity.
        if (undoStack.size() >= MAX_UNDO_STACK) {
            Bitmap oldest = ((ArrayDeque<Bitmap>) undoStack).peekLast();
            if (oldest != null && !oldest.isRecycled()) oldest.recycle();
            ((ArrayDeque<Bitmap>) undoStack).pollLast();
        }
        undoStack.push(currentBitmap.copy(Bitmap.Config.ARGB_8888, false));
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) return;
        bakeAdjustments();
        redoStack.push(currentBitmap.copy(Bitmap.Config.ARGB_8888, false));
        Bitmap prev = undoStack.pop();
        setCurrentBitmap(prev);
        updateUndoRedoButtons();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        bakeAdjustments();
        undoStack.push(currentBitmap.copy(Bitmap.Config.ARGB_8888, false));
        Bitmap next = redoStack.pop();
        setCurrentBitmap(next);
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        if (btnUndo != null) btnUndo.setAlpha(undoStack.isEmpty() ? 0.35f : 1f);
        if (btnRedo != null) btnRedo.setAlpha(redoStack.isEmpty() ? 0.35f : 1f);
    }

    // ─── Reset ───────────────────────────────────────────────────

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset to Original")
                .setMessage("Discard all edits and restore the original image?")
                .setPositiveButton("Reset", (d, w) -> resetToOriginal())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetToOriginal() {
        if (originalBitmap == null) return;
        undoStack.clear();
        redoStack.clear();
        brightness = 0; contrast = 0; saturation = 100;
        refreshSeekBars();
        imageView.clearColorFilter();
        setCurrentBitmap(originalBitmap.copy(Bitmap.Config.ARGB_8888, true));
        updateUndoRedoButtons();
        Toast.makeText(this, "Reset to original", Toast.LENGTH_SHORT).show();
    }

    // ─── Save ────────────────────────────────────────────────────

    private void showSaveOptions() {
        if (currentBitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Save")
                .setItems(new String[]{"Save as New Copy", "Replace Original"}, (dialog, which) -> {
                    if (which == 0) saveAsNewCopy();
                    else confirmReplaceOriginal();
                })
                .show();
    }

    private void saveAsNewCopy() {
        bakeAdjustments();
        new Thread(() -> {
            byte[] jpegBytes = bitmapToJpegBytes(currentBitmap);
            if (jpegBytes == null) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to encode image", Toast.LENGTH_SHORT).show());
                return;
            }
            String newName = buildCopyName(fileItem.originalFileName);
            // Use the repository's importFileFromBytes so thumbnails and metadata are handled there.
            VaultFileItem saved = repo.importFileFromBytes(jpegBytes, newName, "image/jpeg");
            // Persist the inherited albumId (importFileFromBytes doesn't know it).
            if (saved != null && fileItem.albumId != null) {
                saved.albumId = fileItem.albumId;
                repo.updateFile(saved);
            }
            runOnUiThread(() -> {
                if (saved != null) {
                    Toast.makeText(this, "Saved as copy: " + newName, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void confirmReplaceOriginal() {
        new AlertDialog.Builder(this)
                .setTitle("Replace Original")
                .setMessage("This will permanently overwrite the original encrypted file. Continue?")
                .setPositiveButton("Replace", (d, w) -> replaceOriginal())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void replaceOriginal() {
        bakeAdjustments();
        new Thread(() -> {
            byte[] jpegBytes = bitmapToJpegBytes(currentBitmap);
            if (jpegBytes == null) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to encode image", Toast.LENGTH_SHORT).show());
                return;
            }
            boolean ok = encryptAndReplaceVaultFile(jpegBytes, fileItem);
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, "Original replaced", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "Replace failed", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // ─── Save helpers ─────────────────────────────────────────────

    /** Compress the bitmap to JPEG bytes. */
    private byte[] bitmapToJpegBytes(Bitmap bmp) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "bitmapToJpegBytes failed", e);
            return null;
        }
    }

    /**
     * Re-encrypts the edited JPEG bytes over the existing vault file.
     */
    private boolean encryptAndReplaceVaultFile(byte[] bytes, VaultFileItem item) {
        try {
            File tempFile = new File(getCacheDir(), "vault_edit_" + UUID.randomUUID());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
            }

            char[] pin = repo.getSessionPin();
            if (pin == null) { tempFile.delete(); return false; }

            File destFile = new File(repo.getFilesDir(), item.vaultFileName);
            try {
                MediaVaultCrypto.encryptFile(tempFile, destFile, pin);
            } finally {
                java.util.Arrays.fill(pin, '\0');
            }
            item.encryptedSize = destFile.length();
            item.originalSize = bytes.length;
            repo.updateFile(item);

            tempFile.delete();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "encryptAndReplaceVaultFile failed", e);
            return false;
        }
    }

    // ─── Bitmap state helpers ─────────────────────────────────────

    private void setCurrentBitmap(Bitmap bmp) {
        currentBitmap = bmp;
        imageView.clearColorFilter();
        imageView.setImageBitmap(bmp);
        updateUndoRedoButtons();
    }

    private void refreshImageView() {
        imageView.setImageBitmap(currentBitmap);
    }

    private void recycleBitmaps() {
        if (originalBitmap != null && !originalBitmap.isRecycled()) originalBitmap.recycle();
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        for (Bitmap b : undoStack) if (b != null && !b.isRecycled()) b.recycle();
        for (Bitmap b : redoStack) if (b != null && !b.isRecycled()) b.recycle();
        undoStack.clear();
        redoStack.clear();
    }

    // ─── Utilities ────────────────────────────────────────────────

    private String buildCopyName(String original) {
        if (original == null || original.isEmpty()) return "edited_" + System.currentTimeMillis() + ".jpg";
        int dot = original.lastIndexOf('.');
        if (dot > 0) return original.substring(0, dot) + "_edited.jpg";
        return original + "_edited.jpg";
    }

    private TextView makeTextButton(String label, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setClickable(true);
        tv.setOnClickListener(listener);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
