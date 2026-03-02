package com.prajwal.myfirstapp.notes;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  WEATHER CONTEXT PROVIDER — Attaches weather data (temp, condition) to notes.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Uses Open-Meteo (free, no API key) for weather data.
 *  Falls back gracefully to "unknown" if no location/network.
 *  Caches last known weather for 30 minutes.
 */
public class WeatherContextProvider {

    private static final long CACHE_DURATION_MS = 30 * 60 * 1000; // 30 min

    // ═══════════════════════════════════════════════════════════════════════════════
    //  DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class WeatherInfo {
        public double temperature;     // Celsius
        public String condition;       // "Sunny", "Cloudy", "Rainy", etc.
        public String emoji;           // ☀️, ☁️, 🌧️, etc.
        public String city;
        public long timestamp;

        public String getSummary() {
            return emoji + " " + String.format(Locale.US, "%.0f°C", temperature) + ", " + condition;
        }

        public String getShortSummary() {
            return emoji + " " + String.format(Locale.US, "%.0f°", temperature);
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("temperature", temperature);
                json.put("condition", condition);
                json.put("emoji", emoji);
                json.put("city", city != null ? city : "");
                json.put("timestamp", timestamp);
            } catch (JSONException e) { e.printStackTrace(); }
            return json;
        }

        public static WeatherInfo fromJson(JSONObject json) {
            WeatherInfo w = new WeatherInfo();
            w.temperature = json.optDouble("temperature", 0);
            w.condition = json.optString("condition", "Unknown");
            w.emoji = json.optString("emoji", "🌡️");
            w.city = json.optString("city", "");
            w.timestamp = json.optLong("timestamp", 0);
            return w;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CACHE
    // ═══════════════════════════════════════════════════════════════════════════════

    private static WeatherInfo cachedWeather = null;

    /**
     * Get current weather, using cache if fresh enough.
     * This should be called from a background thread.
     */
    public static WeatherInfo getWeather(Context context) {
        // Check cache
        if (cachedWeather != null &&
                System.currentTimeMillis() - cachedWeather.timestamp < CACHE_DURATION_MS) {
            return cachedWeather;
        }

        // Try to get location
        double[] latLon = getLocation(context);
        if (latLon == null) {
            return createFallback("Unknown");
        }

        // Get city name
        String city = getCityName(context, latLon[0], latLon[1]);

        // Fetch weather from Open-Meteo
        WeatherInfo weather = fetchOpenMeteo(latLon[0], latLon[1]);
        if (weather != null) {
            weather.city = city;
            weather.timestamp = System.currentTimeMillis();
            cachedWeather = weather;
            return weather;
        }

        return createFallback(city);
    }

    /**
     * Get cached weather synchronously (no network). Returns null if no cache.
     */
    public static WeatherInfo getCachedWeather() {
        return cachedWeather;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  OPEN-METEO API (FREE, NO KEY)
    // ═══════════════════════════════════════════════════════════════════════════════

    private static WeatherInfo fetchOpenMeteo(double lat, double lon) {
        try {
            String urlStr = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                            "&current=temperature_2m,weather_code&timezone=auto",
                    lat, lon);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return null;

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            JSONObject current = json.optJSONObject("current");
            if (current == null) return null;

            WeatherInfo info = new WeatherInfo();
            info.temperature = current.optDouble("temperature_2m", 0);
            int code = current.optInt("weather_code", 0);
            info.condition = weatherCodeToCondition(code);
            info.emoji = weatherCodeToEmoji(code);

            return info;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WMO WEATHER CODE MAPPING
    // ═══════════════════════════════════════════════════════════════════════════════

    private static String weatherCodeToCondition(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Foggy";
        if (code <= 57) return "Drizzle";
        if (code <= 65) return "Rainy";
        if (code <= 67) return "Freezing Rain";
        if (code <= 77) return "Snowy";
        if (code <= 82) return "Rain Showers";
        if (code <= 86) return "Snow Showers";
        if (code == 95) return "Thunderstorm";
        if (code <= 99) return "Thunderstorm with Hail";
        return "Unknown";
    }

    private static String weatherCodeToEmoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 2) return "⛅";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 57) return "🌦️";
        if (code <= 65) return "🌧️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "🌨️";
        if (code <= 82) return "🌧️";
        if (code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LOCATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static double[] getLocation(Context context) {
        try {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;

            Location loc = null;
            // Try GPS first, then Network
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc == null) return null;

            return new double[]{loc.getLatitude(), loc.getLongitude()};
        } catch (SecurityException e) {
            return null;
        }
    }

    private static String getCityName(Context context, double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                return addr.getLocality() != null ? addr.getLocality() :
                        addr.getSubAdminArea() != null ? addr.getSubAdminArea() : "Unknown";
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "Unknown";
    }

    private static WeatherInfo createFallback(String city) {
        WeatherInfo info = new WeatherInfo();
        info.temperature = 0;
        info.condition = "Unknown";
        info.emoji = "🌡️";
        info.city = city;
        info.timestamp = System.currentTimeMillis();
        return info;
    }
}
