from win10toast import ToastNotifier
import threading

toaster = ToastNotifier()

def show_notification(title, message, app_name):
    # Run in thread so it doesn't block the server loop
    def _show():
        try:
            # Clean up text
            clean_msg = message.replace("\\n", "\n")
            
            # Map common package names to readable names
            apps = {
                "com.whatsapp": "WhatsApp",
                "com.instagram.android": "Instagram",
                "com.google.android.gm": "Gmail",
                "com.twitter.android": "X (Twitter)"
            }
            readable_app = apps.get(app_name, app_name)

            toaster.show_toast(
                f"{readable_app}: {title}",
                clean_msg,
                duration=5,
                threaded=True
            )
            print(f"[*] Notification Mirrored: {title}")
        except Exception as e:
            print(f"Notification Error: {e}")

    threading.Thread(target=_show, daemon=True).start()