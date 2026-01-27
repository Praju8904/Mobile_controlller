import socket
import subprocess
import os
import logging
import time
import threading
import services

# Setup Logging
logging.basicConfig(
    level=logging.INFO, 
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("watchdog_log.txt", mode='a', delay=False),
        logging.StreamHandler()
    ]
)

WATCHDOG_PORT = 5007
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MAIN_SERVER_PATH = os.path.join(BASE_DIR, "server_1.py") 
stop_event = threading.Event()
MY_PID = os.getpid()

def kill_other_python_processes():
    """Kills all python processes except for the watchdog itself."""
    logging.info("Cleaning up existing Python processes...")
    # This command deletes any process named 'python.exe' or 'pythonw.exe' 
    # that is NOT the current watchdog PID
    cmd = f'wmic process where "name like \'python%\' and ProcessId != {MY_PID}" delete'
    os.system(cmd)
    time.sleep(1) # Give Windows a second to release the ports

def is_server_running():
    # Improved check using WMIC
    check = os.popen('wmic process where "commandline like \'%server_1.py%\'" get processid').read()
    # If the output contains a number, it means it's running
    return any(char.isdigit() for char in check)

def watchdog_listener():
    # Start identity broadcast
    threading.Thread(target=services.broadcast_identity, daemon=True).start()
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.bind(('0.0.0.0', WATCHDOG_PORT))
        sock.settimeout(1.0)
        logging.info(f"Watchdog active (PID: {MY_PID}) on port {WATCHDOG_PORT}")
    except Exception as e:
        logging.error(f"Bind Error: {e}")
        return

    # Use the stop_event instead of while True for a clean exit
    while not stop_event.is_set():
        try:
            data, addr = sock.recvfrom(1024)
            command = data.decode('utf-8', errors='ignore').strip()

            if "START_MAIN_SERVER" in command or "WAKE_UP" in command:
                logging.info(f"Start request from {addr[0]}. Clean launch...")
                kill_other_python_processes()
                
                subprocess.Popen(
                    ['python', MAIN_SERVER_PATH],
                    shell=True,
                    cwd=BASE_DIR,
                    creationflags=subprocess.CREATE_NEW_CONSOLE
                )
                logging.info(f"Cleanly launched: {MAIN_SERVER_PATH}")

            elif "STOP_MAIN_SERVER" in command:
                logging.info(f"Stop request from {addr[0]}. Cleaning up...")
                kill_other_python_processes()
                logging.info("Secondary processes stopped. Watchdog idle.")

        except socket.timeout:
            continue # This is normal, it just loops back to check stop_event
        except Exception as e:
            # If the socket was closed by another thread, we just exit
            if stop_event.is_set():
                break
            logging.error(f"Loop Error: {e}")
            time.sleep(0.1)

    # --- THE FIX: sock.close() MUST be outside the while loop ---
    try:
        sock.close()
        logging.info("Socket closed cleanly.")
    except:
        pass
    logging.info("Watchdog listener thread terminated.")

if __name__ == "__main__":
    listener_thread = threading.Thread(target=watchdog_listener, daemon=True)
    listener_thread.start()

    print(f"Watchdog is running on port {WATCHDOG_PORT}...")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nStopping watchdog...")
        stop_event.set()
        listener_thread.join(timeout=2)
        print("Goodbye!")