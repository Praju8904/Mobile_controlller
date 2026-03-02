package com.prajwal.myfirstapp.hub;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HubTimeCapsuleActivity extends AppCompatActivity {

    private static final String PREFS = "hub_capsules";
    private static final String KEY = "capsules_json";

    private HubFileRepository repo;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);

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
        TextView tvTitle = makeTitle("⏳ Time Capsules");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(16));

        Button btnCreate = makeButton("+ Create Capsule", "#6366F1", "#FFFFFF");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCreate.setLayoutParams(lp);
        btnCreate.setOnClickListener(v -> showCreateDialog());
        root.addView(btnCreate);
        root.addView(vspace(16));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        loadCapsules();
        repo.logAudit("VIEW", "Opened Time Capsules");
    }

    private void loadCapsules() {
        listContainer.removeAllViews();
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No time capsules yet. Create one to hide messages for your future self!");
                empty.setTextColor(Color.parseColor("#6B7280")); empty.setTextSize(14);
                listContainer.addView(empty);
                return;
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                listContainer.addView(buildCapsuleCard(obj, now));
                listContainer.addView(vspace(10));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading capsules", Toast.LENGTH_SHORT).show();
        }
    }

    private View buildCapsuleCard(JSONObject obj, long now) {
        long openDate = obj.optLong("openDate", 0);
        boolean isSealed = openDate > now;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(isSealed ? "#1E293B" : "#0F2A1A"));
        bg.setCornerRadius(14f);
        bg.setStroke(2, Color.parseColor(isSealed ? "#6366F1" : "#22C55E"));
        card.setBackground(bg); card.setPadding(18, 16, 18, 16);

        String icon = isSealed ? "🔒" : "📭";
        String title = obj.optString("message", "Time Capsule");
        if (title.length() > 40) title = title.substring(0, 37) + "…";

        LinearLayout titleRow = makeRow();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon); tvIcon.setTextSize(24);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title); tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(15); tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tvIcon);
        titleRow.addView(hspace(10));
        titleRow.addView(tvTitle);
        card.addView(titleRow);
        card.addView(vspace(6));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        String dateStr = openDate > 0 ? sdf.format(new Date(openDate)) : "—";
        String statusText = isSealed ? "Opens on " + dateStr : "Opened " + dateStr;
        TextView tvDate = new TextView(this);
        tvDate.setText(statusText);
        tvDate.setTextColor(Color.parseColor(isSealed ? "#6366F1" : "#22C55E"));
        tvDate.setTextSize(12);
        card.addView(tvDate);

        int fileCount = obj.optJSONArray("fileIds") != null ? obj.optJSONArray("fileIds").length() : 0;
        if (fileCount > 0) {
            TextView tvFiles = new TextView(this);
            tvFiles.setText("📁 " + fileCount + " file(s)");
            tvFiles.setTextColor(Color.parseColor("#9CA3AF")); tvFiles.setTextSize(12);
            card.addView(tvFiles);
        }

        card.addView(vspace(10));
        LinearLayout btns = makeRow();

        if (!isSealed) {
            Button btnOpen = makeButton("🎉 Open Capsule", "#22C55E", "#FFFFFF");
            btnOpen.setOnClickListener(v -> showRevealScreen(obj));
            btns.addView(btnOpen);
            btns.addView(hspace(8));
        }

        Button btnDel = makeButton("🗑", "#374151", "#EF4444");
        btnDel.setOnClickListener(v -> deleteCapsule(obj.optString("id", "")));
        btns.addView(btnDel);
        card.addView(btns);
        return card;
    }

    private void showRevealScreen(JSONObject obj) {
        ScrollView revealScroll = new ScrollView(this);
        revealScroll.setBackgroundColor(Color.parseColor("#0F2A1A"));
        LinearLayout revealRoot = new LinearLayout(this);
        revealRoot.setOrientation(LinearLayout.VERTICAL);
        revealRoot.setPadding(40, 80, 40, 80);
        revealRoot.setGravity(Gravity.CENTER);
        revealScroll.addView(revealRoot);

        TextView tvStars = new TextView(this);
        tvStars.setText("✨🎊✨");
        tvStars.setTextSize(48); tvStars.setGravity(Gravity.CENTER);
        revealRoot.addView(tvStars);
        revealRoot.addView(vspace(20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Your Time Capsule is Open!");
        tvTitle.setTextColor(Color.parseColor("#22C55E")); tvTitle.setTextSize(24);
        tvTitle.setTypeface(null, Typeface.BOLD); tvTitle.setGravity(Gravity.CENTER);
        revealRoot.addView(tvTitle);
        revealRoot.addView(vspace(20));

        String message = obj.optString("message", "");
        if (!message.isEmpty()) {
            GradientDrawable msgBg = new GradientDrawable();
            msgBg.setColor(Color.parseColor("#1E293B")); msgBg.setCornerRadius(12f);
            TextView tvMsg = new TextView(this);
            tvMsg.setText("💌 " + message);
            tvMsg.setTextColor(Color.WHITE); tvMsg.setTextSize(16); tvMsg.setPadding(20, 16, 20, 16);
            tvMsg.setBackground(msgBg);
            revealRoot.addView(tvMsg);
            revealRoot.addView(vspace(16));
        }

        JSONArray fileIds = obj.optJSONArray("fileIds");
        if (fileIds != null && fileIds.length() > 0) {
            TextView tvFilesHeader = new TextView(this);
            tvFilesHeader.setText("Files included:");
            tvFilesHeader.setTextColor(Color.parseColor("#9CA3AF")); tvFilesHeader.setTextSize(13);
            tvFilesHeader.setGravity(Gravity.CENTER);
            revealRoot.addView(tvFilesHeader);
            revealRoot.addView(vspace(8));
            for (int i = 0; i < fileIds.length(); i++) {
                String fid = fileIds.optString(i, "");
                HubFile f = repo.getFileById(fid);
                if (f != null) {
                    String name = f.displayName != null ? f.displayName : f.originalFileName;
                    TextView tvF = new TextView(this);
                    tvF.setText(f.getTypeEmoji() + " " + (name != null ? name : "Unknown"));
                    tvF.setTextColor(Color.WHITE); tvF.setTextSize(14); tvF.setGravity(Gravity.CENTER);
                    revealRoot.addView(tvF);
                }
            }
        }

        revealRoot.addView(vspace(24));
        Button btnClose = makeButton("Close", "#6366F1", "#FFFFFF");
        btnClose.setOnClickListener(v -> loadCapsules());
        revealRoot.addView(btnClose);

        // Show as dialog
        new AlertDialog.Builder(this)
                .setView(revealScroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void deleteCapsule(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Capsule")
                .setMessage("Permanently delete this time capsule?")
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
                        loadCapsules();
                    } catch (Exception e) { Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showCreateDialog() {
        List<HubFile> allFiles = repo.getAllFiles();
        List<String> selectedIds = new ArrayList<>();
        final long[] openDate = {System.currentTimeMillis() + 30L * 24 * 3600 * 1000};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(32, 16, 32, 16);
        form.setBackgroundColor(Color.parseColor("#1E293B"));

        EditText etMsg = new EditText(this);
        etMsg.setHint("Message to your future self...");
        etMsg.setTextColor(Color.WHITE);
        etMsg.setHintTextColor(Color.parseColor("#6B7280"));
        etMsg.setBackgroundColor(Color.parseColor("#0F172A"));
        etMsg.setPadding(12, 8, 12, 8);
        etMsg.setMinLines(3);

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        final TextView tvOpenDate = new TextView(this);
        tvOpenDate.setText("Open date: " + sdf.format(new Date(openDate[0])));
        tvOpenDate.setTextColor(Color.parseColor("#6366F1")); tvOpenDate.setTextSize(13);
        tvOpenDate.setClickable(true); tvOpenDate.setFocusable(true);
        tvOpenDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(openDate[0]);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d);
                openDate[0] = sel.getTimeInMillis();
                tvOpenDate.setText("Open date: " + sdf.format(new Date(openDate[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        form.addView(makeLabel("Message"));
        form.addView(etMsg);
        form.addView(vspace(10));
        form.addView(tvOpenDate);
        form.addView(vspace(10));
        form.addView(makeLabel("Select Files (optional)"));
        form.addView(vspace(4));

        // File checkboxes (limit to 20 for dialog)
        int limit = Math.min(20, allFiles.size());
        for (int i = 0; i < limit; i++) {
            HubFile f = allFiles.get(i);
            CheckBox cb = new CheckBox(this);
            String name = f.displayName != null ? f.displayName : f.originalFileName;
            if (name != null && name.length() > 30) name = name.substring(0, 27) + "…";
            cb.setText(f.getTypeEmoji() + " " + (name != null ? name : "Unknown"));
            cb.setTextColor(Color.WHITE);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#6366F1")));
            final String fid = f.id;
            cb.setOnCheckedChangeListener((v, checked) -> {
                if (checked) selectedIds.add(fid);
                else selectedIds.remove(fid);
            });
            form.addView(cb);
        }

        new AlertDialog.Builder(this)
                .setTitle("Create Time Capsule")
                .setView(new ScrollView(this) {{ addView(form); }})
                .setPositiveButton("Create", (d, w) -> {
                    try {
                        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                        JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
                        JSONObject obj = new JSONObject();
                        obj.put("id", UUID.randomUUID().toString());
                        obj.put("message", etMsg.getText().toString().trim());
                        obj.put("openDate", openDate[0]);
                        obj.put("createdAt", System.currentTimeMillis());
                        JSONArray fileArr = new JSONArray();
                        for (String fid : selectedIds) fileArr.put(fid);
                        obj.put("fileIds", fileArr);
                        arr.put(obj);
                        prefs.edit().putString(KEY, arr.toString()).apply();
                        loadCapsules();
                        repo.logAudit("SETTINGS", "Created time capsule");
                        Toast.makeText(this, "Time capsule created! 🔒", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(this, "Error creating capsule", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(Color.parseColor("#9CA3AF")); tv.setTextSize(12); return tv;
    }
    private LinearLayout makeRow() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r;
    }
    private TextView makeTitle(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.WHITE);
        tv.setTextSize(22); tv.setTypeface(null, Typeface.BOLD); return tv;
    }
    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(this); btn.setText(text); btn.setTextColor(Color.parseColor(fg));
        btn.setTextSize(13); btn.setPadding(24, 12, 24, 12);
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.parseColor(bg)); d.setCornerRadius(20f);
        btn.setBackground(d); return btn;
    }
    private View vspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)); return v;
    }
    private View hspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.MATCH_PARENT)); return v;
    }
}
