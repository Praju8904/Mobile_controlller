package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for managing category budget goals.
 * Handles CRUD operations, budget period management,
 * spending tracking, and history archival.
 */
public class CategoryBudgetRepository {

    private static final String PREFS_NAME = "category_budget_prefs";
    private static final String DATA_KEY = "category_budgets_data";
    private static final String HISTORY_KEY = "budget_history_data";
    private final Context context;

    public CategoryBudgetRepository(Context context) {
        this.context = context;
    }

    // ─── Budget CRUD ─────────────────────────────────────────

    public synchronized void saveAll(ArrayList<CategoryBudget> items) {
        JSONArray array = new JSONArray();
        for (CategoryBudget cb : items) array.put(cb.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<CategoryBudget> loadAll() {
        ArrayList<CategoryBudget> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                CategoryBudget cb = CategoryBudget.fromJson(array.getJSONObject(i));
                if (cb != null) list.add(cb);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addBudget(CategoryBudget budget) {
        // Remove existing budget for same category if exists
        ArrayList<CategoryBudget> all = loadAll();
        all.removeIf(cb -> cb.categoryId.equals(budget.categoryId));
        all.add(0, budget);
        saveAll(all);
    }

    public void updateBudget(CategoryBudget updated) {
        ArrayList<CategoryBudget> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                updated.updatedAt = System.currentTimeMillis();
                all.set(i, updated);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteBudget(String id) {
        ArrayList<CategoryBudget> all = loadAll();
        all.removeIf(cb -> cb.id.equals(id));
        saveAll(all);
    }

    public CategoryBudget getById(String id) {
        for (CategoryBudget cb : loadAll()) {
            if (cb.id.equals(id)) return cb;
        }
        return null;
    }

    public CategoryBudget getByCategory(String categoryId) {
        for (CategoryBudget cb : loadAll()) {
            if (cb.categoryId.equals(categoryId) && cb.isActive) return cb;
        }
        return null;
    }

    // ─── Budget + Spending Queries ───────────────────────────

    /**
     * Get the actual spending for a category within a budget period.
     */
    public double getCategorySpending(String categoryId, long startDate, long endDate,
                                       ExpenseRepository expenseRepo) {
        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (!e.isIncome && e.category.equals(categoryId)
                && e.timestamp >= startDate && e.timestamp < endDate) {
                total += e.amount;
            }
        }
        return total;
    }

    /**
     * Get spending map for all budgeted categories: categoryId -> spent amount.
     */
    public Map<String, Double> getAllCategorySpending(ExpenseRepository expenseRepo) {
        Map<String, Double> spending = new HashMap<>();
        for (CategoryBudget cb : getActiveBudgets()) {
            double spent = getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
            spending.put(cb.categoryId, spent);
        }
        return spending;
    }

    public ArrayList<CategoryBudget> getActiveBudgets() {
        ArrayList<CategoryBudget> result = new ArrayList<>();
        for (CategoryBudget cb : loadAll()) {
            if (cb.isActive) result.add(cb);
        }
        return result;
    }

    /**
     * Calculate overall budget health score as a weighted average.
     * Returns 0-100 where 100 is perfect (all on track).
     */
    public float calculateHealthScore(ExpenseRepository expenseRepo) {
        ArrayList<CategoryBudget> active = getActiveBudgets();
        if (active.isEmpty()) return 100f;

        double totalBudgeted = 0;
        double totalSpent = 0;

        for (CategoryBudget cb : active) {
            double spent = getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
            totalBudgeted += cb.budgetAmount;
            totalSpent += Math.min(spent, cb.budgetAmount * 1.5); // Cap at 150% for scoring
        }

        if (totalBudgeted == 0) return 100f;
        float ratio = (float) (totalSpent / totalBudgeted);
        return Math.max(0f, Math.min(100f, (1f - ratio) * 100f + 50f));
    }

    /**
     * Get the health label for overall budget status.
     */
    public String getHealthLabel(ExpenseRepository expenseRepo) {
        float score = calculateHealthScore(expenseRepo);
        if (score >= 70f) return "On Track";
        if (score >= 40f) return "Nearing Limits";
        return "Over Budget";
    }

    /**
     * Get the health color for overall budget status.
     */
    public int getHealthColor(ExpenseRepository expenseRepo) {
        float score = calculateHealthScore(expenseRepo);
        if (score >= 70f) return 0xFF22C55E;    // Green
        if (score >= 40f) return 0xFFF59E0B;    // Amber
        return 0xFFEF4444;                       // Red
    }

    /**
     * Get total budgeted amount across all active budgets.
     */
    public double getTotalBudgeted() {
        double total = 0;
        for (CategoryBudget cb : getActiveBudgets()) {
            total += cb.budgetAmount;
        }
        return total;
    }

    /**
     * Get total spent across all active budgets.
     */
    public double getTotalSpent(ExpenseRepository expenseRepo) {
        double total = 0;
        for (CategoryBudget cb : getActiveBudgets()) {
            total += getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
        }
        return total;
    }

    // ─── Period Management ───────────────────────────────────

    /**
     * Check for expired budget periods and archive them to history.
     */
    public void processExpiredPeriods(ExpenseRepository expenseRepo) {
        ArrayList<CategoryBudget> all = loadAll();
        boolean changed = false;

        for (CategoryBudget cb : all) {
            if (!cb.isActive) continue;
            if (cb.isPeriodExpired()) {
                // Archive to history
                double spent = getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
                addHistory(new BudgetHistory(cb, spent));

                // Advance to next period
                cb.advanceToNextPeriod();
                changed = true;
            }
        }

        if (changed) {
            saveAll(all);
        }
    }

    // ─── Budget Alerts ───────────────────────────────────────

    /**
     * Check all budgets for threshold alerts.
     * Returns list of budgets that need alerts.
     */
    public ArrayList<CategoryBudget> checkBudgetAlerts(ExpenseRepository expenseRepo) {
        ArrayList<CategoryBudget> alerts = new ArrayList<>();
        ArrayList<CategoryBudget> all = loadAll();
        boolean changed = false;

        for (CategoryBudget cb : all) {
            if (!cb.isActive) continue;
            double spent = getCategorySpending(cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
            float percent = cb.getPercentUsed(spent);

            // Check threshold alert
            if (percent >= cb.alertThresholdPercent && !cb.thresholdAlertFired) {
                cb.thresholdAlertFired = true;
                changed = true;
                alerts.add(cb);
            }

            // Check exceeded alert
            if (percent >= 100f && !cb.exceededAlertFired) {
                cb.exceededAlertFired = true;
                changed = true;
                // Mark as a different kind of alert with a flag
                CategoryBudget exceededCopy = new CategoryBudget();
                exceededCopy.id = cb.id + "_exceeded";
                exceededCopy.categoryId = cb.categoryId;
                exceededCopy.budgetAmount = cb.budgetAmount;
                exceededCopy.currency = cb.currency;
                alerts.add(exceededCopy);
            }
        }

        if (changed) {
            saveAll(all);
        }
        return alerts;
    }

    // ─── History ─────────────────────────────────────────────

    public synchronized void saveHistory(ArrayList<BudgetHistory> items) {
        JSONArray array = new JSONArray();
        for (BudgetHistory bh : items) array.put(bh.toJson());
        getPrefs().edit().putString(HISTORY_KEY, array.toString()).apply();
    }

    public synchronized ArrayList<BudgetHistory> loadHistory() {
        ArrayList<BudgetHistory> list = new ArrayList<>();
        String json = getPrefs().getString(HISTORY_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                BudgetHistory bh = BudgetHistory.fromJson(array.getJSONObject(i));
                if (bh != null) list.add(bh);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // Sort by end date descending (most recent first)
        list.sort((a, b) -> Long.compare(b.endDate, a.endDate));
        return list;
    }

    public void addHistory(BudgetHistory history) {
        ArrayList<BudgetHistory> all = loadHistory();
        all.add(0, history);
        // Keep max 100 history records
        while (all.size() > 100) all.remove(all.size() - 1);
        saveHistory(all);
    }

    /**
     * Get budget history grouped by period end date.
     * Key is a formatted string like "Jan 2026", value is list of records for that period.
     */
    public Map<String, ArrayList<BudgetHistory>> getGroupedHistory() {
        Map<String, ArrayList<BudgetHistory>> grouped = new java.util.LinkedHashMap<>();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault());

        for (BudgetHistory bh : loadHistory()) {
            String key = fmt.format(new java.util.Date(bh.endDate));
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<>());
            }
            grouped.get(key).add(bh);
        }
        return grouped;
    }

    /**
     * Get categories that don't have a budget set.
     */
    public ArrayList<String> getCategoriesWithoutBudget() {
        ArrayList<String> result = new ArrayList<>();
        ArrayList<CategoryBudget> budgets = getActiveBudgets();

        for (String category : Expense.CATEGORIES) {
            if ("Salary".equals(category) || "Other".equals(category)) continue; // Skip income categories
            boolean hasBudget = false;
            for (CategoryBudget cb : budgets) {
                if (cb.categoryId.equals(category)) {
                    hasBudget = true;
                    break;
                }
            }
            if (!hasBudget) result.add(category);
        }
        return result;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
