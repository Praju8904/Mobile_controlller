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
from datetime import datetime
from typing import List, Dict, Optional, Callable

# File for persistent task storage
TASKS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tasks.json")

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
        else:
            _tasks = []
    except Exception as e:
        print(f"[TaskManager] Error loading tasks: {e}")
        _tasks = []

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

# ─── TASK OPERATIONS ────────────────────────────────────────────

def add_task(title: str, source: str = "pc", priority: str = "normal", 
             due_date: Optional[str] = None, due_time: Optional[str] = None) -> Dict:
    """
    Add a new task.
    
    Args:
        title: Task description
        source: 'pc' or 'mobile' - where the task was created
        priority: 'low', 'normal', 'high'
        due_date: Optional due date string (YYYY-MM-DD)
        due_time: Optional due time string (HH:MM)
    
    Returns:
        The created task dictionary
    """
    with _lock:
        # Generate unique ID
        task_id = int(time.time() * 1000) % 1000000000
        
        task = {
            "id": task_id,
            "title": title,
            "completed": False,
            "priority": priority,
            "due_date": due_date,
            "due_time": due_time,
            "created_at": datetime.now().isoformat(),
            "source": source
        }
        
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
    
    # Notify both devices
    _notify_both_devices(task, "added", source)
    
    return task

def complete_task(task_id: int, source: str = "pc") -> bool:
    """Mark a task as completed."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                task["completed"] = True
                task["completed_at"] = datetime.now().isoformat()
                _save_tasks()
                print(f"[TaskManager] Task completed: {task['title']} (from {source})")
                
                _notify("task_completed", task_id, source)
                _notify_both_devices(task, "completed", source)
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

def get_task(task_id: int) -> Optional[Dict]:
    """Get a single task by ID."""
    with _lock:
        for task in _tasks:
            if task["id"] == task_id:
                return task.copy()
    return None

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
    else:
        return
    
    # PC Desktop Notification (always show)
    threading.Thread(target=_show_pc_notification, args=(pc_msg, source), daemon=True).start()
    
    # Mobile Notification (if connected)
    if reverse_commands and reverse_commands.is_connected():
        try:
            reverse_commands.show_notification(mobile_title, mobile_body)
            # Also vibrate briefly for task notifications
            if action == "added":
                reverse_commands.vibrate(200)
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
        - TASK_COMPLETE:id
        - TASK_DELETE:id
        - TASK_LIST
        - TASK_SYNC
    
    Returns:
        Response string or None
    """
    if command.startswith("TASK_ADD:"):
        parts = command[9:].split(":", 3)
        title = parts[0]
        priority = parts[1] if len(parts) > 1 else "normal"
        due_date = parts[2] if len(parts) > 2 else None
        due_time = parts[3] if len(parts) > 3 else None
        
        task = add_task(title, source="mobile", priority=priority, due_date=due_date, due_time=due_time)
        return f"TASK_ADDED:{task['id']}:{task['title']}"
    
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
    
    elif command == "TASK_LIST":
        tasks_json = get_tasks_json()
        return f"TASKS:{tasks_json}"
    
    elif command == "TASK_SYNC":
        sync_tasks_to_mobile()
        return "TASK_SYNC_SENT"
    
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
