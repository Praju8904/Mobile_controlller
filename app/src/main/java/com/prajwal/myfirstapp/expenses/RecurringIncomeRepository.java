package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * Repository for RecurringIncome CRUD and auto-logging.
 */
public class RecurringIncomeRepository {

    private static final String PREFS_NAME = "recurring_income_prefs";
    private static final String DATA_KEY = "recurring_income_data";

    private final Context context;

    public RecurringIncomeRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── CRUD ─────────────────────────────────────────────────

    public synchronized void saveAll(ArrayList<RecurringIncome> items) {
        JSONArray array = new JSONArray();
        for (RecurringIncome ri : items) array.put(ri.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<RecurringIncome> loadAll() {
        ArrayList<RecurringIncome> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                RecurringIncome ri = RecurringIncome.fromJson(array.getJSONObject(i));
                if (ri != null) list.add(ri);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void add(RecurringIncome ri) {
        ArrayList<RecurringIncome> all = loadAll();
        all.add(0, ri);
        saveAll(all);
    }

    public void update(RecurringIncome updated) {
        ArrayList<RecurringIncome> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
    }

    public void delete(String id) {
        ArrayList<RecurringIncome> all = loadAll();
        all.removeIf(ri -> ri.id.equals(id));
        saveAll(all);
    }

    public RecurringIncome getById(String id) {
        for (RecurringIncome ri : loadAll()) {
            if (ri.id.equals(id)) return ri;
        }
        return null;
    }

    public void toggleActive(String id) {
        ArrayList<RecurringIncome> all = loadAll();
        for (RecurringIncome ri : all) {
            if (ri.id.equals(id)) {
                ri.isActive = !ri.isActive;
                ri.updatedAt = System.currentTimeMillis();
                break;
            }
        }
        saveAll(all);
    }

    // ─── Queries ──────────────────────────────────────────────

    public ArrayList<RecurringIncome> getActive() {
        ArrayList<RecurringIncome> result = new ArrayList<>();
        for (RecurringIncome ri : loadAll()) {
            if (ri.isActive) result.add(ri);
        }
        return result;
    }

    public ArrayList<RecurringIncome> getUpcoming(int withinDays) {
        ArrayList<RecurringIncome> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        long cutoff = now + (long) withinDays * 24 * 60 * 60 * 1000L;
        for (RecurringIncome ri : loadAll()) {
            if (ri.isActive && ri.nextDueDate >= now && ri.nextDueDate <= cutoff) {
                result.add(ri);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.nextDueDate, b.nextDueDate));
        return result;
    }

    public double getTotalMonthlyRecurring() {
        double total = 0;
        for (RecurringIncome ri : getActive()) {
            total += ri.getMonthlyEquivalent();
        }
        return total;
    }

    // ─── Auto-logging ─────────────────────────────────────────

    /**
     * Process all overdue recurring incomes — create Income entries, update wallet balances,
     * and advance nextDueDates. Handles missed cycles individually.
     * Returns the number of incomes auto-logged.
     */
    public int processOverdueIncomes(IncomeRepository incomeRepo, WalletRepository walletRepo) {
        ArrayList<RecurringIncome> all = loadAll();
        int logged = 0;
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (RecurringIncome ri : all) {
            if (!ri.isActive) continue;
            if (ri.endDate > 0 && now > ri.endDate) {
                ri.isActive = false;
                changed = true;
                continue;
            }

            while (ri.nextDueDate <= now) {
                Income income = new Income(
                    ri.title + " (Auto - " + ri.getRecurrenceLabel() + ")",
                    ri.amount,
                    ri.categoryId,
                    ri.source,
                    ri.walletId
                );
                income.date = ri.nextDueDate;
                income.isRecurring = true;
                income.recurrenceId = ri.id;
                income.currency = ri.currency;
                income.notes = ri.notes;

                // Set time string
                income.time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(ri.nextDueDate));

                incomeRepo.addIncomeWithBalance(income, walletRepo);
                logged++;

                ri.nextDueDate = ri.calculateNextDueDate();
                ri.updatedAt = System.currentTimeMillis();
                changed = true;

                // Safety guard against infinite loops
                if (logged > 100) break;
            }
        }

        if (changed) saveAll(all);
        return logged;
    }

    /**
     * Get recurring incomes due for reminder (within reminderDaysBefore).
     */
    public ArrayList<RecurringIncome> getDueForReminder() {
        ArrayList<RecurringIncome> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RecurringIncome ri : getActive()) {
            if (ri.reminderDaysBefore <= 0) continue;
            long reminderTime = ri.nextDueDate - (long) ri.reminderDaysBefore * 24 * 60 * 60 * 1000L;
            if (now >= reminderTime && now < ri.nextDueDate) {
                result.add(ri);
            }
        }
        return result;
    }
}
