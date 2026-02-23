import os
import json

# Change this if your main drive is C:/
ALLOWED_ROOT = "D:/"

def list_directory(path):
    try:
        # 1. Path Security
        clean_path = os.path.normpath(path)
        if path == "ROOT" or not clean_path.startswith(os.path.normpath(ALLOWED_ROOT)):
            target_path = ALLOWED_ROOT
        else:
            target_path = clean_path

        print(f"[*] Scanning Directory: {target_path}")

        # 2. Get Files
        items = []
        if os.path.exists(target_path) and os.path.isdir(target_path):
            with os.scandir(target_path) as entries:
                for entry in entries:
                    try:
                        item_type = "FOLDER" if entry.is_dir() else "FILE"
                        items.append({
                            "name": entry.name,
                            "type": item_type,
                            "path": entry.path
                        })
                    except Exception:
                        continue
        
        # 3. Sort
        items.sort(key=lambda x: (x["type"] != "FOLDER", x["name"].lower()))
        
        # 4. Limit Size (CRITICAL FIX)
        # We limit to 20 items to prevent UDP packet overflow
        if len(items) > 3:
            print(f"[!] Directory too large ({len(items)} items). Truncating to 3.")
            items = items[:3]

        # 5. Result
        result = json.dumps({
            "current_path": target_path,
            "items": items
        })
        
        print(f"[*] Found {len(items)} items. JSON Size: {len(result)} bytes")
        return result

    except Exception as e:
        print(f"[!] File Error: {e}")
        return json.dumps({"error": str(e), "items": [], "current_path": "ERROR"})