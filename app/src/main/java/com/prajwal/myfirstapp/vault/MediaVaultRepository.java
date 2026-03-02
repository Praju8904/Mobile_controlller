package com.prajwal.myfirstapp.vault;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Full data layer for the Personal Media Vault.
 *
 * Responsibilities:
 * - PIN setup, hashing, verification (PBKDF2)
 * - Biometric credential management
 * - Exponential lockout tracking
 * - Session management
 * - File import (encrypt + thumbnail), export (decrypt), secure delete
 * - Album CRUD
 * - Activity audit log
 * - Storage statistics
 *
 * Storage layout (app private internal storage):
 *   filesDir/media_vault/          ← vault root
 *   filesDir/media_vault/files/    ← encrypted files
 *   filesDir/media_vault/thumbs/   ← encrypted thumbnails
 *   filesDir/media_vault/.nomedia  ← prevents media scanner indexing
 */
public class MediaVaultRepository {

    private static final String TAG = "MediaVaultRepo";

    // ─── SharedPreferences Keys ───────────────────────────────────
    private static final String PREFS_NAME       = "media_vault_prefs";
    private static final String KEY_PIN_HASH      = "vault_pin_hash";
    private static final String KEY_PIN_SALT      = "vault_pin_salt";
    private static final String KEY_DECOY_HASH    = "vault_decoy_hash";
    private static final String KEY_DECOY_SALT    = "vault_decoy_salt";
    private static final String KEY_DECOY_ENABLED = "vault_decoy_enabled";
    private static final String KEY_FAILED_ATTEMPTS     = "vault_failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL        = "vault_lockout_until";
    private static final String KEY_LOCKOUT_LEVEL        = "vault_lockout_level";
    private static final String KEY_SESSION_DURATION_MS  = "vault_session_duration_ms";
    private static final String KEY_FILES_JSON    = "vault_files_json";
    private static final String KEY_ALBUMS_JSON   = "vault_albums_json";
    private static final String KEY_ACTIVITY_JSON = "vault_activity_json";
    private static final String KEY_COLLECTIONS_JSON      = "vault_collections_json";
    private static final String KEY_FILE_COLLECTIONS_JSON = "vault_file_collections_json";
    private static final String KEY_FILE_EXPIRY_JSON      = "vault_file_expiry_json";
    private static final String KEY_LAST_UNLOCK_TIME      = "vault_last_unlock_time";
    private static final String KEY_LAST_BACKUP_TIME      = "vault_last_backup_time";

    // Lockout durations (ms): level 0→30s, 1→1m, 2→5m, 3→15m, 4→1h
    private static final long[] LOCKOUT_DURATIONS_MS = {
        30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L
    };
    private static final int MAX_FAILED_BEFORE_LOCKOUT = 5;
    private static final long DEFAULT_SESSION_DURATION_MS = 2 * 60 * 1000L; // 2 minutes
    private static final int THUMBNAIL_MAX_DIMENSION = 256;
    private static final int ACTIVITY_LOG_MAX_ENTRIES = 500;

    private final Context context;
    private final SharedPreferences prefs;

    // In-memory state (only set when vault is unlocked)
    private char[] sessionPin = null;
    private boolean isDecoySession = false;

    // ─── Singleton ───────────────────────────────────────────────

    private static MediaVaultRepository instance;

    public static synchronized MediaVaultRepository getInstance(Context context) {
        if (instance == null) instance = new MediaVaultRepository(context.getApplicationContext());
        return instance;
    }

    private MediaVaultRepository(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureVaultDirectories();
    }

    // ─── Vault Directory Setup ────────────────────────────────────

    public File getVaultRoot() {
        File dir = new File(context.getFilesDir(), "media_vault");
        dir.mkdirs();
        return dir;
    }

    public File getFilesDir() {
        File dir = new File(getVaultRoot(), "files");
        dir.mkdirs();
        return dir;
    }

    public File getThumbsDir() {
        File dir = new File(getVaultRoot(), "thumbs");
        dir.mkdirs();
        return dir;
    }

    private void ensureVaultDirectories() {
        getVaultRoot();
        getFilesDir();
        getThumbsDir();
        // Create .nomedia to prevent media scanner from indexing vault
        File nomedia = new File(getVaultRoot(), ".nomedia");
        if (!nomedia.exists()) {
            try { nomedia.createNewFile(); } catch (IOException ignored) {}
        }
    }

    // ─── PIN Setup & Authentication ───────────────────────────────

    public boolean isPinSetup() {
        return prefs.contains(KEY_PIN_HASH);
    }

    /**
     * Set up the vault PIN for the first time.
     */
    public boolean setupPin(String pin) {
        byte[] salt = MediaVaultCrypto.generateFileSalt();
        String hash = MediaVaultCrypto.hashVaultPin(pin, salt);
        if (hash == null) return false;
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply();
        logActivity(new VaultActivityLog(VaultActivityLog.Action.PIN_CHANGED, "Vault PIN set up"));
        return true;
    }

    /**
     * Change the vault PIN (requires old PIN verification).
     */
    public boolean changePin(String oldPin, String newPin) {
        if (!verifyPin(oldPin)) return false;
        boolean success = setupPin(newPin);
        if (success) {
            // Update session pin
            sessionPin = newPin.toCharArray();
            logActivity(new VaultActivityLog(VaultActivityLog.Action.PIN_CHANGED, "Vault PIN changed"));
        }
        return success;
    }

    /**
     * Verify the vault PIN. Returns true if correct.
     * Handles lockout and failed attempt counting.
     */
    public boolean verifyPin(String pin) {
        String hashB64 = prefs.getString(KEY_PIN_HASH, null);
        String saltB64 = prefs.getString(KEY_PIN_SALT, null);
        if (hashB64 == null || saltB64 == null) return false;
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        return MediaVaultCrypto.verifyVaultPin(pin, salt, hashB64);
    }

    /**
     * Unlock the vault with a PIN. Tracks failed attempts and applies lockout.
     * Returns: 0=success, 1=wrong pin, 2=locked out
     */
    public int unlockWithPin(String pin) {
        if (isLockedOut()) return 2;

        // Check real PIN
        if (verifyPin(pin)) {
            resetFailedAttempts();
            sessionPin = pin.toCharArray();
            isDecoySession = false;
            logActivity(new VaultActivityLog(VaultActivityLog.Action.UNLOCKED, "Unlocked with PIN"));
            return 0;
        }

        // Check decoy PIN
        if (isDecoyEnabled() && verifyDecoyPin(pin)) {
            resetFailedAttempts();
            sessionPin = pin.toCharArray();
            isDecoySession = true;
            return 0; // Decoy session — don't log to real activity
        }

        // Wrong PIN
        int attempts = incrementFailedAttempts();
        if (attempts >= MAX_FAILED_BEFORE_LOCKOUT) {
            applyLockout();
        }
        logActivity(new VaultActivityLog(VaultActivityLog.Action.FAILED_ATTEMPT,
                "Failed PIN attempt (" + attempts + ")"));
        return 1;
    }

    /** Unlock via biometric (PIN already verified by biometric binding). */
    public void unlockWithBiometric(String pin) {
        sessionPin = pin.toCharArray();
        isDecoySession = false;
        resetFailedAttempts();
        logActivity(new VaultActivityLog(VaultActivityLog.Action.UNLOCKED, "Unlocked with biometric"));
    }

    public boolean isUnlocked() {
        return sessionPin != null;
    }

    /**
     * Returns a copy of the active session PIN for callers that need to perform
     * their own encryption (e.g. in-memory edited file re-encryption).
     * Returns null if the vault is locked. Callers must wipe the returned array
     * when finished (Arrays.fill(pin, '\0')).
     */
    public char[] getSessionPin() {
        if (sessionPin == null) return null;
        return sessionPin.clone();
    }

    public void lock() {
        if (sessionPin != null) {
            java.util.Arrays.fill(sessionPin, '\0');
            sessionPin = null;
        }
        isDecoySession = false;
        logActivity(new VaultActivityLog(VaultActivityLog.Action.LOCKED, "Vault locked"));
    }

    public boolean isDecoySession() {
        return isDecoySession;
    }

    // ─── Decoy PIN ────────────────────────────────────────────────

    public boolean isDecoyEnabled() {
        return prefs.getBoolean(KEY_DECOY_ENABLED, false);
    }

    public boolean setupDecoyPin(String pin) {
        byte[] salt = MediaVaultCrypto.generateFileSalt();
        String hash = MediaVaultCrypto.hashVaultPin(pin, salt);
        if (hash == null) return false;
        prefs.edit()
            .putString(KEY_DECOY_HASH, hash)
            .putString(KEY_DECOY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putBoolean(KEY_DECOY_ENABLED, true)
            .apply();
        return true;
    }

    /** Disables the decoy vault, clearing stored decoy credentials. */
    public void disableDecoy() {
        prefs.edit()
            .remove(KEY_DECOY_HASH)
            .remove(KEY_DECOY_SALT)
            .putBoolean(KEY_DECOY_ENABLED, false)
            .apply();
    }

    private boolean verifyDecoyPin(String pin) {
        String hashB64 = prefs.getString(KEY_DECOY_HASH, null);
        String saltB64 = prefs.getString(KEY_DECOY_SALT, null);
        if (hashB64 == null || saltB64 == null) return false;
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        return MediaVaultCrypto.verifyVaultPin(pin, salt, hashB64);
    }

    // ─── Lockout ──────────────────────────────────────────────────

    public boolean isLockedOut() {
        long until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        return System.currentTimeMillis() < until;
    }

    public long getLockoutRemainingMs() {
        long until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    private int incrementFailedAttempts() {
        int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
        return attempts;
    }

    private void resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply();
    }

    public int getFailedAttempts() {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    private void applyLockout() {
        int level = prefs.getInt(KEY_LOCKOUT_LEVEL, 0);
        long duration = LOCKOUT_DURATIONS_MS[Math.min(level, LOCKOUT_DURATIONS_MS.length - 1)];
        long until = System.currentTimeMillis() + duration;
        prefs.edit()
            .putLong(KEY_LOCKOUT_UNTIL, until)
            .putInt(KEY_LOCKOUT_LEVEL, Math.min(level + 1, LOCKOUT_DURATIONS_MS.length - 1))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
    }

    // ─── Session Duration ─────────────────────────────────────────

    public long getSessionDurationMs() {
        return prefs.getLong(KEY_SESSION_DURATION_MS, DEFAULT_SESSION_DURATION_MS);
    }

    public void setSessionDurationMs(long ms) {
        prefs.edit().putLong(KEY_SESSION_DURATION_MS, ms).apply();
    }

    // ─── File Import ──────────────────────────────────────────────

    /**
     * Import a file into the vault from a URI.
     *
     * @param sourceUri   URI of the file to import.
     * @param mimeType    MIME type of the file.
     * @param moveToVault If true, the original will be deleted after successful import.
     * @return The imported VaultFileItem, or null on failure.
     */
    public VaultFileItem importFile(Uri sourceUri, String mimeType, boolean moveToVault) {
        if (!isUnlocked()) return null;

        try {
            // Copy source to a temp file first
            File tempFile = new File(context.getCacheDir(), "vault_import_" + UUID.randomUUID());
            try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                if (is == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            }

            VaultFileItem item = new VaultFileItem();
            item.mimeType = mimeType;
            item.fileType = detectFileType(mimeType);
            item.originalSize = tempFile.length();
            item.originalCreatedAt = System.currentTimeMillis();

            // Try to get original filename from URI
            item.originalFileName = getFileNameFromUri(sourceUri);
            if (item.originalFileName == null) item.originalFileName = "file_" + item.id;

            // Populate media metadata
            populateMediaMetadata(item, tempFile, mimeType);

            // Encrypt the file
            String vaultFileName = UUID.randomUUID().toString();
            item.vaultFileName = vaultFileName;
            File destFile = new File(getFilesDir(), vaultFileName);
            MediaVaultCrypto.encryptFile(tempFile, destFile, sessionPin);
            item.encryptedSize = destFile.length();

            // Generate and encrypt thumbnail
            generateAndEncryptThumbnail(item, tempFile, mimeType);

            // Clean up temp file
            tempFile.delete();

            // Optionally delete original (move semantics)
            if (moveToVault) {
                try {
                    context.getContentResolver().delete(sourceUri, null, null);
                } catch (Exception ignored) {}
            }

            // Save metadata
            saveFile(item);
            logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_IMPORTED,
                    "Imported: " + item.originalFileName));

            return item;
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return null;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private VaultFileItem.FileType detectFileType(String mimeType) {
        if (mimeType == null) return VaultFileItem.FileType.OTHER;
        if (mimeType.startsWith("image/")) return VaultFileItem.FileType.IMAGE;
        if (mimeType.startsWith("video/")) return VaultFileItem.FileType.VIDEO;
        if (mimeType.startsWith("audio/")) return VaultFileItem.FileType.AUDIO;
        if (mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd") ||
                mimeType.startsWith("text/")) return VaultFileItem.FileType.DOCUMENT;
        return VaultFileItem.FileType.OTHER;
    }

    private void populateMediaMetadata(VaultFileItem item, File file, String mimeType) {
        try {
            if (mimeType != null && mimeType.startsWith("image/")) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                item.width = opts.outWidth;
                item.height = opts.outHeight;
            } else if (mimeType != null && (mimeType.startsWith("video/") || mimeType.startsWith("audio/"))) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(file.getAbsolutePath());
                String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (dur != null) item.duration = Long.parseLong(dur) / 1000;
                if (mimeType.startsWith("video/")) {
                    String w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if (w != null) item.width = Integer.parseInt(w);
                    if (h != null) item.height = Integer.parseInt(h);
                }
                mmr.release();
            }
        } catch (Exception ignored) {}
    }

    private void generateAndEncryptThumbnail(VaultFileItem item, File file, String mimeType) {
        try {
            Bitmap thumb = null;
            if (mimeType != null && mimeType.startsWith("image/")) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = computeSampleSize(file, THUMBNAIL_MAX_DIMENSION);
                thumb = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (thumb != null) {
                    thumb = scaleBitmap(thumb, THUMBNAIL_MAX_DIMENSION);
                }
            } else if (mimeType != null && mimeType.startsWith("video/")) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(file.getAbsolutePath());
                thumb = mmr.getFrameAtTime(0);
                mmr.release();
                if (thumb != null) thumb = scaleBitmap(thumb, THUMBNAIL_MAX_DIMENSION);
            }

            if (thumb != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] thumbBytes = baos.toByteArray();
                thumb.recycle();

                // Encrypt thumbnail
                byte[] encryptedThumb = MediaVaultCrypto.encryptBytes(thumbBytes, sessionPin);
                if (encryptedThumb != null) {
                    String thumbName = UUID.randomUUID().toString() + ".thumb";
                    File thumbFile = new File(getThumbsDir(), thumbName);
                    try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                        fos.write(encryptedThumb);
                    }
                    item.thumbnailPath = thumbName;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Thumbnail generation failed", e);
        }
    }

    private int computeSampleSize(File file, int maxDim) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        int scale = 1;
        while (o.outWidth / scale > maxDim * 2 || o.outHeight / scale > maxDim * 2) scale *= 2;
        return scale;
    }

    private Bitmap scaleBitmap(Bitmap src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    // ─── File Export ─────────────────────────────────────────────

    /**
     * Decrypt a vault file into memory and return the plaintext bytes.
     * Never writes unencrypted bytes to disk — safe for in-memory image rendering.
     *
     * @param item The vault file item to decrypt.
     * @return Decrypted byte array, or null on failure.
     */
    public byte[] decryptFileToMemory(VaultFileItem item) {
        if (!isUnlocked()) {
            Log.e(TAG, "decryptFileToMemory: vault is locked");
            return null;
        }
        if (item == null || item.vaultFileName == null || item.vaultFileName.isEmpty()) {
            Log.e(TAG, "decryptFileToMemory: null/empty vaultFileName");
            return null;
        }
        File encFile = new File(getFilesDir(), item.vaultFileName);
        if (!encFile.exists()) {
            Log.e(TAG, "decryptFileToMemory: encrypted file not found: " + item.vaultFileName);
            return null;
        }
        byte[] plainBytes = MediaVaultCrypto.decryptFileToMemory(encFile, sessionPin);
        if (plainBytes == null) {
            Log.e(TAG, "decryptFileToMemory: decryption returned null for: " + item.originalFileName);
        }
        return plainBytes;
    }

    /**
     * Decrypt a vault file and write to the given destination file.
     */
    public boolean exportFile(VaultFileItem item, File destFile) {
        if (!isUnlocked()) return false;
        byte[] plainBytes = decryptFileToMemory(item);
        if (plainBytes == null) return false;
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.write(plainBytes);
            logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_EXPORTED,
                    "Exported: " + item.originalFileName));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Export write failed", e);
            return false;
        }
    }

    /**
     * Decrypt a thumbnail into memory for display. Returns null if unavailable.
     */
    public Bitmap decryptThumbnail(VaultFileItem item) {
        if (!isUnlocked() || item.thumbnailPath == null || item.thumbnailPath.isEmpty()) return null;
        File thumbFile = new File(getThumbsDir(), item.thumbnailPath);
        if (!thumbFile.exists()) return null;
        try {
            byte[] encBytes = readFileBytes(thumbFile);
            byte[] plain = MediaVaultCrypto.decryptBytes(encBytes, sessionPin);
            if (plain == null) return null;
            return BitmapFactory.decodeByteArray(plain, 0, plain.length);
        } catch (Exception e) {
            Log.w(TAG, "Thumbnail decrypt failed", e);
            return null;
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int total = 0, n;
            while (total < bytes.length && (n = fis.read(bytes, total, bytes.length - total)) != -1) total += n;
            return bytes;
        }
    }

    // ─── File Deletion ────────────────────────────────────────────

    public void deleteFile(VaultFileItem item) {
        // Securely delete encrypted file
        File encFile = new File(getFilesDir(), item.vaultFileName);
        MediaVaultCrypto.secureDelete(encFile);

        // Delete thumbnail
        if (item.thumbnailPath != null && !item.thumbnailPath.isEmpty()) {
            File thumb = new File(getThumbsDir(), item.thumbnailPath);
            MediaVaultCrypto.secureDelete(thumb);
        }

        // Remove from index
        removeFile(item.id);

        // Remove from albums
        for (VaultAlbum album : getAlbums()) {
            if (item.id.equals(album.coverFileId)) {
                album.coverFileId = null;
                updateAlbum(album);
            }
        }

        logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_DELETED,
                "Deleted: " + item.originalFileName));
    }

    // ─── File Metadata Persistence ────────────────────────────────

    private List<VaultFileItem> loadFiles() {
        String json = prefs.getString(KEY_FILES_JSON, "[]");
        List<VaultFileItem> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                VaultFileItem f = VaultFileItem.fromJson(arr.getJSONObject(i));
                if (f != null) list.add(f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load files failed", e);
        }
        return list;
    }

    private void saveFiles(List<VaultFileItem> files) {
        try {
            JSONArray arr = new JSONArray();
            for (VaultFileItem f : files) arr.put(f.toJson());
            prefs.edit().putString(KEY_FILES_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Save files failed", e);
        }
    }

    private void saveFile(VaultFileItem item) {
        List<VaultFileItem> files = loadFiles();
        files.add(item);
        saveFiles(files);
    }

    /**
     * Public wrapper for persisting a new VaultFileItem that was created outside
     * the repository (e.g. by VaultImageEditorActivity after in-memory editing).
     */
    public void saveFileItem(VaultFileItem item) {
        saveFile(item);
    }

    /**
     * Import an already-decoded file (as raw bytes) into the vault.
     * Encrypts and stores the file, then saves metadata.
     *
     * @param bytes     Raw plaintext bytes of the file.
     * @param fileName  Original display name.
     * @param mimeType  MIME type string.
     * @return The new VaultFileItem, or null on failure.
     */
    public VaultFileItem importFileFromBytes(byte[] bytes, String fileName, String mimeType) {
        if (!isUnlocked() || bytes == null) return null;
        try {
            File tempFile = new File(context.getCacheDir(), "vault_import_" + UUID.randomUUID());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
            }

            VaultFileItem item = new VaultFileItem();
            item.mimeType = mimeType;
            item.fileType = detectFileType(mimeType);
            item.originalFileName = (fileName != null && !fileName.isEmpty()) ? fileName : "file_" + item.id;
            item.originalSize = bytes.length;
            item.originalCreatedAt = System.currentTimeMillis();

            populateMediaMetadata(item, tempFile, mimeType);

            String vaultFileName = UUID.randomUUID().toString();
            item.vaultFileName = vaultFileName;
            File destFile = new File(getFilesDir(), vaultFileName);
            MediaVaultCrypto.encryptFile(tempFile, destFile, sessionPin);
            item.encryptedSize = destFile.length();

            generateAndEncryptThumbnail(item, tempFile, mimeType);
            tempFile.delete();

            saveFile(item);
            logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_IMPORTED,
                    "Imported (bytes): " + item.originalFileName));
            return item;
        } catch (Exception e) {
            Log.e(TAG, "importFileFromBytes failed", e);
            return null;
        }
    }

    public void updateFile(VaultFileItem item) {
        List<VaultFileItem> files = loadFiles();
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).id.equals(item.id)) {
                files.set(i, item);
                saveFiles(files);
                return;
            }
        }
    }

    private void removeFile(String id) {
        List<VaultFileItem> files = loadFiles();
        for (int i = files.size() - 1; i >= 0; i--) {
            if (files.get(i).id.equals(id)) { files.remove(i); break; }
        }
        saveFiles(files);
    }

    // ─── File Querying ────────────────────────────────────────────

    public List<VaultFileItem> getAllFiles() {
        if (isDecoySession) return new ArrayList<>();
        return loadFiles();
    }

    public List<VaultFileItem> getFilesByType(VaultFileItem.FileType type) {
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            if (f.fileType == type) result.add(f);
        }
        return result;
    }

    public List<VaultFileItem> getFilesByAlbum(String albumId) {
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            if (albumId.equals(f.albumId)) result.add(f);
        }
        return result;
    }

    public List<VaultFileItem> getFavourites() {
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            if (f.isFavourited) result.add(f);
        }
        return result;
    }

    public List<VaultFileItem> getRecentFiles(int limit) {
        List<VaultFileItem> all = getAllFiles();
        Collections.sort(all, (a, b) -> Long.compare(b.importedAt, a.importedAt));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<VaultFileItem> searchFiles(String query) {
        if (query == null || query.trim().isEmpty()) return getAllFiles();
        String q = query.toLowerCase();
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            boolean matches = (f.originalFileName != null && f.originalFileName.toLowerCase().contains(q)) ||
                    (f.notes != null && f.notes.toLowerCase().contains(q));
            if (!matches && f.tags != null) {
                for (String tag : f.tags) {
                    if (tag.toLowerCase().contains(q)) { matches = true; break; }
                }
            }
            if (matches) result.add(f);
        }
        return result;
    }

    public List<VaultFileItem> sortFiles(List<VaultFileItem> files, String sortBy) {
        List<VaultFileItem> sorted = new ArrayList<>(files);
        Comparator<VaultFileItem> cmp;
        switch (sortBy) {
            case "date_taken":
                cmp = (a, b) -> Long.compare(b.originalCreatedAt, a.originalCreatedAt); break;
            case "name":
                cmp = (a, b) -> {
                    String na = a.originalFileName != null ? a.originalFileName : "";
                    String nb = b.originalFileName != null ? b.originalFileName : "";
                    return na.compareToIgnoreCase(nb);
                }; break;
            case "size":
                cmp = (a, b) -> Long.compare(b.originalSize, a.originalSize); break;
            case "duration":
                cmp = (a, b) -> Long.compare(b.duration, a.duration); break;
            default: // date_added newest first
                cmp = (a, b) -> Long.compare(b.importedAt, a.importedAt); break;
        }
        Collections.sort(sorted, cmp);
        return sorted;
    }

    // ─── Storage Stats ────────────────────────────────────────────

    public long getTotalStorageUsed() {
        long total = 0;
        for (VaultFileItem f : loadFiles()) total += f.encryptedSize;
        return total;
    }

    public int getCountByType(VaultFileItem.FileType type) {
        int count = 0;
        for (VaultFileItem f : loadFiles()) if (f.fileType == type) count++;
        return count;
    }

    // ─── Album CRUD ───────────────────────────────────────────────

    public List<VaultAlbum> getAlbums() {
        if (isDecoySession) return new ArrayList<>();
        String json = prefs.getString(KEY_ALBUMS_JSON, "[]");
        List<VaultAlbum> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                VaultAlbum a = VaultAlbum.fromJson(arr.getJSONObject(i));
                if (a != null) list.add(a);
            }
        } catch (Exception ignored) {}
        // Update file counts
        for (VaultAlbum album : list) {
            album.fileCount = getFilesByAlbum(album.id).size();
        }
        return list;
    }

    private void saveAlbums(List<VaultAlbum> albums) {
        try {
            JSONArray arr = new JSONArray();
            for (VaultAlbum a : albums) arr.put(a.toJson());
            prefs.edit().putString(KEY_ALBUMS_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public VaultAlbum createAlbum(String name, String colorHex) {
        VaultAlbum album = new VaultAlbum();
        album.name = name;
        album.colorHex = colorHex;
        List<VaultAlbum> albums = getAlbums();
        albums.add(album);
        saveAlbums(albums);
        logActivity(new VaultActivityLog(VaultActivityLog.Action.ALBUM_CREATED, "Album: " + name));
        return album;
    }

    public void updateAlbum(VaultAlbum updated) {
        List<VaultAlbum> albums = getAlbums();
        for (int i = 0; i < albums.size(); i++) {
            if (albums.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                albums.set(i, updated);
                saveAlbums(albums);
                return;
            }
        }
    }

    public void deleteAlbum(String albumId) {
        // Remove album reference from files but keep the files in vault
        List<VaultFileItem> files = loadFiles();
        for (VaultFileItem f : files) {
            if (albumId.equals(f.albumId)) f.albumId = null;
        }
        saveFiles(files);

        List<VaultAlbum> albums = getAlbums();
        String albumName = "";
        for (int i = albums.size() - 1; i >= 0; i--) {
            if (albums.get(i).id.equals(albumId)) {
                albumName = albums.get(i).name != null ? albums.get(i).name : "";
                albums.remove(i);
                break;
            }
        }
        saveAlbums(albums);

        logActivity(new VaultActivityLog(VaultActivityLog.Action.ALBUM_DELETED, "Album: " + albumName));
    }

    public void addFileToAlbum(String fileId, String albumId) {
        List<VaultFileItem> files = loadFiles();
        for (VaultFileItem f : files) {
            if (f.id.equals(fileId)) {
                f.albumId = albumId;
                saveFiles(files);
                return;
            }
        }
    }

    // ─── Activity Log ─────────────────────────────────────────────

    private void logActivity(VaultActivityLog entry) {
        List<VaultActivityLog> log = getActivityLog();
        log.add(0, entry); // most recent first
        // Trim to max entries
        if (log.size() > ACTIVITY_LOG_MAX_ENTRIES) {
            log = log.subList(0, ACTIVITY_LOG_MAX_ENTRIES);
        }
        try {
            JSONArray arr = new JSONArray();
            for (VaultActivityLog e : log) arr.put(e.toJson());
            prefs.edit().putString(KEY_ACTIVITY_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public List<VaultActivityLog> getActivityLog() {
        String json = prefs.getString(KEY_ACTIVITY_JSON, "[]");
        List<VaultActivityLog> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                VaultActivityLog e = VaultActivityLog.fromJson(arr.getJSONObject(i));
                if (e != null) list.add(e);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void clearActivityLog() {
        prefs.edit().putString(KEY_ACTIVITY_JSON, "[]").apply();
    }

    public void logFileViewed(VaultFileItem item) {
        item.lastAccessedAt = System.currentTimeMillis();
        updateFile(item);
        logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_VIEWED, item.originalFileName));
    }

    // ─── Collections ─────────────────────────────────────────────

    public List<VaultCollection> getCollections() {
        String json = prefs.getString(KEY_COLLECTIONS_JSON, "[]");
        List<VaultCollection> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                VaultCollection c = VaultCollection.fromJson(arr.getJSONObject(i));
                if (c != null) list.add(c);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveCollections(List<VaultCollection> collections) {
        try {
            JSONArray arr = new JSONArray();
            for (VaultCollection c : collections) arr.put(c.toJson());
            prefs.edit().putString(KEY_COLLECTIONS_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public VaultCollection createCollection(String name, String colorHex) {
        VaultCollection c = new VaultCollection(name, colorHex);
        List<VaultCollection> list = getCollections();
        list.add(c);
        saveCollections(list);
        return c;
    }

    public void deleteCollection(String collectionId) {
        List<VaultCollection> list = getCollections();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(collectionId)) { list.remove(i); break; }
        }
        saveCollections(list);
    }

    /** Returns the JSON object mapping fileId -> array of collectionIds from SharedPreferences. */
    private JSONObject loadFileCollectionsMap() {
        String json = prefs.getString(KEY_FILE_COLLECTIONS_JSON, "{}");
        try { return new JSONObject(json); } catch (Exception e) { return new JSONObject(); }
    }

    private void saveFileCollectionsMap(JSONObject map) {
        prefs.edit().putString(KEY_FILE_COLLECTIONS_JSON, map.toString()).apply();
    }

    public void addFileToCollection(String fileId, String collectionId) {
        JSONObject map = loadFileCollectionsMap();
        try {
            JSONArray ids = map.optJSONArray(fileId);
            if (ids == null) ids = new JSONArray();
            // avoid duplicates
            for (int i = 0; i < ids.length(); i++) {
                if (collectionId.equals(ids.getString(i))) return;
            }
            ids.put(collectionId);
            map.put(fileId, ids);
            saveFileCollectionsMap(map);
        } catch (Exception ignored) {}
    }

    public void removeFileFromCollection(String fileId, String collectionId) {
        JSONObject map = loadFileCollectionsMap();
        try {
            JSONArray ids = map.optJSONArray(fileId);
            if (ids == null) return;
            JSONArray updated = new JSONArray();
            for (int i = 0; i < ids.length(); i++) {
                if (!collectionId.equals(ids.getString(i))) updated.put(ids.getString(i));
            }
            map.put(fileId, updated);
            saveFileCollectionsMap(map);
        } catch (Exception ignored) {}
    }

    public List<String> getFileCollectionIds(String fileId) {
        JSONObject map = loadFileCollectionsMap();
        List<String> result = new ArrayList<>();
        try {
            JSONArray ids = map.optJSONArray(fileId);
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) result.add(ids.getString(i));
            }
        } catch (Exception ignored) {}
        return result;
    }

    public List<VaultFileItem> getFilesInCollection(String collectionId) {
        JSONObject map = loadFileCollectionsMap();
        List<VaultFileItem> allFiles = getAllFiles();
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : allFiles) {
            JSONArray ids = map.optJSONArray(f.id);
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    try {
                        if (collectionId.equals(ids.getString(i))) { result.add(f); break; }
                    } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }

    // ─── Wipe All Files ──────────────────────────────────────────

    /**
     * Deletes all vault files and clears the file list.
     * Used by auto-destroy and manual wipe flows.
     */
    public void wipeAllFiles() {
        List<VaultFileItem> files = loadFiles();
        for (VaultFileItem item : files) {
            File encFile = new File(getFilesDir(), item.vaultFileName);
            MediaVaultCrypto.secureDelete(encFile);
            if (item.thumbnailPath != null && !item.thumbnailPath.isEmpty()) {
                MediaVaultCrypto.secureDelete(new File(getThumbsDir(), item.thumbnailPath));
            }
        }
        saveFiles(new ArrayList<>());
        logActivity(new VaultActivityLog(VaultActivityLog.Action.FILE_DELETED, "All files wiped"));
    }

    // ─── Auto-Destroy Check ──────────────────────────────────────

    /**
     * Checks if auto-destroy should be triggered based on current failed attempts.
     * If triggered, wipes all vault files and returns true.
     */
    public boolean checkAutoDestroy() {
        int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
        if (VaultAutoDestroyManager.shouldDestroy(context, attempts)) {
            wipeAllFiles();
            return true;
        }
        return false;
    }

    // ─── File Expiry ─────────────────────────────────────────────

    private JSONObject loadFileExpiryMap() {
        String json = prefs.getString(KEY_FILE_EXPIRY_JSON, "{}");
        try { return new JSONObject(json); } catch (Exception e) { return new JSONObject(); }
    }

    private void saveFileExpiryMap(JSONObject map) {
        prefs.edit().putString(KEY_FILE_EXPIRY_JSON, map.toString()).apply();
    }

    public void setFileExpiry(String fileId, long expiryTimestamp) {
        JSONObject map = loadFileExpiryMap();
        try { map.put(fileId, expiryTimestamp); saveFileExpiryMap(map); } catch (Exception ignored) {}
    }

    public long getFileExpiry(String fileId) {
        JSONObject map = loadFileExpiryMap();
        return map.optLong(fileId, 0L);
    }

    public List<VaultFileItem> getExpiredFiles() {
        long now = System.currentTimeMillis();
        JSONObject map = loadFileExpiryMap();
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            long expiry = map.optLong(f.id, 0L);
            if (expiry > 0 && expiry < now) result.add(f);
        }
        return result;
    }

    public List<VaultFileItem> getUpcomingExpiryFiles(int daysAhead) {
        long now = System.currentTimeMillis();
        long cutoff = now + java.util.concurrent.TimeUnit.DAYS.toMillis(daysAhead);
        JSONObject map = loadFileExpiryMap();
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            long expiry = map.optLong(f.id, 0L);
            if (expiry > now && expiry <= cutoff) result.add(f);
        }
        return result;
    }

    // ─── Duplicate Detection ─────────────────────────────────────

    /**
     * Returns existing vault files where originalSize == fileSize AND originalFileName == fileName.
     */
    public List<VaultFileItem> findDuplicates(long fileSize, String fileName) {
        List<VaultFileItem> result = new ArrayList<>();
        for (VaultFileItem f : getAllFiles()) {
            if (f.originalSize == fileSize && fileName.equals(f.originalFileName)) result.add(f);
        }
        return result;
    }

    // ─── Unlock Time & Backup Tracking ───────────────────────────

    /**
     * Records the current time as the last vault unlock time.
     * Call this from unlock flows after a successful unlock.
     */
    public void recordUnlockTime() {
        prefs.edit().putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis()).apply();
    }

    /** Returns the timestamp of the last successful unlock, or 0 if never unlocked. */
    public long getLastUnlockTime() {
        return prefs.getLong(KEY_LAST_UNLOCK_TIME, 0L);
    }

    /** Records the current time as the last backup creation time. */
    public void recordBackupCreated() {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis()).apply();
    }

    /** Returns the timestamp of the last backup, or 0 if never backed up. */
    public long getLastBackupTime() {
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L);
    }
}
