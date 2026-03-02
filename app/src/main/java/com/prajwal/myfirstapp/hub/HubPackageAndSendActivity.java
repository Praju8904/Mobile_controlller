package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HubPackageAndSendActivity extends AppCompatActivity {

    private HubFileRepository repo;
    private List<HubFile> allFiles = new ArrayList<>();
    private List<String> selectedIds = new ArrayList<>();
    private LinearLayout fileListContainer;
    private EditText etPackageName;
    private EditText etPassword;
    private Spinner spCompression;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = HubFileRepository.getInstance(this);
        allFiles = repo.getAllFiles();

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
        TextView tvTitle = makeTitle("📦 Package & Send");
        header.addView(tvTitle);
        root.addView(header);
        root.addView(vspace(20));

        // Package options
        root.addView(sectionHeader("Package Options"));
        root.addView(vspace(8));

        root.addView(label("Package Name"));
        etPackageName = makeEditText("my_package");
        root.addView(etPackageName);
        root.addView(vspace(10));

        root.addView(label("Compression Level"));
        spCompression = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"None", "Low", "Medium", "High"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCompression.setAdapter(adapter);
        spCompression.setSelection(2);
        root.addView(spCompression);
        root.addView(vspace(10));

        root.addView(label("Password (optional)"));
        LinearLayout pwdRow = makeRow();
        pwdRow.setGravity(Gravity.CENTER_VERTICAL);
        etPassword = makeEditText("Leave empty for no password");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams pwdLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etPassword.setLayoutParams(pwdLp);
        Button btnCopyPwd = makeButton("📋", "#374151", "#E5E7EB");
        btnCopyPwd.setOnClickListener(v -> {
            String pwd = etPassword.getText().toString();
            if (!pwd.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("password", pwd));
                Toast.makeText(this, "Password copied", Toast.LENGTH_SHORT).show();
            }
        });
        pwdRow.addView(etPassword);
        pwdRow.addView(hspace(8));
        pwdRow.addView(btnCopyPwd);
        root.addView(pwdRow);
        root.addView(vspace(20));

        // File list
        root.addView(sectionHeader("Select Files (" + allFiles.size() + " available)"));
        root.addView(vspace(8));

        Button btnSelAll = makeButton("Select All", "#374151", "#E5E7EB");
        btnSelAll.setOnClickListener(v -> {
            selectedIds.clear();
            for (HubFile f : allFiles) selectedIds.add(f.id);
            renderFileList();
        });
        root.addView(btnSelAll);
        root.addView(vspace(8));

        fileListContainer = new LinearLayout(this);
        fileListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(fileListContainer);
        renderFileList();
        root.addView(vspace(20));

        // Package & Send button
        Button btnSend = makeButton("📦 Package & Send", "#6366F1", "#FFFFFF");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnSend.setLayoutParams(sendLp);
        btnSend.setOnClickListener(v -> packageAndSend());
        root.addView(btnSend);
    }

    private void renderFileList() {
        fileListContainer.removeAllViews();
        for (HubFile f : allFiles) {
            LinearLayout row = makeRow();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(8, 4, 8, 4);

            CheckBox cb = new CheckBox(this);
            cb.setChecked(selectedIds.contains(f.id));
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#6366F1")));
            cb.setOnCheckedChangeListener((v, checked) -> {
                if (checked) { if (!selectedIds.contains(f.id)) selectedIds.add(f.id); }
                else selectedIds.remove(f.id);
            });

            String displayName = f.displayName != null ? f.displayName : f.originalFileName;
            if (displayName != null && displayName.length() > 30) displayName = displayName.substring(0, 27) + "…";
            TextView tvName = new TextView(this);
            tvName.setText(f.getTypeEmoji() + " " + displayName);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(13);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvSize = new TextView(this);
            tvSize.setText(f.getFormattedSize());
            tvSize.setTextColor(Color.parseColor("#6B7280"));
            tvSize.setTextSize(11);

            row.addView(cb);
            row.addView(hspace(8));
            row.addView(tvName);
            row.addView(tvSize);
            fileListContainer.addView(row);
        }
    }

    private void packageAndSend() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Select at least one file", Toast.LENGTH_SHORT).show();
            return;
        }
        String packageName = etPackageName.getText().toString().trim();
        if (packageName.isEmpty()) packageName = "hub_package";
        String compression = spCompression.getSelectedItem().toString();
        boolean hasPassword = !etPassword.getText().toString().trim().isEmpty();

        StringBuilder manifest = new StringBuilder();
        manifest.append("📦 File Package: ").append(packageName).append("\n");
        manifest.append("Generated: ").append(
                new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(new Date())).append("\n");
        manifest.append("Compression: ").append(compression).append("\n");
        if (hasPassword) manifest.append("🔒 Password Protected\n");
        manifest.append("─────────────────────\n");
        long totalSize = 0;
        int count = 0;
        for (HubFile f : allFiles) {
            if (selectedIds.contains(f.id)) {
                String name = f.displayName != null ? f.displayName : f.originalFileName;
                manifest.append("  ").append(f.getTypeEmoji()).append(" ").append(name)
                        .append("  (").append(f.getFormattedSize()).append(")\n");
                totalSize += f.fileSize;
                count++;
            }
        }
        manifest.append("─────────────────────\n");
        manifest.append("Total: ").append(count).append(" files, ");
        manifest.append(formatSize(totalSize));

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Package: " + packageName);
        share.putExtra(Intent.EXTRA_TEXT, manifest.toString());
        startActivity(Intent.createChooser(share, "Send Package via"));

        repo.logShare(packageName, "Package & Send", totalSize);
        repo.logAudit("SHARE", "Packaged " + count + " files as: " + packageName);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.parseColor("#6366F1"));
        tv.setTextSize(13); tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        return tv;
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
    private EditText makeEditText(String hint) {
        EditText et = new EditText(this); et.setHint(hint); et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#6B7280")); et.setBackgroundColor(Color.parseColor("#1E293B"));
        et.setPadding(12, 8, 12, 8); return et;
    }
    private TextView label(String text) {
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(Color.parseColor("#9CA3AF")); tv.setTextSize(12); return tv;
    }
    private View vspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)); return v;
    }
    private View hspace(int dp) {
        View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.MATCH_PARENT)); return v;
    }
}
