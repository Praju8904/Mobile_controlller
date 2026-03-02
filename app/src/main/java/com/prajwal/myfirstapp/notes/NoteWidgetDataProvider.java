package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.todo.TodoItem;
import com.prajwal.myfirstapp.todo.TodoRepository;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NoteWidgetDataProvider — clean data API layer for home-screen widgets.
 *
 * This class provides all the data that home-screen widgets will need,
 * without coupling widget implementations to the internal repository details.
 *
 * Widget types that can be supported:
 * 1. Today's To-Do items widget  — {@link #getTodayTasks(Context)}
 * 2. Recent Notes widget          — {@link #getRecentNotes(Context, int)}
 * 3. Quick Capture widget         — {@link #saveQuickCaptureNote(Context, String)}
 * 4. To-Do Progress widget        — {@link #getTodayProgress(Context)}
 */
public class NoteWidgetDataProvider {

    // ─── Quick data models used by widgets ───────────────────────

    /** Lightweight snapshot of a note for widget display. */
    public static class NoteWidgetItem {
        public final String id;
        public final String title;
        public final String preview;
        public final String colorHex;
        public final long updatedAt;

        public NoteWidgetItem(String id, String title, String preview,
                              String colorHex, long updatedAt) {
            this.id        = id;
            this.title     = title;
            this.preview   = preview;
            this.colorHex  = colorHex;
            this.updatedAt = updatedAt;
        }
    }

    /** Lightweight snapshot of a to-do task for widget display. */
    public static class TaskWidgetItem {
        public final String id;
        public final String listId;
        public final String title;
        public final boolean isCompleted;
        public final String priority;
        public final String dueTime;

        public TaskWidgetItem(String id, String listId, String title,
                              boolean isCompleted, String priority, String dueTime) {
            this.id          = id;
            this.listId      = listId;
            this.title       = title;
            this.isCompleted = isCompleted;
            this.priority    = priority;
            this.dueTime     = dueTime;
        }
    }

    /** Progress snapshot for the ring-chart widget. */
    public static class TodayProgress {
        public final int total;
        public final int completed;
        public final float percent;

        public TodayProgress(int total, int completed) {
            this.total     = total;
            this.completed = completed;
            this.percent   = total > 0 ? (completed * 100f / total) : 0f;
        }
    }

    // ─── Public API ───────────────────────────────────────────────

    /**
     * Returns to-do tasks due today, ordered by priority then time.
     * Suitable for the "Today's To-Do" widget.
     */
    public static List<TaskWidgetItem> getTodayTasks(Context context) {
        TodoRepository repo = new TodoRepository(context);
        String today = todayDateString();
        List<TaskWidgetItem> result = new ArrayList<>();
        for (TodoItem item : repo.getAllItems()) {
            if (today.equals(item.dueDate) && !TodoItem.STATUS_CANCELLED.equals(item.status)) {
                result.add(new TaskWidgetItem(
                        item.id, item.listId, item.title,
                        item.isCompleted, item.priority, item.dueTime));
            }
        }
        return result;
    }

    /**
     * Returns the N most-recently-modified notes (non-archived, non-trashed).
     * Suitable for the "Recent Notes" widget.
     */
    public static List<NoteWidgetItem> getRecentNotes(Context context, int limit) {
        NoteRepository repo = new NoteRepository(context);
        List<NoteWidgetItem> result = new ArrayList<>();
        List<Note> notes = repo.getAllNotes();
        // getAllNotes already returns newest-first
        for (Note note : notes) {
            if (note.isArchived || note.isTrashed) continue;
            result.add(new NoteWidgetItem(
                    note.id, note.title, note.plainTextPreview,
                    note.colorHex, note.updatedAt));
            if (result.size() >= limit) break;
        }
        return result;
    }

    /**
     * Saves a quick-capture note to the Quick Notes folder (or root if not found).
     * Suitable for the "Quick Capture" widget input action.
     */
    public static void saveQuickCaptureNote(Context context, String text) {
        if (text == null || text.trim().isEmpty()) return;
        NoteRepository repo = new NoteRepository(context);
        NoteFolderRepository folderRepo = new NoteFolderRepository(context, repo);

        // Find or use root
        String folderId = null;
        for (NoteFolder folder : folderRepo.getAllFolders()) {
            if ("Quick Notes".equalsIgnoreCase(folder.name)) {
                folderId = folder.id;
                break;
            }
        }

        Note note = new Note();
        note.title    = text.trim().length() > 60 ? text.trim().substring(0, 60) + "…" : text.trim();
        note.body     = text.trim();
        note.category = "Personal";
        note.folderId = folderId;
        note.updatePlainTextPreview();
        repo.addNote(note);
    }

    /**
     * Returns a progress snapshot of today's tasks.
     * Suitable for the "To-Do Progress" ring-chart widget.
     */
    public static TodayProgress getTodayProgress(Context context) {
        List<TaskWidgetItem> tasks = getTodayTasks(context);
        int completed = 0;
        for (TaskWidgetItem t : tasks) {
            if (t.isCompleted) completed++;
        }
        return new TodayProgress(tasks.size(), completed);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private static String todayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
