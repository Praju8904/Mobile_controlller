package com.prajwal.myfirstapp.tasks;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Bottom sheet for manually logging time worked on a task.
 * Gap refs: 8.7, 8.9
 */
public class ManualTimeEntrySheet extends BottomSheetDialogFragment {

    public interface ManualTimeListener {
        void onTimeLogged(Task task, long startMs, long endMs);
    }

    private static final String ARG_TASK_JSON = "task_json";

    private Task task;
    private ManualTimeListener listener;
    private Calendar startCal;
    private Calendar endCal;
    private TextView tvStartTime;
    private TextView tvEndTime;
    private TextView tvDuration;

    public static ManualTimeEntrySheet newInstance(@Nullable Task task) {
        ManualTimeEntrySheet sheet = new ManualTimeEntrySheet();
        Bundle args = new Bundle();
        if (task != null) {
            try {
                args.putString(ARG_TASK_JSON, task.toJson().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(ManualTimeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Parse task arg
        if (getArguments() != null && getArguments().containsKey(ARG_TASK_JSON)) {
            try {
                String json = getArguments().getString(ARG_TASK_JSON);
                if (json != null) {
                    task = Task.fromJson(new org.json.JSONObject(json));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Default times: end = now, start = 1 hour ago
        endCal = Calendar.getInstance();
        startCal = Calendar.getInstance();
        startCal.add(Calendar.HOUR_OF_DAY, -1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1F35);
        bg.setCornerRadii(new float[]{32, 32, 32, 32, 0, 0, 0, 0});
        root.setBackground(bg);

        // Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Log Time Manually");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        // Time pickers row
        LinearLayout timeRow = new LinearLayout(requireContext());
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams timeRowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeRowLp.topMargin = 24;
        timeRow.setLayoutParams(timeRowLp);

        tvStartTime = createTimeButton("Start: " + formatTime(startCal));
        tvEndTime   = createTimeButton("End: " + formatTime(endCal));

        LinearLayout.LayoutParams halfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        halfLp.rightMargin = 8;
        tvStartTime.setLayoutParams(halfLp);
        timeRow.addView(tvStartTime);
        timeRow.addView(tvEndTime);
        root.addView(timeRow);

        // Duration display
        tvDuration = new TextView(requireContext());
        updateDurationLabel();
        tvDuration.setTextColor(0xFF94A3B8);
        tvDuration.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durLp.topMargin = 8;
        tvDuration.setLayoutParams(durLp);
        root.addView(tvDuration);

        // Wire up time picker clicks
        tvStartTime.setOnClickListener(v -> showTimePicker(true));
        tvEndTime.setOnClickListener(v -> showTimePicker(false));

        // Log Time button
        Button btnLog = new Button(requireContext());
        btnLog.setText("Log Time");
        btnLog.setTextColor(0xFFFFFFFF);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF6366F1);
        btnBg.setCornerRadius(12f);
        btnLog.setBackground(btnBg);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * getResources().getDisplayMetrics().density));
        logLp.topMargin = 24;
        btnLog.setLayoutParams(logLp);
        btnLog.setOnClickListener(v -> logTime());
        root.addView(btnLog);

        return root;
    }

    private TextView createTimeButton(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setPadding(24, 20, 24, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0F172A);
        bg.setStroke(2, 0xFF6366F1);
        bg.setCornerRadius(24f);
        tv.setBackground(bg);
        tv.setGravity(android.view.Gravity.CENTER);
        return tv;
    }

    private void showTimePicker(boolean isStart) {
        Calendar cal = isStart ? startCal : endCal;
        android.app.TimePickerDialog dialog = new android.app.TimePickerDialog(requireContext(),
            (timePicker, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                if (isStart) {
                    tvStartTime.setText("Start: " + formatTime(cal));
                } else {
                    tvEndTime.setText("End: " + formatTime(cal));
                }
                updateDurationLabel();
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false);
        dialog.show();
    }

    private void updateDurationLabel() {
        long diffMs = endCal.getTimeInMillis() - startCal.getTimeInMillis();
        if (diffMs <= 0) {
            tvDuration.setText("Duration: invalid (end must be after start)");
            tvDuration.setTextColor(0xFFEF4444);
        } else {
            long mins = diffMs / 60000;
            long h = mins / 60;
            long m = mins % 60;
            String dur = h > 0 ? h + "h " + m + "m" : m + "m";
            tvDuration.setText("Duration: " + dur);
            tvDuration.setTextColor(0xFF94A3B8);
        }
    }

    private void logTime() {
        long startMs = startCal.getTimeInMillis();
        long endMs   = endCal.getTimeInMillis();
        if (endMs <= startMs) {
            Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
            return;
        }
        if (task == null) {
            Toast.makeText(requireContext(), "No task selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (task.timerSessions == null) task.timerSessions = new java.util.ArrayList<>();
        task.timerSessions.add(new long[]{startMs, endMs});
        long durationMins = (endMs - startMs) / 60000;
        task.actualDuration += (int) durationMins;
        task.updatedAt = System.currentTimeMillis();

        if (listener != null) listener.onTimeLogged(task, startMs, endMs);

        String taskTitle = task.title != null && !task.title.isEmpty() ? task.title : "task";
        long h = durationMins / 60;
        long m = durationMins % 60;
        String dur = h > 0 ? h + "h " + m + "m" : m + "m";
        Toast.makeText(requireContext(), "Logged " + dur + " for " + taskTitle, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private String formatTime(Calendar cal) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.getTime());
    }
}
