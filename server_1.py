import socket
import threading
import config
import services
import commands
import reverse_commands

def start_server():
    # Start background threads exactly once
    threading.Thread(target=services.broadcast_identity, daemon=True).start()
    threading.Thread(target=services.receive_file, daemon=True).start()
    threading.Thread(target=services.receive_camera_stream, daemon=True).start()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((config.HOST, config.PORT))
    sock.settimeout(1.0) 
    print(f"Server Active on {config.PORT}")

    # --- INITIALIZE BOTH FLAGS HERE ---
    status_thread_started = False
    preview_thread_started = False
    
    while True:
        try:
            data, addr = sock.recvfrom(4096)
            client_ip = addr[0]
            
            # Track connected phone for reverse commands
            if not reverse_commands.is_connected():
                reverse_commands.set_phone_ip(client_ip)
                print(f"[*] Phone connected: {client_ip}")
            
            # START STATUS THREAD
            if not status_thread_started:
                threading.Thread(target=services.send_system_status, args=(client_ip,), daemon=True).start()
                status_thread_started = True
            
            # START PREVIEW THREAD
            if not preview_thread_started:
                threading.Thread(target=services.send_live_preview, args=(client_ip,), daemon=True).start()
                preview_thread_started = True

            # Execute command and check for response
            response = commands.execute_command(data.decode('utf-8'), addr, sock)

            
            # If command returns a response (like PONG), send it back
            if response:
                sock.sendto(response.encode('utf-8'), addr)
                
        except socket.timeout:
            continue
        except KeyboardInterrupt:
            print("\nShutting down...")
            break
        except Exception as e:
            print(f"Error: {e}")
    sock.close()

if __name__ == "__main__":
    start_server()