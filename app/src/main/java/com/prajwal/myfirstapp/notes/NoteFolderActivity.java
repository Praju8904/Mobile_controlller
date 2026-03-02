package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * NoteFolderActivity — shown when navigating into a specific folder.
 * Shows breadcrumb nav, subfolders strip, and notes grid.
 */
public class NoteFolderActivity extends AppCompatActivity implements NotesAdapter.OnNoteActionListener {

    public static final String EXTRA_FOLDER_ID = "folder_id";

    private NoteRepository noteRepository;
    private NoteFolderRepository folderRepository;
    private String currentFolderId;
    private NoteFolder currentFolder;

    // Views
    private LinearLayout headerContainer;
    private TextView tvFolderIcon, tvFolderName, tvFolderNoteCount;
    private LinearLayout breadcrumbContainer;
    private HorizontalScrollView subfoldersScrollView;
    private LinearLayout subfoldersContainer;
    private LinearLayout subfoldersSection;
    private RecyclerView notesRecyclerView;
    private NotesAdapter notesAdapter;
    private ArrayList<Note> displayedNotes = new ArrayList<>();
    private LinearLayout emptyStateContainer;
    private TextView tvEmptyTitle, tvEmptyMsg, tvEmptyIcon;
    private FloatingActionButton fabNewNote;
    private ImageButton btnBack;

    // Speed dial
    private View folderSpeedDialOverlay;
    private LinearLayout folderSpeedDialMenu;
    private LinearLayout fabOptionNewNote, fabOptionNewSubfolder, fabOptionImport;
    private boolean isSpeedDialOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw behind status bar — eliminate wasted black space at top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_note_folder);

        currentFolderId = getIntent().getStringExtra(EXTRA_FOLDER_ID);

        noteRepository = new NoteRepository(this);
        folderRepository = new NoteFolderRepository(this, noteRepository);
        currentFolder = folderRepository.getFolderById(currentFolderId);

        if (currentFolder == null) {
            Toast.makeText(this, "Folder not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupHeader();
        setupBreadcrumb();
        setupSubfolders();
        setupNotesGrid();
        setupFab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        noteRepository.reload();
        currentFolder = folderRepository.getFolderById(currentFolderId);
        if (currentFolder != null) {
            refreshAll();
        }
    }

    private void initViews() {
        headerContainer = findViewById(R.id.folderHeaderContainer);
        tvFolderIcon = findViewById(R.id.tvFolderIcon);
        tvFolderName = findViewById(R.id.tvFolderName);
        tvFolderNoteCount = findViewById(R.id.tvFolderNoteCount);
        breadcrumbContainer = findViewById(R.id.breadcrumbContainer);
        subfoldersScrollView = findViewById(R.id.subfoldersScrollView);
        subfoldersContainer = findViewById(R.id.subfoldersContainer);
        subfoldersSection = findViewById(R.id.subfoldersSection);
        notesRecyclerView = findViewById(R.id.folderNotesRecyclerView);
        emptyStateContainer = findViewById(R.id.folderEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMsg = findViewById(R.id.tvEmptyMsg);
        tvEmptyIcon = findViewById(R.id.tvEmptyIcon);
        fabNewNote = findViewById(R.id.fabNewNoteInFolder);
        btnBack = findViewById(R.id.btnBackFolder);
        folderSpeedDialOverlay = findViewById(R.id.folderSpeedDialOverlay);
        folderSpeedDialMenu = findViewById(R.id.folderSpeedDialMenu);
        fabOptionNewNote = findViewById(R.id.fabOptionNewNote);
        fabOptionNewSubfolder = findViewById(R.id.fabOptionNewSubfolder);
        fabOptionImport = findViewById(R.id.fabOptionImport);
    }

    private void setupHeader() {
        btnBack.setOnClickListener(v -> finish());

        // Use a subtle dark header tinted with the folder's color (not a harsh gradient)
        int folderColor = currentFolder.getColorInt();
        int darkBase = 0xFF0F172A;
        // Blend folder color at 15% into dark background — subtle tint
        int r = (int) (Color.red(darkBase) * 0.85f + Color.red(folderColor) * 0.15f);
        int g = (int) (Color.green(darkBase) * 0.85f + Color.green(folderColor) * 0.15f);
        int b = (int) (Color.blue(darkBase) * 0.85f + Color.blue(folderColor) * 0.15f);
        int subtleColor = Color.rgb(r, g, b);
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{subtleColor, 0xFF0F172A});
        gradient.setCornerRadius(0);
        headerContainer.setBackground(gradient);

        tvFolderIcon.setText(currentFolder.getIconEmoji());
        tvFolderName.setText(currentFolder.name);
        updateNoteCount();
    }

    private void updateNoteCount() {
        int count = folderRepository.getNoteCountRecursive(currentFolderId);
        List<NoteFolder> subs = folderRepository.getSubfolders(currentFolderId);
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(count == 1 ? " note" : " notes");
        if (!subs.isEmpty()) {
            sb.append(" · ").append(subs.size()).append(subs.size() == 1 ? " subfolder" : " subfolders");
        }
        tvFolderNoteCount.setText(sb.toString());
    }

    private void setupBreadcrumb() {
        breadcrumbContainer.removeAllViews();
        List<NoteFolder> path = folderRepository.getFolderPath(currentFolderId);

        // Add "All Notes" root item
        addBreadcrumbItem("All Notes", null, true);

        for (int i = 0; i < path.size(); i++) {
            NoteFolder folder = path.get(i);
            boolean isLast = (i == path.size() - 1);

            // Add separator
            TextView separator = new TextView(this);
            separator.setText(" › ");
            separator.setTextColor(isLast ? 0xFFFFFFFF : 0x99FFFFFF);
            separator.setTextSize(14);
            breadcrumbContainer.addView(separator);

            addBreadcrumbItem(folder.name, folder.id, isLast);
        }
    }

    private void addBreadcrumbItem(String name, String folderId, boolean isCurrentLevel) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(13);
        tv.setTextColor(isCurrentLevel ? 0xFFFFFFFF : 0x99FFFFFF);
        if (!isCurrentLevel) {
            tv.setOnClickListener(v -> {
                if (folderId == null) {
                    // Navigate back to NotesActivity
                    Intent intent = new Intent(this, NotesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                } else if (!folderId.equals(currentFolderId)) {
                    // Navigate to that folder
                    Intent intent = new Intent(this, NoteFolderActivity.class);
                    intent.putExtra(EXTRA_FOLDER_ID, folderId);
                    startActivity(intent);
                }
            });
        }
        breadcrumbContainer.addView(tv);
    }

    private void setupSubfolders() {
        List<NoteFolder> subfolders = folderRepository.getSubfolders(currentFolderId);
        if (subfolders.isEmpty()) {
            subfoldersSection.setVisibility(View.GONE);
            return;
        }

        subfoldersSection.setVisibility(View.VISIBLE);
        subfoldersContainer.removeAllViews();

        for (NoteFolder subfolder : subfolders) {
            View card = createSubfolderCard(subfolder);
            subfoldersContainer.addView(card);
        }

        // Add "New Subfolder" button
        View addBtn = createAddSubfolderButton();
        subfoldersContainer.addView(addBtn);
    }

    private View createSubfolderCard(NoteFolder subfolder) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(16, 16, 16, 16);

        int cardWidth = (int) (getResources().getDisplayMetrics().density * 100);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(12);
        card.setLayoutParams(params);

        // Gradient background
        int startColor = subfolder.getColorInt();
        int endColor = subfolder.getGradientColorInt();
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{
                blendWithDark(startColor, 0.3f), blendWithDark(endColor, 0.3f)});
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        card.setBackground(bg);

        TextView iconView = new TextView(this);
        iconView.setText(subfolder.getIconEmoji());
        iconView.setTextSize(28);
        iconView.setGravity(android.view.Gravity.CENTER);
        card.addView(iconView);

        TextView nameView = new TextView(this);
        nameView.setText(subfolder.name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(12);
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setPadding(0, 8, 0, 0);
        card.addView(nameView);

        int count = folderRepository.getNoteCountRecursive(subfolder.id);
        List<NoteFolder> nestedSubs = folderRepository.getSubfolders(subfolder.id);
        TextView countView = new TextView(this);
        String countText = count + (count == 1 ? " note" : " notes");
        if (!nestedSubs.isEmpty()) {
            countText += " · ↳ " + nestedSubs.size();
        }
        countView.setText(countText);
        countView.setTextColor(0xCCFFFFFF);
        countView.setTextSize(11);
        countView.setGravity(android.view.Gravity.CENTER);
        card.addView(countView);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteFolderActivity.class);
            intent.putExtra(EXTRA_FOLDER_ID, subfolder.id);
            startActivity(intent);
        });

        card.setOnLongClickListener(v -> {
            showFolderContextMenu(subfolder);
            return true;
        });

        return card;
    }

    private View createAddSubfolderButton() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(16, 16, 16, 16);

        int cardWidth = (int) (getResources().getDisplayMetrics().density * 100);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1AFFFFFF);
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        bg.setStroke(1, 0x33FFFFFF);
        card.setBackground(bg);

        TextView iconView = new TextView(this);
        iconView.setText("➕");
        iconView.setTextSize(28);
        iconView.setGravity(android.view.Gravity.CENTER);
        card.addView(iconView);

        TextView nameView = new TextView(this);
        nameView.setText("New Subfolder");
        nameView.setTextColor(0x99FFFFFF);
        nameView.setTextSize(11);
        nameView.setGravity(android.view.Gravity.CENTER);
        nameView.setPadding(0, 8, 0, 0);
        card.addView(nameView);

        card.setOnClickListener(v -> showCreateSubfolderDialog());
        return card;
    }

    private void showCreateSubfolderDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkAlertDialog);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_create_subfolder, null);
        dialog.setContentView(sheetView);

        // Make bottom sheet background transparent so our custom bg shows
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        EditText etName = sheetView.findViewById(R.id.etSubfolderName);
        LinearLayout colorGrid = sheetView.findViewById(R.id.colorPickerGrid);
        LinearLayout iconGrid = sheetView.findViewById(R.id.iconPickerGrid);
        LinearLayout folderPreview = sheetView.findViewById(R.id.folderPreview);
        TextView tvPreviewIcon = sheetView.findViewById(R.id.tvPreviewIcon);
        TextView btnCancel = sheetView.findViewById(R.id.btnCancelSubfolder);
        TextView btnSave = sheetView.findViewById(R.id.btnSaveSubfolder);

        tvTitle.setText("New Subfolder in " + currentFolder.name);

        // State
        final String[] selectedColor = {currentFolder.colorHex};
        final String[] selectedIcon = {"folder"};
        final int[] selectedColorIdx = {0};
        final int[] selectedIconIdx = {25}; // default folder

        // Update preview
        Runnable updatePreview = () -> {
            String emoji = "📁";
            for (int i = 0; i < NoteFolder.FOLDER_ICONS.length; i++) {
                if (NoteFolder.FOLDER_ICONS[i].equals(selectedIcon[0])) {
                    emoji = NoteFolder.FOLDER_ICON_EMOJIS[i];
                    break;
                }
            }
            tvPreviewIcon.setText(emoji);
            int color = Note.parseColorSafe(selectedColor[0]);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(blendWithDark(color, 0.35f));
            bg.setCornerRadius(14 * getResources().getDisplayMetrics().density);
            bg.setStroke(2, color);
            folderPreview.setBackground(bg);
        };

        // Build color picker grid (3 rows x 4 cols)
        float density = getResources().getDisplayMetrics().density;
        final View[] colorViews = new View[NoteFolder.FOLDER_COLORS.length];
        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rowParams);

            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= NoteFolder.FOLDER_COLORS.length) break;

                FrameLayout colorItem = new FrameLayout(this);
                int size = (int)(42 * density);
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, size, 1);
                itemParams.setMargins((int)(4 * density), 0, (int)(4 * density), 0);
                colorItem.setLayoutParams(itemParams);

                int colorVal = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[idx]);
                GradientDrawable colorBg = new GradientDrawable();
                colorBg.setShape(GradientDrawable.OVAL);
                colorBg.setColor(colorVal);
                if (idx == selectedColorIdx[0]) {
                    colorBg.setStroke((int)(3 * density), 0xFFFFFFFF);
                }
                colorItem.setBackground(colorBg);
                colorViews[idx] = colorItem;

                final int fIdx = idx;
                colorItem.setOnClickListener(v -> {
                    selectedColor[0] = NoteFolder.FOLDER_COLORS[fIdx];
                    selectedColorIdx[0] = fIdx;
                    // Update all color borders
                    for (int i = 0; i < colorViews.length; i++) {
                        int c = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[i]);
                        GradientDrawable g = new GradientDrawable();
                        g.setShape(GradientDrawable.OVAL);
                        g.setColor(c);
                        if (i == fIdx) g.setStroke((int)(3 * density), 0xFFFFFFFF);
                        colorViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });

                rowLayout.addView(colorItem);
            }
            colorGrid.addView(rowLayout);
        }

        // Build icon picker (3 rows x 10 cols in scrollable area)
        final View[] iconViews = new View[NoteFolder.FOLDER_ICONS.length];
        int iconsPerRow = 10;
        int totalRows = (NoteFolder.FOLDER_ICONS.length + iconsPerRow - 1) / iconsPerRow;
        for (int row = 0; row < totalRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rowParams);

            for (int col = 0; col < iconsPerRow; col++) {
                int idx = row * iconsPerRow + col;
                if (idx >= NoteFolder.FOLDER_ICONS.length) break;

                TextView iconItem = new TextView(this);
                iconItem.setText(NoteFolder.FOLDER_ICON_EMOJIS[idx]);
                iconItem.setTextSize(24);
                iconItem.setGravity(Gravity.CENTER);
                int itemSize = (int)(44 * density);
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemSize, itemSize);
                itemParams.setMargins((int)(3 * density), 0, (int)(3 * density), 0);
                iconItem.setLayoutParams(itemParams);
                iconItem.setPadding(0, (int)(6 * density), 0, 0);

                GradientDrawable iconBg = new GradientDrawable();
                iconBg.setCornerRadius(12 * density);
                if (idx == selectedIconIdx[0]) {
                    iconBg.setColor(0x33FFFFFF);
                    iconBg.setStroke(1, 0x55FFFFFF);
                } else {
                    iconBg.setColor(0x00000000);
                }
                iconItem.setBackground(iconBg);
                iconViews[idx] = iconItem;

                final int fIdx = idx;
                iconItem.setOnClickListener(v -> {
                    selectedIcon[0] = NoteFolder.FOLDER_ICONS[fIdx];
                    selectedIconIdx[0] = fIdx;
                    for (int i = 0; i < iconViews.length; i++) {
                        GradientDrawable g = new GradientDrawable();
                        g.setCornerRadius(12 * density);
                        if (i == fIdx) {
                            g.setColor(0x33FFFFFF);
                            g.setStroke(1, 0x55FFFFFF);
                        } else {
                            g.setColor(0x00000000);
                        }
                        iconViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });

                rowLayout.addView(iconItem);
            }
            iconGrid.addView(rowLayout);
        }

        updatePreview.run();

        // Focus name field
        etName.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Enter a name");
                return;
            }
            NoteFolder subfolder = new NoteFolder(name, selectedColor[0],
                selectedIcon[0], currentFolderId, currentFolder.depth + 1);
            folderRepository.addFolder(subfolder);
            setupSubfolders();
            updateNoteCount();
            Toast.makeText(this, "Subfolder '" + name + "' created", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showFolderContextMenu(NoteFolder subfolder) {
        String[] options = {"Rename", "Change Color", "Move to...", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(subfolder.getIconEmoji() + " " + subfolder.name)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: showRenameFolderDialog(subfolder); break;
                    case 1: showColorPicker(subfolder); break;
                    case 2: showMoveFolderPicker(subfolder); break;
                    case 3: confirmDeleteFolder(subfolder); break;
                }
            })
            .show();
    }

    private void showRenameFolderDialog(NoteFolder folder) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(folder.name);
        input.setSelection(folder.name.length());
        input.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
            .setTitle("Rename Folder")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    folder.name = newName;
                    folderRepository.updateFolder(folder);
                    setupSubfolders();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showColorPicker(NoteFolder folder) {
        // Enhanced color picker with visual color circles
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        float density = getResources().getDisplayMetrics().density;
        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int)(12 * density);
            rowLayout.setLayoutParams(rowParams);

            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= NoteFolder.FOLDER_COLORS.length) break;

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                item.setLayoutParams(itemParams);

                View colorDot = new View(this);
                int dotSize = (int)(40 * density);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
                colorDot.setLayoutParams(dotParams);
                int colorVal = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[idx]);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(colorVal);
                if (NoteFolder.FOLDER_COLORS[idx].equalsIgnoreCase(folder.colorHex)) {
                    bg.setStroke((int)(3 * density), 0xFFFFFFFF);
                }
                colorDot.setBackground(bg);
                item.addView(colorDot);

                TextView label = new TextView(this);
                label.setText(NoteFolder.FOLDER_COLOR_NAMES[idx]);
                label.setTextColor(0xFF94A3B8);
                label.setTextSize(10);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, (int)(4 * density), 0, 0);
                item.addView(label);

                final int fIdx = idx;
                item.setOnClickListener(v -> {
                    folder.colorHex = NoteFolder.FOLDER_COLORS[fIdx];
                    folderRepository.updateFolder(folder);
                    setupSubfolders();
                });

                rowLayout.addView(item);
            }
            layout.addView(rowLayout);
        }

        new AlertDialog.Builder(this)
            .setTitle("Change Color")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMoveFolderPicker(NoteFolder folder) {
        List<NoteFolder> allFolders = folderRepository.getAllFolders();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        names.add("Root (All Notes)");
        ids.add(""); // empty string = root (null parent)
        for (NoteFolder f : allFolders) {
            if (!f.id.equals(folder.id)) {
                StringBuilder indent = new StringBuilder();
                for (int d = 0; d < f.depth; d++) indent.append("  ");
                names.add(indent + f.getIconEmoji() + " " + f.name);
                ids.add(f.id);
            }
        }

        String[] nameArr = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("Move '" + folder.name + "' to...")
            .setItems(nameArr, (d, which) -> {
                String targetId = ids.get(which);
                // empty string means root
                String newParent = targetId.isEmpty() ? null : targetId;
                boolean success = folderRepository.moveFolder(folder.id, newParent);
                if (!success) {
                    Toast.makeText(this, "Cannot move folder into its own subfolder", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Folder moved", Toast.LENGTH_SHORT).show();
                    setupSubfolders();
                }
            })
            .show();
    }

    private void confirmDeleteFolder(NoteFolder folder) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Folder")
            .setMessage("Delete '" + folder.name + "' and all its subfolders? Notes will be moved to All Notes.")
            .setPositiveButton("Delete", (d, w) -> {
                folderRepository.deleteFolder(folder.id);
                setupSubfolders();
                refreshNotes();
                Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupNotesGrid() {
        notesAdapter = new NotesAdapter(this, displayedNotes, this, true);
        notesRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        notesRecyclerView.setAdapter(notesAdapter);
        notesRecyclerView.setNestedScrollingEnabled(false);
        refreshNotes();
    }

    private void refreshNotes() {
        displayedNotes.clear();
        displayedNotes.addAll(folderRepository.getNotesInFolder(currentFolderId));
        notesAdapter.notifyDataSetChanged();

        boolean isEmpty = displayedNotes.isEmpty() && folderRepository.getSubfolders(currentFolderId).isEmpty();
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        notesRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (isEmpty) {
            setupEmptyState();
        }
    }

    private void setupEmptyState() {
        switch (currentFolder.iconIdentifier) {
            case "graduation":
            case "book":
                tvEmptyIcon.setText("🎓");
                tvEmptyTitle.setText("No study notes yet");
                tvEmptyMsg.setText("Start capturing your lecture notes, assignments, and study materials here.");
                break;
            case "briefcase":
                tvEmptyIcon.setText("💼");
                tvEmptyTitle.setText("No work notes yet");
                tvEmptyMsg.setText("Add meeting notes, project plans, and work ideas here.");
                break;
            case "lightbulb":
                tvEmptyIcon.setText("💡");
                tvEmptyTitle.setText("No ideas yet");
                tvEmptyMsg.setText("Capture your brilliant ideas before they slip away.");
                break;
            case "person":
                tvEmptyIcon.setText("👤");
                tvEmptyTitle.setText("No personal notes yet");
                tvEmptyMsg.setText("Write your thoughts, diary entries, and personal notes here.");
                break;
            default:
                tvEmptyIcon.setText(currentFolder.getIconEmoji());
                tvEmptyTitle.setText("This folder is empty");
                tvEmptyMsg.setText("Tap the + button to create your first note here.");
                break;
        }
    }

    private void setupFab() {
        fabNewNote.setOnClickListener(v -> toggleSpeedDial());

        folderSpeedDialOverlay.setOnClickListener(v -> closeSpeedDial());

        fabOptionNewNote.setOnClickListener(v -> {
            closeSpeedDial();
            Intent intent = new Intent(this, NoteEditorActivity.class);
            intent.putExtra("new_note", true);
            intent.putExtra("folder_id", currentFolderId);
            startActivity(intent);
        });

        fabOptionNewSubfolder.setOnClickListener(v -> {
            closeSpeedDial();
            showCreateSubfolderDialog();
        });

        fabOptionImport.setOnClickListener(v -> {
            closeSpeedDial();
            showMoveNoteFromOtherFolderPicker();
        });
    }

    private void toggleSpeedDial() {
        if (isSpeedDialOpen) {
            closeSpeedDial();
        } else {
            openSpeedDial();
        }
    }

    private void openSpeedDial() {
        isSpeedDialOpen = true;
        folderSpeedDialOverlay.setVisibility(View.VISIBLE);
        folderSpeedDialMenu.setVisibility(View.VISIBLE);
        fabNewNote.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    }

    private void closeSpeedDial() {
        isSpeedDialOpen = false;
        folderSpeedDialOverlay.setVisibility(View.GONE);
        folderSpeedDialMenu.setVisibility(View.GONE);
        fabNewNote.setImageResource(android.R.drawable.ic_input_add);
    }

    private void showMoveNoteFromOtherFolderPicker() {
        // Show all notes NOT in this folder so user can import one
        List<Note> otherNotes = new ArrayList<>();
        for (Note note : noteRepository.getAllNotes()) {
            if (!note.isTrashed && !note.isArchived
                    && !currentFolderId.equals(note.folderId)) {
                otherNotes.add(note);
            }
        }
        if (otherNotes.isEmpty()) {
            Toast.makeText(this, "No notes available to import", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titles = new String[otherNotes.size()];
        for (int i = 0; i < otherNotes.size(); i++) {
            String t = otherNotes.get(i).title;
            titles[i] = (t == null || t.isEmpty()) ? "Untitled" : t;
        }
        new AlertDialog.Builder(this)
            .setTitle("Import note into " + currentFolder.name)
            .setItems(titles, (d, which) -> {
                folderRepository.moveNoteToFolder(otherNotes.get(which).id, currentFolderId);
                refreshNotes();
                Toast.makeText(this, "Note imported", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void refreshAll() {
        setupHeader();
        setupBreadcrumb();
        setupSubfolders();
        refreshNotes();
    }

    private int blendWithDark(int color, float ratio) {
        int dark = 0xFF0F172A;
        float inv = 1f - ratio;
        int r = (int) (Color.red(dark) * inv + Color.red(color) * ratio);
        int g = (int) (Color.green(dark) * inv + Color.green(color) * ratio);
        int b = (int) (Color.blue(dark) * inv + Color.blue(color) * ratio);
        return Color.rgb(r, g, b);
    }

    // ─── NotesAdapter.OnNoteActionListener ─────────────────────────

    @Override
    public void onNoteClick(Note note) {
        folderRepository.recordNoteView(note.id);
        Intent intent = new Intent(this, NoteEditorActivity.class);
        intent.putExtra("note_id", note.id);
        startActivity(intent);
    }

    @Override
    public void onNoteLongClick(Note note) {
        String[] options = {"Pin/Unpin", "Move to Folder", "Archive", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(note.title.isEmpty() ? "Note" : note.title)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        noteRepository.togglePin(note.id);
                        refreshNotes();
                        break;
                    case 1:
                        showMoveNotePicker(note);
                        break;
                    case 2:
                        noteRepository.archiveNote(note.id);
                        refreshNotes();
                        Toast.makeText(this, "Note archived", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        noteRepository.trashNote(note.id);
                        refreshNotes();
                        Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .show();
    }

    @Override
    public void onNoteSelectionChanged(String noteId, boolean selected) {
        // multi-select not implemented in folder view for simplicity
    }

    private void showMoveNotePicker(Note note) {
        List<NoteFolder> allFolders = folderRepository.getAllFolders();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        names.add("All Notes (root)");
        ids.add(""); // empty string = root (null folderId)
        for (NoteFolder f : allFolders) {
            StringBuilder indent = new StringBuilder();
            for (int d = 0; d < f.depth; d++) indent.append("  ");
            names.add(indent + f.getIconEmoji() + " " + f.name);
            ids.add(f.id);
        }

        String[] nameArr = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("Move note to folder")
            .setItems(nameArr, (d, which) -> {
                String targetId = ids.get(which);
                // empty string means root (no folder)
                folderRepository.moveNoteToFolder(note.id, targetId.isEmpty() ? null : targetId);
                refreshNotes();
                Toast.makeText(this, "Note moved", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}
