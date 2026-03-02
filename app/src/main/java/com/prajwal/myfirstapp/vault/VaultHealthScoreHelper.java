package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a vault health score (0–100) based on several security factors
 * and provides actionable improvement suggestions.
 */
public class VaultHealthScoreHelper {

    private static final String PREFS_VAULT    = "media_vault_prefs";
    private static final String PREFS_SETTINGS = "vault_settings";

    private static final String KEY_PIN_HASH          = "vault_pin_hash";
    private static final String KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled";
    private static final String KEY_LAST_UNLOCK_TIME  = "vault_last_unlock_time";
    private static final String KEY_AUTO_LOCK_MS      = "auto_lock_ms";

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    // ─── Score weights ────────────────────────────────────────────
    private static final int WEIGHT_PIN_STRENGTH  = 20;
    private static final int WEIGHT_BIOMETRIC     = 10;
    private static final int WEIGHT_NO_GPS        = 20;
    private static final int WEIGHT_RECENT_USE    = 10;
    private static final int WEIGHT_AUTO_LOCK     = 10;
    private static final int WEIGHT_ENCRYPTED     = 30;  // always awarded

    /**
     * Calculates the overall vault health score (0–100).
     */
    public static int calculateScore(Context context, MediaVaultRepository repo) {
        int score = 0;

        // 1. Files always encrypted — always award full points.
        score += WEIGHT_ENCRYPTED;

        // 2. PIN strength.
        score += pinStrengthScore(context);

        // 3. Biometric enabled.
        SharedPreferences vaultPrefs = context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        if (vaultPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)) {
            score += WEIGHT_BIOMETRIC;
        }

        // 4. No GPS data in photos.
        score += gpsScore(repo);

        // 5. Recent activity (vault used in last 7 days).
        long lastUnlock = vaultPrefs.getLong(KEY_LAST_UNLOCK_TIME, 0L);
        if (lastUnlock > 0 && (System.currentTimeMillis() - lastUnlock) <= SEVEN_DAYS_MS) {
            score += WEIGHT_RECENT_USE;
        }

        // 6. Session timeout configured (not "Never").
        SharedPreferences settingsPrefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        if (settingsPrefs.getLong(KEY_AUTO_LOCK_MS, 0L) != 0L) {
            score += WEIGHT_AUTO_LOCK;
        }

        return Math.min(score, 100);
    }

    /**
     * Returns a hex color representing the score level:
     * green (≥80), amber (50–79), red (<50).
     */
    public static String getScoreColor(int score) {
        if (score >= 80) return "#4CAF50";
        if (score >= 50) return "#FF9800";
        return "#F44336";
    }

    /**
     * Returns a list of human-readable improvement suggestions.
     */
    public static List<String> getImprovementSuggestions(Context context, MediaVaultRepository repo) {
        List<String> suggestions = new ArrayList<>();

        if (isWeakPin(context)) {
            suggestions.add("Use a stronger PIN — avoid all-same or sequential digits.");
        }

        SharedPreferences vaultPrefs = context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        if (!vaultPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)) {
            suggestions.add("Enable biometric authentication for faster, safer access.");
        }

        int gpsCount = countFilesWithGps(repo);
        if (gpsCount > 0) {
            suggestions.add(gpsCount + " file(s) contain GPS metadata. Consider stripping location data before importing.");
        }

        long lastUnlock = vaultPrefs.getLong(KEY_LAST_UNLOCK_TIME, 0L);
        if (lastUnlock == 0 || (System.currentTimeMillis() - lastUnlock) > SEVEN_DAYS_MS) {
            suggestions.add("Open your vault regularly to stay on top of security updates.");
        }

        SharedPreferences settingsPrefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        if (settingsPrefs.getLong(KEY_AUTO_LOCK_MS, 0L) == 0L) {
            suggestions.add("Set an auto-lock timeout so the vault locks itself when idle.");
        }

        return suggestions;
    }

    // ─── Private helpers ──────────────────────────────────────────

    private static int pinStrengthScore(Context context) {
        if (isWeakPin(context)) return 0;
        return WEIGHT_PIN_STRENGTH;
    }

    /**
     * Returns {@code true} if the vault PIN has been flagged as weak.
     *
     * <p>Because PINs are stored only as salted hashes, weakness must be
     * evaluated at PIN-setup time by calling {@link #isPinWeak(String)} before
     * hashing, then persisted with
     * {@code prefs.putBoolean("vault_pin_strength_ok", !isPinWeak(pin))}.
     */
    private static boolean isWeakPin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE);
        if (prefs.getString(KEY_PIN_HASH, null) == null) return false; // no PIN yet
        return !prefs.getBoolean("vault_pin_strength_ok", true);
    }

    /**
     * Checks whether the given plaintext PIN is weak (all-same digits or
     * strictly sequential in either direction, e.g. "1111" or "1234").
     *
     * <p>Call this during PIN setup <em>before</em> hashing and store the
     * result so the hash-based code path can still surface the warning:
     * <pre>
     *     prefs.edit()
     *         .putBoolean("vault_pin_strength_ok", !VaultHealthScoreHelper.isPinWeak(pin))
     *         .apply();
     * </pre>
     *
     * @param pin Plaintext PIN string.
     * @return {@code true} if the PIN is considered weak.
     */
    public static boolean isPinWeak(String pin) {
        if (pin == null || pin.length() < 4) return true; // too short is always weak

        boolean allSame = true;
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) != pin.charAt(0)) { allSame = false; break; }
        }
        if (allSame) return true;

        boolean ascending = true, descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int diff = pin.charAt(i) - pin.charAt(i - 1);
            if (diff != 1)  ascending  = false;
            if (diff != -1) descending = false;
        }
        return ascending || descending;
    }

    private static int gpsScore(MediaVaultRepository repo) {
        if (repo == null) return WEIGHT_NO_GPS;
        int gpsFiles = countFilesWithGps(repo);
        // Deduct 4 points per GPS-tagged file, minimum 0.
        int deduction = Math.min(gpsFiles * 4, WEIGHT_NO_GPS);
        return WEIGHT_NO_GPS - deduction;
    }

    private static int countFilesWithGps(MediaVaultRepository repo) {
        if (repo == null) return 0;
        int count = 0;
        List<VaultFileItem> files = repo.getAllFiles();
        if (files == null) return 0;
        for (VaultFileItem f : files) {
            // Convention: import code prefixes the notes field with "gps:<lat>,<lon>|"
            // when GPS EXIF data was detected.  A dedicated hasGpsMetadata field on
            // VaultFileItem would be cleaner but requires a model change.
            if (f.notes != null && f.notes.startsWith("gps:")) {
                count++;
            }
        }
        return count;
    }
}
