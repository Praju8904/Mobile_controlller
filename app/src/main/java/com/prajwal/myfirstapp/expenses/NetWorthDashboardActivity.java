package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.ExportService;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NetWorthDashboardActivity extends AppCompatActivity {

    private NetWorthCalculationService calc;
    private NetWorthRepository netWorthRepo;
    private WalletRepository walletRepo;
    private ExpenseRepository expenseRepo;
    private IncomeRepository incomeRepo;

    // Hero card views
    private TextView tvNetWorthAmount, tvMonthChange, tvMonthChangePct;
    private TextView tvAssetsBreakdown, tvLiabilitiesBreakdown;
    private LinearLayout heroBreakdown;
    private boolean heroExpanded = false;

    // Health ring
    private FinancialHealthRingView healthRing;
    private LinearLayout healthBreakdownContainer;

    // Assets vs liabilities
    private TextView tvTotalAssets, tvTotalLiabilities;
    private View assetsBarSection, liabilitiesBarSection;

    // Charts
    private NetWorthLineChartView netWorthChart;
    private NetWorthBarChartView incomeExpenseChart;
    private CashFlowMiniBarView cashFlowMiniBar;
    private SavingsGaugeView savingsGauge;

    // Cash flow
    private TextView tvCashFlowIncome, tvCashFlowExpenses, tvNetCashFlow, tvSavingsBenchmark;

    // IOU
    private TextView tvOwedToYou, tvYouOwe, tvNetIou, tvIouOverdueBadge;

    // Wallet list
    private LinearLayout walletListContainer;
    private TextView tvTotalWallets;

    // Top categories
    private LinearLayout topCategoriesContainer;

    // Top income sources
    private LinearLayout topIncomeSrcContainer;

    // Insights
    private LinearLayout insightsContainer;

    // Income/expense summary text
    private TextView tvIncomeExpenseSummary;

    // Period selector
    private int selectedPeriodDays = 30;

    // Balance visibility
    private boolean balanceHidden = false;
    private TextView tvLastUpdated;

    // Loading overlay
    private View loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_net_worth_dashboard);

        calc          = new NetWorthCalculationService(this);
        netWorthRepo  = new NetWorthRepository(this);
        walletRepo    = new WalletRepository(this);
        expenseRepo   = new ExpenseRepository(this);
        incomeRepo    = new IncomeRepository(this);

        balanceHidden = calc.isBalanceHidden();

        // Take daily snapshot on open
        netWorthRepo.takeDailySnapshot(calc);

        initViews();
        setupClickListeners();
        loadDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboard();
    }

    // ─── View Init ────────────────────────────────────────────

    private void initViews() {
        tvNetWorthAmount      = findViewById(R.id.tvNetWorthAmount);
        tvMonthChange         = findViewById(R.id.tvMonthChange);
        tvMonthChangePct      = findViewById(R.id.tvMonthChangePct);
        tvAssetsBreakdown     = findViewById(R.id.tvAssetsBreakdown);
        tvLiabilitiesBreakdown = findViewById(R.id.tvLiabilitiesBreakdown);
        heroBreakdown         = findViewById(R.id.heroBreakdown);
        tvLastUpdated         = findViewById(R.id.tvLastUpdated);

        healthRing                = findViewById(R.id.healthRing);
        healthBreakdownContainer  = findViewById(R.id.healthBreakdownContainer);

        tvTotalAssets       = findViewById(R.id.tvTotalAssets);
        tvTotalLiabilities  = findViewById(R.id.tvTotalLiabilities);
        assetsBarSection    = findViewById(R.id.assetsBarSection);
        liabilitiesBarSection = findViewById(R.id.liabilitiesBarSection);

        netWorthChart       = findViewById(R.id.netWorthChart);
        incomeExpenseChart  = findViewById(R.id.incomeExpenseChart);
        cashFlowMiniBar     = findViewById(R.id.cashFlowMiniBar);
        savingsGauge        = findViewById(R.id.savingsGauge);

        tvCashFlowIncome    = findViewById(R.id.tvCashFlowIncome);
        tvCashFlowExpenses  = findViewById(R.id.tvCashFlowExpenses);
        tvNetCashFlow       = findViewById(R.id.tvNetCashFlow);
        tvSavingsBenchmark  = findViewById(R.id.tvSavingsBenchmark);

        tvOwedToYou         = findViewById(R.id.tvOwedToYou);
        tvYouOwe            = findViewById(R.id.tvYouOwe);
        tvNetIou            = findViewById(R.id.tvNetIou);
        tvIouOverdueBadge   = findViewById(R.id.tvIouOverdueBadge);

        walletListContainer = findViewById(R.id.walletListContainer);
        tvTotalWallets      = findViewById(R.id.tvTotalWallets);

        topCategoriesContainer  = findViewById(R.id.topCategoriesContainer);
        topIncomeSrcContainer   = findViewById(R.id.topIncomeSrcContainer);
        insightsContainer       = findViewById(R.id.insightsContainer);
        tvIncomeExpenseSummary  = findViewById(R.id.tvIncomeExpenseSummary);

        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    // ─── Click Listeners ─────────────────────────────────────

    private void setupClickListeners() {
        // Back
        findViewById(R.id.btnBackDashboard).setOnClickListener(v -> finish());

        // Toggle balance visibility
        Button toggleBtn = findViewById(R.id.btnToggleBalance);
        toggleBtn.setOnClickListener(v -> {
            balanceHidden = !balanceHidden;
            calc.setBalanceHidden(balanceHidden);
            toggleBtn.setText(balanceHidden ? "🙈" : "👁");
            loadDashboard();
        });

        // Share report
        findViewById(R.id.btnShareReport).setOnClickListener(v ->
                new ExportService(this).showExportDialog(null));

        // Hero card expand/collapse
        findViewById(R.id.heroCard).setOnClickListener(v -> {
            heroExpanded = !heroExpanded;
            heroBreakdown.setVisibility(heroExpanded ? View.VISIBLE : View.GONE);
        });

        // Period selector
        setupPeriodSelector();

        // Quick actions
        findViewById(R.id.btnQuickAddIncome).setOnClickListener(v ->
                startActivity(new Intent(this, IncomeTrackerActivity.class)));
        findViewById(R.id.btnQuickAddExpense).setOnClickListener(v ->
                startActivity(new Intent(this, ExpenseTrackerActivity.class)));
        findViewById(R.id.btnQuickTransfer).setOnClickListener(v ->
                startActivity(new Intent(this, WalletsActivity.class)));
        findViewById(R.id.btnQuickAddIou).setOnClickListener(v ->
                startActivity(new Intent(this, BorrowLendActivity.class)));

        // IOU details
        Button btnViewIous = findViewById(R.id.btnViewIous);
        if (btnViewIous != null) {
            btnViewIous.setOnClickListener(v ->
                    startActivity(new Intent(this, BorrowLendActivity.class)));
        }

        // Assets / liabilities drill-down
        LinearLayout cardAssets = findViewById(R.id.cardAssets);
        if (cardAssets != null) {
            cardAssets.setOnClickListener(v ->
                    startActivity(new Intent(this, WalletsActivity.class)));
        }
        LinearLayout cardLiabilities = findViewById(R.id.cardLiabilities);
        if (cardLiabilities != null) {
            cardLiabilities.setOnClickListener(v ->
                    startActivity(new Intent(this, BorrowLendActivity.class)));
        }

        // Manage wallets
        Button btnManageWallets = findViewById(R.id.btnManageWallets);
        if (btnManageWallets != null) {
            btnManageWallets.setOnClickListener(v ->
                    startActivity(new Intent(this, WalletsActivity.class)));
        }

        // View all categories
        Button btnViewAll = findViewById(R.id.btnViewAllCategories);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v ->
                    startActivity(new Intent(this, ExpenseTrackerActivity.class)));
        }

        // Refresh is handled via onResume when returning from other screens
    }

    private void setupPeriodSelector() {
        TextView p1m = findViewById(R.id.period1M);
        TextView p3m = findViewById(R.id.period3M);
        TextView p6m = findViewById(R.id.period6M);
        TextView p1y = findViewById(R.id.period1Y);

        View.OnClickListener periodClick = v -> {
            int days;
            if (v.getId() == R.id.period1M)      days = 30;
            else if (v.getId() == R.id.period3M) days = 90;
            else if (v.getId() == R.id.period6M) days = 180;
            else                                  days = 365;

            selectedPeriodDays = days;
            updatePeriodSelector(days);
            updateTrendChart();
        };

        if (p1m != null) p1m.setOnClickListener(periodClick);
        if (p3m != null) p3m.setOnClickListener(periodClick);
        if (p6m != null) p6m.setOnClickListener(periodClick);
        if (p1y != null) p1y.setOnClickListener(periodClick);
    }

    private void updatePeriodSelector(int days) {
        int[][] ids = {
                {R.id.period1M, 30},
                {R.id.period3M, 90},
                {R.id.period6M, 180},
                {R.id.period1Y, 365}
        };
        for (int[] pair : ids) {
            TextView tv = findViewById(pair[0]);
            if (tv == null) continue;
            boolean selected = days == pair[1];
            tv.setTextColor(selected ? Color.WHITE : 0xFF9CA3AF);
            tv.setBackgroundResource(selected ?
                    R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
        }
    }

    // ─── Dashboard Load ───────────────────────────────────────

    private void loadDashboard() {
        tvLastUpdated.setText("Updated " + new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new Date()));

        updateHeroCard();
        updateHealthScore();
        updateAssetsLiabilities();
        updateTrendChart();
        updateCashFlow();
        updateSavingsRate();
        updateIncomeExpenseChart();
        updateIouSummary();
        updateWalletList();
        updateTopCategories();
        updateTopIncomeSources();
        updateInsights();
    }

    // ─── Hero Card ────────────────────────────────────────────

    private void updateHeroCard() {
        double netWorth    = calc.getNetWorth();
        double assets      = calc.getTotalAssets();
        double liabilities = calc.getTotalLiabilities();

        if (balanceHidden) {
            tvNetWorthAmount.setText("₹ ••••••");
            tvMonthChange.setText("••••");
            tvMonthChangePct.setText("");
            tvAssetsBreakdown.setText("₹ ••••");
            tvLiabilitiesBreakdown.setText("₹ ••••");
            tvTotalAssets.setText("₹ ••••");
            tvTotalLiabilities.setText("₹ ••••");
        } else {
            // Count-up animation for net worth
            animateCountUp(tvNetWorthAmount, netWorth, "₹");

            // Month-over-month change
            Double lastMonth = netWorthRepo.getNetWorthLastMonth();
            if (lastMonth != null) {
                double delta = netWorth - lastMonth;
                double pct   = lastMonth != 0 ? (delta / Math.abs(lastMonth)) * 100 : 0;
                boolean up   = delta >= 0;
                String arrow = up ? "▲" : "▼";
                tvMonthChange.setText(arrow + " ₹" + formatAmount(Math.abs(delta)) + " from last month");
                tvMonthChange.setTextColor(up ? 0xFF4ADE80 : 0xFFF87171);
                tvMonthChangePct.setText(String.format("(%+.1f%%)", pct));
                tvMonthChangePct.setTextColor(up ? 0xFF4ADE80 : 0xFFF87171);
            } else {
                tvMonthChange.setText("No prior data yet");
                tvMonthChange.setTextColor(0xFF9CA3AF);
                tvMonthChangePct.setText("");
            }

            tvAssetsBreakdown.setText("₹" + formatAmount(assets));
            tvLiabilitiesBreakdown.setText("₹" + formatAmount(liabilities));
            tvTotalAssets.setText("₹" + formatAmount(assets));
            tvTotalLiabilities.setText("₹" + formatAmount(liabilities));
        }
    }

    private void animateCountUp(TextView tv, double target, String prefix) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, (float) target);
        anim.setDuration(1000);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            tv.setText(prefix + formatAmount(val));
        });
        anim.start();
    }

    // ─── Health Score ─────────────────────────────────────────

    private void updateHealthScore() {
        int score = calc.getFinancialHealthScore();
        healthRing.setScore(score);

        healthBreakdownContainer.removeAllViews();
        int[] breakdown = calc.getHealthScoreBreakdown();
        String[] labels = {"Savings Rate", "Budget Adherence", "Emergency Fund",
                "Debt Ratio", "Consistent Income"};
        int[] maxScores = {25, 20, 20, 20, 15};
        String[] tips   = {
                "Save ≥20% of income for full score",
                "Stay within all category budgets",
                "Keep 3 months of expenses as reserve",
                "Keep liabilities <10% of assets",
                "Log income for 3 consecutive months"
        };

        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLP.setMargins(0, 0, 0, 6);
            row.setLayoutParams(rowLP);

            TextView tvLabel = new TextView(this);
            tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvLabel.setText(labels[i]);
            tvLabel.setTextSize(11f);
            tvLabel.setTextColor(0xFFD1D5DB);

            TextView tvScore = new TextView(this);
            tvScore.setTextSize(11f);
            tvScore.setText(breakdown[i] + "/" + maxScores[i]);
            int color = breakdown[i] >= maxScores[i] ? 0xFF22C55E :
                       (breakdown[i] >= maxScores[i] / 2 ? 0xFFF59E0B : 0xFFEF4444);
            tvScore.setTextColor(color);
            tvScore.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(tvLabel);
            row.addView(tvScore);
            healthBreakdownContainer.addView(row);

            // Tip if not full score
            if (breakdown[i] < maxScores[i]) {
                TextView tvTip = new TextView(this);
                LinearLayout.LayoutParams tipLP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                tipLP.setMargins(0, 0, 0, 8);
                tvTip.setLayoutParams(tipLP);
                tvTip.setText("  → " + tips[i]);
                tvTip.setTextSize(10f);
                tvTip.setTextColor(0xFF6B7B8D);
                healthBreakdownContainer.addView(tvTip);
            }
        }
    }

    // ─── Assets / Liabilities Bar ─────────────────────────────

    private void updateAssetsLiabilities() {
        double assets      = balanceHidden ? 0 : calc.getTotalAssets();
        double liabilities = balanceHidden ? 0 : calc.getTotalLiabilities();
        double total       = assets + liabilities;

        if (total <= 0) {
            setBarWeight(assetsBarSection, 1f);
            setBarWeight(liabilitiesBarSection, 0f);
            return;
        }

        float assetWeight = (float) (assets / total);
        float liabWeight  = 1f - assetWeight;

        // Animate proportional bar
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(800);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            setBarWeight(assetsBarSection, assetWeight * p + (1f - p));
            setBarWeight(liabilitiesBarSection, liabWeight * p);
        });
        anim.start();

        if (!balanceHidden) {
            tvTotalAssets.setText("₹" + formatAmount(assets));
            tvTotalLiabilities.setText("₹" + formatAmount(liabilities));
        }
    }

    private void setBarWeight(View v, float weight) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = weight;
        v.setLayoutParams(lp);
    }

    // ─── Net Worth Trend Chart ────────────────────────────────

    private void updateTrendChart() {
        double[] trend = netWorthRepo.getDailyTrend(selectedPeriodDays);
        String[] labels = buildDateLabels(selectedPeriodDays);
        if (!balanceHidden) {
            netWorthChart.setData(trend, labels);
        } else {
            netWorthChart.setData(new double[0], new String[0]);
        }
    }

    private String[] buildDateLabels(int days) {
        String[] labels = new String[days];
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < days; i++) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -(days - 1 - i));
            labels[i] = fmt.format(c.getTime());
        }
        return labels;
    }

    // ─── Cash Flow ────────────────────────────────────────────

    private void updateCashFlow() {
        double income   = calc.getIncomeThisMonth();
        double expenses = calc.getExpensesThisMonth();
        double net      = income - expenses;

        if (balanceHidden) {
            tvCashFlowIncome.setText("₹ ••••");
            tvCashFlowExpenses.setText("₹ ••••");
            tvNetCashFlow.setText("₹ ••••");
        } else {
            tvCashFlowIncome.setText("₹" + formatAmount(income));
            tvCashFlowExpenses.setText("₹" + formatAmount(expenses));
            animateCountUp(tvNetCashFlow, Math.abs(net), net >= 0 ? "+₹" : "-₹");
            tvNetCashFlow.setTextColor(net >= 0 ? 0xFF4ADE80 : 0xFFF87171);
        }

        double[] daily = calc.getDailyCashFlowThisMonth();
        cashFlowMiniBar.setData(daily);
    }

    // ─── Savings Rate ─────────────────────────────────────────

    private void updateSavingsRate() {
        double rate = calc.getSavingsRateThisMonth();
        savingsGauge.setSavingsRate(rate);

        if (rate >= 20) {
            tvSavingsBenchmark.setText("🎉 Great! Your savings rate (" +
                    String.format("%.1f", rate) + "%) is above the recommended 20%.");
            tvSavingsBenchmark.setTextColor(0xFF4ADE80);
        } else if (rate > 0) {
            double incomeThisMonth = calc.getIncomeThisMonth();
            double gap = calc.getExpensesThisMonth() - incomeThisMonth * 0.8;
            tvSavingsBenchmark.setText(String.format(
                    "💡 Save ₹%.0f more to hit the 20%% savings target.", Math.max(0, gap)));
            tvSavingsBenchmark.setTextColor(0xFFF59E0B);
        } else {
            tvSavingsBenchmark.setText("💡 Financial advisors recommend saving at least 20% of income.");
            tvSavingsBenchmark.setTextColor(0xFF9CA3AF);
        }
    }

    // ─── Income vs Expense 6-Month Chart ─────────────────────

    private void updateIncomeExpenseChart() {
        int months = 6;
        double[] income   = calc.getMonthlyIncome(months);
        double[] expenses = calc.getMonthlyExpenses(months);
        String[] labels   = buildMonthLabels(months);

        incomeExpenseChart.setData(income, expenses, labels);

        // Summary line
        if (!balanceHidden) {
            double totalIncome   = sum(income);
            double totalExpenses = sum(expenses);
            double totalSavings  = totalIncome - totalExpenses;
            double savingsPct    = totalIncome > 0 ? totalSavings / totalIncome * 100 : 0;
            tvIncomeExpenseSummary.setText(String.format(
                    "Over 6 months: earned ₹%s, spent ₹%s, saved ₹%s (%.0f%%)",
                    formatAmount(totalIncome), formatAmount(totalExpenses),
                    formatAmount(totalSavings), savingsPct));
        } else {
            tvIncomeExpenseSummary.setText("Balance hidden");
        }
    }

    private String[] buildMonthLabels(int months) {
        String[] labels = new String[months];
        SimpleDateFormat fmt = new SimpleDateFormat("MMM", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < months; i++) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, -(months - 1 - i));
            labels[i] = fmt.format(c.getTime());
        }
        return labels;
    }

    // ─── IOU Summary ─────────────────────────────────────────

    private void updateIouSummary() {
        double owedToUser = calc.getMoneyOwedToUser();
        double userOwes   = calc.getMoneyUserOwes();
        double net        = owedToUser - userOwes;
        int    overdue    = calc.getOverdueIouCount();

        if (balanceHidden) {
            tvOwedToYou.setText("₹ ••••");
            tvYouOwe.setText("₹ ••••");
            tvNetIou.setText("₹ ••••");
        } else {
            tvOwedToYou.setText("₹" + formatAmount(owedToUser));
            tvYouOwe.setText("₹" + formatAmount(userOwes));
            tvNetIou.setText((net >= 0 ? "+" : "") + "₹" + formatAmount(net));
            tvNetIou.setTextColor(net >= 0 ? 0xFF4ADE80 : 0xFFF87171);
        }

        if (overdue > 0) {
            tvIouOverdueBadge.setVisibility(View.VISIBLE);
            tvIouOverdueBadge.setText(overdue + " overdue");
        } else {
            tvIouOverdueBadge.setVisibility(View.GONE);
        }
    }

    // ─── Wallet List ──────────────────────────────────────────

    private void updateWalletList() {
        walletListContainer.removeAllViews();
        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();

        double total = 0;
        for (Wallet w : wallets) {
            if (!w.includeInTotalBalance) continue;
            total += w.isCreditCard() ? -w.currentBalance : w.currentBalance;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 6, 0, 6);

            TextView tvIcon = new TextView(this);
            tvIcon.setText(w.getTypeIcon());
            tvIcon.setTextSize(18f);
            LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconLP.setMarginEnd(12);
            tvIcon.setLayoutParams(iconLP);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(w.name);
            tvName.setTextSize(13f);
            tvName.setTextColor(0xFFE5E7EB);

            TextView tvBalance = new TextView(this);
            tvBalance.setTextSize(13f);
            tvBalance.setTypeface(null, android.graphics.Typeface.BOLD);
            if (balanceHidden) {
                tvBalance.setText("₹ ••••");
                tvBalance.setTextColor(0xFF9CA3AF);
            } else {
                double bal = w.isCreditCard() ? -w.currentBalance : w.currentBalance;
                tvBalance.setText("₹" + formatAmount(bal));
                tvBalance.setTextColor(bal >= 0 ? 0xFF4ADE80 : 0xFFF87171);
            }

            row.addView(tvIcon);
            row.addView(tvName);
            row.addView(tvBalance);
            walletListContainer.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0x15FFFFFF);
            walletListContainer.addView(divider);
        }

        if (balanceHidden) {
            tvTotalWallets.setText("Total: ••••");
        } else {
            tvTotalWallets.setText("Total: ₹" + formatAmount(total));
        }
    }

    // ─── Top Categories ───────────────────────────────────────

    private void updateTopCategories() {
        topCategoriesContainer.removeAllViews();
        List<String> catNames  = calc.getTopCategoryNames(5);
        List<double[]> catData = calc.getTopCategoryAmounts(5);
        double totalExpenses   = calc.getExpensesThisMonth();

        if (catNames.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No expenses this month");
            tv.setTextSize(12f);
            tv.setTextColor(0xFF6B7B8D);
            topCategoriesContainer.addView(tv);
            return;
        }

        for (int i = 0; i < catNames.size(); i++) {
            String cat  = catNames.get(i);
            double amt  = catData.get(i)[0];
            double pct  = catData.get(i)[1];
            String icon = getCategoryIcon(cat);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLP.setMargins(0, 0, 0, 10);
            row.setLayoutParams(rowLP);

            // Sub-row: icon + name + amount + pct
            LinearLayout subRow = new LinearLayout(this);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            subRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            subRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView tvIcon = new TextView(this);
            tvIcon.setText(icon);
            tvIcon.setTextSize(16f);
            LinearLayout.LayoutParams iconLP2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconLP2.setMarginEnd(8);
            tvIcon.setLayoutParams(iconLP2);

            TextView tvCat = new TextView(this);
            tvCat.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvCat.setText(cat);
            tvCat.setTextSize(13f);
            tvCat.setTextColor(0xFFE5E7EB);

            TextView tvAmt = new TextView(this);
            tvAmt.setTextSize(13f);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmt.setTextColor(0xFFF87171);
            if (balanceHidden) {
                tvAmt.setText("₹ ••••");
            } else {
                tvAmt.setText("₹" + formatAmount(amt) + " (" + String.format("%.0f", pct) + "%)");
            }

            subRow.addView(tvIcon);
            subRow.addView(tvCat);
            subRow.addView(tvAmt);
            row.addView(subRow);

            // Progress bar
            if (!balanceHidden && totalExpenses > 0) {
                FrameLayout barContainer = new FrameLayout(this);
                LinearLayout.LayoutParams barContainerLP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 6);
                barContainerLP.setMargins(0, 4, 0, 0);
                barContainer.setLayoutParams(barContainerLP);
                barContainer.setBackgroundColor(0x15FFFFFF);

                LinearLayout barLL = new LinearLayout(this);
                barLL.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                barLL.setOrientation(LinearLayout.HORIZONTAL);

                View filledPart = new View(this);
                filledPart.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, (float) pct));
                filledPart.setBackgroundColor(getCategoryColor(i));

                View emptyPart = new View(this);
                emptyPart.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, (float) (100 - pct)));
                emptyPart.setBackgroundColor(0x15FFFFFF);

                barLL.addView(filledPart);
                barLL.addView(emptyPart);
                barContainer.addView(barLL);
                row.addView(barContainer);
            }

            topCategoriesContainer.addView(row);
        }
    }

    private int getCategoryColor(int index) {
        int[] colors = {0xFFF87171, 0xFFFBBF24, 0xFF60A5FA, 0xFF34D399, 0xFFA78BFA};
        return colors[index % colors.length];
    }

    // ─── Top Income Sources ───────────────────────────────────

    private void updateTopIncomeSources() {
        topIncomeSrcContainer.removeAllViews();
        Map<String, Double> sources = calc.getTopIncomeSources(3);

        if (sources.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No income this month");
            tv.setTextSize(12f);
            tv.setTextColor(0xFF6B7B8D);
            topIncomeSrcContainer.addView(tv);
            return;
        }

        double totalIncome = calc.getIncomeThisMonth();
        for (Map.Entry<String, Double> entry : sources.entrySet()) {
            String src = entry.getKey();
            double amt = entry.getValue();
            double pct = totalIncome > 0 ? (amt / totalIncome) * 100 : 0;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLP.setMargins(0, 0, 0, 10);
            row.setLayoutParams(rowLP);

            TextView tvIcon = new TextView(this);
            tvIcon.setText(getIncomeSourceIcon(src));
            tvIcon.setTextSize(16f);
            LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconLP.setMarginEnd(8);
            tvIcon.setLayoutParams(iconLP);

            TextView tvSrc = new TextView(this);
            tvSrc.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvSrc.setText(src);
            tvSrc.setTextSize(13f);
            tvSrc.setTextColor(0xFFE5E7EB);

            TextView tvAmt = new TextView(this);
            tvAmt.setTextSize(13f);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmt.setTextColor(0xFF4ADE80);
            if (balanceHidden) {
                tvAmt.setText("₹ ••••");
            } else {
                tvAmt.setText("₹" + formatAmount(amt) + " (" + String.format("%.0f", pct) + "%)");
            }

            row.addView(tvIcon);
            row.addView(tvSrc);
            row.addView(tvAmt);
            topIncomeSrcContainer.addView(row);
        }
    }

    // ─── Insights ─────────────────────────────────────────────

    private void updateInsights() {
        insightsContainer.removeAllViews();
        List<String[]> insights = calc.generateInsights();

        if (insights.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Add more financial data to see personalised insights.");
            tv.setTextSize(12f);
            tv.setTextColor(0xFF6B7B8D);
            insightsContainer.addView(tv);
            return;
        }

        for (String[] insight : insights) {
            // insight[0] = emoji, insight[1] = headline, insight[2] = detail
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setBackgroundColor(0x0AFFFFFF);
            card.setPadding(16, 14, 16, 14);
            LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLP.setMargins(0, 0, 0, 8);
            card.setLayoutParams(cardLP);

            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(insight.length > 0 ? insight[0] : "💡");
            tvEmoji.setTextSize(22f);
            LinearLayout.LayoutParams emojiLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            emojiLP.setMarginEnd(12);
            tvEmoji.setLayoutParams(emojiLP);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvHead = new TextView(this);
            tvHead.setText(insight.length > 1 ? insight[1] : "");
            tvHead.setTextSize(13f);
            tvHead.setTextColor(0xFFFFFFFF);
            tvHead.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvDetail = new TextView(this);
            tvDetail.setText(insight.length > 2 ? insight[2] : "");
            tvDetail.setTextSize(11f);
            tvDetail.setTextColor(0xFF9CA3AF);

            textCol.addView(tvHead);
            textCol.addView(tvDetail);
            card.addView(tvEmoji);
            card.addView(textCol);
            insightsContainer.addView(card);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private String formatAmount(double amount) {
        boolean negative = amount < 0;
        double abs = Math.abs(amount);
        String formatted;
        if (abs >= 10_000_000)    formatted = String.format("%.1fCr", abs / 10_000_000);
        else if (abs >= 100_000)  formatted = String.format("%.1fL",  abs / 100_000);
        else if (abs >= 1_000)    formatted = String.format("%.1fk",  abs / 1_000);
        else                      formatted = String.format("%.0f",   abs);
        return negative ? "-" + formatted : formatted;
    }

    private double sum(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s;
    }

    private String getCategoryIcon(String category) {
        for (int i = 0; i < Expense.CATEGORIES.length; i++) {
            if (Expense.CATEGORIES[i].equals(category)) {
                return Expense.CATEGORY_ICONS[i];
            }
        }
        return "💰";
    }

    private String getIncomeSourceIcon(String source) {
        switch (source) {
            case "Salary":             return "💼";
            case "Freelance":          return "🖥️";
            case "Business":           return "🏢";
            case "Investment":
            case "Investment Returns": return "📈";
            case "Rental":             return "🏠";
            case "Gift":               return "🎁";
            case "Bonus":              return "🏆";
            case "Refund":             return "↩️";
            case "Scholarship":        return "🎓";
            case "Pocket Money":       return "👛";
            default:                   return "💵";
        }
    }
}
