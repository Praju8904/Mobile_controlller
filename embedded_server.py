"""
Embedded Server — Runs the UDP command server in-process.
Replaces launching server_1.py as a separate console window.
All server output appears in the GUI log panel automatically via LogRedirector.

Usage:
    server = EmbeddedServer()
    server.on_stop(callback)              # when remote STOP_SERVER received
    server.on_phone_connected(callback)   # when phone first connects
    server.start()
    ...
    server.stop()
"""

import socket
import threading
import time

import config
import services
import commands
import reverse_commands


# Module-level flag: service threads (file receiver, camera, broadcast)
# only need to start once per process, even across server restarts.
_services_started = False


class EmbeddedServer:
    """In-process UDP command server with clean lifecycle management."""

    def __init__(self):
        self.sock = None
        self._running = False
        self._thread = None
        self._command_count = 0
        self._start_time = None
        self._last_command_time = None
        self._callbacks = {
            "on_stop": None,
            "on_phone_connected": None,
        }

    # ─── PROPERTIES ─────────────────────────────────────────────

    @property
    def is_running(self):
        return self._running

    @property
    def command_count(self):
        return self._command_count

    @property
    def uptime_seconds(self):
        if self._start_time and self._running:
            return time.time() - self._start_time
        return 0

    @property
    def uptime_str(self):
        """Human-readable uptime string."""
        secs = int(self.uptime_seconds)
        if secs < 60:
            return f"{secs}s"
        elif secs < 3600:
            return f"{secs // 60}m {secs % 60}s"
        else:
            h = secs // 3600
            m = (secs % 3600) // 60
            return f"{h}h {m}m"

    # ─── CALLBACKS ──────────────────────────────────────────────

    def on_stop(self, callback):
        """Register callback for when server stops (e.g., remote STOP_SERVER)."""
        self._callbacks["on_stop"] = callback

    def on_phone_connected(self, callback):
        """Register callback for when a phone first connects."""
        self._callbacks["on_phone_connected"] = callback

    # ─── LIFECYCLE ──────────────────────────────────────────────

    def start(self):
        """Start the server in a background thread. Returns True if started."""
        if self._running:
            print("[Server] Already running")
            return False

        self._running = True
        self._start_time = time.time()
        self._command_count = 0
        commands.reset_stop_flag()

        self._thread = threading.Thread(
            target=self._server_loop, daemon=True, name="EmbeddedServer"
        )
        self._thread.start()
        return True

    def stop(self):
        """Gracefully stop the server."""
        if not self._running:
            return

        self._running = False

        # Close the socket to unblock recvfrom()
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

        # Wait for server thread to finish
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3)

        self._start_time = None

    # ─── SERVER LOOP ────────────────────────────────────────────

    def _server_loop(self):
        """Main server loop — runs in a background thread."""
        global _services_started

        try:
            # Start background service threads (once per process lifetime)
            if not _services_started:
                threading.Thread(
                    target=services.broadcast_identity,
                    daemon=True, name="Broadcast"
                ).start()
                threading.Thread(
                    target=services.receive_file,
                    daemon=True, name="FileReceiver"
                ).start()
                threading.Thread(
                    target=services.receive_camera_stream,
                    daemon=True, name="CameraStream"
                ).start()
                _services_started = True
                print("[Server] Background services started (broadcast, file receiver, camera)")

            # Create and bind the main UDP socket
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.sock.bind((config.HOST, config.PORT))
            self.sock.settimeout(1.0)

            print(f"[Server] Active on port {config.PORT} (embedded mode)")

            status_started = False
            preview_started = False

            while self._running:
                try:
                    data, addr = self.sock.recvfrom(4096)
                    client_ip = addr[0]

                    # Track phone connection
                    if not reverse_commands.is_connected():
                        reverse_commands.set_phone_ip(client_ip)
                        print(f"[*] Phone connected: {client_ip}")
                        cb = self._callbacks.get("on_phone_connected")
                        if cb:
                            try:
                                cb(client_ip)
                            except Exception:
                                pass

                    # Lazily start per-client service threads
                    if not status_started:
                        threading.Thread(
                            target=services.send_system_status,
                            args=(client_ip,), daemon=True, name="SystemStatus"
                        ).start()
                        status_started = True

                    if not preview_started:
                        threading.Thread(
                            target=services.send_live_preview,
                            args=(client_ip,), daemon=True, name="LivePreview"
                        ).start()
                        preview_started = True

                    # Process the command
                    self._command_count += 1
                    self._last_command_time = time.time()

                    response = commands.execute_command(data.decode('utf-8'), addr, self.sock)

                    if response:
                        self.sock.sendto(response.encode('utf-8'), addr)

                    # Check if remote stop was requested via STOP_SERVER command
                    if commands.is_stop_requested():
                        print("[Server] Remote stop signal received")
                        self._running = False
                        cb = self._callbacks.get("on_stop")
                        if cb:
                            try:
                                cb()
                            except Exception:
                                pass
                        break

                except socket.timeout:
                    continue
                except OSError:
                    # Socket was closed during shutdown
                    if self._running:
                        pass
                    break
                except Exception as e:
                    if self._running:
                        print(f"[Server] Error: {e}")

        except OSError as e:
            print(f"[Server] Could not bind port {config.PORT}: {e}")
        except Exception as e:
            print(f"[Server] Fatal error: {e}")
        finally:
            try:
                if self.sock:
                    self.sock.close()
            except Exception:
                pass
            self._running = False
            self._start_time = None
            print("[Server] Stopped")
