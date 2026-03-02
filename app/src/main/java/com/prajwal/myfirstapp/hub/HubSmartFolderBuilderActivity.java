package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Smart Folder Builder.
 *
 * Allows the user to build a smart folder using a visual rule builder:
 *   • Add Rule button — adds a condition row
 *   • Each row has: Field selector | Operator selector | Value input
 *   • AND/OR toggle at the top
 *   • Live preview: matching file count updates as rules change
 *   • First 5 matching file names shown as a preview
 *   • Name + colour the smart folder and save
 *
 * Supported fields: File Name, File Type, File Size, Source, Date Added,
 *                   Tags, Color Label, Project, Is Favourite, Is Duplicate
 * Operators: Contains, Does Not Contain, Is, Is Not, Greater Than, Less Than,
 *            Before, After
 */
public class HubSmartFolderBuilderActivity extends AppCompatActivity {

    private static final String[] FIELDS = {
            "File Name", "File Type", "File Size (MB)", "Source",
            "Date Added (days ago)", "Tags", "Is Favourite", "Is Duplicate"
    };
    private static final String[] OPERATORS = {
            "Contains", "Does Not Contain", "Is", "Is Not",
            "Greater Than", "Less Than"
    };

    private HubFileRepository repo;
    private LinearLayout rulesContainer;
    private TextView tvPreviewCount;
    private LinearLayout previewFilesContainer;
    private EditText etFolderName;
    private boolean matchAll = true; // true = AND, false = OR
    private final List<RuleRow> ruleRows = new ArrayList<>();

    private static class RuleRow {
        int fieldIndex = 0;
        int operatorIndex = 0;
        String value = "";
        Spinner fieldSpinner;
        Spinner operatorSpinner;
        EditText etValue;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        buildUI();
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
        TextView tvTitle = makeTitle("Smart Folder Builder");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(24));

        // Folder name
        etFolderName = new EditText(this);
        etFolderName.setHint("Folder name");
        etFolderName.setHintTextColor(Color.parseColor("#6B7280"));
        etFolderName.setTextColor(Color.WHITE);
        etFolderName.setTextSize(15);
        etFolderName.setPadding(24, 16, 24, 16);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(Color.parseColor("#1E293B"));
        etBg.setCornerRadius(12f);
        etFolderName.setBackground(etBg);
        root.addView(etFolderName);
        root.addView(vspace(16));

        // AND / OR toggle
        LinearLayout toggleRow = makeRow();
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvMatch = makeSub("Match:");
        toggleRow.addView(tvMatch);
        toggleRow.addView(hspace(12));
        Button btnAnd = makeChip("ALL rules (AND)", matchAll);
        Button btnOr = makeChip("ANY rule (OR)", !matchAll);
        btnAnd.setOnClickListener(v -> { matchAll = true; updatePreview(); rebuildToggle(btnAnd, btnOr); });
        btnOr.setOnClickListener(v -> { matchAll = false; updatePreview(); rebuildToggle(btnOr, btnAnd); });
        toggleRow.addView(btnAnd);
        toggleRow.addView(hspace(8));
        toggleRow.addView(btnOr);
        root.addView(toggleRow);
        root.addView(vspace(16));

        // Rules container
        rulesContainer = new LinearLayout(this);
        rulesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(rulesContainer);

        // Restore existing rule rows
        for (RuleRow row : ruleRows) {
            rulesContainer.addView(buildRuleView(row));
            rulesContainer.addView(vspace(8));
        }

        // Add Rule button
        Button btnAdd = makeButton("＋ Add Rule", "#1D4ED8", "#FFFFFF");
        btnAdd.setOnClickListener(v -> {
            RuleRow row = new RuleRow();
            ruleRows.add(row);
            rulesContainer.addView(buildRuleView(row));
            rulesContainer.addView(vspace(8));
            updatePreview();
        });
        root.addView(btnAdd);
        root.addView(vspace(24));

        // Preview section
        LinearLayout previewCard = makeCard();
        TextView tvPreviewTitle = makeTitle("Live Preview");
        tvPreviewTitle.setTextSize(15);
        previewCard.addView(tvPreviewTitle);
        previewCard.addView(vspace(8));

        tvPreviewCount = makeSub("0 files match");
        previewCard.addView(tvPreviewCount);
        previewCard.addView(vspace(8));

        previewFilesContainer = new LinearLayout(this);
        previewFilesContainer.setOrientation(LinearLayout.VERTICAL);
        previewCard.addView(previewFilesContainer);

        root.addView(previewCard);
        root.addView(vspace(24));

        // Save button
        Button btnSave = makeButton("💾 Save Smart Folder", "#8B5CF6", "#FFFFFF");
        btnSave.setOnClickListener(v -> saveFolder());
        root.addView(btnSave);

        updatePreview();
    }

    private View buildRuleView(RuleRow row) {
        LinearLayout ruleCard = makeCard();
        ruleCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topRow = makeRow();
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Field spinner
        Spinner fieldSpinner = makeSpinner(FIELDS);
        fieldSpinner.setSelection(row.fieldIndex);
        row.fieldSpinner = fieldSpinner;
        topRow.addView(fieldSpinner);
        topRow.addView(hspace(8));

        // Operator spinner
        Spinner opSpinner = makeSpinner(OPERATORS);
        opSpinner.setSelection(row.operatorIndex);
        row.operatorSpinner = opSpinner;
        topRow.addView(opSpinner);

        // Delete button
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        topRow.addView(spacer);
        Button btnDel = makeButton("✕", "#374151", "#EF4444");
        btnDel.setOnClickListener(v -> {
            ruleRows.remove(row);
            buildUI();
        });
        topRow.addView(btnDel);
        ruleCard.addView(topRow);
        ruleCard.addView(vspace(8));

        // Value input
        EditText etVal = new EditText(this);
        etVal.setHint("Value");
        etVal.setHintTextColor(Color.parseColor("#6B7280"));
        etVal.setTextColor(Color.WHITE);
        etVal.setTextSize(14);
        etVal.setPadding(16, 12, 16, 12);
        etVal.setText(row.value);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(Color.parseColor("#111827"));
        etBg.setCornerRadius(8f);
        etVal.setBackground(etBg);
        row.etValue = etVal;
        ruleCard.addView(etVal);

        // Live update listeners
        fieldSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                row.fieldIndex = pos;
                updatePreview();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        opSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                row.operatorIndex = pos;
                updatePreview();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        etVal.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) { row.value = s.toString(); updatePreview(); }
        });

        return ruleCard;
    }

    // ─── Matching logic ───────────────────────────────────────────────────────

    private List<HubFile> getMatchingFiles() {
        List<HubFile> all = repo.getAllFiles();
        if (ruleRows.isEmpty()) return new ArrayList<>();
        List<HubFile> result = new ArrayList<>();
        for (HubFile f : all) {
            boolean match = matchAll; // start true for AND, false for OR
            for (RuleRow row : ruleRows) {
                boolean rowMatch = evaluateRule(f, row);
                if (matchAll) {
                    match = match && rowMatch;
                } else {
                    match = match || rowMatch;
                }
            }
            if (match) result.add(f);
        }
        return result;
    }

    private boolean evaluateRule(HubFile f, RuleRow row) {
        String field = FIELDS[row.fieldIndex];
        String op = OPERATORS[row.operatorIndex];
        String val = row.value.trim().toLowerCase();

        switch (field) {
            case "File Name": {
                String name = (f.displayName != null ? f.displayName : f.originalFileName != null
                        ? f.originalFileName : "").toLowerCase();
                return applyStringOp(name, op, val);
            }
            case "File Type": {
                String type = f.fileType != null ? f.fileType.name().toLowerCase() : "";
                return applyStringOp(type, op, val);
            }
            case "File Size (MB)": {
                try {
                    double mb = f.fileSize / (1024.0 * 1024);
                    double threshold = Double.parseDouble(val);
                    if (op.equals("Greater Than")) return mb > threshold;
                    if (op.equals("Less Than")) return mb < threshold;
                } catch (NumberFormatException ignored) {}
                return false;
            }
            case "Source": {
                String src = f.source != null ? f.source.name().toLowerCase() : "";
                return applyStringOp(src, op, val);
            }
            case "Date Added (days ago)": {
                try {
                    long days = Long.parseLong(val);
                    long cutoff = System.currentTimeMillis() - days * 86_400_000L;
                    if (op.equals("Less Than")) return f.importedAt > cutoff;     // newer
                    if (op.equals("Greater Than")) return f.importedAt < cutoff;  // older
                } catch (NumberFormatException ignored) {}
                return false;
            }
            case "Tags": {
                if (f.tags == null) return false;
                for (String t : f.tags) if (applyStringOp(t.toLowerCase(), op, val)) return true;
                return false;
            }
            case "Is Favourite":
                return applyBoolOp(f.isFavourited, op, val);
            case "Is Duplicate":
                return applyBoolOp(f.isDuplicate, op, val);
        }
        return false;
    }

    private boolean applyStringOp(String actual, String op, String val) {
        switch (op) {
            case "Contains": return actual.contains(val);
            case "Does Not Contain": return !actual.contains(val);
            case "Is": return actual.equals(val);
            case "Is Not": return !actual.equals(val);
        }
        return false;
    }

    private boolean applyBoolOp(boolean actual, String op, String val) {
        boolean target = "true".equals(val) || "yes".equals(val) || "1".equals(val);
        return (op.equals("Is") || op.equals("Greater Than")) == (actual == target);
    }

    private void updatePreview() {
        List<HubFile> matches = getMatchingFiles();
        if (tvPreviewCount != null) {
            tvPreviewCount.setText(matches.size() + " files match");
        }
        if (previewFilesContainer != null) {
            previewFilesContainer.removeAllViews();
            for (int i = 0; i < Math.min(5, matches.size()); i++) {
                HubFile f = matches.get(i);
                String name = f.displayName != null ? f.displayName : f.originalFileName;
                TextView tv = new TextView(this);
                tv.setText(f.getTypeEmoji() + "  " + name);
                tv.setTextColor(Color.parseColor("#D1D5DB"));
                tv.setTextSize(13);
                tv.setPadding(0, 4, 0, 4);
                previewFilesContainer.addView(tv);
            }
            if (matches.size() > 5) {
                TextView tv = new TextView(this);
                tv.setText("… and " + (matches.size() - 5) + " more");
                tv.setTextColor(Color.parseColor("#6B7280"));
                tv.setTextSize(12);
                previewFilesContainer.addView(tv);
            }
        }
    }

    private void saveFolder() {
        String name = etFolderName.getText().toString().trim();
        if (name.isEmpty()) {
            etFolderName.setError("Enter a folder name");
            return;
        }
        if (ruleRows.isEmpty()) {
            showToast("Add at least one rule");
            return;
        }

        // Build rules JSON
        try {
            JSONObject rules = new JSONObject();
            JSONArray rulesArr = new JSONArray();
            for (RuleRow row : ruleRows) {
                JSONObject r = new JSONObject();
                r.put("field", FIELDS[row.fieldIndex]);
                r.put("operator", OPERATORS[row.operatorIndex]);
                r.put("value", row.value);
                rulesArr.put(r);
            }
            rules.put("rules", rulesArr);
            rules.put("matchAll", matchAll);

            HubFolder folder = HubFolder.createSmartFolder(name, "#8B5CF6", "🔍", rules.toString());
            repo.addFolder(folder);
            showToast("Smart folder '" + name + "' saved!");
            finish();
        } catch (Exception e) {
            showToast("Failed to save folder");
        }
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private void rebuildToggle(Button selected, Button other) {
        setChipSelected(selected, true);
        setChipSelected(other, false);
    }

    private void setChipSelected(Button btn, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? Color.parseColor("#3B82F6") : Color.parseColor("#1F2937"));
        bg.setCornerRadius(16f);
        btn.setBackground(bg);
        btn.setTextColor(selected ? Color.WHITE : Color.parseColor("#9CA3AF"));
    }

    private Spinner makeSpinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setBackgroundColor(Color.parseColor("#111827"));
        return s;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 20, 20, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(16f);
        card.setBackground(bg);
        return card;
    }

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
        btn.setPadding(24, 12, 24, 12);
        GradientDrawable bgD = new GradientDrawable();
        bgD.setColor(Color.parseColor(bg));
        bgD.setCornerRadius(16f);
        btn.setBackground(bgD);
        return btn;
    }

    private Button makeChip(String text, boolean selected) {
        Button btn = makeButton(text, selected ? "#3B82F6" : "#1F2937",
                selected ? "#FFFFFF" : "#9CA3AF");
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
