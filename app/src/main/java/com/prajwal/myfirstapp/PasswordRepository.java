package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;

/**
 * Encrypted password vault storage using SharedPreferences + AES-256-GCM.
 * Master password is hashed with PBKDF2. Vault data encrypted at rest.
 */
public class PasswordRepository {

    private static final String TAG = "PasswordRepo";
    private static final String PREFS_NAME = "password_vault_prefs";
    private static final String KEY_MASTER_HASH = "master_hash";
    private static final String KEY_MASTER_SALT = "master_salt";
    private static final String KEY_ENCRYPTION_SALT = "encryption_salt";
    private static final String KEY_VAULT_DATA = "vault_data";
    private static final String KEY_AUTO_LOCK_MS = "auto_lock_ms";
    private static final long TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final Context context;
    private SecretKey encryptionKey;
    private ArrayList<PasswordEntry> entries;

    public PasswordRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Master Password Setup ───────────────────────────────────

    public boolean isMasterPasswordSet() {
        return getPrefs().contains(KEY_MASTER_HASH);
    }

    public boolean setupMasterPassword(String password) {
        byte[] authSalt = VaultCryptoManager.generateSalt();
        byte[] encSalt = VaultCryptoManager.generateSalt();
        byte[] hash = VaultCryptoManager.hashMasterPassword(password, authSalt);
        if (hash == null) return false;

        getPrefs().edit()
                .putString(KEY_MASTER_HASH, VaultCryptoManager.toBase64(hash))
                .putString(KEY_MASTER_SALT, VaultCryptoManager.toBase64(authSalt))
                .putString(KEY_ENCRYPTION_SALT, VaultCryptoManager.toBase64(encSalt))
                .apply();

        // Derive encryption key and initialize empty vault
        encryptionKey = VaultCryptoManager.deriveEncryptionKey(password, encSalt);
        entries = new ArrayList<>();
        saveVault();
        return true;
    }

    public boolean changeMasterPassword(String oldPassword, String newPassword) {
        if (!verifyAndUnlock(oldPassword)) return false;

        // Re-encrypt with new key
        byte[] newAuthSalt = VaultCryptoManager.generateSalt();
        byte[] newEncSalt = VaultCryptoManager.generateSalt();
        byte[] newHash = VaultCryptoManager.hashMasterPassword(newPassword, newAuthSalt);
        if (newHash == null) return false;

        SecretKey newKey = VaultCryptoManager.deriveEncryptionKey(newPassword, newEncSalt);
        if (newKey == null) return false;

        encryptionKey = newKey;

        getPrefs().edit()
                .putString(KEY_MASTER_HASH, VaultCryptoManager.toBase64(newHash))
                .putString(KEY_MASTER_SALT, VaultCryptoManager.toBase64(newAuthSalt))
                .putString(KEY_ENCRYPTION_SALT, VaultCryptoManager.toBase64(newEncSalt))
                .apply();

        saveVault();
        return true;
    }

    // ─── Authentication ──────────────────────────────────────────

    public boolean verifyAndUnlock(String password) {
        String hashB64 = getPrefs().getString(KEY_MASTER_HASH, null);
        String saltB64 = getPrefs().getString(KEY_MASTER_SALT, null);
        String encSaltB64 = getPrefs().getString(KEY_ENCRYPTION_SALT, null);
        if (hashB64 == null || saltB64 == null || encSaltB64 == null) return false;

        byte[] storedHash = VaultCryptoManager.fromBase64(hashB64);
        byte[] salt = VaultCryptoManager.fromBase64(saltB64);

        if (!VaultCryptoManager.verifyMasterPassword(password, salt, storedHash)) return false;

        byte[] encSalt = VaultCryptoManager.fromBase64(encSaltB64);
        encryptionKey = VaultCryptoManager.deriveEncryptionKey(password, encSalt);
        loadVault();
        return true;
    }

    /** Unlock vault with biometric (re-derive key from cached credential). */
    public boolean unlockWithBiometric(String cachedPassword) {
        return verifyAndUnlock(cachedPassword);
    }

    public void lock() {
        encryptionKey = null;
        entries = null;
    }

    public boolean isUnlocked() {
        return encryptionKey != null && entries != null;
    }

    // ─── Vault I/O ───────────────────────────────────────────────

    private void loadVault() {
        entries = new ArrayList<>();
        String encrypted = getPrefs().getString(KEY_VAULT_DATA, null);
        if (encrypted == null) return;

        String json = VaultCryptoManager.decrypt(encrypted, encryptionKey);
        if (json == null) { Log.e(TAG, "Failed to decrypt vault"); return; }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                PasswordEntry e = PasswordEntry.fromJson(arr.getJSONObject(i));
                if (e != null) entries.add(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse vault", e);
        }

        // Purge expired trash
        purgeExpiredTrash();
    }

    private void saveVault() {
        if (encryptionKey == null || entries == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (PasswordEntry e : entries) arr.put(e.toJson());
            String encrypted = VaultCryptoManager.encrypt(arr.toString(), encryptionKey);
            getPrefs().edit().putString(KEY_VAULT_DATA, encrypted).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save vault", e);
        }
    }

    // ─── CRUD ────────────────────────────────────────────────────

    public ArrayList<PasswordEntry> getActiveEntries() {
        if (entries == null) return new ArrayList<>();
        ArrayList<PasswordEntry> active = new ArrayList<>();
        for (PasswordEntry e : entries) {
            if (!e.isDeleted) active.add(e);
        }
        Collections.sort(active, (a, b) -> Long.compare(b.modifiedAt, a.modifiedAt));
        return active;
    }

    public ArrayList<PasswordEntry> getPasswords() {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ("password".equals(e.type)) result.add(e);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getSecureNotes() {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ("secure_note".equals(e.type)) result.add(e);
        }
        return result;
    }

    public void addEntry(PasswordEntry entry) {
        if (entries == null) entries = new ArrayList<>();
        entries.add(entry);
        saveVault();
    }

    public void updateEntry(PasswordEntry updated) {
        if (entries == null) return;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(updated.id)) {
                updated.modifiedAt = System.currentTimeMillis();
                entries.set(i, updated);
                saveVault();
                return;
            }
        }
    }

    public void markUsed(String id) {
        if (entries == null) return;
        for (PasswordEntry e : entries) {
            if (e.id.equals(id)) {
                e.lastUsedAt = System.currentTimeMillis();
                saveVault();
                return;
            }
        }
    }

    public void softDelete(String id) {
        if (entries == null) return;
        for (PasswordEntry e : entries) {
            if (e.id.equals(id)) {
                e.isDeleted = true;
                e.deletedAt = System.currentTimeMillis();
                saveVault();
                return;
            }
        }
    }

    public void restoreFromTrash(String id) {
        if (entries == null) return;
        for (PasswordEntry e : entries) {
            if (e.id.equals(id) && e.isDeleted) {
                e.isDeleted = false;
                e.deletedAt = 0;
                saveVault();
                return;
            }
        }
    }

    public void permanentDelete(String id) {
        if (entries == null) return;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id.equals(id)) {
                entries.remove(i);
                saveVault();
                return;
            }
        }
    }

    public void toggleFavourite(String id) {
        if (entries == null) return;
        for (PasswordEntry e : entries) {
            if (e.id.equals(id)) {
                e.isFavourite = !e.isFavourite;
                saveVault();
                return;
            }
        }
    }

    private void purgeExpiredTrash() {
        if (entries == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            PasswordEntry e = entries.get(i);
            if (e.isDeleted && e.deletedAt > 0 && (now - e.deletedAt) > TRASH_RETENTION_MS) {
                entries.remove(i);
                changed = true;
            }
        }
        if (changed) saveVault();
    }

    // ─── Query / Filter ──────────────────────────────────────────

    public ArrayList<PasswordEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) return getActiveEntries();
        String q = query.toLowerCase();
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ((e.siteName != null && e.siteName.toLowerCase().contains(q)) ||
                (e.username != null && e.username.toLowerCase().contains(q)) ||
                (e.category != null && e.category.toLowerCase().contains(q)) ||
                (e.notes != null && e.notes.toLowerCase().contains(q))) {
                result.add(e);
            }
        }
        return result;
    }

    public ArrayList<PasswordEntry> getByCategory(String category) {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if (category.equals(e.category)) result.add(e);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getFavourites() {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if (e.isFavourite) result.add(e);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getWeakPasswords() {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if (e.isWeak()) result.add(e);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getReusedPasswords() {
        Map<String, java.util.List<PasswordEntry>> map = new HashMap<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ("password".equals(e.type) && e.password != null && !e.password.isEmpty()) {
                map.computeIfAbsent(e.password, k -> new ArrayList<>()).add(e);
            }
        }
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (java.util.List<PasswordEntry> group : map.values()) {
            if (group.size() > 1) result.addAll(group);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getOldPasswords() {
        ArrayList<PasswordEntry> result = new ArrayList<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ("password".equals(e.type) && e.isOld()) result.add(e);
        }
        return result;
    }

    public ArrayList<PasswordEntry> getTrash() {
        if (entries == null) return new ArrayList<>();
        ArrayList<PasswordEntry> trash = new ArrayList<>();
        for (PasswordEntry e : entries) {
            if (e.isDeleted) trash.add(e);
        }
        return trash;
    }

    // ─── Health / Security Score ──────────────────────────────────

    public int getTotalCount() {
        return getActiveEntries().size();
    }

    public int getWeakCount() {
        return getWeakPasswords().size();
    }

    public int getReusedCount() {
        Set<String> reusedIds = new HashSet<>();
        Map<String, java.util.List<String>> map = new HashMap<>();
        for (PasswordEntry e : getActiveEntries()) {
            if ("password".equals(e.type) && e.password != null && !e.password.isEmpty()) {
                map.computeIfAbsent(e.password, k -> new ArrayList<>()).add(e.id);
            }
        }
        for (java.util.List<String> group : map.values()) {
            if (group.size() > 1) reusedIds.addAll(group);
        }
        return reusedIds.size();
    }

    public int getOldCount() {
        return getOldPasswords().size();
    }

    /** Security score 0–100 based on password health. */
    public int getSecurityScore() {
        ArrayList<PasswordEntry> passwords = getPasswords();
        if (passwords.isEmpty()) return 100;

        int total = passwords.size();
        int weakCount = 0, reusedCount = 0, oldCount = 0;
        int totalStrength = 0;

        Set<String> seenPasswords = new HashSet<>();
        Set<String> duplicatePasswords = new HashSet<>();

        for (PasswordEntry e : passwords) {
            totalStrength += e.getPasswordStrength();
            if (e.isWeak()) weakCount++;
            if (e.isOld()) oldCount++;
            if (e.password != null && !e.password.isEmpty()) {
                if (!seenPasswords.add(e.password)) duplicatePasswords.add(e.password);
            }
        }

        for (PasswordEntry e : passwords) {
            if (e.password != null && duplicatePasswords.contains(e.password)) reusedCount++;
        }

        double avgStrength = (double) totalStrength / total;
        double weakPenalty = (double) weakCount / total * 30;
        double reusedPenalty = (double) reusedCount / total * 25;
        double oldPenalty = (double) oldCount / total * 15;

        int score = (int) (avgStrength - weakPenalty - reusedPenalty - oldPenalty);
        return Math.max(0, Math.min(100, score));
    }

    // ─── Auto-lock Settings ──────────────────────────────────────

    public long getAutoLockMs() {
        return getPrefs().getLong(KEY_AUTO_LOCK_MS, 60000); // default 1 min
    }

    public void setAutoLockMs(long ms) {
        getPrefs().edit().putLong(KEY_AUTO_LOCK_MS, ms).apply();
    }

    // ─── Import / Export ─────────────────────────────────────────

    public String exportToEncryptedJson() {
        if (!isUnlocked()) return null;
        try {
            JSONObject export = new JSONObject();
            JSONArray arr = new JSONArray();
            for (PasswordEntry e : getActiveEntries()) arr.put(e.toJson());
            export.put("entries", arr);
            export.put("exportedAt", System.currentTimeMillis());
            export.put("version", 1);
            return VaultCryptoManager.encrypt(export.toString(), encryptionKey);
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            return null;
        }
    }

    public String exportToCsv() {
        if (!isUnlocked()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(PasswordEntry.csvHeader()).append("\n");
        for (PasswordEntry e : getPasswords()) {
            sb.append(e.toCsvLine()).append("\n");
        }
        return sb.toString();
    }

    public int importFromCsv(String csvData) {
        if (!isUnlocked() || csvData == null) return 0;
        int count = 0;
        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvData));
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    // Skip header if it looks like one
                    String lower = line.toLowerCase();
                    if (lower.contains("name") || lower.contains("url") || lower.contains("username"))
                        continue;
                }
                PasswordEntry entry = PasswordEntry.fromCsvLine(line);
                if (entry != null && entry.siteName != null && !entry.siteName.isEmpty()) {
                    addEntry(entry);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
        }
        return count;
    }

    /** Wipe entire vault — requires auth confirmation before calling. */
    public void clearVault() {
        if (entries != null) {
            entries.clear();
            saveVault();
        }
    }
}
