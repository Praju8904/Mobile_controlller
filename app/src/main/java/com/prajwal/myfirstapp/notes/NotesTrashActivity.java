package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
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
 * NotesTrashActivity — Displays trashed notes with restore and permanent delete options.
 * Notes in trash are automatically deleted after 30 days.
 */
public class NotesTrashActivity extends AppCompatActivity implements NotesAdapter.OnNoteActionListener {

    private NoteRepository repository;
    private RecyclerView trashRecyclerView;
    private NotesAdapter adapter;
    private LinearLayout emptyStateContainer;
    private TextView tvTrashCount;
    private ImageButton btnEmptyTrash;

    private ArrayList<Note> trashedNotes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_trash);

        repository = new NoteRepository(this);

        initViews();
        setupRecyclerView();
        refreshTrash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTrash();
    }

    private void initViews() {
        trashRecyclerView = findViewById(R.id.trashRecyclerView);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvTrashCount = findViewById(R.id.tvTrashCount);
        btnEmptyTrash = findViewById(R.id.btnEmptyTrash);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        btnEmptyTrash.setOnClickListener(v -> confirmEmptyTrash());
    }

    private void setupRecyclerView() {
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(
            2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        trashRecyclerView.setLayoutManager(layoutManager);

        adapter = new NotesAdapter(this, trashedNotes, this, true);
        adapter.setTrashMode(true);
        trashRecyclerView.setAdapter(adapter);
    }

    private void refreshTrash() {
        trashedNotes.clear();
        trashedNotes.addAll(repository.getTrashedNotes());
        adapter.notifyDataSetChanged();

        // Update count with animation
        int count = trashedNotes.size();
        String countText = count + (count == 1 ? " note" : " notes");
        tvTrashCount.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(100)
            .withEndAction(() -> {
                tvTrashCount.setText(countText);
                tvTrashCount.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start();
            })
            .start();

        // Show/hide empty state and empty trash button
        boolean isEmpty = trashedNotes.isEmpty();
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        trashRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        btnEmptyTrash.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
        // Multi-select not enabled in trash view
    }

    private void showNoteOptions(Note note) {
        // Calculate days until deletion
        long daysLeft = 30;
        if (note.deletedAt > 0) {
            daysLeft = 30 - ((System.currentTimeMillis() - note.deletedAt) / (24 * 60 * 60 * 1000));
            if (daysLeft < 0) daysLeft = 0;
        }

        String[] options = {"♻️ Restore", "🗑️ Delete Permanently"};

        new AlertDialog.Builder(this)
            .setTitle(note.title.isEmpty() ? "Untitled Note" : note.title)
            .setMessage("Will be permanently deleted in " + daysLeft + " days")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: // Restore
                        repository.restoreFromTrash(note.id);
                        refreshTrash();
                        Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show();
                        break;
                    case 1: // Delete Permanently
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
                refreshTrash();
                Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmEmptyTrash() {
        new AlertDialog.Builder(this)
            .setTitle("Empty Trash")
            .setMessage("Permanently delete all " + trashedNotes.size() + " notes in trash? This action cannot be undone.")
            .setPositiveButton("Empty Trash", (d, w) -> {
                repository.emptyTrash();
                refreshTrash();
                Toast.makeText(this, "Trash emptied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
