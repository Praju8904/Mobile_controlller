package com.prajwal.myfirstapp.tasks;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for exporting tasks to CSV.
 */
public class TaskExportManager {

    /** Simple callback interfaces (avoid requiring API 24+ java.util.function.Consumer). */
    public interface OnSuccess { void run(); }
    public interface OnError   { void accept(Exception e); }

    private static final String CSV_HEADER =
            "id,title,description,priority,status,category,tags,dueDate,dueTime,notes,createdAt,completedAt";

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

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
     *
     * @param context   Android context
     * @param tasks     tasks to export
     * @param format    currently only "csv" is supported
     * @param onSuccess called on the UI thread when export succeeds
     * @param onError   called with the exception on failure
     */
    public static void exportToFile(Context context, List<Task> tasks, String format,
                                    OnSuccess onSuccess, OnError onError) {
        new Thread(() -> {
            try {
                String csvContent = exportToCsv(tasks);
                String fileName = "tasks_export_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + ".csv";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create file in Downloads");
                    try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new Exception("Could not open output stream");
                        os.write(csvContent.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    File file = new File(dir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(csvContent.getBytes(StandardCharsets.UTF_8));
                    }
                }

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

    // ── Helpers ───────────────────────────────────────────────────

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
}
