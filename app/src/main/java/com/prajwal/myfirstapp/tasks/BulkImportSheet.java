package com.prajwal.myfirstapp.tasks;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet for bulk adding multiple tasks from plain text (one per line).
 * Gap ref: 16.3
 */
public class BulkImportSheet extends BottomSheetDialogFragment {

    public interface BulkImportListener {
        void onTasksCreated(List<Task> tasks);
    }

    private BulkImportListener importListener;
    private EditText etBulkInput;
    private Button btnCreate;
    private TextView tvPreview;

    public static BulkImportSheet newInstance() {
        return new BulkImportSheet();
    }

    public void setListener(BulkImportListener listener) {
        this.importListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        // Build layout programmatically
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1F35);
        bg.setCornerRadii(new float[]{32, 32, 32, 32, 0, 0, 0, 0});
        root.setBackground(bg);

        // Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Bulk Add Tasks");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        // Subtitle
        TextView tvSubtitle = new TextView(requireContext());
        tvSubtitle.setText("One task per line. Smart detection runs on each line.");
        tvSubtitle.setTextColor(0xFF94A3B8);
        tvSubtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = 8;
        tvSubtitle.setLayoutParams(subtitleLp);
        root.addView(tvSubtitle);

        // Input area
        etBulkInput = new EditText(requireContext());
        etBulkInput.setHint("Buy groceries tomorrow\nCall dentist Friday urgent\nReview PR #42 high");
        etBulkInput.setHintTextColor(0xFF64748B);
        etBulkInput.setTextColor(0xFFFFFFFF);
        etBulkInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        etBulkInput.setMinLines(6);
        etBulkInput.setGravity(android.view.Gravity.TOP);
        etBulkInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF0F172A);
        inputBg.setStroke(2, 0x33FFFFFF);
        inputBg.setCornerRadius(8f);
        etBulkInput.setBackground(inputBg);
        etBulkInput.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = 16;
        etBulkInput.setLayoutParams(inputLp);
        root.addView(etBulkInput);

        // Preview area
        tvPreview = new TextView(requireContext());
        tvPreview.setTextColor(0xFF94A3B8);
        tvPreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        tvPreview.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = 8;
        tvPreview.setLayoutParams(previewLp);
        root.addView(tvPreview);

        // Create button
        btnCreate = new Button(requireContext());
        btnCreate.setText("Create 0 Tasks");
        btnCreate.setTextColor(0xFFFFFFFF);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF10B981);
        btnBg.setCornerRadius(12f);
        btnCreate.setBackground(btnBg);
        btnCreate.setEnabled(false);
        btnCreate.setAlpha(0.5f);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * getResources().getDisplayMetrics().density));
        btnLp.topMargin = 24;
        btnCreate.setLayoutParams(btnLp);
        root.addView(btnCreate);

        // Wire up TextWatcher
        etBulkInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    btnCreate.setEnabled(false);
                    btnCreate.setAlpha(0.5f);
                    btnCreate.setText("Create 0 Tasks");
                    tvPreview.setVisibility(View.GONE);
                } else {
                    int count = countNonBlankLines(text);
                    btnCreate.setEnabled(true);
                    btnCreate.setAlpha(1f);
                    btnCreate.setText("Create " + count + " Tasks");
                    updatePreview(text);
                }
            }
        });

        btnCreate.setOnClickListener(v -> createTasks());

        return root;
    }

    private int countNonBlankLines(String text) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    private void updatePreview(String text) {
        StringBuilder sb = new StringBuilder("Preview:\n");
        int shown = 0;
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            Task t = new Task(line.trim(), Task.PRIORITY_NORMAL);
            SmartDetectionHelper.applyAll(t, line.trim());
            sb.append("• ").append(t.title);
            if (!Task.PRIORITY_NORMAL.equals(t.priority)) {
                sb.append(" [").append(t.priority.toUpperCase()).append("]");
            }
            if (t.dueDate != null) sb.append(" (").append(t.dueDate).append(")");
            sb.append("\n");
            if (++shown >= 5) {
                int remaining = countNonBlankLines(text) - 5;
                if (remaining > 0) sb.append("+ ").append(remaining).append(" more…");
                break;
            }
        }
        tvPreview.setText(sb.toString());
        tvPreview.setVisibility(View.VISIBLE);
    }

    private void createTasks() {
        String text = etBulkInput.getText().toString().trim();
        if (text.isEmpty()) return;
        List<Task> created = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            Task t = new Task(line.trim(), Task.PRIORITY_NORMAL);
            SmartDetectionHelper.applyAll(t, line.trim());
            created.add(t);
        }
        if (importListener != null) importListener.onTasksCreated(created);
        Toast.makeText(requireContext(), created.size() + " tasks created", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}
