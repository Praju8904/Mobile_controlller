package com.prajwal.myfirstapp.tasks;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages undo functionality for destructive task operations.
 * <p>
 * Provides a styled Material Snackbar with an UNDO action for all destructive
 * operations: complete, archive/trash, bulk complete, and bulk trash. Maintains
 * a snapshot of the affected task(s) so the operation can be reversed. When undo
 * is not tapped within the Snackbar's display window, the pending write (server sync
 * or other deferred work) is committed. Calling {@link #commitPendingWrite()} forces
 * any outstanding deferred work immediately — invoke this in {@code onStop()} to
 * prevent writes from being lost when the Activity goes to background.
 * </p>
 */
public class TaskUndoManager {

    /** Callback for the host Activity to refresh data after undo or commit. */
    public interface UndoCallback {
        /** Called after an undo action is performed. */
        void onUndoPerformed();
        /** Called after a pending write is committed (timeout or superseded). */
        void onWriteCommitted();
    }

    // ─── Snackbar styling constants ──────────────────────────────
    private static final int COLOR_BG        = Color.parseColor("#1E293B");
    private static final int COLOR_TEXT       = Color.parseColor("#F1F5F9");
    private static final int COLOR_ACTION     = Color.parseColor("#3B82F6");
    private static final long SNACKBAR_TIMEOUT = 5000L;
    private static final long COMMIT_BUFFER   = 500L;

    // ─── Instance state ──────────────────────────────────────────
    private final View anchorView;
    private final UndoCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Snackbar activeSnackbar;
    private Runnable pendingWrite;
    private Task singleSnapshot;
    private List<Task> bulkSnapshot;
    private String pendingAction;

    /**
     * @param anchorView the View used to anchor Material Snackbars
     *                   (typically {@code findViewById(android.R.id.content)})
     * @param callback   lifecycle callback for undo/commit events
     */
    public TaskUndoManager(View anchorView, UndoCallback callback) {
        this.anchorView = anchorView;
        this.callback = callback;
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Record a single-task destructive action and show an undo Snackbar.
     *
     * @param snapshot  a deep copy of the task <b>before</b> the action was applied
     * @param action    label identifying the action (e.g. "complete", "trash")
     * @param message   Snackbar text to display
     * @param undoRunnable code to execute if the user taps UNDO
     * @param commitRunnable optional deferred work (e.g. server sync) committed after timeout;
     *                        may be {@code null} if the work was already done
     */
    public void record(Task snapshot, String action, String message,
                       Runnable undoRunnable, Runnable commitRunnable) {
        // Commit any superseded pending write first
        commitPendingWrite();

        this.singleSnapshot = snapshot;
        this.bulkSnapshot = null;
        this.pendingAction = action;
        this.pendingWrite = commitRunnable;

        showSnackbar(message, undoRunnable);
    }

    /**
     * Record a bulk destructive action and show an undo Snackbar.
     *
     * @param snapshots  deep copies of all affected tasks before the action
     * @param action     label identifying the action (e.g. "bulk_complete", "bulk_trash")
     * @param message    Snackbar text to display
     * @param undoRunnable code to execute if the user taps UNDO
     * @param commitRunnable optional deferred work committed after timeout
     */
    public void recordBulk(List<Task> snapshots, String action, String message,
                           Runnable undoRunnable, Runnable commitRunnable) {
        commitPendingWrite();

        this.singleSnapshot = null;
        this.bulkSnapshot = new ArrayList<>(snapshots);
        this.pendingAction = action;
        this.pendingWrite = commitRunnable;

        showSnackbar(message, undoRunnable);
    }

    /**
     * Immediately commit any outstanding deferred write and dismiss the active
     * Snackbar. Call this in {@code Activity.onStop()} to ensure no writes are lost
     * when the app goes to background.
     */
    public void commitPendingWrite() {
        handler.removeCallbacksAndMessages(null);
        if (pendingWrite != null) {
            pendingWrite.run();
            pendingWrite = null;
            if (callback != null) callback.onWriteCommitted();
        }
        if (activeSnackbar != null && activeSnackbar.isShown()) {
            activeSnackbar.dismiss();
            activeSnackbar = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNALS
    // ═══════════════════════════════════════════════════════════════

    private void showSnackbar(String message, Runnable undoRunnable) {
        if (activeSnackbar != null && activeSnackbar.isShown()) {
            activeSnackbar.dismiss();
        }

        activeSnackbar = Snackbar.make(anchorView, message, Snackbar.LENGTH_INDEFINITE);

        // Style: dark background, light text, accent action
        View sbView = activeSnackbar.getView();
        sbView.setBackgroundColor(COLOR_BG);
        TextView msgView = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (msgView != null) {
            msgView.setTextColor(COLOR_TEXT);
        }
        activeSnackbar.setActionTextColor(COLOR_ACTION);

        activeSnackbar.setAction("UNDO", v -> {
            handler.removeCallbacksAndMessages(null);
            pendingWrite = null;
            if (undoRunnable != null) undoRunnable.run();
            if (callback != null) callback.onUndoPerformed();
        });

        activeSnackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    commitPendingWrite();
                }
            }
        });

        activeSnackbar.show();

        // Auto-dismiss after timeout
        handler.postDelayed(() -> {
            if (activeSnackbar != null && activeSnackbar.isShown()) {
                activeSnackbar.dismiss();
            }
        }, SNACKBAR_TIMEOUT);
    }
}
