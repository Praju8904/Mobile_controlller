"""
Task Manager Module - Synchronized task management between PC and Mobile.

Features:
  - JSON persistent storage
  - Add/Complete/Delete tasks
  - Sync tasks to both PC GUI and mobile app
  - Desktop notifications via win10toast
  - Mobile notifications via reverse_commands
"""

import json
import os
import threading
import time
import copy
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Callable, Any

# Audit log integration (Feature 16)
try:
    import task_audit
except ImportError:
    task_audit = None

# File for persistent task storage
TASKS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tasks.json")

# ─── TASK SCHEMA DEFAULT ───────────────────────────────────────
# Every new task is initialized with these defaults. Missing fields
# on legacy tasks are back-filled by _migrate_tasks().
TASK_DEFAULTS = {
    "id": 0,
    "title": "",
    "description": "",
    "completed": False,
    "priority": "normal",        # low | normal | high | urgent
    "category": None,            # personal | work | study | health | shopping | finance | others | <custom>
    "tags": [],                  # ["tag1", "tag2"]
    "due_date": None,            # "YYYY-MM-DD"
    "due_time": None,            # "HH:MM"
    "reminders": [],             # [{"type":"time","value":"2026-03-01T09:00:00"}, {"type":"location","value":"gym"}]
    "recurrence": None,          # {"type":"daily|weekly|monthly|yearly|custom","interval":1,"days_of_week":[],"end_date":null}
    "estimated_duration": None,  # minutes as int (e.g. 45)
    "time_tracked": 0,           # total seconds tracked
    "time_sessions": [],         # [{"start":"iso","end":"iso","duration":seconds}]
    "timer_running": False,      # True when stopwatch is active
    "timer_start": None,         # ISO timestamp when current timer session started
    "subtasks": [],              # [{"id":1,"title":"...","completed":false}]
    "attachments": [],           # [{"name":"file.pdf","path":"/path/to/file","type":"pdf"}]
    "location_reminder": None,   # {"name":"Gym","lat":0.0,"lng":0.0,"radius":200}
    "energy_level": None,        # "deep_work" | "light" | "low_energy"
    "dependencies": [],          # [task_id, ...]  — IDs this task is blocked by
    "is_starred": False,
    "is_archived": False,
    "reschedule_count": 0,
    "created_at": "",
    "last_modified": "",
    "completed_at": None,
    "source": "pc",
    "color_label": None,     # nullable hex string e.g. "#E11D48"
    "start_date": None,      # nullable "YYYY-MM-DD"
    "effort_points": 0,      # Fibonacci int: 0=unset, 1,2,3,5,8,13
    "links": [],             # [{"id": str, "title": str, "url": str}]
    "comments": [],          # [{"id": str, "author": str, "text": str, "timestamp": int}]
    "isNextAction": False,   # GTD next action flag
    "isMIT": False,          # Most Important Task for today
    "contextTag": None,      # "@home"|"@computer"|"@phone"|"@errands"|"@anywhere"|null
    "waitingFor": None,      # who/what we're waiting on when status="waiting"
    "assignedTo": None,      # "Mobile"|"PC"|null
    "watchers": [],          # device IDs watching this task
    "isPrivate": False,      # hide details unless vault unlocked
    "projectId": None,       # links task to a Project
}

# In-memory task list
_tasks: List[Dict] = []
_lock = threading.Lock()

# Callbacks for UI updates
_callbacks: List[tuple] = []  # (event_name, callback)

# ─── PERSISTENCE ────────────────────────────────────────────────

def _load_tasks():
    """Load tasks from JSON file."""
    global _tasks
    try:
        if os.path.exists(TASKS_FILE):
            with open(TASKS_FILE, 'r', encoding='utf-8') as f:
                _tasks = json.load(f)
            # Migrate old tasks without last_modified field
            _migrate_tasks()
        else:
            _tasks = []
    except Exception as e:
        print(f"[TaskManager] Error loading tasks: {e}")
        _tasks = []

def _migrate_tasks():
    """Back-fill any missing fields from TASK_DEFAULTS onto existing tasks."""
    modified = False
    for task in _tasks:
        for key, default_val in TASK_DEFAULTS.items():
            if key not in task:
                # Use created_at as initial last_modified when migrating that field
                if key == "last_modified":
                    task[key] = task.get("created_at", datetime.now().isoformat())
                else:
                    task[key] = copy.deepcopy(default_val)
                modified = True
    if modified:
        _save_tasks()
        print(f"[TaskManager] Migrated {len(_tasks)} tasks — back-filled new schema fields")

def _save_tasks():
    """Save tasks to JSON file."""
    try:
        with open(TASKS_FILE, 'w', encoding='utf-8') as f:
            json.dump(_tasks, f, indent=2, ensure_ascii=False)
    except Exception as e:
        print(f"[TaskManager] Error saving tasks: {e}")

# Initialize on import
_load_tasks()

# ─── EVENT SYSTEM ───────────────────────────────────────────────

def on_event(event_name: str, callback: Callable):
    """Register a callback for task events.
    
    Events:
        - 'task_added': called with (task_dict, source)
        - 'task_completed': called with (task_id, source)
        - 'task_deleted': called with (task_id, source)
        - 'tasks_synced': called with (tasks_list)
    """
    _callbacks.append((event_name, callback))

def _notify(event_name: str, *args):
    """Notify all registered callbacks for an event."""
    for name, cb in _callbacks:
        if name == event_name:
            try:
                cb(*args)
            except Exception as e:
                print(f"[TaskManager] Callback error: {e}")

# ─── SENTIMENT PRIORITY DETECTION (Feature 12D) ────────────────

URGENT_KW = ["asap", "urgent", "critical", "immediately", "emergency", "!!", "blocker", "blocking"]
HIGH_KW   = ["important", "high priority", "soon", "before eod", "!important", "don't forget"]

def _detect_priority(title: str, desc: str) -> Optional[str]:
    """Auto-detect priority from task title/description keywords."""
    combined = f"{title or ''} {desc or ''}".lower()
    for kw in URGENT_KW:
        if kw in combined:
            return "urgent"
    for kw in HIGH_KW:
        if kw in combined:
            return "high"
    return None

# ─── WATCHER NOTIFICATION (Feature 8) ──────────────────────────

def _notify_watchers(task: dict, event: str, changed_fields: list = None):
    """Push notification to all watcher devices for a task mutation."""
    watchers = task.get("watchers", [])
    if not watchers:
        return
    msg = f"TASK_WATCHER_EVENT:{task.get('id')}:{event}"
    if changed_fields:
        msg += f":{','.join(changed_fields)}"
    for watcher in watchers:
        if watcher == "PC":
            _notify("task_watcher_event", task, event, changed_fields)
        elif watcher == "Mobile":
            try:
                import reverse_commands
                reverse_commands.send_to_phone(msg)
            except Exception as e:
                print(f"[TaskManager] Watcher notify error: {e}")

# ─── TASK OPERATIONS ────────────────────────────────────────────

def add_task(title: str, source: str = "pc", priority: str = "normal", 
             due_date: Optional[str] = None, due_time: Optional[str] = None,
             **extra_fields) -> Dict:
    """
    Add a new task.
    
    Args:
        title: Task description
        source: 'pc' or 'mobile' - where the task was created
        priority: 'low', 'normal', 'high', 'urgent'
        due_date: Optional due date string (YYYY-MM-DD)
        due_time: Optional due time string (HH:MM)
        **extra_fields: Any additional fields from TASK_DEFAULTS
            (description, category, tags, reminders, recurrence,
             estimated_duration, subtasks, attachments, location_reminder,
             energy_level, dependencies, is_starred)
    
    Returns:
        The created task dictionary
    """
    with _lock:
        # Generate unique ID
        task_id = int(time.time() * 1000) % 1000000000
        
        now_iso = datetime.now().isoformat()
        
        # Start from defaults, then overlay provided values
        task = copy.deepcopy(TASK_DEFAULTS)
        task.update({
            "id": task_id,
            "title": title,
            "completed": False,
            "priority": priority,
            "due_date": due_date,
            "due_time": due_time,
            "created_at": now_iso,
            "last_modified": now_iso,
            "source": source,
        })
        
        # Apply extra fields (only those present in TASK_DEFAULTS)
        for key, val in extra_fields.items():
            if key in TASK_DEFAULTS:
                task[key] = val
        
        # Feature 12D: Auto-detect priority when none set explicitly
        if priority in ("none", "normal"):
            detected = _detect_priority(title, task.get("description", ""))
            if detected:
                task["priority"] = detected
                _notify("priority_auto_elevated", task)

        _tasks.append(task)
        _save_tasks()
        
        # Format due info for log
        due_info = ""
        if due_date:
            due_info = f" (due: {due_date}"
            if due_time:
                due_info += f" {due_time}"
            due_info += ")"
        
        print(f"[TaskManager] Task added: {title}{due_info} (from {source})")
        
    # Notify listeners (outside lock)
    _notify("task_added", task, source)
    if task_audit:
        task_audit.log_change(str(task["id"]), "created", source,
                              new_value={"title": task["title"], "priority": task["priority"]})
    
    # Notify both devices
    _notify_both_devices(task, "added", source)
    
    return task

def complete_task(task_id: int, source: str = "pc") -> bool:
    """Mark a task as completed. If the task is recurring, auto-spawn the next occurrence."""
    spawn_next = None
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                task["completed"] = True
                task["completed_at"] = datetime.now().isoformat()
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Task completed: {task['title']} (from {source})")
                if task_audit:
                    task_audit.log_change(str(task_id), "completed", source)

                # Check if recurring — prepare to spawn next occurrence
                recurrence = task.get("recurrence")
                if recurrence and task.get("due_date"):
                    spawn_next = task.copy()

                _notify("task_completed", task_id, source)
                _notify_both_devices(task, "completed", source)
                _notify_watchers(task, "completed")
                break
        else:
            return False

    # Spawn next occurrence outside the lock to avoid deadlock (add_task acquires _lock)
    if spawn_next is not None:
        _spawn_recurring_task(spawn_next)

    return True

def uncomplete_task(task_id: int, source: str = "pc") -> bool:
    """Mark a completed task as incomplete again."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                task["completed"] = False
                task["completed_at"] = None
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Task uncompleted: {task['title']} (from {source})")
                _notify_both_devices(task, "updated", source)
                return True
    return False

def delete_task(task_id: int, source: str = "pc") -> bool:
    """Delete a task."""
    with _lock:
        for i, task in enumerate(_tasks):
            if task["id"] == task_id:
                deleted_task = _tasks.pop(i)
                _save_tasks()
                print(f"[TaskManager] Task deleted: {deleted_task['title']} (from {source})")
                if task_audit:
                    task_audit.log_change(str(task_id), "deleted", source)
                
                _notify("task_deleted", task_id, source)
                _notify_both_devices(deleted_task, "deleted", source)
                return True
    return False

def get_tasks(include_completed: bool = True) -> List[Dict]:
    """Get all tasks."""
    with _lock:
        if include_completed:
            return list(_tasks)
        return [t for t in _tasks if not t["completed"]]

def get_tasks_since(timestamp_iso: str) -> List[Dict]:
    """
    Get tasks modified after the given ISO-8601 timestamp.
    
    This is used for incremental sync — the phone sends its last
    sync timestamp and receives only tasks that changed since then.
    
    Args:
        timestamp_iso: ISO-8601 timestamp string (e.g., "2024-02-25T10:30:00")
    
    Returns:
        List of tasks with last_modified > timestamp_iso
    """
    with _lock:
        try:
            cutoff = datetime.fromisoformat(timestamp_iso)
            result = []
            for task in _tasks:
                task_ts_str = task.get("last_modified") or task.get("created_at", "")
                if task_ts_str:
                    try:
                        task_ts = datetime.fromisoformat(task_ts_str)
                        if task_ts > cutoff:
                            result.append(task)
                    except ValueError:
                        # Malformed timestamp — include it to be safe
                        result.append(task)
            return result
        except ValueError:
            # Invalid cutoff timestamp — return all tasks
            print(f"[TaskManager] Invalid timestamp '{timestamp_iso}' — returning all tasks")
            return list(_tasks)

def get_task(task_id: int) -> Optional[Dict]:
    """Get a single task by ID."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                return task.copy()
    return None

def update_task(task_id: int, updates: Dict[str, Any], source: str = "pc") -> Optional[Dict]:
    """
    Partial update of a task — only provided fields are changed.
    
    Args:
        task_id: The task to update
        updates: Dict of field→value pairs (must be keys from TASK_DEFAULTS)
        source: 'pc' or 'mobile'
    
    Returns:
        Updated task dict, or None if not found
    """
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                old_values = {}
                for key, val in updates.items():
                    if key in TASK_DEFAULTS and key not in ("id", "created_at"):
                        old_values[key] = task.get(key)
                        task[key] = val
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Task updated: {task['title']} fields={list(updates.keys())} (from {source})")
                
                # Feature 16: audit log for changed fields
                if task_audit:
                    for k, v in updates.items():
                        if old_values.get(k) != v:
                            task_audit.log_change(str(task_id), "updated", source,
                                                  field=k, old_value=old_values.get(k), new_value=v)
                
                _notify("task_updated", task_id, source)
                _notify_both_devices(task, "updated", source)
                _notify_watchers(task, "updated", list(updates.keys()))
                return task.copy()
    return None

def toggle_star(task_id: int, source: str = "pc") -> Optional[bool]:
    """Toggle the is_starred flag. Returns new value, or None if not found."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                task["is_starred"] = not task.get("is_starred", False)
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Task {'starred' if task['is_starred'] else 'unstarred'}: {task['title']}")
                _notify_both_devices(task, "updated", source)
                return task["is_starred"]
    return None

def duplicate_task(task_id: int, source: str = "pc") -> Optional[Dict]:
    """Create a copy of a task with a new ID. Returns the new task or None."""
    original = get_task(task_id)
    if not original:
        return None
    # Remove identity fields, they will be regenerated
    original.pop("id", None)
    original.pop("created_at", None)
    original.pop("last_modified", None)
    original.pop("completed", None)
    original.pop("completed_at", None)
    original["title"] = original.get("title", "") + " (copy)"
    return add_task(source=source, **original)

def archive_task(task_id: int, source: str = "pc") -> bool:
    """Move a task to archive (soft-delete)."""
    result = update_task(task_id, {"is_archived": True}, source)
    return result is not None

# ─── RECURRING TASK AUTO-SPAWN ─────────────────────────────────

def _spawn_recurring_task(completed_task: Dict):
    """
    Create the next occurrence of a recurring task after it is completed.
    Copies all fields except completion state, shifts due date forward
    according to the recurrence rule, and adjusts reminders.
    """
    recurrence = completed_task.get("recurrence")
    if not recurrence:
        return

    old_due = completed_task.get("due_date")
    if not old_due:
        return

    new_due = _compute_next_due_date(old_due, recurrence)
    if new_due is None:
        print(f"[TaskManager] Recurrence ended for '{completed_task['title']}'")
        return

    # Check end_date constraint
    end_date = recurrence.get("end_date")
    if end_date and new_due > end_date:
        print(f"[TaskManager] Recurring end reached for '{completed_task['title']}'")
        return

    # Build next task from the completed one
    next_fields = {}
    for key in TASK_DEFAULTS:
        if key in completed_task:
            next_fields[key] = copy.deepcopy(completed_task[key])

    # Remove identity / completion fields
    for k in ("id", "created_at", "last_modified", "completed", "completed_at", "source", "title"):
        next_fields.pop(k, None)

    next_fields["due_date"] = new_due
    next_fields["is_archived"] = False
    next_fields["reschedule_count"] = 0
    next_fields["time_tracked"] = 0
    next_fields["time_sessions"] = []
    next_fields["timer_running"] = False
    next_fields["timer_start"] = None

    # Reset subtask progress
    for sub in next_fields.get("subtasks", []):
        sub["completed"] = False

    # Shift reminders by the same date delta
    next_fields["reminders"] = _shift_reminders(
        completed_task.get("reminders", []), old_due, new_due
    )

    task = add_task(
        title=completed_task.get("title", "Untitled"),
        source="auto_recurrence",
        **next_fields,
    )
    print(f"[TaskManager] ↻ Recurring spawn: '{task['title']}' → due {new_due}")


def _compute_next_due_date(due_date_str: str, rule: Dict) -> Optional[str]:
    """
    Compute the next due date given a recurrence rule.

    Supported rule types: daily, weekly, monthly, yearly, custom.
    The rule dict may contain:
        type       – "daily" | "weekly" | "monthly" | "yearly"
        interval   – int (default 1)
        days_of_week – list of ints (0=Mon..6=Sun) for weekly
        end_date   – "YYYY-MM-DD" or None
    """
    try:
        dt = datetime.strptime(due_date_str, "%Y-%m-%d")
    except ValueError:
        return None

    rec_type = rule.get("type", "daily") if isinstance(rule, dict) else str(rule)
    interval = int(rule.get("interval", 1)) if isinstance(rule, dict) else 1

    if rec_type == "daily":
        dt += timedelta(days=interval)
    elif rec_type == "weekly":
        dt += timedelta(weeks=interval)
    elif rec_type == "monthly":
        month = dt.month - 1 + interval
        year = dt.year + month // 12
        month = month % 12 + 1
        day = min(dt.day, [31,29 if year%4==0 and (year%100!=0 or year%400==0) else 28,31,30,31,30,31,31,30,31,30,31][month-1])
        dt = dt.replace(year=year, month=month, day=day)
    elif rec_type == "yearly":
        try:
            dt = dt.replace(year=dt.year + interval)
        except ValueError:
            # Feb 29 edge case
            dt = dt.replace(year=dt.year + interval, day=28)
    else:
        # Fallback: treat as daily
        dt += timedelta(days=interval)

    return dt.strftime("%Y-%m-%d")


def _shift_reminders(reminders: list, old_due: str, new_due: str) -> list:
    """
    Shift time-based reminders by the same delta as the due date change.
    Non-time reminders (location etc.) are kept as-is.
    """
    if not reminders:
        return []

    try:
        old_dt = datetime.strptime(old_due, "%Y-%m-%d")
        new_dt = datetime.strptime(new_due, "%Y-%m-%d")
        delta = new_dt - old_dt
    except ValueError:
        return copy.deepcopy(reminders)

    shifted = []
    for rem in reminders:
        r = copy.deepcopy(rem)
        if isinstance(r, dict) and r.get("type") == "time" and r.get("value"):
            try:
                rem_dt = datetime.fromisoformat(r["value"])
                r["value"] = (rem_dt + delta).isoformat()
            except (ValueError, TypeError):
                pass
        shifted.append(r)
    return shifted

# ─── TIMER OPERATIONS ──────────────────────────────────────────

def start_timer(task_id: int, source: str = "pc") -> bool:
    """Start the time-tracking stopwatch for a task."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                if task.get("timer_running"):
                    return False  # Already running
                task["timer_running"] = True
                task["timer_start"] = datetime.now().isoformat()
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Timer started: {task['title']}")
                return True
    return False

def stop_timer(task_id: int, source: str = "pc") -> Optional[int]:
    """
    Stop the timer and log the session.
    
    Returns:
        Duration of this session in seconds, or None if not found / not running.
    """
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                if not task.get("timer_running"):
                    return None  # Not running
                start_iso = task.get("timer_start")
                if not start_iso:
                    return None
                now = datetime.now()
                start_dt = datetime.fromisoformat(start_iso)
                duration_secs = int((now - start_dt).total_seconds())
                
                # Log session
                session = {
                    "start": start_iso,
                    "end": now.isoformat(),
                    "duration": duration_secs
                }
                if "time_sessions" not in task:
                    task["time_sessions"] = []
                task["time_sessions"].append(session)
                
                # Accumulate total
                task["time_tracked"] = task.get("time_tracked", 0) + duration_secs
                task["timer_running"] = False
                task["timer_start"] = None
                task["last_modified"] = now.isoformat()
                _save_tasks()
                
                mins = duration_secs // 60
                secs = duration_secs % 60
                print(f"[TaskManager] Timer stopped: {task['title']} — session {mins}m {secs}s")
                return duration_secs
    return None

# ─── SUBTASK OPERATIONS ────────────────────────────────────────

def add_subtask(task_id: int, subtask_title: str, source: str = "pc") -> Optional[Dict]:
    """Add a subtask to a task. Returns the subtask dict or None."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                if "subtasks" not in task:
                    task["subtasks"] = []
                sub_id = int(time.time() * 1000) % 1000000000
                subtask = {"id": sub_id, "title": subtask_title, "completed": False}
                task["subtasks"].append(subtask)
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Subtask added to '{task['title']}': {subtask_title}")
                return subtask
    return None

def complete_subtask(task_id: int, subtask_id: int, source: str = "pc") -> bool:
    """Toggle subtask completion. Returns True if found."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                for sub in task.get("subtasks", []):
                    if sub["id"] == subtask_id:
                        sub["completed"] = not sub["completed"]
                        task["last_modified"] = datetime.now().isoformat()
                        _save_tasks()
                        return True
    return False

def add_comment(task_id: int, text: str, author: str = "PC") -> Optional[Dict]:
    """Add a comment to a task. Returns the comment dict or None."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                if "comments" not in task:
                    task["comments"] = []
                import uuid
                comment = {
                    "id": uuid.uuid4().hex[:8],
                    "author": author,
                    "text": text,
                    "timestamp": int(time.time() * 1000)
                }
                task["comments"].append(comment)
                task["last_modified"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Comment added to '{task['title']}' by {author}")
                if task_audit:
                    task_audit.log_change(str(task_id), "comment_added", author,
                                          new_value={"text": text[:100]})
                return comment
    return None

# ─── QUERY HELPERS ──────────────────────────────────────────────

def get_starred_tasks() -> List[Dict]:
    """Get all starred incomplete tasks."""
    with _lock:
        return [t for t in _tasks if t.get("is_starred") and not t["completed"] and not t.get("is_archived")]

def get_overdue_tasks() -> List[Dict]:
    """Get all overdue incomplete tasks."""
    now = datetime.now()
    today = now.strftime("%Y-%m-%d")
    current_time = now.strftime("%H:%M")
    with _lock:
        result = []
        for t in _tasks:
            if t["completed"] or t.get("is_archived"):
                continue
            dd = t.get("due_date")
            if not dd:
                continue
            if dd < today:
                result.append(t)
            elif dd == today and t.get("due_time") and t["due_time"] < current_time:
                result.append(t)
        return result

def get_tasks_by_category(category: str) -> List[Dict]:
    """Get tasks filtered by category."""
    with _lock:
        return [t for t in _tasks if t.get("category") == category and not t.get("is_archived")]

def get_blocked_tasks() -> List[Dict]:
    """Get tasks that have unfinished dependencies."""
    with _lock:
        completed_ids = {t["id"] for t in _tasks if t["completed"]}
        result = []
        for t in _tasks:
            if t["completed"] or t.get("is_archived"):
                continue
            deps = t.get("dependencies", [])
            if deps and not all(d in completed_ids for d in deps):
                result.append(t)
        return result

def clear_completed() -> int:
    """Remove all completed tasks. Returns count of removed tasks."""
    with _lock:
        initial_count = len(_tasks)
        _tasks[:] = [t for t in _tasks if not t["completed"]]
        removed = initial_count - len(_tasks)
        _save_tasks()
        print(f"[TaskManager] Cleared {removed} completed tasks")
        
        _notify("tasks_synced", list(_tasks))
        return removed

# ─── NOTIFICATION SYSTEM ────────────────────────────────────────

def _notify_both_devices(task: Dict, action: str, source: str):
    """Notify both PC and Mobile about a task change."""
    
    # Import here to avoid circular imports
    try:
        import reverse_commands
    except ImportError:
        reverse_commands = None
    
    title = task.get("title", "Unknown Task")
    
    # Build notification message
    if action == "added":
        pc_msg = f"📋 New Task: {title}"
        mobile_title = "New Task Added"
        mobile_body = title
    elif action == "completed":
        pc_msg = f"✅ Task Completed: {title}"
        mobile_title = "Task Completed"
        mobile_body = f"✅ {title}"
    elif action == "deleted":
        pc_msg = f"🗑 Task Deleted: {title}"
        mobile_title = "Task Deleted"
        mobile_body = title
    elif action == "updated":
        pc_msg = f"✏️ Task Updated: {title}"
        mobile_title = "Task Updated"
        mobile_body = title
    else:
        return
    
    # PC Desktop Notification (always show)
    threading.Thread(target=_show_pc_notification, args=(pc_msg, source), daemon=True).start()
    
    # Mobile Notification (if connected)
    if reverse_commands and reverse_commands.is_connected():
        try:
            # Send task-specific notification command
            task_id = task.get("id", 0)
            if action == "added":
                reverse_commands.notify_task_added(title, task_id)
                reverse_commands.vibrate(200)
            elif action == "completed":
                reverse_commands.notify_task_completed(title, task_id)
            elif action == "deleted":
                reverse_commands.notify_task_deleted(task_id)
            elif action == "updated":
                # Just sync — no special notification handler needed
                pass
            # Send full task list sync so phone's list stays up-to-date
            sync_tasks_to_mobile()
        except Exception as e:
            print(f"[TaskManager] Mobile notify error: {e}")

def _show_pc_notification(message: str, source: str):
    """Show a Windows desktop notification."""
    try:
        import subprocess
        import os
        
        # Remove emoji for compatibility
        clean_message = message.encode('ascii', 'ignore').decode('ascii').strip()
        if not clean_message:
            clean_message = "Task updated"
        
        # Use PowerShell's BurntToast module or native toast
        ps_script = f'''
Add-Type -AssemblyName System.Windows.Forms
$balloon = New-Object System.Windows.Forms.NotifyIcon
$balloon.Icon = [System.Drawing.SystemIcons]::Information
$balloon.BalloonTipTitle = "Mobile Controller"
$balloon.BalloonTipText = "{clean_message}"
$balloon.Visible = $true
$balloon.ShowBalloonTip(5000)
Start-Sleep -Seconds 3
$balloon.Dispose()
'''
        # Run PowerShell hidden
        startupinfo = subprocess.STARTUPINFO()
        startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startupinfo.wShowWindow = 0  # SW_HIDE
        
        subprocess.Popen(
            ["powershell", "-ExecutionPolicy", "Bypass", "-Command", ps_script],
            startupinfo=startupinfo,
            creationflags=subprocess.CREATE_NO_WINDOW
        )
        print(f"[Notification] {message}")
        
    except Exception as e:
        print(f"[Desktop Notification] {message} (toast failed: {e})")

# ─── SYNC OPERATIONS ────────────────────────────────────────────

def get_tasks_json() -> str:
    """Get all tasks as JSON string for sync."""
    with _lock:
        return json.dumps(_tasks, ensure_ascii=False)

def sync_tasks_to_mobile():
    """Send the full task list to mobile."""
    try:
        import reverse_commands
        if reverse_commands.is_connected():
            tasks_json = get_tasks_json()
            reverse_commands.send_to_phone(f"TASKS_SYNC:{tasks_json}")
            print("[TaskManager] Tasks synced to mobile")
            return True
    except Exception as e:
        print(f"[TaskManager] Sync to mobile error: {e}")
    return False

def handle_mobile_command(command: str) -> Optional[str]:
    """
    Handle task-related commands from mobile.
    
    Commands:
        - TASK_ADD:title[:priority[:due_date[:due_time]]]
        - TASK_ADD_JSON:{json}          — full task creation with all fields
        - TASK_UPDATE:{json}            — partial update: {"id":123,"title":"new",...}
        - TASK_COMPLETE:id
        - TASK_DELETE:id
        - TASK_STAR:id                  — toggle star
        - TASK_DUPLICATE:id
        - TASK_ARCHIVE:id
        - TASK_TIMER_START:id
        - TASK_TIMER_STOP:id
        - TASK_ADD_SUBTASK:task_id:title
        - TASK_COMPLETE_SUBTASK:task_id:subtask_id
        - TASK_LIST
        - TASK_SYNC
    
    Returns:
        Response string or None
    """
    if command.startswith("TASK_ADD_JSON:"):
        try:
            payload = json.loads(command[14:])
            title = payload.pop("title", "Untitled")
            source = payload.pop("source", "mobile")
            priority = payload.pop("priority", "normal")
            due_date = payload.pop("due_date", None)
            due_time = payload.pop("due_time", None)
            task = add_task(title, source=source, priority=priority,
                            due_date=due_date, due_time=due_time, **payload)
            return f"TASK_ADDED:{task['id']}:{task['title']}"
        except Exception as e:
            return f"TASK_ERROR:INVALID_JSON:{e}"
    
    elif command.startswith("TASK_ADD:"):
        parts = command[9:].split(":", 3)
        title = parts[0]
        priority = parts[1] if len(parts) > 1 else "normal"
        due_date = parts[2] if len(parts) > 2 else None
        due_time = parts[3] if len(parts) > 3 else None
        
        task = add_task(title, source="mobile", priority=priority, due_date=due_date, due_time=due_time)
        return f"TASK_ADDED:{task['id']}:{task['title']}"
    
    elif command.startswith("TASK_UPDATE:"):
        try:
            payload = json.loads(command[12:])
            task_id = int(payload.pop("id"))
            result = update_task(task_id, payload, source="mobile")
            if result:
                return f"TASK_UPDATED:{task_id}"
            return "TASK_ERROR:NOT_FOUND"
        except Exception as e:
            return f"TASK_ERROR:INVALID_JSON:{e}"
    
    elif command.startswith("TASK_COMPLETE:"):
        try:
            task_id = int(command.split(":")[1])
            if complete_task(task_id, source="mobile"):
                return f"TASK_COMPLETED:{task_id}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_DELETE:"):
        try:
            task_id = int(command.split(":")[1])
            if delete_task(task_id, source="mobile"):
                return f"TASK_DELETED:{task_id}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_STAR:"):
        try:
            task_id = int(command.split(":")[1])
            result = toggle_star(task_id, source="mobile")
            if result is not None:
                return f"TASK_STARRED:{task_id}:{'true' if result else 'false'}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_DUPLICATE:"):
        try:
            task_id = int(command.split(":")[1])
            new_task = duplicate_task(task_id, source="mobile")
            if new_task:
                return f"TASK_DUPLICATED:{new_task['id']}:{new_task['title']}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_ARCHIVE:"):
        try:
            task_id = int(command.split(":")[1])
            if archive_task(task_id, source="mobile"):
                return f"TASK_ARCHIVED:{task_id}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_TIMER_START:"):
        try:
            task_id = int(command.split(":")[1])
            if start_timer(task_id, source="mobile"):
                return f"TASK_TIMER_STARTED:{task_id}"
            return "TASK_ERROR:TIMER_ALREADY_RUNNING"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_TIMER_STOP:"):
        try:
            task_id = int(command.split(":")[1])
            duration = stop_timer(task_id, source="mobile")
            if duration is not None:
                return f"TASK_TIMER_STOPPED:{task_id}:{duration}"
            return "TASK_ERROR:TIMER_NOT_RUNNING"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"
    
    elif command.startswith("TASK_ADD_SUBTASK:"):
        parts = command.split(":", 2)
        if len(parts) >= 3:
            try:
                task_id = int(parts[1])
                sub = add_subtask(task_id, parts[2], source="mobile")
                if sub:
                    return f"TASK_SUBTASK_ADDED:{task_id}:{sub['id']}:{sub['title']}"
                return "TASK_ERROR:NOT_FOUND"
            except ValueError:
                return "TASK_ERROR:INVALID_ID"
        return "TASK_ERROR:MISSING_PARAMS"
    
    elif command.startswith("TASK_COMPLETE_SUBTASK:"):
        parts = command.split(":")
        if len(parts) >= 3:
            try:
                task_id = int(parts[1])
                sub_id = int(parts[2])
                if complete_subtask(task_id, sub_id, source="mobile"):
                    return f"TASK_SUBTASK_TOGGLED:{task_id}:{sub_id}"
                return "TASK_ERROR:NOT_FOUND"
            except ValueError:
                return "TASK_ERROR:INVALID_ID"
        return "TASK_ERROR:MISSING_PARAMS"
    
    elif command == "TASK_LIST":
        tasks_json = get_tasks_json()
        return f"TASKS:{tasks_json}"
    
    elif command.startswith("TASK_UNCOMPLETE:"):
        try:
            task_id = int(command.split(":")[1])
            if uncomplete_task(task_id, source="mobile"):
                return f"TASK_UNCOMPLETED:{task_id}"
            return "TASK_ERROR:NOT_FOUND"
        except ValueError:
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_BULK_COMPLETE:"):
        # TASK_BULK_COMPLETE:id1,id2,id3
        try:
            ids = [int(x) for x in command.split(":", 1)[1].split(",") if x.strip()]
            count = 0
            for tid in ids:
                if complete_task(tid, source="mobile"):
                    count += 1
            return f"TASK_BULK_COMPLETED:{count}"
        except ValueError:
            return "TASK_ERROR:INVALID_IDS"

    elif command.startswith("TASK_BULK_DELETE:"):
        # TASK_BULK_DELETE:id1,id2,id3
        try:
            ids = [int(x) for x in command.split(":", 1)[1].split(",") if x.strip()]
            count = 0
            for tid in ids:
                if delete_task(tid, source="mobile"):
                    count += 1
            return f"TASK_BULK_DELETED:{count}"
        except ValueError:
            return "TASK_ERROR:INVALID_IDS"

    elif command.startswith("TASK_BULK_UPDATE:"):
        # TASK_BULK_UPDATE:{"ids":[1,2,3],"field":"priority","value":"high"}
        try:
            payload = json.loads(command[16:])
            ids = payload.get("ids", [])
            field = payload.get("field")
            value = payload.get("value")
            if not field or field not in TASK_DEFAULTS:
                return "TASK_ERROR:INVALID_FIELD"
            count = 0
            for tid in ids:
                if update_task(int(tid), {field: value}, source="mobile"):
                    count += 1
            return f"TASK_BULK_UPDATED:{count}"
        except (ValueError, json.JSONDecodeError) as e:
            return f"TASK_ERROR:INVALID_PAYLOAD:{e}"

    elif command == "TASK_SYNC":
        sync_tasks_to_mobile()
        return "TASK_SYNC_SENT"

    elif command.startswith("TASK_COMMENT:"):
        # TASK_COMMENT:task_id:comment_text
        try:
            parts = command.split(":", 2)
            task_id = int(parts[1])
            text = parts[2] if len(parts) > 2 else ""
            if text:
                comment = add_comment(task_id, text, author="Mobile")
                if comment:
                    return f"TASK_COMMENT_ADDED:{task_id}"
            return "TASK_ERROR:INVALID_COMMENT"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_COMMENT"

    elif command.startswith("TASK_SYNC_SINCE:"):
        # Incremental sync: TASK_SYNC_SINCE:<iso_timestamp>
        try:
            timestamp_iso = command.split(":", 1)[1].strip()
            tasks = get_tasks_since(timestamp_iso)
            return f"TASKS:{json.dumps(tasks, ensure_ascii=False)}"
        except Exception as e:
            return f"TASK_ERROR:SYNC_SINCE:{e}"

    elif command == "TASK_AUDIT_LOG":
        # Return recent audit log entries
        try:
            import audit_log
            entries = audit_log.get_log(limit=50)
            return f"TASK_AUDIT:{json.dumps(entries, ensure_ascii=False)}"
        except Exception:
            return "TASK_AUDIT:[]"

    elif command.startswith("TASK_AUDIT_HISTORY:"):
        # Return audit log for a specific task: TASK_AUDIT_HISTORY:task_id
        try:
            import audit_log
            task_id = int(command.split(":")[1])
            entries = audit_log.get_task_history(task_id)
            return f"TASK_AUDIT:{json.dumps(entries, ensure_ascii=False)}"
        except Exception:
            return "TASK_AUDIT:[]"

    elif command.startswith("TASK_SET_NEXT_ACTION:"):
        # TASK_SET_NEXT_ACTION:task_id:true/false
        try:
            parts = command.split(":")
            task_id = int(parts[1])
            val = parts[2].lower() == "true" if len(parts) > 2 else True
            result = update_task(task_id, {"isNextAction": val}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_SET_MIT:"):
        # TASK_SET_MIT:task_id:true/false
        try:
            parts = command.split(":")
            task_id = int(parts[1])
            val = parts[2].lower() == "true" if len(parts) > 2 else True
            result = update_task(task_id, {"isMIT": val}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_SET_CONTEXT:"):
        # TASK_SET_CONTEXT:task_id:@home
        try:
            parts = command.split(":", 2)
            task_id = int(parts[1])
            ctx = parts[2] if len(parts) > 2 and parts[2] else None
            result = update_task(task_id, {"contextTag": ctx}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_SET_WAITING:"):
        # TASK_SET_WAITING:task_id:person_name
        try:
            parts = command.split(":", 2)
            task_id = int(parts[1])
            waiting = parts[2] if len(parts) > 2 and parts[2] else None
            updates = {"waitingFor": waiting}
            if waiting:
                updates["status"] = "waiting"
            result = update_task(task_id, updates, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_SET_PRIVATE:"):
        # TASK_SET_PRIVATE:task_id:true/false
        try:
            parts = command.split(":")
            task_id = int(parts[1])
            val = parts[2].lower() == "true" if len(parts) > 2 else True
            result = update_task(task_id, {"isPrivate": val}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_SET_PROJECT:"):
        # TASK_SET_PROJECT:task_id:project_id
        try:
            parts = command.split(":", 2)
            task_id = int(parts[1])
            proj_id = parts[2] if len(parts) > 2 and parts[2] else None
            result = update_task(task_id, {"projectId": proj_id}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command.startswith("TASK_ASSIGN:"):
        # TASK_ASSIGN:task_id:Mobile|PC|None
        try:
            parts = command.split(":", 2)
            task_id = int(parts[1])
            assignee = parts[2] if len(parts) > 2 and parts[2] and parts[2] != "None" else None
            result = update_task(task_id, {"assignedTo": assignee}, source="mobile")
            return f"TASK_UPDATED:{task_id}" if result else "TASK_ERROR:NOT_FOUND"
        except (ValueError, IndexError):
            return "TASK_ERROR:INVALID_ID"

    elif command == "TASK_GET_TEMPLATES":
        try:
            import templates
            return f"TASK_TEMPLATES:{json.dumps(templates.get_templates(), ensure_ascii=False)}"
        except Exception as e:
            return f"TASK_ERROR:TEMPLATES:{e}"

    elif command.startswith("TASK_SAVE_TEMPLATE:"):
        try:
            import templates
            payload = json.loads(command[19:])
            result = templates.save_template(payload)
            return f"TASK_TEMPLATE_SAVED:{result.get('id', '')}"
        except Exception as e:
            return f"TASK_ERROR:TEMPLATE_SAVE:{e}"

    elif command.startswith("TASK_DELETE_TEMPLATE:"):
        try:
            import templates
            template_id = command.split(":", 1)[1]
            if templates.delete_template(template_id):
                return f"TASK_TEMPLATE_DELETED:{template_id}"
            return "TASK_ERROR:TEMPLATE_NOT_FOUND"
        except Exception as e:
            return f"TASK_ERROR:TEMPLATE_DELETE:{e}"

    # ─── Feature 17: Project Commands ──────────────────────────────

    elif command == "TASK_GET_PROJECTS":
        try:
            import projects
            all_projects = projects.get_projects()
            return "TASK_PROJECTS:" + json.dumps(all_projects)
        except Exception as e:
            return f"TASK_ERROR:PROJECTS_GET:{e}"

    elif command.startswith("TASK_SAVE_PROJECT:"):
        try:
            import projects
            data = json.loads(command.split(":", 1)[1])
            result = projects.save_project(data)
            return "TASK_PROJECT_SAVED:" + json.dumps(result)
        except Exception as e:
            return f"TASK_ERROR:PROJECT_SAVE:{e}"

    elif command.startswith("TASK_DELETE_PROJECT:"):
        try:
            import projects
            project_id = command.split(":", 1)[1]
            if projects.delete_project(project_id):
                return f"TASK_PROJECT_DELETED:{project_id}"
            return "TASK_ERROR:PROJECT_NOT_FOUND"
        except Exception as e:
            return f"TASK_ERROR:PROJECT_DELETE:{e}"

    return None

# ─── UTILITY ────────────────────────────────────────────────────

def get_pending_count() -> int:
    """Get count of non-completed tasks."""
    with _lock:
        return sum(1 for t in _tasks if not t["completed"])

def get_high_priority_tasks() -> List[Dict]:
    """Get all high-priority incomplete tasks."""
    with _lock:
        return [t for t in _tasks if t.get("priority") == "high" and not t["completed"]]

def get_tasks_due_today() -> List[Dict]:
    """Get tasks due today."""
    today = datetime.now().strftime("%Y-%m-%d")
    with _lock:
        return [t for t in _tasks if t.get("due_date") == today and not t["completed"]]

# ─── REMINDER SCHEDULER ────────────────────────────────────────

_reminded_ids: set = set()
_reminder_running = False

def _reminder_loop():
    """Background loop that checks for due tasks every 30 seconds."""
    global _reminder_running
    _reminder_running = True
    print("[TaskManager] Reminder scheduler started")

    while _reminder_running:
        try:
            _check_due_tasks()
        except Exception as e:
            print(f"[TaskManager] Reminder loop error: {e}")
        time.sleep(30)

def _check_due_tasks():
    """Check if any tasks are due now and fire notifications."""
    now = datetime.now()
    today = now.strftime("%Y-%m-%d")
    current_time = now.strftime("%H:%M")

    with _lock:
        for task in _tasks:
            if task.get("completed"):
                continue
            if task.get("id") in _reminded_ids:
                continue

            due_date = task.get("due_date")
            due_time = task.get("due_time")

            if not due_date or not due_time:
                continue

            # Only check tasks due today
            if due_date != today:
                continue

            # Check if task is due (within a 2-minute window to avoid missing)
            try:
                due_dt = datetime.strptime(f"{due_date} {due_time}", "%Y-%m-%d %H:%M")
                diff_seconds = (now - due_dt).total_seconds()
                # Fire if due time has passed but within 2 minutes
                if 0 <= diff_seconds <= 120:
                    _reminded_ids.add(task["id"])
                    title = task.get("title", "Task Due")
                    priority = task.get("priority", "normal")
                    threading.Thread(
                        target=_fire_reminder, 
                        args=(task["id"], title, priority, due_time),
                        daemon=True
                    ).start()
            except ValueError:
                continue

def _fire_reminder(task_id: int, title: str, priority: str, due_time: str):
    """Send reminder notification to both PC and phone."""
    print(f"[TaskManager] 🔔 REMINDER: '{title}' is due at {due_time}!")

    # PC desktop notification
    _show_pc_notification(f"🔔 Task Due: {title} (at {due_time})", "reminder")

    # Mobile notification via reverse_commands
    try:
        import reverse_commands
        if reverse_commands.is_connected():
            reverse_commands.send_to_phone(f"TASK_REMINDER:{task_id}:{title}:{due_time}")
            reverse_commands.vibrate(500)
            print(f"[TaskManager] Reminder sent to phone for: {title}")
        else:
            print(f"[TaskManager] Phone not connected, reminder only on PC for: {title}")
    except Exception as e:
        print(f"[TaskManager] Phone reminder error: {e}")

def start_reminder_scheduler():
    """Start the background reminder checker thread."""
    global _reminder_running
    if _reminder_running:
        return
    t = threading.Thread(target=_reminder_loop, daemon=True, name="TaskReminderScheduler")
    t.start()

def stop_reminder_scheduler():
    """Stop the reminder scheduler."""
    global _reminder_running
    _reminder_running = False
    print("[TaskManager] Reminder scheduler stopped")

# Auto-start the reminder scheduler on import
start_reminder_scheduler()
