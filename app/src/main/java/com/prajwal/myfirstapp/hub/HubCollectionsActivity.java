package com.prajwal.myfirstapp.hub;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Collections screen — playlist-style manual file groupings.
 *
 * Unlike smart folders (auto-matching rules) and projects (full metadata),
 * collections are lightweight, manually curated groups.
 *
 * Features:
 *   • Create a new collection (name + colour)
 *   • View all collections as cards with thumbnail strip
 *   • Tap a collection to see its files
 *   • Long-press to delete a collection (files stay, just ungrouped)
 *   • Files added to collections from their detail screen or here
 */
public class HubCollectionsActivity extends AppCompatActivity {

    private static final String[] COLLECTION_COLORS = {
            "#8B5CF6", "#3B82F6", "#10B981", "#F59E0B",
            "#EF4444", "#EC4899", "#06B6D4"
    };

    private HubFileRepository repo;
    private LinearLayout collectionsContainer;
    private int selectedColorIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (collectionsContainer != null) {
            collectionsContainer.removeAllViews();
            populateCollections();
        }
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 48, 24, 48);
        scroll.addView(root);
        setContentView(scroll);

        // Header
        LinearLayout header = makeRow();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = makeButton("←", "#374151", "#E5E7EB");
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);
        header.addView(hspace(16));
        header.addView(makeTitle("Collections"));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        header.addView(spacer);

        Button btnNew = makeButton("＋ New", "#8B5CF6", "#FFFFFF");
        btnNew.setOnClickListener(v -> showCreateDialog());
        header.addView(btnNew);
        root.addView(header);
        root.addView(vspace(8));

        TextView sub = makeSub("Lightweight file groupings — like playlists for your files.");
        root.addView(sub);
        root.addView(vspace(24));

        collectionsContainer = new LinearLayout(this);
        collectionsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(collectionsContainer);

        populateCollections();
    }

    private void populateCollections() {
        List<HubCollection> collections = repo.getAllCollections();
        if (collections.isEmpty()) {
            TextView tv = makeSub("No collections yet.\nTap ＋ New to create one.");
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tv.setPadding(0, 48, 0, 0);
            collectionsContainer.addView(tv);
            return;
        }
        for (HubCollection c : collections) {
            collectionsContainer.addView(makeCollectionCard(c));
            collectionsContainer.addView(vspace(12));
        }
    }

    private View makeCollectionCard(HubCollection collection) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(20f);
        // Color accent on left
        bg.setStroke(3, tryParseColor(collection.colorHex, Color.parseColor("#8B5CF6")));
        card.setBackground(bg);

        // Title row
        LinearLayout titleRow = makeRow();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(16, 16));
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(tryParseColor(collection.colorHex, Color.parseColor("#8B5CF6")));
        dot.setBackground(dotBg);
        titleRow.addView(dot);
        titleRow.addView(hspace(12));

        TextView tvName = new TextView(this);
        tvName.setText(collection.name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvName);

        TextView tvCount = makeSub(collection.getFileCount() + " files");
        titleRow.addView(tvCount);
        card.addView(titleRow);
        card.addView(vspace(12));

        // File thumbnail strip
        List<HubFile> files = repo.getFilesForCollection(collection.id);
        if (!files.isEmpty()) {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = makeRow();
            strip.setPadding(0, 0, 0, 0);
            for (int i = 0; i < Math.min(8, files.size()); i++) {
                HubFile f = files.get(i);
                TextView thumb = new TextView(this);
                thumb.setText(f.getTypeEmoji());
                thumb.setTextSize(22);
                thumb.setPadding(8, 8, 8, 8);
                GradientDrawable thumbBg = new GradientDrawable();
                thumbBg.setColor(Color.parseColor("#111827"));
                thumbBg.setCornerRadius(8f);
                thumb.setBackground(thumbBg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 6, 0);
                thumb.setLayoutParams(lp);
                strip.addView(thumb);
            }
            if (files.size() > 8) {
                TextView more = makeSub("+" + (files.size() - 8));
                more.setPadding(8, 8, 8, 8);
                strip.addView(more);
            }
            hsv.addView(strip);
            card.addView(hsv);
        }

        // Tap to open
        card.setOnClickListener(v -> openCollection(collection));

        // Long-press to delete
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Collection")
                    .setMessage("Delete \"" + collection.name + "\"? Files will not be deleted.")
                    .setPositiveButton("Delete", (d, w) -> {
                        repo.deleteCollection(collection.id);
                        collectionsContainer.removeAllViews();
                        populateCollections();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        return card;
    }

    private void openCollection(HubCollection collection) {
        // Open file browser filtered to this collection
        Intent i = new Intent(this, HubFileBrowserActivity.class);
        i.putExtra("collectionId", collection.id);
        i.putExtra("collectionName", collection.name);
        startActivity(i);
    }

    private void showCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Collection");

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(32, 16, 32, 16);

        EditText etName = new EditText(this);
        etName.setHint("Collection name (e.g. This Week, Thesis)");
        dialogContent.addView(etName);
        dialogContent.addView(vspace(12));

        // Colour picker row
        TextView tvColorLabel = makeSub("Pick a colour:");
        dialogContent.addView(tvColorLabel);
        dialogContent.addView(vspace(8));

        LinearLayout colorRow = makeRow();
        Button[] colorBtns = new Button[COLLECTION_COLORS.length];
        for (int i = 0; i < COLLECTION_COLORS.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setText(" ");
            btn.setMinWidth(48);
            btn.setMinHeight(48);
            GradientDrawable bgD = new GradientDrawable();
            bgD.setShape(GradientDrawable.OVAL);
            bgD.setColor(Color.parseColor(COLLECTION_COLORS[i]));
            btn.setBackground(bgD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(60, 60);
            lp.setMargins(4, 0, 4, 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                selectedColorIndex = idx;
                for (Button b : colorBtns) {
                    GradientDrawable d = new GradientDrawable();
                    d.setShape(GradientDrawable.OVAL);
                    d.setColor(Color.parseColor(COLLECTION_COLORS[
                            java.util.Arrays.asList(colorBtns).indexOf(b)]));
                    b.setBackground(d);
                }
                GradientDrawable selBg = new GradientDrawable();
                selBg.setShape(GradientDrawable.OVAL);
                selBg.setColor(Color.parseColor(COLLECTION_COLORS[idx]));
                selBg.setStroke(4, Color.WHITE);
                btn.setBackground(selBg);
            });
            colorBtns[i] = btn;
            colorRow.addView(btn);
        }
        dialogContent.addView(colorRow);
        builder.setView(dialogContent);

        builder.setPositiveButton("Create", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { showToast("Enter a name"); return; }
            HubCollection col = new HubCollection(name, COLLECTION_COLORS[selectedColorIndex]);
            repo.addCollection(col);
            collectionsContainer.removeAllViews();
            populateCollections();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private int tryParseColor(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private TextView makeTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(20);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView makeSub(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setTextSize(13);
        return tv;
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(fg));
        btn.setTextSize(13);
        btn.setPadding(20, 10, 20, 10);
        GradientDrawable bgD = new GradientDrawable();
        bgD.setColor(Color.parseColor(bg));
        bgD.setCornerRadius(16f);
        btn.setBackground(bgD);
        return btn;
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
