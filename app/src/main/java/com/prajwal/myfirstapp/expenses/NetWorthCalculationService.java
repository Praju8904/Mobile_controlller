package com.prajwal.myfirstapp.expenses;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates all net-worth metrics in real time from the various repositories.
 */
public class NetWorthCalculationService {

    private final Context context;
    private final WalletRepository         walletRepo;
    private final ExpenseRepository        expenseRepo;
    private final IncomeRepository         incomeRepo;
    private final MoneyRecordRepository    iouRepo;
    private final CategoryBudgetRepository budgetRepo;

    private static final double SAVINGS_MILESTONE_TARGET = 100_000;

    public NetWorthCalculationService(Context context) {
        this.context     = context;
        this.walletRepo  = new WalletRepository(context);
        this.expenseRepo = new ExpenseRepository(context);
        this.incomeRepo  = new IncomeRepository(context);
        this.iouRepo     = new MoneyRecordRepository(context);
        this.budgetRepo  = new CategoryBudgetRepository(context);
    }

    // ─── Assets ──────────────────────────────────────────────

    /**
     * Total assets = sum of wallet balances (includeInTotalBalance=true, not archived,
     * excluding credit-card wallets which represent debt) + money owed TO the user.
     */
    public double getTotalAssets() {
        double total = 0;
        for (Wallet w : walletRepo.loadAll()) {
            if (w.isArchived || !w.includeInTotalBalance) continue;
            if (!w.isCreditCard() && w.currentBalance > 0) {
                total += w.currentBalance;
            }
        }
        // Money the user lent out (owed TO the user) is an asset
        total += iouRepo.getTotalLentOutstanding();
        return total;
    }

    /**
     * Total liabilities = negative wallet balances + credit-card outstanding + money user owes.
     */
    public double getTotalLiabilities() {
        double total = 0;
        for (Wallet w : walletRepo.loadAll()) {
            if (w.isArchived) continue;
            if (w.isCreditCard() && w.currentBalance > 0) {
                total += w.currentBalance; // credit card debt
            } else if (!w.isCreditCard() && w.currentBalance < 0) {
                total += Math.abs(w.currentBalance); // negative balance
            }
        }
        // Money the user borrowed (owes) is a liability
        total += iouRepo.getTotalBorrowedOutstanding();
        return total;
    }

    /** Net Worth = Total Assets − Total Liabilities. */
    public double getNetWorth() {
        return getTotalAssets() - getTotalLiabilities();
    }

    /** Money owed to the user (outstanding lent records). */
    public double getMoneyOwedToUser() {
        return iouRepo.getTotalLentOutstanding();
    }

    /** Money the user owes (outstanding borrowed records). */
    public double getMoneyUserOwes() {
        return iouRepo.getTotalBorrowedOutstanding();
    }

    /**
     * Returns a JSON string mapping walletId → currentBalance for all active wallets.
     */
    public String getWalletBalancesJson() {
        JSONObject obj = new JSONObject();
        try {
            for (Wallet w : walletRepo.getActiveWallets()) {
                obj.put(w.id, w.currentBalance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj.toString();
    }

    // ─── Cash Flow ───────────────────────────────────────────

    /** Income this month (from IncomeRepository). */
    public double getIncomeThisMonth() {
        return incomeRepo.getTotalThisMonth();
    }

    /** Expenses this month (from ExpenseRepository). */
    public double getExpensesThisMonth() {
        return expenseRepo.getMonthSpend();
    }

    /** Net cash flow = income − expenses this month. */
    public double getNetCashFlowThisMonth() {
        return getIncomeThisMonth() - getExpensesThisMonth();
    }

    /**
     * Daily net cash flow for the current calendar month.
     * Returns an array indexed 0..daysInMonth-1 where each element is
     * (income − expenses) for that day.
     */
    public double[] getDailyCashFlowThisMonth() {
        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);

        double[] income   = new double[daysInMonth];
        double[] expenses = new double[daysInMonth];

        for (Income inc : incomeRepo.loadAll()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(inc.date);
            if (c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) == currentMonth) {
                int day = c.get(Calendar.DAY_OF_MONTH) - 1;
                income[day] += inc.amount;
            }
        }
        for (Expense exp : expenseRepo.loadAll()) {
            if (exp.isIncome) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(exp.timestamp);
            if (c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) == currentMonth) {
                int day = c.get(Calendar.DAY_OF_MONTH) - 1;
                expenses[day] += exp.amount;
            }
        }

        double[] net = new double[daysInMonth];
        for (int i = 0; i < daysInMonth; i++) {
            net[i] = income[i] - expenses[i];
        }
        return net;
    }

    // ─── Savings Rate ─────────────────────────────────────────

    /**
     * Savings rate = (income − expenses) / income * 100.
     * Returns 0 if income is zero.
     */
    public double getSavingsRateThisMonth() {
        double income = getIncomeThisMonth();
        if (income <= 0) return 0;
        double savings = income - getExpensesThisMonth();
        return Math.max(0, (savings / income) * 100.0);
    }

    // ─── Monthly History (6 months) ──────────────────────────

    /** Income per month for the last N months (index 0 = oldest). */
    public double[] getMonthlyIncome(int months) {
        return incomeRepo.getMonthlyIncomeHistory(months);
    }

    /** Expenses per month for the last N months (index 0 = oldest). */
    public double[] getMonthlyExpenses(int months) {
        double[] result = new double[months];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < months; i++) {
            Calendar mc = (Calendar) cal.clone();
            mc.add(Calendar.MONTH, -i);
            mc.set(Calendar.DAY_OF_MONTH, 1);
            mc.set(Calendar.HOUR_OF_DAY, 0);
            mc.set(Calendar.MINUTE, 0);
            mc.set(Calendar.SECOND, 0);
            mc.set(Calendar.MILLISECOND, 0);
            long start = mc.getTimeInMillis();
            Calendar next = (Calendar) mc.clone();
            next.add(Calendar.MONTH, 1);
            long end = next.getTimeInMillis();

            for (Expense e : expenseRepo.loadAll()) {
                if (!e.isIncome && e.timestamp >= start && e.timestamp < end) {
                    result[i] += e.amount;
                }
            }
        }
        // Reverse so index 0 = oldest
        double[] reversed = new double[months];
        for (int i = 0; i < months; i++) reversed[i] = result[months - 1 - i];
        return reversed;
    }

    // ─── Top Categories ───────────────────────────────────────

    /**
     * Top N spending categories this month.
     * Returns a list of double[2]: {amount, percentage of total expenses}
     */
    public List<double[]> getTopCategoryAmounts(int n) {
        Map<String, Double> breakdown = expenseRepo.getCategoryBreakdown();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(breakdown.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<double[]> result = new ArrayList<>();
        double total = getExpensesThisMonth();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            double amt = entries.get(i).getValue();
            double pct = total > 0 ? (amt / total) * 100.0 : 0;
            result.add(new double[]{amt, pct});
        }
        return result;
    }

    public List<String> getTopCategoryNames(int n) {
        Map<String, Double> breakdown = expenseRepo.getCategoryBreakdown();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(breakdown.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            names.add(entries.get(i).getKey());
        }
        return names;
    }

    // ─── Top Income Sources ───────────────────────────────────

    public Map<String, Double> getTopIncomeSources(int n) {
        Map<String, Double> src = incomeRepo.getSourceBreakdown("This Month");
        List<Map.Entry<String, Double>> entries = new ArrayList<>(src.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            result.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        return result;
    }

    // ─── Financial Health Score ───────────────────────────────

    /**
     * Composite Financial Health Score 0–100.
     *
     * Factors (total 100 points):
     *   1. Savings Rate ≥20%            → 25 pts
     *   2. Budget Adherence              → 20 pts
     *   3. Emergency Fund (3 months)     → 20 pts
     *   4. Debt-to-Asset Ratio           → 20 pts
     *   5. Consistent Income (3 months)  → 15 pts
     */
    public int getFinancialHealthScore() {
        int score = 0;

        // 1. Savings Rate (25 pts)
        double savingsRate = getSavingsRateThisMonth();
        if (savingsRate >= 20) {
            score += 25;
        } else if (savingsRate > 0) {
            score += (int) (25 * savingsRate / 20.0);
        }

        // 2. Budget Adherence (20 pts) — from CategoryBudgetRepository
        ArrayList<CategoryBudget> budgets = budgetRepo.getActiveBudgets();
        if (!budgets.isEmpty()) {
            int onTrack = 0;
            for (CategoryBudget cb : budgets) {
                double spent = budgetRepo.getCategorySpending(
                        cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
                if (spent <= cb.budgetAmount) onTrack++;
            }
            score += (int) (20.0 * onTrack / budgets.size());
        } else {
            score += 10; // No budgets set → give partial credit
        }

        // 3. Emergency Fund (20 pts) — wallet balance covers ≥3 months of avg expenses
        double avgMonthlyExpenses = getAvgMonthlyExpenses(3);
        double totalWalletBalance = walletRepo.getTotalBalance();
        if (avgMonthlyExpenses > 0) {
            double monthsCovered = totalWalletBalance / avgMonthlyExpenses;
            if (monthsCovered >= 3) {
                score += 20;
            } else if (monthsCovered > 0) {
                score += (int) (20 * monthsCovered / 3.0);
            }
        } else {
            score += 20; // No expenses recorded → don't penalise
        }

        // 4. Debt-to-Asset Ratio (20 pts)
        double assets      = getTotalAssets();
        double liabilities = getTotalLiabilities();
        if (liabilities <= 0) {
            score += 20;
        } else if (assets > 0) {
            double ratio = liabilities / assets;
            if (ratio <= 0.1)      score += 20;
            else if (ratio <= 0.3) score += 15;
            else if (ratio <= 0.5) score += 10;
            else if (ratio <= 1.0) score += 5;
        }

        // 5. Consistent Income (15 pts) — income logged in each of last 3 months
        double[] monthlyIncome = getMonthlyIncome(3);
        int monthsWithIncome = 0;
        for (double v : monthlyIncome) if (v > 0) monthsWithIncome++;
        score += (int) (15.0 * monthsWithIncome / 3.0);

        return Math.min(100, Math.max(0, score));
    }

    /**
     * Returns an array of 5 score components [savings, budget, emergency, debt, income].
     * Each value is the points earned out of the max for that factor.
     */
    public int[] getHealthScoreBreakdown() {
        int[] breakdown = new int[5];

        // 1. Savings (max 25)
        double sr = getSavingsRateThisMonth();
        breakdown[0] = sr >= 20 ? 25 : (int) (25 * sr / 20.0);

        // 2. Budget (max 20)
        ArrayList<CategoryBudget> budgets = budgetRepo.getActiveBudgets();
        if (!budgets.isEmpty()) {
            int onTrack = 0;
            for (CategoryBudget cb : budgets) {
                double spent = budgetRepo.getCategorySpending(
                        cb.categoryId, cb.startDate, cb.endDate, expenseRepo);
                if (spent <= cb.budgetAmount) onTrack++;
            }
            breakdown[1] = (int) (20.0 * onTrack / budgets.size());
        } else {
            breakdown[1] = 10;
        }

        // 3. Emergency (max 20)
        double avgExp   = getAvgMonthlyExpenses(3);
        double walBal   = walletRepo.getTotalBalance();
        if (avgExp > 0) {
            double mc = walBal / avgExp;
            breakdown[2] = mc >= 3 ? 20 : (int) (20 * mc / 3.0);
        } else {
            breakdown[2] = 20;
        }

        // 4. Debt/Asset (max 20)
        double assets = getTotalAssets(), liabs = getTotalLiabilities();
        if (liabs <= 0)           breakdown[3] = 20;
        else if (assets <= 0)     breakdown[3] = 0;
        else {
            double ratio = liabs / assets;
            if (ratio <= 0.1)      breakdown[3] = 20;
            else if (ratio <= 0.3) breakdown[3] = 15;
            else if (ratio <= 0.5) breakdown[3] = 10;
            else if (ratio <= 1.0) breakdown[3] = 5;
            else                   breakdown[3] = 0;
        }

        // 5. Consistent Income (max 15)
        double[] mi = getMonthlyIncome(3);
        int months = 0; for (double v : mi) if (v > 0) months++;
        breakdown[4] = (int) (15.0 * months / 3.0);

        return breakdown;
    }

    /** Average monthly expenses over the last N months. */
    public double getAvgMonthlyExpenses(int months) {
        double[] history = getMonthlyExpenses(months);
        double sum = 0; int count = 0;
        for (double v : history) { sum += v; if (v > 0) count++; }
        return count > 0 ? sum / count : 0;
    }

    // ─── Overdue IOUs ─────────────────────────────────────────

    public int getOverdueIouCount() {
        return iouRepo.getOverdueRecords().size();
    }

    // ─── Balance Visibility ───────────────────────────────────

    public boolean isBalanceHidden() {
        return walletRepo.isBalanceHidden();
    }

    public void setBalanceHidden(boolean hidden) {
        walletRepo.setBalanceHidden(hidden);
    }

    // ─── Insights ─────────────────────────────────────────────

    /**
     * Generates a list of personalised financial insight strings from actual data.
     * Each insight is a String[3]: {emoji, headline, detail}.
     */
    public List<String[]> generateInsights() {
        List<String[]> pool = new ArrayList<>();

        double incomeThisMonth    = getIncomeThisMonth();
        double expensesThisMonth  = getExpensesThisMonth();
        double savingsRate        = getSavingsRateThisMonth();
        double netWorth           = getNetWorth();
        int    overdueCount       = getOverdueIouCount();

        // Insight 1: Spending vs income this month
        if (incomeThisMonth > 0 && expensesThisMonth > 0) {
            double ratio = expensesThisMonth / incomeThisMonth * 100;
            if (ratio > 90) {
                pool.add(new String[]{"⚠️", "High spending this month",
                        String.format("You've spent %.0f%% of your income this month.", ratio)});
            } else if (ratio < 60) {
                pool.add(new String[]{"🎉", "Great savings this month!",
                        String.format("You've only spent %.0f%% of your income — keep it up!", ratio)});
            }
        }

        // Insight 2: Savings rate benchmark
        if (savingsRate >= 20) {
            pool.add(new String[]{"✅", "Hitting the savings target",
                    String.format("Your savings rate is %.1f%% — above the recommended 20%%.", savingsRate)});
        } else if (incomeThisMonth > 0) {
            double extra = incomeThisMonth * 0.20 - (incomeThisMonth - expensesThisMonth);
            pool.add(new String[]{"💡", "Boost your savings rate",
                    String.format("Save ₹%.0f more to hit the 20%% savings target.", Math.max(0, extra))});
        }

        // Insight 3: Overdue IOUs
        if (overdueCount > 0) {
            pool.add(new String[]{"🔔", overdueCount + " overdue IOU" + (overdueCount > 1 ? "s" : ""),
                    "Consider following up to recover money owed to you."});
        }

        // Insight 4: Net worth progress
        double[] trend = new NetWorthRepository(context).getDailyTrend(30);
        if (trend.length >= 30 && trend[0] > 0) {
            double delta = trend[trend.length - 1] - trend[0];
            if (delta > 0) {
                pool.add(new String[]{"📈", "Net worth growing",
                        String.format("Your net worth grew by ₹%.0f over the last 30 days.", delta)});
            } else if (delta < 0) {
                pool.add(new String[]{"📉", "Net worth declined",
                        String.format("Your net worth dropped ₹%.0f in the last 30 days.", Math.abs(delta))});
            }
        }

        // Insight 5: Time-to-save projection
        if (savingsRate > 0 && incomeThisMonth > 0) {
            double monthlySavings = incomeThisMonth - expensesThisMonth;
            if (monthlySavings > 0) {
                double target = SAVINGS_MILESTONE_TARGET;
                double months = target / monthlySavings;
                if (months <= 24) {
                    pool.add(new String[]{"🎯", "₹1 Lakh milestone",
                            String.format("At your current rate you'll save ₹1,00,000 in ~%.0f months.", months)});
                }
            }
        }

        // Shuffle and return up to 4 insights
        Collections.shuffle(pool);
        return pool.size() > 4 ? pool.subList(0, 4) : pool;
    }
}
