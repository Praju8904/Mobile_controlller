package com.prajwal.myfirstapp;

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

public class MainActivity extends AppCompatActivity {

    private ConnectionManager connectionManager;
    private BackgroundServices backgroundServices;
    private TouchpadHandler touchpadHandler;

    private SwitchCompat modeSwitch;
    private ConstraintLayout presenterModeUI;
    private TextView tvStatus;
    private TextView tvBattery;
    private ImageView ivPreview;
    private Button btnStop;
    private SensorHandler sensorHandler;

    // Home Navigation
    private View homeScreen;
    private View touchpadScreen;
    private Button btnBackHome;

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
        btnStop = findViewById(R.id.btnStop);

        // --- Home Navigation Init ---
        homeScreen = findViewById(R.id.homeScreen);
        touchpadScreen = findViewById(R.id.touchpadScreen);
        btnBackHome = findViewById(R.id.btnBackHome);
        btnBackHome.setOnClickListener(v -> navigateToHome());
        setupHomeCards();

        // --- Touchpad Init ---
        touchpadHandler = new TouchpadHandler(this, touchPad, modeSwitch, connectionManager);
        touchPad.setOnTouchListener(touchpadHandler);

        // --- Main Control Buttons ---
        findViewById(R.id.btnLeftClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_CLICK"));
        findViewById(R.id.btnRightClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_RIGHT_CLICK"));

        // Dynamic Button: Start/Test when disconnected, Stop when connected
        btnStop.setOnClickListener(v -> {
            // Check if server is selected first
            if (!serverSelected) {
                Toast.makeText(this, "Please select a server first (Menu button)", Toast.LENGTH_LONG).show();
                return;
            }

            if (isServerCurrentlyRunning) {
                // Server is running - Stop it via watchdog
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Stop Server")
                        .setMessage("Stop the Python server on your PC?")
                        .setPositiveButton("Stop", (d, w) -> {
                            connectionManager.toggleServerState(true, null);
                            Toast.makeText(this, "Stopping server...", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                // Server is disconnected - show options
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Server Options")
                        .setMessage("What would you like to do?")
                        .setPositiveButton("Start Server", (d, w) -> {
                            connectionManager.toggleServerState(false, null);
                            Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show();
                        })
                        .setNeutralButton("Test Connection", (d, w) -> testConnection())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        findViewById(R.id.btnOpenNotepad).setOnClickListener(v -> connectionManager.sendCommand("OPEN_NOTEPAD"));
        findViewById(R.id.btnMenu).setOnClickListener(v -> showServerSelectionDialog());
        findViewById(R.id.btnScanQR).setOnClickListener(v -> startQRScan());
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
        tvStatus.setText("● No server selected");
        tvStatus.setTextColor(Color.parseColor("#FFB74D")); // Warm orange
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
                tvStatus.setText("● Connected: " + connectionManager.getLaptopIp());
                tvStatus.setTextColor(Color.parseColor("#66BB6A"));
                btnStop.setText("Stop Server");
                btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#C62828"))); // Deep Red
            } else {
                tvStatus.setText("● Disconnected");
                tvStatus.setTextColor(Color.parseColor("#EF5350"));
                btnStop.setText("Start Server");
                btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1565C0"))); // Blue
            }
            // Keep Dynamic Bar in sync
            updateDynamicBar();
        });
    }

    private void testConnection() {
        btnStop.setEnabled(false);
        btnStop.setText("Testing...");

        connectionManager.testConnection(new ConnectionManager.PingCallback() {
            @Override
            public void onSuccess(long responseTime) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connected (" + responseTime + "ms)", Toast.LENGTH_SHORT).show();
                    lastServerHeartbeat = System.currentTimeMillis();
                    btnStop.setEnabled(true);
                });
            }

            @Override
            public void onFailure() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Server not responding", Toast.LENGTH_SHORT).show();
                    lastServerHeartbeat = 0;
                    btnStop.setEnabled(true);
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
        tvStatus.setText("● Connecting to " + ipAddress + "...");
        tvStatus.setTextColor(Color.parseColor("#FFB74D")); // Warm orange
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
        findViewById(R.id.cardTouchpad).setOnClickListener(v -> navigateToTouchpad());

        findViewById(R.id.cardKeyboard).setOnClickListener(v -> {
            navigateToTouchpad();
            new android.os.Handler().postDelayed(() -> {
                EditText input = findViewById(R.id.hiddenInput);
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
        });

        findViewById(R.id.cardPresenter).setOnClickListener(v -> togglePresenterMode(true));
        // File Transfer — tap to send, long-press to view received
        findViewById(R.id.cardFiles).setOnClickListener(v -> openMediaPicker("*/*", 200));
        findViewById(R.id.cardFiles).setOnLongClickListener(v -> {
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
        findViewById(R.id.cardAI).setOnClickListener(v -> showAIMenu());
        findViewById(R.id.cardPCControl).setOnClickListener(v -> showPCControlMenu());

        findViewById(R.id.cardTasks).setOnClickListener(v -> {
            Intent taskIntent = new Intent(MainActivity.this, TaskManagerActivity.class);
            taskIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(taskIntent);
        });

        findViewById(R.id.cardMedia).setOnClickListener(v -> showMediaMenu());

        // Notes Card
        findViewById(R.id.cardNotes).setOnClickListener(v -> {
            Intent notesIntent = new Intent(MainActivity.this, NotesActivity.class);
            notesIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(notesIntent);
        });

        // Dynamic Bar Card
        findViewById(R.id.cardDynamicBar).setOnClickListener(v -> toggleDynamicBar());

        // Calendar Card
        findViewById(R.id.cardCalendar).setOnClickListener(v -> {
            Intent calendarIntent = new Intent(MainActivity.this, CalendarActivity.class);
            calendarIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(calendarIntent);
        });

        // Chat Card
        findViewById(R.id.cardChat).setOnClickListener(v -> {
            Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
            chatIntent.putExtra("server_ip", connectionManager.getLaptopIp());
            startActivity(chatIntent);
        });

        // Settings Card
        findViewById(R.id.cardSettings).setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        // Expense Tracker Card
        findViewById(R.id.cardExpenses).setOnClickListener(v -> {
            Intent expenseIntent = new Intent(MainActivity.this, ExpenseTrackerActivity.class);
            startActivity(expenseIntent);
        });

        // Password Manager Card
        findViewById(R.id.cardPasswordManager).setOnClickListener(v -> {
            Intent vaultIntent = new Intent(MainActivity.this, PasswordManagerActivity.class);
            startActivity(vaultIntent);
        });

        // Update card summaries on resume
        updateExpenseCardSummary();
        updateVaultCardSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateExpenseCardSummary();
        updateVaultCardSummary();
    }

    private void updateExpenseCardSummary() {
        try {
            ExpenseRepository expRepo = new ExpenseRepository(this);
            double todaySpend = expRepo.getTodaySpend();
            double budget = expRepo.getMonthlyBudget();
            TextView summary = findViewById(R.id.tvExpenseCardSummary);
            if (summary != null) {
                if (todaySpend > 0) {
                    String text = "₹" + (todaySpend >= 1000 ?
                            String.format("%.1fk", todaySpend / 1000) :
                            String.format("%.0f", todaySpend)) + " today";
                    if (budget > 0) {
                        double monthSpend = expRepo.getMonthSpend();
                        double pct = monthSpend / budget * 100;
                        if (pct >= 100) {
                            summary.setTextColor(0xFFEF4444);
                            text += " • Over budget!";
                        } else if (pct >= 80) {
                            summary.setTextColor(0xFFF59E0B);
                            text += " • " + String.format("%.0f%%", pct) + " used";
                        } else {
                            summary.setTextColor(0xFFE0D4FF);
                        }
                    }
                    summary.setText(text);
                } else {
                    summary.setText("Track & Manage");
                    summary.setTextColor(0xFFE0D4FF);
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateVaultCardSummary() {
        try {
            PasswordRepository vaultRepo = new PasswordRepository(this);
            TextView summary = findViewById(R.id.tvVaultCardSummary);
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
    }

    private void navigateToTouchpad() {
        Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.anim_slide_out_left);
        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.anim_slide_in_right);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                homeScreen.setVisibility(View.GONE);
                touchpadScreen.setVisibility(View.VISIBLE);
                touchpadScreen.startAnimation(slideIn);
            }
        });
        homeScreen.startAnimation(slideOut);
        btnBackHome.setVisibility(View.VISIBLE);
        btnBackHome.setAlpha(0f);
        btnBackHome.animate().alpha(1f).setDuration(300).start();
    }

    private void navigateToHome() {
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
        btnBackHome.animate().alpha(0f).setDuration(200).withEndAction(() ->
            btnBackHome.setVisibility(View.GONE)
        ).start();
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

}