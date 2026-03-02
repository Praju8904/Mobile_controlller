package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.ExportService;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.util.Locale;
import java.util.Map;

public class ExpenseTrackerActivity extends AppCompatActivity {

    private ExpenseRepository repo;
    private WalletRepository walletRepo;
    private ArrayList<Expense> displayedExpenses;
    private TransactionAdapter adapter;
    private String currentFilter = "All";
    private String currentWalletFilter = null; // null = all wallets

    // Views
    private TextView tvTodaySpend, tvYesterdayCompare, tvWeekSpend, tvWeekChange;
    private TextView tvLastWeekSpend, tvBudgetLabel, tvBudgetStatus;
    private View budgetProgressBar;
    private LinearLayout budgetSection;
    private ExpenseChartView barChart;
    private ExpenseDonutView donutChart;
    private ExpenseLineChartView lineChart;
    private TextView tvInsight1, tvInsight2, tvInsight3;
    private ListView transactionList;
    private TextView tvEmptyTransactions;
    private LinearLayout filterChipContainer;
    private LinearLayout walletFilterContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_tracker);

        repo = new ExpenseRepository(this);
        walletRepo = new WalletRepository(this);

        // Run data migration (v1 → v2 wallet support)
        new ExpenseMigrationManager(this).runMigrations();

        // Process any overdue recurring expenses & budget period rollovers
        ExpenseNotificationHelper.processRecurringExpenses(this);

        initViews();
        setupWalletFilter();
        setupFilterChips();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupWalletFilter();
        refreshAll();
        updateIouOverdueBanner();
        updateIncomeTrackerSummary();
    }

    private void updateIncomeTrackerSummary() {
        TextView tvSummary = findViewById(R.id.tvIncomeTrackerSummary);
        if (tvSummary == null) return;
        IncomeRepository incRepo = new IncomeRepository(this);
        double monthIncome = incRepo.getTotalThisMonth();
        if (monthIncome > 0) {
            if (monthIncome >= 100000) {
                tvSummary.setText(String.format("₹%.1fL this month", monthIncome / 100000));
            } else if (monthIncome >= 1000) {
                tvSummary.setText(String.format("₹%.1fk this month", monthIncome / 1000));
            } else {
                tvSummary.setText(String.format("₹%.0f this month", monthIncome));
            }
        } else {
            tvSummary.setText("Track earnings");
        }
    }

    private void updateIouOverdueBanner() {
        LinearLayout iouBanner = findViewById(R.id.iouOverdueBanner);
        if (iouBanner == null) return;
        MoneyRecordRepository iouRepo = new MoneyRecordRepository(this);
        iouRepo.updateOverdueStatuses();
        int count = iouRepo.getOverdueRecords().size();
        if (count > 0) {
            iouBanner.setVisibility(android.view.View.VISIBLE);
            TextView tvBanner = findViewById(R.id.tvIouOverdueBanner);
            if (tvBanner != null) {
                tvBanner.setText("⚠️ " + count + " overdue IOU"
                        + (count > 1 ? "s" : "") + " — tap to view");
            }
        } else {
            iouBanner.setVisibility(android.view.View.GONE);
        }
    }

    private void initViews() {
        tvTodaySpend = findViewById(R.id.tvTodaySpend);
        tvYesterdayCompare = findViewById(R.id.tvYesterdayCompare);
        tvWeekSpend = findViewById(R.id.tvWeekSpend);
        tvWeekChange = findViewById(R.id.tvWeekChange);
        tvLastWeekSpend = findViewById(R.id.tvLastWeekSpend);
        tvBudgetLabel = findViewById(R.id.tvBudgetLabel);
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus);
        budgetProgressBar = findViewById(R.id.budgetProgressBar);
        budgetSection = findViewById(R.id.budgetSection);
        barChart = findViewById(R.id.barChart);
        donutChart = findViewById(R.id.donutChart);
        lineChart = findViewById(R.id.lineChart);
        tvInsight1 = findViewById(R.id.tvInsight1);
        tvInsight2 = findViewById(R.id.tvInsight2);
        tvInsight3 = findViewById(R.id.tvInsight3);
        transactionList = findViewById(R.id.transactionList);
        tvEmptyTransactions = findViewById(R.id.tvEmptyTransactions);
        filterChipContainer = findViewById(R.id.filterChipContainer);
        walletFilterContainer = findViewById(R.id.walletFilterContainer);

        displayedExpenses = new ArrayList<>();
        adapter = new TransactionAdapter(this, displayedExpenses);
        transactionList = findViewById(R.id.transactionList);
        transactionList.setAdapter(adapter);

        // Long-press to delete
        transactionList.setOnItemLongClickListener((parent, view, position, id) -> {
            Expense expense = displayedExpenses.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Delete Transaction")
                    .setMessage("Delete " + expense.category + " — ₹" + String.format("%.2f", expense.amount) + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repo.deleteExpenseWithBalanceReverse(expense.id, walletRepo);
                        refreshAll();
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        // Back button
        findViewById(R.id.btnBackExpense).setOnClickListener(v -> finish());

        // Net Worth Dashboard
        findViewById(R.id.btnNetWorth).setOnClickListener(v ->
            startActivity(new Intent(this, NetWorthDashboardActivity.class)));

        // Set budget button
        findViewById(R.id.btnSetBudget).setOnClickListener(v -> showBudgetDialog());

        // FAB
        findViewById(R.id.fabAddExpense).setOnClickListener(v -> showAddExpenseDialog());

        // Navigation to Subscriptions, Budget Goals & Wallets
        findViewById(R.id.btnSubscriptions).setOnClickListener(v ->
            startActivity(new Intent(this, SubscriptionsActivity.class)));
        findViewById(R.id.btnBudgetGoals).setOnClickListener(v ->
            startActivity(new Intent(this, BudgetGoalsActivity.class)));
        findViewById(R.id.btnWallets).setOnClickListener(v ->
            startActivity(new Intent(this, WalletsActivity.class)));
        findViewById(R.id.btnSplitBills).setOnClickListener(v ->
            startActivity(new Intent(this, SplitGroupsActivity.class)));
        findViewById(R.id.btnExport).setOnClickListener(v ->
            new ExportService(this).showExportDialog(null));
        findViewById(R.id.btnBorrowLend).setOnClickListener(v ->
            startActivity(new Intent(this, BorrowLendActivity.class)));
        // Income Tracker
        findViewById(R.id.btnIncomeTracker).setOnClickListener(v ->
            startActivity(new Intent(this, IncomeTrackerActivity.class)));
        // IOU overdue banner
        LinearLayout iouBanner = findViewById(R.id.iouOverdueBanner);
        if (iouBanner != null) {
            iouBanner.setOnClickListener(v ->
                startActivity(new Intent(this, BorrowLendActivity.class)));
        }

        // Bar chart tap
        barChart.setOnBarSelectedListener((dayIndex, amount) -> {
            if (amount > 0) {
                String[] days = {"6 days ago", "5 days ago", "4 days ago", "3 days ago",
                        "2 days ago", "Yesterday", "Today"};
                Toast.makeText(this, days[dayIndex] + ": ₹" + String.format("%.0f", amount),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupWalletFilter() {
        walletFilterContainer.removeAllViews();
        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();
        if (wallets.size() <= 1) return; // Don't show filter if only default wallet

        // "All Wallets" chip
        TextView allChip = new TextView(this);
        allChip.setText("💰 All Wallets");
        allChip.setTextSize(11);
        allChip.setPadding(28, 12, 28, 12);
        boolean allSelected = currentWalletFilter == null;
        allChip.setTextColor(allSelected ? Color.WHITE : 0xFF9CA3AF);
        allChip.setBackgroundResource(allSelected ?
            R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);

        LinearLayout.LayoutParams allLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        allLP.setMarginEnd(8);
        allChip.setLayoutParams(allLP);

        allChip.setOnClickListener(v -> {
            currentWalletFilter = null;
            setupWalletFilter();
            refreshAll();
        });
        walletFilterContainer.addView(allChip);

        // Individual wallet chips
        for (Wallet w : wallets) {
            TextView chip = new TextView(this);
            chip.setText(w.getTypeIcon() + " " + w.name);
            chip.setTextSize(11);
            chip.setPadding(28, 12, 28, 12);
            boolean selected = w.id.equals(currentWalletFilter);
            chip.setTextColor(selected ? Color.WHITE : 0xFF9CA3AF);
            chip.setBackgroundResource(selected ?
                R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                currentWalletFilter = w.id;
                setupWalletFilter();
                refreshAll();
            });
            walletFilterContainer.addView(chip);
        }
    }

    private void setupFilterChips() {
        String[] filters = {"All", "Today", "This Week", "This Month",
                "Food", "Transport", "Shopping", "Bills", "Entertainment"};

        filterChipContainer.removeAllViews();
        for (String filter : filters) {
            TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(12);
            chip.setPadding(32, 16, 32, 16);
            chip.setTextColor(filter.equals(currentFilter) ? Color.WHITE : 0xFF9CA3AF);
            chip.setBackgroundResource(filter.equals(currentFilter) ?
                    R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(8);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                currentFilter = filter;
                setupFilterChips();
                refreshTransactions();
            });

            filterChipContainer.addView(chip);
        }
    }

    private void refreshAll() {
        refreshSummary();
        refreshCharts();
        refreshInsights();
        refreshTransactions();
    }

    private void refreshSummary() {
        double today, yesterday, week, lastWeek, monthSpend;
        if (currentWalletFilter != null) {
            today = repo.getTodaySpendForWallet(currentWalletFilter);
            long yesterdayMs = System.currentTimeMillis() - 86400000L;
            yesterday = repo.getSpendForDayForWallet(yesterdayMs, currentWalletFilter);
            week = repo.getWeekSpendForWallet(currentWalletFilter);
            lastWeek = repo.getLastWeekSpend(); // no wallet variant — use global
            monthSpend = repo.getMonthSpendForWallet(currentWalletFilter);
        } else {
            today = repo.getTodaySpend();
            yesterday = repo.getYesterdaySpend();
            week = repo.getWeekSpend();
            lastWeek = repo.getLastWeekSpend();
            monthSpend = repo.getMonthSpend();
        }
        double budget = repo.getMonthlyBudget();

        tvTodaySpend.setText("₹" + formatAmount(today));
        tvYesterdayCompare.setText("vs ₹" + formatAmount(yesterday) + " yesterday");

        tvWeekSpend.setText("₹" + formatAmount(week));
        tvLastWeekSpend.setText("₹" + formatAmount(lastWeek));

        // Week change indicator
        if (lastWeek > 0) {
            double pctChange = ((week - lastWeek) / lastWeek) * 100;
            if (pctChange > 0) {
                tvWeekChange.setText(String.format("↑%.0f%%", pctChange));
                tvWeekChange.setTextColor(0xFFEF4444);
            } else if (pctChange < 0) {
                tvWeekChange.setText(String.format("↓%.0f%%", Math.abs(pctChange)));
                tvWeekChange.setTextColor(0xFF22C55E);
            } else {
                tvWeekChange.setText("→ 0%");
                tvWeekChange.setTextColor(0xFF6B7B8D);
            }
        } else {
            tvWeekChange.setText("");
        }

        // Budget
        if (budget > 0) {
            budgetSection.setVisibility(View.VISIBLE);
            tvBudgetLabel.setText("₹" + formatAmount(monthSpend) + " / ₹" + formatAmount(budget));

            float pct = (float) (monthSpend / budget);
            pct = Math.min(pct, 1f);
            final float pct2=pct;

            // Animate progress bar width
            budgetProgressBar.post(() -> {
                int parentWidth = ((ViewGroup) budgetProgressBar.getParent()).getWidth();
                ViewGroup.LayoutParams lp = budgetProgressBar.getLayoutParams();
                lp.width = (int) (parentWidth * pct2);
                budgetProgressBar.setLayoutParams(lp);

                if (monthSpend >= budget) {
                    budgetProgressBar.setBackgroundColor(0xFFEF4444);
                    tvBudgetStatus.setTextColor(0xFFEF4444);
                    tvBudgetStatus.setText("⚠️ Budget exceeded!");
                } else if (monthSpend >= budget * 0.8) {
                    budgetProgressBar.setBackgroundColor(0xFFF59E0B);
                    tvBudgetStatus.setTextColor(0xFFF59E0B);
                    tvBudgetStatus.setText("⚡ Approaching budget limit");
                } else {
                    budgetProgressBar.setBackgroundColor(0xFF22C55E);
                    tvBudgetStatus.setTextColor(0xFF22C55E);
                    tvBudgetStatus.setText("✅ Within budget — ₹" + formatAmount(budget - monthSpend) + " remaining");
                }
            });
        } else {
            budgetSection.setVisibility(View.GONE);
        }
    }

    private void refreshCharts() {
        if (currentWalletFilter != null) {
            barChart.setData(repo.getLast7DaysSpendForWallet(currentWalletFilter));
            donutChart.setData(repo.getCategoryBreakdownForWallet(currentWalletFilter));
            lineChart.setData(repo.getMonthlySpendHistoryForWallet(currentWalletFilter, 6));
        } else {
            barChart.setData(repo.getLast7DaysSpend());
            donutChart.setData(repo.getCategoryBreakdown());
            lineChart.setData(repo.getMonthlySpendHistory(6));
        }
    }

    private void refreshInsights() {
        double week = repo.getWeekSpend();
        double lastWeek = repo.getLastWeekSpend();
        String topCategory = repo.getTopCategoryThisMonth();
        Expense biggest = repo.getBiggestThisWeek();

        // Insight 1: Spending comparison
        if (lastWeek > 0) {
            double pct = ((week - lastWeek) / lastWeek) * 100;
            if (pct > 0) {
                tvInsight1.setText("📈 You spent " + String.format("%.0f%%", pct) + " more than last week");
                tvInsight1.setTextColor(0xFFFCA5A5);
            } else {
                tvInsight1.setText("📉 You spent " + String.format("%.0f%%", Math.abs(pct)) + " less than last week — great job!");
                tvInsight1.setTextColor(0xFF86EFAC);
            }
        } else if (week > 0) {
            tvInsight1.setText("📊 You've spent ₹" + formatAmount(week) + " this week");
            tvInsight1.setTextColor(0xFFE0E7FF);
        } else {
            tvInsight1.setText("💡 Start tracking your expenses to see insights here");
            tvInsight1.setTextColor(0xFF6B7B8D);
        }

        // Insight 2: Top category
        if (topCategory != null) {
            tvInsight2.setText("🏆 Top spending category this month: " +
                    Expense.getCategoryIcon(topCategory) + " " + topCategory);
        } else {
            tvInsight2.setText("");
        }

        // Insight 3: Biggest transaction
        if (biggest != null) {
            tvInsight3.setText("💸 Biggest expense this week: ₹" +
                    String.format("%.0f", biggest.amount) + " on " + biggest.category +
                    (TextUtils.isEmpty(biggest.note) ? "" : " (" + biggest.note + ")"));
        } else {
            tvInsight3.setText("");
        }
    }

    private void refreshTransactions() {
        displayedExpenses.clear();
        ArrayList<Expense> all = repo.getFilteredExpenses(currentFilter);
        if (currentWalletFilter != null) {
            for (Expense e : all) {
                if (currentWalletFilter.equals(e.walletId)) {
                    displayedExpenses.add(e);
                }
            }
        } else {
            displayedExpenses.addAll(all);
        }
        adapter.notifyDataSetChanged();

        // Fix ListView height inside ScrollView
        setListViewHeightBasedOnChildren(transactionList);

        tvEmptyTransactions.setVisibility(displayedExpenses.isEmpty() ? View.VISIBLE : View.GONE);
        transactionList.setVisibility(displayedExpenses.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ─── Add Expense Dialog ──────────────────────────────────

    private void showAddExpenseDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etAmount = dialogView.findViewById(R.id.etExpenseAmount);
        EditText etNote = dialogView.findViewById(R.id.etExpenseNote);
        CheckBox cbIncome = dialogView.findViewById(R.id.cbIsIncome);
        LinearLayout row1 = dialogView.findViewById(R.id.categoryRow1);
        LinearLayout row2 = dialogView.findViewById(R.id.categoryRow2);
        LinearLayout walletChipContainer = dialogView.findViewById(R.id.walletChipContainer);

        final String[] selectedCategory = {Expense.CATEGORIES[0]};

        // Build wallet selector chips
        ArrayList<Wallet> activeWallets = walletRepo.getActiveWallets();
        Wallet defaultWallet = walletRepo.getDefaultWallet();
        final String[] selectedWalletId = {defaultWallet != null ? defaultWallet.id : Wallet.DEFAULT_WALLET_ID};

        for (int w = 0; w < activeWallets.size(); w++) {
            Wallet wt = activeWallets.get(w);
            final int wIdx = w;
            TextView chip = new TextView(this);
            chip.setText(wt.getTypeIcon() + " " + wt.name);
            chip.setTextSize(11);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(24, 12, 24, 12);
            boolean isSelected = wt.id.equals(selectedWalletId[0]);
            chip.setBackgroundResource(isSelected ?
                R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);

            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wlp.setMarginEnd(8);
            chip.setLayoutParams(wlp);

            chip.setOnClickListener(v -> {
                selectedWalletId[0] = activeWallets.get(wIdx).id;
                // Update wallet chips selection
                for (int c = 0; c < walletChipContainer.getChildCount(); c++) {
                    walletChipContainer.getChildAt(c).setBackgroundResource(
                        c == wIdx ? R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
                }
            });
            walletChipContainer.addView(chip);
        }

        // Build category chips
        for (int i = 0; i < Expense.CATEGORIES.length; i++) {
            LinearLayout row = i < 5 ? row1 : row2;
            final int idx = i;

            TextView chip = new TextView(this);
            chip.setText(Expense.CATEGORY_ICONS[i] + " " + Expense.CATEGORIES[i]);
            chip.setTextSize(11);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(20, 12, 20, 12);
            chip.setBackgroundResource(idx == 0 ?
                    R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginEnd(6);
            chip.setLayoutParams(params);
            chip.setGravity(android.view.Gravity.CENTER);

            chip.setOnClickListener(v -> {
                selectedCategory[0] = Expense.CATEGORIES[idx];
                // Update all chips
                updateCategoryChips(row1, row2, idx);
            });

            row.addView(chip);
        }

        // Save button
        dialogView.findViewById(R.id.btnSaveExpense).setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                etAmount.setError("Enter amount");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                etAmount.setError("Invalid amount");
                return;
            }
            if (amount <= 0) {
                etAmount.setError("Amount must be positive");
                return;
            }

            String note = etNote.getText().toString().trim();
            boolean isIncome = cbIncome.isChecked();

            Expense expense = new Expense(amount, selectedCategory[0], note, isIncome, selectedWalletId[0]);
            repo.addExpenseWithBalance(expense, walletRepo);

            // Check budget alerts for this category
            if (!isIncome) {
                CategoryBudgetRepository budgetRepo = new CategoryBudgetRepository(this);
                ArrayList<CategoryBudget> alerts = budgetRepo.checkBudgetAlerts(repo);
                for (CategoryBudget alert : alerts) {
                    String msg = alert.id.endsWith("_exceeded")
                        ? "⚠️ Budget exceeded for " + alert.categoryId + "!"
                        : "⚠️ " + alert.categoryId + " budget nearing limit";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            }

            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etAmount.getWindowToken(), 0);

            dialog.dismiss();
            refreshAll();

            String msg = isIncome ?
                    "💰 Income: ₹" + String.format("%.0f", amount) :
                    "✅ Expense: ₹" + String.format("%.0f", amount) + " — " + selectedCategory[0];
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        dialog.show();

        // Auto-focus amount field
        etAmount.requestFocus();
        etAmount.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etAmount, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void updateCategoryChips(LinearLayout row1, LinearLayout row2, int selectedIdx) {
        for (int i = 0; i < 5; i++) {
            if (i < row1.getChildCount()) {
                row1.getChildAt(i).setBackgroundResource(
                        i == selectedIdx ? R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            }
        }
        for (int i = 0; i < 5; i++) {
            if (i < row2.getChildCount()) {
                row2.getChildAt(i).setBackgroundResource(
                        (i + 5) == selectedIdx ? R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            }
        }
    }

    // ─── Budget Dialog ───────────────────────────────────────

    private void showBudgetDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Monthly budget (₹)");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF6B7B8D);
        input.setPadding(40, 30, 40, 30);

        double currentBudget = repo.getMonthlyBudget();
        if (currentBudget > 0) input.setText(String.format("%.0f", currentBudget));

        new AlertDialog.Builder(this)
                .setTitle("Set Monthly Budget")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        try {
                            repo.setMonthlyBudget(Double.parseDouble(val));
                            refreshSummary();
                            Toast.makeText(this, "Budget set!", Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Clear", (d, w) -> {
                    repo.setMonthlyBudget(0);
                    refreshSummary();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Transaction Adapter ─────────────────────────────────

    private static class TransactionAdapter extends ArrayAdapter<Expense> {
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault());

        TransactionAdapter(Context context, ArrayList<Expense> expenses) {
            super(context, R.layout.item_transaction, expenses);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_transaction, parent, false);
            }

            Expense e = getItem(position);
            if (e == null) return convertView;

            TextView icon = convertView.findViewById(R.id.tvTransIcon);
            TextView category = convertView.findViewById(R.id.tvTransCategory);
            TextView note = convertView.findViewById(R.id.tvTransNote);
            TextView amount = convertView.findViewById(R.id.tvTransAmount);
            TextView time = convertView.findViewById(R.id.tvTransTime);

            icon.setText(Expense.getCategoryIcon(e.category));
            category.setText(e.category);
            note.setText(TextUtils.isEmpty(e.note) ? "—" : e.note);
            time.setText(timeFmt.format(new Date(e.timestamp)));

            if (e.isIncome) {
                amount.setText("+₹" + String.format("%.2f", e.amount));
                amount.setTextColor(0xFF22C55E);
            } else {
                amount.setText("-₹" + String.format("%.2f", e.amount));
                amount.setTextColor(0xFFEF4444);
            }

            return convertView;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }

    /** Fix ListView height when inside ScrollView */
    private static void setListViewHeightBasedOnChildren(ListView listView) {
        ArrayAdapter adapter = (ArrayAdapter) listView.getAdapter();
        if (adapter == null) return;

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
