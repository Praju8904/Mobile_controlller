package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.expenses.Expense;
import com.prajwal.myfirstapp.expenses.ExpenseRepository;
import com.prajwal.myfirstapp.expenses.Settlement;
import com.prajwal.myfirstapp.expenses.SplitExpense;
import com.prajwal.myfirstapp.expenses.SplitGroup;
import com.prajwal.myfirstapp.expenses.SplitMember;
import com.prajwal.myfirstapp.expenses.SplitRepository;
import com.prajwal.myfirstapp.expenses.Wallet;
import com.prajwal.myfirstapp.expenses.WalletRepository;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles CSV and PDF export of expense data.
 * Supports multiple data scopes: all expenses, wallet-specific, group-specific.
 */
public class ExportService {

    private final Context context;
    private final ExpenseRepository expenseRepo;
    private final WalletRepository walletRepo;
    private final SplitRepository splitRepo;

    private final NumberFormat currFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat fileDateFmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    // State
    private boolean formatCSV = true;
    private int dateRangeMode = 0; // 0=this month, 1=3 months, 2=all
    private boolean includeExpenses = true;
    private boolean includeIncome = true;
    private boolean includeWalletInfo = true;
    private boolean includeCategorySummary = true;

    public ExportService(Context context) {
        this.context = context;
        this.expenseRepo = new ExpenseRepository(context);
        this.walletRepo = new WalletRepository(context);
        this.splitRepo = new SplitRepository(context);
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT DIALOG
    // ═══════════════════════════════════════════════════════════

    public void showExportDialog(String groupId) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.DarkBottomSheetDialog);
        View sheet = LayoutInflater.from(context).inflate(R.layout.dialog_export, null);
        dialog.setContentView(sheet);

        TextView btnCSV = sheet.findViewById(R.id.btnFormatCSV);
        TextView btnPDF = sheet.findViewById(R.id.btnFormatPDF);
        TextView btnMonth = sheet.findViewById(R.id.btnDateThisMonth);
        TextView btnLast3 = sheet.findViewById(R.id.btnDateLast3);
        TextView btnAll = sheet.findViewById(R.id.btnDateAll);
        CheckBox cbExpenses = sheet.findViewById(R.id.cbExpenses);
        CheckBox cbIncome = sheet.findViewById(R.id.cbIncome);
        CheckBox cbWallet = sheet.findViewById(R.id.cbWalletInfo);
        CheckBox cbCategory = sheet.findViewById(R.id.cbCategorySummary);
        TextView tvPreview = sheet.findViewById(R.id.tvExportPreview);
        TextView tvRecentLabel = sheet.findViewById(R.id.tvRecentExportsLabel);
        LinearLayout recentContainer = sheet.findViewById(R.id.recentExportsContainer);

        // If group-specific export, hide some options
        if (groupId != null) {
            cbWallet.setVisibility(View.GONE);
            cbIncome.setVisibility(View.GONE);
        }

        // Format toggle
        btnCSV.setOnClickListener(v -> {
            formatCSV = true;
            btnCSV.setTextColor(Color.WHITE);
            btnCSV.setTypeface(null, android.graphics.Typeface.BOLD);
            btnCSV.setBackgroundResource(R.drawable.export_format_selected_bg);
            btnPDF.setTextColor(Color.parseColor("#9CA3AF"));
            btnPDF.setTypeface(null, android.graphics.Typeface.NORMAL);
            btnPDF.setBackgroundResource(0);
            updatePreview(tvPreview, groupId);
        });
        btnPDF.setOnClickListener(v -> {
            formatCSV = false;
            btnPDF.setTextColor(Color.WHITE);
            btnPDF.setTypeface(null, android.graphics.Typeface.BOLD);
            btnPDF.setBackgroundResource(R.drawable.export_format_selected_bg);
            btnCSV.setTextColor(Color.parseColor("#9CA3AF"));
            btnCSV.setTypeface(null, android.graphics.Typeface.NORMAL);
            btnCSV.setBackgroundResource(0);
            updatePreview(tvPreview, groupId);
        });

        // Date range toggle
        TextView[] dateButtons = {btnMonth, btnLast3, btnAll};
        for (int i = 0; i < dateButtons.length; i++) {
            final int idx = i;
            dateButtons[i].setOnClickListener(v -> {
                dateRangeMode = idx;
                for (int j = 0; j < dateButtons.length; j++) {
                    dateButtons[j].setTextColor(j == idx ? Color.WHITE : Color.parseColor("#9CA3AF"));
                    dateButtons[j].setTypeface(null, j == idx ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                    dateButtons[j].setBackgroundResource(j == idx ? R.drawable.split_chip_selected_bg : R.drawable.split_chip_bg);
                }
                updatePreview(tvPreview, groupId);
            });
        }

        // Checkboxes
        cbExpenses.setOnCheckedChangeListener((b, c) -> { includeExpenses = c; updatePreview(tvPreview, groupId); });
        cbIncome.setOnCheckedChangeListener((b, c) -> { includeIncome = c; updatePreview(tvPreview, groupId); });
        cbWallet.setOnCheckedChangeListener((b, c) -> { includeWalletInfo = c; });
        cbCategory.setOnCheckedChangeListener((b, c) -> { includeCategorySummary = c; });

        updatePreview(tvPreview, groupId);

        // Recent exports
        ArrayList<org.json.JSONObject> history = splitRepo.getExportHistory();
        if (!history.isEmpty()) {
            tvRecentLabel.setVisibility(View.VISIBLE);
            for (org.json.JSONObject record : history) {
                try {
                    String fmt = record.optString("format", "CSV");
                    long date = record.optLong("date", 0);
                    String range = record.optString("dateRange", "");
                    TextView tv = new TextView(context);
                    tv.setText(fmt.toUpperCase() + " • " + dateFmt.format(new Date(date)) + " • " + range);
                    tv.setTextColor(Color.parseColor("#6B7280"));
                    tv.setTextSize(12);
                    tv.setPadding(0, dp(4), 0, dp(4));
                    recentContainer.addView(tv);
                } catch (Exception ignored) {}
            }
        }

        // Export button
        sheet.findViewById(R.id.btnExport).setOnClickListener(v -> {
            dialog.dismiss();
            if (groupId != null) {
                exportGroupData(groupId);
            } else {
                exportAllData();
            }
        });

        dialog.show();
    }

    /**
     * Convenience overload: show export dialog for all data.
     */
    public void showExportDialog() {
        showExportDialog(null);
    }

    private void updatePreview(TextView tvPreview, String groupId) {
        long[] range = getDateRange();
        int count;
        if (groupId != null) {
            count = 0;
            for (SplitExpense e : splitRepo.getExpensesForGroup(groupId)) {
                if (e.date >= range[0] && e.date <= range[1]) count++;
            }
        } else {
            count = getFilteredExpenses(range[0], range[1]).size();
        }
        tvPreview.setText("Estimated: " + count + " transactions • " + (formatCSV ? "CSV" : "PDF"));
    }

    // ═══════════════════════════════════════════════════════════
    //  DATE RANGE
    // ═══════════════════════════════════════════════════════════

    private long[] getDateRange() {
        Calendar cal = Calendar.getInstance();
        long end = cal.getTimeInMillis();
        long start;

        switch (dateRangeMode) {
            case 0: // This month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                start = cal.getTimeInMillis();
                break;
            case 1: // Last 3 months
                cal.add(Calendar.MONTH, -3);
                start = cal.getTimeInMillis();
                break;
            default: // All time
                start = 0;
                break;
        }

        return new long[]{start, end};
    }

    private String getDateRangeLabel() {
        switch (dateRangeMode) {
            case 0: return "This Month";
            case 1: return "Last 3 Months";
            default: return "All Time";
        }
    }

    private ArrayList<Expense> getFilteredExpenses(long start, long end) {
        ArrayList<Expense> all = expenseRepo.loadAll();
        ArrayList<Expense> filtered = new ArrayList<>();
        for (Expense e : all) {
            if (e.timestamp >= start && e.timestamp <= end) {
                if (e.isIncome && !includeIncome) continue;
                if (!e.isIncome && !includeExpenses) continue;
                filtered.add(e);
            }
        }
        return filtered;
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT ALL DATA
    // ═══════════════════════════════════════════════════════════

    private void exportAllData() {
        long[] range = getDateRange();
        ArrayList<Expense> expenses = getFilteredExpenses(range[0], range[1]);

        if (expenses.isEmpty()) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        File file;
        if (formatCSV) {
            file = exportToCSV(expenses);
        } else {
            file = exportToPDF(expenses);
        }

        if (file != null) {
            splitRepo.addExportRecord(formatCSV ? "CSV" : "PDF", getDateRangeLabel(), file.length(), file.getAbsolutePath());
            shareFile(file);
        }
    }

    private void exportGroupData(String groupId) {
        long[] range = getDateRange();
        SplitGroup group = splitRepo.getGroupById(groupId);
        ArrayList<SplitExpense> expenses = new ArrayList<>();
        for (SplitExpense e : splitRepo.getExpensesForGroup(groupId)) {
            if (e.date >= range[0] && e.date <= range[1]) expenses.add(e);
        }

        if (expenses.isEmpty()) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        File file;
        if (formatCSV) {
            file = exportGroupToCSV(group, expenses);
        } else {
            file = exportGroupToPDF(group, expenses);
        }

        if (file != null) {
            splitRepo.addExportRecord(formatCSV ? "CSV" : "PDF", getDateRangeLabel(), file.length(), file.getAbsolutePath());
            shareFile(file);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CSV EXPORT
    // ═══════════════════════════════════════════════════════════

    private File exportToCSV(ArrayList<Expense> expenses) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            dir.mkdirs();
            File file = new File(dir, "expenses_" + fileDateFmt.format(new Date()) + ".csv");

            FileWriter writer = new FileWriter(file);

            // Header
            if (includeWalletInfo) {
                writer.append("Date,Type,Category,Amount,Note,Wallet\n");
            } else {
                writer.append("Date,Type,Category,Amount,Note\n");
            }

            // Data rows
            for (Expense e : expenses) {
                writer.append(dateFmt.format(new Date(e.timestamp))).append(",");
                writer.append(e.isIncome ? "Income" : "Expense").append(",");
                writer.append(escapeCsv(e.category)).append(",");
                writer.append(String.format(Locale.US, "%.2f", e.amount)).append(",");
                writer.append(escapeCsv(e.note != null ? e.note : ""));
                if (includeWalletInfo) {
                    Wallet w = walletRepo.getById(e.walletId);
                    writer.append(",").append(escapeCsv(w != null ? w.name : "Default"));
                }
                writer.append("\n");
            }

            // Category summary (append at bottom)
            if (includeCategorySummary) {
                writer.append("\n\nCategory Summary\n");
                writer.append("Category,Total\n");
                Map<String, Double> catMap = new HashMap<>();
                for (Expense e : expenses) {
                    if (!e.isIncome) {
                        catMap.put(e.category, catMap.getOrDefault(e.category, 0.0) + e.amount);
                    }
                }
                for (Map.Entry<String, Double> entry : catMap.entrySet()) {
                    writer.append(escapeCsv(entry.getKey())).append(",");
                    writer.append(String.format(Locale.US, "%.2f", entry.getValue())).append("\n");
                }
            }

            writer.flush();
            writer.close();

            Toast.makeText(context, "CSV exported!", Toast.LENGTH_SHORT).show();
            return file;

        } catch (Exception e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private File exportGroupToCSV(SplitGroup group, ArrayList<SplitExpense> expenses) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            dir.mkdirs();
            String safeName = group.name.replaceAll("[^a-zA-Z0-9]", "_");
            File file = new File(dir, "split_" + safeName + "_" + fileDateFmt.format(new Date()) + ".csv");

            FileWriter writer = new FileWriter(file);
            writer.append("Date,Title,Category,Total Amount,Paid By,Split Type,Notes\n");

            for (SplitExpense e : expenses) {
                writer.append(dateFmt.format(new Date(e.date))).append(",");
                writer.append(escapeCsv(e.title)).append(",");
                writer.append(escapeCsv(e.categoryId != null ? e.categoryId : "")).append(",");
                writer.append(String.format(Locale.US, "%.2f", e.totalAmount)).append(",");
                writer.append(escapeCsv(group.getMemberName(e.paidByMemberId))).append(",");
                writer.append(escapeCsv(e.splitType)).append(",");
                writer.append(escapeCsv(e.notes != null ? e.notes : ""));
                writer.append("\n");
            }

            // Member breakdown
            writer.append("\n\nMember Balances\n");
            writer.append("Member,Balance\n");
            Map<String, Double> balances = splitRepo.calculateMemberBalances(group.id);
            for (SplitMember m : group.members) {
                Double bal = balances.get(m.id);
                writer.append(escapeCsv(m.name)).append(",");
                writer.append(String.format(Locale.US, "%.2f", bal != null ? bal : 0)).append("\n");
            }

            // Settlements
            ArrayList<Settlement> settlements = splitRepo.getSettlementsForGroup(group.id);
            if (!settlements.isEmpty()) {
                writer.append("\n\nSettlements\n");
                writer.append("Date,From,To,Amount\n");
                for (Settlement s : settlements) {
                    writer.append(dateFmt.format(new Date(s.date))).append(",");
                    writer.append(escapeCsv(group.getMemberName(s.fromMemberId))).append(",");
                    writer.append(escapeCsv(group.getMemberName(s.toMemberId))).append(",");
                    writer.append(String.format(Locale.US, "%.2f", s.amount)).append("\n");
                }
            }

            writer.flush();
            writer.close();

            Toast.makeText(context, "CSV exported!", Toast.LENGTH_SHORT).show();
            return file;

        } catch (Exception e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PDF EXPORT
    // ═══════════════════════════════════════════════════════════

    private File exportToPDF(ArrayList<Expense> expenses) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            dir.mkdirs();
            File file = new File(dir, "expenses_" + fileDateFmt.format(new Date()) + ".pdf");

            PdfDocument document = new PdfDocument();
            int pageWidth = 595; // A4
            int pageHeight = 842;
            int margin = 40;
            int y = margin;
            int pageNum = 1;

            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.parseColor("#1E1B4B"));
            titlePaint.setTextSize(20);
            titlePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));

            Paint subtitlePaint = new Paint();
            subtitlePaint.setColor(Color.parseColor("#6B7280"));
            subtitlePaint.setTextSize(11);

            Paint headerPaint = new Paint();
            headerPaint.setColor(Color.parseColor("#7C3AED"));
            headerPaint.setTextSize(12);
            headerPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));

            Paint textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#111827"));
            textPaint.setTextSize(10);

            Paint linePaint = new Paint();
            linePaint.setColor(Color.parseColor("#E5E7EB"));
            linePaint.setStrokeWidth(0.5f);

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.parseColor("#F3F4F6"));

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Title
            canvas.drawText("Expense Report", margin, y + 20, titlePaint);
            y += 30;
            canvas.drawText(getDateRangeLabel() + " • Generated " + dateFmt.format(new Date()), margin, y + 12, subtitlePaint);
            y += 30;

            // Summary stats
            double totalExp = 0, totalInc = 0;
            for (Expense e : expenses) {
                if (e.isIncome) totalInc += e.amount;
                else totalExp += e.amount;
            }
            canvas.drawText("Total Expenses: " + currFmt.format(totalExp), margin, y + 12, headerPaint);
            y += 18;
            canvas.drawText("Total Income: " + currFmt.format(totalInc), margin, y + 12, headerPaint);
            y += 18;
            canvas.drawText("Net: " + currFmt.format(totalInc - totalExp), margin, y + 12, headerPaint);
            y += 30;

            // Table header
            canvas.drawRect(margin, y, pageWidth - margin, y + 20, bgPaint);
            int[] colX = {margin + 4, margin + 80, margin + 160, margin + 260, margin + 340};
            canvas.drawText("Date", colX[0], y + 14, headerPaint);
            canvas.drawText("Category", colX[1], y + 14, headerPaint);
            canvas.drawText("Note", colX[2], y + 14, headerPaint);
            canvas.drawText("Amount", colX[3], y + 14, headerPaint);
            if (includeWalletInfo) canvas.drawText("Wallet", colX[4], y + 14, headerPaint);
            y += 24;

            // Table rows
            for (Expense e : expenses) {
                if (y > pageHeight - margin - 40) {
                    document.finishPage(page);
                    pageNum++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin;
                }

                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                canvas.drawText(dateFmt.format(new Date(e.timestamp)), colX[0], y + 14, textPaint);
                canvas.drawText(e.category, colX[1], y + 14, textPaint);
                String note = e.note != null ? e.note : "";
                if (note.length() > 18) note = note.substring(0, 18) + "…";
                canvas.drawText(note, colX[2], y + 14, textPaint);

                Paint amtPaint = new Paint(textPaint);
                amtPaint.setColor(e.isIncome ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));
                canvas.drawText((e.isIncome ? "+" : "-") + String.format(Locale.US, "₹%.2f", e.amount), colX[3], y + 14, amtPaint);

                if (includeWalletInfo) {
                    Wallet w = walletRepo.getById(e.walletId);
                    canvas.drawText(w != null ? w.name : "Default", colX[4], y + 14, textPaint);
                }
                y += 20;
            }

            // Category summary on new page if requested
            if (includeCategorySummary) {
                document.finishPage(page);
                pageNum++;
                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = margin;

                canvas.drawText("Category Breakdown", margin, y + 20, titlePaint);
                y += 40;

                Map<String, Double> catTotals = new HashMap<>();
                for (Expense e : expenses) {
                    if (!e.isIncome) {
                        catTotals.put(e.category, catTotals.getOrDefault(e.category, 0.0) + e.amount);
                    }
                }

                canvas.drawRect(margin, y, pageWidth - margin, y + 20, bgPaint);
                canvas.drawText("Category", margin + 4, y + 14, headerPaint);
                canvas.drawText("Amount", margin + 200, y + 14, headerPaint);
                canvas.drawText("% of Total", margin + 340, y + 14, headerPaint);
                y += 24;

                for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                    canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                    canvas.drawText(entry.getKey(), margin + 4, y + 14, textPaint);
                    canvas.drawText(currFmt.format(entry.getValue()), margin + 200, y + 14, textPaint);
                    double pct = totalExp > 0 ? (entry.getValue() / totalExp * 100) : 0;
                    canvas.drawText(String.format(Locale.US, "%.1f%%", pct), margin + 340, y + 14, textPaint);

                    // Mini bar
                    Paint barPaint = new Paint();
                    barPaint.setColor(Expense.getCategoryColor(entry.getKey()));
                    float barWidth = (float) (pct / 100.0 * 150);
                    canvas.drawRect(margin + 400, y + 3, margin + 400 + barWidth, y + 15, barPaint);

                    y += 20;
                }
            }

            document.finishPage(page);

            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            fos.close();
            document.close();

            Toast.makeText(context, "PDF exported!", Toast.LENGTH_SHORT).show();
            return file;

        } catch (Exception e) {
            Toast.makeText(context, "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private File exportGroupToPDF(SplitGroup group, ArrayList<SplitExpense> expenses) {
        try {
            File dir = new File(context.getExternalFilesDir(null), "exports");
            dir.mkdirs();
            String safeName = group.name.replaceAll("[^a-zA-Z0-9]", "_");
            File file = new File(dir, "split_" + safeName + "_" + fileDateFmt.format(new Date()) + ".pdf");

            PdfDocument document = new PdfDocument();
            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 40;
            int y = margin;

            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.parseColor("#1E1B4B"));
            titlePaint.setTextSize(20);
            titlePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));

            Paint headerPaint = new Paint();
            headerPaint.setColor(Color.parseColor("#7C3AED"));
            headerPaint.setTextSize(12);
            headerPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));

            Paint textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#111827"));
            textPaint.setTextSize(10);

            Paint subtitlePaint = new Paint();
            subtitlePaint.setColor(Color.parseColor("#6B7280"));
            subtitlePaint.setTextSize(11);

            Paint linePaint = new Paint();
            linePaint.setColor(Color.parseColor("#E5E7EB"));
            linePaint.setStrokeWidth(0.5f);

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.parseColor("#F3F4F6"));

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Title
            canvas.drawText(group.name + " — Split Report", margin, y + 20, titlePaint);
            y += 30;
            canvas.drawText(group.getMemberCount() + " members • " + getDateRangeLabel(), margin, y + 12, subtitlePaint);
            y += 30;

            // Total
            canvas.drawText("Total Spent: " + currFmt.format(group.totalExpenses), margin, y + 12, headerPaint);
            y += 30;

            // Member balances
            canvas.drawText("Member Balances", margin, y + 14, headerPaint);
            y += 20;

            Map<String, Double> balances = splitRepo.calculateMemberBalances(group.id);
            for (SplitMember m : group.members) {
                Double bal = balances.get(m.id);
                double b = bal != null ? bal : 0;

                canvas.drawText(m.name, margin + 10, y + 14, textPaint);
                Paint balPaint = new Paint(textPaint);
                balPaint.setColor(b >= 0 ? Color.parseColor("#22C55E") : Color.parseColor("#EF4444"));
                canvas.drawText(currFmt.format(Math.abs(b)) + (b >= 0 ? " (gets back)" : " (owes)"), margin + 200, y + 14, balPaint);
                y += 18;
            }
            y += 10;

            // Simplified debts
            ArrayList<SplitRepository.DebtTransaction> debts = splitRepo.getSimplifiedDebts(group.id);
            if (!debts.isEmpty()) {
                canvas.drawText("Simplified Settlements", margin, y + 14, headerPaint);
                y += 20;
                for (SplitRepository.DebtTransaction dt : debts) {
                    canvas.drawText(group.getMemberName(dt.fromMemberId) + " → " + group.getMemberName(dt.toMemberId) + ": " + currFmt.format(dt.amount),
                            margin + 10, y + 14, textPaint);
                    y += 18;
                }
                y += 10;
            }

            // Expense table
            canvas.drawText("Expenses", margin, y + 14, headerPaint);
            y += 22;

            canvas.drawRect(margin, y, pageWidth - margin, y + 18, bgPaint);
            canvas.drawText("Date", margin + 4, y + 13, headerPaint);
            canvas.drawText("Title", margin + 80, y + 13, headerPaint);
            canvas.drawText("Amount", margin + 250, y + 13, headerPaint);
            canvas.drawText("Paid By", margin + 340, y + 13, headerPaint);
            y += 22;

            for (SplitExpense e : expenses) {
                if (y > pageHeight - margin - 30) {
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin;
                }

                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
                canvas.drawText(dateFmt.format(new Date(e.date)), margin + 4, y + 13, textPaint);
                String title = e.title;
                if (title.length() > 25) title = title.substring(0, 25) + "…";
                canvas.drawText(title, margin + 80, y + 13, textPaint);
                canvas.drawText(currFmt.format(e.totalAmount), margin + 250, y + 13, textPaint);
                canvas.drawText(group.getMemberName(e.paidByMemberId), margin + 340, y + 13, textPaint);
                y += 18;
            }

            document.finishPage(page);

            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            fos.close();
            document.close();

            Toast.makeText(context, "PDF exported!", Toast.LENGTH_SHORT).show();
            return file;

        } catch (Exception e) {
            Toast.makeText(context, "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SHARE FILE
    // ═══════════════════════════════════════════════════════════

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(formatCSV ? "text/csv" : "application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share Export"));
        } catch (Exception e) {
            // If FileProvider not configured, try direct share
            Toast.makeText(context, "File saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
