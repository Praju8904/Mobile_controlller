package com.prajwal.myfirstapp.expenses;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MoneyRecordRepository {

    private static final String PREFS_NAME = "money_record_prefs";
    private static final String DATA_KEY = "money_records_data";
    private static final String REPAYMENTS_KEY = "repayments_data";

    private final Context context;

    public MoneyRecordRepository(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── MoneyRecord CRUD ────────────────────────────────────

    public synchronized void saveAll(ArrayList<MoneyRecord> records) {
        JSONArray array = new JSONArray();
        for (MoneyRecord r : records) array.put(r.toJson());
        getPrefs().edit().putString(DATA_KEY, array.toString()).apply();
    }

    public ArrayList<MoneyRecord> loadAll() {
        ArrayList<MoneyRecord> list = new ArrayList<>();
        String json = getPrefs().getString(DATA_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                MoneyRecord r = MoneyRecord.fromJson(array.getJSONObject(i));
                if (r != null) list.add(r);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Collections.sort(list, (a, b) -> Long.compare(b.date, a.date));
        return list;
    }

    /** Add a new record, optionally logging a wallet transaction. */
    public void addRecord(MoneyRecord record) {
        ArrayList<MoneyRecord> all = loadAll();
        all.add(0, record);
        saveAll(all);

        if (record.logInWallet && record.walletId != null && !record.walletId.isEmpty()) {
            WalletRepository walletRepo = new WalletRepository(context);
            if (MoneyRecord.TYPE_LENT.equals(record.type)) {
                // Money goes out when you lend
                walletRepo.adjustBalance(record.walletId, record.amount, false);
            } else {
                // Money comes in when you borrow
                walletRepo.adjustBalance(record.walletId, record.amount, true);
            }
        }
    }

    public void updateRecord(MoneyRecord record) {
        record.updatedAt = System.currentTimeMillis();
        ArrayList<MoneyRecord> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(record.id)) {
                all.set(i, record);
                break;
            }
        }
        saveAll(all);
    }

    public void deleteRecord(String id, WalletRepository walletRepo) {
        ArrayList<MoneyRecord> all = loadAll();
        MoneyRecord toDelete = null;
        for (MoneyRecord r : all) {
            if (r.id.equals(id)) { toDelete = r; break; }
        }
        // Reverse wallet balance if this record was logged in wallet
        if (toDelete != null && toDelete.logInWallet && walletRepo != null
                && toDelete.walletId != null) {
            if (MoneyRecord.TYPE_LENT.equals(toDelete.type)) {
                // Money went out when lent — put it back
                walletRepo.adjustBalance(toDelete.walletId, toDelete.amount, true);
            } else {
                // Money came in when borrowed — take it back
                walletRepo.adjustBalance(toDelete.walletId, toDelete.amount, false);
            }
        }
        all.removeIf(r -> r.id.equals(id));
        saveAll(all);
        // Also delete associated repayments
        ArrayList<Repayment> repayments = loadAllRepayments();
        repayments.removeIf(r -> r.moneyRecordId.equals(id));
        saveAllRepayments(repayments);
    }

    public void deleteRecord(String id) {
        deleteRecord(id, null);
    }

    public MoneyRecord getById(String id) {
        for (MoneyRecord r : loadAll()) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    // ─── Filter Queries ──────────────────────────────────────

    public ArrayList<MoneyRecord> getByType(String type) {
        ArrayList<MoneyRecord> result = new ArrayList<>();
        for (MoneyRecord r : loadAll()) {
            if (type.equals(r.type)) result.add(r);
        }
        return result;
    }

    public ArrayList<MoneyRecord> getByPerson(String personName) {
        ArrayList<MoneyRecord> result = new ArrayList<>();
        for (MoneyRecord r : loadAll()) {
            if (personName.equalsIgnoreCase(r.personName)) result.add(r);
        }
        return result;
    }

    public ArrayList<MoneyRecord> getByStatus(String status) {
        ArrayList<MoneyRecord> result = new ArrayList<>();
        for (MoneyRecord r : loadAll()) {
            if (status.equals(r.status)) result.add(r);
        }
        return result;
    }

    /** Records where expectedReturnDate < now and status is not terminal. */
    public ArrayList<MoneyRecord> getOverdueRecords() {
        long now = System.currentTimeMillis();
        ArrayList<MoneyRecord> result = new ArrayList<>();
        for (MoneyRecord r : loadAll()) {
            if (r.expectedReturnDate > 0
                    && r.expectedReturnDate < now
                    && !MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                result.add(r);
            }
        }
        return result;
    }

    /** Unique person names sorted alphabetically. */
    public ArrayList<String> getAllPersonNames() {
        ArrayList<String> names = new ArrayList<>();
        for (MoneyRecord r : loadAll()) {
            if (r.personName != null && !r.personName.isEmpty() && !names.contains(r.personName)) {
                names.add(r.personName);
            }
        }
        Collections.sort(names);
        return names;
    }

    // ─── Balance Aggregates ──────────────────────────────────

    /** Sum of outstanding for all LENT active/overdue/partially_paid records. */
    public double getTotalLentOutstanding() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_LENT.equals(r.type) && isActiveStatus(r.status)) {
                total += r.getOutstandingAmount();
            }
        }
        return total;
    }

    /** Sum of outstanding for all BORROWED active/overdue/partially_paid records. */
    public double getTotalBorrowedOutstanding() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_BORROWED.equals(r.type) && isActiveStatus(r.status)) {
                total += r.getOutstandingAmount();
            }
        }
        return total;
    }

    /** Lent outstanding minus borrowed outstanding. */
    public double getNetBalance() {
        return getTotalLentOutstanding() - getTotalBorrowedOutstanding();
    }

    private boolean isActiveStatus(String status) {
        return MoneyRecord.STATUS_ACTIVE.equals(status)
                || MoneyRecord.STATUS_OVERDUE.equals(status)
                || MoneyRecord.STATUS_PARTIALLY_PAID.equals(status);
    }

    // ─── Repayment Operations ────────────────────────────────

    public synchronized void saveAllRepayments(ArrayList<Repayment> repayments) {
        JSONArray array = new JSONArray();
        for (Repayment r : repayments) array.put(r.toJson());
        getPrefs().edit().putString(REPAYMENTS_KEY, array.toString()).apply();
    }

    public ArrayList<Repayment> loadAllRepayments() {
        ArrayList<Repayment> list = new ArrayList<>();
        String json = getPrefs().getString(REPAYMENTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Repayment r = Repayment.fromJson(array.getJSONObject(i));
                if (r != null) list.add(r);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Get all repayments for a specific money record, sorted by date desc. */
    public ArrayList<Repayment> getRepayments(String moneyRecordId) {
        ArrayList<Repayment> result = new ArrayList<>();
        for (Repayment r : loadAllRepayments()) {
            if (moneyRecordId.equals(r.moneyRecordId)) result.add(r);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.date, a.date));
        return result;
    }

    /**
     * Add a repayment, update MoneyRecord.amountPaid, recalculate status,
     * and optionally log wallet transaction.
     */
    public void addRepayment(Repayment repayment, WalletRepository walletRepo) {
        // Save repayment
        ArrayList<Repayment> allRepayments = loadAllRepayments();
        allRepayments.add(repayment);
        saveAllRepayments(allRepayments);

        // Update the money record
        MoneyRecord record = getById(repayment.moneyRecordId);
        if (record != null) {
            record.amountPaid += repayment.amount;
            record.updatedAt = System.currentTimeMillis();

            // Recalculate status
            if (record.amountPaid >= record.amount) {
                record.amountPaid = record.amount; // cap
                record.status = MoneyRecord.STATUS_SETTLED;
                record.actualReturnDate = repayment.date;
            } else if (record.amountPaid > 0) {
                record.status = MoneyRecord.STATUS_PARTIALLY_PAID;
            }
            updateRecord(record);

            // Wallet adjustment
            if (walletRepo != null && repayment.walletId != null && !repayment.walletId.isEmpty()) {
                if (MoneyRecord.TYPE_LENT.equals(record.type)) {
                    // Money comes back in when someone repays a loan you gave
                    walletRepo.adjustBalance(repayment.walletId, repayment.amount, true);
                } else {
                    // Money goes out when you repay a loan you took
                    walletRepo.adjustBalance(repayment.walletId, repayment.amount, false);
                }
            }
        }
    }

    /** Check all records and update status to OVERDUE where applicable. */
    public void updateOverdueStatuses() {
        long now = System.currentTimeMillis();
        ArrayList<MoneyRecord> all = loadAll();
        boolean changed = false;
        for (MoneyRecord r : all) {
            if (r.expectedReturnDate > 0
                    && r.expectedReturnDate < now
                    && (MoneyRecord.STATUS_ACTIVE.equals(r.status)
                        || MoneyRecord.STATUS_PARTIALLY_PAID.equals(r.status))) {
                r.status = MoneyRecord.STATUS_OVERDUE;
                r.updatedAt = now;
                changed = true;
            }
        }
        if (changed) saveAll(all);
    }

    // ─── Analytics ───────────────────────────────────────────

    public double getTotalLentAllTime() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_LENT.equals(r.type)) total += r.amount;
        }
        return total;
    }

    public double getTotalBorrowedAllTime() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_BORROWED.equals(r.type)) total += r.amount;
        }
        return total;
    }

    /** Total amount recovered from LENT records (amountPaid). */
    public double getTotalRecoveredAllTime() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_LENT.equals(r.type)) total += r.amountPaid;
        }
        return total;
    }

    /** Total amount repaid on BORROWED records. */
    public double getTotalRepaidAllTime() {
        double total = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_BORROWED.equals(r.type)) total += r.amountPaid;
        }
        return total;
    }

    /** Recovery rate: (totalRecovered / totalLent) * 100. */
    public double getRecoveryRate() {
        double totalLent = getTotalLentAllTime();
        if (totalLent <= 0) return 0;
        return (getTotalRecoveredAllTime() / totalLent) * 100.0;
    }

    /** Person with the most money records. */
    public String getMostFrequentPerson() {
        Map<String, Integer> counts = new HashMap<>();
        for (MoneyRecord r : loadAll()) {
            if (r.personName != null && !r.personName.isEmpty()) {
                counts.put(r.personName, counts.getOrDefault(r.personName, 0) + 1);
            }
        }
        String best = null;
        int max = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /** Average days between date and actualReturnDate for settled LENT records. */
    public double getAverageDaysToRecover() {
        long totalMs = 0;
        int count = 0;
        for (MoneyRecord r : loadAll()) {
            if (MoneyRecord.TYPE_LENT.equals(r.type)
                    && MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && r.actualReturnDate > 0 && r.date > 0) {
                totalMs += (r.actualReturnDate - r.date);
                count++;
            }
        }
        if (count == 0) return 0;
        return (totalMs / (double) count) / (1000.0 * 60 * 60 * 24);
    }
}
