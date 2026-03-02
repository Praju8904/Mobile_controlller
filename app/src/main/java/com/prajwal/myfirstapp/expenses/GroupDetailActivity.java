package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.ExportService;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Detailed view of a single split group.
 * Tabs: Balances | Expenses | Settlements
 */
public class GroupDetailActivity extends AppCompatActivity {

    private SplitRepository splitRepo;
    private SplitGroup group;
    private String groupId;

    // Header
    private TextView tvGroupName, tvGroupMemberCount, tvTotalSpent, tvYourBalance;

    // Tabs
    private TextView tabBalances, tabExpenses, tabSettlements;
    private LinearLayout contentBalances, contentExpenses, contentSettlements;

    // Balances tab
    private LinearLayout debtsContainer, memberBalancesContainer;
    private TextView tvAllSettled;

    // Expenses tab
    private LinearLayout expensesContainer;
    private TextView tvNoExpenses;

    // Settlements tab
    private LinearLayout settlementsContainer;
    private TextView tvNoSettlements;

    private NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        splitRepo = new SplitRepository(this);
        groupId = getIntent().getStringExtra("group_id");

        initViews();
        setupTabs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        group = splitRepo.getGroupById(groupId);
        if (group == null) { finish(); return; }
        refreshAll();
    }

    private void initViews() {
        tvGroupName = findViewById(R.id.tvGroupName);
        tvGroupMemberCount = findViewById(R.id.tvGroupMemberCount);
        tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvYourBalance = findViewById(R.id.tvYourBalance);

        tabBalances = findViewById(R.id.tabBalances);
        tabExpenses = findViewById(R.id.tabExpenses);
        tabSettlements = findViewById(R.id.tabSettlements);
        contentBalances = findViewById(R.id.contentBalances);
        contentExpenses = findViewById(R.id.contentExpenses);
        contentSettlements = findViewById(R.id.contentSettlements);

        debtsContainer = findViewById(R.id.debtsContainer);
        memberBalancesContainer = findViewById(R.id.memberBalancesContainer);
        tvAllSettled = findViewById(R.id.tvAllSettled);
        expensesContainer = findViewById(R.id.expensesContainer);
        tvNoExpenses = findViewById(R.id.tvNoExpenses);
        settlementsContainer = findViewById(R.id.settlementsContainer);
        tvNoSettlements = findViewById(R.id.tvNoSettlements);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.fabAddExpense).setOnClickListener(v -> showAddExpenseDialog());

        findViewById(R.id.btnShare).setOnClickListener(v -> shareSummary());
        findViewById(R.id.btnMoreOptions).setOnClickListener(this::showOptionsMenu);
    }

    private void setupTabs() {
        View.OnClickListener tabClick = v -> {
            int id = v.getId();
            selectTab(id == R.id.tabBalances ? 0 : id == R.id.tabExpenses ? 1 : 2);
        };
        tabBalances.setOnClickListener(tabClick);
        tabExpenses.setOnClickListener(tabClick);
        tabSettlements.setOnClickListener(tabClick);
    }

    private void selectTab(int index) {
        // Reset all
        tabBalances.setTextColor(Color.parseColor("#9CA3AF"));
        tabExpenses.setTextColor(Color.parseColor("#9CA3AF"));
        tabSettlements.setTextColor(Color.parseColor("#9CA3AF"));
        tabBalances.setBackgroundResource(0);
        tabExpenses.setBackgroundResource(0);
        tabSettlements.setBackgroundResource(0);
        tabBalances.setTypeface(null, Typeface.NORMAL);
        tabExpenses.setTypeface(null, Typeface.NORMAL);
        tabSettlements.setTypeface(null, Typeface.NORMAL);
        contentBalances.setVisibility(View.GONE);
        contentExpenses.setVisibility(View.GONE);
        contentSettlements.setVisibility(View.GONE);

        TextView sel = index == 0 ? tabBalances : index == 1 ? tabExpenses : tabSettlements;
        LinearLayout content = index == 0 ? contentBalances : index == 1 ? contentExpenses : contentSettlements;
        sel.setTextColor(Color.WHITE);
        sel.setTypeface(null, Typeface.BOLD);
        sel.setBackgroundResource(R.drawable.split_chip_selected_bg);
        content.setVisibility(View.VISIBLE);
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH ALL
    // ═══════════════════════════════════════════════════════════

    private void refreshAll() {
        // Header
        tvGroupName.setText(group.name);
        tvGroupMemberCount.setText(group.getMemberCount() + " members");
        tvTotalSpent.setText(currFmt.format(group.totalExpenses));

        double myBal = splitRepo.getCurrentUserBalanceInGroup(groupId);
        if (Math.abs(myBal) < 0.01) {
            tvYourBalance.setText("Settled ✅");
            tvYourBalance.setTextColor(Color.parseColor("#22C55E"));
        } else if (myBal > 0) {
            tvYourBalance.setText("+" + currFmt.format(myBal));
            tvYourBalance.setTextColor(Color.parseColor("#22C55E"));
        } else {
            tvYourBalance.setText("-" + currFmt.format(Math.abs(myBal)));
            tvYourBalance.setTextColor(Color.parseColor("#EF4444"));
        }

        refreshBalancesTab();
        refreshExpensesTab();
        refreshSettlementsTab();
    }

    // ═══════════════════════════════════════════════════════════
    //  BALANCES TAB
    // ═══════════════════════════════════════════════════════════

    private void refreshBalancesTab() {
        debtsContainer.removeAllViews();
        memberBalancesContainer.removeAllViews();

        ArrayList<SplitRepository.DebtTransaction> debts = splitRepo.getSimplifiedDebts(groupId);

        if (debts.isEmpty()) {
            tvAllSettled.setVisibility(View.VISIBLE);
        } else {
            tvAllSettled.setVisibility(View.GONE);
            for (SplitRepository.DebtTransaction dt : debts) {
                debtsContainer.addView(buildDebtCard(dt));
            }
        }

        // Member balances
        Map<String, Double> balances = splitRepo.calculateMemberBalances(groupId);
        for (SplitMember m : group.members) {
            Double bal = balances.get(m.id);
            if (bal == null) bal = 0.0;
            memberBalancesContainer.addView(buildMemberBalanceRow(m, bal));
        }
    }

    private View buildDebtCard(SplitRepository.DebtTransaction dt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.split_debt_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        String fromName = group.getMemberName(dt.fromMemberId);
        String toName = group.getMemberName(dt.toMemberId);

        // From avatar
        SplitMember fromMember = group.getMemberById(dt.fromMemberId);
        card.addView(buildAvatarView(fromMember, dp(32)));

        // Text
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLP.leftMargin = dp(10);
        textLP.rightMargin = dp(10);
        textCol.setLayoutParams(textLP);

        TextView tvDebt = new TextView(this);
        tvDebt.setText(fromName + " → " + toName);
        tvDebt.setTextColor(Color.WHITE);
        tvDebt.setTextSize(14);
        tvDebt.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvDebt);

        TextView tvAmt = new TextView(this);
        tvAmt.setText(currFmt.format(dt.amount));
        tvAmt.setTextColor(Color.parseColor("#A855F7"));
        tvAmt.setTextSize(13);
        textCol.addView(tvAmt);

        card.addView(textCol);

        // Settle button (only if current user is involved)
        SplitMember me = group.getCurrentUser();
        if (me != null && (dt.fromMemberId.equals(me.id) || dt.toMemberId.equals(me.id))) {
            TextView btnSettle = new TextView(this);
            btnSettle.setText("Settle");
            btnSettle.setTextColor(Color.WHITE);
            btnSettle.setTextSize(12);
            btnSettle.setTypeface(null, Typeface.BOLD);
            btnSettle.setBackgroundResource(R.drawable.split_settle_btn_bg);
            btnSettle.setPadding(dp(14), dp(6), dp(14), dp(6));
            btnSettle.setGravity(Gravity.CENTER);
            btnSettle.setOnClickListener(v -> showSettleDialog(dt));
            card.addView(btnSettle);
        }

        return card;
    }

    private View buildMemberBalanceRow(SplitMember member, double balance) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);

        // Avatar
        row.addView(buildAvatarView(member, dp(28)));

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(member.isCurrentUser ? "You" : member.name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        LinearLayout.LayoutParams nameLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameLP.leftMargin = dp(10);
        tvName.setLayoutParams(nameLP);
        row.addView(tvName);

        // Balance
        TextView tvBal = new TextView(this);
        if (Math.abs(balance) < 0.01) {
            tvBal.setText("Settled");
            tvBal.setTextColor(Color.parseColor("#6B7280"));
        } else if (balance > 0) {
            tvBal.setText("gets " + currFmt.format(balance));
            tvBal.setTextColor(Color.parseColor("#22C55E"));
        } else {
            tvBal.setText("owes " + currFmt.format(Math.abs(balance)));
            tvBal.setTextColor(Color.parseColor("#EF4444"));
        }
        tvBal.setTextSize(13);
        tvBal.setTypeface(null, Typeface.BOLD);
        row.addView(tvBal);

        return row;
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPENSES TAB
    // ═══════════════════════════════════════════════════════════

    private void refreshExpensesTab() {
        expensesContainer.removeAllViews();
        ArrayList<SplitExpense> expenses = splitRepo.getExpensesForGroup(groupId);

        tvNoExpenses.setVisibility(expenses.isEmpty() ? View.VISIBLE : View.GONE);

        for (SplitExpense exp : expenses) {
            expensesContainer.addView(buildExpenseCard(exp));
        }
    }

    private View buildExpenseCard(SplitExpense exp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.split_group_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        // Category icon
        TextView tvIcon = new TextView(this);
        tvIcon.setText(exp.getCategoryIcon());
        tvIcon.setTextSize(24);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(tvIcon);

        // Text column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLP.leftMargin = dp(10);
        textCol.setLayoutParams(textLP);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(exp.title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setMaxLines(1);
        textCol.addView(tvTitle);

        String paidBy = group.getMemberName(exp.paidByMemberId);
        TextView tvPaid = new TextView(this);
        tvPaid.setText("Paid by " + paidBy + " · " + exp.getSplitSummary(group.getMemberCount()));
        tvPaid.setTextColor(Color.parseColor("#9CA3AF"));
        tvPaid.setTextSize(11);
        tvPaid.setMaxLines(1);
        textCol.addView(tvPaid);

        card.addView(textCol);

        // Amount
        TextView tvAmt = new TextView(this);
        tvAmt.setText(currFmt.format(exp.totalAmount));
        tvAmt.setTextColor(Color.WHITE);
        tvAmt.setTextSize(15);
        tvAmt.setTypeface(null, Typeface.BOLD);
        card.addView(tvAmt);

        card.setOnClickListener(v -> {
            showExpenseDetail(exp);
        });

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTLEMENTS TAB
    // ═══════════════════════════════════════════════════════════

    private void refreshSettlementsTab() {
        settlementsContainer.removeAllViews();
        ArrayList<Settlement> settlements = splitRepo.getSettlementsForGroup(groupId);

        tvNoSettlements.setVisibility(settlements.isEmpty() ? View.VISIBLE : View.GONE);

        for (Settlement s : settlements) {
            settlementsContainer.addView(buildSettlementCard(s));
        }
    }

    private View buildSettlementCard(Settlement s) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.split_debt_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        // Icon
        TextView tvIcon = new TextView(this);
        tvIcon.setText("💸");
        tvIcon.setTextSize(20);
        tvIcon.setGravity(Gravity.CENTER);
        tvIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        card.addView(tvIcon);

        // Text
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLP.leftMargin = dp(10);
        textCol.setLayoutParams(textLP);

        String fromName = group.getMemberName(s.fromMemberId);
        String toName = group.getMemberName(s.toMemberId);

        TextView tvSettlement = new TextView(this);
        tvSettlement.setText(fromName + " paid " + toName);
        tvSettlement.setTextColor(Color.WHITE);
        tvSettlement.setTextSize(14);
        textCol.addView(tvSettlement);

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFmt.format(new Date(s.date)));
        tvDate.setTextColor(Color.parseColor("#6B7280"));
        tvDate.setTextSize(11);
        textCol.addView(tvDate);

        card.addView(textCol);

        // Amount
        TextView tvAmt = new TextView(this);
        tvAmt.setText(currFmt.format(s.amount));
        tvAmt.setTextColor(Color.parseColor("#22C55E"));
        tvAmt.setTextSize(15);
        tvAmt.setTypeface(null, Typeface.BOLD);
        card.addView(tvAmt);

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  ADD EXPENSE DIALOG
    // ═══════════════════════════════════════════════════════════

    private void showAddExpenseDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_add_split_expense, null);
        dialog.setContentView(sheet);

        EditText etTitle = sheet.findViewById(R.id.etTitle);
        EditText etAmount = sheet.findViewById(R.id.etAmount);
        EditText etNotes = sheet.findViewById(R.id.etNotes);
        LinearLayout paidByContainer = sheet.findViewById(R.id.paidByContainer);
        LinearLayout categoryContainer = sheet.findViewById(R.id.categoryContainer);
        LinearLayout splitDetailsContainer = sheet.findViewById(R.id.splitDetailsContainer);
        TextView tvSplitPreview = sheet.findViewById(R.id.tvSplitPreview);

        TextView btnSplitEqual = sheet.findViewById(R.id.btnSplitEqual);
        TextView btnSplitPercentage = sheet.findViewById(R.id.btnSplitPercentage);
        TextView btnSplitCustom = sheet.findViewById(R.id.btnSplitCustom);
        TextView btnSplitShares = sheet.findViewById(R.id.btnSplitShares);

        // State
        final String[] selectedPaidBy = {group.getCurrentUser() != null ? group.getCurrentUser().id : group.members.get(0).id};
        final String[] selectedCategory = {Expense.CATEGORIES[0]};
        final String[] selectedSplitType = {SplitExpense.SPLIT_EQUAL};
        final ArrayList<EditText> splitInputs = new ArrayList<>();

        // Populate Paid By
        for (SplitMember m : group.members) {
            TextView chip = createMemberChip(m, m.id.equals(selectedPaidBy[0]));
            chip.setOnClickListener(v -> {
                selectedPaidBy[0] = m.id;
                for (int i = 0; i < paidByContainer.getChildCount(); i++) {
                    View c = paidByContainer.getChildAt(i);
                    boolean sel = (c.getTag() != null && c.getTag().equals(m.id));
                    c.setBackgroundResource(sel ? R.drawable.split_chip_selected_bg : R.drawable.split_chip_bg);
                }
            });
            chip.setTag(m.id);
            paidByContainer.addView(chip);
        }

        // Populate Categories
        for (int i = 0; i < Expense.CATEGORIES.length; i++) {
            String cat = Expense.CATEGORIES[i];
            String icon = Expense.CATEGORY_ICONS[i];
            TextView chip = new TextView(this);
            chip.setText(icon + " " + cat);
            chip.setTextColor(i == 0 ? Color.WHITE : Color.parseColor("#9CA3AF"));
            chip.setTextSize(12);
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setBackgroundResource(i == 0 ? R.drawable.split_chip_selected_bg : R.drawable.split_chip_bg);
            LinearLayout.LayoutParams catLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            catLP.rightMargin = dp(6);
            chip.setLayoutParams(catLP);
            chip.setTag(cat);
            chip.setOnClickListener(v -> {
                selectedCategory[0] = cat;
                for (int j = 0; j < categoryContainer.getChildCount(); j++) {
                    View c = categoryContainer.getChildAt(j);
                    boolean sel = cat.equals(c.getTag());
                    c.setBackgroundResource(sel ? R.drawable.split_chip_selected_bg : R.drawable.split_chip_bg);
                    ((TextView) c).setTextColor(sel ? Color.WHITE : Color.parseColor("#9CA3AF"));
                }
            });
            categoryContainer.addView(chip);
        }

        // Split type toggle
        TextView[] splitBtns = {btnSplitEqual, btnSplitPercentage, btnSplitCustom, btnSplitShares};
        String[] splitTypes = {SplitExpense.SPLIT_EQUAL, SplitExpense.SPLIT_PERCENTAGE, SplitExpense.SPLIT_CUSTOM, SplitExpense.SPLIT_SHARES};

        for (int i = 0; i < splitBtns.length; i++) {
            final int idx = i;
            splitBtns[i].setOnClickListener(v -> {
                selectedSplitType[0] = splitTypes[idx];
                for (int j = 0; j < splitBtns.length; j++) {
                    splitBtns[j].setTextColor(j == idx ? Color.WHITE : Color.parseColor("#9CA3AF"));
                    splitBtns[j].setTypeface(null, j == idx ? Typeface.BOLD : Typeface.NORMAL);
                    splitBtns[j].setBackgroundResource(j == idx ? R.drawable.export_format_selected_bg : 0);
                }
                buildSplitDetails(splitDetailsContainer, splitInputs, selectedSplitType[0], tvSplitPreview, etAmount);
            });
        }

        // Initial split details (equal)
        buildSplitDetails(splitDetailsContainer, splitInputs, SplitExpense.SPLIT_EQUAL, tvSplitPreview, etAmount);

        // Add expense button
        sheet.findViewById(R.id.btnAddExpense).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();

            if (TextUtils.isEmpty(title)) {
                Toast.makeText(this, "Enter a title", Toast.LENGTH_SHORT).show(); return;
            }
            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(this, "Enter an amount", Toast.LENGTH_SHORT).show(); return;
            }

            double amount;
            try { amount = Double.parseDouble(amountStr); } catch (Exception e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); return;
            }
            if (amount <= 0) {
                Toast.makeText(this, "Amount must be positive", Toast.LENGTH_SHORT).show(); return;
            }

            SplitExpense expense = new SplitExpense();
            expense.groupId = groupId;
            expense.title = title;
            expense.totalAmount = amount;
            expense.categoryId = selectedCategory[0];
            expense.paidByMemberId = selectedPaidBy[0];
            expense.splitType = selectedSplitType[0];
            expense.notes = etNotes.getText().toString().trim();
            expense.date = System.currentTimeMillis();

            // Build splits based on type
            expense.splits = buildSplitsFromInputs(selectedSplitType[0], amount, splitInputs);
            if (expense.splits == null) return; // validation failed

            splitRepo.addExpense(expense);
            group = splitRepo.getGroupById(groupId);
            dialog.dismiss();
            refreshAll();
            Toast.makeText(this, "Expense added!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void buildSplitDetails(LinearLayout container, ArrayList<EditText> inputs, String splitType, TextView preview, EditText etAmount) {
        container.removeAllViews();
        inputs.clear();

        if (SplitExpense.SPLIT_EQUAL.equals(splitType)) {
            // Equal split — just show members, no inputs
            preview.setText("Split equally among " + group.getMemberCount() + " members");
            for (SplitMember m : group.members) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(6), dp(4), dp(6));

                row.addView(buildAvatarView(m, dp(24)));

                TextView tv = new TextView(this);
                tv.setText("  " + (m.isCurrentUser ? "You" : m.name));
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(13);
                row.addView(tv);

                container.addView(row);
            }
        } else {
            // Percentage / Custom / Shares — show member + input field
            String hint = SplitExpense.SPLIT_PERCENTAGE.equals(splitType) ? "%" :
                         SplitExpense.SPLIT_SHARES.equals(splitType) ? "shares" : "₹ amount";
            preview.setText(SplitExpense.SPLIT_PERCENTAGE.equals(splitType) ? "Must total 100%" :
                          SplitExpense.SPLIT_SHARES.equals(splitType) ? "Enter share ratios" : "Enter each member's amount");

            for (SplitMember m : group.members) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(4), dp(4), dp(4));

                row.addView(buildAvatarView(m, dp(24)));

                TextView tv = new TextView(this);
                tv.setText("  " + (m.isCurrentUser ? "You" : m.name));
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(13);
                LinearLayout.LayoutParams tvLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tv.setLayoutParams(tvLP);
                row.addView(tv);

                EditText et = new EditText(this);
                et.setHint(hint);
                et.setTextColor(Color.WHITE);
                et.setHintTextColor(Color.parseColor("#6B7280"));
                et.setTextSize(13);
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                et.setBackgroundResource(R.drawable.edit_text_background);
                et.setPadding(dp(10), dp(4), dp(10), dp(4));
                et.setTag(m.id);
                LinearLayout.LayoutParams etLP = new LinearLayout.LayoutParams(dp(90), dp(36));
                et.setLayoutParams(etLP);
                row.addView(et);
                inputs.add(et);

                container.addView(row);
            }
        }
    }

    private ArrayList<MemberSplit> buildSplitsFromInputs(String splitType, double totalAmount, ArrayList<EditText> inputs) {
        ArrayList<MemberSplit> splits = new ArrayList<>();

        if (SplitExpense.SPLIT_EQUAL.equals(splitType)) {
            double each = totalAmount / group.getMemberCount();
            each = Math.round(each * 100.0) / 100.0;
            for (SplitMember m : group.members) {
                MemberSplit ms = new MemberSplit(m.id, each);
                ms.percentage = 100.0 / group.getMemberCount();
                splits.add(ms);
            }
        } else if (SplitExpense.SPLIT_PERCENTAGE.equals(splitType)) {
            double totalPct = 0;
            for (EditText et : inputs) {
                String val = et.getText().toString().trim();
                if (TextUtils.isEmpty(val)) {
                    Toast.makeText(this, "Fill in all percentages", Toast.LENGTH_SHORT).show();
                    return null;
                }
                double pct = Double.parseDouble(val);
                totalPct += pct;
                MemberSplit ms = new MemberSplit((String) et.getTag(), Math.round(totalAmount * pct / 100.0 * 100.0) / 100.0);
                ms.percentage = pct;
                splits.add(ms);
            }
            if (Math.abs(totalPct - 100) > 0.5) {
                Toast.makeText(this, "Percentages must total 100%", Toast.LENGTH_SHORT).show();
                return null;
            }
        } else if (SplitExpense.SPLIT_CUSTOM.equals(splitType)) {
            double sum = 0;
            for (EditText et : inputs) {
                String val = et.getText().toString().trim();
                if (TextUtils.isEmpty(val)) {
                    Toast.makeText(this, "Fill in all amounts", Toast.LENGTH_SHORT).show();
                    return null;
                }
                double amt = Double.parseDouble(val);
                sum += amt;
                splits.add(new MemberSplit((String) et.getTag(), amt));
            }
            if (Math.abs(sum - totalAmount) > 1.0) {
                Toast.makeText(this, "Amounts must total " + currFmt.format(totalAmount), Toast.LENGTH_SHORT).show();
                return null;
            }
        } else if (SplitExpense.SPLIT_SHARES.equals(splitType)) {
            double totalShares = 0;
            ArrayList<Double> shareVals = new ArrayList<>();
            for (EditText et : inputs) {
                String val = et.getText().toString().trim();
                if (TextUtils.isEmpty(val)) {
                    Toast.makeText(this, "Fill in all shares", Toast.LENGTH_SHORT).show();
                    return null;
                }
                double shares = Double.parseDouble(val);
                totalShares += shares;
                shareVals.add(shares);
            }
            if (totalShares == 0) {
                Toast.makeText(this, "Shares cannot all be zero", Toast.LENGTH_SHORT).show();
                return null;
            }
            for (int i = 0; i < inputs.size(); i++) {
                double ratio = shareVals.get(i) / totalShares;
                MemberSplit ms = new MemberSplit((String) inputs.get(i).getTag(),
                        Math.round(totalAmount * ratio * 100.0) / 100.0);
                ms.shares = shareVals.get(i).intValue();
                splits.add(ms);
            }
        }

        return splits;
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPENSE DETAIL
    // ═══════════════════════════════════════════════════════════

    private void showExpenseDetail(SplitExpense exp) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle(exp.getCategoryIcon() + " " + exp.title);

        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(currFmt.format(exp.totalAmount)).append("\n");
        sb.append("Paid by: ").append(group.getMemberName(exp.paidByMemberId)).append("\n");
        sb.append("Date: ").append(dateFmt.format(new Date(exp.date))).append("\n");
        sb.append("Split: ").append(exp.splitType).append("\n\n");

        sb.append("Breakdown:\n");
        for (MemberSplit ms : exp.splits) {
            sb.append("  • ").append(group.getMemberName(ms.memberId))
              .append(": ").append(currFmt.format(ms.amountOwed)).append("\n");
        }

        if (!TextUtils.isEmpty(exp.notes)) {
            sb.append("\nNotes: ").append(exp.notes);
        }

        builder.setMessage(sb.toString());
        builder.setPositiveButton("Close", null);
        builder.setNeutralButton("Delete", (d, w) -> {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setTitle("Delete Expense")
                .setMessage("Delete \"" + exp.title + "\"?")
                .setPositiveButton("Delete", (d2, w2) -> {
                    splitRepo.deleteExpense(exp.id);
                    group = splitRepo.getGroupById(groupId);
                    refreshAll();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTLE UP DIALOG
    // ═══════════════════════════════════════════════════════════

    private void showSettleDialog(SplitRepository.DebtTransaction dt) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_settle_up, null);
        dialog.setContentView(sheet);

        SplitMember fromMember = group.getMemberById(dt.fromMemberId);
        SplitMember toMember = group.getMemberById(dt.toMemberId);

        TextView tvFromAvatar = sheet.findViewById(R.id.tvFromAvatar);
        TextView tvFromName = sheet.findViewById(R.id.tvFromName);
        TextView tvToAvatar = sheet.findViewById(R.id.tvToAvatar);
        TextView tvToName = sheet.findViewById(R.id.tvToName);
        EditText etAmount = sheet.findViewById(R.id.etSettleAmount);
        EditText etNotes = sheet.findViewById(R.id.etSettleNotes);

        if (fromMember != null) {
            tvFromAvatar.setText(fromMember.getInitials());
            setAvatarBg(tvFromAvatar, fromMember.avatarColorHex);
            tvFromName.setText(fromMember.isCurrentUser ? "You" : fromMember.name);
        }
        if (toMember != null) {
            tvToAvatar.setText(toMember.getInitials());
            setAvatarBg(tvToAvatar, toMember.avatarColorHex);
            tvToName.setText(toMember.isCurrentUser ? "You" : toMember.name);
        }

        etAmount.setText(String.format(Locale.US, "%.2f", dt.amount));

        sheet.findViewById(R.id.btnConfirmSettle).setOnClickListener(v -> {
            String amtStr = etAmount.getText().toString().trim();
            if (TextUtils.isEmpty(amtStr)) {
                Toast.makeText(this, "Enter an amount", Toast.LENGTH_SHORT).show(); return;
            }
            double amount;
            try { amount = Double.parseDouble(amtStr); } catch (Exception e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); return;
            }
            if (amount <= 0) return;

            Settlement settlement = new Settlement();
            settlement.groupId = groupId;
            settlement.fromMemberId = dt.fromMemberId;
            settlement.toMemberId = dt.toMemberId;
            settlement.amount = amount;
            settlement.notes = etNotes.getText().toString().trim();
            settlement.date = System.currentTimeMillis();
            settlement.isConfirmed = true;

            splitRepo.addSettlement(settlement);

            // Check if group is fully settled
            if (splitRepo.isGroupFullySettled(groupId)) {
                splitRepo.settleGroup(groupId);
                Toast.makeText(this, "🎉 Group fully settled!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Settlement recorded!", Toast.LENGTH_SHORT).show();
            }

            group = splitRepo.getGroupById(groupId);
            dialog.dismiss();
            refreshAll();
        });

        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════
    //  OPTIONS MENU
    // ═══════════════════════════════════════════════════════════

    private void showOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Edit Group");
        popup.getMenu().add(0, 2, 0, "Add Member");
        if (!group.isArchived) popup.getMenu().add(0, 3, 0, "Archive Group");
        else popup.getMenu().add(0, 4, 0, "Unarchive Group");
        popup.getMenu().add(0, 5, 0, "Delete Group");
        popup.getMenu().add(0, 6, 0, "Export Group Data");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showEditGroupDialog(); return true;
                case 2: showAddMemberDialog(); return true;
                case 3:
                    splitRepo.archiveGroup(groupId);
                    Toast.makeText(this, "Group archived", Toast.LENGTH_SHORT).show();
                    finish();
                    return true;
                case 4:
                    splitRepo.unarchiveGroup(groupId);
                    group = splitRepo.getGroupById(groupId);
                    refreshAll();
                    Toast.makeText(this, "Group unarchived", Toast.LENGTH_SHORT).show();
                    return true;
                case 5:
                    new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                        .setTitle("Delete Group")
                        .setMessage("Delete \"" + group.name + "\" and all its expenses?")
                        .setPositiveButton("Delete", (d, w) -> {
                            splitRepo.deleteGroup(groupId);
                            Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                case 6:
                    showExportDialog();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Edit Group");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        EditText etName = new EditText(this);
        etName.setHint("Group name");
        etName.setText(group.name);
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(Color.parseColor("#6B7280"));
        etName.setBackgroundResource(R.drawable.edit_text_background);
        etName.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.addView(etName);

        EditText etDesc = new EditText(this);
        etDesc.setHint("Description");
        etDesc.setText(group.description);
        etDesc.setTextColor(Color.WHITE);
        etDesc.setHintTextColor(Color.parseColor("#6B7280"));
        etDesc.setBackgroundResource(R.drawable.edit_text_background);
        etDesc.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams descLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLP.topMargin = dp(12);
        etDesc.setLayoutParams(descLP);
        layout.addView(etDesc);

        builder.setView(layout);
        builder.setPositiveButton("Save", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                group.name = name;
                group.description = etDesc.getText().toString().trim();
                splitRepo.updateGroup(group);
                refreshAll();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAddMemberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle("Add Member");

        EditText etName = new EditText(this);
        etName.setHint("Member name");
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(Color.parseColor("#6B7280"));
        etName.setBackgroundResource(R.drawable.edit_text_background);
        etName.setPadding(dp(12), dp(10), dp(12), dp(10));
        etName.setInputType(android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(24), dp(16), dp(24), dp(8));
        wrap.addView(etName);

        builder.setView(wrap);
        builder.setPositiveButton("Add", (d, w) -> {
            String name = etName.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                SplitMember newMember = SplitMember.create(name, null, group.members.size());
                group.members.add(newMember);
                splitRepo.updateGroup(group);
                refreshAll();
                Toast.makeText(this, name + " added!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════
    //  SHARE & EXPORT
    // ═══════════════════════════════════════════════════════════

    private void shareSummary() {
        String summary = splitRepo.generateShareSummary(groupId);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, summary);
        startActivity(Intent.createChooser(intent, "Share Summary"));
    }

    private void showExportDialog() {
        // Delegate to ExportService
        ExportService exportService = new ExportService(this);
        exportService.showExportDialog(groupId);
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private TextView buildAvatarView(SplitMember member, int size) {
        TextView av = new TextView(this);
        if (member != null) {
            av.setText(member.getInitials());
            setAvatarBg(av, member.avatarColorHex);
        } else {
            av.setText("?");
            setAvatarBg(av, Color.parseColor("#6B7280"));
        }
        av.setTextColor(Color.WHITE);
        av.setTextSize(size > dp(30) ? 12 : 9);
        av.setGravity(Gravity.CENTER);
        av.setTypeface(null, Typeface.BOLD);
        av.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return av;
    }

    private void setAvatarBg(TextView tv, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        tv.setBackground(gd);
    }

    private TextView createMemberChip(SplitMember m, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(m.isCurrentUser ? "You" : m.name);
        chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#9CA3AF"));
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setBackgroundResource(selected ? R.drawable.split_chip_selected_bg : R.drawable.split_chip_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
