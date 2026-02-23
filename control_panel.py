"""
Mobile Controller — PC Control Panel
A modern GUI to control your phone from the laptop.

Features:
  - QR Code Pairing
  - Connection status dashboard
  - Phone controls (vibrate, ring, flashlight, find my phone)
  - File sharing (send to phone)
  - Clipboard sync
  - System info display
  - Server start/stop
  - Logs viewer

Requires: pip install customtkinter pillow qrcode psutil
"""

import customtkinter as ctk
from tkinter import filedialog, messagebox
import threading
import socket
import time
import os
import sys
import psutil
from PIL import Image, ImageTk, ImageDraw
from io import BytesIO
from datetime import datetime
import notes_module
import calendar_module
import clipboard_sync
import notification_manager

# Add parent to path for imports
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE_DIR)

import qr_pairing
import reverse_commands
import config
import task_manager
from embedded_server import EmbeddedServer
from chat_module import ChatTab

# Optional: System tray support
try:
    import pystray
    HAS_TRAY = True
except ImportError:
    HAS_TRAY = False
    print("[Info] pystray not installed — system tray disabled. Install with: pip install pystray")

# ─── THEME ──────────────────────────────────────────────────────
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

# Color palette
COLORS = {
    "bg_dark": "#1a1a2e",
    "bg_card": "#16213e",
    "bg_card_hover": "#1a2744",
    "accent": "#0f3460",
    "accent_bright": "#00adb5",
    "success": "#00c853",
    "warning": "#ffab00",
    "danger": "#ff1744",
    "text": "#e0e0e0",
    "text_dim": "#888888",
    "border": "#2a2a4a",
}


class LogRedirector:
    """Captures print() output and sends it to the GUI log panel."""
    def __init__(self, callback):
        self.callback = callback
        self._original_stdout = sys.stdout

    def write(self, text):
        if text.strip():
            self.callback(text.strip())
        # Also write to original stdout
        if self._original_stdout:
            self._original_stdout.write(text)
    
    def flush(self):
        if self._original_stdout:
            self._original_stdout.flush()


class StatusIndicator(ctk.CTkFrame):
    """A colored dot indicator with label."""
    def __init__(self, parent, label_text, **kwargs):
        super().__init__(parent, fg_color="transparent", **kwargs)
        
        self.dot = ctk.CTkLabel(self, text="●", font=("Segoe UI", 18), text_color=COLORS["danger"])
        self.dot.pack(side="left", padx=(0, 6))
        
        self.label = ctk.CTkLabel(self, text=label_text, font=("Segoe UI", 13), text_color=COLORS["text_dim"])
        self.label.pack(side="left")
    
    def set_status(self, online, text=None):
        color = COLORS["success"] if online else COLORS["danger"]
        self.dot.configure(text_color=color)
        if text:
            self.label.configure(text=text)


class ControlPanel(ctk.CTk):
    def __init__(self):
        super().__init__()
        
        self.title("Mobile Controller — Command Center")
        self.geometry("1050x720")
        self.minsize(900, 650)
        self.configure(fg_color=COLORS["bg_dark"])
        
        # State
        self.server_running = False
        self.embedded_server = EmbeddedServer()
        self.phone_connected = False
        self.log_lines = []
        self.tray_icon = None
        self._minimized_to_tray = False
        
        # Build UI
        self._build_header()
        self._build_main_content()
        self._build_footer()
        
        # Start background monitors
        self._start_monitors()
        
        # Redirect logs
        self.log_redirector = LogRedirector(self._append_log)
        sys.stdout = self.log_redirector
        
        # Closing handler
        self.protocol("WM_DELETE_WINDOW", self._on_close)
        
        self._append_log("Control Panel started. Scan QR code from your phone to connect.")
        
        # Register embedded server callbacks
        self.embedded_server.on_stop(lambda: self.after(0, self._on_server_remote_stop))
        self.embedded_server.on_phone_connected(
            lambda ip: self.after(0, lambda: self._tray_notify("Phone Connected", f"Device: {ip}"))
        )
        
        # Setup system tray icon
        self._setup_tray_icon()

        clipboard_sync.start_watching()

    # ─── HEADER ─────────────────────────────────────────────────
    def _build_header(self):
        header = ctk.CTkFrame(self, height=60, fg_color=COLORS["bg_card"], corner_radius=0)
        header.pack(fill="x", padx=0, pady=0)
        header.pack_propagate(False)
        
        # Title
        title = ctk.CTkLabel(header, text="📱  Mobile Controller", 
                             font=("Segoe UI Semibold", 22), text_color=COLORS["accent_bright"])
        title.pack(side="left", padx=20)
        
        # Status indicators
        status_frame = ctk.CTkFrame(header, fg_color="transparent")
        status_frame.pack(side="right", padx=20)
        
        self.server_status = StatusIndicator(status_frame, "Server")
        self.server_status.pack(side="left", padx=(0, 20))
        
        self.phone_status = StatusIndicator(status_frame, "Phone")
        self.phone_status.pack(side="left")

    # ─── MAIN CONTENT ───────────────────────────────────────────
    def _build_main_content(self):
        main = ctk.CTkFrame(self, fg_color="transparent")
        main.pack(fill="both", expand=True, padx=12, pady=8)
        
        # Create tabview
        self.tabview = ctk.CTkTabview(main, fg_color=COLORS["bg_card"],
                                       segmented_button_fg_color=COLORS["bg_dark"],
                                       segmented_button_selected_color=COLORS["accent_bright"],
                                       segmented_button_selected_hover_color=COLORS["accent"],
                                       segmented_button_unselected_color=COLORS["bg_dark"],
                                       segmented_button_unselected_hover_color=COLORS["accent"],
                                       corner_radius=12)
        self.tabview.pack(fill="both", expand=True)
        
        # Add tabs
        self.tab_dashboard = self.tabview.add("Dashboard")
        self.tab_phone = self.tabview.add("Phone Controls")
        self.tab_chat = self.tabview.add("💬 Chat")
        self.tab_tasks = self.tabview.add("Tasks")
        self.tab_notes = self.tabview.add("📝 Notes")
        self.tab_calendar = self.tabview.add("📅 Calendar")
        self.tab_notifications = self.tabview.add("🔔 Notifications")
        self.tab_actions = self.tabview.add("Quick Actions")
        self.tab_logs = self.tabview.add("Logs")
        
        # Build each tab
        self._build_dashboard_tab()
        self._build_phone_tab()
        self._build_chat_tab()
        self._build_tasks_tab()
        self._build_notes_tab()
        self._build_calendar_tab()
        self._build_notifications_tab()
        self._build_actions_tab()
        self._build_logs_tab()
        
        # Register for task events
        task_manager.on_event("task_added", self._on_task_event)
        task_manager.on_event("task_completed", self._on_task_event)
        task_manager.on_event("task_deleted", self._on_task_event)
    
    # ─── DASHBOARD TAB ──────────────────────────────────────────
    def _build_dashboard_tab(self):
        tab = self.tab_dashboard
        tab.grid_columnconfigure(0, weight=1)
        tab.grid_columnconfigure(1, weight=2)
        tab.grid_rowconfigure(0, weight=1)
        
        # Left column: QR + Connection
        left_frame = ctk.CTkFrame(tab, fg_color="transparent")
        left_frame.grid(row=0, column=0, sticky="nsew", padx=(0, 8), pady=0)
        
        self._build_qr_section(left_frame)
        self._build_connection_section(left_frame)
        
        # ── Chat Shortcut Button ──
        chat_card = ctk.CTkFrame(left_frame, fg_color="#1a1a2e", corner_radius=12)
        chat_card.pack(fill="x", pady=(0, 8))
        
        ctk.CTkButton(
            chat_card, text="💬  Open Chat", height=42,
            font=("Segoe UI Semibold", 14),
            fg_color="#7C3AED", hover_color="#6D28D9",
            corner_radius=10,
            command=lambda: self.tabview.set("💬 Chat")
        ).pack(fill="x", padx=15, pady=12)
        
        # Right column: System Info
        right_frame = ctk.CTkFrame(tab, fg_color="transparent")
        right_frame.grid(row=0, column=1, sticky="nsew", padx=0, pady=0)
        
        self._build_system_info(right_frame)
        self._build_server_controls(right_frame)
    
    # ─── CHAT TAB ────────────────────────────────────────────────
    def _build_chat_tab(self):
        self.chat_tab = ChatTab(self.tab_chat, self)
        
        # Register callback so commands.py can route incoming CHAT_MSG
        import commands as cmd_module
        cmd_module.set_chat_callback(
            lambda text, mtype: self.after(0, lambda: self.chat_tab.receive_message(text, mtype))
        )

    # ─── PHONE CONTROLS TAB ─────────────────────────────────────
    def _build_phone_tab(self):
        tab = self.tab_phone
        self._build_phone_controls(tab)
    
    # ─── QUICK ACTIONS TAB ──────────────────────────────────────
    def _build_actions_tab(self):
        tab = self.tab_actions
        self._build_quick_actions(tab)
    
    # ─── LOGS TAB ───────────────────────────────────────────────
    def _build_logs_tab(self):
        tab = self.tab_logs
        self._build_log_panel(tab)

    # ─── TASKS TAB ──────────────────────────────────────────────
    def _build_tasks_tab(self):
        tab = self.tab_tasks
        self._build_tasks_panel(tab)

    # ─── NOTES TAB ──────────────────────────────────────────────
    def _build_notes_tab(self):
        self.notes_ui = notes_module.NotesTab(self.tab_notes)
        notes_module.set_notes_tab(self.notes_ui)

    # ─── CALENDAR TAB ───────────────────────────────────────────
    def _build_calendar_tab(self):
        self.calendar_ui = calendar_module.CalendarTab(self.tab_calendar)
        calendar_module.set_calendar_tab(self.calendar_ui)

    # ─── NOTIFICATIONS TAB ──────────────────────────────────────
    def _build_notifications_tab(self):
        tab = self.tab_notifications
        
        # Main card
        card = ctk.CTkFrame(tab, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="both", expand=True, padx=8, pady=8)
        
        # Header
        header = ctk.CTkFrame(card, fg_color="transparent")
        header.pack(fill="x", padx=20, pady=(16, 8))
        
        ctk.CTkLabel(header, text="🔔  Phone Notifications", font=("Segoe UI Semibold", 18),
                     text_color=COLORS["accent_bright"]).pack(side="left")
        
        self.notif_count_label = ctk.CTkLabel(header, text="0 notifications",
                                               font=("Segoe UI", 12), text_color=COLORS["text_dim"])
        self.notif_count_label.pack(side="right", padx=(0, 8))
        
        ctk.CTkButton(header, text="Clear All", width=80, height=28, font=("Segoe UI", 12),
                      fg_color=COLORS["danger"], hover_color="#ff4444",
                      command=self._clear_all_notifications).pack(side="right", padx=(0, 8))
        
        # Scrollable notification list
        self.notif_scroll = ctk.CTkScrollableFrame(card, fg_color=COLORS["bg_dark"],
                                                    corner_radius=8)
        self.notif_scroll.pack(fill="both", expand=True, padx=16, pady=(0, 16))
        
        # Empty state label
        self.notif_empty_label = ctk.CTkLabel(self.notif_scroll, 
                                               text="📭  No notifications yet\n\nNotifications from your phone will appear here.\nMake sure Notification Access is enabled in Android Settings.",
                                               font=("Segoe UI", 14), text_color=COLORS["text_dim"],
                                               justify="center")
        self.notif_empty_label.pack(expand=True, pady=60)
        
        # Track notification card widgets
        self.notif_card_widgets = {}  # key -> frame
        
        # Register for notification events
        notification_manager.on_notification(
            lambda notif: self.after(0, lambda n=notif: self._add_notification_card(n))
        )
        notification_manager.on_dismissed(
            lambda key: self.after(0, lambda k=key: self._on_notification_dismissed(k))
        )
    
    def _add_notification_card(self, notif):
        """Add a notification card to the scroll panel."""
        # Hide empty state
        self.notif_empty_label.pack_forget()
        
        key = notif["key"]
        app_icon = notif["app_icon"]
        app_name = notif["app_name"]
        title = notif["title"]
        body = notif["body"]
        time_str = notif["time_str"]
        
        # Card frame
        frame = ctk.CTkFrame(self.notif_scroll, fg_color=COLORS["bg_card"], corner_radius=10, height=80)
        frame.pack(fill="x", pady=4, padx=2)
        frame.pack_propagate(False)
        
        # Inner layout
        inner = ctk.CTkFrame(frame, fg_color="transparent")
        inner.pack(fill="both", expand=True, padx=12, pady=8)
        
        # Top row: app icon + app name + time + dismiss button
        top_row = ctk.CTkFrame(inner, fg_color="transparent")
        top_row.pack(fill="x")
        
        ctk.CTkLabel(top_row, text=f"{app_icon}  {app_name}", font=("Segoe UI Semibold", 12),
                     text_color=COLORS["accent_bright"]).pack(side="left")
        
        ctk.CTkLabel(top_row, text=time_str, font=("Consolas", 10),
                     text_color=COLORS["text_dim"]).pack(side="right", padx=(8, 0))
        
        dismiss_btn = ctk.CTkButton(top_row, text="✕", width=24, height=24, font=("Segoe UI", 11),
                                     fg_color=COLORS["danger"], hover_color="#ff4444",
                                     command=lambda k=key: self._dismiss_notification(k))
        dismiss_btn.pack(side="right", padx=(4, 0))
        
        # Bottom row: title + body
        content_row = ctk.CTkFrame(inner, fg_color="transparent")
        content_row.pack(fill="x", pady=(4, 0))
        
        title_text = title if len(title) <= 40 else title[:37] + "..."
        body_text = body if len(body) <= 80 else body[:77] + "..."
        display_text = f"{title_text}" + (f"  —  {body_text}" if body_text else "")
        
        ctk.CTkLabel(content_row, text=display_text, font=("Segoe UI", 12),
                     text_color=COLORS["text"], anchor="w").pack(side="left", fill="x", expand=True)
        
        self.notif_card_widgets[key] = frame
        
        # Update count
        count = notification_manager.get_active_count()
        self.notif_count_label.configure(text=f"{count} notification{'s' if count != 1 else ''}")
    
    def _dismiss_notification(self, key):
        """Dismiss a notification from the PC (also dismisses on phone)."""
        notification_manager.dismiss_from_pc(key)
    
    def _on_notification_dismissed(self, key):
        """Update UI when a notification is dismissed (from phone or PC)."""
        if key in self.notif_card_widgets:
            frame = self.notif_card_widgets[key]
            frame.configure(fg_color="#1a1a2e")  # Dim the card
            # Add strikethrough effect by changing text color
            for widget in frame.winfo_children():
                try:
                    for inner_widget in widget.winfo_children():
                        for w in inner_widget.winfo_children():
                            if isinstance(w, ctk.CTkLabel):
                                w.configure(text_color=COLORS["text_dim"])
                            if isinstance(w, ctk.CTkButton):
                                w.configure(state="disabled")
                except Exception:
                    pass
            del self.notif_card_widgets[key]
        
        # Update count
        count = notification_manager.get_active_count()
        self.notif_count_label.configure(text=f"{count} notification{'s' if count != 1 else ''}")
        
        # Show empty state if no active notifications
        if count == 0 and not self.notif_card_widgets:
            self.notif_empty_label.pack(expand=True, pady=60)
    
    def _clear_all_notifications(self):
        """Clear all notification cards."""
        for frame in self.notif_card_widgets.values():
            frame.destroy()
        self.notif_card_widgets.clear()
        notification_manager.clear_all()
        self.notif_count_label.configure(text="0 notifications")
        self.notif_empty_label.pack(expand=True, pady=60)

    # ─── QR PAIRING SECTION ─────────────────────────────────────
    def _build_qr_section(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="x", pady=(0, 8))
        
        ctk.CTkLabel(card, text="QR Pairing", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=15, pady=(12, 5), anchor="w")
        
        # QR Image
        self.qr_label = ctk.CTkLabel(card, text="Generating QR...", width=220, height=220)
        self.qr_label.pack(padx=15, pady=5)
        
        # IP display
        ip = qr_pairing.get_local_ip()
        hostname = qr_pairing.get_hostname()
        self.ip_label = ctk.CTkLabel(card, text=f"{hostname} • {ip}", 
                                      font=("Consolas", 12), text_color=COLORS["text_dim"])
        self.ip_label.pack(padx=15, pady=(0, 5))
        
        # Buttons
        btn_frame = ctk.CTkFrame(card, fg_color="transparent")
        btn_frame.pack(fill="x", padx=15, pady=(0, 12))
        
        ctk.CTkButton(btn_frame, text="Refresh QR", width=120, height=30,
                      font=("Segoe UI", 12), fg_color=COLORS["accent"],
                      hover_color=COLORS["accent_bright"],
                      command=self._refresh_qr).pack(side="left", padx=(0, 5))
        
        ctk.CTkButton(btn_frame, text="New Keys", width=120, height=30,
                      font=("Segoe UI", 12), fg_color=COLORS["danger"],
                      hover_color="#ff4444",
                      command=self._regenerate_keys).pack(side="right")
        
        # Generate initial QR
        self.after(100, self._refresh_qr)

    # ─── CONNECTION SECTION ─────────────────────────────────────
    def _build_connection_section(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="x", pady=(0, 8))
        
        ctk.CTkLabel(card, text="Connection", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=15, pady=(12, 5), anchor="w")
        
        # Connected phone info
        self.phone_info_label = ctk.CTkLabel(card, text="No phone connected",
                                              font=("Segoe UI", 12), text_color=COLORS["text_dim"])
        self.phone_info_label.pack(padx=15, pady=2, anchor="w")
        
        self.conn_time_label = ctk.CTkLabel(card, text="",
                                             font=("Segoe UI", 11), text_color=COLORS["text_dim"])
        self.conn_time_label.pack(padx=15, pady=(0, 5), anchor="w")
        
        # Disconnect button only in connection section
        btn_frame = ctk.CTkFrame(card, fg_color="transparent")
        btn_frame.pack(fill="x", padx=15, pady=(0, 8))
        
        self.disconnect_btn = ctk.CTkButton(btn_frame, text="Disconnect Phone", height=30,
                                             font=("Segoe UI", 12),
                                             fg_color=COLORS["danger"], hover_color="#ff4444",
                                             command=self._disconnect_phone, state="disabled")
        self.disconnect_btn.pack(fill="x")
        
        # Info row
        info_frame = ctk.CTkFrame(card, fg_color="transparent")
        info_frame.pack(fill="x", padx=15, pady=(0, 12))
        
        self.latency_label = ctk.CTkLabel(info_frame, text="Latency: —",
                                           font=("Consolas", 11), text_color=COLORS["text_dim"])
        self.latency_label.pack(side="left")
    
    # ─── SERVER CONTROLS SECTION ────────────────────────────────
    def _build_server_controls(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="x", pady=(0, 8))
        
        ctk.CTkLabel(card, text="Server Controls", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=15, pady=(12, 8), anchor="w")
        
        btn_frame = ctk.CTkFrame(card, fg_color="transparent")
        btn_frame.pack(fill="x", padx=15, pady=(0, 12))
        
        self.server_btn = ctk.CTkButton(btn_frame, text="▶  Start Server", height=40,
                                         font=("Segoe UI Semibold", 14),
                                         fg_color=COLORS["success"], hover_color="#00e676",
                                         text_color="#000000",
                                         command=self._toggle_server)
        self.server_btn.pack(fill="x", pady=(0, 8))
        
        # Quick PC controls
        ctk.CTkLabel(btn_frame, text="PC Quick Controls", font=("Segoe UI", 12),
                     text_color=COLORS["text_dim"]).pack(anchor="w", pady=(8, 4))
        
        pc_row = ctk.CTkFrame(btn_frame, fg_color="transparent")
        pc_row.pack(fill="x")
        
        ctk.CTkButton(pc_row, text="🔒 Lock PC", width=100, height=32,
                      font=("Segoe UI", 11), fg_color=COLORS["accent"],
                      hover_color=COLORS["accent_bright"],
                      command=self._lock_pc).pack(side="left", padx=(0, 4))
        
        ctk.CTkButton(pc_row, text="💤 Sleep", width=100, height=32,
                      font=("Segoe UI", 11), fg_color=COLORS["accent"],
                      hover_color=COLORS["accent_bright"],
                      command=self._sleep_pc).pack(side="left", padx=(0, 4))
        
        ctk.CTkButton(pc_row, text="🚨 Panic", width=100, height=32,
                      font=("Segoe UI", 11), fg_color=COLORS["warning"],
                      hover_color="#ffc107", text_color="#000000",
                      command=self._panic_mode).pack(side="left")
        
        # Server stats
        ctk.CTkFrame(btn_frame, height=1, fg_color=COLORS["border"]).pack(fill="x", pady=(12, 8))
        
        ctk.CTkLabel(btn_frame, text="Server Stats", font=("Segoe UI", 12),
                     text_color=COLORS["text_dim"]).pack(anchor="w", pady=(0, 4))
        
        stats_row = ctk.CTkFrame(btn_frame, fg_color="transparent")
        stats_row.pack(fill="x")
        
        self.uptime_label = ctk.CTkLabel(stats_row, text="Uptime: —", font=("Consolas", 11),
                                          text_color=COLORS["text_dim"])
        self.uptime_label.pack(side="left")
        
        self.cmd_count_label = ctk.CTkLabel(stats_row, text="Commands: 0", font=("Consolas", 11),
                                             text_color=COLORS["text_dim"])
        self.cmd_count_label.pack(side="right")

    # ─── PHONE CONTROLS ────────────────────────────────────────
    def _build_phone_controls(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="both", expand=True, pady=(0, 8), padx=8)
        
        ctk.CTkLabel(card, text="Phone Controls", font=("Segoe UI Semibold", 18),
                     text_color=COLORS["accent_bright"]).pack(padx=20, pady=(16, 12), anchor="w")
        
        # Row 1: Vibrate, Ring, Find
        row1 = ctk.CTkFrame(card, fg_color="transparent")
        row1.pack(fill="x", padx=20, pady=(0, 8))
        
        self._make_control_btn(row1, "📳  Vibrate", self._vibrate_phone).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row1, "🔔  Ring", self._ring_phone).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row1, "📍  Find Phone", self._find_phone, color=COLORS["warning"]).pack(side="left", expand=True, fill="x")

        # Row 2: Flashlight, Flash Screen, Lock
        row2 = ctk.CTkFrame(card, fg_color="transparent")
        row2.pack(fill="x", padx=20, pady=(0, 8))
        
        self._make_control_btn(row2, "🔦  Flashlight", self._toggle_flashlight).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row2, "💡  Flash Screen", self._flash_screen).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row2, "🔒  Lock Phone", self._lock_phone).pack(side="left", expand=True, fill="x")
        
        # Row 3: TTS, Screenshot, Camera
        row3 = ctk.CTkFrame(card, fg_color="transparent")
        row3.pack(fill="x", padx=20, pady=(0, 8))
        
        self._make_control_btn(row3, "🗣  Speak Text", self._speak_on_phone).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row3, "📸  Screenshot", self._phone_screenshot).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row3, "📷  Camera", self._toggle_camera).pack(side="left", expand=True, fill="x")
        
        # Row 4: Volume & Brightness sliders
        slider_frame = ctk.CTkFrame(card, fg_color="transparent")
        slider_frame.pack(fill="x", padx=20, pady=(12, 8))
        
        ctk.CTkLabel(slider_frame, text="Phone Volume:", font=("Segoe UI", 13),
                     text_color=COLORS["text_dim"]).pack(anchor="w")
        self.vol_slider = ctk.CTkSlider(slider_frame, from_=0, to=15, number_of_steps=15,
                                         progress_color=COLORS["accent_bright"],
                                         command=self._on_volume_change)
        self.vol_slider.set(8)
        self.vol_slider.pack(fill="x", pady=(4, 10))
        
        ctk.CTkLabel(slider_frame, text="Phone Brightness:", font=("Segoe UI", 13),
                     text_color=COLORS["text_dim"]).pack(anchor="w")
        self.bright_slider = ctk.CTkSlider(slider_frame, from_=0, to=255, number_of_steps=51,
                                            progress_color=COLORS["accent_bright"],
                                            command=self._on_brightness_change)
        self.bright_slider.set(128)
        self.bright_slider.pack(fill="x", pady=(4, 16))

    # ─── QUICK ACTIONS ──────────────────────────────────────────
    def _build_quick_actions(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="both", expand=True, pady=(0, 8), padx=8)
        
        ctk.CTkLabel(card, text="Quick Actions", font=("Segoe UI Semibold", 18),
                     text_color=COLORS["accent_bright"]).pack(padx=20, pady=(16, 12), anchor="w")
        
        # Row: Send File, Clipboard, Open URL
        row1 = ctk.CTkFrame(card, fg_color="transparent")
        row1.pack(fill="x", padx=20, pady=(0, 8))
        
        self._make_control_btn(row1, "📁  Send File", self._send_file_to_phone).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row1, "📋  Sync Clipboard", self._sync_clipboard).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row1, "🌐  Open URL", self._open_url_on_phone).pack(side="left", expand=True, fill="x")
        
        row2 = ctk.CTkFrame(card, fg_color="transparent")
        row2.pack(fill="x", padx=20, pady=(0, 8))
        
        self._make_control_btn(row2, "🔔  Send Notification", self._send_notification).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row2, "💬  Toast Message", self._send_toast).pack(side="left", expand=True, fill="x", padx=(0, 4))
        self._make_control_btn(row2, "⏰  Stop Ring", self._stop_ring).pack(side="left", expand=True, fill="x")

        # Message input
        msg_frame = ctk.CTkFrame(card, fg_color="transparent")
        msg_frame.pack(fill="x", padx=20, pady=(12, 8))
        
        ctk.CTkLabel(msg_frame, text="Message / URL / Text:", font=("Segoe UI", 13),
                     text_color=COLORS["text_dim"]).pack(anchor="w", pady=(0, 4))
        
        self.msg_entry = ctk.CTkEntry(msg_frame, placeholder_text="Type a message/URL/text...",
                                       height=36, font=("Segoe UI", 13),
                                       fg_color=COLORS["bg_dark"], border_color=COLORS["border"])
        self.msg_entry.pack(fill="x", pady=(0, 4))
        
        quick_row = ctk.CTkFrame(msg_frame, fg_color="transparent")
        quick_row.pack(fill="x")
        
        ctk.CTkButton(quick_row, text="Send as Toast", height=28, font=("Segoe UI", 11),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._quick_send("toast")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(quick_row, text="Send as TTS", height=28, font=("Segoe UI", 11),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._quick_send("tts")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(quick_row, text="Open as URL", height=28, font=("Segoe UI", 11),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._quick_send("url")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(quick_row, text="Clipboard", height=28, font=("Segoe UI", 11),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._quick_send("clip")).pack(side="left")

    # ─── SYSTEM INFO ────────────────────────────────────────────
    def _build_system_info(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="x", pady=(0, 8))
        
        ctk.CTkLabel(card, text="PC System", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=15, pady=(12, 6), anchor="w")
        
        self.sys_labels = {}
        for key in ["CPU", "RAM", "Disk", "Battery", "Network"]:
            row = ctk.CTkFrame(card, fg_color="transparent")
            row.pack(fill="x", padx=15, pady=1)
            ctk.CTkLabel(row, text=f"{key}:", font=("Segoe UI", 12), width=70,
                         text_color=COLORS["text_dim"], anchor="w").pack(side="left")
            lbl = ctk.CTkLabel(row, text="—", font=("Consolas", 12),
                               text_color=COLORS["text"], anchor="w")
            lbl.pack(side="left", fill="x", expand=True)
            self.sys_labels[key] = lbl
        
        # Add empty padding at bottom
        ctk.CTkFrame(card, fg_color="transparent", height=8).pack()

    # ─── LOG PANEL ──────────────────────────────────────────────
    def _build_log_panel(self, parent):
        card = ctk.CTkFrame(parent, fg_color=COLORS["bg_card"], corner_radius=12)
        card.pack(fill="both", expand=True, padx=8, pady=8)
        
        header = ctk.CTkFrame(card, fg_color="transparent")
        header.pack(fill="x", padx=20, pady=(16, 8))
        
        ctk.CTkLabel(header, text="Server Logs", font=("Segoe UI Semibold", 18),
                     text_color=COLORS["accent_bright"]).pack(side="left")
        
        ctk.CTkButton(header, text="Clear Logs", width=80, height=28, font=("Segoe UI", 12),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._clear_logs).pack(side="right", padx=(0, 4))
        
        ctk.CTkButton(header, text="Export", width=70, height=28, font=("Segoe UI", 12),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._export_logs).pack(side="right")
        
        self.log_textbox = ctk.CTkTextbox(card, font=("Consolas", 12),
                                           fg_color=COLORS["bg_dark"],
                                           text_color=COLORS["text"],
                                           border_color=COLORS["border"],
                                           border_width=1, corner_radius=8,
                                           state="disabled", wrap="word")
        self.log_textbox.pack(fill="both", expand=True, padx=16, pady=(0, 16))

    # ─── TASKS PANEL ────────────────────────────────────────────
    def _build_tasks_panel(self, parent):
        main_frame = ctk.CTkFrame(parent, fg_color="transparent")
        main_frame.pack(fill="both", expand=True, padx=8, pady=8)
        main_frame.grid_columnconfigure(0, weight=2)
        main_frame.grid_columnconfigure(1, weight=1)
        main_frame.grid_rowconfigure(0, weight=1)
        
        # Left: Task List
        left_card = ctk.CTkFrame(main_frame, fg_color=COLORS["bg_card"], corner_radius=12)
        left_card.grid(row=0, column=0, sticky="nsew", padx=(0, 6), pady=0)
        
        # Header
        header = ctk.CTkFrame(left_card, fg_color="transparent")
        header.pack(fill="x", padx=16, pady=(12, 8))
        
        ctk.CTkLabel(header, text="📋 Task List", font=("Segoe UI Semibold", 18),
                     text_color=COLORS["accent_bright"]).pack(side="left")
        
        self.task_count_label = ctk.CTkLabel(header, text="0 tasks", 
                                              font=("Segoe UI", 12), text_color=COLORS["text_dim"])
        self.task_count_label.pack(side="right")
        
        # Scrollable task list
        self.tasks_scroll = ctk.CTkScrollableFrame(left_card, fg_color=COLORS["bg_dark"],
                                                    corner_radius=8)
        self.tasks_scroll.pack(fill="both", expand=True, padx=16, pady=(0, 12))
        
        # Task widgets container
        self.task_widgets = {}
        
        # Right: Add Task & Controls
        right_card = ctk.CTkFrame(main_frame, fg_color=COLORS["bg_card"], corner_radius=12)
        right_card.grid(row=0, column=1, sticky="nsew", padx=0, pady=0)
        
        # Add Task Section
        ctk.CTkLabel(right_card, text="Add New Task", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=16, pady=(12, 8), anchor="w")
        
        # Task title input
        self.task_entry = ctk.CTkEntry(right_card, placeholder_text="Enter task title...",
                                        height=38, font=("Segoe UI", 13),
                                        fg_color=COLORS["bg_dark"], border_color=COLORS["border"])
        self.task_entry.pack(fill="x", padx=16, pady=(0, 8))
        self.task_entry.bind("<Return>", lambda e: self._add_task())
        
        # Priority selector
        priority_frame = ctk.CTkFrame(right_card, fg_color="transparent")
        priority_frame.pack(fill="x", padx=16, pady=(0, 8))
        
        ctk.CTkLabel(priority_frame, text="Priority:", font=("Segoe UI", 12),
                     text_color=COLORS["text_dim"]).pack(side="left")
        
        self.priority_var = ctk.StringVar(value="normal")
        self.priority_menu = ctk.CTkOptionMenu(priority_frame, values=["low", "normal", "high"],
                                                variable=self.priority_var, width=100,
                                                fg_color=COLORS["accent"],
                                                button_color=COLORS["accent_bright"],
                                                button_hover_color=COLORS["accent"])
        self.priority_menu.pack(side="right")
        
        # Due Date selector
        date_frame = ctk.CTkFrame(right_card, fg_color="transparent")
        date_frame.pack(fill="x", padx=16, pady=(0, 8))
        
        ctk.CTkLabel(date_frame, text="Due Date:", font=("Segoe UI", 12),
                     text_color=COLORS["text_dim"]).pack(side="left")
        
        self.due_date_entry = ctk.CTkEntry(date_frame, placeholder_text="YYYY-MM-DD",
                                            width=110, height=28, font=("Consolas", 11),
                                            fg_color=COLORS["bg_dark"], border_color=COLORS["border"])
        self.due_date_entry.pack(side="right")
        
        # Quick date buttons
        date_btn_frame = ctk.CTkFrame(right_card, fg_color="transparent")
        date_btn_frame.pack(fill="x", padx=16, pady=(0, 8))
        
        ctk.CTkButton(date_btn_frame, text="Today", width=60, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._set_due_today).pack(side="left", padx=(0, 4))
        ctk.CTkButton(date_btn_frame, text="Tomorrow", width=70, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._set_due_tomorrow).pack(side="left", padx=(0, 4))
        ctk.CTkButton(date_btn_frame, text="Clear", width=50, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["danger"], hover_color="#ff4444",
                      command=self._clear_due_date).pack(side="left")
        
        # Due Time selector
        time_frame = ctk.CTkFrame(right_card, fg_color="transparent")
        time_frame.pack(fill="x", padx=16, pady=(0, 8))
        
        ctk.CTkLabel(time_frame, text="Due Time:", font=("Segoe UI", 12),
                     text_color=COLORS["text_dim"]).pack(side="left")
        
        self.due_time_entry = ctk.CTkEntry(time_frame, placeholder_text="HH:MM",
                                            width=70, height=28, font=("Consolas", 11),
                                            fg_color=COLORS["bg_dark"], border_color=COLORS["border"])
        self.due_time_entry.pack(side="right")
        
        # Quick time buttons
        time_btn_frame = ctk.CTkFrame(right_card, fg_color="transparent")
        time_btn_frame.pack(fill="x", padx=16, pady=(0, 8))
        
        ctk.CTkButton(time_btn_frame, text="9:00", width=45, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._set_due_time("09:00")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(time_btn_frame, text="12:00", width=50, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._set_due_time("12:00")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(time_btn_frame, text="17:00", width=50, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=lambda: self._set_due_time("17:00")).pack(side="left", padx=(0, 4))
        ctk.CTkButton(time_btn_frame, text="Clear", width=45, height=24, font=("Segoe UI", 10),
                      fg_color=COLORS["danger"], hover_color="#ff4444",
                      command=lambda: self.due_time_entry.delete(0, "end")).pack(side="left")
        
        # Add button
        ctk.CTkButton(right_card, text="➕  Add Task", height=40, font=("Segoe UI Semibold", 14),
                      fg_color=COLORS["success"], hover_color="#00e676", text_color="#000000",
                      command=self._add_task).pack(fill="x", padx=16, pady=(0, 16))
        
        # Divider
        ctk.CTkFrame(right_card, height=2, fg_color=COLORS["border"]).pack(fill="x", padx=16)
        
        # Actions Section
        ctk.CTkLabel(right_card, text="Actions", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=16, pady=(12, 8), anchor="w")
        
        ctk.CTkButton(right_card, text="🔄  Sync to Phone", height=36, font=("Segoe UI", 12),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._sync_tasks_to_phone).pack(fill="x", padx=16, pady=(0, 6))
        
        ctk.CTkButton(right_card, text="🗑  Clear Completed", height=36, font=("Segoe UI", 12),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._clear_completed_tasks).pack(fill="x", padx=16, pady=(0, 6))
        
        ctk.CTkButton(right_card, text="📥  Refresh List", height=36, font=("Segoe UI", 12),
                      fg_color=COLORS["accent"], hover_color=COLORS["accent_bright"],
                      command=self._refresh_tasks).pack(fill="x", padx=16, pady=(0, 16))
        
        # Stats Section
        ctk.CTkFrame(right_card, height=2, fg_color=COLORS["border"]).pack(fill="x", padx=16)
        
        ctk.CTkLabel(right_card, text="Statistics", font=("Segoe UI Semibold", 15),
                     text_color=COLORS["accent_bright"]).pack(padx=16, pady=(12, 8), anchor="w")
        
        self.stats_labels = {}
        for key in ["Pending", "Completed", "High Priority"]:
            row = ctk.CTkFrame(right_card, fg_color="transparent")
            row.pack(fill="x", padx=16, pady=2)
            ctk.CTkLabel(row, text=f"{key}:", font=("Segoe UI", 12),
                         text_color=COLORS["text_dim"]).pack(side="left")
            lbl = ctk.CTkLabel(row, text="0", font=("Segoe UI Semibold", 12),
                               text_color=COLORS["text"])
            lbl.pack(side="right")
            self.stats_labels[key] = lbl
        
        ctk.CTkFrame(right_card, height=12, fg_color="transparent").pack()
        
        # Initial load
        self.after(100, self._refresh_tasks)

    def _create_task_widget(self, task: dict):
        """Create a task item widget."""
        task_id = task["id"]
        
        frame = ctk.CTkFrame(self.tasks_scroll, fg_color=COLORS["bg_card"], corner_radius=8, height=55)
        frame.pack(fill="x", pady=3, padx=2)
        frame.pack_propagate(False)
        
        # Checkbox
        completed = task.get("completed", False)
        cb_var = ctk.BooleanVar(value=completed)
        cb = ctk.CTkCheckBox(frame, text="", variable=cb_var, width=24,
                             checkbox_width=22, checkbox_height=22,
                             fg_color=COLORS["success"], hover_color=COLORS["accent_bright"],
                             command=lambda tid=task_id, var=cb_var: self._toggle_task(tid, var.get()))
        cb.pack(side="left", padx=(10, 8), pady=10)
        
        # Task title
        title = task.get("title", "Untitled")
        text_color = COLORS["text_dim"] if completed else COLORS["text"]
        title_lbl = ctk.CTkLabel(frame, text=title, font=("Segoe UI", 13), 
                                  text_color=text_color, anchor="w")
        title_lbl.pack(side="left", fill="x", expand=True, padx=(0, 10))
        
        # Priority indicator
        priority = task.get("priority", "normal")
        priority_colors = {"high": COLORS["danger"], "normal": COLORS["accent_bright"], "low": COLORS["text_dim"]}
        priority_labels = {"high": "⚡", "normal": "", "low": "↓"}
        if priority_labels[priority]:
            p_lbl = ctk.CTkLabel(frame, text=priority_labels[priority], font=("Segoe UI", 14),
                                  text_color=priority_colors[priority])
            p_lbl.pack(side="left", padx=(0, 8))
        
        # Due date/time indicator
        due_date = task.get("due_date")
        due_time = task.get("due_time")
        if due_date or due_time:
            due_text = ""
            if due_date:
                due_text = due_date
            if due_time:
                due_text += f" {due_time}" if due_text else due_time
            due_lbl = ctk.CTkLabel(frame, text=f"🗓 {due_text}", font=("Segoe UI", 10),
                                    text_color=COLORS["warning"])
            due_lbl.pack(side="left", padx=(0, 8))
        
        # Source indicator (mobile/pc)
        source = task.get("source", "pc")
        source_icon = "📱" if source == "mobile" else "💻"
        src_lbl = ctk.CTkLabel(frame, text=source_icon, font=("Segoe UI", 12),
                                text_color=COLORS["text_dim"])
        src_lbl.pack(side="left", padx=(0, 8))
        
        # Delete button
        del_btn = ctk.CTkButton(frame, text="✕", width=28, height=28, font=("Segoe UI", 12),
                                fg_color=COLORS["danger"], hover_color="#ff4444",
                                command=lambda tid=task_id: self._delete_task(tid))
        del_btn.pack(side="right", padx=(0, 10))
        
        self.task_widgets[task_id] = frame

    def _add_task(self):
        """Add a new task from PC."""
        title = self.task_entry.get().strip()
        if not title:
            self._append_log("Enter a task title first")
            return
        
        priority = self.priority_var.get()
        due_date = self.due_date_entry.get().strip() or None
        due_time = self.due_time_entry.get().strip() or None
        
        task = task_manager.add_task(title, source="pc", priority=priority, 
                                      due_date=due_date, due_time=due_time)
        
        # Build log message
        log_msg = f"[Tasks] Added: {title}"
        if due_date:
            log_msg += f" (due: {due_date}"
            if due_time:
                log_msg += f" {due_time}"
            log_msg += ")"
        
        self.task_entry.delete(0, "end")
        self.due_date_entry.delete(0, "end")
        self.due_time_entry.delete(0, "end")
        self._append_log(log_msg)
        self._refresh_tasks()

    def _set_due_today(self):
        """Set due date to today."""
        today = datetime.now().strftime("%Y-%m-%d")
        self.due_date_entry.delete(0, "end")
        self.due_date_entry.insert(0, today)
    
    def _set_due_tomorrow(self):
        """Set due date to tomorrow."""
        from datetime import timedelta
        tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
        self.due_date_entry.delete(0, "end")
        self.due_date_entry.insert(0, tomorrow)
    
    def _clear_due_date(self):
        """Clear the due date field."""
        self.due_date_entry.delete(0, "end")
    
    def _set_due_time(self, time_str: str):
        """Set due time to specified value."""
        self.due_time_entry.delete(0, "end")
        self.due_time_entry.insert(0, time_str)

    def _toggle_task(self, task_id: int, completed: bool):
        """Toggle task completion status."""
        if completed:
            task_manager.complete_task(task_id, source="pc")
            self._append_log(f"[Tasks] Task completed!")
        self._refresh_tasks()

    def _delete_task(self, task_id: int):
        """Delete a task."""
        task_manager.delete_task(task_id, source="pc")
        self._append_log(f"[Tasks] Task deleted")
        self._refresh_tasks()

    def _sync_tasks_to_phone(self):
        """Sync all tasks to mobile."""
        if not reverse_commands.is_connected():
            self._append_log("No phone connected for sync")
            return
        if task_manager.sync_tasks_to_mobile():
            self._append_log("[Tasks] Synced to phone")
        else:
            self._append_log("[Tasks] Sync failed")

    def _clear_completed_tasks(self):
        """Clear all completed tasks."""
        count = task_manager.clear_completed()
        self._append_log(f"[Tasks] Cleared {count} completed tasks")
        self._refresh_tasks()

    def _refresh_tasks(self):
        """Refresh the task list display."""
        # Clear existing widgets
        for widget in self.task_widgets.values():
            widget.destroy()
        self.task_widgets.clear()
        
        # Get all tasks
        tasks = task_manager.get_tasks()
        
        # Sort: incomplete first, then by priority, then by creation time
        priority_order = {"high": 0, "normal": 1, "low": 2}
        tasks.sort(key=lambda t: (t.get("completed", False), 
                                   priority_order.get(t.get("priority", "normal"), 1),
                                   t.get("created_at", "")))
        
        # Create widgets
        for task in tasks:
            self._create_task_widget(task)
        
        # Update count
        total = len(tasks)
        pending = sum(1 for t in tasks if not t.get("completed", False))
        self.task_count_label.configure(text=f"{pending} pending / {total} total")
        
        # Update stats
        completed = sum(1 for t in tasks if t.get("completed", False))
        high_priority = sum(1 for t in tasks if t.get("priority") == "high" and not t.get("completed", False))
        
        self.stats_labels["Pending"].configure(text=str(pending))
        self.stats_labels["Completed"].configure(text=str(completed))
        self.stats_labels["High Priority"].configure(text=str(high_priority))

    def _on_task_event(self, *args):
        """Callback when task events occur - refresh the UI."""
        # Use after() to ensure we're on the main thread
        try:
            self.after(0, self._refresh_tasks)
        except Exception:
            pass

    # ─── FOOTER ─────────────────────────────────────────────────
    def _build_footer(self):
        footer = ctk.CTkFrame(self, height=30, fg_color=COLORS["bg_card"], corner_radius=0)
        footer.pack(fill="x", side="bottom")
        footer.pack_propagate(False)
        
        self.footer_label = ctk.CTkLabel(footer, text="Ready", font=("Segoe UI", 11),
                                          text_color=COLORS["text_dim"])
        self.footer_label.pack(side="left", padx=15)
        
        self.time_label = ctk.CTkLabel(footer, text="", font=("Consolas", 11),
                                        text_color=COLORS["text_dim"])
        self.time_label.pack(side="right", padx=15)
        self._update_clock()

    # ─── HELPER: CREATE STYLED BUTTON ───────────────────────────
    def _make_control_btn(self, parent, text, command, color=None):
        return ctk.CTkButton(parent, text=text, height=38, font=("Segoe UI", 12),
                             fg_color=color or COLORS["accent"],
                             hover_color=COLORS["accent_bright"],
                             corner_radius=8, command=command)

    # ─── QR CODE ACTIONS ────────────────────────────────────────
    def _refresh_qr(self):
        try:
            img = qr_pairing.generate_qr_image(220)
            photo = ctk.CTkImage(light_image=img, dark_image=img, size=(220, 220))
            self.qr_label.configure(image=photo, text="")
            self.qr_label._image = photo  # Keep reference
            
            ip = qr_pairing.get_local_ip()
            hostname = qr_pairing.get_hostname()
            self.ip_label.configure(text=f"{hostname} • {ip}")
            self._append_log(f"QR refreshed — IP: {ip}")
        except Exception as e:
            self._append_log(f"QR Error: {e}")
    
    def _regenerate_keys(self):
        if messagebox.askyesno("Regenerate Keys", 
                                "This will invalidate all existing pairings.\nPhones will need to re-scan the QR code.\n\nContinue?"):
            qr_pairing.regenerate_keys()
            self._refresh_qr()
            self._append_log("Security keys regenerated. Old pairings invalidated.")

    # ─── SERVER CONTROL ─────────────────────────────────────────
    def _toggle_server(self):
        if not self.server_running:
            self._start_server()
        else:
            self._stop_server()
    
    def _start_server(self):
        """Start the UDP command server embedded in this process."""
        try:
            if self.embedded_server.start():
                self.server_running = True
                self.server_btn.configure(text="⏹  Stop Server", fg_color=COLORS["danger"], 
                                           hover_color="#ff4444")
                self.server_status.set_status(True, "Server: Running")
                self._append_log("Server started (embedded mode — no separate console)")
                self.footer_label.configure(text="Server running (embedded)")
                self._update_server_stats()
                
                # Start the reverse command listener
                threading.Thread(target=self._reverse_listener, daemon=True).start()
                
                # Tray notification
                self._tray_notify("Server Started", "Mobile Controller server is now running")
            else:
                self._append_log("Server is already running")
        except Exception as e:
            self._append_log(f"Server start failed: {e}")
    
    def _stop_server(self):
        try:
            self.embedded_server.stop()
            self.server_running = False
            self.server_btn.configure(text="▶  Start Server", fg_color=COLORS["success"],
                                       hover_color="#00e676")
            self.server_status.set_status(False, "Server: Stopped")
            self._append_log("Server stopped")
            self.footer_label.configure(text="Server stopped")
            self.uptime_label.configure(text="Uptime: —")
            self.cmd_count_label.configure(text="Commands: 0")
            
            # Tray notification
            self._tray_notify("Server Stopped", "Mobile Controller server has been stopped")
        except Exception as e:
            self._append_log(f"Server stop failed: {e}")
    
    def _disconnect_phone(self):
        reverse_commands.disconnect()
        self.phone_connected = False
        self.phone_status.set_status(False, "Phone: Disconnected")
        self.phone_info_label.configure(text="No phone connected")
        self.conn_time_label.configure(text="")
        self.disconnect_btn.configure(state="disabled")
        self._append_log("Phone disconnected")
        self._tray_notify("Phone Disconnected", "Device has been disconnected")

    # ─── EMBEDDED SERVER CALLBACKS ──────────────────────────────
    
    def _on_server_remote_stop(self):
        """Called when phone sends STOP_SERVER command."""
        self.server_running = False
        self.server_btn.configure(text="▶  Start Server", fg_color=COLORS["success"],
                                   hover_color="#00e676")
        self.server_status.set_status(False, "Server: Stopped")
        self.footer_label.configure(text="Server stopped (remote)")
        self.uptime_label.configure(text="Uptime: —")
        self.cmd_count_label.configure(text="Commands: 0")
        self._append_log("Server stopped by remote command")
        self._tray_notify("Server Stopped", "Server was stopped by remote command")
    
    def _update_server_stats(self):
        """Periodically update server statistics display."""
        if self.embedded_server.is_running:
            self.uptime_label.configure(text=f"Uptime: {self.embedded_server.uptime_str}")
            self.cmd_count_label.configure(text=f"Commands: {self.embedded_server.command_count}")
            self.after(1000, self._update_server_stats)

    # ─── SYSTEM TRAY ────────────────────────────────────────────
    
    def _setup_tray_icon(self):
        """Initialize the system tray icon with context menu."""
        if not HAS_TRAY:
            return
        
        try:
            image = self._create_tray_image()
            
            menu = pystray.Menu(
                pystray.MenuItem("Show Window", self._tray_show, default=True),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem(
                    lambda item: "Stop Server" if self.server_running else "Start Server",
                    self._tray_toggle_server
                ),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Quit", self._tray_quit)
            )
            
            self.tray_icon = pystray.Icon("MobileController", image, "Mobile Controller", menu)
            threading.Thread(target=self.tray_icon.run, daemon=True, name="TrayIcon").start()
            self._append_log("System tray icon active — minimize to stay running in background")
        except Exception as e:
            self._append_log(f"Tray icon setup failed: {e}")
            self.tray_icon = None
    
    def _create_tray_image(self):
        """Generate a small tray icon image using PIL."""
        size = 64
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        
        # Background circle — accent teal
        draw.ellipse([2, 2, size - 2, size - 2], fill='#00adb5', outline='#0f3460', width=3)
        
        # Phone shape (white rectangle with dark screen)
        draw.rectangle([20, 12, 44, 52], fill='white')
        draw.rectangle([23, 18, 41, 44], fill='#1a1a2e')  # Screen
        draw.ellipse([29, 47, 35, 53], fill='#888888')     # Home button
        
        return img
    
    def _tray_show(self, icon=None, item=None):
        """Show the main window from tray."""
        self.after(0, self._restore_window)
    
    def _restore_window(self):
        """Restore the window to foreground."""
        self.deiconify()
        self.lift()
        self.focus_force()
        self._minimized_to_tray = False
    
    def _tray_toggle_server(self, icon=None, item=None):
        """Toggle server from tray menu."""
        self.after(0, self._toggle_server)
    
    def _tray_quit(self, icon=None, item=None):
        """Quit the application from tray."""
        self.after(0, self._full_shutdown)
    
    def _tray_notify(self, title, message):
        """Show a system tray balloon notification."""
        if HAS_TRAY and self.tray_icon:
            try:
                self.tray_icon.notify(message, title)
            except Exception:
                pass

    # ─── PC CONTROL HANDLERS ────────────────────────────────────
    def _lock_pc(self):
        """Lock the PC."""
        import ctypes
        try:
            ctypes.windll.user32.LockWorkStation()
            self._append_log("[PC] Screen locked")
        except Exception as e:
            self._append_log(f"Lock failed: {e}")
    
    def _sleep_pc(self):
        """Put PC to sleep."""
        import subprocess
        try:
            subprocess.run(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"], check=True)
            self._append_log("[PC] Going to sleep...")
        except Exception as e:
            self._append_log(f"Sleep failed: {e}")
    
    def _panic_mode(self):
        """Emergency mode: minimize all, mute, dim screen."""
        import pyautogui
        import screen_brightness_control as sbc
        try:
            pyautogui.hotkey('win', 'd')  # Minimize all
            pyautogui.press('volumemute')  # Mute
            pyautogui.press('playpause')  # Pause media
            sbc.set_brightness(0)  # Dim screen
            self._append_log("[PC] PANIC MODE activated!")
        except Exception as e:
            self._append_log(f"Panic mode error: {e}")

    # ─── REVERSE LISTENER (Phone → PC heartbet on port 6001) ───
    def _reverse_listener(self):
        """Listen for phone heartbeats / responses on port 6001."""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('0.0.0.0', 6001))
            sock.settimeout(1.0)
            
            while self.server_running:
                try:
                    data, addr = sock.recvfrom(4096)
                    message = data.decode('utf-8', errors='ignore').strip()
                    phone_ip = addr[0]
                    
                    # Phone heartbeat
                    if message.startswith("HEARTBEAT") or message == "KEEP_ALIVE_ACK":
                        reverse_commands.update_heartbeat()
                        if not self.phone_connected:
                            self.phone_connected = True
                            reverse_commands.set_phone_ip(phone_ip)
                            self._conn_time = datetime.now()
                            self.after(0, self._update_phone_connected, phone_ip)
                    
                    elif message.startswith("PHONE_INFO:"):
                        info = message[11:]
                        self.after(0, lambda i=info: self._append_log(f"[Phone] {i}"))
                    
                    elif message.startswith("PAIRED:"):
                        # Phone scanned QR and is confirming pairing  
                        reverse_commands.set_phone_ip(phone_ip)
                        self.phone_connected = True
                        self._conn_time = datetime.now()
                        self.after(0, self._update_phone_connected, phone_ip)
                        self.after(0, lambda: self._append_log(f"Phone paired via QR! ({phone_ip})"))
                    
                    # ── Chat messages from phone ──
                    elif message.startswith("CHAT_MSG:"):
                        chat_text = message[9:]
                        if hasattr(self, 'chat_tab'):
                            self.after(0, lambda t=chat_text: self.chat_tab.receive_message(t, "text"))
                    
                    elif message.startswith("CHAT_FILE:"):
                        file_info = message[10:]
                        if hasattr(self, 'chat_tab'):
                            self.after(0, lambda f=file_info: self.chat_tab.receive_message(f, "file", {"filename": os.path.basename(f)}))
                        
                except socket.timeout:
                    continue
                except Exception as e:
                    if self.server_running:
                        print(f"Reverse listener error: {e}")
            sock.close()
        except Exception as e:
            print(f"Could not start reverse listener: {e}")
    
    def _update_phone_connected(self, ip):
        self.phone_status.set_status(True, f"Phone: {ip}")
        self.phone_info_label.configure(text=f"Connected: {ip}")
        self.conn_time_label.configure(text=f"Since: {self._conn_time.strftime('%H:%M:%S')}")
        self.disconnect_btn.configure(state="normal")
        self._append_log(f"Phone connected: {ip}")
        self._tray_notify("Phone Connected", f"Device connected from {ip}")

    # ─── PHONE CONTROL HANDLERS ─────────────────────────────────
    def _check_phone(self):
        if not reverse_commands.is_connected():
            self._append_log("No phone connected!")
            return False
        return True
    
    def _vibrate_phone(self):
        if self._check_phone():
            reverse_commands.vibrate(500)
            self._append_log("[→ Phone] Vibrate")

    def _ring_phone(self):
        if self._check_phone():
            reverse_commands.ring_phone(5)
            self._append_log("[→ Phone] Ring for 5s")
    
    def _stop_ring(self):
        if self._check_phone():
            reverse_commands.stop_ring()
            self._append_log("[→ Phone] Stop ring")

    def _find_phone(self):
        if self._check_phone():
            reverse_commands.find_my_phone()
            self._append_log("[→ Phone] FIND MY PHONE activated!")

    def _toggle_flashlight(self):
        if self._check_phone():
            reverse_commands.toggle_flashlight(True)
            self._append_log("[→ Phone] Flashlight toggle")

    def _flash_screen(self):
        if self._check_phone():
            reverse_commands.flash_screen("FF0000", 1000)
            self._append_log("[→ Phone] Flash screen red")

    def _lock_phone(self):
        if self._check_phone():
            reverse_commands.lock_phone()
            self._append_log("[→ Phone] Lock screen")

    def _speak_on_phone(self):
        if self._check_phone():
            text = self.msg_entry.get().strip()
            if text:
                reverse_commands.play_tts(text)
                self._append_log(f"[→ Phone] TTS: {text[:40]}...")
                self.msg_entry.delete(0, "end")
            else:
                self._append_log("Enter text to speak first")

    def _phone_screenshot(self):
        if self._check_phone():
            reverse_commands.take_phone_screenshot()
            self._append_log("[→ Phone] Screenshot requested")

    def _toggle_camera(self):
        if self._check_phone():
            reverse_commands.start_camera_stream()
            self._append_log("[→ Phone] Camera stream started")

    def _on_volume_change(self, value):
        if reverse_commands.is_connected():
            reverse_commands.set_phone_volume("music", int(value))
    
    def _on_brightness_change(self, value):
        if reverse_commands.is_connected():
            reverse_commands.set_phone_brightness(int(value))

    # ─── QUICK ACTIONS ──────────────────────────────────────────
    def _send_file_to_phone(self):
        if not self._check_phone():
            return
        filepath = filedialog.askopenfilename(title="Select file to send to phone")
        if filepath:
            import file_service
            phone_ip = reverse_commands.get_phone_ip()
            threading.Thread(target=file_service.send_specific_file, 
                           args=(filepath, phone_ip), daemon=True).start()
            self._append_log(f"[→ Phone] Sending: {os.path.basename(filepath)}")

    def _sync_clipboard(self):
        if not self._check_phone():
            return
        try:
            import pyperclip
            text = pyperclip.paste()
            if text:
                reverse_commands.send_clipboard_to_phone(text)
                self._append_log(f"[→ Phone] Clipboard synced ({len(text)} chars)")
            else:
                self._append_log("PC clipboard is empty")
        except Exception as e:
            self._append_log(f"Clipboard error: {e}")
    
    def _open_url_on_phone(self):
        if self._check_phone():
            url = self.msg_entry.get().strip()
            if url:
                if not url.startswith("http"):
                    url = "https://" + url
                reverse_commands.open_url_on_phone(url)
                self._append_log(f"[→ Phone] Open URL: {url}")
                self.msg_entry.delete(0, "end")
            else:
                self._append_log("Enter a URL first")

    def _send_notification(self):
        if self._check_phone():
            text = self.msg_entry.get().strip()
            if text:
                reverse_commands.show_notification("PC Notification", text)
                self._append_log(f"[→ Phone] Notification: {text[:40]}")
                self.msg_entry.delete(0, "end")

    def _send_toast(self):
        if self._check_phone():
            text = self.msg_entry.get().strip()
            if text:
                reverse_commands.show_toast(text)
                self._append_log(f"[→ Phone] Toast: {text[:40]}")
                self.msg_entry.delete(0, "end")

    def _quick_send(self, mode):
        text = self.msg_entry.get().strip()
        if not text:
            self._append_log("Enter text first")
            return
        if not self._check_phone():
            return
        
        if mode == "toast":
            reverse_commands.show_toast(text)
        elif mode == "tts":
            reverse_commands.play_tts(text)
        elif mode == "url":
            if not text.startswith("http"):
                text = "https://" + text
            reverse_commands.open_url_on_phone(text)
        elif mode == "clip":
            reverse_commands.send_clipboard_to_phone(text)
        
        self.msg_entry.delete(0, "end")
        self._append_log(f"[→ Phone] {mode}: {text[:40]}")

    # ─── SYSTEM INFO MONITOR ───────────────────────────────────
    def _start_monitors(self):
        self._update_system_info()
    
    def _update_system_info(self):
        try:
            cpu = psutil.cpu_percent(interval=0)
            ram = psutil.virtual_memory()
            disk = psutil.disk_usage('/')
            
            self.sys_labels["CPU"].configure(text=f"{cpu}%")
            self.sys_labels["RAM"].configure(text=f"{ram.percent}%  ({ram.used // (1024**3)}/{ram.total // (1024**3)} GB)")
            self.sys_labels["Disk"].configure(text=f"{disk.percent}%  ({disk.free // (1024**3)} GB free)")
            
            battery = psutil.sensors_battery()
            if battery:
                plug = "⚡ Charging" if battery.power_plugged else "🔋"
                self.sys_labels["Battery"].configure(text=f"{battery.percent}% {plug}")
            else:
                self.sys_labels["Battery"].configure(text="Desktop (AC)")
            
            net = psutil.net_io_counters()
            sent_mb = net.bytes_sent / (1024 * 1024)
            recv_mb = net.bytes_recv / (1024 * 1024)
            self.sys_labels["Network"].configure(text=f"↑{sent_mb:.0f}MB ↓{recv_mb:.0f}MB")
        except Exception:
            pass
        
        self.after(2000, self._update_system_info)

    # ─── LOGGING ────────────────────────────────────────────────
    def _append_log(self, text):
        timestamp = datetime.now().strftime("%H:%M:%S")
        line = f"[{timestamp}] {text}"
        self.log_lines.append(line)
        
        # Keep last 200 lines
        if len(self.log_lines) > 200:
            self.log_lines = self.log_lines[-200:]
        
        try:
            self.log_textbox.configure(state="normal")
            self.log_textbox.insert("end", line + "\n")
            self.log_textbox.see("end")
            self.log_textbox.configure(state="disabled")
        except Exception:
            pass  # Widget might not exist yet
    
    def _clear_logs(self):
        self.log_lines.clear()
        self.log_textbox.configure(state="normal")
        self.log_textbox.delete("1.0", "end")
        self.log_textbox.configure(state="disabled")
    
    def _export_logs(self):
        """Export logs to a text file."""
        if not self.log_lines:
            self._append_log("No logs to export")
            return
        
        filepath = filedialog.asksaveasfilename(
            title="Export Logs",
            defaultextension=".txt",
            filetypes=[("Text files", "*.txt"), ("All files", "*.*")],
            initialfilename=f"logs_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        )
        
        if filepath:
            try:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write("\n".join(self.log_lines))
                self._append_log(f"Logs exported to: {os.path.basename(filepath)}")
            except Exception as e:
                self._append_log(f"Export failed: {e}")

    # ─── CLOCK ──────────────────────────────────────────────────
    def _update_clock(self):
        now = datetime.now().strftime("%H:%M:%S")
        self.time_label.configure(text=now)
        self.after(1000, self._update_clock)

    # ─── CLEANUP ────────────────────────────────────────────────
    def _on_close(self):
        """Minimize to tray on close if server is running, otherwise fully shut down."""
        if HAS_TRAY and self.tray_icon and self.server_running:
            self.withdraw()
            self._minimized_to_tray = True
            self._tray_notify("Minimized to Tray", "Server still running. Right-click tray icon to quit.")
        else:
            self._full_shutdown()
    
    def _full_shutdown(self):
        """Complete application shutdown."""
        sys.stdout = self.log_redirector._original_stdout
        self.embedded_server.stop()
        if self.tray_icon:
            try:
                self.tray_icon.stop()
            except Exception:
                pass
        self.destroy()


def main():
    app = ControlPanel()
    app.mainloop()

if __name__ == "__main__":
    main()
