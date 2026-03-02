package com.prajwal.myfirstapp.notes;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE CONTEXT TRACKER — Automatically records contextual metadata when a note is
 *  created or edited: time of day, day of week, and optionally city-level location.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class NoteContextTracker {

    private static final String TAG = "NoteContextTracker";

    // ═══ Time-of-day buckets ═══
    public static final String TIME_MORNING   = "morning";   // 05:00–11:59
    public static final String TIME_AFTERNOON  = "afternoon"; // 12:00–16:59
    public static final String TIME_EVENING    = "evening";   // 17:00–20:59
    public static final String TIME_NIGHT      = "night";     // 21:00–04:59

    // ═══ Emoji for each period ═══
    public static final String EMOJI_MORNING   = "🌅";
    public static final String EMOJI_AFTERNOON = "☀️";
    public static final String EMOJI_EVENING   = "🌇";
    public static final String EMOJI_NIGHT     = "🌙";

    // ═══════════════════════════════════════════════════════════════════════════════
    //  NOTE CONTEXT DATA
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class NoteContext {
        public String timeOfDay;     // morning / afternoon / evening / night
        public String dayOfWeek;     // Monday … Sunday
        public String city;          // optional, null if unavailable
        public String weatherCondition; // optional, null if unavailable
        public String weatherTemp;     // optional, e.g. "22°C"
        public long   timestamp;

        public NoteContext() {
            this.timestamp = System.currentTimeMillis();
        }

        /** Human-readable label: "Created Saturday morning" */
        public String getLabel() {
            String emoji = getTimeEmoji(timeOfDay);
            return emoji + " Created " + dayOfWeek + " " + timeOfDay
                    + (city != null ? " in " + city : "");
        }

        public JSONObject toJson() {
            JSONObject j = new JSONObject();
            try {
                j.put("timeOfDay", timeOfDay);
                j.put("dayOfWeek", dayOfWeek);
                if (city != null) j.put("city", city);
                if (weatherCondition != null) j.put("weatherCondition", weatherCondition);
                if (weatherTemp != null) j.put("weatherTemp", weatherTemp);
                j.put("timestamp", timestamp);
            } catch (JSONException e) {
                Log.e(TAG, "toJson error", e);
            }
            return j;
        }

        public static NoteContext fromJson(JSONObject j) {
            if (j == null) return null;
            NoteContext ctx = new NoteContext();
            ctx.timeOfDay  = j.optString("timeOfDay", TIME_MORNING);
            ctx.dayOfWeek  = j.optString("dayOfWeek", "");
            ctx.city       = j.has("city") ? j.optString("city") : null;
            ctx.weatherCondition = j.has("weatherCondition") ? j.optString("weatherCondition") : null;
            ctx.weatherTemp = j.has("weatherTemp") ? j.optString("weatherTemp") : null;
            ctx.timestamp  = j.optLong("timestamp", System.currentTimeMillis());
            return ctx;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Captures the current context (time, day, optional city).
     */
    public static NoteContext captureContext(Context context) {
        NoteContext ctx = new NoteContext();
        Calendar now = Calendar.getInstance();

        // ── Time of day ──
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour >= 5  && hour < 12) ctx.timeOfDay = TIME_MORNING;
        else if (hour >= 12 && hour < 17) ctx.timeOfDay = TIME_AFTERNOON;
        else if (hour >= 17 && hour < 21) ctx.timeOfDay = TIME_EVENING;
        else ctx.timeOfDay = TIME_NIGHT;

        // ── Day of week ──
        ctx.dayOfWeek = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US);

        // ── City (opt-in, needs permission) ──
        ctx.city = getCityName(context);

        return ctx;
    }

    /**
     * Returns the emoji for the given time-of-day label.
     */
    public static String getTimeEmoji(String timeOfDay) {
        if (timeOfDay == null) return EMOJI_MORNING;
        switch (timeOfDay) {
            case TIME_AFTERNOON: return EMOJI_AFTERNOON;
            case TIME_EVENING:   return EMOJI_EVENING;
            case TIME_NIGHT:     return EMOJI_NIGHT;
            default:             return EMOJI_MORNING;
        }
    }

    /**
     * Merges a NoteContext into an existing propertiesJson string.
     * Returns the updated JSON string.
     */
    public static String mergeIntoProperties(String existingPropertiesJson, NoteContext ctx) {
        try {
            JSONObject props = (existingPropertiesJson != null && !existingPropertiesJson.isEmpty())
                    ? new JSONObject(existingPropertiesJson)
                    : new JSONObject();
            props.put("context", ctx.toJson());
            return props.toString();
        } catch (JSONException e) {
            Log.e(TAG, "mergeIntoProperties error", e);
            return existingPropertiesJson;
        }
    }

    /**
     * Extracts a NoteContext from a propertiesJson string.
     */
    public static NoteContext extractFromProperties(String propertiesJson) {
        if (propertiesJson == null || propertiesJson.isEmpty()) return null;
        try {
            JSONObject props = new JSONObject(propertiesJson);
            JSONObject ctxJson = props.optJSONObject("context");
            return ctxJson != null ? NoteContext.fromJson(ctxJson) : null;
        } catch (JSONException e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to get the city name from the last known location.
     * Returns null if permission not granted or location unavailable.
     */
    private static String getCityName(Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }

            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;

            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) return null;

            Geocoder geocoder = new Geocoder(context, Locale.US);
            List<Address> addrs = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            if (addrs != null && !addrs.isEmpty()) {
                return addrs.get(0).getLocality(); // city name
            }
        } catch (Exception e) {
            Log.w(TAG, "getCityName failed", e);
        }
        return null;
    }
}
