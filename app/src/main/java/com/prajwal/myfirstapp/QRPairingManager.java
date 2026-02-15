package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;

/**
 * Handles QR code pairing data.
 * When the phone scans a QR code from the PC's control panel,
 * this class parses the JSON payload and saves connection settings.
 * 
 * QR Payload format:
 * {
 *   "ip": "192.168.1.100",
 *   "hostname": "MY-PC",
 *   "cmd_port": 5005,
 *   "file_port": 5006,
 *   "watchdog_port": 5007,
 *   "reverse_port": 6000,
 *   "aes_key": "...",
 *   "hmac_key": "...",
 *   "secret_key": "...",
 *   "encryption": true
 * }
 */
public class QRPairingManager {

    private static final String TAG = "QRPairing";
    private static final String PREFS_NAME = "pairing_prefs";

    // Keys for SharedPreferences
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_HOSTNAME = "hostname";
    private static final String KEY_CMD_PORT = "cmd_port";
    private static final String KEY_FILE_PORT = "file_port";
    private static final String KEY_WATCHDOG_PORT = "watchdog_port";
    private static final String KEY_REVERSE_PORT = "reverse_port";
    private static final String KEY_AES_KEY = "aes_key";
    private static final String KEY_HMAC_KEY = "hmac_key";
    private static final String KEY_SECRET_KEY = "secret_key";
    private static final String KEY_ENCRYPTION = "encryption";
    private static final String KEY_PAIRED = "is_paired";

    private final Context context;
    private final SharedPreferences prefs;

    public QRPairingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Parse QR code JSON and save all connection details.
     * @param qrJson The raw JSON string from the QR code
     * @return true if parsing succeeded
     */
    public boolean parsePairingData(String qrJson) {
        try {
            JSONObject json = new JSONObject(qrJson);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_SERVER_IP, json.getString("ip"));
            editor.putString(KEY_HOSTNAME, json.optString("hostname", "Unknown"));
            editor.putInt(KEY_CMD_PORT, json.optInt("cmd_port", 5005));
            editor.putInt(KEY_FILE_PORT, json.optInt("file_port", 5006));
            editor.putInt(KEY_WATCHDOG_PORT, json.optInt("watchdog_port", 5007));
            editor.putInt(KEY_REVERSE_PORT, json.optInt("reverse_port", 6000));
            editor.putString(KEY_AES_KEY, json.getString("aes_key"));
            editor.putString(KEY_HMAC_KEY, json.getString("hmac_key"));
            editor.putString(KEY_SECRET_KEY, json.optString("secret_key", ""));
            editor.putBoolean(KEY_ENCRYPTION, json.optBoolean("encryption", true));
            editor.putBoolean(KEY_PAIRED, true);
            editor.apply();

            Log.i(TAG, "Paired with: " + json.getString("ip") + " (" + json.optString("hostname") + ")");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "QR parse error: " + e.getMessage());
            return false;
        }
    }

    // ─── GETTERS ───────────────────────────────────────────────

    public boolean isPaired() {
        return prefs.getBoolean(KEY_PAIRED, false);
    }

    public String getServerIp() {
        return prefs.getString(KEY_SERVER_IP, "");
    }

    public String getHostname() {
        return prefs.getString(KEY_HOSTNAME, "Unknown");
    }

    public int getCommandPort() {
        return prefs.getInt(KEY_CMD_PORT, 5005);
    }

    public int getFilePort() {
        return prefs.getInt(KEY_FILE_PORT, 5006);
    }

    public int getWatchdogPort() {
        return prefs.getInt(KEY_WATCHDOG_PORT, 5007);
    }

    public int getReversePort() {
        return prefs.getInt(KEY_REVERSE_PORT, 6000);
    }

    public String getAesKey() {
        return prefs.getString(KEY_AES_KEY, SecurityUtils.AES_KEY);
    }

    public String getHmacKey() {
        return prefs.getString(KEY_HMAC_KEY, SecurityUtils.HMAC_KEY);
    }

    public String getSecretKey() {
        return prefs.getString(KEY_SECRET_KEY, "");
    }

    public boolean isEncryptionEnabled() {
        return prefs.getBoolean(KEY_ENCRYPTION, true);
    }

    /**
     * Apply paired keys to SecurityUtils (call after successful pairing).
     */
    public void applyKeys() {
        if (isPaired()) {
            SecurityUtils.AES_KEY = getAesKey();
            SecurityUtils.HMAC_KEY = getHmacKey();
            SecurityUtils.USE_ENCRYPTION = isEncryptionEnabled();
            Log.i(TAG, "Security keys applied from QR pairing");
        }
    }

    /**
     * Clear all pairing data (unpair).
     */
    public void clearPairing() {
        prefs.edit().clear().apply();
        Log.i(TAG, "Pairing data cleared");
    }

    /**
     * Get a summary string for display.
     */
    public String getSummary() {
        if (!isPaired()) return "Not paired";
        return getHostname() + " • " + getServerIp() + " (Encrypted: " + isEncryptionEnabled() + ")";
    }
}
