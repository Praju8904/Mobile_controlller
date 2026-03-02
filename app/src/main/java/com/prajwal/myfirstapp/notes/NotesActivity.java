package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.connectivity.ConnectionManager;
import com.prajwal.myfirstapp.timecapsule.TimeCapsuleActivity;
import com.prajwal.myfirstapp.todo.CreateTodoListSheet;
import com.prajwal.myfirstapp.todo.TodoItem;
import com.prajwal.myfirstapp.todo.TodoList;
import com.prajwal.myfirstapp.todo.TodoListAdapter;
import com.prajwal.myfirstapp.todo.TodoListDetailActivity;
import com.prajwal.myfirstapp.todo.TodoRepository;
import android.animation.Animator;
import android.util.Log;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.os.Build;
import android.view.WindowManager;

/**
 * NotesActivity — Redesigned premium notes experience.
 * 
 * Features:
 * - Staggered masonry grid with colored cards
 * - Pinned notes carousel
 * - Real-time search with voice support
 * - Filter chips for categories
 * - Multi-select mode with batch actions
 * - Speed dial FAB
 * - Archive, Trash, Tags management
 */
public class NotesActivity extends AppCompatActivity implements NotesAdapter.OnNoteActionListener {

    private static final String TAG = "NotesActivity";
    private static final int VOICE_SEARCH_REQUEST = 100;

    // Repository
    private NoteRepository repository;

    // Views - Header
    private ImageButton btnViewToggle, btnMoreMenu;
    private ImageButton btnDrawerToggle;
    private TextView tvNotesTitle, tvNoteCount, tvDateLine;

    // Views - Drawer
    private DrawerLayout drawerLayout;
    private LinearLayout drawerFoldersList;
    private TextView tvDrawerNoteCount, tvDrawerAllCount, tvDrawerPinnedCount, drawerAddFolder;

    // Views - Folder Strip
    private LinearLayout folderStripSection, folderCardsContainer;
    private TextView btnViewAllFolders;
    private NoteFolderRepository folderRepository;

    // Views - Search
    private EditText etSearchNotes;
    private ImageButton btnVoiceSearch;
    private LinearLayout recentSearchesContainer, recentSearchesList;
    private boolean isSearchFocused = false;

    // Views - Filter Chips
    private LinearLayout chipContainer;
    private String currentFilter = "All";

    // Views - Pinned Section
    private LinearLayout pinnedSection, pinnedNotesContainer;
    private TextView tvOthersHeader;

    // Views - Grid
    private RecyclerView notesRecyclerView;
    private NotesAdapter notesAdapter;
    private LinearLayout emptyStateContainer;
    private TextView btnEmptyCreateNote;

    // Views - Recently Viewed
    private LinearLayout recentlyViewedSection, recentlyViewedContainer;

    // Views - To-Do Lists section
    private LinearLayout todoListsSection;
    private RecyclerView todoListsRecycler;
    private TextView tvTodoHeader, btnViewAllTodos, tvTodoSummary;
    private TodoRepository todoRepository;
    private TodoListAdapter todoListAdapter;
    private ArrayList<TodoList> todoLists = new ArrayList<>();

    // Views - Multi-select
    private LinearLayout multiSelectBar;
    private boolean isMultiSelectMode = false;
    private Set<String> selectedNoteIds = new HashSet<>();

    // Views - Speed Dial
    private View speedDialOverlay;
    private LinearLayout speedDialItem1, speedDialItem2, speedDialItem3, speedDialItem4;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAddNote;
    private boolean isSpeedDialOpen = false;

    // Data
    private ArrayList<Note> displayedNotes = new ArrayList<>();
    private String currentSearchQuery = "";
    private boolean isGridView = true;
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Network
    private ConnectionManager connectionManager;
    private String serverIp;

    // Singleton for receiving sync commands
    private static NotesActivity instance;
    public static NotesActivity getInstance() { return instance; }

    // ═══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Draw behind status bar — eliminate wasted black space at top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_notes_new);
        instance = this;

        // Get server IP
        serverIp = getIntent().getStringExtra("server_ip");
        if (serverIp == null) {
            serverIp = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("last_server_ip", null);
        }
        if (serverIp != null) {
            connectionManager = new ConnectionManager(serverIp);
        }

        // Initialize repository
        repository = new NoteRepository(this);
        folderRepository = new NoteFolderRepository(this, repository);

        // Initialize views
        initViews();
        setupHeader();
        setupSearch();
        setupFilterChips();
        setupNotesGrid();
        setupMultiSelectBar();
        setupSpeedDial();
        setupDrawer();
        setupFolderStrip();
        todoRepository = new TodoRepository(this);
        setupTodoSection();

        // Load data
        refreshNotes();
        updateDateLine();

        // Request sync from PC
        if (connectionManager != null) {
            connectionManager.sendCommand("NOTE_SYNC");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotes();
        setupFolderStrip();
        setupRecentlyViewed();
        refreshTodoSection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
        } else if (isSpeedDialOpen) {
            closeSpeedDial();
        } else if (isMultiSelectMode) {
            exitMultiSelectMode();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VIEW INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    private void initViews() {
        // Drawer
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerFoldersList = findViewById(R.id.drawerFoldersList);
        tvDrawerNoteCount = findViewById(R.id.tvDrawerNoteCount);
        tvDrawerAllCount = findViewById(R.id.tvDrawerAllCount);
        tvDrawerPinnedCount = findViewById(R.id.tvDrawerPinnedCount);
        drawerAddFolder = findViewById(R.id.drawerAddFolder);
        btnDrawerToggle = findViewById(R.id.btnDrawerToggle);

        // Header
        btnViewToggle = findViewById(R.id.btnViewToggle);
        btnMoreMenu = findViewById(R.id.btnMoreMenu);
        tvNotesTitle = findViewById(R.id.tvNotesTitle);
        tvNoteCount = findViewById(R.id.tvNoteCount);
        tvDateLine = findViewById(R.id.tvDateLine);

        // Search
        etSearchNotes = findViewById(R.id.etSearchNotes);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        recentSearchesContainer = findViewById(R.id.recentSearchesContainer);
        recentSearchesList = findViewById(R.id.recentSearchesList);

        // Filter chips
        chipContainer = findViewById(R.id.chipContainer);

        // Folder strip
        folderStripSection = findViewById(R.id.folderStripSection);
        folderCardsContainer = findViewById(R.id.folderCardsContainer);
        btnViewAllFolders = findViewById(R.id.btnViewAllFolders);

        // Pinned section
        pinnedSection = findViewById(R.id.pinnedSection);
        pinnedNotesContainer = findViewById(R.id.pinnedNotesContainer);
        tvOthersHeader = findViewById(R.id.tvOthersHeader);

        // Grid
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        btnEmptyCreateNote = findViewById(R.id.btnEmptyCreateNote);

        // Recently Viewed
        recentlyViewedSection = findViewById(R.id.recentlyViewedSection);
        recentlyViewedContainer = findViewById(R.id.recentlyViewedContainer);

        // Multi-select
        multiSelectBar = findViewById(R.id.multiSelectBar);

        // Speed dial
        speedDialOverlay = findViewById(R.id.speedDialOverlay);
        speedDialItem1 = findViewById(R.id.speedDialItem1);
        speedDialItem2 = findViewById(R.id.speedDialItem2);
        speedDialItem3 = findViewById(R.id.speedDialItem3);
        speedDialItem4 = findViewById(R.id.speedDialItem4);
        fabAddNote = findViewById(R.id.fabAddNote);

        // Load view mode preference
        isGridView = repository.getViewMode().equals("grid");
        updateViewToggleIcon();
    }

    // ═══════════════════════════════════════════════════════════════
    //  HEADER
    // ═══════════════════════════════════════════════════════════════

    private void setupHeader() {
        btnViewToggle.setOnClickListener(v -> {
            isGridView = !isGridView;
            repository.setViewMode(isGridView ? "grid" : "list");
            updateViewToggleIcon();
            setupNotesGrid();
            refreshNotes();
        });

        btnMoreMenu.setOnClickListener(this::showMoreMenu);

        if (btnEmptyCreateNote != null) {
            btnEmptyCreateNote.setOnClickListener(v -> createNewNote("note"));
        }
    }

    private void updateViewToggleIcon() {
        btnViewToggle.setImageResource(isGridView ? 
            android.R.drawable.ic_dialog_dialer : 
            android.R.drawable.ic_menu_sort_by_size);
    }

    private void updateDateLine() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
        tvDateLine.setText(sdf.format(new Date()));
    }

    private void updateNoteCount(int count) {
        int folderCount = 0;
        try {
            folderCount = folderRepository.getAllFolders().size();
        } catch (Exception ignored) {}

        int fCount = folderCount;
        String text = count + (count == 1 ? " Note" : " Notes") + " · " + fCount + " Folder" + (fCount == 1 ? "" : "s");

        tvNoteCount.animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(100)
            .withEndAction(() -> {
                tvNoteCount.setText(text);
                tvNoteCount.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start();
            })
            .start();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, "📂  Folders View");
        popup.getMenu().add(0, 1, 1, "📦  Archive");
        popup.getMenu().add(0, 2, 2, "🗑️  Trash");
        popup.getMenu().add(0, 3, 3, "🏷️  Tags");
        popup.getMenu().add(0, 4, 4, "📅  Sort by Date");
        popup.getMenu().add(0, 5, 5, "🔤  Sort by Title");
        popup.getMenu().add(0, 6, 6, "⚙️  Settings");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    startActivity(new Intent(this, NoteFoldersHomeActivity.class));
                    return true;
                case 1:
                    startActivity(new Intent(this, NotesArchiveActivity.class));
                    return true;
                case 2:
                    startActivity(new Intent(this, NotesTrashActivity.class));
                    return true;
                case 3:
                    startActivity(new Intent(this, TagsManagerActivity.class));
                    return true;
                case 4:
                    displayedNotes.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
                    notesAdapter.notifyDataSetChanged();
                    return true;
                case 5:
                    displayedNotes.sort((a, b) -> {
                        String ta = a.title != null ? a.title : "";
                        String tb = b.title != null ? b.title : "";
                        return ta.compareToIgnoreCase(tb);
                    });
                    notesAdapter.notifyDataSetChanged();
                    return true;
                case 6:
                    startActivity(new Intent(this, NotesSettingsActivity.class));
                    return true;
            }
            return false;
        });

        popup.show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  SEARCH

    private void setupSearch() {
        etSearchNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Debounce search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    currentSearchQuery = s.toString().trim();
                    refreshNotes();
                    
                    // Hide recent searches when typing
                    if (!currentSearchQuery.isEmpty()) {
                        recentSearchesContainer.setVisibility(View.GONE);
                    }
                };
                searchHandler.postDelayed(searchRunnable, 200);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etSearchNotes.setOnFocusChangeListener((v, hasFocus) -> {
            isSearchFocused = hasFocus;
            if (hasFocus && currentSearchQuery.isEmpty()) {
                showRecentSearches();
                // Add glow effect
                etSearchNotes.setBackgroundResource(R.drawable.notes_search_focused_bg);
            } else {
                recentSearchesContainer.setVisibility(View.GONE);
                etSearchNotes.setBackgroundResource(R.drawable.notes_search_bg);
            }
        });

        // Save search on submit
        etSearchNotes.setOnEditorActionListener((v, actionId, event) -> {
            String query = etSearchNotes.getText().toString().trim();
            if (!query.isEmpty()) {
                repository.addRecentSearch(query);
                hideKeyboard();
            }
            return true;
        });

        btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());
    }

    private void showRecentSearches() {
        List<String> searches = repository.getRecentSearches();
        if (searches.isEmpty()) {
            recentSearchesContainer.setVisibility(View.GONE);
            return;
        }

        recentSearchesList.removeAllViews();
        for (String search : searches) {
            TextView tv = new TextView(this);
            tv.setText("🕐  " + search);
            tv.setTextColor(0xFF94A3B8);
            tv.setTextSize(14);
            tv.setPadding(0, 16, 0, 16);
            tv.setOnClickListener(v -> {
                etSearchNotes.setText(search);
                etSearchNotes.setSelection(search.length());
                recentSearchesContainer.setVisibility(View.GONE);
            });
            recentSearchesList.addView(tv);
        }

        // Add clear button
        TextView clearBtn = new TextView(this);
        clearBtn.setText("Clear recent searches");
        clearBtn.setTextColor(0xFFEF4444);
        clearBtn.setTextSize(12);
        clearBtn.setPadding(0, 20, 0, 8);
        clearBtn.setOnClickListener(v -> {
            repository.clearRecentSearches();
            recentSearchesContainer.setVisibility(View.GONE);
        });
        recentSearchesList.addView(clearBtn);

        recentSearchesContainer.setVisibility(View.VISIBLE);
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Search notes...");
        try {
            startActivityForResult(intent, VOICE_SEARCH_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Voice search not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_SEARCH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String voiceQuery = results.get(0);
                etSearchNotes.setText(voiceQuery);
                etSearchNotes.setSelection(voiceQuery.length());
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        etSearchNotes.clearFocus();
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILTER CHIPS
    // ═══════════════════════════════════════════════════════════════

    private void setupFilterChips() {
        List<String> categories = repository.getAllCategories();
        chipContainer.removeAllViews();

        for (String category : categories) {
            TextView chip = createFilterChip(category);
            chipContainer.addView(chip);
        }

        // Add To-Do chip
        TextView todoChip = createFilterChip("Todo");
        todoChip.setText("To-Do");
        chipContainer.addView(todoChip);
    }

    private TextView createFilterChip(String category) {
        TextView chip = new TextView(this);
        chip.setText(getCategoryEmoji(category) + " " + category);
        chip.setTextSize(13);
        chip.setPadding(40, 20, 40, 20);

        boolean isSelected = category.equals(currentFilter);
        int chipColor = getCategoryColor(category);
        float density = getResources().getDisplayMetrics().density;

        if (isSelected) {
            // Vibrant gradient background per category with glow
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{chipColor, adjustAlpha(chipColor, 0.75f)});
            gd.setCornerRadius(50f);
            chip.setBackground(gd);
            chip.setTextColor(0xFFFFFFFF);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            // Glow effect via elevation + shadow
            chip.setElevation(8 * density);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                chip.setOutlineAmbientShadowColor(chipColor);
                chip.setOutlineSpotShadowColor(chipColor);
            }
            chip.setShadowLayer(12f, 0, 2, adjustAlpha(chipColor, 0.5f));
        } else {
            // Subtle tinted border in category color
            GradientDrawable unselectedBg = new GradientDrawable();
            unselectedBg.setCornerRadius(50f);
            unselectedBg.setColor(adjustAlpha(chipColor, 0.08f));
            unselectedBg.setStroke((int)(1 * density), adjustAlpha(chipColor, 0.25f));
            chip.setBackground(unselectedBg);
            chip.setTextColor(0xFF9CA3AF);
            chip.setElevation(0);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(12);
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> {
            if (!category.equals(currentFilter)) {
                currentFilter = category;
                
                // Animate selection
                chip.animate()
                    .scaleX(1.05f).scaleY(1.05f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        chip.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    })
                    .start();

                setupFilterChips();
                refreshNotes();
            }
        });

        return chip;
    }

    private String getCategoryEmoji(String category) {
        switch (category) {
            case "All":      return "📋";
            case "Pinned":   return "📌";
            case "Personal": return "👤";
            case "Work":     return "💼";
            case "Ideas":    return "💡";
            case "Study":    return "📖";
            case "Todo":     return "✅";
            default:         return "📝";
        }
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case "All":      return 0xFFF59E0B;
            case "Pinned":   return 0xFFFCD34D;
            case "Personal": return 0xFF3B82F6;
            case "Work":     return 0xFF6366F1;
            case "Ideas":    return 0xFFF97316;
            case "Study":    return 0xFFA855F7;
            case "Todo":     return 0xFF34D399;
            default:         return 0xFFF59E0B;
        }
    }

    private int adjustAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.argb(a, r, g, b);
    }

    // ═══════════════════════════════════════════════════════════════
    //  NOTES GRID
    // ═══════════════════════════════════════════════════════════════

    private void setupNotesGrid() {
        int spanCount = isGridView ? 2 : 1;
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(
            spanCount, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        notesRecyclerView.setLayoutManager(layoutManager);

        notesAdapter = new NotesAdapter(this, displayedNotes, this, isGridView);
        notesRecyclerView.setAdapter(notesAdapter);
    }

    private void refreshNotes() {
        // Get filtered notes
        ArrayList<Note> pinnedNotes = repository.filterPinnedNotes(currentFilter, currentSearchQuery);
        ArrayList<Note> unpinnedNotes = repository.filterUnpinnedNotes(currentFilter, currentSearchQuery);

        // Update pinned section
        updatePinnedSection(pinnedNotes);

        // Update others header
        if (!pinnedNotes.isEmpty() && !unpinnedNotes.isEmpty()) {
            tvOthersHeader.setText("Others");
            tvOthersHeader.setVisibility(View.VISIBLE);
        } else if (pinnedNotes.isEmpty() && !unpinnedNotes.isEmpty()) {
            tvOthersHeader.setText("All Notes");
            tvOthersHeader.setVisibility(View.VISIBLE);
        } else {
            tvOthersHeader.setVisibility(View.GONE);
        }

        // Update main grid with unpinned notes
        displayedNotes.clear();
        displayedNotes.addAll(unpinnedNotes);
        notesAdapter.notifyDataSetChanged();

        // Update count (total active notes for current filter)
        int totalCount = pinnedNotes.size() + unpinnedNotes.size();
        updateNoteCount(totalCount);

        // Show/hide empty state
        boolean isEmpty = pinnedNotes.isEmpty() && unpinnedNotes.isEmpty();
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        notesRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updatePinnedSection(ArrayList<Note> pinnedNotes) {
        if (pinnedNotes.isEmpty()) {
            pinnedSection.setVisibility(View.GONE);
            return;
        }

        pinnedSection.setVisibility(View.VISIBLE);
        pinnedNotesContainer.removeAllViews();

        for (Note note : pinnedNotes) {
            View card = createPinnedNoteCard(note);
            pinnedNotesContainer.addView(card);
        }
    }

    private View createPinnedNoteCard(Note note) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View card = inflater.inflate(R.layout.item_pinned_note, pinnedNotesContainer, false);

        TextView tvTitle = card.findViewById(R.id.tvPinnedTitle);
        TextView tvPreview = card.findViewById(R.id.tvPinnedPreview);
        TextView tvCategory = card.findViewById(R.id.tvPinnedCategory);

        tvTitle.setText(note.title.isEmpty() ? "Untitled" : note.title);
        tvPreview.setText(note.plainTextPreview.isEmpty() ? "No content" : note.plainTextPreview);
        tvCategory.setText(note.category);

        // Apply note color tint
        if (!note.colorHex.equals(Note.NOTE_COLORS[0])) {
            int color = Note.parseColorSafe(note.colorHex);
            card.getBackground().setTint(color);
        }

        card.setOnClickListener(v -> openNote(note));
        card.setOnLongClickListener(v -> {
            if (!isMultiSelectMode) {
                enterMultiSelectMode();
                toggleNoteSelection(note.id);
            }
            return true;
        });

        return card;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MULTI-SELECT MODE
    // ═══════════════════════════════════════════════════════════════

    private void setupMultiSelectBar() {
        LinearLayout btnMultiPin = findViewById(R.id.btnMultiPin);
        LinearLayout btnMultiColor = findViewById(R.id.btnMultiColor);
        LinearLayout btnMultiCategory = findViewById(R.id.btnMultiCategory);
        LinearLayout btnMultiArchive = findViewById(R.id.btnMultiArchive);
        LinearLayout btnMultiDelete = findViewById(R.id.btnMultiDelete);

        btnMultiPin.setOnClickListener(v -> {
            if (!selectedNoteIds.isEmpty()) {
                repository.batchPin(new ArrayList<>(selectedNoteIds), true);
                exitMultiSelectMode();
                refreshNotes();
                Toast.makeText(this, "Pinned " + selectedNoteIds.size() + " notes", Toast.LENGTH_SHORT).show();
            }
        });

        btnMultiColor.setOnClickListener(v -> showColorPicker());

        btnMultiCategory.setOnClickListener(v -> showCategoryPicker());

        btnMultiArchive.setOnClickListener(v -> {
            if (!selectedNoteIds.isEmpty()) {
                repository.batchArchive(new ArrayList<>(selectedNoteIds));
                exitMultiSelectMode();
                refreshNotes();
                Toast.makeText(this, "Archived " + selectedNoteIds.size() + " notes", Toast.LENGTH_SHORT).show();
            }
        });

        btnMultiDelete.setOnClickListener(v -> {
            if (!selectedNoteIds.isEmpty()) {
                new AlertDialog.Builder(this)
                    .setTitle("Move to Trash")
                    .setMessage("Move " + selectedNoteIds.size() + " notes to trash?")
                    .setPositiveButton("Move", (d, w) -> {
                        repository.batchTrash(new ArrayList<>(selectedNoteIds));
                        exitMultiSelectMode();
                        refreshNotes();
                        Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
    }

    private void enterMultiSelectMode() {
        isMultiSelectMode = true;
        selectedNoteIds.clear();
        
        // Show bottom bar with animation
        multiSelectBar.setVisibility(View.VISIBLE);
        multiSelectBar.setTranslationY(multiSelectBar.getHeight());
        multiSelectBar.animate()
            .translationY(0)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        // Hide FAB
        fabAddNote.animate()
            .scaleX(0).scaleY(0)
            .setDuration(150)
            .start();

        notesAdapter.setMultiSelectMode(true);
    }

    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedNoteIds.clear();

        // Hide bottom bar
        multiSelectBar.animate()
            .translationY(multiSelectBar.getHeight())
            .setDuration(200)
            .withEndAction(() -> multiSelectBar.setVisibility(View.GONE))
            .start();

        // Show FAB
        fabAddNote.animate()
            .scaleX(1).scaleY(1)
            .setDuration(200)
            .setInterpolator(new OvershootInterpolator())
            .start();

        notesAdapter.setMultiSelectMode(false);
        notesAdapter.clearSelections();
    }

    private void toggleNoteSelection(String noteId) {
        if (selectedNoteIds.contains(noteId)) {
            selectedNoteIds.remove(noteId);
        } else {
            selectedNoteIds.add(noteId);
        }
        notesAdapter.toggleSelection(noteId);

        // Exit if nothing selected
        if (selectedNoteIds.isEmpty()) {
            exitMultiSelectMode();
        }
    }

    private void showColorPicker() {
        String[] colorNames = Note.NOTE_COLOR_NAMES;
        String[] colorHexes = Note.NOTE_COLORS;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Color");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        for (int i = 0; i < colorNames.length; i++) {
            final String hex = colorHexes[i];
            
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 20, 0, 20);

            View colorDot = new View(this);
            colorDot.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
            colorDot.setBackgroundColor(Note.parseColorSafe(hex));
            ((LinearLayout.LayoutParams) colorDot.getLayoutParams()).setMarginEnd(24);

            TextView name = new TextView(this);
            name.setText(colorNames[i]);
            name.setTextColor(0xFFE2E8F0);
            name.setTextSize(16);

            row.addView(colorDot);
            row.addView(name);

            row.setOnClickListener(v -> {
                repository.batchSetColor(new ArrayList<>(selectedNoteIds), hex);
                exitMultiSelectMode();
                refreshNotes();
            });

            layout.addView(row);
        }

        builder.setView(layout);
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showCategoryPicker() {
        List<String> categories = repository.getAllCategories();
        // Remove "All", "Pinned", "Locked" filters
        categories.remove("All");
        categories.remove("Pinned");
        categories.remove("Locked");

        String[] items = categories.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle("Move to Category")
            .setItems(items, (d, which) -> {
                String category = items[which];
                repository.batchSetCategory(new ArrayList<>(selectedNoteIds), category);
                exitMultiSelectMode();
                refreshNotes();
                Toast.makeText(this, "Moved to " + category, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  SPEED DIAL FAB
    // ═══════════════════════════════════════════════════════════════

    private void setupSpeedDial() {
        fabAddNote.setOnClickListener(v -> {
            if (isSpeedDialOpen) {
                closeSpeedDial();
            } else {
                openSpeedDial();
            }
        });

        speedDialOverlay.setOnClickListener(v -> closeSpeedDial());

        speedDialItem1.setOnClickListener(v -> {
            closeSpeedDial();
            createNewNote("note");
        });

        speedDialItem2.setOnClickListener(v -> {
            closeSpeedDial();
            createNewNote("checklist");
        });

        speedDialItem3.setOnClickListener(v -> {
            closeSpeedDial();
            createNewNote("voice");
        });

        speedDialItem4.setOnClickListener(v -> {
            closeSpeedDial();
            createNewNote("image");
        });
    }

    private void openSpeedDial() {
        isSpeedDialOpen = true;

        // Show overlay
        speedDialOverlay.setVisibility(View.VISIBLE);
        speedDialOverlay.setAlpha(0f);
        speedDialOverlay.animate().alpha(1f).setDuration(200).start();

        // Rotate FAB
        fabAddNote.animate()
            .rotation(45f)
            .setDuration(200)
            .setInterpolator(new OvershootInterpolator())
            .start();

        // Animate items in radially
        animateSpeedDialItem(speedDialItem1, 0);
        animateSpeedDialItem(speedDialItem2, 50);
        animateSpeedDialItem(speedDialItem3, 100);
        animateSpeedDialItem(speedDialItem4, 150);
    }

    private void animateSpeedDialItem(View item, long delay) {
        item.setVisibility(View.VISIBLE);
        item.setAlpha(0f);
        item.setTranslationY(60f);
        item.setScaleX(0.6f);
        item.setScaleY(0.6f);

        item.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(200)
            .setInterpolator(new OvershootInterpolator())
            .start();
    }

    private void closeSpeedDial() {
        isSpeedDialOpen = false;

        // Hide overlay
        speedDialOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> speedDialOverlay.setVisibility(View.GONE))
            .start();

        // Rotate FAB back
        fabAddNote.animate()
            .rotation(0f)
            .setDuration(200)
            .start();

        // Hide items
        hideSpeedDialItem(speedDialItem4, 0);
        hideSpeedDialItem(speedDialItem3, 30);
        hideSpeedDialItem(speedDialItem2, 60);
        hideSpeedDialItem(speedDialItem1, 90);
    }

    private void hideSpeedDialItem(View item, long delay) {
        item.animate()
            .alpha(0f)
            .translationY(30f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setStartDelay(delay)
            .setDuration(150)
            .withEndAction(() -> item.setVisibility(View.GONE))
            .start();
    }

    private void createNewNote(String type) {
        String category = currentFilter.equals("All") || currentFilter.equals("Pinned") || 
                       currentFilter.equals("Locked") ? "Personal" : currentFilter;

        switch (type) {
            case "note":
                // Open editor for new note
                Intent intent = NoteEditorActivity.createNewNoteIntent(this, category);
                startActivityForResult(intent, 200);
                break;
            case "checklist":
                // Create a note with checklist template
                Note checklistNote = new Note();
                checklistNote.category = category;
                checklistNote.title = "Checklist";
                checklistNote.body = "[ ] Item 1\n[ ] Item 2\n[ ] Item 3";
                checklistNote.updatePlainTextPreview();
                repository.addNote(checklistNote);
                Intent checklistIntent = NoteEditorActivity.createIntent(this, checklistNote.id);
                startActivityForResult(checklistIntent, 200);
                break;
            case "voice":
                // Create note and show voice input hint
                Intent voiceIntent = NoteEditorActivity.createNewNoteIntent(this, category);
                voiceIntent.putExtra("start_voice", true);
                startActivityForResult(voiceIntent, 200);
                break;
            case "image":
                // Create note and trigger image insert
                Intent imageIntent = NoteEditorActivity.createNewNoteIntent(this, category);
                imageIntent.putExtra("start_image", true);
                startActivityForResult(imageIntent, 200);
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  NOTE ACTIONS (Adapter Callbacks)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onNoteClick(Note note) {
        if (isMultiSelectMode) {
            toggleNoteSelection(note.id);
        } else {
            openNote(note);
        }
    }

    @Override
    public void onNoteLongClick(Note note) {
        if (!isMultiSelectMode) {
            enterMultiSelectMode();
        }
        toggleNoteSelection(note.id);
    }

    @Override
    public void onNoteSelectionChanged(String noteId, boolean selected) {
        if (selected) {
            selectedNoteIds.add(noteId);
        } else {
            selectedNoteIds.remove(noteId);
        }
        if (selectedNoteIds.isEmpty() && isMultiSelectMode) {
            exitMultiSelectMode();
        }
    }

    private void openNote(Note note) {
        // Open full editor activity - handles locked notes internally
        Intent intent = NoteEditorActivity.createIntent(this, note.id);
        startActivityForResult(intent, 200);
    }

    // Kept for backwards compatibility with quick actions
    private void showQuickEditDialog(Note note) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Note");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etTitle = new EditText(this);
        etTitle.setHint("Title");
        etTitle.setText(note.title);
        etTitle.setTextColor(0xFFE2E8F0);
        etTitle.setHintTextColor(0xFF64748B);
        layout.addView(etTitle);

        EditText etBody = new EditText(this);
        etBody.setHint("Content");
        etBody.setText(note.body.replaceAll("<[^>]*>", "")); // Strip HTML for simple edit
        etBody.setTextColor(0xFFE2E8F0);
        etBody.setHintTextColor(0xFF64748B);
        etBody.setMinLines(5);
        etBody.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = 24;
        etBody.setLayoutParams(bodyParams);
        layout.addView(etBody);

        builder.setView(layout);
        builder.setPositiveButton("Save", (d, w) -> {
            note.title = etTitle.getText().toString().trim();
            note.body = etBody.getText().toString().trim();
            note.updatePlainTextPreview();
            repository.updateNote(note);
            refreshNotes();
        });
        builder.setNeutralButton("More Options", (d, w) -> {
            showNoteOptionsDialog(note);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showNoteOptionsDialog(Note note) {
        String[] options = {
            note.isPinned ? "📌 Unpin" : "📌 Pin",
            note.isLocked ? "🔓 Unlock" : "🔒 Lock",
            "🎨 Change Color",
            "📂 Change Category",
            "🏷️ Edit Tags",
            "📦 Archive",
            "🗑️ Move to Trash"
        };

        new AlertDialog.Builder(this)
            .setTitle(note.title.isEmpty() ? "Untitled Note" : note.title)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: // Pin/Unpin
                        repository.togglePin(note.id);
                        refreshNotes();
                        break;
                    case 1: // Lock/Unlock
                        repository.toggleLock(note.id);
                        refreshNotes();
                        Toast.makeText(this, note.isLocked ? "Note unlocked" : "Note locked", 
                            Toast.LENGTH_SHORT).show();
                        break;
                    case 2: // Change Color
                        showSingleNoteColorPicker(note);
                        break;
                    case 3: // Change Category
                        showSingleNoteCategoryPicker(note);
                        break;
                    case 4: // Edit Tags
                        showTagsEditor(note);
                        break;
                    case 5: // Archive
                        repository.archiveNote(note.id);
                        refreshNotes();
                        Toast.makeText(this, "Note archived", Toast.LENGTH_SHORT).show();
                        break;
                    case 6: // Trash
                        repository.trashNote(note.id);
                        refreshNotes();
                        Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSingleNoteColorPicker(Note note) {
        String[] colorNames = Note.NOTE_COLOR_NAMES;
        String[] colorHexes = Note.NOTE_COLORS;

        new AlertDialog.Builder(this)
            .setTitle("Choose Color")
            .setItems(colorNames, (d, which) -> {
                repository.setNoteColor(note.id, colorHexes[which]);
                refreshNotes();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSingleNoteCategoryPicker(Note note) {
        List<String> categories = repository.getAllCategories();
        categories.remove("All");
        categories.remove("Pinned");
        categories.remove("Locked");
        String[] items = categories.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle("Move to Category")
            .setItems(items, (d, which) -> {
                repository.setNoteCategory(note.id, items[which]);
                refreshNotes();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showTagsEditor(Note note) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Tags");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // Current tags
        TextView tvCurrent = new TextView(this);
        String currentTags = note.tags != null && !note.tags.isEmpty() ? 
            String.join(", ", note.tags) : "No tags";
        tvCurrent.setText("Current: " + currentTags);
        tvCurrent.setTextColor(0xFF94A3B8);
        layout.addView(tvCurrent);

        // Input for new tags
        EditText etTags = new EditText(this);
        etTags.setHint("Enter tags (comma separated)");
        etTags.setText(note.tags != null ? String.join(", ", note.tags) : "");
        etTags.setTextColor(0xFFE2E8F0);
        etTags.setHintTextColor(0xFF64748B);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        etTags.setLayoutParams(params);
        layout.addView(etTags);

        builder.setView(layout);
        builder.setPositiveButton("Save", (d, w) -> {
            String input = etTags.getText().toString().trim();
            note.tags = new ArrayList<>();
            if (!input.isEmpty()) {
                String[] parts = input.split(",");
                for (String part : parts) {
                    String tag = part.trim();
                    if (!tag.isEmpty()) {
                        note.tags.add(tag);
                    }
                }
            }
            repository.updateNote(note);
            refreshNotes();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  NAVIGATION DRAWER
    // ═══════════════════════════════════════════════════════════════

    private void setupDrawer() {
        if (btnDrawerToggle != null && drawerLayout != null) {
            btnDrawerToggle.setOnClickListener(v ->
                    drawerLayout.openDrawer(GravityCompat.START));
        }

        // Nav item click listeners
        LinearLayout drawerNavAllNotes = findViewById(R.id.drawerNavAllNotes);
        LinearLayout drawerNavPinned = findViewById(R.id.drawerNavPinned);
        LinearLayout drawerNavFavourites = findViewById(R.id.drawerNavFavourites);
        LinearLayout drawerNavReminders = findViewById(R.id.drawerNavReminders);
        LinearLayout drawerNavArchive = findViewById(R.id.drawerNavArchive);
        LinearLayout drawerNavTrash = findViewById(R.id.drawerNavTrash);

        if (drawerNavAllNotes != null) {
            drawerNavAllNotes.setOnClickListener(v -> {
                currentFilter = "All";
                setupFilterChips();
                refreshNotes();
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }
        if (drawerNavPinned != null) {
            drawerNavPinned.setOnClickListener(v -> {
                currentFilter = "Pinned";
                setupFilterChips();
                refreshNotes();
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }
        if (drawerNavFavourites != null) {
            drawerNavFavourites.setOnClickListener(v -> {
                currentFilter = "Pinned"; // Favourites = Pinned for now
                setupFilterChips();
                refreshNotes();
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }
        if (drawerNavReminders != null) {
            drawerNavReminders.setOnClickListener(v -> {
                // Filter notes with reminders
                currentFilter = "All";
                setupFilterChips();
                refreshNotes();
                if (drawerLayout != null) drawerLayout.closeDrawers();
                Toast.makeText(this, "Showing notes with reminders", Toast.LENGTH_SHORT).show();
            });
        }
        if (drawerNavArchive != null) {
            drawerNavArchive.setOnClickListener(v -> {
                startActivity(new Intent(this, NotesArchiveActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }
        if (drawerNavTrash != null) {
            drawerNavTrash.setOnClickListener(v -> {
                startActivity(new Intent(this, NotesTrashActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }
        if (drawerAddFolder != null) {
            drawerAddFolder.setOnClickListener(v -> {
                showCreateFolderBottomSheet();
                if (drawerLayout != null) drawerLayout.closeDrawers();
            });
        }

        updateDrawerCounts();
        populateDrawerFolders();
        populateDrawerTags();
        populateDrawerTodos();

        // ── Smart Intelligence Drawer Entries (Prompt 3) ──
        addSmartDrawerEntries();
    }

    /** Adds Study Mode, Insights, and Time Capsule entries to the drawer. */
    private void addSmartDrawerEntries() {
        if (drawerFoldersList == null) return;

        // Add a separator
        View divider = new View(this);
        divider.setBackgroundColor(0xFF1E293B);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
        divider.setPadding(0, dpToPx(8), 0, dpToPx(8));
        drawerFoldersList.addView(divider);

        // Section header
        TextView header = new TextView(this);
        header.setText("SMART FEATURES");
        header.setTextColor(0xFF64748B);
        header.setTextSize(11);
        header.setPadding(dpToPx(20), dpToPx(12), 0, dpToPx(6));
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setLetterSpacing(0.1f);
        drawerFoldersList.addView(header);

        // Study Dashboard
        drawerFoldersList.addView(createDrawerSmartEntry("📚  Study Mode", v -> {
            startActivity(new Intent(this, StudyDashboardActivity.class));
            if (drawerLayout != null) drawerLayout.closeDrawers();
        }));

        // Note Insights
        drawerFoldersList.addView(createDrawerSmartEntry("📊  Note Insights", v -> {
            startActivity(new Intent(this, NoteInsightsActivity.class));
            if (drawerLayout != null) drawerLayout.closeDrawers();
        }));

        // Time Capsules
        drawerFoldersList.addView(createDrawerSmartEntry("⏰  Time Capsules", v -> {
            startActivity(new Intent(this, TimeCapsuleActivity.class));
            if (drawerLayout != null) drawerLayout.closeDrawers();
        }));
    }

    private View createDrawerSmartEntry(String label, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFCBD5E1);
        tv.setTextSize(14);
        tv.setPadding(dpToPx(20), dpToPx(12), dpToPx(16), dpToPx(12));
        tv.setOnClickListener(onClick);
        return tv;
    }

    private void updateDrawerCounts() {
        try {
            java.util.ArrayList<Note> all = repository.filterUnpinnedNotes("All", "");
            java.util.ArrayList<Note> pinned = repository.filterPinnedNotes("All", "");
            int totalCount = all.size() + pinned.size();
            int folderCount = folderRepository.getAllFolders().size();

            if (tvDrawerNoteCount != null) {
                tvDrawerNoteCount.setText(totalCount + " notes · " + folderCount + " folders");
            }
            if (tvDrawerAllCount != null) {
                tvDrawerAllCount.setText(String.valueOf(totalCount));
            }
            if (tvDrawerPinnedCount != null) {
                tvDrawerPinnedCount.setText(String.valueOf(pinned.size()));
            }

            // Favourites count (same as pinned)
            TextView tvFavCount = findViewById(R.id.tvDrawerFavouritesCount);
            if (tvFavCount != null) tvFavCount.setText(String.valueOf(pinned.size()));

            // Reminders count
            int reminderCount = 0;
            for (Note n : all) if (n.reminderDateTime > 0) reminderCount++;
            for (Note n : pinned) if (n.reminderDateTime > 0) reminderCount++;
            TextView tvRemCount = findViewById(R.id.tvDrawerRemindersCount);
            if (tvRemCount != null) tvRemCount.setText(String.valueOf(reminderCount));

            // Archive count
            TextView tvArchiveCount = findViewById(R.id.tvDrawerArchiveCount);
            if (tvArchiveCount != null) {
                java.util.ArrayList<Note> archived = repository.getArchivedNotes();
                tvArchiveCount.setText(String.valueOf(archived != null ? archived.size() : 0));
            }

            // Trash count
            TextView tvTrashCount = findViewById(R.id.tvDrawerTrashCount);
            if (tvTrashCount != null) {
                java.util.ArrayList<Note> trashed = repository.getTrashedNotes();
                tvTrashCount.setText(String.valueOf(trashed != null ? trashed.size() : 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update drawer counts", e);
        }
    }

    private void populateDrawerFolders() {
        if (drawerFoldersList == null) return;
        drawerFoldersList.removeAllViews();

        try {
            List<NoteFolder> rootFolders = folderRepository.getRootFolders();
            for (NoteFolder folder : rootFolders) {
                addDrawerFolderItem(folder, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to populate drawer folders", e);
        }
    }

    private void addDrawerFolderItem(NoteFolder folder, int indentLevel) {
        int indentPx = dpToPx(20 + indentLevel * 16);
        TextView tv = new TextView(this);
        StringBuilder label = new StringBuilder();
        if (indentLevel > 0) label.append("↳ ");
        label.append(folder.getIconEmoji()).append("  ").append(folder.name);
        int count = folderRepository.getNoteCountRecursive(folder.id);
        if (count > 0) label.append(" (").append(count).append(")");
        tv.setText(label.toString());
        tv.setTextColor(indentLevel == 0 ? 0xFFE2E8F0 : 0xFFADBBCB);
        tv.setTextSize(indentLevel == 0 ? 14 : 13);
        tv.setPadding(indentPx, dpToPx(10), dpToPx(16), dpToPx(10));
        tv.setBackgroundResource(android.R.drawable.list_selector_background);
        String folderId = folder.id;
        tv.setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteFolderActivity.class);
            intent.putExtra("folder_id", folderId);
            startActivity(intent);
            if (drawerLayout != null) drawerLayout.closeDrawers();
        });
        drawerFoldersList.addView(tv);

        // Recursively add subfolders
        List<NoteFolder> subs = folderRepository.getSubfolders(folder.id);
        for (NoteFolder sub : subs) {
            addDrawerFolderItem(sub, indentLevel + 1);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void populateDrawerTags() {
        LinearLayout drawerTagsList = findViewById(R.id.drawerTagsList);
        if (drawerTagsList == null) return;
        drawerTagsList.removeAllViews();

        try {
            // Collect all unique tags from all notes
            java.util.Set<String> allTags = new java.util.LinkedHashSet<>();
            java.util.ArrayList<Note> allNotes = repository.filterUnpinnedNotes("All", "");
            java.util.ArrayList<Note> pinnedNotes = repository.filterPinnedNotes("All", "");
            for (Note n : allNotes) {
                if (n.tags != null) allTags.addAll(n.tags);
            }
            for (Note n : pinnedNotes) {
                if (n.tags != null) allTags.addAll(n.tags);
            }

            if (allTags.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("No tags yet");
                empty.setTextColor(0xFF475569);
                empty.setTextSize(12);
                empty.setPadding(0, dpToPx(4), 0, dpToPx(8));
                drawerTagsList.addView(empty);
                return;
            }

            // Create a flow-like layout with horizontal wrapping
            float density = getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            drawerTagsList.addView(row);

            int currentRowWidth = 0;
            int maxWidth = (int)(240 * density); // approximate drawer content width

            for (String tag : allTags) {
                TextView chip = new TextView(this);
                chip.setText("#" + tag);
                chip.setTextColor(0xFFCBD5E1);
                chip.setTextSize(12);
                chip.setPadding((int)(10*density), (int)(4*density), (int)(10*density), (int)(4*density));
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(20 * density);
                chipBg.setColor(0x15FFFFFF);
                chipBg.setStroke(1, 0x20FFFFFF);
                chip.setBackground(chipBg);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, (int)(6*density), (int)(6*density));
                chip.setLayoutParams(lp);

                chip.setOnClickListener(v -> {
                    // Filter by this tag
                    if (etSearchNotes != null) {
                        etSearchNotes.setText("#" + tag);
                        currentSearchQuery = "#" + tag;
                        refreshNotes();
                    }
                    if (drawerLayout != null) drawerLayout.closeDrawers();
                });

                // Estimate width
                chip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int chipWidth = chip.getMeasuredWidth() + (int)(6*density);
                if (currentRowWidth + chipWidth > maxWidth && currentRowWidth > 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    drawerTagsList.addView(row);
                    currentRowWidth = 0;
                }
                row.addView(chip);
                currentRowWidth += chipWidth;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to populate drawer tags", e);
        }
    }

    private void populateDrawerTodos() {
        LinearLayout drawerTodoList = findViewById(R.id.drawerTodoList);
        TextView tvDrawerTodoCount = findViewById(R.id.tvDrawerTodoCount);
        if (drawerTodoList == null) return;
        drawerTodoList.removeAllViews();

        try {
            if (todoRepository == null) return;
            java.util.List<TodoList> lists = todoRepository.getAllLists();
            if (tvDrawerTodoCount != null) {
                tvDrawerTodoCount.setText(lists.size() + " lists");
            }

            for (TodoList list : lists) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(android.view.Gravity.CENTER_VERTICAL);
                item.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));
                item.setBackgroundResource(android.R.drawable.list_selector_background);

                // Icon
                TextView icon = new TextView(this);
                icon.setText(list.iconIdentifier != null && !list.iconIdentifier.isEmpty() ? list.iconIdentifier : "📋");
                icon.setTextSize(14);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                iconLp.setMarginEnd(dpToPx(10));
                icon.setLayoutParams(iconLp);
                item.addView(icon);

                // Name
                TextView name = new TextView(this);
                name.setText(list.title);
                name.setTextColor(0xFFCBD5E1);
                name.setTextSize(13);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                name.setLayoutParams(nameLp);
                item.addView(name);

                // Progress text
                java.util.List<TodoItem> items = todoRepository.getItemsByListId(list.id);
                int total = items.size();
                int done = 0;
                for (TodoItem ti : items) {
                    if (ti.isCompleted) done++;
                }
                TextView progress = new TextView(this);
                progress.setText(done + "/" + total);
                progress.setTextColor(0xFF64748B);
                progress.setTextSize(11);
                item.addView(progress);

                String listId = list.id;
                item.setOnClickListener(v -> {
                    startActivity(TodoListDetailActivity.createIntent(this, listId));
                    if (drawerLayout != null) drawerLayout.closeDrawers();
                });
                drawerTodoList.addView(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to populate drawer todos", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TO-DO LISTS SECTION
    // ═══════════════════════════════════════════════════════════════

    private void setupTodoSection() {
        todoListsSection = findViewById(R.id.todoListsSection);
        todoListsRecycler = findViewById(R.id.todoListsRecycler);
        tvTodoHeader = findViewById(R.id.tvTodoHeader);
        btnViewAllTodos = findViewById(R.id.btnViewAllTodos);
        tvTodoSummary = findViewById(R.id.tvTodoSummary);

        if (todoListsSection == null) return;

        // Setup horizontal recycler
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        todoListsRecycler.setLayoutManager(layoutManager);

        // Create adapter
        todoListAdapter = new TodoListAdapter(this, todoLists, todoRepository,
            new TodoListAdapter.OnListActionListener() {
                @Override
                public void onListClick(TodoList list) {
                    startActivity(TodoListDetailActivity.createIntent(NotesActivity.this, list.id));
                }
                @Override
                public void onAddListClick() {
                    CreateTodoListSheet sheet = new CreateTodoListSheet();
                    sheet.setOnListCreatedListener(list -> refreshTodoSection());
                    sheet.show(getSupportFragmentManager(), CreateTodoListSheet.TAG);
                }
            });
        todoListsRecycler.setAdapter(todoListAdapter);

        refreshTodoSection();
    }

    private void refreshTodoSection() {
        if (todoRepository == null) return;
        todoLists.clear();
        todoLists.addAll(todoRepository.getAllLists());
        if (todoListAdapter != null) todoListAdapter.notifyDataSetChanged();

        // Update summary
        if (tvTodoSummary != null) {
            int active = todoRepository.getGlobalActiveCount();
            int overdue = todoRepository.getGlobalOverdueCount();
            String summary = active + " active";
            if (overdue > 0) summary += " · " + overdue + " overdue";
            tvTodoSummary.setText(summary);
        }

        // Show section
        if (todoListsSection != null) {
            todoListsSection.setVisibility(View.VISIBLE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  FOLDER STRIP
    // ═══════════════════════════════════════════════════════════════

    private void setupFolderStrip() {
        if (folderStripSection == null || folderCardsContainer == null) return;

        try {
            java.util.ArrayList<NoteFolder> folders = folderRepository.getRootFolders();
            if (folders.isEmpty()) {
                folderStripSection.setVisibility(View.GONE);
                return;
            }

            folderStripSection.setVisibility(View.VISIBLE);
            folderCardsContainer.removeAllViews();

            // Pre-compute per-folder note counts
            java.util.HashMap<String, Integer> countMap = new java.util.HashMap<>();
            for (Note n : repository.getActiveNotes()) {
                if (n.folderId != null) {
                    countMap.put(n.folderId, countMap.getOrDefault(n.folderId, 0) + 1);
                }
            }

            float density = getResources().getDisplayMetrics().density;

            for (NoteFolder folder : folders) {
                View card = LayoutInflater.from(this).inflate(
                        R.layout.item_notes_folder_strip_card, folderCardsContainer, false);

                TextView tvIcon  = card.findViewById(R.id.tvFolderIcon);
                TextView tvName  = card.findViewById(R.id.tvFolderName);
                TextView tvCount = card.findViewById(R.id.tvFolderCount);

                // Use the folder's actual icon emoji instead of generic "📁"
                tvIcon.setText(folder.getIconEmoji());
                tvName.setText(folder.name);

                int count = folderRepository.getNoteCountRecursive(folder.id);
                tvCount.setText(count + (count == 1 ? " note" : " notes"));

                // Apply gradient background using folder's color
                int startColor = folder.getColorInt();
                int endColor = folder.getGradientColorInt();
                int darkStart = blendWithDark(startColor, 0.3f);
                int darkEnd = blendWithDark(endColor, 0.2f);
                GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{darkStart, darkEnd});
                bg.setCornerRadius(16 * density);
                bg.setStroke(1, blendWithDark(startColor, 0.45f));
                card.setBackground(bg);

                // Subfolder indicator
                java.util.ArrayList<NoteFolder> subs = folderRepository.getSubfolders(folder.id);
                if (!subs.isEmpty()) {
                    tvCount.setText(count + (count == 1 ? " note" : " notes") + " · ↳ " + subs.size());
                }

                String fId = folder.id;
                card.setOnClickListener(v -> {
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                            .withEndAction(() -> {
                                Intent intent = new Intent(this, NoteFolderActivity.class);
                                intent.putExtra("folder_id", fId);
                                startActivity(intent);
                            }).start()).start();
                });

                folderCardsContainer.addView(card);
            }

            // Add "+" create card with gradient border
            View plusCard = LayoutInflater.from(this).inflate(
                    R.layout.item_notes_folder_strip_card, folderCardsContainer, false);
            TextView tvPlusIcon  = plusCard.findViewById(R.id.tvFolderIcon);
            TextView tvPlusName  = plusCard.findViewById(R.id.tvFolderName);
            TextView tvPlusCount = plusCard.findViewById(R.id.tvFolderCount);
            tvPlusIcon.setText("➕");
            tvPlusIcon.setTextSize(28);
            tvPlusName.setText("New");
            tvPlusCount.setText("folder");
            GradientDrawable plusBg = new GradientDrawable();
            plusBg.setCornerRadius(16 * density);
            plusBg.setColor(0x0DFFFFFF);
            plusBg.setStroke((int)(1.5f * density), 0x33F59E0B);
            plusCard.setBackground(plusBg);
            plusCard.setOnClickListener(v -> showCreateFolderBottomSheet());
            folderCardsContainer.addView(plusCard);

            if (btnViewAllFolders != null) {
                btnViewAllFolders.setOnClickListener(v ->
                        startActivity(new Intent(this, NoteFoldersHomeActivity.class)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup folder strip", e);
            if (folderStripSection != null) folderStripSection.setVisibility(View.GONE);
        }
    }

    private int blendWithDark(int color, float ratio) {
        int dark = 0xFF0F172A;
        float inv = 1f - ratio;
        int r = (int) (Color.red(dark) * inv + Color.red(color) * ratio);
        int g = (int) (Color.green(dark) * inv + Color.green(color) * ratio);
        int b = (int) (Color.blue(dark) * inv + Color.blue(color) * ratio);
        return Color.rgb(r, g, b);
    }

    private void showCreateFolderBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.DarkAlertDialog);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_create_subfolder, null);
        dialog.setContentView(sheetView);

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundColor(Color.TRANSPARENT);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        EditText etName = sheetView.findViewById(R.id.etSubfolderName);
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
            NoteFolder f = new NoteFolder(name, selectedColor[0], selectedIcon[0], null, 0);
            f.sortOrder = folderRepository.getRootFolders().size();
            folderRepository.addFolder(f);
            setupFolderStrip();
            Toast.makeText(this, "Folder '" + name + "' created", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  RECENTLY VIEWED STRIP
    // ═══════════════════════════════════════════════════════════════

    private void setupRecentlyViewed() {
        if (recentlyViewedSection == null || recentlyViewedContainer == null) return;

        try {
            ArrayList<Note> candidates = repository.filterUnpinnedNotes("All", "");
            List<Note> recentNotes = new ArrayList<>();
            for (Note n : candidates) {
                if (!n.isLocked) {
                    recentNotes.add(n);
                    if (recentNotes.size() >= 6) break;
                }
            }

            if (recentNotes.size() < 3) {
                recentlyViewedSection.setVisibility(View.GONE);
                return;
            }

            recentlyViewedSection.setVisibility(View.VISIBLE);
            recentlyViewedContainer.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(this);
            for (Note note : recentNotes) {
                View card = inflater.inflate(R.layout.item_recently_viewed_card, recentlyViewedContainer, false);
                TextView tvTitle = card.findViewById(R.id.tvRecentNoteTitle);
                TextView tvPreview = card.findViewById(R.id.tvRecentNotePreview);
                TextView tvFolderChip = card.findViewById(R.id.tvRecentFolderChip);
                TextView tvTimestamp = card.findViewById(R.id.tvRecentTimestamp);
                View accent = card.findViewById(R.id.recentCategoryAccent);

                tvTitle.setText(note.title.isEmpty() ? "Untitled" : note.title);

                // Content preview
                if (note.body != null && !note.body.isEmpty()) {
                    String preview = note.body.replaceAll("\\n+", " ").trim();
                    tvPreview.setText(preview.length() > 80 ? preview.substring(0, 80) + "…" : preview);
                    tvPreview.setVisibility(View.VISIBLE);
                } else {
                    tvPreview.setVisibility(View.GONE);
                }

                // Folder chip
                if (note.folderId != null) {
                    NoteFolder folder = folderRepository.getFolderById(note.folderId);
                    if (folder != null) {
                        tvFolderChip.setText(folder.getIconEmoji() + " " + folder.name);
                        tvFolderChip.setVisibility(View.VISIBLE);
                    }
                }

                // Timestamp
                tvTimestamp.setText(getRelativeTime(note.updatedAt));

                // Category accent color
                int accentColor = Note.getCategoryColor(note.category);
                GradientDrawable accentBg = new GradientDrawable();
                accentBg.setColor(accentColor);
                float[] radii = {8, 8, 0, 0, 0, 0, 8, 8}; // top-left, bottom-left rounded
                accentBg.setCornerRadii(radii);
                accent.setBackground(accentBg);

                // Tap animation
                card.setOnClickListener(v -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                            .withEndAction(() -> openNote(note)).start()).start();
                });
                recentlyViewedContainer.addView(card);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup recently viewed", e);
            if (recentlyViewedSection != null) recentlyViewedSection.setVisibility(View.GONE);
        }
    }

    private String getRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 60_000) return "just now";
        if (diff < 3_600_000) return (diff / 60_000) + "m ago";
        if (diff < 86_400_000) return (diff / 3_600_000) + "h ago";
        if (diff < 604_800_000) return (diff / 86_400_000) + "d ago";
        return new java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
    }

    // ═══════════════════════════════════════════════════════════════
    //  SYNC CALLBACKS (Called by ReverseCommandListener)
    // ═══════════════════════════════════════════════════════════════

    public void onNotesSyncReceived(String jsonData) {
        runOnUiThread(() -> {
            repository.importFromJson(jsonData);
            refreshNotes();
            Toast.makeText(this, "Notes synced!", Toast.LENGTH_SHORT).show();
        });
    }

    public void onNoteAddedFromPC(String noteId, String name) {
        runOnUiThread(() -> {
            repository.reload();
            refreshNotes();
        });
    }

    public void onNoteUpdatedFromPC(String noteId) {
        runOnUiThread(() -> {
            repository.reload();
            refreshNotes();
        });
    }

    public void onNoteDeletedFromPC(String noteId) {
        runOnUiThread(() -> {
            repository.reload();
            refreshNotes();
        });
    }

    /**
     * Called by ReverseCommandListener for note events from PC
     */
    public void onNoteEventReceived(String eventType, String noteId) {
        runOnUiThread(() -> {
            switch (eventType) {
                case "ADDED":
                    onNoteAddedFromPC(noteId, "");
                    break;
                case "UPDATED":
                    onNoteUpdatedFromPC(noteId);
                    break;
                case "DELETED":
                    onNoteDeletedFromPC(noteId);
                    break;
            }
        });
    }
}
