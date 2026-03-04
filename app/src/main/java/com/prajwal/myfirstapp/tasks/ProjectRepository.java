package com.prajwal.myfirstapp.tasks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Project CRUD operations (Feature 17).
 * Persists to SharedPreferences as JSON.
 */
public class ProjectRepository {

    private static final String PREFS_NAME = "projects_prefs";
    private static final String PROJECTS_KEY = "projects_list";

    private final SharedPreferences prefs;
    private List<Project> projects;

    public ProjectRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadProjects();
    }

    private void loadProjects() {
        projects = new ArrayList<>();
        String json = prefs.getString(PROJECTS_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Project p = Project.fromJson(arr.getJSONObject(i));
                if (p != null) projects.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveProjects() {
        JSONArray arr = new JSONArray();
        for (Project p : projects) {
            arr.put(p.toJson());
        }
        prefs.edit().putString(PROJECTS_KEY, arr.toString()).apply();
    }

    public List<Project> getAllProjects() {
        return new ArrayList<>(projects);
    }

    public List<Project> getActiveProjects() {
        List<Project> result = new ArrayList<>();
        for (Project p : projects) {
            if (!p.isArchived) result.add(p);
        }
        return result;
    }

    public Project getProjectById(String id) {
        for (Project p : projects) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public void addProject(Project project) {
        projects.add(project);
        saveProjects();
    }

    public void updateProject(Project project) {
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).id.equals(project.id)) {
                project.updatedAt = System.currentTimeMillis();
                projects.set(i, project);
                saveProjects();
                return;
            }
        }
    }

    public void deleteProject(String id) {
        projects.removeIf(p -> p.id.equals(id));
        saveProjects();
    }

    public void archiveProject(String id) {
        Project p = getProjectById(id);
        if (p != null) {
            p.isArchived = true;
            p.updatedAt = System.currentTimeMillis();
            saveProjects();
        }
    }
}
