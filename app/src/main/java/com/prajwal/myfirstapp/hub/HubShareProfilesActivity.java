package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class HubShareProfilesActivity extends AppCompatActivity {

    private static final String PREFS = "hub_share_profiles";
    private static final String KEY = "profiles_json";

    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        TextView tvTitle = makeTitle("📤 Share Profiles");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(16));

        Button btnAdd = makeButton("+ New Profile", "#6366F1", "#FFFFFF");
        btnAdd.setOnClickListener(v -> showAddDialog());
        root.addView(btnAdd);
        root.addView(vspace(16));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        loadProfiles();
    }

    private void loadProfiles() {
        listContainer.removeAllViews();
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No share profiles yet. Tap '+ New Profile' to create one.");
                empty.setTextColor(Color.parseColor("#6B7280"));
                empty.setTextSize(14);
                listContainer.addView(empty);
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                listContainer.addView(buildProfileCard(obj));
                listContainer.addView(vspace(8));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading profiles", Toast.LENGTH_SHORT).show();
        }
    }

    private View buildProfileCard(JSONObject obj) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(12f);
        card.setBackground(bg);
        card.setPadding(20, 16, 20, 16);

        try {
            String name = obj.optString("name", "Untitled");
            String desc = obj.optString("description", "");
            String contacts = obj.optString("contacts", "");
            String format = obj.optString("format", "Original");
            int expiry = obj.optInt("expiryDays", 0);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(16);
            tvName.setTypeface(null, Typeface.BOLD);
            card.addView(tvName);

            if (!desc.isEmpty()) {
                TextView tvDesc = new TextView(this);
                tvDesc.setText(desc);
                tvDesc.setTextColor(Color.parseColor("#9CA3AF"));
                tvDesc.setTextSize(13);
                card.addView(tvDesc);
            }

            LinearLayout meta = makeRow();
            meta.addView(makeChip(format, false));
            meta.addView(hspace(8));
            if (expiry > 0) meta.addView(makeChip("Expires in " + expiry + "d", false));
            card.addView(vspace(6));
            card.addView(meta);

            if (!contacts.isEmpty()) {
                TextView tvContacts = new TextView(this);
                tvContacts.setText("👥 " + contacts);
                tvContacts.setTextColor(Color.parseColor("#6B7280"));
                tvContacts.setTextSize(12);
                card.addView(vspace(4));
                card.addView(tvContacts);
            }

            card.addView(vspace(10));
            LinearLayout btns = makeRow();
            Button btnExec = makeButton("▶ Execute", "#6366F1", "#FFFFFF");
            btnExec.setOnClickListener(v -> executeProfile(name, desc, format));
            Button btnDel = makeButton("🗑", "#374151", "#EF4444");
            btnDel.setOnClickListener(v -> deleteProfile(obj.optString("id", "")));
            btns.addView(btnExec);
            btns.addView(hspace(8));
            btns.addView(btnDel);
            card.addView(btns);
        } catch (Exception ignored) {}

        return card;
    }

    private void executeProfile(String name, String desc, String format) {
        String manifest = "Share Profile: " + name + "\n" +
                "Description: " + desc + "\n" +
                "Format: " + format + "\n" +
                "Generated: " + new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date());
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Share Profile: " + name);
        share.putExtra(Intent.EXTRA_TEXT, manifest);
        startActivity(Intent.createChooser(share, "Share via"));
        HubFileRepository.getInstance(this).logShare(name, "Share Profile", 0);
        HubFileRepository.getInstance(this).logAudit("SHARE", "Executed profile: " + name);
    }

    private void deleteProfile(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Profile")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                        JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
                        JSONArray newArr = new JSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            if (!arr.getJSONObject(i).optString("id", "").equals(id))
                                newArr.put(arr.getJSONObject(i));
                        }
                        prefs.edit().putString(KEY, newArr.toString()).apply();
                        loadProfiles();
                    } catch (Exception e) { Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(32, 16, 32, 16);
        form.setBackgroundColor(Color.parseColor("#1E293B"));

        EditText etName = makeEditText("Profile Name");
        EditText etDesc = makeEditText("Description (what files to share)");
        EditText etContacts = makeEditText("Contacts (comma-separated names)");
        EditText etExpiry = makeEditText("Expiry days (0 = none)");
        etExpiry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        String[] formats = {"Original", "ZIP", "PDF"};
        Spinner spFormat = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, formats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFormat.setAdapter(adapter);

        form.addView(label("Profile Name"));
        form.addView(etName);
        form.addView(vspace(8));
        form.addView(label("Description"));
        form.addView(etDesc);
        form.addView(vspace(8));
        form.addView(label("Contacts"));
        form.addView(etContacts);
        form.addView(vspace(8));
        form.addView(label("Format"));
        form.addView(spFormat);
        form.addView(vspace(8));
        form.addView(label("Expiry Days (optional)"));
        form.addView(etExpiry);

        new AlertDialog.Builder(this)
                .setTitle("New Share Profile")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                        JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
                        JSONObject obj = new JSONObject();
                        obj.put("id", UUID.randomUUID().toString());
                        obj.put("name", name);
                        obj.put("description", etDesc.getText().toString().trim());
                        obj.put("contacts", etContacts.getText().toString().trim());
                        obj.put("format", spFormat.getSelectedItem().toString());
                        int expDays = 0;
                        try { expDays = Integer.parseInt(etExpiry.getText().toString().trim()); } catch (Exception ignored) {}
                        obj.put("expiryDays", expDays);
                        arr.put(obj);
                        prefs.edit().putString(KEY, arr.toString()).apply();
                        loadProfiles();
                        HubFileRepository.getInstance(this).logAudit("SETTINGS", "Created share profile: " + name);
                    } catch (Exception e) { Toast.makeText(this, "Error saving", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── UI helpers ───────────────────────────────────────────────────────────

    private LinearLayout makeRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView makeTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.WHITE);
        tv.setTextSize(22); tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(this);
        btn.setText(text); btn.setTextColor(Color.parseColor(fg));
        btn.setTextSize(13); btn.setPadding(24, 12, 24, 12);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bg)); d.setCornerRadius(20f);
        btn.setBackground(d);
        return btn;
    }

    private Button makeChip(String text, boolean selected) {
        Button btn = new Button(this);
        btn.setText(text); btn.setTextSize(11);
        btn.setTextColor(selected ? Color.WHITE : Color.parseColor("#9CA3AF"));
        btn.setPadding(14, 4, 14, 4);
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? Color.parseColor("#6366F1") : Color.parseColor("#374151"));
        d.setCornerRadius(12f);
        btn.setBackground(d);
        return btn;
    }

    private EditText makeEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#6B7280"));
        et.setBackgroundColor(Color.parseColor("#0F172A"));
        et.setPadding(12, 8, 12, 8);
        return et;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.parseColor("#9CA3AF")); tv.setTextSize(12);
        return tv;
    }

    private View vspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp));
        return v;
    }

    private View hspace(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }
}
