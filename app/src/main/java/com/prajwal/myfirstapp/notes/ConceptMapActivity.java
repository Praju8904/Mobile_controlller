package com.prajwal.myfirstapp.notes;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  CONCEPT MAP ACTIVITY — Displays a visual concept map of related notes.
 *  Uses ConceptMapView for force-directed graph visualization with zoom/pan/tap.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class ConceptMapActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private ConceptMapView conceptMapView;
    private NoteRepository noteRepository;
    private String centerNoteId;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        // Build layout programmatically (no separate XML needed)
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF0A0E21);

        conceptMapView = new ConceptMapView(this);
        root.addView(conceptMapView, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        // Top bar overlay
        android.widget.LinearLayout topBar = new android.widget.LinearLayout(this);
        topBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(48), dp(16), dp(16));

        // Back button
        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextSize(24);
        btnBack.setTextColor(0xFFF1F5F9);
        btnBack.setPadding(dp(8), dp(8), dp(16), dp(8));
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Concept Map");
        tvTitle.setTextColor(0xFFF1F5F9);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleLp);
        topBar.addView(tvTitle);

        android.widget.FrameLayout.LayoutParams topBarLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        topBarLp.gravity = android.view.Gravity.TOP;
        root.addView(topBar, topBarLp);

        // Hint text at bottom
        TextView tvHint = new TextView(this);
        tvHint.setText("Pinch to zoom · Drag to pan · Tap a node to open note");
        tvHint.setTextColor(0xFF64748B);
        tvHint.setTextSize(12);
        tvHint.setGravity(android.view.Gravity.CENTER);
        tvHint.setPadding(dp(16), dp(8), dp(16), dp(32));
        android.widget.FrameLayout.LayoutParams hintLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = android.view.Gravity.BOTTOM;
        root.addView(tvHint, hintLp);

        setContentView(root);

        noteRepository = new NoteRepository(this);

        // Get center note from intent
        centerNoteId = getIntent().getStringExtra("noteId");

        // Set tap listener
        conceptMapView.setOnNodeTapListener(note -> {
            // Open the tapped note
            Intent intent = new Intent(ConceptMapActivity.this, NoteEditorActivity.class);
            intent.putExtra("noteId", note.id);
            startActivity(intent);
        });

        loadConceptMap();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadConceptMap() {
        List<Note> allNotes = noteRepository.getAllNotes();
        if (allNotes == null || allNotes.isEmpty()) {
            Toast.makeText(this, "No notes to display", Toast.LENGTH_SHORT).show();
            return;
        }

        Note centerNote = null;

        // Find center note
        if (centerNoteId != null) {
            for (Note n : allNotes) {
                if (centerNoteId.equals(n.id)) {
                    centerNote = n;
                    break;
                }
            }
        }

        // If no center note specified, use the first one
        if (centerNote == null && !allNotes.isEmpty()) {
            centerNote = allNotes.get(0);
        }

        if (centerNote == null) {
            Toast.makeText(this, "Note not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find related notes
        List<Note> relatedNotes = new ArrayList<>();
        if (centerNote.relatedNoteIds != null && !centerNote.relatedNoteIds.isEmpty()) {
            for (String rid : centerNote.relatedNoteIds) {
                String trimId = rid.trim();
                for (Note n : allNotes) {
                    if (trimId.equals(n.id)) {
                        relatedNotes.add(n);
                        break;
                    }
                }
            }
        }

        // If no explicit relations, find notes with same category
        if (relatedNotes.isEmpty() && centerNote.category != null) {
            for (Note n : allNotes) {
                if (!n.id.equals(centerNote.id) && centerNote.category.equals(n.category)) {
                    relatedNotes.add(n);
                    if (relatedNotes.size() >= 10) break;
                }
            }
        }

        // If still empty, just show up to 8 recent notes
        if (relatedNotes.isEmpty()) {
            for (Note n : allNotes) {
                if (!n.id.equals(centerNote.id)) {
                    relatedNotes.add(n);
                    if (relatedNotes.size() >= 8) break;
                }
            }
        }

        conceptMapView.setNotes(centerNote, relatedNotes);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UTILS
    // ═══════════════════════════════════════════════════════════════════════════════

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
