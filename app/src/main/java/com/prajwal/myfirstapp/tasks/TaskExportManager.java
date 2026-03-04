package com.prajwal.myfirstapp.tasks;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Utility class for exporting tasks to CSV/JSON, importing from CSV,
 * and sharing individual tasks as formatted text.
 */
public class TaskExportManager {

    /** Simple callback interfaces (avoid requiring API 24+ java.util.function.Consumer). */
    public interface OnSuccess { void run(); }
    public interface OnError   { void accept(Exception e); }

    /** Import callback for CSV import results. */
    public interface ImportCallback {
        void onComplete(int importedCount, List<String> errors);
        void onError(String message);
    }

    private static final String CSV_HEADER =
            "id,title,description,priority,status,category,tags,dueDate,dueTime,notes,createdAt,completedAt";

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

    // ═══════════════════════════════════════════════════════════════
    // CSV EXPORT (existing)
    // ═══════════════════════════════════════════════════════════════

    /** Returns a CSV string with all fields for the given tasks. */
    public static String exportToCsv(List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\n");
        for (Task task : tasks) {
            sb.append(escapeCsv(task.id)).append(",");
            sb.append(escapeCsv(task.title)).append(",");
            sb.append(escapeCsv(task.description)).append(",");
            sb.append(escapeCsv(task.priority)).append(",");
            sb.append(escapeCsv(task.status)).append(",");
            sb.append(escapeCsv(task.category)).append(",");
            sb.append(escapeCsv(joinTags(task))).append(",");
            sb.append(escapeCsv(task.dueDate != null ? task.dueDate : "")).append(",");
            sb.append(escapeCsv(task.dueTime != null ? task.dueTime : "")).append(",");
            sb.append(escapeCsv(task.notes != null ? task.notes : "")).append(",");
            sb.append(escapeCsv(formatMillis(task.createdAt))).append(",");
            sb.append(escapeCsv(task.completedAt > 0 ? formatMillis(task.completedAt) : ""));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Writes the CSV to the Downloads folder.
     */
    public static void exportToFile(Context context, List<Task> tasks, String format,
                                    OnSuccess onSuccess, OnError onError) {
        new Thread(() -> {
            try {
                String csvContent = exportToCsv(tasks);
                String fileName = "tasks_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + ".csv";

                writeToDownloads(context, fileName, "text/csv",
                        csvContent.getBytes(StandardCharsets.UTF_8));

                android.os.Handler main = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                main.post(onSuccess::run);

            } catch (Exception e) {
                android.os.Handler main = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                main.post(() -> onError.accept(e));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON EXPORT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Exports all tasks as a JSON backup file to the Downloads folder.
     *
     * @param context Android context
     * @param tasks   list of tasks to export
     */
    public static void exportToJson(Context context, List<Task> tasks) {
        new Thread(() -> {
            try {
                JSONArray array = new JSONArray();
                for (Task t : tasks) {
                    array.put(t.toJson());
                }
                String json = array.toString(2); // pretty-printed with 2-space indent
                String filename = "tasks_backup_"
                        + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(new Date())
                        + ".json";

                writeToDownloads(context, filename, "application/json",
                        json.getBytes(StandardCharsets.UTF_8));

                android.os.Handler main = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "Exported to Downloads/" + filename, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                android.os.Handler main = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // CSV IMPORT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Imports tasks from a CSV file. Runs entirely on a background thread.
     *
     * <p>The CSV must have a header row. Columns are matched case-insensitively.
     * At minimum, a "title" column is required. Unknown columns are ignored.
     * Rows without a title are skipped with an error entry.</p>
     *
     * @param context  Android context
     * @param fileUri  URI of the CSV file (from ActivityResultContracts.GetContent)
     * @param repo     TaskRepository for batch insert
     * @param callback receives the import result
     */
    public static void importFromCsv(Context context, Uri fileUri,
                                     TaskRepository repo, ImportCallback callback) {
        new Thread(() -> {
            List<Task> imported = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            try (InputStream is = context.getContentResolver().openInputStream(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String headerLine = reader.readLine();
                if (headerLine == null) {
                    postCallback(callback, "Empty file");
                    return;
                }

                // Map column names to indices
                String[] headers = headerLine.split(",");
                Map<String, Integer> colMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    colMap.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
                }

                String line;
                int lineNum = 1;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    try {
                        String[] cols = parseCsvLine(line);
                        Task t = new Task();
                        t.id = UUID.randomUUID().toString().substring(0, 12);
                        t.title = getCol(cols, colMap, "title");
                        if (t.title == null || t.title.trim().isEmpty()) {
                            errors.add("Line " + lineNum + ": missing title — skipped");
                            continue;
                        }
                        t.description = getCol(cols, colMap, "description");
                        t.priority = getCol(cols, colMap, "priority");
                        if (t.priority == null || t.priority.isEmpty()) {
                            t.priority = Task.PRIORITY_NORMAL;
                        }
                        t.dueDate = getCol(cols, colMap, "duedate");
                        if (t.dueDate == null) t.dueDate = getCol(cols, colMap, "due_date");
                        t.dueTime = getCol(cols, colMap, "duetime");
                        if (t.dueTime == null) t.dueTime = getCol(cols, colMap, "due_time");
                        t.category = getCol(cols, colMap, "category");
                        t.notes = getCol(cols, colMap, "notes");
                        t.status = Task.STATUS_TODO;
                        t.createdAt = System.currentTimeMillis();
                        t.updatedAt = System.currentTimeMillis();
                        t.source = "import_csv";

                        // Parse tags from pipe-separated or semicolon-separated string
                        String tagsStr = getCol(cols, colMap, "tags");
                        if (tagsStr != null && !tagsStr.isEmpty()) {
                            String delimiter = tagsStr.contains("|") ? "\\|" : ";";
                            t.tags = new ArrayList<>(Arrays.asList(tagsStr.split(delimiter)));
                        }

                        imported.add(t);
                    } catch (Exception e) {
                        errors.add("Line " + lineNum + ": " + e.getMessage());
                    }
                }

                // Batch insert all imported tasks 
                for (Task t : imported) {
                    repo.addTask(t);
                }

                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> callback.onComplete(imported.size(), errors));

            } catch (Exception e) {
                postCallback(callback, e.getMessage());
            }
        }).start();
    }

    private static void postCallback(ImportCallback callback, String error) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> callback.onError(error));
    }

    // ═══════════════════════════════════════════════════════════════
    // SHARE TASK AS TEXT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Shares a single task as a formatted plain-text string via the system
     * share sheet. Includes title, priority, due date, description, and subtasks.
     */
    public static void shareTaskAsText(Context context, Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(task.title).append("\n");
        if (task.priority != null && !Task.PRIORITY_NONE.equals(task.priority)) {
            sb.append("Priority: ").append(TaskUtils.capitalize(task.priority)).append("\n");
        }
        if (task.dueDate != null) {
            sb.append("Due: ").append(TaskUtils.formatDateForDisplay(task.dueDate));
            if (task.dueTime != null) sb.append(" at ").append(task.dueTime);
            sb.append("\n");
        }
        if (task.category != null && !task.category.isEmpty()) {
            sb.append("Category: ").append(task.category).append("\n");
        }
        if (task.description != null && !task.description.isEmpty()) {
            sb.append("\n").append(task.description).append("\n");
        }
        if (task.subtasks != null && !task.subtasks.isEmpty()) {
            sb.append("\nSubtasks:\n");
            for (SubTask st : task.subtasks) {
                sb.append(st.isCompleted ? "✓ " : "○ ").append(st.title).append("\n");
            }
        }
        if (task.tags != null && !task.tags.isEmpty()) {
            sb.append("\nTags: ");
            for (int i = 0; i < task.tags.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("#").append(task.tags.get(i));
            }
            sb.append("\n");
        }
        sb.append("\nShared from advanced_app");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, task.title);
        context.startActivity(Intent.createChooser(shareIntent, "Share Task"));
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS (PRIVATE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Writes data to the Downloads folder, compatible with both pre-Q and Q+ APIs.
     */
    private static void writeToDownloads(Context context, String filename,
                                         String mimeType, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create file in Downloads");
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("Could not open output stream");
                os.write(data);
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
        }
    }

    /**
     * Parses a CSV line handling quoted fields with embedded commas and quotes.
     */
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(field.toString().trim());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
        }
        result.add(field.toString().trim());
        return result.toArray(new String[0]);
    }

    /**
     * Gets a column value from a parsed CSV row by column name.
     */
    private static String getCol(String[] cols, Map<String, Integer> colMap, String name) {
        Integer idx = colMap.get(name.toLowerCase(Locale.ROOT));
        if (idx == null || idx >= cols.length) return null;
        String val = cols[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String joinTags(Task task) {
        if (task.tags == null || task.tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < task.tags.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(task.tags.get(i));
        }
        return sb.toString();
    }

    private static String formatMillis(long millis) {
        if (millis <= 0) return "";
        return DATE_FMT.format(new Date(millis));
    }

    // ═══════════════════════════════════════════════════════════════
    // iCAL (.ics) EXPORT — RFC 5545
    // ═══════════════════════════════════════════════════════════════

    /**
     * Exports tasks with a dueDate as an iCal (.ics) file and offers share.
     */
    public static void exportToIcs(Context context, List<Task> tasks) {
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("BEGIN:VCALENDAR\r\n");
                sb.append("VERSION:2.0\r\n");
                sb.append("PRODID:-//advanced_app//TaskManager//EN\r\n");
                sb.append("CALSCALE:GREGORIAN\r\n");
                sb.append("METHOD:PUBLISH\r\n");

                SimpleDateFormat dtStamp = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
                dtStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
                String now = dtStamp.format(new Date());

                SimpleDateFormat dateOnly = new SimpleDateFormat("yyyyMMdd", Locale.US);

                for (Task t : tasks) {
                    if (t.dueDate == null || t.dueDate.isEmpty()) continue;

                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:").append(t.id).append("@myfirstapp\r\n");
                    sb.append("DTSTAMP:").append(now).append("\r\n");

                    // Parse dueDate (yyyy-MM-dd)
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        Date due = sdf.parse(t.dueDate);
                        if (due != null) {
                            sb.append("DTSTART;VALUE=DATE:").append(dateOnly.format(due)).append("\r\n");
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(due);
                            cal.add(Calendar.DAY_OF_MONTH, 1);
                            sb.append("DTEND;VALUE=DATE:").append(dateOnly.format(cal.getTime())).append("\r\n");
                        }
                    } catch (Exception ignored) {}

                    sb.append("SUMMARY:").append(icsEscape(t.title)).append("\r\n");

                    // Description: include subtasks
                    StringBuilder desc = new StringBuilder();
                    if (t.description != null && !t.description.isEmpty()) {
                        desc.append(t.description);
                    }
                    if (t.subtasks != null && !t.subtasks.isEmpty()) {
                        if (desc.length() > 0) desc.append("\\n\\n");
                        desc.append("Subtasks:\\n");
                        for (SubTask st : t.subtasks) {
                            desc.append(st.isCompleted ? "[x] " : "[ ] ").append(st.title).append("\\n");
                        }
                    }
                    if (desc.length() > 0) {
                        sb.append("DESCRIPTION:").append(icsEscape(desc.toString())).append("\r\n");
                    }

                    // Priority mapping
                    int prio = 9;
                    if ("urgent".equals(t.priority)) prio = 1;
                    else if ("high".equals(t.priority)) prio = 3;
                    else if ("normal".equals(t.priority)) prio = 5;
                    else if ("low".equals(t.priority)) prio = 7;
                    sb.append("PRIORITY:").append(prio).append("\r\n");

                    sb.append("STATUS:").append(t.isCompleted() ? "COMPLETED" : "NEEDS-ACTION").append("\r\n");
                    sb.append("END:VEVENT\r\n");
                }

                sb.append("END:VCALENDAR\r\n");

                String filename = "tasks_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + ".ics";

                writeToDownloads(context, filename, "text/calendar",
                        sb.toString().getBytes(StandardCharsets.UTF_8));

                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "Exported to Downloads/" + filename, Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "iCal export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** RFC 5545 text escaping. */
    private static String icsEscape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }

    // ═══════════════════════════════════════════════════════════════
    // PDF EXPORT — android.graphics.pdf.PdfDocument
    // ═══════════════════════════════════════════════════════════════

    /** A4 page dimensions at 72 dpi. */
    private static final int PDF_PAGE_W = 595;
    private static final int PDF_PAGE_H = 842;
    private static final int PDF_MARGIN_TOP = 40;
    private static final int PDF_MARGIN_BOTTOM = 40;
    private static final int PDF_MARGIN_LEFT = 36;
    private static final int PDF_MARGIN_RIGHT = 36;
    private static final int PDF_USABLE_W = PDF_PAGE_W - PDF_MARGIN_LEFT - PDF_MARGIN_RIGHT;

    /**
     * Exports tasks as a paginated A4 PDF with priority stripes and metadata.
     */
    public static void exportToPdf(Context context, List<Task> tasks) {
        new Thread(() -> {
            try {
                PdfDocument doc = new PdfDocument();
                List<PdfDocument.Page> pages = new ArrayList<>();

                // Paints
                Paint titlePaint = new Paint();
                titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                titlePaint.setTextSize(18);
                titlePaint.setColor(android.graphics.Color.parseColor("#1E293B"));
                titlePaint.setAntiAlias(true);

                Paint subtitlePaint = new Paint();
                subtitlePaint.setTextSize(10);
                subtitlePaint.setColor(android.graphics.Color.parseColor("#64748B"));
                subtitlePaint.setAntiAlias(true);

                Paint taskTitlePaint = new Paint();
                taskTitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                taskTitlePaint.setTextSize(14);
                taskTitlePaint.setColor(android.graphics.Color.parseColor("#0F172A"));
                taskTitlePaint.setAntiAlias(true);

                Paint metaPaint = new Paint();
                metaPaint.setTextSize(10);
                metaPaint.setColor(android.graphics.Color.parseColor("#64748B"));
                metaPaint.setAntiAlias(true);

                Paint bodyPaint = new Paint();
                bodyPaint.setTextSize(10);
                bodyPaint.setColor(android.graphics.Color.parseColor("#374151"));
                bodyPaint.setAntiAlias(true);

                Paint subtaskPaint = new Paint();
                subtaskPaint.setTextSize(8);
                subtaskPaint.setColor(android.graphics.Color.parseColor("#374151"));
                subtaskPaint.setAntiAlias(true);

                Paint stripePaint = new Paint();
                stripePaint.setAntiAlias(true);

                Paint linePaint = new Paint();
                linePaint.setColor(android.graphics.Color.parseColor("#F1F5F9"));
                linePaint.setStrokeWidth(1);

                Paint rulePaint = new Paint();
                rulePaint.setColor(android.graphics.Color.parseColor("#E2E8F0"));
                rulePaint.setStrokeWidth(1);

                Paint footerPaint = new Paint();
                footerPaint.setTextSize(8);
                footerPaint.setColor(android.graphics.Color.parseColor("#94A3B8"));
                footerPaint.setAntiAlias(true);
                footerPaint.setTextAlign(Paint.Align.CENTER);

                int pageNum = 0;
                float currentY = 0;
                Canvas canvas = null;

                // Start first page
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        PDF_PAGE_W, PDF_PAGE_H, pageNum).create();
                PdfDocument.Page page = doc.startPage(pageInfo);
                pages.add(page);
                canvas = page.getCanvas();
                pageNum++;

                // Header
                currentY = PDF_MARGIN_TOP + 18;
                canvas.drawText("Task Report", PDF_MARGIN_LEFT, currentY, titlePaint);
                currentY += 16;

                String subText = "Generated on "
                        + new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date())
                        + " · " + tasks.size() + " tasks";
                canvas.drawText(subText, PDF_MARGIN_LEFT, currentY, subtitlePaint);
                currentY += 10;
                canvas.drawLine(PDF_MARGIN_LEFT, currentY, PDF_PAGE_W - PDF_MARGIN_RIGHT,
                        currentY, rulePaint);
                currentY += 20;

                float maxY = PDF_PAGE_H - PDF_MARGIN_BOTTOM - 20; // leave room for footer

                for (int i = 0; i < tasks.size(); i++) {
                    Task t = tasks.get(i);

                    // Estimate block height
                    float blockHeight = 20; // title
                    blockHeight += 14; // meta row
                    String desc = t.description != null ? t.description : "";
                    if (!desc.isEmpty()) blockHeight += 36; // up to 3 lines
                    if (t.subtasks != null && !t.subtasks.isEmpty()) {
                        blockHeight += t.subtasks.size() * 11;
                    }
                    blockHeight += 8; // spacing

                    // Check pagination
                    if (currentY + blockHeight > maxY) {
                        // Draw footer on current page
                        // (page number set at end)

                        doc.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(
                                PDF_PAGE_W, PDF_PAGE_H, pageNum).create();
                        page = doc.startPage(pageInfo);
                        pages.add(page);
                        canvas = page.getCanvas();
                        pageNum++;
                        currentY = PDF_MARGIN_TOP;
                    }

                    // Priority stripe
                    String stripeColor = getPdfPriorityColor(t.priority);
                    stripePaint.setColor(android.graphics.Color.parseColor(stripeColor));
                    canvas.drawRect(PDF_MARGIN_LEFT, currentY,
                            PDF_MARGIN_LEFT + 3, currentY + blockHeight - 8, stripePaint);

                    float textX = PDF_MARGIN_LEFT + 12;

                    // Title
                    canvas.drawText(truncate(t.title, 70), textX, currentY + 14, taskTitlePaint);
                    currentY += 20;

                    // Metadata row
                    StringBuilder meta = new StringBuilder();
                    if (t.dueDate != null) meta.append("Due: ").append(t.dueDate);
                    if (t.priority != null && !t.priority.isEmpty()) {
                        if (meta.length() > 0) meta.append(" | ");
                        meta.append("Priority: ").append(t.priority.toUpperCase(Locale.US));
                    }
                    if (t.category != null && !t.category.isEmpty()) {
                        if (meta.length() > 0) meta.append(" | ");
                        meta.append(t.category);
                    }
                    if (meta.length() > 0) {
                        canvas.drawText(meta.toString(), textX, currentY + 10, metaPaint);
                    }
                    currentY += 14;

                    // Description (max 3 lines)
                    if (!desc.isEmpty()) {
                        String[] descLines = desc.split("\n");
                        int lineCount = 0;
                        for (String line : descLines) {
                            if (lineCount >= 3) {
                                canvas.drawText("…", textX, currentY + 10, bodyPaint);
                                currentY += 12;
                                break;
                            }
                            canvas.drawText(truncate(line, 80), textX, currentY + 10, bodyPaint);
                            currentY += 12;
                            lineCount++;
                        }
                    }

                    // Subtasks
                    if (t.subtasks != null && !t.subtasks.isEmpty()) {
                        for (SubTask st : t.subtasks) {
                            String bullet = st.isCompleted ? "✓ " : "○ ";
                            canvas.drawText(bullet + truncate(st.title, 60),
                                    textX + 4, currentY + 8, subtaskPaint);
                            currentY += 11;
                        }
                    }

                    // Separator
                    currentY += 4;
                    canvas.drawLine(PDF_MARGIN_LEFT, currentY,
                            PDF_PAGE_W - PDF_MARGIN_RIGHT, currentY, linePaint);
                    currentY += 4;
                }

                // Finish last page
                doc.finishPage(page);

                // Draw page number footers (re-render would be complex; skip for simplicity)

                // Write to Downloads
                String filename = "tasks_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + ".pdf";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                            if (os != null) doc.writeTo(os);
                        }
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    File file = new File(dir, filename);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        doc.writeTo(fos);
                    }
                }

                doc.close();

                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "Exported to Downloads/" + filename, Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                main.post(() -> Toast.makeText(context,
                        "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static String getPdfPriorityColor(String priority) {
        if (priority == null) return "#374151";
        switch (priority) {
            case "urgent":  return "#EF4444";
            case "high":    return "#F97316";
            case "normal":  return "#3B82F6";
            case "low":     return "#6B7280";
            default:        return "#374151";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
