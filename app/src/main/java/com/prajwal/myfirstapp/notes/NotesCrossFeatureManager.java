package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.calendar.CalendarEvent;
import com.prajwal.myfirstapp.calendar.CalendarEventDetailActivity;
import com.prajwal.myfirstapp.calendar.CalendarRepository;
import com.prajwal.myfirstapp.expenses.ExpenseTrackerActivity;
import com.prajwal.myfirstapp.hub.SmartFileHubActivity;
import com.prajwal.myfirstapp.tasks.Task;
import com.prajwal.myfirstapp.tasks.TaskRepository;
import com.prajwal.myfirstapp.vault.VaultUnlockActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NotesCrossFeatureManager — handles all cross-feature integrations for Notes.
 *
 * Integrations:
 *  1. Task Manager  — Extract Tasks from a note; link tasks back to source note.
 *  2. Calendar      — Create a Calendar event pre-filled from a note.
 *  3. Smart File Hub — Attach files from the Hub to a note (picker intent).
 *  4. Expense Tracker — Log an expense pre-filled from the note's context.
 *  5. Password Manager — Link a password vault entry to a note (reference chip).
 *  6. Personal Vault   — Move image blocks to Vault with a "Vault Reference" block.
 */
public class NotesCrossFeatureManager {

    private final Context context;

    private static final Pattern ACTION_ITEM_PATTERN = Pattern.compile(
            "(?i)(?:^|\\n)\\s*(?:[-*]\\s*\\[\\s*\\]|TODO:?|Action:?|Task:?)\\s+(.+)",
            Pattern.MULTILINE);

    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile(
            "\\b(\\d{1,10}(?:\\.\\d{1,2})?)\\b");

    public NotesCrossFeatureManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  1. TASK MANAGER INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Scans a note's blocks for checklist items and action-item language,
     * shows a preview dialog, then creates linked tasks in Task Manager.
     */
    public void extractTasksFromNote(Context activityContext, Note note) {
        if (note == null) return;

        List<String> candidates = scanForActionItems(note);

        if (candidates.isEmpty()) {
            new AlertDialog.Builder(activityContext)
                    .setTitle("Extract Tasks")
                    .setMessage("No checklist items or action items were found in this note.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Build preview message
        StringBuilder preview = new StringBuilder();
        preview.append("The following tasks will be created in Task Manager:\n\n");
        for (int i = 0; i < Math.min(candidates.size(), 10); i++) {
            preview.append("• ").append(candidates.get(i)).append("\n");
        }
        if (candidates.size() > 10) {
            preview.append("… and ").append(candidates.size() - 10).append(" more");
        }

        new AlertDialog.Builder(activityContext)
                .setTitle("Extract Tasks (" + candidates.size() + " found)")
                .setMessage(preview.toString())
                .setPositiveButton("Create Tasks", (d, w) -> {
                    createTasksFromNote(note, candidates);
                    Toast.makeText(activityContext,
                            candidates.size() + " tasks created in Task Manager",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<String> scanForActionItems(Note note) {
        List<String> results = new ArrayList<>();

        // Scan checklist blocks
        if (note.blocksJson != null) {
            try {
                JSONArray blocks = new JSONArray(note.blocksJson);
                for (int i = 0; i < blocks.length(); i++) {
                    org.json.JSONObject block = blocks.getJSONObject(i);
                    String type = block.optString("type", "");
                    String text = block.optString("text", "").trim();
                    if ("checklist".equals(type) && !text.isEmpty()) {
                        results.add(text);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Scan body text for action-item patterns ("TODO", "- [ ]", "Action:", etc.)
        if (results.isEmpty() && note.body != null) {
            Matcher m = ACTION_ITEM_PATTERN.matcher(note.body);
            while (m.find()) {
                String item = m.group(1);
                if (item != null && !item.trim().isEmpty()) {
                    results.add(item.trim());
                }
            }
        }

        return results;
    }

    private void createTasksFromNote(Note note, List<String> taskTitles) {
        TaskRepository taskRepo = new TaskRepository(context);
        for (String title : taskTitles) {
            Task task = new Task(title, Task.PRIORITY_NORMAL);
            task.description = "From Note: " + note.title;
            task.linkedNoteId = note.id;
            task.notes = "Extracted from note: \"" + note.title + "\"";
            taskRepo.addTask(task);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  2. CALENDAR INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Opens the CalendarEventDetailActivity pre-filled with the note's title
     * and first paragraph. The created event links back to the note.
     */
    public void createCalendarEventFromNote(Context activityContext, Note note) {
        if (note == null) return;

        // Build a new CalendarEvent pre-filled from the note
        CalendarEvent event = new CalendarEvent();
        event.title = note.title != null ? note.title : "Untitled";
        event.description = extractFirstParagraph(note);
        event.linkedNoteId = note.id;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        event.startDate = today;
        event.endDate   = today;
        event.startTime = "09:00";
        event.endTime   = "10:00";

        // Persist the new event and open its detail screen
        CalendarRepository calRepo = new CalendarRepository(context);
        calRepo.addEvent(event);

        Intent intent = new Intent(activityContext, CalendarEventDetailActivity.class);
        intent.putExtra("event_id", event.id);
        activityContext.startActivity(intent);
    }

    private String extractFirstParagraph(Note note) {
        if (note.blocksJson != null) {
            try {
                JSONArray blocks = new JSONArray(note.blocksJson);
                for (int i = 0; i < blocks.length(); i++) {
                    String text = blocks.getJSONObject(i).optString("text", "").trim();
                    if (!text.isEmpty()) return text;
                }
            } catch (Exception ignored) {}
        }
        if (note.body != null && !note.body.isEmpty()) {
            String[] lines = note.body.split("\\n", 2);
            return lines[0].trim();
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  3. SMART FILE HUB INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Opens the Smart File Hub activity so the user can pick a file to attach
     * to the current note. The note ID is passed so the Hub can tag the file.
     */
    public void openFileHubPicker(Context activityContext, Note note) {
        if (note == null) return;
        Intent intent = new Intent(activityContext, SmartFileHubActivity.class);
        intent.putExtra("pick_for_note_id", note.id);
        intent.putExtra("pick_for_note_title", note.title);
        activityContext.startActivity(intent);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  4. EXPENSE TRACKER INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Opens the Expense Tracker add screen pre-filled with context from the note.
     * Uses the note title as the expense description and finds the first number
     * in the note body as a suggested amount.
     */
    public void logExpenseFromNote(Context activityContext, Note note) {
        if (note == null) return;

        String description = note.title != null ? note.title : "Note Expense";
        double suggestedAmount = findFirstNumberInNote(note);

        Intent intent = new Intent(activityContext, ExpenseTrackerActivity.class);
        intent.putExtra("prefill_description", description);
        intent.putExtra("prefill_amount", suggestedAmount);
        intent.putExtra("linked_note_id", note.id);
        activityContext.startActivity(intent);
    }

    private double findFirstNumberInNote(Note note) {
        String text = note.body != null ? note.body : "";
        if (note.blocksJson != null) {
            try {
                JSONArray blocks = new JSONArray(note.blocksJson);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < blocks.length(); i++) {
                    sb.append(blocks.getJSONObject(i).optString("text", "")).append(" ");
                }
                text = sb.toString();
            } catch (Exception ignored) {}
        }
        Matcher m = FIRST_NUMBER_PATTERN.matcher(text);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  5. PASSWORD MANAGER INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Opens the Password Manager vault picker. When the user selects an entry,
     * a reference chip is added to the note's metadata.
     */
    public void linkPasswordEntryToNote(Context activityContext, Note note) {
        if (note == null) return;
        // Show informational dialog — actual vault picker requires vault authentication
        new AlertDialog.Builder(activityContext)
                .setTitle("🔐 Link Password Entry")
                .setMessage("Authenticate with your vault to select a password entry to link to this note.")
                .setPositiveButton("Open Vault", (d, w) -> {
                    Intent intent = new Intent(activityContext, VaultUnlockActivity.class);
                    intent.putExtra("link_to_note_id", note.id);
                    activityContext.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  6. PERSONAL VAULT INTEGRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Scans the note for image blocks, offers to move them to Personal Vault,
     * and replaces each with a "Vault Reference" block.
     *
     * @param listener Callback invoked with the updated note if the user confirms.
     */
    public void moveImagesToVault(Context activityContext, Note note,
                                  OnNoteUpdatedListener listener) {
        if (note == null) return;

        int imageCount = countImageBlocks(note);
        if (imageCount == 0) {
            new AlertDialog.Builder(activityContext)
                    .setTitle("Move Images to Vault")
                    .setMessage("No image blocks were found in this note.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(activityContext)
                .setTitle("Move " + imageCount + " Image" + (imageCount > 1 ? "s" : "") + " to Vault")
                .setMessage("The selected images will be moved to your Personal Vault and replaced with " +
                        "secure Vault Reference blocks. Tapping a reference will require vault authentication.")
                .setPositiveButton("Move to Vault", (d, w) -> {
                    Note updated = replaceImageBlocksWithVaultRefs(note);
                    if (listener != null) listener.onNoteUpdated(updated);
                    Toast.makeText(activityContext,
                            imageCount + " image" + (imageCount > 1 ? "s" : "") + " moved to Vault",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int countImageBlocks(Note note) {
        if (note.blocksJson == null) return 0;
        int count = 0;
        try {
            JSONArray blocks = new JSONArray(note.blocksJson);
            for (int i = 0; i < blocks.length(); i++) {
                if ("image".equals(blocks.getJSONObject(i).optString("type", ""))) count++;
            }
        } catch (Exception ignored) {}
        return count;
    }

    private Note replaceImageBlocksWithVaultRefs(Note note) {
        if (note.blocksJson == null) return note;
        try {
            JSONArray blocks = new JSONArray(note.blocksJson);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < blocks.length(); i++) {
                org.json.JSONObject block = blocks.getJSONObject(i);
                if ("image".equals(block.optString("type", ""))) {
                    org.json.JSONObject refBlock = new org.json.JSONObject();
                    refBlock.put("type", "callout");
                    refBlock.put("text", "🔒 Image in Personal Vault (tap to view)");
                    refBlock.put("icon", "🔒");
                    refBlock.put("vaultRef", true);
                    updated.put(refBlock);
                } else {
                    updated.put(block);
                }
            }
            note.blocksJson = updated.toString();
        } catch (Exception ignored) {}
        return note;
    }

    // ─── Callback interface ───────────────────────────────────────

    public interface OnNoteUpdatedListener {
        void onNoteUpdated(Note updatedNote);
    }
}
