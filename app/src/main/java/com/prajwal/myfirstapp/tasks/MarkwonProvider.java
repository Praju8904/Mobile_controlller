package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.widget.TextView;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/**
 * Singleton provider for Markwon instance configured for dark-theme task rendering.
 * Renders Markdown in task descriptions / notes with:
 *   - Strikethrough
 *   - Task list checkboxes
 *   - Standard inline formatting (bold, italic, code, links)
 */
public class MarkwonProvider {

    private static Markwon instance;

    /**
     * Get the singleton Markwon instance, creating it lazily.
     */
    public static Markwon get(Context context) {
        if (instance == null) {
            synchronized (MarkwonProvider.class) {
                if (instance == null) {
                    instance = Markwon.builder(context.getApplicationContext())
                            .usePlugin(StrikethroughPlugin.create())
                            .usePlugin(TaskListPlugin.create(context.getApplicationContext()))
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * Convenience: render Markdown text into a TextView.
     * Falls back to plain setText if text is null/empty.
     */
    public static void render(Context context, TextView textView, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            textView.setText("");
            return;
        }
        get(context).setMarkdown(textView, markdown);
    }
}
