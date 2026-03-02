package com.prajwal.myfirstapp.widgets;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.Note;
import com.prajwal.myfirstapp.notes.NoteEditorActivity;
import com.prajwal.myfirstapp.notes.NoteRepository;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  IDEA CAPTURE WIDGET PROVIDER — Home screen widget for quickly capturing ideas.
 *  Creates a quick note from the widget's text input and saves it.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class IdeaCaptureWidgetProvider extends AppWidgetProvider {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static final String ACTION_CAPTURE_IDEA = "com.prajwal.myfirstapp.ACTION_CAPTURE_IDEA";
    private static final String ACTION_OPEN_NOTES = "com.prajwal.myfirstapp.ACTION_OPEN_NOTES";
    private static final String PREFS_NAME = "widget_ideas";
    private static final String KEY_LAST_IDEA = "last_idea";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WIDGET LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_idea_capture);

        // Click on widget opens the notes app
        Intent openIntent = new Intent(context, IdeaCaptureWidgetProvider.class);
        openIntent.setAction(ACTION_OPEN_NOTES);
        PendingIntent openPendingIntent = PendingIntent.getBroadcast(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetContainer, openPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BROADCAST HANDLING
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_OPEN_NOTES.equals(intent.getAction())) {
            // Open the NoteEditorActivity with a new note pre-tagged as "Quick Idea"
            Intent launchIntent = new Intent(context, NoteEditorActivity.class);
            launchIntent.putExtra("quick_idea", true);
            launchIntent.putExtra("category", "Ideas");
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SAVE QUICK IDEA
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Save a quick idea captured from the widget (called from NoteEditorActivity
     * when launched via widget).
     */
    public static void saveQuickIdea(Context context, String idea) {
        if (idea == null || idea.trim().isEmpty()) return;

        // Save as a note via SmartNotesHelper
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_IDEA, idea).apply();

        // Create a quick note
        Note note = new Note();
        note.title = "💡 Quick Idea";
        note.body = idea;
        note.category = "Ideas";
        note.tags = new java.util.ArrayList<>();
        note.tags.add("quick-idea");
        note.tags.add("widget");
        note.createdAt = System.currentTimeMillis();
        note.updatedAt = System.currentTimeMillis();

        NoteRepository repo = new NoteRepository(context);
        repo.addNote(note);
    }

    @Override
    public void onEnabled(Context context) {
        // Widget first created
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget removed
    }
}
