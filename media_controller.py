from ctypes import cast, POINTER
from comtypes import CLSCTX_ALL
from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
import pyautogui

def set_volume(action):
    try:
        devices = AudioUtilities.GetSpeakers()
        interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        volume = cast(interface, POINTER(IAudioEndpointVolume))
        
        if action == "MUTE":
            # Toggle Mute
            current = volume.GetMute()
            volume.SetMute(not current, None)
            print(f"[*] Volume Mute Toggled: {not current}")
            
        elif action == "UP":
            # Increase by ~10%
            current_vol = volume.GetMasterVolumeLevelScalar()
            new_vol = min(1.0, current_vol + 0.1)
            volume.SetMasterVolumeLevelScalar(new_vol, None)
            
        elif action == "DOWN":
            # Decrease by ~10%
            current_vol = volume.GetMasterVolumeLevelScalar()
            new_vol = max(0.0, current_vol - 0.1)
            volume.SetMasterVolumeLevelScalar(new_vol, None)

    except Exception as e:
        print(f"Audio Error: {e}")

def media_action(action):
    # Uses standard media keys (Works on YouTube, Spotify, VLC)
    if action == "PLAY_PAUSE":
        pyautogui.press("playpause")
    elif action == "NEXT":
        pyautogui.press("nexttrack")
    elif action == "PREV":
        pyautogui.press("prevtrack")