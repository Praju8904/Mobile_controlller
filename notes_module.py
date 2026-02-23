import customtkinter as ctk
import os
import json
import time
import uuid

# ─── CONFIGURATION ──────────────────────────────────────────────
NOTES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "notes_data.json")

class NotesManager:
    """Handles loading and saving the hierarchical notes data."""
    def __init__(self):
        self.data = self._load_data()

    def _load_data(self):
        if not os.path.exists(NOTES_FILE):
            return {"id": "root", "name": "Root", "type": "folder", "children": []}
        try:
            with open(NOTES_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return {"id": "root", "name": "Root", "type": "folder", "children": []}

    def save_data(self):
        with open(NOTES_FILE, 'w', encoding='utf-8') as f:
            json.dump(self.data, f, indent=2)

    def get_node_by_id(self, target_id, current_node=None):
        """Recursive search for a node."""
        if current_node is None: current_node = self.data
        if current_node['id'] == target_id: return current_node
        
        if current_node['type'] == 'folder':
            for child in current_node.get('children', []):
                found = self.get_node_by_id(target_id, child)
                if found: return found
        return None

    def add_item(self, parent_id, name, item_type):
        parent = self.get_node_by_id(parent_id)
        if parent and parent['type'] == 'folder':
            new_item = {
                "id": str(uuid.uuid4())[:8],
                "name": name,
                "type": item_type,
                "timestamp": time.time()
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
        if node and node['type'] == 'note':
            node['content'] = content
            node['timestamp'] = time.time()
            self.save_data()
            return True
        return False
    
    def delete_item(self, parent_id, item_id):
        parent = self.get_node_by_id(parent_id)
        if parent:
            parent['children'] = [c for c in parent['children'] if c['id'] != item_id]
            self.save_data()

# ─── GUI WIDGET ─────────────────────────────────────────────────

class NotesTab:
    def __init__(self, parent_frame):
        self.manager = NotesManager()
        self.current_folder_id = "root"
        self.path_stack = [{"id": "root", "name": "Home"}] # For Breadcrumbs
        
        # Layout: Left (List) | Right (Editor)
        self.main_split = ctk.CTkFrame(parent_frame, fg_color="transparent")
        self.main_split.pack(fill="both", expand=True)

        # ── LEFT PANEL (EXPLORER) ──
        self.left_panel = ctk.CTkFrame(self.main_split, width=250, corner_radius=0)
        self.left_panel.pack(side="left", fill="y", padx=(0, 2))
        
        # Header (Breadcrumbs + Add)
        self.header = ctk.CTkFrame(self.left_panel, height=40, fg_color="#1F2937")
        self.header.pack(fill="x")
        
        self.back_btn = ctk.CTkButton(self.header, text="⬅", width=30, command=self.go_back, fg_color="transparent")
        self.back_btn.pack(side="left")
        
        self.path_label = ctk.CTkLabel(self.header, text="Home", font=("Segoe UI", 12, "bold"))
        self.path_label.pack(side="left", padx=5)

        self.add_btn = ctk.CTkButton(self.header, text="+", width=30, command=self.show_add_menu)
        self.add_btn.pack(side="right", padx=5)

        # List Area
        self.scroll_list = ctk.CTkScrollableFrame(self.left_panel, fg_color="transparent")
        self.scroll_list.pack(fill="both", expand=True)

        # ── RIGHT PANEL (EDITOR) ──
        self.right_panel = ctk.CTkFrame(self.main_split, fg_color="#111827", corner_radius=0)
        self.right_panel.pack(side="right", fill="both", expand=True)

        self.editor_title = ctk.CTkEntry(self.right_panel, font=("Segoe UI", 18, "bold"), 
                                         fg_color="transparent", border_width=0, placeholder_text="Note Title")
        self.editor_title.pack(fill="x", padx=20, pady=(20, 10))

        self.editor_content = ctk.CTkTextbox(self.right_panel, font=("Consolas", 14), 
                                             fg_color="#1F2937", text_color="#E5E7EB", corner_radius=10)
        self.editor_content.pack(fill="both", expand=True, padx=20, pady=(0, 20))
        
        self.save_btn = ctk.CTkButton(self.right_panel, text="Save Changes", fg_color="#059669", 
                                      hover_color="#047857", command=self.save_current_note)
        self.save_btn.pack(pady=10)

        self.active_note_id = None
        self.refresh_list()

    def refresh_list(self):
        # Clear list
        for widget in self.scroll_list.winfo_children(): widget.destroy()
        
        # Get items
        folder = self.manager.get_node_by_id(self.current_folder_id)
        if not folder: return

        # Render Items
        for item in folder.get('children', []):
            self.render_item(item)

        # Update Breadcrumb
        self.path_label.configure(text=self.path_stack[-1]['name'])
        self.back_btn.configure(state="normal" if len(self.path_stack) > 1 else "disabled")

    def render_item(self, item):
        is_folder = item['type'] == 'folder'
        icon = "📁" if is_folder else "📝"
        color = "#374151" if is_folder else "#1F2937"
        
        btn = ctk.CTkButton(
            self.scroll_list, 
            text=f"{icon}  {item['name']}", 
            fg_color=color, 
            anchor="w",
            height=40,
            command=lambda: self.on_item_click(item)
        )
        btn.pack(fill="x", pady=2, padx=5)

    def on_item_click(self, item):
        if item['type'] == 'folder':
            self.current_folder_id = item['id']
            self.path_stack.append({"id": item['id'], "name": item['name']})
            self.refresh_list()
        else:
            self.load_note(item)

    def go_back(self):
        if len(self.path_stack) > 1:
            self.path_stack.pop()
            self.current_folder_id = self.path_stack[-1]['id']
            self.refresh_list()

    def load_note(self, item):
        self.active_note_id = item['id']
        self.editor_title.delete(0, "end")
        self.editor_title.insert(0, item['name'])
        self.editor_content.delete("0.0", "end")
        self.editor_content.insert("0.0", item.get('content', ''))

    def save_current_note(self):
        if self.active_note_id:
            content = self.editor_content.get("0.0", "end")
            self.manager.update_note_content(self.active_note_id, content)
            # Sync to phone (Implementation later)
            # 1. Visual Feedback
            from tkinter import messagebox
            messagebox.showinfo("Notes", "✅ Note Saved Successfully!")
            
            # 2. Sync to Phone immediately
            try:
                import reverse_commands
                if reverse_commands.is_connected():
                    json_data = get_all_notes_json()
                    reverse_commands.send_to_phone(f"NOTES_DATA:{json_data}")
            except Exception as e:
                print(f"Sync error: {e}")

    def show_add_menu(self):
        dialog = ctk.CTkInputDialog(text="Enter Name:", title="New Item")
        name = dialog.get_input()
        if name:
            # Simple toggle: if name ends with / it's a folder, else note (quick hack for UI simplicity)
            # Or simplified: Default to note, maybe add prefix "f:" for folder
            is_folder = name.startswith("f:")
            clean_name = name[2:] if is_folder else name
            type_ = "folder" if is_folder else "note"
            
            self.manager.add_item(self.current_folder_id, clean_name, type_)
            self.refresh_list()

# Global Helper for external access
_manager_instance = NotesManager()
def get_all_notes_json():
    return json.dumps(_manager_instance.data)

def update_note_from_mobile(note_id, content):
    _manager_instance.update_note_content(note_id, content)