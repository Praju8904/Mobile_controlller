package com.prajwal.myfirstapp;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles all cryptographic operations for the Password Manager vault.
 * - Master password hashing (PBKDF2-HMAC-SHA256)
 * - AES-256-GCM encryption/decryption for vault data
 * - Password strength calculation
 * - Password generation
 * - HaveIBeenPwned breach check (k-anonymity model)
 */
public class VaultCryptoManager {

    private static final String TAG = "VaultCrypto";
    private static final int PBKDF2_ITERATIONS = 120000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // ─── Master Password Hashing ─────────────────────────────────

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public static byte[] hashMasterPassword(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            Log.e(TAG, "Hash failed", e);
            return null;
        }
    }

    public static boolean verifyMasterPassword(String password, byte[] salt, byte[] storedHash) {
        byte[] hash = hashMasterPassword(password, salt);
        if (hash == null || storedHash == null || hash.length != storedHash.length) return false;
        int diff = 0;
        for (int i = 0; i < hash.length; i++) diff |= hash[i] ^ storedHash[i];
        return diff == 0;
    }

    // ─── AES-256-GCM Encryption ──────────────────────────────────

    public static SecretKey deriveEncryptionKey(String masterPassword, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Key derivation failed", e);
            return null;
        }
    }

    /** Encrypts plaintext. Returns Base64(IV + ciphertext). */
    public static String encrypt(String plaintext, SecretKey key) {
        if (plaintext == null || key == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /** Decrypts Base64(IV + ciphertext) back to plaintext. */
    public static String decrypt(String encryptedBase64, SecretKey key) {
        if (encryptedBase64 == null || key == null) return null;
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(ciphertext);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    // ─── Password Strength ───────────────────────────────────────

    /** Returns strength score 0–100. */
    public static int calculateStrength(String password) {
        if (password == null || password.isEmpty()) return 0;
        int score = 0;
        int len = password.length();

        // Length scoring
        if (len >= 8) score += 15;
        if (len >= 12) score += 15;
        if (len >= 16) score += 10;
        if (len >= 20) score += 5;

        // Character variety
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }
        int variety = 0;
        if (hasLower) variety++;
        if (hasUpper) variety++;
        if (hasDigit) variety++;
        if (hasSymbol) variety++;
        score += variety * 10;

        // Penalty for common patterns
        String lower = password.toLowerCase();
        String[] commonPatterns = {"password", "123456", "qwerty", "abc123", "letmein",
                "admin", "welcome", "monkey", "master", "dragon"};
        for (String p : commonPatterns) {
            if (lower.contains(p)) { score -= 25; break; }
        }

        // Penalty for sequential/repeated chars
        int sequential = 0, repeated = 0;
        for (int i = 1; i < len; i++) {
            if (password.charAt(i) == password.charAt(i - 1)) repeated++;
            if (password.charAt(i) == password.charAt(i - 1) + 1) sequential++;
        }
        if (repeated > len / 3) score -= 15;
        if (sequential > len / 3) score -= 10;

        // Entropy bonus
        double entropy = Math.log(Math.pow(calculateCharsetSize(password), len)) / Math.log(2);
        if (entropy > 60) score += 10;
        if (entropy > 80) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    private static int calculateCharsetSize(String password) {
        int size = 0;
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c) && !lower) { size += 26; lower = true; }
            else if (Character.isUpperCase(c) && !upper) { size += 26; upper = true; }
            else if (Character.isDigit(c) && !digit) { size += 10; digit = true; }
            else if (!Character.isLetterOrDigit(c) && !symbol) { size += 33; symbol = true; }
        }
        return Math.max(size, 1);
    }

    public static String getStrengthLabel(int score) {
        if (score >= 80) return "Strong";
        if (score >= 50) return "Medium";
        return "Weak";
    }

    public static int getStrengthColor(int score) {
        if (score >= 80) return 0xFF10B981; // green
        if (score >= 50) return 0xFFF59E0B; // amber
        return 0xFFEF4444; // red
    }

    // ─── Password Generator ──────────────────────────────────────

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?/~`";

    public static String generatePassword(int length, boolean upper, boolean lower,
                                           boolean numbers, boolean symbols) {
        StringBuilder charset = new StringBuilder();
        List<Character> required = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        if (lower) { charset.append(LOWERCASE); required.add(LOWERCASE.charAt(random.nextInt(26))); }
        if (upper) { charset.append(UPPERCASE); required.add(UPPERCASE.charAt(random.nextInt(26))); }
        if (numbers) { charset.append(DIGITS); required.add(DIGITS.charAt(random.nextInt(10))); }
        if (symbols) { charset.append(SYMBOLS); required.add(SYMBOLS.charAt(random.nextInt(SYMBOLS.length()))); }

        if (charset.length() == 0) {
            charset.append(LOWERCASE).append(DIGITS);
            required.add(LOWERCASE.charAt(random.nextInt(26)));
        }

        length = Math.max(length, required.size());
        char[] result = new char[length];

        // Place required characters at random positions
        Set<Integer> usedPositions = new HashSet<>();
        for (char c : required) {
            int pos;
            do { pos = random.nextInt(length); } while (usedPositions.contains(pos));
            result[pos] = c;
            usedPositions.add(pos);
        }

        // Fill remaining
        String chars = charset.toString();
        for (int i = 0; i < length; i++) {
            if (!usedPositions.contains(i)) {
                result[i] = chars.charAt(random.nextInt(chars.length()));
            }
        }

        return new String(result);
    }

    // ─── Breach Check (HaveIBeenPwned k-anonymity) ──────────────

    /** SHA-1 hash a password and return hex string. */
    public static String sha1Hex(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Get the 5-char prefix for the HIBP API range query. */
    public static String getBreachCheckPrefix(String password) {
        String hash = sha1Hex(password);
        return hash != null ? hash.substring(0, 5) : null;
    }

    /** Get the suffix to search for in HIBP response. */
    public static String getBreachCheckSuffix(String password) {
        String hash = sha1Hex(password);
        return hash != null ? hash.substring(5) : null;
    }

    // ─── Utility ─────────────────────────────────────────────────

    public static String toBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }
}
