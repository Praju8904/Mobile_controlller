package com.prajwal.myfirstapp.vault;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Manage vault collections — lightweight groupings where a file can belong to
 * multiple collections simultaneously (unlike albums).
 *
 * <p>Features:
 * <ul>
 *   <li>Lists all collections with a colour indicator, name and file count.</li>
 *   <li>Tap a collection to see its files in a dialog.</li>
 *   <li>Long-press a collection to delete it.</li>
 *   <li>"New" button opens a creation dialog (name + colour picker).</li>
 * </ul>
 */
public class VaultCollectionActivity extends Activity {

    // ── Preset colours offered during collection creation ────────────────────
    private static final String[] PRESET_COLORS = {
            "#6C63FF", "#EF4444", "#10B981", "#F59E0B",
            "#3B82F6", "#EC4899", "#8B5CF6", "#14B8A6"
    };
    private static final String[] PRESET_NAMES = {
            "Violet", "Red", "Green", "Amber",
            "Blue", "Pink", "Purple", "Teal"
    };

    // ── Dark-vault colour palette ────────────────────────────────────────────
    private static final int CLR_BG       = 0xFF0A0E21;
    private static final int CLR_CARD     = 0xFF1E293B;
    private static final int CLR_ACCENT   = 0xFF6C63FF;
    private static final int CLR_TEXT_PRI = 0xFFF1F5F9;
    private static final int CLR_TEXT_SEC = 0xFF94A3B8;
    private static final int CLR_DELETE   = 0xFFEF4444;

    // ── State ────────────────────────────────────────────────────────────────
    private MediaVaultRepository repo;
    private LinearLayout listContainer;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        setContentView(buildRootLayout());
        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private LinearLayout buildRootLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CLR_BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(buildTopBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setBackgroundColor(CLR_BG);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(12), dp(8), dp(12), dp(16));
        scroll.addView(listContainer);

        root.addView(scroll);
        return root;
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(CLR_CARD);
        bar.setPadding(dp(12), dp(14), dp(12), dp(14));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(CLR_ACCENT);
        btnBack.setTextSize(20);
        btnBack.setPadding(0, 0, dp(16), 0);
        btnBack.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("Collections");
        title.setTextColor(CLR_TEXT_PRI);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnNew = new TextView(this);
        btnNew.setText("＋ New");
        btnNew.setTextColor(CLR_ACCENT);
        btnNew.setTextSize(14);
        btnNew.setTypeface(null, Typeface.BOLD);
        btnNew.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnNew.setOnClickListener(v -> showCreateDialog());

        bar.addView(btnBack);
        bar.addView(title);
        bar.addView(btnNew);
        return bar;
    }

    // ── List rendering ───────────────────────────────────────────────────────

    private void refreshList() {
        listContainer.removeAllViews();

        List<VaultCollection> collections = repo.getCollections();

        if (collections.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No collections yet.\nTap  ＋ New  to create one.");
            empty.setTextColor(CLR_TEXT_SEC);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(48), dp(16), dp(48));
            listContainer.addView(empty);
            return;
        }

        for (VaultCollection col : collections) {
            listContainer.addView(buildCollectionRow(col));
        }
    }

    private LinearLayout buildCollectionRow(VaultCollection col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(CLR_CARD);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(3));
        row.setLayoutParams(rowLp);

        // Coloured square indicator
        TextView colorSquare = new TextView(this);
        int squareSize = dp(28);
        LinearLayout.LayoutParams sqLp = new LinearLayout.LayoutParams(squareSize, squareSize);
        sqLp.setMarginEnd(dp(14));
        colorSquare.setLayoutParams(sqLp);
        GradientDrawable squareBg = new GradientDrawable();
        squareBg.setShape(GradientDrawable.RECTANGLE);
        squareBg.setCornerRadius(dp(4));
        try {
            squareBg.setColor(Color.parseColor(
                    col.colorHex != null && !col.colorHex.isEmpty() ? col.colorHex : "#607D8B"));
        } catch (IllegalArgumentException e) {
            squareBg.setColor(CLR_ACCENT);
        }
        colorSquare.setBackground(squareBg);

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(col.name != null ? col.name : "(unnamed)");
        tvName.setTextColor(CLR_TEXT_PRI);
        tvName.setTextSize(15);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setMaxLines(1);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // File count
        int fileCount = repo.getFilesInCollection(col.id).size();
        TextView tvCount = new TextView(this);
        tvCount.setText(fileCount + " file" + (fileCount == 1 ? "" : "s"));
        tvCount.setTextColor(CLR_TEXT_SEC);
        tvCount.setTextSize(13);

        row.addView(colorSquare);
        row.addView(tvName);
        row.addView(tvCount);

        // Tap → view files
        row.setOnClickListener(v -> showFilesDialog(col));

        // Long-press → delete
        row.setOnLongClickListener(v -> {
            confirmDelete(col);
            return true;
        });

        return row;
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private void showCreateDialog() {
        final String[] selectedColor = {PRESET_COLORS[0]};

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView labelName = new TextView(this);
        labelName.setText("Collection Name");
        labelName.setTextColor(CLR_TEXT_SEC);
        labelName.setTextSize(12);
        labelName.setPadding(0, 0, 0, dp(4));
        dialogLayout.addView(labelName);

        EditText etName = new EditText(this);
        etName.setHint("e.g. Travel 2024");
        etName.setTextColor(CLR_TEXT_PRI);
        etName.setHintTextColor(CLR_TEXT_SEC);
        etName.setTextSize(15);
        etName.setSingleLine(true);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, 0, 0, dp(16));
        etName.setLayoutParams(etLp);
        dialogLayout.addView(etName);

        TextView labelColor = new TextView(this);
        labelColor.setText("Colour");
        labelColor.setTextColor(CLR_TEXT_SEC);
        labelColor.setTextSize(12);
        labelColor.setPadding(0, 0, 0, dp(8));
        dialogLayout.addView(labelColor);

        // Colour swatch grid (2 rows × 4 cols)

        final LinearLayout[] swatchRows = {null, null};
        swatchRows[0] = buildSwatchRow(0, 4, selectedColor, swatchRows);
        swatchRows[1] = buildSwatchRow(4, 8, selectedColor, swatchRows);
        dialogLayout.addView(swatchRows[0]);
        dialogLayout.addView(swatchRows[1]);

        new AlertDialog.Builder(this)
                .setTitle("New Collection")
                .setView(dialogLayout)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.createCollection(name, selectedColor[0]);
                    refreshList();
                    Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Builds a horizontal row of colour swatches for indices [from, to).
     *
     * @param swatchRowsRef two-element array holding both swatch rows so clicks can
     *                      refresh highlights across both rows; may be null during
     *                      the pre-construction phase (swatches won't refresh highlights).
     */
    private LinearLayout buildSwatchRow(int from, int to, String[] selectedColor,
                                        LinearLayout[] swatchRowsRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rowLp);

        for (int i = from; i < to && i < PRESET_COLORS.length; i++) {
            final String hex = PRESET_COLORS[i];
            final String colorName = PRESET_NAMES[i];

            TextView swatch = new TextView(this);
            swatch.setContentDescription(colorName);
            int swatchSize = dp(36);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            swLp.setMarginEnd(dp(8));
            swatch.setLayoutParams(swLp);
            swatch.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            try { bg.setColor(Color.parseColor(hex)); } catch (Exception ignored) {}
            swatch.setBackground(bg);

            if (hex.equals(selectedColor[0])) swatch.setText("✓");

            swatch.setOnClickListener(v -> {
                selectedColor[0] = hex;
                // Refresh highlights in both rows if we have the refs
                if (swatchRowsRef != null && swatchRowsRef[0] != null) {
                    refreshSwatchHighlights(swatchRowsRef[0], selectedColor[0]);
                    refreshSwatchHighlights(swatchRowsRef[1], selectedColor[0]);
                }
            });

            row.addView(swatch);
        }
        return row;
    }

    private void refreshSwatchHighlights(LinearLayout row, String selectedHex) {
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof TextView) {
                TextView sw = (TextView) row.getChildAt(i);
                // use contentDescription to look up the swatch's preset colour
                int colorIndex = indexOfColorName(sw.getContentDescription() != null
                        ? sw.getContentDescription().toString() : "");
                if (colorIndex >= 0) {
                    sw.setText(PRESET_COLORS[colorIndex].equals(selectedHex) ? "✓" : "");
                }
            }
        }
    }

    private int indexOfColorName(String name) {
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            if (PRESET_NAMES[i].equals(name)) return i;
        }
        return -1;
    }

    private void showFilesDialog(VaultCollection col) {
        List<VaultFileItem> files = repo.getFilesInCollection(col.id);

        if (files.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(col.name)
                    .setMessage("This collection has no files yet.\n\n" +
                            "Open a file in the vault and use 'Add to Collection' to add files here.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(20), dp(8), dp(20), dp(8));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        for (VaultFileItem f : files) {
            TextView tv = new TextView(this);
            String name = (f.originalFileName != null && !f.originalFileName.isEmpty())
                    ? f.originalFileName : f.vaultFileName;
            String typeLabel = f.fileType != null ? f.fileType.name().toLowerCase() : "other";
            tv.setText("• " + name + "  (" + typeLabel + ")");
            tv.setTextColor(CLR_TEXT_PRI);
            tv.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(6));
            tv.setLayoutParams(lp);
            layout.addView(tv);
        }

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("\"" + col.name + "\"  (" + files.size() + " file" + (files.size() == 1 ? "" : "s") + ")")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDelete(VaultCollection col) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Collection")
                .setMessage("Delete \"" + col.name + "\"?\n\n" +
                        "Files in this collection will NOT be deleted — " +
                        "only the collection grouping is removed.")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteCollection(col.id);
                    refreshList();
                    Toast.makeText(this,
                            "\"" + col.name + "\" deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
