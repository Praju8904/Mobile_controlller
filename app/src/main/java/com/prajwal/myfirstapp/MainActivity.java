package com.prajwal.myfirstapp;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

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

    // Heartbeat & Connection State Variables
    private long lastServerHeartbeat = 0;
    private final long TIMEOUT_THRESHOLD = 7000; // 7 seconds
    private boolean isServerCurrentlyRunning = false;
    private int missCount = 0;
    private boolean isPreviewOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_old);

        // --- Core Components Init ---
        // Default IP, can be changed via Dialog
        connectionManager = new ConnectionManager("10.190.76.54");
        backgroundServices = new BackgroundServices();

        // --- View Initializations ---
        modeSwitch = findViewById(R.id.modeSwitch);
        View touchPad = findViewById(R.id.touchPad);
        EditText hiddenInput = findViewById(R.id.hiddenInput);
        presenterModeUI = findViewById(R.id.presenterModeUI);
        tvStatus = findViewById(R.id.tvStatus);
        tvBattery = findViewById(R.id.tvBattery);
        ivPreview = findViewById(R.id.ivPreview);
        btnStop = findViewById(R.id.btnStop);

        // --- Touchpad Init ---
        touchpadHandler = new TouchpadHandler(this, touchPad, modeSwitch, connectionManager);
        touchPad.setOnTouchListener(touchpadHandler);

        // --- Main Control Buttons ---
        findViewById(R.id.btnLeftClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_CLICK"));
        findViewById(R.id.btnRightClick).setOnClickListener(v -> connectionManager.sendCommand("MOUSE_RIGHT_CLICK"));

        // Dynamic Start/Stop Button
        btnStop.setOnClickListener(v -> {
            connectionManager.toggleServerState(isServerCurrentlyRunning, () -> {
                String msg = isServerCurrentlyRunning ? "STOP_MAIN_SERVER" : "START_MAIN_SERVER";
                runOnUiThread(() -> Toast.makeText(this, "Sending " + msg, Toast.LENGTH_SHORT).show());
            });
        });

        findViewById(R.id.btnOpenNotepad).setOnClickListener(v -> connectionManager.sendCommand("OPEN_NOTEPAD"));
        findViewById(R.id.btnMenu).setOnClickListener(v -> showIPDialog());
        findViewById(R.id.btnVoice).setOnClickListener(v -> startVoiceRecognition(300));
        findViewById(R.id.btnWriteAI).setOnClickListener(v -> showWriteAIDialog());
        findViewById(R.id.btnEnterPresenter).setOnClickListener(v -> togglePresenterMode(true));

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
        //  if the user relies on it we can add a specific handler, 
        //  but the TouchPadHandler supports 2-finger scroll now).
        //  Let's actually restore the simple strip logic if they use it specifically.
        setupScrollStrip(scrollStrip);


        // Keyboard Logic
        setupKeyboard(hiddenInput);

        // Preview Toggle
        findViewById(R.id.btnTogglePreview).setOnClickListener(v -> togglePreview((Button)v));

        // Start background listeners
        backgroundServices.startAutoDiscovery(() -> lastServerHeartbeat = System.currentTimeMillis());
        backgroundServices.startStatusListener((battery, plugged) -> {
            runOnUiThread(() -> tvBattery.setText("PC Battery: " + battery + (plugged ? " ⚡" : "")));
        });
        backgroundServices.startPreviewListener((bitmap) -> {
            runOnUiThread(() -> ivPreview.setImageBitmap(bitmap));
        });

        startConnectionMonitor(); // Initialize the dynamic status monitor
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
                        v.setBackgroundColor(Color.parseColor("#6603DAC5"));
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
                        v.setBackgroundColor(Color.parseColor("#3303DAC5"));
                        break;
                }
                return true;
            }
        });
    }
    
    // --- Dynamic Status Handlers ---

    private void startConnectionMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000); // Check every 3 seconds
                    long currentTime = System.currentTimeMillis();
                    boolean isHearbeatFresh = (currentTime - lastServerHeartbeat < TIMEOUT_THRESHOLD);

                    if (isHearbeatFresh) {
                        missCount = 0;
                        if (!isServerCurrentlyRunning) updateConnectionUI(true);
                    } else {
                        missCount++;
                        if (missCount >= 2 && isServerCurrentlyRunning) updateConnectionUI(false);
                    }
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }).start();
    }

    private void updateConnectionUI(boolean isConnected) {
        runOnUiThread(() -> {
            isServerCurrentlyRunning = isConnected;
            if (isConnected) {
                tvStatus.setText("Connected: " + connectionManager.getLaptopIp());
                tvStatus.setTextColor(Color.GREEN);
                btnStop.setText("Stop Server");
                btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#CF6679")));
            } else {
                tvStatus.setText("Disconnected");
                tvStatus.setTextColor(Color.RED);
                btnStop.setText("Start Server");
                btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#03DAC5")));
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
             if (uri != null) sendSharedFile(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (fileUris != null) {
                for (Uri uri : fileUris) sendSharedFile(uri);
            }
        }
    }

    private void sendSharedFile(Uri uri) {
        // Wake server then send
        connectionManager.toggleServerState(false, null);
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait for wake
                connectionManager.sendFileToLaptop(this, uri, 
                    () -> runOnUiThread(() -> Toast.makeText(this, "Sending file...", Toast.LENGTH_SHORT).show()),
                    () -> runOnUiThread(() -> Toast.makeText(this, "Sent: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show()),
                    () -> runOnUiThread(() -> Toast.makeText(this, "Transfer Failed", Toast.LENGTH_SHORT).show())
                );
            } catch (InterruptedException e) { e.printStackTrace(); }
        }).start();
    }


    // --- UI Dialogs & Pickers ---

    private void showClipboardDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send to PC Clipboard");
        final EditText input = new EditText(this);
        input.setHint("Type or paste text here...");

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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
        builder.setPositiveButton("Execute", (d, w) -> connectionManager.sendCommand("VOICE:" + input.getText().toString()));
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
        if (presenterModeUI != null) presenterModeUI.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    
    private void setupKeyboard(EditText hiddenInput) {
        hiddenInput.setText(" ");
        findViewById(R.id.btnKeyboard).setOnClickListener(v -> {
            hiddenInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT);
        });

        hiddenInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) connectionManager.sendCommand("KEY:BACKSPACE");
                else if (s.length() > 1) {
                    char c = s.charAt(s.length() - 1);
                    String newChar = String.valueOf(c);
                    if (newChar.equals(" ")) connectionManager.sendCommand("KEY:SPACE");
                    else if (newChar.equals("\n")) connectionManager.sendCommand("KEY:ENTER");
                    else connectionManager.sendCommand("KEY:" + newChar);
                }
            }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() != 1 || !s.toString().equals(" ")) {
                    hiddenInput.removeTextChangedListener(this);
                    s.clear(); s.append(" ");
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
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            
            // File Handling
            if (requestCode >= 200 && requestCode <= 202) {
                 Uri uri = data.getData();
                 if (uri != null) {
                     connectionManager.sendFileToLaptop(this, uri, 
                        () -> runOnUiThread(() -> Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()),
                        () -> runOnUiThread(() -> Toast.makeText(this, "Sent!", Toast.LENGTH_SHORT).show()),
                        () -> runOnUiThread(() -> Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show())
                     );
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
                        .setPositiveButton("Execute", (d,w) -> connectionManager.sendCommand("VOICE:" + text))
                        .setNegativeButton("Cancel", null)
                        .show();
                } else if (requestCode == 500) {
                     connectionManager.sendCommand("CLIPBOARD:" + text);
                     Toast.makeText(this, "Synced Clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    @Override public void onBackPressed() {
        if (presenterModeUI != null && presenterModeUI.getVisibility() == View.VISIBLE) togglePresenterMode(false);
        else super.onBackPressed();
    }
}