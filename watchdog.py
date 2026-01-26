import socket
import subprocess
import os
import logging
import time

# 1. Setup Logging - check 'watchdog_log.txt' if things don't work
logging.basicConfig(
    filename='watchdog_log.txt', 
    level=logging.INFO, 
    format='%(asctime)s - %(message)s'
)

WATCHDOG_PORT = 5007

# 2. Setup Absolute Paths
# This ensures the script finds 'server.pyw' regardless of how it is launched
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MAIN_SERVER_PATH = os.path.join(BASE_DIR, "server_1.py") 

def start_watchdog():
    # Create UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    try:
        sock.bind(('0.0.0.0', WATCHDOG_PORT))
        logging.info(f"Watchdog active on port {WATCHDOG_PORT}")
        print(f"Watchdog listening on port {WATCHDOG_PORT}")
    except Exception as e:
        logging.error(f"Could not bind to port: {e}")
        return

    while True:
        try:
            # Buffer size 1024 is plenty for simple text commands
            data, addr = sock.recvfrom(1024)
            command = data.decode('utf-8').strip()
            
            if command == "DISCOVERY_REQUEST":
                logging.info(f"Discovery request received from {addr[0]}")
                # Reply to the phone on port 37020
                sock.sendto(b"LAPTOP_IP_FOUND", (addr[0], 37020))
                print(f"Sent LAPTOP_IP_FOUND to {addr[0]}:37020")

            elif command == "START_MAIN_SERVER":
                logging.info(f"Received START signal from {addr[0]}")
                
                # Check if 'server.pyw' is already running
                check_process = subprocess.check_output('tasklist', shell=True).decode()
                
                # Launcher logic
                if os.path.exists(MAIN_SERVER_PATH):
                    subprocess.Popen(['pythonw', MAIN_SERVER_PATH], shell=True)
                    logging.info("Main server process launched.")
                    print("Main server launched")
                else:
                    logging.error(f"Server file not found: {MAIN_SERVER_PATH}")
                
            elif command == "STOP_MAIN_SERVER":
                logging.info(f"Received STOP signal from {addr[0]}")
                # This kills all background python processes EXCEPT the one running this script
                current_pid = os.getpid()
                os.system(f"taskkill /f /fi \"PID ne {current_pid}\" /im pythonw.exe")
                logging.info("Main server process killed.")
                print("Main server stopped")

        except KeyboardInterrupt:
            print("\nShutting down watchdog...")
            logging.info("Watchdog shutdown by user")
            sock.close()
            break
            
        except Exception as e:
            logging.error(f"Watchdog Loop Error: {e}")
            print(f"Error: {e}")
            time.sleep(1)  # Prevent rapid-fire errors from locking the CPU

if __name__ == "__main__":
    start_watchdog()
