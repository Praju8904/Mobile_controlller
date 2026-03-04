package com.prajwal.myfirstapp.tasks;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.List;
import java.util.function.Consumer;

/**
 * Helper for launching Android speech recognition and returning the result.
 * Gap ref: 4.7
 */
public class VoiceInputHelper {

    private final ActivityResultLauncher<Intent> launcher;
    private final Context context;
    private Consumer<String> onResult;

    public VoiceInputHelper(ActivityResultCaller caller, Context context) {
        this.context = context;
        launcher = caller.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    List<String> matches = result.getData()
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty() && onResult != null) {
                        onResult.accept(matches.get(0));
                    }
                }
            }
        );
    }

    public void startListening(Consumer<String> onResult) {
        this.onResult = onResult;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your task…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context,
                "Voice input not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if voice recognition is available on this device.
     */
    public static boolean isAvailable(Context context) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        return !context.getPackageManager().queryIntentActivities(intent, 0).isEmpty();
    }
}
