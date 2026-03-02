package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class WalletDetailActivity extends AppCompatActivity {

    private WalletRepository walletRepo;
    private ExpenseRepository expenseRepo;
    private RecurringExpenseRepository recurringRepo;

    private String walletId;
    private Wallet wallet;
    private String currentFilter = "All";

    private TextView tvWalletName, tvWalletIcon, tvWalletType, tvWalletBankName;
    private TextView tvWalletBalance, tvWalletIncome, tvWalletExpenses;
    private TextView tvDefaultBadge, tvNoTransactions;
    private View creditCardSection;
    private TextView tvCreditUsage, tvAvailableCredit, tvBillingDate;
    private ProgressBar creditUsageBar;
    private Button btnPayBill;
    private LinearLayout filterChips, transactionsContainer;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_detail);

        walletRepo = new WalletRepository(this);
        expenseRepo = new ExpenseRepository(this);
        recurringRepo = new RecurringExpenseRepository(this);

        walletId = getIntent().getStringExtra("wallet_id");
        if (walletId == null) {
            finish();
            return;
        }

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        wallet = walletRepo.getById(walletId);
        if (wallet == null) {
            finish();
            return;
        }
        refreshAll();
    }

    private void initViews() {
        tvWalletName = findViewById(R.id.tvWalletName);
        tvWalletIcon = findViewById(R.id.tvWalletIcon);
        tvWalletType = findViewById(R.id.tvWalletType);
        tvWalletBankName = findViewById(R.id.tvWalletBankName);
        tvWalletBalance = findViewById(R.id.tvWalletBalance);
        tvWalletIncome = findViewById(R.id.tvWalletIncome);
        tvWalletExpenses = findViewById(R.id.tvWalletExpenses);
        tvDefaultBadge = findViewById(R.id.tvDefaultBadge);
        tvNoTransactions = findViewById(R.id.tvNoTransactions);
        creditCardSection = findViewById(R.id.creditCardSection);
        tvCreditUsage = findViewById(R.id.tvCreditUsage);
        tvAvailableCredit = findViewById(R.id.tvAvailableCredit);
        tvBillingDate = findViewById(R.id.tvBillingDate);
        creditUsageBar = findViewById(R.id.creditUsageBar);
        btnPayBill = findViewById(R.id.btnPayBill);
        filterChips = findViewById(R.id.filterChips);
        transactionsContainer = findViewById(R.id.transactionsContainer);
    }

    private void setupListeners() {
        findViewById(R.id.btnBackWalletDetail).setOnClickListener(v -> finish());

        findViewById(R.id.btnEditWallet).setOnClickListener(v -> {
            // Re-open the WalletsActivity add dialog won't work easily from here.
            // Instead, we'll send back to WalletsActivity with edit intent.
            Intent intent = new Intent(this, WalletsActivity.class);
            intent.putExtra("edit_wallet_id", walletId);
            startActivity(intent);
        });

        findViewById(R.id.btnWalletMenu).setOnClickListener(v -> showContextMenu());

        btnPayBill.setOnClickListener(v -> showPayBillDialog());
    }

    // ─── Refresh ─────────────────────────────────────────────

    private void refreshAll() {
        refreshHeader();
        refreshCreditCardSection();
        refreshFilterChips();
        refreshTransactions();
    }

    private void refreshHeader() {
        tvWalletName.setText(wallet.getTypeIcon() + " " + wallet.name);
        tvWalletIcon.setText(wallet.getTypeIcon());
        tvWalletType.setText(wallet.type);

        String bankName = "";
        if (wallet.bankOrServiceName != null && !wallet.bankOrServiceName.isEmpty()) {
            bankName = wallet.bankOrServiceName;
        }
        if (wallet.accountNumberLastFour != null && !wallet.accountNumberLastFour.isEmpty()) {
            bankName += (bankName.isEmpty() ? "" : " ") + "••" + wallet.accountNumberLastFour;
        }
        tvWalletBankName.setText(bankName);
        tvWalletBankName.setVisibility(bankName.isEmpty() ? View.GONE : View.VISIBLE);

        boolean hidden = walletRepo.isBalanceHidden();
        if (hidden) {
            tvWalletBalance.setText("••••••");
            tvWalletIncome.setText("•••");
            tvWalletExpenses.setText("•••");
        } else {
            if (wallet.isCreditCard()) {
                tvWalletBalance.setText("Owes " + currencyFormat.format(wallet.currentBalance));
            } else {
                tvWalletBalance.setText(currencyFormat.format(wallet.currentBalance));
            }
            double income = walletRepo.getWalletIncomeThisMonth(walletId, expenseRepo);
            double expenses = walletRepo.getWalletExpensesThisMonth(walletId, expenseRepo);
            tvWalletIncome.setText(currencyFormat.format(income));
            tvWalletExpenses.setText(currencyFormat.format(expenses));
        }

        tvDefaultBadge.setVisibility(wallet.isDefault ? View.VISIBLE : View.GONE);

        // Tint balance card with wallet color
        View balanceCard = findViewById(R.id.walletBalanceCard);
        try {
            // Don't fully tint — just leave the gradient background
        } catch (Exception ignored) {}
    }

    private void refreshCreditCardSection() {
        if (!wallet.isCreditCard()) {
            creditCardSection.setVisibility(View.GONE);
            return;
        }
        creditCardSection.setVisibility(View.VISIBLE);

        float usagePercent = (float) wallet.getCreditUsagePercent();
        creditUsageBar.setProgress((int) usagePercent);
        tvCreditUsage.setText(String.format(Locale.getDefault(), "%.0f%%", usagePercent));

        double available = wallet.getAvailableCredit();
        tvAvailableCredit.setText("Available: " + currencyFormat.format(available));

        if (wallet.billingCycleDate > 0) {
            int daysUntil = wallet.getDaysUntilBilling();
            tvBillingDate.setText("Billing in " + daysUntil + " days");
        } else {
            tvBillingDate.setText("No billing date set");
        }
    }

    private void refreshFilterChips() {
        filterChips.removeAllViews();
        String[] filters = {"All", "Income", "Expenses"};

        for (String filter : filters) {
            TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(12);
            boolean selected = filter.equals(currentFilter);
            chip.setTextColor(selected ? 0xFFC084FC : 0xFFD1D5DB);
            chip.setBackgroundResource(selected ? R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                currentFilter = filter;
                refreshFilterChips();
                refreshTransactions();
            });
            filterChips.addView(chip);
        }
    }

    private void refreshTransactions() {
        transactionsContainer.removeAllViews();
        ArrayList<Expense> expenses = walletRepo.getWalletExpenses(walletId, expenseRepo, currentFilter);
        boolean hidden = walletRepo.isBalanceHidden();

        if (expenses.isEmpty()) {
            tvNoTransactions.setVisibility(View.VISIBLE);
            return;
        }
        tvNoTransactions.setVisibility(View.GONE);

        for (Expense e : expenses) {
            transactionsContainer.addView(createTransactionCard(e, hidden));
        }

        // Also show transfers for this wallet
        ArrayList<WalletTransfer> transfers = walletRepo.getTransfersForWallet(walletId);
        if (!transfers.isEmpty() && "All".equals(currentFilter)) {
            // Add transfer header
            TextView header = new TextView(this);
            header.setText("TRANSFERS");
            header.setTextColor(0xFF7C3AED);
            header.setTextSize(11);
            header.setLetterSpacing(0.15f);
            header.setPadding(0, dp(16), 0, dp(8));
            transactionsContainer.addView(header);

            for (WalletTransfer t : transfers) {
                transactionsContainer.addView(createTransferItemCard(t, hidden));
            }
        }
    }

    private View createTransactionCard(Expense expense, boolean hidden) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.glass_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLP.bottomMargin = dp(8);
        card.setLayoutParams(cardLP);

        // Category icon
        TextView icon = new TextView(this);
        icon.setText(Expense.getCategoryIcon(expense.category));
        icon.setTextSize(20);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLP = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLP.leftMargin = dp(10);
        info.setLayoutParams(infoLP);

        TextView tvCat = new TextView(this);
        tvCat.setText(expense.category);
        tvCat.setTextColor(0xFFFFFFFF);
        tvCat.setTextSize(13);
        tvCat.setTypeface(null, Typeface.BOLD);
        info.addView(tvCat);

        if (expense.note != null && !expense.note.isEmpty()) {
            TextView tvNote = new TextView(this);
            tvNote.setText(expense.note);
            tvNote.setTextColor(0x80FFFFFF);
            tvNote.setTextSize(11);
            tvNote.setMaxLines(1);
            info.addView(tvNote);
        }

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFormat.format(new Date(expense.timestamp)));
        tvDate.setTextColor(0x60FFFFFF);
        tvDate.setTextSize(10);
        info.addView(tvDate);

        card.addView(info);

        // Amount
        TextView tvAmt = new TextView(this);
        if (hidden) {
            tvAmt.setText("•••");
            tvAmt.setTextColor(0x80FFFFFF);
        } else {
            String prefix = expense.isIncome ? "+" : "-";
            tvAmt.setText(prefix + currencyFormat.format(expense.amount));
            tvAmt.setTextColor(expense.isIncome ? 0xFF22C55E : 0xFFEF4444);
        }
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, Typeface.BOLD);
        card.addView(tvAmt);

        // Long press to delete
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete transaction?")
                .setMessage(currencyFormat.format(expense.amount) + " - " + expense.category)
                .setPositiveButton("Delete", (d, w) -> {
                    expenseRepo.deleteExpenseWithBalanceReverse(expense.id, walletRepo);
                    wallet = walletRepo.getById(walletId); // Refresh wallet data
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        return card;
    }

    private View createTransferItemCard(WalletTransfer transfer, boolean hidden) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.wallet_transfer_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLP.bottomMargin = dp(8);
        card.setLayoutParams(cardLP);

        // Arrow icon
        TextView arrow = new TextView(this);
        boolean isOutgoing = walletId.equals(transfer.fromWalletId);
        arrow.setText(isOutgoing ? "↗" : "↙");
        arrow.setTextSize(18);
        arrow.setTextColor(isOutgoing ? 0xFFEF4444 : 0xFF22C55E);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLP = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLP.leftMargin = dp(10);
        info.setLayoutParams(infoLP);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(isOutgoing
            ? "Transfer to " + transfer.toWalletName
            : "Transfer from " + transfer.fromWalletName);
        tvDesc.setTextColor(0xFFFFFFFF);
        tvDesc.setTextSize(13);
        info.addView(tvDesc);

        TextView tvDate = new TextView(this);
        tvDate.setText(transfer.getFormattedDate());
        tvDate.setTextColor(0x60FFFFFF);
        tvDate.setTextSize(10);
        info.addView(tvDate);

        card.addView(info);

        // Amount
        TextView tvAmt = new TextView(this);
        if (hidden) {
            tvAmt.setText("•••");
        } else {
            String prefix = isOutgoing ? "-" : "+";
            tvAmt.setText(prefix + transfer.getFormattedAmount());
        }
        tvAmt.setTextColor(isOutgoing ? 0xFFEF4444 : 0xFF22C55E);
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, Typeface.BOLD);
        card.addView(tvAmt);

        return card;
    }

    // ─── Context Menu ────────────────────────────────────────

    private void showContextMenu() {
        String[] options = {"Set as Default", "Archive", "Delete"};

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle(wallet.name)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        walletRepo.setDefault(walletId);
                        wallet = walletRepo.getById(walletId);
                        refreshAll();
                        Toast.makeText(this, "Set as default", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        walletRepo.archiveWallet(walletId);
                        Toast.makeText(this, "Wallet archived", Toast.LENGTH_SHORT).show();
                        finish();
                        break;
                    case 2:
                        if (wallet.isDefault) {
                            Toast.makeText(this, "Cannot delete default wallet", Toast.LENGTH_SHORT).show();
                        } else {
                            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                                .setTitle("Delete " + wallet.name + "?")
                                .setPositiveButton("Delete", (dd, ww) -> {
                                    walletRepo.deleteWallet(walletId);
                                    finish();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                        break;
                }
            })
            .show();
    }

    // ─── Pay Credit Card Bill ────────────────────────────────

    private void showPayBillDialog() {
        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();
        ArrayList<Wallet> payFrom = new ArrayList<>();
        for (Wallet w : wallets) {
            if (!w.id.equals(walletId) && !w.isCreditCard()) {
                payFrom.add(w);
            }
        }

        if (payFrom.isEmpty()) {
            Toast.makeText(this, "No non-credit-card wallets to pay from", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[payFrom.size()];
        for (int i = 0; i < payFrom.size(); i++) {
            names[i] = payFrom.get(i).getTypeIcon() + " " + payFrom.get(i).name
                + " (" + currencyFormat.format(payFrom.get(i).currentBalance) + ")";
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("Pay from which wallet?")
            .setItems(names, (d, which) -> {
                // Ask for amount — default to current balance (outstanding)
                android.widget.EditText etAmt = new android.widget.EditText(this);
                etAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                etAmt.setTextColor(0xFFFFFFFF);
                etAmt.setHintTextColor(0xFF4B5563);
                etAmt.setHint("Amount");
                etAmt.setText(String.valueOf(wallet.currentBalance));
                etAmt.setPadding(dp(16), dp(12), dp(16), dp(12));

                new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle("Payment Amount")
                    .setView(etAmt)
                    .setPositiveButton("Pay", (dd, ww) -> {
                        try {
                            double amount = Double.parseDouble(etAmt.getText().toString().trim());
                            if (amount <= 0) return;

                            walletRepo.markCreditCardBillPaid(walletId, payFrom.get(which).id, amount);
                            wallet = walletRepo.getById(walletId);
                            refreshAll();
                            Toast.makeText(this, "Payment of " + currencyFormat.format(amount) + " recorded",
                                Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException ignored) {}
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .show();
    }

    // ─── Utility ─────────────────────────────────────────────

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
