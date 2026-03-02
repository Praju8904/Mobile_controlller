package com.prajwal.myfirstapp.expenses;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.notes.PersonDetailActivity;
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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class BorrowLendActivity extends AppCompatActivity {

    private MoneyRecordRepository repo;
    private WalletRepository walletRepo;

    private LinearLayout recordsContainer;
    private TextView tvEmpty, tvTotalLent, tvTotalBorrowed, tvNetBalance;
    private TextView tabLent, tabBorrowed;
    private LinearLayout overdueBanner;
    private TextView tvOverdueBanner;
    private LinearLayout analyticsContent;
    private TextView btnToggleAnalytics;
    private TextView tvTotalLentAllTime, tvRecoveryRate, tvTotalBorrowedAllTime,
            tvAvgDaysRecover, tvMostFrequent;

    public static final String EXTRA_EDIT_RECORD_ID = "edit_record_id";

    private String currentTab = MoneyRecord.TYPE_LENT;
    private boolean balanceHidden = false;
    private boolean analyticsVisible = false;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_borrow_lend);

        repo = new MoneyRecordRepository(this);
        walletRepo = new WalletRepository(this);

        IouNotificationHelper.createNotificationChannel(this);
        repo.updateOverdueStatuses();

        initViews();
        refreshAll();

        // Handle edit intent from MoneyRecordDetailActivity
        String editId = getIntent().getStringExtra(EXTRA_EDIT_RECORD_ID);
        if (editId != null) {
            MoneyRecord toEdit = repo.getById(editId);
            if (toEdit != null) showAddRecordDialog(toEdit);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.updateOverdueStatuses();
        refreshAll();
    }

    private void initViews() {
        tvTotalLent = findViewById(R.id.tvTotalLent);
        tvTotalBorrowed = findViewById(R.id.tvTotalBorrowed);
        tvNetBalance = findViewById(R.id.tvNetBalance);
        recordsContainer = findViewById(R.id.recordsContainer);
        tvEmpty = findViewById(R.id.tvEmpty);
        overdueBanner = findViewById(R.id.overdueBanner);
        tvOverdueBanner = findViewById(R.id.tvOverdueBanner);
        tabLent = findViewById(R.id.tabLent);
        tabBorrowed = findViewById(R.id.tabBorrowed);
        analyticsContent = findViewById(R.id.analyticsContent);
        btnToggleAnalytics = findViewById(R.id.btnToggleAnalytics);
        tvTotalLentAllTime = findViewById(R.id.tvTotalLentAllTime);
        tvRecoveryRate = findViewById(R.id.tvRecoveryRate);
        tvTotalBorrowedAllTime = findViewById(R.id.tvTotalBorrowedAllTime);
        tvAvgDaysRecover = findViewById(R.id.tvAvgDaysRecover);
        tvMostFrequent = findViewById(R.id.tvMostFrequent);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddRecordDialog(null));

        // Balance toggle
        findViewById(R.id.btnToggleBalance).setOnClickListener(v -> {
            balanceHidden = !balanceHidden;
            refreshHeader();
        });

        // Tabs
        tabLent.setOnClickListener(v -> switchTab(MoneyRecord.TYPE_LENT));
        tabBorrowed.setOnClickListener(v -> switchTab(MoneyRecord.TYPE_BORROWED));

        // Overdue banner tap
        overdueBanner.setOnClickListener(v -> showOverdueDialog());

        // Analytics toggle
        btnToggleAnalytics.setOnClickListener(v -> {
            analyticsVisible = !analyticsVisible;
            analyticsContent.setVisibility(analyticsVisible ? View.VISIBLE : View.GONE);
            btnToggleAnalytics.setText(analyticsVisible ? "▲" : "▼");
            if (analyticsVisible) refreshAnalytics();
        });
    }

    private void switchTab(String type) {
        currentTab = type;
        boolean isLent = MoneyRecord.TYPE_LENT.equals(type);

        tabLent.setBackgroundResource(isLent ? R.drawable.iou_tab_selected_bg : 0);
        tabLent.setTextColor(isLent ? Color.WHITE : Color.parseColor("#9CA3AF"));
        tabBorrowed.setBackgroundResource(!isLent ? R.drawable.iou_tab_selected_bg : 0);
        tabBorrowed.setTextColor(!isLent ? Color.WHITE : Color.parseColor("#9CA3AF"));

        refreshList();
    }

    private void refreshAll() {
        refreshHeader();
        refreshOverdueBanner();
        refreshList();
        if (analyticsVisible) refreshAnalytics();
    }

    private void refreshHeader() {
        double lent = repo.getTotalLentOutstanding();
        double borrowed = repo.getTotalBorrowedOutstanding();
        double net = repo.getNetBalance();

        if (balanceHidden) {
            tvTotalLent.setText("₹***");
            tvTotalBorrowed.setText("₹***");
            tvNetBalance.setText("₹***");
        } else {
            tvTotalLent.setText("₹" + String.format("%.0f", lent));
            tvTotalBorrowed.setText("₹" + String.format("%.0f", borrowed));
            tvNetBalance.setText((net >= 0 ? "+" : "") + "₹" + String.format("%.0f", net));
        }
        tvNetBalance.setTextColor(Color.parseColor(net >= 0 ? "#22C55E" : "#EF4444"));
    }

    private void refreshOverdueBanner() {
        ArrayList<MoneyRecord> overdue = repo.getOverdueRecords();
        if (overdue.isEmpty()) {
            overdueBanner.setVisibility(View.GONE);
        } else {
            overdueBanner.setVisibility(View.VISIBLE);
            tvOverdueBanner.setText("⚠️ " + overdue.size() + " overdue IOU"
                    + (overdue.size() > 1 ? "s" : "") + " — tap to view");
        }
    }

    private void refreshList() {
        recordsContainer.removeAllViews();
        ArrayList<MoneyRecord> records = repo.getByType(currentTab);

        if (records.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        // Group by person
        Map<String, ArrayList<MoneyRecord>> byPerson = new LinkedHashMap<>();
        for (MoneyRecord r : records) {
            String name = r.personName != null ? r.personName : "Unknown";
            if (!byPerson.containsKey(name)) byPerson.put(name, new ArrayList<>());
            byPerson.get(name).add(r);
        }

        // Render person groups
        for (Map.Entry<String, ArrayList<MoneyRecord>> entry : byPerson.entrySet()) {
            recordsContainer.addView(buildPersonGroupView(entry.getKey(), entry.getValue()));
        }
    }

    private View buildPersonGroupView(String personName, ArrayList<MoneyRecord> personRecords) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        container.setLayoutParams(lp);

        // Calculate outstanding for this person
        double outstanding = 0;
        for (MoneyRecord r : personRecords) {
            if (!MoneyRecord.STATUS_SETTLED.equals(r.status)
                    && !MoneyRecord.STATUS_WRITTEN_OFF.equals(r.status)) {
                outstanding += r.getOutstandingAmount();
            }
        }
        final double finalOutstanding = outstanding;

        // Person header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.glass_card_bg);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setClickable(true);
        header.setFocusable(true);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(2);
        header.setLayoutParams(headerLp);

        // Avatar
        MoneyRecord first = personRecords.get(0);
        TextView avatar = new TextView(this);
        avatar.setText(first.getAvatarInitials());
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(14);
        avatar.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(first.personAvatarColorHex != 0 ? first.personAvatarColorHex
                : MoneyRecord.pickAvatarColor(personName));
        gd.setSize(dp(40), dp(40));
        avatar.setBackground(gd);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        header.addView(avatar);

        // Name column
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameColLp.leftMargin = dp(12);
        nameCol.setLayoutParams(nameColLp);

        TextView tvName = new TextView(this);
        tvName.setText(personName);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        nameCol.addView(tvName);

        TextView tvCount = new TextView(this);
        tvCount.setText(personRecords.size() + " record" + (personRecords.size() != 1 ? "s" : ""));
        tvCount.setTextColor(Color.parseColor("#9CA3AF"));
        tvCount.setTextSize(11);
        nameCol.addView(tvCount);
        header.addView(nameCol);

        // Outstanding badge
        TextView tvBadge = new TextView(this);
        boolean isLent = MoneyRecord.TYPE_LENT.equals(currentTab);
        if (finalOutstanding < 0.01) {
            tvBadge.setText("✓ Settled");
            tvBadge.setTextColor(Color.parseColor("#9CA3AF"));
            tvBadge.setTextSize(12);
        } else {
            tvBadge.setText((isLent ? "+" : "-") + "₹" + String.format("%.0f", finalOutstanding));
            tvBadge.setTextColor(Color.parseColor(isLent ? "#22C55E" : "#EF4444"));
            tvBadge.setTextSize(15);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        header.addView(tvBadge);
        container.addView(header);

        // Individual record cards
        LinearLayout recordCards = new LinearLayout(this);
        recordCards.setOrientation(LinearLayout.VERTICAL);
        recordCards.setPadding(dp(8), 0, 0, 0);
        recordCards.setTag("cards");

        // Sort: overdue first, then active, then partially paid, then settled
        ArrayList<MoneyRecord> sorted = new ArrayList<>(personRecords);
        sorted.sort((a, b) -> statusOrder(a.status) - statusOrder(b.status));

        for (MoneyRecord r : sorted) {
            recordCards.addView(buildRecordCard(r));
        }
        container.addView(recordCards);

        // Header click → open PersonDetailActivity; long click → toggle expand
        header.setOnClickListener(v -> {
            Intent intent = new Intent(this, PersonDetailActivity.class);
            intent.putExtra(PersonDetailActivity.EXTRA_PERSON_NAME, personName);
            startActivity(intent);
        });
        header.setOnLongClickListener(v -> {
            int vis = recordCards.getVisibility();
            recordCards.setVisibility(vis == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });

        return container;
    }

    private int statusOrder(String status) {
        switch (status) {
            case MoneyRecord.STATUS_OVERDUE: return 0;
            case MoneyRecord.STATUS_ACTIVE: return 1;
            case MoneyRecord.STATUS_PARTIALLY_PAID: return 2;
            case MoneyRecord.STATUS_SETTLED: return 3;
            case MoneyRecord.STATUS_WRITTEN_OFF: return 4;
            default: return 5;
        }
    }

    private View buildRecordCard(MoneyRecord record) {
        boolean isOverdue = MoneyRecord.STATUS_OVERDUE.equals(record.status);
        boolean isSettled = MoneyRecord.STATUS_SETTLED.equals(record.status)
                || MoneyRecord.STATUS_WRITTEN_OFF.equals(record.status);
        boolean isLent = MoneyRecord.TYPE_LENT.equals(record.type);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(isOverdue ? R.drawable.iou_overdue_card_bg
                : R.drawable.glass_card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        card.setLayoutParams(lp);
        if (isSettled) card.setAlpha(0.6f);

        // Top row: type badge + description + menu
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = dp(8);
        topRow.setLayoutParams(topLp);

        TextView typeBadge = new TextView(this);
        typeBadge.setText(isLent ? "LENT" : "BORROW");
        typeBadge.setTextColor(Color.parseColor(isLent ? "#22C55E" : "#EF4444"));
        typeBadge.setTextSize(9);
        typeBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        typeBadge.setBackgroundResource(isLent ? R.drawable.iou_lent_chip_bg
                : R.drawable.iou_borrowed_chip_bg);
        typeBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        topRow.addView(typeBadge);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(record.description);
        tvDesc.setTextColor(Color.WHITE);
        tvDesc.setTextSize(13);
        tvDesc.setSingleLine(true);
        tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        descLp.leftMargin = dp(8);
        tvDesc.setLayoutParams(descLp);
        topRow.addView(tvDesc);

        // Status dot
        TextView tvStatus = new TextView(this);
        tvStatus.setText(getStatusEmoji(record.status));
        tvStatus.setTextSize(13);
        tvStatus.setPadding(dp(4), 0, dp(4), 0);
        topRow.addView(tvStatus);

        // Menu button (⋮)
        TextView btnMenu = new TextView(this);
        btnMenu.setText("⋮");
        btnMenu.setTextColor(Color.parseColor("#9CA3AF"));
        btnMenu.setTextSize(18);
        btnMenu.setPadding(dp(8), 0, 0, 0);
        btnMenu.setClickable(true);
        btnMenu.setFocusable(true);
        btnMenu.setOnClickListener(v -> showRecordContextMenu(record));
        topRow.addView(btnMenu);
        card.addView(topRow);

        // Amounts row
        LinearLayout amtRow = new LinearLayout(this);
        amtRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        amtLp.bottomMargin = dp(8);
        amtRow.setLayoutParams(amtLp);

        LinearLayout amtCol = new LinearLayout(this);
        amtCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams amtColLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        amtCol.setLayoutParams(amtColLp);

        TextView tvOriginal = new TextView(this);
        tvOriginal.setText("₹" + String.format("%.0f", record.amount));
        tvOriginal.setTextColor(Color.parseColor("#9CA3AF"));
        tvOriginal.setTextSize(12);
        amtCol.addView(tvOriginal);

        TextView tvDate = new TextView(this);
        tvDate.setText(dateFmt.format(new Date(record.date)));
        tvDate.setTextColor(Color.parseColor("#6B7280"));
        tvDate.setTextSize(11);
        amtCol.addView(tvDate);
        amtRow.addView(amtCol);

        TextView tvOutstanding = new TextView(this);
        tvOutstanding.setText("₹" + String.format("%.0f", record.getOutstandingAmount()));
        tvOutstanding.setTextColor(isSettled ? Color.parseColor("#22C55E")
                : Color.parseColor(isLent ? "#22C55E" : "#EF4444"));
        tvOutstanding.setTextSize(18);
        tvOutstanding.setTypeface(null, android.graphics.Typeface.BOLD);
        tvOutstanding.setGravity(Gravity.END);
        amtRow.addView(tvOutstanding);
        card.addView(amtRow);

        // Progress bar
        if (record.amount > 0) {
            FrameLayout progressTrack = new FrameLayout(this);
            progressTrack.setBackgroundResource(R.drawable.iou_progress_track_bg);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
            progressLp.bottomMargin = dp(8);
            progressTrack.setLayoutParams(progressLp);

            View progressFill = new View(this);
            progressFill.setBackgroundResource(R.drawable.iou_progress_fill_bg);
            float pct = (float) Math.min(1.0, record.amountPaid / record.amount);
            FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            progressFill.setLayoutParams(fillLp);
            progressTrack.addView(progressFill);
            card.addView(progressTrack);

            // Set progress width after layout
            final float pctFinal = pct;
            progressTrack.post(() -> {
                int trackW = progressTrack.getWidth();
                FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
                fl.width = (int) (trackW * pctFinal);
                progressFill.setLayoutParams(fl);
            });
        }

        // Expected return date
        if (record.expectedReturnDate > 0) {
            long now = System.currentTimeMillis();
            long diff = record.expectedReturnDate - now;
            long daysLeft = diff / (1000L * 60 * 60 * 24);

            TextView tvReturn = new TextView(this);
            String returnStr;
            String returnColor;
            if (diff < 0) {
                returnStr = "⚠️ Overdue since " + dateFmt.format(new Date(record.expectedReturnDate));
                returnColor = "#EF4444";
            } else if (daysLeft <= 7) {
                returnStr = "⏰ Due " + dateFmt.format(new Date(record.expectedReturnDate))
                        + " (" + daysLeft + "d)";
                returnColor = "#F59E0B";
            } else {
                returnStr = "📅 Due " + dateFmt.format(new Date(record.expectedReturnDate));
                returnColor = "#22C55E";
            }
            tvReturn.setText(returnStr);
            tvReturn.setTextColor(Color.parseColor(returnColor));
            tvReturn.setTextSize(11);
            card.addView(tvReturn);
        }

        // Card tap → open detail
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, MoneyRecordDetailActivity.class);
            intent.putExtra(MoneyRecordDetailActivity.EXTRA_RECORD_ID, record.id);
            startActivity(intent);
        });

        return card;
    }

    private String getStatusEmoji(String status) {
        switch (status) {
            case MoneyRecord.STATUS_ACTIVE: return "🔵";
            case MoneyRecord.STATUS_PARTIALLY_PAID: return "🟡";
            case MoneyRecord.STATUS_SETTLED: return "✅";
            case MoneyRecord.STATUS_OVERDUE: return "🔴";
            case MoneyRecord.STATUS_WRITTEN_OFF: return "⛔";
            default: return "";
        }
    }

    private void showRecordContextMenu(MoneyRecord record) {
        boolean isSettled = MoneyRecord.STATUS_SETTLED.equals(record.status)
                || MoneyRecord.STATUS_WRITTEN_OFF.equals(record.status);

        String[] options;
        if (isSettled) {
            options = new String[]{"View Detail", "Delete"};
        } else {
            options = new String[]{"Add Repayment", "Edit", "Mark Settled", "Write Off", "Delete"};
        }

        new AlertDialog.Builder(this)
                .setTitle(record.description)
                .setItems(options, (dialog, which) -> {
                    if (isSettled) {
                        if (which == 0) openDetail(record.id);
                        else confirmDelete(record);
                    } else {
                        switch (which) {
                            case 0: showAddRepaymentDialog(record); break;
                            case 1: showAddRecordDialog(record); break;
                            case 2: markSettled(record); break;
                            case 3: writeOff(record); break;
                            case 4: confirmDelete(record); break;
                        }
                    }
                }).show();
    }

    private void openDetail(String id) {
        Intent intent = new Intent(this, MoneyRecordDetailActivity.class);
        intent.putExtra(MoneyRecordDetailActivity.EXTRA_RECORD_ID, id);
        startActivity(intent);
    }

    private void markSettled(MoneyRecord record) {
        record.status = MoneyRecord.STATUS_SETTLED;
        record.amountPaid = record.amount;
        record.actualReturnDate = System.currentTimeMillis();
        repo.updateRecord(record);
        IouNotificationHelper.sendSettledNotification(this, record);
        refreshAll();
        Toast.makeText(this, "Marked as settled!", Toast.LENGTH_SHORT).show();
    }

    private void writeOff(MoneyRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Write Off")
                .setMessage("Write off the outstanding amount for this record?")
                .setPositiveButton("Write Off", (d, w) -> {
                    record.status = MoneyRecord.STATUS_WRITTEN_OFF;
                    repo.updateRecord(record);
                    refreshAll();
                    Toast.makeText(this, "Written off", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(MoneyRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Delete this record for " + record.personName + "? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteRecord(record.id, walletRepo);
                    refreshAll();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOverdueDialog() {
        ArrayList<MoneyRecord> overdue = repo.getOverdueRecords();
        String[] items = new String[overdue.size()];
        for (int i = 0; i < overdue.size(); i++) {
            MoneyRecord r = overdue.get(i);
            items[i] = r.personName + " — ₹" + String.format("%.0f", r.getOutstandingAmount())
                    + " (" + (MoneyRecord.TYPE_LENT.equals(r.type) ? "lent" : "borrowed") + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Overdue IOUs")
                .setItems(items, (d, which) -> openDetail(overdue.get(which).id))
                .setNegativeButton("Close", null)
                .show();
    }

    private void refreshAnalytics() {
        tvTotalLentAllTime.setText("₹" + String.format("%.0f", repo.getTotalLentAllTime()));
        tvTotalBorrowedAllTime.setText("₹" + String.format("%.0f", repo.getTotalBorrowedAllTime()));
        tvRecoveryRate.setText(String.format("%.1f%%", repo.getRecoveryRate()));
        tvAvgDaysRecover.setText(String.format("%.1f days", repo.getAverageDaysToRecover()));
        String freq = repo.getMostFrequentPerson();
        tvMostFrequent.setText(freq != null ? freq : "—");
    }

    // ═══════════════════════════════════════════════════════════
    //  ADD / EDIT RECORD DIALOG
    // ═══════════════════════════════════════════════════════════

    private void showAddRecordDialog(MoneyRecord existing) {
        boolean isEdit = (existing != null);
        ScrollView sv = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(16));
        sv.addView(form);

        // Type selector
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeRow.setPadding(0, 0, 0, dp(12));
        typeRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final String[] selectedType = {isEdit ? existing.type : MoneyRecord.TYPE_LENT};
        TextView btnTypeLent = new TextView(this);
        btnTypeLent.setText("I Lent");
        btnTypeLent.setGravity(Gravity.CENTER);
        btnTypeLent.setTextSize(13);
        btnTypeLent.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(0,
                dp(40), 1f);
        typeLp.rightMargin = dp(6);
        btnTypeLent.setLayoutParams(typeLp);

        TextView btnTypeBorrowed = new TextView(this);
        btnTypeBorrowed.setText("I Borrowed");
        btnTypeBorrowed.setGravity(Gravity.CENTER);
        btnTypeBorrowed.setTextSize(13);
        btnTypeBorrowed.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams typeLp2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        typeLp2.leftMargin = dp(6);
        btnTypeBorrowed.setLayoutParams(typeLp2);
        typeRow.addView(btnTypeLent);
        typeRow.addView(btnTypeBorrowed);
        form.addView(typeRow);

        // Closure-friendly UI refresh via arrays
        updateTypeChips(btnTypeLent, btnTypeBorrowed, selectedType[0]);

        btnTypeLent.setOnClickListener(v -> {
            selectedType[0] = MoneyRecord.TYPE_LENT;
            updateTypeChips(btnTypeLent, btnTypeBorrowed, selectedType[0]);
        });
        btnTypeBorrowed.setOnClickListener(v -> {
            selectedType[0] = MoneyRecord.TYPE_BORROWED;
            updateTypeChips(btnTypeLent, btnTypeBorrowed, selectedType[0]);
        });

        // Person name (with autocomplete)
        form.addView(formLabel("Person Name *"));
        ArrayList<String> personNames = repo.getAllPersonNames();
        AutoCompleteTextView etPerson = new AutoCompleteTextView(this);
        etPerson.setHint("Who is this with?");
        etPerson.setTextColor(Color.WHITE);
        etPerson.setHintTextColor(Color.parseColor("#6B7280"));
        etPerson.setTextSize(14);
        etPerson.setBackgroundResource(R.drawable.glass_card_bg);
        etPerson.setPadding(dp(12), dp(10), dp(12), dp(10));
        etPerson.setLayoutParams(fieldLp());
        ArrayAdapter<String> acAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, personNames);
        etPerson.setAdapter(acAdapter);
        if (isEdit) etPerson.setText(existing.personName);
        form.addView(etPerson);

        // Phone (optional)
        form.addView(formLabel("Phone (optional)"));
        EditText etPhone = createEditText("e.g. +91 9876543210");
        if (isEdit) etPhone.setText(existing.personPhone);
        form.addView(etPhone);

        // Amount
        form.addView(formLabel("Amount *"));
        EditText etAmount = createEditText("0.00");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (isEdit) etAmount.setText(String.valueOf(existing.amount));
        form.addView(etAmount);

        // Description
        form.addView(formLabel("Description *"));
        EditText etDesc = createEditText("What is this for?");
        if (isEdit) etDesc.setText(existing.description);
        form.addView(etDesc);

        // Date
        form.addView(formLabel("Date *"));
        final long[] selectedDate = {isEdit ? existing.date : System.currentTimeMillis()};
        TextView tvDatePicker = new TextView(this);
        tvDatePicker.setTextColor(Color.WHITE);
        tvDatePicker.setTextSize(14);
        tvDatePicker.setBackgroundResource(R.drawable.glass_card_bg);
        tvDatePicker.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvDatePicker.setLayoutParams(fieldLp());
        tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
        tvDatePicker.setClickable(true);
        tvDatePicker.setFocusable(true);
        tvDatePicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(selectedDate[0]);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 12, 0, 0);
                selectedDate[0] = sel.getTimeInMillis();
                tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(tvDatePicker);

        // Expected return date
        form.addView(formLabel("Expected Return Date (optional)"));
        final long[] selectedReturnDate = {isEdit ? existing.expectedReturnDate : 0};
        TextView tvReturnPicker = new TextView(this);
        tvReturnPicker.setTextColor(Color.WHITE);
        tvReturnPicker.setTextSize(14);
        tvReturnPicker.setBackgroundResource(R.drawable.glass_card_bg);
        tvReturnPicker.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvReturnPicker.setLayoutParams(fieldLp());
        tvReturnPicker.setText(selectedReturnDate[0] > 0
                ? dateFmt.format(new Date(selectedReturnDate[0])) : "Tap to set (optional)");
        tvReturnPicker.setClickable(true);
        tvReturnPicker.setFocusable(true);
        tvReturnPicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (selectedReturnDate[0] > 0) c.setTimeInMillis(selectedReturnDate[0]);
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 23, 59, 0);
                selectedReturnDate[0] = sel.getTimeInMillis();
                tvReturnPicker.setText(dateFmt.format(new Date(selectedReturnDate[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(tvReturnPicker);

        // Wallet selector
        form.addView(formLabel("Wallet"));
        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();
        final String[] selectedWalletId = {isEdit && existing.walletId != null ? existing.walletId
                : (wallets.isEmpty() ? "" : wallets.get(0).id)};
        LinearLayout walletRow = new LinearLayout(this);
        walletRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wRowLp.bottomMargin = dp(10);
        walletRow.setLayoutParams(wRowLp);
        for (Wallet w : wallets) {
            TextView wChip = new TextView(this);
            wChip.setText(w.icon + " " + w.name);
            wChip.setTextSize(12);
            wChip.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams wcLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wcLp.rightMargin = dp(6);
            wChip.setLayoutParams(wcLp);
            wChip.setClickable(true);
            wChip.setFocusable(true);
            wChip.setOnClickListener(v -> {
                selectedWalletId[0] = w.id;
                for (int i = 0; i < walletRow.getChildCount(); i++) {
                    View child = walletRow.getChildAt(i);
                    child.setBackgroundResource(R.drawable.glass_card_bg);
                    ((TextView) child).setTextColor(Color.parseColor("#9CA3AF"));
                }
                wChip.setBackgroundResource(R.drawable.iou_tab_selected_bg);
                wChip.setTextColor(Color.WHITE);
            });
            boolean sel = w.id.equals(selectedWalletId[0]);
            wChip.setBackgroundResource(sel ? R.drawable.iou_tab_selected_bg
                    : R.drawable.glass_card_bg);
            wChip.setTextColor(Color.parseColor(sel ? "#FFFFFF" : "#9CA3AF"));
            walletRow.addView(wChip);
        }
        form.addView(walletRow);

        // Notes
        form.addView(formLabel("Notes (optional)"));
        EditText etNotes = createEditText("Additional notes...");
        etNotes.setMinLines(2);
        if (isEdit) etNotes.setText(existing.notes);
        form.addView(etNotes);

        // Log in wallet toggle
        LinearLayout logRow = new LinearLayout(this);
        logRow.setOrientation(LinearLayout.HORIZONTAL);
        logRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams logRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        logRowLp.topMargin = dp(8);
        logRow.setLayoutParams(logRowLp);
        TextView logLabel = new TextView(this);
        logLabel.setText("Also log in wallet");
        logLabel.setTextColor(Color.WHITE);
        logLabel.setTextSize(13);
        LinearLayout.LayoutParams logLblLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        logLabel.setLayoutParams(logLblLp);
        logRow.addView(logLabel);
        Switch swLogWallet = new Switch(this);
        swLogWallet.setChecked(isEdit ? existing.logInWallet : false);
        logRow.addView(swLogWallet);
        form.addView(logRow);

        // Reminder toggle
        LinearLayout reminderRow = new LinearLayout(this);
        reminderRow.setOrientation(LinearLayout.HORIZONTAL);
        reminderRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams remRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        remRowLp.topMargin = dp(8);
        reminderRow.setLayoutParams(remRowLp);
        TextView reminderLabel = new TextView(this);
        reminderLabel.setText("Enable reminders");
        reminderLabel.setTextColor(Color.WHITE);
        reminderLabel.setTextSize(13);
        LinearLayout.LayoutParams remLblLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        reminderLabel.setLayoutParams(remLblLp);
        reminderRow.addView(reminderLabel);
        Switch swReminder = new Switch(this);
        swReminder.setChecked(isEdit && existing.reminderEnabled);
        reminderRow.addView(swReminder);
        form.addView(reminderRow);

        String title = isEdit ? "Edit Record" : "Add IOU Record";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton(isEdit ? "Save" : "Add", (d, w) -> {
                    String person = etPerson.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();

                    if (TextUtils.isEmpty(person)) {
                        Toast.makeText(this, "Enter person name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(amtStr)) {
                        Toast.makeText(this, "Enter amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(desc)) {
                        Toast.makeText(this, "Enter description", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(amtStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    MoneyRecord record = isEdit ? existing : new MoneyRecord();
                    record.type = selectedType[0];
                    record.personName = person;
                    record.personPhone = etPhone.getText().toString().trim();
                    record.personAvatarColorHex = MoneyRecord.pickAvatarColor(person);
                    record.amount = amount;
                    record.description = desc;
                    record.date = selectedDate[0];
                    record.expectedReturnDate = selectedReturnDate[0];
                    record.walletId = selectedWalletId[0];
                    record.notes = etNotes.getText().toString().trim();
                    record.logInWallet = swLogWallet.isChecked();
                    record.reminderEnabled = swReminder.isChecked();

                    if (isEdit) {
                        repo.updateRecord(record);
                        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
                    } else {
                        repo.addRecord(record);
                        Toast.makeText(this, "Record added", Toast.LENGTH_SHORT).show();
                    }
                    refreshAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════
    //  ADD REPAYMENT DIALOG
    // ═══════════════════════════════════════════════════════════

    private void showAddRepaymentDialog(MoneyRecord record) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(16));

        form.addView(formLabel("Amount *"));
        EditText etAmount = createEditText("0.00");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format("%.2f", record.getOutstandingAmount()));
        form.addView(etAmount);

        form.addView(formLabel("Date *"));
        final long[] selectedDate = {System.currentTimeMillis()};
        TextView tvDatePicker = new TextView(this);
        tvDatePicker.setTextColor(Color.WHITE);
        tvDatePicker.setTextSize(14);
        tvDatePicker.setBackgroundResource(R.drawable.glass_card_bg);
        tvDatePicker.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvDatePicker.setLayoutParams(fieldLp());
        tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
        tvDatePicker.setClickable(true);
        tvDatePicker.setFocusable(true);
        tvDatePicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (dp, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 12, 0, 0);
                selectedDate[0] = sel.getTimeInMillis();
                tvDatePicker.setText(dateFmt.format(new Date(selectedDate[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(tvDatePicker);

        // Wallet selector
        form.addView(formLabel("Wallet (optional)"));
        ArrayList<Wallet> wallets = walletRepo.getActiveWallets();
        final String[] selectedWalletId = {""};
        LinearLayout walletRow = new LinearLayout(this);
        walletRow.setOrientation(LinearLayout.HORIZONTAL);
        walletRow.setLayoutParams(fieldLp());
        for (Wallet w : wallets) {
            TextView wChip = new TextView(this);
            wChip.setText(w.icon + " " + w.name);
            wChip.setTextSize(12);
            wChip.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams wcLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wcLp.rightMargin = dp(6);
            wChip.setLayoutParams(wcLp);
            wChip.setBackgroundResource(R.drawable.glass_card_bg);
            wChip.setTextColor(Color.parseColor("#9CA3AF"));
            wChip.setClickable(true);
            wChip.setFocusable(true);
            wChip.setOnClickListener(v -> {
                String prevId = selectedWalletId[0];
                selectedWalletId[0] = prevId.equals(w.id) ? "" : w.id;
                for (int i = 0; i < walletRow.getChildCount(); i++) {
                    View child = walletRow.getChildAt(i);
                    child.setBackgroundResource(R.drawable.glass_card_bg);
                    ((TextView) child).setTextColor(Color.parseColor("#9CA3AF"));
                }
                if (!selectedWalletId[0].isEmpty()) {
                    wChip.setBackgroundResource(R.drawable.iou_tab_selected_bg);
                    wChip.setTextColor(Color.WHITE);
                }
            });
            walletRow.addView(wChip);
        }
        form.addView(walletRow);

        form.addView(formLabel("Notes (optional)"));
        EditText etNotes = createEditText("Any notes...");
        form.addView(etNotes);

        new AlertDialog.Builder(this)
                .setTitle("Add Repayment — " + record.personName)
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
                    repayment.walletId = selectedWalletId[0];
                    repayment.notes = etNotes.getText().toString().trim();

                    repo.addRepayment(repayment, walletRepo);
                    MoneyRecord updated = repo.getById(record.id);
                    if (updated != null && MoneyRecord.STATUS_SETTLED.equals(updated.status)) {
                        IouNotificationHelper.sendSettledNotification(this, updated);
                    }
                    refreshAll();
                    Toast.makeText(this, "Repayment saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTypeChips(TextView lentChip, TextView borrowedChip, String type) {
        boolean isLent = MoneyRecord.TYPE_LENT.equals(type);
        lentChip.setBackgroundResource(isLent ? R.drawable.iou_lent_chip_bg : R.drawable.glass_card_bg);
        lentChip.setTextColor(Color.parseColor(isLent ? "#22C55E" : "#9CA3AF"));
        borrowedChip.setBackgroundResource(!isLent ? R.drawable.iou_borrowed_chip_bg : R.drawable.glass_card_bg);
        borrowedChip.setTextColor(Color.parseColor(!isLent ? "#EF4444" : "#9CA3AF"));
    }

    // ─── Helpers ─────────────────────────────────────────────

    private TextView formLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9CA3AF"));
        tv.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(3);
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText createEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#6B7280"));
        et.setTextSize(14);
        et.setBackgroundResource(R.drawable.glass_card_bg);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setLayoutParams(fieldLp());
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
