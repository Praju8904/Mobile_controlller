# Secure Unified Desktop Administration System (SUDAS)

A centralized, secure, and low-latency system for remote desktop administration and control using an Android device over a Local Area Network (LAN).  
This project provides a powerful, privacy-focused alternative to commercial tools like TeamViewer or AnyDesk, designed specifically for secure, offline environments.

---

## 🧠 About The Project

Remote system administration often requires expensive physical peripherals or heavy reliance on internet-based commercial software, which introduces latency and privacy risks.  
This leads to dependency on third-party servers and security vulnerabilities in sensitive local networks.

This project solves that problem by implementing a **robust Client–Server architecture using Hybrid UDP/TCP protocols**.  
A central **Python Server**, running on the host machine, is controlled by a secure **Android Client** that acts as a universal input and monitoring device.

Administrators can:
- Control Mouse and Keyboard with sub-10ms latency  
- Transfer files securely between Phone and PC  
- Stream the PC screen (Live Preview) to the phone  
- Use the phone camera as a wireless PC Webcam  
- Monitor system health via a Watchdog service — all from one mobile dashboard

---

## ⚙️ Key Features

### 🏗️ Architecture & Network
- 🔒 **Secure Communication:** All traffic is encrypted using AES-256 (CBC Mode) and authenticated via HMAC-SHA256 signatures.  
- 🌐 **Resilient Networking:** The Android client can auto-discover the PC using UDP broadcasts—no manual IP entry required.  
- ⚡ **Hybrid Protocol Stack:** Uses UDP for real-time input/video and TCP for reliable, chunk-based file transfer.  
- 🔄 **Self-Healing Server:** A background Watchdog process monitors the main server and automatically restarts it if it crashes.

### 🎮 Remote Control & Input
- 🖱️ **Gesture Mapping:** Maps mobile touch gestures (Tap, Scroll, Drag) to system-level Windows API events via PyAutoGUI.  
- ⌨️ **Smart Keyboard:** Supports full text typing, special keys (Win, Alt, Ctrl), and shortcut combinations.  
- 📐 **Sensitivity Control:** Dynamic scaling of coordinates ensures smooth cursor movement across different screen resolutions.

### 📺 Multimedia & File Management
- 📸 **Desktop Live Preview:** Real-time screen mirroring from PC to Android using high-efficiency JPEG compression.  
- 🎥 **Wireless Webcam:** Transforms the Android device's camera into a video input source for the PC via OpenCV.  
- 📂 **Seamless File Transfer:** Send images, documents, and videos from the phone storage directly to the PC desktop.

### 🛡️ Security & Reliability
- 🧱 **Anti-Replay Protection:** Timestamp-based validation prevents packet replay attacks.  
- 🔑 **Shared Secret Auth:** A pre-shared key mechanism ensures only authorized mobile devices can issue commands.  
- 🚫 **Local Execution:** Operates entirely on LAN, ensuring no data ever leaves the local network.

---

## 🧰 Tech Stack

- **Server Language:** Python 3.10+  
- **Client Language:** Java (Android SDK)  
- **Networking:** socket (TCP/UDP), threading  
- **Automation:** PyAutoGUI, Keyboard  
- **Image Processing:** OpenCV (cv2), Pillow (PIL)  
- **Security:** Cryptography (AES, HMAC), hashlib  

---

## 📁 Directory Structure

```text
server/
  ├── services.py             # Main server logic (Mouse, Keyboard, Video)
  ├── watchdog.py             # Self-healing process & Auto-Discovery
  ├── security.py             # AES Encryption & HMAC Verification logic
  ├── file_transfer.py        # TCP Server for receiving files
  ├── config.py               # Configuration loader (Ports, Sensitivities)
  ├── utils.py                # Helper functions for screen capture
  ├── requirements.txt        # Python dependencies
  └── logs/                   # Application logs
      └── server.log

android_client/
  ├── app/src/main/java/com/project/sudas/
  │   ├── MainActivity.java   # Main UI & Dashboard
  │   ├── TouchpadActivity.java # Gesture detection logic
  │   ├── NetworkService.java # Background UDP/TCP sender
  │   ├── SecurityUtils.java  # AES Encryption & HMAC Generation
  │   ├── FileSender.java     # File transfer logic
  │   └── StreamViewer.java   # Live Preview renderer
  ├── res/layout/             # XML UI Layouts
  └── AndroidManifest.xml     # Permissions (Internet, Storage, Camera)
