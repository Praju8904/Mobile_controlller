"""
QR Code Pairing System
Generates secure keys and a QR code for instant phone pairing.
The phone scans the QR → gets IP, ports, AES key, HMAC key.
No more hardcoded secrets or manual IP entry.
"""

import json
import os
import socket
import secrets
import qrcode
from io import BytesIO
from PIL import Image

PAIRING_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pairing_keys.json")

def get_local_ip():
    """Get the machine's LAN IP address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def get_hostname():
    return socket.gethostname()

def generate_keys():
    """Generate cryptographically secure random keys."""
    aes_key = secrets.token_hex(8)   # 16 chars = 128-bit AES key
    hmac_key = secrets.token_hex(16) # 32 chars for HMAC
    secret_key = secrets.token_hex(16)
    return aes_key, hmac_key, secret_key

def load_or_create_keys():
    """Load existing keys from file, or generate new ones."""
    if os.path.exists(PAIRING_FILE):
        try:
            with open(PAIRING_FILE, 'r') as f:
                data = json.load(f)
                # Validate all fields exist
                if all(k in data for k in ['aes_key', 'hmac_key', 'secret_key']):
                    return data['aes_key'], data['hmac_key'], data['secret_key']
        except (json.JSONDecodeError, KeyError):
            pass
    
    # Generate new keys
    aes_key, hmac_key, secret_key = generate_keys()
    save_keys(aes_key, hmac_key, secret_key)
    return aes_key, hmac_key, secret_key

def save_keys(aes_key, hmac_key, secret_key):
    """Persist keys to disk."""
    data = {
        'aes_key': aes_key,
        'hmac_key': hmac_key,
        'secret_key': secret_key
    }
    with open(PAIRING_FILE, 'w') as f:
        json.dump(data, f, indent=2)

def regenerate_keys():
    """Force-regenerate all keys (invalidates old pairings)."""
    aes_key, hmac_key, secret_key = generate_keys()
    save_keys(aes_key, hmac_key, secret_key)
    return aes_key, hmac_key, secret_key

def build_pairing_payload():
    """Build the JSON payload that goes into the QR code."""
    aes_key, hmac_key, secret_key = load_or_create_keys()
    ip = get_local_ip()
    hostname = get_hostname()
    
    payload = {
        "ip": ip,
        "hostname": hostname,
        "cmd_port": 5005,
        "file_port": 5006,
        "watchdog_port": 5007,
        "reverse_port": 6000,    # New: PC → Phone commands
        "aes_key": aes_key,
        "hmac_key": hmac_key,
        "secret_key": secret_key,
        "encryption": True
    }
    return payload

def generate_qr_image(size=300):
    """Generate QR code as a PIL Image."""
    payload = build_pairing_payload()
    payload_json = json.dumps(payload)
    
    qr = qrcode.QRCode(
        version=None,  # Auto-size
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=8,
        border=2,
    )
    qr.add_data(payload_json)
    qr.make(fit=True)
    
    img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    img = img.resize((size, size), Image.NEAREST)
    return img

def generate_qr_bytes(size=300):
    """Generate QR code as PNG bytes (for embedding in GUI)."""
    img = generate_qr_image(size)
    buffer = BytesIO()
    img.save(buffer, format="PNG")
    return buffer.getvalue()


if __name__ == "__main__":
    # Quick test
    payload = build_pairing_payload()
    print("Pairing Payload:")
    print(json.dumps(payload, indent=2))
    
    img = generate_qr_image()
    img.show()
    print("QR Code displayed!")
