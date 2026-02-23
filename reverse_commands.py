"""
Reverse Commands — PC to Phone communication.
Sends UDP commands FROM the laptop TO the connected Android phone.
The phone app listens on port 6000 for these commands.
"""

import socket
import json
import time
import threading
import notes_module

# The phone's IP (set when phone connects)
_phone_ip = None
_phone_port = 6000
_connected = False
_callbacks = []  # List of (event_name, callback) for GUI updates

def set_phone_ip(ip):
    global _phone_ip, _connected
    _phone_ip = ip
    _connected = True
    _notify("connection", {"ip": ip, "connected": True})

def get_phone_ip():
    return _phone_ip

def is_connected():
    return _connected and _phone_ip is not None

def disconnect():
    global _connected, _phone_ip
    _connected = False
    _phone_ip = None
    _notify("connection", {"ip": None, "connected": False})

def on_event(event_name, callback):
    """Register a callback for GUI events."""
    _callbacks.append((event_name, callback))

def _notify(event_name, data):
    for name, cb in _callbacks:
        if name == event_name:
            try:
                cb(data)
            except Exception as e:
                print(f"Callback error: {e}")

def send_to_phone(command):
    # """Send a UDP command to the connected phone."""
    # if not _phone_ip:
    #     print("[Reverse] No phone connected.")
    #     return False
    # try:
    #     sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    #     sock.sendto(command.encode('utf-8'), (_phone_ip, _phone_port))
    #     sock.close()
    #     print(f"[PC→Phone] {command}")
    #     return True
    # except Exception as e:
    #     print(f"[Reverse] Send error: {e}")
    #     return False
    try:
        import reverse_commands
        if reverse_commands.is_connected():
            # If the command is already formatted, send it directly
            if command.startswith("CHAT_MSG:"):
                    reverse_commands.send_to_phone(command)
            else:
                    reverse_commands.send_chat_message(command) 
    except Exception:
        pass

# ─── HIGH-LEVEL PHONE CONTROL COMMANDS ─────────────────────────

def vibrate(duration_ms=500):
    """Make the phone vibrate."""
    return send_to_phone(f"VIBRATE:{duration_ms}")

def vibrate_pattern(pattern="0,200,100,200,100,400"):
    """Vibrate with pattern (off,on,off,on,...) in ms."""
    return send_to_phone(f"VIBRATE_PATTERN:{pattern}")

def ring_phone(duration_s=5):
    """Play a ringtone/alarm sound on the phone."""
    return send_to_phone(f"RING:{duration_s}")

def stop_ring():
    """Stop ringing."""
    return send_to_phone("RING_STOP")

def flash_screen(color="FF0000", duration_ms=1000):
    """Flash the phone screen a color."""
    return send_to_phone(f"FLASH:{color}:{duration_ms}")

def show_toast(message):
    """Show a toast message on the phone."""
    return send_to_phone(f"TOAST:{message}")

def show_notification(title, body):
    """Push a notification to the phone."""
    return send_to_phone(f"NOTIFY:{title}|{body}")

def set_phone_brightness(level):
    """Set phone screen brightness (0-255)."""
    return send_to_phone(f"BRIGHTNESS:{level}")

def set_phone_volume(stream, level):
    """Set phone volume. stream: music/ring/alarm/notification. level: 0-15."""
    return send_to_phone(f"VOLUME:{stream}:{level}")

def send_clipboard_to_phone(text):
    """Sync text from PC clipboard to phone clipboard."""
    return send_to_phone(f"CLIPBOARD:{text}")

def open_url_on_phone(url):
    """Open a URL in the phone's browser."""
    return send_to_phone(f"OPEN_URL:{url}")

def take_phone_screenshot():
    """Request a screenshot from the phone."""
    return send_to_phone("TAKE_SCREENSHOT")

def toggle_flashlight(on=True):
    """Toggle the phone's flashlight."""
    return send_to_phone(f"FLASHLIGHT:{'ON' if on else 'OFF'}")

def get_phone_info():
    """Request battery, storage, etc. from phone."""
    return send_to_phone("GET_INFO")

def send_file_to_phone(filepath):
    """Initiate a file transfer from PC to phone."""
    return send_to_phone(f"RECEIVE_FILE:{filepath}")

def lock_phone():
    """Lock the phone screen."""
    return send_to_phone("LOCK_SCREEN")

def send_chat_message(text):
    """Send a chat message to the phone."""
    # Prefix is crucial so the phone knows it's a chat message, not a command
    return send_to_phone(f"CHAT_MSG:{text}")

def play_tts(text):
    """Make the phone speak text using TTS."""
    return send_to_phone(f"TTS:{text}")

def start_camera_stream():
    """Start streaming phone camera to PC."""
    return send_to_phone("CAMERA_STREAM:START")

def stop_camera_stream():
    """Stop camera stream."""
    return send_to_phone("CAMERA_STREAM:STOP")

def find_my_phone():
    """Ring + vibrate + max brightness to find the phone."""
    return send_to_phone("FIND_MY_PHONE")

def phone_keep_alive():
    """Ping the phone to check connection."""
    return send_to_phone("KEEP_ALIVE")

# ─── TASK MANAGER COMMANDS ──────────────────────────────────────

def sync_tasks_to_phone(tasks_json):
    """Send full task list to phone for sync."""
    return send_to_phone(f"TASKS_SYNC:{tasks_json}")

def notify_task_added(task_title, task_id):
    """Notify phone that a task was added."""
    return send_to_phone(f"TASK_NOTIFY_ADDED:{task_id}:{task_title}")

def notify_task_completed(task_title, task_id):
    """Notify phone that a task was completed."""
    return send_to_phone(f"TASK_NOTIFY_COMPLETED:{task_id}:{task_title}")

def notify_task_deleted(task_id):
    """Notify phone that a task was deleted."""
    return send_to_phone(f"TASK_NOTIFY_DELETED:{task_id}")

def request_tasks_from_phone():
    """Request the phone to send its local task list."""
    return send_to_phone("TASKS_REQUEST")

# ─── NOTES COMMANDS ─────────────────────────────────────────────

def sync_notes_to_phone(notes_json):
    """Send full notes tree to phone for sync."""
    return send_to_phone(f"NOTES_SYNC:{notes_json}")

def notify_note_added(note_id, note_name):
    """Notify phone that a note was added."""
    return send_to_phone(f"NOTE_NOTIFY_ADDED:{note_id}:{note_name}")

def notify_note_updated(note_id, note_name):
    """Notify phone that a note was updated."""
    return send_to_phone(f"NOTE_NOTIFY_UPDATED:{note_id}:{note_name}")

def notify_note_deleted(note_id):
    """Notify phone that a note was deleted."""
    return send_to_phone(f"NOTE_NOTIFY_DELETED:{note_id}")

def request_notes_from_phone():
    """Request the phone to send its local notes."""
    return send_to_phone("NOTES_REQUEST")

# ─── CALENDAR COMMANDS ─────────────────────────────────────────

def sync_calendar_to_phone(calendar_json):
    """Send full calendar event list to phone for sync."""
    return send_to_phone(f"CAL_SYNC:{calendar_json}")

def notify_event_added(event_id, event_title):
    """Notify phone that a calendar event was added."""
    return send_to_phone(f"CAL_NOTIFY_ADDED:{event_id}:{event_title}")

def notify_event_updated(event_id, event_title):
    """Notify phone that a calendar event was updated."""
    return send_to_phone(f"CAL_NOTIFY_UPDATED:{event_id}:{event_title}")

def notify_event_deleted(event_id):
    """Notify phone that a calendar event was deleted."""
    return send_to_phone(f"CAL_NOTIFY_DELETED:{event_id}")

def request_calendar_from_phone():
    """Request the phone to send its local calendar events."""
    return send_to_phone("CAL_REQUEST")

# ─── NOTIFICATION MIRROR COMMANDS ──────────────────────────────

def dismiss_notification(key):
    """Tell the phone to dismiss a notification."""
    return send_to_phone(f"NOTIF_DISMISS:{key}")

# ─── CONNECTION MONITOR ─────────────────────────────────────────

_last_phone_heartbeat = 0

def update_heartbeat():
    global _last_phone_heartbeat
    _last_phone_heartbeat = time.time()

def is_phone_online(timeout=5):
    if _last_phone_heartbeat == 0:
        return False
    return (time.time() - _last_phone_heartbeat) < timeout

def start_heartbeat_monitor(on_status_change=None):
    """Background thread that checks phone connectivity."""
    def monitor():
        was_online = False
        while True:
            online = is_phone_online()
            if online != was_online:
                was_online = online
                _notify("phone_status", {"online": online, "ip": _phone_ip})
                if on_status_change:
                    on_status_change(online)
            time.sleep(1)
    
    threading.Thread(target=monitor, daemon=True).start()


