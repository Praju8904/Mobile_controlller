package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists NetWorthSnapshot records (one per day).
 * Automatically prunes snapshots older than 2 years.
 */
public class NetWorthRepository {

    private static final String PREFS_NAME  = "net_worth_prefs";
    private static final String SNAPSHOTS_KEY = "net_worth_snapshots";
    private static final long   TWO_YEARS_MS  = 2L * 365 * 24 * 60 * 60 * 1000;

    private final Context context;

    public NetWorthRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── CRUD ────────────────────────────────────────────────

    public synchronized void saveAll(ArrayList<NetWorthSnapshot> list) {
        JSONArray array = new JSONArray();
        for (NetWorthSnapshot s : list) array.put(s.toJson());
        getPrefs().edit().putString(SNAPSHOTS_KEY, array.toString()).apply();
    }

    public ArrayList<NetWorthSnapshot> loadAll() {
        ArrayList<NetWorthSnapshot> list = new ArrayList<>();
        String json = getPrefs().getString(SNAPSHOTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                NetWorthSnapshot s = NetWorthSnapshot.fromJson(array.getJSONObject(i));
                if (s != null) list.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Newest first
        Collections.sort(list, (a, b) -> Long.compare(b.recordedAt, a.recordedAt));
        return list;
    }

    /**
     * Record today's snapshot (idempotent – at most one per calendar day).
     * Prunes any snapshot older than 2 years.
     */
    public void takeDailySnapshot(NetWorthCalculationService calc) {
        ArrayList<NetWorthSnapshot> all = loadAll();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Check if today's snapshot already exists
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (NetWorthSnapshot s : all) {
            String d = sdf.format(new Date(s.recordedAt));
            if (today.equals(d)) return; // Already have today's
        }

        // Build snapshot
        NetWorthSnapshot snap = new NetWorthSnapshot();
        snap.id               = UUID.randomUUID().toString();
        snap.totalAssets      = calc.getTotalAssets();
        snap.totalLiabilities = calc.getTotalLiabilities();
        snap.netWorth         = calc.getNetWorth();
        snap.moneyOwedToUser  = calc.getMoneyOwedToUser();
        snap.moneyUserOwes    = calc.getMoneyUserOwes();
        snap.walletBalancesJson = calc.getWalletBalancesJson();
        snap.recordedAt       = System.currentTimeMillis();

        all.add(0, snap);

        // Prune entries older than 2 years
        long cutoff = System.currentTimeMillis() - TWO_YEARS_MS;
        all.removeIf(s -> s.recordedAt < cutoff);

        saveAll(all);
    }

    // ─── Trend Data ──────────────────────────────────────────

    /**
     * Returns an array of net-worth values, one per day, for the last {@code days} days.
     * Missing days are filled with the most recent known value.
     */
    public double[] getDailyTrend(int days) {
        double[] trend = new double[days];
        ArrayList<NetWorthSnapshot> all = loadAll(); // newest first
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int i = 0; i < days; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -(days - 1 - i));
            String dayStr = sdf.format(cal.getTime());

            // Find snapshot for this day (exact match)
            Double val = null;
            for (NetWorthSnapshot s : all) {
                String d = sdf.format(new Date(s.recordedAt));
                if (dayStr.equals(d)) { val = s.netWorth; break; }
            }

            if (val == null) {
                // Forward-fill from earlier snapshots
                val = i > 0 ? trend[i - 1] : 0.0;
            }
            trend[i] = val;
        }
        return trend;
    }

    /** Net worth snapshot for exactly 1 month ago (closest available). */
    public Double getNetWorthLastMonth() {
        Calendar lastMonth = Calendar.getInstance();
        lastMonth.add(Calendar.MONTH, -1);
        long target = lastMonth.getTimeInMillis();

        NetWorthSnapshot closest = null;
        long minDiff = Long.MAX_VALUE;
        for (NetWorthSnapshot s : loadAll()) {
            long diff = Math.abs(s.recordedAt - target);
            if (diff < minDiff) { minDiff = diff; closest = s; }
        }

        // Only return if it's reasonably close (within 5 days)
        if (closest != null && minDiff < 5L * 24 * 60 * 60 * 1000) {
            return closest.netWorth;
        }
        return null;
    }
}
