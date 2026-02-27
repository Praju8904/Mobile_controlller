package com.prajwal.myfirstapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE EDITOR ACTIVITY — Full-featured rich text editor for notes
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Rich text formatting (bold, italic, underline, strikethrough, highlight, colors)
 * - Heading styles (H1, H2, H3)
 * - Lists: bullet, numbered, checklist
 * - Auto-save every 30 seconds with version history
 * - PIN/biometric lock per note
 * - Reminders with local notifications
 * - Export as PDF or styled image card
 * - Undo/Redo stack
 * - Word/char count and read time
 * - Auto-hiding toolbar on scroll
 */
public class NoteEditorActivity extends AppCompatActivity {

    private static final String TAG = "NoteEditorActivity";
    private static final String EXTRA_NOTE_ID = "note_id";
    private static final String EXTRA_NEW_NOTE = "new_note";
    private static final String EXTRA_CATEGORY = "category";

    // ═══ UI Elements ═══
    private LinearLayout topToolbar;
    private ImageButton btnBack, btnPin, btnLock, btnReminder, btnMoreMenu;
    private ScrollView editorScrollView;
    private LinearLayout editorContainer;
    private TextView tvCategory;
    private EditText etTitle, etBody;
    private LinearLayout tagsContainer, tagsList, reminderBanner;
    private TextView btnAddTag, tvReminderTime;
    private ImageButton btnClearReminder;
    private HorizontalScrollView formattingToolbar;
    private TextView tvWordCount, tvCharCount, tvReadTime, tvLastSaved;

    // Formatting buttons
    private ImageButton btnBold, btnItalic, btnUnderline, btnStrikethrough;
    private ImageButton btnHighlight, btnTextColor, btnHeading;
    private ImageButton btnBulletList, btnNumberedList, btnChecklist;
    private ImageButton btnAlignment, btnInsertDivider, btnCodeBlock;
    private ImageButton btnInsertImage, btnVoiceInput, btnSketch;
    private ImageButton btnUndo, btnRedo;

    // ═══ Data ═══
    private NoteRepository noteRepository;
    private Note currentNote;
    private boolean isNewNote;
    private String initialCategory;
    private boolean hasUnsavedChanges = false;
    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;
    private static final long AUTO_SAVE_INTERVAL = 30_000; // 30 seconds

    // ═══ Undo/Redo ═══
    private LinkedList<String> undoStack = new LinkedList<>();
    private LinkedList<String> redoStack = new LinkedList<>();
    private static final int MAX_UNDO_STACK = 50;
    private String lastSavedBodyContent = "";

    // ═══ Toolbar auto-hide ═══
    private int lastScrollY = 0;
    private boolean isToolbarHidden = false;

    // ═══ Managers ═══
    private NoteLockManager lockManager;
    private NoteReminderManager reminderManager;
    private NoteVersionManager versionManager;
    private NoteExportManager exportManager;

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
        if (category != null) {
            intent.putExtra(EXTRA_CATEGORY, category);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        noteRepository = new NoteRepository(this);

        // Initialize managers
        lockManager = new NoteLockManager(this);
        reminderManager = new NoteReminderManager(this);
        versionManager = new NoteVersionManager(this);
        exportManager = new NoteExportManager(this);

        // Parse intent
        Intent intent = getIntent();
        isNewNote = intent.getBooleanExtra(EXTRA_NEW_NOTE, true);
        initialCategory = intent.getStringExtra(EXTRA_CATEGORY);

        if (!isNewNote) {
            String noteId = intent.getStringExtra(EXTRA_NOTE_ID);
            if (noteId != null) {
                currentNote = noteRepository.getNoteById(noteId);
            }
        }

        initializeViews();
        setupListeners();
        setupAutoSave();

        if (currentNote != null) {
            // Check if note is locked - handle unlock flow
            if (currentNote.isLocked) {
                checkNoteLock();
            } else {
                loadNote();
            }
        } else {
            // Create new note
            currentNote = new Note();
            if (initialCategory != null) {
                currentNote.category = initialCategory;
            }
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
        topToolbar = findViewById(R.id.topToolbar);
        btnBack = findViewById(R.id.btnBack);
        btnPin = findViewById(R.id.btnPin);
        btnLock = findViewById(R.id.btnLock);
        btnReminder = findViewById(R.id.btnReminder);
        btnMoreMenu = findViewById(R.id.btnMoreMenu);

        // Editor
        editorScrollView = findViewById(R.id.editorScrollView);
        editorContainer = findViewById(R.id.editorContainer);
        tvCategory = findViewById(R.id.tvCategory);
        etTitle = findViewById(R.id.etTitle);
        etBody = findViewById(R.id.etBody);
        tagsContainer = findViewById(R.id.tagsContainer);
        tagsList = findViewById(R.id.tagsList);
        btnAddTag = findViewById(R.id.btnAddTag);
        reminderBanner = findViewById(R.id.reminderBanner);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        btnClearReminder = findViewById(R.id.btnClearReminder);

        // Status bar
        tvWordCount = findViewById(R.id.tvWordCount);
        tvCharCount = findViewById(R.id.tvCharCount);
        tvReadTime = findViewById(R.id.tvReadTime);
        tvLastSaved = findViewById(R.id.tvLastSaved);

        // Formatting toolbar
        formattingToolbar = findViewById(R.id.formattingToolbar);
        btnBold = findViewById(R.id.btnBold);
        btnItalic = findViewById(R.id.btnItalic);
        btnUnderline = findViewById(R.id.btnUnderline);
        btnStrikethrough = findViewById(R.id.btnStrikethrough);
        btnHighlight = findViewById(R.id.btnHighlight);
        btnTextColor = findViewById(R.id.btnTextColor);
        btnHeading = findViewById(R.id.btnHeading);
        btnBulletList = findViewById(R.id.btnBulletList);
        btnNumberedList = findViewById(R.id.btnNumberedList);
        btnChecklist = findViewById(R.id.btnChecklist);
        btnAlignment = findViewById(R.id.btnAlignment);
        btnInsertDivider = findViewById(R.id.btnInsertDivider);
        btnCodeBlock = findViewById(R.id.btnCodeBlock);
        btnInsertImage = findViewById(R.id.btnInsertImage);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        btnSketch = findViewById(R.id.btnSketch);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
    }

    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> {
            hapticTick();
            onBackPressed();
        });

        // Pin toggle
        btnPin.setOnClickListener(v -> {
            hapticTick();
            togglePin();
        });

        // Lock toggle
        btnLock.setOnClickListener(v -> {
            hapticTick();
            toggleLock();
        });

        // Reminder
        btnReminder.setOnClickListener(v -> {
            hapticTick();
            showReminderPicker();
        });

        // More menu
        btnMoreMenu.setOnClickListener(v -> {
            hapticTick();
            showMoreMenu();
        });

        // Add tag
        btnAddTag.setOnClickListener(v -> {
            hapticTick();
            showAddTagDialog();
        });

        // Clear reminder
        btnClearReminder.setOnClickListener(v -> {
            hapticTick();
            clearReminder();
        });

        // Text watchers for auto-save and stats
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasUnsavedChanges = true;
                updateStats();
                tvLastSaved.setText("Editing...");
                tvLastSaved.setTextColor(Color.parseColor("#F59E0B"));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etTitle.addTextChangedListener(textWatcher);
        etBody.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Save state for undo
                if (count > 0 || after > 0) {
                    String currentContent = s.toString();
                    if (!currentContent.equals(lastSavedBodyContent)) {
                        pushUndo(lastSavedBodyContent);
                        lastSavedBodyContent = currentContent;
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hasUnsavedChanges = true;
                updateStats();
                tvLastSaved.setText("Editing...");
                tvLastSaved.setTextColor(Color.parseColor("#F59E0B"));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Scroll listener for toolbar auto-hide
        editorScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int deltaY = scrollY - lastScrollY;
            if (Math.abs(deltaY) > 10) {
                if (deltaY > 0 && !isToolbarHidden) {
                    // Scrolling down - hide toolbar
                    hideFormattingToolbar();
                } else if (deltaY < 0 && isToolbarHidden) {
                    // Scrolling up - show toolbar
                    showFormattingToolbar();
                }
            }
            lastScrollY = scrollY;
        });

        // Formatting buttons
        setupFormattingButtons();
    }

    private void setupFormattingButtons() {
        btnBold.setOnClickListener(v -> {
            hapticTick();
            applyStyle(Typeface.BOLD);
        });

        btnItalic.setOnClickListener(v -> {
            hapticTick();
            applyStyle(Typeface.ITALIC);
        });

        btnUnderline.setOnClickListener(v -> {
            hapticTick();
            applyUnderline();
        });

        btnStrikethrough.setOnClickListener(v -> {
            hapticTick();
            applyStrikethrough();
        });

        btnHighlight.setOnClickListener(v -> {
            hapticTick();
            showHighlightColorPicker();
        });

        btnTextColor.setOnClickListener(v -> {
            hapticTick();
            showTextColorPicker();
        });

        btnHeading.setOnClickListener(v -> {
            hapticTick();
            showHeadingPicker();
        });

        btnBulletList.setOnClickListener(v -> {
            hapticTick();
            insertBulletItem();
        });

        btnNumberedList.setOnClickListener(v -> {
            hapticTick();
            insertNumberedItem();
        });

        btnChecklist.setOnClickListener(v -> {
            hapticTick();
            insertChecklistItem();
        });

        btnAlignment.setOnClickListener(v -> {
            hapticTick();
            showAlignmentPicker();
        });

        btnInsertDivider.setOnClickListener(v -> {
            hapticTick();
            insertDivider();
        });

        btnCodeBlock.setOnClickListener(v -> {
            hapticTick();
            insertCodeBlock();
        });

        btnInsertImage.setOnClickListener(v -> {
            hapticTick();
            // TODO: Implement image insertion
            Toast.makeText(this, "Image insertion coming soon", Toast.LENGTH_SHORT).show();
        });

        btnVoiceInput.setOnClickListener(v -> {
            hapticTick();
            // TODO: Implement voice input
            Toast.makeText(this, "Voice input coming soon", Toast.LENGTH_SHORT).show();
        });

        btnSketch.setOnClickListener(v -> {
            hapticTick();
            // TODO: Implement sketch
            Toast.makeText(this, "Sketching coming soon", Toast.LENGTH_SHORT).show();
        });

        btnUndo.setOnClickListener(v -> {
            hapticTick();
            performUndo();
        });

        btnRedo.setOnClickListener(v -> {
            hapticTick();
            performRedo();
        });
    }

    private void setupAutoSave() {
        autoSaveHandler = new Handler(Looper.getMainLooper());
        autoSaveRunnable = () -> {
            if (hasUnsavedChanges && currentNote != null) {
                saveNote(false);
                // Save version snapshot
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
        etBody.setText(currentNote.body);
        lastSavedBodyContent = currentNote.body;

        updateCategoryDisplay();
        updateTagsDisplay();
        updatePinButton();
        updateLockButton();
        updateReminderDisplay();
        updateStats();

        tvLastSaved.setText("Saved");
        tvLastSaved.setTextColor(Color.parseColor("#22C55E"));
    }

    private void saveNote(boolean finish) {
        if (currentNote == null) return;

        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString();

        // Don't save empty notes
        if (title.isEmpty() && body.isEmpty()) {
            if (finish) {
                super.onBackPressed();
            }
            return;
        }

        // Default title if empty
        if (title.isEmpty()) {
            title = "Untitled";
        }

        currentNote.title = title;
        currentNote.body = body;
        currentNote.plainTextPreview = getPlainTextPreview(body);
        currentNote.updatedAt = System.currentTimeMillis();

        if (isNewNote) {
            noteRepository.addNote(currentNote);
            isNewNote = false;
        } else {
            noteRepository.updateNote(currentNote);
        }

        hasUnsavedChanges = false;
        tvLastSaved.setText("Saved");
        tvLastSaved.setTextColor(Color.parseColor("#22C55E"));

        if (finish) {
            setResult(RESULT_OK);
            super.onBackPressed();
        }
    }

    private String getPlainTextPreview(String body) {
        // Strip markdown/formatting for preview
        String plain = body
                .replaceAll("\\[x\\]|\\[ \\]", "") // Checkboxes
                .replaceAll("^#+\\s*", "") // Headings
                .replaceAll("^[\\-*•]\\s*", "") // Bullets
                .replaceAll("^\\d+\\.\\s*", "") // Numbered
                .replaceAll("---+", "") // Dividers
                .replaceAll("```[\\s\\S]*?```", "[code]") // Code blocks
                .trim();

        if (plain.length() > 150) {
            plain = plain.substring(0, 150) + "...";
        }
        return plain;
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
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
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
        String body = etBody.getText().toString();
        int charCount = body.length();
        int wordCount = body.trim().isEmpty() ? 0 : body.trim().split("\\s+").length;
        int readTimeMinutes = Math.max(1, wordCount / 200); // Average reading speed

        tvCharCount.setText(charCount + " chars");
        tvWordCount.setText(wordCount + " words");

        if (readTimeMinutes == 1) {
            tvReadTime.setText("< 1 min read");
        } else {
            tvReadTime.setText(readTimeMinutes + " min read");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TOOLBAR ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void hideFormattingToolbar() {
        if (formattingToolbar == null) return;

        ObjectAnimator animator = ObjectAnimator.ofFloat(formattingToolbar, "translationY", 0, formattingToolbar.getHeight() + dpToPx(32));
        animator.setDuration(200);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isToolbarHidden = true;
            }
        });
        animator.start();
    }

    private void showFormattingToolbar() {
        if (formattingToolbar == null) return;

        ObjectAnimator animator = ObjectAnimator.ofFloat(formattingToolbar, "translationY", formattingToolbar.getTranslationY(), 0);
        animator.setDuration(200);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isToolbarHidden = false;
            }
        });
        animator.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  RICH TEXT FORMATTING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void applyStyle(int style) {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();

        if (start == end) {
            Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show();
            return;
        }

        Editable editable = etBody.getText();
        editable.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void applyUnderline() {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();

        if (start == end) {
            Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show();
            return;
        }

        Editable editable = etBody.getText();
        editable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void applyStrikethrough() {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();

        if (start == end) {
            Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show();
            return;
        }

        Editable editable = etBody.getText();
        editable.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showHighlightColorPicker() {
        showColorPicker("Highlight Color", color -> {
            int start = etBody.getSelectionStart();
            int end = etBody.getSelectionEnd();

            if (start == end) {
                Toast.makeText(this, "Select text to highlight", Toast.LENGTH_SHORT).show();
                return;
            }

            Editable editable = etBody.getText();
            editable.setSpan(new BackgroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
    }

    private void showTextColorPicker() {
        showColorPicker("Text Color", color -> {
            int start = etBody.getSelectionStart();
            int end = etBody.getSelectionEnd();

            if (start == end) {
                Toast.makeText(this, "Select text to color", Toast.LENGTH_SHORT).show();
                return;
            }

            Editable editable = etBody.getText();
            editable.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
    }

    private interface ColorSelectedListener {
        void onColorSelected(int color);
    }

    private void showColorPicker(String title, ColorSelectedListener listener) {
        String[] colors = {
                "#F59E0B", "#EF4444", "#22C55E", "#3B82F6",
                "#8B5CF6", "#EC4899", "#06B6D4", "#F1F5F9"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        for (String colorHex : colors) {
            View colorView = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dpToPx(48);
            params.height = dpToPx(48);
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            colorView.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(colorHex));
            bg.setCornerRadius(dpToPx(8));
            colorView.setBackground(bg);

            colorView.setOnClickListener(v -> {
                listener.onColorSelected(Color.parseColor(colorHex));
                // Dismiss dialog - handled by dialog builder
            });

            grid.addView(colorView);
        }

        builder.setView(grid);
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showHeadingPicker() {
        String[] options = {"Heading 1", "Heading 2", "Heading 3", "Normal"};
        float[] sizes = {1.5f, 1.3f, 1.15f, 1.0f};

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Heading Style");
        builder.setItems(options, (dialog, which) -> {
            int start = etBody.getSelectionStart();
            int end = etBody.getSelectionEnd();

            if (start == end) {
                Toast.makeText(this, "Select text to change heading", Toast.LENGTH_SHORT).show();
                return;
            }

            Editable editable = etBody.getText();
            editable.setSpan(new RelativeSizeSpan(sizes[which]), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (which < 3) {
                editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        });
        builder.show();
    }

    private void showAlignmentPicker() {
        // Note: Standard EditText doesn't support inline alignment spans
        // This would require a custom rich text view. For now, show a placeholder.
        Toast.makeText(this, "Text alignment requires custom view (coming soon)", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIST FORMATTING
    // ═══════════════════════════════════════════════════════════════════════════════

    private void insertBulletItem() {
        int cursor = etBody.getSelectionStart();
        Editable editable = etBody.getText();

        // Find start of current line
        int lineStart = cursor;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        // Check if line already has a bullet
        String linePrefix = editable.subSequence(lineStart, Math.min(lineStart + 2, editable.length())).toString();
        if (!linePrefix.startsWith("• ")) {
            editable.insert(lineStart, "• ");
        }
    }

    private void insertNumberedItem() {
        int cursor = etBody.getSelectionStart();
        Editable editable = etBody.getText();

        // Count existing numbered items
        String text = editable.toString();
        int number = 1;
        int searchPos = cursor - 1;

        while (searchPos > 0) {
            int lineStart = searchPos;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }

            String line = text.substring(lineStart, Math.min(lineStart + 10, text.length()));
            if (line.matches("^\\d+\\.\\s.*")) {
                number = Integer.parseInt(line.split("\\.")[0]) + 1;
                break;
            }

            searchPos = lineStart - 1;
        }

        // Find start of current line
        int lineStart = cursor;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        editable.insert(lineStart, number + ". ");
    }

    private void insertChecklistItem() {
        int cursor = etBody.getSelectionStart();
        Editable editable = etBody.getText();

        // Find start of current line
        int lineStart = cursor;
        while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        // Check if line already has a checkbox
        String linePrefix = "";
        if (lineStart + 4 <= editable.length()) {
            linePrefix = editable.subSequence(lineStart, lineStart + 4).toString();
        }

        if (linePrefix.startsWith("[ ] ") || linePrefix.startsWith("[x] ")) {
            // Toggle the checkbox
            if (linePrefix.startsWith("[ ] ")) {
                editable.replace(lineStart, lineStart + 4, "[x] ");
            } else {
                editable.replace(lineStart, lineStart + 4, "[ ] ");
            }
        } else {
            editable.insert(lineStart, "[ ] ");
        }
    }

    private void insertDivider() {
        int cursor = etBody.getSelectionStart();
        Editable editable = etBody.getText();

        String divider = "\n───────────────────\n";
        editable.insert(cursor, divider);
    }

    private void insertCodeBlock() {
        int start = etBody.getSelectionStart();
        int end = etBody.getSelectionEnd();
        Editable editable = etBody.getText();

        if (start == end) {
            editable.insert(cursor(), "\n```\n\n```\n");
            etBody.setSelection(cursor() - 4);
        } else {
            String selected = editable.subSequence(start, end).toString();
            editable.replace(start, end, "```\n" + selected + "\n```");
        }
    }

    private int cursor() {
        return etBody.getSelectionStart();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UNDO / REDO
    // ═══════════════════════════════════════════════════════════════════════════════

    private void pushUndo(String content) {
        undoStack.push(content);
        if (undoStack.size() > MAX_UNDO_STACK) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentContent = etBody.getText().toString();
        redoStack.push(currentContent);

        String previousContent = undoStack.pop();
        etBody.setText(previousContent);
        etBody.setSelection(previousContent.length());

        lastSavedBodyContent = previousContent;
        updateUndoRedoButtons();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentContent = etBody.getText().toString();
        undoStack.push(currentContent);

        String redoContent = redoStack.pop();
        etBody.setText(redoContent);
        etBody.setSelection(redoContent.length());

        lastSavedBodyContent = redoContent;
        updateUndoRedoButtons();
    }

    private void updateUndoRedoButtons() {
        btnUndo.setAlpha(undoStack.isEmpty() ? 0.3f : 1.0f);
        btnRedo.setAlpha(redoStack.isEmpty() ? 0.3f : 1.0f);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PIN / LOCK / REMINDER
    // ═══════════════════════════════════════════════════════════════════════════════

    private void togglePin() {
        if (currentNote == null) return;

        currentNote.isPinned = !currentNote.isPinned;
        updatePinButton();
        hasUnsavedChanges = true;

        String msg = currentNote.isPinned ? "Note pinned" : "Note unpinned";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toggleLock() {
        if (currentNote == null) return;

        if (currentNote.isLocked) {
            // Unlock - verify first
            lockManager.verifyLock(currentNote, success -> {
                if (success) {
                    currentNote.isLocked = false;
                    updateLockButton();
                    hasUnsavedChanges = true;
                    Toast.makeText(this, "Note unlocked", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Lock - set up if needed
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
            if (success) {
                loadNote();
            } else {
                // User cancelled or failed - go back
                Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showReminderPicker() {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(this, R.style.DarkDatePickerDialog,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);

                    TimePickerDialog timePicker = new TimePickerDialog(this, R.style.DarkTimePickerDialog,
                            (timeView, hourOfDay, minute) -> {
                                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                selectedDate.set(Calendar.MINUTE, minute);

                                setReminder(selectedDate.getTimeInMillis());
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false);
                    timePicker.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
        datePicker.show();
    }

    private void setReminder(long timeMillis) {
        if (currentNote == null) return;

        currentNote.reminderDateTime = timeMillis;
        updateReminderDisplay();
        hasUnsavedChanges = true;

        // Schedule notification
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

        builder.setPositiveButton("Add", (dialog, which) -> {
            String tag = input.getText().toString().trim();
            if (!tag.isEmpty()) {
                addTag(tag);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();

        // Show keyboard
        input.requestFocus();
        new Handler().postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void addTag(String tag) {
        if (currentNote == null) return;

        List<String> tags = currentNote.tags;
        if (tags == null) {
            tags = new ArrayList<>();
        }

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
                .setPositiveButton("Remove", (dialog, which) -> {
                    removeTag(tag);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeTag(String tag) {
        if (currentNote == null) return;

        List<String> tags = currentNote.tags;
        if (tags != null) {
            tags.remove(tag);
            currentNote.tags = tags;
            updateTagsDisplay();
            hasUnsavedChanges = true;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MORE MENU
    // ═══════════════════════════════════════════════════════════════════════════════

    private void showMoreMenu() {
        PopupMenu popup = new PopupMenu(this, btnMoreMenu);
        popup.getMenu().add(0, 1, 0, "Change Color");
        popup.getMenu().add(0, 2, 1, "Change Category");
        popup.getMenu().add(0, 3, 2, "Duplicate Note");
        popup.getMenu().add(0, 4, 3, "Version History");
        popup.getMenu().add(0, 5, 4, "Export as PDF");
        popup.getMenu().add(0, 6, 5, "Share as Image");
        popup.getMenu().add(0, 7, 6, "Archive Note");
        popup.getMenu().add(0, 8, 7, "Delete Note");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showColorPicker();
                    return true;
                case 2:
                    showCategoryPicker();
                    return true;
                case 3:
                    duplicateNote();
                    return true;
                case 4:
                    showVersionHistory();
                    return true;
                case 5:
                    exportAsPdf();
                    return true;
                case 6:
                    shareAsImage();
                    return true;
                case 7:
                    archiveNote();
                    return true;
                case 8:
                    deleteNote();
                    return true;
            }
            return false;
        });

        popup.show();
    }

    private void showColorPicker() {
        String[] colors = Note.NOTE_COLORS;
        String[] colorNames = {"Default", "Yellow", "Orange", "Red", "Pink", "Purple", "Blue", "Teal", "Green", "Gray"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Note Color");

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        AlertDialog[] dialogRef = new AlertDialog[1];

        for (int i = 0; i < colors.length; i++) {
            View colorView = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dpToPx(48);
            params.height = dpToPx(48);
            params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
            colorView.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            int color = colors[i].equals("default") ? Color.parseColor("#1E293B") : Color.parseColor(colors[i]);
            bg.setColor(color);
            bg.setCornerRadius(dpToPx(8));

            // Add border if currently selected
            if (currentNote != null && colors[i].equals(currentNote.colorHex)) {
                bg.setStroke(dpToPx(3), Color.parseColor("#F59E0B"));
            }

            colorView.setBackground(bg);

            final String colorHex = colors[i];
            colorView.setOnClickListener(v -> {
                currentNote.colorHex = colorHex;
                hasUnsavedChanges = true;
                Toast.makeText(this, "Color changed", Toast.LENGTH_SHORT).show();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });

            grid.addView(colorView);
        }

        builder.setView(grid);
        builder.setNegativeButton("Cancel", null);
        dialogRef[0] = builder.create();
        dialogRef[0].show();
    }

    private void showCategoryPicker() {
        String[] categories = Note.DEFAULT_CATEGORIES;

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> {
                    currentNote.category = categories[which];
                    updateCategoryDisplay();
                    hasUnsavedChanges = true;
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void duplicateNote() {
        if (currentNote == null) return;

        saveNote(false);

        Note duplicate = new Note();
        duplicate.title = currentNote.title + " (Copy)";
        duplicate.body = currentNote.body;
        duplicate.plainTextPreview = currentNote.plainTextPreview;
        duplicate.category = currentNote.category;
        duplicate.colorHex = currentNote.colorHex;
        duplicate.tags = currentNote.tags != null ? new ArrayList<>(currentNote.tags) : null;

        noteRepository.addNote(duplicate);

        Toast.makeText(this, "Note duplicated", Toast.LENGTH_SHORT).show();

        // Open the duplicate
        Intent intent = createIntent(this, duplicate.id);
        startActivity(intent);
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
            items[i] = sdf.format(new Date(versions.get(i).timestamp));
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Version History")
                .setItems(items, (dialog, which) -> {
                    showVersionPreview(versions.get(which));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showVersionPreview(NoteVersionManager.NoteVersion version) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Version Preview")
                .setMessage(version.title + "\n\n" + version.body)
                .setPositiveButton("Restore", (dialog, which) -> {
                    restoreVersion(version);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreVersion(NoteVersionManager.NoteVersion version) {
        // Save current as undo
        pushUndo(etBody.getText().toString());

        etTitle.setText(version.title);
        etBody.setText(version.body);

        hasUnsavedChanges = true;
        Toast.makeText(this, "Version restored", Toast.LENGTH_SHORT).show();
    }

    private void exportAsPdf() {
        if (currentNote == null) return;

        saveNote(false);
        exportManager.exportAsPdf(currentNote, success -> {
            if (success) {
                Toast.makeText(this, "PDF exported", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareAsImage() {
        if (currentNote == null) return;

        saveNote(false);
        exportManager.shareAsImage(currentNote, success -> {
            if (success) {
                // Share intent handled by export manager
            } else {
                Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void archiveNote() {
        if (currentNote == null) return;

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Archive Note")
                .setMessage("Move this note to archive?")
                .setPositiveButton("Archive", (dialog, which) -> {
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
                .setPositiveButton("Delete", (dialog, which) -> {
                    currentNote.isTrashed = true;
                    currentNote.deletedAt = System.currentTimeMillis();
                    noteRepository.updateNote(currentNote);
                    Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
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
}
