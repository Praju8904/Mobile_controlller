import socket
import threading
import config
import services
import commands
import reverse_commands

def start_server():
    # Start global threads (these survive phone reconnections)
    global_threads = []
    for target in [services.broadcast_identity, services.receive_file, services.receive_camera_stream]:
        t = threading.Thread(target=target, daemon=True)
        t.start()
        global_threads.append(t)

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((config.HOST, config.PORT))
    sock.settimeout(1.0) 
    print(f"Server Active on {config.PORT}")

    # ─── Session thread tracking ───
    current_phone_ip = None
    ip_threads = []          # Threads tied to a specific phone IP

    def start_ip_threads(client_ip):
        """Start threads that target a specific phone IP."""
        nonlocal current_phone_ip, ip_threads
        current_phone_ip = client_ip

        t_status = threading.Thread(
            target=services.send_system_status,
            args=(client_ip,),
            daemon=True
        )
        t_preview = threading.Thread(
            target=services.send_live_preview,
            args=(client_ip,),
            daemon=True
        )
        ip_threads = [t_status, t_preview]
        for t in ip_threads:
            t.start()
        print(f"[server] Started session threads for {client_ip}")

    def stop_ip_threads():
        """Stop session threads and wait for them to exit."""
        nonlocal ip_threads
        if not ip_threads:
            return
        # Only stop session threads — global threads keep running
        services.stop_session()
        for t in ip_threads:
            t.join(timeout=2)  # Short timeout: session threads exit fast via wait()
        services.reset_session()
        ip_threads = []
        print("[server] Session threads stopped and cleared.")

    while True:
        try:
            data, addr = sock.recvfrom(4096)
            client_ip = addr[0]
            
            # Track connected phone for reverse commands
            if not reverse_commands.is_connected():
                reverse_commands.set_phone_ip(client_ip)
                print(f"[*] Phone connected: {client_ip}")
            
            # First connection — start IP-bound threads
            if current_phone_ip is None:
                start_ip_threads(client_ip)
            elif current_phone_ip != client_ip:
                # Phone IP changed — restart session threads only
                print(f"[server] Phone IP changed: {current_phone_ip} → {client_ip}")
                stop_ip_threads()
                reverse_commands.set_phone_ip(client_ip)
                start_ip_threads(client_ip)

            # Execute command and check for response
            response = commands.execute_command(data.decode('utf-8'), addr, sock)
            
            if response:
                sock.sendto(response.encode('utf-8'), addr)
                
        except socket.timeout:
            continue
        except KeyboardInterrupt:
            print("\nShutting down...")
            break
        except Exception as e:
            print(f"Error: {e}")

    # ─── Full shutdown — stop everything ───
    services.stop_all()
    for t in ip_threads + global_threads:
        t.join(timeout=3)
    sock.close()
    print("[server] Server shut down cleanly.")

if __name__ == "__main__":
    start_server()