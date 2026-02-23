import pyperclip
import time
import threading
import reverse_commands

class ClipboardWatcher:
    def __init__(self):
        self.last_text = ""
        self.running = False
        self._lock = threading.Lock()

    def start(self):
        self.running = True
        # Initialize with current content so we don't spam on startup
        try:
            self.last_text = pyperclip.paste()
        except:
            self.last_text = ""
            
        thread = threading.Thread(target=self._watch_loop, daemon=True)
        thread.start()

    def stop(self):
        self.running = False

    def _watch_loop(self):
        print("[*] Clipboard Sync Started")
        while self.running:
            try:
                # 1. Check PC Clipboard
                current_text = pyperclip.paste()
                
                # 2. If changed, send to Phone
                if current_text != self.last_text:
                    self.last_text = current_text
                    if current_text.strip(): # Don't send empty/whitespace only
                        # Limit size to prevent crashing (e.g. 4KB limit)
                        if len(current_text) < 4000: 
                            # Escape newlines for transport
                            safe_text = current_text.replace("\n", "\\n")
                            reverse_commands.send_to_phone(f"CLIPBOARD_DATA:{safe_text}")
                            print(f"[+] Synced Clipboard to Phone: {current_text[:20]}...")
            except Exception as e:
                print(f"Clipboard Error: {e}")
            
            time.sleep(1.0) # Check every 1 second

    def set_pc_clipboard(self, text):
        """Called when Phone sends text to PC"""
        try:
            with self._lock:
                # Update local tracker so we don't bounce it back
                self.last_text = text 
                pyperclip.copy(text)
                print(f"[+] Clipboard updated from Phone: {text[:20]}...")
        except Exception as e:
            print(f"Set Clipboard Error: {e}")

# Global Instance
watcher = ClipboardWatcher()

def start_watching():
    watcher.start()

def update_from_mobile(text):
    watcher.set_pc_clipboard(text)