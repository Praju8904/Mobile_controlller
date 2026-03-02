package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.calendar.CalendarEvent;
import com.prajwal.myfirstapp.calendar.CalendarRepository;
import com.prajwal.myfirstapp.expenses.Expense;
import com.prajwal.myfirstapp.expenses.ExpenseRepository;
import com.prajwal.myfirstapp.tasks.FocusModeActivity;
import com.prajwal.myfirstapp.timecapsule.TimeCapsuleManager;
import com.prajwal.myfirstapp.timecapsule.TimeCapsuleNotificationReceiver;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE EDITOR ACTIVITY — Block-based rich editor (Notion / Evernote level)
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Architecture: Every content piece is an independent ContentBlock rendered in a
 * RecyclerView by BlockEditorAdapter. Blocks can be reordered via drag-handle,
 * converted between 21 types, and individually formatted.
 *
 * Features:
 * - 21 block types (text, h1-h3, checklist, bullet, numbered, toggle, quote,
 *   callout, code, divider, image, table, math, file, link preview, audio,
 *   drawing, video, location)
 * - Inline rich-text formatting (bold, italic, underline, strikethrough,
 *   highlight, text color)
 * - Block-level operations (drag reorder, duplicate, convert, delete, indent)
 * - Undo/Redo via block-snapshot stack
 * - Auto-save every 30 s with version history
 * - PIN/biometric lock, reminders, tags, categories
 * - Export PDF / share image
 * - Smart suggestions (auto-category, tag hints, duplicate warning)
 * - Word-count goal tracker
 * - Note relations, backlinks, table of contents
 * - Calendar & expense linking
 * - Note icon (emoji) & cover image placeholders
 */
public class NoteEditorActivity extends AppCompatActivity
        implements BlockEditorAdapter.BlockEditorListener,
                   WritingAssistantBottomSheet.WritingAssistantListener {

    private static final String TAG = "NoteEditorActivity";
    private static final String EXTRA_NOTE_ID = "note_id";
    private static final String EXTRA_NEW_NOTE = "new_note";
    private static final String EXTRA_CATEGORY = "category";
    private static final String LABEL_EDITING = "✏️ Editing...";
    private static final String LABEL_SAVING  = "💾 Saving...";
    private static final String LABEL_SAVED   = "✓ Saved";
    private static final long AUTO_SAVE_INTERVAL = 30_000;
    private static final int MAX_UNDO_STACK = 50;
    private static final SimpleDateFormat SDF_MMM_D =
            new SimpleDateFormat("MMM d", Locale.US);

    // ═══ UI — Top Toolbar ═══
    private LinearLayout topToolbar;
    private ImageButton btnBack, btnPin, btnLock, btnReminder, btnMoreMenu;
    private ImageButton btnUndo, btnRedo;
    private TextView tvBlockTypeIndicator;

    // ═══ UI — Editor Area ═══
    private NestedScrollView editorScrollView;
    private LinearLayout editorContainer;
    private TextView tvCategory;
    private EditText etTitle;
    private LinearLayout tagsContainer, tagsList, reminderBanner;
    private TextView btnAddTag, tvReminderTime;
    private ImageButton btnClearReminder;
    private FrameLayout coverImageContainer;
    private ImageView coverImage;
    private TextView noteIconEmoji;
    private LinearLayout propertiesContainer;

    // ═══ UI — Block Editor ═══
    private RecyclerView blocksRecyclerView;
    private BlockEditorAdapter blockEditorAdapter;
    private final List<ContentBlock> blocks = new ArrayList<>();
    private int pendingInsertPosition = -1;

    // ═══ UI — Backlinks & TOC ═══
    private LinearLayout backlinksContainer, tocContainer;
    private LinearLayout backlinksList, tocList;

    // ═══ UI — Status Bar ═══
    private TextView tvWordCount, tvCharCount, tvReadTime, tvBlockCount, tvLastSaved;

    // ═══ UI — Formatting Toolbar ═══
    private HorizontalScrollView formattingToolbar;
    private ImageButton btnAddBlock;
    private ImageButton btnBold, btnItalic, btnUnderline, btnStrikethrough;
    private ImageButton btnHighlight, btnTextColor, btnHeading;
    private ImageButton btnBulletList, btnNumberedList, btnChecklist;
    private ImageButton btnInsertImage, btnInsertDivider, btnCodeBlock;
    private ImageButton btnIndent, btnOutdent;
    private ImageButton btnVoiceInput, btnSketch;

    // ═══ Smart Features ═══
    private LinearLayout smartBannerContainer;
    private View wordGoalProgressLine;
    private boolean smartFeaturesShown = false;
    private int wordCountGoal = 0;
    private boolean goalReached = false;

    // ═══ Data ═══
    private NoteRepository noteRepository;
    private Note currentNote;
    private boolean isNewNote;
    private String initialCategory;
    private boolean hasUnsavedChanges = false;
    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;

    // ═══ Undo / Redo (block-snapshot) ═══
    private final LinkedList<String> undoStack = new LinkedList<>();
    private final LinkedList<String> redoStack = new LinkedList<>();

    // ═══ Toolbar auto-hide ═══
    private int lastScrollY = 0;
    private boolean isToolbarHidden = false;

    // ═══ Managers ═══
    private NoteLockManager lockManager;
    private NoteReminderManager reminderManager;
    private NoteVersionManager versionManager;
    private NoteExportManager exportManager;
    private NoteTemplatesManager templatesManager;
    private NoteRelationsManager relationsManager;
    private NotesCrossFeatureManager crossFeatureManager;
    private Vibrator vibrator;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    public static Intent createIntent(Context context, String noteId) {
        Intent intent = new Intent(context, NoteEditorActivity.class);
        intent.putExtra(EXTRA_NOTE_ID, noteId);
        intent.putExtra(EXTRA_NEW_NOTE, false);
        return intent;
    }

    public static Intent createNewNoteIntent(Context context, String category) {
        Intent intent = new Intent(context, NoteEditorActivity.class);
        intent.putExtra(EXTRA_NEW_NOTE, true);
        if (category != null) intent.putExtra(EXTRA_CATEGORY, category);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        noteRepository = new NoteRepository(this);

        // Managers
        lockManager    = new NoteLockManager(this);
        reminderManager = new NoteReminderManager(this);
        versionManager  = new NoteVersionManager(this);
        exportManager   = new NoteExportManager(this);
        templatesManager = new NoteTemplatesManager(this);
        relationsManager = new NoteRelationsManager(this);
        crossFeatureManager = new NotesCrossFeatureManager(this);

        // Parse intent
        Intent intent = getIntent();
        isNewNote = intent.getBooleanExtra(EXTRA_NEW_NOTE, true);
        initialCategory = intent.getStringExtra(EXTRA_CATEGORY);
        String initialFolderId = intent.getStringExtra("folder_id");

        if (!isNewNote) {
            String noteId = intent.getStringExtra(EXTRA_NOTE_ID);
            if (noteId != null) currentNote = noteRepository.getNoteById(noteId);
        }

        // Handle shared text intent
        if (Intent.ACTION_SEND.equals(intent.getAction()) && currentNote == null) {
            isNewNote = true;
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            currentNote = new Note();
            currentNote.title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (currentNote.title == null) currentNote.title = "";
            if (shared != null && !shared.isEmpty()) {
                ContentBlock tb = new ContentBlock(ContentBlock.TYPE_TEXT);
                tb.setText(shared);
                blocks.add(tb);
            }
        }

        initializeViews();
        setupBlockEditor();
        setupListeners();
        setupAutoSave();

        if (currentNote != null && !Intent.ACTION_SEND.equals(intent.getAction())) {
            if (currentNote.isLocked) {
                checkNoteLock();
            } else {
                loadNote();
            }
        } else if (currentNote == null) {
            // Brand-new note
            currentNote = new Note();
            if (initialCategory != null) currentNote.category = initialCategory;
            if (initialFolderId != null && !initialFolderId.isEmpty()) {
                currentNote.folderId = initialFolderId;
            }
            updateCategoryDisplay();
            if (blocks.isEmpty()) {
                blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
            }
            blockEditorAdapter.notifyDataSetChanged();
            etTitle.requestFocus();
        } else {
            // Shared-text path — blocks already populated
            blockEditorAdapter.notifyDataSetChanged();
            updateCategoryDisplay();
            etTitle.requestFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges) {
            saveNote(true);
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  VIEW INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    private void initializeViews() {
        // Top toolbar
        topToolbar          = findViewById(R.id.topToolbar);
        btnBack             = findViewById(R.id.btnBack);
        btnPin              = findViewById(R.id.btnPin);
        btnLock             = findViewById(R.id.btnLock);
        btnReminder         = findViewById(R.id.btnReminder);
        btnMoreMenu         = findViewById(R.id.btnMoreMenu);
        btnUndo             = findViewById(R.id.btnUndo);
        btnRedo             = findViewById(R.id.btnRedo);
        tvBlockTypeIndicator = findViewById(R.id.tvBlockTypeIndicator);

        // Editor area
        editorScrollView    = findViewById(R.id.editorScrollView);
        editorContainer     = findViewById(R.id.editorContainer);
        tvCategory          = findViewById(R.id.tvCategory);
        etTitle             = findViewById(R.id.etTitle);
        tagsContainer       = findViewById(R.id.tagsContainer);
        tagsList            = findViewById(R.id.tagsList);
        btnAddTag           = findViewById(R.id.btnAddTag);
        reminderBanner      = findViewById(R.id.reminderBanner);
        tvReminderTime      = findViewById(R.id.tvReminderTime);
        btnClearReminder    = findViewById(R.id.btnClearReminder);
        coverImageContainer = findViewById(R.id.coverImageContainer);
        coverImage          = findViewById(R.id.coverImage);
        noteIconEmoji       = findViewById(R.id.noteIconEmoji);
        propertiesContainer = findViewById(R.id.propertiesContainer);

        // Block editor
        blocksRecyclerView  = findViewById(R.id.blocksRecyclerView);

        // Backlinks & TOC
        backlinksContainer  = findViewById(R.id.backlinksContainer);
        backlinksList       = findViewById(R.id.backlinksList);
        tocContainer        = findViewById(R.id.tocContainer);
        tocList             = findViewById(R.id.tocList);

        // Status bar
        tvWordCount  = findViewById(R.id.tvWordCount);
        tvCharCount  = findViewById(R.id.tvCharCount);
        tvReadTime   = findViewById(R.id.tvReadTime);
        tvBlockCount = findViewById(R.id.tvBlockCount);
        tvLastSaved  = findViewById(R.id.tvLastSaved);

        // Formatting toolbar
        formattingToolbar = findViewById(R.id.formattingToolbar);
        btnAddBlock       = findViewById(R.id.btnAddBlock);
        btnBold           = findViewById(R.id.btnBold);
        btnItalic         = findViewById(R.id.btnItalic);
        btnUnderline      = findViewById(R.id.btnUnderline);
        btnStrikethrough  = findViewById(R.id.btnStrikethrough);
        btnHighlight      = findViewById(R.id.btnHighlight);
        btnTextColor      = findViewById(R.id.btnTextColor);
        btnHeading        = findViewById(R.id.btnHeading);
        btnBulletList     = findViewById(R.id.btnBulletList);
        btnNumberedList   = findViewById(R.id.btnNumberedList);
        btnChecklist      = findViewById(R.id.btnChecklist);
        btnInsertImage    = findViewById(R.id.btnInsertImage);
        btnInsertDivider  = findViewById(R.id.btnInsertDivider);
        btnCodeBlock      = findViewById(R.id.btnCodeBlock);
        btnIndent         = findViewById(R.id.btnIndent);
        btnOutdent        = findViewById(R.id.btnOutdent);
        btnVoiceInput     = findViewById(R.id.btnVoiceInput);
        btnSketch         = findViewById(R.id.btnSketch);

        // Smart features
        smartBannerContainer = findViewById(R.id.smartBannerContainer);
        wordGoalProgressLine = findViewById(R.id.wordGoalProgressLine);
    }

    private void setupBlockEditor() {
        blockEditorAdapter = new BlockEditorAdapter(this, blocks, this);
        blocksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        blocksRecyclerView.setAdapter(blockEditorAdapter);
        blocksRecyclerView.setNestedScrollingEnabled(false);

        // Drag-and-drop reorder via ItemTouchHelper
        ItemTouchHelper.SimpleCallback touchCb = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder from,
                                  RecyclerView.ViewHolder to) {
                blockEditorAdapter.moveBlock(from.getAdapterPosition(), to.getAdapterPosition());
                hasUnsavedChanges = true;
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int dir) { /* no swipe */ }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // drag initiated via drag handle only
            }
        };
        new ItemTouchHelper(touchCb).attachToRecyclerView(blocksRecyclerView);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LISTENERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void setupListeners() {
        btnBack.setOnClickListener(v -> { hapticTick(); onBackPressed(); });
        btnPin.setOnClickListener(v -> { hapticTick(); togglePin(); });
        btnLock.setOnClickListener(v -> { hapticTick(); toggleLock(); });
        btnReminder.setOnClickListener(v -> { hapticTick(); showReminderPicker(); });
        btnMoreMenu.setOnClickListener(v -> { hapticTick(); showMoreMenu(); });
        btnAddTag.setOnClickListener(v -> { hapticTick(); showAddTagDialog(); });
        btnClearReminder.setOnClickListener(v -> { hapticTick(); clearReminder(); });
        btnUndo.setOnClickListener(v -> { hapticTick(); performUndo(); });
        btnRedo.setOnClickListener(v -> { hapticTick(); performRedo(); });

        // Title text watcher
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasUnsavedChanges = true;
                tvLastSaved.setText(LABEL_EDITING);
                tvLastSaved.setTextColor(Color.parseColor("#F59E0B"));
                tvLastSaved.animate().alpha(1f).setDuration(150).start();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Scroll listener — auto-hide formatting toolbar
        editorScrollView.setOnScrollChangeListener(
                (NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
                    int deltaY = scrollY - lastScrollY;
                    if (Math.abs(deltaY) > 10) {
                        if (deltaY > 0 && !isToolbarHidden) hideFormattingToolbar();
                        else if (deltaY < 0 && isToolbarHidden) showFormattingToolbar();
                    }
                    lastScrollY = scrollY;
                });

        // Emoji icon tap
        if (noteIconEmoji != null) {
            noteIconEmoji.setOnClickListener(v -> showEmojiPicker());
        }

        setupFormattingButtons();
    }

    private void setupFormattingButtons() {
        // ── Add Block ──
        btnAddBlock.setOnClickListener(v -> {
            hapticTick();
            int pos = blockEditorAdapter.getFocusedPosition();
            if (pos < 0) pos = blocks.size() - 1;
            pendingInsertPosition = pos;
            BlockPickerBottomSheet picker = BlockPickerBottomSheet.newInstance(pos);
            picker.setListener(type -> insertNewBlock(type, pendingInsertPosition + 1));
            picker.show(getSupportFragmentManager(), "block_picker");
        });

        // ── Text Formatting (spans on focused EditText) ──
        btnBold.setOnClickListener(v ->          { hapticTick(); applySpanToFocused(new StyleSpan(Typeface.BOLD)); });
        btnItalic.setOnClickListener(v ->        { hapticTick(); applySpanToFocused(new StyleSpan(Typeface.ITALIC)); });
        btnUnderline.setOnClickListener(v ->     { hapticTick(); applySpanToFocused(new UnderlineSpan()); });
        btnStrikethrough.setOnClickListener(v -> { hapticTick(); applySpanToFocused(new StrikethroughSpan()); });
        btnHighlight.setOnClickListener(v ->     { hapticTick(); showHighlightColorPicker(); });
        btnTextColor.setOnClickListener(v ->     { hapticTick(); showTextColorPicker(); });

        // ── Block Type Conversions ──
        btnHeading.setOnClickListener(v ->       { hapticTick(); showHeadingPicker(); });
        btnBulletList.setOnClickListener(v ->    { hapticTick(); convertFocusedBlock(ContentBlock.TYPE_BULLET); });
        btnNumberedList.setOnClickListener(v ->  { hapticTick(); convertFocusedBlock(ContentBlock.TYPE_NUMBERED); });
        btnChecklist.setOnClickListener(v ->     { hapticTick(); convertFocusedBlock(ContentBlock.TYPE_CHECKLIST); });

        // ── Insert Media Blocks ──
        btnInsertImage.setOnClickListener(v ->   { hapticTick(); insertNewBlock(ContentBlock.TYPE_IMAGE, getInsertPos()); });
        btnInsertDivider.setOnClickListener(v -> { hapticTick(); insertNewBlock(ContentBlock.TYPE_DIVIDER, getInsertPos()); });
        btnCodeBlock.setOnClickListener(v ->     { hapticTick(); insertNewBlock(ContentBlock.TYPE_CODE, getInsertPos()); });

        // ── Indent / Outdent ──
        btnIndent.setOnClickListener(v -> {
            hapticTick();
            int pos = blockEditorAdapter.getFocusedPosition();
            if (pos >= 0 && pos < blocks.size() && blocks.get(pos).isIndentable()) {
                blocks.get(pos).indent();
                blockEditorAdapter.notifyItemChanged(pos);
                hasUnsavedChanges = true;
            }
        });
        btnOutdent.setOnClickListener(v -> {
            hapticTick();
            int pos = blockEditorAdapter.getFocusedPosition();
            if (pos >= 0 && pos < blocks.size()) {
                blocks.get(pos).outdent();
                blockEditorAdapter.notifyItemChanged(pos);
                hasUnsavedChanges = true;
            }
        });

        // ── Voice & Sketch ──
        btnVoiceInput.setOnClickListener(v -> {
            hapticTick();
            Toast.makeText(this, "Voice input coming soon", Toast.LENGTH_SHORT).show();
        });
        btnSketch.setOnClickListener(v -> {
            hapticTick();
            insertNewBlock(ContentBlock.TYPE_DRAWING, getInsertPos());
        });
    }

    /** Position after the focused block, or end of list. */
    private int getInsertPos() {
        int pos = blockEditorAdapter.getFocusedPosition();
        return pos >= 0 ? pos + 1 : blocks.size();
    }

    private void setupAutoSave() {
        autoSaveHandler = new Handler(Looper.getMainLooper());
        autoSaveRunnable = () -> {
            if (hasUnsavedChanges && currentNote != null) {
                saveNote(false);
                versionManager.saveVersion(currentNote);
            }
            autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL);
        };
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTE LOADING / SAVING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void loadNote() {
        if (currentNote == null) return;

        etTitle.setText(currentNote.title);

        // ── Load blocks from JSON, or migrate legacy body ──
        blocks.clear();
        if (currentNote.blocksJson != null && !currentNote.blocksJson.isEmpty()) {
            try {
                blocks.addAll(ContentBlock.fromJsonArray(currentNote.blocksJson));
            } catch (Exception e) {
                Log.e(TAG, "Block JSON parse failed, migrating body", e);
                migrateBodyToBlocks(currentNote.body);
            }
        } else if (currentNote.body != null && !currentNote.body.isEmpty()) {
            migrateBodyToBlocks(currentNote.body);
        }

        if (blocks.isEmpty()) blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
        blockEditorAdapter.notifyDataSetChanged();

        updateCategoryDisplay();
        updateTagsDisplay();
        updatePinButton();
        updateLockButton();
        updateReminderDisplay();
        updateStats();
        displayBacklinks();
        generateTableOfContents();

        // Restore icon from properties
        if (currentNote.propertiesJson != null) {
            try {
                JSONObject props = new JSONObject(currentNote.propertiesJson);
                String emoji = props.optString("icon", "");
                if (!emoji.isEmpty() && noteIconEmoji != null) {
                    noteIconEmoji.setText(emoji);
                    noteIconEmoji.setVisibility(View.VISIBLE);
                }
            } catch (Exception ignored) {}
        }

        tvLastSaved.setText(LABEL_SAVED);
        tvLastSaved.setTextColor(Color.parseColor("#22C55E"));
    }

    /** Converts a legacy plain-text body into ContentBlocks (one-time migration). */
    private void migrateBodyToBlocks(String body) {
        if (body == null || body.isEmpty()) return;
        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            if (line.startsWith("[ ] ") || line.startsWith("[x] ")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_CHECKLIST);
                cb.setChecked(line.startsWith("[x] "));
                cb.setText(line.substring(4));
                blocks.add(cb);
            } else if (line.startsWith("• ") || line.startsWith("- ")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_BULLET);
                cb.setText(line.substring(2));
                blocks.add(cb);
            } else if (line.matches("^\\d+\\.\\s.*")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_NUMBERED);
                cb.setText(line.replaceFirst("^\\d+\\.\\s", ""));
                blocks.add(cb);
            } else if (line.startsWith("### ")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_HEADING3);
                cb.setText(line.substring(4));
                blocks.add(cb);
            } else if (line.startsWith("## ")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_HEADING2);
                cb.setText(line.substring(3));
                blocks.add(cb);
            } else if (line.startsWith("# ")) {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_HEADING1);
                cb.setText(line.substring(2));
                blocks.add(cb);
            } else if (line.matches("^[-─═]{3,}$")) {
                blocks.add(new ContentBlock(ContentBlock.TYPE_DIVIDER));
            } else {
                ContentBlock cb = new ContentBlock(ContentBlock.TYPE_TEXT);
                cb.setText(line);
                blocks.add(cb);
            }
        }
    }

    private void saveNote(boolean finish) {
        if (currentNote == null) return;

        String title = etTitle.getText().toString().trim();
        String bodyPlain = getBlocksPlainText();

        // Skip saving truly empty notes
        if (title.isEmpty() && bodyPlain.isEmpty() && blocks.size() <= 1) {
            if (finish) super.onBackPressed();
            return;
        }
        if (title.isEmpty()) title = "Untitled";

        currentNote.title = title;
        currentNote.body = bodyPlain;                               // plain text for search/cards
        currentNote.blocksJson = ContentBlock.toJsonArray(blocks).toString();  // full block data
        currentNote.plainTextPreview = getPlainTextPreview(bodyPlain);
        currentNote.updatedAt = System.currentTimeMillis();

        // Update mention tracking
        relationsManager.resolveBlockMentions(blocks, noteRepository);

        // Duplicate-note warning (first save only)
        if (isNewNote && !currentNote.title.isEmpty()) {
            List<Note> all = noteRepository.getAllNotes();
            Note similar = SmartNotesHelper.findSimilarNote(currentNote.title, all);
            if (similar != null && !similar.id.equals(currentNote.id)) {
                showDuplicateWarning(similar);
            }
        }

        if (isNewNote) {
            noteRepository.addNote(currentNote);
            isNewNote = false;
        } else {
            noteRepository.updateNote(currentNote);
        }

        if (!smartFeaturesShown && !isNewNote) {
            showSmartSuggestions();
            smartFeaturesShown = true;
        }

        hasUnsavedChanges = false;
        animateSaveIndicator();

        if (finish) {
            setResult(RESULT_OK);
            super.onBackPressed();
        }
    }

    /** Gather plain text from every block for search index & preview. */
    private String getBlocksPlainText() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            String t = b.getPlainText();
            if (t != null && !t.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private String getPlainTextPreview(String body) {
        String plain = body
                .replaceAll("\\[x\\]|\\[ \\]", "")
                .replaceAll("^#+\\s*", "")
                .replaceAll("^[\\-*•]\\s*", "")
                .replaceAll("^\\d+\\.\\s*", "")
                .replaceAll("---+", "")
                .trim();
        if (plain.length() > 150) plain = plain.substring(0, 150) + "...";
        return plain;
    }

    private void animateSaveIndicator() {
        tvLastSaved.setText(LABEL_SAVING);
        tvLastSaved.setTextColor(Color.parseColor("#94A3B8"));
        ValueAnimator pulse = ValueAnimator.ofFloat(1f, 0.4f, 1f);
        pulse.setDuration(600);
        pulse.addUpdateListener(a -> tvLastSaved.setAlpha((float) a.getAnimatedValue()));
        pulse.start();
        tvLastSaved.postDelayed(() -> {
            tvLastSaved.setText(LABEL_SAVED);
            tvLastSaved.setTextColor(Color.parseColor("#22C55E"));
            tvLastSaved.setAlpha(1f);
        }, 500);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BLOCK EDITOR LISTENER (16 callbacks)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onBlockChanged(int position, ContentBlock block) {
        hasUnsavedChanges = true;
        updateStats();
        tvLastSaved.setText(LABEL_EDITING);
        tvLastSaved.setTextColor(Color.parseColor("#F59E0B"));
    }

    @Override
    public void onBlockFocused(int position, ContentBlock block) {
        if (tvBlockTypeIndicator != null && block != null) {
            tvBlockTypeIndicator.setText(block.getTypeName());
            tvBlockTypeIndicator.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBlockInsertRequested(int afterPosition) {
        insertNewBlock(ContentBlock.TYPE_TEXT, afterPosition + 1);
    }

    @Override
    public void onBlockMenuRequested(int position, ContentBlock block, View anchor) {
        showBlockMenu(position, block, anchor);
    }

    @Override
    public void onBlockDragStarted(RecyclerView.ViewHolder holder) {
        // Drag is handled by ItemTouchHelper — could trigger manually here
    }

    @Override
    public void onEnterPressed(int position, ContentBlock block, int cursorPos) {
        pushUndoSnapshot();

        String text = block.getText();
        if (text == null) text = "";

        String before = cursorPos <= text.length() ? text.substring(0, cursorPos) : text;
        String after  = cursorPos <= text.length() ? text.substring(cursorPos) : "";

        block.setText(before);
        blockEditorAdapter.notifyItemChanged(position);

        ContentBlock newBlock = new ContentBlock(ContentBlock.TYPE_TEXT);
        newBlock.setText(after);
        blockEditorAdapter.insertBlock(position + 1, newBlock);

        // Focus the new block at start
        blocksRecyclerView.post(() -> {
            blockEditorAdapter.setFocusedPosition(position + 1);
            RecyclerView.ViewHolder vh =
                    blocksRecyclerView.findViewHolderForAdapterPosition(position + 1);
            if (vh instanceof BlockEditorAdapter.BlockViewHolder) {
                EditText et = ((BlockEditorAdapter.BlockViewHolder) vh).findEditText();
                if (et != null) { et.requestFocus(); et.setSelection(0); }
            }
        });

        hasUnsavedChanges = true;
        updateStats();
    }

    @Override
    public void onDeleteAtStart(int position, ContentBlock block) {
        if (position <= 0) return;
        pushUndoSnapshot();

        ContentBlock prev = blocks.get(position - 1);
        if (prev.isTextBased() && block.isTextBased()) {
            String prevText = prev.getText() != null ? prev.getText() : "";
            String curText  = block.getText() != null ? block.getText() : "";
            int mergePoint = prevText.length();
            prev.setText(prevText + curText);
            blockEditorAdapter.removeBlock(position);

            int target = position - 1;
            blocksRecyclerView.post(() -> {
                blockEditorAdapter.setFocusedPosition(target);
                RecyclerView.ViewHolder vh =
                        blocksRecyclerView.findViewHolderForAdapterPosition(target);
                if (vh instanceof BlockEditorAdapter.BlockViewHolder) {
                    EditText et = ((BlockEditorAdapter.BlockViewHolder) vh).findEditText();
                    if (et != null) {
                        et.requestFocus();
                        et.setSelection(Math.min(mergePoint, et.getText().length()));
                    }
                }
            });
        } else if (block.getText() == null || block.getText().isEmpty()) {
            blockEditorAdapter.removeBlock(position);
            blockEditorAdapter.setFocusedPosition(position - 1);
        }

        hasUnsavedChanges = true;
        updateStats();
    }

    @Override
    public void onImageBlockClicked(int position, ContentBlock block) {
        Toast.makeText(this, "Image picker coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFileBlockClicked(int position, ContentBlock block) {
        String path = block.getFilePath();
        if (path != null && !path.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(path)));
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "File attachment coming soon", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAudioPlayRequested(int position, ContentBlock block) {
        Toast.makeText(this, "Audio playback coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onVideoBlockClicked(int position, ContentBlock block) {
        Toast.makeText(this, "Video player coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDrawingBlockClicked(int position, ContentBlock block) {
        startActivity(new Intent(this, DrawingNoteActivity.class));
    }

    @Override
    public void onLocationBlockClicked(int position, ContentBlock block) {
        Toast.makeText(this, "Location picker coming soon", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLinkPreviewClicked(int position, ContentBlock block) {
        String url = block.getUrl();
        if (url != null && !url.isEmpty()) {
            try {
                if (!url.startsWith("http")) url = "https://" + url;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onToggleExpanded(int position, ContentBlock block, boolean expanded) {
        block.setCollapsed(!expanded);
        blockEditorAdapter.notifyItemChanged(position);
        hasUnsavedChanges = true;
    }

    @Override
    public void onChecklistToggled(int position, ContentBlock block, boolean checked) {
        block.setChecked(checked);
        hasUnsavedChanges = true;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BLOCK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void insertNewBlock(String type, int position) {
        pushUndoSnapshot();
        ContentBlock nb = new ContentBlock(type);
        int insertPos = Math.min(position, blocks.size());
        blockEditorAdapter.insertBlock(insertPos, nb);

        blocksRecyclerView.post(() -> {
            blockEditorAdapter.setFocusedPosition(insertPos);
            blocksRecyclerView.scrollToPosition(insertPos);
            RecyclerView.ViewHolder vh =
                    blocksRecyclerView.findViewHolderForAdapterPosition(insertPos);
            if (vh instanceof BlockEditorAdapter.BlockViewHolder) {
                EditText et = ((BlockEditorAdapter.BlockViewHolder) vh).findEditText();
                if (et != null) {
                    et.requestFocus();
                    InputMethodManager imm =
                            (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        hasUnsavedChanges = true;
        updateStats();
    }

    private void convertFocusedBlock(String newType) {
        int pos = blockEditorAdapter.getFocusedPosition();
        if (pos < 0 || pos >= blocks.size()) return;
        pushUndoSnapshot();

        ContentBlock block = blocks.get(pos);
        block.convertTo(block.blockType.equals(newType) ? ContentBlock.TYPE_TEXT : newType);
        blockEditorAdapter.notifyItemChanged(pos);
        hasUnsavedChanges = true;
    }

    private void showBlockMenu(int position, ContentBlock block, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Delete Block");
        popup.getMenu().add(0, 2, 1, "Duplicate Block");
        popup.getMenu().add(0, 3, 2, "Convert To…");
        popup.getMenu().add(0, 4, 3, "Move Up");
        popup.getMenu().add(0, 5, 4, "Move Down");
        if (block.isTextBased()) popup.getMenu().add(0, 6, 5, "Clear Formatting");
        popup.getMenu().add(0, 7, 6, "Add Block Above");
        popup.getMenu().add(0, 8, 7, "Add Block Below");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Delete
                    pushUndoSnapshot();
                    blockEditorAdapter.removeBlock(position);
                    if (blocks.isEmpty()) {
                        blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
                        blockEditorAdapter.notifyItemInserted(0);
                    }
                    hasUnsavedChanges = true;
                    updateStats();
                    return true;

                case 2: // Duplicate
                    pushUndoSnapshot();
                    blockEditorAdapter.insertBlock(position + 1, block.duplicate());
                    hasUnsavedChanges = true;
                    return true;

                case 3: // Convert
                    BlockPickerBottomSheet picker = BlockPickerBottomSheet.newInstance(position);
                    picker.setListener(type -> {
                        pushUndoSnapshot();
                        block.convertTo(type);
                        blockEditorAdapter.notifyItemChanged(position);
                        hasUnsavedChanges = true;
                    });
                    picker.show(getSupportFragmentManager(), "block_convert");
                    return true;

                case 4: // Move up
                    if (position > 0) { blockEditorAdapter.moveBlock(position, position - 1); hasUnsavedChanges = true; }
                    return true;

                case 5: // Move down
                    if (position < blocks.size() - 1) { blockEditorAdapter.moveBlock(position, position + 1); hasUnsavedChanges = true; }
                    return true;

                case 6: // Clear formatting
                    block.setText(block.getText());
                    blockEditorAdapter.notifyItemChanged(position);
                    hasUnsavedChanges = true;
                    return true;

                case 7: // Above
                    insertNewBlock(ContentBlock.TYPE_TEXT, position);
                    return true;

                case 8: // Below
                    insertNewBlock(ContentBlock.TYPE_TEXT, position + 1);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RICH TEXT FORMATTING (on focused block)
    // ═══════════════════════════════════════════════════════════════════════════════

    private void applySpanToFocused(Object span) {
        EditText et = blockEditorAdapter.getFocusedEditText(blocksRecyclerView);
        if (et == null) {
            Toast.makeText(this, "Tap a text block first", Toast.LENGTH_SHORT).show();
            return;
        }
        int start = et.getSelectionStart(), end = et.getSelectionEnd();
        if (start == end) {
            Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show();
            return;
        }
        et.getText().setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showHighlightColorPicker() {
        showColorPickerDialog("Highlight Color", color -> {
            EditText et = blockEditorAdapter.getFocusedEditText(blocksRecyclerView);
            if (et == null) return;
            int s = et.getSelectionStart(), e = et.getSelectionEnd();
            if (s != e) et.getText().setSpan(new BackgroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
    }

    private void showTextColorPicker() {
        showColorPickerDialog("Text Color", color -> {
            EditText et = blockEditorAdapter.getFocusedEditText(blocksRecyclerView);
            if (et == null) return;
            int s = et.getSelectionStart(), e = et.getSelectionEnd();
            if (s != e) et.getText().setSpan(new ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
    }

    private void showHeadingPicker() {
        String[] options = {"Heading 1", "Heading 2", "Heading 3", "Normal Text"};
        String[] types = {
                ContentBlock.TYPE_HEADING1, ContentBlock.TYPE_HEADING2,
                ContentBlock.TYPE_HEADING3, ContentBlock.TYPE_TEXT
        };
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Heading Style")
                .setItems(options, (d, w) -> convertFocusedBlock(types[w]))
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  COLOR PICKER (for formatting)
    // ═══════════════════════════════════════════════════════════════════════════════

    private interface ColorSelectedListener {
        void onColorSelected(int color);
    }

    private void showColorPickerDialog(String title, ColorSelectedListener listener) {
        String[] colors = {"#F59E0B", "#EF4444", "#22C55E", "#3B82F6",
                           "#8B5CF6", "#EC4899", "#06B6D4", "#F1F5F9"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        AlertDialog[] dRef = new AlertDialog[1];
        for (String hex : colors) {
            View cv = new View(this);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dpToPx(48); p.height = dpToPx(48);
            p.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            cv.setLayoutParams(p);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(hex));
            bg.setCornerRadius(dpToPx(8));
            cv.setBackground(bg);

            cv.setOnClickListener(v -> {
                listener.onColorSelected(Color.parseColor(hex));
                if (dRef[0] != null) dRef[0].dismiss();
            });
            grid.addView(cv);
        }

        builder.setView(grid);
        builder.setNegativeButton("Cancel", null);
        dRef[0] = builder.create();
        dRef[0].show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UNDO / REDO (block-snapshot)
    // ═══════════════════════════════════════════════════════════════════════════════

    private void pushUndoSnapshot() {
        String snap = ContentBlock.toJsonArray(blocks).toString();
        undoStack.push(snap);
        if (undoStack.size() > MAX_UNDO_STACK) undoStack.removeLast();
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.push(ContentBlock.toJsonArray(blocks).toString());
        restoreBlockSnapshot(undoStack.pop());
        updateUndoRedoButtons();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.push(ContentBlock.toJsonArray(blocks).toString());
        restoreBlockSnapshot(redoStack.pop());
        updateUndoRedoButtons();
    }

    private void restoreBlockSnapshot(String json) {
        blocks.clear();
        try {
            blocks.addAll(ContentBlock.fromJsonArray(json));
        } catch (Exception e) {
            blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
        }
        blockEditorAdapter.notifyDataSetChanged();
        hasUnsavedChanges = true;
        updateStats();
    }

    private void updateUndoRedoButtons() {
        btnUndo.setAlpha(undoStack.isEmpty() ? 0.3f : 1.0f);
        btnRedo.setAlpha(redoStack.isEmpty() ? 0.3f : 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UI UPDATES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void updateCategoryDisplay() {
        if (currentNote != null && currentNote.category != null) {
            tvCategory.setText(currentNote.category);
            tvCategory.setVisibility(View.VISIBLE);
        } else {
            tvCategory.setVisibility(View.GONE);
        }
    }

    private void updateTagsDisplay() {
        if (currentNote == null || currentNote.tags == null || currentNote.tags.isEmpty()) {
            tagsContainer.setVisibility(View.GONE);
            return;
        }
        tagsContainer.setVisibility(View.VISIBLE);
        tagsList.removeAllViews();

        for (String tag : currentNote.tags) {
            TextView chip = new TextView(this);
            chip.setText("#" + tag);
            chip.setTextColor(Color.parseColor("#94A3B8"));
            chip.setTextSize(12);
            chip.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1E293B"));
            bg.setCornerRadius(dpToPx(12));
            chip.setBackground(bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dpToPx(6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> showRemoveTagDialog(tag));
            tagsList.addView(chip);
        }
    }

    private void updatePinButton() {
        if (currentNote != null && currentNote.isPinned) {
            btnPin.setColorFilter(Color.parseColor("#F59E0B"));
        } else {
            btnPin.setColorFilter(Color.parseColor("#64748B"));
        }
    }

    private void updateLockButton() {
        if (currentNote != null && currentNote.isLocked) {
            btnLock.setColorFilter(Color.parseColor("#F59E0B"));
        } else {
            btnLock.setColorFilter(Color.parseColor("#64748B"));
        }
    }

    private void updateReminderDisplay() {
        if (currentNote != null && currentNote.reminderDateTime > 0) {
            reminderBanner.setVisibility(View.VISIBLE);
            btnReminder.setColorFilter(Color.parseColor("#F59E0B"));
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault());
            tvReminderTime.setText("Reminder: " + sdf.format(new Date(currentNote.reminderDateTime)));
        } else {
            reminderBanner.setVisibility(View.GONE);
            btnReminder.setColorFilter(Color.parseColor("#64748B"));
        }
    }

    private void updateStats() {
        String body = getBlocksPlainText();
        int charCount = body.length();
        int wordCount = body.trim().isEmpty() ? 0 : body.trim().split("\\s+").length;
        int readMin   = Math.max(1, wordCount / 200);

        tvCharCount.setText(charCount + " chars");
        tvWordCount.setText(wordCount + " words");
        tvReadTime.setText(readMin <= 1 ? "< 1 min read" : readMin + " min read");
        if (tvBlockCount != null) tvBlockCount.setText(blocks.size() + " blocks");

        if (wordCountGoal > 0) updateWordGoalProgress();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TOOLBAR ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void hideFormattingToolbar() {
        if (formattingToolbar == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(formattingToolbar, "translationY",
                0, formattingToolbar.getHeight() + dpToPx(32));
        anim.setDuration(200);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { isToolbarHidden = true; }
        });
        anim.start();
    }

    private void showFormattingToolbar() {
        if (formattingToolbar == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(formattingToolbar, "translationY",
                formattingToolbar.getTranslationY(), 0);
        anim.setDuration(200);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { isToolbarHidden = false; }
        });
        anim.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PIN / LOCK / REMINDER
    // ═══════════════════════════════════════════════════════════════════════════════

    private void togglePin() {
        if (currentNote == null) return;
        currentNote.isPinned = !currentNote.isPinned;
        updatePinButton();
        hasUnsavedChanges = true;
        Toast.makeText(this, currentNote.isPinned ? "Note pinned" : "Note unpinned",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleLock() {
        if (currentNote == null) return;
        if (currentNote.isLocked) {
            lockManager.verifyLock(currentNote, success -> {
                if (success) {
                    currentNote.isLocked = false;
                    updateLockButton();
                    hasUnsavedChanges = true;
                    Toast.makeText(this, "Note unlocked", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            lockManager.setupLock(currentNote, success -> {
                if (success) {
                    currentNote.isLocked = true;
                    updateLockButton();
                    hasUnsavedChanges = true;
                    Toast.makeText(this, "Note locked", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void checkNoteLock() {
        lockManager.verifyLock(currentNote, success -> {
            if (success) loadNote();
            else {
                Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showReminderPicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this, R.style.DarkDatePickerDialog,
                (view, year, month, day) -> {
                    Calendar sel = Calendar.getInstance();
                    sel.set(year, month, day);
                    TimePickerDialog tp = new TimePickerDialog(this, R.style.DarkTimePickerDialog,
                            (tv, h, m) -> { sel.set(Calendar.HOUR_OF_DAY, h); sel.set(Calendar.MINUTE, m); setReminder(sel.getTimeInMillis()); },
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false);
                    tp.show();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis());
        dp.show();
    }

    private void setReminder(long millis) {
        if (currentNote == null) return;
        currentNote.reminderDateTime = millis;
        updateReminderDisplay();
        hasUnsavedChanges = true;
        reminderManager.scheduleReminder(currentNote);
        Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show();
    }

    private void clearReminder() {
        if (currentNote == null) return;
        reminderManager.cancelReminder(currentNote);
        currentNote.reminderDateTime = 0;
        updateReminderDisplay();
        hasUnsavedChanges = true;
        Toast.makeText(this, "Reminder cleared", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TAGS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showAddTagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Add Tag");

        EditText input = new EditText(this);
        input.setHint("Enter tag name");
        input.setTextColor(Color.parseColor("#F1F5F9"));
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        builder.setView(input);

        builder.setPositiveButton("Add", (d, w) -> {
            String tag = input.getText().toString().trim();
            if (!tag.isEmpty()) addTag(tag);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();

        input.requestFocus();
        new Handler().postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void addTag(String tag) {
        if (currentNote == null) return;
        List<String> tags = currentNote.tags;
        if (tags == null) tags = new ArrayList<>();
        if (!tags.contains(tag)) {
            tags.add(tag);
            currentNote.tags = tags;
            updateTagsDisplay();
            hasUnsavedChanges = true;
        }
    }

    private void showRemoveTagDialog(String tag) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Remove Tag")
                .setMessage("Remove tag \"" + tag + "\"?")
                .setPositiveButton("Remove", (d, w) -> removeTag(tag))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeTag(String tag) {
        if (currentNote == null) return;
        if (currentNote.tags != null) {
            currentNote.tags.remove(tag);
            updateTagsDisplay();
            hasUnsavedChanges = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MORE MENU
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showMoreMenu() {
        PopupMenu popup = new PopupMenu(this, btnMoreMenu);
        popup.getMenu().add(0, 1,  0,  "Change Color");
        popup.getMenu().add(0, 2,  1,  "Change Category");
        popup.getMenu().add(0, 3,  2,  "Duplicate Note");
        popup.getMenu().add(0, 4,  3,  "Version History");
        popup.getMenu().add(0, 5,  4,  "Export as PDF");
        popup.getMenu().add(0, 6,  5,  "Share as Image");
        popup.getMenu().add(0, 7,  6,  "Archive Note");
        popup.getMenu().add(0, 8,  7,  "Delete Note");
        popup.getMenu().add(0, 9,  8,  "Use Template");
        popup.getMenu().add(0, 10, 9,  "Save as Template");
        popup.getMenu().add(0, 11, 10, "Set Writing Goal");
        popup.getMenu().add(0, 12, 11, "📅  Link to Calendar Event");
        popup.getMenu().add(0, 13, 12, "💰  Link to Expense");
        popup.getMenu().add(0, 14, 13, "📑  Table of Contents");
        popup.getMenu().add(0, 15, 14, "🔗  Add Relation");
        popup.getMenu().add(0, 16, 15, "🖼️  Add Cover Image");
        popup.getMenu().add(0, 17, 16, "😀  Set Icon");
        // ── Smart Intelligence (Prompt 3) ──
        popup.getMenu().add(0, 20, 17, "✍️  Writing Assistant");
        popup.getMenu().add(0, 21, 18, "📚  Study This Note");
        popup.getMenu().add(0, 22, 19, "🧘  Focus Mode");
        popup.getMenu().add(0, 23, 20, "🔒  Seal as Time Capsule");
        popup.getMenu().add(0, 24, 21, "📤  Share Read-Only");
        popup.getMenu().add(0, 25, 22, "🌐  Export as Web Page");
        popup.getMenu().add(0, 26, 23, "🗺️  Concept Map");
        popup.getMenu().add(0, 27, 24, "🧬  Note DNA");
        // ── Cross-Feature Integrations (Prompt 4) ──
        popup.getMenu().add(0, 30, 25, "✅  Extract Tasks to Task Manager");
        popup.getMenu().add(0, 31, 26, "📅  Create Calendar Event from Note");
        popup.getMenu().add(0, 32, 27, "📎  Attach from File Hub");
        popup.getMenu().add(0, 33, 28, "💰  Log Expense from Note");
        popup.getMenu().add(0, 34, 29, "🔐  Link Password Entry");
        popup.getMenu().add(0, 35, 30, "🔒  Move Images to Vault");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:  showNoteColorPicker(); return true;
                case 2:  showCategoryPicker();  return true;
                case 3:  duplicateNote();       return true;
                case 4:  showVersionHistory();  return true;
                case 5:  exportAsPdf();         return true;
                case 6:  shareAsImage();        return true;
                case 7:  archiveNote();         return true;
                case 8:  deleteNote();          return true;
                case 9:  showTemplatesPicker(); return true;
                case 10: saveCurrentNoteAsTemplate(); return true;
                case 11: setWordCountGoal();    return true;
                case 12: showLinkCalendarEventSheet(); return true;
                case 13: showLinkExpenseSheet();       return true;
                case 14: toggleTableOfContents();      return true;
                case 15: showAddRelationDialog();      return true;
                case 16:
                    Toast.makeText(this, "Cover image coming soon", Toast.LENGTH_SHORT).show();
                    return true;
                case 17: showEmojiPicker(); return true;
                // ── Smart Intelligence handlers ──
                case 20: openWritingAssistant(); return true;
                case 21: openStudyMode();        return true;
                case 22: openFocusMode();        return true;
                case 23: sealAsTimeCapsule();    return true;
                case 24: shareReadOnly();        return true;
                case 25: exportAsWebPage();      return true;
                case 26: openConceptMap();       return true;
                case 27: showNoteDNA();          return true;
                // ── Cross-Feature Integration handlers ──
                case 30: crossFeatureManager.extractTasksFromNote(this, currentNote); return true;
                case 31: crossFeatureManager.createCalendarEventFromNote(this, currentNote); return true;
                case 32: crossFeatureManager.openFileHubPicker(this, currentNote); return true;
                case 33: crossFeatureManager.logExpenseFromNote(this, currentNote); return true;
                case 34: crossFeatureManager.linkPasswordEntryToNote(this, currentNote); return true;
                case 35:
                    crossFeatureManager.moveImagesToVault(this, currentNote, updatedNote -> {
                        currentNote.blocksJson = updatedNote.blocksJson;
                        noteRepository.updateNote(currentNote);
                        blocks.clear();
                        if (currentNote.blocksJson != null) {
                            blocks.addAll(ContentBlock.fromJsonArray(currentNote.blocksJson));
                        }
                        if (blocks.isEmpty()) blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
                        blockEditorAdapter.notifyDataSetChanged();
                    });
                    return true;
            }
            return false;
        });
        popup.show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTE COLOR / CATEGORY PICKERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showNoteColorPicker() {
        String[] colors = Note.NOTE_COLORS;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Note Color");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        AlertDialog[] dRef = new AlertDialog[1];
        for (String hex : colors) {
            View cv = new View(this);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dpToPx(48); p.height = dpToPx(48);
            p.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
            cv.setLayoutParams(p);

            GradientDrawable bg = new GradientDrawable();
            int c = hex.equals("default") ? Color.parseColor("#1E293B") : Color.parseColor(hex);
            bg.setColor(c);
            bg.setCornerRadius(dpToPx(8));
            if (currentNote != null && hex.equals(currentNote.colorHex)) {
                bg.setStroke(dpToPx(3), Color.parseColor("#F59E0B"));
            }
            cv.setBackground(bg);

            cv.setOnClickListener(v -> {
                currentNote.colorHex = hex;
                hasUnsavedChanges = true;
                Toast.makeText(this, "Color changed", Toast.LENGTH_SHORT).show();
                if (dRef[0] != null) dRef[0].dismiss();
            });
            grid.addView(cv);
        }

        builder.setView(grid);
        builder.setNegativeButton("Cancel", null);
        dRef[0] = builder.create();
        dRef[0].show();
    }

    private void showCategoryPicker() {
        String[] categories = Note.DEFAULT_CATEGORIES;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Select Category")
                .setItems(categories, (d, w) -> {
                    currentNote.category = categories[w];
                    updateCategoryDisplay();
                    hasUnsavedChanges = true;
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DUPLICATE / VERSION HISTORY / EXPORT / ARCHIVE / DELETE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void duplicateNote() {
        if (currentNote == null) return;
        saveNote(false);

        Note dup = new Note();
        dup.title = currentNote.title + " (Copy)";
        dup.body = currentNote.body;
        dup.blocksJson = currentNote.blocksJson;
        dup.plainTextPreview = currentNote.plainTextPreview;
        dup.category = currentNote.category;
        dup.colorHex = currentNote.colorHex;
        dup.tags = currentNote.tags != null ? new ArrayList<>(currentNote.tags) : null;

        noteRepository.addNote(dup);
        Toast.makeText(this, "Note duplicated", Toast.LENGTH_SHORT).show();
        startActivity(createIntent(this, dup.id));
        finish();
    }

    private void showVersionHistory() {
        if (currentNote == null) return;
        List<NoteVersionManager.NoteVersion> versions = versionManager.getVersions(currentNote.id);
        if (versions.isEmpty()) {
            Toast.makeText(this, "No version history available", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[versions.size()];
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        for (int i = 0; i < versions.size(); i++) {
            NoteVersionManager.NoteVersion v = versions.get(i);
            String label = sdf.format(new Date(v.timestamp)) + "  (" + v.wordCount + " words)";
            if (v.hasBlocks()) label += " \uD83E\uDDE9"; // puzzle piece for block versions
            items[i] = label;
        }
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Version History")
                .setItems(items, (d, w) -> showVersionPreview(versions.get(w)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showVersionPreview(NoteVersionManager.NoteVersion version) {
        // Build preview from blocks if available, otherwise from body
        String preview;
        if (version.hasBlocks()) {
            try {
                List<ContentBlock> vBlocks =
                        ContentBlock.fromJsonArray(version.blocksJson);
                StringBuilder sb = new StringBuilder();
                for (ContentBlock b : vBlocks) {
                    String pt = b.getPlainText();
                    if (pt != null && !pt.isEmpty()) {
                        sb.append(b.getTypeName()).append(": ").append(pt).append("\n");
                    }
                }
                preview = sb.toString();
            } catch (Exception e) {
                preview = version.body;
            }
        } else {
            preview = version.body;
        }
        if (preview != null && preview.length() > 2000) preview = preview.substring(0, 2000) + "…";

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Version Preview")
                .setMessage(version.title + "\n\n" + preview)
                .setPositiveButton("Restore", (d, w) -> restoreVersion(version))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreVersion(NoteVersionManager.NoteVersion version) {
        pushUndoSnapshot();
        etTitle.setText(version.title);
        blocks.clear();

        if (version.hasBlocks()) {
            // Restore full block structure
            try {
                List<ContentBlock> restored =
                        ContentBlock.fromJsonArray(version.blocksJson);
                blocks.addAll(restored);
            } catch (Exception e) {
                Log.w(TAG, "Failed to restore blocks from version, falling back to body", e);
                migrateBodyToBlocks(version.body);
            }
        } else {
            // Legacy version — parse body text into blocks
            migrateBodyToBlocks(version.body);
        }

        if (blocks.isEmpty()) blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
        blockEditorAdapter.notifyDataSetChanged();
        hasUnsavedChanges = true;
        updateStats();
        Toast.makeText(this, "Version restored", Toast.LENGTH_SHORT).show();
    }

    private void exportAsPdf() {
        if (currentNote == null) return;
        saveNote(false);
        exportManager.exportAsPdf(currentNote, ok ->
                Toast.makeText(this, ok ? "PDF exported" : "Export failed", Toast.LENGTH_SHORT).show());
    }

    private void shareAsImage() {
        if (currentNote == null) return;
        saveNote(false);
        exportManager.shareAsImage(currentNote, ok -> {
            if (!ok) Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void archiveNote() {
        if (currentNote == null) return;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Archive Note")
                .setMessage("Move this note to archive?")
                .setPositiveButton("Archive", (d, w) -> {
                    currentNote.isArchived = true;
                    noteRepository.updateNote(currentNote);
                    Toast.makeText(this, "Note archived", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNote() {
        if (currentNote == null) return;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete Note")
                .setMessage("Move this note to trash?")
                .setPositiveButton("Delete", (d, w) -> {
                    currentNote.isTrashed = true;
                    currentNote.deletedAt = System.currentTimeMillis();
                    noteRepository.updateNote(currentNote);
                    relationsManager.removeNote(currentNote.id);
                    Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SMART FEATURES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showSmartSuggestions() {
        if (currentNote == null || smartBannerContainer == null) return;

        String text  = currentNote.body;
        String title = currentNote.title;

        String cat = SmartNotesHelper.suggestCategory(title, text);
        if (cat != null && !cat.equals(currentNote.category)) showCategorizationBanner(cat);

        List<String> tags = SmartNotesHelper.suggestTags(title, text, currentNote.tags);
        if (!tags.isEmpty()) showTagSuggestions(tags);
    }

    private void showCategorizationBanner(String suggestedCategory) {
        if (smartBannerContainer == null) return;

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(0xFF1E3A5F);
        banner.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        banner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvMsg = new TextView(this);
        tvMsg.setText("📁 This looks like a " + suggestedCategory + " note — Move to " + suggestedCategory + " folder?");
        tvMsg.setTextColor(0xFFE2E8F0);
        tvMsg.setTextSize(13);
        tvMsg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvMove = new TextView(this);
        tvMove.setText("Move");
        tvMove.setTextColor(0xFF60A5FA);
        tvMove.setTextSize(13);
        tvMove.setPadding(dpToPx(8), 0, dpToPx(4), 0);
        tvMove.setOnClickListener(v -> {
            currentNote.category = suggestedCategory;
            noteRepository.updateNote(currentNote);
            banner.setVisibility(View.GONE);
            Toast.makeText(this, "Moved to " + suggestedCategory, Toast.LENGTH_SHORT).show();
        });

        TextView tvDismiss = new TextView(this);
        tvDismiss.setText("✕");
        tvDismiss.setTextColor(0xFF64748B);
        tvDismiss.setTextSize(16);
        tvDismiss.setPadding(dpToPx(8), 0, 0, 0);
        tvDismiss.setOnClickListener(v -> banner.setVisibility(View.GONE));

        banner.addView(tvMsg);
        banner.addView(tvMove);
        banner.addView(tvDismiss);
        smartBannerContainer.addView(banner);
        smartBannerContainer.setVisibility(View.VISIBLE);
    }

    private void showTagSuggestions(List<String> tags) {
        if (smartBannerContainer == null) return;

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(0xFF1E3A5F);
        banner.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        banner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🏷️ Suggested: ");
        tvTitle.setTextColor(0xFF94A3B8);
        tvTitle.setTextSize(12);
        banner.addView(tvTitle);

        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        banner.addView(chipsRow);

        for (String tag : tags) {
            TextView chip = new TextView(this);
            chip.setText("#" + tag);
            chip.setTextColor(0xFF60A5FA);
            chip.setTextSize(13);
            chip.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            chip.setOnClickListener(v -> {
                if (currentNote.tags == null) currentNote.tags = new ArrayList<>();
                if (!currentNote.tags.contains(tag)) {
                    currentNote.tags.add(tag);
                    noteRepository.updateNote(currentNote);
                    Toast.makeText(this, "#" + tag + " added", Toast.LENGTH_SHORT).show();
                    chip.setTextColor(0xFF34D399);
                }
            });
            chipsRow.addView(chip);
        }

        smartBannerContainer.addView(banner);
        smartBannerContainer.setVisibility(View.VISIBLE);
    }

    private void showDuplicateWarning(Note similar) {
        if (smartBannerContainer == null) return;

        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(0xFF3D1F1F);
        banner.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        banner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvMsg = new TextView(this);
        tvMsg.setText("⚠️ Similar note exists — '" + similar.title + "'");
        tvMsg.setTextColor(0xFFE2E8F0);
        tvMsg.setTextSize(13);
        tvMsg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvView = new TextView(this);
        tvView.setText("View");
        tvView.setTextColor(0xFFFBBF24);
        tvView.setTextSize(13);
        tvView.setPadding(dpToPx(8), 0, dpToPx(4), 0);
        tvView.setOnClickListener(v -> startActivity(createIntent(this, similar.id)));

        TextView tvIgnore = new TextView(this);
        tvIgnore.setText("Ignore");
        tvIgnore.setTextColor(0xFF64748B);
        tvIgnore.setTextSize(13);
        tvIgnore.setPadding(dpToPx(4), 0, 0, 0);
        tvIgnore.setOnClickListener(v -> banner.setVisibility(View.GONE));

        banner.addView(tvMsg);
        banner.addView(tvView);
        banner.addView(tvIgnore);
        smartBannerContainer.addView(banner);
        smartBannerContainer.setVisibility(View.VISIBLE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showTemplatesPicker() {
        List<NoteTemplatesManager.NoteTemplate> templates = templatesManager.getAllTemplates();
        String[] names = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            NoteTemplatesManager.NoteTemplate t = templates.get(i);
            names[i] = t.name + (t.hasBlocks() ? " \uD83E\uDDE9" : ""); // puzzle piece emoji for block templates
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Use Template")
                .setItems(names, (d, w) -> {
                    NoteTemplatesManager.NoteTemplate sel = templates.get(w);
                    pushUndoSnapshot();
                    blocks.clear();

                    if (sel.hasBlocks()) {
                        // Block-based template — restore blocks directly
                        try {
                            List<ContentBlock> templateBlocks =
                                    ContentBlock.fromJsonArray(sel.blocksJson);
                            // Generate new IDs so blocks aren't shared with the template
                            for (ContentBlock b : templateBlocks) {
                                b.id = java.util.UUID.randomUUID().toString();
                            }
                            blocks.addAll(templateBlocks);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse template blocks, falling back to body", e);
                            String content = NoteTemplatesManager.applyTemplate(sel);
                            String plain = android.text.Html.fromHtml(content,
                                    android.text.Html.FROM_HTML_MODE_COMPACT).toString();
                            migrateBodyToBlocks(plain);
                        }
                    } else {
                        // Legacy HTML template — parse to blocks
                        String content = NoteTemplatesManager.applyTemplate(sel);
                        String plain = android.text.Html.fromHtml(content,
                                android.text.Html.FROM_HTML_MODE_COMPACT).toString();
                        migrateBodyToBlocks(plain);
                    }

                    // Apply [date] replacement in text blocks
                    String today = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date());
                    for (ContentBlock b : blocks) {
                        if (b.isTextBased() && b.getText() != null) {
                            b.setText(b.getText().replace("[date]", today));
                        }
                    }

                    if (blocks.isEmpty()) blocks.add(new ContentBlock(ContentBlock.TYPE_TEXT));
                    blockEditorAdapter.notifyDataSetChanged();
                    hasUnsavedChanges = true;
                    updateStats();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCurrentNoteAsTemplate() {
        if (currentNote == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Save as Template");

        EditText etName = new EditText(this);
        etName.setHint("Template name");
        etName.setText(currentNote.title);
        etName.setTextColor(Color.parseColor("#F1F5F9"));
        etName.setHintTextColor(Color.parseColor("#64748B"));
        etName.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        builder.setView(etName);

        builder.setPositiveButton("Save", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                // Save both blocks JSON and plain text fallback
                String blocksJson = null;
                try {
                    blocksJson = ContentBlock.toJsonArray(blocks).toString();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to serialize blocks for template", e);
                }
                templatesManager.saveCustomTemplate(name, getBlocksPlainText(), blocksJson);
                Toast.makeText(this, "Saved as template: " + name, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WORD COUNT GOAL
    // ═══════════════════════════════════════════════════════════════════════════════

    private void setWordCountGoal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Set Writing Goal");

        EditText etGoal = new EditText(this);
        etGoal.setHint("Target word count (e.g. 500)");
        etGoal.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (wordCountGoal > 0) etGoal.setText(String.valueOf(wordCountGoal));
        etGoal.setTextColor(Color.parseColor("#F1F5F9"));
        etGoal.setHintTextColor(Color.parseColor("#64748B"));
        etGoal.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        builder.setView(etGoal);

        builder.setPositiveButton("Set Goal", (d, w) -> {
            try {
                wordCountGoal = Integer.parseInt(etGoal.getText().toString().trim());
                updateWordGoalProgress();
            } catch (NumberFormatException ignored) {}
        });
        builder.setNeutralButton("Clear Goal", (d, w) -> {
            wordCountGoal = 0;
            if (wordGoalProgressLine != null) wordGoalProgressLine.setVisibility(View.GONE);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateWordGoalProgress() {
        if (wordCountGoal <= 0 || wordGoalProgressLine == null) return;

        String text = getBlocksPlainText();
        int currentWords = SmartNotesHelper.countWords(text);
        float progress = Math.min(1.0f, (float) currentWords / wordCountGoal);

        wordGoalProgressLine.setVisibility(View.VISIBLE);
        ViewGroup parent = (ViewGroup) wordGoalProgressLine.getParent();
        if (parent != null) {
            int parentWidth = parent.getWidth();
            ViewGroup.LayoutParams lp = wordGoalProgressLine.getLayoutParams();
            lp.width = (int) (parentWidth * progress);
            wordGoalProgressLine.setLayoutParams(lp);
        }

        wordGoalProgressLine.setBackgroundColor(progress >= 1.0f ? 0xFF34D399 : 0xFF3B82F6);

        if (progress >= 1.0f && !goalReached) {
            goalReached = true;
            Toast.makeText(this, "🎉 Writing goal reached!", Toast.LENGTH_LONG).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CROSS-FEATURE INTEGRATIONS (Calendar / Expense)
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showLinkCalendarEventSheet() {
        CalendarRepository calendarRepo = new CalendarRepository(this);
        List<CalendarEvent> events = calendarRepo.getAllEvents();

        if (events.isEmpty()) {
            Toast.makeText(this, "No calendar events found. Create events in the Calendar feature first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        int limit = Math.min(events.size(), 20);
        String[] labels = new String[limit];
        CalendarEvent[] limited = new CalendarEvent[limit];
        for (int i = 0; i < limit; i++) {
            CalendarEvent ev = events.get(i);
            limited[i] = ev;
            String date = ev.startDate != null ? ev.startDate : "";
            String time = ev.startTime != null ? " at " + ev.startTime : "";
            labels[i] = ev.title + " — " + date + time;
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Link to Calendar Event")
                .setItems(labels, (d, w) -> {
                    CalendarEvent sel = limited[w];
                    if (currentNote != null) {
                        currentNote.linkedCalendarEventId = sel.id;
                        hasUnsavedChanges = true;
                        sel.linkedNoteId = currentNote.id;
                        calendarRepo.updateEvent(sel);
                        saveNote(false);
                        Toast.makeText(this, "📅 Linked: " + sel.title, Toast.LENGTH_SHORT).show();
                        refreshLinkedCalendarChip();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLinkExpenseSheet() {
        ExpenseRepository expenseRepo = new ExpenseRepository(this);
        java.util.ArrayList<Expense> expenses = expenseRepo.loadAll();

        if (expenses.isEmpty()) {
            Toast.makeText(this, "No expenses found. Add expenses in the Expense Tracker first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        int limit = Math.min(expenses.size(), 20);
        String[] labels = new String[limit];
        Expense[] limited = new Expense[limit];
        for (int i = 0; i < limit; i++) {
            Expense exp = expenses.get(expenses.size() - 1 - i);
            limited[i] = exp;
            labels[i] = exp.category + " ₹" + String.format(Locale.US, "%.0f", exp.amount)
                    + " — " + SDF_MMM_D.format(new Date(exp.timestamp));
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Link to Expense")
                .setItems(labels, (d, w) -> {
                    Expense sel = limited[w];
                    if (currentNote != null) {
                        currentNote.linkedExpenseId = sel.id;
                        hasUnsavedChanges = true;
                        saveNote(false);
                        Toast.makeText(this,
                                "💰 Linked: " + sel.category + " ₹"
                                        + String.format(Locale.US, "%.0f", sel.amount),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshLinkedCalendarChip() {
        if (currentNote == null || currentNote.linkedCalendarEventId == null) return;
        CalendarRepository repo = new CalendarRepository(this);
        CalendarEvent ev = repo.getEventById(currentNote.linkedCalendarEventId);
        if (ev != null) Toast.makeText(this, "📅 Linked: " + ev.title, Toast.LENGTH_SHORT).show();
    }

    private void refreshLinkedExpenseChip() {
        if (currentNote == null || currentNote.linkedExpenseId == null) return;
        ExpenseRepository repo = new ExpenseRepository(this);
        for (Expense exp : repo.loadAll()) {
            if (exp.id.equals(currentNote.linkedExpenseId)) {
                Toast.makeText(this, "💰 Linked: " + exp.category + " ₹"
                        + String.format(Locale.US, "%.0f", exp.amount), Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TABLE OF CONTENTS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void generateTableOfContents() {
        if (tocContainer == null || tocList == null) return;
        tocList.removeAllViews();

        boolean hasHeadings = false;
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock b = blocks.get(i);
            boolean isH1 = ContentBlock.TYPE_HEADING1.equals(b.blockType);
            boolean isH2 = ContentBlock.TYPE_HEADING2.equals(b.blockType);
            boolean isH3 = ContentBlock.TYPE_HEADING3.equals(b.blockType);
            if (!isH1 && !isH2 && !isH3) continue;

            String text = b.getText();
            if (text == null || text.trim().isEmpty()) continue;
            hasHeadings = true;

            TextView entry = new TextView(this);
            entry.setText(text);
            entry.setTextSize(isH1 ? 15 : isH2 ? 14 : 13);
            entry.setTextColor(Color.parseColor("#94A3B8"));
            entry.setTypeface(null, isH1 ? Typeface.BOLD : Typeface.NORMAL);

            int indent = isH1 ? 0 : isH2 ? dpToPx(16) : dpToPx(32);
            entry.setPadding(indent, dpToPx(6), 0, dpToPx(6));

            final int blockPos = i;
            entry.setOnClickListener(v -> {
                // Scroll to that heading block
                RecyclerView.ViewHolder vh =
                        blocksRecyclerView.findViewHolderForAdapterPosition(blockPos);
                if (vh != null) {
                    vh.itemView.requestFocus();
                    editorScrollView.smoothScrollTo(0, vh.itemView.getTop());
                } else {
                    blocksRecyclerView.scrollToPosition(blockPos);
                }
            });
            tocList.addView(entry);
        }

        // Only show if the user toggled it on AND there are headings
        if (!hasHeadings && tocContainer.getVisibility() == View.VISIBLE) {
            tocContainer.setVisibility(View.GONE);
        }
    }

    private void toggleTableOfContents() {
        if (tocContainer == null) return;
        if (tocContainer.getVisibility() == View.VISIBLE) {
            tocContainer.setVisibility(View.GONE);
        } else {
            generateTableOfContents();
            tocContainer.setVisibility(View.VISIBLE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BACKLINKS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void displayBacklinks() {
        if (backlinksContainer == null || backlinksList == null || currentNote == null) return;
        backlinksList.removeAllViews();

        List<String> backlinkIds = relationsManager.getBacklinks(currentNote.id);
        // Also include explicit relations
        if (currentNote.relatedNoteIds != null) {
            for (String rid : currentNote.relatedNoteIds) {
                if (!backlinkIds.contains(rid)) backlinkIds.add(rid);
            }
        }

        if (backlinkIds.isEmpty()) {
            backlinksContainer.setVisibility(View.GONE);
            return;
        }

        backlinksContainer.setVisibility(View.VISIBLE);
        for (String id : backlinkIds) {
            Note linked = noteRepository.getNoteById(id);
            if (linked == null || linked.isTrashed) continue;

            TextView entry = new TextView(this);
            entry.setText("↗ " + linked.title);
            entry.setTextColor(Color.parseColor("#60A5FA"));
            entry.setTextSize(14);
            entry.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#0F172A"));
            bg.setCornerRadius(dpToPx(8));
            bg.setStroke(dpToPx(1), Color.parseColor("#1E293B"));
            entry.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
            entry.setLayoutParams(lp);

            entry.setOnClickListener(v -> startActivity(createIntent(this, linked.id)));
            backlinksList.addView(entry);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  EMOJI PICKER / NOTE ICON
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showEmojiPicker() {
        String[] emojis = {
                "📝", "📌", "⭐", "💡", "🔥", "❤️", "🎯", "📚",
                "🏠", "💼", "🎨", "🎵", "🌍", "🔬", "💻", "🎮"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Set Note Icon");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        AlertDialog[] dRef = new AlertDialog[1];
        for (String emoji : emojis) {
            TextView tv = new TextView(this);
            tv.setText(emoji);
            tv.setTextSize(24);
            tv.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dpToPx(56); p.height = dpToPx(56);
            p.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            tv.setLayoutParams(p);

            tv.setOnClickListener(v -> {
                if (noteIconEmoji != null) {
                    noteIconEmoji.setText(emoji);
                    noteIconEmoji.setVisibility(View.VISIBLE);
                }
                try {
                    JSONObject props = currentNote.propertiesJson != null
                            ? new JSONObject(currentNote.propertiesJson) : new JSONObject();
                    props.put("icon", emoji);
                    currentNote.propertiesJson = props.toString();
                    hasUnsavedChanges = true;
                } catch (Exception ignored) {}
                if (dRef[0] != null) dRef[0].dismiss();
            });
            grid.addView(tv);
        }

        builder.setView(grid);
        builder.setNeutralButton("Remove Icon", (d, w) -> {
            if (noteIconEmoji != null) noteIconEmoji.setVisibility(View.GONE);
            try {
                JSONObject props = currentNote.propertiesJson != null
                        ? new JSONObject(currentNote.propertiesJson) : new JSONObject();
                props.remove("icon");
                currentNote.propertiesJson = props.toString();
                hasUnsavedChanges = true;
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton("Cancel", null);
        dRef[0] = builder.create();
        dRef[0].show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTE RELATIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showAddRelationDialog() {
        if (currentNote == null) return;
        List<Note> all = noteRepository.getAllNotes();
        List<Note> available = new ArrayList<>();
        for (Note n : all) {
            if (!n.id.equals(currentNote.id) && !n.isTrashed) available.add(n);
        }
        if (available.isEmpty()) {
            Toast.makeText(this, "No other notes to link", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] titles = new String[available.size()];
        for (int i = 0; i < available.size(); i++) titles[i] = available.get(i).title;

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Link to Note")
                .setItems(titles, (d, w) -> {
                    Note target = available.get(w);
                    relationsManager.addRelation(currentNote.id, target.id);
                    if (currentNote.relatedNoteIds == null)
                        currentNote.relatedNoteIds = new ArrayList<>();
                    if (!currentNote.relatedNoteIds.contains(target.id))
                        currentNote.relatedNoteIds.add(target.id);
                    hasUnsavedChanges = true;
                    displayBacklinks();
                    Toast.makeText(this, "Linked to: " + target.title, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void hapticTick() {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SMART INTELLIGENCE — Prompt 3 Menu Handlers
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Opens the Writing Assistant bottom sheet with current note content. */
    private void openWritingAssistant() {
        saveNote(false);
        String content = getNoteContentAsText();
        WritingAssistantBottomSheet sheet = WritingAssistantBottomSheet.newInstance(
                currentNote.id, currentNote.title, content);
        sheet.show(getSupportFragmentManager(), "writing_assistant");
    }

    /** Launches study mode for the current note (flashcards/quiz). */
    private void openStudyMode() {
        saveNote(false);
        // Generate flashcards from this note first
        FlashcardManager fm = new FlashcardManager(this);
        if (currentNote.blocksJson != null) {
            try {
                List<ContentBlock> blocks = ContentBlock.fromJsonArray(currentNote.blocksJson);
                List<Flashcard> generated = fm.generateFromNote(currentNote, blocks);
                if (!generated.isEmpty()) fm.addCards(generated);
            } catch (Exception e) { /* ignore */ }
        }
        // Open study dashboard for this note
        Intent intent = new Intent(this, StudyDashboardActivity.class);
        intent.putExtra("noteId", currentNote.id);
        startActivity(intent);
    }

    /** Launches Focus Mode with the current note. */
    private void openFocusMode() {
        saveNote(false);
        Intent intent = new Intent(this, FocusModeActivity.class);
        intent.putExtra("noteId", currentNote.id);
        intent.putExtra("title", currentNote.title);
        intent.putExtra("body", currentNote.body != null ? currentNote.body : "");
        startActivity(intent);
    }

    /** Seals current note as a time capsule. */
    private void sealAsTimeCapsule() {
        saveNote(false);
        String[] labels = TimeCapsuleManager.DURATION_LABELS;
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🔒 Seal as Time Capsule")
                .setMessage("Lock this note and rediscover it in the future!")
                .setItems(labels, (d, which) -> {
                    TimeCapsuleManager mgr = new TimeCapsuleManager(this);
                    int days = TimeCapsuleManager.DURATION_DAYS[which];
                    mgr.createCapsuleIn(currentNote.id, currentNote.title, days,
                            "Time capsule of: " + currentNote.title);
                    TimeCapsuleNotificationReceiver.scheduleNotification(this,
                            currentNote.id, System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000);
                    Toast.makeText(this, "🔒 Note sealed for " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shares note as read-only via plain text or markdown. */
    private void shareReadOnly() {
        saveNote(false);
        NoteShareManager shareManager = new NoteShareManager(this);
        List<ContentBlock> blocks = null;
        if (currentNote.blocksJson != null) {
            try {
                blocks = ContentBlock.fromJsonArray(currentNote.blocksJson);
            } catch (Exception e) { /* ignore */ }
        }
        shareManager.shareAsText(currentNote, blocks);
    }

    /** Exports note as a standalone HTML web page. */
    private void exportAsWebPage() {
        saveNote(false);
        try {
            List<ContentBlock> blocks = null;
            if (currentNote.blocksJson != null) {
                blocks = ContentBlock.fromJsonArray(currentNote.blocksJson);
            }
            String html = NoteHtmlExporter.exportToHtml(currentNote, blocks);

            // Save to cache and share
            java.io.File cacheDir = new java.io.File(getCacheDir(), "html_exports");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            String fileName = (currentNote.title != null ? currentNote.title : "note")
                    .replaceAll("[^a-zA-Z0-9]", "_") + ".html";
            java.io.File htmlFile = new java.io.File(cacheDir, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(htmlFile);
            writer.write(html);
            writer.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", htmlFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/html");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share HTML"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the Concept Map showing this note's relations. */
    private void openConceptMap() {
        saveNote(false);
        Intent intent = new Intent(this, ConceptMapActivity.class);
        intent.putExtra("noteId", currentNote.id);
        startActivity(intent);
    }

    /** Shows the Note DNA fingerprint in a dialog. */
    private void showNoteDNA() {
        String content = (currentNote.title != null ? currentNote.title : "")
                + " " + (currentNote.body != null ? currentNote.body : "");
        NoteDNAView dnaView = new NoteDNAView(this);
        dnaView.setNoteContent(content);
        int size = dpToPx(200);
        dnaView.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        dnaView.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("🧬 Note DNA")
                .setMessage("Unique visual fingerprint of this note's content")
                .setView(dnaView)
                .setPositiveButton("OK", null)
                .show();
    }

    /** Extracts all note content as plain text for writing assistant analysis. */
    private String getNoteContentAsText() {
        StringBuilder sb = new StringBuilder();
        if (currentNote.title != null) sb.append(currentNote.title).append("\n");
        if (currentNote.blocksJson != null) {
            try {
                List<ContentBlock> blocks = ContentBlock.fromJsonArray(currentNote.blocksJson);
                for (ContentBlock b : blocks) {
                    if (b.content != null) sb.append(b.content).append("\n");
                }
            } catch (Exception e) {
                if (currentNote.body != null) sb.append(currentNote.body);
            }
        } else if (currentNote.body != null) {
            sb.append(currentNote.body);
        }
        return sb.toString();
    }

    // ── WritingAssistantListener implementation ──
    @Override
    public void onFocusModeRequested() { openFocusMode(); }

    @Override
    public void onExportHtmlRequested() { exportAsWebPage(); }

    @Override
    public void onSuggestionApplied(String suggestion) {
        Toast.makeText(this, "Suggestion noted: " + suggestion, Toast.LENGTH_SHORT).show();
    }
}
