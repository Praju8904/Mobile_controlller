import time
import threading
import pyautogui
import subprocess
import os
import webbrowser
import pyperclip
import screen_brightness_control as sbc
import config
from geometry_utils import normalize_points, get_similarity
import hmac
import hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
import base64
import socket
import file_service
import notes_module
import task_manager
import calendar_module
import reverse_commands
import clipboard_sync
import notification_manager
import media_controller
import system_monitor
import file_explorer

SCREENSHOT_DIR = os.path.join(os.getcwd(), "screenshots")
if not os.path.exists(SCREENSHOT_DIR):
    os.makedirs(SCREENSHOT_DIR)

# ─── STOP FLAG (for embedded server mode) ───────────────────────
_stop_server_flag = False

def is_stop_requested():
    """Check if a remote STOP_SERVER command was received."""
    return _stop_server_flag

def reset_stop_flag():
    """Reset the stop flag (called when server starts)."""
    global _stop_server_flag
    _stop_server_flag = False

# ─── CHAT CALLBACK (for GUI chat integration) ─────────────────
_chat_callback = None

def set_chat_callback(fn):
    """Register a callback for incoming CHAT_MSG commands.
    The callback receives (text, msg_type)."""
    global _chat_callback
    _chat_callback = fn

def execute_command(command_text, addr):
    client_ip = addr[0] # Phone's IP

    if command_text == "REQUEST_FILE_LIST":
        # Check the folder and return the list
        folder = "./send_file"
        if not os.path.exists(folder): os.makedirs(folder)
        files = os.listdir(folder)
        return "FILES:" + "|".join(files) if files else "FILES:EMPTY"

    if command_text.startswith("DOWNLOAD_FILE:"):
        filename = command_text.split(":")[1]
        # Start the TCP server from file_service in a new thread
        threading.Thread(target=file_service.start_file_transfer, 
                         args=(filename, client_ip)).start()
        return "SENDING_STARTED"

    return None
def handle_screenshot(mode, client_ip=None):
    # 1. Capture the screen
    filename = f"screenshot_{int(time.time())}.png"
    filepath = os.path.join(SCREENSHOT_DIR, filename)  # <--- This contains the FULL path (D:\...\screenshots\...)
    
    screenshot = pyautogui.screenshot()
    screenshot.save(filepath)
    print(f"[*] Screenshot saved locally: {filepath}")

    if mode == "SEND" and client_ip:
        # --- THE FIX IS HERE ---
        # OLD: args=(filename, client_ip)
        # NEW: args=(filepath, client_ip)
        print(f"[*] Sending screenshot to mobile at {client_ip}...")
        threading.Thread(target=file_service.send_specific_file, 
                         args=(filepath, client_ip)).start() # Pass 'filepath', NOT 'filename'
        return "SENDING_STARTED"
    return None

def decrypt_command(ciphertext_b64):
    try:
        # 1. Decode Base64
        raw_data = base64.b64decode(ciphertext_b64)
        
        # 2. Extract IV (first 16 bytes) and Ciphertext 
        iv = raw_data[:16]
        ciphertext = raw_data[16:]
        
        # 3. Decrypt using AES-CBC
        key = config.AES_KEY.encode('utf-8')
        cipher = AES.new(key, AES.MODE_CBC, iv)
        decrypted = unpad(cipher.decrypt(ciphertext), AES.block_size)
        
        return decrypted.decode('utf-8')
    except Exception as e:
        print(f"   [Security] Decryption Error: {e}")
        return None
    
def verify_signature(message_to_sign, received_signature):
    # Recompute HMAC-SHA256 
    key = config.HMAC_KEY.encode('utf-8')
    expected_signature = hmac.new(
        key, 
        message_to_sign.encode('utf-8'), 
        hashlib.sha256
    ).hexdigest()
    
    # Use compare_digest to prevent timing attacks
    return hmac.compare_digest(expected_signature, received_signature)

def scheduled_task(delay, cmd):
    print(f"[Timer] Starting {delay}s countdown for {cmd}")
    time.sleep(delay)
    execute_command(cmd)

def handle_voice_command(text):
    text = text.lower()
    print(f"[*] AI Processing: {text}")

    # --- 1. SEARCH LOGIC ---
    if "search" in text:
        if "youtube" in text:
            query = text.split("youtube")[-1].replace("for", "").strip()
            webbrowser.open(f"https://www.youtube.com/results?search_query={query}")
        elif "google" in text:
            query = text.split("google")[-1].replace("for", "").strip()
            webbrowser.open(f"https://www.google.com/search?q={query}")

    # --- 2. OPEN LOGIC (Apps & Sites) ---
    elif "open" in text:
        # Check for local apps first
        apps = {"paint": "mspaint", "calculator": "calc", "notepad": "notepad", "code": "code"}
        found = False
        for name, cmd in apps.items():
            if name in text:
                subprocess.Popen([cmd], shell=True)
                found = True
        
        # Fallback to browser sites
        if not found:
            sites = {"gemini": "https://gemini.google.com", "leetcode": "https://leetcode.com"}
            for name, url in sites.items():
                if name in text:
                    webbrowser.open(url)

    # --- 3. CLOSE LOGIC (New!) ---
    elif "close" in text:
        # Example: "close notepad"
        target = text.split("close")[-1].strip()
        os.system(f"taskkill /f /im {target}.exe")
        print(f"   [AI] Attempting to close: {target}")

    # --- 4. TYPE LOGIC ---
    elif "type" in text:
        content = text.split("type")[-1].strip()
        pyautogui.write(content)


def execute_command(data,addr,sock):
    try:
        # 1. Split the packet: ENCRYPTED_OR_PLAIN|TIMESTAMP|SIGNATURE
        parts = data.split("|")
        client_ip = addr[0]
        if len(parts) != 3:
            return

        payload, timestamp, signature = parts

        # 2. Verify Integrity first (Always sign the transmitted payload)
        message_to_sign = f"{payload}|{timestamp}"
        if not verify_signature(message_to_sign, signature):
            print("   [Security] Authentication Failed! Unauthorized command ignored.")
            return

        # 3. Check for Replay Attacks (10-second window)
        if abs(time.time() - float(timestamp)) > 10:
            print("   [Security] Replay Attack detected or Clock out of sync.")
            return

        # 4. Decrypt if the toggle is ON
        if config.USE_ENCRYPTION:
            command = decrypt_command(payload)
            if not command: return
        else:
            command = payload
        
        # 3. Use the cleaned text for all subsequent logic
        command = command.strip()
        if not command: return
        print(f"[*] Executing Verified Command: {command}")

        # Core System Logic
        if command == "PING":
            print("[*] PING received - responding with PONG")
            return "PONG"
        
        # ── Chat Messages from Phone ──
        elif command.startswith("CHAT_MSG:"):
            chat_text = command.split(":", 1)[1]
            print(f"   [💬 Chat] Phone: {chat_text[:60]}")
            if _chat_callback:
                try:
                    _chat_callback(chat_text, "text")
                except Exception as e:
                    print(f"   [Chat] Callback error: {e}")
            return None
        
        elif command.startswith("CHAT_FILE:"):
            file_info = command.split(":", 1)[1]
            print(f"   [💬 Chat] Phone sent file: {file_info}")
            # File message is added to chat by services.py after the file transfer completes
            return None
        
        if command == "PREVIEW_ON":
            config.preview_active = True
        elif command == "PREVIEW_OFF":
            config.preview_active = False

        # Unified App Launching
        elif command == "OPEN_NOTEPAD":
            try:
                subprocess.Popen(['notepad.exe']) 
            except Exception as e:
                print(f"Error: {e}")

        elif command == "SHUTDOWN_LAPTOP":
            os.system("shutdown /s /t 60")

        elif command == "GET_SYSTEM_STATS":
            # Phone requested a single update
            phone_ip = reverse_commands.get_phone_ip()
            if phone_ip:
                system_monitor.send_snapshot(phone_ip)
        
        elif command.startswith("GET_FILES:"):
            path = command.split(":", 1)[1]
            
            # Get list
            json_data = file_explorer.list_directory(path)
            
            # Send
            reverse_commands.send_to_phone(f"FILE_LIST:{json_data}")
            print("[+] Sent FILE_LIST to phone")

        elif command.startswith("MEDIA:"):
            # Format: MEDIA:MUTE, MEDIA:VOL_UP, MEDIA:PLAY
            action = command.split(":")[1]
            
            if action in ["MUTE", "UP", "DOWN"]:
                media_controller.set_volume(action)
            else:
                media_controller.media_action(action)

        elif command.startswith("SCHEDULE:"):
            _, mins, cmd_to_run = command.split(":")
            threading.Thread(target=scheduled_task, args=(int(mins)*60, cmd_to_run), daemon=True).start()

        # Mouse Handling (Uses command_text coordinates only)
        elif command.startswith("MOUSE_MOVE:"):
            try:
                coords = command.split(":", 1)[1].split(",")
                dx, dy = float(coords[0]), float(coords[1])
                pyautogui.moveRel(dx * 2.5, dy * 2.5, _pause=False)
            except (ValueError, IndexError) as e:
                print(f"Mouse Move Error: {e}")

        elif command == "MOUSE_CLICK": pyautogui.click()
        elif command == "MOUSE_RIGHT_CLICK": pyautogui.rightClick()
        
        elif command.startswith("MOUSE_SCROLL:"):
            try:
                scroll_direction = int(command.split(":")[1])
                pyautogui.scroll(scroll_direction * 150) 
            except Exception as e:
                print(f"Scroll Error: {e}")

        elif command == "STOP_SERVER":
            global _stop_server_flag
            _stop_server_flag = True
            print("Remote Stop signal received.")
            return "SERVER_STOPPING"
        
        elif command == "MEDIA_PLAY_PAUSE":
            pyautogui.press('playpause') 
        
        elif command == "MEDIA_NEXT":
            pyautogui.press('nexttrack')
            print("   [Action] Media: Next Track")

        elif command == "MEDIA_PREV":
            pyautogui.press('prevtrack')
            print("   [Action] Media: Previous Track")

        elif command == "MEDIA_STOP":
            pyautogui.press('stop')
            print("   [Action] Media: Stop")

        elif command == "LOCK_PC":
            import ctypes
            ctypes.windll.user32.LockWorkStation()
            print("   [Action] PC Locked")

        elif command.startswith("VOICE:"):
            voice_text = command.split(":", 1)[1]
            handle_voice_command(voice_text)
            
        elif command == "NAV_NEXT":
            pyautogui.press('right')
            print("   [Action] Navigation: Next")

        elif command == "NAV_BACK":
            pyautogui.press('left')
            print("   [Action] Navigation: Back")
        
        elif command == "SCREEN_BLACK":
            pyautogui.press('b')
            print("   [Action] Presentation Blackout")

        # ─── NOTES COMMANDS ──────────────────────────────────────
        elif command == "GET_NOTES" or command == "NOTE_SYNC":
            print("[*] Syncing Notes to Phone...")
            json_data = notes_module.get_all_notes_json()
            reverse_commands.send_to_phone(f"NOTES_SYNC:{json_data}")

        elif command.startswith("NOTE_ADD:"):
            # Format: NOTE_ADD:parent_id:mob_id:name:type
            try:
                parts = command.split(":", 4)
                parent_id = parts[1]
                mob_id = parts[2] if len(parts) > 2 else None
                name = parts[3] if len(parts) > 3 else "Untitled"
                item_type = parts[4] if len(parts) > 4 else "note"
                result = notes_module.add_note_from_mobile(parent_id, name, item_type, mob_id)
                if result:
                    print(f"[*] Note added from mobile: {name}")
                    # Send full sync back
                    json_data = notes_module.get_all_notes_json()
                    reverse_commands.send_to_phone(f"NOTES_SYNC:{json_data}")
                    return f"NOTE_ADDED:{result['id']}:{name}"
                else:
                    print(f"[!] Note add failed — parent '{parent_id}' not found")
            except Exception as e:
                print(f"[!] Note add error: {e}")

        elif command.startswith("NOTE_DELETE:"):
            # Format: NOTE_DELETE:item_id
            try:
                item_id = command.split(":", 1)[1]
                notes_module.delete_note_from_mobile(item_id)
                print(f"[*] Note deleted from mobile: {item_id}")
                json_data = notes_module.get_all_notes_json()
                reverse_commands.send_to_phone(f"NOTES_SYNC:{json_data}")
            except Exception as e:
                print(f"[!] Note delete error: {e}")

        elif command.startswith("NOTE_RENAME:"):
            # Format: NOTE_RENAME:item_id:new_name
            try:
                _, item_id, new_name = command.split(":", 2)
                notes_module.rename_note_from_mobile(item_id, new_name)
                print(f"[*] Note renamed from mobile: {item_id} -> {new_name}")
                json_data = notes_module.get_all_notes_json()
                reverse_commands.send_to_phone(f"NOTES_SYNC:{json_data}")
            except Exception as e:
                print(f"[!] Note rename error: {e}")

        elif command.startswith("NOTE_UPDATE:") or command.startswith("UPDATE_NOTE:"):
            # Format: NOTE_UPDATE:note_id:title:content  (content may contain colons)
            try:
                parts = command.split(":", 3)
                note_id = parts[1]
                rest = parts[2] if len(parts) > 2 else ""
                # rest = "title:content" — split once more to separate title from content
                title_content = rest.split(":", 1)
                title = title_content[0] if title_content else ""
                content = title_content[1] if len(title_content) > 1 else ""
                # Unescape newlines/colons from mobile
                content = content.replace("\\n", "\n").replace("\\:", ":")
                notes_module.update_note_from_mobile(note_id, content, title)
                print(f"[*] Note updated from mobile: {note_id} (title={title})")
            except Exception as e:
                print(f"[!] Note update error: {e}")

        # ─── CALENDAR COMMANDS ────────────────────────────────────────
        elif command == "CAL_SYNC" or command == "GET_CALENDAR":
            print("[*] Syncing Calendar to Phone...")
            json_data = calendar_module.get_all_events_json()
            reverse_commands.send_to_phone(f"CAL_SYNC:{json_data}")

        elif command.startswith("CAL_ADD:"):
            # Format: CAL_ADD:<json_event>
            try:
                event_json = command.split(":", 1)[1]
                result = calendar_module.add_event_from_mobile(event_json)
                if result:
                    print(f"[*] Calendar event added from mobile: {result['title']}")
                    json_data = calendar_module.get_all_events_json()
                    reverse_commands.send_to_phone(f"CAL_SYNC:{json_data}")
            except Exception as e:
                print(f"[!] Calendar add error: {e}")

        elif command.startswith("CAL_UPDATE:"):
            # Format: CAL_UPDATE:<json_event>
            try:
                event_json = command.split(":", 1)[1]
                result = calendar_module.update_event_from_mobile(event_json)
                if result:
                    print(f"[*] Calendar event updated from mobile: {result['title']}")
                    json_data = calendar_module.get_all_events_json()
                    reverse_commands.send_to_phone(f"CAL_SYNC:{json_data}")
            except Exception as e:
                print(f"[!] Calendar update error: {e}")

        elif command.startswith("CAL_DELETE:"):
            # Format: CAL_DELETE:<event_id>
            try:
                event_id = command.split(":", 1)[1]
                calendar_module.delete_event_from_mobile(event_id)
                print(f"[*] Calendar event deleted from mobile: {event_id}")
                json_data = calendar_module.get_all_events_json()
                reverse_commands.send_to_phone(f"CAL_SYNC:{json_data}")
            except Exception as e:
                print(f"[!] Calendar delete error: {e}")
        
        elif command.startswith("KEY:"):
            try:
                key = command.split(":", 1)[1].lower()
                # Handle specific mappings for PyAutoGUI
                if key == "backspace": pyautogui.press('backspace')
                elif key == "enter": pyautogui.press('enter')
                elif key == "space": pyautogui.press('space')
                else: pyautogui.press(key) 
            except Exception as e:
                print(f"Keyboard Error: {e}")

        elif command.startswith("CLIPBOARD:"):
            text_to_copy = command.split(":", 1)[1]
            pyperclip.copy(text_to_copy)
            print(f"   [Action] Copied to Clipboard: {text_to_copy[:30]}...")

        elif command.startswith("DICTATE:"):
            text_content = command.split(":", 1)[1]
            time.sleep(0.5) 
            pyautogui.write(text_content, interval=0.01)
            print(f"   [Action] Dictated: {text_content}")

        # System Controls
        elif command == "VOL_UP":
            pyautogui.press("volumeup")
        elif command == "VOL_DOWN":
            pyautogui.press("volumedown")
        elif command == "MUTE_TOGGLE":
            pyautogui.press('volumemute')

        elif command == "BRIGHT_UP":
            current = sbc.get_brightness()[0]
            sbc.set_brightness(min(100, current + 10))
        elif command == "BRIGHT_DOWN":
            current = sbc.get_brightness()[0]
            sbc.set_brightness(max(0, current - 10))

        elif command == "SHOW_DESKTOP":
            pyautogui.hotkey('win', 'd')
        
        elif command == "APP_SWITCHER":
            pyautogui.hotkey('win', 'tab')
        
        elif command == "ZOOM_IN":
            pyautogui.hotkey('ctrl', '=')
        elif command == "ZOOM_OUT":
            pyautogui.hotkey('ctrl', '-')
        elif command == "ZOOM_RESET":
            pyautogui.hotkey('ctrl', '0')
        elif command == "PANIC_SHIELD":
            print("!!! PANIC SHIELD ACTIVATED !!!")
            try:
                # 1. Minimize All Windows (Win + D)
                pyautogui.hotkey('win', 'd')
                
                # 2. Mute System Audio
                pyautogui.press('volumemute')
                
                # 3. Blackout (If in PPT) or just Pause Media
                pyautogui.press('playpause')
                
                # 4. Optional: Lower brightness to 0
                sbc.set_brightness(0)
                
                print("   [Action] All systems silenced and hidden.")
            except Exception as e:
                print(f"Panic Error: {e}")
        
        elif command.startswith("CLIPBOARD_DATA:"):
            # Format: CLIPBOARD_DATA:Text content here
            text = command[15:].replace("\\n", "\n") # Unescape
            clipboard_sync.update_from_mobile(text)
            print("[*] PC Clipboard set from Mobile")
        
        elif command.startswith("NOTIF_MIRROR:"):
            # Format: NOTIF_MIRROR:<JSON>
            try:
                payload = command[13:]
                notification_manager.handle_notification(payload)
            except Exception as e:
                print(f"[!] Notification parse error: {e}")

        elif command.startswith("NOTIF_DISMISSED:"):
            # Phone reports a notification was dismissed
            try:
                key = command[16:]
                notification_manager.handle_dismissed(key)
            except Exception as e:
                print(f"[!] Notification dismiss error: {e}")

        elif command == "SCREENSHOT_LOCAL":
            handle_screenshot("LOCAL")

        elif command == "SCREENSHOT_SEND":
            handle_screenshot("SEND", client_ip)
        
        elif command.startswith("MOUSE_MOVE_RELATIVE:"):
            _, dx, dy = command.split(":")
            # moveRel moves the mouse FROM its current position
            pyautogui.moveRel(int(dx), int(dy), _pause=False)
        
        if command == "REQUEST_FILE_LIST":
            file_list = file_service.get_folder_contents()
            # Send the list back via UDP (it's just a small string)
            sock.sendto(f"FILE_LIST:{file_list}".encode(), addr)

        elif command.startswith("GET_FILE:"):
            filename = command.split(":")[1]
            # Start TCP transfer in background
            threading.Thread(target=file_service.send_specific_file, 
                            args=(filename, addr[0])).start()
            
            
        

        # Gesture Recognition (Uses cleaned coordinates)
        elif command.startswith("GESTURE:"):
            try:
                raw_coords = command.split(":", 1)[1]
                raw = [tuple(map(float, p.split(","))) for p in raw_coords.split("|") if p]
                if len(raw) < 5: return
                
                user_p = normalize_points(raw)
                
                # Recognition logic
                if get_similarity(user_p, config.L_BRACKET) < 25:
                    pyautogui.press('left')
                    print("   [Action] Skip Backward")
                elif get_similarity(user_p, config.R_BRACKET) < 25:
                    pyautogui.press('right')
                    print("   [Action] Skip Forward")
                elif get_similarity(user_p, config.CIRCLE_TEMPLATE) < 35:
                    pyautogui.hotkey('ctrl', 'r')
                    print("   [Action] Refresh")
                elif get_similarity(user_p, config.CARET_TEMPLATE) < 25:
                    pyautogui.hotkey('win', 'up')
                    print("   [Action] Maximize Window")
                elif get_similarity(user_p, config.M_SHAPE_TEMPLATE) < 25:
                    pyautogui.hotkey('win', 'd')
                    print("   [Action] Minimize All")
                elif get_similarity(user_p, config.V_SHAPE_TEMPLATE) < 25:
                    pyautogui.hotkey('win', 'down')
                    print("   [Action] Mute Toggle")
            except Exception as e:
                print(f"Gesture Error: {e}")

        # ─── TASK MANAGER COMMANDS ──────────────────────────────────
        elif command.startswith("TASK_"):
            response = task_manager.handle_mobile_command(command)
            if response:
                return response

    except Exception as e:
        print(f"Global Command Error: {e}")

# def execute_command(data):
#     try:
#         # Split the new format: COMMAND|TIMESTAMP|SIGNATURE
#         parts = data.split("|")
#         if len(parts) != 3: 
#             print("   [Security] Invalid packet format.")
#             return

#         command_text, timestamp, signature = parts
        
#         # Security Gatekeeper
#         if not verify_signature(command_text, timestamp, signature):
#             print("   [Security] Authentication Failed! Unauthorized command ignored.")
#             return
        
#         command = data.strip()
#         if not command: return
#         print(f"[*] Command: {command}")

#         if data == "PING":
#             # Respond immediately - handled by server_1.py with client address
#             print("[*] PING received - will respond with PONG")
#             return "PONG"  # Return value to be sent back by server
        
#         if data == "PREVIEW_ON":
#             config.preview_active = True
#         elif data == "PREVIEW_OFF":
#             config.preview_active = False

#         # Unified App Launching
#         if command == "OPEN_NOTEPAD":
#             try:
#                 subprocess.Popen(['notepad.exe']) 
#             except Exception as e:
#                 print(f"Error: {e}")

#         elif command == "SHUTDOWN_LAPTOP":
#             os.system("shutdown /s /t 60")

#         elif command.startswith("SCHEDULE:"):
#             _, mins, cmd_to_run = command.split(":")
#             threading.Thread(target=scheduled_task, args=(int(mins)*60, cmd_to_run), daemon=True).start()

#         elif command.startswith("MOUSE_MOVE:"):
#             dx, dy = map(float, command.split(":")[1].split(","))
#             pyautogui.moveRel(dx * 2.5, dy * 2.5, _pause=False)

#         elif command == "MOUSE_CLICK": pyautogui.click()
#         elif command == "MOUSE_RIGHT_CLICK": pyautogui.rightClick()
#         elif command.startswith("MOUSE_SCROLL:"):
#             try:
#                 scroll_direction = int(command.split(":")[1])
#                 pyautogui.scroll(scroll_direction * 150) 
#             except Exception as e:
#                 print(f"Scroll Error: {e}")

#         elif command == "STOP_SERVER":
#             print("Remote Stop signal received.")
#             os._exit(0)
        
#         elif command == "MEDIA_PLAY_PAUSE":
#             pyautogui.press('playpause') # Triggered by double-tap
        
#         elif command.startswith("VOICE:"):
#             voice_text = command.split(":", 1)[1]
#             handle_voice_command(voice_text)
            
#         elif command == "NAV_NEXT":
#             pyautogui.press('right') # Works for PPT slides, Photos, and Video skips
#             print("   [Action] Navigation: Next")

#         elif command == "NAV_BACK":
#             pyautogui.press('left') # Works for PPT back, Photos, and Video rewinds
#             print("   [Action] Navigation: Back")
        
#         elif command == "SCREEN_BLACK":
#             # Special PPT shortcut: Pressing 'B' during a slideshow blacks out the screen
#             pyautogui.press('b')
#             print("   [Action] Presentation Blackout")
        
        
#         elif "KEY:" in command:
#             key = command.split(":")[1].lower()
#             pyautogui.press(key) # Handles up, down, left, right, esc
        
#         elif command.startswith("CLIPBOARD:"):
#             # Extract the text after the prefix
#             text_to_copy = command.split(":", 1)[1]
#             pyperclip.copy(text_to_copy)
#             print(f"   [Action] Copied to Clipboard: {text_to_copy[:30]}...")

#         elif command.startswith("DICTATE:"):
#             text_content = command.split(":", 1)[1]
#             # Small delay gives you time to ensure the cursor is in the right spot
#             time.sleep(0.5) 
#             # Use interval=0.01 to make it look like human typing
#             pyautogui.write(text_content, interval=0.01)
#             print(f"   [Action] Dictated: {text_content}")

#         elif command == "VOL_UP":
#             pyautogui.press("volumeup")
#         elif command == "VOL_DOWN":
#             pyautogui.press("volumedown")

#         elif command == "BRIGHT_UP":
#             current = sbc.get_brightness()[0]
#             sbc.set_brightness(min(100, current + 10))
#         elif command == "BRIGHT_DOWN":
#             current = sbc.get_brightness()[0]
#             sbc.set_brightness(max(0, current - 10))
#         elif command == "MUTE_TOGGLE":
#             pyautogui.press('volumemute')
#             print("   [Action] Mute Toggled")

#         elif command == "SHOW_DESKTOP":
#             pyautogui.hotkey('win', 'd')
#             print("   [Action] Minimizing All / Showing Desktop")
        
#         elif command == "APP_SWITCHER":
#             # This triggers the Windows Task View (Win + Tab)
#             # It's more visual and easier to navigate than a quick Alt+Tab
#             pyautogui.hotkey('win', 'tab')
#             print("   [Action] Application Switcher Opened")
        
#         elif command == "ZOOM_IN":
#             pyautogui.hotkey('ctrl', '=') # Standard Windows zoom in (+ is shift and =)
#             print("   [Action] Zooming In")

#         elif command == "ZOOM_OUT":
#             pyautogui.hotkey('ctrl', '-')
#             print("   [Action] Zooming Out")

#         elif command == "ZOOM_RESET":
#             pyautogui.hotkey('ctrl', '0')
#             print("   [Action] Resetting Zoom to 100%")

#         elif command.startswith("GESTURE:"):
#             try:
#                 raw = [tuple(map(float, p.split(","))) for p in command.split(":")[1].split("|")]
#                 if len(raw) < 5: return
#                 user_p = normalize_points(raw)
#                 v_dist = get_similarity(user_p, config.V_SHAPE_TEMPLATE)
#                 circle_dist = get_similarity(user_p, config.CIRCLE_TEMPLATE)
#                 caret_dist = get_similarity(user_p, config.CARET_TEMPLATE)
                
#                 # Check similarity against new templates
#                 if get_similarity(user_p, config.L_BRACKET) < 25:
#                     pyautogui.press('left') # Standard skip back
#                     print("   [Action] Skip Backward")
#                 elif get_similarity(user_p, config.R_BRACKET) < 25:
#                     pyautogui.press('right') # Standard skip forward
#                     print("   [Action] Skip Forward")
#                 elif circle_dist < 35:
#                     pyautogui.hotkey('ctrl', 'r')
#                     print(f"   [Action] Refresh (Dist: {circle_dist:.2f})")
#                 elif get_similarity(user_p, config.CARET_TEMPLATE) < 25:
#                     pyautogui.hotkey('win', 'up') # Maximize Window
#                     print("   [Action] Maximize Window")
#                 elif get_similarity(user_p, config.M_SHAPE_TEMPLATE) < 25:
#                     pyautogui.hotkey('win', 'd') # Show Desktop
#                     print("   [Action] Minimize All")
#                 if v_dist < 25:
#                     pyautogui.hotkey('win', 'down')
#                     print("   [Action] Mute Toggle")
#             except Exception as e:
#                 print(f"Gesture Error: {e}")
        
#         elif command.startswith("KEY:"):
#             try:
#                 key = command.split(":", 1)[1]
#                 if key == "BACKSPACE": pyautogui.press('backspace')
#                 elif key == "ENTER": pyautogui.press('enter')
#                 elif key == "SPACE": pyautogui.press('space')
#                 else: pyautogui.write(key)
#             except Exception as e:
#                 print(f"Keyboard Error: {e}")

#         elif command.startswith("MOUSE_SCROLL:"):
#             try:
#                 scroll_direction = int(command.split(":")[1])
#                 pyautogui.scroll(scroll_direction * 150) 
#             except Exception as e:
#                 print(f"Scroll Error: {e}")
#     except Exception as e:
#         print(f"Auth Error: {e}")



