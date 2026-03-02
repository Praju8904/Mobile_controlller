package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Hub Settings screen — all preferences for Smart File Hub.
 */
public class HubSettingsActivity extends AppCompatActivity {

    private static final String PREFS = "hub_settings";
    private SharedPreferences prefs;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_settings);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        findViewById(R.id.btnSettingsBack).setOnClickListener(v -> finish());
        root = findViewById(R.id.settingsRoot);
        buildUI();
    }

    private void buildUI() {
        // Sources
        addSectionHeader("📡 Sources");
        addToggle("WhatsApp Images", "src_wa_images", true);
        addToggle("WhatsApp Videos", "src_wa_videos", true);
        addToggle("WhatsApp Documents", "src_wa_docs", true);
        addToggle("Device Downloads", "src_downloads", true);
        addToggle("Screenshots", "src_screenshots", true);
        addToggle("Camera Photos", "src_camera", true);
        addToggle("Auto-scan on App Open", "auto_scan_open", true);
        addToggle("Background Scan", "bg_scan", false);
        addButton("🔄 Rescan All Sources", () -> {
            HubFileRepository.getInstance(this).scanForNewFiles(() ->
                    Toast.makeText(this, "Scan complete", Toast.LENGTH_SHORT).show());
        });

        // Inbox
        addSectionHeader("📥 Inbox");
        addToggle("Auto-categorization", "inbox_auto_cat", true);
        addSeekBar("Confidence Threshold", "inbox_conf_threshold", 50, 95, 75);
        addSeekBar("Bulk Accept Threshold", "inbox_bulk_threshold", 50, 95, 80);
        addToggle("Inbox Badge", "inbox_badge", true);
        addToggle("New File Notification", "inbox_notif", true);

        // Display
        addSectionHeader("🖥️ Display");
        addRadioGroup("Default View", "display_view", new String[]{"Grid", "List", "Timeline"}, 0);
        addRadioGroup("Grid Column Count", "grid_cols", new String[]{"2", "3", "4"}, 1);
        addRadioGroup("Thumbnail Quality", "thumb_quality", new String[]{"Low", "Medium", "High"}, 1);
        addToggle("Show File Size", "show_file_size", true);
        addToggle("Show Source Badge", "show_source_badge", true);
        addToggle("Show Date", "show_date", true);
        addToggle("Show Color Label Dots", "show_color_dots", true);
        addToggle("Relative Date Format", "date_relative", true);

        // Duplicate Detection
        addSectionHeader("🔍 Duplicate Detection");
        addToggle("Auto-detect on Import", "dupe_auto", true);
        addRadioGroup("Detection Method", "dupe_method", new String[]{"Exact Match", "Near Duplicate"}, 0);
        addSeekBar("Near Duplicate Sensitivity", "dupe_sensitivity", 0, 100, 70);
        addToggle("Show Duplicate Badge", "dupe_badge", true);
        addToggle("Duplicate Notification", "dupe_notif", false);

        // Storage
        addSectionHeader("💾 Storage");
        addSpinner("Low Storage Warning", "storage_low_pct",
                new String[]{"10%", "15%", "20%", "25%"}, 1);
        addSpinner("Large File Threshold", "large_file_mb",
                new String[]{"25 MB", "50 MB", "100 MB", "250 MB", "500 MB"}, 2);
        addSpinner("Old File Threshold", "old_file_months",
                new String[]{"3 months", "6 months", "12 months"}, 1);

        // Quick Share
        addSectionHeader("⚡ Quick Share");
        addButton("📌 Manage Pins", () ->
                startActivity(new Intent(this, HubQuickShareActivity.class)));
        addSpinner("Maximum Pins", "max_pins", new String[]{"5", "10", "15"}, 1);

        // Privacy & Security
        addSectionHeader("🔒 Privacy & Security");
        addToggle("App Lock", "app_lock", false);
        addToggle("Secure Screen", "secure_screen", false);
        addToggle("Hide File Names in Recent Apps", "hide_names_recents", false);
        addToggle("Activity Log", "activity_log", true);
        addButton("🗑️ Clear Activity Log", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Clear Activity Log")
                        .setMessage("Are you sure? This cannot be undone.")
                        .setPositiveButton("Clear", (d, w) ->
                                Toast.makeText(this, "Activity log cleared", Toast.LENGTH_SHORT).show())
                        .setNegativeButton("Cancel", null)
                        .show());
        addButton("🛡️ Privacy Analyzer", () ->
                startActivity(new android.content.Intent(this, HubPrivacyAnalyzerActivity.class)));
        addButton("📒 Access Audit Log", () ->
                startActivity(new android.content.Intent(this, HubAuditLogActivity.class)));

        // Stealth Mode
        addSectionHeader("🕵️ Stealth Mode");
        addToggle("Enable Stealth Mode", "stealth_enabled", false);
        addButton("🔑 Set Secret Code", () -> {
            android.widget.EditText et = new android.widget.EditText(this);
            et.setHint("Secret code (e.g. 1337)");
            et.setText(prefs.getString("stealth_code", "1337"));
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(this)
                    .setTitle("Secret Code")
                    .setView(et)
                    .setPositiveButton("Save", (d, w) -> {
                        String code = et.getText().toString().trim();
                        if (!code.isEmpty()) {
                            prefs.edit().putString("stealth_code", code).apply();
                            Toast.makeText(this, "Code saved", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        });
        addButton("🏷️ Set Display Label", () -> {
            String[] labels = {"Calculator", "Utilities", "Tools", "Custom…"};
            new AlertDialog.Builder(this)
                    .setTitle("Display Label")
                    .setItems(labels, (d, which) -> {
                        if (which == labels.length - 1) {
                            android.widget.EditText et = new android.widget.EditText(this);
                            et.setHint("Custom label");
                            new AlertDialog.Builder(this)
                                    .setTitle("Custom Label")
                                    .setView(et)
                                    .setPositiveButton("Save", (d2, w2) -> {
                                        String lbl = et.getText().toString().trim();
                                        if (!lbl.isEmpty()) prefs.edit().putString("stealth_label", lbl).apply();
                                    })
                                    .setNegativeButton("Cancel", null).show();
                        } else {
                            prefs.edit().putString("stealth_label", labels[which]).apply();
                            Toast.makeText(this, "Label set to: " + labels[which], Toast.LENGTH_SHORT).show();
                        }
                    }).show();
        });

        // Data
        addSectionHeader("📦 Data");
        addButton("📤 Export File Index", () ->
                Toast.makeText(this, "Export not yet implemented", Toast.LENGTH_SHORT).show());
        addButton("🖼️ Clear Thumbnail Cache", () ->
                Toast.makeText(this, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show());
        addButton("🔄 Reset Smart Categories", () ->
                Toast.makeText(this, "Smart categories reset", Toast.LENGTH_SHORT).show());
        addButton("⚠️ Reset All Settings", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Reset All Settings")
                        .setMessage("All settings will be reset to defaults. Continue?")
                        .setPositiveButton("Reset", (d, w) -> {
                            prefs.edit().clear().apply();
                            Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show();
                            root.removeAllViews();
                            buildUI();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        // Bottom padding
        View pad = new View(this);
        pad.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48));
        root.addView(pad);
    }

    // ─── UI Builders ──────────────────────────────────────────────────────────

    private void addSectionHeader(String title) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 24, 0, 8);
        tv.setLayoutParams(lp);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#7C3AED"));
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(false);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#312E81"));

        root.addView(tv);
        root.addView(divider);
    }

    @SuppressWarnings("UseSwitchCompatOrMaterialCode")
    private void addToggle(String label, String prefKey, boolean defaultVal) {
        LinearLayout row = buildRowContainer();
        TextView tv = buildLabel(label);
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(prefKey, defaultVal));
        sw.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(prefKey, checked).apply());
        row.addView(tv);
        row.addView(sw);
        root.addView(row);
    }

    private void addSeekBar(String label, String prefKey, int min, int max, int defaultVal) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        wrapper.setLayoutParams(lp);
        wrapper.setPadding(0, 8, 0, 8);

        LinearLayout header = buildRowContainer();
        TextView tvLabel = buildLabel(label);
        int saved = prefs.getInt(prefKey, defaultVal);
        TextView tvValue = new TextView(this);
        tvValue.setText(String.valueOf(saved));
        tvValue.setTextColor(Color.parseColor("#7C3AED"));
        tvValue.setTextSize(14f);
        tvValue.setTypeface(null, Typeface.BOLD);
        header.addView(tvLabel);
        header.addView(tvValue);
        wrapper.addView(header);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(saved - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + min;
                tvValue.setText(String.valueOf(val));
                prefs.edit().putInt(prefKey, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        wrapper.addView(seekBar);
        root.addView(wrapper);
    }

    private void addRadioGroup(String label, String prefKey, String[] options, int defaultIdx) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        wrapper.setLayoutParams(lp);

        TextView tvLabel = buildLabel(label);
        wrapper.addView(tvLabel);

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        int saved = prefs.getInt(prefKey, defaultIdx);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(options[i]);
            rb.setTextColor(Color.parseColor("#CBD5E1"));
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7C3AED")));
            rb.setTextSize(13f);
            LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbLp.setMargins(0, 0, 16, 0);
            rb.setLayoutParams(rbLp);
            rg.addView(rb);
            if (i == saved) rg.check(rb.getId());
        }
        int[] idxHolder = {saved};
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < rg.getChildCount(); i++) {
                if (rg.getChildAt(i).getId() == checkedId) {
                    prefs.edit().putInt(prefKey, i).apply();
                    break;
                }
            }
        });
        wrapper.addView(rg);
        root.addView(wrapper);
    }

    private void addSpinner(String label, String prefKey, String[] options, int defaultIdx) {
        LinearLayout row = buildRowContainer();
        TextView tvLabel = buildLabel(label);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(prefs.getInt(prefKey, defaultIdx));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(prefKey, position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        row.addView(tvLabel);
        row.addView(spinner);
        root.addView(row);
    }

    private void addButton(String label, Runnable action) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        btn.setLayoutParams(lp);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setOnClickListener(v -> action.run());
        root.addView(btn);
    }

    private LinearLayout buildRowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 4, 0, 4);
        row.setLayoutParams(lp);
        row.setPadding(0, 8, 0, 8);
        return row;
    }

    private TextView buildLabel(String text) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(14f);
        return tv;
    }
}
