"""
Calendar Module — Event management with PC GUI and mobile sync.

Features:
  - JSON file persistence (calendar_data.json)
  - Thread-safe CRUD for events
  - Month grid GUI with event indicators
  - Event add/edit/delete dialogs
  - Bidirectional mobile sync via reverse_commands
"""

import customtkinter as ctk
import tkinter as tk
import json
import os
import threading
import time
import uuid
import calendar
from datetime import datetime, timedelta

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CALENDAR_FILE = os.path.join(BASE_DIR, "calendar_data.json")

# Color palette for events
EVENT_COLORS = {
    "blue":    {"bg": "#1E3A5F", "accent": "#3B82F6", "text": "#BFDBFE"},
    "red":     {"bg": "#7F1D1D", "accent": "#EF4444", "text": "#FECACA"},
    "green":   {"bg": "#14532D", "accent": "#22C55E", "text": "#BBF7D0"},
    "purple":  {"bg": "#4C1D95", "accent": "#8B5CF6", "text": "#DDD6FE"},
    "orange":  {"bg": "#7C2D12", "accent": "#F97316", "text": "#FED7AA"},
    "pink":    {"bg": "#831843", "accent": "#EC4899", "text": "#FBCFE8"},
    "yellow":  {"bg": "#713F12", "accent": "#EAB308", "text": "#FEF08A"},
    "teal":    {"bg": "#134E4A", "accent": "#14B8A6", "text": "#99F6E4"},
}

DEFAULT_COLOR = "blue"

# ─── DATA LAYER ─────────────────────────────────────────────────

class CalendarManager:
    """Thread-safe calendar event data manager."""

    def __init__(self):
        self._lock = threading.Lock()
        self.events = self._load_data()

    def _load_data(self):
        if not os.path.exists(CALENDAR_FILE):
            return []
        try:
            with open(CALENDAR_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                return data if isinstance(data, list) else []
        except Exception:
            return []

    def save_data(self):
        with self._lock:
            try:
                with open(CALENDAR_FILE, 'w', encoding='utf-8') as f:
                    json.dump(self.events, f, indent=2, ensure_ascii=False)
            except Exception as e:
                print(f"[Calendar] Save error: {e}")

    def _new_event(self, title, date, start_time="", end_time="",
                   description="", color=DEFAULT_COLOR, recurring="none",
                   reminder="none", event_id=None):
        return {
            "id": event_id or f"evt_{uuid.uuid4().hex[:8]}",
            "title": title,
            "date": date,             # "YYYY-MM-DD"
            "start_time": start_time, # "HH:MM" or ""
            "end_time": end_time,     # "HH:MM" or ""
            "description": description,
            "color": color,
            "recurring": recurring,   # "none", "daily", "weekly", "monthly", "yearly"
            "reminder": reminder,     # "none", "5min", "15min", "30min", "1hr", "1day"
            "timestamp": time.time(),
        }

    # ── CRUD ──

    def add_event(self, title, date, start_time="", end_time="",
                  description="", color=DEFAULT_COLOR, recurring="none",
                  reminder="none", event_id=None):
        event = self._new_event(title, date, start_time, end_time,
                                description, color, recurring, reminder, event_id)
        self.events.append(event)
        self.save_data()
        return event

    def update_event(self, event_id, **kwargs):
        for event in self.events:
            if event['id'] == event_id:
                for key, value in kwargs.items():
                    if key in event:
                        event[key] = value
                event['timestamp'] = time.time()
                self.save_data()
                return event
        return None

    def delete_event(self, event_id):
        before = len(self.events)
        self.events = [e for e in self.events if e['id'] != event_id]
        if len(self.events) < before:
            self.save_data()
            return True
        return False

    def get_event_by_id(self, event_id):
        for event in self.events:
            if event['id'] == event_id:
                return event
        return None

    def get_events_for_date(self, date_str):
        """Get events for a specific date (YYYY-MM-DD)."""
        results = []
        for event in self.events:
            if event['date'] == date_str:
                results.append(event)
            elif self._matches_recurring(event, date_str):
                results.append(event)
        return sorted(results, key=lambda e: e.get('start_time', ''))

    def get_events_for_month(self, year, month):
        """Get all events for a given month."""
        month_prefix = f"{year:04d}-{month:02d}"
        results = []
        for event in self.events:
            if event['date'].startswith(month_prefix):
                results.append(event)
            elif event.get('recurring', 'none') != 'none':
                # Check each day of the month for recurring matches
                cal = calendar.monthcalendar(year, month)
                for week in cal:
                    for day in week:
                        if day > 0:
                            date_str = f"{year:04d}-{month:02d}-{day:02d}"
                            if self._matches_recurring(event, date_str):
                                results.append(event)
                                break
                    else:
                        continue
                    break
        return results

    def _matches_recurring(self, event, target_date_str):
        """Check if a recurring event matches a target date."""
        recurrence = event.get('recurring', 'none')
        if recurrence == 'none':
            return False
        try:
            event_date = datetime.strptime(event['date'], "%Y-%m-%d")
            target_date = datetime.strptime(target_date_str, "%Y-%m-%d")
            if target_date <= event_date:
                return False

            if recurrence == 'daily':
                return True
            elif recurrence == 'weekly':
                return event_date.weekday() == target_date.weekday()
            elif recurrence == 'monthly':
                return event_date.day == target_date.day
            elif recurrence == 'yearly':
                return (event_date.month == target_date.month and
                        event_date.day == target_date.day)
        except (ValueError, KeyError):
            pass
        return False

    def search_events(self, query):
        query_lower = query.lower()
        return [e for e in self.events
                if query_lower in e.get('title', '').lower()
                or query_lower in e.get('description', '').lower()]


# ─── GUI WIDGET ─────────────────────────────────────────────────

class CalendarTab:
    """Modern Calendar GUI for the laptop control panel."""

    def __init__(self, parent_frame):
        self.manager = CalendarManager()
        self.today = datetime.now()
        self.current_year = self.today.year
        self.current_month = self.today.month
        self.selected_date = self.today.strftime("%Y-%m-%d")

        # ── Main Container ──
        self.container = ctk.CTkFrame(parent_frame, fg_color="transparent")
        self.container.pack(fill="both", expand=True)

        # ── LEFT: Calendar Grid (400px) ──
        self.left_panel = ctk.CTkFrame(self.container, width=400, fg_color="#0F172A", corner_radius=0)
        self.left_panel.pack(side="left", fill="y")
        self.left_panel.pack_propagate(False)

        self._build_calendar_grid()

        # ── SEPARATOR ──
        sep = ctk.CTkFrame(self.container, width=1, fg_color="#334155")
        sep.pack(side="left", fill="y")

        # ── RIGHT: Event List ──
        self.right_panel = ctk.CTkFrame(self.container, fg_color="#111827", corner_radius=0)
        self.right_panel.pack(side="right", fill="both", expand=True)

        self._build_event_panel()
        self._render_month()
        self._refresh_events()

    # ── CALENDAR GRID ────────────────────────────────────────────

    def _build_calendar_grid(self):
        # Navigation header
        nav = ctk.CTkFrame(self.left_panel, fg_color="#1E293B", height=56, corner_radius=0)
        nav.pack(fill="x")
        nav.pack_propagate(False)

        self.btn_prev = ctk.CTkButton(
            nav, text="◀", width=40, height=36,
            fg_color="transparent", hover_color="#334155",
            font=("Segoe UI", 16), command=self._prev_month
        )
        self.btn_prev.pack(side="left", padx=8)

        self.month_label = ctk.CTkLabel(
            nav, text="", font=("Segoe UI", 18, "bold"), text_color="#F1F5F9"
        )
        self.month_label.pack(side="left", expand=True)

        self.btn_next = ctk.CTkButton(
            nav, text="▶", width=40, height=36,
            fg_color="transparent", hover_color="#334155",
            font=("Segoe UI", 16), command=self._next_month
        )
        self.btn_next.pack(side="right", padx=8)

        btn_today = ctk.CTkButton(
            nav, text="Today", width=60, height=30,
            fg_color="#1D4ED8", hover_color="#2563EB",
            font=("Segoe UI", 11), corner_radius=6,
            command=self._go_today
        )
        btn_today.pack(side="right", padx=4)

        # Day-of-week headers
        dow_frame = ctk.CTkFrame(self.left_panel, fg_color="#0F172A", height=32)
        dow_frame.pack(fill="x", padx=8, pady=(8, 0))
        dow_frame.pack_propagate(False)

        for day_name in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]:
            lbl = ctk.CTkLabel(
                dow_frame, text=day_name, width=50,
                font=("Segoe UI", 11, "bold"), text_color="#64748B"
            )
            lbl.pack(side="left", expand=True)

        # Grid area (6 rows of 7 days)
        self.grid_frame = ctk.CTkFrame(self.left_panel, fg_color="#0F172A")
        self.grid_frame.pack(fill="both", expand=True, padx=8, pady=4)

        # Quick add at bottom
        add_frame = ctk.CTkFrame(self.left_panel, fg_color="transparent", height=50)
        add_frame.pack(fill="x", padx=12, pady=(0, 8))
        add_frame.pack_propagate(False)

        ctk.CTkButton(
            add_frame, text="＋ New Event", height=38,
            fg_color="#1D4ED8", hover_color="#2563EB",
            font=("Segoe UI", 13, "bold"), corner_radius=8,
            command=self._show_add_dialog
        ).pack(fill="x")

    def _render_month(self):
        """Render the month grid."""
        for widget in self.grid_frame.winfo_children():
            widget.destroy()

        self.month_label.configure(
            text=f"{calendar.month_name[self.current_month]} {self.current_year}"
        )

        cal = calendar.monthcalendar(self.current_year, self.current_month)
        today_str = self.today.strftime("%Y-%m-%d")

        for row_idx, week in enumerate(cal):
            row_frame = ctk.CTkFrame(self.grid_frame, fg_color="transparent", height=48)
            row_frame.pack(fill="x", pady=1)
            row_frame.pack_propagate(False)

            for col_idx, day in enumerate(week):
                if day == 0:
                    # Empty cell — same padding as day cells for alignment
                    empty = ctk.CTkFrame(row_frame, fg_color="transparent")
                    empty.pack(side="left", expand=True, fill="both", padx=1, pady=1)
                    ctk.CTkLabel(empty, text="", font=("Segoe UI", 13)).pack(expand=True)
                else:
                    date_str = f"{self.current_year:04d}-{self.current_month:02d}-{day:02d}"
                    is_today = (date_str == today_str)
                    is_selected = (date_str == self.selected_date)
                    has_events = len(self.manager.get_events_for_date(date_str)) > 0

                    if is_selected:
                        bg = "#1D4ED8"
                    elif is_today:
                        bg = "#164E63"
                    else:
                        bg = "transparent"

                    cell = ctk.CTkFrame(
                        row_frame, fg_color=bg, corner_radius=8
                    )
                    cell.pack(side="left", expand=True, fill="both", padx=1, pady=1)

                    text_color = "#F1F5F9" if (is_selected or is_today) else "#CBD5E1"
                    day_lbl = ctk.CTkLabel(
                        cell, text=str(day),
                        font=("Segoe UI", 13, "bold" if is_today else "normal"),
                        text_color=text_color
                    )
                    day_lbl.pack(expand=True)

                    # Event indicator dot
                    if has_events:
                        dot = ctk.CTkFrame(
                            cell, width=6, height=6,
                            fg_color="#3B82F6" if not is_selected else "#BFDBFE",
                            corner_radius=3
                        )
                        dot.pack(pady=(0, 2))

                    # Click handler
                    for w in [cell, day_lbl]:
                        w.bind("<Button-1>", lambda e, d=date_str: self._select_date(d))

                    # Hover effects
                    if not is_selected and not is_today:
                        for w in [cell, day_lbl]:
                            w.bind("<Enter>", lambda e, c=cell: c.configure(fg_color="#1E293B"))
                            w.bind("<Leave>", lambda e, c=cell: c.configure(fg_color="transparent"))

    # ── EVENT PANEL ──────────────────────────────────────────────

    def _build_event_panel(self):
        # Date header
        self.date_header = ctk.CTkLabel(
            self.right_panel, text="", font=("Segoe UI", 20, "bold"),
            text_color="#F1F5F9", anchor="w"
        )
        self.date_header.pack(fill="x", padx=24, pady=(20, 4))

        self.event_count_label = ctk.CTkLabel(
            self.right_panel, text="", font=("Segoe UI", 12),
            text_color="#64748B", anchor="w"
        )
        self.event_count_label.pack(fill="x", padx=24, pady=(0, 12))

        # Separator
        ctk.CTkFrame(self.right_panel, height=1, fg_color="#1E293B").pack(fill="x", padx=24)

        # Scrollable event list
        self.event_list = ctk.CTkScrollableFrame(
            self.right_panel, fg_color="transparent",
            scrollbar_button_color="#334155",
            scrollbar_button_hover_color="#475569"
        )
        self.event_list.pack(fill="both", expand=True, padx=16, pady=8)

    def _refresh_events(self):
        """Refresh the event list for the selected date."""
        for widget in self.event_list.winfo_children():
            widget.destroy()

        # Parse date for display
        try:
            dt = datetime.strptime(self.selected_date, "%Y-%m-%d")
            day_name = dt.strftime("%A")
            display_date = dt.strftime("%B %d, %Y")
            self.date_header.configure(text=f"{day_name}")
            self.event_count_label.configure(text=display_date)
        except ValueError:
            self.date_header.configure(text=self.selected_date)

        events = self.manager.get_events_for_date(self.selected_date)

        if not events:
            empty = ctk.CTkLabel(
                self.event_list,
                text="No events for this day\nClick '＋ New Event' to add one",
                font=("Segoe UI", 14), text_color="#475569", justify="center"
            )
            empty.pack(pady=60)
        else:
            self.event_count_label.configure(
                text=f"{display_date} · {len(events)} event{'s' if len(events) != 1 else ''}"
            )
            for event in events:
                self._render_event_card(event)

    def _render_event_card(self, event):
        """Render a single event card."""
        color = EVENT_COLORS.get(event.get('color', DEFAULT_COLOR), EVENT_COLORS[DEFAULT_COLOR])

        card = ctk.CTkFrame(
            self.event_list, fg_color=color['bg'],
            corner_radius=10, height=80
        )
        card.pack(fill="x", pady=4)
        card.pack_propagate(False)

        # Color accent bar
        accent = ctk.CTkFrame(card, width=4, fg_color=color['accent'], corner_radius=2)
        accent.pack(side="left", fill="y", padx=(8, 0), pady=8)

        # Content area
        content = ctk.CTkFrame(card, fg_color="transparent")
        content.pack(side="left", fill="both", expand=True, padx=12, pady=8)

        # Title row
        title_lbl = ctk.CTkLabel(
            content, text=event['title'],
            font=("Segoe UI", 14, "bold"), text_color="#F1F5F9", anchor="w"
        )
        title_lbl.pack(fill="x")

        # Time row
        time_parts = []
        if event.get('start_time'):
            time_parts.append(event['start_time'])
        if event.get('end_time'):
            time_parts.append(event['end_time'])
        time_text = " — ".join(time_parts) if time_parts else "All day"

        if event.get('recurring', 'none') != 'none':
            time_text += f"  🔄 {event['recurring']}"

        time_lbl = ctk.CTkLabel(
            content, text=time_text,
            font=("Segoe UI", 12), text_color=color['text'], anchor="w"
        )
        time_lbl.pack(fill="x")

        # Description (if any)
        if event.get('description'):
            desc_lbl = ctk.CTkLabel(
                content, text=event['description'][:60] + ("..." if len(event.get('description', '')) > 60 else ""),
                font=("Segoe UI", 11), text_color="#94A3B8", anchor="w"
            )
            desc_lbl.pack(fill="x")

        # Actions column
        actions = ctk.CTkFrame(card, fg_color="transparent", width=80)
        actions.pack(side="right", fill="y", padx=(0, 8))
        actions.pack_propagate(False)

        edit_btn = ctk.CTkButton(
            actions, text="✏️", width=32, height=28,
            fg_color="transparent", hover_color="#334155",
            font=("Segoe UI", 13), corner_radius=4,
            command=lambda e=event: self._show_edit_dialog(e)
        )
        edit_btn.pack(pady=(12, 2))

        del_btn = ctk.CTkButton(
            actions, text="🗑️", width=32, height=28,
            fg_color="transparent", hover_color="#7F1D1D",
            font=("Segoe UI", 13), corner_radius=4,
            command=lambda e=event: self._delete_event(e)
        )
        del_btn.pack(pady=2)

        # Click to edit
        for w in [card, content, title_lbl, time_lbl]:
            w.bind("<Button-1>", lambda e, ev=event: self._show_edit_dialog(ev))

    # ── NAVIGATION ───────────────────────────────────────────────

    def _prev_month(self):
        if self.current_month == 1:
            self.current_month = 12
            self.current_year -= 1
        else:
            self.current_month -= 1
        self._render_month()

    def _next_month(self):
        if self.current_month == 12:
            self.current_month = 1
            self.current_year += 1
        else:
            self.current_month += 1
        self._render_month()

    def _go_today(self):
        self.today = datetime.now()
        self.current_year = self.today.year
        self.current_month = self.today.month
        self.selected_date = self.today.strftime("%Y-%m-%d")
        self._render_month()
        self._refresh_events()

    def _select_date(self, date_str):
        self.selected_date = date_str
        self._render_month()
        self._refresh_events()

    # ── ADD / EDIT DIALOG ────────────────────────────────────────

    def _show_add_dialog(self):
        self._show_event_dialog(None)

    def _show_edit_dialog(self, event):
        self._show_event_dialog(event)

    def _show_event_dialog(self, event=None):
        """Show add/edit event dialog."""
        is_edit = event is not None
        dialog = ctk.CTkToplevel(self.container)
        dialog.title("Edit Event" if is_edit else "New Event")
        dialog.geometry("420x620")
        dialog.configure(fg_color="#0F172A")
        dialog.transient(self.container.winfo_toplevel())
        dialog.grab_set()

        # Center on parent
        dialog.after(10, lambda: dialog.focus_force())

        # Title
        ctk.CTkLabel(
            dialog, text="✏️ Edit Event" if is_edit else "📅 New Event",
            font=("Segoe UI", 18, "bold"), text_color="#F1F5F9"
        ).pack(padx=20, pady=(16, 8))

        # Scrollable form area
        scroll_form = ctk.CTkScrollableFrame(
            dialog, fg_color="transparent",
            scrollbar_button_color="#334155",
            scrollbar_button_hover_color="#475569"
        )
        scroll_form.pack(fill="both", expand=True, padx=4, pady=(0, 4))

        # Form fields
        fields = {}

        def add_field(label, default="", placeholder=""):
            ctk.CTkLabel(
                scroll_form, text=label, font=("Segoe UI", 12, "bold"),
                text_color="#94A3B8", anchor="w"
            ).pack(fill="x", padx=16, pady=(8, 2))
            entry = ctk.CTkEntry(
                scroll_form, fg_color="#1E293B", border_color="#334155",
                text_color="#E2E8F0", font=("Segoe UI", 13),
                height=36, corner_radius=6, placeholder_text=placeholder
            )
            entry.pack(fill="x", padx=16)
            if default:
                entry.insert(0, default)
            return entry

        fields['title'] = add_field("Title", event['title'] if is_edit else "", "Event title")
        fields['date'] = add_field("Date (YYYY-MM-DD)", event['date'] if is_edit else self.selected_date, "2026-01-15")
        fields['start_time'] = add_field("Start Time (HH:MM)", event.get('start_time', '') if is_edit else "", "09:00")
        fields['end_time'] = add_field("End Time (HH:MM)", event.get('end_time', '') if is_edit else "", "10:00")
        fields['description'] = add_field("Description", event.get('description', '') if is_edit else "", "Optional details")

        # Color picker
        ctk.CTkLabel(
            scroll_form, text="Color", font=("Segoe UI", 12, "bold"),
            text_color="#94A3B8", anchor="w"
        ).pack(fill="x", padx=16, pady=(8, 2))

        color_frame = ctk.CTkFrame(scroll_form, fg_color="transparent", height=36)
        color_frame.pack(fill="x", padx=16)
        color_frame.pack_propagate(False)

        selected_color = [event.get('color', DEFAULT_COLOR) if is_edit else DEFAULT_COLOR]
        color_buttons = {}

        def select_color(cname):
            selected_color[0] = cname
            for cn, cb in color_buttons.items():
                if cn == cname:
                    cb.configure(border_width=3, border_color="#FFFFFF")
                else:
                    cb.configure(border_width=0)

        for cname, cvals in EVENT_COLORS.items():
            btn = ctk.CTkButton(
                color_frame, text="", width=30, height=30,
                fg_color=cvals['accent'], hover_color=cvals['accent'],
                corner_radius=15, border_width=3 if cname == selected_color[0] else 0,
                border_color="#FFFFFF",
                command=lambda c=cname: select_color(c)
            )
            btn.pack(side="left", padx=2)
            color_buttons[cname] = btn

        # Recurring dropdown
        ctk.CTkLabel(
            scroll_form, text="Recurring", font=("Segoe UI", 12, "bold"),
            text_color="#94A3B8", anchor="w"
        ).pack(fill="x", padx=16, pady=(8, 2))

        recurring_var = ctk.StringVar(value=event.get('recurring', 'none') if is_edit else 'none')
        recurring_menu = ctk.CTkOptionMenu(
            scroll_form, values=["none", "daily", "weekly", "monthly", "yearly"],
            variable=recurring_var, fg_color="#1E293B", button_color="#334155",
            button_hover_color="#475569", dropdown_fg_color="#1E293B",
            font=("Segoe UI", 12), height=34, corner_radius=6
        )
        recurring_menu.pack(fill="x", padx=16)

        # Buttons
        btn_frame = ctk.CTkFrame(dialog, fg_color="transparent", height=50)
        btn_frame.pack(fill="x", padx=20, pady=(16, 12))
        btn_frame.pack_propagate(False)

        def on_save():
            title = fields['title'].get().strip()
            if not title:
                return

            date_val = fields['date'].get().strip()
            if not date_val:
                date_val = self.selected_date

            if is_edit:
                self.manager.update_event(
                    event['id'],
                    title=title,
                    date=date_val,
                    start_time=fields['start_time'].get().strip(),
                    end_time=fields['end_time'].get().strip(),
                    description=fields['description'].get().strip(),
                    color=selected_color[0],
                    recurring=recurring_var.get()
                )
                self._sync_to_mobile("updated", event)
            else:
                new_event = self.manager.add_event(
                    title=title,
                    date=date_val,
                    start_time=fields['start_time'].get().strip(),
                    end_time=fields['end_time'].get().strip(),
                    description=fields['description'].get().strip(),
                    color=selected_color[0],
                    recurring=recurring_var.get()
                )
                self._sync_to_mobile("added", new_event)

            dialog.destroy()
            self._render_month()
            self._refresh_events()

        ctk.CTkButton(
            btn_frame, text="Save" if is_edit else "Add Event", height=38,
            fg_color="#1D4ED8", hover_color="#2563EB",
            font=("Segoe UI", 13, "bold"), corner_radius=8,
            command=on_save
        ).pack(side="left", expand=True, fill="x", padx=(0, 6))

        ctk.CTkButton(
            btn_frame, text="Cancel", height=38,
            fg_color="#374151", hover_color="#4B5563",
            font=("Segoe UI", 13), corner_radius=8,
            command=dialog.destroy
        ).pack(side="right", expand=True, fill="x", padx=(6, 0))

    # ── DELETE ───────────────────────────────────────────────────

    def _delete_event(self, event):
        confirm = tk.messagebox.askyesno(
            "Delete Event",
            f"Delete '{event['title']}'?\n\nThis action cannot be undone.",
            icon="warning"
        )
        if confirm:
            self.manager.delete_event(event['id'])
            self._render_month()
            self._refresh_events()
            self._sync_to_mobile("deleted", event)

    # ── SYNC ─────────────────────────────────────────────────────

    def _sync_to_mobile(self, action, event):
        """Sync calendar changes to mobile device."""
        try:
            import reverse_commands
            if reverse_commands.is_connected():
                if action == "added":
                    reverse_commands.send_to_phone(f"CAL_NOTIFY_ADDED:{event['id']}:{event['title']}")
                elif action == "updated":
                    reverse_commands.send_to_phone(f"CAL_NOTIFY_UPDATED:{event['id']}:{event.get('title', '')}")
                elif action == "deleted":
                    reverse_commands.send_to_phone(f"CAL_NOTIFY_DELETED:{event['id']}")
                # Also send full sync
                json_data = get_all_events_json()
                reverse_commands.send_to_phone(f"CAL_SYNC:{json_data}")
        except Exception as e:
            print(f"[Calendar] Sync error: {e}")

    def reload_from_disk(self):
        """Reload calendar data from disk (called after mobile update)."""
        self.manager.events = self.manager._load_data()
        self._render_month()
        self._refresh_events()


# ─── GLOBAL HELPERS ─────────────────────────────────────────────

_manager_instance = CalendarManager()
_calendar_tab_instance = None

def set_calendar_tab(tab):
    global _calendar_tab_instance
    _calendar_tab_instance = tab

def get_all_events_json():
    _manager_instance.events = _manager_instance._load_data()  # Refresh
    return json.dumps(_manager_instance.events, ensure_ascii=False)

def add_event_from_mobile(event_json):
    """Add event from mobile device (receives JSON string)."""
    try:
        data = json.loads(event_json) if isinstance(event_json, str) else event_json
        event = _manager_instance.add_event(
            title=data.get('title', 'Untitled'),
            date=data.get('date', datetime.now().strftime("%Y-%m-%d")),
            start_time=data.get('start_time', data.get('start', '')),
            end_time=data.get('end_time', data.get('end', '')),
            description=data.get('description', ''),
            color=data.get('color', DEFAULT_COLOR),
            recurring=data.get('recurring', 'none'),
            reminder=data.get('reminder', 'none'),
            event_id=data.get('id', None)
        )
        if _calendar_tab_instance:
            try:
                _calendar_tab_instance.container.after(0, _calendar_tab_instance.reload_from_disk)
            except Exception:
                pass
        return event
    except Exception as e:
        print(f"[Calendar] Add from mobile error: {e}")
        return None

def update_event_from_mobile(event_json):
    """Update event from mobile device (receives JSON string)."""
    try:
        data = json.loads(event_json) if isinstance(event_json, str) else event_json
        event_id = data.get('id')
        if not event_id:
            return None

        kwargs = {}
        for key in ['title', 'date', 'start_time', 'end_time', 'description',
                     'color', 'recurring', 'reminder']:
            if key in data:
                kwargs[key] = data[key]
        # Handle alternate key names from mobile
        if 'start' in data and 'start_time' not in data:
            kwargs['start_time'] = data['start']
        if 'end' in data and 'end_time' not in data:
            kwargs['end_time'] = data['end']

        result = _manager_instance.update_event(event_id, **kwargs)
        if _calendar_tab_instance:
            try:
                _calendar_tab_instance.container.after(0, _calendar_tab_instance.reload_from_disk)
            except Exception:
                pass
        return result
    except Exception as e:
        print(f"[Calendar] Update from mobile error: {e}")
        return None

def delete_event_from_mobile(event_id):
    """Delete event from mobile device."""
    result = _manager_instance.delete_event(event_id)
    if _calendar_tab_instance:
        try:
            _calendar_tab_instance.container.after(0, _calendar_tab_instance.reload_from_disk)
        except Exception:
            pass
    return result
