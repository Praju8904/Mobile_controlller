import psutil
import socket

# Config
BROADCAST_PORT = 6000 
udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

def send_snapshot(target_ip):
    try:
        # Get Stats (One-time)
        cpu = int(psutil.cpu_percent(interval=0.1)) # Fast read
        ram = int(psutil.virtual_memory().percent)
        
        battery = psutil.sensors_battery()
        bat_level = int(battery.percent) if battery else 100
        is_plugged = 1 if (battery and battery.power_plugged) else 0

        # Send Single Packet
        msg = f"SYS_INFO:{cpu}|{ram}|{bat_level}|{is_plugged}"
        udp_socket.sendto(msg.encode(), (target_ip, BROADCAST_PORT))
        print(f" -> Sent Snapshot: CPU {cpu}% | RAM {ram}%")
        
    except Exception as e:
        print(f"Monitor Error: {e}")