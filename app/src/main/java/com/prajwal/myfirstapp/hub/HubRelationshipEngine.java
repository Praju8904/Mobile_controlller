package com.prajwal.myfirstapp.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects connections between files automatically and groups related files.
 *
 * Relationship types detected:
 *   1. SAME_PROJECT_KEYWORD  — files sharing the same leading project token in their name
 *                               (e.g. "ProjectX_Design.pdf" ↔ "ProjectX_Code.zip")
 *   2. CREATED_TOGETHER      — files created within 2 minutes of each other (likely together)
 *   3. VERSION_SIBLINGS      — files whose names differ only by a version token
 *                               ("Report_v1.pdf" ↔ "Report_v2.pdf")
 *   4. WHATSAPP_THREAD       — files from WhatsApp within 5 minutes of each other
 *   5. DOC_CODE_PAIR         — code file and a PDF/document whose base names match
 */
public class HubRelationshipEngine {

    public enum RelationshipType {
        SAME_PROJECT_KEYWORD,
        CREATED_TOGETHER,
        VERSION_SIBLINGS,
        WHATSAPP_THREAD,
        DOC_CODE_PAIR
    }

    public static class Relationship {
        public final String fileId;
        public final RelationshipType type;
        public final String label;

        public Relationship(String fileId, RelationshipType type, String label) {
            this.fileId = fileId;
            this.type = type;
            this.label = label;
        }
    }

    private static final long TWO_MINUTES_MS = 2 * 60 * 1000L;
    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

    /** Version token patterns at the end of a base name (before extension). */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)(_v\\d+|_final\\d*|_revised\\d*|_updated\\d*|_new\\d*|\\s*\\(\\d+\\))$");

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns all files related to {@code target} from the provided list.
     * The target file itself is excluded from results.
     */
    public static List<Relationship> findRelated(HubFile target, List<HubFile> all) {
        List<Relationship> result = new ArrayList<>();
        if (target == null || all == null) return result;

        String targetBase = stripVersion(baseName(target));

        for (HubFile candidate : all) {
            if (candidate.id.equals(target.id)) continue;

            // 1. VERSION_SIBLINGS — same base name, different version token
            if (areVersionSiblings(target, candidate)) {
                result.add(new Relationship(candidate.id, RelationshipType.VERSION_SIBLINGS,
                        "Version: " + getVersionLabel(candidate)));
                continue;
            }

            // 2. SAME_PROJECT_KEYWORD
            String projectKeyword = extractProjectKeyword(target);
            if (projectKeyword != null && !projectKeyword.isEmpty()) {
                String candidateKeyword = extractProjectKeyword(candidate);
                if (projectKeyword.equals(candidateKeyword)) {
                    result.add(new Relationship(candidate.id,
                            RelationshipType.SAME_PROJECT_KEYWORD, "Same project: " + projectKeyword));
                    continue;
                }
            }

            // 3. CREATED_TOGETHER (within 2 minutes)
            if (Math.abs(target.originalCreatedAt - candidate.originalCreatedAt) < TWO_MINUTES_MS
                    && target.originalCreatedAt > 0 && candidate.originalCreatedAt > 0) {
                result.add(new Relationship(candidate.id, RelationshipType.CREATED_TOGETHER,
                        "Created together"));
                continue;
            }

            // 4. WHATSAPP_THREAD (within 5 minutes, same source)
            if (target.source == HubFile.Source.WHATSAPP
                    && candidate.source == HubFile.Source.WHATSAPP
                    && Math.abs(target.importedAt - candidate.importedAt) < FIVE_MINUTES_MS) {
                result.add(new Relationship(candidate.id, RelationshipType.WHATSAPP_THREAD,
                        "WhatsApp thread"));
                continue;
            }

            // 5. DOC_CODE_PAIR — code file ↔ PDF/doc with matching base name
            if (isDocCodePair(target, candidate, targetBase)) {
                result.add(new Relationship(candidate.id, RelationshipType.DOC_CODE_PAIR,
                        "Docs & code pair"));
            }
        }

        // Deduplicate by fileId
        List<Relationship> deduped = new ArrayList<>();
        for (Relationship r : result) {
            boolean found = false;
            for (Relationship d : deduped) { if (d.fileId.equals(r.fileId)) { found = true; break; } }
            if (!found) deduped.add(r);
        }
        return deduped;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static boolean areVersionSiblings(HubFile a, HubFile b) {
        String baseA = stripVersion(baseName(a)).toLowerCase(Locale.ROOT);
        String baseB = stripVersion(baseName(b)).toLowerCase(Locale.ROOT);
        if (baseA.isEmpty() || baseB.isEmpty()) return false;
        // Same stripped base, different full names
        return baseA.equals(baseB) && !baseName(a).equalsIgnoreCase(baseName(b));
    }

    private static boolean isDocCodePair(HubFile target, HubFile candidate, String targetBase) {
        boolean targetIsCode = target.fileType == HubFile.FileType.CODE;
        boolean candidateIsDoc = candidate.fileType == HubFile.FileType.PDF
                || candidate.fileType == HubFile.FileType.DOCUMENT;
        boolean targetIsDoc = target.fileType == HubFile.FileType.PDF
                || target.fileType == HubFile.FileType.DOCUMENT;
        boolean candidateIsCode = candidate.fileType == HubFile.FileType.CODE;

        if (!(targetIsCode && candidateIsDoc) && !(targetIsDoc && candidateIsCode)) return false;

        String candBase = stripVersion(baseName(candidate)).toLowerCase(Locale.ROOT);
        return !targetBase.isEmpty() && !candBase.isEmpty()
                && (targetBase.contains(candBase) || candBase.contains(targetBase));
    }

    /** Extracts the leading "ProjectX" keyword from a file name like "ProjectX_Design.pdf". */
    private static String extractProjectKeyword(HubFile f) {
        String name = baseName(f);
        if (name == null || name.isEmpty()) return null;
        int us = name.indexOf('_');
        int hy = name.indexOf('-');
        int sep = -1;
        if (us > 0 && hy > 0) sep = Math.min(us, hy);
        else if (us > 0) sep = us;
        else if (hy > 0) sep = hy;
        if (sep > 2) return name.substring(0, sep).toLowerCase(Locale.ROOT);
        return null;
    }

    /** Base file name without extension. */
    private static String baseName(HubFile f) {
        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Strips version tokens from a base name. */
    static String stripVersion(String baseName) {
        if (baseName == null) return "";
        return VERSION_PATTERN.matcher(baseName).replaceAll("").trim();
    }

    /** Extracts the version label from a file name if present. */
    public static String getVersionLabel(HubFile f) {
        if (f.versionLabel != null && !f.versionLabel.isEmpty()) return f.versionLabel;
        String base = baseName(f);
        Matcher m = VERSION_PATTERN.matcher(base);
        if (m.find()) return m.group(1).replaceAll("[_()]", "").trim();
        return "";
    }
}
