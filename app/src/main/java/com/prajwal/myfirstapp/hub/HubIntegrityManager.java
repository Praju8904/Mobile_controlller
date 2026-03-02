package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Formatter;

public class HubIntegrityManager {

    private static final String TAG = "HubIntegrityManager";
    private static final String PREFS = "hub_integrity";
    private static final String KEY = "integrity_json";

    public static void lockIntegrity(HubFile file, Context context) {
        if (file == null || file.id == null) return;
        String hash = computeHash(file.filePath);
        if (hash == null) hash = "";
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = loadArr(prefs);
            // Remove existing entry for this fileId
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!obj.optString("fileId", "").equals(file.id)) newArr.put(obj);
            }
            JSONObject entry = new JSONObject();
            entry.put("fileId", file.id);
            entry.put("hash", hash);
            entry.put("lockedAt", System.currentTimeMillis());
            newArr.put(entry);
            prefs.edit().putString(KEY, newArr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "lockIntegrity", e);
        }
    }

    /** Returns "VERIFIED", "MODIFIED", or "NOT_LOCKED" */
    public static String verifyIntegrity(HubFile file, Context context) {
        if (file == null || file.id == null) return "NOT_LOCKED";
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = loadArr(prefs);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.optString("fileId", "").equals(file.id)) {
                    String storedHash = obj.optString("hash", "");
                    String currentHash = computeHash(file.filePath);
                    if (currentHash == null) currentHash = "";
                    return storedHash.equals(currentHash) ? "VERIFIED" : "MODIFIED";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "verifyIntegrity", e);
        }
        return "NOT_LOCKED";
    }

    private static JSONArray loadArr(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static String computeHash(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        try (FileInputStream fis = new FileInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) digest.update(buf, 0, n);
            return toHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        try (Formatter formatter = new Formatter()) {
            for (byte b : bytes) formatter.format("%02x", b);
            return formatter.toString();
        }
    }
}
