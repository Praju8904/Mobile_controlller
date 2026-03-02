package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Anomaly Detector for the Smart File Hub.
 *
 * Detects unusual patterns in the file system and surfaces them as actionable alerts
 * on the Storage Intelligence screen and optionally as notifications.
 *
 * Anomaly types:
 *   SUDDEN_ACCUMULATION  — more than {@link #THRESHOLD_FILES_PER_DAY} files from
 *                          the same source in the last 24 h
 *   LARGE_FILE           — a file larger than {@link #THRESHOLD_LARGE_FILE_BYTES}
 *                          that appeared recently (within 48 h)
 *   ZERO_BYTE            — files with size == 0 bytes
 *   NO_EXTENSION         — files with no file extension
 *   EXTERNAL_MODIFICATION — a tracked file whose on-disk last-modified timestamp is
 *                           newer than the repository's {@code originalModifiedAt}
 */
public class HubAnomalyDetector {

    public enum AnomalyType {
        SUDDEN_ACCUMULATION,
        LARGE_FILE,
        ZERO_BYTE,
        NO_EXTENSION,
        EXTERNAL_MODIFICATION
    }

    public static class Anomaly {
        public final AnomalyType type;
        public final String title;
        public final String detail;
        public final String fileId;  // may be null for aggregate anomalies

        public Anomaly(AnomalyType type, String title, String detail, String fileId) {
            this.type = type;
            this.title = title;
            this.detail = detail;
            this.fileId = fileId;
        }

        public String getEmoji() {
            switch (type) {
                case SUDDEN_ACCUMULATION: return "📈";
                case LARGE_FILE: return "🗂️";
                case ZERO_BYTE: return "⚠️";
                case NO_EXTENSION: return "❓";
                case EXTERNAL_MODIFICATION: return "✏️";
                default: return "⚠️";
            }
        }
    }

    private static final long THRESHOLD_LARGE_FILE_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final int THRESHOLD_FILES_PER_DAY = 100;
    private static final long HOURS_48 = 48L * 3600 * 1000;
    private static final long HOURS_24 = 24L * 3600 * 1000;
    /**
     * Tolerance in milliseconds when comparing on-disk lastModified to the indexed
     * originalModifiedAt timestamp. 5 seconds avoids false positives from file system
     * clock rounding or minor metadata updates by the OS.
     */
    private static final long MODIFICATION_TOLERANCE_MS = 5_000L;

    private static final String CHANNEL_ID = "hub_anomaly";
    private static final int NOTIF_ID = 7800;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Scans all tracked files and returns a list of detected anomalies.
     * Also flags externally modified files back on the HubFile objects
     * (call {@link HubFileRepository#updateFile} on changed files).
     */
    public static List<Anomaly> detect(List<HubFile> files) {
        List<Anomaly> anomalies = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Per-source accumulation counters for last 24 h
        java.util.Map<HubFile.Source, Integer> sourceCount = new java.util.HashMap<>();
        java.util.Map<HubFile.Source, Long> sourceBytes = new java.util.HashMap<>();

        List<HubFile> externallyModified = new ArrayList<>();
        List<HubFile> zeroBytes = new ArrayList<>();
        List<HubFile> noExtension = new ArrayList<>();
        List<HubFile> largeRecent = new ArrayList<>();

        for (HubFile f : files) {
            // Count recent by source
            if (f.importedAt > now - HOURS_24) {
                HubFile.Source src = f.source != null ? f.source : HubFile.Source.OTHER;
                sourceCount.put(src, sourceCount.getOrDefault(src, 0) + 1);
                sourceBytes.put(src, sourceBytes.getOrDefault(src, 0L) + f.fileSize);
            }

            // Zero-byte files
            if (f.fileSize == 0) {
                zeroBytes.add(f);
            }

            // No extension
            if (f.fileExtension == null || f.fileExtension.isEmpty()) {
                noExtension.add(f);
            }

            // Large file added recently
            if (f.fileSize > THRESHOLD_LARGE_FILE_BYTES && f.importedAt > now - HOURS_48) {
                largeRecent.add(f);
            }

            // External modification: on-disk lastModified newer than indexed
            if (f.filePath != null && !f.filePath.isEmpty()) {
                File diskFile = new File(f.filePath);
                if (diskFile.exists() && diskFile.lastModified() > f.originalModifiedAt + MODIFICATION_TOLERANCE_MS
                        && f.originalModifiedAt > 0) {
                    f.modifiedExternally = true;
                    externallyModified.add(f);
                }
            }
        }

        // Sudden accumulation
        for (java.util.Map.Entry<HubFile.Source, Integer> e : sourceCount.entrySet()) {
            if (e.getValue() >= THRESHOLD_FILES_PER_DAY) {
                long bytes = sourceBytes.getOrDefault(e.getKey(), 0L);
                anomalies.add(new Anomaly(AnomalyType.SUDDEN_ACCUMULATION,
                        "Sudden file accumulation from " + e.getKey().name(),
                        "You received " + e.getValue() + " files ("
                                + formatSize(bytes) + ") from "
                                + e.getKey().name() + " today — review?",
                        null));
            }
        }

        // Large recent files (show individually, up to 3)
        int shown = 0;
        for (HubFile f : largeRecent) {
            if (shown++ >= 3) break;
            String name = f.displayName != null ? f.displayName : f.originalFileName;
            anomalies.add(new Anomaly(AnomalyType.LARGE_FILE,
                    "Large file added recently",
                    "A " + formatSize(f.fileSize) + " file \"" + name
                            + "\" was added — keep or delete?",
                    f.id));
        }

        // Zero-byte
        if (!zeroBytes.isEmpty()) {
            anomalies.add(new Anomaly(AnomalyType.ZERO_BYTE,
                    zeroBytes.size() + " empty (zero-byte) files detected",
                    "These files contain no data and can likely be deleted.",
                    null));
        }

        // No extension
        if (!noExtension.isEmpty()) {
            anomalies.add(new Anomaly(AnomalyType.NO_EXTENSION,
                    noExtension.size() + " file(s) have no extension",
                    "Files with no extension may be corrupted or incomplete.",
                    null));
        }

        // Externally modified
        for (HubFile f : externallyModified) {
            String name = f.displayName != null ? f.displayName : f.originalFileName;
            anomalies.add(new Anomaly(AnomalyType.EXTERNAL_MODIFICATION,
                    "File modified externally",
                    "\"" + name + "\" was changed by another app since last indexed.",
                    f.id));
        }

        return anomalies;
    }

    /** Shows a notification summarising detected anomalies. */
    public static void notifyAnomalies(Context context, List<Anomaly> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) return;
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "File Anomalies", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }

        String title = anomalies.size() == 1
                ? anomalies.get(0).title
                : anomalies.size() + " file anomalies detected";
        String text = anomalies.get(0).detail;

        Intent intent = new Intent(context, HubStorageIntelligenceActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true);

        nm.notify(NOTIF_ID, builder.build());
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
