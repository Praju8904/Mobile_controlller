package com.prajwal.myfirstapp.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects files that are different versions of the same document and groups
 * them into {@link HubVersionChain}s.
 *
 * Naming patterns detected:
 *   _v1, _v2, _v3 …
 *   _final, _final2, _final_v2 …
 *   _revised, _revised2 …
 *   _updated, _updated2 …
 *   _new, _new2 …
 *   (1), (2), (3) … (appended by Android/Windows copy logic)
 *
 * The newest file in a chain (highest version number / most recent date) is
 * the "head" and receives a {@code versionChainId} pointing back to the chain.
 * The UI shows a "N versions" badge on the head file.
 */
public class HubVersionManager {

    /** Pattern that matches a version suffix at the end of a filename base. */
    private static final Pattern VERSION_SUFFIX = Pattern.compile(
            "(?i)(_v(\\d+)|_final(\\d*)|_revised(\\d*)|_updated(\\d*)|_new(\\d*)|\\s*\\((\\d+)\\))$");

    private static final int VERSION_SCORE_BASE = 100;
    /** Score offset applied to _updated suffixes (higher = newer in ordering). */
    private static final int UPDATED_OFFSET = 10;
    /** Score offset applied to _revised suffixes. */
    private static final int REVISED_OFFSET = 20;
    /** Score offset applied to _final suffixes (treated as near-latest). */
    private static final int FINAL_OFFSET = 50;
    /** Score offset applied to Windows-style copy parentheses e.g. (2). */
    private static final int COPY_PAREN_OFFSET = 60;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Scans all files and builds version chains. Updates each file's
     * {@code versionChainId} and {@code versionLabel} in place.
     *
     * @return newly built chains (may be empty if no versions found)
     */
    public static List<HubVersionChain> detectVersionChains(List<HubFile> allFiles) {
        // Group files by normalised base name (extension-stripped, version-stripped)
        Map<String, List<HubFile>> groups = new HashMap<>();

        for (HubFile f : allFiles) {
            String key = normaliseKey(f);
            if (key == null || key.isEmpty()) continue;
            if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
            groups.get(key).add(f);
        }

        List<HubVersionChain> chains = new ArrayList<>();

        for (Map.Entry<String, List<HubFile>> entry : groups.entrySet()) {
            List<HubFile> group = entry.getValue();
            if (group.size() < 2) continue;

            // Sort group oldest → newest by version score, then import date as tiebreaker
            Collections.sort(group, Comparator
                    .comparingInt(HubVersionManager::versionScore)
                    .thenComparingLong(f -> f.importedAt));

            HubVersionChain chain = new HubVersionChain();
            chain.baseName = entry.getKey();
            for (HubFile f : group) {
                chain.fileIds.add(f.id);
                f.versionChainId = chain.id;
                f.versionLabel = extractVersionLabel(f);
            }
            chains.add(chain);
        }

        return chains;
    }

    /**
     * Generates rename suggestions for the next version of a file in a chain.
     *
     * For example, if the latest version is "Report_v2.pdf" the suggestion is
     * "Report_v3.pdf".
     */
    public static List<String> getNextVersionSuggestions(HubFile file) {
        List<String> suggestions = new ArrayList<>();
        String base = baseNameWithoutExtension(file);
        String ext = extension(file.originalFileName != null ? file.originalFileName : "");
        if (!ext.isEmpty()) ext = "." + ext;

        Matcher m = VERSION_SUFFIX.matcher(base);
        if (!m.find()) {
            suggestions.add(base + "_v2" + ext);
            suggestions.add(base + "_final" + ext);
            return suggestions;
        }

        String fullMatch = m.group(0).toLowerCase(Locale.ROOT);
        String stripped = base.substring(0, m.start());

        if (fullMatch.contains("_v")) {
            int n = parseNumber(m.group(2));
            suggestions.add(stripped + "_v" + (n + 1) + ext);
        } else if (fullMatch.contains("_final")) {
            String num = m.group(3);
            int n = (num == null || num.isEmpty()) ? 1 : Integer.parseInt(num);
            suggestions.add(stripped + "_final" + (n + 1) + ext);
        } else if (fullMatch.contains("(")) {
            int n = parseNumber(m.group(7));
            suggestions.add(stripped + " (" + (n + 1) + ")" + ext);
        }

        return suggestions;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns a normalised key: lowercase base name with extension and version suffix removed. */
    static String normaliseKey(HubFile f) {
        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name == null) return null;
        // Remove extension
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        // Remove version suffix
        return VERSION_SUFFIX.matcher(base).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    /** Assigns a numeric ordering score so that v1 < v2 < final < final2 etc. */
    private static int versionScore(HubFile f) {
        String base = baseNameWithoutExtension(f);
        Matcher m = VERSION_SUFFIX.matcher(base);
        if (!m.find()) return 0; // no version suffix → treat as v1 / original

        String full = m.group(0).toLowerCase(Locale.ROOT);
        if (full.contains("_v")) return parseNumber(m.group(2));
        if (full.contains("_new")) return VERSION_SCORE_BASE + parseNumber(m.group(6));
        if (full.contains("_updated")) return VERSION_SCORE_BASE + UPDATED_OFFSET + parseNumber(m.group(5));
        if (full.contains("_revised")) return VERSION_SCORE_BASE + REVISED_OFFSET + parseNumber(m.group(4));
        if (full.contains("_final")) return VERSION_SCORE_BASE + FINAL_OFFSET + parseNumber(m.group(3));
        if (full.contains("(")) return VERSION_SCORE_BASE + COPY_PAREN_OFFSET + parseNumber(m.group(7));
        return 0;
    }

    public static String extractVersionLabel(HubFile f) {
        if (f.versionLabel != null && !f.versionLabel.isEmpty()) return f.versionLabel;
        String base = baseNameWithoutExtension(f);
        Matcher m = VERSION_SUFFIX.matcher(base);
        if (!m.find()) return "";
        return m.group(0).replaceAll("[_()]", "").trim();
    }

    private static String baseNameWithoutExtension(HubFile f) {
        String name = f.displayName != null ? f.displayName : f.originalFileName;
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }

    private static int parseNumber(String s) {
        if (s == null || s.isEmpty()) return 1;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }
}
