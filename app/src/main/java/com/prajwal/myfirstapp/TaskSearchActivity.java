package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Advanced task search with filters, recent-search history, and debounced live results.
 */
public class TaskSearchActivity extends AppCompatActivity {

    private TaskRepository repo;

    private EditText etQuery;
    private View btnClear;
    private TextView chipPriority, chipCategory, chipStatus, chipDate;
    private LinearLayout recentSection, recentContainer;
    private LinearLayout resultsSection;
    private LinearLayout emptyState;
    private TextView tvResultCount;
    private RecyclerView recycler;
    private SearchResultAdapter adapter;

    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // Active filter values (null = any)
    private String filterPriority = null;
    private String filterCategory = null;
    private String filterStatus = null;
    private long filterDateFrom = -1;
    private long filterDateTo = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_search);

        repo = new TaskRepository(this);

        initViews();
        showRecentSearches();
        etQuery.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.reload();
        // Refresh current results if any
        String q = etQuery.getText().toString().trim();
        if (!q.isEmpty()) runSearch(q);
    }

    // ── Init ─────────────────────────────────────────────────────

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        etQuery = findViewById(R.id.etSearchQuery);
        btnClear = findViewById(R.id.btnClearSearch);
        chipPriority = findViewById(R.id.chipFilterPriority);
        chipCategory = findViewById(R.id.chipFilterCategory);
        chipStatus = findViewById(R.id.chipFilterStatus);
        chipDate = findViewById(R.id.chipFilterDate);
        recentSection = findViewById(R.id.recentSearchesSection);
        recentContainer = findViewById(R.id.recentSearchesContainer);
        resultsSection = findViewById(R.id.searchResultsSection);
        emptyState = findViewById(R.id.searchEmptyState);
        tvResultCount = findViewById(R.id.tvSearchResultCount);
        recycler = findViewById(R.id.recyclerSearchResults);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultAdapter();
        recycler.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            etQuery.setText("");
            showRecentSearches();
        });

        etQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (pendingSearch != null) debounce.removeCallbacks(pendingSearch);
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    showRecentSearches();
                    return;
                }
                pendingSearch = () -> runSearch(q);
                debounce.postDelayed(pendingSearch, 300);
            }
        });

        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = etQuery.getText().toString().trim();
                if (!q.isEmpty()) {
                    repo.addRecentSearch(q);
                    runSearch(q);
                }
                return true;
            }
            return false;
        });

        chipPriority.setOnClickListener(v -> showPriorityFilter());
        chipCategory.setOnClickListener(v -> showCategoryFilter());
        chipStatus.setOnClickListener(v -> showStatusFilter());
        chipDate.setOnClickListener(v -> showDateFilter());

        findViewById(R.id.btnClearRecent).setOnClickListener(v -> {
            repo.clearRecentSearches();
            showRecentSearches();
        });
    }

    // ── Recent Searches ──────────────────────────────────────────

    private void showRecentSearches() {
        recentSection.setVisibility(View.VISIBLE);
        resultsSection.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);

        recentContainer.removeAllViews();
        List<String> recent = repo.getRecentSearches();
        if (recent.isEmpty()) {
            recentSection.setVisibility(View.GONE);
            return;
        }

        for (String q : recent) {
            TextView tv = new TextView(this);
            tv.setText("🔍  " + q);
            tv.setTextColor(Color.parseColor("#94A3B8"));
            tv.setTextSize(14);
            tv.setPadding(0, dp(8), 0, dp(8));
            tv.setOnClickListener(v -> {
                etQuery.setText(q);
                etQuery.setSelection(q.length());
                runSearch(q);
            });
            recentContainer.addView(tv);
        }
    }

    // ── Search Execution ─────────────────────────────────────────

    private void runSearch(String query) {
        recentSection.setVisibility(View.GONE);

        // Map string filter status to Task constants
        String statusFilter = null;
        if (filterStatus != null) {
            switch (filterStatus) {
                case "To-Do": statusFilter = Task.STATUS_TODO; break;
                case "In Progress": statusFilter = Task.STATUS_INPROGRESS; break;
                case "Completed": statusFilter = Task.STATUS_COMPLETED; break;
                case "Cancelled": statusFilter = Task.STATUS_CANCELLED; break;
            }
        }

        // Date range strings (yyyy-MM-dd)
        String dateFrom = null, dateTo = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        if (filterDateFrom > 0) dateFrom = sdf.format(filterDateFrom);
        if (filterDateTo > 0) dateTo = sdf.format(filterDateTo);

        List<Task> results = repo.advancedSearch(
                query,
                filterPriority,
                filterCategory,
                statusFilter,
                dateFrom,
                dateTo
        );

        if (results.isEmpty()) {
            resultsSection.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            resultsSection.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            tvResultCount.setText(results.size() + " result" + (results.size() == 1 ? "" : "s"));
            adapter.setData(results, query);
        }
    }

    // ── Filter Dialogs ───────────────────────────────────────────

    private void showPriorityFilter() {
        String[] items = {"Any", "None", "Low", "Normal", "High", "Urgent"};
        String[] values = {null, Task.PRIORITY_NONE, Task.PRIORITY_LOW, Task.PRIORITY_NORMAL, Task.PRIORITY_HIGH, Task.PRIORITY_URGENT};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].equals(filterPriority)) { checked = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("Filter by Priority")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    filterPriority = values[which];
                    chipPriority.setText(which == 0 ? "Priority ▾" : items[which] + " ✕");
                    chipPriority.setTextColor(which == 0 ? Color.parseColor("#94A3B8") : Color.parseColor("#F59E0B"));
                    d.dismiss();
                    retriggerSearch();
                })
                .show();
    }

    private void showCategoryFilter() {
        List<String> cats = new ArrayList<>();
        cats.add("Any");
        cats.addAll(repo.getAllCategoryNames());
        String[] items = cats.toArray(new String[0]);
        int checked = 0;
        if (filterCategory != null) {
            for (int i = 1; i < items.length; i++) {
                if (items[i].equals(filterCategory)) { checked = i; break; }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Filter by Category")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    filterCategory = which == 0 ? null : items[which];
                    chipCategory.setText(which == 0 ? "Category ▾" : items[which] + " ✕");
                    chipCategory.setTextColor(which == 0 ? Color.parseColor("#94A3B8") : Color.parseColor("#F59E0B"));
                    d.dismiss();
                    retriggerSearch();
                })
                .show();
    }

    private void showStatusFilter() {
        String[] items = {"Any", "To-Do", "In Progress", "Completed", "Cancelled"};
        int checked = 0;
        if (filterStatus != null) {
            for (int i = 1; i < items.length; i++) {
                if (items[i].equals(filterStatus)) { checked = i; break; }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Filter by Status")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    filterStatus = which == 0 ? null : items[which];
                    chipStatus.setText(which == 0 ? "Status ▾" : items[which] + " ✕");
                    chipStatus.setTextColor(which == 0 ? Color.parseColor("#94A3B8") : Color.parseColor("#F59E0B"));
                    d.dismiss();
                    retriggerSearch();
                })
                .show();
    }

    private void showDateFilter() {
        // Show "from" date, then "to" date
        Calendar now = Calendar.getInstance();
        DatePickerDialog fromDlg = new DatePickerDialog(this, (vw, y, m, d) -> {
            Calendar from = Calendar.getInstance();
            from.set(y, m, d, 0, 0, 0);
            filterDateFrom = from.getTimeInMillis();

            // Now pick "to"
            DatePickerDialog toDlg = new DatePickerDialog(this, (vw2, y2, m2, d2) -> {
                Calendar to = Calendar.getInstance();
                to.set(y2, m2, d2, 23, 59, 59);
                filterDateTo = to.getTimeInMillis();

                SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.US);
                chipDate.setText(sdf.format(filterDateFrom) + " – " + sdf.format(filterDateTo) + " ✕");
                chipDate.setTextColor(Color.parseColor("#F59E0B"));
                retriggerSearch();
            }, y, m, now.get(Calendar.DAY_OF_MONTH));
            toDlg.setTitle("Due date to");
            toDlg.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        fromDlg.setTitle("Due date from");

        // Long-press to reset
        chipDate.setOnLongClickListener(v -> {
            filterDateFrom = -1;
            filterDateTo = -1;
            chipDate.setText("Date Range ▾");
            chipDate.setTextColor(Color.parseColor("#94A3B8"));
            retriggerSearch();
            return true;
        });
        fromDlg.show();
    }

    private void retriggerSearch() {
        String q = etQuery.getText().toString().trim();
        if (!q.isEmpty()) runSearch(q);
    }

    // ── Result Adapter ───────────────────────────────────────────

    private class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.VH> {
        private List<Task> items = new ArrayList<>();
        private String highlight = "";

        void setData(List<Task> data, String query) {
            items = data;
            highlight = query;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Task t = items.get(pos);
            h.bind(t);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            View dot;
            TextView tvTitle, tvDate, tvPreview, tvCategory, tvMatch;

            VH(View v) {
                super(v);
                dot = v.findViewById(R.id.viewSearchPriorityDot);
                tvTitle = v.findViewById(R.id.tvSearchResultTitle);
                tvDate = v.findViewById(R.id.tvSearchResultDate);
                tvPreview = v.findViewById(R.id.tvSearchResultPreview);
                tvCategory = v.findViewById(R.id.tvSearchResultCategory);
                tvMatch = v.findViewById(R.id.tvSearchResultMatchType);
            }

            void bind(Task t) {
                tvTitle.setText(t.title);

                // Priority dot
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(t.getPriorityColor());
                dot.setBackground(dotBg);

                // Due date
                if (t.hasDueDate()) {
                    tvDate.setVisibility(View.VISIBLE);
                    tvDate.setText(t.getFormattedDueDate());
                    tvDate.setTextColor(t.isOverdue()
                            ? Color.parseColor("#EF5350")
                            : Color.parseColor("#6B7280"));
                } else {
                    tvDate.setVisibility(View.GONE);
                }

                // Preview (description or subtask snippet)
                String preview = "";
                if (!TextUtils.isEmpty(t.description)) {
                    preview = t.description;
                } else if (!TextUtils.isEmpty(t.notes)) {
                    preview = t.notes;
                } else if (t.hasSubtasks()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(3, t.subtasks.size()); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(t.subtasks.get(i).title);
                    }
                    preview = sb.toString();
                }
                if (!TextUtils.isEmpty(preview)) {
                    tvPreview.setVisibility(View.VISIBLE);
                    tvPreview.setText(preview);
                } else {
                    tvPreview.setVisibility(View.GONE);
                }

                // Category chip
                if (!TextUtils.isEmpty(t.category)) {
                    tvCategory.setVisibility(View.VISIBLE);
                    tvCategory.setText(t.category);
                } else {
                    tvCategory.setVisibility(View.GONE);
                }

                // Match type
                String lq = highlight.toLowerCase(Locale.US);
                if (t.title.toLowerCase(Locale.US).contains(lq)) {
                    tvMatch.setText("title match");
                } else if (t.description != null && t.description.toLowerCase(Locale.US).contains(lq)) {
                    tvMatch.setText("description match");
                } else if (t.tags != null) {
                    boolean tagMatch = false;
                    for (String tag : t.tags) {
                        if (tag.toLowerCase(Locale.US).contains(lq)) { tagMatch = true; break; }
                    }
                    tvMatch.setText(tagMatch ? "tag match" : "filter match");
                } else {
                    tvMatch.setText("filter match");
                }

                // Click → open detail
                itemView.setOnClickListener(v -> {
                    // Save search query
                    repo.addRecentSearch(highlight);
                    Intent intent = new Intent(TaskSearchActivity.this, TaskDetailActivity.class);
                    intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, t.id);
                    startActivity(intent);
                });
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
