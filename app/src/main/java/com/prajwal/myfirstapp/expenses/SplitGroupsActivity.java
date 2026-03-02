package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Split Bills home: shows overall balance summary, active groups, archived groups.
 * Tap a group → GroupDetailActivity. FAB → create new group.
 */
public class SplitGroupsActivity extends AppCompatActivity {

    private SplitRepository splitRepo;

    private TextView tvNetBalance, tvOwedToYou, tvYouOwe;
    private LinearLayout activeGroupsContainer, archivedGroupsContainer;
    private TextView tvNoGroups, tvArchivedLabel, tvActiveLabel;

    private NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_split_groups);

        splitRepo = new SplitRepository(this);

        initViews();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        tvNetBalance = findViewById(R.id.tvNetBalance);
        tvOwedToYou = findViewById(R.id.tvOwedToYou);
        tvYouOwe = findViewById(R.id.tvYouOwe);
        activeGroupsContainer = findViewById(R.id.activeGroupsContainer);
        archivedGroupsContainer = findViewById(R.id.archivedGroupsContainer);
        tvNoGroups = findViewById(R.id.tvNoGroups);
        tvArchivedLabel = findViewById(R.id.tvArchivedLabel);
        tvActiveLabel = findViewById(R.id.tvActiveLabel);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.fabNewGroup).setOnClickListener(v -> showCreateGroupSheet());
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH
    // ═══════════════════════════════════════════════════════════

    private void refreshAll() {
        refreshSummary();
        refreshGroups();
    }

    private void refreshSummary() {
        double net = splitRepo.getCurrentUserNetBalance();
        double owedToYou = splitRepo.getTotalOwedToYou();
        double youOwe = splitRepo.getTotalYouOwe();

        tvNetBalance.setText(currFmt.format(Math.abs(net)));
        if (net >= 0) {
            tvNetBalance.setTextColor(Color.parseColor("#22C55E"));
        } else {
            tvNetBalance.setTextColor(Color.parseColor("#EF4444"));
        }
        tvOwedToYou.setText(currFmt.format(owedToYou));
        tvYouOwe.setText(currFmt.format(youOwe));
    }

    private void refreshGroups() {
        activeGroupsContainer.removeAllViews();
        archivedGroupsContainer.removeAllViews();

        ArrayList<SplitGroup> active = splitRepo.getActiveGroups();
        ArrayList<SplitGroup> archived = splitRepo.getArchivedOrSettledGroups();

        tvNoGroups.setVisibility(active.isEmpty() && archived.isEmpty() ? View.VISIBLE : View.GONE);
        tvActiveLabel.setVisibility(active.isEmpty() ? View.GONE : View.VISIBLE);

        for (SplitGroup g : active) {
            activeGroupsContainer.addView(buildGroupCard(g, false));
        }

        tvArchivedLabel.setVisibility(archived.isEmpty() ? View.GONE : View.VISIBLE);
        for (SplitGroup g : archived) {
            archivedGroupsContainer.addView(buildGroupCard(g, true));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GROUP CARD
    // ═══════════════════════════════════════════════════════════

    private View buildGroupCard(SplitGroup group, boolean isArchived) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.split_group_card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // Top row: name + member count
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(group.name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLP);
        topRow.addView(tvName);

        // Member avatars row
        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setOrientation(LinearLayout.HORIZONTAL);
        int shown = Math.min(group.members.size(), 4);
        for (int i = 0; i < shown; i++) {
            SplitMember m = group.members.get(i);
            TextView av = new TextView(this);
            av.setText(m.getInitials());
            av.setTextColor(Color.WHITE);
            av.setTextSize(9);
            av.setGravity(Gravity.CENTER);
            av.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(m.avatarColorHex);
            gd.setSize(dp(28), dp(28));
            av.setBackground(gd);
            LinearLayout.LayoutParams avLP = new LinearLayout.LayoutParams(dp(28), dp(28));
            if (i > 0) avLP.leftMargin = dp(-6);
            av.setLayoutParams(avLP);
            avatarRow.addView(av);
        }
        if (group.members.size() > 4) {
            TextView more = new TextView(this);
            more.setText("+" + (group.members.size() - 4));
            more.setTextColor(Color.parseColor("#9CA3AF"));
            more.setTextSize(11);
            LinearLayout.LayoutParams moreLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            moreLP.leftMargin = dp(4);
            more.setLayoutParams(moreLP);
            avatarRow.addView(more);
        }
        topRow.addView(avatarRow);
        card.addView(topRow);

        // Description
        if (!TextUtils.isEmpty(group.description)) {
            TextView tvDesc = new TextView(this);
            tvDesc.setText(group.description);
            tvDesc.setTextColor(Color.parseColor("#9CA3AF"));
            tvDesc.setTextSize(12);
            tvDesc.setMaxLines(1);
            LinearLayout.LayoutParams descLP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            descLP.topMargin = dp(4);
            tvDesc.setLayoutParams(descLP);
            card.addView(tvDesc);
        }

        // Bottom row: total + your balance
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bottomLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomLP.topMargin = dp(10);
        bottomRow.setLayoutParams(bottomLP);

        TextView tvTotal = new TextView(this);
        tvTotal.setText("Total: " + currFmt.format(group.totalExpenses));
        tvTotal.setTextColor(Color.parseColor("#B0B0B0"));
        tvTotal.setTextSize(12);
        LinearLayout.LayoutParams totalLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTotal.setLayoutParams(totalLP);
        bottomRow.addView(tvTotal);

        double myBal = splitRepo.getCurrentUserBalanceInGroup(group.id);
        TextView tvBal = new TextView(this);
        if (Math.abs(myBal) < 0.01) {
            tvBal.setText("✅ Settled");
            tvBal.setTextColor(Color.parseColor("#22C55E"));
        } else if (myBal > 0) {
            tvBal.setText("+" + currFmt.format(myBal));
            tvBal.setTextColor(Color.parseColor("#22C55E"));
        } else {
            tvBal.setText("-" + currFmt.format(Math.abs(myBal)));
            tvBal.setTextColor(Color.parseColor("#EF4444"));
        }
        tvBal.setTextSize(13);
        tvBal.setTypeface(null, android.graphics.Typeface.BOLD);
        bottomRow.addView(tvBal);

        card.addView(bottomRow);

        if (isArchived) {
            card.setAlpha(0.6f);
        }

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupDetailActivity.class);
            intent.putExtra("group_id", group.id);
            startActivity(intent);
        });

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  CREATE GROUP BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════

    private void showCreateGroupSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null);
        dialog.setContentView(sheet);

        EditText etName = sheet.findViewById(R.id.etGroupName);
        EditText etDesc = sheet.findViewById(R.id.etGroupDescription);
        EditText etMember = sheet.findViewById(R.id.etNewMemberName);
        LinearLayout membersContainer = sheet.findViewById(R.id.membersContainer);
        Button btnAddMember = sheet.findViewById(R.id.btnAddMember);
        Button btnCreate = sheet.findViewById(R.id.btnCreateGroup);

        ArrayList<String> memberNames = new ArrayList<>();

        btnAddMember.setOnClickListener(v -> {
            String name = etMember.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show();
                return;
            }
            memberNames.add(name);
            addMemberChip(membersContainer, memberNames, name, memberNames.size());
            etMember.setText("");
        });

        btnCreate.setOnClickListener(v -> {
            String groupName = etName.getText().toString().trim();
            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(this, "Enter a group name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (memberNames.isEmpty()) {
                Toast.makeText(this, "Add at least one member", Toast.LENGTH_SHORT).show();
                return;
            }

            SplitGroup group = new SplitGroup();
            group.name = groupName;
            group.description = etDesc.getText().toString().trim();

            // Add "Me" as first member
            SplitMember me = SplitMember.createCurrentUser();
            group.members.add(me);
            group.createdByUserId = me.id;

            // Add other members
            for (int i = 0; i < memberNames.size(); i++) {
                SplitMember member = SplitMember.create(memberNames.get(i), null, i + 1);
                group.members.add(member);
            }

            splitRepo.addGroup(group);
            dialog.dismiss();
            refreshAll();
            Toast.makeText(this, "Group created!", Toast.LENGTH_SHORT).show();

            // Open the new group
            Intent intent = new Intent(this, GroupDetailActivity.class);
            intent.putExtra("group_id", group.id);
            startActivity(intent);
        });

        dialog.show();
    }

    private void addMemberChip(LinearLayout container, ArrayList<String> memberNames, String name, int index) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.split_chip_bg);
        chip.setPadding(dp(14), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams chipLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        chipLP.bottomMargin = dp(6);
        chip.setLayoutParams(chipLP);

        // Avatar
        SplitMember tempMember = SplitMember.create(name, null, index);
        TextView av = new TextView(this);
        av.setText(tempMember.getInitials());
        av.setTextColor(Color.WHITE);
        av.setTextSize(10);
        av.setGravity(Gravity.CENTER);
        av.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(tempMember.avatarColorHex);
        gd.setSize(dp(28), dp(28));
        av.setBackground(gd);
        av.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        chip.addView(av);

        // Name
        TextView tvN = new TextView(this);
        tvN.setText(name);
        tvN.setTextColor(Color.WHITE);
        tvN.setTextSize(14);
        LinearLayout.LayoutParams nLP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nLP.leftMargin = dp(10);
        tvN.setLayoutParams(nLP);
        chip.addView(tvN);

        // Remove button
        TextView btnRemove = new TextView(this);
        btnRemove.setText("✕");
        btnRemove.setTextColor(Color.parseColor("#EF4444"));
        btnRemove.setTextSize(16);
        btnRemove.setPadding(dp(8), 0, dp(4), 0);
        btnRemove.setOnClickListener(v -> {
            memberNames.remove(name);
            container.removeView(chip);
        });
        chip.addView(btnRemove);

        container.addView(chip);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
