package com.prajwal.myfirstapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.ArrayList;

/**
 * NotesArchiveActivity — Displays archived notes with unarchive option.
 */
public class NotesArchiveActivity extends AppCompatActivity implements NotesAdapter.OnNoteActionListener {

    private NoteRepository repository;
    private RecyclerView archiveRecyclerView;
    private NotesAdapter adapter;
    private LinearLayout emptyStateContainer;
    private TextView tvArchiveCount;

    private ArrayList<Note> archivedNotes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_archive);

        repository = new NoteRepository(this);

        initViews();
        setupRecyclerView();
        refreshArchive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshArchive();
    }

    private void initViews() {
        archiveRecyclerView = findViewById(R.id.archiveRecyclerView);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvArchiveCount = findViewById(R.id.tvArchiveCount);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(
            2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        archiveRecyclerView.setLayoutManager(layoutManager);

        adapter = new NotesAdapter(this, archivedNotes, this, true);
        adapter.setArchiveMode(true);
        archiveRecyclerView.setAdapter(adapter);
    }

    private void refreshArchive() {
        archivedNotes.clear();
        archivedNotes.addAll(repository.getArchivedNotes());
        adapter.notifyDataSetChanged();

        // Update count with animation
        int count = archivedNotes.size();
        String countText = count + (count == 1 ? " note" : " notes");
        tvArchiveCount.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(100)
            .withEndAction(() -> {
                tvArchiveCount.setText(countText);
                tvArchiveCount.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start();
            })
            .start();

        // Show/hide empty state
        boolean isEmpty = archivedNotes.isEmpty();
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        archiveRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ─── Adapter Callbacks ───────────────────────────────────────

    @Override
    public void onNoteClick(Note note) {
        showNoteOptions(note);
    }

    @Override
    public void onNoteLongClick(Note note) {
        showNoteOptions(note);
    }

    @Override
    public void onNoteSelectionChanged(String noteId, boolean selected) {
        // Multi-select not enabled in archive view
    }

    private void showNoteOptions(Note note) {
        String[] options = {"📤 Unarchive", "🗑️ Move to Trash", "🗑️ Delete Permanently"};

        new AlertDialog.Builder(this)
            .setTitle(note.title.isEmpty() ? "Untitled Note" : note.title)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: // Unarchive
                        repository.unarchiveNote(note.id);
                        refreshArchive();
                        Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show();
                        break;
                    case 1: // Move to Trash
                        repository.trashNote(note.id);
                        refreshArchive();
                        Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show();
                        break;
                    case 2: // Delete Permanently
                        confirmPermanentDelete(note);
                        break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmPermanentDelete(Note note) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Permanently")
            .setMessage("This action cannot be undone. Are you sure?")
            .setPositiveButton("Delete", (d, w) -> {
                repository.deleteNotePermanently(note.id);
                refreshArchive();
                Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
