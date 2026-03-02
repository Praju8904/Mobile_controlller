package com.prajwal.myfirstapp.connectivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Offline-first command queue — the "Outbox" Pattern.
 *
 * When a data command (TASK_ADD, NOTE_UPDATE, CAL_ADD, etc.) cannot be
 * delivered to the PC because it is unreachable, the command is saved here.
 * The queue is persisted to SharedPreferences so it survives app restarts.
 *
 * Usage:
 *   outbox.enqueue("TASK_ADD:Buy milk:normal");   // called on send failure
 *   outbox.flush(connectionManager, () -> {...});  // called when back online
 */
public class SyncOutbox {

    private static final String TAG = "SyncOutbox";
    private static final String PREFS_NAME = "sync_outbox_prefs";
    private static final String QUEUE_KEY  = "pending_commands";
    private static final int    MAX_RETRIES = 5;

    private final Context context;

    public SyncOutbox(Context context) {
        this.context = context.getApplicationContext();
    }

    // ─── Public API ─────────────────────────────────────────────

    /** Add a command to the outbox for later delivery. */
    public synchronized void enqueue(String command) {
        try {
            JSONArray queue = loadQueue();
            JSONObject entry = new JSONObject();
            entry.put("command", command);
            entry.put("enqueued_at", System.currentTimeMillis());
            entry.put("retries", 0);
            queue.put(entry);
            saveQueue(queue);
            Log.i(TAG, "Queued offline: " + abbrev(command));
        } catch (JSONException e) {
            Log.e(TAG, "Enqueue error: " + e.getMessage());
        }
    }

    /** Number of commands currently waiting to be sent. */
    public synchronized int getPendingCount() {
        return loadQueue().length();
    }

    /**
     * Attempt to deliver all queued commands.
     *
     * Each successfully-sent command is removed from the queue.
     * Failed commands have their retry counter incremented; after
     * MAX_RETRIES they are silently dropped.
     *
     * Runs on a background thread. {@code onComplete} is invoked when done.
     */
    public void flush(ConnectionManager connectionManager, Runnable onComplete) {
        new Thread(() -> {
            synchronized (SyncOutbox.this) {
                JSONArray queue = loadQueue();
                if (queue.length() == 0) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                Log.i(TAG, "Flushing outbox — " + queue.length() + " pending command(s)");
                JSONArray remaining = new JSONArray();

                for (int i = 0; i < queue.length(); i++) {
                    try {
                        JSONObject entry = queue.getJSONObject(i);
                        String command = entry.getString("command");
                        int retries   = entry.optInt("retries", 0);

                        boolean sent = connectionManager.sendCommandSync(command);

                        if (sent) {
                            Log.i(TAG, "✓ Flushed: " + abbrev(command));
                        } else if (retries < MAX_RETRIES) {
                            entry.put("retries", retries + 1);
                            remaining.put(entry);
                            Log.w(TAG, "Retry " + (retries + 1) + "/" + MAX_RETRIES
                                    + ": " + abbrev(command));
                        } else {
                            Log.e(TAG, "Dropped after " + MAX_RETRIES
                                    + " retries: " + abbrev(command));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Flush entry error: " + e.getMessage());
                    }
                }

                saveQueue(remaining);
                Log.i(TAG, "Flush done. Remaining: " + remaining.length());
            }
            if (onComplete != null) onComplete.run();
        }).start();
    }

    /** Remove all pending commands (e.g., after a full server-side reset). */
    public synchronized void clear() {
        saveQueue(new JSONArray());
        Log.i(TAG, "Outbox cleared");
    }

    // ─── Internal ────────────────────────────────────────────────

    private JSONArray loadQueue() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(QUEUE_KEY, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveQueue(JSONArray queue) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(QUEUE_KEY, queue.toString()).apply();
    }

    private static String abbrev(String s) {
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }
}
