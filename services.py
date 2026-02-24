import socket
import os
import time
import base64
import threading
import psutil
import pyautogui
from io import BytesIO
import config
import cv2
import numpy as np

# ─── THREAD LIFECYCLE ───────────────────────────────────────────
# Two separate events to avoid the "global shutdown" bug:
#   _global_stop  → only set on full server shutdown (affects ALL threads)
#   _session_stop → set on phone IP change (affects only IP-bound threads)
_global_stop = threading.Event()
_session_stop = threading.Event()

def stop_session():
    """Stop only IP-bound threads (status, preview). Global threads keep running."""
    _session_stop.set()
    print("[services] Session stop signal sent.")

def reset_session():
    """Clear the session stop so new IP-bound threads can start."""
    _session_stop.clear()

def stop_all():
    """Stop ALL threads (global + session). Used only on full server shutdown."""
    _session_stop.set()
    _global_stop.set()
    print("[services] Global stop signal sent to all threads.")

def is_stopping():
    """Check if a full shutdown has been requested."""
    return _global_stop.is_set()


# ─── CAMERA STREAM (GLOBAL) ─────────────────────────────────────

def receive_camera_stream():
    stream_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    stream_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    stream_sock.bind(('0.0.0.0', 37023))
    stream_sock.settimeout(2.0)  # Prevents blocking — thread can check stop event
    print("[*] Waiting for Phone Camera stream on port 37023...")

    frame_count = 0
    fps_start = time.time()
    fps = 0

    try:
        while not _global_stop.is_set():
            try:
                data, addr = stream_sock.recvfrom(60000)
            except socket.timeout:
                continue
            
            np_arr = np.frombuffer(data, dtype=np.uint8)
            frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
            
            if frame is not None:
                frame_count += 1
                elapsed = time.time() - fps_start
                if elapsed >= 1.0:
                    fps = frame_count / elapsed
                    frame_count = 0
                    fps_start = time.time()

                cv2.putText(frame, f"FPS: {fps:.1f}", (10, 30),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
                cv2.imshow("Phone Camera Stream", frame)
                
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
    except Exception as e:
        print(f"Stream Error: {e}")
    finally:
        cv2.destroyAllWindows()
        stream_sock.close()
        print("[services] Camera stream thread stopped.")


# ─── SYSTEM STATUS (SESSION — IP-BOUND) ─────────────────────────

def send_system_status(target_ip):
    """Sends battery and system info back to the Android app."""
    status_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        while not _session_stop.is_set():
            try:
                battery = psutil.sensors_battery()
                percent = battery.percent if battery else 0
                is_plugged = battery.power_plugged if battery else False
                
                message = f"STATUS:{percent}|{1 if is_plugged else 0}"
                status_sock.sendto(message.encode('utf-8'), (target_ip, 37021))
            except Exception as e:
                print(f"Status Error: {e}")
            _session_stop.wait(5)
    finally:
        status_sock.close()
        print(f"[services] Status thread stopped (was targeting {target_ip}).")


# ─── BROADCAST IDENTITY (GLOBAL) ────────────────────────────────

def broadcast_identity():
    broadcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    broadcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    msg = b"LAPTOP_SERVER_ACTIVE"
    try:
        while not _global_stop.is_set():
            try:
                broadcast_sock.sendto(msg, ('<broadcast>', config.DISCOVERY_PORT))
            except Exception:
                pass
            _global_stop.wait(1)
    finally:
        broadcast_sock.close()
        print("[services] Broadcast identity thread stopped.")


# ─── FILE RECEIVER (GLOBAL) ─────────────────────────────────────

def receive_file():
    file_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    file_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
    file_sock.bind((config.HOST, config.FILE_PORT))
    file_sock.listen(5)
    file_sock.settimeout(2.0)  # Prevents blocking accept()
    
    save_path = os.path.join(os.getcwd(), "Received_Files")
    if not os.path.exists(save_path): 
        os.makedirs(save_path)

    try:
        while not _global_stop.is_set():
            try:
                conn, addr = file_sock.accept()
            except socket.timeout:
                continue

            try:
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
                        # Check stop event during large file transfers
                        if _global_stop.is_set():
                            print(f"[!] Transfer aborted mid-file: {filename}")
                            break
                        chunk = conn.recv(min(remaining, 8192))
                        if not chunk: break
                        f.write(chunk)
                        remaining -= len(chunk)
                
                if remaining == 0:
                    print(f"[*] Media Saved: {filename}")

                    try:
                        import chat_module
                        chat_module.append_chat_message(full_path, msg_type="file", is_user=False) 
                    except Exception:
                        pass

                    ext = filename.lower()
                    if ext.endswith(('.mp4', '.mkv', '.avi', '.mp3', '.wav', '.flac', '.jpg', '.png')):
                        os.startfile(full_path)
                    else:
                        os.startfile(save_path)

            except Exception as e:
                print(f"Transfer Error: {e}")
            finally:
                conn.close()
    finally:
        file_sock.close()
        print("[services] File receiver thread stopped.")


# ─── LIVE PREVIEW (SESSION — IP-BOUND) ──────────────────────────

def send_live_preview(target_ip):
    preview_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        while not _session_stop.is_set():
            if config.preview_active:
                try:
                    screenshot = pyautogui.screenshot()
                    screenshot.thumbnail((640, 360)) 
                    buffered = BytesIO()
                    screenshot.save(buffered, format="JPEG", quality=50)
                    img_base64 = base64.b64encode(buffered.getvalue())
                    preview_sock.sendto(b"IMG:" + img_base64, (target_ip, 37022))
                except Exception as e:
                    print(f"Preview Error: {e}")
            _session_stop.wait(0.5)
    finally:
        preview_sock.close()
        print(f"[services] Preview thread stopped (was targeting {target_ip}).")
