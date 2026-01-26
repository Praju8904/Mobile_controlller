import socket
import os
import time
import base64
import psutil
import pyautogui
from io import BytesIO
import config

def send_system_status(target_ip):
    """Sends battery and system info back to the Android app."""
    status_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    while True:
        try:
            battery = psutil.sensors_battery()
            percent = battery.percent if battery else 0
            is_plugged = battery.power_plugged if battery else False
            
            # Format: STATUS:Percentage|PluggedIn
            message = f"STATUS:{percent}|{1 if is_plugged else 0}"
            status_sock.sendto(message.encode('utf-8'), (target_ip, 37021))
        except Exception as e:
            print(f"Status Error: {e}")
        time.sleep(5)

def broadcast_identity():
    broadcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    broadcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    msg = b"LAPTOP_SERVER_ACTIVE"
    while True:
        try:
            broadcast_sock.sendto(msg, ('<broadcast>', config.DISCOVERY_PORT))
        except: pass
        time.sleep(3)

def receive_file():
    file_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    file_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
    file_sock.bind((config.HOST, config.FILE_PORT))
    file_sock.listen(5)
    
    save_path = os.path.join(os.getcwd(), "Received_Files")
    if not os.path.exists(save_path): 
        os.makedirs(save_path)

    while True:
        conn, addr = file_sock.accept()
        try:
            # Use makefile to read the header as a clean line 
            fileobj = conn.makefile('rb')
            header_line = fileobj.readline().decode('utf-8').strip()
            
            if not header_line or '|' not in header_line:
                continue

            filename, filesize = header_line.split('|')
            filesize = int(filesize)
            full_path = os.path.join(save_path, filename)
            
            print(f"[*] Receiving: {filename} ({filesize} bytes)")

            with open(full_path, "wb") as f:
                remaining = filesize
                while remaining > 0:
                    # Read directly from the connection for file data 
                    chunk = conn.recv(min(remaining, 8192))
                    if not chunk: break
                    f.write(chunk)
                    remaining -= len(chunk)
            
            print(f"[*] Media Saved: {filename}")
            # Automatically handle opening media or folders 
            ext = filename.lower()
            if ext.endswith(('.mp4', '.mkv', '.avi', '.mp3', '.wav', '.flac', '.jpg', '.png')):
                os.startfile(full_path)
            else:
                os.startfile(save_path)

        except Exception as e:
            print(f"Transfer Error: {e}")
        finally:
            conn.close()

def send_live_preview(target_ip):
    preview_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    while True:
        if config.preview_active: # Only capture if the phone requested it
            try:
                screenshot = pyautogui.screenshot()
                screenshot.thumbnail((640, 360)) 
                buffered = BytesIO()
                screenshot.save(buffered, format="JPEG", quality=50)
                img_base64 = base64.b64encode(buffered.getvalue())
                preview_sock.sendto(b"IMG:" + img_base64, (target_ip, 37022))
            except Exception as e:
                print(f"Preview Error: {e}")
        time.sleep(0.5)
