"""
Chat Module — Beautiful PC ↔ Phone real-time messaging.

A full-featured chat interface with:
  - Custom neon dark / crystal light themes
  - Message types: text, image, file, command, system, link
  - Slash commands: /screenshot, /lock, /volume, /brightness, /open, /info, /help, /clear
  - Clickable images, files, and URLs
  - Persistent JSON history with search
  - Date separators
  - File attachments via picker

Requires: customtkinter, Pillow
"""

import customtkinter as ctk
import os
import json
import time
import re
import threading
import subprocess
import webbrowser
from datetime import datetime
from tkinter import filedialog, messagebox
from PIL import Image

# ─── CHAT THEMES ────────────────────────────────────────────────

CHAT_DARK = {
    "bg":              "#0A0E1A",
    "header":          "#111827",
    "my_bubble":       "#7C3AED",
    "my_hover":        "#6D28D9",
    "my_text":         "#FFFFFF",
    "their_bubble":    "#1A2332",
    "their_border":    "#06B6D4",
    "their_text":      "#F1F5F9",
    "accent":          "#06B6D4",
    "accent_2":        "#8B5CF6",
    "text":            "#F1F5F9",
    "text_dim":        "#94A3B8",
    "timestamp":       "#64748B",
    "input_bg":        "#111827",
    "input_border":    "#334155",
    "input_focus":     "#7C3AED",
    "system_bg":       "#1E1B4B",
    "system_text":     "#A5B4FC",
    "cmd_bg":          "#0F172A",
    "cmd_text":        "#67E8F9",
    "link":            "#22D3EE",
    "file_card":       "#1E293B",
    "divider":         "#334155",
    "send_btn":        "#7C3AED",
    "send_hover":      "#6D28D9",
    "attach_bg":       "#1E293B",
    "date_bg":         "#1E1B4B",
    "date_text":       "#A5B4FC",
    "sender_me":       "#C4B5FD",
    "sender_them":     "#06B6D4",
    "scrollbar":       "#374151",
    "search_bg":       "#1E293B",
}

CHAT_LIGHT = {
    "bg":              "#F8FAFC",
    "header":          "#FFFFFF",
    "my_bubble":       "#7C3AED",
    "my_hover":        "#6D28D9",
    "my_text":         "#FFFFFF",
    "their_bubble":    "#FFFFFF",
    "their_border":    "#E2E8F0",
    "their_text":      "#1E293B",
    "accent":          "#7C3AED",
    "accent_2":        "#06B6D4",
    "text":            "#1E293B",
    "text_dim":        "#64748B",
    "timestamp":       "#94A3B8",
    "input_bg":        "#FFFFFF",
    "input_border":    "#CBD5E1",
    "input_focus":     "#7C3AED",
    "system_bg":       "#EDE9FE",
    "system_text":     "#6D28D9",
    "cmd_bg":          "#F1F5F9",
    "cmd_text":        "#7C3AED",
    "link":            "#2563EB",
    "file_card":       "#F1F5F9",
    "divider":         "#E2E8F0",
    "send_btn":        "#7C3AED",
    "send_hover":      "#6D28D9",
    "attach_bg":       "#F1F5F9",
    "date_bg":         "#EDE9FE",
    "date_text":       "#6D28D9",
    "sender_me":       "#7C3AED",
    "sender_them":     "#0891B2",
    "scrollbar":       "#CBD5E1",
    "search_bg":       "#F1F5F9",
}

# ─── STORAGE PATH ───────────────────────────────────────────────

CHAT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "chat_history.json")
SCREENSHOTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")
os.makedirs(SCREENSHOTS_DIR, exist_ok=True)


# ═════════════════════════════════════════════════════════════════
#  CHAT STORAGE — Persistent JSON message store
# ═════════════════════════════════════════════════════════════════

class ChatStorage:
    """Thread-safe persistent JSON storage for chat messages."""

    def __init__(self, filepath=CHAT_FILE):
        self.filepath = filepath
        self.messages = []
        self._lock = threading.Lock()
        self._load()

    def _load(self):
        try:
            if os.path.exists(self.filepath):
                with open(self.filepath, 'r', encoding='utf-8') as f:
                    self.messages = json.load(f)
        except Exception as e:
            print(f"[Chat] Load error: {e}")
            self.messages = []

    def _save(self):
        try:
            with open(self.filepath, 'w', encoding='utf-8') as f:
                json.dump(self.messages, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"[Chat] Save error: {e}")

    def add_message(self, sender, content, msg_type="text", metadata=None):
        """
        Add a message.
        sender: "pc" | "phone" | "system"
        msg_type: "text" | "image" | "file" | "command" | "system" | "link"
        """
        msg = {
            "id": int(time.time() * 1000),
            "sender": sender,
            "content": content,
            "type": msg_type,
            "timestamp": datetime.now().isoformat(),
            "metadata": metadata or {},
        }
        with self._lock:
            self.messages.append(msg)
            self._save()
        return msg

    def get_messages(self, limit=200):
        with self._lock:
            if limit:
                return list(self.messages[-limit:])
            return list(self.messages)

    def search(self, query):
        q = query.lower()
        with self._lock:
            return [m for m in self.messages if q in m.get("content", "").lower()]

    def clear(self):
        with self._lock:
            self.messages.clear()
            self._save()

    def get_stats(self):
        with self._lock:
            total = len(self.messages)
            pc = sum(1 for m in self.messages if m["sender"] == "pc")
            phone = sum(1 for m in self.messages if m["sender"] == "phone")
            return {"total": total, "pc": pc, "phone": phone}


# ═════════════════════════════════════════════════════════════════
#  CHAT TAB — Beautiful chat interface widget
# ═════════════════════════════════════════════════════════════════

class ChatTab:
    """Embeddable chat UI for the control panel tabview."""

    def __init__(self, parent_tab, control_panel):
        self.parent = parent_tab
        self.cp = control_panel
        self.storage = ChatStorage()
        self.is_dark = True
        self._image_refs = []       # prevent GC of CTkImage
        self._bubble_widgets = []   # all rendered row widgets
        self._last_date = None      # for date separators

        self._build_ui()
        self._load_history()

    # ─── THEME HELPER ───────────────────────────────────────────

    def _t(self):
        return CHAT_DARK if self.is_dark else CHAT_LIGHT

    # ─── BUILD UI ───────────────────────────────────────────────

    def _build_ui(self):
        t = self._t()

        self.main_frame = ctk.CTkFrame(self.parent, fg_color=t["bg"], corner_radius=0)
        self.main_frame.pack(fill="both", expand=True)

        self._build_header()
        self._build_chat_area()
        self._build_input_bar()

    def _build_header(self):
        t = self._t()

        self.header = ctk.CTkFrame(self.main_frame, height=52, fg_color=t["header"], corner_radius=0)
        self.header.pack(fill="x")
        self.header.pack_propagate(False)

        # Left: title + count
        left = ctk.CTkFrame(self.header, fg_color="transparent")
        left.pack(side="left", padx=16)

        self.chat_title = ctk.CTkLabel(
            left, text="💬  Chat", font=("Segoe UI Semibold", 17),
            text_color=t["accent"])
        self.chat_title.pack(side="left")

        self.msg_count_label = ctk.CTkLabel(
            left, text="", font=("Segoe UI", 11), text_color=t["timestamp"])
        self.msg_count_label.pack(side="left", padx=(12, 0))

        # Right: search + theme + clear
        right = ctk.CTkFrame(self.header, fg_color="transparent")
        right.pack(side="right", padx=16)

        self.search_entry = ctk.CTkEntry(
            right, placeholder_text="🔍 Search...", width=170, height=30,
            font=("Segoe UI", 11), fg_color=t["search_bg"],
            border_color=t["input_border"], corner_radius=15)
        self.search_entry.pack(side="left", padx=(0, 6))
        self.search_entry.bind("<Return>", lambda e: self._search_messages())
        self.search_entry.bind("<Escape>", lambda e: self._reload_all())

        self.theme_btn = ctk.CTkButton(
            right, text="☀️", width=32, height=32, font=("Segoe UI", 15),
            fg_color=t["attach_bg"], hover_color=t["divider"], corner_radius=16,
            command=self._toggle_theme)
        self.theme_btn.pack(side="left", padx=(0, 4))

        self.clear_btn = ctk.CTkButton(
            right, text="🗑️", width=32, height=32, font=("Segoe UI", 15),
            fg_color=t["attach_bg"], hover_color=t["divider"], corner_radius=16,
            command=self._clear_chat)
        self.clear_btn.pack(side="left")

    def _build_chat_area(self):
        t = self._t()

        self.chat_scroll = ctk.CTkScrollableFrame(
            self.main_frame, fg_color=t["bg"], corner_radius=0,
            scrollbar_button_color=t["scrollbar"],
            scrollbar_button_hover_color=t["accent"])
        self.chat_scroll.pack(fill="both", expand=True, padx=0, pady=0)

    def _build_input_bar(self):
        t = self._t()

        self.input_frame = ctk.CTkFrame(
            self.main_frame, height=62, fg_color=t["header"], corner_radius=0)
        self.input_frame.pack(fill="x", side="bottom")
        self.input_frame.pack_propagate(False)

        inner = ctk.CTkFrame(self.input_frame, fg_color="transparent")
        inner.pack(fill="both", expand=True, padx=12, pady=10)

        # Attach button
        self.attach_btn = ctk.CTkButton(
            inner, text="📎", width=38, height=38, font=("Segoe UI", 17),
            fg_color=t["attach_bg"], hover_color=t["divider"], corner_radius=19,
            command=self._attach_file)
        self.attach_btn.pack(side="left", padx=(0, 8))

        # Text entry
        self.msg_input = ctk.CTkEntry(
            inner, placeholder_text="Type a message or /command...",
            height=38, font=("Segoe UI", 13),
            fg_color=t["input_bg"], border_color=t["input_border"], corner_radius=19)
        self.msg_input.pack(side="left", fill="x", expand=True, padx=(0, 8))
        self.msg_input.bind("<Return>", lambda e: self._send_message())

        # Send button
        self.send_btn = ctk.CTkButton(
            inner, text="➤", width=38, height=38, font=("Segoe UI", 18),
            fg_color=t["send_btn"], hover_color=t["send_hover"], corner_radius=19,
            command=self._send_message)
        self.send_btn.pack(side="right")

    # ═════════════════════════════════════════════════════════════
    #  MESSAGE RENDERING
    # ═════════════════════════════════════════════════════════════

    def _load_history(self):
        """Load stored messages and render them."""
        self._last_date = None
        messages = self.storage.get_messages()
        for msg in messages:
            self._render_message(msg)
        self._update_count()
        self.main_frame.after(100, self._scroll_to_bottom)

    def _scroll_to_bottom(self):
        try:
            self.chat_scroll._parent_canvas.yview_moveto(1.0)
        except Exception:
            pass

    # ─── DATE SEPARATOR ─────────────────────────────────────────

    def _maybe_add_date_sep(self, timestamp_str):
        """Insert a date pill if the day has changed."""
        try:
            dt = datetime.fromisoformat(timestamp_str)
            date_str = dt.strftime("%B %d, %Y")
            if date_str != self._last_date:
                self._last_date = date_str
                t = self._t()

                row = ctk.CTkFrame(self.chat_scroll, fg_color="transparent")
                row.pack(fill="x", pady=(12, 4))

                pill = ctk.CTkFrame(row, fg_color=t["date_bg"], corner_radius=12)
                pill.pack(anchor="center")

                today = datetime.now().strftime("%B %d, %Y")
                display = "Today" if date_str == today else date_str

                ctk.CTkLabel(
                    pill, text=f"  {display}  ", font=("Segoe UI Semibold", 10),
                    text_color=t["date_text"]
                ).pack(padx=14, pady=4)

                self._bubble_widgets.append(row)
        except Exception:
            pass

    # ─── DISPATCH RENDERER ──────────────────────────────────────

    def _render_message(self, msg):
        """Render a message dict as a UI bubble."""
        t = self._t()
        sender = msg.get("sender", "pc")
        msg_type = msg.get("type", "text")
        content = msg.get("content", "")
        timestamp = msg.get("timestamp", "")
        metadata = msg.get("metadata", {})

        # Date separator
        self._maybe_add_date_sep(timestamp)

        # System messages → centered
        if msg_type == "system":
            self._render_system(content, timestamp)
            return

        is_me = (sender == "pc")

        # Outer row
        row = ctk.CTkFrame(self.chat_scroll, fg_color="transparent")
        row.pack(fill="x", padx=14, pady=2)

        # Bubble
        bubble_fg   = t["my_bubble"] if is_me else t["their_bubble"]
        border_w    = 0 if is_me else 2
        border_c    = None if is_me else t["their_border"]

        bubble = ctk.CTkFrame(
            row, fg_color=bubble_fg, corner_radius=18,
            border_width=border_w, border_color=border_c)

        if is_me:
            bubble.pack(side="right", padx=(80, 0))
        else:
            bubble.pack(side="left", padx=(0, 80))

        # Sender tag
        sender_text  = "You" if is_me else "📱 Phone"
        sender_color = t["sender_me"] if is_me else t["sender_them"]
        ctk.CTkLabel(
            bubble, text=sender_text, font=("Segoe UI Semibold", 10),
            text_color=sender_color
        ).pack(padx=14, pady=(10, 0), anchor="w")

        # ── Content by type ──
        if msg_type == "text":
            self._render_text(bubble, content, is_me)
        elif msg_type == "image":
            self._render_image(bubble, content, metadata)
        elif msg_type == "file":
            self._render_file(bubble, content, metadata, is_me)
        elif msg_type == "command":
            self._render_command(bubble, content, metadata, is_me)
        elif msg_type == "link":
            self._render_link(bubble, content, is_me)
        else:
            self._render_text(bubble, content, is_me)

        # Timestamp
        try:
            dt = datetime.fromisoformat(timestamp)
            time_str = dt.strftime("%I:%M %p")
        except Exception:
            time_str = ""

        ctk.CTkLabel(
            bubble, text=time_str, font=("Segoe UI", 9),
            text_color=t["timestamp"]
        ).pack(padx=14, pady=(2, 10), anchor="e")

        self._bubble_widgets.append(row)

    # ─── TEXT ────────────────────────────────────────────────────

    def _render_text(self, bubble, content, is_me):
        t = self._t()
        text_color = t["my_text"] if is_me else t["their_text"]

        lbl = ctk.CTkLabel(
            bubble, text=content, font=("Segoe UI", 13),
            text_color=text_color, wraplength=380,
            justify="left", anchor="w")
        lbl.pack(padx=14, pady=(4, 0), anchor="w")

        # Detect and render URLs in text
        urls = re.findall(r'https?://\S+', content)
        for url in urls:
            link_btn = ctk.CTkButton(
                bubble, text=f"🔗 Open link",
                font=("Segoe UI", 10), height=22,
                fg_color="transparent", hover_color=t["divider"],
                text_color=t["link"], anchor="w",
                command=lambda u=url: webbrowser.open(u))
            link_btn.pack(padx=12, pady=(2, 0), anchor="w")

    # ─── IMAGE ──────────────────────────────────────────────────

    def _render_image(self, bubble, filepath, metadata):
        t = self._t()

        try:
            if os.path.exists(filepath):
                img = Image.open(filepath)
                img.thumbnail((300, 220))
                ctk_img = ctk.CTkImage(
                    light_image=img, dark_image=img,
                    size=(img.width, img.height))

                img_label = ctk.CTkLabel(bubble, image=ctk_img, text="", cursor="hand2")
                img_label.pack(padx=10, pady=(6, 0))
                img_label.bind("<Button-1>", lambda e, p=filepath: os.startfile(p))
                self._image_refs.append(ctk_img)
            else:
                ctk.CTkLabel(
                    bubble, text=f"📷  {os.path.basename(filepath)}  (file missing)",
                    font=("Segoe UI", 11), text_color=t["text_dim"]
                ).pack(padx=14, pady=4)
        except Exception:
            ctk.CTkLabel(
                bubble, text="📷  Image (failed to load)",
                font=("Segoe UI", 11), text_color=t["text_dim"]
            ).pack(padx=14, pady=4)

    # ─── FILE ───────────────────────────────────────────────────

    def _render_file(self, bubble, content, metadata, is_me):
        t = self._t()
        filename = metadata.get("filename", os.path.basename(content))
        filesize = metadata.get("filesize", "")
        filepath = metadata.get("filepath", content)

        card = ctk.CTkFrame(bubble, fg_color=t["file_card"], corner_radius=12)
        card.pack(padx=10, pady=(6, 0), fill="x")

        # Icon by extension
        ext = os.path.splitext(filename)[1].lower()
        icons = {
            '.pdf': '📄', '.doc': '📝', '.docx': '📝', '.txt': '📝',
            '.jpg': '🖼️', '.png': '🖼️', '.gif': '🖼️', '.bmp': '🖼️',
            '.mp4': '🎬', '.mkv': '🎬', '.avi': '🎬', '.mov': '🎬',
            '.mp3': '🎵', '.wav': '🎵', '.flac': '🎵',
            '.zip': '📦', '.rar': '📦', '.7z': '📦',
            '.py': '🐍', '.js': '💛', '.java': '☕', '.cpp': '⚙️',
            '.exe': '💿', '.apk': '📲',
        }
        icon = icons.get(ext, '📎')

        inner = ctk.CTkFrame(card, fg_color="transparent")
        inner.pack(fill="x", padx=12, pady=10)

        ctk.CTkLabel(inner, text=icon, font=("Segoe UI", 26)).pack(side="left", padx=(0, 10))

        info = ctk.CTkFrame(inner, fg_color="transparent")
        info.pack(side="left", fill="x", expand=True)

        ctk.CTkLabel(
            info, text=filename, font=("Segoe UI Semibold", 12),
            text_color=t["text"], anchor="w"
        ).pack(anchor="w")

        if filesize:
            size_text = self._format_size(int(filesize)) if str(filesize).isdigit() else filesize
            ctk.CTkLabel(
                info, text=size_text, font=("Segoe UI", 10),
                text_color=t["timestamp"], anchor="w"
            ).pack(anchor="w")

        # Open button
        if os.path.exists(filepath):
            ctk.CTkButton(
                inner, text="Open", width=52, height=26, font=("Segoe UI", 10),
                fg_color=t["accent_2"], hover_color=t["send_hover"],
                corner_radius=13,
                command=lambda p=filepath: os.startfile(p)
            ).pack(side="right")

    # ─── COMMAND ────────────────────────────────────────────────

    def _render_command(self, bubble, content, metadata, is_me):
        t = self._t()

        cmd_frame = ctk.CTkFrame(bubble, fg_color=t["cmd_bg"], corner_radius=10)
        cmd_frame.pack(padx=10, pady=(6, 0), fill="x")

        ctk.CTkLabel(
            cmd_frame, text=f"  {content}", font=("Consolas", 12),
            text_color=t["cmd_text"], anchor="w"
        ).pack(padx=10, pady=7, anchor="w")

        result = metadata.get("result", "")
        if result:
            ctk.CTkLabel(
                bubble, text=result, font=("Segoe UI", 12),
                text_color=t["text_dim"], wraplength=370,
                justify="left", anchor="w"
            ).pack(padx=14, pady=(4, 0), anchor="w")

    # ─── LINK ───────────────────────────────────────────────────

    def _render_link(self, bubble, url, is_me):
        t = self._t()
        display = url if len(url) <= 55 else url[:52] + "..."
        link_btn = ctk.CTkButton(
            bubble, text=f"🌐  {display}", font=("Segoe UI", 12),
            height=28, fg_color="transparent", hover_color=t["divider"],
            text_color=t["link"] if not is_me else "#B0D4FF",
            anchor="w", cursor="hand2",
            command=lambda: webbrowser.open(url))
        link_btn.pack(padx=10, pady=(6, 0), anchor="w")

    # ─── SYSTEM ─────────────────────────────────────────────────

    def _render_system(self, content, timestamp):
        t = self._t()

        row = ctk.CTkFrame(self.chat_scroll, fg_color="transparent")
        row.pack(fill="x", padx=14, pady=6)

        card = ctk.CTkFrame(row, fg_color=t["system_bg"], corner_radius=14)
        card.pack(anchor="center")

        try:
            dt = datetime.fromisoformat(timestamp)
            time_str = dt.strftime("%I:%M %p")
        except Exception:
            time_str = ""

        ctk.CTkLabel(
            card, text=f"  {content}  •  {time_str}  ",
            font=("Segoe UI", 11), text_color=t["system_text"]
        ).pack(padx=16, pady=7)

        self._bubble_widgets.append(row)

    # ═════════════════════════════════════════════════════════════
    #  ACTIONS
    # ═════════════════════════════════════════════════════════════

    def _send_message(self):
        """Send a message from the PC."""
        text = self.msg_input.get().strip()
        if not text:
            return

        self.msg_input.delete(0, "end")

        # Slash commands
        if text.startswith("/"):
            self._handle_command(text)
            return

        # URL detection
        if re.match(r'https?://', text):
            msg = self.storage.add_message("pc", text, "link")
        else:
            msg = self.storage.add_message("pc", text, "text")

        self._render_message(msg)
        self._send_to_phone(f"CHAT_MSG:{text}")
        self._update_count()
        self._scroll_to_bottom()

    def receive_message(self, content, msg_type="text", metadata=None):
        """Public: called when a message arrives from the phone."""
        msg = self.storage.add_message("phone", content, msg_type, metadata)
        self._render_message(msg)
        self._update_count()
        self._scroll_to_bottom()

        # Tray notification
        if hasattr(self.cp, '_tray_notify'):
            preview = content[:50] + ("..." if len(content) > 50 else "")
            self.cp._tray_notify("New Message", f"📱 {preview}")

    def add_system_message(self, content):
        """Public: add a centered system notification card."""
        msg = self.storage.add_message("system", content, "system")
        self._render_message(msg)
        self._scroll_to_bottom()

    # ─── SLASH COMMANDS ─────────────────────────────────────────

    def _handle_command(self, text):
        parts = text.split(maxsplit=1)
        cmd = parts[0].lower()
        args = parts[1] if len(parts) > 1 else ""
        result = ""

        # /help
        if cmd == "/help":
            result = (
                "Available commands:\n"
                "  /screenshot  — Capture PC screen (shown inline)\n"
                "  /lock        — Lock the PC\n"
                "  /volume <n>  — Set volume (0-100)\n"
                "  /brightness <n> — Set brightness (0-100)\n"
                "  /open <app>  — Open an application\n"
                "  /info        — Show PC system info\n"
                "  /clear       — Clear chat history\n"
                "  /help        — This help message"
            )

        # /screenshot
        elif cmd == "/screenshot":
            try:
                import pyautogui
                filename = f"chat_ss_{int(time.time())}.png"
                filepath = os.path.join(SCREENSHOTS_DIR, filename)
                screenshot = pyautogui.screenshot()
                screenshot.save(filepath)

                # Render command bubble first
                cmd_msg = self.storage.add_message("pc", text, "command",
                                                    {"result": "📸 Screenshot captured!"})
                self._render_message(cmd_msg)

                # Then the image inline
                img_msg = self.storage.add_message("pc", filepath, "image",
                                                    {"filename": filename})
                self._render_message(img_msg)

                self._update_count()
                self._scroll_to_bottom()
                return  # skip default render below
            except Exception as e:
                result = f"Screenshot failed: {e}"

        # /lock
        elif cmd == "/lock":
            try:
                import ctypes
                ctypes.windll.user32.LockWorkStation()
                result = "🔒 PC locked!"
            except Exception as e:
                result = f"Lock failed: {e}"

        # /volume
        elif cmd == "/volume":
            try:
                level = int(args)
                import pyautogui
                for _ in range(50):
                    pyautogui.press('volumedown')
                for _ in range(level // 2):
                    pyautogui.press('volumeup')
                result = f"🔊 Volume → ~{level}%"
            except ValueError:
                result = "Usage: /volume <0-100>"
            except Exception as e:
                result = f"Volume error: {e}"

        # /brightness
        elif cmd == "/brightness":
            try:
                level = int(args)
                import screen_brightness_control as sbc
                sbc.set_brightness(level)
                result = f"☀️ Brightness → {level}%"
            except ValueError:
                result = "Usage: /brightness <0-100>"
            except Exception as e:
                result = f"Brightness error: {e}"

        # /open
        elif cmd == "/open":
            if args:
                try:
                    apps = {
                        "notepad": "notepad", "calculator": "calc",
                        "paint": "mspaint", "cmd": "cmd",
                        "explorer": "explorer", "code": "code",
                        "chrome": "chrome", "firefox": "firefox",
                        "edge": "msedge", "terminal": "wt",
                    }
                    app_cmd = apps.get(args.lower(), args)
                    subprocess.Popen([app_cmd], shell=True)
                    result = f"✅ Opened: {args}"
                except Exception as e:
                    result = f"Failed to open: {e}"
            else:
                result = "Usage: /open <app_name>"

        # /info
        elif cmd == "/info":
            try:
                import psutil
                cpu = psutil.cpu_percent(interval=0.5)
                ram = psutil.virtual_memory()
                disk = psutil.disk_usage('/')
                battery = psutil.sensors_battery()
                bat_str = (f"{battery.percent}% {'⚡' if battery.power_plugged else '🔋'}"
                           if battery else "Desktop (AC)")

                result = (
                    f"💻 System Info\n"
                    f"  CPU: {cpu}%\n"
                    f"  RAM: {ram.percent}%  ({ram.used // (1024**3)}/{ram.total // (1024**3)} GB)\n"
                    f"  Disk: {disk.percent}%  ({disk.free // (1024**3)} GB free)\n"
                    f"  Battery: {bat_str}"
                )
            except Exception as e:
                result = f"Info error: {e}"

        # /clear
        elif cmd == "/clear":
            self._clear_chat()
            return

        # unknown
        else:
            result = f"Unknown command: {cmd}\nType /help for available commands"

        # Default: render the command bubble
        msg = self.storage.add_message("pc", text, "command", {"result": result})
        self._render_message(msg)
        self._update_count()
        self._scroll_to_bottom()

    # ─── FILE ATTACH ────────────────────────────────────────────

    def _attach_file(self):
        """Pick and send a file."""
        filepath = filedialog.askopenfilename(title="Send File in Chat")
        if not filepath:
            return

        filename = os.path.basename(filepath)
        filesize = os.path.getsize(filepath)

        msg = self.storage.add_message("pc", filepath, "file", {
            "filename": filename,
            "filesize": str(filesize),
            "filepath": filepath,
        })
        self._render_message(msg)
        self._update_count()
        self._scroll_to_bottom()

        # Send to phone if connected
        try:
            import reverse_commands
            import file_service
            if reverse_commands.is_connected():
                phone_ip = reverse_commands.get_phone_ip()
                threading.Thread(
                    target=file_service.send_specific_file,
                    args=(filepath, phone_ip), daemon=True
                ).start()
                self.add_system_message(f"📤 Sending {filename}...")
        except Exception as e:
            print(f"[Chat] File send error: {e}")

    # ─── HELPERS ────────────────────────────────────────────────

    def _send_to_phone(self, command):
        try:
            import reverse_commands
            if reverse_commands.is_connected():
                reverse_commands.send_to_phone(command)
        except Exception:
            pass

    def _search_messages(self):
        query = self.search_entry.get().strip()
        if not query:
            self._reload_all()
            return

        results = self.storage.search(query)
        self._clear_display()
        self._last_date = None

        if results:
            for msg in results:
                self._render_message(msg)
            self._update_count(f"🔍 {len(results)} results for \"{query}\"")
        else:
            self._update_count("🔍 No results found")

        # Restore all after 8 seconds
        self.main_frame.after(8000, self._reload_all)

    def _reload_all(self):
        self.search_entry.delete(0, "end")
        self._clear_display()
        self._last_date = None
        messages = self.storage.get_messages()
        for msg in messages:
            self._render_message(msg)
        self._update_count()
        self.main_frame.after(50, self._scroll_to_bottom)

    def _clear_display(self):
        for widget in self._bubble_widgets:
            try:
                widget.destroy()
            except Exception:
                pass
        self._bubble_widgets.clear()
        self._image_refs.clear()

    def _clear_chat(self):
        if messagebox.askyesno("Clear Chat", "Delete all chat history?\nThis cannot be undone."):
            self.storage.clear()
            self._clear_display()
            self._last_date = None
            self._update_count()
            self.add_system_message("Chat history cleared")

    # ─── THEME TOGGLE ───────────────────────────────────────────

    def _toggle_theme(self):
        self.is_dark = not self.is_dark
        t = self._t()
        self.theme_btn.configure(text="🌙" if not self.is_dark else "☀️")

        # Re-apply colors to all containers
        self.main_frame.configure(fg_color=t["bg"])
        self.header.configure(fg_color=t["header"])
        self.chat_scroll.configure(
            fg_color=t["bg"],
            scrollbar_button_color=t["scrollbar"],
            scrollbar_button_hover_color=t["accent"])
        self.input_frame.configure(fg_color=t["header"])
        self.msg_input.configure(fg_color=t["input_bg"], border_color=t["input_border"])
        self.search_entry.configure(fg_color=t["search_bg"], border_color=t["input_border"])
        self.send_btn.configure(fg_color=t["send_btn"], hover_color=t["send_hover"])
        self.attach_btn.configure(fg_color=t["attach_bg"], hover_color=t["divider"])
        self.theme_btn.configure(fg_color=t["attach_bg"], hover_color=t["divider"])
        self.clear_btn.configure(fg_color=t["attach_bg"], hover_color=t["divider"])
        self.chat_title.configure(text_color=t["accent"])
        self.msg_count_label.configure(text_color=t["timestamp"])

        # Re-render all messages with new theme
        self._clear_display()
        self._last_date = None
        messages = self.storage.get_messages()
        for msg in messages:
            self._render_message(msg)
        self.main_frame.after(50, self._scroll_to_bottom)

    def _update_count(self, text=None):
        if text:
            self.msg_count_label.configure(text=text)
        else:
            stats = self.storage.get_stats()
            self.msg_count_label.configure(text=f"{stats['total']} messages")

    @staticmethod
    def _format_size(size_bytes):
        if size_bytes < 1024:
            return f"{size_bytes} B"
        elif size_bytes < 1024 * 1024:
            return f"{size_bytes / 1024:.1f} KB"
        elif size_bytes < 1024 * 1024 * 1024:
            return f"{size_bytes / (1024 * 1024):.1f} MB"
        else:
            return f"{size_bytes / (1024 * 1024 * 1024):.1f} GB"
