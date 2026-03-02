package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Income Tracker — shows income summary, category/source breakdown, and recent income list.
 * Accessible from the Expense Tracker as a peer tab.
 */
public class IncomeTrackerActivity extends AppCompatActivity {

    private IncomeRepository incomeRepo;
    private IncomeCategoryRepository categoryRepo;
    private RecurringIncomeRepository recurringRepo;
    private WalletRepository walletRepo;
    private ExpenseRepository expenseRepo;

    private String currentPeriod = "This Month";

    // Views
    private TextView tvTotalIncome, tvIncomeAmount, tvExpenseAmount;
    private TextView tvNetSavings, tvSavingsRate;
    private LinearLayout categoryBreakdownContainer, sourceBreakdownContainer;
    private TextView tvNoCategoryData, tvNoSourceData;
    private ListView incomeList;
    private TextView tvEmptyIncome;
    private LinearLayout periodChipContainer;

    private ArrayList<Income> displayedIncomes;
    private IncomeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_income_tracker);

        incomeRepo = new IncomeRepository(this);
        categoryRepo = new IncomeCategoryRepository(this);
        recurringRepo = new RecurringIncomeRepository(this);
        walletRepo = new WalletRepository(this);
        expenseRepo = new ExpenseRepository(this);

        // Auto-log any overdue recurring incomes
        recurringRepo.processOverdueIncomes(incomeRepo, walletRepo);

        initViews();
        setupPeriodChips();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvIncomeAmount = findViewById(R.id.tvIncomeAmount);
        tvExpenseAmount = findViewById(R.id.tvExpenseAmount);
        tvNetSavings = findViewById(R.id.tvNetSavings);
        tvSavingsRate = findViewById(R.id.tvSavingsRate);
        categoryBreakdownContainer = findViewById(R.id.categoryBreakdownContainer);
        sourceBreakdownContainer = findViewById(R.id.sourceBreakdownContainer);
        tvNoCategoryData = findViewById(R.id.tvNoCategoryData);
        tvNoSourceData = findViewById(R.id.tvNoSourceData);
        incomeList = findViewById(R.id.incomeList);
        tvEmptyIncome = findViewById(R.id.tvEmptyIncome);
        periodChipContainer = findViewById(R.id.periodChipContainer);

        displayedIncomes = new ArrayList<>();
        adapter = new IncomeAdapter(this, displayedIncomes);
        incomeList.setAdapter(adapter);

        // Long press to delete
        incomeList.setOnItemLongClickListener((parent, view, position, id) -> {
            Income income = displayedIncomes.get(position);
            new AlertDialog.Builder(this)
                .setTitle("Delete Income")
                .setMessage("Delete \"" + income.title + "\" — ₹" +
                    String.format("%.2f", income.amount) + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    incomeRepo.deleteIncomeWithBalanceReverse(income.id, walletRepo);
                    refreshAll();
                    Toast.makeText(this, "Income deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        // Back
        findViewById(R.id.btnBackIncome).setOnClickListener(v -> finish());

        // Recurring income section
        findViewById(R.id.btnRecurringIncome).setOnClickListener(v ->
            showRecurringIncomeSummary());

        // FAB
        findViewById(R.id.fabAddIncome).setOnClickListener(v -> showAddIncomeDialog());
    }

    private void setupPeriodChips() {
        String[] periods = {"Today", "This Week", "This Month", "This Year"};
        periodChipContainer.removeAllViews();
        for (String period : periods) {
            TextView chip = new TextView(this);
            chip.setText(period);
            chip.setTextSize(12);
            chip.setPadding(32, 16, 32, 16);
            boolean selected = period.equals(currentPeriod);
            chip.setTextColor(selected ? Color.WHITE : 0xFF9CA3AF);
            chip.setBackgroundResource(selected ?
                R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                currentPeriod = period;
                setupPeriodChips();
                refreshAll();
            });
            periodChipContainer.addView(chip);
        }
    }

    private void refreshAll() {
        refreshSummary();
        refreshCategoryBreakdown();
        refreshSourceBreakdown();
        refreshIncomeList();
    }

    private void refreshSummary() {
        double income = incomeRepo.getTotalForPeriod(currentPeriod);
        double expenses = getExpensesForPeriod();
        double savings = income - expenses;
        double savingsRate = income > 0 ? (savings / income) * 100 : 0;

        tvTotalIncome.setText("₹" + formatAmount(income));
        tvIncomeAmount.setText("₹" + formatAmount(income));
        tvExpenseAmount.setText("₹" + formatAmount(expenses));

        tvNetSavings.setText((savings >= 0 ? "+" : "") + "₹" + formatAmount(savings));
        tvNetSavings.setTextColor(savings >= 0 ? 0xFF22C55E : 0xFFEF4444);

        String rateLabel;
        if (income <= 0) {
            rateLabel = "No income recorded this period";
        } else if (savings >= 0) {
            rateLabel = String.format("You saved %.0f%% of your income this period", savingsRate);
        } else {
            rateLabel = String.format("You spent %.0f%% more than your income!", Math.abs(savingsRate));
        }
        tvSavingsRate.setText(rateLabel);
    }

    private double getExpensesForPeriod() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        switch (currentPeriod) {
            case "Today":
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                break;
            case "This Week":
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                break;
            case "This Year":
                cal.set(java.util.Calendar.DAY_OF_YEAR, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                break;
            default: // This Month
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                break;
        }
        long start = cal.getTimeInMillis();
        double total = 0;
        for (Expense e : expenseRepo.loadAll()) {
            if (!e.isIncome && e.timestamp >= start) total += e.amount;
        }
        return total;
    }

    private void refreshCategoryBreakdown() {
        categoryBreakdownContainer.removeAllViews();
        Map<String, Double> breakdown = incomeRepo.getCategoryBreakdown(currentPeriod);

        if (breakdown.isEmpty()) {
            tvNoCategoryData.setVisibility(View.VISIBLE);
            categoryBreakdownContainer.setVisibility(View.GONE);
            return;
        }
        tvNoCategoryData.setVisibility(View.GONE);
        categoryBreakdownContainer.setVisibility(View.VISIBLE);

        double total = 0;
        for (double v : breakdown.values()) total += v;

        // Sort entries by amount descending
        List<Map.Entry<String, Double>> entries = new ArrayList<>(breakdown.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Double> entry : entries) {
            IncomeCategory cat = categoryRepo.getById(entry.getKey());
            String name = cat != null ? cat.name : entry.getKey();
            String icon = cat != null ? cat.iconIdentifier : "💰";
            int color = cat != null ? cat.colorHex : 0xFF22C55E;
            double amount = entry.getValue();
            float pct = total > 0 ? (float) (amount / total) : 0;

            View row = buildBreakdownRow(icon, name, amount, pct, color);
            categoryBreakdownContainer.addView(row);
        }
    }

    private void refreshSourceBreakdown() {
        sourceBreakdownContainer.removeAllViews();
        Map<String, Double> breakdown = incomeRepo.getSourceBreakdown(currentPeriod);

        if (breakdown.isEmpty()) {
            tvNoSourceData.setVisibility(View.VISIBLE);
            sourceBreakdownContainer.setVisibility(View.GONE);
            return;
        }
        tvNoSourceData.setVisibility(View.GONE);
        sourceBreakdownContainer.setVisibility(View.VISIBLE);

        double total = 0;
        for (double v : breakdown.values()) total += v;

        List<Map.Entry<String, Double>> entries = new ArrayList<>(breakdown.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (Map.Entry<String, Double> entry : entries) {
            String src = entry.getKey();
            double amount = entry.getValue();
            float pct = total > 0 ? (float) (amount / total) : 0;
            String icon = Income.getSourceIcon(src);
            View row = buildBreakdownRow(icon, src, amount, pct, 0xFF22C55E);
            sourceBreakdownContainer.addView(row);
        }
    }

    /**
     * Build a horizontal bar breakdown row: icon | name | progress bar | amount | pct%
     */
    private View buildBreakdownRow(String icon, String label, double amount,
                                    float pct, int color) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerLp.setMargins(0, 0, 0, 12);
        container.setLayoutParams(containerLp);

        // Top row: icon + label + amount + pct
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(14);
        tvIcon.setMinWidth(28);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFFE5E7EB);
        tvLabel.setTextSize(13);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(8);
        tvLabel.setLayoutParams(labelLp);

        TextView tvAmt = new TextView(this);
        tvAmt.setText("₹" + formatAmount(amount));
        tvAmt.setTextColor(0xFF22C55E);
        tvAmt.setTextSize(13);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvPct = new TextView(this);
        tvPct.setText(String.format(" (%.0f%%)", pct * 100));
        tvPct.setTextColor(0xFF6B7280);
        tvPct.setTextSize(11);

        topRow.addView(tvIcon);
        topRow.addView(tvLabel);
        topRow.addView(tvAmt);
        topRow.addView(tvPct);
        container.addView(topRow);

        // Progress bar track
        LinearLayout track = new LinearLayout(this);
        android.graphics.drawable.GradientDrawable trackBg =
            new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(0xFF1F2937);
        trackBg.setCornerRadius(4);
        track.setBackground(trackBg);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 6);
        trackLp.setMargins(28, 4, 0, 0);
        track.setLayoutParams(trackLp);

        // Progress fill
        View fill = new View(this);
        android.graphics.drawable.GradientDrawable fillBg =
            new android.graphics.drawable.GradientDrawable();
        fillBg.setColor(color);
        fillBg.setCornerRadius(4);
        fill.setBackground(fillBg);
        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(0, 6, pct);
        fill.setLayoutParams(fillLp);
        track.addView(fill);

        // Remaining space
        View space = new View(this);
        LinearLayout.LayoutParams spaceLp = new LinearLayout.LayoutParams(0, 6, 1f - pct);
        space.setLayoutParams(spaceLp);
        track.addView(space);

        container.addView(track);
        return container;
    }

    private void refreshIncomeList() {
        displayedIncomes.clear();
        displayedIncomes.addAll(incomeRepo.getFiltered(currentPeriod));
        adapter.notifyDataSetChanged();
        setListViewHeightBasedOnChildren(incomeList);

        tvEmptyIncome.setVisibility(displayedIncomes.isEmpty() ? View.VISIBLE : View.GONE);
        incomeList.setVisibility(displayedIncomes.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ─── Add Income Dialog ────────────────────────────────────

    private void showAddIncomeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_income, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etAmount = dialogView.findViewById(R.id.etIncomeAmount);
        EditText etTitle = dialogView.findViewById(R.id.etIncomeTitle);
        EditText etNote = dialogView.findViewById(R.id.etIncomeNote);
        LinearLayout categoryChips = dialogView.findViewById(R.id.incomeCategoryChipContainer);
        LinearLayout sourceRow1 = dialogView.findViewById(R.id.sourceRow1);
        LinearLayout sourceRow2 = dialogView.findViewById(R.id.sourceRow2);
        LinearLayout walletChips = dialogView.findViewById(R.id.incomeWalletChipContainer);

        // State holders
        ArrayList<IncomeCategory> categories = categoryRepo.loadAll();
        final String[] selectedCategoryId = {
            categories.isEmpty() ? "" : categories.get(0).id
        };
        final String[] selectedSource = {Income.SOURCE_SALARY};
        ArrayList<Wallet> activeWallets = walletRepo.getActiveWallets();
        Wallet defaultWallet = walletRepo.getDefaultWallet();
        final String[] selectedWalletId = {
            defaultWallet != null ? defaultWallet.id : Wallet.DEFAULT_WALLET_ID
        };

        // Build category chips
        for (int i = 0; i < categories.size(); i++) {
            IncomeCategory cat = categories.get(i);
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(cat.iconIdentifier + " " + cat.name);
            chip.setTextSize(11);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(20, 12, 20, 12);
            chip.setBackgroundResource(i == 0 ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedCategoryId[0] = cat.id;
                for (int c = 0; c < categoryChips.getChildCount(); c++) {
                    categoryChips.getChildAt(c).setBackgroundResource(
                        c == idx ? R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
                }
            });
            categoryChips.addView(chip);
        }

        // Build source chips (5 per row)
        for (int i = 0; i < Income.SOURCES.length; i++) {
            String src = Income.SOURCES[i];
            String icon = Income.SOURCE_ICONS[i];
            final int idx = i;
            LinearLayout row = i < 5 ? sourceRow1 : sourceRow2;

            TextView chip = new TextView(this);
            chip.setText(icon + " " + src);
            chip.setTextSize(10);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(16, 10, 16, 10);
            chip.setGravity(android.view.Gravity.CENTER);
            boolean selected = src.equals(selectedSource[0]);
            chip.setBackgroundResource(selected ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(6);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedSource[0] = src;
                updateSourceChips(sourceRow1, sourceRow2, idx);
            });
            row.addView(chip);
        }

        // Build wallet chips
        for (int i = 0; i < activeWallets.size(); i++) {
            Wallet wt = activeWallets.get(i);
            final int wIdx = i;
            TextView chip = new TextView(this);
            chip.setText(wt.getTypeIcon() + " " + wt.name);
            chip.setTextSize(11);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(24, 12, 24, 12);
            boolean sel = wt.id.equals(selectedWalletId[0]);
            chip.setBackgroundResource(sel ?
                R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedWalletId[0] = wt.id;
                for (int c = 0; c < walletChips.getChildCount(); c++) {
                    walletChips.getChildAt(c).setBackgroundResource(
                        c == wIdx ? R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
                }
            });
            walletChips.addView(chip);
        }

        // Save button
        dialogView.findViewById(R.id.btnSaveIncome).setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                etAmount.setError("Enter amount");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ex) {
                etAmount.setError("Invalid amount");
                return;
            }
            if (amount <= 0) {
                etAmount.setError("Amount must be positive");
                return;
            }

            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) title = selectedSource[0];

            String note = etNote.getText().toString().trim();

            Income income = new Income(title, amount, selectedCategoryId[0],
                selectedSource[0], selectedWalletId[0]);
            income.notes = note;
            income.time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date());

            incomeRepo.addIncomeWithBalance(income, walletRepo);

            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etAmount.getWindowToken(), 0);

            dialog.dismiss();
            refreshAll();
            Toast.makeText(this, "💰 Income ₹" + String.format("%.0f", amount) + " saved!",
                Toast.LENGTH_SHORT).show();
        });

        dialog.show();
        etAmount.requestFocus();
        etAmount.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etAmount, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void updateSourceChips(LinearLayout row1, LinearLayout row2, int selectedIdx) {
        for (int i = 0; i < 5 && i < row1.getChildCount(); i++) {
            row1.getChildAt(i).setBackgroundResource(
                i == selectedIdx ? R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
        }
        for (int i = 0; i < 5 && i < row2.getChildCount(); i++) {
            row2.getChildAt(i).setBackgroundResource(
                (i + 5) == selectedIdx ?
                    R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
        }
    }

    // ─── Recurring Income Summary ─────────────────────────────

    private void showRecurringIncomeSummary() {
        double monthly = recurringRepo.getTotalMonthlyRecurring();
        ArrayList<RecurringIncome> active = recurringRepo.getActive();

        StringBuilder sb = new StringBuilder();
        sb.append("📅 Active recurring incomes: ").append(active.size()).append("\n");
        sb.append("💰 Expected monthly total: ₹").append(formatAmount(monthly)).append("\n\n");
        for (RecurringIncome ri : active) {
            int days = ri.getDaysUntilDue();
            String due = days == 0 ? "Today!" : (days < 0 ? "Overdue" : "In " + days + " days");
            sb.append("• ").append(ri.title)
              .append(" — ₹").append(formatAmount(ri.amount))
              .append(" (").append(ri.getRecurrenceLabel()).append(")")
              .append(" — Next: ").append(due)
              .append("\n");
        }
        if (active.isEmpty()) {
            sb.append("No active recurring incomes.\nAdd one via the Add Income dialog.");
        }

        new AlertDialog.Builder(this)
            .setTitle("🔄 Recurring Income")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    // ─── Income Adapter ───────────────────────────────────────

    private class IncomeAdapter extends ArrayAdapter<Income> {
        private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault());

        IncomeAdapter(Context context, ArrayList<Income> items) {
            super(context, R.layout.item_income_transaction, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_income_transaction, parent, false);
            }

            Income inc = getItem(position);
            if (inc == null) return convertView;

            TextView tvIcon = convertView.findViewById(R.id.tvIncomeIcon);
            TextView tvTitle = convertView.findViewById(R.id.tvIncomeTitle);
            TextView tvSource = convertView.findViewById(R.id.tvIncomeSource);
            TextView tvTime = convertView.findViewById(R.id.tvIncomeTime);
            TextView tvAmount = convertView.findViewById(R.id.tvIncomeAmount);
            TextView tvWallet = convertView.findViewById(R.id.tvIncomeWallet);

            // Category icon
            IncomeCategory cat = categoryRepo.getById(inc.categoryId);
            String icon = cat != null ? cat.iconIdentifier : Income.getSourceIcon(inc.source);
            tvIcon.setText(icon);

            tvTitle.setText(TextUtils.isEmpty(inc.title) ? inc.source : inc.title);
            tvSource.setText(Income.getSourceIcon(inc.source) + " " + inc.source);
            tvTime.setText(timeFmt.format(new Date(inc.date)));
            tvAmount.setText("+" + (inc.currency != null ? inc.currency : "₹") +
                String.format("%.2f", inc.amount));

            // Wallet name
            Wallet wallet = walletRepo.getById(inc.walletId);
            tvWallet.setText(wallet != null ? wallet.getTypeIcon() + " " + wallet.name : "");

            return convertView;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }

    private static void setListViewHeightBasedOnChildren(ListView listView) {
        ArrayAdapter adapter = (ArrayAdapter) listView.getAdapter();
        if (adapter == null || adapter.getCount() == 0) return;

        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(
            listView.getWidth(), View.MeasureSpec.UNSPECIFIED);

        for (int i = 0; i < adapter.getCount(); i++) {
            View listItem = adapter.getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }
}
