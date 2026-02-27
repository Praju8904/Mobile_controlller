package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Subscriptions / Recurring Expenses screen.
 * Shows header summary, upcoming renewals, active/paused lists,
 * category breakdown donut, and monthly cost trend chart.
 */
public class SubscriptionsActivity extends AppCompatActivity {

    private RecurringExpenseRepository recurringRepo;
    private ExpenseRepository expenseRepo;

    // Views
    private TextView tvTotalMonthly, tvTotalYearly, tvActiveCount, tvDueSoonWarning;
    private LinearLayout upcomingContainer, activeSubscriptionsContainer;
    private LinearLayout pausedSection, pausedContainer;
    private TextView tvPausedCount, tvPausedToggle, tvNoUpcoming, tvNoSubscriptions;
    private ExpenseDonutView subscriptionDonut;
    private SubscriptionTrendChartView trendChart;
    private boolean pausedExpanded = false;

    private SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private SimpleDateFormat shortDateFmt = new SimpleDateFormat("dd MMM", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscriptions);

        recurringRepo = new RecurringExpenseRepository(this);
        expenseRepo = new ExpenseRepository(this);

        // Process overdue on screen open
        ExpenseNotificationHelper.processRecurringExpenses(this);

        initViews();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        tvTotalMonthly = findViewById(R.id.tvTotalMonthly);
        tvTotalYearly = findViewById(R.id.tvTotalYearly);
        tvActiveCount = findViewById(R.id.tvActiveCount);
        tvDueSoonWarning = findViewById(R.id.tvDueSoonWarning);
        upcomingContainer = findViewById(R.id.upcomingContainer);
        activeSubscriptionsContainer = findViewById(R.id.activeSubscriptionsContainer);
        pausedSection = findViewById(R.id.pausedSection);
        pausedContainer = findViewById(R.id.pausedContainer);
        tvPausedCount = findViewById(R.id.tvPausedCount);
        tvPausedToggle = findViewById(R.id.tvPausedToggle);
        tvNoUpcoming = findViewById(R.id.tvNoUpcoming);
        tvNoSubscriptions = findViewById(R.id.tvNoSubscriptions);
        subscriptionDonut = findViewById(R.id.subscriptionDonut);
        trendChart = findViewById(R.id.trendChart);

        findViewById(R.id.btnBackSubscriptions).setOnClickListener(v -> finish());
        findViewById(R.id.fabAddSubscription).setOnClickListener(v -> showSubscriptionSheet(null));

        // Paused section toggle
        findViewById(R.id.pausedHeader).setOnClickListener(v -> {
            pausedExpanded = !pausedExpanded;
            pausedContainer.setVisibility(pausedExpanded ? View.VISIBLE : View.GONE);
            tvPausedToggle.setText(pausedExpanded ? " ▲" : " ▼");
        });
    }

    private void refreshAll() {
        refreshHeader();
        refreshUpcoming();
        refreshActiveList();
        refreshPausedList();
        refreshCharts();
    }

    // ─── Header Summary ──────────────────────────────────────

    private void refreshHeader() {
        double monthly = recurringRepo.getTotalMonthlyRecurring();
        double yearly = recurringRepo.getTotalYearlyRecurring();
        int count = recurringRepo.getActiveCount();

        tvTotalMonthly.setText("₹" + formatAmount(monthly));
        tvTotalYearly.setText("₹" + formatAmount(yearly) + " / year");
        tvActiveCount.setText(count + " active");

        if (recurringRepo.hasUpcomingWithin7Days()) {
            tvDueSoonWarning.setVisibility(View.VISIBLE);
            int upcoming = recurringRepo.getUpcoming(7).size();
            tvDueSoonWarning.setText("⚠️ " + upcoming + " due within 7 days");
        } else {
            tvDueSoonWarning.setVisibility(View.GONE);
        }
    }

    // ─── Upcoming Renewals ───────────────────────────────────

    private void refreshUpcoming() {
        upcomingContainer.removeAllViews();
        ArrayList<RecurringExpense> upcoming = recurringRepo.getUpcoming(30);

        if (upcoming.isEmpty()) {
            tvNoUpcoming.setVisibility(View.VISIBLE);
            upcomingContainer.setVisibility(View.GONE);
        } else {
            tvNoUpcoming.setVisibility(View.GONE);
            upcomingContainer.setVisibility(View.VISIBLE);

            for (RecurringExpense re : upcoming) {
                upcomingContainer.addView(createUpcomingCard(re));
            }
        }
    }

    private View createUpcomingCard(RecurringExpense re) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.upcoming_renewal_card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(10));
        card.setLayoutParams(lp);

        // Icon
        TextView icon = new TextView(this);
        icon.setText(re.logoOrIcon);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        // Name
        TextView name = new TextView(this);
        name.setText(re.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLP.topMargin = dp(4);
        name.setLayoutParams(nameLP);
        card.addView(name);

        // Amount
        TextView amount = new TextView(this);
        amount.setText(re.currency + String.format("%.0f", re.amount));
        amount.setTextColor(0xFFB0B8CC);
        amount.setTextSize(12);
        amount.setGravity(Gravity.CENTER);
        card.addView(amount);

        // Days until due
        int days = re.getDaysUntilDue();
        TextView dueLabel = new TextView(this);
        if (days <= 0) {
            dueLabel.setText("Due today!");
            dueLabel.setTextColor(0xFFEF4444);
        } else if (days <= 3) {
            dueLabel.setText("Due in " + days + "d");
            dueLabel.setTextColor(0xFFEF4444);
        } else if (days <= 7) {
            dueLabel.setText("Due in " + days + "d");
            dueLabel.setTextColor(0xFFF59E0B);
        } else {
            dueLabel.setText("Due in " + days + "d");
            dueLabel.setTextColor(0xFF22C55E);
        }
        dueLabel.setTextSize(11);
        dueLabel.setGravity(Gravity.CENTER);
        dueLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams dueLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dueLP.topMargin = dp(4);
        dueLabel.setLayoutParams(dueLP);
        card.addView(dueLabel);

        card.setOnClickListener(v -> showSubscriptionSheet(re));
        return card;
    }

    // ─── Active Subscriptions List ───────────────────────────

    private void refreshActiveList() {
        activeSubscriptionsContainer.removeAllViews();
        Map<String, ArrayList<RecurringExpense>> grouped = recurringRepo.getGroupedByRecurrence();
        boolean hasAny = false;

        for (Map.Entry<String, ArrayList<RecurringExpense>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            hasAny = true;

            // Group header
            TextView header = new TextView(this);
            header.setText(entry.getKey().toUpperCase());
            header.setTextColor(0xFF6B7B8D);
            header.setTextSize(10);
            header.setLetterSpacing(0.15f);
            LinearLayout.LayoutParams headerLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerLP.topMargin = dp(8);
            headerLP.bottomMargin = dp(4);
            header.setLayoutParams(headerLP);
            activeSubscriptionsContainer.addView(header);

            for (RecurringExpense re : entry.getValue()) {
                activeSubscriptionsContainer.addView(createSubscriptionItem(re, true));
            }
        }

        tvNoSubscriptions.setVisibility(hasAny ? View.GONE : View.VISIBLE);
    }

    private View createSubscriptionItem(RecurringExpense re, boolean isActive) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackgroundResource(R.drawable.glass_card_bg);
        item.setPadding(dp(4), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams itemLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemLP.bottomMargin = dp(4);
        item.setLayoutParams(itemLP);

        // Left color border
        View colorBar = new View(this);
        LinearLayout.LayoutParams barLP = new LinearLayout.LayoutParams(dp(4), dp(48));
        barLP.setMarginEnd(dp(10));
        colorBar.setLayoutParams(barLP);
        colorBar.setBackgroundColor(re.color);
        item.addView(colorBar);

        // Icon
        TextView icon = new TextView(this);
        icon.setText(re.logoOrIcon);
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconLP = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconLP.setMarginEnd(dp(10));
        icon.setLayoutParams(iconLP);
        icon.setBackgroundResource(R.drawable.expense_chip_bg);
        item.addView(icon);

        // Details column
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        details.setLayoutParams(detailsLP);

        TextView nameTV = new TextView(this);
        nameTV.setText(re.name);
        nameTV.setTextColor(isActive ? Color.WHITE : 0xFF6B7B8D);
        nameTV.setTextSize(14);
        nameTV.setTypeface(null, android.graphics.Typeface.BOLD);
        nameTV.setMaxLines(1);
        nameTV.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(nameTV);

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        // Next due date
        TextView dueTV = new TextView(this);
        dueTV.setText("Next: " + shortDateFmt.format(new Date(re.nextDueDate)));
        dueTV.setTextColor(0xFF6B7B8D);
        dueTV.setTextSize(11);
        metaRow.addView(dueTV);

        // Category badge
        TextView catBadge = new TextView(this);
        catBadge.setText(" " + Expense.getCategoryIcon(re.categoryId) + " " + re.categoryId);
        catBadge.setTextColor(0xFF6B7B8D);
        catBadge.setTextSize(10);
        LinearLayout.LayoutParams catLP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        catLP.setMarginStart(dp(8));
        catBadge.setLayoutParams(catLP);
        metaRow.addView(catBadge);

        details.addView(metaRow);

        // Monthly equivalent for yearly subs
        if (RecurringExpense.RECURRENCE_YEARLY.equals(re.recurrenceType)) {
            TextView monthlyEq = new TextView(this);
            monthlyEq.setText("≈ " + re.currency + String.format("%.0f", re.getMonthlyEquivalent()) + "/mo");
            monthlyEq.setTextColor(0xFF4B5563);
            monthlyEq.setTextSize(10);
            details.addView(monthlyEq);
        }

        item.addView(details);

        // Amount column
        LinearLayout amountCol = new LinearLayout(this);
        amountCol.setOrientation(LinearLayout.VERTICAL);
        amountCol.setGravity(Gravity.END);

        TextView amountTV = new TextView(this);
        amountTV.setText(re.currency + String.format("%.0f", re.amount));
        amountTV.setTextColor(Color.WHITE);
        amountTV.setTextSize(15);
        amountTV.setTypeface(null, android.graphics.Typeface.BOLD);
        amountTV.setGravity(Gravity.END);
        amountCol.addView(amountTV);

        TextView freqTV = new TextView(this);
        freqTV.setText("/" + re.getRecurrenceLabel().toLowerCase().charAt(0) + "o");
        freqTV.setTextColor(0xFF6B7B8D);
        freqTV.setTextSize(10);
        freqTV.setGravity(Gravity.END);
        amountCol.addView(freqTV);

        item.addView(amountCol);

        // Action buttons
        if (isActive) {
            // Pause button
            TextView pauseBtn = new TextView(this);
            pauseBtn.setText("⏸");
            pauseBtn.setTextSize(16);
            pauseBtn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pauseLP = new LinearLayout.LayoutParams(dp(36), dp(36));
            pauseLP.setMarginStart(dp(6));
            pauseBtn.setLayoutParams(pauseLP);
            pauseBtn.setOnClickListener(v -> {
                recurringRepo.toggleActive(re.id);
                refreshAll();
                Toast.makeText(this, re.name + " paused", Toast.LENGTH_SHORT).show();
            });
            item.addView(pauseBtn);

            // 3-dot menu
            TextView menuBtn = new TextView(this);
            menuBtn.setText("⋮");
            menuBtn.setTextColor(0xFF6B7B8D);
            menuBtn.setTextSize(18);
            menuBtn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams menuLP = new LinearLayout.LayoutParams(dp(30), dp(36));
            menuBtn.setLayoutParams(menuLP);
            menuBtn.setOnClickListener(v -> showItemMenu(v, re));
            item.addView(menuBtn);
        } else {
            // Resume button
            TextView resumeBtn = new TextView(this);
            resumeBtn.setText("▶️");
            resumeBtn.setTextSize(16);
            resumeBtn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams resumeLP = new LinearLayout.LayoutParams(dp(36), dp(36));
            resumeLP.setMarginStart(dp(8));
            resumeBtn.setLayoutParams(resumeLP);
            resumeBtn.setOnClickListener(v -> {
                recurringRepo.toggleActive(re.id);
                refreshAll();
                Toast.makeText(this, re.name + " resumed", Toast.LENGTH_SHORT).show();
            });
            item.addView(resumeBtn);
        }

        item.setOnClickListener(v -> showSubscriptionSheet(re));
        return item;
    }

    private void showItemMenu(View anchor, RecurringExpense re) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Duplicate");
        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(menuItem -> {
            String title = menuItem.getTitle().toString();
            switch (title) {
                case "Edit":
                    showSubscriptionSheet(re);
                    return true;
                case "Duplicate":
                    recurringRepo.duplicate(re.id);
                    refreshAll();
                    Toast.makeText(this, "Duplicated " + re.name, Toast.LENGTH_SHORT).show();
                    return true;
                case "Delete":
                    new AlertDialog.Builder(this)
                        .setTitle("Delete Subscription")
                        .setMessage("Delete " + re.name + " (" + re.currency + re.amount + ")?")
                        .setPositiveButton("Delete", (d, w) -> {
                            recurringRepo.deleteRecurringExpense(re.id);
                            refreshAll();
                            Toast.makeText(this, "Deleted " + re.name, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
            }
            return false;
        });

        popup.show();
    }

    // ─── Paused Subscriptions ────────────────────────────────

    private void refreshPausedList() {
        ArrayList<RecurringExpense> paused = recurringRepo.getPaused();
        pausedContainer.removeAllViews();

        if (paused.isEmpty()) {
            pausedSection.setVisibility(View.GONE);
        } else {
            pausedSection.setVisibility(View.VISIBLE);
            tvPausedCount.setText(String.valueOf(paused.size()));

            for (RecurringExpense re : paused) {
                pausedContainer.addView(createSubscriptionItem(re, false));
            }
        }
    }

    // ─── Charts ──────────────────────────────────────────────

    private void refreshCharts() {
        // Category breakdown donut
        Map<String, Double> breakdown = recurringRepo.getCategoryBreakdown();
        if (!breakdown.isEmpty()) {
            subscriptionDonut.setData(breakdown);
            findViewById(R.id.categoryBreakdownCard).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.categoryBreakdownCard).setVisibility(View.GONE);
        }

        // Monthly cost trend
        trendChart.setData(recurringRepo.getMonthlyCostTrend(6));
    }

    // ─── Add / Edit Bottom Sheet ─────────────────────────────

    private void showSubscriptionSheet(RecurringExpense existing) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_subscription, null);
        dialog.setContentView(sheet);

        // Make background transparent for rounded corners
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        }

        // Get references
        TextView tvTitle = sheet.findViewById(R.id.tvSheetTitle);
        EditText etName = sheet.findViewById(R.id.etServiceName);
        EditText etAmount = sheet.findViewById(R.id.etSubAmount);
        TextView tvCurrency = sheet.findViewById(R.id.tvCurrency);
        LinearLayout catRow1 = sheet.findViewById(R.id.subCategoryRow1);
        LinearLayout catRow2 = sheet.findViewById(R.id.subCategoryRow2);
        EditText etPayment = sheet.findViewById(R.id.etPaymentMethod);
        LinearLayout recurrenceContainer = sheet.findViewById(R.id.recurrenceContainer);
        TextView tvStartDate = sheet.findViewById(R.id.tvStartDate);
        CheckBox cbEndDate = sheet.findViewById(R.id.cbHasEndDate);
        TextView tvEndDate = sheet.findViewById(R.id.tvEndDate);
        CheckBox cbReminder = sheet.findViewById(R.id.cbReminder);
        LinearLayout reminderDaysContainer = sheet.findViewById(R.id.reminderDaysContainer);
        LinearLayout colorPicker = sheet.findViewById(R.id.colorPickerContainer);
        EditText etNotes = sheet.findViewById(R.id.etSubNotes);
        LinearLayout suggestionsContainer = sheet.findViewById(R.id.suggestionsContainer);

        boolean isEdit = existing != null;
        tvTitle.setText(isEdit ? "EDIT SUBSCRIPTION" : "ADD SUBSCRIPTION");

        // State holders
        final String[] selectedCategory = {isEdit ? existing.categoryId : Expense.CATEGORIES[0]};
        final String[] selectedRecurrence = {isEdit ? existing.recurrenceType : RecurringExpense.RECURRENCE_MONTHLY};
        final long[] startDateMs = {isEdit ? existing.startDate : System.currentTimeMillis()};
        final long[] endDateMs = {isEdit ? existing.endDate : 0};
        final int[] selectedColor = {isEdit ? existing.color : 0xFF7C3AED};
        final int[] reminderDays = {isEdit ? existing.reminderDaysBefore : 3};
        final String[] selectedIcon = {isEdit ? existing.logoOrIcon : "📦"};
        final String[] selectedCurrency = {isEdit ? existing.currency : "₹"};

        // Populate suggestions
        buildSuggestions(suggestionsContainer, etName, etAmount, selectedCategory, selectedIcon);

        // Pre-fill if editing
        if (isEdit) {
            etName.setText(existing.name);
            etAmount.setText(String.format("%.0f", existing.amount));
            etPayment.setText(existing.walletOrPaymentMethod);
            etNotes.setText(existing.notes);
            tvCurrency.setText(existing.currency);
            cbReminder.setChecked(existing.reminderDaysBefore > 0);
            cbEndDate.setChecked(existing.endDate > 0);
        }

        // Start date
        tvStartDate.setText(dateFmt.format(new Date(startDateMs[0])));
        tvStartDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(startDateMs[0]);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(y, m, d);
                startDateMs[0] = selected.getTimeInMillis();
                tvStartDate.setText(dateFmt.format(selected.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // End date toggle
        cbEndDate.setOnCheckedChangeListener((btn, checked) -> {
            tvEndDate.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && endDateMs[0] == 0) {
                endDateMs[0] = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000;
                tvEndDate.setText(dateFmt.format(new Date(endDateMs[0])));
            }
        });
        if (isEdit && existing.endDate > 0) {
            tvEndDate.setVisibility(View.VISIBLE);
            tvEndDate.setText(dateFmt.format(new Date(existing.endDate)));
        }
        tvEndDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(endDateMs[0]);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(y, m, d);
                endDateMs[0] = selected.getTimeInMillis();
                tvEndDate.setText(dateFmt.format(selected.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Currency selector
        tvCurrency.setOnClickListener(v -> {
            String[] currencies = {"₹", "$", "€", "£", "¥"};
            new AlertDialog.Builder(this)
                .setTitle("Currency")
                .setItems(currencies, (d, which) -> {
                    selectedCurrency[0] = currencies[which];
                    tvCurrency.setText(currencies[which]);
                })
                .show();
        });

        // Category chips
        buildCategoryChips(catRow1, catRow2, selectedCategory);

        // Recurrence type pills
        buildRecurrencePills(recurrenceContainer, selectedRecurrence);

        // Reminder days pills
        buildReminderDaysPills(reminderDaysContainer, reminderDays);
        cbReminder.setOnCheckedChangeListener((btn, checked) -> {
            reminderDaysContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        reminderDaysContainer.setVisibility(cbReminder.isChecked() ? View.VISIBLE : View.GONE);

        // Color picker
        buildColorPicker(colorPicker, selectedColor);

        // Save button
        sheet.findViewById(R.id.btnSaveSubscription).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();

            if (name.isEmpty()) { etName.setError("Name required"); return; }
            if (amountStr.isEmpty()) { etAmount.setError("Amount required"); return; }

            double amount;
            try { amount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) { etAmount.setError("Invalid amount"); return; }
            if (amount <= 0) { etAmount.setError("Must be positive"); return; }

            RecurringExpense re = isEdit ? existing : new RecurringExpense();
            re.name = name;
            re.amount = amount;
            re.currency = selectedCurrency[0];
            re.categoryId = selectedCategory[0];
            re.walletOrPaymentMethod = etPayment.getText().toString().trim();
            re.recurrenceType = selectedRecurrence[0];
            re.startDate = startDateMs[0];
            re.endDate = cbEndDate.isChecked() ? endDateMs[0] : 0;
            re.reminderDaysBefore = cbReminder.isChecked() ? reminderDays[0] : 0;
            re.color = selectedColor[0];
            re.logoOrIcon = selectedIcon[0];
            re.notes = etNotes.getText().toString().trim();
            re.updatedAt = System.currentTimeMillis();

            if (!isEdit) {
                re.nextDueDate = re.startDate;
                if (re.nextDueDate <= System.currentTimeMillis()) {
                    // First due date is in the future
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(re.startDate);
                    switch (re.recurrenceType) {
                        case RecurringExpense.RECURRENCE_DAILY:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
                            break;
                        case RecurringExpense.RECURRENCE_WEEKLY:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1);
                            break;
                        case RecurringExpense.RECURRENCE_MONTHLY:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.MONTH, 1);
                            break;
                        case RecurringExpense.RECURRENCE_QUARTERLY:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.MONTH, 3);
                            break;
                        case RecurringExpense.RECURRENCE_YEARLY:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.YEAR, 1);
                            break;
                        default:
                            while (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, Math.max(1, re.recurrenceInterval));
                            break;
                    }
                    re.nextDueDate = cal.getTimeInMillis();
                }
                recurringRepo.addRecurringExpense(re);
            } else {
                recurringRepo.updateRecurringExpense(re);
            }

            // Schedule reminder
            ExpenseNotificationHelper.scheduleSubscriptionReminder(this, re);

            dialog.dismiss();
            refreshAll();

            String msg = isEdit ? "Updated " + re.name : "Added " + re.name;
            Toast.makeText(this, "✅ " + msg, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    // ─── UI Builders for Bottom Sheet ────────────────────────

    private void buildSuggestions(LinearLayout container, EditText etName, EditText etAmount,
                                   String[] selectedCategory, String[] selectedIcon) {
        container.removeAllViews();
        for (String[] service : RecurringExpense.COMMON_SERVICES) {
            TextView chip = new TextView(this);
            chip.setText(service[1] + " " + service[0]);
            chip.setTextSize(11);
            chip.setTextColor(0xFFB0B8CC);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackgroundResource(R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                etName.setText(service[0]);
                etAmount.setText(service[3]);
                selectedCategory[0] = service[2];
                selectedIcon[0] = service[1];
            });
            container.addView(chip);
        }
    }

    private void buildCategoryChips(LinearLayout row1, LinearLayout row2, String[] selected) {
        row1.removeAllViews();
        row2.removeAllViews();
        for (int i = 0; i < Expense.CATEGORIES.length; i++) {
            LinearLayout row = i < 5 ? row1 : row2;
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(Expense.CATEGORY_ICONS[i] + " " + Expense.CATEGORIES[i]);
            chip.setTextSize(10);
            chip.setTextColor(Color.WHITE);
            chip.setPadding(dp(8), dp(8), dp(8), dp(8));
            chip.setBackgroundResource(Expense.CATEGORIES[i].equals(selected[0]) ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(dp(4));
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);

            chip.setOnClickListener(v -> {
                selected[0] = Expense.CATEGORIES[idx];
                buildCategoryChips(row1, row2, selected);
            });
            row.addView(chip);
        }
    }

    private void buildRecurrencePills(LinearLayout container, String[] selected) {
        container.removeAllViews();
        String[] types = {"Daily", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom"};
        String[] vals = {RecurringExpense.RECURRENCE_DAILY, RecurringExpense.RECURRENCE_WEEKLY,
            RecurringExpense.RECURRENCE_MONTHLY, RecurringExpense.RECURRENCE_QUARTERLY,
            RecurringExpense.RECURRENCE_YEARLY, RecurringExpense.RECURRENCE_CUSTOM};

        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            TextView pill = new TextView(this);
            pill.setText(types[i]);
            pill.setTextSize(12);
            pill.setTextColor(vals[i].equals(selected[0]) ? Color.WHITE : 0xFF6B7B8D);
            pill.setPadding(dp(14), dp(8), dp(14), dp(8));
            pill.setBackgroundResource(vals[i].equals(selected[0]) ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                selected[0] = vals[idx];
                buildRecurrencePills(container, selected);
            });
            container.addView(pill);
        }
    }

    private void buildReminderDaysPills(LinearLayout container, int[] selected) {
        container.removeAllViews();
        int[] options = {1, 3, 7};
        String[] labels = {"1 day", "3 days", "7 days"};

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView pill = new TextView(this);
            pill.setText(labels[i]);
            pill.setTextSize(11);
            pill.setTextColor(options[i] == selected[0] ? Color.WHITE : 0xFF6B7B8D);
            pill.setPadding(dp(10), dp(6), dp(10), dp(6));
            pill.setBackgroundResource(options[i] == selected[0] ?
                R.drawable.expense_chip_selected_bg : R.drawable.expense_chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                selected[0] = options[idx];
                buildReminderDaysPills(container, selected);
            });
            container.addView(pill);
        }
    }

    private void buildColorPicker(LinearLayout container, int[] selected) {
        container.removeAllViews();
        int[] colors = {
            0xFF7C3AED, 0xFFEF4444, 0xFF22C55E, 0xFF3B82F6, 0xFFF59E0B,
            0xFFA855F7, 0xFF06B6D4, 0xFFEC4899, 0xFFFF6B6B, 0xFF6366F1
        };

        for (int color : colors) {
            View swatch = new View(this);
            int size = dp(32);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            swatch.setLayoutParams(lp);
            swatch.setBackgroundColor(color);

            // Selected indicator
            if (color == selected[0]) {
                swatch.setAlpha(1f);
                swatch.setScaleX(1.2f);
                swatch.setScaleY(1.2f);
            } else {
                swatch.setAlpha(0.6f);
            }

            swatch.setOnClickListener(v -> {
                selected[0] = color;
                buildColorPicker(container, selected);
            });
            container.addView(swatch);
        }
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
