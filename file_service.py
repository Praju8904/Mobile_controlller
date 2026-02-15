import os
import socket
import threading

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SEND_FOLDER = os.path.join(BASE_DIR, "send_file")

# Ensure folder exists
if not os.path.exists(SEND_FOLDER):
    os.makedirs(SEND_FOLDER)

def get_folder_contents():
    """Returns a string list of files in the send_file folder."""
    files = os.listdir(SEND_FOLDER)
    if not files:
        return "EMPTY"
    return "|".join(files) # Joins filenames with a pipe symbol

#

def send_specific_file(filename, phone_ip):
    # Determine the full path. 
    # If it's an absolute path (like from the screenshot logic), use it directly.
    # Otherwise, assume it's in the send_file folder.
    if os.path.isabs(filename):
        filepath = filename
        actual_filename = os.path.basename(filename) # Extract just "image.png" from path
    else:
        filepath = os.path.join(SEND_FOLDER, filename)
        actual_filename = filename

    if not os.path.exists(filepath):
        print(f"[!] File {filepath} not found.")
        return

    try:
        filesize = os.path.getsize(filepath)
        
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(10) # Give it time to connect
            s.connect((phone_ip, 5006))
            
            # 1. Create the Header: "filename|filesize\n"
            header = f"{actual_filename}|{filesize}\n"
            
            # 2. Send Header first
            s.sendall(header.encode('utf-8'))
            
            # 3. Send the File Content
            with open(filepath, 'rb') as f:
                while True:
                    chunk = f.read(8192)
                    if not chunk: break
                    s.sendall(chunk)
                    
            print(f"[*] Successfully sent {actual_filename}")
            
    except Exception as e:
        print(f"[!] Transfer failed: {e}")

def start_file_transfer(filename, phone_ip):
    FILE_PORT = 5006
    print(f"[DEBUG] Attempting to connect to phone at {phone_ip}:{FILE_PORT}") # ADD THIS
    
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as tcp_sock:
            tcp_sock.settimeout(5)
            tcp_sock.connect((phone_ip, FILE_PORT))
            print("[DEBUG] Connection Successful!") # ADD THIS
            
            # ... send file logic ...
    except Exception as e:
        print(f"[!] Connection Failed: {e}")