package com.prajwal.myfirstapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * NotesActivity — Notion-inspired notes with folder navigation and PC sync.
 *
 * Features:
 *  - Hierarchical folder/note tree (stored as JSON)
 *  - Folder navigation with breadcrumb
 *  - Search across all notes
 *  - Create / Rename / Delete / Pin notes & folders
 *  - Bidirectional sync with PC via UDP commands
 */
public class NotesActivity extends AppCompatActivity {

    private static final String TAG = "NotesActivity";
    private static final String PREFS_NAME = "notes_prefs";
    private static final String NOTES_KEY = "notes_json";

    // views
    private ListView notesListView;
    private EditText etSearchNotes, etNoteTitle, etNoteContent;
    private TextView tvNoteCount, tvBreadcrumb, tvWordCount, tvNotesTitle;
    private View editorView, breadcrumbBar;
    private Button btnBackNotes, btnSyncNotes, btnNavBack, btnSaveNote;
    private FloatingActionButton fabAddNote;

    // data
    private JSONObject rootTree;                 // full notes tree
    private JSONArray currentItems;              // items in current folder
    private List<String> folderPath = new ArrayList<>();  // breadcrumb path
    private String editingNoteId = null;

    // adapter data
    private List<JSONObject> displayList = new ArrayList<>();
    private NoteAdapter noteAdapter;

    // network
    private ConnectionManager connectionManager;
    private String serverIp;

    // Singleton for receiving sync commands from ReverseCommandListener
    private static NotesActivity instance;
    public static NotesActivity getInstance() { return instance; }

    // ───────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ───────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        instance = this;

        serverIp = getIntent().getStringExtra("server_ip");
        if (serverIp == null) serverIp = "10.190.76.54";
        connectionManager = new ConnectionManager(serverIp);

        initViews();
        loadNotes();
        navigateToFolder(null);  // show root

        // Request sync from PC on open
        connectionManager.sendCommand("NOTE_SYNC");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    // ───────────────────────────────────────────────────────────────
    //  VIEW INIT
    // ───────────────────────────────────────────────────────────────

    private void initViews() {
        notesListView   = findViewById(R.id.notesListView);
        etSearchNotes   = findViewById(R.id.etSearchNotes);
        etNoteTitle     = findViewById(R.id.etNoteTitle);
        etNoteContent   = findViewById(R.id.etNoteContent);
        tvNoteCount     = findViewById(R.id.tvNoteCount);
        tvBreadcrumb    = findViewById(R.id.tvBreadcrumb);
        tvWordCount     = findViewById(R.id.tvWordCount);
        tvNotesTitle    = findViewById(R.id.tvNotesTitle);
        editorView      = findViewById(R.id.editorView);
        breadcrumbBar   = findViewById(R.id.breadcrumbBar);
        btnBackNotes    = findViewById(R.id.btnBackNotes);
        btnSyncNotes    = findViewById(R.id.btnSyncNotes);
        btnNavBack      = findViewById(R.id.btnNavBack);
        btnSaveNote     = findViewById(R.id.btnSaveNote);
        fabAddNote      = findViewById(R.id.fabAddNote);

        noteAdapter = new NoteAdapter();
        notesListView.setAdapter(noteAdapter);

        // Back to home
        btnBackNotes.setOnClickListener(v -> finish());

        // Sync
        btnSyncNotes.setOnClickListener(v -> {
            connectionManager.sendCommand("NOTE_SYNC");
            Toast.makeText(this, "Syncing notes...", Toast.LENGTH_SHORT).show();
        });

        // Navigate up in folder tree
        btnNavBack.setOnClickListener(v -> navigateUp());

        // FAB — show add options
        fabAddNote.setOnClickListener(v -> showAddDialog());

        // Save note
        btnSaveNote.setOnClickListener(v -> saveCurrentNote());

        // Search
        etSearchNotes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterNotes(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Word count while editing
        etNoteContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                int words = text.isEmpty() ? 0 : text.split("\\s+").length;
                tvWordCount.setText(words + " words");
            }
        });

        // Item click — open note or enter folder
        notesListView.setOnItemClickListener((parent, view, pos, id) -> {
            JSONObject item = displayList.get(pos);
            String type = item.optString("type", "note");
            if (type.equals("folder")) {
                folderPath.add(item.optString("name", "Folder"));
                navigateToFolder(item.optString("id"));
            } else {
                openNoteEditor(item);
            }
        });

        // Long click — context menu (rename, delete, pin)
        notesListView.setOnItemLongClickListener((parent, view, pos, id) -> {
            showItemContextMenu(displayList.get(pos));
            return true;
        });
    }

    // ───────────────────────────────────────────────────────────────
    //  NAVIGATION
    // ───────────────────────────────────────────────────────────────

    private void navigateToFolder(String folderId) {
        showListView();
        currentItems = getItemsForFolder(folderId);
        btnNavBack.setVisibility(folderPath.isEmpty() ? View.GONE : View.VISIBLE);
        updateBreadcrumb();
        refreshList();
    }

    private void navigateUp() {
        if (!folderPath.isEmpty()) {
            folderPath.remove(folderPath.size() - 1);
        }
        // Re-derive folder id from path — walk from root
        String folderId = resolveCurrentFolderId();
        navigateToFolder(folderId);
    }

    private String resolveCurrentFolderId() {
        if (folderPath.isEmpty()) return null;
        try {
            JSONArray items = rootTree.optJSONArray("items");
            if (items == null) return null;
            JSONArray current = items;
            String lastId = null;
            for (String name : folderPath) {
                for (int i = 0; i < current.length(); i++) {
                    JSONObject obj = current.getJSONObject(i);
                    if (obj.optString("name").equals(name) && obj.optString("type").equals("folder")) {
                        lastId = obj.optString("id");
                        current = obj.optJSONArray("children");
                        if (current == null) current = new JSONArray();
                        break;
                    }
                }
            }
            return lastId;
        } catch (JSONException e) {
            return null;
        }
    }

    private JSONArray getItemsForFolder(String folderId) {
        try {
            if (folderId == null) {
                return rootTree.optJSONArray("items");
            }
            return findFolderChildren(rootTree.optJSONArray("items"), folderId);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONArray findFolderChildren(JSONArray items, String folderId) throws JSONException {
        if (items == null) return new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.getJSONObject(i);
            if (obj.optString("id").equals(folderId)) {
                JSONArray children = obj.optJSONArray("children");
                return children != null ? children : new JSONArray();
            }
            if (obj.optString("type").equals("folder")) {
                JSONArray children = obj.optJSONArray("children");
                JSONArray result = findFolderChildren(children, folderId);
                if (result != null && result.length() > 0) return result;
            }
        }
        return new JSONArray();
    }

    private void updateBreadcrumb() {
        StringBuilder sb = new StringBuilder("📚 Notes");
        for (String p : folderPath) {
            sb.append("  ›  ").append(p);
        }
        tvBreadcrumb.setText(sb.toString());
    }

    // ───────────────────────────────────────────────────────────────
    //  LIST DISPLAY
    // ───────────────────────────────────────────────────────────────

    private void refreshList() {
        displayList.clear();
        if (currentItems == null) {
            noteAdapter.notifyDataSetChanged();
            tvNoteCount.setText("0 notes");
            return;
        }

        // Sort: pinned first, then folders, then notes, then by modified desc
        List<JSONObject> all = new ArrayList<>();
        for (int i = 0; i < currentItems.length(); i++) {
            try { all.add(currentItems.getJSONObject(i)); } catch (JSONException e) {}
        }

        Collections.sort(all, (a, b) -> {
            boolean pinA = a.optBoolean("pinned", false);
            boolean pinB = b.optBoolean("pinned", false);
            if (pinA != pinB) return pinA ? -1 : 1;

            String typeA = a.optString("type", "note");
            String typeB = b.optString("type", "note");
            if (!typeA.equals(typeB)) return typeA.equals("folder") ? -1 : 1;

            String modA = a.optString("modified", "");
            String modB = b.optString("modified", "");
            return modB.compareTo(modA);
        });

        displayList.addAll(all);
        noteAdapter.notifyDataSetChanged();
        tvNoteCount.setText(displayList.size() + " items");
    }

    private void filterNotes(String query) {
        if (query.isEmpty()) {
            refreshList();
            return;
        }
        displayList.clear();
        String q = query.toLowerCase();
        if (currentItems == null) return;
        for (int i = 0; i < currentItems.length(); i++) {
            try {
                JSONObject obj = currentItems.getJSONObject(i);
                String name = obj.optString("name", "").toLowerCase();
                String content = obj.optString("content", "").toLowerCase();
                if (name.contains(q) || content.contains(q)) {
                    displayList.add(obj);
                }
            } catch (JSONException e) {}
        }
        noteAdapter.notifyDataSetChanged();
    }

    // ───────────────────────────────────────────────────────────────
    //  EDITOR
    // ───────────────────────────────────────────────────────────────

    private void openNoteEditor(JSONObject note) {
        editingNoteId = note.optString("id");
        etNoteTitle.setText(note.optString("name", ""));
        etNoteContent.setText(note.optString("content", ""));
        showEditorView();
    }

    private void saveCurrentNote() {
        if (editingNoteId == null) return;

        String newTitle = etNoteTitle.getText().toString().trim();
        String newContent = etNoteContent.getText().toString();

        if (newTitle.isEmpty()) newTitle = "Untitled";

        // Update in tree
        updateNoteInTree(rootTree.optJSONArray("items"), editingNoteId, newTitle, newContent);
        saveNotes();

        // Send to PC
        String safeTitle = newTitle.replace(":", "_");
        String safeContent = newContent.replace("\n", "\\n").replace(":", "\\:");
        connectionManager.sendCommand("NOTE_UPDATE:" + editingNoteId + ":" + safeTitle + ":" + safeContent);

        Toast.makeText(this, "Saved & synced ✨", Toast.LENGTH_SHORT).show();
        showListView();
        navigateToFolder(resolveCurrentFolderId());
    }

    private boolean updateNoteInTree(JSONArray items, String noteId, String name, String content) {
        if (items == null) return false;
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                if (obj.optString("id").equals(noteId)) {
                    obj.put("name", name);
                    obj.put("content", content);
                    obj.put("modified", getCurrentTimestamp());
                    return true;
                }
                if (obj.optString("type").equals("folder")) {
                    if (updateNoteInTree(obj.optJSONArray("children"), noteId, name, content))
                        return true;
                }
            }
        } catch (JSONException e) {}
        return false;
    }

    // ───────────────────────────────────────────────────────────────
    //  CRUD
    // ───────────────────────────────────────────────────────────────

    private void showAddDialog() {
        String[] options = {"📝 New Note", "📁 New Folder"};
        new AlertDialog.Builder(this)
            .setTitle("Create")
            .setItems(options, (d, which) -> {
                if (which == 0) addNote();
                else addFolder();
            })
            .show();
    }

    private void addNote() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("New Note");
        final EditText input = new EditText(this);
        input.setHint("Note name");
        b.setView(input);
        b.setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Untitled";

            String id = "note_mob_" + System.currentTimeMillis();
            try {
                JSONObject note = new JSONObject();
                note.put("id", id);
                note.put("name", name);
                note.put("type", "note");
                note.put("content", "");
                note.put("pinned", false);
                note.put("color", "default");
                note.put("created", getCurrentTimestamp());
                note.put("modified", getCurrentTimestamp());

                currentItems.put(note);
                saveNotes();
                refreshList();

                // Send to PC
                connectionManager.sendCommand("NOTE_ADD:" + id + ":" + name.replace(":", "_"));
            } catch (JSONException e) { e.printStackTrace(); }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void addFolder() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("New Folder");
        final EditText input = new EditText(this);
        input.setHint("Folder name");
        b.setView(input);
        b.setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "New Folder";

            String id = "folder_mob_" + System.currentTimeMillis();
            try {
                JSONObject folder = new JSONObject();
                folder.put("id", id);
                folder.put("name", name);
                folder.put("type", "folder");
                folder.put("children", new JSONArray());
                folder.put("created", getCurrentTimestamp());
                folder.put("modified", getCurrentTimestamp());

                currentItems.put(folder);
                saveNotes();
                refreshList();

                connectionManager.sendCommand("NOTE_ADD:" + id + ":" + name.replace(":", "_"));
            } catch (JSONException e) { e.printStackTrace(); }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showItemContextMenu(JSONObject item) {
        String name = item.optString("name", "Item");
        boolean isPinned = item.optBoolean("pinned", false);
        String pinLabel = isPinned ? "Unpin" : "📌 Pin to top";

        String[] options = {pinLabel, "✏️ Rename", "🗑️ Delete"};
        new AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options, (d, which) -> {
                String itemId = item.optString("id");
                switch (which) {
                    case 0: // Toggle pin
                        try {
                            item.put("pinned", !isPinned);
                            saveNotes();
                            refreshList();
                        } catch (JSONException e) {}
                        break;
                    case 1: // Rename
                        showRenameDialog(item);
                        break;
                    case 2: // Delete
                        new AlertDialog.Builder(this)
                            .setTitle("Delete \"" + name + "\"?")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete", (dd, ww) -> {
                                removeItemFromTree(rootTree.optJSONArray("items"), itemId);
                                saveNotes();
                                navigateToFolder(resolveCurrentFolderId());
                                connectionManager.sendCommand("NOTE_DELETE:" + itemId);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        break;
                }
            })
            .show();
    }

    private void showRenameDialog(JSONObject item) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Rename");
        final EditText input = new EditText(this);
        input.setText(item.optString("name", ""));
        b.setView(input);
        b.setPositiveButton("Rename", (d, w) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                try {
                    item.put("name", newName);
                    item.put("modified", getCurrentTimestamp());
                    saveNotes();
                    refreshList();
                    connectionManager.sendCommand("NOTE_RENAME:" + item.optString("id") + ":" + newName.replace(":", "_"));
                } catch (JSONException e) {}
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private boolean removeItemFromTree(JSONArray items, String targetId) {
        if (items == null) return false;
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.getJSONObject(i);
                if (obj.optString("id").equals(targetId)) {
                    items.remove(i);
                    return true;
                }
                if (obj.optString("type").equals("folder")) {
                    if (removeItemFromTree(obj.optJSONArray("children"), targetId))
                        return true;
                }
            }
        } catch (JSONException e) {}
        return false;
    }

    // ───────────────────────────────────────────────────────────────
    //  SYNC FROM PC (called by ReverseCommandListener)
    // ───────────────────────────────────────────────────────────────

    /**
     * Called when the PC sends a full notes sync.
     * Replaces local tree with the PC version.
     */
    public void onNotesSyncReceived(String jsonStr) {
        runOnUiThread(() -> {
            try {
                rootTree = new JSONObject(jsonStr);
                saveNotes();
                folderPath.clear();
                navigateToFolder(null);
                Toast.makeText(this, "Notes synced! ✅", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse notes sync: " + e.getMessage());
            }
        });
    }

    /**
     * Called when PC notifies about a single note event (added/updated/deleted).
     */
    public void onNoteEventReceived(String eventType, String data) {
        runOnUiThread(() -> {
            switch (eventType) {
                case "ADDED":
                case "UPDATED":
                    // Request full sync to get the latest state
                    connectionManager.sendCommand("NOTE_SYNC");
                    break;
                case "DELETED":
                    // Remove locally
                    String noteId = data;
                    removeItemFromTree(rootTree.optJSONArray("items"), noteId);
                    saveNotes();
                    navigateToFolder(resolveCurrentFolderId());
                    break;
            }
        });
    }

    // ───────────────────────────────────────────────────────────────
    //  PERSISTENCE
    // ───────────────────────────────────────────────────────────────

    private void loadNotes() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(NOTES_KEY, null);
        if (json != null) {
            try {
                rootTree = new JSONObject(json);
                return;
            } catch (JSONException e) {
                Log.e(TAG, "Failed to load notes: " + e.getMessage());
            }
        }
        // Default empty tree
        try {
            rootTree = new JSONObject();
            rootTree.put("items", new JSONArray());
        } catch (JSONException e) {}
    }

    private void saveNotes() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(NOTES_KEY, rootTree.toString()).apply();
    }

    // ───────────────────────────────────────────────────────────────
    //  VIEW TOGGLING
    // ───────────────────────────────────────────────────────────────

    private void showListView() {
        notesListView.setVisibility(View.VISIBLE);
        etSearchNotes.setVisibility(View.VISIBLE);
        fabAddNote.setVisibility(View.VISIBLE);
        editorView.setVisibility(View.GONE);
        editingNoteId = null;
    }

    private void showEditorView() {
        notesListView.setVisibility(View.GONE);
        etSearchNotes.setVisibility(View.GONE);
        fabAddNote.setVisibility(View.GONE);
        editorView.setVisibility(View.VISIBLE);
    }

    // ───────────────────────────────────────────────────────────────
    //  HELPERS
    // ───────────────────────────────────────────────────────────────

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    @Override
    public void onBackPressed() {
        if (editorView.getVisibility() == View.VISIBLE) {
            // Back from editor → list
            showListView();
        } else if (!folderPath.isEmpty()) {
            // Navigate up in folder hierarchy
            navigateUp();
        } else {
            super.onBackPressed();
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  ADAPTER
    // ───────────────────────────────────────────────────────────────

    private class NoteAdapter extends BaseAdapter {
        @Override public int getCount() { return displayList.size(); }
        @Override public Object getItem(int pos) { return displayList.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = getLayoutInflater().inflate(R.layout.item_note, parent, false);
            }

            JSONObject item = displayList.get(pos);
            String type = item.optString("type", "note");
            String name = item.optString("name", "Untitled");
            boolean pinned = item.optBoolean("pinned", false);
            String modified = item.optString("modified", "");

            // Icon
            TextView tvIcon = cv.findViewById(R.id.tvNoteIcon);
            tvIcon.setText(type.equals("folder") ? "📁" : "📝");

            // Name
            TextView tvName = cv.findViewById(R.id.tvNoteName);
            tvName.setText(name);

            // Pin badge
            TextView tvPin = cv.findViewById(R.id.tvPinBadge);
            tvPin.setVisibility(pinned ? View.VISIBLE : View.GONE);

            // Timestamp
            TextView tvTime = cv.findViewById(R.id.tvNoteTimestamp);
            if (!modified.isEmpty() && modified.length() >= 10) {
                tvTime.setText(modified.substring(5, 10)); // show MM-DD
            } else {
                tvTime.setText("");
            }

            // Arrow for folders
            TextView tvArrow = cv.findViewById(R.id.tvArrow);
            tvArrow.setVisibility(type.equals("folder") ? View.VISIBLE : View.GONE);

            return cv;
        }
    }
}
