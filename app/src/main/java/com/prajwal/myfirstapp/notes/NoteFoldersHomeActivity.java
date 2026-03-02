package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * NoteFoldersHomeActivity — Folder home screen with 2-column folder grid,
 * slide-out drawer for full folder tree, and recently viewed notes.
 *
 * This activity is the new default notes home when homeMode = "folders".
 * Users can toggle back to classic NotesActivity (all notes flat grid).
 */
public class NoteFoldersHomeActivity extends AppCompatActivity {

    private NoteRepository noteRepository;
    private NoteFolderRepository folderRepository;

    // Views
    private DrawerLayout drawerLayout;
    private LinearLayout folderGrid;
    private LinearLayout recentlyViewedContainer;
    private LinearLayout recentlyViewedList;
    private ImageButton btnMenuDrawer;
    private ImageButton btnViewToggle;
    private ImageButton btnAddFolder;
    private LinearLayout drawerFolderTree;
    private LinearLayout tvDrawerAllNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw behind status bar — eliminate wasted black space at top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_note_folders_home);

        noteRepository = new NoteRepository(this);
        folderRepository = new NoteFolderRepository(this, noteRepository);

        initViews();
        setupDrawer();
        loadFolderGrid();
        loadRecentlyViewed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        noteRepository.reload();
        loadFolderGrid();
        loadRecentlyViewed();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.foldersDrawerLayout);
        folderGrid = findViewById(R.id.folderHomeGrid);
        recentlyViewedContainer = findViewById(R.id.recentlyViewedContainer);
        recentlyViewedList = findViewById(R.id.recentlyViewedList);
        btnMenuDrawer = findViewById(R.id.btnMenuDrawer);
        btnViewToggle = findViewById(R.id.btnFolderViewToggle);
        btnAddFolder = findViewById(R.id.btnAddFolderHome);
        drawerFolderTree = findViewById(R.id.drawerFolderTree);
        tvDrawerAllNotes = findViewById(R.id.tvDrawerAllNotes);
    }

    private void setupDrawer() {
        btnMenuDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        tvDrawerAllNotes.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(this, NotesActivity.class);
            startActivity(intent);
        });

        btnViewToggle.setOnClickListener(v -> {
            folderRepository.setHomeMode("all");
            Intent intent = new Intent(this, NotesActivity.class);
            startActivity(intent);
            finish();
        });

        btnAddFolder.setOnClickListener(v -> showCreateFolderDialog());
        findViewById(R.id.btnBackFolderHome).setOnClickListener(v -> finish());
    }

    private void loadFolderGrid() {
        folderGrid.removeAllViews();
        List<NoteFolder> rootFolders = folderRepository.getRootFolders();

        // Build 2-column grid manually
        LinearLayout currentRow = null;
        for (int i = 0; i < rootFolders.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setWeightSum(2);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, 12);
                currentRow.setLayoutParams(rowParams);
                folderGrid.addView(currentRow);
            }

            View folderCard = createFolderCard(rootFolders.get(i));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            cardParams.setMargins(i % 2 == 0 ? 0 : 6, 0, i % 2 == 0 ? 6 : 0, 0);
            folderCard.setLayoutParams(cardParams);
            if (currentRow != null) currentRow.addView(folderCard);
        }

        // If odd number, add placeholder
        if (rootFolders.size() % 2 != 0 && currentRow != null) {
            View placeholder = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1);
            placeholder.setLayoutParams(p);
            currentRow.addView(placeholder);
        }

        // Refresh drawer tree
        loadDrawerTree();
    }

    private View createFolderCard(NoteFolder folder) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_folder_card, null);

        TextView tvIcon = card.findViewById(R.id.tvFolderCardIcon);
        TextView tvName = card.findViewById(R.id.tvFolderCardName);
        TextView tvCount = card.findViewById(R.id.tvFolderCardCount);
        TextView tvPreview = card.findViewById(R.id.tvFolderCardPreview);
        TextView tvDate = card.findViewById(R.id.tvFolderCardDate);
        LinearLayout cardRoot = card.findViewById(R.id.folderCardRoot);

        tvIcon.setText(folder.getIconEmoji());
        tvName.setText(folder.name);

        int count = folderRepository.getNoteCountRecursive(folder.id);
        List<NoteFolder> subs = folderRepository.getSubfolders(folder.id);
        String countText = count + (count == 1 ? " note" : " notes");
        if (!subs.isEmpty()) {
            countText += " · ↳ " + subs.size();
        }
        tvCount.setText(countText);

        // Preview note titles
        List<String> previewTitles = folderRepository.getPreviewNoteTitles(folder.id, 2);
        if (!previewTitles.isEmpty()) {
            tvPreview.setText(String.join(", ", previewTitles));
            tvPreview.setVisibility(View.VISIBLE);
        } else {
            tvPreview.setVisibility(View.GONE);
        }

        // Last modified date
        long lastMod = getLastModifiedForFolder(folder.id);
        if (lastMod > 0) {
            tvDate.setText(formatRelativeTime(lastMod));
            tvDate.setVisibility(View.VISIBLE);
        } else {
            tvDate.setVisibility(View.GONE);
        }

        // Apply gradient background
        int startColor = folder.getColorInt();
        int endColor = folder.getGradientColorInt();
        int darkStart = blendWithDark(startColor, 0.25f);
        int darkEnd = blendWithDark(endColor, 0.25f);
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{darkStart, darkEnd});
        bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
        bg.setStroke(1, blendColors(startColor, 0x00000000, 0.5f));
        cardRoot.setBackground(bg);

        // Click - navigate into folder
        card.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                    .withEndAction(() -> {
                        Intent intent = new Intent(this, NoteFolderActivity.class);
                        intent.putExtra(NoteFolderActivity.EXTRA_FOLDER_ID, folder.id);
                        startActivity(intent);
                    }).start())
                .start();
        });

        // Long press - context menu
        card.setOnLongClickListener(v -> {
            showFolderContextMenu(folder);
            return true;
        });

        return card;
    }

    private void loadDrawerTree() {
        drawerFolderTree.removeAllViews();
        List<NoteFolder> rootFolders = folderRepository.getRootFolders();
        for (NoteFolder folder : rootFolders) {
            addDrawerFolderItem(drawerFolderTree, folder, 0);
        }
    }

    private void addDrawerFolderItem(LinearLayout parent, NoteFolder folder, int indent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setPadding(
            (int) (getResources().getDisplayMetrics().density * (16 + indent * 16)),
            12, 16, 12);
        item.setBackgroundResource(android.R.drawable.list_selector_background);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(folder.getIconEmoji());
        tvIcon.setTextSize(18);
        item.addView(tvIcon);

        TextView tvName = new TextView(this);
        tvName.setText("  " + folder.name);
        tvName.setTextColor(0xFFE2E8F0);
        tvName.setTextSize(14);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        item.addView(tvName);

        int count = folderRepository.getNoteCountRecursive(folder.id);
        TextView tvCount = new TextView(this);
        tvCount.setText(String.valueOf(count));
        tvCount.setTextColor(0xFF64748B);
        tvCount.setTextSize(12);
        item.addView(tvCount);

        item.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(this, NoteFolderActivity.class);
            intent.putExtra(NoteFolderActivity.EXTRA_FOLDER_ID, folder.id);
            startActivity(intent);
        });

        parent.addView(item);

        // Add subfolders
        List<NoteFolder> subs = folderRepository.getSubfolders(folder.id);
        for (NoteFolder sub : subs) {
            addDrawerFolderItem(parent, sub, indent + 1);
        }
    }

    private void loadRecentlyViewed() {
        List<Note> recentNotes = folderRepository.getRecentlyViewedNotes(5);
        if (recentNotes.size() < 3) {
            recentlyViewedContainer.setVisibility(View.GONE);
            return;
        }

        recentlyViewedContainer.setVisibility(View.VISIBLE);
        recentlyViewedList.removeAllViews();

        for (Note note : recentNotes) {
            View chip = createRecentNoteChip(note);
            recentlyViewedList.addView(chip);
        }
    }

    private View createRecentNoteChip(Note note) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setPadding(16, 12, 16, 12);

        int chipWidth = (int) (getResources().getDisplayMetrics().density * 130);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(chipWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(12);
        chip.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E293B);
        bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        chip.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(note.title.isEmpty() ? "Untitled" : note.title);
        tvTitle.setTextColor(0xFFE2E8F0);
        tvTitle.setTextSize(13);
        tvTitle.setMaxLines(2);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.addView(tvTitle);

        TextView tvDate = new TextView(this);
        tvDate.setText(note.getFormattedDate());
        tvDate.setTextColor(0xFF64748B);
        tvDate.setTextSize(11);
        tvDate.setPadding(0, 4, 0, 0);
        chip.addView(tvDate);

        chip.setOnClickListener(v -> {
            folderRepository.recordNoteView(note.id);
            Intent intent = new Intent(this, NoteEditorActivity.class);
            intent.putExtra("note_id", note.id);
            startActivity(intent);
        });

        return chip;
    }

    private void showCreateFolderDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.DarkAlertDialog);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_create_subfolder, null);
        dialog.setContentView(sheetView);

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        android.widget.EditText etName = sheetView.findViewById(R.id.etSubfolderName);
        LinearLayout colorGrid = sheetView.findViewById(R.id.colorPickerGrid);
        LinearLayout iconGrid = sheetView.findViewById(R.id.iconPickerGrid);
        LinearLayout folderPreview = sheetView.findViewById(R.id.folderPreview);
        TextView tvPreviewIcon = sheetView.findViewById(R.id.tvPreviewIcon);
        TextView btnCancel = sheetView.findViewById(R.id.btnCancelSubfolder);
        TextView btnSave = sheetView.findViewById(R.id.btnSaveSubfolder);

        tvTitle.setText("New Folder");

        final String[] selectedColor = {NoteFolder.FOLDER_COLORS[0]};
        final String[] selectedIcon = {"folder"};
        final int[] selectedColorIdx = {0};
        final int[] selectedIconIdx = {25};

        float density = getResources().getDisplayMetrics().density;

        Runnable updatePreview = () -> {
            String emoji = "📁";
            for (int i = 0; i < NoteFolder.FOLDER_ICONS.length; i++) {
                if (NoteFolder.FOLDER_ICONS[i].equals(selectedIcon[0])) {
                    emoji = NoteFolder.FOLDER_ICON_EMOJIS[i]; break;
                }
            }
            tvPreviewIcon.setText(emoji);
            int c = Note.parseColorSafe(selectedColor[0]);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(blendWithDark(c, 0.35f));
            bg.setCornerRadius(14 * density);
            bg.setStroke(2, c);
            folderPreview.setBackground(bg);
        };

        // Build color grid (3x4)
        final View[] colorViews = new View[NoteFolder.FOLDER_COLORS.length];
        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rp);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= NoteFolder.FOLDER_COLORS.length) break;
                FrameLayout ci = new FrameLayout(this);
                int sz = (int)(42 * density);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, sz, 1);
                ip.setMargins((int)(4*density), 0, (int)(4*density), 0);
                ci.setLayoutParams(ip);
                int cv = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[idx]);
                GradientDrawable cbg = new GradientDrawable();
                cbg.setShape(GradientDrawable.OVAL);
                cbg.setColor(cv);
                if (idx == 0) cbg.setStroke((int)(3*density), 0xFFFFFFFF);
                ci.setBackground(cbg);
                colorViews[idx] = ci;
                final int fi = idx;
                ci.setOnClickListener(v -> {
                    selectedColor[0] = NoteFolder.FOLDER_COLORS[fi];
                    selectedColorIdx[0] = fi;
                    for (int i = 0; i < colorViews.length; i++) {
                        int cc = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[i]);
                        GradientDrawable g = new GradientDrawable();
                        g.setShape(GradientDrawable.OVAL); g.setColor(cc);
                        if (i == fi) g.setStroke((int)(3*density), 0xFFFFFFFF);
                        colorViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });
                rowLayout.addView(ci);
            }
            colorGrid.addView(rowLayout);
        }

        // Build icon grid
        final View[] iconViews = new View[NoteFolder.FOLDER_ICONS.length];
        int iconsPerRow = 10;
        int totalRows = (NoteFolder.FOLDER_ICONS.length + iconsPerRow - 1) / iconsPerRow;
        for (int row = 0; row < totalRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rp);
            for (int col = 0; col < iconsPerRow; col++) {
                int idx = row * iconsPerRow + col;
                if (idx >= NoteFolder.FOLDER_ICONS.length) break;
                TextView ii = new TextView(this);
                ii.setText(NoteFolder.FOLDER_ICON_EMOJIS[idx]);
                ii.setTextSize(24);
                ii.setGravity(android.view.Gravity.CENTER);
                int is = (int)(44 * density);
                LinearLayout.LayoutParams iip = new LinearLayout.LayoutParams(is, is);
                iip.setMargins((int)(3*density), 0, (int)(3*density), 0);
                ii.setLayoutParams(iip);
                ii.setPadding(0, (int)(6*density), 0, 0);
                GradientDrawable ibg = new GradientDrawable();
                ibg.setCornerRadius(12 * density);
                ibg.setColor(idx == selectedIconIdx[0] ? 0x33FFFFFF : 0x00000000);
                ii.setBackground(ibg);
                iconViews[idx] = ii;
                final int fi = idx;
                ii.setOnClickListener(v -> {
                    selectedIcon[0] = NoteFolder.FOLDER_ICONS[fi];
                    selectedIconIdx[0] = fi;
                    for (int i = 0; i < iconViews.length; i++) {
                        GradientDrawable g = new GradientDrawable();
                        g.setCornerRadius(12 * density);
                        g.setColor(i == fi ? 0x33FFFFFF : 0x00000000);
                        iconViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });
                rowLayout.addView(ii);
            }
            iconGrid.addView(rowLayout);
        }

        updatePreview.run();
        etName.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { etName.setError("Enter a name"); return; }
            NoteFolder folder = new NoteFolder(name, selectedColor[0], selectedIcon[0], null, 0);
            folder.sortOrder = folderRepository.getRootFolders().size();
            folderRepository.addFolder(folder);
            loadFolderGrid();
            loadDrawerTree();
            Toast.makeText(this, "Folder '" + name + "' created", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showFolderContextMenu(NoteFolder folder) {
        String[] options = {"Rename", "Change Color", "New Subfolder", "Move to...", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(folder.getIconEmoji() + " " + folder.name)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: showRenameFolderDialog(folder); break;
                    case 1: showColorPickerForFolder(folder); break;
                    case 2: showCreateSubfolderDialog(folder); break;
                    case 3: showMoveFolderPicker(folder); break;
                    case 4: confirmDeleteFolder(folder); break;
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
                    loadFolderGrid();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showColorPickerForFolder(NoteFolder folder) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding((int)(20*density), (int)(16*density), (int)(20*density), (int)(8*density));

        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int)(10 * density);
            rowLayout.setLayoutParams(rp);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= NoteFolder.FOLDER_COLORS.length) break;
                FrameLayout ci = new FrameLayout(this);
                int sz = (int)(46 * density);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, sz, 1);
                ip.setMargins((int)(4*density), 0, (int)(4*density), 0);
                ci.setLayoutParams(ip);
                int cv = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[idx]);
                GradientDrawable cbg = new GradientDrawable();
                cbg.setShape(GradientDrawable.OVAL); cbg.setColor(cv);
                if (NoteFolder.FOLDER_COLORS[idx].equalsIgnoreCase(folder.colorHex))
                    cbg.setStroke((int)(3*density), 0xFFFFFFFF);
                ci.setBackground(cbg);
                final int fi = idx;
                ci.setOnClickListener(v -> {
                    folder.colorHex = NoteFolder.FOLDER_COLORS[fi];
                    folderRepository.updateFolder(folder);
                    loadFolderGrid();
                });
                rowLayout.addView(ci);
            }
            container.addView(rowLayout);
        }

        new AlertDialog.Builder(this)
            .setTitle("Change Color")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showCreateSubfolderDialog(NoteFolder parent) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.DarkAlertDialog);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_create_subfolder, null);
        dialog.setContentView(sheetView);

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        android.widget.EditText etName = sheetView.findViewById(R.id.etSubfolderName);
        LinearLayout colorGrid = sheetView.findViewById(R.id.colorPickerGrid);
        LinearLayout iconGrid = sheetView.findViewById(R.id.iconPickerGrid);
        LinearLayout folderPreview = sheetView.findViewById(R.id.folderPreview);
        TextView tvPreviewIcon = sheetView.findViewById(R.id.tvPreviewIcon);
        TextView btnCancel = sheetView.findViewById(R.id.btnCancelSubfolder);
        TextView btnSave = sheetView.findViewById(R.id.btnSaveSubfolder);

        tvTitle.setText("New Subfolder in " + parent.name);

        final String[] selectedColor = {parent.colorHex};
        final String[] selectedIcon = {"folder"};
        final int[] selectedColorIdx = {0};
        final int[] selectedIconIdx = {25};
        float density = getResources().getDisplayMetrics().density;

        // Find parent color index
        for (int i = 0; i < NoteFolder.FOLDER_COLORS.length; i++) {
            if (NoteFolder.FOLDER_COLORS[i].equalsIgnoreCase(parent.colorHex)) {
                selectedColorIdx[0] = i; break;
            }
        }

        Runnable updatePreview = () -> {
            String emoji = "\uD83D\uDCC1";
            for (int i = 0; i < NoteFolder.FOLDER_ICONS.length; i++) {
                if (NoteFolder.FOLDER_ICONS[i].equals(selectedIcon[0])) {
                    emoji = NoteFolder.FOLDER_ICON_EMOJIS[i]; break;
                }
            }
            tvPreviewIcon.setText(emoji);
            int c = Note.parseColorSafe(selectedColor[0]);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(blendWithDark(c, 0.35f));
            bg.setCornerRadius(14 * density);
            bg.setStroke(2, c);
            folderPreview.setBackground(bg);
        };

        // Build color grid (3x4)
        final View[] colorViews = new View[NoteFolder.FOLDER_COLORS.length];
        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rp);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                if (idx >= NoteFolder.FOLDER_COLORS.length) break;
                FrameLayout ci = new FrameLayout(this);
                int sz = (int)(42 * density);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, sz, 1);
                ip.setMargins((int)(4*density), 0, (int)(4*density), 0);
                ci.setLayoutParams(ip);
                int cv = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[idx]);
                GradientDrawable cbg = new GradientDrawable();
                cbg.setShape(GradientDrawable.OVAL); cbg.setColor(cv);
                if (idx == selectedColorIdx[0]) cbg.setStroke((int)(3*density), 0xFFFFFFFF);
                ci.setBackground(cbg);
                colorViews[idx] = ci;
                final int fi = idx;
                ci.setOnClickListener(v -> {
                    selectedColor[0] = NoteFolder.FOLDER_COLORS[fi];
                    selectedColorIdx[0] = fi;
                    for (int i = 0; i < colorViews.length; i++) {
                        int cc = Note.parseColorSafe(NoteFolder.FOLDER_COLORS[i]);
                        GradientDrawable g = new GradientDrawable();
                        g.setShape(GradientDrawable.OVAL); g.setColor(cc);
                        if (i == fi) g.setStroke((int)(3*density), 0xFFFFFFFF);
                        colorViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });
                rowLayout.addView(ci);
            }
            colorGrid.addView(rowLayout);
        }

        // Build icon grid
        final View[] iconViews = new View[NoteFolder.FOLDER_ICONS.length];
        int iconsPerRow = 10;
        int totalRows = (NoteFolder.FOLDER_ICONS.length + iconsPerRow - 1) / iconsPerRow;
        for (int row = 0; row < totalRows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int)(8 * density);
            rowLayout.setLayoutParams(rp);
            for (int col = 0; col < iconsPerRow; col++) {
                int idx = row * iconsPerRow + col;
                if (idx >= NoteFolder.FOLDER_ICONS.length) break;
                TextView ii = new TextView(this);
                ii.setText(NoteFolder.FOLDER_ICON_EMOJIS[idx]);
                ii.setTextSize(24);
                ii.setGravity(android.view.Gravity.CENTER);
                int is = (int)(44 * density);
                LinearLayout.LayoutParams iip = new LinearLayout.LayoutParams(is, is);
                iip.setMargins((int)(3*density), 0, (int)(3*density), 0);
                ii.setLayoutParams(iip);
                ii.setPadding(0, (int)(6*density), 0, 0);
                GradientDrawable ibg = new GradientDrawable();
                ibg.setCornerRadius(12 * density);
                ibg.setColor(idx == selectedIconIdx[0] ? 0x33FFFFFF : 0x00000000);
                ii.setBackground(ibg);
                iconViews[idx] = ii;
                final int fi = idx;
                ii.setOnClickListener(v -> {
                    selectedIcon[0] = NoteFolder.FOLDER_ICONS[fi];
                    selectedIconIdx[0] = fi;
                    for (int i = 0; i < iconViews.length; i++) {
                        GradientDrawable g = new GradientDrawable();
                        g.setCornerRadius(12 * density);
                        g.setColor(i == fi ? 0x33FFFFFF : 0x00000000);
                        iconViews[i].setBackground(g);
                    }
                    updatePreview.run();
                });
                rowLayout.addView(ii);
            }
            iconGrid.addView(rowLayout);
        }

        updatePreview.run();
        etName.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { etName.setError("Enter a name"); return; }
            NoteFolder sub = new NoteFolder(name, selectedColor[0], selectedIcon[0], parent.id, parent.depth + 1);
            folderRepository.addFolder(sub);
            loadFolderGrid();
            loadDrawerTree();
            Toast.makeText(this, "Subfolder '" + name + "' created", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showMoveFolderPicker(NoteFolder folder) {
        List<NoteFolder> all = folderRepository.getAllFolders();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        names.add("Root (Top Level)");
        ids.add(""); // empty string = root
        for (NoteFolder f : all) {
            if (!f.id.equals(folder.id)) {
                StringBuilder indent = new StringBuilder();
                for (int d = 0; d < f.depth; d++) indent.append("  ");
                names.add(indent + f.getIconEmoji() + " " + f.name);
                ids.add(f.id);
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("Move '" + folder.name + "' to...")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                String targetId = ids.get(which);
                boolean ok = folderRepository.moveFolder(folder.id, targetId.isEmpty() ? null : targetId);
                if (!ok) Toast.makeText(this, "Cannot move into own subfolder", Toast.LENGTH_SHORT).show();
                else { loadFolderGrid(); Toast.makeText(this, "Folder moved", Toast.LENGTH_SHORT).show(); }
            })
            .show();
    }

    private void confirmDeleteFolder(NoteFolder folder) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Folder")
            .setMessage("Delete '" + folder.name + "' and all its subfolders? Notes will be moved to All Notes.")
            .setPositiveButton("Delete", (d, w) -> {
                folderRepository.deleteFolder(folder.id);
                loadFolderGrid();
                Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private long getLastModifiedForFolder(String folderId) {
        long latest = 0;
        for (Note note : noteRepository.getAllNotes()) {
            if (!note.isTrashed && !note.isArchived && folderId.equals(note.folderId)) {
                if (note.updatedAt > latest) latest = note.updatedAt;
            }
        }
        return latest;
    }

    private String formatRelativeTime(long timeMs) {
        long diff = System.currentTimeMillis() - timeMs;
        long hours = diff / (60 * 60 * 1000);
        long days = hours / 24;
        if (hours < 1) return "Just now";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.US);
        return sdf.format(new java.util.Date(timeMs));
    }

    private int blendWithDark(int color, float ratio) {
        int dark = 0xFF0F172A;
        float inv = 1f - ratio;
        int r = (int) (Color.red(dark) * inv + Color.red(color) * ratio);
        int g = (int) (Color.green(dark) * inv + Color.green(color) * ratio);
        int b = (int) (Color.blue(dark) * inv + Color.blue(color) * ratio);
        return Color.rgb(r, g, b);
    }

    private int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.rgb(r, g, b);
    }
}
