"""
Feature 17: Projects module for the Python server.
Manages project CRUD with JSON file persistence.
"""
import os
import json
import time
import uuid

PROJECTS_FILE = os.path.join(os.path.dirname(__file__), "projects.json")


def _load():
    """Load projects from disk."""
    if not os.path.exists(PROJECTS_FILE):
        return []
    try:
        with open(PROJECTS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return []


def _save(projects):
    """Save projects to disk."""
    try:
        with open(PROJECTS_FILE, "w", encoding="utf-8") as f:
            json.dump(projects, f, indent=2, ensure_ascii=False)
    except OSError as e:
        print(f"[Projects] Save error: {e}")


def get_projects():
    """Get all projects."""
    return _load()


def save_project(data):
    """Create or update a project. Returns the project dict."""
    projects = _load()
    project_id = data.get("id")
    
    if project_id:
        # Update existing
        for i, p in enumerate(projects):
            if p.get("id") == project_id:
                p.update(data)
                p["updatedAt"] = int(time.time() * 1000)
                projects[i] = p
                _save(projects)
                return p
    
    # Create new
    project = {
        "id": uuid.uuid4().hex[:8],
        "name": data.get("name", "Untitled Project"),
        "description": data.get("description", ""),
        "colorHex": data.get("colorHex", "#6366F1"),
        "goalDeadline": data.get("goalDeadline"),
        "createdAt": int(time.time() * 1000),
        "updatedAt": int(time.time() * 1000),
        "isArchived": False,
    }
    project.update({k: v for k, v in data.items() if k in project})
    projects.append(project)
    _save(projects)
    return project


def delete_project(project_id):
    """Delete a project by ID. Returns True if found and deleted."""
    projects = _load()
    for i, p in enumerate(projects):
        if p.get("id") == project_id:
            projects.pop(i)
            _save(projects)
            return True
    return False
