package com.prajwal.myfirstapp.tasks;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Transparent activity that handles ACTION_SEND intents and opens
 * QuickAddTaskSheet pre-filled with the shared content.
 * Gap ref: 13.5
 */
public class ShareReceiverActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            finish();
            return;
        }

        String sharedText    = intent.getStringExtra(Intent.EXTRA_TEXT);
        String sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

        // Build title from subject or first line of text
        String title = "";
        if (sharedSubject != null && !sharedSubject.trim().isEmpty()) {
            title = sharedSubject.trim();
        } else if (sharedText != null && !sharedText.trim().isEmpty()) {
            title = sharedText.split("\n")[0].trim();
        }
        if (title.length() > 120) title = title.substring(0, 120);

        String notes = (sharedText != null && sharedText.length() > title.length())
            ? sharedText : "";

        Task preFilledTask = new Task(title, Task.PRIORITY_NORMAL);
        preFilledTask.notes = notes;

        // Apply smart detection
        SmartDetectionHelper.applyAll(preFilledTask, title);

        // Show QuickAddTaskSheet pre-filled
        QuickAddTaskSheet sheet = QuickAddTaskSheet.newInstance(preFilledTask);
        sheet.show(getSupportFragmentManager(), "quick_add_share");
        sheet.setOnDismissListener(d -> finish());
    }
}
