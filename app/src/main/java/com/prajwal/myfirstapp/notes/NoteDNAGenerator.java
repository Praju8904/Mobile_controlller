package com.prajwal.myfirstapp.notes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE DNA GENERATOR — Generates a unique visual fingerprint (geometric pattern)
 *  deterministically from a note's content hash.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Every note gets a unique visual identity derived from SHA-256 of its content.
 *  The hash bytes are mapped to shapes, positions, colors, and rotations.
 *  Two notes with identical content produce the same DNA pattern.
 *  Purely aesthetic — gives each note a distinctive visual marker.
 */
public class NoteDNAGenerator {

    private static final int DNA_SIZE = 128;  // px
    private static final int GRID = 5;        // 5×5 symmetric grid

    // Theme-compatible color palette (dark theme friendly)
    private static final int[] PALETTE = {
        0xFFF59E0B, // amber
        0xFF3B82F6, // blue
        0xFF10B981, // emerald
        0xFFA855F7, // purple
        0xFFEF4444, // red
        0xFFF472B6, // pink
        0xFF06B6D4, // cyan
        0xFF22C55E, // green
        0xFFE879F9, // fuchsia
        0xFFFBBF24, // yellow
        0xFF818CF8, // indigo
        0xFF2DD4BF, // teal
    };

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Generate a DNA fingerprint bitmap from note content.
     * @param content The plain-text content of the note (title + body).
     * @return A square Bitmap (DNA_SIZE × DNA_SIZE) with the unique pattern.
     */
    public static Bitmap generateDNA(String content) {
        byte[] hash = sha256(content != null ? content : "");
        return renderDNA(hash, DNA_SIZE);
    }

    /**
     * Generate a smaller thumbnail DNA for list items.
     * @param content The plain-text content of the note.
     * @return A 48×48 Bitmap.
     */
    public static Bitmap generateThumbnailDNA(String content) {
        byte[] hash = sha256(content != null ? content : "");
        return renderDNA(hash, 48);
    }

    /**
     * Compute SHA-256 hash of the given content, used as the DNA seed.
     */
    public static String computeHash(String content) {
        byte[] hash = sha256(content != null ? content : "");
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RENDERING
    // ═══════════════════════════════════════════════════════════════════════════════

    private static Bitmap renderDNA(byte[] hash, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.TRANSPARENT);

        if (hash.length < 32) return bmp; // safety

        // ── Pick 2 colors from the palette ──
        int color1 = PALETTE[(hash[0] & 0xFF) % PALETTE.length];
        int color2 = PALETTE[(hash[1] & 0xFF) % PALETTE.length];
        if (color1 == color2) color2 = PALETTE[((hash[1] & 0xFF) + 1) % PALETTE.length];

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cellSize = (float) size / GRID;

        // ── Draw background circle ──
        paint.setColor(Color.parseColor("#0F172A"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // ── Draw symmetric grid pattern ──
        // Use hash bytes to decide which cells are filled (mirror horizontally)
        int byteIdx = 2;
        for (int row = 0; row < GRID; row++) {
            int halfCols = (GRID + 1) / 2; // 3 for a 5-grid
            for (int col = 0; col < halfCols; col++) {
                if (byteIdx >= hash.length) byteIdx = 2;
                byte b = hash[byteIdx++];
                boolean filled = (b & 0x01) == 1;

                if (filled) {
                    int shapeType = (b >> 1) & 0x03; // 0-3: circle, square, triangle, diamond

                    // Alternate colors based on position
                    paint.setColor(((row + col) % 2 == 0) ? color1 : color2);
                    paint.setAlpha(160 + ((b & 0x3F) % 96)); // 160-255

                    float cx = col * cellSize + cellSize / 2f;
                    float cy = row * cellSize + cellSize / 2f;
                    float r = cellSize * 0.35f;

                    // Draw on left side
                    drawShape(canvas, paint, shapeType, cx, cy, r);

                    // Mirror to right side (symmetric)
                    int mirrorCol = GRID - 1 - col;
                    if (mirrorCol != col) {
                        float mx = mirrorCol * cellSize + cellSize / 2f;
                        drawShape(canvas, paint, shapeType, mx, cy, r);
                    }
                }
            }
        }

        // ── Draw center accent ──
        paint.setColor(color1);
        paint.setAlpha(200);
        float centerR = cellSize * 0.3f;
        canvas.drawCircle(size / 2f, size / 2f, centerR, paint);

        // ── Draw subtle border ring ──
        paint.setColor(color2);
        paint.setAlpha(80);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.02f);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - size * 0.02f, paint);

        return bmp;
    }

    private static void drawShape(Canvas canvas, Paint paint, int type, float cx, float cy, float r) {
        Paint.Style origStyle = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);

        switch (type) {
            case 0: // Circle
                canvas.drawCircle(cx, cy, r, paint);
                break;
            case 1: // Square
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, paint);
                break;
            case 2: // Triangle
                Path tri = new Path();
                tri.moveTo(cx, cy - r);
                tri.lineTo(cx - r, cy + r);
                tri.lineTo(cx + r, cy + r);
                tri.close();
                canvas.drawPath(tri, paint);
                break;
            case 3: // Diamond
                Path dia = new Path();
                dia.moveTo(cx, cy - r);
                dia.lineTo(cx + r, cy);
                dia.lineTo(cx, cy + r);
                dia.lineTo(cx - r, cy);
                dia.close();
                canvas.drawPath(dia, paint);
                break;
        }

        paint.setStyle(origStyle);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HASH UTILITY
    // ═══════════════════════════════════════════════════════════════════════════════

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Fallback: simple XOR-based pseudo-hash (every JVM has SHA-256 though)
            byte[] fallback = new byte[32];
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                fallback[i % 32] ^= bytes[i];
            }
            return fallback;
        }
    }
}
