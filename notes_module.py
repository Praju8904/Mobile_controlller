"""
Notes Module — Notion-inspired synchronized notes system.

Features:
  - Hierarchical folder/note tree with JSON persistence
  - Rich laptop GUI with sidebar explorer + editor pane
  - Auto-save with debounce timer
  - Right-click context menus (rename, delete, pin, color)
  - Full-text search across all notes
  - Bidirectional sync with mobile via reverse_commands
"""

import customtkinter as ctk
import tkinter as tk
import os
import json
import time
import uuid
import threading

# ─── CONFIGURATION ──────────────────────────────────────────────
NOTES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "notes_data.json")

NOTE_COLORS = {
    "default": "#1F2937",
    "red":     "#7F1D1D",
    "orange":  "#7C2D12",
    "yellow":  "#713F12",
    "green":   "#14532D",
    "blue":    "#1E3A5F",
    "purple":  "#4C1D95",
    "pink":    "#831843",
}

# ─── DATA LAYER ─────────────────────────────────────────────────

class NotesManager:
    """Thread-safe hierarchical notes data manager."""

    def __init__(self):
        self._lock = threading.Lock()
        self.data = self._load_data()

    def _load_data(self):
        if not os.path.exists(NOTES_FILE):
            return self._default_root()
        try:
            with open(NOTES_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            return self._default_root()

    @staticmethod
    def _default_root():
        return {"id": "root", "name": "Root", "type": "folder", "children": []}

    def save_data(self):
        with self._lock:
            try:
                with open(NOTES_FILE, 'w', encoding='utf-8') as f:
                    json.dump(self.data, f, indent=2, ensure_ascii=False)
            except Exception as e:
                print(f"[Notes] Save error: {e}")

    # ── Tree Traversal ──

    def get_node_by_id(self, target_id, current_node=None):
        if current_node is None:
            current_node = self.data
        if current_node['id'] == target_id:
            return current_node
        if current_node.get('type') == 'folder':
            for child in current_node.get('children', []):
                found = self.get_node_by_id(target_id, child)
                if found:
                    return found
        return None

    def get_parent_of(self, target_id, current_node=None, parent=None):
        if current_node is None:
            current_node = self.data
        if current_node['id'] == target_id:
            return parent
        if current_node.get('type') == 'folder':
            for child in current_node.get('children', []):
                found = self.get_parent_of(target_id, child, current_node)
                if found:
                    return found
        return None

    # ── CRUD ──

    def add_item(self, parent_id, name, item_type, mob_id=None):
        if parent_id == 'root':
            parent = self.data  # root is the top-level dict with 'children'
        else:
            parent = self.get_node_by_id(parent_id)
        if parent and parent.get('type') == 'folder':
            new_item = {
                "id": mob_id or str(uuid.uuid4())[:8],
                "name": name,
                "type": item_type,
                "timestamp": time.time(),
                "pinned": False,
                "color": "default",
            }
            if item_type == 'folder':
                new_item['children'] = []
            else:
                new_item['content'] = ""
            parent['children'].append(new_item)
            self.save_data()
            return new_item
        return None

    def update_note_content(self, note_id, content):
        node = self.get_node_by_id(note_id)
        if node and node.get('type') == 'note':
            node['content'] = content
            node['timestamp'] = time.time()
            self.save_data()
            return True
        return False

    def delete_item(self, item_id):
        parent = self.get_parent_of(item_id)
        if parent:
            parent['children'] = [c for c in parent['children'] if c['id'] != item_id]
            self.save_data()
            return True
        return False

    def rename_item(self, item_id, new_name):
        node = self.get_node_by_id(item_id)
        if node:
            node['name'] = new_name
            node['timestamp'] = time.time()
            self.save_data()
            return True
        return False

    def pin_item(self, item_id):
        node = self.get_node_by_id(item_id)
        if node:
            node['pinned'] = not node.get('pinned', False)
            self.save_data()
            return node['pinned']
        return False

    def set_color(self, item_id, color):
        node = self.get_node_by_id(item_id)
        if node and color in NOTE_COLORS:
            node['color'] = color
            self.save_data()
            return True
        return False

    def search_notes(self, query):
        results = []
        query_lower = query.lower()
        self._search_recursive(self.data, query_lower, results)
        return results

    def _search_recursive(self, node, query, results):
        if node.get('type') == 'note':
            if query in node.get('name', '').lower() or query in node.get('content', '').lower():
                results.append(node)
        if node.get('type') == 'folder':
            for child in node.get('children', []):
                self._search_recursive(child, query, results)

    def get_sorted_children(self, folder_id):
        folder = self.get_node_by_id(folder_id)
        if not folder:
            return []
        children = folder.get('children', [])
        # Pinned first, then folders, then notes, then by name
        return sorted(children, key=lambda x: (
            not x.get('pinned', False),
            0 if x['type'] == 'folder' else 1,
            x.get('name', '').lower()
        ))

# ─── GUI WIDGET ─────────────────────────────────────────────────

class NotesTab:
    """Notion-inspired Notes GUI for the laptop control panel."""

    def __init__(self, parent_frame):
        self.manager = NotesManager()
        self.current_folder_id = "root"
        self.path_stack = [{"id": "root", "name": "📚 Notes"}]
        self.active_note_id = None
        self._autosave_timer = None
        self._sync_callback = None

        # ── Main Container ──
        self.container = ctk.CTkFrame(parent_frame, fg_color="transparent")
        self.container.pack(fill="both", expand=True)

        # ── LEFT SIDEBAR (280px) ──
        self.sidebar = ctk.CTkFrame(self.container, width=280, fg_color="#0F172A", corner_radius=0)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)

        self._build_sidebar()

        # ── SEPARATOR ──
        sep = ctk.CTkFrame(self.container, width=1, fg_color="#334155")
        sep.pack(side="left", fill="y")

        # ── RIGHT EDITOR PANE ──
        self.editor_pane = ctk.CTkFrame(self.container, fg_color="#111827", corner_radius=0)
        self.editor_pane.pack(side="right", fill="both", expand=True)

        self._build_editor()
        self.refresh_list()

    # ── SIDEBAR ──────────────────────────────────────────────────

    def _build_sidebar(self):
        # Search bar
        search_frame = ctk.CTkFrame(self.sidebar, fg_color="transparent", height=45)
        search_frame.pack(fill="x", padx=12, pady=(12, 6))
        search_frame.pack_propagate(False)

        self.search_entry = ctk.CTkEntry(
            search_frame, placeholder_text="🔍 Search notes...",
            fg_color="#1E293B", border_color="#334155", border_width=1,
            text_color="#E2E8F0", font=("Segoe UI", 13), height=36, corner_radius=8
        )
        self.search_entry.pack(fill="x", expand=True)
        self.search_entry.bind("<KeyRelease>", self._on_search)

        # Action buttons row
        btn_frame = ctk.CTkFrame(self.sidebar, fg_color="transparent", height=38)
        btn_frame.pack(fill="x", padx=12, pady=(4, 8))
        btn_frame.pack_propagate(False)

        self.new_note_btn = ctk.CTkButton(
            btn_frame, text="📝 Note", width=80, height=32,
            fg_color="#1D4ED8", hover_color="#2563EB", font=("Segoe UI", 12),
            corner_radius=6, command=self._add_note
        )
        self.new_note_btn.pack(side="left", padx=(0, 6))

        self.new_folder_btn = ctk.CTkButton(
            btn_frame, text="📁 Folder", width=80, height=32,
            fg_color="#374151", hover_color="#4B5563", font=("Segoe UI", 12),
            corner_radius=6, command=self._add_folder
        )
        self.new_folder_btn.pack(side="left", padx=(0, 6))

        # Breadcrumb / navigation
        self.nav_frame = ctk.CTkFrame(self.sidebar, fg_color="#1E293B", height=36, corner_radius=6)
        self.nav_frame.pack(fill="x", padx=12, pady=(0, 8))
        self.nav_frame.pack_propagate(False)

        self.back_btn = ctk.CTkButton(
            self.nav_frame, text="◀", width=30, height=28,
            fg_color="transparent", hover_color="#334155",
            font=("Segoe UI", 14), command=self.go_back, state="disabled"
        )
        self.back_btn.pack(side="left", padx=(4, 0))

        self.path_label = ctk.CTkLabel(
            self.nav_frame, text="📚 Notes",
            font=("Segoe UI", 13, "bold"), text_color="#94A3B8"
        )
        self.path_label.pack(side="left", padx=8)

        # Scrollable note list
        self.note_list = ctk.CTkScrollableFrame(
            self.sidebar, fg_color="transparent",
            scrollbar_button_color="#334155",
            scrollbar_button_hover_color="#475569"
        )
        self.note_list.pack(fill="both", expand=True, padx=8, pady=(0, 8))

        # Footer stats
        self.stats_label = ctk.CTkLabel(
            self.sidebar, text="",
            font=("Segoe UI", 11), text_color="#64748B", height=24
        )
        self.stats_label.pack(fill="x", padx=12, pady=(0, 8))

    # ── EDITOR PANE ──────────────────────────────────────────────

    def _build_editor(self):
        # Empty state (shown when no note selected)
        self.empty_state = ctk.CTkFrame(self.editor_pane, fg_color="transparent")
        self.empty_state.pack(fill="both", expand=True)

        empty_icon = ctk.CTkLabel(
            self.empty_state, text="📝",
            font=("Segoe UI", 72), text_color="#334155"
        )
        empty_icon.pack(pady=(100, 10))

        empty_title = ctk.CTkLabel(
            self.empty_state, text="Select a note to start editing",
            font=("Segoe UI", 20, "bold"), text_color="#475569"
        )
        empty_title.pack(pady=(0, 8))

        empty_hint = ctk.CTkLabel(
            self.empty_state, text="Create a new note or click an existing one from the sidebar",
            font=("Segoe UI", 14), text_color="#64748B"
        )
        empty_hint.pack()

        # Editor container (hidden initially)
        self.editor_container = ctk.CTkFrame(self.editor_pane, fg_color="transparent")

        # Title field
        title_frame = ctk.CTkFrame(self.editor_container, fg_color="transparent")
        title_frame.pack(fill="x", padx=30, pady=(24, 0))

        self.editor_title = ctk.CTkEntry(
            title_frame, font=("Segoe UI", 24, "bold"),
            fg_color="transparent", border_width=0,
            text_color="#F1F5F9", placeholder_text="Untitled Note",
            placeholder_text_color="#475569", height=44
        )
        self.editor_title.pack(fill="x")
        self.editor_title.bind("<KeyRelease>", self._on_title_change)

        # Divider
        ctk.CTkFrame(self.editor_container, height=1, fg_color="#1E293B").pack(fill="x", padx=30, pady=(8, 0))

        # Formatting toolbar
        toolbar = ctk.CTkFrame(self.editor_container, fg_color="#0F172A", height=40, corner_radius=8)
        toolbar.pack(fill="x", padx=30, pady=(12, 8))
        toolbar.pack_propagate(False)

        fmt_buttons = [
            ("B", "bold", self._fmt_bold),
            ("I", "italic", self._fmt_italic),
            ("U", "underline", self._fmt_underline),
            ("S̶", "strikethrough", self._fmt_strike),
            ("•", "bullet", self._fmt_bullet),
            ("H", "heading", self._fmt_heading),
            ("—", "divider", self._fmt_divider),
        ]

        for text, tip, cmd in fmt_buttons:
            b = ctk.CTkButton(
                toolbar, text=text, width=36, height=30,
                fg_color="transparent", hover_color="#1E293B",
                font=("Consolas", 14, "bold"), text_color="#94A3B8",
                corner_radius=4, command=cmd
            )
            b.pack(side="left", padx=2, pady=4)

        # Spacer
        ctk.CTkFrame(toolbar, width=1, fg_color="#334155").pack(side="left", fill="y", padx=6, pady=8)

        # Color picker dropdown
        self.color_menu_btn = ctk.CTkButton(
            toolbar, text="🎨", width=36, height=30,
            fg_color="transparent", hover_color="#1E293B",
            font=("Segoe UI", 14), corner_radius=4,
            command=self._show_color_picker
        )
        self.color_menu_btn.pack(side="left", padx=2, pady=4)

        # Save indicator
        self.save_indicator = ctk.CTkLabel(
            toolbar, text="", font=("Segoe UI", 11), text_color="#22C55E"
        )
        self.save_indicator.pack(side="right", padx=10)

        # Text editor
        self.editor_content = ctk.CTkTextbox(
            self.editor_container, font=("Consolas", 14),
            fg_color="#111827", text_color="#E2E8F0",
            corner_radius=0, border_width=0,
            scrollbar_button_color="#334155",
            scrollbar_button_hover_color="#475569",
            wrap="word"
        )
        self.editor_content.pack(fill="both", expand=True, padx=30, pady=(0, 8))
        self.editor_content.bind("<KeyRelease>", self._on_content_change)

        # Footer with word count
        footer = ctk.CTkFrame(self.editor_container, fg_color="#0F172A", height=30, corner_radius=0)
        footer.pack(fill="x", side="bottom")
        footer.pack_propagate(False)

        self.word_count_label = ctk.CTkLabel(
            footer, text="0 words · 0 characters",
            font=("Segoe UI", 11), text_color="#64748B"
        )
        self.word_count_label.pack(side="right", padx=16)

        self.last_edited_label = ctk.CTkLabel(
            footer, text="",
            font=("Segoe UI", 11), text_color="#64748B"
        )
        self.last_edited_label.pack(side="left", padx=16)

    # ── LIST RENDERING ───────────────────────────────────────────

    def refresh_list(self):
        for widget in self.note_list.winfo_children():
            widget.destroy()

        children = self.manager.get_sorted_children(self.current_folder_id)

        if not children:
            empty = ctk.CTkLabel(
                self.note_list, text="No notes yet\nClick '📝 Note' to create one",
                font=("Segoe UI", 13), text_color="#475569", justify="center"
            )
            empty.pack(pady=40)
        else:
            for item in children:
                self._render_item(item)

        # Breadcrumb
        self.path_label.configure(text=self.path_stack[-1]['name'])
        self.back_btn.configure(state="normal" if len(self.path_stack) > 1 else "disabled")

        # Stats
        total_notes = self._count_notes(self.manager.data)
        self.stats_label.configure(text=f"📊 {total_notes} note{'s' if total_notes != 1 else ''} total")

    def _count_notes(self, node):
        count = 1 if node.get('type') == 'note' else 0
        for child in node.get('children', []):
            count += self._count_notes(child)
        return count

    def _render_item(self, item):
        is_folder = item['type'] == 'folder'
        is_pinned = item.get('pinned', False)
        color_key = item.get('color', 'default')
        bg_color = NOTE_COLORS.get(color_key, NOTE_COLORS['default'])

        icon = "📁" if is_folder else "📝"
        pin_icon = " 📌" if is_pinned else ""
        is_active = (item['id'] == self.active_note_id)

        # Container
        item_frame = ctk.CTkFrame(
            self.note_list, fg_color=bg_color if not is_active else "#1D4ED8",
            corner_radius=8, height=52
        )
        item_frame.pack(fill="x", pady=2, padx=2)
        item_frame.pack_propagate(False)

        # Inner layout
        inner = ctk.CTkFrame(item_frame, fg_color="transparent")
        inner.pack(fill="both", expand=True, padx=12, pady=6)

        # Icon + Name
        name_label = ctk.CTkLabel(
            inner, text=f"{icon}  {item['name']}{pin_icon}",
            font=("Segoe UI", 13, "bold" if is_folder else "normal"),
            text_color="#F1F5F9" if is_active else "#E2E8F0",
            anchor="w"
        )
        name_label.pack(side="left", fill="x", expand=True)

        # Timestamp for notes
        if not is_folder and item.get('timestamp'):
            ts = time.strftime("%b %d", time.localtime(item['timestamp']))
            time_label = ctk.CTkLabel(
                inner, text=ts, font=("Segoe UI", 11), text_color="#64748B"
            )
            time_label.pack(side="right")

        # Click handlers
        for widget in [item_frame, inner, name_label]:
            widget.bind("<Button-1>", lambda e, i=item: self._on_item_click(i))
            widget.bind("<Button-3>", lambda e, i=item: self._show_context_menu(e, i))

        # Hover effects
        def on_enter(e, f=item_frame):
            if item['id'] != self.active_note_id:
                f.configure(fg_color="#1E293B" if color_key == "default" else bg_color)
        def on_leave(e, f=item_frame):
            if item['id'] != self.active_note_id:
                f.configure(fg_color=bg_color)

        for widget in [item_frame, inner, name_label]:
            widget.bind("<Enter>", on_enter)
            widget.bind("<Leave>", on_leave)

    # ── ITEM ACTIONS ─────────────────────────────────────────────

    def _on_item_click(self, item):
        if item['type'] == 'folder':
            self.current_folder_id = item['id']
            self.path_stack.append({"id": item['id'], "name": f"📁 {item['name']}"})
            self.refresh_list()
        else:
            self._load_note(item)

    def go_back(self):
        if len(self.path_stack) > 1:
            self.path_stack.pop()
            self.current_folder_id = self.path_stack[-1]['id']
            self.refresh_list()

    def _load_note(self, item):
        self.active_note_id = item['id']

        # Switch views
        self.empty_state.pack_forget()
        self.editor_container.pack(fill="both", expand=True)

        # Populate editor
        self.editor_title.delete(0, "end")
        self.editor_title.insert(0, item['name'])

        self.editor_content.delete("0.0", "end")
        self.editor_content.insert("0.0", item.get('content', ''))

        # Update footer
        self._update_word_count()
        if item.get('timestamp'):
            edited = time.strftime("%b %d, %Y at %I:%M %p", time.localtime(item['timestamp']))
            self.last_edited_label.configure(text=f"Last edited: {edited}")

        self.save_indicator.configure(text="")
        self.refresh_list()  # Update active highlight

    def _add_note(self):
        dialog = ctk.CTkInputDialog(text="Note name:", title="Create New Note")
        name = dialog.get_input()
        if name and name.strip():
            new_item = self.manager.add_item(self.current_folder_id, name.strip(), "note")
            if new_item:
                self.refresh_list()
                self._load_note(new_item)
                self._sync_to_mobile("added", new_item)

    def _add_folder(self):
        dialog = ctk.CTkInputDialog(text="Folder name:", title="Create New Folder")
        name = dialog.get_input()
        if name and name.strip():
            new_item = self.manager.add_item(self.current_folder_id, name.strip(), "folder")
            if new_item:
                self.refresh_list()
                self._sync_to_mobile("added", new_item)

    # ── CONTEXT MENU ─────────────────────────────────────────────

    def _show_context_menu(self, event, item):
        menu = tk.Menu(self.container, tearoff=0,
                       bg="#1E293B", fg="#E2E8F0", activebackground="#334155",
                       activeforeground="#F1F5F9", font=("Segoe UI", 11),
                       relief="flat", bd=1)

        pin_text = "📌 Unpin" if item.get('pinned') else "📌 Pin to Top"
        menu.add_command(label=pin_text, command=lambda: self._pin_item(item))
        menu.add_command(label="✏️ Rename", command=lambda: self._rename_item(item))
        menu.add_separator()

        # Color submenu
        color_menu = tk.Menu(menu, tearoff=0,
                             bg="#1E293B", fg="#E2E8F0", activebackground="#334155",
                             font=("Segoe UI", 11), relief="flat")
        for cname in NOTE_COLORS:
            color_menu.add_command(
                label=f"{'●' if item.get('color') == cname else '○'} {cname.capitalize()}",
                command=lambda c=cname: self._set_color(item, c)
            )
        menu.add_cascade(label="🎨 Color", menu=color_menu)
        menu.add_separator()
        menu.add_command(label="🗑️ Delete", command=lambda: self._delete_item(item))

        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()

    def _rename_item(self, item):
        dialog = ctk.CTkInputDialog(text=f"New name for '{item['name']}':", title="Rename")
        new_name = dialog.get_input()
        if new_name and new_name.strip():
            self.manager.rename_item(item['id'], new_name.strip())
            # Update title if this is the active note
            if item['id'] == self.active_note_id:
                self.editor_title.delete(0, "end")
                self.editor_title.insert(0, new_name.strip())
            self.refresh_list()
            self._sync_to_mobile("updated", item)

    def _delete_item(self, item):
        confirm = tk.messagebox.askyesno(
            "Delete",
            f"Delete '{item['name']}'?\n\nThis action cannot be undone.",
            icon="warning"
        )
        if confirm:
            self.manager.delete_item(item['id'])
            if item['id'] == self.active_note_id:
                self.active_note_id = None
                self.editor_container.pack_forget()
                self.empty_state.pack(fill="both", expand=True)
            self.refresh_list()
            self._sync_to_mobile("deleted", item)

    def _pin_item(self, item):
        self.manager.pin_item(item['id'])
        self.refresh_list()

    def _set_color(self, item, color):
        self.manager.set_color(item['id'], color)
        self.refresh_list()

    # ── SEARCH ───────────────────────────────────────────────────

    def _on_search(self, event=None):
        query = self.search_entry.get().strip()
        if not query:
            self.refresh_list()
            return

        # Clear list and show search results
        for widget in self.note_list.winfo_children():
            widget.destroy()

        results = self.manager.search_notes(query)
        if not results:
            no_result = ctk.CTkLabel(
                self.note_list, text=f"No results for '{query}'",
                font=("Segoe UI", 13), text_color="#64748B"
            )
            no_result.pack(pady=40)
        else:
            header = ctk.CTkLabel(
                self.note_list, text=f"🔍 {len(results)} result{'s' if len(results) != 1 else ''}",
                font=("Segoe UI", 12, "bold"), text_color="#94A3B8", anchor="w"
            )
            header.pack(fill="x", padx=4, pady=(4, 8))
            for item in results:
                self._render_item(item)

    # ── AUTO-SAVE & FORMATTING ───────────────────────────────────

    def _on_content_change(self, event=None):
        self._update_word_count()
        self._schedule_autosave()

    def _on_title_change(self, event=None):
        if self.active_note_id:
            new_title = self.editor_title.get().strip()
            if new_title:
                self.manager.rename_item(self.active_note_id, new_title)
                self.refresh_list()

    def _schedule_autosave(self):
        if self._autosave_timer:
            try:
                self.container.after_cancel(self._autosave_timer)
            except Exception:
                pass
        self.save_indicator.configure(text="● Unsaved", text_color="#F59E0B")
        self._autosave_timer = self.container.after(2000, self._autosave)

    def _autosave(self):
        if self.active_note_id:
            content = self.editor_content.get("0.0", "end").rstrip('\n')
            self.manager.update_note_content(self.active_note_id, content)
            self.save_indicator.configure(text="✓ Saved", text_color="#22C55E")

            # Update last edited time
            node = self.manager.get_node_by_id(self.active_note_id)
            if node and node.get('timestamp'):
                edited = time.strftime("%b %d, %Y at %I:%M %p", time.localtime(node['timestamp']))
                self.last_edited_label.configure(text=f"Last edited: {edited}")

            # Sync to mobile
            self._sync_to_mobile("updated", node)

    def _update_word_count(self):
        content = self.editor_content.get("0.0", "end").strip()
        chars = len(content)
        words = len(content.split()) if content else 0
        self.word_count_label.configure(text=f"{words} word{'s' if words != 1 else ''} · {chars} character{'s' if chars != 1 else ''}")

    # ── FORMATTING ──

    def _insert_at_cursor(self, prefix, suffix=""):
        try:
            sel_start = self.editor_content.index("sel.first")
            sel_end = self.editor_content.index("sel.last")
            selected = self.editor_content.get(sel_start, sel_end)
            self.editor_content.delete(sel_start, sel_end)
            self.editor_content.insert(sel_start, f"{prefix}{selected}{suffix}")
        except tk.TclError:
            self.editor_content.insert("insert", f"{prefix}{suffix}")
        self._on_content_change()

    def _fmt_bold(self):
        self._insert_at_cursor("**", "**")

    def _fmt_italic(self):
        self._insert_at_cursor("*", "*")

    def _fmt_underline(self):
        self._insert_at_cursor("__", "__")

    def _fmt_strike(self):
        self._insert_at_cursor("~~", "~~")

    def _fmt_bullet(self):
        self._insert_at_cursor("• ")

    def _fmt_heading(self):
        self._insert_at_cursor("# ")

    def _fmt_divider(self):
        self._insert_at_cursor("\n───────────────────────\n")

    def _show_color_picker(self):
        if self.active_note_id:
            node = self.manager.get_node_by_id(self.active_note_id)
            if node:
                menu = tk.Menu(self.container, tearoff=0,
                               bg="#1E293B", fg="#E2E8F0", activebackground="#334155",
                               font=("Segoe UI", 11), relief="flat")
                for cname in NOTE_COLORS:
                    current = node.get('color', 'default')
                    label = f"{'●' if current == cname else '○'} {cname.capitalize()}"
                    menu.add_command(label=label,
                                     command=lambda c=cname: self._apply_note_color(c))
                try:
                    menu.tk_popup(
                        self.color_menu_btn.winfo_rootx(),
                        self.color_menu_btn.winfo_rooty() + 30
                    )
                finally:
                    menu.grab_release()

    def _apply_note_color(self, color):
        if self.active_note_id:
            self.manager.set_color(self.active_note_id, color)
            self.refresh_list()

    # ── SYNC ─────────────────────────────────────────────────────

    def _sync_to_mobile(self, action, item):
        """Sync note changes to mobile device."""
        try:
            import reverse_commands
            if reverse_commands.is_connected():
                if action == "added":
                    reverse_commands.send_to_phone(f"NOTE_NOTIFY_ADDED:{item['id']}:{item['name']}")
                elif action == "updated":
                    reverse_commands.send_to_phone(f"NOTE_NOTIFY_UPDATED:{item['id']}:{item.get('name', '')}")
                elif action == "deleted":
                    reverse_commands.send_to_phone(f"NOTE_NOTIFY_DELETED:{item['id']}")
                # Also send full sync
                json_data = get_all_notes_json()
                reverse_commands.send_to_phone(f"NOTES_SYNC:{json_data}")
        except Exception as e:
            print(f"[Notes] Sync error: {e}")

    def reload_from_disk(self):
        """Reload notes data from disk (called after mobile update)."""
        self.manager.data = self.manager._load_data()
        self.refresh_list()
        if self.active_note_id:
            node = self.manager.get_node_by_id(self.active_note_id)
            if node:
                self._load_note(node)


# ─── GLOBAL HELPERS ─────────────────────────────────────────────

_manager_instance = NotesManager()
_notes_tab_instance = None

def set_notes_tab(tab):
    global _notes_tab_instance
    _notes_tab_instance = tab

def get_all_notes_json():
    _manager_instance.data = _manager_instance._load_data()  # Refresh
    return json.dumps(_manager_instance.data, ensure_ascii=False)

def update_note_from_mobile(note_id, content, title=None):
    node = _manager_instance.get_node_by_id(note_id)
    if node:
        node['content'] = content
        if title:
            node['name'] = title
        node['timestamp'] = time.time()
        _manager_instance.save_data()
    if _notes_tab_instance:
        try:
            _notes_tab_instance.parent.after(0, _notes_tab_instance.reload_from_disk)
        except Exception:
            pass

def add_note_from_mobile(parent_id, name, item_type, mob_id=None):
    result = _manager_instance.add_item(parent_id, name, item_type, mob_id)
    if _notes_tab_instance:
        try:
            _notes_tab_instance.parent.after(0, _notes_tab_instance.reload_from_disk)
        except Exception:
            pass
    return result

def delete_note_from_mobile(item_id):
    result = _manager_instance.delete_item(item_id)
    if _notes_tab_instance:
        try:
            _notes_tab_instance.parent.after(0, _notes_tab_instance.reload_from_disk)
        except Exception:
            pass
    return result

def rename_note_from_mobile(item_id, new_name):
    result = _manager_instance.rename_item(item_id, new_name)
    if _notes_tab_instance:
        try:
            _notes_tab_instance.parent.after(0, _notes_tab_instance.reload_from_disk)
        except Exception:
            pass
    return result