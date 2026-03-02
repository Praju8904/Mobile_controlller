package com.prajwal.myfirstapp.expenses;

import org.json.JSONObject;

/**
 * A point-in-time snapshot of the user's net worth.
 * Stored daily; snapshots older than 2 years are pruned automatically.
 */
public class NetWorthSnapshot {

    public String id;
    public double totalAssets;
    public double totalLiabilities;
    public double netWorth;
    public String walletBalancesJson;   // serialised map walletId → balance
    public double moneyOwedToUser;      // from Borrow & Lend (LENT outstanding)
    public double moneyUserOwes;        // from Borrow & Lend (BORROWED outstanding)
    public long   recordedAt;           // epoch millis

    public NetWorthSnapshot() {}

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("totalAssets", totalAssets);
            o.put("totalLiabilities", totalLiabilities);
            o.put("netWorth", netWorth);
            o.put("walletBalancesJson", walletBalancesJson != null ? walletBalancesJson : "{}");
            o.put("moneyOwedToUser", moneyOwedToUser);
            o.put("moneyUserOwes", moneyUserOwes);
            o.put("recordedAt", recordedAt);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }

    public static NetWorthSnapshot fromJson(JSONObject o) {
        if (o == null) return null;
        NetWorthSnapshot s = new NetWorthSnapshot();
        try {
            s.id                 = o.optString("id");
            s.totalAssets        = o.optDouble("totalAssets", 0);
            s.totalLiabilities   = o.optDouble("totalLiabilities", 0);
            s.netWorth           = o.optDouble("netWorth", 0);
            s.walletBalancesJson = o.optString("walletBalancesJson", "{}");
            s.moneyOwedToUser    = o.optDouble("moneyOwedToUser", 0);
            s.moneyUserOwes      = o.optDouble("moneyUserOwes", 0);
            s.recordedAt         = o.optLong("recordedAt", 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }
}
