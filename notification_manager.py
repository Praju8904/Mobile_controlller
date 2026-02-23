"""
Notification Manager — Handles mirrored phone notifications on the PC.

Features:
  - Receives JSON notification data from phone
  - Stores notification history (last 100)
  - Shows Windows toast notifications
  - Callback system for GUI updates
  - Dismiss notifications back to phone
"""

import json
import threading
import time
from datetime import datetime

try:
    from win10toast import ToastNotifier
    toaster = ToastNotifier()
    HAS_TOAST = True
except ImportError:
    HAS_TOAST = False
    print("[Info] win10toast not installed — Windows toasts disabled")

import reverse_commands

# ─── NOTIFICATION STORE ─────────────────────────────────────────

_notifications = []        # List of notification dicts
_max_history = 100         # Keep last N notifications
_callbacks = []            # List of (event, callback) tuples
_lock = threading.Lock()

# ─── APP NAME MAPPING ───────────────────────────────────────────

APP_NAMES = {
    "com.whatsapp": ("WhatsApp", "💬"),
    "com.whatsapp.w4b": ("WhatsApp Business", "💼"),
    "com.instagram.android": ("Instagram", "📸"),
    "com.google.android.gm": ("Gmail", "📧"),
    "com.google.android.apps.messaging": ("Messages", "💬"),
    "com.google.android.apps.maps": ("Google Maps", "🗺"),
    "com.twitter.android": ("X (Twitter)", "🐦"),
    "com.facebook.orca": ("Messenger", "💬"),
    "com.facebook.katana": ("Facebook", "📘"),
    "com.snapchat.android": ("Snapchat", "👻"),
    "com.spotify.music": ("Spotify", "🎵"),
    "com.amazon.mShop.android.shopping": ("Amazon", "📦"),
    "org.telegram.messenger": ("Telegram", "✈️"),
    "com.discord": ("Discord", "🎮"),
    "com.linkedin.android": ("LinkedIn", "💼"),
    "com.google.android.youtube": ("YouTube", "▶️"),
    "com.microsoft.teams": ("Teams", "👥"),
    "com.slack": ("Slack", "💼"),
    "com.google.android.dialer": ("Phone", "📞"),
    "com.samsung.android.messaging": ("Samsung Messages", "💬"),
    "com.samsung.android.incallui": ("Phone", "📞"),
}

def get_app_info(package_name):
    """Get readable app name and emoji for a package."""
    if package_name in APP_NAMES:
        return APP_NAMES[package_name]
    # Try to make a readable name from the package
    parts = package_name.split(".")
    name = parts[-1].replace("android", "").replace("app", "").strip().title()
    if not name:
        name = package_name
    return (name, "📱")


# ─── CALLBACK SYSTEM ────────────────────────────────────────────

def on_notification(callback):
    """Register callback for new notifications. callback(notif_dict)"""
    _callbacks.append(("new", callback))

def on_dismissed(callback):
    """Register callback for dismissed notifications. callback(key)"""
    _callbacks.append(("dismissed", callback))

def _notify(event, data):
    for name, cb in _callbacks:
        if name == event:
            try:
                cb(data)
            except Exception as e:
                print(f"[Notif] Callback error: {e}")


# ─── CORE FUNCTIONS ─────────────────────────────────────────────

def handle_notification(json_str):
    """
    Process an incoming notification from the phone.
    Expected JSON: {"key": "...", "title": "...", "body": "...", "pkg": "...", "time": 123}
    """
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError:
        # Fallback: try legacy pipe-delimited format
        parts = json_str.split("|")
        if len(parts) >= 3:
            data = {
                "key": f"legacy_{int(time.time())}",
                "title": parts[0],
                "body": parts[1],
                "pkg": parts[2],
                "time": int(time.time() * 1000)
            }
        else:
            print(f"[Notif] Invalid notification data: {json_str[:50]}")
            return

    key = data.get("key", "")
    title = data.get("title", "")
    body = data.get("body", "").replace("\\n", "\n")
    pkg = data.get("pkg", "unknown")
    timestamp = data.get("time", int(time.time() * 1000))

    app_name, app_icon = get_app_info(pkg)

    notif = {
        "key": key,
        "title": title,
        "body": body,
        "pkg": pkg,
        "app_name": app_name,
        "app_icon": app_icon,
        "time": timestamp,
        "time_str": datetime.fromtimestamp(timestamp / 1000).strftime("%H:%M:%S"),
        "dismissed": False,
    }

    with _lock:
        _notifications.insert(0, notif)  # Newest first
        # Trim history
        while len(_notifications) > _max_history:
            _notifications.pop()

    print(f"[🔔 Notification] {app_icon} {app_name}: {title}")

    # Show Windows toast
    if HAS_TOAST:
        _show_toast(app_name, app_icon, title, body)

    # Notify GUI
    _notify("new", notif)


def handle_dismissed(key):
    """Phone reports a notification was dismissed."""
    with _lock:
        for n in _notifications:
            if n["key"] == key:
                n["dismissed"] = True
                break
    _notify("dismissed", key)


def dismiss_from_pc(key):
    """User clicked dismiss on PC — tell phone to dismiss it too."""
    with _lock:
        for n in _notifications:
            if n["key"] == key:
                n["dismissed"] = True
                break
    reverse_commands.send_to_phone(f"NOTIF_DISMISS:{key}")
    print(f"[Notif] Dismiss sent to phone: {key[:30]}...")
    _notify("dismissed", key)


def clear_all():
    """Clear notification history."""
    with _lock:
        _notifications.clear()
    print("[Notif] All notifications cleared")


def get_notifications():
    """Get all stored notifications (newest first)."""
    with _lock:
        return list(_notifications)


def get_active_count():
    """Count of non-dismissed notifications."""
    with _lock:
        return sum(1 for n in _notifications if not n.get("dismissed"))


# ─── WINDOWS TOAST ──────────────────────────────────────────────

def _show_toast(app_name, app_icon, title, body):
    """Show a Windows toast notification (non-blocking)."""
    def _do():
        try:
            toast_title = f"{app_icon} {app_name}: {title}"
            clean_body = body.replace("\\n", "\n")
            toaster.show_toast(
                toast_title,
                clean_body,
                duration=5,
                threaded=True
            )
        except Exception as e:
            print(f"[Notif] Toast error: {e}")

    threading.Thread(target=_do, daemon=True).start()


# ─── LEGACY COMPAT ──────────────────────────────────────────────

def show_notification(title, message, app_name):
    """Legacy function for backwards compatibility."""
    handle_notification(json.dumps({
        "key": f"legacy_{int(time.time())}",
        "title": title,
        "body": message,
        "pkg": app_name,
        "time": int(time.time() * 1000)
    }))