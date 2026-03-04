package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Feature 15: Task Vault — manages visibility of private/hidden tasks.
 * Private tasks show as "[Private Task]" placeholders when locked.
 * Biometric authentication required to unlock and view private task details.
 * Auto-locks when the activity goes to background (onPause).
 */
public class TaskVaultManager {

    private static final String PREFS = "task_vault";
    private static final String KEY_FIRST_TIME_HINT = "first_time_hint_shown";

    private static TaskVaultManager instance;
    private boolean isUnlocked = false;
    private final Context context;

    private TaskVaultManager(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public static synchronized TaskVaultManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new TaskVaultManager(ctx);
        }
        return instance;
    }

    /** Returns true if the vault is currently unlocked (private tasks visible). */
    public boolean isUnlocked() {
        return isUnlocked;
    }

    /** Unlock the vault (called after successful biometric authentication). */
    public void unlock() {
        isUnlocked = true;
    }

    /** Lock the vault (called in onPause to re-hide private tasks). */
    public void lock() {
        isUnlocked = false;
    }

    /** Returns true if device supports biometric authentication. */
    public boolean isBiometricAvailable(Context ctx) {
        try {
            BiometricManager bm = BiometricManager.from(ctx);
            return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Show BiometricPrompt to unlock the vault.
     * Calls onSuccess.run() on successful authentication.
     * If biometrics are not available, unlocks directly.
     */
    public void promptUnlock(FragmentActivity activity, Runnable onSuccess) {
        if (!isBiometricAvailable(activity)) {
            // If no biometrics, just unlock directly
            isUnlocked = true;
            activity.runOnUiThread(onSuccess);
            return;
        }

        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                isUnlocked = true;
                activity.runOnUiThread(onSuccess);
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Auth error: " + errString, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onAuthenticationFailed() {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show());
            }
        };

        BiometricPrompt prompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity), callback);
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Task Vault")
                .setSubtitle("Authenticate to view private tasks")
                .setNegativeButtonText("Cancel")
                .build();
        prompt.authenticate(info);
    }

    /**
     * Returns true if the first-time privacy hint has been shown before.
     * Call markFirstTimeHintShown() after showing it.
     */
    public boolean hasShownFirstTimeHint() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_FIRST_TIME_HINT, false);
    }

    public void markFirstTimeHintShown() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FIRST_TIME_HINT, true).apply();
    }
}
