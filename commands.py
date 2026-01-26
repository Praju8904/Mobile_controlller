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

def execute_command(data):
    command = data.strip()
    if not command: return
    print(f"[*] Command: {command}")

    if data == "PREVIEW_ON":
        config.preview_active = True
    elif data == "PREVIEW_OFF":
        config.preview_active = False

    # Unified App Launching
    if command == "OPEN_NOTEPAD":
        try:
            subprocess.Popen(['notepad.exe']) 
        except Exception as e:
            print(f"Error: {e}")

    elif command == "SHUTDOWN_LAPTOP":
        os.system("shutdown /s /t 60")

    elif command.startswith("SCHEDULE:"):
        _, mins, cmd_to_run = command.split(":")
        threading.Thread(target=scheduled_task, args=(int(mins)*60, cmd_to_run), daemon=True).start()

    elif command.startswith("MOUSE_MOVE:"):
        dx, dy = map(float, command.split(":")[1].split(","))
        pyautogui.moveRel(dx * 2.5, dy * 2.5, _pause=False)

    elif command == "MOUSE_CLICK": pyautogui.click()
    elif command == "MOUSE_RIGHT_CLICK": pyautogui.rightClick()
    elif command.startswith("MOUSE_SCROLL:"):
        try:
            scroll_direction = int(command.split(":")[1])
            pyautogui.scroll(scroll_direction * 150) 
        except Exception as e:
            print(f"Scroll Error: {e}")

    elif command == "STOP_SERVER":
        print("Remote Stop signal received.")
        os._exit(0)
    
    elif command == "MEDIA_PLAY_PAUSE":
        pyautogui.press('playpause') # Triggered by double-tap
    
    elif command.startswith("VOICE:"):
        voice_text = command.split(":", 1)[1]
        handle_voice_command(voice_text)
        
    elif command == "NAV_NEXT":
        pyautogui.press('right') # Works for PPT slides, Photos, and Video skips
        print("   [Action] Navigation: Next")

    elif command == "NAV_BACK":
        pyautogui.press('left') # Works for PPT back, Photos, and Video rewinds
        print("   [Action] Navigation: Back")
    
    elif command == "SCREEN_BLACK":
        # Special PPT shortcut: Pressing 'B' during a slideshow blacks out the screen
        pyautogui.press('b')
        print("   [Action] Presentation Blackout")
    
    elif "KEY:" in command:
        key = command.split(":")[1].lower()
        pyautogui.press(key) # Handles up, down, left, right, esc
    
    elif command.startswith("CLIPBOARD:"):
        # Extract the text after the prefix
        text_to_copy = command.split(":", 1)[1]
        pyperclip.copy(text_to_copy)
        print(f"   [Action] Copied to Clipboard: {text_to_copy[:30]}...")

    elif command.startswith("DICTATE:"):
        text_content = command.split(":", 1)[1]
        # Small delay gives you time to ensure the cursor is in the right spot
        time.sleep(0.5) 
        # Use interval=0.01 to make it look like human typing
        pyautogui.write(text_content, interval=0.01)
        print(f"   [Action] Dictated: {text_content}")

    elif command == "VOL_UP":
        pyautogui.press("volumeup")
    elif command == "VOL_DOWN":
        pyautogui.press("volumedown")

    elif command == "BRIGHT_UP":
        current = sbc.get_brightness()[0]
        sbc.set_brightness(min(100, current + 10))
    elif command == "BRIGHT_DOWN":
        current = sbc.get_brightness()[0]
        sbc.set_brightness(max(0, current - 10))
    elif command == "MUTE_TOGGLE":
        pyautogui.press('volumemute')
        print("   [Action] Mute Toggled")

    elif command == "SHOW_DESKTOP":
        pyautogui.hotkey('win', 'd')
        print("   [Action] Minimizing All / Showing Desktop")
    
    elif command == "APP_SWITCHER":
        # This triggers the Windows Task View (Win + Tab)
        # It's more visual and easier to navigate than a quick Alt+Tab
        pyautogui.hotkey('win', 'tab')
        print("   [Action] Application Switcher Opened")
    
    elif command == "ZOOM_IN":
        pyautogui.hotkey('ctrl', '=') # Standard Windows zoom in (+ is shift and =)
        print("   [Action] Zooming In")

    elif command == "ZOOM_OUT":
        pyautogui.hotkey('ctrl', '-')
        print("   [Action] Zooming Out")

    elif command == "ZOOM_RESET":
        pyautogui.hotkey('ctrl', '0')
        print("   [Action] Resetting Zoom to 100%")

    elif command.startswith("GESTURE:"):
        try:
            raw = [tuple(map(float, p.split(","))) for p in command.split(":")[1].split("|")]
            if len(raw) < 5: return
            user_p = normalize_points(raw)
            v_dist = get_similarity(user_p, config.V_SHAPE_TEMPLATE)
            circle_dist = get_similarity(user_p, config.CIRCLE_TEMPLATE)
            caret_dist = get_similarity(user_p, config.CARET_TEMPLATE)
            
            # Check similarity against new templates
            if get_similarity(user_p, config.L_BRACKET) < 25:
                pyautogui.press('left') # Standard skip back
                print("   [Action] Skip Backward")
            elif get_similarity(user_p, config.R_BRACKET) < 25:
                pyautogui.press('right') # Standard skip forward
                print("   [Action] Skip Forward")
            elif circle_dist < 35:
                pyautogui.hotkey('ctrl', 'r')
                print(f"   [Action] Refresh (Dist: {circle_dist:.2f})")
            elif get_similarity(user_p, config.CARET_TEMPLATE) < 25:
                pyautogui.hotkey('win', 'up') # Maximize Window
                print("   [Action] Maximize Window")
            elif get_similarity(user_p, config.M_SHAPE_TEMPLATE) < 25:
                pyautogui.hotkey('win', 'd') # Show Desktop
                print("   [Action] Minimize All")
            if v_dist < 25:
                pyautogui.hotkey('win', 'down')
                print("   [Action] Mute Toggle")
        except Exception as e:
            print(f"Gesture Error: {e}")
    
    elif command.startswith("KEY:"):
        try:
            key = command.split(":", 1)[1]
            if key == "BACKSPACE": pyautogui.press('backspace')
            elif key == "ENTER": pyautogui.press('enter')
            elif key == "SPACE": pyautogui.press('space')
            else: pyautogui.write(key)
        except Exception as e:
            print(f"Keyboard Error: {e}")

    elif command.startswith("MOUSE_SCROLL:"):
        try:
            scroll_direction = int(command.split(":")[1])
            pyautogui.scroll(scroll_direction * 150) 
        except Exception as e:
            print(f"Scroll Error: {e}")
