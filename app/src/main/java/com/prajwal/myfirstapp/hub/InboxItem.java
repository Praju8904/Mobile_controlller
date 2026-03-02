package com.prajwal.myfirstapp.hub;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A file detected by the scanner that is waiting for the user to organize in the Inbox.
 */
public class InboxItem {

    public enum Status {
        PENDING, ACCEPTED, REJECTED, SNOOZED
    }

    public String id;
    public String filePath;
    public String fileName;
    public HubFile.FileType fileType;
    public long fileSize;
    public HubFile.Source source;
    public long detectedAt;
    public String suggestedFolderId;
    public String suggestedProjectId;
    public List<String> suggestedTags;
    public int autoCategorizationConfidence;
    public Status status;
    public String thumbnailPath;
    public String mimeType;

    public InboxItem() {
        this.id = UUID.randomUUID().toString();
        this.suggestedTags = new ArrayList<>();
        this.status = Status.PENDING;
        this.detectedAt = System.currentTimeMillis();
        this.autoCategorizationConfidence = 0;
    }

    public String getFormattedSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024L * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
    }

    public String getConfidenceLabel() {
        if (autoCategorizationConfidence >= 80) return "High";
        if (autoCategorizationConfidence >= 50) return "Medium";
        return "Low";
    }

    public String getSourceEmoji() {
        if (source == null) return "📥";
        switch (source) {
            case WHATSAPP: return "💬";
            case DOWNLOADS: return "⬇️";
            case GALLERY: return "🖼️";
            case SCREENSHOTS: return "📸";
            case CAMERA: return "📷";
            default: return "📥";
        }
    }

    public String getTypeEmoji() {
        if (fileType == null) return "📄";
        switch (fileType) {
            case PDF: return "📕";
            case DOCUMENT: return "📝";
            case IMAGE: return "🖼️";
            case SCREENSHOT: return "📸";
            case VIDEO: return "🎬";
            case AUDIO: return "🎵";
            case CODE: return "💻";
            case ARCHIVE: return "🗜️";
            case SPREADSHEET: return "📊";
            case PRESENTATION: return "📽️";
            default: return "📄";
        }
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id != null ? id : "");
            o.put("filePath", filePath != null ? filePath : "");
            o.put("fileName", fileName != null ? fileName : "");
            o.put("fileType", fileType != null ? fileType.name() : HubFile.FileType.OTHER.name());
            o.put("fileSize", fileSize);
            o.put("source", source != null ? source.name() : HubFile.Source.OTHER.name());
            o.put("detectedAt", detectedAt);
            o.put("suggestedFolderId", suggestedFolderId != null ? suggestedFolderId : "");
            o.put("suggestedProjectId", suggestedProjectId != null ? suggestedProjectId : "");
            JSONArray tagsArr = new JSONArray();
            if (suggestedTags != null) for (String t : suggestedTags) tagsArr.put(t);
            o.put("suggestedTags", tagsArr);
            o.put("autoCategorizationConfidence", autoCategorizationConfidence);
            o.put("status", status != null ? status.name() : Status.PENDING.name());
            o.put("thumbnailPath", thumbnailPath != null ? thumbnailPath : "");
            o.put("mimeType", mimeType != null ? mimeType : "");
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static InboxItem fromJson(JSONObject o) {
        if (o == null) return null;
        try {
            InboxItem item = new InboxItem();
            item.id = o.optString("id", UUID.randomUUID().toString());
            item.filePath = o.optString("filePath", "");
            item.fileName = o.optString("fileName", "");
            String ft = o.optString("fileType", "OTHER");
            try { item.fileType = HubFile.FileType.valueOf(ft); } catch (Exception e) { item.fileType = HubFile.FileType.OTHER; }
            item.fileSize = o.optLong("fileSize", 0);
            String src = o.optString("source", "OTHER");
            try { item.source = HubFile.Source.valueOf(src); } catch (Exception e) { item.source = HubFile.Source.OTHER; }
            item.detectedAt = o.optLong("detectedAt", System.currentTimeMillis());
            item.suggestedFolderId = o.optString("suggestedFolderId", "");
            if (item.suggestedFolderId.isEmpty()) item.suggestedFolderId = null;
            item.suggestedProjectId = o.optString("suggestedProjectId", "");
            if (item.suggestedProjectId.isEmpty()) item.suggestedProjectId = null;
            item.suggestedTags = new ArrayList<>();
            JSONArray tagsArr = o.optJSONArray("suggestedTags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) item.suggestedTags.add(tagsArr.getString(i));
            }
            item.autoCategorizationConfidence = o.optInt("autoCategorizationConfidence", 0);
            String st = o.optString("status", "PENDING");
            try { item.status = Status.valueOf(st); } catch (Exception e) { item.status = Status.PENDING; }
            item.thumbnailPath = o.optString("thumbnailPath", "");
            item.mimeType = o.optString("mimeType", "");
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
