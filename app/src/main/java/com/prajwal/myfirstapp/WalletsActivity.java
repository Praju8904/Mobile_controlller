package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class WalletsActivity extends AppCompatActivity {

    private WalletRepository walletRepo;
    private ExpenseRepository expenseRepo;

    private TextView tvTotalBalance, tvTotalIncome, tvTotalExpenses, tvWalletCount;
    private LinearLayout walletsContainer, transfersContainer, archivedContainer;
    private View archivedSection, tvTransfersLabel;
    private TextView tvNoWallets;
    private boolean showingArchived = false;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallets);

        walletRepo = new WalletRepository(this);
        expenseRepo = new ExpenseRepository(this);

        // Take daily snapshot on open
        walletRepo.takeDailySnapshot();

        initViews();
        setupListeners();

        // Handle edit request from WalletDetailActivity
        String editWalletId = getIntent().getStringExtra("edit_wallet_id");
        if (editWalletId != null) {
            Wallet toEdit = walletRepo.getById(editWalletId);
            if (toEdit != null) {
                // Post to ensure views are ready
                findViewById(R.id.fabAddWallet).post(() -> showAddWalletDialog(toEdit));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvWalletCount = findViewById(R.id.tvWalletCount);
        walletsContainer = findViewById(R.id.walletsContainer);
        transfersContainer = findViewById(R.id.transfersContainer);
        archivedContainer = findViewById(R.id.archivedContainer);
        archivedSection = findViewById(R.id.archivedSection);
        tvTransfersLabel = findViewById(R.id.tvTransfersLabel);
        tvNoWallets = findViewById(R.id.tvNoWallets);
    }

    private void setupListeners() {
        findViewById(R.id.btnBackWallets).setOnClickListener(v -> finish());

        findViewById(R.id.fabAddWallet).setOnClickListener(v -> showAddWalletDialog(null));

        findViewById(R.id.btnTransfer).setOnClickListener(v -> {
            if (walletRepo.getActiveCount() < 2) {
                Toast.makeText(this, "Need at least 2 wallets to transfer", Toast.LENGTH_SHORT).show();
                return;
            }
            showTransferDialog();
        });

        findViewById(R.id.btnViewArchived).setOnClickListener(v -> {
            showingArchived = !showingArchived;
            archivedSection.setVisibility(showingArchived ? View.VISIBLE : View.GONE);
        });

        findViewById(R.id.btnToggleBalance).setOnClickListener(v -> {
            walletRepo.setBalanceHidden(!walletRepo.isBalanceHidden());
            refreshAll();
        });
    }

    // ─── Refresh All Sections ────────────────────────────────

    private void refreshAll() {
        refreshHeader();
        refreshWalletCards();
        refreshTransfers();
        refreshArchived();
        updateEmptyState();
    }

    private void refreshHeader() {
        boolean hidden = walletRepo.isBalanceHidden();
        double total = walletRepo.getTotalBalance();
        double income = walletRepo.getTotalIncomeThisMonth(expenseRepo);
        double expenses = walletRepo.getTotalExpensesThisMonth(expenseRepo);

        tvTotalBalance.setText(hidden ? "••••••" : currencyFormat.format(total));
        tvTotalIncome.setText(hidden ? "•••" : currencyFormat.format(income));
        tvTotalExpenses.setText(hidden ? "•••" : currencyFormat.format(expenses));
        tvWalletCount.setText(String.valueOf(walletRepo.getActiveCount()));
    }

    private void refreshWalletCards() {
        walletsContainer.removeAllViews();
        ArrayList<Wallet> active = walletRepo.getActiveWallets();
        boolean hidden = walletRepo.isBalanceHidden();

        for (Wallet w : active) {
            walletsContainer.addView(createWalletCard(w, hidden));
        }
    }

    private void refreshTransfers() {
        transfersContainer.removeAllViews();
        ArrayList<WalletTransfer> transfers = walletRepo.loadTransfers();
        boolean hidden = walletRepo.isBalanceHidden();

        boolean hasTransfers = !transfers.isEmpty();
        tvTransfersLabel.setVisibility(hasTransfers ? View.VISIBLE : View.GONE);
        transfersContainer.setVisibility(hasTransfers ? View.VISIBLE : View.GONE);

        // Show last 5 transfers
        int limit = Math.min(5, transfers.size());
        for (int i = 0; i < limit; i++) {
            transfersContainer.addView(createTransferCard(transfers.get(i), hidden));
        }
    }

    private void refreshArchived() {
        archivedContainer.removeAllViews();
        ArrayList<Wallet> archived = walletRepo.getArchivedWallets();
        boolean hidden = walletRepo.isBalanceHidden();

        for (Wallet w : archived) {
            archivedContainer.addView(createWalletCard(w, hidden));
        }
    }

    private void updateEmptyState() {
        boolean hasActive = walletRepo.getActiveCount() > 0;
        tvNoWallets.setVisibility(hasActive ? View.GONE : View.VISIBLE);
    }

    // ─── Wallet Card Builder ─────────────────────────────────

    private View createWalletCard(Wallet wallet, boolean hidden) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.wallet_item_bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLP.bottomMargin = dp(10);
        card.setLayoutParams(cardLP);

        // Color indicator
        View colorDot = new View(this);
        LinearLayout.LayoutParams dotLP = new LinearLayout.LayoutParams(dp(6), dp(40));
        dotLP.rightMargin = dp(14);
        colorDot.setLayoutParams(dotLP);
        try {
            colorDot.setBackgroundColor(wallet.colorHex);
        } catch (Exception e) {
            colorDot.setBackgroundColor(0xFF7C3AED);
        }
        card.addView(colorDot);

        // Icon
        TextView icon = new TextView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setTextSize(20);
        icon.setText(wallet.getTypeIcon());
        icon.setBackgroundResource(R.drawable.wallet_chip_bg);
        card.addView(icon);

        // Info column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLP = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLP.leftMargin = dp(12);
        info.setLayoutParams(infoLP);

        TextView tvName = new TextView(this);
        tvName.setText(wallet.name);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(tvName);

        String subtitle = wallet.type;
        if (wallet.bankOrServiceName != null && !wallet.bankOrServiceName.isEmpty()) {
            subtitle += " • " + wallet.bankOrServiceName;
        }
        if (wallet.accountNumberLastFour != null && !wallet.accountNumberLastFour.isEmpty()) {
            subtitle += " ••" + wallet.accountNumberLastFour;
        }
        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(0x80FFFFFF);
        tvSub.setTextSize(11);
        info.addView(tvSub);

        if (wallet.isDefault) {
            TextView def = new TextView(this);
            def.setText("DEFAULT");
            def.setTextColor(0xFF22C55E);
            def.setTextSize(9);
            def.setLetterSpacing(0.1f);
            info.addView(def);
        }

        card.addView(info);

        // Balance
        LinearLayout balCol = new LinearLayout(this);
        balCol.setOrientation(LinearLayout.VERTICAL);
        balCol.setGravity(android.view.Gravity.END);

        TextView tvBal = new TextView(this);
        if (hidden) {
            tvBal.setText("••••");
        } else {
            if (wallet.isCreditCard()) {
                tvBal.setText("-" + currencyFormat.format(wallet.currentBalance));
            } else {
                tvBal.setText(currencyFormat.format(wallet.currentBalance));
            }
        }
        tvBal.setTextColor(wallet.currentBalance >= 0 && !wallet.isCreditCard() ? 0xFFFFFFFF : 0xFFEF4444);
        tvBal.setTextSize(15);
        tvBal.setTypeface(null, android.graphics.Typeface.BOLD);
        balCol.addView(tvBal);

        if (wallet.isCreditCard() && wallet.creditLimit > 0 && !hidden) {
            TextView tvCredit = new TextView(this);
            tvCredit.setText("Limit: " + currencyFormat.format(wallet.creditLimit));
            tvCredit.setTextColor(0x80FFFFFF);
            tvCredit.setTextSize(10);
            balCol.addView(tvCredit);
        }

        card.addView(balCol);

        // Click → detail
        card.setOnClickListener(v -> {
            Intent intent = new Intent(WalletsActivity.this, WalletDetailActivity.class);
            intent.putExtra("wallet_id", wallet.id);
            startActivity(intent);
        });

        // Long press → context menu
        card.setOnLongClickListener(v -> {
            showWalletContextMenu(wallet);
            return true;
        });

        return card;
    }

    // ─── Transfer Card Builder ───────────────────────────────

    private View createTransferCard(WalletTransfer transfer, boolean hidden) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.wallet_transfer_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardLP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLP.bottomMargin = dp(8);
        card.setLayoutParams(cardLP);

        // Arrow icon
        TextView arrow = new TextView(this);
        arrow.setText("↔");
        arrow.setTextSize(18);
        arrow.setTextColor(0xFF7C3AED);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        arrow.setGravity(android.view.Gravity.CENTER);
        card.addView(arrow);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLP = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLP.leftMargin = dp(10);
        info.setLayoutParams(infoLP);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(transfer.getDescription());
        tvDesc.setTextColor(0xFFFFFFFF);
        tvDesc.setTextSize(13);
        info.addView(tvDesc);

        TextView tvDate = new TextView(this);
        tvDate.setText(transfer.getShortDate());
        tvDate.setTextColor(0x80FFFFFF);
        tvDate.setTextSize(11);
        info.addView(tvDate);

        card.addView(info);

        // Amount
        TextView tvAmt = new TextView(this);
        tvAmt.setText(hidden ? "•••" : transfer.getFormattedAmount());
        tvAmt.setTextColor(0xFFC084FC);
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvAmt);

        return card;
    }

    // ─── Wallet Context Menu ─────────────────────────────────

    private void showWalletContextMenu(Wallet wallet) {
        String[] options;
        if (wallet.isArchived) {
            options = new String[]{"Unarchive", "Set as Default", "Delete"};
        } else {
            options = new String[]{"Edit", "Set as Default", "Archive", "Delete"};
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle(wallet.name)
            .setItems(options, (d, which) -> {
                if (wallet.isArchived) {
                    switch (which) {
                        case 0: walletRepo.unarchiveWallet(wallet.id); refreshAll(); break;
                        case 1: walletRepo.setDefault(wallet.id); refreshAll(); break;
                        case 2: confirmDelete(wallet); break;
                    }
                } else {
                    switch (which) {
                        case 0: showAddWalletDialog(wallet); break;
                        case 1: walletRepo.setDefault(wallet.id); refreshAll(); break;
                        case 2: walletRepo.archiveWallet(wallet.id); refreshAll(); break;
                        case 3: confirmDelete(wallet); break;
                    }
                }
            })
            .show();
    }

    private void confirmDelete(Wallet wallet) {
        if (wallet.isDefault) {
            Toast.makeText(this, "Cannot delete the default wallet", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("Delete " + wallet.name + "?")
            .setMessage("This will permanently delete this wallet. Expenses linked to it will remain.")
            .setPositiveButton("Delete", (d, w) -> {
                walletRepo.deleteWallet(wallet.id);
                refreshAll();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ─── Add / Edit Wallet Dialog ────────────────────────────

    private void showAddWalletDialog(Wallet existing) {
        boolean isEdit = existing != null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_wallet, null);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Wire up views
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etName = dialogView.findViewById(R.id.etWalletName);
        EditText etBalance = dialogView.findViewById(R.id.etWalletBalance);
        EditText etLast4 = dialogView.findViewById(R.id.etAccountLast4);
        EditText etCreditLimit = dialogView.findViewById(R.id.etCreditLimit);
        EditText etBillingDate = dialogView.findViewById(R.id.etBillingDate);
        EditText etNotes = dialogView.findViewById(R.id.etWalletNotes);
        CheckBox cbInclude = dialogView.findViewById(R.id.cbIncludeInTotal);
        CheckBox cbDefault = dialogView.findViewById(R.id.cbSetDefault);
        LinearLayout walletTypeChips = dialogView.findViewById(R.id.walletTypeChips);
        LinearLayout bankChips = dialogView.findViewById(R.id.bankChips);
        LinearLayout colorPicker = dialogView.findViewById(R.id.colorPicker);
        View bankSection = dialogView.findViewById(R.id.bankSection);
        View creditCardFields = dialogView.findViewById(R.id.creditCardFields);
        TextView tvBankLabel = dialogView.findViewById(R.id.tvBankLabel);
        Button btnSave = dialogView.findViewById(R.id.btnSaveWallet);

        tvTitle.setText(isEdit ? "Edit Wallet" : "Add Wallet");
        btnSave.setText(isEdit ? "Save Changes" : "Add Wallet");

        // State holders
        final String[] selectedType = {isEdit ? existing.type : Wallet.WALLET_TYPES[0]};
        final String[] selectedBank = {isEdit ? existing.bankOrServiceName : ""};
        final int[] selectedColor = {isEdit ? existing.colorHex : Wallet.WALLET_COLORS[0]};

        // Type chips
        buildTypeChips(walletTypeChips, selectedType, bankSection, bankChips,
            tvBankLabel, creditCardFields, selectedBank);

        // Color chips
        buildColorChips(colorPicker, selectedColor);

        // Pre-fill if editing
        if (isEdit) {
            etName.setText(existing.name);
            etBalance.setText(String.valueOf(existing.currentBalance));
            etLast4.setText(existing.accountNumberLastFour);
            etNotes.setText(existing.notes);
            cbInclude.setChecked(existing.includeInTotalBalance);
            cbDefault.setChecked(existing.isDefault);

            if (existing.isCreditCard()) {
                creditCardFields.setVisibility(View.VISIBLE);
                if (existing.creditLimit > 0) etCreditLimit.setText(String.valueOf(existing.creditLimit));
                if (existing.billingCycleDate > 0) etBillingDate.setText(String.valueOf(existing.billingCycleDate));
            }

            // Update type + bank chips to show selected
            updateTypeChipSelection(walletTypeChips, existing.type);
            refreshBankChips(bankChips, existing.type, selectedBank);
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Required");
                return;
            }

            Wallet wallet = isEdit ? existing : new Wallet();
            wallet.name = name;
            wallet.type = selectedType[0];
            wallet.bankOrServiceName = selectedBank[0];
            wallet.colorHex = selectedColor[0];
            wallet.accountNumberLastFour = etLast4.getText().toString().trim();
            wallet.notes = etNotes.getText().toString().trim();
            wallet.includeInTotalBalance = cbInclude.isChecked();
            wallet.isDefault = cbDefault.isChecked();

            String balStr = etBalance.getText().toString().trim();
            if (!balStr.isEmpty()) {
                try {
                    wallet.currentBalance = Double.parseDouble(balStr);
                } catch (NumberFormatException e) {
                    etBalance.setError("Invalid number");
                    return;
                }
            }

            if (wallet.isCreditCard()) {
                String limitStr = etCreditLimit.getText().toString().trim();
                if (!limitStr.isEmpty()) {
                    try { wallet.creditLimit = Double.parseDouble(limitStr); } catch (Exception ignored) {}
                }
                String billingStr = etBillingDate.getText().toString().trim();
                if (!billingStr.isEmpty()) {
                    try { wallet.billingCycleDate = Integer.parseInt(billingStr); } catch (Exception ignored) {}
                }
            }

            wallet.updatedAt = System.currentTimeMillis();

            if (isEdit) {
                walletRepo.updateWallet(wallet);
            } else {
                walletRepo.addWallet(wallet);
            }

            dialog.dismiss();
            refreshAll();
            Toast.makeText(this, isEdit ? "Wallet updated" : "Wallet added", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void buildTypeChips(LinearLayout container, String[] selectedType,
                                 View bankSection, LinearLayout bankChips,
                                 TextView tvBankLabel, View creditCardFields,
                                 String[] selectedBank) {
        container.removeAllViews();
        for (int i = 0; i < Wallet.WALLET_TYPES.length; i++) {
            String type = Wallet.WALLET_TYPES[i];
            String icon = Wallet.WALLET_TYPE_ICONS[i];

            TextView chip = createChip(icon + " " + type, type.equals(selectedType[0]));
            chip.setOnClickListener(v -> {
                selectedType[0] = type;
                updateTypeChipSelection(container, type);

                // Show/hide bank section
                boolean showBank = "Bank Account".equals(type) || "Debit Card".equals(type)
                    || "Credit Card".equals(type) || "UPI".equals(type);
                bankSection.setVisibility(showBank ? View.VISIBLE : View.GONE);
                if (showBank) {
                    tvBankLabel.setText("UPI".equals(type) ? "UPI Service" : "Bank");
                    refreshBankChips(bankChips, type, selectedBank);
                }

                // Show/hide credit card fields
                creditCardFields.setVisibility("Credit Card".equals(type) ? View.VISIBLE : View.GONE);
            });
            container.addView(chip);
        }
    }

    private void refreshBankChips(LinearLayout container, String type, String[] selectedBank) {
        container.removeAllViews();
        String[] options = "UPI".equals(type) ? Wallet.COMMON_UPI_SERVICES : Wallet.COMMON_BANKS;
        for (String bank : options) {
            TextView chip = createChip(bank, bank.equals(selectedBank[0]));
            chip.setOnClickListener(v -> {
                selectedBank[0] = bank;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View c = container.getChildAt(j);
                    c.setBackgroundResource(R.drawable.wallet_chip_bg);
                    if (c instanceof TextView) ((TextView)c).setTextColor(0xFFD1D5DB);
                }
                chip.setBackgroundResource(R.drawable.wallet_chip_selected_bg);
                chip.setTextColor(0xFFC084FC);
            });
            container.addView(chip);
        }
    }

    private void buildColorChips(LinearLayout container, int[] selectedColor) {
        container.removeAllViews();
        for (int hex : Wallet.WALLET_COLORS) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
            lp.rightMargin = dp(8);
            dot.setLayoutParams(lp);
            dot.setBackgroundColor(hex);

            if (hex == selectedColor[0]) {
                dot.setAlpha(1f);
                dot.setScaleX(1.2f);
                dot.setScaleY(1.2f);
            } else {
                dot.setAlpha(0.5f);
            }

            dot.setOnClickListener(v -> {
                selectedColor[0] = hex;
                buildColorChips(container, selectedColor);
            });

            container.addView(dot);
        }
    }

    private void updateTypeChipSelection(LinearLayout container, String selected) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                String tag = (String) child.getTag();
                boolean sel = selected.equals(tag);
                child.setBackgroundResource(sel ? R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
                ((TextView)child).setTextColor(sel ? 0xFFC084FC : 0xFFD1D5DB);
            }
        }
    }

    private TextView createChip(String text, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setTextColor(selected ? 0xFFC084FC : 0xFFD1D5DB);
        chip.setBackgroundResource(selected ? R.drawable.wallet_chip_selected_bg : R.drawable.wallet_chip_bg);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        // Store type name as tag (strip icon prefix)
        String tag = text;
        for (String type : Wallet.WALLET_TYPES) {
            if (text.contains(type)) { tag = type; break; }
        }
        chip.setTag(tag);
        return chip;
    }

    // ─── Transfer Dialog ─────────────────────────────────────

    private void showTransferDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wallet_transfer, null);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();
        final int[] fromIndex = {0};
        final int[] toIndex = {wallets.size() > 1 ? 1 : 0};

        TextView tvFromName = dialogView.findViewById(R.id.tvFromWalletName);
        TextView tvFromBalance = dialogView.findViewById(R.id.tvFromWalletBalance);
        TextView tvFromIcon = dialogView.findViewById(R.id.tvFromWalletIcon);
        TextView tvToName = dialogView.findViewById(R.id.tvToWalletName);
        TextView tvToBalance = dialogView.findViewById(R.id.tvToWalletBalance);
        TextView tvToIcon = dialogView.findViewById(R.id.tvToWalletIcon);
        EditText etAmount = dialogView.findViewById(R.id.etTransferAmount);
        EditText etNotes = dialogView.findViewById(R.id.etTransferNotes);

        Runnable updateUI = () -> {
            Wallet from = wallets.get(fromIndex[0]);
            Wallet to = wallets.get(toIndex[0]);
            tvFromName.setText(from.name);
            tvFromBalance.setText("Balance: " + currencyFormat.format(from.currentBalance));
            tvFromIcon.setText(from.getTypeIcon());
            tvToName.setText(to.name);
            tvToBalance.setText("Balance: " + currencyFormat.format(to.currentBalance));
            tvToIcon.setText(to.getTypeIcon());
        };
        updateUI.run();

        // Wallet pickers
        View.OnClickListener fromPicker = v -> showWalletPicker(wallets, fromIndex[0], selected -> {
            fromIndex[0] = selected;
            updateUI.run();
        });
        dialogView.findViewById(R.id.fromWalletSelector).setOnClickListener(fromPicker);

        View.OnClickListener toPicker = v -> showWalletPicker(wallets, toIndex[0], selected -> {
            toIndex[0] = selected;
            updateUI.run();
        });
        dialogView.findViewById(R.id.toWalletSelector).setOnClickListener(toPicker);

        // Swap
        dialogView.findViewById(R.id.btnSwapWallets).setOnClickListener(v -> {
            int temp = fromIndex[0];
            fromIndex[0] = toIndex[0];
            toIndex[0] = temp;
            updateUI.run();
        });

        // Execute transfer
        dialogView.findViewById(R.id.btnExecuteTransfer).setOnClickListener(v -> {
            String amtStr = etAmount.getText().toString().trim();
            if (amtStr.isEmpty()) {
                etAmount.setError("Required");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amtStr);
            } catch (NumberFormatException e) {
                etAmount.setError("Invalid");
                return;
            }

            if (amount <= 0) {
                etAmount.setError("Must be positive");
                return;
            }

            if (fromIndex[0] == toIndex[0]) {
                Toast.makeText(this, "Cannot transfer to same wallet", Toast.LENGTH_SHORT).show();
                return;
            }

            Wallet from = wallets.get(fromIndex[0]);
            Wallet to = wallets.get(toIndex[0]);

            walletRepo.executeTransfer(from.id, to.id, amount,
                etNotes.getText().toString().trim());

            dialog.dismiss();
            refreshAll();
            Toast.makeText(this, "Transferred " + currencyFormat.format(amount), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showWalletPicker(ArrayList<Wallet> wallets, int currentIndex,
                                   WalletPickListener listener) {
        String[] names = new String[wallets.size()];
        for (int i = 0; i < wallets.size(); i++) {
            Wallet w = wallets.get(i);
            names[i] = w.getTypeIcon() + " " + w.name + " (" + currencyFormat.format(w.currentBalance) + ")";
        }

        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("Select Wallet")
            .setSingleChoiceItems(names, currentIndex, (d, which) -> {
                listener.onWalletSelected(which);
                d.dismiss();
            })
            .show();
    }

    interface WalletPickListener {
        void onWalletSelected(int index);
    }

    // ─── Utility ─────────────────────────────────────────────

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
