package com.prajwal.myfirstapp.tasks;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Day view time-blocking activity.
 *
 * Shows hours 6 AM – 11 PM as vertical time slots.
 * Tasks with a dueTime are rendered as coloured blocks at their time slot.
 */
public class TimeBlockActivity extends AppCompatActivity {

    private static final int START_HOUR = 6;   // 6 AM
    private static final int END_HOUR   = 23;  // 11 PM (inclusive)
    private static final int SLOT_HEIGHT_DP = 64;

    private TaskRepository repo;
    private Calendar currentDay;
    private LinearLayout timelineContainer;
    private TextView tvDateLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new TaskRepository(this);
        currentDay = Calendar.getInstance();
        clearTime(currentDay);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1A1A2E"));

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.parseColor("#252545"));
        toolbar.setPadding(dp(8), dp(12), dp(16), dp(12));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_media_previous);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnPrev = new TextView(this);
        btnPrev.setText("◀");
        btnPrev.setTextColor(Color.parseColor("#6C63FF"));
        btnPrev.setTextSize(18);
        btnPrev.setPadding(dp(12), dp(4), dp(12), dp(4));
        btnPrev.setOnClickListener(v -> navigateDay(-1));
        navRow.addView(btnPrev);

        tvDateLabel = new TextView(this);
        tvDateLabel.setTextColor(Color.WHITE);
        tvDateLabel.setTextSize(16);
        tvDateLabel.setTypeface(null, Typeface.BOLD);
        tvDateLabel.setGravity(Gravity.CENTER);
        tvDateLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        navRow.addView(tvDateLabel);

        TextView btnNext = new TextView(this);
        btnNext.setText("▶");
        btnNext.setTextColor(Color.parseColor("#6C63FF"));
        btnNext.setTextSize(18);
        btnNext.setPadding(dp(12), dp(4), dp(12), dp(4));
        btnNext.setOnClickListener(v -> navigateDay(1));
        navRow.addView(btnNext);

        toolbar.addView(navRow);
        root.addView(toolbar);

        // ── Timeline ScrollView ───────────────────────────────────
        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);

        timelineContainer = new LinearLayout(this);
        timelineContainer.setOrientation(LinearLayout.VERTICAL);
        timelineContainer.setPadding(dp(8), dp(8), dp(8), dp(24));
        scroll.addView(timelineContainer);
        root.addView(scroll);

        setContentView(root);
        renderDay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDay();
    }

    private void navigateDay(int offset) {
        currentDay.add(Calendar.DAY_OF_YEAR, offset);
        renderDay();
    }

    private void renderDay() {
        tvDateLabel.setText(
                new SimpleDateFormat("EEE, MMM dd yyyy", Locale.US).format(currentDay.getTime()));

        timelineContainer.removeAllViews();

        String dayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(currentDay.getTime());

        List<Task> allTasks = repo.getAllTasks();

        for (int hour = START_HOUR; hour <= END_HOUR; hour++) {
            LinearLayout slot = new LinearLayout(this);
            slot.setOrientation(LinearLayout.HORIZONTAL);
            slot.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(SLOT_HEIGHT_DP));
            slotLp.setMargins(0, 0, 0, dp(1));
            slot.setLayoutParams(slotLp);

            // Hour label
            TextView tvHour = new TextView(this);
            tvHour.setText(formatHour(hour));
            tvHour.setTextColor(Color.parseColor("#9E9E9E"));
            tvHour.setTextSize(12);
            tvHour.setGravity(Gravity.TOP | Gravity.END);
            tvHour.setPadding(dp(4), dp(6), dp(8), dp(6));
            tvHour.setMinWidth(dp(52));
            slot.addView(tvHour);

            // Divider
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#2A2A4A"));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1),
                    LinearLayout.LayoutParams.MATCH_PARENT);
            divider.setLayoutParams(divLp);
            slot.addView(divider);

            // Task block area
            LinearLayout blockArea = new LinearLayout(this);
            blockArea.setOrientation(LinearLayout.VERTICAL);
            blockArea.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            blockArea.setPadding(dp(4), dp(4), dp(4), dp(4));

            boolean hasTask = false;
            for (Task t : allTasks) {
                if (t.isTrashed) continue;
                if (!dayStr.equals(t.dueDate)) continue;
                if (!t.hasDueTime()) continue;
                int taskHour = parseHour(t.dueTime);
                if (taskHour != hour) continue;

                hasTask = true;
                LinearLayout block = buildTaskBlock(t);
                blockArea.addView(block);
            }

            if (!hasTask) {
                blockArea.setBackgroundColor(Color.parseColor("#14142A"));
            }

            slot.addView(blockArea);
            timelineContainer.addView(slot);
        }
    }

    private LinearLayout buildTaskBlock(Task task) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(2));
        block.setLayoutParams(lp);

        int color = Task.getPriorityColorFor(task.priority);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(adjustAlpha(color, 0.25f));
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(2), color);
        block.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(task.title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, Typeface.BOLD);
        block.addView(tvTitle);

        TextView tvTime = new TextView(this);
        tvTime.setText(task.getFormattedDueTime());
        tvTime.setTextColor(Color.parseColor("#9E9E9E"));
        tvTime.setTextSize(11);
        block.addView(tvTime);

        block.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskDetailActivity.class);
            intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id);
            startActivity(intent);
        });

        return block;
    }

    private String formatHour(int hour) {
        if (hour == 0) return "12 AM";
        if (hour == 12) return "12 PM";
        return hour < 12 ? hour + " AM" : (hour - 12) + " PM";
    }

    private int parseHour(String dueTime) {
        if (dueTime == null || dueTime.isEmpty()) return -1;
        try {
            return Integer.parseInt(dueTime.split(":")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.argb(alpha, r, g, b);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
