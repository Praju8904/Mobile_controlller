package com.prajwal.myfirstapp.core;


import com.prajwal.myfirstapp.R;
import com.prajwal.myfirstapp.calendar.CalendarActivity;
import com.prajwal.myfirstapp.calendar.CalendarEvent;
import com.prajwal.myfirstapp.calendar.CalendarRepository;
import com.prajwal.myfirstapp.chat.ChatActivity;
import com.prajwal.myfirstapp.connectivity.BackgroundServices;
import com.prajwal.myfirstapp.connectivity.BluetoothFileHelper;
import com.prajwal.myfirstapp.connectivity.ConnectionManager;
import com.prajwal.myfirstapp.connectivity.DynamicBarService;
import com.prajwal.myfirstapp.connectivity.NotifMirrorService;
import com.prajwal.myfirstapp.connectivity.QRPairingManager;
import com.prajwal.myfirstapp.connectivity.ReverseCommandListener;
import com.prajwal.myfirstapp.connectivity.SensorHandler;
import com.prajwal.myfirstapp.connectivity.TouchpadHandler;
import com.prajwal.myfirstapp.hub.HubFileRepository;
import com.prajwal.myfirstapp.hub.LaptopHubActivity;
import com.prajwal.myfirstapp.hub.SmartFileHubActivity;
import com.prajwal.myfirstapp.notes.NoteRepository;
import com.prajwal.myfirstapp.notes.NotesActivity;
import com.prajwal.myfirstapp.passwords.PasswordManagerActivity;
import com.prajwal.myfirstapp.passwords.PasswordRepository;
import com.prajwal.myfirstapp.tasks.TaskManagerActivity;
import com.prajwal.myfirstapp.tasks.TaskRepository;
import com.prajwal.myfirstapp.vault.MediaVaultRepository;
import com.prajwal.myfirstapp.vault.VaultUnlockActivity;
import com.prajwal.myfirstapp.expenses.ExpenseTrackerActivity;
import com.prajwal.myfirstapp.expenses.ExpenseRepository;
import com.prajwal.myfirstapp.mathstudio.MathStudioHubActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.KeyEvent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
//import dynamic_bar.DynamicBarService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import android.net.Uri;
import android.content.Intent;
import android.widget.Toast;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;

import java.util.ArrayList;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MainActivity extends AppCompatActivity {

    private ConnectionManager connectionManager;
    private BackgroundServices backgroundServices;
    private TouchpadHandler touchpadHandler;

    private SwitchCompat modeSwitch;
    private ConstraintLayout presenterModeUI;
    private TextView tvStatus;
    private TextView tvBattery;
    private ImageView ivPreview;
    private SensorHandler sensorHandler;

    // Home Navigation (tab-based)
    private View homeScreen;
    private View touchpadScreen;
    private View devicesScreen;
    private View historyScreen;
    private Button btnBackHome;

    // Bottom Navigation
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigation;

    // Header icons
    private TextView btnHeaderSettings;
    private TextView btnHeaderConnect;

    // QR Pairing & Reverse Commands
    private QRPairingManager qrPairingManager;
    private ReverseCommandListener reverseCommandListener;

    // Heartbeat & Connection State Variables
    private long lastServerHeartbeat = 0;
    private final long TIMEOUT_THRESHOLD = 3000; // 3 seconds (faster detection)
    private boolean isServerCurrentlyRunning = false;
    private int missCount = 0;
    private boolean isPreviewOn = false;
    private boolean serverSelected = false; // Only start monitoring after server is selected

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // sensorHandler = new SensorHandler(this, connectionManager); //Air Mouse
        setContentView(R.layout.activity_main);

        // --- QR Pairing Init ---
        qrPairingManager = new QRPairingManager(this);
        if (qrPairingManager.isPaired()) {
            qrPairingManager.applyKeys();
        }

        // --- Core Components Init ---
        // Use paired IP if available, else default
        String initialIp = qrPairingManager.isPaired() ? qrPairingManager.getServerIp() : "10.190.76.54";
        connectionManager = new ConnectionManager(initialIp);
        backgroundServices = new BackgroundServices();

        // --- Reverse Command Listener (PC → Phone) ---
        reverseCommandListener = new ReverseCommandListener(this, initialIp);

        reverseCommandListener.setCallback(command -> {
    // Existing Logs
            Log.d("ReverseCmd", "Received: " + command);

            if (command.startsWith("CHAT_MSG:")) {
                String msg = command.substring(9);
                
                // 1. Notify ChatActivity if open
                Intent intent = new Intent("com.prajwal.myfirstapp.CHAT_EVENT");
                intent.putExtra("type", "text");
                intent.putExtra("content", msg);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                // 2. Ideally, if ChatActivity is NOT open, save to Repository here too
                // (Optional for advanced polish)
                
            } else if (command.startsWith("CHAT_FILE_INFO:")) {
                // Handle file notifications from PC
                String filename = command.substring(15);
                
                // Build the full local path where the file was saved on the phone
                File docFolder = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
                File receivedFile = new File(new File(docFolder, "Received_Files"), filename);
                String fullPath = receivedFile.getAbsolutePath();
                
                Intent intent = new Intent("com.prajwal.myfirstapp.CHAT_EVENT");
                intent.putExtra("type", "file");
                intent.putExtra("content", fullPath);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            } else if (command.startsWith("CLIPBOARD_PC:")) {
                // Automatic clipboard sync from PC - set phone clipboard silently
                String clipText = command.substring(13);
                if (!clipText.isEmpty()) {
                    runOnUiThread(() -> {
                        android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("PC Clipboard", clipText);
                        clipboard.setPrimaryClip(clip);
                    });
                }
            }
        });

        // Auto-start reverse listener if already paired
        if (qrPairingManager.isPaired()) {
            reverseCommandListener.start();
            serverSelected = true;
        }

        // --- View Initializations ---
        modeSwitch = findViewById(R.id.modeSwitch);
        View touchPad = findViewById(R.id.touchPad);
        EditText hiddenInput = findViewById(R.id.hiddenInput);
        presenterModeUI = findViewById(R.id.presenterModeUI);
        tvStatus = findViewById(R.id.tvStatus);
        tvBattery = findViewById(R.id.tvBattery);
        ivPreview = findViewById(R.id.ivPreview);

        // --- Home/Tab Navigation Init ---
        homeScreen = findViewById(R.id.homeScreen);
        touchpadScreen = findViewById(R.id.touchpadScreen);
        devicesScreen = findViewById(R.id.devicesScreen);
        historyScreen = findViewById(R.id.historyScreen);
        btnBackHome = findViewById(R.id.btnBackHome);
        btnBackHome.setOnClickListener(v -> navigateToHome());

        // --- Bottom Navigation ---
        bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showTab(homeScreen);
                return true;
            } else if (id == R.id.nav_devices) {
                showTab(devicesScreen);
                return true;
            } else if (id == R.id.nav_history) {
                showTab(historyScreen);
                return true;
            }
            return false;
        });

        // --- Header Icons ---
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        btnHeaderSettings.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });
        btnHeaderConnect = findViewById(R.id.btnHeaderConnect);
        btnHeaderConnect.setOnClickListener(v -> showConnectionBottomSheet());

        setupHomeCards();
        setupDevicesTab();

        // --- Touchpad Init ---
        touchpadHandler = new TouchpadHandler(this, touchPad, modeSwitch, connectionManager);
        touchPad.setOnTouchListener(touchpadHandler);

        // --- Main Control Buttons ---
        findViewById(R.id.btnLeftClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_CLICK"));
        findViewById(R.id.btnRightClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_RIGHT_CLICK"));

        // Connection management moved to header connect icon and Devices tab
        // QR scan accessible from Connection bottom sheet or Devices tab
        findViewById(R.id.btnOpenNotepad).setOnClickListener(v -> connectionManager.sendCommand("OPEN_NOTEPAD"));
        findViewById(R.id.btnVoice).setOnClickListener(v -> startVoiceRecognition(300));
        findViewById(R.id.btnWriteAI).setOnClickListener(v -> showWriteAIDialog());
        findViewById(R.id.btnEnterPresenter).setOnClickListener(v -> togglePresenterMode(true));

        // Task Manager
        findViewById(R.id.btnTasks).setOnClickListener(v -> {
            Intent taskIntent = new Intent(MainActivity.this, TaskManagerActivity.class);
            taskIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(taskIntent);
        });

        // Navigation & Utility Buttons
        setupUtilityButtons();

        // File Pickers
        findViewById(R.id.btnSendFile).setOnClickListener(v -> openMediaPicker("*/*", 200));
        findViewById(R.id.btnSendVideo).setOnClickListener(v -> openMediaPicker("video/*", 201));
        findViewById(R.id.btnSendAudio).setOnClickListener(v -> openMediaPicker("audio/*", 202));

        // Shared Intents (Open with...)
        handleSharedIntents(getIntent());

        // Voice Type
        findViewById(R.id.btnVoiceType).setOnClickListener(v -> startVoiceRecognition(400));

        // Clipboard Sync
        Button btnSyncClip = findViewById(R.id.btnSyncClipboard);
        btnSyncClip.setOnClickListener(v -> showClipboardDialog());
        btnSyncClip.setOnLongClickListener(v -> {
            startVoiceRecognition(500);
            return true;
        });

        // screen shot
        Button btnScreenshot = findViewById(R.id.btnScreenshot);

        // 1. Simple Click -> Save on Laptop
        btnScreenshot.setOnClickListener(v -> {
            connectionManager.sendCommand("SCREENSHOT_LOCAL");
            Toast.makeText(this, "Screenshot saved on PC", Toast.LENGTH_SHORT).show();
        });

        // 2. Long Press -> Save on Laptop AND Send to Mobile
        btnScreenshot.setOnLongClickListener(v -> {
            connectionManager.sendCommand("SCREENSHOT_SEND");
            Toast.makeText(this, "Capturing and transferring...", Toast.LENGTH_LONG).show();

            // Vibrate to give feedback that long press worked
            // Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            // if (vibrator != null) {
            // vibrator.vibrate(VibrationEffect.createOneShot(100,
            // VibrationEffect.DEFAULT_AMPLITUDE));
            // }

            return true; // Tells Android we handled the long press
        });

        // air mouse
        /*
         * View laserTrigger = findViewById(R.id.touchPad);
         * 
         * laserTrigger.setOnTouchListener((v, event) -> {
         * switch (event.getAction()) {
         * case MotionEvent.ACTION_DOWN:
         * // Start the laser when finger touches
         * sensorHandler.start();
         * v.setBackgroundColor(Color.parseColor("#33FF0000")); // Tint red for "Laser"
         * feel
         * break;
         * 
         * case MotionEvent.ACTION_UP:
         * // Stop the laser when finger lifts
         * sensorHandler.stop();
         * v.setBackgroundColor(Color.TRANSPARENT);
         * break;
         * }
         * return true;
         * });
         * 
         */

        // Scroll Strip
        View scrollStrip = findViewById(R.id.scrollStrip);
        scrollStrip.setOnTouchListener((v, event) -> {
            // Simplified scroll logic for strip
            // Note: Since TouchpadHandler handles main touch, this is separate.
            // Keeping original logic inline here or moving to a handler is fine.
            // For brevity, I'll keep the inline logic from original but cleaned up.
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    // Basic vertical check, can be refined if needed,
                    // but TouchpadHandler also has scroll.
                    break;
            }
            return true;
        });
        // (The original scrollStrip logic was a bit verbose,
        // if the user relies on it we can add a specific handler,
        // but the TouchPadHandler supports 2-finger scroll now).
        // Let's actually restore the simple strip logic if they use it specifically.
        setupScrollStrip(scrollStrip);

        // Keyboard Logic
        setupKeyboard(hiddenInput);

        // Preview Toggle
        findViewById(R.id.btnTogglePreview).setOnClickListener(v -> togglePreview((Button) v));

        // Don't start background listeners automatically
        // Only start after user selects a server
        // backgroundServices.startAutoDiscovery(() -> lastServerHeartbeat =
        // System.currentTimeMillis());
        backgroundServices.startStatusListener((battery, plugged) -> {
            runOnUiThread(() -> tvBattery.setText("PC Battery: " + battery + (plugged ? " ⚡" : "")));
        });
        backgroundServices.startPreviewListener((bitmap) -> {
            runOnUiThread(() -> ivPreview.setImageBitmap(bitmap));
        });

        // Don't start connection monitor automatically - wait for server selection
        // startConnectionMonitor();

        // Show initial status
        tvStatus.setText("Not Connected");
        tvStatus.setTextColor(Color.parseColor("#EF5350")); // Red for not connected
        // file reciver
        // connectionManager.startFileReceiver(this);

        // file reciver
        Button btnOpenFolder = findViewById(R.id.btnOpenFolder);

        btnOpenFolder.setOnClickListener(v -> {
            // 1. Get the exact path to your received files
            File docFolder = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            File receivedFolder = new File(docFolder, "Received_Files");

            if (receivedFolder.exists()) {
                // 2. Create an Intent to open the file provider
                // This opens the system's file manager at your app's specific location
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(receivedFolder.getPath());
                intent.setDataAndType(uri, "*/*"); // Allow viewing all file types

                try {
                    startActivity(Intent.createChooser(intent, "Open Received Files"));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(this, "Please install a File Manager", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No files received yet!", Toast.LENGTH_SHORT).show();
            }
        });
        Log.d("RE_SYSTEM", "Calling startFileReceiver now...");
        connectionManager.startFileReceiver(getApplicationContext());
    }

    private void setupUtilityButtons() {
        findViewById(R.id.btnUp).setOnClickListener(v -> connectionManager.sendCommand("KEY:UP"));
        findViewById(R.id.btnDown).setOnClickListener(v -> connectionManager.sendCommand("KEY:DOWN"));
        findViewById(R.id.btnLeft).setOnClickListener(v -> connectionManager.sendCommand("KEY:LEFT"));
        findViewById(R.id.btnRight).setOnClickListener(v -> connectionManager.sendCommand("KEY:RIGHT"));
        findViewById(R.id.btnEsc).setOnClickListener(v -> connectionManager.sendCommand("KEY:ESC"));
        findViewById(R.id.btnVolUp).setOnClickListener(v -> connectionManager.sendCommand("VOL_UP"));
        findViewById(R.id.btnVolDown).setOnClickListener(v -> connectionManager.sendCommand("VOL_DOWN"));
        findViewById(R.id.btnBrightUp).setOnClickListener(v -> connectionManager.sendCommand("BRIGHT_UP"));
        findViewById(R.id.btnBrightDown).setOnClickListener(v -> connectionManager.sendCommand("BRIGHT_DOWN"));
        findViewById(R.id.btnBlackout).setOnClickListener(v -> connectionManager.sendCommand("SCREEN_BLACK"));
        findViewById(R.id.btnMute).setOnClickListener(v -> connectionManager.sendCommand("MUTE_TOGGLE"));
        findViewById(R.id.btnShowDesktop).setOnClickListener(v -> connectionManager.sendCommand("SHOW_DESKTOP"));
        findViewById(R.id.btnAppSwitcher).setOnClickListener(v -> connectionManager.sendCommand("APP_SWITCHER"));
        findViewById(R.id.btnScheduler).setOnClickListener(v -> showSchedulerDialog());
        findViewById(R.id.btnSyncClipboard).setOnClickListener(v -> showClipboardDialog());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> connectionManager.sendCommand("ZOOM_IN"));
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> connectionManager.sendCommand("ZOOM_OUT"));
        findViewById(R.id.btnResetZoom).setOnClickListener(v -> connectionManager.sendCommand("ZOOM_RESET"));
        findViewById(R.id.btnEnter).setOnClickListener(v -> connectionManager.sendCommand("KEY:ENTER"));
    }

    private void setupScrollStrip(View scrollStrip) {
        scrollStrip.setOnTouchListener(new View.OnTouchListener() {
            private float lastScrollY = 0;
            private final float SCROLL_THRESHOLD = 30;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float currentY = event.getY();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastScrollY = currentY;
                        v.setBackgroundColor(Color.parseColor("#40667EEA"));
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = currentY - lastScrollY;
                        if (Math.abs(deltaY) > SCROLL_THRESHOLD) {
                            String direction = (deltaY > 0) ? "-1" : "1";
                            connectionManager.sendCommand("MOUSE_SCROLL:" + direction);
                            lastScrollY = currentY;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setBackground(getDrawable(R.drawable.scroll_strip_bg));
                        break;
                }
                return true;
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // 1. Give the user feedback on the phone
            Toast.makeText(this, "Privacy Shield Activated!", Toast.LENGTH_SHORT).show();

            // 2. Send the specific command to the laptop
            connectionManager.sendCommand("PANIC_SHIELD");

            // 3. Return 'true' so the phone's actual volume doesn't change
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // --- Dynamic Status Handlers ---

    private void startConnectionMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000); // Check every 1 second (faster response)
                    long currentTime = System.currentTimeMillis();
                    boolean isHearbeatFresh = (currentTime - lastServerHeartbeat < TIMEOUT_THRESHOLD);

                    if (isHearbeatFresh) {
                        missCount = 0;
                        if (!isServerCurrentlyRunning)
                            updateConnectionUI(true);
                    } else {
                        missCount++;
                        if (missCount >= 2 && isServerCurrentlyRunning)
                            updateConnectionUI(false);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateConnectionUI(boolean isConnected) {
        runOnUiThread(() -> {
            isServerCurrentlyRunning = isConnected;
            if (isConnected) {
                tvStatus.setText("Connected");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#66BB6A"));
            } else {
                tvStatus.setText("Not Connected");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#EF5350"));
            }
            // Keep Dynamic Bar in sync
            updateDynamicBar();
            // Update laptop card connection dot
            updateLaptopConnectionDot();
        });
    }

    private void testConnection() {
        connectionManager.testConnection(new ConnectionManager.PingCallback() {
            @Override
            public void onSuccess(long responseTime) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connected (" + responseTime + "ms)", Toast.LENGTH_SHORT).show();
                    lastServerHeartbeat = System.currentTimeMillis();
                });
            }

            @Override
            public void onFailure() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Server not responding", Toast.LENGTH_SHORT).show();
                    lastServerHeartbeat = 0;
                });
            }
        });
    }

    private void togglePreview(Button btn) {
        if (!isPreviewOn) {
            connectionManager.sendCommand("PREVIEW_ON");
            isPreviewOn = true;
            backgroundServices.setPreviewEnabled(true);
            btn.setText("Live: ON");
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
            findViewById(R.id.previewContainer).setVisibility(View.VISIBLE);
        } else {
            connectionManager.sendCommand("PREVIEW_OFF");
            isPreviewOn = false;
            backgroundServices.setPreviewEnabled(false);
            btn.setText("Live: OFF");
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5722")));
            findViewById(R.id.previewContainer).setVisibility(View.GONE);
        }
    }

    private void handleSharedIntents(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null)
                sendSharedFile(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (fileUris != null) {
                for (Uri uri : fileUris)
                    sendSharedFile(uri);
            }
        }
    }

    private void sendSharedFile(Uri uri) {
        // Server should already be running - send file directly
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Brief delay to ensure connection is ready
                connectionManager.sendFileToLaptop(this, uri,
                        () -> runOnUiThread(() -> Toast.makeText(this, "Sending file...", Toast.LENGTH_SHORT).show()),
                        () -> runOnUiThread(() -> Toast
                                .makeText(this, "Sent: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show()),
                        () -> runOnUiThread(() -> Toast.makeText(this, "Transfer Failed", Toast.LENGTH_SHORT).show()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Ask the user whether to send via Wi-Fi or Bluetooth, then dispatch accordingly.
     */
    @SuppressWarnings("MissingPermission")
    private void showTransferMethodDialog(Uri uri) {
        // Check if Bluetooth is even available
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean btAvailable = btAdapter != null && btAdapter.isEnabled();

        if (!btAvailable) {
            // No Bluetooth — send over Wi-Fi directly
            sendViaWifi(uri);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Send via")
                .setItems(new String[]{"📶  Wi-Fi", "📡  Bluetooth"}, (dialog, which) -> {
                    if (which == 0) {
                        sendViaWifi(uri);
                    } else {
                        sendViaBluetooth(uri);
                    }
                })
                .show();
    }

    private void sendViaWifi(Uri uri) {
        connectionManager.sendFileToLaptop(this, uri,
                () -> runOnUiThread(() -> Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()),
                () -> runOnUiThread(() -> Toast.makeText(this, "Sent!", Toast.LENGTH_SHORT).show()),
                () -> runOnUiThread(() -> Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()));
    }

    @SuppressWarnings("MissingPermission")
    private void sendViaBluetooth(Uri uri) {
        // Check runtime permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                   != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 700);
            Toast.makeText(this, "Please grant Bluetooth permission, then try again", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothFileHelper.getPairedDevices(new BluetoothFileHelper.DeviceListCallback() {
            @Override
            public void onDevicesFound(java.util.List<BluetoothDevice> devices) {
                runOnUiThread(() -> {
                    if (devices.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Build device name list for chooser
                    String[] names = new String[devices.size()];
                    for (int i = 0; i < devices.size(); i++) {
                        BluetoothDevice d = devices.get(i);
                        names[i] = d.getName() != null ? d.getName() : d.getAddress();
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Select Bluetooth Device")
                            .setItems(names, (dialog, which) -> {
                                BluetoothDevice selected = devices.get(which);
                                connectionManager.sendFileOverBluetooth(MainActivity.this, selected, uri,
                                        () -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sending via Bluetooth...", Toast.LENGTH_SHORT).show()),
                                        () -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sent!", Toast.LENGTH_SHORT).show()),
                                        () -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "BT Transfer Failed", Toast.LENGTH_SHORT).show()));
                            })
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- UI Dialogs & Pickers ---

    private void showClipboardDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send to PC Clipboard");
        final EditText input = new EditText(this);
        input.setHint("Type or paste text here...");

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            input.setText(clipboard.getPrimaryClip().getItemAt(0).getText());
        }

        builder.setView(input);
        builder.setPositiveButton("Sync", (d, w) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                connectionManager.sendCommand("CLIPBOARD:" + text);
                Toast.makeText(this, "Sent to Laptop Clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showRemoteFilesDialog(String pipeSeparatedFiles) {
        if (pipeSeparatedFiles.equals("EMPTY")) {
            Toast.makeText(this, "No files in laptop folder", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] files = pipeSeparatedFiles.split("\\|");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Files on Laptop");
        builder.setItems(files, (dialog, which) -> {
            String selectedFile = files[which];
            connectionManager.sendCommand("GET_FILE:" + selectedFile);
            Toast.makeText(this, "Downloading " + selectedFile, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void showServerSelectionDialog() {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage("Discovering servers...");
        progress.setCancelable(false);
        progress.show();

        connectionManager.discoverServers(servers -> {
            runOnUiThread(() -> {
                progress.dismiss();

                if (servers.isEmpty()) {
                    // No servers found - show manual IP entry
                    new AlertDialog.Builder(this)
                            .setTitle("No Servers Found")
                            .setMessage("No servers detected. Enter IP manually?")
                            .setPositiveButton("Manual Entry", (d, w) -> showManualIPDialog())
                            .setNeutralButton("Scan QR Code", (d, w) -> startQRScan())
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    // Show list of discovered servers
                    String[] serverArray = servers.toArray(new String[0]);

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Select Server (" + servers.size() + " found)");
                    builder.setItems(serverArray, (dialog, which) -> {
                        String selectedIP = serverArray[which];
                        selectServer(selectedIP);
                    });
                    builder.setNeutralButton("Scan QR", (d, w) -> startQRScan());
                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                }
            });
        });
    }

    private void showManualIPDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setText(connectionManager.getLaptopIp());
        input.setHint("Enter IP address (e.g., 192.168.1.100)");
        builder.setTitle("Manual IP Entry").setView(input);
        builder.setPositiveButton("Connect", (d, w) -> {
            String ip = input.getText().toString().trim();
            if (!ip.isEmpty()) {
                selectServer(ip);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void selectServer(String ipAddress) {
        connectionManager.setLaptopIp(ipAddress);
        serverSelected = true;

        connectionManager.wakeUpWatchdog();

        // Start/restart reverse command listener for this server
        if (reverseCommandListener != null) {
            reverseCommandListener.stop();
        }
        reverseCommandListener = new ReverseCommandListener(this, ipAddress);
        reverseCommandListener.start();

        // Set server IP for notification mirroring
        NotifMirrorService.setServerIp(ipAddress);

        // Start monitoring now that server is selected
        if (!isServerCurrentlyRunning) {
            backgroundServices.startAutoDiscovery(() -> lastServerHeartbeat = System.currentTimeMillis());
            startConnectionMonitor();
        }

        Toast.makeText(this, "Server set to: " + ipAddress, Toast.LENGTH_SHORT).show();
        tvStatus.setText("Connecting...");
        tvStatus.setTextColor(Color.parseColor("#FFB74D")); // Amber
    }

    private void startQRScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan the QR code from the PC Control Panel");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    private void handleQRResult(String qrData) {
        boolean success = qrPairingManager.parsePairingData(qrData);
        if (success) {
            qrPairingManager.applyKeys();
            String ip = qrPairingManager.getServerIp();
            String hostname = qrPairingManager.getHostname();
            selectServer(ip);
            Toast.makeText(this, "Paired with " + hostname + " (" + ip + ")",
                    Toast.LENGTH_LONG).show();

            // Send confirmation to PC
            new Thread(() -> {
                try {
                    java.net.DatagramSocket sock = new java.net.DatagramSocket();
                    byte[] buf = ("PAIRED:" + Build.MODEL).getBytes();
                    java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
                    java.net.DatagramPacket pkt = new java.net.DatagramPacket(buf, buf.length, addr, 6001);
                    sock.send(pkt);
                    sock.close();
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } else {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
        }
    }

    // Keep old showIPDialog for backwards compatibility if needed elsewhere
    private void showIPDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setText(connectionManager.getLaptopIp());
        builder.setTitle("Set IP").setView(input);
        builder.setPositiveButton("Set", (d, w) -> {
            connectionManager.setLaptopIp(input.getText().toString().trim());
            lastServerHeartbeat = 0; // Force a re-check
        });
        builder.show();
    }

    private void showSchedulerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Schedule Shutdown (Minutes)");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);
        builder.setPositiveButton("Schedule", (dialog, which) -> {
            String mins = input.getText().toString();
            if (!mins.isEmpty()) {
                connectionManager.sendCommand("SCHEDULE:" + mins + ":SHUTDOWN_LAPTOP");
                Toast.makeText(this, "Shutdown scheduled in " + mins + " mins", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showWriteAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Write AI Command");
        final EditText input = new EditText(this);
        builder.setView(input);
        builder.setPositiveButton("Execute",
                (d, w) -> connectionManager.sendCommand("VOICE:" + input.getText().toString()));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void startVoiceRecognition(int requestCode) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak...");
        startActivityForResult(intent, requestCode);
    }

    private void togglePresenterMode(boolean show) {
        if (presenterModeUI == null) return;
        if (show) {
            presenterModeUI.setVisibility(View.VISIBLE);
            Animation animIn = AnimationUtils.loadAnimation(this, R.anim.anim_presenter_in);
            presenterModeUI.startAnimation(animIn);
        } else {
            Animation animOut = AnimationUtils.loadAnimation(this, R.anim.anim_presenter_out);
            animOut.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    presenterModeUI.setVisibility(View.GONE);
                }
            });
            presenterModeUI.startAnimation(animOut);
        }
    }

    // ═══ DYNAMIC BAR CONTROL ═══

    private void toggleDynamicBar() {
        // Check if already running → stop it
        if (DynamicBarService.isServiceRunning()) {
            Intent stopIntent = new Intent(this, DynamicBarService.class);
            stopIntent.setAction("STOP_DYNAMIC_BAR");
            startService(stopIntent);
            Toast.makeText(this, "Dynamic Bar disabled", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check overlay permission (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage("The Dynamic Bar needs 'Display over other apps' permission to float on top of your screen.\n\nTap 'Grant' to open settings.")
                    .setPositiveButton("Grant", (d, w) -> {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())
                        );
                        startActivityForResult(intent, 600);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Start the Dynamic Bar service
        startDynamicBarService();
    }

    private void startDynamicBarService() {
        Intent serviceIntent = new Intent(this, DynamicBarService.class);
        serviceIntent.putExtra("laptop_ip", connectionManager.getLaptopIp());
        serviceIntent.putExtra("is_connected", isServerCurrentlyRunning);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Dynamic Bar enabled ✨", Toast.LENGTH_SHORT).show();
    }

    /** Keep Dynamic Bar in sync when connection changes */
    private void updateDynamicBar() {
        DynamicBarService bar = DynamicBarService.getInstance();
        if (bar != null) {
            bar.updateConnectionStatus(isServerCurrentlyRunning, connectionManager.getLaptopIp());
        }
    }

    // ═══ HOME SCREEN NAVIGATION ═══

    private void setupHomeCards() {
        // Legacy cards (may be null after home screen redesign — safe null checks)
        View cardTouchpad = findViewById(R.id.cardTouchpad);
        if (cardTouchpad != null) cardTouchpad.setOnClickListener(v -> navigateToTouchpad());

        View cardKeyboard = findViewById(R.id.cardKeyboard);
        if (cardKeyboard != null) cardKeyboard.setOnClickListener(v -> {
            navigateToTouchpad();
            new android.os.Handler().postDelayed(() -> {
                EditText input = findViewById(R.id.hiddenInput);
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
        });

        View cardPresenter = findViewById(R.id.cardPresenter);
        if (cardPresenter != null) cardPresenter.setOnClickListener(v -> togglePresenterMode(true));

        View cardFiles = findViewById(R.id.cardFiles);
        if (cardFiles != null) {
            cardFiles.setOnClickListener(v -> openMediaPicker("*/*", 200));
            cardFiles.setOnLongClickListener(v -> {
                File docFolder = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
                File receivedFolder = new File(docFolder, "Received_Files");
                if (receivedFolder.exists()) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setDataAndType(Uri.parse(receivedFolder.getPath()), "*/*");
                    try { startActivity(Intent.createChooser(intent, "Open Received Files")); }
                    catch (android.content.ActivityNotFoundException ex) {
                        Toast.makeText(this, "Install a File Manager", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No files received yet!", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        View cardAI = findViewById(R.id.cardAI);
        if (cardAI != null) cardAI.setOnClickListener(v -> showAIMenu());

        View cardPCControl = findViewById(R.id.cardPCControl);
        if (cardPCControl != null) cardPCControl.setOnClickListener(v -> showPCControlMenu());

        View cardTasks = findViewById(R.id.cardTasks);
        if (cardTasks != null) cardTasks.setOnClickListener(v -> {
            Intent taskIntent = new Intent(MainActivity.this, TaskManagerActivity.class);
            taskIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(taskIntent);
        });

        View cardMedia = findViewById(R.id.cardMedia);
        if (cardMedia != null) cardMedia.setOnClickListener(v -> showMediaMenu());

        View cardNotes = findViewById(R.id.cardNotes);
        if (cardNotes != null) cardNotes.setOnClickListener(v -> {
            Intent notesIntent = new Intent(MainActivity.this, NotesActivity.class);
            notesIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(notesIntent);
        });

        View cardDynamicBar = findViewById(R.id.cardDynamicBar);
        if (cardDynamicBar != null) cardDynamicBar.setOnClickListener(v -> toggleDynamicBar());

        View cardCalendar = findViewById(R.id.cardCalendar);
        if (cardCalendar != null) cardCalendar.setOnClickListener(v -> {
            Intent calendarIntent = new Intent(MainActivity.this, CalendarActivity.class);
            calendarIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(calendarIntent);
        });

        View cardChat = findViewById(R.id.cardChat);
        if (cardChat != null) cardChat.setOnClickListener(v -> {
            Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
            chatIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(chatIntent);
        });

        View cardSettings = findViewById(R.id.cardSettingsHome);
        if (cardSettings != null) cardSettings.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        View cardPasswordManager = findViewById(R.id.cardPasswordHome);
        if (cardPasswordManager != null) cardPasswordManager.setOnClickListener(v -> {
            Intent vaultIntent = new Intent(MainActivity.this, PasswordManagerActivity.class);
            startActivity(vaultIntent);
        });

        View cardMediaVault = findViewById(R.id.cardPersonalVaultHome);
        if (cardMediaVault != null) cardMediaVault.setOnClickListener(v -> {
            Intent mediaVaultIntent = new Intent(MainActivity.this, VaultUnlockActivity.class);
            startActivity(mediaVaultIntent);
        });

        View cardSmartFileHub = findViewById(R.id.cardSmartFileHubHome);
        if (cardSmartFileHub != null) cardSmartFileHub.setOnClickListener(v -> {
            Intent hubIntent = new Intent(MainActivity.this, SmartFileHubActivity.class);
            startActivity(hubIntent);
        });

        // New home screen cards
        View cardLaptop = findViewById(R.id.cardLaptop);
        if (cardLaptop != null) {
            cardLaptop.setOnClickListener(v -> {
                Intent laptopHubIntent = new Intent(MainActivity.this, LaptopHubActivity.class);
                laptopHubIntent.putExtra("server_ip", connectionManager.getLaptopIp());
                laptopHubIntent.putExtra("is_connected", isServerCurrentlyRunning);
                startActivity(laptopHubIntent);
            });
        }

        View cardTasksHome = findViewById(R.id.cardTasksHome);
        if (cardTasksHome != null) {
            cardTasksHome.setOnClickListener(v -> {
                Intent taskIntent = new Intent(MainActivity.this, TaskManagerActivity.class);
                taskIntent.putExtra("server_ip", connectionManager.getLaptopIp());
                startActivity(taskIntent);
            });
        }

        View cardNotesHome = findViewById(R.id.cardNotesHome);
        if (cardNotesHome != null) {
            cardNotesHome.setOnClickListener(v -> {
                Intent notesIntent = new Intent(MainActivity.this, NotesActivity.class);
                notesIntent.putExtra("server_ip", connectionManager.getLaptopIp());
                startActivity(notesIntent);
            });
        }

        View cardCalendarHome = findViewById(R.id.cardCalendarHome);
        if (cardCalendarHome != null) {
            cardCalendarHome.setOnClickListener(v -> {
                Intent calendarIntent = new Intent(MainActivity.this, CalendarActivity.class);
                calendarIntent.putExtra("server_ip", connectionManager.getLaptopIp());
                startActivity(calendarIntent);
            });
        }

        View cardSettingsHome = findViewById(R.id.cardSettingsHome);
        if (cardSettingsHome != null) {
            cardSettingsHome.setOnClickListener(v -> {
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            });
        }

        View cardPasswordHome = findViewById(R.id.cardPasswordHome);
        if (cardPasswordHome != null) {
            cardPasswordHome.setOnClickListener(v -> {
                Intent vaultIntent = new Intent(MainActivity.this, PasswordManagerActivity.class);
                startActivity(vaultIntent);
            });
        }

        View cardPersonalVaultHome = findViewById(R.id.cardPersonalVaultHome);
        if (cardPersonalVaultHome != null) {
            cardPersonalVaultHome.setOnClickListener(v -> {
                Intent mediaVaultIntent = new Intent(MainActivity.this, VaultUnlockActivity.class);
                startActivity(mediaVaultIntent);
            });
        }

        View cardSmartFileHubHome = findViewById(R.id.cardSmartFileHubHome);
        if (cardSmartFileHubHome != null) {
            cardSmartFileHubHome.setOnClickListener(v -> {
                Intent hubIntent = new Intent(MainActivity.this, SmartFileHubActivity.class);
                startActivity(hubIntent);
            });
        }

        View cardExpenseHome = findViewById(R.id.cardExpenseHome);
        if (cardExpenseHome != null) {
            cardExpenseHome.setOnClickListener(v -> {
                Intent expenseIntent = new Intent(MainActivity.this, ExpenseTrackerActivity.class);
                startActivity(expenseIntent);
            });
        }

        // Update card summaries on resume
        updateExpenseCardSummary();
        updateVaultCardSummary();

        View cardMathStudioHome = findViewById(R.id.cardMathStudioHome);
        if (cardMathStudioHome != null) {
            cardMathStudioHome.setOnClickListener(v -> {
                Intent mathIntent = new Intent(MainActivity.this, MathStudioHubActivity.class);
                startActivity(mathIntent);
            });
        }
    }

    // ═══ TAB NAVIGATION ═══

    private void showTab(View targetScreen) {
        homeScreen.setVisibility(View.GONE);
        devicesScreen.setVisibility(View.GONE);
        historyScreen.setVisibility(View.GONE);
        // touchpadScreen is managed separately
        targetScreen.setVisibility(View.VISIBLE);
    }

    // ═══ CONNECTION BOTTOM SHEET ═══

    private void showConnectionBottomSheet() {
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        android.view.View sheetView = inflater.inflate(R.layout.bottom_sheet_connection, container, false);

        // Status text
        android.widget.TextView bsStatus = sheetView.findViewById(R.id.bsConnectionStatusText);
        if (isServerCurrentlyRunning) {
            bsStatus.setText("● Connected: " + connectionManager.getLaptopIp());
            bsStatus.setTextColor(Color.parseColor("#66BB6A"));
        } else {
            bsStatus.setText("● Not Connected");
            bsStatus.setTextColor(Color.parseColor("#EF5350"));
        }

        // Server list
        android.widget.ListView bsServerList = sheetView.findViewById(R.id.bsServerListView);
        android.content.SharedPreferences prefs = getSharedPreferences("MobileControllerPrefs", MODE_PRIVATE);
        String serversJson = prefs.getString("saved_servers", "[]");
        java.util.List<String> serverItems = new java.util.ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(serversJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                serverItems.add(obj.optString("name", "Server") + " - " + obj.optString("ip", ""));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to parse saved servers for bottom sheet", e);
        }
        if (serverItems.isEmpty()) serverItems.add("No saved servers");
        android.widget.ArrayAdapter<String> bsAdapter = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_list_item_1, serverItems);
        bsServerList.setAdapter(bsAdapter);

        // Buttons
        sheetView.findViewById(R.id.bsQrPairBtn).setOnClickListener(btn -> startQRScan());
        sheetView.findViewById(R.id.bsAddServerBtn).setOnClickListener(btn -> showManualIPDialog());
        sheetView.findViewById(R.id.bsAutoDiscoverBtn).setOnClickListener(btn -> showServerSelectionDialog());
        sheetView.findViewById(R.id.bsStartServerBtn).setOnClickListener(btn -> {
            if (isServerCurrentlyRunning) {
                connectionManager.toggleServerState(true, null);
                Toast.makeText(this, "Stopping server...", Toast.LENGTH_SHORT).show();
            } else {
                if (!serverSelected) {
                    Toast.makeText(this, "Select a server first", Toast.LENGTH_SHORT).show();
                    return;
                }
                connectionManager.toggleServerState(false, null);
                Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show();
            }
        });

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(sheetView);
        dialog.show();
    }

    // ═══ DEVICES TAB SETUP ═══

    private void setupDevicesTab() {
        View devTab = devicesScreen;
        if (devTab == null) return;

        // QR Pair card in Devices tab
        View qrCard = devTab.findViewById(R.id.devicesQrPairCard);
        if (qrCard != null) qrCard.setOnClickListener(v -> startQRScan());

        // Manual IP card
        View manualCard = devTab.findViewById(R.id.devicesManualIpCard);
        if (manualCard != null) manualCard.setOnClickListener(v -> showManualIPDialog());

        // Auto Discover card
        View discoverCard = devTab.findViewById(R.id.devicesAutoDiscoverCard);
        if (discoverCard != null) discoverCard.setOnClickListener(v -> showServerSelectionDialog());

        // Start Server button in Devices tab
        Button devStartBtn = devTab.findViewById(R.id.devicesStartServerBtn);
        if (devStartBtn != null) {
            devStartBtn.setOnClickListener(v -> {
                if (isServerCurrentlyRunning) {
                    connectionManager.toggleServerState(true, null);
                    Toast.makeText(this, "Stopping server...", Toast.LENGTH_SHORT).show();
                } else {
                    if (!serverSelected) {
                        Toast.makeText(this, "Select a server first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    connectionManager.toggleServerState(false, null);
                    Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Disconnect button
        Button btnDisconnect = devTab.findViewById(R.id.btnDisconnect);
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> {
                serverSelected = false;
                updateConnectionUI(false);
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
            });
        }

        // Populate saved servers list
        refreshDevicesServerList();
    }

    private void refreshDevicesServerList() {
        View devTab = devicesScreen;
        if (devTab == null) return;
        android.widget.ListView devServerList = devTab.findViewById(R.id.devicesServerListView);
        if (devServerList == null) return;

        android.content.SharedPreferences prefs = getSharedPreferences("MobileControllerPrefs", MODE_PRIVATE);
        String serversJson = prefs.getString("saved_servers", "[]");
        java.util.List<String> serverItems = new java.util.ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(serversJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                serverItems.add(obj.optString("name", "Server") + "\n" + obj.optString("ip", ""));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to parse saved servers for devices tab", e);
        }
        if (serverItems.isEmpty()) serverItems.add("No saved servers\nAdd one below");
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_list_item_1, serverItems);
        devServerList.setAdapter(adapter);
        devServerList.setOnItemClickListener((parent, view, pos, id) -> {
            // Try to connect to this server
            try {
                android.content.SharedPreferences p = getSharedPreferences("MobileControllerPrefs", MODE_PRIVATE);
                org.json.JSONArray arr = new org.json.JSONArray(p.getString("saved_servers", "[]"));
                if (pos < arr.length()) {
                    String ip = arr.getJSONObject(pos).optString("ip", "");
                    if (!ip.isEmpty()) selectServer(ip);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to connect to selected server", e);
            }
        });
    }

    // ═══ NEW FEATURE MENUS ═══

    private void showSystemMonitorMenu() {
        String[] options = {"📊  CPU Usage", "💾  RAM Usage", "🖥️  GPU Info", "💿  Disk Usage", "📶  Network Speed"};
        new AlertDialog.Builder(this)
            .setTitle("📊 System Monitor")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: connectionManager.sendCommand("SYS_CPU"); break;
                    case 1: connectionManager.sendCommand("SYS_RAM"); break;
                    case 2: connectionManager.sendCommand("SYS_GPU"); break;
                    case 3: connectionManager.sendCommand("SYS_DISK"); break;
                    case 4: connectionManager.sendCommand("SYS_NET"); break;
                }
            })
            .show();
    }

    private void showPowerControlMenu() {
        String[] options = {"⏻  Shutdown PC", "🔄  Restart PC", "💤  Sleep PC", "🔒  Lock PC", "🚪  Log Off"};
        new AlertDialog.Builder(this)
            .setTitle("⏻ Power Control")
            .setItems(options, (dialog, which) -> {
                String[] commands = {"SHUTDOWN_LAPTOP", "RESTART_PC", "SLEEP_PC", "LOCK_PC", "LOGOFF_PC"};
                String[] labels = {"Shutting down", "Restarting", "Sleeping", "Locking", "Logging off"};
                new AlertDialog.Builder(this)
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

    private void showCustomShortcutsMenu() {
        String[] shortcuts = {
            "Alt+Tab - Switch Apps",
            "Win+D - Show Desktop",
            "Ctrl+Alt+Del - Security Screen",
            "PrtScr - Screenshot",
            "Win+L - Lock Screen",
            "Ctrl+C - Copy",
            "Ctrl+V - Paste",
            "Ctrl+Z - Undo",
            "Ctrl+Shift+Esc - Task Manager"
        };
        String[] commands = {
            "KEY_COMBO:ALT+TAB", "KEY_COMBO:WIN+D", "KEY_COMBO:CTRL+ALT+DEL",
            "KEY:PRINTSCREEN", "KEY_COMBO:WIN+L",
            "KEY_COMBO:CTRL+C", "KEY_COMBO:CTRL+V", "KEY_COMBO:CTRL+Z",
            "KEY_COMBO:CTRL+SHIFT+ESC"
        };
        new AlertDialog.Builder(this)
            .setTitle("⌨ Custom Shortcuts")
            .setItems(shortcuts, (dialog, which) -> {
                connectionManager.sendCommand(commands[which]);
                Toast.makeText(this, "Sent: " + shortcuts[which].split(" - ")[0].trim(), Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showBrowserRemoteMenu() {
        String[] options = {
            "◀  Back", "▶  Forward", "↻  Refresh",
            "⊕  New Tab", "✕  Close Tab",
            "⬆  Scroll Up", "⬇  Scroll Down",
            "🔍  Focus Address Bar"
        };
        String[] commands = {
            "BROWSER:BACK", "BROWSER:FORWARD", "BROWSER:REFRESH",
            "BROWSER:NEW_TAB", "BROWSER:CLOSE_TAB",
            "BROWSER:SCROLL_UP", "BROWSER:SCROLL_DOWN",
            "BROWSER:FOCUS_BAR"
        };
        new AlertDialog.Builder(this)
            .setTitle("🌐 Browser Remote")
            .setItems(options, (dialog, which) -> {
                connectionManager.sendCommand(commands[which]);
            })
            .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String openScreen = intent.getStringExtra("open_screen");
        if (openScreen != null) {
            handleOpenScreen(openScreen);
        }
    }

    private void handleOpenScreen(String screen) {
        switch (screen) {
            case "touchpad":
                navigateToTouchpad();
                break;
            case "keyboard":
                navigateToTouchpad();
                new android.os.Handler().postDelayed(() -> {
                    EditText input = findViewById(R.id.hiddenInput);
                    if (input != null) {
                        input.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 300);
                break;
            case "presenter":
                togglePresenterMode(true);
                break;
            case "file_transfer":
                showFilesMenu();
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateExpenseCardSummary();
        updateVaultCardSummary();
        refreshDevicesServerList();
        updateHomeCardSubtitles();
        updateHistoryTab();
        updateLaptopConnectionDot();
    }

    private void updateExpenseCardSummary() {
        try {
            ExpenseRepository expenseRepo = new ExpenseRepository(this);
            TextView expenseSummary = findViewById(R.id.tvExpenseCardSubtitle);
            if (expenseSummary != null) {
                double monthTotal = expenseRepo.getMonthSpend();
                if (monthTotal > 0) {
                    expenseSummary.setText(String.format("This month: ₹%.0f", monthTotal));
                } else {
                    expenseSummary.setText("Track your spending");
                }
                expenseSummary.setTextColor(0xFFC4B5FD);
            }
        } catch (Exception ignored) {}
    }

    private void updateVaultCardSummary() {
        try {
            PasswordRepository vaultRepo = new PasswordRepository(this);
            TextView summary = findViewById(R.id.tvPasswordCardSubtitle);
            if (summary != null) {
                if (vaultRepo.isMasterPasswordSet()) {
                    summary.setText("🔒 Vault locked • Tap to open");
                    summary.setTextColor(0xFF8ECAE6);
                } else {
                    summary.setText("Set up your secure vault");
                    summary.setTextColor(0xFF8ECAE6);
                }
            }
        } catch (Exception ignored) {}

        try {
            MediaVaultRepository mediaVault = MediaVaultRepository.getInstance(this);
            TextView mediaVaultSummary = findViewById(R.id.tvPersonalVaultSubtitle);
            if (mediaVaultSummary != null) {
                if (mediaVault.isPinSetup()) {
                    int totalFiles = mediaVault.getAllFiles().size();
                    if (totalFiles > 0) {
                        mediaVaultSummary.setText("🔒 " + totalFiles + " encrypted file" + (totalFiles == 1 ? "" : "s"));
                    } else {
                        mediaVaultSummary.setText("🔒 Vault ready • Tap to open");
                    }
                } else {
                    mediaVaultSummary.setText("Private encrypted photo & file storage");
                }
                mediaVaultSummary.setTextColor(0xFF86EFAC);
            }
        } catch (Exception ignored) {}
    }

    private void navigateToTouchpad() {
        View connectionBar = findViewById(R.id.connectionBar);
        Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.anim_slide_out_left);
        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.anim_slide_in_right);
        // Hide all tab screens
        homeScreen.setVisibility(View.GONE);
        devicesScreen.setVisibility(View.GONE);
        historyScreen.setVisibility(View.GONE);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                touchpadScreen.setVisibility(View.VISIBLE);
                touchpadScreen.startAnimation(slideIn);
            }
        });
        homeScreen.startAnimation(slideOut);
        if (connectionBar != null) connectionBar.setVisibility(View.VISIBLE);
        btnBackHome.setVisibility(View.VISIBLE);
        btnBackHome.setAlpha(0f);
        btnBackHome.animate().alpha(1f).setDuration(300).start();
        bottomNavigation.setVisibility(View.GONE);
    }

    private void navigateToHome() {
        View connectionBar = findViewById(R.id.connectionBar);
        Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.anim_slide_out_right);
        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.anim_slide_in_left);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                touchpadScreen.setVisibility(View.GONE);
                homeScreen.setVisibility(View.VISIBLE);
                homeScreen.startAnimation(slideIn);
            }
        });
        touchpadScreen.startAnimation(slideOut);
        if (connectionBar != null) connectionBar.setVisibility(View.GONE);
        btnBackHome.animate().alpha(0f).setDuration(200).withEndAction(() ->
            btnBackHome.setVisibility(View.GONE)
        ).start();
        bottomNavigation.setVisibility(View.VISIBLE);
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    private void showFilesMenu() {
        String[] options = {"📄  Send File", "🎬  Send Video", "🎵  Send Audio", "📂  Browse Received Files"};
        new AlertDialog.Builder(this)
            .setTitle("📁 File Transfer")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: openMediaPicker("*/*", 200); break;
                    case 1: openMediaPicker("video/*", 201); break;
                    case 2: openMediaPicker("audio/*", 202); break;
                    case 3:
                        File docFolder = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
                        File receivedFolder = new File(docFolder, "Received_Files");
                        if (receivedFolder.exists()) {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setDataAndType(Uri.parse(receivedFolder.getPath()), "*/*");
                            try { startActivity(Intent.createChooser(intent, "Open Received Files")); }
                            catch (android.content.ActivityNotFoundException ex) {
                                Toast.makeText(this, "Install a File Manager", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "No files received yet!", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            })
            .show();
    }

    private void showAIMenu() {
        String[] options = {"🎙️  Voice Command", "✍️  Write AI Prompt", "🗣️  Voice Dictation"};
        new AlertDialog.Builder(this)
            .setTitle("🤖 AI Assistant")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: startVoiceRecognition(300); break;
                    case 1: showWriteAIDialog(); break;
                    case 2: startVoiceRecognition(400); break;
                }
            })
            .show();
    }

    private void showPCControlMenu() {
        String[] options = {
            "🔊  Volume Up", "🔉  Volume Down", "🔇  Mute Toggle",
            "🔆  Brightness Up", "🔅  Brightness Down",
            "🖥️  Show Desktop", "🔄  App Switcher",
            "📸  Screenshot (Save)", "📸  Screenshot (Send)",
            "📋  Clipboard Sync", "⏱️  Schedule Shutdown",
            "🔍  Zoom In", "🔎  Zoom Out", "💯  Reset Zoom",
            "🖥️  Screen Black / Wake", "⎋  Escape Key"
        };
        new AlertDialog.Builder(this)
            .setTitle("🎮 PC Control")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: connectionManager.sendCommand("VOL_UP"); break;
                    case 1: connectionManager.sendCommand("VOL_DOWN"); break;
                    case 2: connectionManager.sendCommand("MUTE_TOGGLE"); break;
                    case 3: connectionManager.sendCommand("BRIGHT_UP"); break;
                    case 4: connectionManager.sendCommand("BRIGHT_DOWN"); break;
                    case 5: connectionManager.sendCommand("SHOW_DESKTOP"); break;
                    case 6: connectionManager.sendCommand("APP_SWITCHER"); break;
                    case 7:
                        connectionManager.sendCommand("SCREENSHOT_LOCAL");
                        Toast.makeText(this, "Screenshot saved on PC", Toast.LENGTH_SHORT).show();
                        break;
                    case 8:
                        connectionManager.sendCommand("SCREENSHOT_SEND");
                        Toast.makeText(this, "Capturing & transferring...", Toast.LENGTH_LONG).show();
                        break;
                    case 9: showClipboardDialog(); break;
                    case 10: showSchedulerDialog(); break;
                    case 11: connectionManager.sendCommand("ZOOM_IN"); break;
                    case 12: connectionManager.sendCommand("ZOOM_OUT"); break;
                    case 13: connectionManager.sendCommand("ZOOM_RESET"); break;
                    case 14: connectionManager.sendCommand("SCREEN_BLACK"); break;
                    case 15: connectionManager.sendCommand("KEY:ESC"); break;
                }
            })
            .show();
    }

    private void showMediaMenu() {
        String[] options = {"📝  Open Notepad", "📷  Webcam Stream"};
        new AlertDialog.Builder(this)
            .setTitle("🎬 Media & Apps")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: connectionManager.sendCommand("OPEN_NOTEPAD"); break;
                    case 1: connectionManager.sendCommand("CAMERA_STREAM"); break;
                }
            })
            .show();
    }

    private void setupKeyboard(EditText hiddenInput) {
        hiddenInput.setText(" ");
        findViewById(R.id.btnKeyboard).setOnClickListener(v -> {
            hiddenInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT);
        });

        hiddenInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0)
                    connectionManager.sendCommand("KEY:BACKSPACE");
                else if (s.length() > 1) {
                    char c = s.charAt(s.length() - 1);
                    String newChar = String.valueOf(c);
                    if (newChar.equals(" "))
                        connectionManager.sendCommand("KEY:SPACE");
                    else if (newChar.equals("\n"))
                        connectionManager.sendCommand("KEY:ENTER");
                    else
                        connectionManager.sendCommand("KEY:" + newChar);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 1 || !s.toString().equals(" ")) {
                    hiddenInput.removeTextChangedListener(this);
                    s.clear();
                    s.append(" ");
                    hiddenInput.addTextChangedListener(this);
                }
            }
        });
    }

    private void openMediaPicker(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Handle QR scan result (ZXing uses its own result codes)
        IntentResult qrResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (qrResult != null) {
            if (qrResult.getContents() != null) {
                handleQRResult(qrResult.getContents());
            } else {
                Toast.makeText(this, "QR scan cancelled", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);

        // Handle overlay permission result
        if (requestCode == 600) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startDynamicBarService();
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (resultCode == RESULT_OK && data != null) {

            // File Handling
            if (requestCode >= 200 && requestCode <= 202) {
                Uri uri = data.getData();
                if (uri != null) {
                    showTransferMethodDialog(uri);
                }
                return;
            }

            // Voice Handling
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                if (requestCode == 400) {
                    connectionManager.sendCommand("DICTATE:" + text);
                    Toast.makeText(this, "Typing: " + text, Toast.LENGTH_SHORT).show();
                } else if (requestCode == 300) {
                    // Confirm Command
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Command")
                            .setMessage(text)
                            .setPositiveButton("Execute", (d, w) -> connectionManager.sendCommand("VOICE:" + text))
                            .setNegativeButton("Cancel", null)
                            .show();
                } else if (requestCode == 500) {
                    connectionManager.sendCommand("CLIPBOARD:" + text);
                    Toast.makeText(this, "Synced Clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (presenterModeUI != null && presenterModeUI.getVisibility() == View.VISIBLE) {
            togglePresenterMode(false);
        } else if (touchpadScreen != null && touchpadScreen.getVisibility() == View.VISIBLE) {
            navigateToHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reverseCommandListener != null) {
            reverseCommandListener.stop();
        }
    }

    // ═══ HOME CARD SUBTITLES ═══

    private void updateHomeCardSubtitles() {
        // Tasks subtitle
        try {
            TaskRepository taskRepo = new TaskRepository(this);
            int dueTodayCount = taskRepo.getTotalTodayCount();
            TextView tvTasks = findViewById(R.id.tvTasksCardSubtitle);
            if (tvTasks != null) {
                if (dueTodayCount > 0) {
                    tvTasks.setText(dueTodayCount + " task" + (dueTodayCount == 1 ? "" : "s") + " due today");
                } else {
                    tvTasks.setText("All caught up!");
                }
            }
        } catch (Exception ignored) {}

        // Notes subtitle
        try {
            NoteRepository noteRepo = new NoteRepository(this);
            int noteCount = noteRepo.getAllNotes().size();
            TextView tvNotes = findViewById(R.id.tvNotesCardSubtitle);
            if (tvNotes != null) {
                if (noteCount > 0) {
                    tvNotes.setText(noteCount + " note" + (noteCount == 1 ? "" : "s"));
                } else {
                    tvNotes.setText("No notes yet");
                }
            }
        } catch (Exception ignored) {}

        // Calendar subtitle
        try {
            CalendarRepository calRepo = new CalendarRepository(this);
            java.util.List<CalendarEvent> todayEvents = calRepo.getWidgetTodayEvents();
            TextView tvCal = findViewById(R.id.tvCalendarCardSubtitle);
            if (tvCal != null) {
                if (!todayEvents.isEmpty()) {
                    tvCal.setText(todayEvents.get(0).title);
                } else {
                    tvCal.setText("No events today");
                }
            }
        } catch (Exception ignored) {}

        // Password Vault subtitle
        try {
            PasswordRepository vaultRepo = new PasswordRepository(this);
            TextView tvPwd = findViewById(R.id.tvPasswordCardSubtitle);
            if (tvPwd != null) {
                if (vaultRepo.isMasterPasswordSet()) {
                    int count = vaultRepo.getActiveEntries().size();
                    if (count > 0) {
                        tvPwd.setText(count + " password" + (count == 1 ? "" : "s") + " saved");
                    } else {
                        tvPwd.setText("Vault locked — tap to open");
                    }
                } else {
                    tvPwd.setText("Set up your vault");
                }
            }
        } catch (Exception ignored) {}

        // Personal Vault subtitle
        try {
            MediaVaultRepository mediaVault = MediaVaultRepository.getInstance(this);
            TextView tvVault = findViewById(R.id.tvPersonalVaultSubtitle);
            if (tvVault != null) {
                if (mediaVault.isPinSetup()) {
                    int totalFiles = mediaVault.getAllFiles().size();
                    if (totalFiles > 0) {
                        tvVault.setText(totalFiles + " encrypted file" + (totalFiles == 1 ? "" : "s"));
                    } else {
                        tvVault.setText("Vault secured");
                    }
                } else {
                    tvVault.setText("Private encrypted storage");
                }
            }
        } catch (Exception ignored) {}

        // Smart File Hub subtitle
        try {
            HubFileRepository hubRepo = HubFileRepository.getInstance(this);
            int fileCount = hubRepo.getAllFiles().size();
            TextView tvHub = findViewById(R.id.tvFileHubSubtitle);
            if (tvHub != null) {
                if (fileCount > 0) {
                    tvHub.setText(fileCount + " file" + (fileCount == 1 ? "" : "s") + " organized");
                } else {
                    tvHub.setText("Organized");
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateLaptopConnectionDot() {
        View dot = findViewById(R.id.laptopConnectionDot);
        if (dot != null) {
            dot.setBackgroundResource(isServerCurrentlyRunning ?
                R.drawable.connection_dot_green : R.drawable.connection_dot_red);
        }
    }

    // ═══ HISTORY TAB ═══

    private void updateHistoryTab() {
        try {
            ActivityLogRepository logRepo = new ActivityLogRepository(this);

            // Update stats
            int tasksCount = logRepo.countThisWeek(ActivityLogEntry.FEATURE_TASKS);
            int notesCount = logRepo.countThisWeek(ActivityLogEntry.FEATURE_NOTES);
            int filesCount = logRepo.countThisWeek(ActivityLogEntry.FEATURE_HUB);

            TextView tvTasks = findViewById(R.id.tvHistoryTasksCount);
            if (tvTasks != null) tvTasks.setText(String.valueOf(tasksCount));
            TextView tvNotes = findViewById(R.id.tvHistoryNotesCount);
            if (tvNotes != null) tvNotes.setText(String.valueOf(notesCount));
            TextView tvFiles = findViewById(R.id.tvHistoryFilesCount);
            if (tvFiles != null) tvFiles.setText(String.valueOf(filesCount));

            // Populate activity feed
            android.widget.LinearLayout feed = findViewById(R.id.historyActivityFeed);
            View emptyState = findViewById(R.id.historyEmptyState);
            if (feed == null) return;

            java.util.List<ActivityLogEntry> entries = logRepo.getAllEntries();

            if (entries.isEmpty()) {
                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                return;
            }
            if (emptyState != null) emptyState.setVisibility(View.GONE);

            // Remove dynamically added items (but keep the empty state child)
            int childCount = feed.getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                View child = feed.getChildAt(i);
                if (child != emptyState) feed.removeViewAt(i);
            }

            long now = System.currentTimeMillis();
            long todayStart = getTodayStart();
            long yesterdayStart = todayStart - (24 * 60 * 60 * 1000L);
            long weekStart = now - (7L * 24 * 60 * 60 * 1000);

            String lastSection = "";
            for (ActivityLogEntry entry : entries) {
                String section;
                if (entry.timestamp >= todayStart) section = "Today";
                else if (entry.timestamp >= yesterdayStart) section = "Yesterday";
                else if (entry.timestamp >= weekStart) section = "This Week";
                else section = "Older";

                if (!section.equals(lastSection)) {
                    addHistorySectionHeader(feed, section);
                    lastSection = section;
                }
                addHistoryItem(feed, entry);
            }
        } catch (Exception ignored) {}

        // History More menu
        View btnMore = findViewById(R.id.btnHistoryMore);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("History Options")
                    .setItems(new String[]{"Clear Today's History", "Clear All History"}, (d, which) -> {
                        ActivityLogRepository repo = new ActivityLogRepository(this);
                        if (which == 0) {
                            repo.clearToday();
                            Toast.makeText(this, "Today's history cleared", Toast.LENGTH_SHORT).show();
                        } else {
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Clear All History")
                                .setMessage("This will clear all activity records. Your actual data (notes, tasks, etc.) won't be affected.")
                                .setPositiveButton("Clear All", (d2, w2) -> {
                                    repo.clearAll();
                                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                                    updateHistoryTab();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                        updateHistoryTab();
                    })
                    .show();
            });
        }
    }

    private long getTodayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void addHistorySectionHeader(android.widget.LinearLayout parent, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(0xFF8899AA);
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int)(12 * getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int)(6 * getResources().getDisplayMetrics().density);
        header.setLayoutParams(lp);
        parent.addView(header);
    }

    private void addHistoryItem(android.widget.LinearLayout parent, ActivityLogEntry entry) {
        android.widget.LinearLayout item = new android.widget.LinearLayout(this);
        item.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        item.setBackgroundResource(R.drawable.history_activity_item_bg);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int dp8 = (int)(8 * getResources().getDisplayMetrics().density);
        int dp12 = (int)(12 * getResources().getDisplayMetrics().density);
        item.setPadding(dp12, dp8, dp12, dp8);

        android.widget.LinearLayout.LayoutParams itemLp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        itemLp.bottomMargin = dp8;
        item.setLayoutParams(itemLp);

        // Icon
        TextView iconView = new TextView(this);
        iconView.setText(entry.icon);
        iconView.setTextSize(20);
        android.widget.LinearLayout.LayoutParams iconLp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        iconLp.setMarginEnd(dp12);
        iconView.setLayoutParams(iconLp);
        item.addView(iconView);

        // Text container
        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(this);
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams tcLp = new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        textContainer.setLayoutParams(tcLp);

        TextView desc = new TextView(this);
        desc.setText(entry.description);
        desc.setTextColor(0xFFFFFFFF);
        desc.setTextSize(14);
        textContainer.addView(desc);

        TextView time = new TextView(this);
        time.setText(getRelativeTime(entry.timestamp));
        time.setTextColor(0xFF5B6B7D);
        time.setTextSize(11);
        textContainer.addView(time);

        item.addView(textContainer);

        // Feature badge
        TextView badge = new TextView(this);
        badge.setText(entry.feature);
        badge.setTextColor(0xFF8899AA);
        badge.setTextSize(10);
        badge.setMaxLines(1);
        item.addView(badge);

        parent.addView(item);
    }

    private String getRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) return "Just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        return (diff / 86400000) + "d ago";
    }

}