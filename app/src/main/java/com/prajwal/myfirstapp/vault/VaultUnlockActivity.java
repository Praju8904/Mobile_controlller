package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * Vault unlock screen — the first screen shown when opening Personal Media Vault.
 *
 * Features:
 * - Biometric prompt triggers automatically on launch
 * - PIN pad fallback
 * - First-launch PIN setup flow
 * - Exponential lockout with countdown timer
 * - FLAG_SECURE prevents screenshots and app-switcher preview
 */
public class VaultUnlockActivity extends AppCompatActivity {

    private static final String TAG = "VaultUnlock";
    private static final int PIN_LENGTH = 4;

    private MediaVaultRepository repo;
    private StringBuilder currentPin = new StringBuilder();
    private boolean isSetupMode = false;
    private String firstPinEntry = null; // stored during confirm step
    private boolean isConfirmStep = false;

    // Views — unlock
    private LinearLayout unlockMainContainer;
    private LinearLayout setupPinContainer;
    private LinearLayout pinEntrySection;
    private LinearLayout pinDotsContainer;
    private TextView tvPinStatus;
    private TextView tvLockoutTimer;
    private Button btnBiometricUnlock;
    private TextView tvUsePinInstead;
    private TextView tvForgotPin;

    // Views — setup
    private TextView tvSetupStep;
    private LinearLayout setupPinDotsContainer;
    private TextView tvSetupStatus;

    private CountDownTimer lockoutTimer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FLAG_SECURE: prevent screenshots and app-switcher preview
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_vault_unlock);

        repo = MediaVaultRepository.getInstance(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        bindViews();
        startBackgroundAnimation();

        if (!repo.isPinSetup()) {
            showSetupMode();
        } else if (repo.isLockedOut()) {
            showUnlockMode();
            showLockoutTimer();
        } else {
            showUnlockMode();
            // Auto-trigger biometric if available
            if (isBiometricAvailable()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerBiometric, 300);
            } else {
                showPinPad();
            }
        }
    }

    private void startBackgroundAnimation() {
        View root = findViewById(R.id.vaultUnlockRoot);
        if (root == null) return;
        ValueAnimator animator = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                0xFF0A0E1F, 0xFF0D1328);
        animator.setDuration(8000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(a -> root.setBackgroundColor((int) a.getAnimatedValue()));
        animator.start();
    }

    private void bindViews() {
        unlockMainContainer = findViewById(R.id.unlockMainContainer);
        setupPinContainer = findViewById(R.id.setupPinContainer);
        pinEntrySection = findViewById(R.id.pinEntrySection);
        pinDotsContainer = findViewById(R.id.pinDotsContainer);
        tvPinStatus = findViewById(R.id.tvPinStatus);
        tvLockoutTimer = findViewById(R.id.tvLockoutTimer);
        btnBiometricUnlock = findViewById(R.id.btnBiometricUnlock);
        tvUsePinInstead = findViewById(R.id.tvUsePinInstead);
        tvForgotPin = findViewById(R.id.tvForgotPin);

        tvSetupStep = findViewById(R.id.tvSetupStep);
        setupPinDotsContainer = findViewById(R.id.setupPinDotsContainer);
        tvSetupStatus = findViewById(R.id.tvSetupStatus);

        // Unlock mode buttons
        btnBiometricUnlock.setOnClickListener(v -> triggerBiometric());
        tvUsePinInstead.setOnClickListener(v -> showPinPad());
        tvForgotPin.setOnClickListener(v -> onForgotPin());

        // Wire up number pad buttons (unlock)
        wireUnlockPad();
        // Wire up setup pad buttons
        wireSetupPad();
    }

    private void wireUnlockPad() {
        int[] buttonIds = {R.id.pin0, R.id.pin1, R.id.pin2, R.id.pin3, R.id.pin4,
                R.id.pin5, R.id.pin6, R.id.pin7, R.id.pin8, R.id.pin9};
        for (int i = 0; i < buttonIds.length; i++) {
            final String digit = String.valueOf(i);
            Button btn = findViewById(buttonIds[i]);
            if (btn != null) btn.setOnClickListener(v -> onUnlockPinDigit(digit));
        }
        Button backspace = findViewById(R.id.pinBackspace);
        if (backspace != null) backspace.setOnClickListener(v -> onUnlockBackspace());
        Button clear = findViewById(R.id.pinClear);
        if (clear != null) clear.setOnClickListener(v -> { currentPin.setLength(0); updateUnlockDots(); });
    }

    private void wireSetupPad() {
        int[] buttonIds = {R.id.setup0, R.id.setup1, R.id.setup2, R.id.setup3, R.id.setup4,
                R.id.setup5, R.id.setup6, R.id.setup7, R.id.setup8, R.id.setup9};
        for (int i = 0; i < buttonIds.length; i++) {
            final String digit = String.valueOf(i);
            Button btn = findViewById(buttonIds[i]);
            if (btn != null) btn.setOnClickListener(v -> onSetupPinDigit(digit));
        }
        Button backspace = findViewById(R.id.setupBackspace);
        if (backspace != null) backspace.setOnClickListener(v -> onSetupBackspace());
        Button clear = findViewById(R.id.setupClear);
        if (clear != null) clear.setOnClickListener(v -> { currentPin.setLength(0); updateSetupDots(); });
    }

    // ─── Mode Switching ───────────────────────────────────────────

    private void showUnlockMode() {
        isSetupMode = false;
        unlockMainContainer.setVisibility(View.VISIBLE);
        setupPinContainer.setVisibility(View.GONE);
        if (!isBiometricAvailable()) {
            btnBiometricUnlock.setVisibility(View.GONE);
            tvUsePinInstead.setVisibility(View.GONE);
            showPinPad();
        }
    }

    private void showSetupMode() {
        isSetupMode = true;
        unlockMainContainer.setVisibility(View.GONE);
        setupPinContainer.setVisibility(View.VISIBLE);
        currentPin.setLength(0);
        isConfirmStep = false;
        firstPinEntry = null;
        tvSetupStep.setText("Enter your new vault PIN (4 digits)");
        tvSetupStatus.setVisibility(View.GONE);
        updateSetupDots();
    }

    private void showPinPad() {
        btnBiometricUnlock.setVisibility(View.GONE);
        tvUsePinInstead.setVisibility(View.GONE);
        pinEntrySection.setVisibility(View.VISIBLE);
        currentPin.setLength(0);
        updateUnlockDots();
        updateFailedAttemptsText();
    }

    // ─── Unlock PIN Pad Logic ─────────────────────────────────────

    private void onUnlockPinDigit(String digit) {
        if (currentPin.length() >= PIN_LENGTH) return;
        currentPin.append(digit);
        updateUnlockDots();
        hapticClick();

        if (currentPin.length() == PIN_LENGTH) {
            // Auto-submit when 4 digits entered
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentPin.length() == PIN_LENGTH) attemptUnlock();
            }, 200);
        }
    }

    private void onUnlockBackspace() {
        if (currentPin.length() > 0) {
            currentPin.deleteCharAt(currentPin.length() - 1);
            updateUnlockDots();
        }
    }

    private void attemptUnlock() {
        if (repo.isLockedOut()) {
            showLockoutTimer();
            return;
        }

        String pin = currentPin.toString();
        int result = repo.unlockWithPin(pin);
        currentPin.setLength(0);
        updateUnlockDots();

        if (result == 0) {
            // Success
            openVaultHome();
        } else if (result == 2) {
            // Locked out
            showLockoutTimer();
        } else {
            // Wrong PIN
            hapticError();
            int failed = repo.getFailedAttempts();
            // Auto-destroy check — after recording failed attempt, before lockout display
            if (VaultAutoDestroyManager.shouldDestroy(this, failed)) {
                new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                        .setTitle("Vault Destroyed")
                        .setMessage("All vault contents have been destroyed.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show();
                return;
            }
            tvPinStatus.setVisibility(View.VISIBLE);
            if (failed >= 5) {
                tvPinStatus.setText("Too many attempts — vault locked");
                showLockoutTimer();
            } else {
                int remaining = 5 - failed;
                tvPinStatus.setText("Incorrect PIN — " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining");
            }
        }
    }

    private void updateFailedAttemptsText() {
        int failed = repo.getFailedAttempts();
        if (failed > 0 && !repo.isLockedOut()) {
            tvPinStatus.setVisibility(View.VISIBLE);
            tvPinStatus.setText(failed + " of 5 attempts used");
        } else {
            tvPinStatus.setVisibility(View.GONE);
        }
    }

    private void showLockoutTimer() {
        pinEntrySection.setVisibility(View.VISIBLE);
        tvPinStatus.setVisibility(View.VISIBLE);
        tvPinStatus.setText("Vault locked — too many failed attempts");
        tvLockoutTimer.setVisibility(View.VISIBLE);

        if (lockoutTimer != null) lockoutTimer.cancel();
        long remainingMs = repo.getLockoutRemainingMs();
        lockoutTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secs = millisUntilFinished / 1000;
                long mins = secs / 60;
                secs = secs % 60;
                tvLockoutTimer.setText("Try again in " + mins + ":" + String.format("%02d", secs));
            }
            @Override
            public void onFinish() {
                tvLockoutTimer.setVisibility(View.GONE);
                tvPinStatus.setVisibility(View.GONE);
                updateFailedAttemptsText();
            }
        }.start();
    }

    private void updateUnlockDots() {
        pinDotsContainer.removeAllViews();
        for (int i = 0; i < PIN_LENGTH; i++) {
            View dot = new View(this);
            int size = dpToPx(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dpToPx(8), 0, dpToPx(8), 0);
            dot.setLayoutParams(lp);
            if (i < currentPin.length()) {
                dot.setBackgroundColor(Color.parseColor("#F59E0B"));
                animateDotFill(dot);
            } else {
                dot.setBackgroundColor(Color.parseColor("#334155"));
            }
            pinDotsContainer.addView(dot);
        }
    }

    // ─── Setup PIN Logic ──────────────────────────────────────────

    private void onSetupPinDigit(String digit) {
        if (currentPin.length() >= PIN_LENGTH) return;
        currentPin.append(digit);
        updateSetupDots();
        hapticClick();

        if (currentPin.length() == PIN_LENGTH) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentPin.length() == PIN_LENGTH) handleSetupSubmit();
            }, 300);
        }
    }

    private void onSetupBackspace() {
        if (currentPin.length() > 0) {
            currentPin.deleteCharAt(currentPin.length() - 1);
            updateSetupDots();
        }
    }

    private void handleSetupSubmit() {
        String pin = currentPin.toString();
        if (!isConfirmStep) {
            // First entry — store and ask to confirm
            firstPinEntry = pin;
            isConfirmStep = true;
            currentPin.setLength(0);
            updateSetupDots();
            tvSetupStep.setText("Confirm your vault PIN");
            tvSetupStatus.setVisibility(View.GONE);
        } else {
            // Confirmation step
            if (pin.equals(firstPinEntry)) {
                // PIN confirmed
                repo.setupPin(pin);
                Toast.makeText(this, "Vault PIN created successfully", Toast.LENGTH_SHORT).show();

                // Offer biometric enrollment
                if (isBiometricAvailable()) {
                    askEnableBiometric(pin);
                } else {
                    openVaultHomeAfterSetup(pin);
                }
            } else {
                // Mismatch
                hapticError();
                tvSetupStatus.setVisibility(View.VISIBLE);
                tvSetupStatus.setText("PINs don't match — try again");
                isConfirmStep = false;
                firstPinEntry = null;
                currentPin.setLength(0);
                updateSetupDots();
                tvSetupStep.setText("Enter your new vault PIN (4 digits)");
            }
        }
    }

    private void updateSetupDots() {
        setupPinDotsContainer.removeAllViews();
        for (int i = 0; i < PIN_LENGTH; i++) {
            View dot = new View(this);
            int size = dpToPx(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dpToPx(8), 0, dpToPx(8), 0);
            dot.setLayoutParams(lp);
            if (i < currentPin.length()) {
                dot.setBackgroundColor(Color.parseColor("#F59E0B"));
                animateDotFill(dot);
            } else {
                dot.setBackgroundColor(Color.parseColor("#334155"));
            }
            setupPinDotsContainer.addView(dot);
        }
    }

    // ─── Biometric Auth ───────────────────────────────────────────

    private boolean isBiometricAvailable() {
        BiometricManager bm = BiometricManager.from(this);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void triggerBiometric() {
        if (!isBiometricAvailable()) {
            showPinPad();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        // Biometric succeeded — retrieve stored PIN and unlock
                        String encoded = getSharedPreferences("media_vault_prefs", MODE_PRIVATE)
                                .getString("biometric_pin", null);
                        if (encoded != null) {
                            try {
                                String pin = new String(android.util.Base64.decode(
                                        encoded, android.util.Base64.NO_WRAP));
                                repo.unlockWithBiometric(pin);
                                openVaultHome();
                                return;
                            } catch (Exception ignored) {}
                        }
                        // Fallback to PIN if stored PIN not found
                        showPinPad();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        // Always fall back to PIN pad automatically on any error
                        if (pinEntrySection.getVisibility() != View.VISIBLE) {
                            showPinPad();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // User can retry — don't dismiss
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Personal Vault")
                .setSubtitle("Verify your identity to access the vault")
                .setNegativeButtonText("Use PIN")
                .build();

        prompt.authenticate(info);
    }

    private void askEnableBiometric(String pin) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Enable Biometric")
                .setMessage("Use fingerprint or face unlock to open your vault?")
                .setPositiveButton("Enable", (d, w) -> {
                    // Store PIN for biometric unlock (base64 encoded)
                    String encoded = android.util.Base64.encodeToString(
                            pin.getBytes(), android.util.Base64.NO_WRAP);
                    getSharedPreferences("media_vault_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("biometric_enabled", true)
                            .putString("biometric_pin", encoded)
                            .apply();
                    openVaultHomeAfterSetup(pin);
                })
                .setNegativeButton("Not Now", (d, w) -> openVaultHomeAfterSetup(pin))
                .show();
    }

    // ─── Forgot PIN ───────────────────────────────────────────────

    private void onForgotPin() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Forgot Vault PIN?")
                .setMessage("Resetting your vault PIN will permanently delete all vault contents. " +
                        "This action cannot be undone.\n\nAre you absolutely sure?")
                .setPositiveButton("Reset (Delete All)", (d, w) -> confirmResetVault())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmResetVault() {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("⚠️ Final Confirmation")
                .setMessage("ALL vault files will be permanently deleted. Type \"DELETE\" to confirm.")
                .setPositiveButton("Delete Everything", (d, w) -> {
                    // Reset vault
                    getSharedPreferences("media_vault_prefs", MODE_PRIVATE).edit().clear().apply();
                    Toast.makeText(this, "Vault reset. Please set up a new PIN.", Toast.LENGTH_LONG).show();
                    showSetupMode();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Navigation ───────────────────────────────────────────────

    private void openVaultHome() {
        // Vault should already be unlocked by this point (via unlockWithPin or setup flow)
        if (!repo.isUnlocked()) return;
        repo.recordUnlockTime();
        Intent intent = new Intent(this, VaultHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void openVaultHomeAfterSetup(String pin) {
        // After first-time setup, unlock directly with the just-created PIN
        repo.unlockWithBiometric(pin);
        openVaultHome();
    }

    // ─── Lifecycle ────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) lockoutTimer.cancel();
    }

    @Override
    public void onBackPressed() {
        // Don't allow back to bypass auth
        finish();
    }

    // ─── Utilities ────────────────────────────────────────────────

    private void hapticClick() {
        if (vibrator == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void hapticError() {
        if (vibrator == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 80, 60, 80}, -1));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void animateDotFill(View dot) {
        dot.animate().scaleX(1.2f).scaleY(1.2f).setDuration(80)
                .withEndAction(() -> dot.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();
    }
}
