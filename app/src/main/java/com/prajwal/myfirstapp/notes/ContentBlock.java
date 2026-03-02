package com.prajwal.myfirstapp.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  ContentBlock — A single block in the block-based Notion-style editor.
 *  Every piece of content is a block: text, heading, checklist item, image, code, etc.
 *  Blocks are independently typed, styled, moved, and converted.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class ContentBlock {

    // ═══ Block Type Constants ═══
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_HEADING1 = "heading1";
    public static final String TYPE_HEADING2 = "heading2";
    public static final String TYPE_HEADING3 = "heading3";
    public static final String TYPE_CHECKLIST = "checklist";
    public static final String TYPE_BULLET = "bullet";
    public static final String TYPE_NUMBERED = "numbered";
    public static final String TYPE_TOGGLE = "toggle";
    public static final String TYPE_QUOTE = "quote";
    public static final String TYPE_CALLOUT = "callout";
    public static final String TYPE_CODE = "code";
    public static final String TYPE_DIVIDER = "divider";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_MATH = "math";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_LINK_PREVIEW = "link_preview";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_DRAWING = "drawing";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_LOCATION = "location";

    // View type constants for RecyclerView adapter
    public static final int VIEW_TEXT = 0;
    public static final int VIEW_HEADING1 = 1;
    public static final int VIEW_HEADING2 = 2;
    public static final int VIEW_HEADING3 = 3;
    public static final int VIEW_CHECKLIST = 4;
    public static final int VIEW_BULLET = 5;
    public static final int VIEW_NUMBERED = 6;
    public static final int VIEW_TOGGLE = 7;
    public static final int VIEW_QUOTE = 8;
    public static final int VIEW_CALLOUT = 9;
    public static final int VIEW_CODE = 10;
    public static final int VIEW_DIVIDER = 11;
    public static final int VIEW_IMAGE = 12;
    public static final int VIEW_TABLE = 13;
    public static final int VIEW_MATH = 14;
    public static final int VIEW_FILE = 15;
    public static final int VIEW_LINK_PREVIEW = 16;
    public static final int VIEW_AUDIO = 17;
    public static final int VIEW_DRAWING = 18;
    public static final int VIEW_VIDEO = 19;
    public static final int VIEW_LOCATION = 20;

    // All supported types for block picker
    public static final String[] ALL_TYPES = {
        TYPE_TEXT, TYPE_HEADING1, TYPE_HEADING2, TYPE_HEADING3,
        TYPE_CHECKLIST, TYPE_BULLET, TYPE_NUMBERED, TYPE_TOGGLE,
        TYPE_QUOTE, TYPE_CALLOUT, TYPE_CODE, TYPE_DIVIDER,
        TYPE_IMAGE, TYPE_TABLE, TYPE_MATH, TYPE_FILE,
        TYPE_LINK_PREVIEW, TYPE_AUDIO, TYPE_DRAWING, TYPE_VIDEO,
        TYPE_LOCATION
    };

    public static final String[] TYPE_NAMES = {
        "Text", "Heading 1", "Heading 2", "Heading 3",
        "Checklist", "Bullet List", "Numbered List", "Toggle",
        "Quote", "Callout", "Code Block", "Divider",
        "Image", "Table", "Math / Equation", "File Attachment",
        "Link Preview", "Audio Recording", "Drawing / Sketch", "Video",
        "Location"
    };

    public static final String[] TYPE_ICONS = {
        "¶", "H₁", "H₂", "H₃",
        "☑", "•", "1.", "▶",
        "❝", "💡", "</>", "—",
        "🖼️", "⊞", "∑", "📎",
        "🔗", "🎙️", "✏️", "🎬",
        "📍"
    };

    public static final String[] TYPE_DESCRIPTIONS = {
        "Plain paragraph text", "Large bold heading", "Medium bold heading", "Small semibold heading",
        "Interactive checkbox item", "Bullet point with indent", "Auto-numbered list item", "Collapsible section",
        "Highlighted quote block", "Colored box with emoji", "Syntax-highlighted code", "Horizontal separator",
        "Embedded image with caption", "Rows and columns grid", "LaTeX math notation", "Attach a file",
        "Rich URL card preview", "Record audio inline", "Sketch or draw inline", "Embed video from gallery",
        "Pin a location"
    };

    // Supported code languages
    public static final String[] CODE_LANGUAGES = {
        "plain", "java", "python", "javascript", "typescript", "c", "cpp", "csharp",
        "html", "css", "xml", "json", "sql", "bash", "kotlin", "swift",
        "ruby", "php", "go", "rust", "dart", "yaml", "markdown", "r"
    };

    // ═══ Fields ═══
    public String id;
    public String blockType;
    public JSONObject content;
    public int sortOrder;
    public int indentLevel;   // 0–3 for lists
    public long createdAt;
    public long updatedAt;

    // ═══ Constructors ═══

    public ContentBlock() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.blockType = TYPE_TEXT;
        this.content = new JSONObject();
        this.sortOrder = 0;
        this.indentLevel = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public ContentBlock(String type) {
        this();
        this.blockType = type;
        initDefaultContent(type);
    }

    public ContentBlock(String type, String text) {
        this(type);
        setText(text);
    }

    // ═══ Content Accessors ═══

    public String getText() {
        return content.optString("text", "");
    }

    public void setText(String text) {
        try { content.put("text", text != null ? text : ""); } catch (JSONException ignored) {}
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isChecked() { return content.optBoolean("checked", false); }
    public void setChecked(boolean checked) {
        try { content.put("checked", checked); } catch (JSONException ignored) {}
        this.updatedAt = System.currentTimeMillis();
    }

    public String getLanguage() { return content.optString("language", "plain"); }
    public void setLanguage(String lang) {
        try { content.put("language", lang); } catch (JSONException ignored) {}
    }

    public String getDividerStyle() { return content.optString("style", "solid"); }
    public void setDividerStyle(String style) {
        try { content.put("style", style); } catch (JSONException ignored) {}
    }

    public String getEmoji() { return content.optString("emoji", "💡"); }
    public void setEmoji(String emoji) {
        try { content.put("emoji", emoji); } catch (JSONException ignored) {}
    }

    public String getBlockColor() { return content.optString("backgroundColor", "#1E3A5F"); }
    public void setBlockColor(String color) {
        try { content.put("backgroundColor", color); } catch (JSONException ignored) {}
    }

    public String getQuoteColor() { return content.optString("color", "#3B82F6"); }
    public void setQuoteColor(String color) {
        try { content.put("color", color); } catch (JSONException ignored) {}
    }

    public boolean isCollapsed() { return content.optBoolean("collapsed", false); }
    public void setCollapsed(boolean collapsed) {
        try { content.put("collapsed", collapsed); } catch (JSONException ignored) {}
    }

    public String getImageUri() { return content.optString("uri", ""); }
    public void setImageUri(String uri) {
        try { content.put("uri", uri); } catch (JSONException ignored) {}
    }

    public String getCaption() { return content.optString("caption", ""); }
    public void setCaption(String caption) {
        try { content.put("caption", caption); } catch (JSONException ignored) {}
    }

    public int getImageWidth() { return content.optInt("width", 100); }
    public void setImageWidth(int widthPercent) {
        try { content.put("width", widthPercent); } catch (JSONException ignored) {}
    }

    public String getUrl() { return content.optString("url", ""); }
    public void setUrl(String url) {
        try { content.put("url", url); } catch (JSONException ignored) {}
    }

    public String getLinkTitle() { return content.optString("title", ""); }
    public String getLinkDescription() { return content.optString("description", ""); }
    public String getLinkDomain() { return content.optString("domain", ""); }

    public String getFileName() { return content.optString("fileName", ""); }
    public long getFileSize() { return content.optLong("fileSize", 0); }
    public String getFilePath() { return content.optString("filePath", ""); }
    public String getFileType() { return content.optString("fileType", ""); }

    public String getLatex() { return content.optString("latex", ""); }
    public void setLatex(String latex) {
        try { content.put("latex", latex); } catch (JSONException ignored) {}
    }

    public double getLatitude() { return content.optDouble("lat", 0); }
    public double getLongitude() { return content.optDouble("lng", 0); }
    public String getLocationName() { return content.optString("locationName", ""); }

    public long getAudioDuration() { return content.optLong("duration", 0); }
    public String getAudioPath() { return content.optString("filePath", ""); }

    public String getVideoUri() { return content.optString("uri", ""); }
    public String getVideoThumbnail() { return content.optString("thumbnail", ""); }

    public String getDrawingPath() { return content.optString("imagePath", ""); }

    // Table accessors
    public JSONArray getTableRows() { return content.optJSONArray("rows"); }
    public boolean hasTableHeader() { return content.optBoolean("hasHeader", true); }

    // Toggle child block IDs
    public JSONArray getChildBlockIds() { return content.optJSONArray("childBlockIds"); }
    public void addChildBlockId(String blockId) {
        try {
            JSONArray arr = getChildBlockIds();
            if (arr == null) { arr = new JSONArray(); content.put("childBlockIds", arr); }
            arr.put(blockId);
        } catch (JSONException ignored) {}
    }

    // Text/block color for any block
    public String getTextColor() { return content.optString("textColor", ""); }
    public void setTextColor(String color) {
        try { content.put("textColor", color); } catch (JSONException ignored) {}
    }

    // Inline comments on this block
    public JSONArray getComments() { return content.optJSONArray("comments"); }
    public void addComment(String commentText, long timestamp) {
        try {
            JSONArray arr = getComments();
            if (arr == null) { arr = new JSONArray(); content.put("comments", arr); }
            JSONObject c = new JSONObject();
            c.put("text", commentText);
            c.put("timestamp", timestamp);
            c.put("resolved", false);
            arr.put(c);
        } catch (JSONException ignored) {}
    }

    // ═══ Default Content Initialization ═══

    private void initDefaultContent(String type) {
        try {
            switch (type) {
                case TYPE_TEXT:
                case TYPE_HEADING1:
                case TYPE_HEADING2:
                case TYPE_HEADING3:
                case TYPE_BULLET:
                case TYPE_NUMBERED:
                    content.put("text", "");
                    break;
                case TYPE_CHECKLIST:
                    content.put("text", "");
                    content.put("checked", false);
                    break;
                case TYPE_CODE:
                    content.put("text", "");
                    content.put("language", "plain");
                    break;
                case TYPE_DIVIDER:
                    content.put("style", "solid");
                    break;
                case TYPE_CALLOUT:
                    content.put("text", "");
                    content.put("emoji", "💡");
                    content.put("backgroundColor", "#1E3A5F");
                    break;
                case TYPE_TOGGLE:
                    content.put("text", "Toggle section");
                    content.put("collapsed", true);
                    content.put("childBlockIds", new JSONArray());
                    break;
                case TYPE_QUOTE:
                    content.put("text", "");
                    content.put("color", "#3B82F6");
                    break;
                case TYPE_TABLE:
                    JSONArray rows = new JSONArray();
                    JSONArray h = new JSONArray(); h.put("Column 1"); h.put("Column 2"); h.put("Column 3");
                    JSONArray r = new JSONArray(); r.put(""); r.put(""); r.put("");
                    rows.put(h); rows.put(r);
                    content.put("rows", rows);
                    content.put("hasHeader", true);
                    break;
                case TYPE_IMAGE:
                    content.put("uri", "");
                    content.put("caption", "");
                    content.put("width", 100);
                    break;
                case TYPE_MATH:
                    content.put("latex", "");
                    break;
                case TYPE_FILE:
                    content.put("fileName", "");
                    content.put("filePath", "");
                    content.put("fileSize", 0);
                    content.put("fileType", "");
                    break;
                case TYPE_LINK_PREVIEW:
                    content.put("url", "");
                    content.put("title", "");
                    content.put("description", "");
                    content.put("domain", "");
                    break;
                case TYPE_AUDIO:
                    content.put("filePath", "");
                    content.put("duration", 0);
                    break;
                case TYPE_DRAWING:
                    content.put("imagePath", "");
                    break;
                case TYPE_VIDEO:
                    content.put("uri", "");
                    content.put("thumbnail", "");
                    break;
                case TYPE_LOCATION:
                    content.put("lat", 0.0);
                    content.put("lng", 0.0);
                    content.put("locationName", "");
                    break;
            }
        } catch (JSONException ignored) {}
    }

    // ═══ View Type Mapping ═══

    public int getViewType() {
        switch (blockType) {
            case TYPE_HEADING1: return VIEW_HEADING1;
            case TYPE_HEADING2: return VIEW_HEADING2;
            case TYPE_HEADING3: return VIEW_HEADING3;
            case TYPE_CHECKLIST: return VIEW_CHECKLIST;
            case TYPE_BULLET: return VIEW_BULLET;
            case TYPE_NUMBERED: return VIEW_NUMBERED;
            case TYPE_TOGGLE: return VIEW_TOGGLE;
            case TYPE_QUOTE: return VIEW_QUOTE;
            case TYPE_CALLOUT: return VIEW_CALLOUT;
            case TYPE_CODE: return VIEW_CODE;
            case TYPE_DIVIDER: return VIEW_DIVIDER;
            case TYPE_IMAGE: return VIEW_IMAGE;
            case TYPE_TABLE: return VIEW_TABLE;
            case TYPE_MATH: return VIEW_MATH;
            case TYPE_FILE: return VIEW_FILE;
            case TYPE_LINK_PREVIEW: return VIEW_LINK_PREVIEW;
            case TYPE_AUDIO: return VIEW_AUDIO;
            case TYPE_DRAWING: return VIEW_DRAWING;
            case TYPE_VIDEO: return VIEW_VIDEO;
            case TYPE_LOCATION: return VIEW_LOCATION;
            default: return VIEW_TEXT;
        }
    }

    // ═══ JSON Serialization ═══

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("blockType", blockType);
            json.put("content", content);
            json.put("sortOrder", sortOrder);
            json.put("indentLevel", indentLevel);
            json.put("createdAt", createdAt);
            json.put("updatedAt", updatedAt);
        } catch (JSONException ignored) {}
        return json;
    }

    public static ContentBlock fromJson(JSONObject json) {
        ContentBlock block = new ContentBlock();
        block.id = json.optString("id", UUID.randomUUID().toString().substring(0, 12));
        block.blockType = json.optString("blockType", TYPE_TEXT);
        block.content = json.optJSONObject("content");
        if (block.content == null) block.content = new JSONObject();
        block.sortOrder = json.optInt("sortOrder", 0);
        block.indentLevel = json.optInt("indentLevel", 0);
        block.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        block.updatedAt = json.optLong("updatedAt", System.currentTimeMillis());
        return block;
    }

    public static JSONArray toJsonArray(List<ContentBlock> blocks) {
        JSONArray arr = new JSONArray();
        for (ContentBlock b : blocks) arr.put(b.toJson());
        return arr;
    }

    public static List<ContentBlock> fromJsonArray(String json) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (json == null || json.isEmpty()) return blocks;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                blocks.add(ContentBlock.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
        return blocks;
    }

    // ═══ Utility Methods ═══

    /** Returns true if this block type contains editable text */
    public boolean isTextBased() {
        switch (blockType) {
            case TYPE_TEXT: case TYPE_HEADING1: case TYPE_HEADING2: case TYPE_HEADING3:
            case TYPE_QUOTE: case TYPE_BULLET: case TYPE_NUMBERED: case TYPE_CHECKLIST:
            case TYPE_CALLOUT: case TYPE_TOGGLE: case TYPE_CODE: case TYPE_MATH:
                return true;
            default: return false;
        }
    }

    /** Returns true if this block type supports indentation */
    public boolean isIndentable() {
        return blockType.equals(TYPE_BULLET) || blockType.equals(TYPE_NUMBERED)
            || blockType.equals(TYPE_CHECKLIST);
    }

    /** Increase indent level (max 3) */
    public void indent() { if (indentLevel < 3) indentLevel++; }

    /** Decrease indent level (min 0) */
    public void outdent() { if (indentLevel > 0) indentLevel--; }

    /** Create a deep copy of this block with a new ID */
    public ContentBlock duplicate() {
        ContentBlock copy = new ContentBlock();
        copy.blockType = this.blockType;
        try { copy.content = new JSONObject(this.content.toString()); }
        catch (JSONException e) { copy.content = new JSONObject(); }
        copy.indentLevel = this.indentLevel;
        return copy;
    }

    /** Convert this block to a different type, preserving text content */
    public void convertTo(String newType) {
        String preservedText = getText();
        boolean wasChecked = isChecked();
        this.blockType = newType;
        this.content = new JSONObject();
        initDefaultContent(newType);
        if (isTextBased()) setText(preservedText);
        if (newType.equals(TYPE_CHECKLIST)) setChecked(wasChecked);
        this.updatedAt = System.currentTimeMillis();
    }

    /** Get a plain text representation for search/preview */
    public String getPlainText() {
        switch (blockType) {
            case TYPE_DIVIDER: return "---";
            case TYPE_IMAGE: return "[Image" + (getCaption().isEmpty() ? "]" : ": " + getCaption() + "]");
            case TYPE_FILE: return "[File: " + getFileName() + "]";
            case TYPE_AUDIO: return "[Audio recording]";
            case TYPE_DRAWING: return "[Drawing]";
            case TYPE_VIDEO: return "[Video]";
            case TYPE_LOCATION: return "[Location: " + getLocationName() + "]";
            case TYPE_LINK_PREVIEW: return "[Link: " + getUrl() + "]";
            case TYPE_TABLE: return "[Table]";
            case TYPE_MATH: return "[Math: " + getLatex() + "]";
            case TYPE_CALLOUT: return getEmoji() + " " + getText();
            case TYPE_CHECKLIST:
                return (isChecked() ? "[x] " : "[ ] ") + getText();
            case TYPE_BULLET: return "• " + getText();
            case TYPE_CODE: return "```" + getLanguage() + "\n" + getText() + "\n```";
            default: return getText();
        }
    }

    /** Get bullet character based on indent level */
    public String getBulletChar() {
        switch (indentLevel) {
            case 0: return "●";
            case 1: return "○";
            case 2: return "■";
            default: return "▪";
        }
    }

    /** Get the display name for this block's type */
    public String getTypeName() {
        for (int i = 0; i < ALL_TYPES.length; i++) {
            if (ALL_TYPES[i].equals(blockType)) return TYPE_NAMES[i];
        }
        return "Text";
    }

    /** Get the icon for this block's type */
    public String getTypeIcon() {
        for (int i = 0; i < ALL_TYPES.length; i++) {
            if (ALL_TYPES[i].equals(blockType)) return TYPE_ICONS[i];
        }
        return "¶";
    }

    /** Formats file size for display */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Formats audio duration for display */
    public static String formatDuration(long millis) {
        long secs = millis / 1000;
        long mins = secs / 60;
        secs %= 60;
        if (mins > 0) return String.format(java.util.Locale.US, "%d:%02d", mins, secs);
        return String.format(java.util.Locale.US, "0:%02d", secs);
    }
}
