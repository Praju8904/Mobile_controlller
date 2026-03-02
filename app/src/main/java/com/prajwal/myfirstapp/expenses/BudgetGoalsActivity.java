package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Budget Goals per Category screen.
 * Health ring, per-category budget cards with animated progress,
 * categories without budget, comparison chart, and history.
 */
public class BudgetGoalsActivity extends AppCompatActivity {

    private CategoryBudgetRepository budgetRepo;
    private ExpenseRepository expenseRepo;
    private RecurringExpenseRepository recurringRepo;

    // Views
    private BudgetHealthRingView healthRing;
    private TextView tvHealthLabel, tvTotalBudgeted, tvTotalSpent;
    private LinearLayout budgetCardsContainer, noBudgetCardsContainer;
    private BudgetComparisonChartView comparisonChart;
    private View tvNoBudgets;
    private View comparisonChartCard;

    private SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_goals);

        budgetRepo = new CategoryBudgetRepository(this);
        expenseRepo = new ExpenseRepository(this);
        recurringRepo = new RecurringExpenseRepository(this);

        // Process expired budget periods
        budgetRepo.processExpiredPeriods(expenseRepo);

        initViews();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        healthRing = findViewById(R.id.healthRing);
        tvHealthLabel = findViewById(R.id.tvHealthLabel);
        tvTotalBudgeted = findViewById(R.id.tvTotalBudgeted);
        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        budgetCardsContainer = findViewById(R.id.budgetCardsContainer);
        noBudgetCardsContainer = findViewById(R.id.noBudgetCardsContainer);
        comparisonChart = findViewById(R.id.comparisonChart);
        tvNoBudgets = findViewById(R.id.tvNoBudgets);
        comparisonChartCard = findViewById(R.id.comparisonChartCard);

        findViewById(R.id.btnBackBudget).setOnClickListener(v -> finish());
        findViewById(R.id.btnBudgetHistory).setOnClickListener(v -> showHistoryDialog());
    }

    private void refreshAll() {
        refreshHealthCard();
        refreshBudgetCards();
        refreshNoBudgetCategories();
        refreshComparisonChart();
        updateEmptyState();
    }

    // ─── Health Card ─────────────────────────────────────────

    private void refreshHealthCard() {
        double totalBudget = budgetRepo.getTotalBudgeted();
        double totalSpent = budgetRepo.getTotalSpent(expenseRepo);
        float healthScore = budgetRepo.calculateHealthScore(expenseRepo);
        String healthLabel = budgetRepo.getHealthLabel(expenseRepo);

        healthRing.setScore(healthScore, healthLabel, budgetRepo.getHealthColor(expenseRepo));
        tvHealthLabel.setText(healthLabel);
        tvHealthLabel.setTextColor(budgetRepo.getHealthColor(expenseRepo));
        tvTotalBudgeted.setText("₹" + formatAmount(totalBudget) + " budgeted");
        tvTotalSpent.setText("₹" + formatAmount(totalSpent) + " spent");
    }

    // ─── Budget Cards ────────────────────────────────────────

    private void refreshBudgetCards() {
        budgetCardsContainer.removeAllViews();
        ArrayList<CategoryBudget> budgets = budgetRepo.loadAll();

        for (CategoryBudget budget : budgets) {
            if (!budget.isActive) continue;
            budgetCardsContainer.addView(createBudgetCard(budget));
        }
    }

    private View createBudgetCard(CategoryBudget budget) {
        double spending = budgetRepo.getCategorySpending(budget.categoryId, budget.startDate, budget.endDate, expenseRepo);
        double percent = budget.budgetAmount > 0 ? (spending / budget.budgetAmount) * 100 : 0;
        int progressColor = CategoryBudget.getProgressColor((float) percent);
        int catIndex = Expense.getCategoryIndex(budget.categoryId);

        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.budget_card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLP.bottomMargin = dp(6);
        card.setLayoutParams(cardLP);

        // Top row: category icon + name | amount + percent
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // Category icon
        TextView icon = new TextView(this);
        icon.setText(catIndex >= 0 ? Expense.CATEGORY_ICONS[catIndex] : "📊");
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLP.setMarginEnd(dp(10));
        icon.setLayoutParams(iconLP);
        topRow.addView(icon);

        // Name + period
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameCol.setLayoutParams(nameColLP);

        TextView tvName = new TextView(this);
        tvName.setText(budget.categoryId);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameCol.addView(tvName);

        TextView tvPeriod = new TextView(this);
        int daysLeft = budget.getDaysRemaining();
        tvPeriod.setText(budget.period.substring(0, 1).toUpperCase() + budget.period.substring(1) +
            " • " + daysLeft + " days left");
        tvPeriod.setTextColor(0xFF6B7B8D);
        tvPeriod.setTextSize(11);
        nameCol.addView(tvPeriod);

        topRow.addView(nameCol);

        // Amount + percent
        LinearLayout amtCol = new LinearLayout(this);
        amtCol.setOrientation(LinearLayout.VERTICAL);
        amtCol.setGravity(Gravity.END);

        TextView tvAmount = new TextView(this);
        tvAmount.setText("₹" + formatAmount(spending) + " / ₹" + formatAmount(budget.budgetAmount));
        tvAmount.setTextColor(Color.WHITE);
        tvAmount.setTextSize(12);
        tvAmount.setGravity(Gravity.END);
        amtCol.addView(tvAmount);

        if (percent > 100) {
            // Over budget badge
            TextView overBadge = new TextView(this);
            overBadge.setText("OVER " + String.format("%.0f", percent - 100) + "%");
            overBadge.setTextColor(Color.WHITE);
            overBadge.setTextSize(9);
            overBadge.setBackgroundResource(R.drawable.over_budget_badge_bg);
            overBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            overBadge.setGravity(Gravity.END);
            amtCol.addView(overBadge);
        } else {
            TextView tvPercent = new TextView(this);
            tvPercent.setText(String.format("%.0f%%", percent));
            tvPercent.setTextColor(progressColor);
            tvPercent.setTextSize(11);
            tvPercent.setGravity(Gravity.END);
            amtCol.addView(tvPercent);
        }

        topRow.addView(amtCol);
        card.addView(topRow);

        // Progress bar
        FrameLayout progressTrack = new FrameLayout(this);
        LinearLayout.LayoutParams trackLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        trackLP.topMargin = dp(8);
        progressTrack.setLayoutParams(trackLP);
        progressTrack.setBackgroundResource(R.drawable.budget_progress_track_bg);

        View progressFill = new View(this);
        FrameLayout.LayoutParams fillLP = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        progressFill.setLayoutParams(fillLP);
        progressFill.setBackgroundColor(progressColor);

        // Round corners on fill
        android.graphics.drawable.GradientDrawable fillBg = new android.graphics.drawable.GradientDrawable();
        fillBg.setColor(progressColor);
        fillBg.setCornerRadius(dp(3));
        progressFill.setBackground(fillBg);

        progressTrack.addView(progressFill);
        card.addView(progressTrack);

        // Animate progress bar
        float targetWidth = (float) Math.min(percent / 100.0, 1.3); // Cap at 130% visual
        card.post(() -> {
            int trackWidth = progressTrack.getWidth();
            ValueAnimator animator = ValueAnimator.ofInt(0, (int)(trackWidth * targetWidth));
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                fillLP.width = (int) a.getAnimatedValue();
                progressFill.setLayoutParams(fillLP);
            });
            animator.start();
        });

        // Alert threshold indicator
        if (budget.alertThresholdPercent > 0 && budget.alertThresholdPercent < 100) {
            card.post(() -> {
                int trackWidth = progressTrack.getWidth();
                View marker = new View(this);
                FrameLayout.LayoutParams markerLP = new FrameLayout.LayoutParams(dp(2), dp(10));
                markerLP.leftMargin = (int)(trackWidth * budget.alertThresholdPercent / 100.0);
                markerLP.gravity = Gravity.CENTER_VERTICAL;
                marker.setLayoutParams(markerLP);
                marker.setBackgroundColor(0x60FFFFFF);
                progressTrack.addView(marker);
            });
        }

        // Bottom row: quick actions
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams bottomLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLP.topMargin = dp(6);
        bottomRow.setLayoutParams(bottomLP);

        TextView editBtn = createActionText("Edit");
        editBtn.setOnClickListener(v -> showBudgetSheet(budget));
        bottomRow.addView(editBtn);

        TextView deleteBtn = createActionText("Delete");
        deleteBtn.setTextColor(0xFFEF4444);
        deleteBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete Budget")
                .setMessage("Remove budget for " + budget.categoryId + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    budgetRepo.deleteBudget(budget.id);
                    refreshAll();
                    Toast.makeText(this, "Deleted budget for " + budget.categoryId, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        bottomRow.addView(deleteBtn);

        card.addView(bottomRow);

        // Tap to view expenses ---
        card.setOnClickListener(v -> {
            // Go back to expense tracker filtered by this category
            // Pass category info as extra
            Toast.makeText(this, "Long-press to view " + budget.categoryId + " expenses", Toast.LENGTH_SHORT).show();
        });

        card.setOnLongClickListener(v -> {
            Intent intent = new Intent(this, ExpenseTrackerActivity.class);
            intent.putExtra("filter_category", budget.categoryId);
            startActivity(intent);
            return true;
        });

        return card;
    }

    private TextView createActionText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF7C3AED);
        tv.setTextSize(11);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        return tv;
    }

    // ─── No-Budget Categories ────────────────────────────────

    private void refreshNoBudgetCategories() {
        noBudgetCardsContainer.removeAllViews();
        ArrayList<String> noBudgetCats = budgetRepo.getCategoriesWithoutBudget();

        if (noBudgetCats.isEmpty()) {
            ((View) noBudgetCardsContainer.getParent()).setVisibility(View.GONE);
            return;
        }
        ((View) noBudgetCardsContainer.getParent()).setVisibility(View.VISIBLE);

        for (String catId : noBudgetCats) {
            int catIndex = Expense.getCategoryIndex(catId);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackgroundResource(R.drawable.budget_card_muted_bg);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams itemLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemLP.bottomMargin = dp(4);
            item.setLayoutParams(itemLP);

            // Icon
            TextView iconTV = new TextView(this);
            iconTV.setText(catIndex >= 0 ? Expense.CATEGORY_ICONS[catIndex] : "📊");
            iconTV.setTextSize(18);
            LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(dp(32), dp(32));
            iconLP.setMarginEnd(dp(8));
            iconTV.setLayoutParams(iconLP);
            iconTV.setGravity(Gravity.CENTER);
            item.addView(iconTV);

            // Name
            TextView nameTV = new TextView(this);
            nameTV.setText(catId);
            nameTV.setTextColor(0xFF6B7B8D);
            nameTV.setTextSize(13);
            LinearLayout.LayoutParams nameLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameTV.setLayoutParams(nameLP);
            item.addView(nameTV);

            // Set Budget button
            TextView setBtn = new TextView(this);
            setBtn.setText("+ Set Budget");
            setBtn.setTextColor(0xFF7C3AED);
            setBtn.setTextSize(11);
            setBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            setBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            setBtn.setOnClickListener(v -> {
                CategoryBudget newBudget = new CategoryBudget();
                newBudget.categoryId = catId;
                showBudgetSheet(newBudget);
            });
            item.addView(setBtn);

            noBudgetCardsContainer.addView(item);
        }
    }

    // ─── Comparison Chart ────────────────────────────────────

    private void refreshComparisonChart() {
        ArrayList<CategoryBudget> budgets = budgetRepo.loadAll();
        boolean hasActive = false;
        for (CategoryBudget b : budgets) {
            if (b.isActive) { hasActive = true; break; }
        }

        if (!hasActive) {
            comparisonChartCard.setVisibility(View.GONE);
            return;
        }
        comparisonChartCard.setVisibility(View.VISIBLE);

        ArrayList<String> categories = new ArrayList<>();
        ArrayList<Double> budgetAmounts = new ArrayList<>();
        ArrayList<Double> actualAmounts = new ArrayList<>();

        for (CategoryBudget b : budgets) {
            if (!b.isActive) continue;
            categories.add(b.categoryId);
            budgetAmounts.add(b.budgetAmount);
            actualAmounts.add(budgetRepo.getCategorySpending(b.categoryId, b.startDate, b.endDate, expenseRepo));
        }

        ArrayList<BudgetComparisonChartView.BudgetBar> barList = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            barList.add(new BudgetComparisonChartView.BudgetBar(
                    cat, budgetAmounts.get(i), actualAmounts.get(i),
                    Expense.getCategoryColor(cat)));
        }
        comparisonChart.setData(barList);
    }

    // ─── Empty State ─────────────────────────────────────────

    private void updateEmptyState() {
        ArrayList<CategoryBudget> all = budgetRepo.loadAll();
        boolean hasAny = false;
        for (CategoryBudget b : all) {
            if (b.isActive) { hasAny = true; break; }
        }

        tvNoBudgets.setVisibility(hasAny ? View.GONE : View.VISIBLE);
        findViewById(R.id.budgetHealthCard).setVisibility(hasAny ? View.VISIBLE : View.GONE);
    }

    // ─── Budget Bottom Sheet ─────────────────────────────────

    private void showBudgetSheet(CategoryBudget existing) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_budget, null);
        dialog.setContentView(sheet);

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }

        boolean isEdit = existing != null && existing.id != null && budgetRepo.getByCategory(existing.categoryId) != null;
        boolean isPresetCategory = existing != null && existing.categoryId != null;

        TextView tvTitle = sheet.findViewById(R.id.tvBudgetSheetTitle);
        LinearLayout categorySelectorContainer = sheet.findViewById(R.id.budgetCategoryContainer);
        EditText etBudgetAmount = sheet.findViewById(R.id.etBudgetAmount);
        LinearLayout periodContainer = sheet.findViewById(R.id.periodContainer);
        SeekBar seekThreshold = sheet.findViewById(R.id.seekThreshold);
        TextView tvThresholdValue = sheet.findViewById(R.id.tvThresholdValue);
        CheckBox cbBudgetActive = sheet.findViewById(R.id.cbBudgetActive);

        tvTitle.setText(isEdit ? "EDIT BUDGET" : "SET BUDGET");

        // State
        final String[] selectedCategory = {isPresetCategory ? existing.categoryId : Expense.CATEGORIES[0]};
        final String[] selectedPeriod = {isEdit ? existing.period : CategoryBudget.PERIOD_MONTHLY};

        // Category selector
        buildBudgetCategorySelector(categorySelectorContainer, selectedCategory, isEdit || isPresetCategory);

        // Pre-fill if editing
        if (isEdit) {
            etBudgetAmount.setText(String.format("%.0f", existing.budgetAmount));
            seekThreshold.setProgress(existing.alertThresholdPercent - 50);
            tvThresholdValue.setText(existing.alertThresholdPercent + "%");
            cbBudgetActive.setChecked(existing.isActive);
        } else {
            seekThreshold.setProgress(30); // default 80%
            tvThresholdValue.setText("80%");
            cbBudgetActive.setChecked(true);
        }

        // Period pills
        buildPeriodPills(periodContainer, selectedPeriod);

        // Threshold seek bar
        seekThreshold.setMax(45); // 50% to 95%
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvThresholdValue.setText((progress + 50) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        // Save
        sheet.findViewById(R.id.btnSaveBudget).setOnClickListener(v -> {
            String amtStr = etBudgetAmount.getText().toString().trim();
            if (amtStr.isEmpty()) { etBudgetAmount.setError("Required"); return; }

            double amount;
            try { amount = Double.parseDouble(amtStr); }
            catch (NumberFormatException e) { etBudgetAmount.setError("Invalid"); return; }
            if (amount <= 0) { etBudgetAmount.setError("Must be positive"); return; }

            // Check if category already has budget (for new)
            CategoryBudget existingForCat = budgetRepo.getByCategory(selectedCategory[0]);
            CategoryBudget budget;

            if (isEdit) {
                budget = existing;
            } else if (existingForCat != null) {
                budget = existingForCat;
            } else {
                budget = new CategoryBudget();
                budget.categoryId = selectedCategory[0];
            }

            budget.budgetAmount = amount;
            budget.period = selectedPeriod[0];
            budget.alertThresholdPercent = seekThreshold.getProgress() + 50;
            budget.isActive = cbBudgetActive.isChecked();
            budget.updatedAt = System.currentTimeMillis();
            budget.thresholdAlertFired = false;
            budget.exceededAlertFired = false;

            if (budget.startDate == 0) {
                budget.startDate = System.currentTimeMillis();
                budget.calculatePeriodDates();
            }

            if (isEdit || existingForCat != null) {
                budgetRepo.updateBudget(budget);
            } else {
                budgetRepo.addBudget(budget);
            }

            dialog.dismiss();
            refreshAll();

            String msg = isEdit ? "Updated " : "Set ";
            Toast.makeText(this, "✅ " + msg + selectedCategory[0] + " budget", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void buildBudgetCategorySelector(LinearLayout container, String[] selected, boolean locked) {
        container.removeAllViews();

        for (int i = 0; i < Expense.CATEGORIES.length; i++) {
            if ("Salary".equals(Expense.CATEGORIES[i])) continue; // Skip income categories

            final int idx = i;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(8), dp(10), dp(8));
            chip.setBackgroundResource(Expense.CATEGORIES[i].equals(selected[0]) ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);

            // Category icon
            TextView iconTV = new TextView(this);
            iconTV.setText(Expense.CATEGORY_ICONS[i]);
            iconTV.setTextSize(18);
            iconTV.setGravity(Gravity.CENTER);
            chip.addView(iconTV);

            // Category name
            TextView nameTV = new TextView(this);
            nameTV.setText(Expense.CATEGORIES[i]);
            nameTV.setTextColor(Expense.CATEGORIES[i].equals(selected[0]) ? Color.WHITE : 0xFF6B7B8D);
            nameTV.setTextSize(9);
            nameTV.setGravity(Gravity.CENTER);
            chip.addView(nameTV);

            // Current spend label
            Map<String, Double> spendMap = expenseRepo.getCategoryBreakdown();
            double catSpend = spendMap.containsKey(Expense.CATEGORIES[i]) ? spendMap.get(Expense.CATEGORIES[i]) : 0;
            if (catSpend > 0) {
                TextView spendTV = new TextView(this);
                spendTV.setText("₹" + formatAmount(catSpend));
                spendTV.setTextColor(0xFF4B5563);
                spendTV.setTextSize(8);
                spendTV.setGravity(Gravity.CENTER);
                chip.addView(spendTV);
            }

            if (!locked) {
                chip.setOnClickListener(v -> {
                    selected[0] = Expense.CATEGORIES[idx];
                    buildBudgetCategorySelector(container, selected, false);
                });
            }

            container.addView(chip);
        }
    }

    private void buildPeriodPills(LinearLayout container, String[] selected) {
        container.removeAllViews();
        String[] labels = {"Weekly", "Monthly", "Quarterly", "Yearly"};
        String[] values = {CategoryBudget.PERIOD_WEEKLY, CategoryBudget.PERIOD_MONTHLY,
            CategoryBudget.PERIOD_QUARTERLY, CategoryBudget.PERIOD_YEARLY};

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView pill = new TextView(this);
            pill.setText(labels[i]);
            pill.setTextSize(12);
            pill.setTextColor(values[i].equals(selected[0]) ? Color.WHITE : 0xFF6B7B8D);
            pill.setPadding(dp(14), dp(8), dp(14), dp(8));
            pill.setBackgroundResource(values[i].equals(selected[0]) ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                selected[0] = values[idx];
                buildPeriodPills(container, selected);
            });
            container.addView(pill);
        }
    }

    // ─── Budget History ──────────────────────────────────────

    private void showHistoryDialog() {
        Map<String, ArrayList<BudgetHistory>> grouped = budgetRepo.getGroupedHistory();
        if (grouped.isEmpty()) {
            Toast.makeText(this, "No budget history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF111827);
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        for (Map.Entry<String, ArrayList<BudgetHistory>> entry : grouped.entrySet()) {
            // Category header
            int catIndex = Expense.getCategoryIndex(entry.getKey());
            TextView header = new TextView(this);
            header.setText((catIndex >= 0 ? Expense.CATEGORY_ICONS[catIndex] + " " : "") + entry.getKey());
            header.setTextColor(Color.WHITE);
            header.setTextSize(15);
            header.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerLP.topMargin = dp(12);
            headerLP.bottomMargin = dp(6);
            header.setLayoutParams(headerLP);
            content.addView(header);

            for (BudgetHistory h : entry.getValue()) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(6), dp(8), dp(6));
                row.setBackgroundResource(R.drawable.glass_card_bg);
                LinearLayout.LayoutParams rowLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLP.bottomMargin = dp(4);
                row.setLayoutParams(rowLP);

                // Period dates
                TextView dates = new TextView(this);
                dates.setText(dateFmt.format(new Date(h.startDate)) + " - " + dateFmt.format(new Date(h.endDate)));
                dates.setTextColor(0xFF6B7B8D);
                dates.setTextSize(11);
                LinearLayout.LayoutParams datesLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                dates.setLayoutParams(datesLP);
                row.addView(dates);

                // Budget vs actual
                boolean over = h.actualSpent > h.budgetAmount;
                TextView amounts = new TextView(this);
                amounts.setText("₹" + formatAmount(h.actualSpent) + " / ₹" + formatAmount(h.budgetAmount));
                amounts.setTextColor(over ? 0xFFEF4444 : 0xFF22C55E);
                amounts.setTextSize(11);
                amounts.setGravity(Gravity.END);
                row.addView(amounts);

                content.addView(row);
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("Budget History")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show();
    }

    // ─── Helpers ─────────────────────────────────────────────

    private String formatAmount(double amount) {
        if (amount >= 100000) return String.format("%.1fL", amount / 100000);
        if (amount >= 1000) return String.format("%.1fk", amount / 1000);
        return String.format("%.0f", amount);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
