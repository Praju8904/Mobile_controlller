package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the auto-destroy-on-failed-attempts feature.
 *
 * When enabled, the vault should be wiped after a configurable number of
 * consecutive failed unlock attempts (5, 10, or 20).
 */
public class VaultAutoDestroyManager {

    private static final String PREFS_NAME = "vault_auto_destroy";

    private static final String KEY_ENABLED   = "enabled";
    private static final String KEY_THRESHOLD = "threshold";

    /** Default wipe threshold when none has been explicitly set. */
    private static final int DEFAULT_THRESHOLD = 10;

    private VaultAutoDestroyManager() {}

    /** Returns {@code true} if the auto-destroy feature is currently enabled. */
    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, false);
    }

    /**
     * Returns the configured failed-attempt threshold (5, 10, or 20).
     * Defaults to {@value #DEFAULT_THRESHOLD} if not set.
     */
    public static int getThreshold(Context ctx) {
        return prefs(ctx).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    /**
     * Enables auto-destroy with the given threshold.
     *
     * @param threshold Number of failed attempts before wipe (5, 10, or 20).
     */
    public static void setEnabled(Context ctx, boolean enabled, int threshold) {
        prefs(ctx).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_THRESHOLD, threshold)
                .apply();
    }

    /** Disables the auto-destroy feature without changing the stored threshold. */
    public static void disable(Context ctx) {
        prefs(ctx).edit()
                .putBoolean(KEY_ENABLED, false)
                .apply();
    }

    /**
     * Returns {@code true} if the vault should be destroyed now.
     *
     * @param failedAttempts Total consecutive failed unlock attempts so far.
     */
    public static boolean shouldDestroy(Context ctx, int failedAttempts) {
        return isEnabled(ctx) && failedAttempts >= getThreshold(ctx);
    }

    // ─── Private helper ───────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
