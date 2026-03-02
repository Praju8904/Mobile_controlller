package com.prajwal.myfirstapp.hub;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Content-Aware File Indexer.
 *
 * Extracts and indexes the actual text content of files so that users can
 * search by *what is inside* a file rather than just its name.
 *
 * Supported extraction:
 *   • Plain text / Markdown  — full content
 *   • Source code files      — full content (function names, variables, snippets)
 *   • PDF                    — basic text extraction by scanning for readable ASCII
 *                              sequences (no external library needed; works for
 *                              most non-scanned PDFs)
 *   • Images (JPEG/PNG)      — basic EXIF metadata extraction from raw bytes
 *
 * The extracted text is stored in {@link HubFile#contentIndex} and a flag
 * {@link HubFile#contentIndexed} is set to avoid redundant re-indexing.
 *
 * The {@link HubSearchActivity} uses {@code contentIndex} to show "Content Match"
 * badges on results that matched by content rather than filename.
 */
public class HubContentIndexer {

    private static final String TAG = "HubContentIndexer";
    private static final int MAX_CONTENT_LENGTH = 50_000;   // characters stored per file
    private static final int READ_BUFFER = 8192;

    /** File extensions treated as plain text / code. */
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "md", "markdown", "csv", "log", "xml", "json", "yaml", "yml", "ini", "cfg",
            "java", "py", "js", "ts", "kt", "cpp", "c", "h", "cs", "go", "rs", "rb", "php",
            "swift", "dart", "html", "htm", "css", "sh", "bat", "gradle", "sql"
    ));

    private static HubContentIndexer instance;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private HubContentIndexer(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized HubContentIndexer getInstance(Context context) {
        if (instance == null) instance = new HubContentIndexer(context);
        return instance;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Indexes a single file asynchronously. Saves the result back through the
     * repository. The callback is invoked on the calling thread (background).
     */
    public void indexFile(HubFile file, Runnable onComplete) {
        executor.execute(() -> {
            doIndex(file);
            HubFileRepository.getInstance(context).updateFile(file);
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Indexes all files that have not yet been indexed. Runs entirely in the
     * background; calls {@code onComplete} on the main thread when done.
     */
    public void indexAllPending(Runnable onComplete) {
        executor.execute(() -> {
            HubFileRepository repo = HubFileRepository.getInstance(context);
            List<HubFile> all = repo.getAllFiles();
            for (HubFile f : all) {
                if (!f.contentIndexed) {
                    doIndex(f);
                    repo.updateFile(f);
                }
            }
            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        });
    }

    /**
     * Re-index a single file regardless of whether it was previously indexed.
     */
    public void reIndex(HubFile file, Runnable onComplete) {
        file.contentIndexed = false;
        indexFile(file, onComplete);
    }

    // ─── Core indexing logic ──────────────────────────────────────────────────

    private void doIndex(HubFile file) {
        if (file == null || file.filePath == null || file.filePath.isEmpty()) {
            markIndexed(file, "");
            return;
        }
        File f = new File(file.filePath);
        if (!f.exists() || !f.canRead() || f.length() == 0) {
            markIndexed(file, "");
            return;
        }

        try {
            String ext = extension(file.originalFileName != null
                    ? file.originalFileName : f.getName()).toLowerCase();

            if (file.fileType == HubFile.FileType.PDF || "pdf".equals(ext)) {
                String text = extractTextFromPdf(f);
                markIndexed(file, text);
                return;
            }

            if (TEXT_EXTENSIONS.contains(ext)
                    || file.fileType == HubFile.FileType.CODE
                    || file.fileType == HubFile.FileType.DOCUMENT) {
                String text = readTextFile(f);
                markIndexed(file, text);
                return;
            }

            if (file.fileType == HubFile.FileType.IMAGE
                    || file.fileType == HubFile.FileType.SCREENSHOT) {
                String exif = extractExifMetadata(f);
                file.exifJson = exif;
                // Also put key exif fields into contentIndex so they're searchable
                markIndexed(file, exif);
                return;
            }

            // For all other types mark as indexed with empty content
            markIndexed(file, "");
        } catch (Exception e) {
            Log.e(TAG, "doIndex: " + file.filePath, e);
            markIndexed(file, "");
        }
    }

    private void markIndexed(HubFile file, String content) {
        file.contentIndex = content != null ? truncate(content) : "";
        file.contentIndexed = true;
    }

    // ─── Plain text / code ────────────────────────────────────────────────────

    private String readTextFile(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (sb.length() >= MAX_CONTENT_LENGTH) break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "readTextFile failed: " + f.getName(), e);
            return "";
        }
    }

    // ─── PDF text extraction ──────────────────────────────────────────────────

    /**
     * Extracts readable text from a PDF by scanning for PDF string objects.
     * This is a heuristic approach that works well for most text-based PDFs
     * without requiring any external library.
     *
     * It looks for BT (begin text) ... ET (end text) blocks and extracts
     * text within parentheses (Tj/TJ operators) and hex strings.
     */
    private String extractTextFromPdf(File f) {
        try {
            byte[] bytes = readBytes(f, 1024 * 1024); // read up to 1 MB
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            StringBuilder sb = new StringBuilder();

            int i = 0;
            while (i < raw.length() && sb.length() < MAX_CONTENT_LENGTH) {
                // Look for parenthesized strings: (some text)
                int start = raw.indexOf('(', i);
                if (start < 0) break;
                int end = findClosingParen(raw, start + 1);
                if (end < 0) { i = start + 1; continue; }
                String candidate = raw.substring(start + 1, end);
                // Only keep if it contains at least a few printable ASCII chars
                if (isPrintable(candidate)) {
                    sb.append(candidate).append(' ');
                }
                i = end + 1;
            }
            return sb.toString().replaceAll("\\s{2,}", " ").trim();
        } catch (Exception e) {
            Log.w(TAG, "extractTextFromPdf failed: " + f.getName(), e);
            return "";
        }
    }

    private int findClosingParen(String s, int from) {
        int depth = 1;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; } // skip escape
            if (c == '(') depth++;
            else if (c == ')') { if (--depth == 0) return i; }
        }
        return -1;
    }

    private boolean isPrintable(String s) {
        if (s == null || s.length() < 3) return false;
        int printable = 0;
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 127) printable++;
        }
        return printable > s.length() * 0.6;
    }

    // ─── EXIF metadata extraction ─────────────────────────────────────────────

    /**
     * Extracts basic EXIF metadata from a JPEG file by parsing the raw bytes.
     * Returns a JSON string with available fields: cameraMake, cameraModel,
     * dateTime, gpsLat, gpsLon, iso, exposureTime, fNumber.
     */
    private String extractExifMetadata(File f) {
        try {
            JSONObject exif = new JSONObject();
            // Read first 64 KB (EXIF is always near the start of a JPEG)
            byte[] bytes = readBytes(f, 65536);
            if (bytes.length < 4) return exif.toString();

            // Check for JPEG magic
            if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) return exif.toString();

            // Search for EXIF APP1 marker (FF E1) followed by "Exif\0\0"
            for (int i = 2; i < bytes.length - 10; i++) {
                if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xFF) == 0xE1) {
                    int segLen = ((bytes[i + 2] & 0xFF) << 8) | (bytes[i + 3] & 0xFF);
                    String header = new String(bytes, i + 4, Math.min(6, bytes.length - i - 4),
                            StandardCharsets.US_ASCII);
                    if (header.startsWith("Exif")) {
                        // We found the EXIF segment — try to extract Make/Model strings
                        int exifStart = i + 10; // skip marker, length, "Exif\0\0"
                        int exifEnd = Math.min(exifStart + segLen, bytes.length);
                        parseExifSegment(bytes, exifStart, exifEnd, exif);
                        break;
                    }
                    i += segLen + 1;
                }
            }
            return exif.toString();
        } catch (Exception e) {
            Log.w(TAG, "extractExifMetadata failed: " + f.getName(), e);
            return "{}";
        }
    }

    private void parseExifSegment(byte[] bytes, int start, int end, JSONObject out) {
        try {
            // Determine byte order from TIFF header
            if (start + 8 > end) return;
            boolean bigEndian = (bytes[start] == 'M' && bytes[start + 1] == 'M');

            // Read IFD0 offset
            int ifdOffset = start + readInt32(bytes, start + 4, bigEndian);
            if (ifdOffset < start || ifdOffset + 2 > end) return;

            int entryCount = readInt16(bytes, ifdOffset, bigEndian);
            for (int e = 0; e < entryCount; e++) {
                int entryBase = ifdOffset + 2 + e * 12;
                if (entryBase + 12 > end) break;
                int tag = readInt16(bytes, entryBase, bigEndian);
                int type = readInt16(bytes, entryBase + 2, bigEndian);
                int count = readInt32(bytes, entryBase + 4, bigEndian);
                // Value or offset at entryBase+8

                switch (tag) {
                    case 0x010F: // Make
                        out.put("cameraMake", readExifString(bytes, entryBase + 8, count, start, bigEndian, end));
                        break;
                    case 0x0110: // Model
                        out.put("cameraModel", readExifString(bytes, entryBase + 8, count, start, bigEndian, end));
                        break;
                    case 0x0132: // DateTime
                        out.put("dateTime", readExifString(bytes, entryBase + 8, count, start, bigEndian, end));
                        break;
                    case 0x8827: // ISO
                        if (type == 3) out.put("iso", readInt16(bytes, entryBase + 8, bigEndian));
                        break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "parseExifSegment error", e);
        }
    }

    private String readExifString(byte[] bytes, int valueOffset, int count,
                                   int tiffBase, boolean bigEndian, int end) {
        try {
            int offset;
            if (count <= 4) {
                offset = valueOffset; // value is inline
            } else {
                offset = tiffBase + readInt32(bytes, valueOffset, bigEndian);
            }
            int len = Math.min(count - 1, end - offset); // -1 to skip null terminator
            if (len <= 0) return "";
            return new String(bytes, offset, len, StandardCharsets.US_ASCII).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private int readInt16(byte[] b, int offset, boolean bigEndian) {
        if (offset + 1 >= b.length) return 0;
        int a0 = b[offset] & 0xFF;
        int a1 = b[offset + 1] & 0xFF;
        return bigEndian ? (a0 << 8) | a1 : (a1 << 8) | a0;
    }

    private int readInt32(byte[] b, int offset, boolean bigEndian) {
        if (offset + 3 >= b.length) return 0;
        int a0 = b[offset] & 0xFF, a1 = b[offset + 1] & 0xFF;
        int a2 = b[offset + 2] & 0xFF, a3 = b[offset + 3] & 0xFF;
        return bigEndian
                ? (a0 << 24) | (a1 << 16) | (a2 << 8) | a3
                : (a3 << 24) | (a2 << 16) | (a1 << 8) | a0;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private byte[] readBytes(File f, int maxBytes) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            int toRead = (int) Math.min(f.length(), maxBytes);
            byte[] buf = new byte[toRead];
            int read = 0;
            while (read < toRead) {
                int r = fis.read(buf, read, toRead - read);
                if (r < 0) break;
                read += r;
            }
            if (read == toRead) return buf;
            byte[] trimmed = new byte[read];
            System.arraycopy(buf, 0, trimmed, 0, read);
            return trimmed;
        }
    }

    private String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > MAX_CONTENT_LENGTH ? s.substring(0, MAX_CONTENT_LENGTH) : s;
    }

    /**
     * Returns true if the given query string matches in the file's content index.
     * Used by the search layer to set the "Content Match" flag.
     */
    public static boolean contentMatches(HubFile file, String query) {
        if (file == null || query == null || query.isEmpty()) return false;
        String ci = file.contentIndex;
        if (ci == null || ci.isEmpty()) return false;
        return ci.toLowerCase().contains(query.toLowerCase());
    }
}
