package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  TASK TEMPLATES MANAGER — Built-in and custom task templates
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class TaskTemplatesManager {

    private static final String TAG = "TaskTemplatesManager";
    private static final String PREFS_NAME = "task_templates_prefs";
    private static final String CUSTOM_TEMPLATES_KEY = "custom_task_templates";

    private final SharedPreferences prefs;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TEMPLATE DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════════

    public static class TaskTemplate {
        public String id;
        public String name;
        public String description;
        public String priority;
        public String category;
        public List<String> subtaskTitles;
        public boolean isCustom;

        public TaskTemplate(String id, String name, String description,
                            String priority, String category,
                            List<String> subtaskTitles, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.priority = priority;
            this.category = category;
            this.subtaskTitles = subtaskTitles != null ? subtaskTitles : new ArrayList<>();
            this.isCustom = isCustom;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("description", description);
                json.put("priority", priority);
                json.put("category", category);
                json.put("isCustom", isCustom);
                JSONArray arr = new JSONArray();
                for (String s : subtaskTitles) arr.put(s);
                json.put("subtaskTitles", arr);
            } catch (JSONException e) {
                Log.e(TAG, "toJson: " + e.getMessage());
            }
            return json;
        }

        public static TaskTemplate fromJson(JSONObject json) {
            if (json == null) return null;
            try {
                List<String> titles = new ArrayList<>();
                JSONArray arr = json.optJSONArray("subtaskTitles");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) titles.add(arr.getString(i));
                }
                return new TaskTemplate(
                        json.optString("id", UUID.randomUUID().toString()),
                        json.optString("name", ""),
                        json.optString("description", ""),
                        json.optString("priority", Task.PRIORITY_NORMAL),
                        json.optString("category", "Personal"),
                        titles,
                        json.optBoolean("isCustom", true)
                );
            } catch (Exception e) {
                Log.e(TAG, "fromJson: " + e.getMessage());
                return null;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════════

    public TaskTemplatesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  BUILT-IN TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════════════

    public static List<TaskTemplate> getBuiltinTemplates() {
        List<TaskTemplate> templates = new ArrayList<>();

        List<String> standup = new ArrayList<>();
        standup.add("Review yesterday's progress");
        standup.add("Write today's plan");
        standup.add("Identify blockers");
        standup.add("Update team channel");
        templates.add(new TaskTemplate("builtin_standup", "Daily Standup",
                "Prepare for the daily standup meeting", Task.PRIORITY_NORMAL,
                "Work", standup, false));

        List<String> weekly = new ArrayList<>();
        weekly.add("Review last week's tasks");
        weekly.add("Set goals for this week");
        weekly.add("Schedule key meetings");
        weekly.add("Clear email inbox");
        weekly.add("Update project boards");
        templates.add(new TaskTemplate("builtin_weekly", "Weekly Planning",
                "Plan and organise the week ahead", Task.PRIORITY_HIGH,
                "Work", weekly, false));

        List<String> project = new ArrayList<>();
        project.add("Define project scope");
        project.add("Create task breakdown");
        project.add("Assign responsibilities");
        project.add("Set milestones & deadlines");
        project.add("Set up communication channels");
        project.add("Kick-off meeting");
        templates.add(new TaskTemplate("builtin_project", "Project Setup",
                "Initial setup for a new project", Task.PRIORITY_HIGH,
                "Work", project, false));

        List<String> study = new ArrayList<>();
        study.add("Review notes from last session");
        study.add("Read new material");
        study.add("Summarise key points");
        study.add("Practice problems / exercises");
        study.add("Create flashcards");
        templates.add(new TaskTemplate("builtin_study", "Study Session",
                "Structured study session", Task.PRIORITY_NORMAL,
                "Personal", study, false));

        List<String> workout = new ArrayList<>();
        workout.add("Warm-up (5–10 min)");
        workout.add("Main workout");
        workout.add("Cool-down stretches");
        workout.add("Log workout stats");
        workout.add("Hydrate & rest");
        templates.add(new TaskTemplate("builtin_workout", "Workout",
                "Daily workout routine", Task.PRIORITY_LOW,
                "Health", workout, false));

        return templates;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    public List<TaskTemplate> getAllTemplates() {
        List<TaskTemplate> all = new ArrayList<>(getBuiltinTemplates());
        all.addAll(getCustomTemplates());
        return all;
    }

    public List<TaskTemplate> getCustomTemplates() {
        List<TaskTemplate> customs = new ArrayList<>();
        String json = prefs.getString(CUSTOM_TEMPLATES_KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                TaskTemplate t = TaskTemplate.fromJson(array.getJSONObject(i));
                if (t != null) customs.add(t);
            }
        } catch (JSONException e) {
            Log.e(TAG, "getCustomTemplates: " + e.getMessage());
        }
        return customs;
    }

    public void saveCustomTemplate(TaskTemplate template) {
        if (template.id == null || template.id.isEmpty()) {
            template.id = "custom_" + UUID.randomUUID().toString();
        }
        template.isCustom = true;
        List<TaskTemplate> customs = getCustomTemplates();
        customs.add(template);
        persist(customs);
        Log.d(TAG, "Saved custom template: " + template.name);
    }

    public void deleteCustomTemplate(String id) {
        List<TaskTemplate> customs = getCustomTemplates();
        for (int i = 0; i < customs.size(); i++) {
            if (customs.get(i).id.equals(id)) {
                customs.remove(i);
                persist(customs);
                Log.d(TAG, "Deleted custom template: " + id);
                return;
            }
        }
    }

    /** Creates a new Task pre-filled from the given template. */
    public static Task applyTemplate(TaskTemplate template) {
        Task task = new Task();
        task.title = template.name;
        task.description = template.description;
        task.priority = template.priority;
        task.category = template.category;
        task.templateId = template.id;
        for (String title : template.subtaskTitles) {
            task.subtasks.add(new SubTask(title));
        }
        return task;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PRIVATE
    // ═══════════════════════════════════════════════════════════════════════════════

    private void persist(List<TaskTemplate> customs) {
        JSONArray array = new JSONArray();
        for (TaskTemplate t : customs) array.put(t.toJson());
        prefs.edit().putString(CUSTOM_TEMPLATES_KEY, array.toString()).apply();
    }
}
