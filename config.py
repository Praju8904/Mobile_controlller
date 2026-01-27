import pyautogui
from geometry_utils import normalize_points

# --- SETUP ---
HOST, PORT = '0.0.0.0', 5005
FILE_PORT = 5006
DISCOVERY_PORT = 37020
# Add this line - Keep this key secret!

SECRET_KEY = "my_super_secret_project_key"

# --- SECURITY SETUP ---
USE_ENCRYPTION = True  # The main toggle
AES_KEY = "my_secret_16byte" # Must be 16 chars
HMAC_KEY = "my_hmac_secret_key"

# Optimized PyAutoGUI settings
pyautogui.FAILSAFE, pyautogui.PAUSE = False, 0

# --- GLOBAL STATE ---
# Shared between commands.py (writer) and services.py (reader)
preview_active = False

# --- REDEFINED TEMPLATES (Resampled) ---
V_SHAPE_TEMPLATE = normalize_points([(0, 0), (50, 100), (100, 0)])
S_SHAPE_TEMPLATE = normalize_points([(100, 0), (0, 0), (0, 100), (100, 100)])
L_BRACKET = normalize_points([(100, 0), (0, 50), (100, 100)])  # < (Back 5s)
R_BRACKET = normalize_points([(0, 0), (100, 50), (0, 100)])   # > (Forward 5s)
# O - Circle: For Refreshing a page
CIRCLE_TEMPLATE = normalize_points([
    (50, 0), (85, 15), (100, 50), (85, 85), 
    (50, 100), (15, 85), (0, 50), (15, 15), (50, 0)
])
# ^ - Caret: For Maximizing a window
CARET_TEMPLATE = normalize_points([(0,100), (50,0), (100,100)])

# M - Shape: For Minimizing all windows (Show Desktop)
M_SHAPE_TEMPLATE = normalize_points([(0,100), (25,0), (50,100), (75,0), (100,100)])
