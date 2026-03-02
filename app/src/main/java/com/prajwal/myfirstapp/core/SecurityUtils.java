package com.prajwal.myfirstapp.core;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Formatter;

public class SecurityUtils {

    // These can be updated at runtime via QR pairing
    // Defaults match the Python config.py fallback values
    public static String AES_KEY = "my_secret_16byte";
    public static String HMAC_KEY = "my_hmac_secret_key";
    public static boolean USE_ENCRYPTION = true;

    public static String encryptAES(String plainText) throws Exception {
        byte[] keyBytes = AES_KEY.getBytes("UTF-8");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        // Generate a random IV for every packet (Security Best Practice)
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));

        // Combine IV + Ciphertext
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    public static String calculateHMAC(String data) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(HMAC_KEY.getBytes("UTF-8"), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));

        Formatter formatter = new Formatter();
        for (byte b : rawHmac) { formatter.format("%02x", b); }
        return formatter.toString();
    }
}