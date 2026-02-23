package com.prajwal.myfirstapp;

//package com.prajwal.myfirstapp;
// Core Android
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings;

// Networking
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;

// Hardware Control (Vibration, Audio, Camera, Battery)
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.speech.tts.TextToSpeech;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.Environment;

// Clipboard & Notifications
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;

// Broadcasting (Important for System Monitor & Chat)
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.net.Uri;

/**
 * Listens for reverse commands sent FROM the PC TO the phone.
 * Runs as a background thread started from MainActivity.
 * * Protocol: UDP packets on port 6000
 * Response/heartbeat: UDP packets sent to PC on port 6001
 */
public class ReverseCommandListener {

    private static final String TAG = "ReverseCmd";
    private static final int LISTEN_PORT = 6000;
    private static final int RESPONSE_PORT = 6001;

    private final Context context;
    private final Handler mainHandler;
    private DatagramSocket listenSocket;
    private boolean running = false;
    private String serverIp;

    // Hardware
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;
    private boolean flashlightOn = false;

    public interface StatusCallback {
        void onReverseCommand(String command);
    }

    private StatusCallback callback;

    public ReverseCommandListener(Context context, String serverIp) {
        this.context = context.getApplicationContext();
        this.serverIp = serverIp;
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize TTS
        tts = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                Log.i(TAG, "TTS initialized");
            }
        });
    }

    public void setServerIp(String ip) {
        this.serverIp = ip;
    }

    public void setCallback(StatusCallback callback) {
        this.callback = callback;
    }

    public void start() {
        if (running) return;
        running = true;

        // Listener thread
        new Thread(() -> {
            try {
                listenSocket = new DatagramSocket(LISTEN_PORT);
                listenSocket.setSoTimeout(1000);
                Log.i(TAG, "Reverse command listener started on port " + LISTEN_PORT);

                byte[] buffer = new byte[4096];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        listenSocket.receive(packet);
                        String command = new String(packet.getData(), 0, packet.getLength()).trim();
                        String senderIp = packet.getAddress().getHostAddress();

                        Log.i(TAG, "Received: " + command + " from " + senderIp);
                        handleCommand(command, senderIp);

                    } catch (java.net.SocketTimeoutException e) {
                        // Normal timeout, continue
                    } catch (Exception e) {
                        if (running) Log.e(TAG, "Receive error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Listener start failed: " + e.getMessage());
            } finally {
                if (listenSocket != null && !listenSocket.isClosed()) {
                    listenSocket.close();
                }
            }
        }).start();

        // Heartbeat thread — tells the PC we're alive
        new Thread(() -> {
            while (running) {
                try {
                    sendToPC("HEARTBEAT:" + System.currentTimeMillis());
                    Thread.sleep(2000);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }).start();

        Log.i(TAG, "Reverse command system started");
    }

    public void stop() {
        running = false;
        if (listenSocket != null && !listenSocket.isClosed()) {
            listenSocket.close();
        }
        if (tts != null) {
            tts.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    // ─── COMMAND DISPATCHER ────────────────────────────────────
    private void handleCommand(String command, String senderIp) {
        try {
            if (command.startsWith("VIBRATE_PATTERN:")) {
                String pattern = command.substring(16);
                vibratePattern(pattern);

            } else if (command.startsWith("VIBRATE:")) {
                int duration = Integer.parseInt(command.substring(8));
                vibrate(duration);

            } else if (command.startsWith("RING:")) {
                int seconds = Integer.parseInt(command.substring(5));
                ringPhone(seconds);

            } else if (command.equals("RING_STOP")) {
                stopRing();

            } else if (command.startsWith("FLASH:")) {
                // FLASH:FF0000:1000
                // Flash screen effect — just show a toast for now
                showToast("Flash: " + command.substring(6));

            } else if (command.startsWith("TOAST:")) {
                String msg = command.substring(6);
                showToast(msg);

            } else if (command.startsWith("NOTIFY:")) {
                // NOTIFY:Title|Body
                String payload = command.substring(7);
                String[] parts = payload.split("\\|", 2);
                if (parts.length == 2) {
                    showNotification(parts[0], parts[1]);
                }

            } else if (command.startsWith("BRIGHTNESS:")) {
                int level = Integer.parseInt(command.substring(11));
                setScreenBrightness(level);

            } else if (command.startsWith("VOLUME:")) {
                // VOLUME:music:10
                String[] parts = command.substring(7).split(":");
                if (parts.length == 2) {
                    setVolume(parts[0], Integer.parseInt(parts[1]));
                }

            } else if (command.startsWith("CLIPBOARD:")) {
                String text = command.substring(10);
                setClipboard(text);

            } else if (command.startsWith("OPEN_URL:")) {
                String url = command.substring(9);
                openUrl(url);

            } else if (command.equals("TAKE_SCREENSHOT")) {
                showToast("Screenshot requested (requires system permission)");

            } else if (command.startsWith("FLASHLIGHT:")) {
                boolean on = command.substring(11).equals("ON");
                toggleFlashlight(on);

            } else if (command.equals("GET_INFO")) {
                sendPhoneInfo();

            } else if (command.equals("LOCK_SCREEN")) {
                showToast("Lock screen (requires Device Admin)");

            } else if (command.startsWith("TTS:")) {
                String text = command.substring(4);
                speakText(text);

            } else if (command.equals("FIND_MY_PHONE")) {
                findMyPhone();

            } else if (command.equals("KEEP_ALIVE")) {
                sendToPC("KEEP_ALIVE_ACK");

            } else if (command.startsWith("CAMERA_STREAM:")) {
                String action = command.substring(14);
                showToast("Camera stream: " + action);

                // ─── TASK MANAGER COMMANDS ─────────────────────────
            } else if (command.startsWith("TASKS_SYNC:")) {
                // Full task list sync from PC
                String tasksJson = command.substring(11);
                Log.i(TAG, "Received task sync from PC");
                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
                if (taskActivity != null) {
                    taskActivity.onTasksSyncReceived(tasksJson);
                } else {
                    // Activity not open — show notification
                    showNotification("Tasks Synced", "Task list updated from PC");
                }

            } else if (command.startsWith("TASK_NOTIFY_ADDED:")) {
                // TASK_NOTIFY_ADDED:id:title
                String[] parts = command.substring(18).split(":", 2);
                String taskId = parts[0];
                String title = parts.length > 1 ? parts[1] : "New Task";
                Log.i(TAG, "PC added task: " + title);
                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
                if (taskActivity != null) {
                    taskActivity.onTaskNotifyAdded(taskId, title);
                } else {
                    showNotification("New Task from PC", title);
                    vibrate(200);
                }

            } else if (command.startsWith("TASK_NOTIFY_COMPLETED:")) {
                // TASK_NOTIFY_COMPLETED:id:title
                String[] parts = command.substring(22).split(":", 2);
                String taskId = parts[0];
                String title = parts.length > 1 ? parts[1] : "Task";
                Log.i(TAG, "PC completed task: " + title);
                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
                if (taskActivity != null) {
                    taskActivity.onTaskNotifyCompleted(taskId, title);
                } else {
                    showNotification("Task Completed", "✅ " + title);
                }

            } else if (command.startsWith("TASK_NOTIFY_DELETED:")) {
                String taskId = command.substring(20);
                Log.i(TAG, "PC deleted task: " + taskId);
                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
                if (taskActivity != null) {
                    taskActivity.onTaskNotifyDeleted(taskId);
                } else {
                    showNotification("Task Deleted", "A task was removed from PC");
                }

            } else if (command.startsWith("TASK_ADDED:")) {
                // Acknowledgment from PC that task was added
                Log.i(TAG, "PC confirmed task add: " + command.substring(11));

            } else if (command.startsWith("TASKS:")) {
                // Response to TASK_LIST request
                String tasksJson = command.substring(6);
                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
                if (taskActivity != null) {
                    taskActivity.onTasksSyncReceived(tasksJson);
                }

            } else if (command.startsWith("NOTES_DATA:")) {
                String jsonData = command.substring(11);

                Intent intent = new Intent("com.prajwal.myfirstapp.NOTES_EVENT");
                intent.putExtra("data", jsonData);
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            } else if (command.startsWith("CLIPBOARD_DATA:")) {
                String text = command.substring(15).replace("\\n", "\n"); // Unescape

                // Clipboard operations must run on Main Thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("PC Sync", text);
                            clipboard.setPrimaryClip(clip);
                            // Optional: Toast feedback
                            // Toast.makeText(context, "Copied from PC!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }else if (command.startsWith("SYS_INFO:")) {
                // Format: SYS_INFO:CPU|RAM|BAT|PLUGGED
                try {
                    String[] parts = command.substring(9).split("\\|");
                    if (parts.length >= 4) {
                        int cpu = Integer.parseInt(parts[0]);
                        int ram = Integer.parseInt(parts[1]);
                        int bat = Integer.parseInt(parts[2]);
                        boolean plugged = parts[3].equals("1");

                        // Broadcast to MainActivity
                        Intent intent = new Intent("com.prajwal.myfirstapp.SYS_UPDATE");
                        intent.putExtra("cpu", cpu);
                        intent.putExtra("ram", ram);
                        intent.putExtra("bat", bat);
                        intent.putExtra("plugged", plugged);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Sys Info parse error: " + e.getMessage());
                }
            } else if (command.startsWith("FILE_LIST:")) {
                String jsonData = command.substring(10);

                Intent intent = new Intent("com.prajwal.myfirstapp.FILE_LIST_UPDATE");
                intent.putExtra("data", jsonData);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }

            // Notify callback
            if (callback != null) {
                mainHandler.post(() -> callback.onReverseCommand(command));
            }

        } catch (Exception e) {
            Log.e(TAG, "Command handling error: " + e.getMessage());
        }
    }

    // ─── IMPLEMENTATIONS ───────────────────────────────────────

    private void vibrate(int durationMs) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
        Log.i(TAG, "Vibrate: " + durationMs + "ms");
    }

    private void vibratePattern(String patternStr) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;

        String[] parts = patternStr.split(",");
        long[] pattern = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            pattern[i] = Long.parseLong(parts[i].trim());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    private void ringPhone(int seconds) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mediaPlayer = MediaPlayer.create(context, ringtoneUri);
            if (mediaPlayer != null) {
                // Set to max volume
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_RING);
                    am.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0);
                }

                mediaPlayer.setLooping(true);
                mediaPlayer.start();

                // Auto-stop after 'seconds'
                mainHandler.postDelayed(this::stopRing, seconds * 1000L);
                Log.i(TAG, "Ringing for " + seconds + "s");
            }
        } catch (Exception e) {
            Log.e(TAG, "Ring error: " + e.getMessage());
        }
    }

    private void stopRing() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "Stop ring error: " + e.getMessage());
            }
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    private void showNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String channelId = "pc_control_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "PC Control", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications from PC Control Panel");
            nm.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, channelId);
        } else {
            builder = new Notification.Builder(context);
        }

        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();

        nm.notify((int) System.currentTimeMillis(), notification);
        Log.i(TAG, "Notification: " + title);
    }

    private void setScreenBrightness(int level) {
        // level: 0-255
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, Math.min(255, Math.max(0, level)));
                Log.i(TAG, "Brightness set to " + level);
            } else {
                showToast("Grant 'Modify System Settings' permission");
            }
        } catch (Exception e) {
            Log.e(TAG, "Brightness error: " + e.getMessage());
        }
    }

    private void setVolume(String stream, int level) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        int streamType;
        switch (stream.toLowerCase()) {
            case "ring": streamType = AudioManager.STREAM_RING; break;
            case "alarm": streamType = AudioManager.STREAM_ALARM; break;
            case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
            default: streamType = AudioManager.STREAM_MUSIC; break;
        }

        int maxVol = am.getStreamMaxVolume(streamType);
        int clampedLevel = Math.min(maxVol, Math.max(0, level));
        am.setStreamVolume(streamType, clampedLevel, 0);
        Log.i(TAG, "Volume " + stream + " set to " + clampedLevel);
    }

    private void setClipboard(String text) {
        mainHandler.post(() -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("PC Clipboard", text));
                showToast("Clipboard synced from PC");
            }
        });
    }

    private void openUrl(String url) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                showToast("Can't open URL: " + url);
            }
        });
    }

    private void toggleFlashlight(boolean on) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                String cameraId = cameraManager.getCameraIdList()[0];
                cameraManager.setTorchMode(cameraId, on);
                flashlightOn = on;
                Log.i(TAG, "Flashlight: " + (on ? "ON" : "OFF"));
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Flashlight error: " + e.getMessage());
        }
    }

    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pc_tts");
            Log.i(TAG, "TTS: " + text);
        }
    }

    private void findMyPhone() {
        // Ring at max volume + vibrate pattern + flash
        vibrate(3000);
        ringPhone(10);

        // Also set brightness to max
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 255);
            }
        } catch (Exception ignored) {}

        showToast("📍 FIND MY PHONE — Here I am!");
        Log.i(TAG, "FIND MY PHONE activated!");
    }

    private void sendPhoneInfo() {
        new Thread(() -> {
            try {
                // Battery
                android.os.BatteryManager bm = (android.os.BatteryManager)
                        context.getSystemService(Context.BATTERY_SERVICE);
                int battery = bm != null ? bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;

                // Storage
                android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
                long freeGB = stat.getAvailableBytes() / (1024 * 1024 * 1024);
                long totalGB = stat.getTotalBytes() / (1024 * 1024 * 1024);

                String info = String.format("Battery:%d|Storage:%dGB/%dGB|Model:%s|Android:%s",
                        battery, freeGB, totalGB,
                        Build.MODEL, Build.VERSION.RELEASE);

                sendToPC("PHONE_INFO:" + info);
            } catch (Exception e) {
                Log.e(TAG, "Info gather error: " + e.getMessage());
            }
        }).start();
    }

    // ─── SEND TO PC ────────────────────────────────────────────
    private void sendToPC(String message) {
        if (serverIp == null) return;
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = message.getBytes();
                InetAddress address = InetAddress.getByName(serverIp);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, RESPONSE_PORT);
                socket.send(packet);
                socket.close();
            } catch (Exception e) {
                // Silently fail for heartbeats
            }
        }).start();
    }
}



//package com.prajwal.myfirstapp;
//
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.content.ClipData;
//import android.content.ClipboardManager;
//import android.content.Context;
//import android.content.Intent;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraManager;
//import android.media.AudioManager;
//import android.media.MediaPlayer;
//import android.media.RingtoneManager;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Handler;
//import android.os.Looper;
//import android.os.VibrationEffect;
//import android.os.Vibrator;
//import android.provider.Settings;
//import android.speech.tts.TextToSpeech;
//import android.util.Log;
//import android.widget.Toast;
//
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.net.InetAddress;
//import java.util.Locale;
//
//import android.content.ClipData;
//import android.content.ClipboardManager;
//import android.os.Handler;
//import android.os.Looper;
//
///**
// * Listens for reverse commands sent FROM the PC TO the phone.
// * Runs as a background thread started from MainActivity.
// *
// * Protocol: UDP packets on port 6000
// * Response/heartbeat: UDP packets sent to PC on port 6001
// */
//public class ReverseCommandListener {
//
//    private static final String TAG = "ReverseCmd";
//    private static final int LISTEN_PORT = 6000;
//    private static final int RESPONSE_PORT = 6001;
//
//    private final Context context;
//    private final Handler mainHandler;
//    private DatagramSocket listenSocket;
//    private boolean running = false;
//    private String serverIp;
//
//    // Hardware
//    private TextToSpeech tts;
//    private MediaPlayer mediaPlayer;
//    private boolean flashlightOn = false;
//
//    public interface StatusCallback {
//        void onReverseCommand(String command);
//    }
//
//    private StatusCallback callback;
//
//    public ReverseCommandListener(Context context, String serverIp) {
//        this.context = context.getApplicationContext();
//        this.serverIp = serverIp;
//        this.mainHandler = new Handler(Looper.getMainLooper());
//
//        // Initialize TTS
//        tts = new TextToSpeech(this.context, status -> {
//            if (status == TextToSpeech.SUCCESS) {
//                tts.setLanguage(Locale.US);
//                Log.i(TAG, "TTS initialized");
//            }
//        });
//    }
//
//    public void setServerIp(String ip) {
//        this.serverIp = ip;
//    }
//
//    public void setCallback(StatusCallback callback) {
//        this.callback = callback;
//    }
//
//    public void start() {
//        if (running) return;
//        running = true;
//
//        // Listener thread
//        new Thread(() -> {
//            try {
//                listenSocket = new DatagramSocket(LISTEN_PORT);
//                listenSocket.setSoTimeout(1000);
//                Log.i(TAG, "Reverse command listener started on port " + LISTEN_PORT);
//
//                byte[] buffer = new byte[4096];
//                while (running) {
//                    try {
//                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
//                        listenSocket.receive(packet);
//                        String command = new String(packet.getData(), 0, packet.getLength()).trim();
//                        String senderIp = packet.getAddress().getHostAddress();
//
//                        Log.i(TAG, "Received: " + command + " from " + senderIp);
//                        handleCommand(command, senderIp);
//
//                    } catch (java.net.SocketTimeoutException e) {
//                        // Normal timeout, continue
//                    } catch (Exception e) {
//                        if (running) Log.e(TAG, "Receive error: " + e.getMessage());
//                    }
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Listener start failed: " + e.getMessage());
//            } finally {
//                if (listenSocket != null && !listenSocket.isClosed()) {
//                    listenSocket.close();
//                }
//            }
//        }).start();
//
//        // Heartbeat thread — tells the PC we're alive
//        new Thread(() -> {
//            while (running) {
//                try {
//                    sendToPC("HEARTBEAT:" + System.currentTimeMillis());
//                    Thread.sleep(2000);
//                } catch (Exception e) {
//                    // Ignore
//                }
//            }
//        }).start();
//
//        Log.i(TAG, "Reverse command system started");
//    }
//
//    public void stop() {
//        running = false;
//        if (listenSocket != null && !listenSocket.isClosed()) {
//            listenSocket.close();
//        }
//        if (tts != null) {
//            tts.shutdown();
//        }
//        if (mediaPlayer != null) {
//            mediaPlayer.release();
//        }
//    }
//
//    // ─── COMMAND DISPATCHER ────────────────────────────────────
//    private void handleCommand(String command, String senderIp) {
//        try {
//            if (command.startsWith("VIBRATE_PATTERN:")) {
//                String pattern = command.substring(16);
//                vibratePattern(pattern);
//
//            } else if (command.startsWith("VIBRATE:")) {
//                int duration = Integer.parseInt(command.substring(8));
//                vibrate(duration);
//
//            } else if (command.startsWith("RING:")) {
//                int seconds = Integer.parseInt(command.substring(5));
//                ringPhone(seconds);
//
//            } else if (command.equals("RING_STOP")) {
//                stopRing();
//
//            } else if (command.startsWith("FLASH:")) {
//                // FLASH:FF0000:1000
//                // Flash screen effect — just show a toast for now
//                showToast("Flash: " + command.substring(6));
//
//            } else if (command.startsWith("TOAST:")) {
//                String msg = command.substring(6);
//                showToast(msg);
//
//            } else if (command.startsWith("NOTIFY:")) {
//                // NOTIFY:Title|Body
//                String payload = command.substring(7);
//                String[] parts = payload.split("\\|", 2);
//                if (parts.length == 2) {
//                    showNotification(parts[0], parts[1]);
//                }
//
//            } else if (command.startsWith("BRIGHTNESS:")) {
//                int level = Integer.parseInt(command.substring(11));
//                setScreenBrightness(level);
//
//            } else if (command.startsWith("VOLUME:")) {
//                // VOLUME:music:10
//                String[] parts = command.substring(7).split(":");
//                if (parts.length == 2) {
//                    setVolume(parts[0], Integer.parseInt(parts[1]));
//                }
//
//            } else if (command.startsWith("CLIPBOARD:")) {
//                String text = command.substring(10);
//                setClipboard(text);
//
//            } else if (command.startsWith("OPEN_URL:")) {
//                String url = command.substring(9);
//                openUrl(url);
//
//            } else if (command.equals("TAKE_SCREENSHOT")) {
//                showToast("Screenshot requested (requires system permission)");
//
//            } else if (command.startsWith("FLASHLIGHT:")) {
//                boolean on = command.substring(11).equals("ON");
//                toggleFlashlight(on);
//
//            } else if (command.equals("GET_INFO")) {
//                sendPhoneInfo();
//
//            } else if (command.equals("LOCK_SCREEN")) {
//                showToast("Lock screen (requires Device Admin)");
//
//            } else if (command.startsWith("TTS:")) {
//                String text = command.substring(4);
//                speakText(text);
//
//            } else if (command.equals("FIND_MY_PHONE")) {
//                findMyPhone();
//
//            } else if (command.equals("KEEP_ALIVE")) {
//                sendToPC("KEEP_ALIVE_ACK");
//
//            } else if (command.startsWith("CAMERA_STREAM:")) {
//                String action = command.substring(14);
//                showToast("Camera stream: " + action);
//
//            // ─── TASK MANAGER COMMANDS ─────────────────────────
//            } else if (command.startsWith("TASKS_SYNC:")) {
//                // Full task list sync from PC
//                String tasksJson = command.substring(11);
//                Log.i(TAG, "Received task sync from PC");
//                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
//                if (taskActivity != null) {
//                    taskActivity.onTasksSyncReceived(tasksJson);
//                } else {
//                    // Activity not open — show notification
//                    showNotification("Tasks Synced", "Task list updated from PC");
//                }
//
//            } else if (command.startsWith("TASK_NOTIFY_ADDED:")) {
//                // TASK_NOTIFY_ADDED:id:title
//                String[] parts = command.substring(18).split(":", 2);
//                String taskId = parts[0];
//                String title = parts.length > 1 ? parts[1] : "New Task";
//                Log.i(TAG, "PC added task: " + title);
//                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
//                if (taskActivity != null) {
//                    taskActivity.onTaskNotifyAdded(taskId, title);
//                } else {
//                    showNotification("New Task from PC", title);
//                    vibrate(200);
//                }
//
//            } else if (command.startsWith("TASK_NOTIFY_COMPLETED:")) {
//                // TASK_NOTIFY_COMPLETED:id:title
//                String[] parts = command.substring(22).split(":", 2);
//                String taskId = parts[0];
//                String title = parts.length > 1 ? parts[1] : "Task";
//                Log.i(TAG, "PC completed task: " + title);
//                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
//                if (taskActivity != null) {
//                    taskActivity.onTaskNotifyCompleted(taskId, title);
//                } else {
//                    showNotification("Task Completed", "✅ " + title);
//                }
//
//            } else if (command.startsWith("TASK_NOTIFY_DELETED:")) {
//                String taskId = command.substring(20);
//                Log.i(TAG, "PC deleted task: " + taskId);
//                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
//                if (taskActivity != null) {
//                    taskActivity.onTaskNotifyDeleted(taskId);
//                } else {
//                    showNotification("Task Deleted", "A task was removed from PC");
//                }
//
//            } else if (command.startsWith("TASK_ADDED:")) {
//                // Acknowledgment from PC that task was added
//                Log.i(TAG, "PC confirmed task add: " + command.substring(11));
//
//            } else if (command.startsWith("TASKS:")) {
//                // Response to TASK_LIST request
//                String tasksJson = command.substring(6);
//                TaskManagerActivity taskActivity = TaskManagerActivity.getInstance();
//                if (taskActivity != null) {
//                    taskActivity.onTasksSyncReceived(tasksJson);
//                }
//            }
//            else if (command.startsWith("NOTES_DATA:")) {
//                String jsonData = command.substring(11);
//
//                Intent intent = new Intent("com.prajwal.myfirstapp.NOTES_EVENT");
//                intent.putExtra("data", jsonData);
//                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
//            }else if (command.startsWith("CLIPBOARD_DATA:")) {
//                String text = command.substring(15).replace("\\n", "\n"); // Unescape
//
//                // Clipboard operations must run on Main Thread
//                new Handler(Looper.getMainLooper()).post(() -> {
//                    try {
//                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
//                        ClipData clip = ClipData.newPlainText("PC Sync", text);
//                        clipboard.setPrimaryClip(clip);
//                        // Optional: Toast feedback
//                        // Toast.makeText(context, "Copied from PC!", Toast.LENGTH_SHORT).show();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                });
//
//
//            // Notify callback
//            if (callback != null) {
//                mainHandler.post(() -> callback.onReverseCommand(command));
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "Command handling error: " + e.getMessage());
//        }
//    }
//
//    // ─── IMPLEMENTATIONS ───────────────────────────────────────
//
//    private void vibrate(int durationMs) {
//        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
//        if (vibrator == null) return;
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
//        } else {
//            vibrator.vibrate(durationMs);
//        }
//        Log.i(TAG, "Vibrate: " + durationMs + "ms");
//    }
//
//    private void vibratePattern(String patternStr) {
//        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
//        if (vibrator == null) return;
//
//        String[] parts = patternStr.split(",");
//        long[] pattern = new long[parts.length];
//        for (int i = 0; i < parts.length; i++) {
//            pattern[i] = Long.parseLong(parts[i].trim());
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
//        } else {
//            vibrator.vibrate(pattern, -1);
//        }
//    }
//
//    private void ringPhone(int seconds) {
//        try {
//            if (mediaPlayer != null) {
//                mediaPlayer.release();
//            }
//            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
//            mediaPlayer = MediaPlayer.create(context, ringtoneUri);
//            if (mediaPlayer != null) {
//                // Set to max volume
//                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
//                if (am != null) {
//                    int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_RING);
//                    am.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0);
//                }
//
//                mediaPlayer.setLooping(true);
//                mediaPlayer.start();
//
//                // Auto-stop after 'seconds'
//                mainHandler.postDelayed(this::stopRing, seconds * 1000L);
//                Log.i(TAG, "Ringing for " + seconds + "s");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Ring error: " + e.getMessage());
//        }
//    }
//
//    private void stopRing() {
//        if (mediaPlayer != null) {
//            try {
//                mediaPlayer.stop();
//                mediaPlayer.release();
//                mediaPlayer = null;
//            } catch (Exception e) {
//                Log.e(TAG, "Stop ring error: " + e.getMessage());
//            }
//        }
//    }
//
//    private void showToast(String message) {
//        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
//    }
//
//    private void showNotification(String title, String body) {
//        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//        if (nm == null) return;
//
//        String channelId = "pc_control_channel";
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    channelId, "PC Control", NotificationManager.IMPORTANCE_HIGH);
//            channel.setDescription("Notifications from PC Control Panel");
//            nm.createNotificationChannel(channel);
//        }
//
//        Notification.Builder builder;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            builder = new Notification.Builder(context, channelId);
//        } else {
//            builder = new Notification.Builder(context);
//        }
//
//        Notification notification = builder
//                .setSmallIcon(android.R.drawable.ic_dialog_info)
//                .setContentTitle(title)
//                .setContentText(body)
//                .setAutoCancel(true)
//                .build();
//
//        nm.notify((int) System.currentTimeMillis(), notification);
//        Log.i(TAG, "Notification: " + title);
//    }
//
//    private void setScreenBrightness(int level) {
//        // level: 0-255
//        try {
//            if (Settings.System.canWrite(context)) {
//                Settings.System.putInt(context.getContentResolver(),
//                        Settings.System.SCREEN_BRIGHTNESS, Math.min(255, Math.max(0, level)));
//                Log.i(TAG, "Brightness set to " + level);
//            } else {
//                showToast("Grant 'Modify System Settings' permission");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Brightness error: " + e.getMessage());
//        }
//    }
//
//    private void setVolume(String stream, int level) {
//        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
//        if (am == null) return;
//
//        int streamType;
//        switch (stream.toLowerCase()) {
//            case "ring": streamType = AudioManager.STREAM_RING; break;
//            case "alarm": streamType = AudioManager.STREAM_ALARM; break;
//            case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
//            default: streamType = AudioManager.STREAM_MUSIC; break;
//        }
//
//        int maxVol = am.getStreamMaxVolume(streamType);
//        int clampedLevel = Math.min(maxVol, Math.max(0, level));
//        am.setStreamVolume(streamType, clampedLevel, 0);
//        Log.i(TAG, "Volume " + stream + " set to " + clampedLevel);
//    }
//
//    private void setClipboard(String text) {
//        mainHandler.post(() -> {
//            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
//            if (clipboard != null) {
//                clipboard.setPrimaryClip(ClipData.newPlainText("PC Clipboard", text));
//                showToast("Clipboard synced from PC");
//            }
//        });
//    }
//
//    private void openUrl(String url) {
//        mainHandler.post(() -> {
//            try {
//                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                context.startActivity(intent);
//            } catch (Exception e) {
//                showToast("Can't open URL: " + url);
//            }
//        });
//    }
//
//    private void toggleFlashlight(boolean on) {
//        try {
//            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//            if (cameraManager != null) {
//                String cameraId = cameraManager.getCameraIdList()[0];
//                cameraManager.setTorchMode(cameraId, on);
//                flashlightOn = on;
//                Log.i(TAG, "Flashlight: " + (on ? "ON" : "OFF"));
//            }
//        } catch (CameraAccessException e) {
//            Log.e(TAG, "Flashlight error: " + e.getMessage());
//        }
//    }
//
//    private void speakText(String text) {
//        if (tts != null) {
//            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pc_tts");
//            Log.i(TAG, "TTS: " + text);
//        }
//    }
//
//    private void findMyPhone() {
//        // Ring at max volume + vibrate pattern + flash
//        vibrate(3000);
//        ringPhone(10);
//
//        // Also set brightness to max
//        try {
//            if (Settings.System.canWrite(context)) {
//                Settings.System.putInt(context.getContentResolver(),
//                        Settings.System.SCREEN_BRIGHTNESS, 255);
//            }
//        } catch (Exception ignored) {}
//
//        showToast("📍 FIND MY PHONE — Here I am!");
//        Log.i(TAG, "FIND MY PHONE activated!");
//    }
//
//    private void sendPhoneInfo() {
//        new Thread(() -> {
//            try {
//                // Battery
//                android.os.BatteryManager bm = (android.os.BatteryManager)
//                        context.getSystemService(Context.BATTERY_SERVICE);
//                int battery = bm != null ? bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
//
//                // Storage
//                android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
//                long freeGB = stat.getAvailableBytes() / (1024 * 1024 * 1024);
//                long totalGB = stat.getTotalBytes() / (1024 * 1024 * 1024);
//
//                String info = String.format("Battery:%d|Storage:%dGB/%dGB|Model:%s|Android:%s",
//                        battery, freeGB, totalGB,
//                        Build.MODEL, Build.VERSION.RELEASE);
//
//                sendToPC("PHONE_INFO:" + info);
//            } catch (Exception e) {
//                Log.e(TAG, "Info gather error: " + e.getMessage());
//            }
//        }).start();
//    }
//
//    // ─── SEND TO PC ────────────────────────────────────────────
//    private void sendToPC(String message) {
//        if (serverIp == null) return;
//        new Thread(() -> {
//            try {
//                DatagramSocket socket = new DatagramSocket();
//                byte[] buf = message.getBytes();
//                InetAddress address = InetAddress.getByName(serverIp);
//                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, RESPONSE_PORT);
//                socket.send(packet);
//                socket.close();
//            } catch (Exception e) {
//                // Silently fail for heartbeats
//            }
//        }).start();
//    }
//}
