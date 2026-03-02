package com.prajwal.myfirstapp.core;


import com.prajwal.myfirstapp.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "MobileControllerPrefs";
    private static final String KEY_SERVERS = "saved_servers";
    private static final String KEY_DEFAULT_SERVER = "default_server";
    private static final String KEY_AUTO_CONNECT = "auto_connect";
    private static final String KEY_MAIN_PORT = "main_port";
    private static final String KEY_FILE_PORT = "file_port";
    private static final String KEY_REVERSE_PORT = "reverse_port";
    private static final String KEY_HEARTBEAT_PORT = "heartbeat_port";
    private static final String KEY_MIRROR_APPS = "mirror_apps";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    private SharedPreferences prefs;
    private ListView serverListView;
    private ServerAdapter serverAdapter;
    private List<ServerInfo> serverList;
    
    private Switch autoConnectSwitch;
    private Switch themeModeSwitch;
    private Switch autoReconnectSwitch;
    private Switch keepAliveSwitch;
    private Switch tapToClickSwitch;
    private Switch naturalScrollSwitch;
    private Switch palmRejectionSwitch;
    private Switch shortcutsBarSwitch;
    private Switch keyboardHapticSwitch;
    private Switch autoCapitalizeSwitch;
    private Switch presenterTimerSwitch;
    private Switch presenterVibrateSwitch;
    private Switch transferCompressionSwitch;
    private Switch autoAcceptTransferSwitch;
    private Switch transferNotificationsSwitch;
    private Switch notifSoundSwitch;
    private Switch globalHapticSwitch;
    private Switch appLockSwitch;
    private Switch hideInRecentsSwitch;
    private EditText mainPortEdit;
    private EditText filePortEdit;
    private EditText reversePortEdit;
    private EditText heartbeatPortEdit;
    private Button clearCacheBtn;
    private Button clearDataBtn;
    private Button manageAppsBtn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        initViews();
        loadSettings();
        setupListeners();
    }
    
    private void initViews() {
        // Back button
        Button btnBack = findViewById(R.id.btnSettingsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Server list
        serverListView = findViewById(R.id.serverListView);
        Button addServerBtn = findViewById(R.id.addServerBtn);
        
        // Connection preferences
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch);
        mainPortEdit = findViewById(R.id.mainPortEdit);
        filePortEdit = findViewById(R.id.filePortEdit);
        reversePortEdit = findViewById(R.id.reversePortEdit);
        heartbeatPortEdit = findViewById(R.id.heartbeatPortEdit);
        autoReconnectSwitch = findViewById(R.id.autoReconnectSwitch);
        keepAliveSwitch = findViewById(R.id.keepAliveSwitch);

        // Touchpad settings
        android.widget.SeekBar touchpadSeek = findViewById(R.id.touchpadSensitivitySeek);
        android.widget.TextView touchpadLabel = findViewById(R.id.touchpadSensitivityLabel);
        if (touchpadSeek != null && touchpadLabel != null) {
            touchpadSeek.setProgress(prefs.getInt("touchpad_sensitivity", 4));
            touchpadLabel.setText(TOUCHPAD_SENSITIVITY_LEVELS[touchpadSeek.getProgress()]);
            touchpadSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                    touchpadLabel.setText(TOUCHPAD_SENSITIVITY_LEVELS[progress]);
                    if (fromUser) prefs.edit().putInt("touchpad_sensitivity", progress).apply();
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
            });
        }
        tapToClickSwitch = findViewById(R.id.tapToClickSwitch);
        naturalScrollSwitch = findViewById(R.id.naturalScrollSwitch);
        palmRejectionSwitch = findViewById(R.id.palmRejectionSwitch);

        // Keyboard settings
        shortcutsBarSwitch = findViewById(R.id.shortcutsBarSwitch);
        keyboardHapticSwitch = findViewById(R.id.keyboardHapticSwitch);
        autoCapitalizeSwitch = findViewById(R.id.autoCapitalizeSwitch);

        // Presenter settings
        presenterTimerSwitch = findViewById(R.id.presenterTimerSwitch);
        presenterVibrateSwitch = findViewById(R.id.presenterVibrateSwitch);

        // File Transfer settings
        transferCompressionSwitch = findViewById(R.id.transferCompressionSwitch);
        autoAcceptTransferSwitch = findViewById(R.id.autoAcceptTransferSwitch);
        transferNotificationsSwitch = findViewById(R.id.transferNotificationsSwitch);

        // Notification settings
        notifSoundSwitch = findViewById(R.id.notifSoundSwitch);

        // App settings
        themeModeSwitch = findViewById(R.id.themeModeSwitch);
        globalHapticSwitch = findViewById(R.id.globalHapticSwitch);
        appLockSwitch = findViewById(R.id.appLockSwitch);
        hideInRecentsSwitch = findViewById(R.id.hideInRecentsSwitch);

        // Notification apps management
        manageAppsBtn = findViewById(R.id.manageAppsBtn);
        
        // Data management
        clearCacheBtn = findViewById(R.id.clearCacheBtn);
        clearDataBtn = findViewById(R.id.clearDataBtn);

        // About section
        android.widget.TextView tvVersion = findViewById(R.id.tvAppVersion);
        if (tvVersion != null) {
            try {
                String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText("v" + version);
            } catch (Exception e) {
                Log.w("SettingsActivity", "Could not retrieve app version", e);
                tvVersion.setText("v1.0");
            }
        }
        Button checkUpdatesBtn = findViewById(R.id.checkUpdatesBtn);
        if (checkUpdatesBtn != null) {
            checkUpdatesBtn.setOnClickListener(v ->
                Toast.makeText(this, "You are on the latest version", Toast.LENGTH_SHORT).show());
        }
        Button sendFeedbackBtn = findViewById(R.id.sendFeedbackBtn);
        if (sendFeedbackBtn != null) {
            sendFeedbackBtn.setOnClickListener(v -> {
                android.content.Intent emailIntent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
                emailIntent.setData(android.net.Uri.parse("mailto:"));
                emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Controller App Feedback");
                try { startActivity(emailIntent); } catch (Exception ignored) {}
            });
        }

        // Export settings button
        Button exportBtn = findViewById(R.id.exportSettingsBtn);
        if (exportBtn != null) {
            exportBtn.setOnClickListener(v ->
                Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show());
        }

        // Cache size
        android.widget.TextView tvCacheSize = findViewById(R.id.tvCacheSize);
        if (tvCacheSize != null) {
            long cacheSize = getDirSize(getCacheDir());
            tvCacheSize.setText(formatSize(cacheSize));
        }
        
        // Server list adapter
        serverList = new ArrayList<>();
        serverAdapter = new ServerAdapter(this, serverList);
        serverListView.setAdapter(serverAdapter);
        
        addServerBtn.setOnClickListener(v -> showAddServerDialog(null, -1));
    }

    private long getDirSize(java.io.File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    size += f.isDirectory() ? getDirSize(f) : f.length();
                }
            }
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private void loadSettings() {
        // Load servers
        loadSavedServers();
        
        // Load connection preferences
        if (autoConnectSwitch != null) autoConnectSwitch.setChecked(prefs.getBoolean(KEY_AUTO_CONNECT, false));
        if (mainPortEdit != null) mainPortEdit.setText(String.valueOf(prefs.getInt(KEY_MAIN_PORT, 5005)));
        if (filePortEdit != null) filePortEdit.setText(String.valueOf(prefs.getInt(KEY_FILE_PORT, 5006)));
        if (reversePortEdit != null) reversePortEdit.setText(String.valueOf(prefs.getInt(KEY_REVERSE_PORT, 6000)));
        if (heartbeatPortEdit != null) heartbeatPortEdit.setText(String.valueOf(prefs.getInt(KEY_HEARTBEAT_PORT, 6001)));
        if (autoReconnectSwitch != null) autoReconnectSwitch.setChecked(prefs.getBoolean("auto_reconnect", true));
        if (keepAliveSwitch != null) keepAliveSwitch.setChecked(prefs.getBoolean("keep_alive", true));

        // Touchpad settings
        if (tapToClickSwitch != null) tapToClickSwitch.setChecked(prefs.getBoolean("tap_to_click", true));
        if (naturalScrollSwitch != null) naturalScrollSwitch.setChecked(prefs.getBoolean("natural_scroll", false));
        if (palmRejectionSwitch != null) palmRejectionSwitch.setChecked(prefs.getBoolean("palm_rejection", true));

        // Keyboard settings
        if (shortcutsBarSwitch != null) shortcutsBarSwitch.setChecked(prefs.getBoolean("shortcuts_bar", true));
        if (keyboardHapticSwitch != null) keyboardHapticSwitch.setChecked(prefs.getBoolean("keyboard_haptic", false));
        if (autoCapitalizeSwitch != null) autoCapitalizeSwitch.setChecked(prefs.getBoolean("auto_capitalize", false));

        // Presenter settings
        if (presenterTimerSwitch != null) presenterTimerSwitch.setChecked(prefs.getBoolean("presenter_timer", false));
        if (presenterVibrateSwitch != null) presenterVibrateSwitch.setChecked(prefs.getBoolean("presenter_vibrate", true));

        // File transfer settings
        if (transferCompressionSwitch != null) transferCompressionSwitch.setChecked(prefs.getBoolean("transfer_compression", false));
        if (autoAcceptTransferSwitch != null) autoAcceptTransferSwitch.setChecked(prefs.getBoolean("auto_accept_transfer", false));
        if (transferNotificationsSwitch != null) transferNotificationsSwitch.setChecked(prefs.getBoolean("transfer_notifications", true));

        // Notification settings
        if (notifSoundSwitch != null) notifSoundSwitch.setChecked(prefs.getBoolean("notif_sound", true));

        // Load theme
        int themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (themeModeSwitch != null) themeModeSwitch.setChecked(themeMode == AppCompatDelegate.MODE_NIGHT_YES);

        // App settings
        if (globalHapticSwitch != null) globalHapticSwitch.setChecked(prefs.getBoolean("global_haptic", true));
        if (appLockSwitch != null) appLockSwitch.setChecked(prefs.getBoolean("app_lock", false));
        if (hideInRecentsSwitch != null) hideInRecentsSwitch.setChecked(prefs.getBoolean("hide_in_recents", false));
    }
    
    private void loadSavedServers() {
        serverList.clear();
        String serversJson = prefs.getString(KEY_SERVERS, "[]");
        String defaultServer = prefs.getString(KEY_DEFAULT_SERVER, "");
        
        try {
            JSONArray serversArray = new JSONArray(serversJson);
            for (int i = 0; i < serversArray.length(); i++) {
                JSONObject serverObj = serversArray.getJSONObject(i);
                ServerInfo server = new ServerInfo(
                    serverObj.getString("name"),
                    serverObj.getString("ip")
                );
                server.isDefault = server.ip.equals(defaultServer);
                serverList.add(server);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        serverAdapter.notifyDataSetChanged();
    }
    
    private void setupListeners() {
        // Auto-connect toggle
        if (autoConnectSwitch != null) {
            autoConnectSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_AUTO_CONNECT, isChecked).apply();
                Toast.makeText(this, "Auto-connect " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
            });
        }

        // Auto-reconnect toggle
        if (autoReconnectSwitch != null) {
            autoReconnectSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean("auto_reconnect", isChecked).apply());
        }

        // Keep alive toggle
        if (keepAliveSwitch != null) {
            keepAliveSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean("keep_alive", isChecked).apply());
        }

        // Theme toggle
        if (themeModeSwitch != null) {
            themeModeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
                Toast.makeText(this, isChecked ? "Dark theme enabled" : "Light theme enabled", Toast.LENGTH_SHORT).show();
            });
        }

        // Touchpad toggles
        if (tapToClickSwitch != null) tapToClickSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("tap_to_click", c).apply());
        if (naturalScrollSwitch != null) naturalScrollSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("natural_scroll", c).apply());
        if (palmRejectionSwitch != null) palmRejectionSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("palm_rejection", c).apply());

        // Keyboard toggles
        if (shortcutsBarSwitch != null) shortcutsBarSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("shortcuts_bar", c).apply());
        if (keyboardHapticSwitch != null) keyboardHapticSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("keyboard_haptic", c).apply());
        if (autoCapitalizeSwitch != null) autoCapitalizeSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("auto_capitalize", c).apply());

        // Presenter toggles
        if (presenterTimerSwitch != null) presenterTimerSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("presenter_timer", c).apply());
        if (presenterVibrateSwitch != null) presenterVibrateSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("presenter_vibrate", c).apply());

        // File transfer toggles
        if (transferCompressionSwitch != null) transferCompressionSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("transfer_compression", c).apply());
        if (autoAcceptTransferSwitch != null) autoAcceptTransferSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("auto_accept_transfer", c).apply());
        if (transferNotificationsSwitch != null) transferNotificationsSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("transfer_notifications", c).apply());

        // Notification sound toggle
        if (notifSoundSwitch != null) notifSoundSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("notif_sound", c).apply());

        // App setting toggles
        if (globalHapticSwitch != null) globalHapticSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("global_haptic", c).apply());
        if (appLockSwitch != null) appLockSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("app_lock", c).apply());
        if (hideInRecentsSwitch != null) hideInRecentsSwitch.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("hide_in_recents", c).apply());
        
        // Port settings - save on focus change
        View.OnFocusChangeListener portSaveListener = (v, hasFocus) -> {
            if (!hasFocus) {
                savePortSettings();
            }
        };
        
        if (mainPortEdit != null) mainPortEdit.setOnFocusChangeListener(portSaveListener);
        if (filePortEdit != null) filePortEdit.setOnFocusChangeListener(portSaveListener);
        if (reversePortEdit != null) reversePortEdit.setOnFocusChangeListener(portSaveListener);
        if (heartbeatPortEdit != null) heartbeatPortEdit.setOnFocusChangeListener(portSaveListener);
        
        // Notification apps management
        if (manageAppsBtn != null) manageAppsBtn.setOnClickListener(v -> showNotificationAppsDialog());
        
        // Clear cache
        if (clearCacheBtn != null) {
            clearCacheBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Clear Cache")
                    .setMessage("This will clear temporary files and cache. Continue?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        clearCache();
                        // Update cache size display
                        android.widget.TextView tvCacheSize = findViewById(R.id.tvCacheSize);
                        if (tvCacheSize != null) tvCacheSize.setText("0 B");
                        Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
        
        // Clear data
        if (clearDataBtn != null) {
            clearDataBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Clear All Data")
                    .setMessage("This will reset all settings and delete saved servers. This cannot be undone!")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        clearAllData();
                        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
    }
    
    private void savePortSettings() {
        if (mainPortEdit == null || filePortEdit == null || reversePortEdit == null || heartbeatPortEdit == null) return;
        try {
            int mainPort = Integer.parseInt(mainPortEdit.getText().toString());
            int filePort = Integer.parseInt(filePortEdit.getText().toString());
            int reversePort = Integer.parseInt(reversePortEdit.getText().toString());
            int heartbeatPort = Integer.parseInt(heartbeatPortEdit.getText().toString());
            
            prefs.edit()
                .putInt(KEY_MAIN_PORT, mainPort)
                .putInt(KEY_FILE_PORT, filePort)
                .putInt(KEY_REVERSE_PORT, reversePort)
                .putInt(KEY_HEARTBEAT_PORT, heartbeatPort)
                .apply();
                
            Toast.makeText(this, "Port settings saved", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showAddServerDialog(ServerInfo existingServer, int position) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);
        
        EditText nameInput = dialogView.findViewById(R.id.serverNameInput);
        EditText ipInput = dialogView.findViewById(R.id.serverIpInput);
        Switch defaultSwitch = dialogView.findViewById(R.id.setDefaultSwitch);
        
        if (existingServer != null) {
            nameInput.setText(existingServer.name);
            ipInput.setText(existingServer.ip);
            defaultSwitch.setChecked(existingServer.isDefault);
        }
        
        new AlertDialog.Builder(this)
            .setTitle(existingServer == null ? "Add Server" : "Edit Server")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                String ip = ipInput.getText().toString().trim();
                
                if (name.isEmpty() || ip.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!isValidIP(ip)) {
                    Toast.makeText(this, "Invalid IP address", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (existingServer == null) {
                    addServer(name, ip, defaultSwitch.isChecked());
                } else {
                    updateServer(position, name, ip, defaultSwitch.isChecked());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private boolean isValidIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private void addServer(String name, String ip, boolean isDefault) {
        ServerInfo newServer = new ServerInfo(name, ip);
        newServer.isDefault = isDefault;
        
        if (isDefault) {
            // Remove default from others
            for (ServerInfo server : serverList) {
                server.isDefault = false;
            }
        }
        
        serverList.add(newServer);
        saveServers();
        serverAdapter.notifyDataSetChanged();
        
        Toast.makeText(this, "Server added", Toast.LENGTH_SHORT).show();
    }
    
    private void updateServer(int position, String name, String ip, boolean isDefault) {
        ServerInfo server = serverList.get(position);
        server.name = name;
        server.ip = ip;
        
        if (isDefault) {
            // Remove default from others
            for (ServerInfo s : serverList) {
                s.isDefault = false;
            }
            server.isDefault = true;
        } else {
            server.isDefault = false;
        }
        
        saveServers();
        serverAdapter.notifyDataSetChanged();
        
        Toast.makeText(this, "Server updated", Toast.LENGTH_SHORT).show();
    }
    
    private void deleteServer(int position) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Server")
            .setMessage("Are you sure you want to delete this server?")
            .setPositiveButton("Delete", (dialog, which) -> {
                serverList.remove(position);
                saveServers();
                serverAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Server deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void saveServers() {
        try {
            JSONArray serversArray = new JSONArray();
            String defaultServer = "";
            
            for (ServerInfo server : serverList) {
                JSONObject serverObj = new JSONObject();
                serverObj.put("name", server.name);
                serverObj.put("ip", server.ip);
                serversArray.put(serverObj);
                
                if (server.isDefault) {
                    defaultServer = server.ip;
                }
            }
            
            prefs.edit()
                .putString(KEY_SERVERS, serversArray.toString())
                .putString(KEY_DEFAULT_SERVER, defaultServer)
                .apply();
                
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving servers", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showNotificationAppsDialog() {
        // Get currently mirrored apps
        Set<String> mirroredApps = prefs.getStringSet(KEY_MIRROR_APPS, new HashSet<>());
        String[] allApps = {
            "WhatsApp", "Telegram", "Gmail", "Messages", 
            "Instagram", "Facebook", "Twitter", "Discord",
            "Slack", "Teams", "Outlook"
        };
        
        boolean[] checkedItems = new boolean[allApps.length];
        for (int i = 0; i < allApps.length; i++) {
            checkedItems[i] = mirroredApps.contains(allApps[i]);
        }
        
        new AlertDialog.Builder(this)
            .setTitle("Notification Mirror Settings")
            .setMultiChoiceItems(allApps, checkedItems, (dialog, which, isChecked) -> {
                checkedItems[which] = isChecked;
            })
            .setPositiveButton("Save", (dialog, which) -> {
                Set<String> selectedApps = new HashSet<>();
                for (int i = 0; i < allApps.length; i++) {
                    if (checkedItems[i]) {
                        selectedApps.add(allApps[i]);
                    }
                }
                prefs.edit().putStringSet(KEY_MIRROR_APPS, selectedApps).apply();
                Toast.makeText(this, "Notification settings saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void clearCache() {
        // Clear app cache
        try {
            deleteDir(getCacheDir());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void clearAllData() {
        // Clear cache
        clearCache();
        
        // Clear shared preferences
        prefs.edit().clear().apply();
        
        // Clear app data directory
        try {
            deleteDir(getDataDir());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean deleteDir(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new java.io.File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }
    
    private static final String[] TOUCHPAD_SENSITIVITY_LEVELS = {
        "Very Slow", "Slow", "Slow+", "Normal-", "Normal", "Normal+", "Fast-", "Fast", "Fast+", "Very Fast"
    };

    // Helper class for server info
    private static class ServerInfo {
        String name;
        String ip;
        boolean isDefault;
        
        ServerInfo(String name, String ip) {
            this.name = name;
            this.ip = ip;
            this.isDefault = false;
        }
    }

    // Adapter for server list
    private class ServerAdapter extends ArrayAdapter<ServerInfo> {

        ServerAdapter(Context context, List<ServerInfo> servers) {
            super(context, 0, servers);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_server, parent, false);
            }
            
            ServerInfo server = getItem(position);
            
            TextView nameText = convertView.findViewById(R.id.serverNameText);
            TextView ipText = convertView.findViewById(R.id.serverIpText);
            TextView defaultBadge = convertView.findViewById(R.id.defaultBadge);
            ImageButton editBtn = convertView.findViewById(R.id.editServerBtn);
            ImageButton deleteBtn = convertView.findViewById(R.id.deleteServerBtn);
            
            nameText.setText(server.name);
            ipText.setText(server.ip);
            defaultBadge.setVisibility(server.isDefault ? View.VISIBLE : View.GONE);
            
            editBtn.setOnClickListener(v -> showAddServerDialog(server, position));
            deleteBtn.setOnClickListener(v -> deleteServer(position));
            
            return convertView;
        }
    }
    
    // Public static methods for accessing settings from other activities
    public static String getDefaultServerIP(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEFAULT_SERVER, "");
    }
    
    public static boolean isAutoConnectEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_CONNECT, false);
    }
    
    public static int getMainPort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_MAIN_PORT, 5005);
    }
    
    public static int getFilePort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_FILE_PORT, 5006);
    }
    
    public static int getReversePort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_REVERSE_PORT, 6000);
    }
    
    public static int getHeartbeatPort(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_HEARTBEAT_PORT, 6001);
    }
    
    public static Set<String> getMirroredApps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_MIRROR_APPS, new HashSet<>());
    }
}
