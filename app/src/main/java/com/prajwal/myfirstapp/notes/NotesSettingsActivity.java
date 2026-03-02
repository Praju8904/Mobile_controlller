package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.todo.TodoRepository;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * NotesSettingsActivity — comprehensive Notes & To-Do settings screen.
 *
 * Sections: Appearance · Editor · Study Mode · Organization · Security
 *           To-Do · Notifications · Import & Export · About
 */
public class NotesSettingsActivity extends Activity {

    private NotesSettings settings;

    // ─── Appearance ───────────────────────────────────────────────
    private TextView itemDefaultNoteColor;
    private TextView itemBackgroundTexture;
    private TextView itemDefaultView;
    private TextView itemGridColumns;
    private TextView itemPreviewLines;
    private Switch   switchShowTags;
    private Switch   switchShowFolderBadge;
    private Switch   switchShowTimestamps;
    private Switch   switchShowWordCountCards;
    private TextView itemAccentColor;

    // ─── Editor ───────────────────────────────────────────────────
    private TextView itemDefaultBlockType;
    private TextView itemAutosaveInterval;
    private TextView itemFontSize;
    private TextView itemFontFamily;
    private Switch   switchAutoCapitalize;
    private Switch   switchSpellCheck;
    private TextView itemToolbarVisibility;
    private Switch   switchBlockHandles;
    private Switch   switchTypewriterMode;
    private TextView itemAmbientSounds;
    private Switch   switchWordCount;
    private Switch   switchReadingTime;
    private TextView itemChecklistBehavior;

    // ─── Study Mode ───────────────────────────────────────────────
    private TextView itemSpacedRep;
    private TextView itemReviewReminderTime;
    private TextView itemQuizDifficulty;
    private TextView itemFlashcardAnimation;
    private TextView itemStudySession;
    private TextView itemBreakDuration;

    // ─── Organization ─────────────────────────────────────────────
    private TextView itemDefaultSort;
    private TextView itemDefaultCategory;
    private Switch   switchAutoCateg;
    private Switch   switchSmartTags;
    private Switch   switchDuplicateDetect;
    private Switch   switchSmartReminders;
    private Switch   switchAutoRelationship;
    private Switch   switchMoodContext;
    private Switch   switchWeatherContext;

    // ─── Security ─────────────────────────────────────────────────
    private Switch   switchAppLock;
    private TextView itemBiometricPref;
    private TextView itemNoteLockPref;
    private TextView itemAutoLockAfter;
    private Switch   switchScreenshotProtection;

    // ─── To-Do ────────────────────────────────────────────────────
    private TextView itemDefaultPriority;
    private TextView itemDefaultDueTime;
    private TextView itemOverdueNotifTime;
    private Switch   switchDailyDigest;
    private TextView itemDigestTime;
    private Switch   switchRecurringAutoCreate;
    private Switch   switchCompletedAutoArchive;

    // ─── Notifications ────────────────────────────────────────────
    private Switch   switchNoteReminders;
    private Switch   switchTodoReminders;
    private Switch   switchStudyReviewReminder;
    private TextView itemStudyReviewReminderTime;
    private Switch   switchWeeklyNoteDigest;
    private Switch   switchTimeCapsuleNotif;
    private Switch   switchQuickCapture;
    private TextView itemNotifSound;
    private Switch   switchNotifVibration;

    // ─── Import & Export ──────────────────────────────────────────
    private TextView itemTrashAutoDelete;
    private TextView itemArchiveNotes;
    private TextView itemExportJson;
    private TextView itemExportZip;
    private TextView itemExportPdf;
    private TextView itemImportJson;
    private TextView itemImportEnex;
    private TextView itemImportNotion;
    private TextView itemClearAllNotes;
    private TextView itemClearCompletedTodos;
    private TextView itemResetSettings;

    // ─── About ────────────────────────────────────────────────────
    private TextView tvAboutStats;
    private TextView tvAboutVersion;
    private TextView tvAboutStorage;
    private TextView itemRateApp;
    private TextView itemSendFeedback;
    private TextView itemKeyboardShortcuts;

    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_settings);

        settings = NotesSettings.getInstance(this);

        applyScreenshotProtection();
        bindViews();
        loadSettings();
        setupListeners();
    }

    private void applyScreenshotProtection() {
        if (settings.isScreenshotProtection()) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    // ─── View binding ─────────────────────────────────────────────

    private void bindViews() {
        findViewById(R.id.btnBackNotesSettings)
                .setOnClickListener(v -> finish());

        // Appearance
        itemDefaultNoteColor      = findViewById(R.id.itemDefaultNoteColor);
        itemBackgroundTexture     = findViewById(R.id.itemBackgroundTexture);
        itemDefaultView           = findViewById(R.id.itemDefaultView);
        itemGridColumns           = findViewById(R.id.itemGridColumns);
        itemPreviewLines          = findViewById(R.id.itemPreviewLines);
        switchShowTags            = findViewById(R.id.switchShowTags);
        switchShowFolderBadge     = findViewById(R.id.switchShowFolderBadge);
        switchShowTimestamps      = findViewById(R.id.switchShowTimestamps);
        switchShowWordCountCards  = findViewById(R.id.switchShowWordCountCards);
        itemAccentColor           = findViewById(R.id.itemAccentColor);

        // Editor
        itemDefaultBlockType      = findViewById(R.id.itemDefaultBlockType);
        itemAutosaveInterval      = findViewById(R.id.itemAutosaveInterval);
        itemFontSize              = findViewById(R.id.itemFontSize);
        itemFontFamily            = findViewById(R.id.itemFontFamily);
        switchAutoCapitalize      = findViewById(R.id.switchAutoCapitalize);
        switchSpellCheck          = findViewById(R.id.switchSpellCheck);
        itemToolbarVisibility     = findViewById(R.id.itemToolbarVisibility);
        switchBlockHandles        = findViewById(R.id.switchBlockHandles);
        switchTypewriterMode      = findViewById(R.id.switchTypewriterMode);
        itemAmbientSounds         = findViewById(R.id.itemAmbientSounds);
        switchWordCount           = findViewById(R.id.switchWordCount);
        switchReadingTime         = findViewById(R.id.switchReadingTime);
        itemChecklistBehavior     = findViewById(R.id.itemChecklistBehavior);

        // Study Mode
        itemSpacedRep             = findViewById(R.id.itemSpacedRep);
        itemReviewReminderTime    = findViewById(R.id.itemReviewReminderTime);
        itemQuizDifficulty        = findViewById(R.id.itemQuizDifficulty);
        itemFlashcardAnimation    = findViewById(R.id.itemFlashcardAnimation);
        itemStudySession          = findViewById(R.id.itemStudySession);
        itemBreakDuration         = findViewById(R.id.itemBreakDuration);

        // Organization
        itemDefaultSort           = findViewById(R.id.itemDefaultSort);
        itemDefaultCategory       = findViewById(R.id.itemDefaultCategory);
        switchAutoCateg           = findViewById(R.id.switchAutoCateg);
        switchSmartTags           = findViewById(R.id.switchSmartTags);
        switchDuplicateDetect     = findViewById(R.id.switchDuplicateDetect);
        switchSmartReminders      = findViewById(R.id.switchSmartReminders);
        switchAutoRelationship    = findViewById(R.id.switchAutoRelationship);
        switchMoodContext         = findViewById(R.id.switchMoodContext);
        switchWeatherContext      = findViewById(R.id.switchWeatherContext);

        // Security
        switchAppLock             = findViewById(R.id.switchAppLock);
        itemBiometricPref         = findViewById(R.id.itemBiometricPref);
        itemNoteLockPref          = findViewById(R.id.itemNoteLockPref);
        itemAutoLockAfter         = findViewById(R.id.itemAutoLockAfter);
        switchScreenshotProtection = findViewById(R.id.switchScreenshotProtection);

        // To-Do
        itemDefaultPriority       = findViewById(R.id.itemDefaultPriority);
        itemDefaultDueTime        = findViewById(R.id.itemDefaultDueTime);
        itemOverdueNotifTime      = findViewById(R.id.itemOverdueNotifTime);
        switchDailyDigest         = findViewById(R.id.switchDailyDigest);
        itemDigestTime            = findViewById(R.id.itemDigestTime);
        switchRecurringAutoCreate  = findViewById(R.id.switchRecurringAutoCreate);
        switchCompletedAutoArchive = findViewById(R.id.switchCompletedAutoArchive);

        // Notifications
        switchNoteReminders          = findViewById(R.id.switchNoteReminders);
        switchTodoReminders          = findViewById(R.id.switchTodoReminders);
        switchStudyReviewReminder    = findViewById(R.id.switchStudyReviewReminder);
        itemStudyReviewReminderTime  = findViewById(R.id.itemStudyReviewReminderTime);
        switchWeeklyNoteDigest       = findViewById(R.id.switchWeeklyNoteDigest);
        switchTimeCapsuleNotif       = findViewById(R.id.switchTimeCapsuleNotif);
        switchQuickCapture           = findViewById(R.id.switchQuickCapture);
        itemNotifSound               = findViewById(R.id.itemNotifSound);
        switchNotifVibration         = findViewById(R.id.switchNotifVibration);

        // Import & Export
        itemTrashAutoDelete       = findViewById(R.id.itemTrashAutoDelete);
        itemArchiveNotes          = findViewById(R.id.itemArchiveNotes);
        itemExportJson            = findViewById(R.id.itemExportJson);
        itemExportZip             = findViewById(R.id.itemExportZip);
        itemExportPdf             = findViewById(R.id.itemExportPdf);
        itemImportJson            = findViewById(R.id.itemImportJson);
        itemImportEnex            = findViewById(R.id.itemImportEnex);
        itemImportNotion          = findViewById(R.id.itemImportNotion);
        itemClearAllNotes         = findViewById(R.id.itemClearAllNotes);
        itemClearCompletedTodos   = findViewById(R.id.itemClearCompletedTodos);
        itemResetSettings         = findViewById(R.id.itemResetSettings);

        // About
        tvAboutStats              = findViewById(R.id.tvAboutStats);
        tvAboutVersion            = findViewById(R.id.tvAboutVersion);
        tvAboutStorage            = findViewById(R.id.tvAboutStorage);
        itemRateApp               = findViewById(R.id.itemRateApp);
        itemSendFeedback          = findViewById(R.id.itemSendFeedback);
        itemKeyboardShortcuts     = findViewById(R.id.itemKeyboardShortcuts);
    }

    // ─── Load current settings into views ─────────────────────────

    private void loadSettings() {
        // Appearance
        itemDefaultNoteColor.setText("🎨  Default Note Color: " + settings.getDefaultNoteColor());
        itemBackgroundTexture.setText("🖼️  Background Texture: " + capitalize(settings.getBackgroundTexture()));
        itemDefaultView.setText("📐  Default View: " + capitalize(settings.getDefaultView()));
        itemGridColumns.setText("⊞  Grid Columns: " + settings.getGridColumnCount());
        int lines = settings.getCardPreviewLines();
        itemPreviewLines.setText("📄  Preview Lines: " + (lines == 0 ? "None (title only)" : lines + " lines"));
        switchShowTags.setChecked(settings.showTagsOnCards());
        switchShowFolderBadge.setChecked(settings.showFolderBadge());
        switchShowTimestamps.setChecked(settings.showTimestamps());
        switchShowWordCountCards.setChecked(settings.showWordCountOnCards());
        itemAccentColor.setText("🎨  App Theme Accent: " + accentColorName(settings.getAccentColor()));

        // Editor
        itemDefaultBlockType.setText("📝  Default Block: " + blockTypeLabel(settings.getDefaultBlockType()));
        itemAutosaveInterval.setText("💾  Auto-save: " + formatInterval(settings.getAutosaveInterval()));
        itemFontSize.setText("🔤  Font Size: " + capitalize(settings.getFontSize()));
        itemFontFamily.setText("🔡  Font Family: " + capitalize(settings.getFontFamily()));
        switchAutoCapitalize.setChecked(settings.isAutoCapitalize());
        switchSpellCheck.setChecked(settings.isSpellCheck());
        itemToolbarVisibility.setText("🛠️  Show Toolbar: " +
                ("always".equals(settings.getToolbarVisibility()) ? "Always" : "Only When Keyboard Visible"));
        switchBlockHandles.setChecked(settings.showBlockHandles());
        switchTypewriterMode.setChecked(settings.isTypewriterMode());
        itemAmbientSounds.setText("🎵  Ambient Sounds: " + ambientSoundLabel(settings.getAmbientSounds()));
        switchWordCount.setChecked(settings.showWordCount());
        switchReadingTime.setChecked(settings.showReadingTime());
        itemChecklistBehavior.setText("☑️  Completed Items: " +
                (settings.hideCompletedChecklist() ? "Hide completed" : "Show completed"));

        // Study Mode
        itemSpacedRep.setText("🧠  Spaced Repetition: " + capitalize(settings.getSpacedRepAlgorithm()));
        itemReviewReminderTime.setText("⏰  Daily Review Reminder: " + settings.getReviewReminderTime());
        itemQuizDifficulty.setText("❓  Quiz Difficulty: " + capitalize(settings.getQuizDifficulty()));
        itemFlashcardAnimation.setText("🃏  Flashcard Animation: " + capitalize(settings.getFlashcardAnimation()));
        itemStudySession.setText("⏱  Study Session: " + settings.getStudySessionMinutes() + " min");
        itemBreakDuration.setText("☕  Break Duration: " + settings.getBreakDurationMinutes() + " min");

        // Organization
        itemDefaultSort.setText("⇅  Default Sort: " + sortLabel(settings.getDefaultSort()));
        itemDefaultCategory.setText("🗂️  Default Category: " + settings.getDefaultCategory());
        switchAutoCateg.setChecked(settings.isAutoCategorization());
        switchSmartTags.setChecked(settings.isSmartTagSuggestions());
        switchDuplicateDetect.setChecked(settings.isDuplicateDetection());
        switchSmartReminders.setChecked(settings.isSmartReminders());
        switchAutoRelationship.setChecked(settings.isAutoRelationship());
        switchMoodContext.setChecked(settings.isMoodContext());
        switchWeatherContext.setChecked(settings.isWeatherContext());

        // Security
        switchAppLock.setChecked(settings.isAppLockEnabled());
        itemBiometricPref.setText("🔐  App Lock Method: " + capitalize(settings.getBiometricPref()));
        itemNoteLockPref.setText("🔒  Note Lock Method: " + capitalize(settings.getNoteLockPref()));
        itemAutoLockAfter.setText("⏱  Auto-lock After: " + formatLockAfter(settings.getAutoLockAfter()));
        switchScreenshotProtection.setChecked(settings.isScreenshotProtection());

        // To-Do
        itemDefaultPriority.setText("⚡  Default Priority: " + capitalize(settings.getDefaultTaskPriority()));
        itemDefaultDueTime.setText("🕒  Default Due Time: " + settings.getDefaultDueTime());
        itemOverdueNotifTime.setText("⏰  Overdue Alert Time: " + settings.getOverdueNotifTime());
        switchDailyDigest.setChecked(settings.isDailyDigest());
        itemDigestTime.setText("📋  Digest Time: " + settings.getDigestTime());
        switchRecurringAutoCreate.setChecked(settings.isRecurringAutoCreate());
        switchCompletedAutoArchive.setChecked(settings.isCompletedAutoArchive());

        // Notifications
        switchNoteReminders.setChecked(settings.isNoteReminders());
        switchTodoReminders.setChecked(settings.isTodoReminders());
        switchStudyReviewReminder.setChecked(settings.isStudyReviewReminder());
        itemStudyReviewReminderTime.setText("📚  Review Reminder Time: " + settings.getStudyReviewReminderTime());
        itemStudyReviewReminderTime.setEnabled(settings.isStudyReviewReminder());
        switchWeeklyNoteDigest.setChecked(settings.isWeeklyNoteDigest());
        switchTimeCapsuleNotif.setChecked(settings.isTimeCapsuleNotif());
        switchQuickCapture.setChecked(settings.isQuickCaptureNotif());
        itemNotifSound.setText("🔔  Sound: " + capitalize(settings.getNotifSound()));
        switchNotifVibration.setChecked(settings.isNotifVibration());

        // Import & Export
        int trashDays = settings.getTrashAutoDeleteDays();
        itemTrashAutoDelete.setText("🗑️  Trash Auto-delete: " +
                (trashDays < 0 ? "Never" : "After " + trashDays + " days"));

        // About stats
        loadAboutStats();
        tvAboutVersion.setText("Notes v4.0  ·  Built for Android");
        long notesBytes = estimateNotesStorage();
        tvAboutStorage.setText("💾  Storage: " + formatBytes(notesBytes));
    }

    private void loadAboutStats() {
        try {
            NoteRepository repo = new NoteRepository(this);
            int noteCount = repo.getAllNotes().size();
            tvAboutStats.setText("📊  Notes: " + noteCount);
        } catch (Exception e) {
            tvAboutStats.setText("📊  Notes: —");
        }
    }

    // ─── Listeners ────────────────────────────────────────────────

    private void setupListeners() {
        // Appearance
        itemDefaultNoteColor.setOnClickListener(v -> pickDefaultNoteColor());
        itemBackgroundTexture.setOnClickListener(v -> pickOption("Background Texture",
                new String[]{"Plain", "Grid", "Dots", "Crosshatch"},
                new String[]{NotesSettings.TEXTURE_PLAIN, NotesSettings.TEXTURE_GRID,
                             NotesSettings.TEXTURE_DOTS, NotesSettings.TEXTURE_CROSSHATCH},
                settings.getBackgroundTexture(),
                (val) -> { settings.setBackgroundTexture(val);
                           itemBackgroundTexture.setText("🖼️  Background Texture: " + capitalize(val)); }));
        itemDefaultView.setOnClickListener(v -> pickOption("Default View",
                new String[]{"Grid", "List"},
                new String[]{"grid", "list"},
                settings.getDefaultView(),
                (val) -> { settings.setDefaultView(val);
                           itemDefaultView.setText("📐  Default View: " + capitalize(val)); }));
        itemGridColumns.setOnClickListener(v -> pickOption("Grid Column Count",
                new String[]{"2 columns", "3 columns"},
                new String[]{"2", "3"},
                String.valueOf(settings.getGridColumnCount()),
                (val) -> { settings.setGridColumnCount(Integer.parseInt(val));
                           itemGridColumns.setText("⊞  Grid Columns: " + val); }));
        itemPreviewLines.setOnClickListener(v -> pickOption("Preview Lines",
                new String[]{"None (title only)", "1 line", "2 lines", "3 lines"},
                new String[]{"0", "1", "2", "3"},
                String.valueOf(settings.getCardPreviewLines()),
                (val) -> { int n = Integer.parseInt(val); settings.setCardPreviewLines(n);
                           itemPreviewLines.setText("📄  Preview Lines: " +
                               (n == 0 ? "None (title only)" : n + " lines")); }));
        switchShowTags.setOnCheckedChangeListener((b, checked) -> settings.setShowTagsOnCards(checked));
        switchShowFolderBadge.setOnCheckedChangeListener((b, checked) -> settings.setShowFolderBadge(checked));
        switchShowTimestamps.setOnCheckedChangeListener((b, checked) -> settings.setShowTimestamps(checked));
        switchShowWordCountCards.setOnCheckedChangeListener((b, checked) -> settings.setShowWordCountOnCards(checked));
        itemAccentColor.setOnClickListener(v -> pickAccentColor());

        // Editor
        itemDefaultBlockType.setOnClickListener(v -> pickOption("Default Block Type",
                new String[]{"Text", "Heading 1", "Bullet List"},
                new String[]{"text", "h1", "bullet"},
                settings.getDefaultBlockType(),
                (val) -> { settings.setDefaultBlockType(val);
                           itemDefaultBlockType.setText("📝  Default Block: " + blockTypeLabel(val)); }));
        itemAutosaveInterval.setOnClickListener(v -> pickOption("Auto-save Interval",
                new String[]{"15 seconds", "30 seconds", "1 minute", "2 minutes"},
                new String[]{"15000", "30000", "60000", "120000"},
                String.valueOf(settings.getAutosaveInterval()),
                (val) -> { long ms = Long.parseLong(val); settings.setAutosaveInterval(ms);
                           itemAutosaveInterval.setText("💾  Auto-save: " + formatInterval(ms)); }));
        itemFontSize.setOnClickListener(v -> pickOption("Font Size",
                new String[]{"Small", "Medium", "Large", "Extra Large"},
                new String[]{"small", "medium", "large", "xlarge"},
                settings.getFontSize(),
                (val) -> { settings.setFontSize(val);
                           itemFontSize.setText("🔤  Font Size: " + capitalize(val)); }));
        itemFontFamily.setOnClickListener(v -> pickOption("Font Family",
                new String[]{"Default (Sans-serif)", "Serif", "Monospace"},
                new String[]{"default", "serif", "monospace"},
                settings.getFontFamily(),
                (val) -> { settings.setFontFamily(val);
                           itemFontFamily.setText("🔡  Font Family: " + capitalize(val)); }));
        switchAutoCapitalize.setOnCheckedChangeListener((b, checked) -> settings.setAutoCapitalize(checked));
        switchSpellCheck.setOnCheckedChangeListener((b, checked) -> settings.setSpellCheck(checked));
        itemToolbarVisibility.setOnClickListener(v -> pickOption("Show Toolbar",
                new String[]{"Always", "Only When Keyboard Visible"},
                new String[]{"always", "keyboard"},
                settings.getToolbarVisibility(),
                (val) -> { settings.setToolbarVisibility(val);
                           itemToolbarVisibility.setText("🛠️  Show Toolbar: " +
                               ("always".equals(val) ? "Always" : "Only When Keyboard Visible")); }));
        switchBlockHandles.setOnCheckedChangeListener((b, checked) -> settings.setShowBlockHandles(checked));
        switchTypewriterMode.setOnCheckedChangeListener((b, checked) -> settings.setTypewriterMode(checked));
        itemAmbientSounds.setOnClickListener(v -> pickOption("Ambient Sounds Default",
                new String[]{"Off", "Rain", "Café", "Fireplace", "Ocean Waves", "White Noise"},
                new String[]{"off", "rain", "cafe", "fire", "ocean", "white"},
                settings.getAmbientSounds(),
                (val) -> { settings.setAmbientSounds(val);
                           itemAmbientSounds.setText("🎵  Ambient Sounds: " + ambientSoundLabel(val)); }));
        switchWordCount.setOnCheckedChangeListener((b, checked) -> settings.setShowWordCount(checked));
        switchReadingTime.setOnCheckedChangeListener((b, checked) -> settings.setShowReadingTime(checked));
        itemChecklistBehavior.setOnClickListener(v -> pickOption("Completed Checklist Behavior",
                new String[]{"Show completed items", "Hide completed items"},
                new String[]{"show", "hide"},
                settings.hideCompletedChecklist() ? "hide" : "show",
                (val) -> { boolean hide = "hide".equals(val); settings.setHideCompletedChecklist(hide);
                           itemChecklistBehavior.setText("☑️  Completed Items: " +
                               (hide ? "Hide completed" : "Show completed")); }));

        // Study Mode
        itemSpacedRep.setOnClickListener(v -> pickOption("Spaced Repetition Algorithm",
                new String[]{"Standard (SM-2)", "Aggressive (shorter intervals)", "Conservative (longer intervals)"},
                new String[]{"standard", "aggressive", "conservative"},
                settings.getSpacedRepAlgorithm(),
                (val) -> { settings.setSpacedRepAlgorithm(val);
                           itemSpacedRep.setText("🧠  Spaced Repetition: " + capitalize(val)); }));
        itemReviewReminderTime.setOnClickListener(v ->
                showTimePicker(settings.getReviewReminderTime(), (time) -> {
                    settings.setReviewReminderTime(time);
                    itemReviewReminderTime.setText("⏰  Daily Review Reminder: " + time);
                }));
        itemQuizDifficulty.setOnClickListener(v -> pickOption("Quiz Difficulty",
                new String[]{"Easy", "Medium", "Hard"},
                new String[]{"easy", "medium", "hard"},
                settings.getQuizDifficulty(),
                (val) -> { settings.setQuizDifficulty(val);
                           itemQuizDifficulty.setText("❓  Quiz Difficulty: " + capitalize(val)); }));
        itemFlashcardAnimation.setOnClickListener(v -> pickOption("Flashcard Animation Style",
                new String[]{"Flip (3D card flip)", "Slide", "Fade"},
                new String[]{"flip", "slide", "fade"},
                settings.getFlashcardAnimation(),
                (val) -> { settings.setFlashcardAnimation(val);
                           itemFlashcardAnimation.setText("🃏  Flashcard Animation: " + capitalize(val)); }));
        itemStudySession.setOnClickListener(v -> pickOption("Study Session Duration",
                new String[]{"25 minutes (Pomodoro)", "45 minutes", "60 minutes"},
                new String[]{"25", "45", "60"},
                String.valueOf(settings.getStudySessionMinutes()),
                (val) -> { settings.setStudySessionMinutes(Integer.parseInt(val));
                           itemStudySession.setText("⏱  Study Session: " + val + " min"); }));
        itemBreakDuration.setOnClickListener(v -> pickOption("Break Duration",
                new String[]{"5 minutes", "10 minutes", "15 minutes"},
                new String[]{"5", "10", "15"},
                String.valueOf(settings.getBreakDurationMinutes()),
                (val) -> { settings.setBreakDurationMinutes(Integer.parseInt(val));
                           itemBreakDuration.setText("☕  Break Duration: " + val + " min"); }));

        // Organization
        itemDefaultSort.setOnClickListener(v -> pickOption("Default Sort",
                new String[]{"Date Modified", "Date Created", "Title A–Z", "Manual"},
                new String[]{"modified", "created", "title", "manual"},
                settings.getDefaultSort(),
                (val) -> { settings.setDefaultSort(val);
                           itemDefaultSort.setText("⇅  Default Sort: " + sortLabel(val)); }));
        itemDefaultCategory.setOnClickListener(v -> {
            String[] cats = {"All", "Personal", "Work", "Study", "Ideas"};
            pickOption("Default Category", cats, cats, settings.getDefaultCategory(),
                (val) -> { settings.setDefaultCategory(val);
                           itemDefaultCategory.setText("🗂️  Default Category: " + val); });
        });
        switchAutoCateg.setOnCheckedChangeListener((b, checked) -> settings.setAutoCategorization(checked));
        switchSmartTags.setOnCheckedChangeListener((b, checked) -> settings.setSmartTagSuggestions(checked));
        switchDuplicateDetect.setOnCheckedChangeListener((b, checked) -> settings.setDuplicateDetection(checked));
        switchSmartReminders.setOnCheckedChangeListener((b, checked) -> settings.setSmartReminders(checked));
        switchAutoRelationship.setOnCheckedChangeListener((b, checked) -> settings.setAutoRelationship(checked));
        switchMoodContext.setOnCheckedChangeListener((b, checked) -> settings.setMoodContext(checked));
        switchWeatherContext.setOnCheckedChangeListener((b, checked) -> {
            settings.setWeatherContext(checked);
            if (checked) {
                Toast.makeText(this, "Weather context requires location permission", Toast.LENGTH_SHORT).show();
            }
        });

        // Security
        switchAppLock.setOnCheckedChangeListener((b, checked) -> {
            settings.setAppLockEnabled(checked);
            itemBiometricPref.setEnabled(checked);
            itemNoteLockPref.setEnabled(checked);
            itemAutoLockAfter.setEnabled(checked);
        });
        itemBiometricPref.setOnClickListener(v -> pickOption("App Lock Method",
                new String[]{"Biometric (Face/Fingerprint)", "PIN", "Both"},
                new String[]{"biometric", "pin", "both"},
                settings.getBiometricPref(),
                (val) -> { settings.setBiometricPref(val);
                           itemBiometricPref.setText("🔐  App Lock Method: " + capitalize(val)); }));
        itemNoteLockPref.setOnClickListener(v -> pickOption("Individual Note Lock Method",
                new String[]{"Biometric (Face/Fingerprint)", "PIN", "Both"},
                new String[]{"biometric", "pin", "both"},
                settings.getNoteLockPref(),
                (val) -> { settings.setNoteLockPref(val);
                           itemNoteLockPref.setText("🔒  Note Lock Method: " + capitalize(val)); }));
        itemAutoLockAfter.setOnClickListener(v -> pickOption("Auto-lock After",
                new String[]{"1 minute", "5 minutes", "10 minutes", "Never"},
                new String[]{
                    String.valueOf(java.util.concurrent.TimeUnit.MINUTES.toMillis(1)),
                    String.valueOf(java.util.concurrent.TimeUnit.MINUTES.toMillis(5)),
                    String.valueOf(java.util.concurrent.TimeUnit.MINUTES.toMillis(10)),
                    "-1"
                },
                String.valueOf(settings.getAutoLockAfter()),
                (val) -> { long ms = Long.parseLong(val); settings.setAutoLockAfter(ms);
                           itemAutoLockAfter.setText("⏱  Auto-lock After: " + formatLockAfter(ms)); }));
        switchScreenshotProtection.setOnCheckedChangeListener((b, checked) -> {
            settings.setScreenshotProtection(checked);
            if (checked) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                                     WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });

        // To-Do
        itemDefaultPriority.setOnClickListener(v -> pickOption("Default Priority",
                new String[]{"None", "Low", "Medium", "High"},
                new String[]{"none", "low", "medium", "high"},
                settings.getDefaultTaskPriority(),
                (val) -> { settings.setDefaultTaskPriority(val);
                           itemDefaultPriority.setText("⚡  Default Priority: " + capitalize(val)); }));
        itemDefaultDueTime.setOnClickListener(v ->
                showTimePicker(settings.getDefaultDueTime(), (time) -> {
                    settings.setDefaultDueTime(time);
                    itemDefaultDueTime.setText("🕒  Default Due Time: " + time);
                }));
        itemOverdueNotifTime.setOnClickListener(v ->
                showTimePicker(settings.getOverdueNotifTime(), (time) -> {
                    settings.setOverdueNotifTime(time);
                    itemOverdueNotifTime.setText("⏰  Overdue Alert Time: " + time);
                }));
        switchDailyDigest.setOnCheckedChangeListener((b, checked) -> {
            settings.setDailyDigest(checked);
            itemDigestTime.setEnabled(checked);
        });
        itemDigestTime.setOnClickListener(v ->
                showTimePicker(settings.getDigestTime(), (time) -> {
                    settings.setDigestTime(time);
                    itemDigestTime.setText("📋  Digest Time: " + time);
                }));
        switchRecurringAutoCreate.setOnCheckedChangeListener((b, checked) -> settings.setRecurringAutoCreate(checked));
        switchCompletedAutoArchive.setOnCheckedChangeListener((b, checked) -> settings.setCompletedAutoArchive(checked));

        // Notifications
        switchNoteReminders.setOnCheckedChangeListener((b, checked) -> settings.setNoteReminders(checked));
        switchTodoReminders.setOnCheckedChangeListener((b, checked) -> settings.setTodoReminders(checked));
        switchStudyReviewReminder.setOnCheckedChangeListener((b, checked) -> {
            settings.setStudyReviewReminder(checked);
            itemStudyReviewReminderTime.setEnabled(checked);
        });
        itemStudyReviewReminderTime.setOnClickListener(v ->
                showTimePicker(settings.getStudyReviewReminderTime(), (time) -> {
                    settings.setStudyReviewReminderTime(time);
                    itemStudyReviewReminderTime.setText("📚  Review Reminder Time: " + time);
                }));
        switchWeeklyNoteDigest.setOnCheckedChangeListener((b, checked) -> settings.setWeeklyNoteDigest(checked));
        switchTimeCapsuleNotif.setOnCheckedChangeListener((b, checked) -> settings.setTimeCapsuleNotif(checked));
        switchQuickCapture.setOnCheckedChangeListener((b, checked) -> settings.setQuickCaptureNotif(checked));
        itemNotifSound.setOnClickListener(v -> pickOption("Notification Sound",
                new String[]{"Default", "Chime", "Bell", "None"},
                new String[]{"default", "chime", "bell", "none"},
                settings.getNotifSound(),
                (val) -> { settings.setNotifSound(val);
                           itemNotifSound.setText("🔔  Sound: " + capitalize(val)); }));
        switchNotifVibration.setOnCheckedChangeListener((b, checked) -> settings.setNotifVibration(checked));

        // Import & Export
        itemTrashAutoDelete.setOnClickListener(v -> pickOption("Trash Auto-delete",
                new String[]{"7 days", "15 days", "30 days", "Never"},
                new String[]{"7", "15", "30", "-1"},
                String.valueOf(settings.getTrashAutoDeleteDays()),
                (val) -> { int days = Integer.parseInt(val); settings.setTrashAutoDeleteDays(days);
                           itemTrashAutoDelete.setText("🗑️  Trash Auto-delete: " +
                               (days < 0 ? "Never" : "After " + days + " days")); }));
        itemArchiveNotes.setOnClickListener(v ->
                startActivity(new Intent(this, NotesArchiveActivity.class)));
        itemExportJson.setOnClickListener(v -> confirmExport("JSON Backup",
                "Export all notes as a single JSON backup file. Can be re-imported later.",
                "json"));
        itemExportZip.setOnClickListener(v -> confirmExport("ZIP with HTML Files",
                "Export all notes as HTML files inside a ZIP archive.",
                "zip"));
        itemExportPdf.setOnClickListener(v -> confirmExport("PDF (per note)",
                "Export each note as an individual PDF file.",
                "pdf"));
        itemImportJson.setOnClickListener(v ->
                Toast.makeText(this, "Select a Notes JSON backup file from Files", Toast.LENGTH_SHORT).show());
        itemImportEnex.setOnClickListener(v ->
                Toast.makeText(this, "Select an Evernote .enex export file from Files", Toast.LENGTH_SHORT).show());
        itemImportNotion.setOnClickListener(v ->
                Toast.makeText(this, "Select a Notion export ZIP file from Files", Toast.LENGTH_SHORT).show());
        itemClearAllNotes.setOnClickListener(v -> confirmClearAllNotes());
        itemClearCompletedTodos.setOnClickListener(v -> confirmClearCompletedTodos());
        itemResetSettings.setOnClickListener(v -> confirmResetSettings());

        // About
        itemRateApp.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "Play Store not available", Toast.LENGTH_SHORT).show();
            }
        });
        itemSendFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Notes App Feedback (" + getPackageName() + ")");
            try { startActivity(intent); }
            catch (Exception e) { Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show(); }
        });
        itemKeyboardShortcuts.setOnClickListener(v -> showKeyboardShortcuts());
    }

    // ─── Dialogs ──────────────────────────────────────────────────

    private void pickOption(String title, String[] labels, String[] values,
                            String current, OnPickedListener listener) {
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) { selected = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    listener.onPicked(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickDefaultNoteColor() {
        String[] colorNames = Note.NOTE_COLOR_NAMES;
        String[] colorHexes = Note.NOTE_COLORS;
        String current = settings.getDefaultNoteColor();
        pickOption("Default Note Color", colorNames, colorHexes, current,
            (val) -> { settings.setDefaultNoteColor(val);
                       itemDefaultNoteColor.setText("🎨  Default Note Color: " + val); });
    }

    private void pickAccentColor() {
        pickOption("App Theme Accent",
                NotesSettings.ACCENT_COLOR_NAMES,
                NotesSettings.ACCENT_COLORS,
                settings.getAccentColor(),
                (val) -> { settings.setAccentColor(val);
                           itemAccentColor.setText("🎨  App Theme Accent: " + accentColorName(val)); });
    }

    private void showTimePicker(String currentTime, OnPickedListener listener) {
        int hour = 9, minute = 0;
        try {
            String[] parts = currentTime.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        new TimePickerDialog(this, (view, h, m) -> {
            String time = String.format(Locale.US, "%02d:%02d", h, m);
            listener.onPicked(time);
        }, hour, minute, true).show();
    }

    private void confirmExport(String formatName, String description, String format) {
        new AlertDialog.Builder(this)
                .setTitle("Export as " + formatName)
                .setMessage(description)
                .setPositiveButton("Export", (d, w) -> {
                    Toast.makeText(this, "Exporting notes as " + formatName + "…", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        try {
                            NoteRepository repo = new NoteRepository(this);
                            NoteExportManager exportMgr = new NoteExportManager(this);
                            // Export all notes — single note PDF export reused per note for pdf format
                            for (Note note : repo.getAllNotes()) {
                                if ("pdf".equals(format)) {
                                    exportMgr.exportAsPdf(note, success -> {});
                                }
                            }
                            runOnUiThread(() -> Toast.makeText(this,
                                    "Export complete", Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this,
                                    "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAllNotes() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Clear All Notes")
                .setMessage("This will permanently delete ALL notes and their contents. This cannot be undone.")
                .setPositiveButton("Delete Everything", (d, w) -> {
                    try {
                        NoteRepository repo = new NoteRepository(this);
                        for (Note note : repo.getAllNotes()) {
                            repo.deleteNotePermanently(note.id);
                        }
                        repo.emptyTrash();
                        Toast.makeText(this, "All notes deleted", Toast.LENGTH_SHORT).show();
                        loadAboutStats();
                    } catch (Exception e) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearCompletedTodos() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Completed To-Do Items")
                .setMessage("This will permanently delete all completed to-do tasks. This cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    TodoRepository repo = new TodoRepository(this);
                    repo.clearAllCompleted();
                    Toast.makeText(this, "Completed tasks cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmResetSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Notes Settings")
                .setMessage("All Notes settings will be restored to their defaults. Your notes will not be affected.")
                .setPositiveButton("Reset", (d, w) -> {
                    settings.resetToDefaults();
                    loadSettings();
                    Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showKeyboardShortcuts() {
        String shortcuts =
            "Keyboard & Gesture Shortcuts\n\n" +
            "Notes Home:\n" +
            "• Swipe right → open drawer\n" +
            "• Long press card → multi-select\n" +
            "• Swipe card left/right → quick actions\n\n" +
            "Editor:\n" +
            "• Ctrl+B → Bold\n" +
            "• Ctrl+I → Italic\n" +
            "• Ctrl+U → Underline\n" +
            "• Ctrl+Z → Undo\n" +
            "• Ctrl+S → Force save\n" +
            "• Swipe down → close editor\n\n" +
            "To-Do:\n" +
            "• Tap checkbox → complete task\n" +
            "• Long press → convert to note task\n" +
            "• Swipe left → delete task";
        new AlertDialog.Builder(this)
                .setTitle("Tips & Shortcuts")
                .setMessage(shortcuts)
                .setPositiveButton("Got it", null)
                .show();
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    private String formatInterval(long ms) {
        if (ms < 60_000) return (ms / 1000) + " seconds";
        return (ms / 60_000) + " minute" + (ms / 60_000 == 1 ? "" : "s");
    }

    private String formatLockAfter(long ms) {
        if (ms < 0) return "Never";
        if (ms < 60_000) return (ms / 1000) + " seconds";
        return (ms / 60_000) + " minute" + (ms / 60_000 == 1 ? "" : "s");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024));
    }

    private String sortLabel(String key) {
        switch (key) {
            case "modified": return "Date Modified";
            case "created":  return "Date Created";
            case "title":    return "Title A–Z";
            case "manual":   return "Manual";
            default:         return capitalize(key);
        }
    }

    private String blockTypeLabel(String key) {
        switch (key) {
            case "text":   return "Text";
            case "h1":     return "Heading 1";
            case "bullet": return "Bullet List";
            default:       return capitalize(key);
        }
    }

    private String ambientSoundLabel(String key) {
        switch (key) {
            case "off":   return "Off";
            case "rain":  return "Rain";
            case "cafe":  return "Café";
            case "fire":  return "Fireplace";
            case "ocean": return "Ocean Waves";
            case "white": return "White Noise";
            default:      return capitalize(key);
        }
    }

    private String accentColorName(String hex) {
        for (int i = 0; i < NotesSettings.ACCENT_COLORS.length; i++) {
            if (NotesSettings.ACCENT_COLORS[i].equalsIgnoreCase(hex)) {
                return NotesSettings.ACCENT_COLOR_NAMES[i];
            }
        }
        return hex;
    }

    private long estimateNotesStorage() {
        try {
            NoteRepository repo = new NoteRepository(this);
            long total = 0;
            for (Note n : repo.getAllNotes()) {
                if (n.body != null) total += n.body.length() * 2L;
                if (n.title != null) total += n.title.length() * 2L;
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private interface OnPickedListener {
        void onPicked(String value);
    }
}
