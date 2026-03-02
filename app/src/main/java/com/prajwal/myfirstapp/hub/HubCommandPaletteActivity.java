package com.prajwal.myfirstapp.hub;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Command Palette — power-user overlay accessible by long-pressing the search bar
 * or from a keyboard shortcut.
 *
 * As the user types, shows matching commands with their actions.
 * Tapping any command executes it instantly.
 *
 * Commands include navigation, search shortcuts, and file hub actions.
 */
public class HubCommandPaletteActivity extends AppCompatActivity {

    static class Command {
        final String icon;
        final String label;
        final String keywords; // space-separated search keywords (lowercase)
        final Runnable action;

        Command(String icon, String label, String keywords, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.keywords = keywords;
            this.action = action;
        }
    }

    private final List<Command> allCommands = new ArrayList<>();
    private LinearLayout resultsContainer;
    private EditText etQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildCommands();
        buildUI();
        // Show keyboard immediately
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void buildCommands() {
        allCommands.add(new Command("🔍", "Search files", "search find files",
                () -> startActivity(new Intent(this, HubSearchActivity.class))));
        allCommands.add(new Command("📥", "Open Inbox", "inbox pending review",
                () -> startActivity(new Intent(this, HubInboxActivity.class))));
        allCommands.add(new Command("📁", "Browse all files", "browse all files",
                () -> startActivity(new Intent(this, HubFileBrowserActivity.class))));
        allCommands.add(new Command("📊", "Open Analytics", "analytics dashboard stats charts",
                () -> startActivity(new Intent(this, HubAnalyticsActivity.class))));
        allCommands.add(new Command("📋", "View Projects", "projects",
                () -> startActivity(new Intent(this, HubProjectsActivity.class))));
        allCommands.add(new Command("🗃️", "Find Duplicates", "duplicates duplicate scan",
                () -> startActivity(new Intent(this, HubDuplicateManagerActivity.class))));
        allCommands.add(new Command("🧠", "Storage Intelligence", "storage health score clean",
                () -> startActivity(new Intent(this, HubStorageIntelligenceActivity.class))));
        allCommands.add(new Command("⚡", "Quick Share", "share quick pin",
                () -> startActivity(new Intent(this, HubQuickShareActivity.class))));
        allCommands.add(new Command("📈", "File Timeline", "timeline history chronological",
                () -> startActivity(new Intent(this, HubFileTimelineActivity.class))));
        allCommands.add(new Command("📦", "Collections", "collections playlist group",
                () -> startActivity(new Intent(this, HubCollectionsActivity.class))));
        allCommands.add(new Command("⚙️", "Batch Operations", "batch bulk operations rename move",
                () -> startActivity(new Intent(this, HubBatchOperationsActivity.class))));
        allCommands.add(new Command("👁️", "Watchlist", "watchlist watch monitor",
                () -> startActivity(new Intent(this, HubWatchlistActivity.class))));
        allCommands.add(new Command("🔨", "Smart Folder Builder", "smart folder builder rules",
                () -> startActivity(new Intent(this, HubSmartFolderBuilderActivity.class))));
        allCommands.add(new Command("⚙️", "Hub Settings", "settings preferences configure",
                () -> startActivity(new Intent(this, HubSettingsActivity.class))));
        allCommands.add(new Command("🏠", "Hub Home", "home main hub",
                () -> { finish(); })); // just close palette to return to hub
        allCommands.add(new Command("📕", "Show PDFs", "pdfs pdf documents",
                () -> { Intent i = new Intent(this, HubFileBrowserActivity.class);
                    i.putExtra("filterType", "PDF"); startActivity(i); }));
        allCommands.add(new Command("🖼️", "Show Images", "images photos pictures",
                () -> { Intent i = new Intent(this, HubFileBrowserActivity.class);
                    i.putExtra("filterType", "IMAGE"); startActivity(i); }));
        allCommands.add(new Command("💬", "Show WhatsApp files", "whatsapp files media",
                () -> { Intent i = new Intent(this, HubFileBrowserActivity.class);
                    i.putExtra("filterSource", "WHATSAPP"); startActivity(i); }));
        allCommands.add(new Command("📸", "Show Screenshots", "screenshots screen capture",
                () -> { Intent i = new Intent(this, HubFileBrowserActivity.class);
                    i.putExtra("filterType", "SCREENSHOT"); startActivity(i); }));
        allCommands.add(new Command("⭐", "Show Favourites", "favourites starred pinned",
                () -> { Intent i = new Intent(this, HubFileBrowserActivity.class);
                    i.putExtra("filterFavourites", true); startActivity(i); }));
        allCommands.add(new Command("🆕", "New Project", "new project create",
                () -> startActivity(new Intent(this, HubProjectsActivity.class))));
        allCommands.add(new Command("🔄", "Scan for new files", "scan index new files",
                () -> {
                    HubFileRepository.getInstance(this).scanForNewFiles(null);
                    showToast("Scanning for new files…");
                    finish();
                }));
    }

    private void buildUI() {
        // Full-screen semi-transparent overlay
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#E5111827")); // semi-transparent dark
        root.setPadding(24, 60, 24, 24);
        setContentView(root);

        // Search bar
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(Color.parseColor("#1E293B"));
        etBg.setCornerRadius(20f);
        etBg.setStroke(2, Color.parseColor("#3B82F6"));

        etQuery = new EditText(this);
        etQuery.setHint("⌘ Type a command…");
        etQuery.setHintTextColor(Color.parseColor("#6B7280"));
        etQuery.setTextColor(Color.WHITE);
        etQuery.setTextSize(17);
        etQuery.setPadding(32, 20, 32, 20);
        etQuery.setBackground(etBg);
        etQuery.setSingleLine(true);
        root.addView(etQuery);
        root.addView(vspace(12));

        // Results scroll
        ScrollView scroll = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(resultsContainer);
        root.addView(scroll);

        // Close on background tap
        root.setOnClickListener(v -> finish());

        // Live filter
        etQuery.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) { filterCommands(s.toString()); }
        });

        // Auto-focus
        etQuery.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etQuery, InputMethodManager.SHOW_IMPLICIT);

        filterCommands("");
    }

    private void filterCommands(String query) {
        resultsContainer.removeAllViews();
        String q = query.trim().toLowerCase();
        int count = 0;
        for (Command cmd : allCommands) {
            if (q.isEmpty() || cmd.label.toLowerCase().contains(q)
                    || cmd.keywords.contains(q)) {
                resultsContainer.addView(makeCommandRow(cmd));
                if (++count >= 10) break;
            }
        }
        if (count == 0) {
            TextView tv = new TextView(this);
            tv.setText("No commands match \"" + query + "\"");
            tv.setTextColor(Color.parseColor("#6B7280"));
            tv.setTextSize(14);
            tv.setPadding(16, 16, 16, 16);
            resultsContainer.addView(tv);
        }
    }

    private View makeCommandRow(Command cmd) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(20, 18, 20, 18);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(14f);
        row.setBackground(bg);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(cmd.icon);
        tvIcon.setTextSize(22);
        row.addView(tvIcon);
        row.addView(hspace(16));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(cmd.label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(15);
        row.addView(tvLabel);

        row.setOnClickListener(v -> {
            finish(); // close palette first
            cmd.action.run();
        });

        // Ripple-like feedback
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        row.setLayoutParams(lp);

        return row;
    }

    private View vspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp));
        return v;
    }

    private View hspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp,
                LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
