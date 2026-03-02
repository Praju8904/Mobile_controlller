package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * NotesSettings — single source of truth for all Notes & To-Do settings.
 *
 * Persisted in SharedPreferences under "notes_settings".
 * Use the static getInstance() / getPrefs() helpers from activities,
 * or access typed getters / setters directly.
 */
public class NotesSettings {

    private static final String PREFS_NAME = "notes_settings";

    // ─── Appearance ───────────────────────────────────────────────
    public static final String KEY_DEFAULT_NOTE_COLOR    = "default_note_color";
    public static final String KEY_BACKGROUND_TEXTURE    = "background_texture";
    public static final String KEY_DEFAULT_VIEW          = "default_view";          // "grid" / "list"
    public static final String KEY_GRID_COLUMN_COUNT     = "grid_column_count";     // 2 / 3
    public static final String KEY_CARD_PREVIEW_LINES    = "card_preview_lines";    // 0/1/2/3
    public static final String KEY_SHOW_TAGS_ON_CARDS    = "show_tags_on_cards";
    public static final String KEY_SHOW_FOLDER_BADGE     = "show_folder_badge";
    public static final String KEY_SHOW_TIMESTAMPS       = "show_timestamps";
    public static final String KEY_SHOW_WORD_COUNT_CARDS = "show_word_count_cards";
    public static final String KEY_ACCENT_COLOR          = "accent_color";

    // ─── Editor ───────────────────────────────────────────────────
    public static final String KEY_DEFAULT_BLOCK_TYPE    = "default_block_type";    // "text"/"h1"/"bullet"
    public static final String KEY_AUTOSAVE_INTERVAL     = "autosave_interval_ms";  // ms
    public static final String KEY_FONT_SIZE             = "editor_font_size";      // "small"/"medium"/"large"/"xlarge"
    public static final String KEY_FONT_FAMILY           = "editor_font_family";    // "default"/"serif"/"monospace"
    public static final String KEY_AUTO_CAPITALIZE       = "auto_capitalize";
    public static final String KEY_SPELL_CHECK           = "spell_check";
    public static final String KEY_TOOLBAR_VISIBILITY    = "toolbar_visibility";    // "always"/"keyboard"
    public static final String KEY_SHOW_BLOCK_HANDLES    = "show_block_handles";
    public static final String KEY_TYPEWRITER_MODE       = "typewriter_mode_default";
    public static final String KEY_AMBIENT_SOUNDS        = "ambient_sounds_default"; // "off"/"rain"/"cafe"/"fire"/"ocean"/"white"
    public static final String KEY_SHOW_WORD_COUNT       = "show_word_count";
    public static final String KEY_SHOW_READING_TIME     = "show_reading_time";
    public static final String KEY_CHECKLIST_HIDE_DONE   = "checklist_hide_done";

    // ─── Study Mode ───────────────────────────────────────────────
    public static final String KEY_SPACED_REP_ALGORITHM  = "spaced_rep_algorithm";  // "standard"/"aggressive"/"conservative"
    public static final String KEY_REVIEW_REMINDER_TIME  = "review_reminder_time";  // "HH:MM"
    public static final String KEY_QUIZ_DIFFICULTY       = "quiz_difficulty";        // "easy"/"medium"/"hard"
    public static final String KEY_FLASHCARD_ANIMATION   = "flashcard_animation";    // "flip"/"slide"/"fade"
    public static final String KEY_STUDY_SESSION_MINUTES = "study_session_minutes";  // 25/45/60
    public static final String KEY_BREAK_DURATION_MINUTES= "break_duration_minutes"; // 5/10/15

    // ─── Organization ─────────────────────────────────────────────
    public static final String KEY_DEFAULT_SORT          = "default_sort";          // "modified"/"created"/"title"/"manual"
    public static final String KEY_DEFAULT_CATEGORY      = "default_category";
    public static final String KEY_AUTO_CATEGORIZATION   = "auto_categorization";
    public static final String KEY_SMART_TAG_SUGGESTIONS = "smart_tag_suggestions";
    public static final String KEY_DUPLICATE_DETECTION   = "duplicate_detection";
    public static final String KEY_SMART_REMINDERS       = "smart_reminders";
    public static final String KEY_AUTO_RELATIONSHIP     = "auto_relationship_detection";
    public static final String KEY_MOOD_CONTEXT          = "mood_context_recording";
    public static final String KEY_WEATHER_CONTEXT       = "weather_context_recording";

    // ─── Security ─────────────────────────────────────────────────
    public static final String KEY_APP_LOCK_ENABLED      = "app_lock_enabled";
    public static final String KEY_BIOMETRIC_PREF        = "biometric_pref";        // "biometric"/"pin"/"both"
    public static final String KEY_NOTE_LOCK_PREF        = "note_lock_pref";        // "biometric"/"pin"/"both"
    public static final String KEY_AUTO_LOCK_AFTER       = "auto_lock_after_ms";    // ms; -1 = never
    public static final String KEY_SCREENSHOT_PROTECTION = "screenshot_protection";

    // ─── To-Do ────────────────────────────────────────────────────
    public static final String KEY_DEFAULT_TASK_PRIORITY = "default_task_priority"; // "none"/"low"/"medium"/"high"
    public static final String KEY_DEFAULT_DUE_TIME      = "default_due_time";      // "HH:MM"
    public static final String KEY_OVERDUE_NOTIF_TIME    = "overdue_notif_time";    // "HH:MM"
    public static final String KEY_DAILY_DIGEST          = "daily_digest";
    public static final String KEY_DIGEST_TIME           = "digest_time";           // "HH:MM"
    public static final String KEY_RECURRING_AUTO_CREATE = "recurring_auto_create";
    public static final String KEY_COMPLETED_AUTO_ARCHIVE = "completed_auto_archive";

    // ─── Notifications ────────────────────────────────────────────
    public static final String KEY_NOTE_REMINDERS             = "note_reminders";
    public static final String KEY_TODO_REMINDERS             = "todo_reminders";
    public static final String KEY_QUICK_CAPTURE_NOTIF        = "quick_capture_notif";
    public static final String KEY_STUDY_REVIEW_REMINDER      = "study_review_reminder";
    public static final String KEY_STUDY_REVIEW_REMINDER_TIME = "study_review_reminder_time"; // "HH:MM"
    public static final String KEY_WEEKLY_NOTE_DIGEST         = "weekly_note_digest";
    public static final String KEY_TIME_CAPSULE_NOTIF         = "time_capsule_notifications";
    public static final String KEY_NOTIF_SOUND                = "notif_sound";           // "default"/"none"/"chime"/"bell"
    public static final String KEY_NOTIF_VIBRATION            = "notif_vibration";

    // ─── Data ─────────────────────────────────────────────────────
    public static final String KEY_TRASH_AUTO_DELETE_DAYS = "trash_auto_delete_days"; // 7/15/30/-1(never)

    // ─── Defaults ─────────────────────────────────────────────────
    public static final String DEFAULT_NOTE_COLOR         = "#2D2D2D";
    public static final String DEFAULT_TEXTURE            = "plain";
    public static final String DEFAULT_VIEW               = "grid";
    public static final int    DEFAULT_GRID_COLUMNS       = 2;
    public static final int    DEFAULT_PREVIEW_LINES      = 2;
    public static final String DEFAULT_ACCENT_COLOR       = "#FF6600";   // Orange

    public static final String DEFAULT_BLOCK_TYPE         = "text";
    public static final long   DEFAULT_AUTOSAVE_INTERVAL  = 30_000L;     // 30 s
    public static final String DEFAULT_FONT_SIZE          = "medium";
    public static final String DEFAULT_FONT_FAMILY        = "default";
    public static final String DEFAULT_TOOLBAR_VIS        = "keyboard";
    public static final String DEFAULT_AMBIENT_SOUNDS     = "off";

    public static final String DEFAULT_SPACED_REP         = "standard";
    public static final String DEFAULT_REVIEW_TIME        = "09:00";
    public static final String DEFAULT_QUIZ_DIFFICULTY    = "medium";
    public static final String DEFAULT_FLASHCARD_ANIM     = "flip";
    public static final int    DEFAULT_STUDY_MINUTES      = 25;
    public static final int    DEFAULT_BREAK_MINUTES      = 5;

    public static final String DEFAULT_SORT               = "modified";
    public static final String DEFAULT_CATEGORY           = "Personal";

    public static final String DEFAULT_BIOMETRIC_PREF     = "biometric";
    public static final String DEFAULT_NOTE_LOCK_PREF     = "biometric";
    public static final long   DEFAULT_AUTO_LOCK_AFTER    = 5 * 60_000L; // 5 min
    public static final String DEFAULT_TASK_PRIORITY      = "none";
    public static final String DEFAULT_DUE_TIME           = "09:00";
    public static final String DEFAULT_OVERDUE_NOTIF_TIME = "08:00";
    public static final String DEFAULT_DIGEST_TIME        = "07:00";
    public static final String DEFAULT_STUDY_REVIEW_TIME  = "08:00";
    public static final String DEFAULT_NOTIF_SOUND        = "default";
    public static final int    DEFAULT_TRASH_DAYS         = 30;

    // ─── Accent palette (6 options) ──────────────────────────────
    public static final String[] ACCENT_COLORS = {
        "#FF6600",  // Orange (default)
        "#3B82F6",  // Blue
        "#10B981",  // Green
        "#A855F7",  // Purple
        "#EF4444",  // Red
        "#F59E0B",  // Amber
    };
    public static final String[] ACCENT_COLOR_NAMES = {
        "Orange", "Blue", "Green", "Purple", "Red", "Amber"
    };

    // ─── Background texture options ───────────────────────────────
    public static final String TEXTURE_PLAIN      = "plain";
    public static final String TEXTURE_GRID       = "grid";
    public static final String TEXTURE_DOTS       = "dots";
    public static final String TEXTURE_CROSSHATCH = "crosshatch";

    // ─── Singleton / helpers ──────────────────────────────────────

    private static NotesSettings instance;

    private final SharedPreferences prefs;

    private NotesSettings(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized NotesSettings getInstance(Context context) {
        if (instance == null) {
            instance = new NotesSettings(context);
        }
        return instance;
    }

    // ─── Typed getters ────────────────────────────────────────────

    public String getDefaultNoteColor()    { return prefs.getString(KEY_DEFAULT_NOTE_COLOR, DEFAULT_NOTE_COLOR); }
    public String getBackgroundTexture()   { return prefs.getString(KEY_BACKGROUND_TEXTURE, DEFAULT_TEXTURE); }
    public String getDefaultView()         { return prefs.getString(KEY_DEFAULT_VIEW, DEFAULT_VIEW); }
    public int    getGridColumnCount()     { return prefs.getInt(KEY_GRID_COLUMN_COUNT, DEFAULT_GRID_COLUMNS); }
    public int    getCardPreviewLines()    { return prefs.getInt(KEY_CARD_PREVIEW_LINES, DEFAULT_PREVIEW_LINES); }
    public boolean showTagsOnCards()       { return prefs.getBoolean(KEY_SHOW_TAGS_ON_CARDS, true); }
    public boolean showFolderBadge()       { return prefs.getBoolean(KEY_SHOW_FOLDER_BADGE, true); }
    public boolean showTimestamps()        { return prefs.getBoolean(KEY_SHOW_TIMESTAMPS, true); }
    public boolean showWordCountOnCards()  { return prefs.getBoolean(KEY_SHOW_WORD_COUNT_CARDS, false); }
    public String getAccentColor()         { return prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR); }

    public String getDefaultBlockType()    { return prefs.getString(KEY_DEFAULT_BLOCK_TYPE, DEFAULT_BLOCK_TYPE); }
    public long   getAutosaveInterval()    { return prefs.getLong(KEY_AUTOSAVE_INTERVAL, DEFAULT_AUTOSAVE_INTERVAL); }
    public String getFontSize()            { return prefs.getString(KEY_FONT_SIZE, DEFAULT_FONT_SIZE); }
    public String getFontFamily()          { return prefs.getString(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY); }
    public boolean isAutoCapitalize()      { return prefs.getBoolean(KEY_AUTO_CAPITALIZE, true); }
    public boolean isSpellCheck()          { return prefs.getBoolean(KEY_SPELL_CHECK, true); }
    public String getToolbarVisibility()   { return prefs.getString(KEY_TOOLBAR_VISIBILITY, DEFAULT_TOOLBAR_VIS); }
    public boolean showBlockHandles()      { return prefs.getBoolean(KEY_SHOW_BLOCK_HANDLES, true); }
    public boolean isTypewriterMode()      { return prefs.getBoolean(KEY_TYPEWRITER_MODE, false); }
    public String getAmbientSounds()       { return prefs.getString(KEY_AMBIENT_SOUNDS, DEFAULT_AMBIENT_SOUNDS); }
    public boolean showWordCount()         { return prefs.getBoolean(KEY_SHOW_WORD_COUNT, true); }
    public boolean showReadingTime()       { return prefs.getBoolean(KEY_SHOW_READING_TIME, true); }
    public boolean hideCompletedChecklist(){ return prefs.getBoolean(KEY_CHECKLIST_HIDE_DONE, false); }

    public String getSpacedRepAlgorithm()  { return prefs.getString(KEY_SPACED_REP_ALGORITHM, DEFAULT_SPACED_REP); }
    public String getReviewReminderTime()  { return prefs.getString(KEY_REVIEW_REMINDER_TIME, DEFAULT_REVIEW_TIME); }
    public String getQuizDifficulty()      { return prefs.getString(KEY_QUIZ_DIFFICULTY, DEFAULT_QUIZ_DIFFICULTY); }
    public String getFlashcardAnimation()  { return prefs.getString(KEY_FLASHCARD_ANIMATION, DEFAULT_FLASHCARD_ANIM); }
    public int    getStudySessionMinutes() { return prefs.getInt(KEY_STUDY_SESSION_MINUTES, DEFAULT_STUDY_MINUTES); }
    public int    getBreakDurationMinutes(){ return prefs.getInt(KEY_BREAK_DURATION_MINUTES, DEFAULT_BREAK_MINUTES); }

    public String getDefaultSort()         { return prefs.getString(KEY_DEFAULT_SORT, DEFAULT_SORT); }
    public String getDefaultCategory()     { return prefs.getString(KEY_DEFAULT_CATEGORY, DEFAULT_CATEGORY); }
    public boolean isAutoCategorization()  { return prefs.getBoolean(KEY_AUTO_CATEGORIZATION, true); }
    public boolean isSmartTagSuggestions() { return prefs.getBoolean(KEY_SMART_TAG_SUGGESTIONS, true); }
    public boolean isDuplicateDetection()  { return prefs.getBoolean(KEY_DUPLICATE_DETECTION, false); }
    public boolean isSmartReminders()      { return prefs.getBoolean(KEY_SMART_REMINDERS, true); }
    public boolean isAutoRelationship()    { return prefs.getBoolean(KEY_AUTO_RELATIONSHIP, false); }
    public boolean isMoodContext()         { return prefs.getBoolean(KEY_MOOD_CONTEXT, false); }
    public boolean isWeatherContext()      { return prefs.getBoolean(KEY_WEATHER_CONTEXT, false); }

    public boolean isAppLockEnabled()      { return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false); }
    public String getBiometricPref()       { return prefs.getString(KEY_BIOMETRIC_PREF, DEFAULT_BIOMETRIC_PREF); }
    public String getNoteLockPref()        { return prefs.getString(KEY_NOTE_LOCK_PREF, DEFAULT_NOTE_LOCK_PREF); }
    public long   getAutoLockAfter()       { return prefs.getLong(KEY_AUTO_LOCK_AFTER, DEFAULT_AUTO_LOCK_AFTER); }
    public boolean isScreenshotProtection(){ return prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, false); }

    public String getDefaultTaskPriority() { return prefs.getString(KEY_DEFAULT_TASK_PRIORITY, DEFAULT_TASK_PRIORITY); }
    public String getDefaultDueTime()      { return prefs.getString(KEY_DEFAULT_DUE_TIME, DEFAULT_DUE_TIME); }
    public String getOverdueNotifTime()    { return prefs.getString(KEY_OVERDUE_NOTIF_TIME, DEFAULT_OVERDUE_NOTIF_TIME); }
    public boolean isDailyDigest()         { return prefs.getBoolean(KEY_DAILY_DIGEST, false); }
    public String getDigestTime()          { return prefs.getString(KEY_DIGEST_TIME, DEFAULT_DIGEST_TIME); }
    public boolean isRecurringAutoCreate() { return prefs.getBoolean(KEY_RECURRING_AUTO_CREATE, true); }
    public boolean isCompletedAutoArchive(){ return prefs.getBoolean(KEY_COMPLETED_AUTO_ARCHIVE, false); }

    public boolean isNoteReminders()            { return prefs.getBoolean(KEY_NOTE_REMINDERS, true); }
    public boolean isTodoReminders()            { return prefs.getBoolean(KEY_TODO_REMINDERS, true); }
    public boolean isQuickCaptureNotif()        { return prefs.getBoolean(KEY_QUICK_CAPTURE_NOTIF, false); }
    public boolean isStudyReviewReminder()      { return prefs.getBoolean(KEY_STUDY_REVIEW_REMINDER, false); }
    public String getStudyReviewReminderTime()  { return prefs.getString(KEY_STUDY_REVIEW_REMINDER_TIME, DEFAULT_STUDY_REVIEW_TIME); }
    public boolean isWeeklyNoteDigest()         { return prefs.getBoolean(KEY_WEEKLY_NOTE_DIGEST, false); }
    public boolean isTimeCapsuleNotif()         { return prefs.getBoolean(KEY_TIME_CAPSULE_NOTIF, true); }
    public String getNotifSound()          { return prefs.getString(KEY_NOTIF_SOUND, DEFAULT_NOTIF_SOUND); }
    public boolean isNotifVibration()      { return prefs.getBoolean(KEY_NOTIF_VIBRATION, true); }

    public int    getTrashAutoDeleteDays() { return prefs.getInt(KEY_TRASH_AUTO_DELETE_DAYS, DEFAULT_TRASH_DAYS); }

    // ─── Typed setters ────────────────────────────────────────────

    public void setDefaultNoteColor(String v)    { prefs.edit().putString(KEY_DEFAULT_NOTE_COLOR, v).apply(); }
    public void setBackgroundTexture(String v)   { prefs.edit().putString(KEY_BACKGROUND_TEXTURE, v).apply(); }
    public void setDefaultView(String v)         { prefs.edit().putString(KEY_DEFAULT_VIEW, v).apply(); }
    public void setGridColumnCount(int v)        { prefs.edit().putInt(KEY_GRID_COLUMN_COUNT, v).apply(); }
    public void setCardPreviewLines(int v)       { prefs.edit().putInt(KEY_CARD_PREVIEW_LINES, v).apply(); }
    public void setShowTagsOnCards(boolean v)    { prefs.edit().putBoolean(KEY_SHOW_TAGS_ON_CARDS, v).apply(); }
    public void setShowFolderBadge(boolean v)    { prefs.edit().putBoolean(KEY_SHOW_FOLDER_BADGE, v).apply(); }
    public void setShowTimestamps(boolean v)     { prefs.edit().putBoolean(KEY_SHOW_TIMESTAMPS, v).apply(); }
    public void setShowWordCountOnCards(boolean v){ prefs.edit().putBoolean(KEY_SHOW_WORD_COUNT_CARDS, v).apply(); }
    public void setAccentColor(String v)         { prefs.edit().putString(KEY_ACCENT_COLOR, v).apply(); }

    public void setDefaultBlockType(String v)    { prefs.edit().putString(KEY_DEFAULT_BLOCK_TYPE, v).apply(); }
    public void setAutosaveInterval(long v)      { prefs.edit().putLong(KEY_AUTOSAVE_INTERVAL, v).apply(); }
    public void setFontSize(String v)            { prefs.edit().putString(KEY_FONT_SIZE, v).apply(); }
    public void setFontFamily(String v)          { prefs.edit().putString(KEY_FONT_FAMILY, v).apply(); }
    public void setAutoCapitalize(boolean v)     { prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, v).apply(); }
    public void setSpellCheck(boolean v)         { prefs.edit().putBoolean(KEY_SPELL_CHECK, v).apply(); }
    public void setToolbarVisibility(String v)   { prefs.edit().putString(KEY_TOOLBAR_VISIBILITY, v).apply(); }
    public void setShowBlockHandles(boolean v)   { prefs.edit().putBoolean(KEY_SHOW_BLOCK_HANDLES, v).apply(); }
    public void setTypewriterMode(boolean v)     { prefs.edit().putBoolean(KEY_TYPEWRITER_MODE, v).apply(); }
    public void setAmbientSounds(String v)       { prefs.edit().putString(KEY_AMBIENT_SOUNDS, v).apply(); }
    public void setShowWordCount(boolean v)      { prefs.edit().putBoolean(KEY_SHOW_WORD_COUNT, v).apply(); }
    public void setShowReadingTime(boolean v)    { prefs.edit().putBoolean(KEY_SHOW_READING_TIME, v).apply(); }
    public void setHideCompletedChecklist(boolean v){ prefs.edit().putBoolean(KEY_CHECKLIST_HIDE_DONE, v).apply(); }

    public void setSpacedRepAlgorithm(String v)  { prefs.edit().putString(KEY_SPACED_REP_ALGORITHM, v).apply(); }
    public void setReviewReminderTime(String v)  { prefs.edit().putString(KEY_REVIEW_REMINDER_TIME, v).apply(); }
    public void setQuizDifficulty(String v)      { prefs.edit().putString(KEY_QUIZ_DIFFICULTY, v).apply(); }
    public void setFlashcardAnimation(String v)  { prefs.edit().putString(KEY_FLASHCARD_ANIMATION, v).apply(); }
    public void setStudySessionMinutes(int v)    { prefs.edit().putInt(KEY_STUDY_SESSION_MINUTES, v).apply(); }
    public void setBreakDurationMinutes(int v)   { prefs.edit().putInt(KEY_BREAK_DURATION_MINUTES, v).apply(); }

    public void setDefaultSort(String v)         { prefs.edit().putString(KEY_DEFAULT_SORT, v).apply(); }
    public void setDefaultCategory(String v)     { prefs.edit().putString(KEY_DEFAULT_CATEGORY, v).apply(); }
    public void setAutoCategorization(boolean v) { prefs.edit().putBoolean(KEY_AUTO_CATEGORIZATION, v).apply(); }
    public void setSmartTagSuggestions(boolean v){ prefs.edit().putBoolean(KEY_SMART_TAG_SUGGESTIONS, v).apply(); }
    public void setDuplicateDetection(boolean v) { prefs.edit().putBoolean(KEY_DUPLICATE_DETECTION, v).apply(); }
    public void setSmartReminders(boolean v)     { prefs.edit().putBoolean(KEY_SMART_REMINDERS, v).apply(); }
    public void setAutoRelationship(boolean v)   { prefs.edit().putBoolean(KEY_AUTO_RELATIONSHIP, v).apply(); }
    public void setMoodContext(boolean v)        { prefs.edit().putBoolean(KEY_MOOD_CONTEXT, v).apply(); }
    public void setWeatherContext(boolean v)     { prefs.edit().putBoolean(KEY_WEATHER_CONTEXT, v).apply(); }

    public void setAppLockEnabled(boolean v)     { prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, v).apply(); }
    public void setBiometricPref(String v)       { prefs.edit().putString(KEY_BIOMETRIC_PREF, v).apply(); }
    public void setNoteLockPref(String v)        { prefs.edit().putString(KEY_NOTE_LOCK_PREF, v).apply(); }
    public void setAutoLockAfter(long v)         { prefs.edit().putLong(KEY_AUTO_LOCK_AFTER, v).apply(); }
    public void setScreenshotProtection(boolean v){ prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, v).apply(); }

    public void setDefaultTaskPriority(String v) { prefs.edit().putString(KEY_DEFAULT_TASK_PRIORITY, v).apply(); }
    public void setDefaultDueTime(String v)      { prefs.edit().putString(KEY_DEFAULT_DUE_TIME, v).apply(); }
    public void setOverdueNotifTime(String v)    { prefs.edit().putString(KEY_OVERDUE_NOTIF_TIME, v).apply(); }
    public void setDailyDigest(boolean v)        { prefs.edit().putBoolean(KEY_DAILY_DIGEST, v).apply(); }
    public void setDigestTime(String v)          { prefs.edit().putString(KEY_DIGEST_TIME, v).apply(); }
    public void setRecurringAutoCreate(boolean v){ prefs.edit().putBoolean(KEY_RECURRING_AUTO_CREATE, v).apply(); }
    public void setCompletedAutoArchive(boolean v){ prefs.edit().putBoolean(KEY_COMPLETED_AUTO_ARCHIVE, v).apply(); }

    public void setNoteReminders(boolean v)           { prefs.edit().putBoolean(KEY_NOTE_REMINDERS, v).apply(); }
    public void setTodoReminders(boolean v)           { prefs.edit().putBoolean(KEY_TODO_REMINDERS, v).apply(); }
    public void setQuickCaptureNotif(boolean v)       { prefs.edit().putBoolean(KEY_QUICK_CAPTURE_NOTIF, v).apply(); }
    public void setStudyReviewReminder(boolean v)     { prefs.edit().putBoolean(KEY_STUDY_REVIEW_REMINDER, v).apply(); }
    public void setStudyReviewReminderTime(String v)  { prefs.edit().putString(KEY_STUDY_REVIEW_REMINDER_TIME, v).apply(); }
    public void setWeeklyNoteDigest(boolean v)        { prefs.edit().putBoolean(KEY_WEEKLY_NOTE_DIGEST, v).apply(); }
    public void setTimeCapsuleNotif(boolean v)        { prefs.edit().putBoolean(KEY_TIME_CAPSULE_NOTIF, v).apply(); }
    public void setNotifSound(String v)          { prefs.edit().putString(KEY_NOTIF_SOUND, v).apply(); }
    public void setNotifVibration(boolean v)     { prefs.edit().putBoolean(KEY_NOTIF_VIBRATION, v).apply(); }

    public void setTrashAutoDeleteDays(int v)    { prefs.edit().putInt(KEY_TRASH_AUTO_DELETE_DAYS, v).apply(); }

    // ─── Reset ────────────────────────────────────────────────────

    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
