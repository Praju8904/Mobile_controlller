package com.prajwal.myfirstapp.vault;

import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles file-level AES-256-GCM encryption/decryption for the Personal Media Vault.
 *
 * Security model:
 * - Each file has a unique 32-byte salt.
 * - The encryption key is derived from the vault PIN + salt using PBKDF2-HMAC-SHA256.
 * - Encrypted file format: [4-byte salt length][salt][12-byte IV][ciphertext+GCM tag]
 * - The raw key is NEVER stored on disk — derived fresh each session and held in memory.
 */
public class MediaVaultCrypto {

    private static final String TAG = "MediaVaultCrypto";
    private static final int PBKDF2_ITERATIONS = 200_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // ─── Key Derivation ──────────────────────────────────────────

    /** Generate a new random salt for a file. */
    public static byte[] generateFileSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Derive an AES-256 key from the vault PIN and a file-specific salt.
     * The returned key is held in memory only — never persisted.
     */
    public static SecretKey deriveFileKey(char[] vaultPin, byte[] fileSalt) {
        try {
            KeySpec spec = new PBEKeySpec(vaultPin, fileSalt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Key derivation failed", e);
            return null;
        }
    }

    // ─── Hash PIN for storage ─────────────────────────────────────

    /**
     * Hash the vault PIN using PBKDF2 for secure storage.
     * Returns Base64(hash).
     */
    public static String hashVaultPin(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "PIN hash failed", e);
            return null;
        }
    }

    public static boolean verifyVaultPin(String pin, byte[] salt, String storedHashB64) {
        String computed = hashVaultPin(pin, salt);
        if (computed == null || storedHashB64 == null) return false;
        // Constant-time comparison
        if (computed.length() != storedHashB64.length()) return false;
        int diff = 0;
        for (int i = 0; i < computed.length(); i++) diff |= computed.charAt(i) ^ storedHashB64.charAt(i);
        return diff == 0;
    }

    // ─── File Encryption ─────────────────────────────────────────

    /**
     * Encrypts a plaintext file and writes to the destination encrypted file.
     * File format: [4 bytes: salt length][salt bytes][12 bytes: IV][ciphertext + 16-byte GCM tag]
     *
     * @param sourceFile  The unencrypted source file.
     * @param destFile    The destination encrypted file.
     * @param vaultPin    The vault PIN chars (not stored).
     * @return The salt used (must be persisted in metadata so decryption can re-derive the key).
     */
    public static byte[] encryptFile(File sourceFile, File destFile, char[] vaultPin) throws IOException {
        byte[] salt = generateFileSalt();
        SecretKey key = deriveFileKey(vaultPin, salt);
        if (key == null) throw new IOException("Key derivation failed");

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile)) {

            // Write salt length + salt
            fos.write(intToBytes(salt.length));
            fos.write(salt);
            // Write IV
            fos.write(iv);

            // Read source into memory (for files up to reasonable size)
            byte[] plainBytes = new byte[(int) sourceFile.length()];
            int totalRead = 0;
            int read;
            while (totalRead < plainBytes.length &&
                    (read = fis.read(plainBytes, totalRead, plainBytes.length - totalRead)) != -1) {
                totalRead += read;
            }

            // Encrypt
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainBytes);
            fos.write(cipherBytes);

        } catch (Exception e) {
            destFile.delete();
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        }

        return salt;
    }

    /**
     * Decrypts an encrypted vault file into memory and returns the plaintext bytes.
     * Never writes unencrypted bytes to disk.
     *
     * @param encryptedFile The encrypted vault file.
     * @param vaultPin      The vault PIN chars.
     * @return Decrypted bytes, or null on failure.
     */
    public static byte[] decryptFileToMemory(File encryptedFile, char[] vaultPin) {
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            // Read salt
            byte[] saltLenBytes = new byte[4];
            if (fis.read(saltLenBytes) != 4) return null;
            int saltLen = bytesToInt(saltLenBytes);
            if (saltLen <= 0 || saltLen > 256) return null;
            byte[] salt = new byte[saltLen];
            if (fis.read(salt) != saltLen) return null;

            // Read IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            if (fis.read(iv) != GCM_IV_LENGTH) return null;

            // Read ciphertext
            long headerSize = 4L + saltLen + GCM_IV_LENGTH;
            long cipherLen = encryptedFile.length() - headerSize;
            if (cipherLen <= 0) return null;
            byte[] cipherBytes = new byte[(int) cipherLen];
            int totalRead = 0, r;
            while (totalRead < cipherBytes.length &&
                    (r = fis.read(cipherBytes, totalRead, cipherBytes.length - totalRead)) != -1) {
                totalRead += r;
            }

            // Derive key and decrypt
            SecretKey key = deriveFileKey(vaultPin, salt);
            if (key == null) return null;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherBytes);

        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    /**
     * Encrypts raw bytes (e.g. thumbnail) using a derived key from PIN and a new salt.
     * Returns: [4-byte salt len][salt][12-byte IV][ciphertext]
     */
    public static byte[] encryptBytes(byte[] plainBytes, char[] vaultPin) {
        try {
            byte[] salt = generateFileSalt();
            SecretKey key = deriveFileKey(vaultPin, salt);
            if (key == null) return null;

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            byte[] result = new byte[4 + salt.length + GCM_IV_LENGTH + cipherBytes.length];
            byte[] saltLenBytes = intToBytes(salt.length);
            System.arraycopy(saltLenBytes, 0, result, 0, 4);
            System.arraycopy(salt, 0, result, 4, salt.length);
            System.arraycopy(iv, 0, result, 4 + salt.length, GCM_IV_LENGTH);
            System.arraycopy(cipherBytes, 0, result, 4 + salt.length + GCM_IV_LENGTH, cipherBytes.length);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Byte encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypts bytes produced by encryptBytes() back to plaintext in memory.
     */
    public static byte[] decryptBytes(byte[] encryptedBytes, char[] vaultPin) {
        try {
            int saltLen = bytesToInt(new byte[]{encryptedBytes[0], encryptedBytes[1],
                    encryptedBytes[2], encryptedBytes[3]});
            byte[] salt = new byte[saltLen];
            System.arraycopy(encryptedBytes, 4, salt, 0, saltLen);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedBytes, 4 + saltLen, iv, 0, GCM_IV_LENGTH);
            int cipherStart = 4 + saltLen + GCM_IV_LENGTH;
            byte[] cipherBytes = new byte[encryptedBytes.length - cipherStart];
            System.arraycopy(encryptedBytes, cipherStart, cipherBytes, 0, cipherBytes.length);

            SecretKey key = deriveFileKey(vaultPin, salt);
            if (key == null) return null;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            Log.e(TAG, "Byte decryption failed", e);
            return null;
        }
    }

    // ─── Secure File Delete ───────────────────────────────────────

    /**
     * Securely wipe a file by overwriting with random bytes before deletion.
     */
    public static void secureDelete(File file) {
        if (file == null || !file.exists()) return;
        try {
            long length = file.length();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] random = new byte[4096];
                SecureRandom sr = new SecureRandom();
                long written = 0;
                while (written < length) {
                    sr.nextBytes(random);
                    int toWrite = (int) Math.min(random.length, length - written);
                    fos.write(random, 0, toWrite);
                    written += toWrite;
                }
                fos.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "Secure overwrite failed, deleting anyway", e);
        }
        file.delete();
    }

    // ─── Utilities ───────────────────────────────────────────────

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    private static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8)  |  (bytes[3] & 0xFF);
    }
}
