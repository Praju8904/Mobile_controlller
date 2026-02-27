package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Repository for split groups, split expenses, and settlements.
 * Includes the debt simplification algorithm.
 */
public class SplitRepository {

    private static final String PREFS_NAME = "split_expense_prefs";
    private static final String KEY_GROUPS = "split_groups_data";
    private static final String KEY_EXPENSES = "split_expenses_data";
    private static final String KEY_SETTLEMENTS = "settlements_data";
    private static final String KEY_EXPORT_HISTORY = "export_history_data";

    private final SharedPreferences prefs;
    private final NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public SplitRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════
    //  GROUP CRUD
    // ═══════════════════════════════════════════════════════════

    public ArrayList<SplitGroup> loadAllGroups() {
        ArrayList<SplitGroup> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_GROUPS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                SplitGroup g = SplitGroup.fromJson(arr.getJSONObject(i));
                if (g != null) list.add(g);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveAllGroups(ArrayList<SplitGroup> groups) {
        JSONArray arr = new JSONArray();
        for (SplitGroup g : groups) arr.put(g.toJson());
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply();
    }

    public void addGroup(SplitGroup group) {
        ArrayList<SplitGroup> all = loadAllGroups();
        all.add(0, group);
        saveAllGroups(all);
    }

    public void updateGroup(SplitGroup group) {
        group.updatedAt = System.currentTimeMillis();
        ArrayList<SplitGroup> all = loadAllGroups();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(group.id)) {
                all.set(i, group);
                break;
            }
        }
        saveAllGroups(all);
    }

    public void deleteGroup(String groupId) {
        ArrayList<SplitGroup> all = loadAllGroups();
        all.removeIf(g -> g.id.equals(groupId));
        saveAllGroups(all);
        // Also delete related expenses and settlements
        ArrayList<SplitExpense> expenses = loadAllExpenses();
        expenses.removeIf(e -> groupId.equals(e.groupId));
        saveAllExpenses(expenses);
        ArrayList<Settlement> settlements = loadAllSettlements();
        settlements.removeIf(s -> groupId.equals(s.groupId));
        saveAllSettlements(settlements);
    }

    public SplitGroup getGroupById(String groupId) {
        for (SplitGroup g : loadAllGroups()) {
            if (g.id.equals(groupId)) return g;
        }
        return null;
    }

    public ArrayList<SplitGroup> getActiveGroups() {
        ArrayList<SplitGroup> result = new ArrayList<>();
        for (SplitGroup g : loadAllGroups()) {
            if (!g.isArchived && !g.isSettled) result.add(g);
        }
        return result;
    }

    public ArrayList<SplitGroup> getArchivedOrSettledGroups() {
        ArrayList<SplitGroup> result = new ArrayList<>();
        for (SplitGroup g : loadAllGroups()) {
            if (g.isArchived || g.isSettled) result.add(g);
        }
        return result;
    }

    public void archiveGroup(String groupId) {
        SplitGroup g = getGroupById(groupId);
        if (g != null) {
            g.isArchived = true;
            updateGroup(g);
        }
    }

    public void unarchiveGroup(String groupId) {
        SplitGroup g = getGroupById(groupId);
        if (g != null) {
            g.isArchived = false;
            g.isSettled = false;
            updateGroup(g);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SPLIT EXPENSE CRUD
    // ═══════════════════════════════════════════════════════════

    public ArrayList<SplitExpense> loadAllExpenses() {
        ArrayList<SplitExpense> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_EXPENSES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                SplitExpense e = SplitExpense.fromJson(arr.getJSONObject(i));
                if (e != null) list.add(e);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveAllExpenses(ArrayList<SplitExpense> expenses) {
        JSONArray arr = new JSONArray();
        for (SplitExpense e : expenses) arr.put(e.toJson());
        prefs.edit().putString(KEY_EXPENSES, arr.toString()).apply();
    }

    public void addExpense(SplitExpense expense) {
        ArrayList<SplitExpense> all = loadAllExpenses();
        all.add(0, expense);
        saveAllExpenses(all);
        // Update group total
        recalculateGroupTotal(expense.groupId);
    }

    public void updateExpense(SplitExpense expense) {
        expense.updatedAt = System.currentTimeMillis();
        ArrayList<SplitExpense> all = loadAllExpenses();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(expense.id)) {
                all.set(i, expense);
                break;
            }
        }
        saveAllExpenses(all);
        recalculateGroupTotal(expense.groupId);
    }

    public void deleteExpense(String expenseId) {
        ArrayList<SplitExpense> all = loadAllExpenses();
        String groupId = null;
        for (SplitExpense e : all) {
            if (e.id.equals(expenseId)) {
                groupId = e.groupId;
                break;
            }
        }
        all.removeIf(e -> e.id.equals(expenseId));
        saveAllExpenses(all);
        if (groupId != null) recalculateGroupTotal(groupId);
    }

    public SplitExpense getExpenseById(String expenseId) {
        for (SplitExpense e : loadAllExpenses()) {
            if (e.id.equals(expenseId)) return e;
        }
        return null;
    }

    public ArrayList<SplitExpense> getExpensesForGroup(String groupId) {
        ArrayList<SplitExpense> result = new ArrayList<>();
        for (SplitExpense e : loadAllExpenses()) {
            if (groupId.equals(e.groupId)) result.add(e);
        }
        // Sort by date descending
        Collections.sort(result, (a, b) -> Long.compare(b.date, a.date));
        return result;
    }

    private void recalculateGroupTotal(String groupId) {
        double total = 0;
        for (SplitExpense e : getExpensesForGroup(groupId)) {
            total += e.totalAmount;
        }
        SplitGroup g = getGroupById(groupId);
        if (g != null) {
            g.totalExpenses = total;
            updateGroup(g);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTLEMENT CRUD
    // ═══════════════════════════════════════════════════════════

    public ArrayList<Settlement> loadAllSettlements() {
        ArrayList<Settlement> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_SETTLEMENTS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                Settlement s = Settlement.fromJson(arr.getJSONObject(i));
                if (s != null) list.add(s);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveAllSettlements(ArrayList<Settlement> settlements) {
        JSONArray arr = new JSONArray();
        for (Settlement s : settlements) arr.put(s.toJson());
        prefs.edit().putString(KEY_SETTLEMENTS, arr.toString()).apply();
    }

    public void addSettlement(Settlement settlement) {
        ArrayList<Settlement> all = loadAllSettlements();
        all.add(0, settlement);
        saveAllSettlements(all);
    }

    public ArrayList<Settlement> getSettlementsForGroup(String groupId) {
        ArrayList<Settlement> result = new ArrayList<>();
        for (Settlement s : loadAllSettlements()) {
            if (groupId.equals(s.groupId)) result.add(s);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.date, a.date));
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  BALANCE CALCULATION — THE CORE ALGORITHM
    // ═══════════════════════════════════════════════════════════

    /**
     * Calculate raw net balance for each member in a group.
     * Positive = member is owed money. Negative = member owes money.
     * Accounts for all expenses + all settlements.
     */
    public Map<String, Double> calculateMemberBalances(String groupId) {
        SplitGroup group = getGroupById(groupId);
        if (group == null) return new HashMap<>();

        Map<String, Double> balances = new HashMap<>();
        // Initialize all members to 0
        for (SplitMember m : group.members) {
            balances.put(m.id, 0.0);
        }

        // Process all expenses
        for (SplitExpense expense : getExpensesForGroup(groupId)) {
            String payerId = expense.paidByMemberId;
            // The payer put up totalAmount
            Double payerBal = balances.get(payerId);
            if (payerBal != null) {
                balances.put(payerId, payerBal + expense.totalAmount);
            }
            // Each member owes their share
            for (MemberSplit split : expense.splits) {
                Double memberBal = balances.get(split.memberId);
                if (memberBal != null) {
                    balances.put(split.memberId, memberBal - split.amountOwed);
                }
            }
        }

        // Process all settlements
        for (Settlement s : getSettlementsForGroup(groupId)) {
            // fromMember paid toMember
            Double fromBal = balances.get(s.fromMemberId);
            Double toBal = balances.get(s.toMemberId);
            if (fromBal != null) balances.put(s.fromMemberId, fromBal + s.amount);
            if (toBal != null) balances.put(s.toMemberId, toBal - s.amount);
        }

        return balances;
    }

    /**
     * Debt simplification algorithm.
     * Given raw balances, compute the minimum number of transactions needed to settle.
     * Returns a list of "from pays to amount" triples.
     *
     * Algorithm: Greedy matching of max creditor with max debtor.
     */
    public ArrayList<DebtTransaction> getSimplifiedDebts(String groupId) {
        Map<String, Double> balances = calculateMemberBalances(groupId);
        ArrayList<DebtTransaction> result = new ArrayList<>();

        // Separate into creditors (+) and debtors (-)
        ArrayList<String> creditorIds = new ArrayList<>();
        ArrayList<Double> creditorAmounts = new ArrayList<>();
        ArrayList<String> debtorIds = new ArrayList<>();
        ArrayList<Double> debtorAmounts = new ArrayList<>();

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            double bal = entry.getValue();
            if (bal > 0.01) {
                creditorIds.add(entry.getKey());
                creditorAmounts.add(bal);
            } else if (bal < -0.01) {
                debtorIds.add(entry.getKey());
                debtorAmounts.add(-bal); // Store as positive
            }
        }

        // Greedy: match largest creditor with largest debtor
        while (!creditorIds.isEmpty() && !debtorIds.isEmpty()) {
            // Find max creditor
            int maxC = 0;
            for (int i = 1; i < creditorAmounts.size(); i++) {
                if (creditorAmounts.get(i) > creditorAmounts.get(maxC)) maxC = i;
            }
            // Find max debtor
            int maxD = 0;
            for (int i = 1; i < debtorAmounts.size(); i++) {
                if (debtorAmounts.get(i) > debtorAmounts.get(maxD)) maxD = i;
            }

            double amount = Math.min(creditorAmounts.get(maxC), debtorAmounts.get(maxD));
            if (amount < 0.01) break;

            // Round to 2 decimal places
            amount = Math.round(amount * 100.0) / 100.0;

            DebtTransaction dt = new DebtTransaction();
            dt.fromMemberId = debtorIds.get(maxD);
            dt.toMemberId = creditorIds.get(maxC);
            dt.amount = amount;
            result.add(dt);

            creditorAmounts.set(maxC, creditorAmounts.get(maxC) - amount);
            debtorAmounts.set(maxD, debtorAmounts.get(maxD) - amount);

            if (creditorAmounts.get(maxC) < 0.01) {
                creditorIds.remove(maxC);
                creditorAmounts.remove(maxC);
            }
            if (maxD < debtorAmounts.size() && debtorAmounts.get(maxD) < 0.01) {
                debtorIds.remove(maxD);
                debtorAmounts.remove(maxD);
            }
        }

        return result;
    }

    /**
     * Simple data holder for a simplified debt transaction.
     */
    public static class DebtTransaction {
        public String fromMemberId;
        public String toMemberId;
        public double amount;
    }

    /**
     * Get the current user's net balance across ALL active groups.
     * Positive = others owe you. Negative = you owe others.
     */
    public double getCurrentUserNetBalance() {
        double total = 0;
        for (SplitGroup g : getActiveGroups()) {
            total += getCurrentUserBalanceInGroup(g.id);
        }
        return total;
    }

    /**
     * Get the current user's balance in a specific group.
     */
    public double getCurrentUserBalanceInGroup(String groupId) {
        SplitGroup group = getGroupById(groupId);
        if (group == null) return 0;
        SplitMember me = group.getCurrentUser();
        if (me == null) return 0;
        Map<String, Double> balances = calculateMemberBalances(groupId);
        Double bal = balances.get(me.id);
        return bal != null ? bal : 0;
    }

    /**
     * Total amount others owe you across all active groups.
     */
    public double getTotalOwedToYou() {
        double total = 0;
        for (SplitGroup g : getActiveGroups()) {
            double bal = getCurrentUserBalanceInGroup(g.id);
            if (bal > 0) total += bal;
        }
        return total;
    }

    /**
     * Total amount you owe others across all active groups.
     */
    public double getTotalYouOwe() {
        double total = 0;
        for (SplitGroup g : getActiveGroups()) {
            double bal = getCurrentUserBalanceInGroup(g.id);
            if (bal < 0) total += Math.abs(bal);
        }
        return total;
    }

    /**
     * Check if a group is fully settled (all balances are ~0).
     */
    public boolean isGroupFullySettled(String groupId) {
        Map<String, Double> balances = calculateMemberBalances(groupId);
        for (double bal : balances.values()) {
            if (Math.abs(bal) > 0.01) return false;
        }
        return true;
    }

    /**
     * Mark a group as settled.
     */
    public void settleGroup(String groupId) {
        SplitGroup g = getGroupById(groupId);
        if (g != null) {
            g.isSettled = true;
            updateGroup(g);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STATS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get category breakdown of split expenses in a group.
     */
    public Map<String, Double> getGroupCategoryBreakdown(String groupId) {
        Map<String, Double> breakdown = new HashMap<>();
        for (SplitExpense e : getExpensesForGroup(groupId)) {
            String cat = e.categoryId != null ? e.categoryId : "Other";
            breakdown.put(cat, breakdown.getOrDefault(cat, 0.0) + e.totalAmount);
        }
        return breakdown;
    }

    /**
     * Get monthly spending for a group (last N months).
     */
    public double[] getGroupMonthlySpend(String groupId, int months) {
        double[] result = new double[months];
        Calendar cal = Calendar.getInstance();
        for (int m = 0; m < months; m++) {
            Calendar monthStart = (Calendar) cal.clone();
            monthStart.add(Calendar.MONTH, -(months - 1 - m));
            monthStart.set(Calendar.DAY_OF_MONTH, 1);
            monthStart.set(Calendar.HOUR_OF_DAY, 0);
            monthStart.set(Calendar.MINUTE, 0);
            monthStart.set(Calendar.SECOND, 0);

            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);

            for (SplitExpense e : getExpensesForGroup(groupId)) {
                if (e.date >= monthStart.getTimeInMillis() && e.date < monthEnd.getTimeInMillis()) {
                    result[m] += e.totalAmount;
                }
            }
        }
        return result;
    }

    /**
     * Find the member who paid the most often in a group.
     */
    public String getMostActivePayer(String groupId) {
        Map<String, Integer> counts = new HashMap<>();
        for (SplitExpense e : getExpensesForGroup(groupId)) {
            counts.put(e.paidByMemberId, counts.getOrDefault(e.paidByMemberId, 0) + 1);
        }
        String maxId = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxId = entry.getKey();
            }
        }
        return maxId;
    }

    /**
     * Get the most expensive single expense in a group.
     */
    public SplitExpense getMostExpensiveInGroup(String groupId) {
        SplitExpense max = null;
        for (SplitExpense e : getExpensesForGroup(groupId)) {
            if (max == null || e.totalAmount > max.totalAmount) max = e;
        }
        return max;
    }

    // ═══════════════════════════════════════════════════════════
    //  SHARE SUMMARY TEXT
    // ═══════════════════════════════════════════════════════════

    /**
     * Generate a formatted text summary for sharing via WhatsApp/SMS etc.
     */
    public String generateShareSummary(String groupId) {
        SplitGroup group = getGroupById(groupId);
        if (group == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *").append(group.name).append(" — Split Summary*\n\n");
        sb.append("💰 Total Spent: ").append(currFmt.format(group.totalExpenses)).append("\n");
        sb.append("👥 Members: ").append(group.getMemberCount()).append("\n\n");

        ArrayList<DebtTransaction> debts = getSimplifiedDebts(groupId);
        if (debts.isEmpty()) {
            sb.append("✅ Everyone is settled up!\n");
        } else {
            sb.append("💸 *Outstanding Balances:*\n");
            for (DebtTransaction dt : debts) {
                String fromName = group.getMemberName(dt.fromMemberId);
                String toName = group.getMemberName(dt.toMemberId);
                sb.append("  • ").append(fromName).append(" owes ").append(toName)
                  .append(" ").append(currFmt.format(dt.amount)).append("\n");
            }
        }

        sb.append("\n— Sent from Mobile Controller");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT HISTORY
    // ═══════════════════════════════════════════════════════════

    public ArrayList<JSONObject> getExportHistory() {
        ArrayList<JSONObject> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_EXPORT_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void addExportRecord(String format, String dateRange, long fileSize, String filePath) {
        try {
            ArrayList<JSONObject> history = getExportHistory();
            JSONObject record = new JSONObject();
            record.put("date", System.currentTimeMillis());
            record.put("format", format);
            record.put("dateRange", dateRange);
            record.put("fileSize", fileSize);
            record.put("filePath", filePath);
            history.add(0, record);
            // Keep only last 10
            while (history.size() > 10) history.remove(history.size() - 1);
            JSONArray arr = new JSONArray();
            for (JSONObject o : history) arr.put(o);
            prefs.edit().putString(KEY_EXPORT_HISTORY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
