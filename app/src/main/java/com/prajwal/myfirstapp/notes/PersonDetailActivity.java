package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.expenses.MoneyRecordDetailActivity;
import com.prajwal.myfirstapp.expenses.MoneyRecordDetailActivity;
import com.prajwal.myfirstapp.expenses.MoneyRecord;
import com.prajwal.myfirstapp.expenses.MoneyRecordRepository;
import com.prajwal.myfirstapp.expenses.Repayment;
import com.prajwal.myfirstapp.expenses.WalletRepository;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PersonDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PERSON_NAME = "person_name";

    private MoneyRecordRepository repo;
    private WalletRepository walletRepo;

    private String personName;
    private LinearLayout recordsContainer, repaymentHistoryContainer;
    private TextView tvNoRepayments, tvSummaryLent, tvSummaryBorrowed, tvSummaryNet;
    private TextView tvAvatar, tvName, tvPhone, btnCall, btnWhatsApp;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_detail);

        personName = getIntent().getStringExtra(EXTRA_PERSON_NAME);
        if (personName == null) {
            finish();
            return;
        }

        repo = new MoneyRecordRepository(this);
        walletRepo = new WalletRepository(this);

        initViews();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void initViews() {
        tvAvatar = findViewById(R.id.tvAvatar);
        tvName = findViewById(R.id.tvName);
        tvPhone = findViewById(R.id.tvPhone);
        btnCall = findViewById(R.id.btnCall);
        btnWhatsApp = findViewById(R.id.btnWhatsApp);
        tvSummaryLent = findViewById(R.id.tvSummaryLent);
        tvSummaryBorrowed = findViewById(R.id.tvSummaryBorrowed);
        tvSummaryNet = findViewById(R.id.tvSummaryNet);
        recordsContainer = findViewById(R.id.recordsContainer);
        repaymentHistoryContainer = findViewById(R.id.repaymentHistoryContainer);
        tvNoRepayments = findViewById(R.id.tvNoRepayments);

        ((TextView) findViewById(R.id.tvPersonName)).setText(personName);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnSettleAll).setOnClickListener(v -> showSettleAllDialog());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareSummary());
    }

    private void refreshAll() {
        ArrayList<MoneyRecord> records = repo.getByPerson(personName);
        if (records.isEmpty()) {
            Toast.makeText(this, "No records found for " + personName, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MoneyRecord first = records.get(0);

        // Avatar
        String initials = first.getAvatarInitials();
        tvAvatar.setText(initials);
        int avatarColor = first.personAvatarColorHex != 0
                ? first.personAvatarColorHex : MoneyRecord.pickAvatarColor(personName);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(avatarColor);
        tvAvatar.setBackground(gd);
        tvName.setText(personName);

        // Phone
        String phone = first.personPhone;
        if (!TextUtils.isEmpty(phone)) {
            tvPhone.setText(phone);
            tvPhone.setVisibility(View.VISIBLE);
            btnCall.setVisibility(View.VISIBLE);
            btnWhatsApp.setVisibility(View.VISIBLE);
            btnCall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                startActivity(intent);
            });
            btnWhatsApp.setOnClickListener(v -> {
                String clean = phone.replaceAll("[^0-9+]", "");
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://wa.me/" + clean));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "WhatsApp not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Summary pills
        double totalLent = 0, totalBorrowed = 0;
        for (MoneyRecord r : records) {
            if (MoneyRecord.TYPE_LENT.equals(r.type)) {
                if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                        && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                    totalLent += r.getOutstandingAmount();
                }
            } else {
                if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                        && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                    totalBorrowed += r.getOutstandingAmount();
                }
            }
        }
        double net = totalLent - totalBorrowed;
        tvSummaryLent.setText("₹" + String.format("%.0f", totalLent));
        tvSummaryBorrowed.setText("₹" + String.format("%.0f", totalBorrowed));
        tvSummaryNet.setText((net >= 0 ? "+" : "") + "₹" + String.format("%.0f", net));
        tvSummaryNet.setTextColor(Color.parseColor(net >= 0 ? "#22C55E" : "#EF4444"));

        // Records list
        recordsContainer.removeAllViews();
        for (MoneyRecord r : records) {
            recordsContainer.addView(buildRecordRow(r));
        }

        // Repayment history
        repaymentHistoryContainer.removeAllViews();
        ArrayList<Repayment> allRepayments = new ArrayList<>();
        for (MoneyRecord r : records) {
            allRepayments.addAll(repo.getRepayments(r.id));
        }
        allRepayments.sort((a, b) -> Long.compare(b.date, a.date));

        if (allRepayments.isEmpty()) {
            tvNoRepayments.setVisibility(View.VISIBLE);
        } else {
            tvNoRepayments.setVisibility(View.GONE);
            for (Repayment rep : allRepayments) {
                repaymentHistoryContainer.addView(buildRepaymentRow(rep, records));
            }
        }
    }

    private View buildRecordRow(MoneyRecord record) {
        boolean isLent = MoneyRecord.TYPE_LENT.equals(record.type);
        boolean isSettled = MoneyRecord.STATUS_SETTLED.equals(record.status)
                || MoneyRecord.STATUS_WRITTEN_OFF.equals(record.status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.glass_card_bg);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        if (isSettled) row.setAlpha(0.6f);

        TextView typeBadge = new TextView(this);
        typeBadge.setText(isLent ? "LENT" : "BORROW");
        typeBadge.setTextColor(Color.parseColor(isLent ? "#22C55E" : "#EF4444"));
        typeBadge.setTextSize(9);
        typeBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        typeBadge.setBackgroundResource(isLent ? R.drawable.iou_lent_chip_bg
                : R.drawable.iou_borrowed_chip_bg);
        typeBadge.setPadding(dp(7), dp(3), dp(7), dp(3));
        row.addView(typeBadge);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = dp(10);
        mid.setLayoutParams(midLp);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(record.description);
        tvDesc.setTextColor(Color.WHITE);
        tvDesc.setTextSize(13);
        mid.addView(tvDesc);

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFmt.format(new Date(record.date)) + " · " + record.status);
        tvDate.setTextColor(Color.parseColor("#9CA3AF"));
        tvDate.setTextSize(11);
        mid.addView(tvDate);
        row.addView(mid);

        TextView tvAmt = new TextView(this);
        tvAmt.setText("₹" + String.format("%.0f", record.getOutstandingAmount()));
        tvAmt.setTextColor(Color.parseColor(isSettled ? "#9CA3AF" : (isLent ? "#22C55E" : "#EF4444")));
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvAmt);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, MoneyRecordDetailActivity.class);
            intent.putExtra(MoneyRecordDetailActivity.EXTRA_RECORD_ID, record.id);
            startActivity(intent);
        });
        return row;
    }

    private View buildRepaymentRow(Repayment repayment, ArrayList<MoneyRecord> records) {
        // Find the parent record
        String desc = "";
        for (MoneyRecord r : records) {
            if (r.id.equals(repayment.moneyRecordId)) {
                desc = r.description;
                break;
            }
        }
        final String descFinal = desc;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.glass_card_bg);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        row.setLayoutParams(lp);

        TextView tvEmoji = new TextView(this);
        tvEmoji.setText("💳");
        tvEmoji.setTextSize(16);
        tvEmoji.setPadding(0, 0, dp(10), 0);
        row.addView(tvEmoji);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mid.setLayoutParams(midLp);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(!TextUtils.isEmpty(descFinal) ? "Re: " + descFinal : "Repayment");
        tvDesc.setTextColor(Color.WHITE);
        tvDesc.setTextSize(13);
        mid.addView(tvDesc);

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFmt.format(new Date(repayment.date)));
        tvDate.setTextColor(Color.parseColor("#9CA3AF"));
        tvDate.setTextSize(11);
        mid.addView(tvDate);
        row.addView(mid);

        TextView tvAmt = new TextView(this);
        tvAmt.setText("₹" + String.format("%.0f", repayment.amount));
        tvAmt.setTextColor(Color.parseColor("#22C55E"));
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvAmt);

        return row;
    }

    private void showSettleAllDialog() {
        ArrayList<MoneyRecord> records = repo.getByPerson(personName);
        boolean hasActive = false;
        for (MoneyRecord r : records) {
            if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                hasActive = true;
                break;
            }
        }
        if (!hasActive) {
            Toast.makeText(this, "All records are already settled", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Settle All with " + personName)
                .setMessage("Mark all outstanding records with " + personName + " as settled?")
                .setPositiveButton("Settle All", (d, w) -> {
                    for (MoneyRecord r : records) {
                        if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                                && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                            r.status = MoneyRecord.STATUS_SETTLED;
                            r.amountPaid = r.amount;
                            r.actualReturnDate = System.currentTimeMillis();
                            repo.updateRecord(r);
                        }
                    }
                    Toast.makeText(this, "All settled with " + personName, Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareSummary() {
        ArrayList<MoneyRecord> records = repo.getByPerson(personName);
        StringBuilder sb = new StringBuilder();
        sb.append("💸 IOU Summary with ").append(personName).append("\n\n");

        double totalOwedToMe = 0, totalIOwe = 0;
        for (MoneyRecord r : records) {
            if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                if (MoneyRecord.TYPE_LENT.equals(r.type)) {
                    sb.append("• I lent ₹").append(String.format("%.0f", r.amount))
                            .append(" for ").append(r.description)
                            .append(" — Outstanding: ₹")
                            .append(String.format("%.0f", r.getOutstandingAmount())).append("\n");
                    totalOwedToMe += r.getOutstandingAmount();
                } else {
                    sb.append("• I borrowed ₹").append(String.format("%.0f", r.amount))
                            .append(" for ").append(r.description)
                            .append(" — I owe: ₹")
                            .append(String.format("%.0f", r.getOutstandingAmount())).append("\n");
                    totalIOwe += r.getOutstandingAmount();
                }
            }
        }
        sb.append("\n");
        if (totalOwedToMe > 0)
            sb.append("Total owed to me: ₹").append(String.format("%.0f", totalOwedToMe)).append("\n");
        if (totalIOwe > 0)
            sb.append("Total I owe: ₹").append(String.format("%.0f", totalIOwe)).append("\n");
        double net = totalOwedToMe - totalIOwe;
        sb.append("Net: ").append(net >= 0 ? "+" : "").append("₹")
                .append(String.format("%.0f", net)).append("\n");

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, "Share IOU Summary"));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
