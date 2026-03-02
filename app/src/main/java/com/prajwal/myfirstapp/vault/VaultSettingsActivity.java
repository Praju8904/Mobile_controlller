package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Comprehensive Vault Settings — Security, Privacy, Import, Display,
 * Backup, Activity, and Danger Zone sections, built entirely in code.
 */
public class VaultSettingsActivity extends Activity {

    // ─── Preferences ─────────────────────────────────────────────
    private static final String PREFS_SETTINGS = "vault_settings";

    // Security
    private static final String KEY_AUTO_LOCK          = "auto_lock_ms";
    private static final String KEY_FAILED_LIMIT       = "failed_limit";
    private static final String KEY_LOCKOUT_DURATION   = "lockout_duration_ms";
    private static final String KEY_BIOMETRIC_ENABLED  = "vault_biometric_enabled";

    // Privacy
    private static final String KEY_GPS_AUTO_STRIP     = "gps_auto_strip";
    private static final String KEY_SECURE_EXPORT      = "secure_export_default";
    private static final String KEY_WATERMARK_ENABLED  = "vault_watermark_enabled";
    private static final String KEY_WATERMARK_TEXT     = "vault_watermark_text";

    // Import
    private static final String KEY_IMPORT_DEFAULT     = "import_default";
    private static final String KEY_FACE_DETECTION     = "face_detection_import";
    private static final String KEY_DUPLICATE_DETECT   = "duplicate_detection";

    // Display
    private static final String KEY_GRID_COLUMNS       = "grid_columns";
    private static final String KEY_THUMB_QUALITY      = "thumb_quality";
    private static final String KEY_SHOW_NAMES         = "show_names";
    private static final String KEY_SHOW_SIZES         = "show_sizes";
    private static final String KEY_DEFAULT_SORT       = "default_sort";

    // Backup / Activity
    private static final String KEY_BACKUP_REMINDER    = "backup_reminder";
    private static final String KEY_LOG_RETENTION      = "log_retention";

    // ─── Colors ───────────────────────────────────────────────────
    private static final int COLOR_BG        = 0xFF0F0F1A;
    private static final int COLOR_CARD      = 0xFF1A1A2E;
    private static final int COLOR_ACCENT    = 0xFF3B82F6;
    private static final int COLOR_TEXT      = 0xFFEEEEEE;
    private static final int COLOR_SUBTEXT   = 0xFF9CA3AF;
    private static final int COLOR_DIVIDER   = 0xFF2A2A4A;
    private static final int COLOR_DANGER_BG = 0xFF2D0A0A;
    private static final int COLOR_DANGER    = 0xFFEF4444;
    private static final int COLOR_POSITIVE  = 0xFF22C55E;

    // ─── State ────────────────────────────────────────────────────
    private MediaVaultRepository repo;
    private SharedPreferences prefs;
    private LinearLayout container;

    // Dynamic rows — updated when settings change
    private TextView rowBiometric, rowSessionTimeout, rowFailedLimit;
    private TextView rowLockoutDuration, rowDecoyVault, rowAutoDestroy;
    private TextView rowGpsStrip, rowSecureExport, rowWatermark;
    private TextView rowImportDefault, rowFaceDetection, rowDuplicateDetect;
    private TextView rowGridColumns, rowThumbQuality, rowShowNames;
    private TextView rowShowSizes, rowDefaultSort;
    private TextView rowLastBackup, rowBackupReminder, rowLogRetention;

    // ─── Lifecycle ────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        repo  = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }
        prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);

        buildUI();
    }

    // ─── UI Construction ─────────────────────────────────────────

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setFillViewport(true);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(8), dp(12), dp(40));
        scroll.addView(container);

        addScreenHeader();
        buildSecuritySection();
        buildPrivacySection();
        buildImportSection();
        buildDisplaySection();
        buildBackupSection();
        buildActivitySection();
        buildDangerZone();

        setContentView(scroll);
    }

    private void addScreenHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(12), dp(4), dp(16));

        TextView btnBack = new TextView(this);
        btnBack.setText("← Back");
        btnBack.setTextColor(COLOR_ACCENT);
        btnBack.setTextSize(15);
        btnBack.setPadding(dp(8), dp(8), dp(16), dp(8));
        btnBack.setOnClickListener(v -> finish());
        bar.addView(btnBack);

        TextView title = new TextView(this);
        title.setText("Vault Settings");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, lp);

        container.addView(bar);
    }

    /** Adds a section header label in accent colour. */
    private void addSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title.toUpperCase(Locale.getDefault()));
        tv.setTextColor(COLOR_ACCENT);
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(4), dp(20), dp(4), dp(6));
        tv.setLetterSpacing(0.12f);
        container.addView(tv);
    }

    /** Adds a card container for section items. */
    private LinearLayout addCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_CARD);
        int r = dp(10);
        card.setPadding(0, 0, 0, 0);
        // Rounded corners via background drawable would need XML; use a plain card
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        container.addView(card, lp);
        return card;
    }

    /**
     * Adds a settings row TextView to the given parent.
     * Returns the TextView so callers can update it later.
     */
    private TextView addRow(LinearLayout parent, String text, View.OnClickListener listener) {
        return addRow(parent, text, COLOR_TEXT, listener);
    }

    private TextView addRow(LinearLayout parent, String text, int textColor,
                             View.OnClickListener listener) {
        // Separator above (skip for first child)
        if (parent.getChildCount() > 0) {
            View sep = new View(this);
            sep.setBackgroundColor(COLOR_DIVIDER);
            parent.addView(sep, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(14);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (listener != null) {
            tv.setOnClickListener(listener);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        parent.addView(tv);
        return tv;
    }

    /** Adds a static info row (no click handler, dimmed colour). */
    private TextView addInfoRow(LinearLayout parent, String text) {
        return addRow(parent, text, COLOR_SUBTEXT, null);
    }

    // ─── Section: Security ────────────────────────────────────────

    private void buildSecuritySection() {
        addSectionHeader("🔒  Security");
        LinearLayout card = addCard();

        addRow(card, "🔑  Change Vault PIN", v -> showChangePinStep1());

        rowBiometric = addRow(card, biometricLabel(), v -> toggleBiometric());

        rowSessionTimeout = addRow(card, sessionTimeoutLabel(), v -> showSessionTimeoutPicker());

        rowFailedLimit = addRow(card, failedLimitLabel(), v -> showFailedLimitPicker());

        rowLockoutDuration = addRow(card, lockoutDurationLabel(), v -> showLockoutDurationPicker());

        rowDecoyVault = addRow(card, decoyVaultLabel(), v -> showDecoyVaultDialog());

        rowAutoDestroy = addRow(card, autoDestroyLabel(), v -> showAutoDestroyDialog());

        addInfoRow(card, "🖼️  Screenshot Protection: Always Enabled (FLAG_SECURE)");
    }

    // ─── Section: Privacy ─────────────────────────────────────────

    private void buildPrivacySection() {
        addSectionHeader("🕵️  Privacy");
        LinearLayout card = addCard();

        addRow(card, "🔍  Privacy Scan — Scan for sensitive metadata",
                v -> startActivity(new Intent(this, VaultPrivacyScannerActivity.class)));

        rowGpsStrip = addRow(card, gpsStripLabel(), v -> toggleGpsStrip());

        rowSecureExport = addRow(card, secureExportLabel(), v -> toggleSecureExport());

        rowWatermark = addRow(card, watermarkLabel(), v -> showWatermarkDialog());
    }

    // ─── Section: Import ──────────────────────────────────────────

    private void buildImportSection() {
        addSectionHeader("📥  Import");
        LinearLayout card = addCard();

        rowImportDefault = addRow(card, importDefaultLabel(), v -> showImportDefaultPicker());

        rowFaceDetection = addRow(card, faceDetectionLabel(), v -> toggleFaceDetection());

        rowDuplicateDetect = addRow(card, duplicateDetectLabel(), v -> toggleDuplicateDetect());
    }

    // ─── Section: Display ─────────────────────────────────────────

    private void buildDisplaySection() {
        addSectionHeader("🖼️  Display");
        LinearLayout card = addCard();

        rowGridColumns = addRow(card, gridColumnsLabel(), v -> showGridColumnsPicker());

        rowThumbQuality = addRow(card, thumbQualityLabel(), v -> showThumbQualityPicker());

        rowShowNames = addRow(card, showNamesLabel(), v -> toggleShowNames());

        rowShowSizes = addRow(card, showSizesLabel(), v -> toggleShowSizes());

        rowDefaultSort = addRow(card, defaultSortLabel(), v -> showDefaultSortPicker());
    }

    // ─── Section: Backup ──────────────────────────────────────────

    private void buildBackupSection() {
        addSectionHeader("💾  Backup");
        LinearLayout card = addCard();

        addRow(card, "📦  Create Backup Archive", v -> startBackupArchive());

        rowLastBackup = addInfoRow(card, lastBackupLabel());

        rowBackupReminder = addRow(card, backupReminderLabel(), v -> showBackupReminderPicker());
    }

    // ─── Section: Activity ────────────────────────────────────────

    private void buildActivitySection() {
        addSectionHeader("📋  Activity");
        LinearLayout card = addCard();

        addRow(card, "📖  View Activity Log",
                v -> startActivity(new Intent(this, VaultActivityLogActivity.class)));

        rowLogRetention = addRow(card, logRetentionLabel(), v -> showLogRetentionPicker());

        addRow(card, "🗑️  Clear Activity Log", v -> showClearActivityLogDialog());
    }

    // ─── Section: Danger Zone ─────────────────────────────────────

    private void buildDangerZone() {
        addSectionHeader("⚠️  Danger Zone");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_DANGER_BG);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        container.addView(card, lp);

        addRow(card, "💥  Wipe Entire Vault — Permanently delete all files",
                COLOR_DANGER, v -> showWipeVaultStep1());
    }

    // ─── Label helpers (read current prefs each time) ─────────────

    private String biometricLabel() {
        boolean on = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        return "👆  Biometric Unlock: " + (on ? "Enabled" : "Disabled");
    }

    private String sessionTimeoutLabel() {
        long ms = prefs.getLong(KEY_AUTO_LOCK, 5 * 60_000L);
        return "⏱️  Session Timeout: " + formatAutoLock(ms);
    }

    private String failedLimitLabel() {
        int n = prefs.getInt(KEY_FAILED_LIMIT, 5);
        return "🚫  Failed Attempt Limit: " + n + " attempts";
    }

    private String lockoutDurationLabel() {
        long ms = prefs.getLong(KEY_LOCKOUT_DURATION, 60_000L);
        return "🔒  Lockout Duration: " + formatDuration(ms);
    }

    private String decoyVaultLabel() {
        boolean on = repo.isDecoyEnabled();
        return "🎭  Decoy Vault: " + (on ? "Enabled" : "Disabled");
    }

    private String autoDestroyLabel() {
        boolean on = VaultAutoDestroyManager.isEnabled(this);
        String extra = on ? " (after " + VaultAutoDestroyManager.getThreshold(this) + " attempts)" : "";
        return "💣  Auto-Destroy Mode: " + (on ? "Enabled" + extra : "Disabled");
    }

    private String gpsStripLabel() {
        boolean on = prefs.getBoolean(KEY_GPS_AUTO_STRIP, true);
        return "📍  GPS Auto-Strip on Import: " + (on ? "Enabled" : "Disabled");
    }

    private String secureExportLabel() {
        boolean on = prefs.getBoolean(KEY_SECURE_EXPORT, false);
        return "🔐  Secure Export Default: " + (on ? "Enabled" : "Disabled");
    }

    private String watermarkLabel() {
        boolean on = prefs.getBoolean(KEY_WATERMARK_ENABLED, false);
        String text = prefs.getString(KEY_WATERMARK_TEXT, "");
        if (on && !text.isEmpty()) return "💧  Watermark on Export: \"" + text + "\"";
        return "💧  Watermark on Export: " + (on ? "Enabled (no text set)" : "Disabled");
    }

    private String importDefaultLabel() {
        String val = prefs.getString(KEY_IMPORT_DEFAULT, "ask");
        String display = "ask".equals(val) ? "Ask Every Time"
                       : "copy".equals(val) ? "Copy" : "Move";
        return "📥  Default Import Mode: " + display;
    }

    private String faceDetectionLabel() {
        boolean on = prefs.getBoolean(KEY_FACE_DETECTION, false);
        return "🙂  Face Detection on Import: " + (on ? "Enabled" : "Disabled");
    }

    private String duplicateDetectLabel() {
        boolean on = prefs.getBoolean(KEY_DUPLICATE_DETECT, true);
        return "🔁  Duplicate Detection: " + (on ? "Enabled" : "Disabled");
    }

    private String gridColumnsLabel() {
        int cols = prefs.getInt(KEY_GRID_COLUMNS, 3);
        return "⊞  Grid Column Count: " + cols;
    }

    private String thumbQualityLabel() {
        String q = prefs.getString(KEY_THUMB_QUALITY, "medium");
        return "🖼️  Thumbnail Quality: " + capitalize(q);
    }

    private String showNamesLabel() {
        boolean on = prefs.getBoolean(KEY_SHOW_NAMES, true);
        return "🏷️  Show File Names in Grid: " + (on ? "On" : "Off");
    }

    private String showSizesLabel() {
        boolean on = prefs.getBoolean(KEY_SHOW_SIZES, true);
        return "📏  Show File Size in Grid: " + (on ? "On" : "Off");
    }

    private String defaultSortLabel() {
        String sort = prefs.getString(KEY_DEFAULT_SORT, "date_added");
        return "↕️  Default Sort: " + sortDisplayName(sort);
    }

    private String lastBackupLabel() {
        long t = repo.getLastBackupTime();
        if (t == 0) return "📅  Last Backup: Never";
        return "📅  Last Backup: " + formatDate(t);
    }

    private String backupReminderLabel() {
        String r = prefs.getString(KEY_BACKUP_REMINDER, "monthly");
        return "🔔  Backup Reminder: " + capitalize(r);
    }

    private String logRetentionLabel() {
        String r = prefs.getString(KEY_LOG_RETENTION, "90days");
        return "📆  Log Retention: " + logRetentionDisplayName(r);
    }

    // ─── Security Dialogs ────────────────────────────────────────

    private void showChangePinStep1() {
        EditText et = makeNumberEdit("Current PIN");
        new AlertDialog.Builder(this)
                .setTitle("Change PIN")
                .setMessage("Enter your current PIN to continue:")
                .setView(wrap(et))
                .setPositiveButton("Next", (d, w) -> {
                    String pin = et.getText().toString();
                    if (!repo.verifyPin(pin)) {
                        toast("Incorrect PIN");
                        return;
                    }
                    showChangePinStep2(pin);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePinStep2(String currentPin) {
        EditText et = makeNumberEdit("New PIN (4–8 digits)");
        new AlertDialog.Builder(this)
                .setTitle("New PIN")
                .setView(wrap(et))
                .setPositiveButton("Next", (d, w) -> {
                    String newPin = et.getText().toString().trim();
                    if (newPin.length() < 4) { toast("PIN must be at least 4 digits"); return; }
                    showChangePinStep3(currentPin, newPin);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePinStep3(String currentPin, String newPin) {
        EditText et = makeNumberEdit("Confirm new PIN");
        new AlertDialog.Builder(this)
                .setTitle("Confirm New PIN")
                .setView(wrap(et))
                .setPositiveButton("Save", (d, w) -> {
                    if (!et.getText().toString().trim().equals(newPin)) {
                        toast("PINs do not match");
                        return;
                    }
                    boolean ok = repo.changePin(currentPin, newPin);
                    toast(ok ? "PIN changed successfully" : "Failed to change PIN");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleBiometric() {
        boolean current = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, !current).apply();
        rowBiometric.setText(biometricLabel());
        toast("Biometric unlock " + (!current ? "enabled" : "disabled"));
    }

    private void showSessionTimeoutPicker() {
        String[] labels = {"1 Minute", "2 Minutes", "5 Minutes", "10 Minutes", "Never"};
        long[]   values = {60_000L, 120_000L, 300_000L, 600_000L, 0L};
        new AlertDialog.Builder(this)
                .setTitle("Session Timeout")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putLong(KEY_AUTO_LOCK, values[i]).apply();
                    rowSessionTimeout.setText(sessionTimeoutLabel());
                })
                .show();
    }

    private void showFailedLimitPicker() {
        String[] labels = {"3 attempts", "5 attempts", "10 attempts", "20 attempts"};
        int[]    values = {3, 5, 10, 20};
        new AlertDialog.Builder(this)
                .setTitle("Failed Attempt Limit")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putInt(KEY_FAILED_LIMIT, values[i]).apply();
                    rowFailedLimit.setText(failedLimitLabel());
                })
                .show();
    }

    private void showLockoutDurationPicker() {
        String[] labels = {"30 Seconds", "1 Minute", "5 Minutes", "15 Minutes", "1 Hour"};
        long[]   values = {30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L};
        new AlertDialog.Builder(this)
                .setTitle("Lockout Duration")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putLong(KEY_LOCKOUT_DURATION, values[i]).apply();
                    rowLockoutDuration.setText(lockoutDurationLabel());
                })
                .show();
    }

    private void showDecoyVaultDialog() {
        if (repo.isDecoyEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Decoy Vault")
                    .setMessage("The decoy vault is currently enabled.\n\n"
                            + "Entering the decoy PIN at the lock screen shows a separate, empty vault.\n\n"
                            + "Disable decoy vault?")
                    .setPositiveButton("Disable", (d, w) -> {
                        repo.disableDecoy();
                        rowDecoyVault.setText(decoyVaultLabel());
                        toast("Decoy vault disabled");
                    })
                    .setNegativeButton("Keep Enabled", null)
                    .show();
        } else {
            EditText etPin = makeNumberEdit("Decoy PIN (must differ from real PIN)");
            EditText etConfirm = makeNumberEdit("Confirm decoy PIN");
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(20), dp(8), dp(20), dp(8));
            layout.addView(etPin);
            layout.addView(etConfirm);
            new AlertDialog.Builder(this)
                    .setTitle("Set Up Decoy Vault")
                    .setMessage("Enter a separate PIN. When unlocked with this PIN the vault will "
                            + "appear empty, protecting your real files.")
                    .setView(layout)
                    .setPositiveButton("Enable", (d, w) -> {
                        String pin1 = etPin.getText().toString().trim();
                        String pin2 = etConfirm.getText().toString().trim();
                        if (pin1.length() < 4) { toast("Decoy PIN must be at least 4 digits"); return; }
                        if (!pin1.equals(pin2)) { toast("Decoy PINs do not match"); return; }
                        if (repo.verifyPin(pin1)) { toast("Decoy PIN must differ from your real PIN"); return; }
                        boolean ok = repo.setupDecoyPin(pin1);
                        rowDecoyVault.setText(decoyVaultLabel());
                        toast(ok ? "Decoy vault enabled" : "Failed to set up decoy vault");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showAutoDestroyDialog() {
        if (VaultAutoDestroyManager.isEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Auto-Destroy Mode")
                    .setMessage("Auto-destroy is currently ENABLED.\n\n"
                            + "The vault will be wiped after "
                            + VaultAutoDestroyManager.getThreshold(this)
                            + " failed unlock attempts.\n\nDisable this feature?")
                    .setPositiveButton("Disable", (d, w) -> {
                        VaultAutoDestroyManager.disable(this);
                        rowAutoDestroy.setText(autoDestroyLabel());
                        toast("Auto-destroy disabled");
                    })
                    .setNegativeButton("Keep Enabled", null)
                    .show();
        } else {
            EditText etConfirm = new EditText(this);
            etConfirm.setHint("Type \"I understand\" to continue");
            etConfirm.setInputType(InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(this)
                    .setTitle("⚠ Enable Auto-Destroy Mode")
                    .setMessage("When enabled the entire vault will be permanently wiped after too "
                            + "many consecutive failed unlock attempts.\n\n"
                            + "THIS ACTION CANNOT BE UNDONE.\n\n"
                            + "Type \"I understand\" below to proceed:")
                    .setView(wrap(etConfirm))
                    .setPositiveButton("Continue", (d, w) -> {
                        if (!"I understand".equals(etConfirm.getText().toString().trim())) {
                            toast("You must type \"I understand\" exactly");
                            return;
                        }
                        showAutoDestroyThresholdPicker();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void showAutoDestroyThresholdPicker() {
        String[] labels = {"5 failed attempts", "10 failed attempts", "20 failed attempts"};
        int[]    values = {5, 10, 20};
        new AlertDialog.Builder(this)
                .setTitle("Wipe After How Many Attempts?")
                .setItems(labels, (d, i) -> {
                    VaultAutoDestroyManager.setEnabled(this, true, values[i]);
                    rowAutoDestroy.setText(autoDestroyLabel());
                    toast("Auto-destroy enabled — vault wipes after " + values[i] + " failed attempts");
                })
                .show();
    }

    // ─── Privacy Dialogs ──────────────────────────────────────────

    private void toggleGpsStrip() {
        boolean on = prefs.getBoolean(KEY_GPS_AUTO_STRIP, true);
        prefs.edit().putBoolean(KEY_GPS_AUTO_STRIP, !on).apply();
        rowGpsStrip.setText(gpsStripLabel());
    }

    private void toggleSecureExport() {
        boolean on = prefs.getBoolean(KEY_SECURE_EXPORT, false);
        prefs.edit().putBoolean(KEY_SECURE_EXPORT, !on).apply();
        rowSecureExport.setText(secureExportLabel());
    }

    private void showWatermarkDialog() {
        boolean enabled = prefs.getBoolean(KEY_WATERMARK_ENABLED, false);
        String  current = prefs.getString(KEY_WATERMARK_TEXT, "");

        EditText etText = new EditText(this);
        etText.setHint("Watermark text (leave blank to disable)");
        etText.setInputType(InputType.TYPE_CLASS_TEXT);
        etText.setText(current);

        new AlertDialog.Builder(this)
                .setTitle("Watermark on Export")
                .setMessage("Enter text to embed as a watermark on exported images, or leave blank to disable:")
                .setView(wrap(etText))
                .setPositiveButton("Save", (d, w) -> {
                    String text = etText.getText().toString().trim();
                    boolean enable = !text.isEmpty();
                    prefs.edit()
                            .putBoolean(KEY_WATERMARK_ENABLED, enable)
                            .putString(KEY_WATERMARK_TEXT, text)
                            .apply();
                    rowWatermark.setText(watermarkLabel());
                    toast(enable ? "Watermark enabled" : "Watermark disabled");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Import Dialogs ───────────────────────────────────────────

    private void showImportDefaultPicker() {
        String[] labels = {"Ask Every Time", "Copy", "Move"};
        String[] keys   = {"ask", "copy", "move"};
        new AlertDialog.Builder(this)
                .setTitle("Default Import Mode")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putString(KEY_IMPORT_DEFAULT, keys[i]).apply();
                    rowImportDefault.setText(importDefaultLabel());
                })
                .show();
    }

    private void toggleFaceDetection() {
        boolean on = prefs.getBoolean(KEY_FACE_DETECTION, false);
        prefs.edit().putBoolean(KEY_FACE_DETECTION, !on).apply();
        rowFaceDetection.setText(faceDetectionLabel());
    }

    private void toggleDuplicateDetect() {
        boolean on = prefs.getBoolean(KEY_DUPLICATE_DETECT, true);
        prefs.edit().putBoolean(KEY_DUPLICATE_DETECT, !on).apply();
        rowDuplicateDetect.setText(duplicateDetectLabel());
    }

    // ─── Display Dialogs ──────────────────────────────────────────

    private void showGridColumnsPicker() {
        String[] labels = {"2 Columns", "3 Columns", "4 Columns"};
        int[]    values = {2, 3, 4};
        new AlertDialog.Builder(this)
                .setTitle("Grid Column Count")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putInt(KEY_GRID_COLUMNS, values[i]).apply();
                    rowGridColumns.setText(gridColumnsLabel());
                })
                .show();
    }

    private void showThumbQualityPicker() {
        String[] labels = {"Low", "Medium", "High"};
        String[] keys   = {"low", "medium", "high"};
        new AlertDialog.Builder(this)
                .setTitle("Thumbnail Quality")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putString(KEY_THUMB_QUALITY, keys[i]).apply();
                    rowThumbQuality.setText(thumbQualityLabel());
                })
                .show();
    }

    private void toggleShowNames() {
        boolean on = prefs.getBoolean(KEY_SHOW_NAMES, true);
        prefs.edit().putBoolean(KEY_SHOW_NAMES, !on).apply();
        rowShowNames.setText(showNamesLabel());
    }

    private void toggleShowSizes() {
        boolean on = prefs.getBoolean(KEY_SHOW_SIZES, true);
        prefs.edit().putBoolean(KEY_SHOW_SIZES, !on).apply();
        rowShowSizes.setText(showSizesLabel());
    }

    private void showDefaultSortPicker() {
        String[] labels = {"Date Added", "Date Taken", "Name", "Size"};
        String[] keys   = {"date_added", "date_taken", "name", "size"};
        new AlertDialog.Builder(this)
                .setTitle("Default Sort")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putString(KEY_DEFAULT_SORT, keys[i]).apply();
                    rowDefaultSort.setText(defaultSortLabel());
                })
                .show();
    }

    // ─── Backup Dialogs ───────────────────────────────────────────

    private void startBackupArchive() {
        Toast.makeText(this, "Creating backup archive…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File archive = VaultBackupManager.createBackupArchive(this, repo, null, null);
            runOnUiThread(() -> {
                if (archive == null) {
                    toast("Backup failed");
                    return;
                }
                repo.recordBackupCreated();
                rowLastBackup.setText(lastBackupLabel());

                Uri uri;
                try {
                    uri = FileProvider.getUriForFile(
                            this, getPackageName() + ".provider", archive);
                } catch (Exception e) {
                    uri = Uri.fromFile(archive);
                }
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/zip");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Save Backup Archive"));
            });
        }).start();
    }

    private void showBackupReminderPicker() {
        String[] labels = {"Weekly", "Monthly", "Never"};
        String[] keys   = {"weekly", "monthly", "never"};
        new AlertDialog.Builder(this)
                .setTitle("Backup Reminder")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putString(KEY_BACKUP_REMINDER, keys[i]).apply();
                    rowBackupReminder.setText(backupReminderLabel());
                })
                .show();
    }

    // ─── Activity Dialogs ─────────────────────────────────────────

    private void showLogRetentionPicker() {
        String[] labels = {"30 Days", "90 Days", "1 Year", "Forever"};
        String[] keys   = {"30days", "90days", "1year", "forever"};
        new AlertDialog.Builder(this)
                .setTitle("Log Retention Period")
                .setItems(labels, (d, i) -> {
                    prefs.edit().putString(KEY_LOG_RETENTION, keys[i]).apply();
                    rowLogRetention.setText(logRetentionLabel());
                })
                .show();
    }

    private void showClearActivityLogDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Activity Log")
                .setMessage("This will permanently delete all activity log entries. Continue?")
                .setPositiveButton("Clear", (d, w) -> {
                    repo.clearActivityLog();
                    toast("Activity log cleared");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Danger Zone: Wipe Vault ──────────────────────────────────

    private void showWipeVaultStep1() {
        EditText etPin = makeNumberEdit("Enter your PIN to confirm");
        new AlertDialog.Builder(this)
                .setTitle("⚠ Wipe Entire Vault")
                .setMessage("This will PERMANENTLY DELETE all files in the vault.\nThis cannot be undone.\n\nEnter your PIN to continue:")
                .setView(wrap(etPin))
                .setPositiveButton("Continue", (d, w) -> {
                    if (!repo.verifyPin(etPin.getText().toString())) {
                        toast("Incorrect PIN");
                        return;
                    }
                    showWipeVaultStep2();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWipeVaultStep2() {
        EditText etConfirm = new EditText(this);
        etConfirm.setHint("Type WIPE VAULT to confirm");
        etConfirm.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Final Confirmation")
                .setMessage("Type WIPE VAULT (all caps) to permanently wipe the vault:")
                .setView(wrap(etConfirm))
                .setPositiveButton("Wipe Vault", (d, w) -> {
                    if (!"WIPE VAULT".equals(etConfirm.getText().toString().trim())) {
                        toast("You must type WIPE VAULT exactly");
                        return;
                    }
                    performWipe();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performWipe() {
        Toast.makeText(this, "Wiping vault…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            repo.wipeAllFiles();
            runOnUiThread(() -> {
                toast("Vault wiped");
                finish();
            });
        }).start();
    }

    // ─── Utility helpers ─────────────────────────────────────────

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private EditText makeNumberEdit(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return et;
    }

    /** Wraps a view in a LinearLayout with horizontal padding for dialogs. */
    private LinearLayout wrap(View v) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(20), dp(8), dp(20), dp(8));
        ll.addView(v);
        return ll;
    }

    private String formatAutoLock(long ms) {
        if (ms == 0) return "Never";
        long mins = ms / 60_000;
        return mins + " Minute" + (mins > 1 ? "s" : "");
    }

    private String formatDuration(long ms) {
        if (ms < 60_000) return (ms / 1000) + " Seconds";
        if (ms < 3_600_000) return (ms / 60_000) + " Minute" + (ms / 60_000 > 1 ? "s" : "");
        return (ms / 3_600_000) + " Hour" + (ms / 3_600_000 > 1 ? "s" : "");
    }

    private String formatDate(long ms) {
        return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                .format(new Date(ms));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.getDefault());
    }

    private String sortDisplayName(String key) {
        switch (key) {
            case "date_taken": return "Date Taken";
            case "name":       return "Name";
            case "size":       return "Size";
            default:           return "Date Added";
        }
    }

    private String logRetentionDisplayName(String key) {
        switch (key) {
            case "30days":  return "30 Days";
            case "1year":   return "1 Year";
            case "forever": return "Forever";
            default:        return "90 Days";
        }
    }
}

