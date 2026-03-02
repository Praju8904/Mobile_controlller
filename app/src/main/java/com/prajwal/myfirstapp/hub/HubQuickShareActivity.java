package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Quick Share screen — shows pinned files as large share cards.
 */
public class HubQuickShareActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_FILE = 6001;

    private HubFileRepository repo;
    private LinearLayout pinsContainer;
    private TextView tvEmpty;
    private Button btnEditPins;
    private boolean editMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_quick_share);
        repo = HubFileRepository.getInstance(this);

        findViewById(R.id.btnQuickShareBack).setOnClickListener(v -> finish());

        btnEditPins = findViewById(R.id.btnEditPins);
        btnEditPins.setOnClickListener(v -> toggleEditMode());

        Button btnAddPin = findViewById(R.id.btnAddPin);
        btnAddPin.setOnClickListener(v -> {
            Intent i = new Intent(this, HubFileBrowserActivity.class);
            i.putExtra(HubFileBrowserActivity.EXTRA_PICK_MODE, true);
            startActivityForResult(i, REQUEST_PICK_FILE);
        });

        pinsContainer = findViewById(R.id.quickSharePinsContainer);
        tvEmpty = findViewById(R.id.tvQuickShareEmpty);

        loadPins();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPins();
    }

    private void loadPins() {
        pinsContainer.removeAllViews();
        List<String> pinIds = repo.getQuickSharePins();
        if (pinIds.isEmpty()) {
            tvEmpty.setVisibility(android.view.View.VISIBLE);
            pinsContainer.setVisibility(android.view.View.GONE);
            return;
        }
        tvEmpty.setVisibility(android.view.View.GONE);
        pinsContainer.setVisibility(android.view.View.VISIBLE);

        for (String id : pinIds) {
            HubFile file = repo.getFileById(id);
            if (file == null) continue;
            pinsContainer.addView(buildPinCard(file));
        }
    }

    private LinearLayout buildPinCard(HubFile file) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        card.setLayoutParams(lp);
        card.setPadding(16, 16, 16, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(14f);
        card.setBackground(bg);

        // Header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText(file.getTypeEmoji());
        tvEmoji.setTextSize(28f);
        tvEmoji.setPadding(0, 0, 12, 0);

        LinearLayout nameBlock = new LinearLayout(this);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameBlock.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String name = file.displayName != null && !file.displayName.isEmpty() ? file.displayName : file.originalFileName;
        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(15f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setMaxLines(2);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvSize = new TextView(this);
        tvSize.setText(file.getFormattedSize() + "  •  " + (file.fileType != null ? file.fileType.name() : "FILE"));
        tvSize.setTextColor(Color.parseColor("#94A3B8"));
        tvSize.setTextSize(12f);

        nameBlock.addView(tvName);
        nameBlock.addView(tvSize);

        header.addView(tvEmoji);
        header.addView(nameBlock);

        if (editMode) {
            Button btnRemove = new Button(this);
            btnRemove.setText("✕");
            btnRemove.setTextColor(Color.WHITE);
            btnRemove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444")));
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(72, 72);
            btnRemove.setLayoutParams(removeLp);
            btnRemove.setOnClickListener(v -> {
                repo.removeQuickSharePin(file.id);
                loadPins();
            });
            header.addView(btnRemove);
        }

        card.addView(header);

        // Share button
        Button btnShare = new Button(this);
        btnShare.setText("⬆ Share");
        btnShare.setTextColor(Color.WHITE);
        btnShare.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#7C3AED")));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 12, 0, 0);
        btnShare.setLayoutParams(btnLp);
        btnShare.setOnClickListener(v -> shareFile(file));
        card.addView(btnShare);

        return card;
    }

    private void shareFile(HubFile file) {
        if (file.filePath == null || file.filePath.isEmpty()) {
            Toast.makeText(this, "File path not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(file.mimeType != null && !file.mimeType.isEmpty() ? file.mimeType : "*/*");
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", new java.io.File(file.filePath));
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share " + file.displayName));
        } catch (Exception e) {
            // Fallback: share just the path as text
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, file.filePath);
            startActivity(Intent.createChooser(shareIntent, "Share file path"));
        }
    }

    private void toggleEditMode() {
        editMode = !editMode;
        btnEditPins.setText(editMode ? "Done" : "Edit Pins");
        loadPins();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            String fileId = data.getStringExtra(HubFileBrowserActivity.EXTRA_PICKED_FILE_ID);
            if (fileId != null && !fileId.isEmpty()) {
                repo.addQuickSharePin(fileId);
                loadPins();
            }
        }
    }
}
