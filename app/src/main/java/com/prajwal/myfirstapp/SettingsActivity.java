package com.prajwal.myfirstapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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
        // Server list
        serverListView = findViewById(R.id.serverListView);
        Button addServerBtn = findViewById(R.id.addServerBtn);
        
        // Connection preferences
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch);
        mainPortEdit = findViewById(R.id.mainPortEdit);
        filePortEdit = findViewById(R.id.filePortEdit);
        reversePortEdit = findViewById(R.id.reversePortEdit);
        heartbeatPortEdit = findViewById(R.id.heartbeatPortEdit);
        
        // App settings
        themeModeSwitch = findViewById(R.id.themeModeSwitch);
        manageAppsBtn = findViewById(R.id.manageAppsBtn);
        
        // Data management
        clearCacheBtn = findViewById(R.id.clearCacheBtn);
        clearDataBtn = findViewById(R.id.clearDataBtn);
        
        // Server list adapter
        serverList = new ArrayList<>();
        serverAdapter = new ServerAdapter(this, serverList);
        serverListView.setAdapter(serverAdapter);
        
        addServerBtn.setOnClickListener(v -> showAddServerDialog(null, -1));
    }
    
    private void loadSettings() {
        // Load servers
        loadSavedServers();
        
        // Load connection preferences
        autoConnectSwitch.setChecked(prefs.getBoolean(KEY_AUTO_CONNECT, false));
        mainPortEdit.setText(String.valueOf(prefs.getInt(KEY_MAIN_PORT, 5005)));
        filePortEdit.setText(String.valueOf(prefs.getInt(KEY_FILE_PORT, 5006)));
        reversePortEdit.setText(String.valueOf(prefs.getInt(KEY_REVERSE_PORT, 6000)));
        heartbeatPortEdit.setText(String.valueOf(prefs.getInt(KEY_HEARTBEAT_PORT, 6001)));
        
        // Load theme
        int themeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        themeModeSwitch.setChecked(themeMode == AppCompatDelegate.MODE_NIGHT_YES);
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
        autoConnectSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_CONNECT, isChecked).apply();
            Toast.makeText(this, "Auto-connect " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
        
        // Theme toggle
        themeModeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
            Toast.makeText(this, isChecked ? "Dark theme enabled" : "Light theme enabled", Toast.LENGTH_SHORT).show();
        });
        
        // Port settings - save on focus change
        View.OnFocusChangeListener portSaveListener = (v, hasFocus) -> {
            if (!hasFocus) {
                savePortSettings();
            }
        };
        
        mainPortEdit.setOnFocusChangeListener(portSaveListener);
        filePortEdit.setOnFocusChangeListener(portSaveListener);
        reversePortEdit.setOnFocusChangeListener(portSaveListener);
        heartbeatPortEdit.setOnFocusChangeListener(portSaveListener);
        
        // Notification apps management
        manageAppsBtn.setOnClickListener(v -> showNotificationAppsDialog());
        
        // Clear cache
        clearCacheBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("This will clear temporary files and cache. Continue?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    clearCache();
                    Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        
        // Clear data
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
    
    private void savePortSettings() {
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
