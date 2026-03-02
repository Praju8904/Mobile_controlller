package com.prajwal.myfirstapp.notes;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE EXPORT MANAGER — Exports notes as PDF or styled image cards
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Export as PDF document
 * - Share as styled image card (social-ready)
 * - Custom styling with note colors
 * - Dark theme aesthetic
 */
public class NoteExportManager {

    private static final String TAG = "NoteExportManager";
    private static final int PDF_PAGE_WIDTH = 595; // A4 width in points
    private static final int PDF_PAGE_HEIGHT = 842; // A4 height in points
    private static final int MARGIN = 50;

    private static final int IMAGE_WIDTH = 1080; // Instagram-friendly
    private static final int IMAGE_MIN_HEIGHT = 1080;
    private static final int IMAGE_MAX_HEIGHT = 1920;

    private final Context context;

    public interface ExportCallback {
        void onResult(boolean success);
    }

    public NoteExportManager(Context context) {
        this.context = context;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PDF EXPORT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Export note as PDF document
     */
    public void exportAsPdf(Note note, ExportCallback callback) {
        if (note == null) {
            callback.onResult(false);
            return;
        }

        new Thread(() -> {
            try {
                PdfDocument document = new PdfDocument();
                
                // Create page info
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create();
                
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // Draw background
                Paint bgPaint = new Paint();
                bgPaint.setColor(Color.parseColor("#0F172A"));
                canvas.drawRect(0, 0, PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, bgPaint);

                int y = MARGIN;

                // Draw title
                TextPaint titlePaint = new TextPaint();
                titlePaint.setColor(Color.parseColor("#F1F5F9"));
                titlePaint.setTextSize(24);
                titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                titlePaint.setAntiAlias(true);

                String title = note.title != null ? note.title : "Untitled";
                StaticLayout titleLayout = StaticLayout.Builder.obtain(
                        title, 0, title.length(), titlePaint, PDF_PAGE_WIDTH - 2 * MARGIN)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0, 1.2f)
                        .build();

                canvas.save();
                canvas.translate(MARGIN, y);
                titleLayout.draw(canvas);
                canvas.restore();

                y += titleLayout.getHeight() + 20;

                // Draw date
                Paint datePaint = new Paint();
                datePaint.setColor(Color.parseColor("#64748B"));
                datePaint.setTextSize(12);
                datePaint.setAntiAlias(true);

                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(note.updatedAt));
                canvas.drawText(dateStr, MARGIN, y, datePaint);

                y += 30;

                // Draw divider
                Paint dividerPaint = new Paint();
                dividerPaint.setColor(Color.parseColor("#1E293B"));
                dividerPaint.setStrokeWidth(1);
                canvas.drawLine(MARGIN, y, PDF_PAGE_WIDTH - MARGIN, y, dividerPaint);

                y += 30;

                // Draw body text
                TextPaint bodyPaint = new TextPaint();
                bodyPaint.setColor(Color.parseColor("#E2E8F0"));
                bodyPaint.setTextSize(14);
                bodyPaint.setAntiAlias(true);

                String body = note.body != null ? note.body : "";
                int availableHeight = PDF_PAGE_HEIGHT - y - MARGIN;

                StaticLayout bodyLayout = StaticLayout.Builder.obtain(
                        body, 0, body.length(), bodyPaint, PDF_PAGE_WIDTH - 2 * MARGIN)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(4, 1.3f)
                        .setMaxLines(availableHeight / 20)
                        .build();

                canvas.save();
                canvas.translate(MARGIN, y);
                bodyLayout.draw(canvas);
                canvas.restore();

                // Finish page
                document.finishPage(page);

                // Save file
                String filename = "Note_" + sanitizeFilename(title) + ".pdf";
                Uri savedUri = saveToDownloads(document, filename, "application/pdf");

                document.close();

                if (savedUri != null) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show();
                        callback.onResult(true);
                    });
                } else {
                    ((android.app.Activity) context).runOnUiThread(() -> callback.onResult(false));
                }

            } catch (Exception e) {
                Log.e(TAG, "PDF export failed", e);
                ((android.app.Activity) context).runOnUiThread(() -> callback.onResult(false));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  IMAGE CARD EXPORT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Share note as a styled image card
     */
    public void shareAsImage(Note note, ExportCallback callback) {
        if (note == null) {
            callback.onResult(false);
            return;
        }

        new Thread(() -> {
            try {
                Bitmap bitmap = createImageCard(note);
                if (bitmap == null) {
                    ((android.app.Activity) context).runOnUiThread(() -> callback.onResult(false));
                    return;
                }

                // Save to cache for sharing
                File cacheDir = new File(context.getCacheDir(), "shared_images");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }

                String filename = "note_card_" + System.currentTimeMillis() + ".png";
                File imageFile = new File(cacheDir, filename);

                FileOutputStream fos = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();

                // Create share intent
                Uri imageUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        imageFile
                );

                ((android.app.Activity) context).runOnUiThread(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/png");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    context.startActivity(Intent.createChooser(shareIntent, "Share Note"));
                    callback.onResult(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "Image export failed", e);
                ((android.app.Activity) context).runOnUiThread(() -> callback.onResult(false));
            }
        }).start();
    }

    /**
     * Create a styled image card bitmap
     */
    private Bitmap createImageCard(Note note) {
        int padding = 80;
        int contentWidth = IMAGE_WIDTH - 2 * padding;

        // Measure text heights first
        TextPaint titlePaint = new TextPaint();
        titlePaint.setColor(Color.parseColor("#F1F5F9"));
        titlePaint.setTextSize(64);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setAntiAlias(true);

        String title = note.title != null ? note.title : "Untitled";
        StaticLayout titleLayout = StaticLayout.Builder.obtain(
                title, 0, title.length(), titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.2f)
                .build();

        TextPaint bodyPaint = new TextPaint();
        bodyPaint.setColor(Color.parseColor("#CBD5E1"));
        bodyPaint.setTextSize(36);
        bodyPaint.setAntiAlias(true);

        String body = note.body != null ? note.body : "";
        // Truncate body for image card
        if (body.length() > 500) {
            body = body.substring(0, 500) + "...";
        }

        StaticLayout bodyLayout = StaticLayout.Builder.obtain(
                body, 0, body.length(), bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8, 1.4f)
                .build();

        // Calculate image height
        int totalHeight = padding // top
                + titleLayout.getHeight()
                + 40 // title-body gap
                + bodyLayout.getHeight()
                + 60 // footer gap
                + 50 // footer height
                + padding; // bottom

        // Clamp height
        totalHeight = Math.max(IMAGE_MIN_HEIGHT, Math.min(IMAGE_MAX_HEIGHT, totalHeight));

        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw gradient background
        int bgColor1 = Color.parseColor("#0A0E21");
        int bgColor2 = Color.parseColor("#1E293B");

        // Check if note has a color
        if (note.colorHex != null && !note.colorHex.equals("default")) {
            int noteColor = Color.parseColor(note.colorHex);
            // Mix note color into gradient
            bgColor2 = blendColors(bgColor2, noteColor, 0.3f);
        }

        Paint gradientPaint = new Paint();
        gradientPaint.setShader(new LinearGradient(
                0, 0, 0, totalHeight,
                bgColor1, bgColor2,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, IMAGE_WIDTH, totalHeight, gradientPaint);

        // Draw subtle pattern/border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#F59E0B"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        RectF rect = new RectF(20, 20, IMAGE_WIDTH - 20, totalHeight - 20);
        canvas.drawRoundRect(rect, 24, 24, borderPaint);

        int y = padding;

        // Draw category pill if exists
        if (note.category != null && !note.category.isEmpty()) {
            Paint pillPaint = new Paint();
            pillPaint.setColor(Color.parseColor("#F59E0B"));
            pillPaint.setAntiAlias(true);

            Paint pillTextPaint = new Paint();
            pillTextPaint.setColor(Color.parseColor("#0A0E21"));
            pillTextPaint.setTextSize(24);
            pillTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            pillTextPaint.setAntiAlias(true);

            float pillWidth = pillTextPaint.measureText(note.category) + 32;
            RectF pillRect = new RectF(padding, y, padding + pillWidth, y + 44);
            canvas.drawRoundRect(pillRect, 22, 22, pillPaint);
            canvas.drawText(note.category, padding + 16, y + 32, pillTextPaint);

            y += 60;
        }

        // Draw title
        canvas.save();
        canvas.translate(padding, y);
        titleLayout.draw(canvas);
        canvas.restore();

        y += titleLayout.getHeight() + 40;

        // Draw divider
        Paint dividerPaint = new Paint();
        dividerPaint.setColor(Color.parseColor("#334155"));
        dividerPaint.setStrokeWidth(2);
        canvas.drawLine(padding, y, IMAGE_WIDTH - padding, y, dividerPaint);

        y += 30;

        // Draw body
        canvas.save();
        canvas.translate(padding, y);
        bodyLayout.draw(canvas);
        canvas.restore();

        // Draw footer (branding)
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.parseColor("#475569"));
        footerPaint.setTextSize(24);
        footerPaint.setAntiAlias(true);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(note.updatedAt));
        canvas.drawText(dateStr, padding, totalHeight - padding, footerPaint);

        // App name on right
        String appName = "Notes";
        float appNameWidth = footerPaint.measureText(appName);
        canvas.drawText(appName, IMAGE_WIDTH - padding - appNameWidth, totalHeight - padding, footerPaint);

        return bitmap;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FILE SAVING
    // ═══════════════════════════════════════════════════════════════════════════════

    private Uri saveToDownloads(PdfDocument document, String filename, String mimeType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        document.writeTo(os);
                        os.close();
                        return uri;
                    }
                }
            } else {
                // Legacy approach for older Android
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, filename);

                FileOutputStream fos = new FileOutputStream(file);
                document.writeTo(fos);
                fos.close();

                return Uri.fromFile(file);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save PDF", e);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════════

    private String sanitizeFilename(String name) {
        if (name == null) return "note";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(name.length(), 50));
    }

    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;

        int r = (int) ((Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio));
        int g = (int) ((Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio));
        int b = (int) ((Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio));

        return Color.rgb(r, g, b);
    }
}
