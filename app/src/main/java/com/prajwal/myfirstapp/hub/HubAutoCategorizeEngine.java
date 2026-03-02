package com.prajwal.myfirstapp.hub;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility for auto-categorizing files based on name, extension, source, and size.
 */
public class HubAutoCategorizeEngine {

    /** Regex matching a 4-digit year (19xx or 20xx) as a whole word. */
    private static final String DATE_YEAR_REGEX = ".*\\b(19|20)\\d{2}\\b.*";

    /** Document-related keywords used alongside date detection. */
    private static final String[] DATE_DOC_KEYWORDS = {"doc", "report", "letter", "memo"};

    public static class CategorizeResult {
        public HubFile.FileType suggestedFileType;
        public String suggestedFolder;
        public List<String> suggestedTags = new ArrayList<>();
        public int confidence; // 0-100
        public String confidenceLabel; // "High", "Medium", "Low"
    }

    public static CategorizeResult analyze(InboxItem item) {
        CategorizeResult result = new CategorizeResult();
        String name = item.fileName != null ? item.fileName.toLowerCase() : "";
        HubFile.FileType type = item.fileType != null ? item.fileType : HubFile.FileType.OTHER;
        HubFile.Source source = item.source != null ? item.source : HubFile.Source.OTHER;
        long size = item.fileSize;

        // Extension-based type override
        HubFile.FileType extType = typeFromExtension(name);
        if (extType != HubFile.FileType.OTHER) {
            type = extType;
        }
        result.suggestedFileType = type;

        // Name-based keyword categorization
        if (containsAny(name, "assignment", "homework", "lecture", "notes", "syllabus", "exam")) {
            result.suggestedFolder = "Study";
            result.suggestedTags.add("#study");
        } else if (containsAny(name, "invoice", "receipt", "statement", "salary", "tax")) {
            result.suggestedFolder = "Finance";
            result.suggestedTags.add("#finance");
        } else if (containsAny(name, "resume", "cv", "cover letter", "portfolio")) {
            result.suggestedFolder = "Work";
            result.suggestedTags.add("#work");
        } else if (containsAny(name, "screenshot", "screen")) {
            result.suggestedFolder = "Screenshots";
        }

        // Date-like pattern in name (e.g. 2024, 20240101)
        if (name.matches(DATE_YEAR_REGEX) && containsAny(name, DATE_DOC_KEYWORDS)) {
            result.suggestedTags.add("#dated");
        }

        // Type-based folder suggestions
        if (result.suggestedFolder == null) {
            switch (type) {
                case CODE:         result.suggestedFolder = "Code";          break;
                case PDF:          result.suggestedFolder = "PDFs";          break;
                case IMAGE:        result.suggestedFolder = "Images";        break;
                case SCREENSHOT:   result.suggestedFolder = "Screenshots";   break;
                case VIDEO:        result.suggestedFolder = "Videos";        break;
                case AUDIO:        result.suggestedFolder = "Audio";         break;
                case ARCHIVE:      result.suggestedFolder = "Archives";      break;
                case SPREADSHEET:  result.suggestedFolder = "Spreadsheets";  break;
                case PRESENTATION: result.suggestedFolder = "Presentations"; break;
                default:           result.suggestedFolder = "Documents";     break;
            }
        }

        // Source-based tagging
        if (source == HubFile.Source.WHATSAPP) {
            result.suggestedTags.add("#whatsapp");
        } else if (source == HubFile.Source.DOWNLOADS) {
            result.suggestedTags.add("#downloads");
        } else if (source == HubFile.Source.SCREENSHOTS) {
            if (result.suggestedFolder == null) result.suggestedFolder = "Screenshots";
        }

        // Size-based flag
        if (size > 500L * 1024 * 1024) {
            result.suggestedTags.add("#large-file");
            result.suggestedFolder = "Large Files";
        }

        // Confidence
        result.confidence = computeConfidence(item, result);
        result.confidenceLabel = result.confidence >= 80 ? "High"
                : result.confidence >= 50 ? "Medium" : "Low";

        return result;
    }

    public static List<String> suggestTags(String fileName, HubFile.FileType fileType, HubFile.Source source) {
        List<String> tags = new ArrayList<>();
        String name = fileName != null ? fileName.toLowerCase() : "";

        if (containsAny(name, "assignment", "homework", "lecture", "notes", "syllabus", "exam")) tags.add("#study");
        if (containsAny(name, "invoice", "receipt", "statement", "salary", "tax")) tags.add("#finance");
        if (containsAny(name, "resume", "cv", "portfolio")) tags.add("#work");
        if (containsAny(name, "screenshot", "screen")) tags.add("#screenshot");

        if (source == HubFile.Source.WHATSAPP) tags.add("#whatsapp");
        if (source == HubFile.Source.DOWNLOADS) tags.add("#downloads");

        if (fileType == HubFile.FileType.CODE) tags.add("#code");
        if (fileType == HubFile.FileType.PDF) tags.add("#pdf");

        return tags;
    }

    private static HubFile.FileType typeFromExtension(String nameLower) {
        if (nameLower.endsWith(".py") || nameLower.endsWith(".js") || nameLower.endsWith(".java")
                || nameLower.endsWith(".kt") || nameLower.endsWith(".dart") || nameLower.endsWith(".html")
                || nameLower.endsWith(".css") || nameLower.endsWith(".json") || nameLower.endsWith(".ts")
                || nameLower.endsWith(".cpp") || nameLower.endsWith(".c") || nameLower.endsWith(".rs")
                || nameLower.endsWith(".go") || nameLower.endsWith(".swift") || nameLower.endsWith(".rb"))
            return HubFile.FileType.CODE;
        if (nameLower.endsWith(".pdf")) return HubFile.FileType.PDF;
        if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png")
                || nameLower.endsWith(".gif") || nameLower.endsWith(".webp") || nameLower.endsWith(".bmp"))
            return HubFile.FileType.IMAGE;
        if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi")
                || nameLower.endsWith(".mov") || nameLower.endsWith(".webm"))
            return HubFile.FileType.VIDEO;
        if (nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".flac")
                || nameLower.endsWith(".aac") || nameLower.endsWith(".ogg"))
            return HubFile.FileType.AUDIO;
        if (nameLower.endsWith(".zip") || nameLower.endsWith(".rar") || nameLower.endsWith(".tar")
                || nameLower.endsWith(".gz") || nameLower.endsWith(".7z"))
            return HubFile.FileType.ARCHIVE;
        if (nameLower.endsWith(".xlsx") || nameLower.endsWith(".xls") || nameLower.endsWith(".csv"))
            return HubFile.FileType.SPREADSHEET;
        if (nameLower.endsWith(".pptx") || nameLower.endsWith(".ppt"))
            return HubFile.FileType.PRESENTATION;
        return HubFile.FileType.OTHER;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    private static int computeConfidence(InboxItem item, CategorizeResult result) {
        int base = item.autoCategorizationConfidence > 0 ? item.autoCategorizationConfidence : 50;
        if (!result.suggestedTags.isEmpty()) base = Math.min(100, base + 10);
        if (result.suggestedFileType != HubFile.FileType.OTHER) base = Math.min(100, base + 10);
        return base;
    }
}
