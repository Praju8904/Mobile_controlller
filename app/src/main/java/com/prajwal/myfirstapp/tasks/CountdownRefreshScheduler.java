package com.prajwal.myfirstapp.tasks;

import android.os.Handler;
import android.os.Looper;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Periodically refreshes the visible countdown badges in the task RecyclerView.
 *
 * <p>Uses {@code notifyItemRangeChanged} with a {@code "countdown"} payload so that
 * the adapter only rebinds the countdown badge — avoiding a full card rebind and
 * preventing image flicker or text jitter.</p>
 *
 * <p>Call {@link #start()} in {@code onResume()} and {@link #stop()} in {@code onPause()}.</p>
 */
public class CountdownRefreshScheduler {

    private static final long REFRESH_INTERVAL_MS = 60_000L; // 60 seconds
    public static final String PAYLOAD_COUNTDOWN = "countdown";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RecyclerView recyclerView;
    private boolean running;

    public CountdownRefreshScheduler(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    /**
     * Starts the periodic refresh. Safe to call multiple times.
     */
    public void start() {
        if (running) return;
        running = true;
        scheduleNext();
    }

    /**
     * Stops the periodic refresh and clears pending callbacks.
     */
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleNext() {
        if (!running) return;
        handler.postDelayed(() -> {
            if (!running) return;
            refreshVisibleRange();
            scheduleNext();
        }, REFRESH_INTERVAL_MS);
    }

    private void refreshVisibleRange() {
        if (recyclerView == null) return;
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (lm == null || adapter == null) return;

        if (lm instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) lm;
            int first = llm.findFirstVisibleItemPosition();
            int last = llm.findLastVisibleItemPosition();
            if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                adapter.notifyItemRangeChanged(first, last - first + 1, PAYLOAD_COUNTDOWN);
            }
        }
    }
}
