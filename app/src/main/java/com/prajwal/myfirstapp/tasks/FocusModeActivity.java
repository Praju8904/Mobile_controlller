package com.prajwal.myfirstapp.tasks;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen focus mode activity with Pomodoro timer.
 */
public class FocusModeActivity extends AppCompatActivity {

    private long pomodoroMillis = 25 * 60 * 1000L; // default; overridden from settings

    private TaskRepository repo;
    private List<Task> todayTasks = new ArrayList<>();
    private int currentIndex = 0;

    // ── Views ────────────────────────────────────────────────────
    private TextView tvTitle, tvPriority, tvTimer, tvStatus;
    private LinearLayout subtaskContainer, btnContainer;
    private PomodoroRingView ringView;
    private View emptyState;
    private ScrollView contentScroll;

    // ── Timer ────────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long remainingMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new TaskRepository(this);

        // Read pomodoro duration from settings
        TaskManagerSettings settings = TaskManagerSettings.getInstance(this);
        pomodoroMillis = settings.pomodoroFocusMinutes * 60 * 1000L;
        remainingMillis = pomodoroMillis;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0A0A1A"));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(16), dp(16), dp(8));

        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.parseColor("#9E9E9E"));
        btnClose.setTextSize(20);
        btnClose.setOnClickListener(v -> finish());
        toolbar.addView(btnClose);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Focus Mode");
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setTextSize(18);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(tvHeader);

        TextView tvNext = new TextView(this);
        tvNext.setText("Skip ›");
        tvNext.setTextColor(Color.parseColor("#6C63FF"));
        tvNext.setTextSize(15);
        tvNext.setOnClickListener(v -> skipTask());
        toolbar.addView(tvNext);

        root.addView(toolbar);

        // ── Empty state ───────────────────────────────────────────
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        empty.setLayoutParams(emptyLp);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText("🎉");
        tvEmoji.setTextSize(64);
        tvEmoji.setGravity(Gravity.CENTER);
        empty.addView(tvEmoji);

        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("No tasks for today 🎉");
        tvEmpty.setTextColor(Color.WHITE);
        tvEmpty.setTextSize(22);
        tvEmpty.setTypeface(null, Typeface.BOLD);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(dp(24), dp(12), dp(24), dp(8));
        empty.addView(tvEmpty);

        TextView tvEmptySub = new TextView(this);
        tvEmptySub.setText("You're all caught up. Great work!");
        tvEmptySub.setTextColor(Color.parseColor("#9E9E9E"));
        tvEmptySub.setTextSize(15);
        tvEmptySub.setGravity(Gravity.CENTER);
        empty.addView(tvEmptySub);

        emptyState = empty;
        root.addView(empty);

        // ── Content ───────────────────────────────────────────────
        contentScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        contentScroll.setLayoutParams(scrollLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(24));
        contentScroll.addView(content);

        // Priority badge
        tvPriority = new TextView(this);
        tvPriority.setTextSize(13);
        tvPriority.setTypeface(null, Typeface.BOLD);
        tvPriority.setPadding(dp(12), dp(4), dp(12), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(20));
        tvPriority.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        badgeLp.bottomMargin = dp(16);
        tvPriority.setLayoutParams(badgeLp);
        content.addView(tvPriority);

        // Task title
        tvTitle = new TextView(this);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(32);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(24));
        content.addView(tvTitle);

        // Pomodoro ring
        ringView = new PomodoroRingView(this);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(200), dp(200));
        ringLp.gravity = Gravity.CENTER_HORIZONTAL;
        ringLp.bottomMargin = dp(8);
        ringView.setLayoutParams(ringLp);
        content.addView(ringView);

        // Timer label
        tvTimer = new TextView(this);
        tvTimer.setTextColor(Color.WHITE);
        tvTimer.setTextSize(36);
        tvTimer.setTypeface(null, Typeface.BOLD);
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setPadding(0, 0, 0, dp(4));
        content.addView(tvTimer);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#9E9E9E"));
        tvStatus.setTextSize(13);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, dp(20));
        content.addView(tvStatus);

        // Timer buttons
        LinearLayout timerBtns = new LinearLayout(this);
        timerBtns.setOrientation(LinearLayout.HORIZONTAL);
        timerBtns.setGravity(Gravity.CENTER);

        TextView btnStart = buildBtn("▶ Start", "#6C63FF");
        btnStart.setOnClickListener(v -> startTimer(btnStart));
        timerBtns.addView(btnStart);

        TextView btnReset = buildBtn("↺ Reset", "#374151");
        btnReset.setOnClickListener(v -> resetTimer(btnStart));
        timerBtns.addView(btnReset);

        content.addView(timerBtns);

        // Subtasks
        TextView tvSubtaskHeader = new TextView(this);
        tvSubtaskHeader.setText("Subtasks");
        tvSubtaskHeader.setTextColor(Color.parseColor("#9E9E9E"));
        tvSubtaskHeader.setTextSize(13);
        tvSubtaskHeader.setTypeface(null, Typeface.BOLD);
        tvSubtaskHeader.setPadding(0, dp(20), 0, dp(8));
        content.addView(tvSubtaskHeader);

        subtaskContainer = new LinearLayout(this);
        subtaskContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(subtaskContainer);

        // Mark Done & Next Task buttons
        btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setGravity(Gravity.CENTER);
        btnContainer.setPadding(0, dp(24), 0, 0);

        TextView btnDone = buildBtn("✓ Mark Done", "#34C759");
        btnDone.setOnClickListener(v -> markTaskDone());
        btnContainer.addView(btnDone);

        TextView btnNextTask = buildBtn("→ Next Task", "#FF9500");
        btnNextTask.setOnClickListener(v -> skipTask());
        btnContainer.addView(btnNextTask);

        content.addView(btnContainer);
        root.addView(contentScroll);

        setContentView(root);
        loadTasks();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void loadTasks() {
        todayTasks.clear();
        for (Task t : repo.getAllTasks()) {
            if (!t.isTrashed && t.isActive() && t.isDueToday()) todayTasks.add(t);
        }
        todayTasks.sort((a, b) -> Integer.compare(a.getPriorityWeight(), b.getPriorityWeight()));
        currentIndex = 0;
        showCurrentTask();
    }

    private void showCurrentTask() {
        if (todayTasks.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            contentScroll.setVisibility(View.GONE);
            return;
        }
        emptyState.setVisibility(View.GONE);
        contentScroll.setVisibility(View.VISIBLE);

        Task t = todayTasks.get(currentIndex);
        tvTitle.setText(t.title);

        int pColor = Task.getPriorityColorFor(t.priority);
        tvPriority.setText("  " + Task.getPriorityLabelFor(t.priority) + "  ");
        tvPriority.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pColor);
        bg.setCornerRadius(dp(20));
        tvPriority.setBackground(bg);
        tvPriority.setVisibility(t.priority.equals(Task.PRIORITY_NONE) ? View.GONE : View.VISIBLE);

        subtaskContainer.removeAllViews();
        if (t.hasSubtasks()) {
            for (SubTask st : t.subtasks) {
                CheckBox cb = new CheckBox(this);
                cb.setText(st.title);
                cb.setTextColor(Color.WHITE);
                cb.setTextSize(14);
                cb.setChecked(st.isCompleted);
                cb.setPadding(dp(4), dp(6), dp(4), dp(6));
                cb.setOnCheckedChangeListener((btn, checked) -> {
                    st.isCompleted = checked;
                    repo.updateTask(t);
                });
                subtaskContainer.addView(cb);
            }
        }

        resetTimer(null);
        tvStatus.setText("Pomodoro " + (currentIndex + 1) + " of " + todayTasks.size());
    }

    private void startTimer(TextView btnStart) {
        if (timerRunning) {
            countDownTimer.cancel();
            timerRunning = false;
            btnStart.setText("▶ Start");
            return;
        }
        timerRunning = true;
        btnStart.setText("⏸ Pause");
        countDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millis) {
                remainingMillis = millis;
                updateTimerDisplay(millis);
                ringView.setProgress(1f - (float) millis / pomodoroMillis);
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                btnStart.setText("▶ Start");
                Toast.makeText(FocusModeActivity.this,
                        "Pomodoro complete! Take a break 🎉", Toast.LENGTH_LONG).show();
                ringView.setProgress(1f);
            }
        }.start();
    }

    private void resetTimer(TextView btnStart) {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        remainingMillis = pomodoroMillis;
        updateTimerDisplay(remainingMillis);
        ringView.setProgress(0f);
        if (btnStart != null) btnStart.setText("▶ Start");
    }

    private void updateTimerDisplay(long millis) {
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        tvTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
    }

    private void markTaskDone() {
        if (todayTasks.isEmpty()) return;
        Task t = todayTasks.get(currentIndex);
        t.markCompleted();
        repo.updateTask(t);
        Toast.makeText(this, "🎉 Task completed!", Toast.LENGTH_SHORT).show();
        todayTasks.remove(currentIndex);
        if (currentIndex >= todayTasks.size()) currentIndex = 0;
        showCurrentTask();
    }

    private void skipTask() {
        if (todayTasks.isEmpty()) return;
        currentIndex = (currentIndex + 1) % todayTasks.size();
        showCurrentTask();
    }

    private TextView buildBtn(String text, String color) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(24));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));
        return btn;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ── Inner Pomodoro Ring View ──────────────────────────────────

    private static class PomodoroRingView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f; // 0..1

        PomodoroRingView(android.content.Context ctx) {
            super(ctx);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(20);
            bgPaint.setColor(Color.parseColor("#2A2A4A"));

            fgPaint.setStyle(Paint.Style.STROKE);
            fgPaint.setStrokeWidth(20);
            fgPaint.setStrokeCap(Paint.Cap.ROUND);
            fgPaint.setColor(Color.parseColor("#6C63FF"));
        }

        void setProgress(float p) {
            this.progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float pad = 20f;
            RectF oval = new RectF(pad, pad, w - pad, h - pad);
            canvas.drawArc(oval, -90, 360, false, bgPaint);
            if (progress > 0f) {
                canvas.drawArc(oval, -90, 360 * progress, false, fgPaint);
            }
        }
    }
}
