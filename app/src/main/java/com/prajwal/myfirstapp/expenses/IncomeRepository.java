package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for Income CRUD, period-based queries, breakdowns, and savings calculations.
 */
public class IncomeRepository {

    private static final String PREFS_NAME = "income_tracker_prefs";
    private static final String DATA_KEY = "income_data";

    private final Context context;

    public IncomeRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── CRUD ─────────────────────────────────────────────────

    public synchronized void saveAll(ArrayList<Income> incomes) {
        JSONArray array = new JSONArray();
        for (Income inc : incomes) array.put(inc.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<Income> loadAll() {
        ArrayList<Income> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Income inc = Income.fromJson(array.getJSONObject(i));
                if (inc != null) list.add(inc);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Collections.sort(list, (a, b) -> Long.compare(b.date, a.date));
        return list;
    }

    public void addIncome(Income income) {
        ArrayList<Income> all = loadAll();
        all.add(0, income);
        saveAll(all);
    }

    public void updateIncome(Income updated) {
        ArrayList<Income> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteIncome(String id) {
        ArrayList<Income> all = loadAll();
        all.removeIf(inc -> inc.id.equals(id));
        saveAll(all);
    }

    public Income getById(String id) {
        for (Income inc : loadAll()) {
            if (inc.id.equals(id)) return inc;
        }
        return null;
    }

    /**
     * Add income and update wallet balance.
     */
    public void addIncomeWithBalance(Income income, WalletRepository walletRepo) {
        addIncome(income);
        walletRepo.adjustBalance(income.walletId, income.amount, true);
    }

    /**
     * Delete income and reverse the balance on its wallet.
     */
    public void deleteIncomeWithBalanceReverse(String id, WalletRepository walletRepo) {
        Income income = getById(id);
        if (income != null) {
            walletRepo.reverseBalanceAdjustment(income.walletId, income.amount, true);
        }
        deleteIncome(id);
    }

    // ─── Period Queries ───────────────────────────────────────

    /**
     * Get start timestamp for a period label: Today, This Week, This Month, This Year.
     */
    private long getPeriodStart(String period) {
        Calendar cal = Calendar.getInstance();
        switch (period) {
            case "Today":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            case "This Week":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            case "This Year":
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            default: // "This Month"
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
        }
    }

    public double getTotalForPeriod(String period) {
        long start = getPeriodStart(period);
        double total = 0;
        for (Income inc : loadAll()) {
            if (inc.date >= start) total += inc.amount;
        }
        return total;
    }

    public double getTotalThisMonth() {
        return getTotalForPeriod("This Month");
    }

    public double getTotalToday() {
        return getTotalForPeriod("Today");
    }

    public double getTotalThisWeek() {
        return getTotalForPeriod("This Week");
    }

    public double getTotalThisYear() {
        return getTotalForPeriod("This Year");
    }

    public double getTotalForRange(long startMs, long endMs) {
        double total = 0;
        for (Income inc : loadAll()) {
            if (inc.date >= startMs && inc.date <= endMs) total += inc.amount;
        }
        return total;
    }

    // ─── Category Breakdown ───────────────────────────────────

    public Map<String, Double> getCategoryBreakdown(String period) {
        long start = getPeriodStart(period);
        Map<String, Double> map = new HashMap<>();
        for (Income inc : loadAll()) {
            if (inc.date >= start) {
                String cat = inc.categoryId != null ? inc.categoryId : "Others";
                map.put(cat, map.getOrDefault(cat, 0.0) + inc.amount);
            }
        }
        return map;
    }

    // ─── Source Breakdown ─────────────────────────────────────

    public Map<String, Double> getSourceBreakdown(String period) {
        long start = getPeriodStart(period);
        Map<String, Double> map = new HashMap<>();
        for (Income inc : loadAll()) {
            if (inc.date >= start) {
                String src = inc.source != null ? inc.source : Income.SOURCE_OTHER;
                map.put(src, map.getOrDefault(src, 0.0) + inc.amount);
            }
        }
        return map;
    }

    // ─── Wallet Breakdown ─────────────────────────────────────

    public Map<String, Double> getWalletBreakdown(String period) {
        long start = getPeriodStart(period);
        Map<String, Double> map = new HashMap<>();
        for (Income inc : loadAll()) {
            if (inc.date >= start) {
                String wId = inc.walletId != null ? inc.walletId : Wallet.DEFAULT_WALLET_ID;
                map.put(wId, map.getOrDefault(wId, 0.0) + inc.amount);
            }
        }
        return map;
    }

    // ─── Wallet-Specific Queries ──────────────────────────────

    public ArrayList<Income> loadForWallet(String walletId) {
        ArrayList<Income> result = new ArrayList<>();
        for (Income inc : loadAll()) {
            if (walletId.equals(inc.walletId)) result.add(inc);
        }
        return result;
    }

    public double getTotalThisMonthForWallet(String walletId) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();
        double total = 0;
        for (Income inc : loadAll()) {
            if (walletId.equals(inc.walletId) && inc.date >= monthStart) total += inc.amount;
        }
        return total;
    }

    // ─── Monthly History ──────────────────────────────────────

    public double[] getMonthlyIncomeHistory(int months) {
        double[] history = new double[months];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < months; i++) {
            Calendar monthCal = (Calendar) cal.clone();
            monthCal.add(Calendar.MONTH, -i);
            monthCal.set(Calendar.DAY_OF_MONTH, 1);
            monthCal.set(Calendar.HOUR_OF_DAY, 0);
            monthCal.set(Calendar.MINUTE, 0);
            monthCal.set(Calendar.SECOND, 0);
            monthCal.set(Calendar.MILLISECOND, 0);
            long monthStart = monthCal.getTimeInMillis();
            Calendar nextMonth = (Calendar) monthCal.clone();
            nextMonth.add(Calendar.MONTH, 1);
            long monthEnd = nextMonth.getTimeInMillis();
            for (Income inc : loadAll()) {
                if (inc.date >= monthStart && inc.date < monthEnd) {
                    history[i] += inc.amount;
                }
            }
        }
        return history;
    }

    // ─── Savings Calculation ──────────────────────────────────

    /**
     * Net savings = total income – total expenses for the given period.
     */
    public double getNetSavings(String period, ExpenseRepository expenseRepo) {
        double income = getTotalForPeriod(period);
        long start = getPeriodStart(period);
        double expenses = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (!e.isIncome && e.timestamp >= start) expenses += e.amount;
        }
        return income - expenses;
    }

    /**
     * Savings rate percentage = net savings / total income * 100.
     */
    public double getSavingsRate(String period, ExpenseRepository expenseRepo) {
        double income = getTotalForPeriod(period);
        if (income <= 0) return 0;
        double savings = getNetSavings(period, expenseRepo);
        return (savings / income) * 100.0;
    }

    // ─── Search ───────────────────────────────────────────────

    public ArrayList<Income> search(String query) {
        String q = query.toLowerCase().trim();
        ArrayList<Income> results = new ArrayList<>();
        for (Income inc : loadAll()) {
            if ((inc.title != null && inc.title.toLowerCase().contains(q))
                    || (inc.notes != null && inc.notes.toLowerCase().contains(q))
                    || (inc.source != null && inc.source.toLowerCase().contains(q))) {
                results.add(inc);
            }
        }
        return results;
    }

    // ─── Filtered List ────────────────────────────────────────

    public ArrayList<Income> getFiltered(String period) {
        if ("All".equals(period)) return loadAll();
        long start = getPeriodStart(period);
        ArrayList<Income> filtered = new ArrayList<>();
        for (Income inc : loadAll()) {
            if (inc.date >= start) filtered.add(inc);
        }
        return filtered;
    }
}
