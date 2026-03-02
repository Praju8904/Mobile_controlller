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
 * Repository for managing recurring expenses / subscriptions.
 * Handles CRUD operations, auto-logging of due expenses,
 * and computation of summary metrics.
 */
public class RecurringExpenseRepository {

    private static final String PREFS_NAME = "recurring_expense_prefs";
    private static final String DATA_KEY = "recurring_expenses_data";
    private final Context context;

    public RecurringExpenseRepository(Context context) {
        this.context = context;
    }

    // ─── CRUD Operations ─────────────────────────────────────

    public synchronized void saveAll(ArrayList<RecurringExpense> items) {
        JSONArray array = new JSONArray();
        for (RecurringExpense re : items) array.put(re.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<RecurringExpense> loadAll() {
        ArrayList<RecurringExpense> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                RecurringExpense re = RecurringExpense.fromJson(array.getJSONObject(i));
                if (re != null) list.add(re);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addRecurringExpense(RecurringExpense re) {
        ArrayList<RecurringExpense> all = loadAll();
        all.add(0, re);
        saveAll(all);
    }

    public void updateRecurringExpense(RecurringExpense updated) {
        ArrayList<RecurringExpense> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteRecurringExpense(String id) {
        ArrayList<RecurringExpense> all = loadAll();
        all.removeIf(re -> re.id.equals(id));
        saveAll(all);
    }

    public RecurringExpense getById(String id) {
        for (RecurringExpense re : loadAll()) {
            if (re.id.equals(id)) return re;
        }
        return null;
    }

    public void toggleActive(String id) {
        ArrayList<RecurringExpense> all = loadAll();
        for (RecurringExpense re : all) {
            if (re.id.equals(id)) {
                re.isActive = !re.isActive;
                re.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        saveAll(all);
    }

    public RecurringExpense duplicate(String id) {
        RecurringExpense original = getById(id);
        if (original == null) return null;

        RecurringExpense copy = new RecurringExpense();
        copy.name = original.name + " (Copy)";
        copy.description = original.description;
        copy.amount = original.amount;
        copy.currency = original.currency;
        copy.categoryId = original.categoryId;
        copy.walletOrPaymentMethod = original.walletOrPaymentMethod;
        copy.walletId = original.walletId;
        copy.recurrenceType = original.recurrenceType;
        copy.recurrenceInterval = original.recurrenceInterval;
        copy.startDate = System.currentTimeMillis();
        copy.nextDueDate = original.calculateNextDueDate();
        copy.endDate = original.endDate;
        copy.reminderDaysBefore = original.reminderDaysBefore;
        copy.isActive = true;
        copy.color = original.color;
        copy.logoOrIcon = original.logoOrIcon;
        copy.notes = original.notes;

        addRecurringExpense(copy);
        return copy;
    }

    // ─── Query Helpers ───────────────────────────────────────

    public ArrayList<RecurringExpense> getActive() {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        for (RecurringExpense re : loadAll()) {
            if (re.isActive) result.add(re);
        }
        return result;
    }

    public ArrayList<RecurringExpense> getPaused() {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        for (RecurringExpense re : loadAll()) {
            if (!re.isActive) result.add(re);
        }
        return result;
    }

    public ArrayList<RecurringExpense> getUpcoming(int withinDays) {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        long cutoff = now + (long) withinDays * 24 * 60 * 60 * 1000L;
        for (RecurringExpense re : loadAll()) {
            if (re.isActive && re.nextDueDate >= now && re.nextDueDate <= cutoff) {
                result.add(re);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.nextDueDate, b.nextDueDate));
        return result;
    }

    public ArrayList<RecurringExpense> getOverdue() {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RecurringExpense re : loadAll()) {
            if (re.isActive && re.nextDueDate <= now) {
                result.add(re);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.nextDueDate, b.nextDueDate));
        return result;
    }

    /**
     * Group active subscriptions by recurrence type.
     * Returns map: "Monthly" -> list, "Yearly" -> list, etc.
     */
    public Map<String, ArrayList<RecurringExpense>> getGroupedByRecurrence() {
        Map<String, ArrayList<RecurringExpense>> grouped = new java.util.LinkedHashMap<>();
        grouped.put("Monthly", new ArrayList<>());
        grouped.put("Yearly", new ArrayList<>());
        grouped.put("Weekly", new ArrayList<>());
        grouped.put("Others", new ArrayList<>());

        for (RecurringExpense re : getActive()) {
            switch (re.recurrenceType) {
                case RecurringExpense.RECURRENCE_MONTHLY:
                    grouped.get("Monthly").add(re);
                    break;
                case RecurringExpense.RECURRENCE_YEARLY:
                    grouped.get("Yearly").add(re);
                    break;
                case RecurringExpense.RECURRENCE_WEEKLY:
                    grouped.get("Weekly").add(re);
                    break;
                default:
                    grouped.get("Others").add(re);
                    break;
            }
        }
        return grouped;
    }

    // ─── Summary Metrics ─────────────────────────────────────

    public double getTotalMonthlyRecurring() {
        double total = 0;
        for (RecurringExpense re : getActive()) {
            total += re.getMonthlyEquivalent();
        }
        return total;
    }

    public double getTotalYearlyRecurring() {
        return getTotalMonthlyRecurring() * 12;
    }

    public int getActiveCount() {
        return getActive().size();
    }

    public boolean hasUpcomingWithin7Days() {
        return !getUpcoming(7).isEmpty();
    }

    /**
     * Get category breakdown of recurring expenses (monthly equivalent).
     */
    public Map<String, Double> getCategoryBreakdown() {
        Map<String, Double> breakdown = new HashMap<>();
        for (RecurringExpense re : getActive()) {
            String cat = re.categoryId;
            breakdown.put(cat, breakdown.getOrDefault(cat, 0.0) + re.getMonthlyEquivalent());
        }
        return breakdown;
    }

    /**
     * Get monthly cost trend for the last N months.
     * Based on which subscriptions were active during each month.
     */
    public double[] getMonthlyCostTrend(int months) {
        double[] trend = new double[months];
        ArrayList<RecurringExpense> all = loadAll();
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < months; i++) {
            Calendar monthStart = (Calendar) cal.clone();
            monthStart.add(Calendar.MONTH, -i);
            monthStart.set(Calendar.DAY_OF_MONTH, 1);
            monthStart.set(Calendar.HOUR_OF_DAY, 0);
            monthStart.set(Calendar.MINUTE, 0);
            monthStart.set(Calendar.SECOND, 0);
            monthStart.set(Calendar.MILLISECOND, 0);

            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);

            for (RecurringExpense re : all) {
                // Check if subscription was active during this month
                if (re.startDate <= monthEnd.getTimeInMillis() &&
                    (re.endDate == 0 || re.endDate >= monthStart.getTimeInMillis())) {
                    trend[i] += re.getMonthlyEquivalent();
                }
            }
        }
        return trend;
    }

    // ─── Auto-logging ────────────────────────────────────────

    /**
     * Process all overdue recurring expenses — create regular expense entries
     * and advance due dates. Handles missed cycles individually.
     * Returns the number of expenses auto-logged.
     */
    public int processOverdueExpenses(ExpenseRepository expenseRepo) {
        ArrayList<RecurringExpense> all = loadAll();
        int logged = 0;
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (RecurringExpense re : all) {
            if (!re.isActive) continue;
            if (re.endDate > 0 && now > re.endDate) {
                re.isActive = false;
                changed = true;
                continue;
            }

            while (re.nextDueDate <= now) {
                // Create a regular expense for this due date — propagate walletId
                Expense expense = new Expense(
                    re.amount,
                    re.categoryId,
                    re.name + " (Auto - " + re.getRecurrenceLabel() + ")",
                    false,  // not income
                    re.walletId  // propagate wallet from recurring expense
                );
                expense.timestamp = re.nextDueDate;
                expenseRepo.addExpense(expense);
                logged++;

                // Advance to next due date
                re.nextDueDate = re.calculateNextDueDate();
                re.updatedAt = System.currentTimeMillis();
                changed = true;

                // Safety: don't loop forever if something's wrong
                if (logged > 100) break;
            }
        }

        if (changed) {
            saveAll(all);
        }
        return logged;
    }

    /**
     * Get expenses that are due for reminder (within reminderDaysBefore).
     */
    public ArrayList<RecurringExpense> getDueForReminder() {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (RecurringExpense re : getActive()) {
            if (re.reminderDaysBefore <= 0) continue;
            long reminderTime = re.nextDueDate - (long) re.reminderDaysBefore * 24 * 60 * 60 * 1000L;
            if (now >= reminderTime && now < re.nextDueDate) {
                result.add(re);
            }
        }
        return result;
    }

    // ─── Wallet-Aware Queries ────────────────────────────────

    /**
     * Process overdue expenses AND adjust wallet balances.
     */
    public int processOverdueExpensesWithBalance(ExpenseRepository expenseRepo,
                                                  WalletRepository walletRepo) {
        ArrayList<RecurringExpense> all = loadAll();
        int logged = 0;
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (RecurringExpense re : all) {
            if (!re.isActive) continue;
            if (re.endDate > 0 && now > re.endDate) {
                re.isActive = false;
                changed = true;
                continue;
            }

            while (re.nextDueDate <= now) {
                Expense expense = new Expense(
                    re.amount,
                    re.categoryId,
                    re.name + " (Auto - " + re.getRecurrenceLabel() + ")",
                    false,
                    re.walletId
                );
                expense.timestamp = re.nextDueDate;
                expenseRepo.addExpenseWithBalance(expense, walletRepo);
                logged++;

                re.nextDueDate = re.calculateNextDueDate();
                re.updatedAt = System.currentTimeMillis();
                changed = true;

                if (logged > 100) break;
            }
        }

        if (changed) saveAll(all);
        return logged;
    }

    /**
     * Get recurring expenses linked to a specific wallet.
     */
    public ArrayList<RecurringExpense> getForWallet(String walletId) {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        for (RecurringExpense re : loadAll()) {
            if (walletId.equals(re.walletId)) result.add(re);
        }
        return result;
    }

    /**
     * Get active recurring expenses linked to a specific wallet.
     */
    public ArrayList<RecurringExpense> getActiveForWallet(String walletId) {
        ArrayList<RecurringExpense> result = new ArrayList<>();
        for (RecurringExpense re : getActive()) {
            if (walletId.equals(re.walletId)) result.add(re);
        }
        return result;
    }

    /**
     * Get total monthly recurring cost for a specific wallet.
     */
    public double getTotalMonthlyRecurringForWallet(String walletId) {
        double total = 0;
        for (RecurringExpense re : getActiveForWallet(walletId)) {
            total += re.getMonthlyEquivalent();
        }
        return total;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
