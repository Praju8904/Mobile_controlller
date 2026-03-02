package com.prajwal.myfirstapp.hub;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.chat.ChatActivity;
import com.prajwal.myfirstapp.connectivity.ConnectionManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LaptopHubActivity extends AppCompatActivity {

    private ConnectionManager connectionManager;
    private boolean isConnected = false;
    private String serverIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_laptop_hub);

        serverIp = getIntent().getStringExtra("server_ip");
        isConnected = getIntent().getBooleanExtra("is_connected", false);
        if (serverIp == null) serverIp = "";
        connectionManager = new ConnectionManager(serverIp);

        // Back button
        findViewById(R.id.btnLaptopHubBack).setOnClickListener(v -> finish());

        // Update header
        TextView tvPcName = findViewById(R.id.tvLaptopHubPcName);
        TextView tvConnStatus = findViewById(R.id.tvLaptopHubStatus);
        View connectBanner = findViewById(R.id.laptopHubConnectBanner);

        if (isConnected && !serverIp.isEmpty()) {
            tvPcName.setText("Connected: " + serverIp);
            tvConnStatus.setText("● Connected");
            tvConnStatus.setTextColor(0xFF22C55E);
            connectBanner.setVisibility(View.GONE);
        } else {
            tvPcName.setText("PC Control Hub");
            tvConnStatus.setText("● Not Connected");
            tvConnStatus.setTextColor(0xFFEF4444);
            connectBanner.setVisibility(View.VISIBLE);
        }

        // Connect button in banner
        findViewById(R.id.btnLaptopHubConnect).setOnClickListener(v -> {
            finish(); // Go back to main activity to connect
        });

        setupHubCards();
    }

    private void setupHubCards() {
        // Touchpad
        findViewById(R.id.hubCardTouchpad).setOnClickListener(v -> {
            finish(); // Return to MainActivity, user navigates to touchpad from there
            Toast.makeText(this, "Use the touchpad from the main screen", Toast.LENGTH_SHORT).show();
        });

        // Keyboard
        findViewById(R.id.hubCardKeyboard).setOnClickListener(v -> {
            finish();
            Toast.makeText(this, "Use the keyboard from the main screen", Toast.LENGTH_SHORT).show();
        });

        // Presenter
        findViewById(R.id.hubCardPresenter).setOnClickListener(v -> {
            finish();
            Toast.makeText(this, "Use presenter mode from the main screen", Toast.LENGTH_SHORT).show();
        });

        // File Transfer
        findViewById(R.id.hubCardFileTransfer).setOnClickListener(v -> {
            Toast.makeText(this, "File Transfer — use the main screen", Toast.LENGTH_SHORT).show();
        });

        // AI Assistant
        findViewById(R.id.hubCardAI).setOnClickListener(v -> {
            connectionManager.sendCommand("VOICE:");
            Toast.makeText(this, "AI Assistant — use voice command", Toast.LENGTH_SHORT).show();
        });

        // PC Control
        findViewById(R.id.hubCardPCControl).setOnClickListener(v -> showPCControlMenu());

        // Media & Apps
        findViewById(R.id.hubCardMedia).setOnClickListener(v -> showMediaMenu());

        // Dynamic Bar
        findViewById(R.id.hubCardDynamicBar).setOnClickListener(v -> {
            Toast.makeText(this, "Dynamic Bar — toggle from main screen", Toast.LENGTH_SHORT).show();
        });

        // Chat
        findViewById(R.id.hubCardChat).setOnClickListener(v -> {
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("server_ip", serverIp);
            startActivity(chatIntent);
        });

        // System Monitor
        findViewById(R.id.hubCardSystemMonitor).setOnClickListener(v -> showSystemMonitorMenu());

        // Power Control
        findViewById(R.id.hubCardPowerControl).setOnClickListener(v -> showPowerControlMenu());

        // Shortcuts
        findViewById(R.id.hubCardShortcuts).setOnClickListener(v -> showShortcutsMenu());

        // Browser Remote
        findViewById(R.id.hubCardBrowserRemote).setOnClickListener(v -> showBrowserRemoteMenu());

        // Game Controller
        findViewById(R.id.hubCardGameController).setOnClickListener(v ->
            Toast.makeText(this, "Game Controller — coming soon!", Toast.LENGTH_SHORT).show());

        // Whiteboard
        findViewById(R.id.hubCardWhiteboard).setOnClickListener(v ->
            Toast.makeText(this, "Whiteboard — coming soon!", Toast.LENGTH_SHORT).show());
    }

    private void showPCControlMenu() {
        String[] options = {
            "🔊  Volume Up", "🔉  Volume Down", "🔇  Mute Toggle",
            "🔆  Brightness Up", "🔅  Brightness Down",
            "🖥️  Show Desktop", "🔄  App Switcher",
            "📸  Screenshot (Save)", "📸  Screenshot (Send)",
            "⏱️  Schedule Shutdown",
            "🔍  Zoom In", "🔎  Zoom Out", "💯  Reset Zoom",
            "🖥️  Screen Black / Wake", "⎋  Escape Key"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎮 PC Control")
            .setItems(options, (dialog, which) -> {
                String[] commands = {
                    "VOL_UP", "VOL_DOWN", "MUTE_TOGGLE",
                    "BRIGHT_UP", "BRIGHT_DOWN",
                    "SHOW_DESKTOP", "APP_SWITCHER",
                    "SCREENSHOT_LOCAL", "SCREENSHOT_SEND",
                    null, "ZOOM_IN", "ZOOM_OUT", "ZOOM_RESET",
                    "SCREEN_BLACK", "KEY:ESC"
                };
                if (commands[which] != null)
                    connectionManager.sendCommand(commands[which]);
            })
            .show();
    }

    private void showMediaMenu() {
        String[] options = {"📝  Open Notepad", "📷  Webcam Stream"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎬 Media & Apps")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: connectionManager.sendCommand("OPEN_NOTEPAD"); break;
                    case 1: connectionManager.sendCommand("CAMERA_STREAM"); break;
                }
            })
            .show();
    }

    private void showSystemMonitorMenu() {
        String[] options = {"📊  CPU Usage", "💾  RAM Usage", "🖥️  GPU Info", "💿  Disk Usage", "📶  Network Speed"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📊 System Monitor")
            .setItems(options, (dialog, which) -> {
                String[] cmds = {"SYS_CPU", "SYS_RAM", "SYS_GPU", "SYS_DISK", "SYS_NET"};
                connectionManager.sendCommand(cmds[which]);
            })
            .show();
    }

    private void showPowerControlMenu() {
        String[] options = {"⏻  Shutdown PC", "🔄  Restart PC", "💤  Sleep PC", "🔒  Lock PC", "🚪  Log Off"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⏻ Power Control")
            .setItems(options, (dialog, which) -> {
                String[] commands = {"SHUTDOWN_LAPTOP", "RESTART_PC", "SLEEP_PC", "LOCK_PC", "LOGOFF_PC"};
                String[] labels = {"Shutting down", "Restarting", "Sleeping", "Locking", "Logging off"};
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Confirm: " + options[which])
                    .setMessage(labels[which] + " your PC. Are you sure?")
                    .setPositiveButton("Confirm", (d, w) -> {
                        connectionManager.sendCommand(commands[which]);
                        Toast.makeText(this, labels[which] + " PC...", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .show();
    }

    private void showShortcutsMenu() {
        String[] shortcuts = {
            "Alt+Tab - Switch Apps", "Win+D - Show Desktop", "Ctrl+Alt+Del - Security Screen",
            "PrtScr - Screenshot", "Win+L - Lock Screen",
            "Ctrl+C - Copy", "Ctrl+V - Paste", "Ctrl+Z - Undo", "Ctrl+Shift+Esc - Task Manager"
        };
        String[] commands = {
            "KEY_COMBO:ALT+TAB", "KEY_COMBO:WIN+D", "KEY_COMBO:CTRL+ALT+DEL",
            "KEY:PRINTSCREEN", "KEY_COMBO:WIN+L",
            "KEY_COMBO:CTRL+C", "KEY_COMBO:CTRL+V", "KEY_COMBO:CTRL+Z", "KEY_COMBO:CTRL+SHIFT+ESC"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⌨ Custom Shortcuts")
            .setItems(shortcuts, (dialog, which) -> {
                connectionManager.sendCommand(commands[which]);
                Toast.makeText(this, "Sent: " + shortcuts[which].split(" - ")[0].trim(), Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showBrowserRemoteMenu() {
        String[] options = {
            "◀  Back", "▶  Forward", "↻  Refresh", "⊕  New Tab", "✕  Close Tab",
            "⬆  Scroll Up", "⬇  Scroll Down", "🔍  Focus Address Bar"
        };
        String[] commands = {
            "BROWSER:BACK", "BROWSER:FORWARD", "BROWSER:REFRESH",
            "BROWSER:NEW_TAB", "BROWSER:CLOSE_TAB",
            "BROWSER:SCROLL_UP", "BROWSER:SCROLL_DOWN", "BROWSER:FOCUS_BAR"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🌐 Browser Remote")
            .setItems(options, (dialog, which) -> connectionManager.sendCommand(commands[which]))
            .show();
    }
}
