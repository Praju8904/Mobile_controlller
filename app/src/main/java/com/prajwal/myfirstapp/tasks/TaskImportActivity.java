package com.prajwal.myfirstapp.tasks;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Import tasks from a CSV file.
 *
 * Expected CSV columns (header row required):
 *   title, description, priority, due_date, category, tags
 */
public class TaskImportActivity extends AppCompatActivity {

    private TaskRepository repo;
    private TextView tvPickFile, tvSummary;
    private Button btnConfirm;
    private RecyclerView recyclerView;
    private LinearLayout errorContainer;

    private final List<Task> validTasks = new ArrayList<>();
    private final List<String> errorRows = new ArrayList<>();

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) parseCsv(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new TaskRepository(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1A1A2E"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        // ── Toolbar ──────────────────────────────────────────────
        LinearLayout toolbar = buildRow();
        toolbar.setPadding(0, 0, 0, dp(16));

        TextView btnBack = new TextView(this);
        btnBack.setText("← Import Tasks");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(20);
        btnBack.setTypeface(null, Typeface.BOLD);
        btnBack.setOnClickListener(v -> finish());
        toolbar.addView(btnBack);
        root.addView(toolbar);

        // ── Pick file button ──────────────────────────────────────
        tvPickFile = buildCard("📂  Pick CSV File");
        tvPickFile.setGravity(Gravity.CENTER);
        tvPickFile.setTextColor(Color.parseColor("#6C63FF"));
        tvPickFile.setTextSize(16);
        tvPickFile.setTypeface(null, Typeface.BOLD);
        tvPickFile.setOnClickListener(v -> filePicker.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain"}));
        root.addView(tvPickFile);

        // ── Summary ───────────────────────────────────────────────
        tvSummary = new TextView(this);
        tvSummary.setTextColor(Color.parseColor("#9E9E9E"));
        tvSummary.setTextSize(13);
        tvSummary.setPadding(dp(4), dp(12), dp(4), dp(4));
        root.addView(tvSummary);

        // ── Preview RecyclerView ──────────────────────────────────
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rvLp.setMargins(0, dp(8), 0, dp(8));
        recyclerView.setLayoutParams(rvLp);
        recyclerView.setVisibility(View.GONE);
        root.addView(recyclerView);

        // ── Errors ────────────────────────────────────────────────
        errorContainer = new LinearLayout(this);
        errorContainer.setOrientation(LinearLayout.VERTICAL);
        errorContainer.setVisibility(View.GONE);
        root.addView(errorContainer);

        // ── Confirm button ────────────────────────────────────────
        btnConfirm = new Button(this);
        btnConfirm.setText("Confirm Import");
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setTextSize(15);
        btnConfirm.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#6C63FF"));
        btnBg.setCornerRadius(dp(12));
        btnConfirm.setBackground(btnBg);
        btnConfirm.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(8), 0, 0);
        btnConfirm.setLayoutParams(btnLp);
        btnConfirm.setVisibility(View.GONE);
        btnConfirm.setOnClickListener(v -> confirmImport());
        root.addView(btnConfirm);
    }

    // ── CSV Parsing ───────────────────────────────────────────────

    private void parseCsv(Uri uri) {
        validTasks.clear();
        errorRows.clear();

        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] headers = splitCsvLine(headerLine);
            int iTitle = indexOf(headers, "title");
            int iDesc = indexOf(headers, "description");
            int iPrio = indexOf(headers, "priority");
            int iDate = indexOf(headers, "due_date");
            int iCat = indexOf(headers, "category");
            int iTags = indexOf(headers, "tags");

            if (iTitle < 0) {
                Toast.makeText(this, "CSV must have a 'title' column", Toast.LENGTH_LONG).show();
                return;
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) continue;
                String[] cols = splitCsvLine(line);
                String title = get(cols, iTitle).trim();
                if (title.isEmpty()) {
                    errorRows.add("Row " + rowNum + ": missing title");
                    continue;
                }
                Task task = new Task();
                task.title = title;
                task.description = get(cols, iDesc);
                String prio = get(cols, iPrio).toLowerCase().trim();
                task.priority = parsePriority(prio);
                task.dueDate = parseDueDate(get(cols, iDate).trim());
                task.category = get(cols, iCat).trim();
                if (task.category.isEmpty()) task.category = "Personal";
                String tagsStr = get(cols, iTags).trim();
                if (!tagsStr.isEmpty()) {
                    for (String tag : tagsStr.split("[|;]")) {
                        String t = tag.trim();
                        if (!t.isEmpty()) task.tags.add(t);
                    }
                }
                validTasks.add(task);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        updateUI();
    }

    private void updateUI() {
        int valid = validTasks.size();
        int errors = errorRows.size();
        tvSummary.setText(valid + " task" + (valid != 1 ? "s" : "") + " will be created" +
                (errors > 0 ? "  •  " + errors + " row" + (errors != 1 ? "s" : "") + " had errors" : ""));

        recyclerView.setVisibility(valid > 0 ? View.VISIBLE : View.GONE);
        recyclerView.setAdapter(new PreviewAdapter());

        errorContainer.setVisibility(errors > 0 ? View.VISIBLE : View.GONE);
        errorContainer.removeAllViews();
        if (errors > 0) {
            TextView errorTitle = new TextView(this);
            errorTitle.setText("⚠ Errors (" + errors + ")");
            errorTitle.setTextColor(Color.parseColor("#EF4444"));
            errorTitle.setTextSize(13);
            errorTitle.setTypeface(null, Typeface.BOLD);
            errorTitle.setPadding(dp(4), dp(8), dp(4), dp(4));
            errorContainer.addView(errorTitle);
            for (String err : errorRows) {
                TextView tv = new TextView(this);
                tv.setText("• " + err);
                tv.setTextColor(Color.parseColor("#9E9E9E"));
                tv.setTextSize(12);
                tv.setPadding(dp(8), dp(2), dp(4), dp(2));
                errorContainer.addView(tv);
            }
        }

        btnConfirm.setVisibility(valid > 0 ? View.VISIBLE : View.GONE);
        btnConfirm.setText("Confirm Import (" + valid + " task" + (valid != 1 ? "s" : "") + ")");
    }

    private void confirmImport() {
        for (Task t : validTasks) repo.addTask(t);
        Toast.makeText(this, validTasks.size() + " tasks imported!", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String[] splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped quote ""
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++; // skip second quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private String get(String[] arr, int index) {
        if (index < 0 || index >= arr.length) return "";
        return arr[index].trim();
    }

    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String parsePriority(String raw) {
        switch (raw) {
            case "urgent": return Task.PRIORITY_URGENT;
            case "high":   return Task.PRIORITY_HIGH;
            case "low":    return Task.PRIORITY_LOW;
            case "none":   return Task.PRIORITY_NONE;
            default:       return Task.PRIORITY_NORMAL;
        }
    }

    private String parseDueDate(String raw) {
        if (raw.isEmpty()) return null;
        if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) return raw;
        return null;
    }

    // ── Preview Adapter ───────────────────────────────────────────

    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.VH> {

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(TaskImportActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#252545"));
            bg.setCornerRadius(dp(8));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(lp);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Task t = validTasks.get(position);
            holder.tvTitle.setText(t.title);
            holder.tvPriority.setText(Task.getPriorityLabelFor(t.priority));
            holder.tvPriority.setTextColor(Task.getPriorityColorFor(t.priority));
            holder.tvDate.setText(t.dueDate != null ? t.dueDate : "No date");
        }

        @Override
        public int getItemCount() { return validTasks.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvPriority, tvDate;
            VH(LinearLayout row) {
                super(row);
                tvTitle = new TextView(TaskImportActivity.this);
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(14);
                tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(tvTitle);

                tvPriority = new TextView(TaskImportActivity.this);
                tvPriority.setTextSize(11);
                tvPriority.setTypeface(null, Typeface.BOLD);
                tvPriority.setPadding(dp(8), 0, dp(8), 0);
                row.addView(tvPriority);

                tvDate = new TextView(TaskImportActivity.this);
                tvDate.setTextColor(Color.parseColor("#9E9E9E"));
                tvDate.setTextSize(11);
                row.addView(tvDate);
            }
        }
    }

    // ── View Builders ─────────────────────────────────────────────

    private TextView buildCard(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#252545"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#6C63FF"));
        tv.setBackground(bg);
        tv.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout buildRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
