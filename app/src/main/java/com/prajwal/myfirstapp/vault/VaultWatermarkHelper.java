package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Applies a semi-transparent text watermark to a {@link Bitmap} for safe export.
 */
public class VaultWatermarkHelper {

    private static final String PREFS_NAME              = "vault_settings";
    private static final String KEY_WATERMARK_TEXT      = "vault_watermark_text";
    private static final String KEY_WATERMARK_ENABLED   = "vault_watermark_enabled";
    private static final String DEFAULT_WATERMARK_TEXT  = "Personal Copy";

    private static final float TEXT_SIZE_RATIO = 0.06f;  // 6 % of the shorter dimension
    private static final int   PADDING_DP      = 16;

    private VaultWatermarkHelper() {}

    /**
     * Draws {@code text} on a copy of {@code source} at the requested {@code position}
     * with the given {@code opacity} (0.0–1.0).
     *
     * @param source   Original bitmap (not mutated).
     * @param text     Watermark string.
     * @param position One of: "bottom_right", "bottom_left", "top_right", "top_left", "center".
     * @param opacity  Alpha factor 0.0 (invisible) – 1.0 (fully opaque).
     * @return A new {@link Bitmap} with the watermark applied.
     */
    public static Bitmap applyWatermark(Bitmap source, String text, String position, float opacity) {
        if (source == null) return null;
        if (text == null || text.isEmpty()) text = DEFAULT_WATERMARK_TEXT;
        opacity = Math.max(0f, Math.min(1f, opacity));

        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        int w = result.getWidth();
        int h = result.getHeight();

        // Text size relative to the shorter side so it looks consistent across resolutions.
        float textSize = Math.min(w, h) * TEXT_SIZE_RATIO;
        int paddingPx  = dpToPx(PADDING_DP, w);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (opacity * 255));
        paint.setTextSize(textSize);
        paint.setShadowLayer(textSize * 0.15f, 2f, 2f, Color.BLACK);

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int tw = bounds.width();
        int th = bounds.height();

        float x, y;
        if (position == null) position = "bottom_right";
        switch (position) {
            case "bottom_left":
                x = paddingPx;
                y = h - paddingPx;
                break;
            case "top_right":
                x = w - tw - paddingPx;
                y = th + paddingPx;
                break;
            case "top_left":
                x = paddingPx;
                y = th + paddingPx;
                break;
            case "center":
                x = (w - tw) / 2f;
                y = (h + th) / 2f;
                break;
            case "bottom_right":
            default:
                x = w - tw - paddingPx;
                y = h - paddingPx;
                break;
        }

        canvas.drawText(text, x, y, paint);
        return result;
    }

    /**
     * Returns the watermark text configured in SharedPreferences,
     * or {@value #DEFAULT_WATERMARK_TEXT} if none is set.
     */
    public static String getDefaultWatermarkText(Context ctx) {
        return prefs(ctx).getString(KEY_WATERMARK_TEXT, DEFAULT_WATERMARK_TEXT);
    }

    /**
     * Returns {@code true} if the watermark feature is enabled in SharedPreferences.
     */
    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WATERMARK_ENABLED, false);
    }

    // ─── Private helpers ──────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Converts a conceptual "dp" value to pixels scaled relative to the
     * <em>image</em> width (not the screen density). This is intentional:
     * the watermark padding should look consistent regardless of which device
     * renders the exported image.  A 320-pixel image is treated as the 1× base.
     */
    private static int dpToPx(int dp, int referenceDimension) {
        float density = referenceDimension / 320f;
        return (int) (dp * density);
    }
}
