package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  NOTE TEMPLATES MANAGER — Built-in and custom note templates
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - 7 built-in templates (Meeting, Journal, Study, Project, Book, Recipe, Blank)
 * - Custom template creation, retrieval, and deletion via SharedPreferences
 * - Applies current date when inserting a template
 */
public class NoteTemplatesManager {

    private static final String TAG = "NoteTemplatesManager";
    private static final String PREFS_NAME = "note_templates_prefs";
    private static final String CUSTOM_TEMPLATES_KEY = "custom_templates";

    private final SharedPreferences prefs;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATE DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class NoteTemplate {
        public String id;
        public String name;
        public String body;       // Legacy HTML/rich text content (fallback)
        public String blocksJson;  // Block-based content (JSON array of ContentBlocks)
        public boolean isCustom;

        public NoteTemplate(String id, String name, String body, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.body = body;
            this.blocksJson = null;
            this.isCustom = isCustom;
        }

        public NoteTemplate(String id, String name, String body, String blocksJson, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.body = body;
            this.blocksJson = blocksJson;
            this.isCustom = isCustom;
        }

        /** Whether this template stores block-based content */
        public boolean hasBlocks() {
            return blocksJson != null && !blocksJson.isEmpty() && !blocksJson.equals("[]");
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("body", body);
                if (blocksJson != null) json.put("blocksJson", blocksJson);
                json.put("isCustom", isCustom);
            } catch (JSONException e) {
                Log.e("NoteTemplate", "Error serializing template: " + e.getMessage());
            }
            return json;
        }

        public static NoteTemplate fromJson(JSONObject json) {
            if (json == null) return null;
            try {
                NoteTemplate t = new NoteTemplate(
                        json.optString("id", UUID.randomUUID().toString().substring(0, 12)),
                        json.optString("name", ""),
                        json.optString("body", ""),
                        json.optString("blocksJson", null),
                        json.optBoolean("isCustom", true)
                );
                return t;
            } catch (Exception e) {
                Log.e("NoteTemplate", "Error deserializing template: " + e.getMessage());
                return null;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════════

    public NoteTemplatesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BUILT-IN TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns the list of built-in templates. "[date]" placeholders are replaced
     * with the actual date when the template is applied via {@link #applyTemplate}.
     */
    public static List<NoteTemplate> getBuiltInTemplates() {
        List<NoteTemplate> templates = new ArrayList<>();

        // ── Meeting Notes ──
        templates.add(new NoteTemplate(
                "builtin_meeting",
                "Meeting Notes",
                "",
                buildBlocksJson(
                    block("heading1", "Meeting Notes"),
                    block("text", "Date: [date]"),
                    block("heading2", "Attendees"),
                    block("bullet", ""),
                    block("heading2", "Agenda"),
                    block("numbered", ""),
                    block("heading2", "Discussion"),
                    block("text", ""),
                    block("divider", ""),
                    block("heading2", "Action Items"),
                    block("checklist", ""),
                    block("heading2", "Decisions"),
                    block("bullet", "")
                ),
                false
        ));

        // ── Daily Journal ──
        templates.add(new NoteTemplate(
                "builtin_journal",
                "Daily Journal",
                "",
                buildBlocksJson(
                    block("heading1", "Daily Journal"),
                    block("text", "Date: [date]"),
                    block("callout", "Mood: \uD83D\uDE0A"),
                    block("heading2", "Today's thoughts"),
                    block("text", ""),
                    block("heading2", "Gratitude"),
                    block("bullet", ""),
                    block("bullet", ""),
                    block("bullet", ""),
                    block("divider", ""),
                    block("heading2", "Tomorrow's goals"),
                    block("checklist", ""),
                    block("checklist", "")
                ),
                false
        ));

        // ── Study Notes ──
        templates.add(new NoteTemplate(
                "builtin_study",
                "Study Notes",
                "",
                buildBlocksJson(
                    block("heading1", "Study Notes"),
                    block("text", "Subject: "),
                    block("text", "Date: [date]"),
                    block("heading2", "Key Concepts"),
                    block("bullet", ""),
                    block("heading2", "Summary"),
                    block("text", ""),
                    block("heading2", "Important Formulas / Code"),
                    block("code", ""),
                    block("divider", ""),
                    block("heading2", "Questions & Follow-ups"),
                    block("checklist", ""),
                    block("heading3", "Resources"),
                    block("bullet", "")
                ),
                false
        ));

        // ── Project Plan ──
        templates.add(new NoteTemplate(
                "builtin_project",
                "Project Plan",
                "",
                buildBlocksJson(
                    block("heading1", "Project Plan"),
                    block("heading2", "Overview"),
                    block("text", ""),
                    block("heading2", "Goals"),
                    block("checklist", ""),
                    block("heading2", "Milestones"),
                    block("numbered", ""),
                    block("heading2", "Tasks"),
                    block("checklist", ""),
                    block("checklist", ""),
                    block("divider", ""),
                    block("callout", "Deadline: "),
                    block("heading2", "Notes"),
                    block("text", "")
                ),
                false
        ));

        // ── Book Notes ──
        templates.add(new NoteTemplate(
                "builtin_book",
                "Book Notes",
                "",
                buildBlocksJson(
                    block("heading1", "Book Notes"),
                    block("text", "Title: "),
                    block("text", "Author: "),
                    block("heading2", "Key Takeaways"),
                    block("numbered", ""),
                    block("heading2", "Favourite Quotes"),
                    block("quote", ""),
                    block("heading2", "Chapter Notes"),
                    block("toggle", "Chapter 1"),
                    block("divider", ""),
                    block("callout", "Rating: \u2B50\u2B50\u2B50\u2B50\u2B50")
                ),
                false
        ));

        // ── Recipe ──
        templates.add(new NoteTemplate(
                "builtin_recipe",
                "Recipe",
                "",
                buildBlocksJson(
                    block("heading1", "Recipe"),
                    block("text", "Servings: "),
                    block("text", "Prep time: "),
                    block("heading2", "Ingredients"),
                    block("checklist", ""),
                    block("checklist", ""),
                    block("heading2", "Instructions"),
                    block("numbered", ""),
                    block("numbered", ""),
                    block("divider", ""),
                    block("heading3", "Tips"),
                    block("callout", "")
                ),
                false
        ));

        // ── Blank ──
        templates.add(new NoteTemplate(
                "builtin_blank",
                "Blank",
                "",
                buildBlocksJson(block("text", "")),
                false
        ));

        return templates;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BLOCK TEMPLATE BUILDERS (helper methods for built-in templates)
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Create a minimal JSON entry for one block. */
    private static JSONObject block(String type, String text) {
        JSONObject b = new JSONObject();
        try {
            b.put("id", UUID.randomUUID().toString().substring(0, 12));
            b.put("type", type);
            b.put("text", text);
        } catch (JSONException e) {
            Log.e(TAG, "block() error", e);
        }
        return b;
    }

    /** Build a JSON array string from an array of block JSONObjects. */
    private static String buildBlocksJson(JSONObject... blockObjs) {
        JSONArray arr = new JSONArray();
        for (JSONObject b : blockObjs) arr.put(b);
        return arr.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns all built-in templates followed by all custom templates.
     */
    public List<NoteTemplate> getAllTemplates() {
        List<NoteTemplate> all = new ArrayList<>(getBuiltInTemplates());
        all.addAll(getCustomTemplates());
        return all;
    }

    /**
     * Loads and returns the list of custom templates from SharedPreferences.
     */
    public List<NoteTemplate> getCustomTemplates() {
        List<NoteTemplate> customs = new ArrayList<>();
        String json = prefs.getString(CUSTOM_TEMPLATES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                NoteTemplate template = NoteTemplate.fromJson(array.getJSONObject(i));
                if (template != null) customs.add(template);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading custom templates: " + e.getMessage());
        }
        return customs;
    }

    /**
     * Creates a new custom template with the given name and body, and persists it.
     */
    public void saveCustomTemplate(String name, String body) {
        saveCustomTemplate(name, body, null);
    }

    /**
     * Creates a new custom template with block-based content.
     * @param name       Template display name
     * @param body       Plain text fallback body
     * @param blocksJson JSON array of ContentBlocks (null for legacy templates)
     */
    public void saveCustomTemplate(String name, String body, String blocksJson) {
        String id = "custom_" + UUID.randomUUID().toString().substring(0, 12);
        NoteTemplate newTemplate = new NoteTemplate(
                id, name, body != null ? body : "",
                blocksJson, true);

        List<NoteTemplate> customs = getCustomTemplates();
        customs.add(newTemplate);
        persistCustomTemplates(customs);
        Log.d(TAG, "Saved custom template: " + name);
    }

    /**
     * Removes the custom template with the given id from SharedPreferences.
     */
    public void deleteCustomTemplate(String id) {
        List<NoteTemplate> customs = getCustomTemplates();
        boolean removed = false;
        for (int i = 0; i < customs.size(); i++) {
            if (customs.get(i).id.equals(id)) {
                customs.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            persistCustomTemplates(customs);
            Log.d(TAG, "Deleted custom template: " + id);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATE APPLICATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns the template body with "[date]" replaced by the current date
     * formatted as "MMM dd, yyyy" (e.g., "Dec 15, 2024").
     */
    public static String applyTemplate(NoteTemplate template) {
        if (template == null || template.body == null) return "";
        String currentDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date());
        return template.body.replace("[date]", currentDate);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════════

    private void persistCustomTemplates(List<NoteTemplate> customs) {
        JSONArray array = new JSONArray();
        for (NoteTemplate t : customs) array.put(t.toJson());
        prefs.edit().putString(CUSTOM_TEMPLATES_KEY, array.toString()).apply();
    }
}
