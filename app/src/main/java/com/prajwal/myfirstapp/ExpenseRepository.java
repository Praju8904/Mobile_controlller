package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExpenseRepository {
    private static final String PREFS_NAME = "expense_tracker_prefs";
    private static final String DATA_KEY = "expenses_data";
    private static final String BUDGET_KEY = "monthly_budget";
    private final Context context;

    public ExpenseRepository(Context context) {
        this.context = context;
    }

    public synchronized void save(ArrayList<Expense> expenses) {
        JSONArray array = new JSONArray();
        for (Expense e : expenses) array.put(e.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<Expense> loadAll() {
        ArrayList<Expense> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Expense e = Expense.fromJson(array.getJSONObject(i));
                if (e != null) list.add(e);
            }
        } catch (JSONException e) { e.printStackTrace(); }
        Collections.sort(list, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list;
    }

    public void addExpense(Expense expense) {
        ArrayList<Expense> all = loadAll();
        all.add(0, expense);
        save(all);
    }

    public void deleteExpense(String id) {
        ArrayList<Expense> all = loadAll();
        all.removeIf(e -> e.id.equals(id));
        save(all);
    }

    public void setMonthlyBudget(double budget) {
        getPrefs().edit().putFloat(BUDGET_KEY, (float) budget).apply();
    }

    public double getMonthlyBudget() {
        return getPrefs().getFloat(BUDGET_KEY, 0f);
    }

    // ── Query Helpers ────────────────────────────────────────

    public double getTodaySpend() {
        return getSpendForDay(System.currentTimeMillis());
    }

    public double getYesterdaySpend() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return getSpendForDay(cal.getTimeInMillis());
    }

    public double getSpendForDay(long timeInDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeInDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        long dayEnd = dayStart + 86400000L;

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= dayStart && e.timestamp < dayEnd) {
                total += e.amount;
            }
        }
        return total;
    }

    public double getWeekSpend() {
        return getSpendSinceNDaysAgo(7);
    }

    public double getLastWeekSpend() {
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long weekAgo = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long twoWeeksAgo = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= twoWeeksAgo && e.timestamp < weekAgo) {
                total += e.amount;
            }
        }
        return total;
    }

    public double getMonthSpend() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= monthStart) {
                total += e.amount;
            }
        }
        return total;
    }

    public double[] getLast7DaysSpend() {
        double[] days = new double[7];
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, -(6 - i));
            days[i] = getSpendForDay(dayCal.getTimeInMillis());
        }
        return days;
    }

    public Map<String, Double> getCategoryBreakdown() {
        Map<String, Double> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long monthStart = cal.getTimeInMillis();

        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= monthStart) {
                map.put(e.category, map.getOrDefault(e.category, 0.0) + e.amount);
            }
        }
        return map;
    }

    public double[] getMonthlySpendHistory(int months) {
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

            for (Expense e : loadAll()) {
                if (!e.isIncome && e.timestamp >= monthStart && e.timestamp < monthEnd) {
                    history[i] += e.amount;
                }
            }
        }
        return history;
    }

    public ArrayList<Expense> getFilteredExpenses(String filter) {
        ArrayList<Expense> all = loadAll();
        if ("All".equals(filter)) return all;

        Calendar cal = Calendar.getInstance();
        long cutoff;

        switch (filter) {
            case "Today":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cutoff = cal.getTimeInMillis();
                break;
            case "This Week":
                cal.add(Calendar.DAY_OF_YEAR, -7);
                cutoff = cal.getTimeInMillis();
                break;
            case "This Month":
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cutoff = cal.getTimeInMillis();
                break;
            default:
                // Filter by category name
                ArrayList<Expense> filtered = new ArrayList<>();
                for (Expense e : all) {
                    if (e.category.equals(filter)) filtered.add(e);
                }
                return filtered;
        }

        ArrayList<Expense> filtered = new ArrayList<>();
        for (Expense e : all) {
            if (e.timestamp >= cutoff) filtered.add(e);
        }
        return filtered;
    }

    public Expense getBiggestThisWeek() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long weekAgo = cal.getTimeInMillis();

        Expense biggest = null;
        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= weekAgo) {
                if (biggest == null || e.amount > biggest.amount) biggest = e;
            }
        }
        return biggest;
    }

    public String getTopCategoryThisMonth() {
        Map<String, Double> breakdown = getCategoryBreakdown();
        String top = null;
        double max = 0;
        for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                top = entry.getKey();
            }
        }
        return top;
    }

    private double getSpendSinceNDaysAgo(int n) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -n);
        long cutoff = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && e.timestamp >= cutoff) total += e.amount;
        }
        return total;
    }

    // ── Wallet-Aware Queries ─────────────────────────────────

    /**
     * Load expenses for a specific wallet only.
     */
    public ArrayList<Expense> loadForWallet(String walletId) {
        ArrayList<Expense> result = new ArrayList<>();
        for (Expense e : loadAll()) {
            if (walletId.equals(e.walletId)) result.add(e);
        }
        return result;
    }

    public double getTodaySpendForWallet(String walletId) {
        return getSpendForDayForWallet(System.currentTimeMillis(), walletId);
    }

    public double getSpendForDayForWallet(long timeInDay, String walletId) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeInDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        long dayEnd = dayStart + 86400000L;

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && walletId.equals(e.walletId)
                && e.timestamp >= dayStart && e.timestamp < dayEnd) {
                total += e.amount;
            }
        }
        return total;
    }

    public double getWeekSpendForWallet(String walletId) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long cutoff = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && walletId.equals(e.walletId) && e.timestamp >= cutoff) {
                total += e.amount;
            }
        }
        return total;
    }

    public double getMonthSpendForWallet(String walletId) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        double total = 0;
        for (Expense e : loadAll()) {
            if (!e.isIncome && walletId.equals(e.walletId) && e.timestamp >= monthStart) {
                total += e.amount;
            }
        }
        return total;
    }

    public double[] getLast7DaysSpendForWallet(String walletId) {
        double[] days = new double[7];
        Calendar cal = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = (Calendar) cal.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, -(6 - i));
            days[i] = getSpendForDayForWallet(dayCal.getTimeInMillis(), walletId);
        }
        return days;
    }

    public Map<String, Double> getCategoryBreakdownForWallet(String walletId) {
        Map<String, Double> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long monthStart = cal.getTimeInMillis();

        for (Expense e : loadAll()) {
            if (!e.isIncome && walletId.equals(e.walletId) && e.timestamp >= monthStart) {
                map.put(e.category, map.getOrDefault(e.category, 0.0) + e.amount);
            }
        }
        return map;
    }

    public double[] getMonthlySpendHistoryForWallet(String walletId, int months) {
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

            for (Expense e : loadAll()) {
                if (!e.isIncome && walletId.equals(e.walletId)
                    && e.timestamp >= monthStart && e.timestamp < monthEnd) {
                    history[i] += e.amount;
                }
            }
        }
        return history;
    }

    /**
     * Delete expense and reverse the balance on its wallet.
     */
    public void deleteExpenseWithBalanceReverse(String id, WalletRepository walletRepo) {
        ArrayList<Expense> all = loadAll();
        for (Expense e : all) {
            if (e.id.equals(id)) {
                walletRepo.reverseBalanceAdjustment(e.walletId, e.amount, e.isIncome);
                break;
            }
        }
        all.removeIf(e -> e.id.equals(id));
        save(all);
    }

    /**
     * Add expense and update wallet balance.
     */
    public void addExpenseWithBalance(Expense expense, WalletRepository walletRepo) {
        addExpense(expense);
        walletRepo.adjustBalance(expense.walletId, expense.amount, expense.isIncome);
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
