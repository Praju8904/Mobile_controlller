package com.prajwal.myfirstapp.notes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE HTML EXPORTER — Export notes as standalone styled HTML pages.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Generates a complete HTML document with:
 *  • Dark theme styling matching the app
 *  • Responsive layout
 *  • Code syntax highlighting (basic)
 *  • Proper semantic elements for all block types
 *  • Meta information (category, tags, date)
 *  • Print-friendly media query
 */
public class NoteHtmlExporter {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  MAIN EXPORT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Export a note to a complete standalone HTML document.
     * @param note   The note to export.
     * @param blocks The content blocks.
     * @return Complete HTML string.
     */
    public static String exportToHtml(Note note, List<ContentBlock> blocks) {
        StringBuilder html = new StringBuilder();

        // ── DOCTYPE & HEAD ──
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(escapeHtml(note.title != null ? note.title : "Untitled")).append("</title>\n");
        html.append(getStyleSheet());
        html.append("</head>\n<body>\n");

        // ── HEADER ──
        html.append("<article class=\"note\">\n");
        html.append("<header>\n");
        if (note.title != null && !note.title.isEmpty()) {
            html.append("<h1 class=\"note-title\">").append(escapeHtml(note.title)).append("</h1>\n");
        }

        // Meta info bar
        html.append("<div class=\"meta\">\n");
        if (note.category != null) {
            html.append("<span class=\"badge category\">").append(escapeHtml(note.category)).append("</span>\n");
        }
        if (note.tags != null && !note.tags.isEmpty()) {
            for (String tag : note.tags) {
                html.append("<span class=\"badge tag\">#").append(escapeHtml(tag)).append("</span>\n");
            }
        }
        String dateStr = new SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.US)
                .format(new Date(note.updatedAt));
        html.append("<span class=\"date\">Last updated: ").append(dateStr).append("</span>\n");
        html.append("</div>\n");
        html.append("</header>\n\n");

        // ── CONTENT BLOCKS ──
        html.append("<div class=\"content\">\n");
        if (blocks != null) {
            boolean inList = false;
            String listTag = "";

            for (ContentBlock block : blocks) {
                String text = block.getText();
                String type = block.blockType;

                // Close open list if block is not a list type
                if (inList && !isListType(type)) {
                    html.append("</").append(listTag).append(">\n");
                    inList = false;
                }

                switch (type) {
                    case ContentBlock.TYPE_TEXT:
                        html.append("<p>").append(formatInlineText(text)).append("</p>\n");
                        break;

                    case ContentBlock.TYPE_HEADING1:
                        html.append("<h2>").append(escapeHtml(text)).append("</h2>\n");
                        break;

                    case ContentBlock.TYPE_HEADING2:
                        html.append("<h3>").append(escapeHtml(text)).append("</h3>\n");
                        break;

                    case ContentBlock.TYPE_HEADING3:
                        html.append("<h4>").append(escapeHtml(text)).append("</h4>\n");
                        break;

                    case ContentBlock.TYPE_BULLET:
                        if (!inList || !listTag.equals("ul")) {
                            if (inList) html.append("</").append(listTag).append(">\n");
                            html.append("<ul>\n");
                            listTag = "ul";
                            inList = true;
                        }
                        html.append("<li>").append(formatInlineText(text)).append("</li>\n");
                        break;

                    case ContentBlock.TYPE_NUMBERED:
                        if (!inList || !listTag.equals("ol")) {
                            if (inList) html.append("</").append(listTag).append(">\n");
                            html.append("<ol>\n");
                            listTag = "ol";
                            inList = true;
                        }
                        html.append("<li>").append(formatInlineText(text)).append("</li>\n");
                        break;

                    case ContentBlock.TYPE_CHECKLIST:
                        html.append("<div class=\"checklist-item\">");
                        html.append("<input type=\"checkbox\" disabled> ");
                        html.append(formatInlineText(text));
                        html.append("</div>\n");
                        break;

                    case ContentBlock.TYPE_QUOTE:
                        html.append("<blockquote>").append(formatInlineText(text)).append("</blockquote>\n");
                        break;

                    case ContentBlock.TYPE_CODE:
                        html.append("<pre><code>").append(escapeHtml(text)).append("</code></pre>\n");
                        break;

                    case ContentBlock.TYPE_CALLOUT:
                        html.append("<div class=\"callout\">💡 ").append(formatInlineText(text)).append("</div>\n");
                        break;

                    case ContentBlock.TYPE_DIVIDER:
                        html.append("<hr>\n");
                        break;

                    case ContentBlock.TYPE_TABLE:
                        html.append(renderTable(text));
                        break;

                    case ContentBlock.TYPE_MATH:
                        html.append("<div class=\"math\">").append(escapeHtml(text)).append("</div>\n");
                        break;

                    case ContentBlock.TYPE_TOGGLE:
                        html.append("<details><summary>").append(escapeHtml(text)).append("</summary>");
                        html.append("<p>(Toggle content)</p></details>\n");
                        break;

                    default:
                        if (text != null && !text.trim().isEmpty()) {
                            html.append("<p>").append(formatInlineText(text)).append("</p>\n");
                        }
                        break;
                }
            }

            // Close any open list
            if (inList) {
                html.append("</").append(listTag).append(">\n");
            }
        }
        html.append("</div>\n");

        // ── FOOTER ──
        html.append("<footer>\n");
        html.append("<p>Exported from <strong>Smart Notes</strong></p>\n");
        html.append("</footer>\n");

        html.append("</article>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIGHT THEME VARIANT
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Export with a light theme instead of dark.
     */
    public static String exportToHtmlLight(Note note, List<ContentBlock> blocks) {
        String html = exportToHtml(note, blocks);
        // Replace dark theme colors with light ones
        html = html.replace("#0A0E21", "#FFFFFF");
        html = html.replace("#0F172A", "#F8FAFC");
        html = html.replace("#1E293B", "#E2E8F0");
        html = html.replace("#F1F5F9", "#1E293B");
        html = html.replace("#94A3B8", "#64748B");
        html = html.replace("#64748B", "#94A3B8");
        html = html.replace("color: #CBD5E1", "color: #475569");
        return html;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  STYLESHEET
    // ═══════════════════════════════════════════════════════════════════════════════

    private static String getStyleSheet() {
        return "<style>\n" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "body {\n" +
                "  background: #0A0E21;\n" +
                "  color: #F1F5F9;\n" +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "  line-height: 1.7;\n" +
                "  padding: 2rem;\n" +
                "}\n" +
                ".note {\n" +
                "  max-width: 800px;\n" +
                "  margin: 0 auto;\n" +
                "  background: #0F172A;\n" +
                "  border-radius: 16px;\n" +
                "  padding: 2.5rem;\n" +
                "  box-shadow: 0 4px 24px rgba(0,0,0,0.3);\n" +
                "}\n" +
                "header { margin-bottom: 2rem; border-bottom: 1px solid #1E293B; padding-bottom: 1.5rem; }\n" +
                ".note-title {\n" +
                "  font-size: 2rem;\n" +
                "  font-weight: 700;\n" +
                "  color: #F1F5F9;\n" +
                "  margin-bottom: 1rem;\n" +
                "}\n" +
                ".meta { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }\n" +
                ".badge {\n" +
                "  display: inline-block;\n" +
                "  padding: 4px 12px;\n" +
                "  border-radius: 20px;\n" +
                "  font-size: 0.8rem;\n" +
                "  font-weight: 500;\n" +
                "}\n" +
                ".category { background: #F59E0B22; color: #F59E0B; }\n" +
                ".tag { background: #3B82F622; color: #3B82F6; }\n" +
                ".date { color: #64748B; font-size: 0.85rem; margin-left: auto; }\n" +
                ".content { margin-top: 1rem; }\n" +
                ".content p { margin-bottom: 1rem; color: #CBD5E1; }\n" +
                "h2 { font-size: 1.5rem; margin: 2rem 0 1rem; color: #F1F5F9; }\n" +
                "h3 { font-size: 1.25rem; margin: 1.5rem 0 0.75rem; color: #F1F5F9; }\n" +
                "h4 { font-size: 1.1rem; margin: 1.25rem 0 0.5rem; color: #F1F5F9; }\n" +
                "ul, ol { margin: 1rem 0; padding-left: 2rem; color: #CBD5E1; }\n" +
                "li { margin-bottom: 0.5rem; }\n" +
                "blockquote {\n" +
                "  border-left: 3px solid #F59E0B;\n" +
                "  padding: 1rem 1.5rem;\n" +
                "  margin: 1rem 0;\n" +
                "  background: #1E293B;\n" +
                "  border-radius: 0 8px 8px 0;\n" +
                "  color: #94A3B8;\n" +
                "  font-style: italic;\n" +
                "}\n" +
                "pre {\n" +
                "  background: #1E293B;\n" +
                "  border-radius: 8px;\n" +
                "  padding: 1rem;\n" +
                "  margin: 1rem 0;\n" +
                "  overflow-x: auto;\n" +
                "}\n" +
                "code { font-family: 'Fira Code', 'Cascadia Code', monospace; font-size: 0.9rem; color: #A5F3FC; }\n" +
                ".callout {\n" +
                "  background: #F59E0B15;\n" +
                "  border: 1px solid #F59E0B33;\n" +
                "  border-radius: 8px;\n" +
                "  padding: 1rem;\n" +
                "  margin: 1rem 0;\n" +
                "  color: #F59E0B;\n" +
                "}\n" +
                ".checklist-item { margin: 0.5rem 0; color: #CBD5E1; }\n" +
                ".checklist-item input { margin-right: 0.5rem; }\n" +
                "hr { border: none; height: 1px; background: #1E293B; margin: 2rem 0; }\n" +
                ".math { background: #1E293B; padding: 1rem; border-radius: 8px; font-family: monospace; margin: 1rem 0; }\n" +
                "details { margin: 1rem 0; }\n" +
                "summary { cursor: pointer; color: #F59E0B; font-weight: 500; }\n" +
                "details p { padding: 0.5rem 1rem; }\n" +
                "table { width: 100%; border-collapse: collapse; margin: 1rem 0; }\n" +
                "th, td { border: 1px solid #1E293B; padding: 0.5rem 1rem; text-align: left; }\n" +
                "th { background: #1E293B; color: #F59E0B; }\n" +
                "footer {\n" +
                "  margin-top: 2rem;\n" +
                "  padding-top: 1rem;\n" +
                "  border-top: 1px solid #1E293B;\n" +
                "  text-align: center;\n" +
                "  color: #64748B;\n" +
                "  font-size: 0.8rem;\n" +
                "}\n" +
                "@media print {\n" +
                "  body { background: white; color: black; }\n" +
                "  .note { box-shadow: none; background: white; }\n" +
                "  .content p, li, .checklist-item { color: #333; }\n" +
                "  pre, .callout, blockquote { background: #f5f5f5; }\n" +
                "  code { color: #333; }\n" +
                "}\n" +
                "</style>\n";
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Convert inline markdown-like formatting to HTML. */
    private static String formatInlineText(String text) {
        if (text == null) return "";
        String s = escapeHtml(text);
        // Bold: **text** → <strong>text</strong>
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // Italic: *text* → <em>text</em>
        s = s.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // Inline code: `text` → <code>text</code>
        s = s.replaceAll("`(.+?)`", "<code>$1</code>");
        // Links: [text](url) → <a>text</a>
        s = s.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\" style=\"color:#3B82F6\">$1</a>");
        return s;
    }

    private static boolean isListType(String type) {
        return ContentBlock.TYPE_BULLET.equals(type) ||
                ContentBlock.TYPE_NUMBERED.equals(type);
    }

    /** Render a simple table from pipe-separated text. */
    private static String renderTable(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder table = new StringBuilder("<table>\n");
        String[] rows = text.split("\n");
        boolean isHeader = true;
        for (String row : rows) {
            if (row.trim().startsWith("---")) continue; // Skip separator row
            String[] cells = row.split("\\|");
            table.append("<tr>");
            for (String cell : cells) {
                String trimmed = cell.trim();
                if (trimmed.isEmpty()) continue;
                if (isHeader) {
                    table.append("<th>").append(escapeHtml(trimmed)).append("</th>");
                } else {
                    table.append("<td>").append(escapeHtml(trimmed)).append("</td>");
                }
            }
            table.append("</tr>\n");
            isHeader = false;
        }
        table.append("</table>\n");
        return table.toString();
    }
}
