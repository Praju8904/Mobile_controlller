package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MoneyRecordDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECORD_ID = "money_record_id";

    private MoneyRecordRepository repo;
    private WalletRepository walletRepo;

    private String recordId;
    private MoneyRecord record;

    private TextView tvTypeBadge, tvAvatar, tvPersonName, tvStatusBadge;
    private TextView tvDescription, tvOriginalAmount, tvOutstanding;
    private TextView tvDate, tvExpectedReturn, tvWalletName, tvNotes, tvReminderInfo;
    private View progressFill;
    private FrameLayout progressTrack;
    private LinearLayout repaymentHistoryContainer;
    private TextView tvNoRepayments;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_record_detail);

        recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);
        if (recordId == null) {
            finish();
            return;
        }

        repo = new MoneyRecordRepository(this);
        walletRepo = new WalletRepository(this);

        initViews();
        loadAndRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndRefresh();
    }

    private void initViews() {
        tvTypeBadge = findViewById(R.id.tvTypeBadge);
        tvAvatar = findViewById(R.id.tvAvatar);
        tvPersonName = findViewById(R.id.tvPersonName);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvDescription = findViewById(R.id.tvDescription);
        tvOriginalAmount = findViewById(R.id.tvOriginalAmount);
        tvOutstanding = findViewById(R.id.tvOutstanding);
        tvDate = findViewById(R.id.tvDate);
        tvExpectedReturn = findViewById(R.id.tvExpectedReturn);
        tvWalletName = findViewById(R.id.tvWalletName);
        tvNotes = findViewById(R.id.tvNotes);
        tvReminderInfo = findViewById(R.id.tvReminderInfo);
        progressFill = findViewById(R.id.progressFill);
        progressTrack = null; // We manage width via post()
        repaymentHistoryContainer = findViewById(R.id.repaymentHistoryContainer);
        tvNoRepayments = findViewById(R.id.tvNoRepayments);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            if (record != null) showEditDialog();
        });
        findViewById(R.id.btnAddRepayment).setOnClickListener(v -> {
            if (record != null) showAddRepaymentDialog();
        });
        findViewById(R.id.btnMarkSettled).setOnClickListener(v -> {
            if (record != null) markSettled();
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (record != null) confirmDelete();
        });
        findViewById(R.id.btnWriteOff).setOnClickListener(v -> {
            if (record != null) writeOff();
        });
    }

    private void loadAndRefresh() {
        record = repo.getById(recordId);
        if (record == null) {
            finish();
            return;
        }
        renderRecord();
        renderRepayments();
    }

    private void renderRecord() {
        boolean isLent = MoneyRecord.TYPE_LENT.equals(record.type);
        boolean isSettled = MoneyRecord.STATUS_SETTLED.equals(record.status)
                || MoneyRecord.STATUS_WRITTEN_OFF.equals(record.status);

        // Type badge
        tvTypeBadge.setText(isLent ? "LENT" : "BORROWED");
        tvTypeBadge.setTextColor(Color.parseColor(isLent ? "#22C55E" : "#EF4444"));
        tvTypeBadge.setBackgroundResource(isLent ? R.drawable.iou_lent_chip_bg
                : R.drawable.iou_borrowed_chip_bg);

        // Avatar
        tvAvatar.setText(record.getAvatarInitials());
        int avatarColor = record.personAvatarColorHex != 0
                ? record.personAvatarColorHex : MoneyRecord.pickAvatarColor(record.personName);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(avatarColor);
        tvAvatar.setBackground(gd);

        tvPersonName.setText(record.personName);
        tvStatusBadge.setText(record.status);

        // Set status color
        switch (record.status) {
            case MoneyRecord.STATUS_ACTIVE:
                tvStatusBadge.setTextColor(Color.parseColor("#3B82F6")); break;
            case MoneyRecord.STATUS_OVERDUE:
                tvStatusBadge.setTextColor(Color.parseColor("#EF4444")); break;
            case MoneyRecord.STATUS_PARTIALLY_PAID:
                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B")); break;
            case MoneyRecord.STATUS_SETTLED:
                tvStatusBadge.setTextColor(Color.parseColor("#22C55E")); break;
            default:
                tvStatusBadge.setTextColor(Color.parseColor("#9CA3AF")); break;
        }

        tvDescription.setText(record.description);
        tvOriginalAmount.setText(record.currency + String.format("%.2f", record.amount));

        double outstanding = record.getOutstandingAmount();
        tvOutstanding.setText(record.currency + String.format("%.2f", outstanding));
        tvOutstanding.setTextColor(Color.parseColor(isSettled ? "#22C55E" : "#EF4444"));

        tvDate.setText(dateFmt.format(new Date(record.date)));

        // Expected return date
        if (record.expectedReturnDate > 0) {
            long diff = record.expectedReturnDate - System.currentTimeMillis();
            long daysLeft = diff / (1000L * 60 * 60 * 24);
            String retStr = dateFmt.format(new Date(record.expectedReturnDate));
            if (diff < 0 && !isSettled) {
                tvExpectedReturn.setText("⚠️ " + retStr + " (overdue)");
                tvExpectedReturn.setTextColor(Color.parseColor("#EF4444"));
            } else if (daysLeft <= 7 && !isSettled) {
                tvExpectedReturn.setText("⏰ " + retStr + " (" + daysLeft + "d left)");
                tvExpectedReturn.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                tvExpectedReturn.setText("📅 " + retStr);
                tvExpectedReturn.setTextColor(Color.parseColor("#22C55E"));
            }
        } else {
            tvExpectedReturn.setText("Not set");
            tvExpectedReturn.setTextColor(Color.parseColor("#9CA3AF"));
        }

        // Wallet name
        if (!TextUtils.isEmpty(record.walletId)) {
            Wallet w = walletRepo.getById(record.walletId);
            tvWalletName.setText("💳 " + (w != null ? w.name : "Unknown wallet"));
        } else {
            tvWalletName.setText("💳 No wallet linked");
        }

        // Notes
        if (!TextUtils.isEmpty(record.notes)) {
            tvNotes.setVisibility(View.VISIBLE);
            tvNotes.setText("📝 " + record.notes);
        } else {
            tvNotes.setVisibility(View.GONE);
        }

        // Reminder info
        if (record.reminderEnabled) {
            tvReminderInfo.setVisibility(View.VISIBLE);
            tvReminderInfo.setText("🔔 Reminder every " + record.reminderFrequencyDays + " days");
        } else {
            tvReminderInfo.setVisibility(View.GONE);
        }

        // Progress bar
        final View fill = progressFill;
        fill.post(() -> {
            View track = (View) fill.getParent();
            if (track != null && record.amount > 0) {
                float pct = (float) Math.min(1.0, record.amountPaid / record.amount);
                ViewGroup.LayoutParams flp = fill.getLayoutParams();
                flp.width = (int) (track.getWidth() * pct);
                fill.setLayoutParams(flp);
            }
        });

        // Hide action buttons if settled/written off
        if (isSettled) {
            findViewById(R.id.btnAddRepayment).setVisibility(View.GONE);
            findViewById(R.id.btnMarkSettled).setVisibility(View.GONE);
            findViewById(R.id.btnWriteOff).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btnAddRepayment).setVisibility(View.VISIBLE);
            findViewById(R.id.btnMarkSettled).setVisibility(View.VISIBLE);
            findViewById(R.id.btnWriteOff).setVisibility(View.VISIBLE);
        }
    }

    private void renderRepayments() {
        repaymentHistoryContainer.removeAllViews();
        ArrayList<Repayment> repayments = repo.getRepayments(recordId);
        if (repayments.isEmpty()) {
            tvNoRepayments.setVisibility(View.VISIBLE);
        } else {
            tvNoRepayments.setVisibility(View.GONE);
            for (Repayment rep : repayments) {
                repaymentHistoryContainer.addView(buildRepaymentRow(rep));
            }
        }
    }

    private View buildRepaymentRow(Repayment rep) {
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

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFmt.format(new Date(rep.date)));
        tvDate.setTextColor(Color.WHITE);
        tvDate.setTextSize(13);
        mid.addView(tvDate);

        if (!TextUtils.isEmpty(rep.notes)) {
            TextView tvNotes = new TextView(this);
            tvNotes.setText(rep.notes);
            tvNotes.setTextColor(Color.parseColor("#9CA3AF"));
            tvNotes.setTextSize(11);
            mid.addView(tvNotes);
        }

        if (!TextUtils.isEmpty(rep.walletId)) {
            Wallet w = walletRepo.getById(rep.walletId);
            if (w != null) {
                TextView tvWallet = new TextView(this);
                tvWallet.setText("via " + w.name);
                tvWallet.setTextColor(Color.parseColor("#6B7280"));
                tvWallet.setTextSize(11);
                mid.addView(tvWallet);
            }
        }
        row.addView(mid);

        TextView tvAmt = new TextView(this);
        tvAmt.setText("₹" + String.format("%.0f", rep.amount));
        tvAmt.setTextColor(Color.parseColor("#22C55E"));
        tvAmt.setTextSize(14);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvAmt);

        return row;
    }

    private void markSettled() {
        new AlertDialog.Builder(this)
                .setTitle("Mark as Settled")
                .setMessage("Mark this entire record as settled?")
                .setPositiveButton("Settle", (d, w) -> {
                    record.status = MoneyRecord.STATUS_SETTLED;
                    record.amountPaid = record.amount;
                    record.actualReturnDate = System.currentTimeMillis();
                    repo.updateRecord(record);
                    IouNotificationHelper.sendSettledNotification(this, record);
                    loadAndRefresh();
                    Toast.makeText(this, "Marked as settled!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void writeOff() {
        new AlertDialog.Builder(this)
                .setTitle("Write Off")
                .setMessage("Write off the outstanding balance for this record?")
                .setPositiveButton("Write Off", (d, w) -> {
                    record.status = MoneyRecord.STATUS_WRITTEN_OFF;
                    repo.updateRecord(record);
                    loadAndRefresh();
                    Toast.makeText(this, "Written off", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Delete this record permanently?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteRecord(record.id, walletRepo);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddRepaymentDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView labelAmt = new TextView(this);
        labelAmt.setText("Amount *");
        labelAmt.setTextColor(Color.parseColor("#9CA3AF"));
        labelAmt.setTextSize(11);
        form.addView(labelAmt);

        EditText etAmount = new EditText(this);
        etAmount.setHint("0.00");
        etAmount.setTextColor(Color.WHITE);
        etAmount.setHintTextColor(Color.parseColor("#6B7280"));
        etAmount.setTextSize(14);
        etAmount.setBackgroundResource(R.drawable.glass_card_bg);
        etAmount.setPadding(dp(12), dp(10), dp(12), dp(10));
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format("%.2f", record.getOutstandingAmount()));
        form.addView(etAmount);

        TextView labelDate = new TextView(this);
        labelDate.setText("Date *");
        labelDate.setTextColor(Color.parseColor("#9CA3AF"));
        labelDate.setTextSize(11);
        LinearLayout.LayoutParams labelDateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelDateLp.topMargin = dp(10);
        labelDate.setLayoutParams(labelDateLp);
        form.addView(labelDate);

        final long[] selectedDate = {System.currentTimeMillis()};
        TextView tvDatePicker = new TextView(this);
        tvDatePicker.setTextColor(Color.WHITE);
        tvDatePicker.setTextSize(14);
        tvDatePicker.setBackgroundResource(R.drawable.glass_card_bg);
        tvDatePicker.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
        tvDatePicker.setClickable(true);
        tvDatePicker.setFocusable(true);
        tvDatePicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (dp2, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 12, 0, 0);
                selectedDate[0] = sel.getTimeInMillis();
                tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(tvDatePicker);

        TextView labelNotes = new TextView(this);
        labelNotes.setText("Notes (optional)");
        labelNotes.setTextColor(Color.parseColor("#9CA3AF"));
        labelNotes.setTextSize(11);
        LinearLayout.LayoutParams labelNotesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelNotesLp.topMargin = dp(10);
        labelNotes.setLayoutParams(labelNotesLp);
        form.addView(labelNotes);

        EditText etNotes = new EditText(this);
        etNotes.setHint("Any notes...");
        etNotes.setTextColor(Color.WHITE);
        etNotes.setHintTextColor(Color.parseColor("#6B7280"));
        etNotes.setTextSize(14);
        etNotes.setBackgroundResource(R.drawable.glass_card_bg);
        etNotes.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(etNotes);

        new AlertDialog.Builder(this)
                .setTitle("Add Repayment")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String amtStr = etAmount.getText().toString().trim();
                    if (TextUtils.isEmpty(amtStr)) {
                        Toast.makeText(this, "Enter amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double amt;
                    try {
                        amt = Double.parseDouble(amtStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Repayment repayment = new Repayment();
                    repayment.moneyRecordId = record.id;
                    repayment.amount = amt;
                    repayment.date = selectedDate[0];
                    repayment.notes = etNotes.getText().toString().trim();

                    repo.addRepayment(repayment, walletRepo);
                    loadAndRefresh();
                    Toast.makeText(this, "Repayment saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog() {
        Intent intent = new Intent(this, BorrowLendActivity.class);
        intent.putExtra(BorrowLendActivity.EXTRA_EDIT_RECORD_ID, record.id);
        startActivity(intent);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
