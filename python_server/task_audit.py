"""
Feature 16: Server-side audit log for task changes.
Writes JSON-line entries to task_audit.log for every task CRUD operation.
"""
import os
import json
import time
import logging
from datetime import datetime

AUDIT_FILE = os.path.join(os.path.dirname(__file__), "task_audit.log")

# Dedicated logger (separate from main server logger)
audit_logger = logging.getLogger("task_audit")
_handler = logging.FileHandler(AUDIT_FILE, encoding="utf-8")
_handler.setFormatter(logging.Formatter("%(message)s"))
audit_logger.addHandler(_handler)
audit_logger.setLevel(logging.INFO)
audit_logger.propagate = False


def log_change(task_id: str, event: str, source: str,
               old_value=None, new_value=None, field: str = None):
    """Write a single audit entry as a JSON log line.
    
    Args:
        task_id: The task's unique ID.
        event: One of "created", "updated", "completed", "deleted", "comment_added".
        source: "mobile" or "pc".
        old_value: Previous value (for updates).
        new_value: New value (for creates/updates).
        field: Changed field name, or None for full creates/deletes.
    """
    entry = {
        "ts": int(time.time() * 1000),
        "dt": datetime.utcnow().isoformat() + "Z",
        "task_id": task_id,
        "event": event,
        "source": source,
        "field": field,
        "old": old_value,
        "new": new_value,
    }
    try:
        audit_logger.info(json.dumps(entry, ensure_ascii=False))
    except Exception:
        pass


def get_log_for_task(task_id: str, limit: int = 50) -> list:
    """Read the audit log and return the last `limit` entries for a given task_id."""
    if not os.path.exists(AUDIT_FILE):
        return []
    entries = []
    try:
        with open(AUDIT_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                    if entry.get("task_id") == task_id:
                        entries.append(entry)
                except json.JSONDecodeError:
                    continue
    except OSError:
        return []
    return entries[-limit:]


def get_recent_log(limit: int = 100) -> list:
    """Return the last `limit` entries across all tasks."""
    if not os.path.exists(AUDIT_FILE):
        return []
    entries = []
    try:
        with open(AUDIT_FILE, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    entries.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except OSError:
        return []
    return entries[-limit:]
