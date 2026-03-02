package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Search activity for Hub files.
 */
public class HubSearchActivity extends AppCompatActivity {

    private static final String PREFS_SEARCH = "hub_search_history";
    private static final String KEY_HISTORY = "history_json";
    private static final int MAX_HISTORY = 20;

    private HubFileRepository repo;
    private EditText etSearchQuery;
    private View searchPreSearchLayout;
    private View searchResultsLayout;
    private View searchEmptyState;
    private android.widget.ProgressBar searchProgressBar;
    private LinearLayout recentSearchesContainer;
    private LinearLayout suggestionsContainer;
    private ListView searchResultsList;
    private TextView tvResultsCount;

    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    private List<HubFile> results = new ArrayList<>();
    private SearchResultAdapter adapter;
    /** IDs of files that matched by content (rather than name/tags). */
    private final java.util.Set<String> contentMatchSet = new java.util.HashSet<>();

    private final String[] SUGGESTIONS = {
            "PDFs this week", "Large videos", "WhatsApp files", "Favourites", "Recent images"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_search);

        repo = HubFileRepository.getInstance(this);

        etSearchQuery = findViewById(R.id.etSearchQuery);
        searchPreSearchLayout = findViewById(R.id.searchPreSearchLayout);
        searchResultsLayout = findViewById(R.id.searchResultsLayout);
        searchEmptyState = findViewById(R.id.searchEmptyState);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        recentSearchesContainer = findViewById(R.id.recentSearchesContainer);
        suggestionsContainer = findViewById(R.id.suggestionsContainer);
        searchResultsList = findViewById(R.id.searchResultsList);
        tvResultsCount = findViewById(R.id.tvResultsCount);

        adapter = new SearchResultAdapter(this, results, contentMatchSet);
        searchResultsList.setAdapter(adapter);
        searchResultsList.setOnItemClickListener((parent, view, position, id) ->
                openFile(results.get(position)));

        findViewById(R.id.btnSearchBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearHistory).setOnClickListener(v -> clearHistory());
        findViewById(R.id.btnSearchFilter).setOnClickListener(v -> showFilterDialog());

        loadPreSearch();
        setupSearchBar();

        // Auto-focus
        etSearchQuery.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT);
    }

    private void loadPreSearch() {
        loadRecentSearches();
        loadSuggestions();
    }

    private void loadRecentSearches() {
        recentSearchesContainer.removeAllViews();
        List<String> history = getHistory();
        float dp = getResources().getDisplayMetrics().density;

        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No recent searches");
            empty.setTextColor(Color.parseColor("#475569"));
            empty.setTextSize(13);
            recentSearchesContainer.addView(empty);
            return;
        }

        for (String query : history) {
            LinearLayout chip = buildChip(query, "#1E293B", "#94A3B8");
            chip.setOnClickListener(v -> {
                etSearchQuery.setText(query);
                etSearchQuery.setSelection(query.length());
                performSearch(query);
            });
            recentSearchesContainer.addView(chip);
        }
    }

    private void loadSuggestions() {
        suggestionsContainer.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        for (String suggestion : SUGGESTIONS) {
            LinearLayout chip = buildChip("✨ " + suggestion, "#2D1F5E", "#8B5CF6");
            chip.setOnClickListener(v -> {
                etSearchQuery.setText(suggestion);
                etSearchQuery.setSelection(suggestion.length());
                performSearch(suggestion);
            });
            suggestionsContainer.addView(chip);
        }
    }

    private LinearLayout buildChip(String text, String bgColor, String textColor) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout chip = new LinearLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20 * dp);
        try { bg.setColor(Color.parseColor(bgColor)); } catch (Exception e) { bg.setColor(Color.parseColor("#1E293B")); }
        chip.setBackground(bg);
        chip.setPadding((int)(12*dp), (int)(8*dp), (int)(12*dp), (int)(8*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(6*dp));
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setForeground(getDrawable(android.R.attr.selectableItemBackground));

        TextView tv = new TextView(this);
        tv.setText(text);
        try { tv.setTextColor(Color.parseColor(textColor)); } catch (Exception e) { tv.setTextColor(Color.WHITE); }
        tv.setTextSize(13);
        chip.addView(tv);
        return chip;
    }

    private void setupSearchBar() {
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                String query = s.toString().trim();
                if (query.length() < 2) {
                    showPreSearch();
                } else {
                    debounceRunnable = () -> performSearch(query);
                    debounceHandler.postDelayed(debounceRunnable, 300);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearchQuery.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                    saveToHistory(query);
                }
                return true;
            }
            return false;
        });
    }

    private void showPreSearch() {
        searchPreSearchLayout.setVisibility(View.VISIBLE);
        searchResultsLayout.setVisibility(View.GONE);
        searchEmptyState.setVisibility(View.GONE);
        searchProgressBar.setVisibility(View.GONE);
    }

    private void performSearch(String query) {
        searchProgressBar.setVisibility(View.VISIBLE);
        searchPreSearchLayout.setVisibility(View.GONE);
        searchEmptyState.setVisibility(View.GONE);

        // Use content-aware search that also searches inside files
        List<HubFileRepository.SearchResult> raw = repo.searchFilesWithContent(query);

        // Sort: name matches first, then content matches
        List<HubFileRepository.SearchResult> nameMatches = new ArrayList<>();
        List<HubFileRepository.SearchResult> contentMatches = new ArrayList<>();
        for (HubFileRepository.SearchResult r : raw) {
            if (r.contentMatch) contentMatches.add(r);
            else nameMatches.add(r);
        }

        results.clear();
        contentMatchSet.clear();
        for (HubFileRepository.SearchResult r : nameMatches) {
            results.add(r.file);
        }
        for (HubFileRepository.SearchResult r : contentMatches) {
            results.add(r.file);
            contentMatchSet.add(r.file.id);
        }

        searchProgressBar.setVisibility(View.GONE);

        if (results.isEmpty()) {
            searchResultsLayout.setVisibility(View.GONE);
            searchEmptyState.setVisibility(View.VISIBLE);
        } else {
            searchResultsLayout.setVisibility(View.VISIBLE);
            searchEmptyState.setVisibility(View.GONE);
            int contentCount = contentMatches.size();
            String countText = results.size() + " results for \"" + query + "\"";
            if (contentCount > 0) countText += " (" + contentCount + " content matches)";
            tvResultsCount.setText(countText);
            adapter.notifyDataSetChanged();
        }

        saveToHistory(query);
    }

    private void showFilterDialog() {
        HubFile.FileType[] types = HubFile.FileType.values();
        String[] names = new String[types.length];
        boolean[] checked = new boolean[types.length];
        for (int i = 0; i < types.length; i++) names[i] = types[i].name();

        new AlertDialog.Builder(this)
                .setTitle("Filter by Type")
                .setMultiChoiceItems(names, checked, null)
                .setPositiveButton("Apply", (dialog, which) ->
                        Toast.makeText(this, "Filter applied", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFile(HubFile file) {
        Intent intent = new Intent(this, HubFileViewerActivity.class);
        intent.putExtra(HubFileViewerActivity.EXTRA_FILE_ID, file.id);
        startActivity(intent);
    }

    private List<String> getHistory() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE);
            String json = prefs.getString(KEY_HISTORY, "[]");
            JSONArray arr = new JSONArray(json);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            return list;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private void saveToHistory(String query) {
        if (query == null || query.trim().isEmpty()) return;
        List<String> history = getHistory();
        history.remove(query);
        history.add(0, query);
        if (history.size() > MAX_HISTORY) history = history.subList(0, MAX_HISTORY);
        try {
            JSONArray arr = new JSONArray();
            for (String s : history) arr.put(s);
            getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE).edit()
                    .putString(KEY_HISTORY, arr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    private void clearHistory() {
        getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, "[]").apply();
        loadRecentSearches();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    static class SearchResultAdapter extends BaseAdapter {
        private final Context context;
        private final List<HubFile> files;
        private final java.util.Set<String> contentMatchSet;

        SearchResultAdapter(Context context, List<HubFile> files, java.util.Set<String> contentMatchSet) {
            this.context = context;
            this.files = files;
            this.contentMatchSet = contentMatchSet;
        }

        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int pos) { return files.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float dp = context.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int)(16*dp), (int)(12*dp), (int)(16*dp), (int)(12*dp));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(10 * dp);
            bg.setColor(Color.parseColor("#1A1A2E"));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int)(8*dp), 0, (int)(8*dp), (int)(4*dp));
            row.setLayoutParams(lp);

            HubFile f = files.get(position);

            TextView emoji = new TextView(context);
            emoji.setText(f.getTypeEmoji());
            emoji.setTextSize(22);
            emoji.setLayoutParams(new LinearLayout.LayoutParams((int)(36*dp), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(emoji);

            LinearLayout info = new LinearLayout(context);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(context);
            String display = f.displayName != null ? f.displayName : f.originalFileName;
            name.setText(display != null ? display : "File");
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(name);

            String typeStr = f.fileType != null ? f.fileType.name() : "FILE";
            TextView meta = new TextView(context);
            meta.setText(typeStr + " · " + f.getFormattedSize() + " · " +
                    new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(f.importedAt)));
            meta.setTextColor(Color.parseColor("#64748B"));
            meta.setTextSize(11);
            info.addView(meta);

            row.addView(info);

            // Type badge
            TextView badge = new TextView(context);
            badge.setText(typeStr);
            badge.setTextColor(Color.parseColor("#8B5CF6"));
            badge.setTextSize(10);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(4 * dp);
            badgeBg.setColor(Color.parseColor("#2D1F5E"));
            badge.setBackground(badgeBg);
            badge.setPadding((int)(6*dp), (int)(2*dp), (int)(6*dp), (int)(2*dp));
            badge.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(badge);

            // "Content Match" badge for files matched by content indexing
            if (contentMatchSet.contains(f.id)) {
                TextView contentBadge = new TextView(context);
                contentBadge.setText("📄 Content Match");
                contentBadge.setTextColor(Color.parseColor("#10B981"));
                contentBadge.setTextSize(10);
                GradientDrawable cbBg = new GradientDrawable();
                cbBg.setShape(GradientDrawable.RECTANGLE);
                cbBg.setCornerRadius(4 * dp);
                cbBg.setColor(Color.parseColor("#064E3B"));
                contentBadge.setBackground(cbBg);
                LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cbLp.setMargins((int)(4*dp), 0, 0, 0);
                contentBadge.setLayoutParams(cbLp);
                contentBadge.setPadding((int)(6*dp), (int)(2*dp), (int)(6*dp), (int)(2*dp));
                row.addView(contentBadge);
            }

            return row;
        }
    }
}
