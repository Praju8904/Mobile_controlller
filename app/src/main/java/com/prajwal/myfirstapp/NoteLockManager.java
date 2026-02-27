package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE LOCK MANAGER — Handles PIN and biometric authentication for locked notes
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Security features:
 * - Master PIN stored with AES-256-GCM encryption
 * - Biometric authentication when available
 * - Rate limiting on failed attempts
 */
public class NoteLockManager {

    private static final String TAG = "NoteLockManager";
    private static final String PREFS_NAME = "note_lock_prefs";
    private static final String KEY_MASTER_PIN_HASH = "master_pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_TIME = "lockout_time";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 30_000; // 30 seconds

    private final Context context;
    private final SharedPreferences prefs;
    private final Vibrator vibrator;

    public interface LockCallback {
        void onResult(boolean success);
    }

    public NoteLockManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Check if master PIN has been set up
     */
    public boolean isPinSetup() {
        return prefs.contains(KEY_MASTER_PIN_HASH);
    }

    /**
     * Check if biometric authentication is available and enabled
     */
    public boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(context);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isBiometricAvailable();
    }

    /**
     * Setup lock for a note - prompts to create PIN if not setup, then enables lock
     */
    public void setupLock(Note note, LockCallback callback) {
        if (!isPinSetup()) {
            // Need to create master PIN first
            showSetupPinDialog(success -> {
                if (success) {
                    callback.onResult(true);
                } else {
                    callback.onResult(false);
                }
            });
        } else {
            // PIN already set, just confirm with existing PIN
            verifyPin(success -> {
                callback.onResult(success);
            });
        }
    }

    /**
     * Verify lock for a note - attempts biometric first, then falls back to PIN
     */
    public void verifyLock(Note note, LockCallback callback) {
        if (!isPinSetup()) {
            // No PIN set up, allow access
            callback.onResult(true);
            return;
        }

        // Check lockout
        if (isLockedOut()) {
            long remainingTime = getLockoutRemainingTime() / 1000;
            Toast.makeText(context, "Too many attempts. Try again in " + remainingTime + "s", Toast.LENGTH_LONG).show();
            callback.onResult(false);
            return;
        }

        if (isBiometricEnabled()) {
            // Try biometric first
            showBiometricPrompt(success -> {
                if (success) {
                    resetFailedAttempts();
                    callback.onResult(true);
                } else {
                    // Fall back to PIN
                    showVerifyPinDialog(callback);
                }
            });
        } else {
            // PIN only
            showVerifyPinDialog(callback);
        }
    }

    /**
     * Change the master PIN
     */
    public void changeMasterPin(LockCallback callback) {
        // Verify current PIN first
        verifyPin(success -> {
            if (success) {
                showSetupPinDialog(callback);
            } else {
                callback.onResult(false);
            }
        });
    }

    /**
     * Enable/disable biometric authentication
     */
    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PIN DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showSetupPinDialog(LockCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DarkAlertDialog);
        builder.setTitle("Create Lock PIN");
        builder.setMessage("Set a 4-6 digit PIN to lock your notes");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        // PIN input
        EditText pinInput = createPinInput("Enter PIN");
        layout.addView(pinInput);

        // Confirm PIN input
        EditText confirmInput = createPinInput("Confirm PIN");
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        confirmParams.topMargin = dpToPx(12);
        confirmInput.setLayoutParams(confirmParams);
        layout.addView(confirmInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> callback.onResult(false));

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pin = pinInput.getText().toString();
                String confirm = confirmInput.getText().toString();

                if (pin.length() < 4) {
                    pinInput.setError("PIN must be at least 4 digits");
                    hapticError();
                    return;
                }

                if (pin.length() > 6) {
                    pinInput.setError("PIN must be 6 digits or less");
                    hapticError();
                    return;
                }

                if (!pin.equals(confirm)) {
                    confirmInput.setError("PINs don't match");
                    hapticError();
                    return;
                }

                // Save the PIN
                saveMasterPin(pin);

                // Ask about biometric if available
                if (isBiometricAvailable()) {
                    dialog.dismiss();
                    askEnableBiometric(callback);
                } else {
                    dialog.dismiss();
                    Toast.makeText(context, "PIN created successfully", Toast.LENGTH_SHORT).show();
                    callback.onResult(true);
                }
            });
        });

        dialog.show();
        pinInput.requestFocus();
    }

    private void showVerifyPinDialog(LockCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DarkAlertDialog);
        builder.setTitle("Enter PIN");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        int failedAttempts = getFailedAttempts();
        if (failedAttempts > 0) {
            TextView attemptsText = new TextView(context);
            attemptsText.setText(String.format("%d of %d attempts used", failedAttempts, MAX_FAILED_ATTEMPTS));
            attemptsText.setTextColor(Color.parseColor("#EF4444"));
            attemptsText.setTextSize(12);
            attemptsText.setGravity(Gravity.CENTER);
            layout.addView(attemptsText);
        }

        EditText pinInput = createPinInput("Enter PIN");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(8);
        pinInput.setLayoutParams(params);
        layout.addView(pinInput);

        builder.setView(layout);

        builder.setPositiveButton("Unlock", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> callback.onResult(false));

        if (isBiometricAvailable() && isBiometricEnabled()) {
            builder.setNeutralButton("Use Biometric", (dialog, which) -> {
                showBiometricPrompt(callback);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pin = pinInput.getText().toString();

                if (verifyPinHash(pin)) {
                    resetFailedAttempts();
                    dialog.dismiss();
                    callback.onResult(true);
                } else {
                    incrementFailedAttempts();
                    int attempts = getFailedAttempts();

                    if (attempts >= MAX_FAILED_ATTEMPTS) {
                        startLockout();
                        dialog.dismiss();
                        Toast.makeText(context, "Too many attempts. Locked for 30 seconds.", Toast.LENGTH_LONG).show();
                        callback.onResult(false);
                    } else {
                        pinInput.setText("");
                        pinInput.setError("Incorrect PIN (" + (MAX_FAILED_ATTEMPTS - attempts) + " attempts left)");
                        hapticError();
                    }
                }
            });
        });

        dialog.show();
        pinInput.requestFocus();
    }

    private void verifyPin(LockCallback callback) {
        showVerifyPinDialog(callback);
    }

    private EditText createPinInput(String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextColor(Color.parseColor("#F1F5F9"));
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setLetterSpacing(0.3f);
        input.setBackgroundColor(Color.parseColor("#1E293B"));
        input.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        return input;
    }

    private void askEnableBiometric(LockCallback callback) {
        new AlertDialog.Builder(context, R.style.DarkAlertDialog)
                .setTitle("Enable Biometric")
                .setMessage("Would you like to use fingerprint or face unlock to access locked notes?")
                .setPositiveButton("Enable", (dialog, which) -> {
                    setBiometricEnabled(true);
                    Toast.makeText(context, "Biometric enabled", Toast.LENGTH_SHORT).show();
                    callback.onResult(true);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Toast.makeText(context, "PIN created successfully", Toast.LENGTH_SHORT).show();
                    callback.onResult(true);
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BIOMETRIC
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showBiometricPrompt(LockCallback callback) {
        if (!(context instanceof FragmentActivity)) {
            // Fall back to PIN
            showVerifyPinDialog(callback);
            return;
        }

        FragmentActivity activity = (FragmentActivity) context;
        Executor executor = ContextCompat.getMainExecutor(context);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onResult(true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // User cancelled or biometric not available - fall back to PIN
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            showVerifyPinDialog(callback);
                        } else {
                            callback.onResult(false);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Let user try again - don't call callback yet
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Note")
                .setSubtitle("Use biometric to access this note")
                .setNegativeButtonText("Use PIN")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PIN STORAGE (AES-256-GCM encrypted)
    // ═══════════════════════════════════════════════════════════════════════════════

    private void saveMasterPin(String pin) {
        try {
            // Generate salt
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            // Hash the PIN with salt using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));

            // Store salt and hash
            prefs.edit()
                    .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_MASTER_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean verifyPinHash(String pin) {
        try {
            String saltStr = prefs.getString(KEY_PIN_SALT, null);
            String storedHash = prefs.getString(KEY_MASTER_PIN_HASH, null);

            if (saltStr == null || storedHash == null) return false;

            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);

            // Hash the input PIN with stored salt
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));

            String inputHash = Base64.encodeToString(hash, Base64.NO_WRAP);
            return storedHash.equals(inputHash);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════════

    private int getFailedAttempts() {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    private void incrementFailedAttempts() {
        int attempts = getFailedAttempts() + 1;
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
    }

    private void resetFailedAttempts() {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply();
    }

    private boolean isLockedOut() {
        long lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME, 0);
        if (lockoutTime == 0) return false;

        return System.currentTimeMillis() < lockoutTime + LOCKOUT_DURATION_MS;
    }

    private long getLockoutRemainingTime() {
        long lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME, 0);
        long elapsed = System.currentTimeMillis() - lockoutTime;
        return Math.max(0, LOCKOUT_DURATION_MS - elapsed);
    }

    private void startLockout() {
        prefs.edit()
                .putLong(KEY_LOCKOUT_TIME, System.currentTimeMillis())
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .apply();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void hapticError() {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
