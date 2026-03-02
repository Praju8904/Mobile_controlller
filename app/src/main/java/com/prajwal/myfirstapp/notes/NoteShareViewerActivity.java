package com.prajwal.myfirstapp.notes;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE SHARE VIEWER ACTIVITY — Renders a shared note in read-only mode.
 *  Supports viewing rich content shared via NoteShareManager links or imports.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class NoteShareViewerActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        String noteId = getIntent().getStringExtra("noteId");
        String sharedContent = getIntent().getStringExtra("sharedContent");

        if (noteId != null) {
            loadNoteById(noteId);
        } else if (sharedContent != null) {
            renderSharedContent(sharedContent);
        } else {
            // Try to handle incoming share intent
            handleIncomingShare();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LOAD NOTE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadNoteById(String noteId) {
        NoteRepository repo = new NoteRepository(this);
        Note note = null;
        for (Note n : repo.getAllNotes()) {
            if (noteId.equals(n.id)) {
                note = n;
                break;
            }
        }

        if (note == null) {
            Toast.makeText(this, "Note not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        renderNote(note);
    }

    private void handleIncomingShare() {
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                renderSharedContent(text);
                return;
            }
        }
        Toast.makeText(this, "Nothing to display", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RENDER NOTE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void renderNote(Note note) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF0A0E21);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(24));

        // ── Top Bar ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextSize(24);
        btnBack.setTextColor(0xFFF1F5F9);
        btnBack.setPadding(dp(8), dp(8), dp(16), dp(8));
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Shared Note");
        tvHeader.setTextColor(0xFFF1F5F9);
        tvHeader.setTextSize(18);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvHeader.setLayoutParams(headerLp);
        topBar.addView(tvHeader);

        // Share button
        TextView btnShare = new TextView(this);
        btnShare.setText("📤");
        btnShare.setTextSize(22);
        btnShare.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnShare.setOnClickListener(v -> {
            NoteShareManager shareManager = new NoteShareManager(this);
            shareManager.shareAsText(note, null);
        });
        topBar.addView(btnShare);

        root.addView(topBar);
        root.addView(createSpacer(24));

        // ── Title ──
        TextView tvTitle = new TextView(this);
        tvTitle.setText(note.title != null ? note.title : "Untitled");
        tvTitle.setTextColor(0xFFF1F5F9);
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);

        // ── Meta ──
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setPadding(0, dp(8), 0, dp(16));

        if (note.category != null && !note.category.isEmpty()) {
            TextView tvCategory = createPill(note.category, 0xFF3B82F6);
            metaRow.addView(tvCategory);
        }

        TextView tvDate = new TextView(this);
        tvDate.setText(sdf.format(new Date(note.updatedAt)));
        tvDate.setTextColor(0xFF64748B);
        tvDate.setTextSize(12);
        tvDate.setPadding(dp(12), dp(4), 0, dp(4));
        metaRow.addView(tvDate);

        root.addView(metaRow);

        // ── Divider ──
        View divider = new View(this);
        divider.setBackgroundColor(0xFF1E293B);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        root.addView(divider);
        root.addView(createSpacer(16));

        // ── Body Content ──
        if (note.blocksJson != null && !note.blocksJson.isEmpty()) {
            renderBlocks(root, note.blocksJson);
        } else if (note.body != null) {
            TextView tvBody = new TextView(this);
            tvBody.setText(note.body);
            tvBody.setTextColor(0xFFCBD5E1);
            tvBody.setTextSize(16);
            tvBody.setLineSpacing(dp(4), 1f);
            root.addView(tvBody);
        }

        // ── Tags ──
        if (note.tags != null && !note.tags.isEmpty()) {
            root.addView(createSpacer(24));
            LinearLayout tagsRow = new LinearLayout(this);
            tagsRow.setOrientation(LinearLayout.HORIZONTAL);
            tagsRow.setPadding(0, 0, 0, 0);
            for (String tag : note.tags) {
                String t = tag.trim();
                if (!t.isEmpty()) {
                    TextView pill = createPill("#" + t, 0xFF1E293B);
                    pill.setTextColor(0xFF94A3B8);
                    tagsRow.addView(pill);
                }
            }
            root.addView(tagsRow);
        }

        // ── Clone to My Notes Button ──
        root.addView(createSpacer(32));
        MaterialButton btnClone = new MaterialButton(this);
        btnClone.setText("📋 Save to My Notes");
        btnClone.setTextColor(0xFF0A0E21);
        btnClone.setBackgroundColor(0xFFF59E0B);
        btnClone.setCornerRadius(dp(12));
        btnClone.setOnClickListener(v -> {
            // Clone the note
            Note cloned = new Note();
            cloned.title = note.title + " (copy)";
            cloned.body = note.body;
            cloned.blocksJson = note.blocksJson;
            cloned.category = note.category;
            cloned.tags = note.tags;
            cloned.createdAt = System.currentTimeMillis();
            cloned.updatedAt = System.currentTimeMillis();

            NoteRepository repo2 = new NoteRepository(this);
            repo2.addNote(cloned);
            Toast.makeText(this, "Note saved to your collection!", Toast.LENGTH_SHORT).show();
        });
        root.addView(btnClone);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RENDER BLOCKS (simplified read-only)
    // ═══════════════════════════════════════════════════════════════════════════════

    private void renderBlocks(LinearLayout container, String blocksJson) {
        try {
            JSONArray blocks = new JSONArray(blocksJson);
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                int type = block.optInt("type", 0);
                String content = block.optString("content", "");

                TextView tv = new TextView(this);
                tv.setTextColor(0xFFCBD5E1);
                tv.setPadding(0, dp(4), 0, dp(4));

                switch (type) {
                    case 1: // H1
                        tv.setTextSize(24);
                        tv.setTypeface(null, android.graphics.Typeface.BOLD);
                        tv.setTextColor(0xFFF1F5F9);
                        break;
                    case 2: // H2
                        tv.setTextSize(20);
                        tv.setTypeface(null, android.graphics.Typeface.BOLD);
                        tv.setTextColor(0xFFF1F5F9);
                        break;
                    case 3: // H3
                        tv.setTextSize(18);
                        tv.setTypeface(null, android.graphics.Typeface.BOLD);
                        tv.setTextColor(0xFFF1F5F9);
                        break;
                    case 4: // Bullet
                        content = "• " + content;
                        tv.setTextSize(16);
                        tv.setPadding(dp(16), dp(2), 0, dp(2));
                        break;
                    case 5: // Numbered
                        content = (i + 1) + ". " + content;
                        tv.setTextSize(16);
                        tv.setPadding(dp(16), dp(2), 0, dp(2));
                        break;
                    case 6: // Checkbox
                        boolean checked = block.optBoolean("checked", false);
                        content = (checked ? "☑ " : "☐ ") + content;
                        tv.setTextSize(16);
                        tv.setPadding(dp(16), dp(2), 0, dp(2));
                        break;
                    case 7: // Quote
                        tv.setTextSize(16);
                        tv.setTypeface(null, android.graphics.Typeface.ITALIC);
                        tv.setTextColor(0xFFF59E0B);
                        tv.setPadding(dp(16), dp(8), dp(8), dp(8));
                        tv.setBackgroundColor(0x20F59E0B);
                        break;
                    case 8: // Code
                        tv.setTextSize(13);
                        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                        tv.setTextColor(0xFF10B981);
                        tv.setBackgroundColor(0xFF1E293B);
                        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
                        break;
                    case 9: // Divider
                        View divider = new View(this);
                        divider.setBackgroundColor(0xFF1E293B);
                        divider.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                        container.addView(divider);
                        continue;
                    default:
                        tv.setTextSize(16);
                        break;
                }

                tv.setText(content);
                container.addView(tv);
            }
        } catch (Exception e) {
            // Fallback — show raw text
            TextView tv = new TextView(this);
            tv.setText(blocksJson);
            tv.setTextColor(0xFFCBD5E1);
            tv.setTextSize(14);
            container.addView(tv);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RENDER SHARED TEXT
    // ═══════════════════════════════════════════════════════════════════════════════

    private void renderSharedContent(String text) {
        Note note = new Note();
        note.title = "Shared Content";
        note.body = text;
        note.createdAt = System.currentTimeMillis();
        note.updatedAt = System.currentTimeMillis();
        renderNote(note);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private TextView createPill(String text, int bgColor) {
        TextView pill = new TextView(this);
        pill.setText(text);
        pill.setTextSize(11);
        pill.setTextColor(0xFFF1F5F9);
        pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        pill.setBackgroundColor(bgColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private View createSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return spacer;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
