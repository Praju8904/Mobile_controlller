package com.prajwal.myfirstapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Dynamic Bar Service — Creates a floating "Dynamic Island" style overlay
 * that sits on top of all apps. Provides quick access to:
 *   - Media controls (play/pause, next, prev)
 *   - Volume controls (up, down, mute)
 *   - Quick actions (show desktop, screenshot, app switcher, lock PC)
 *
 * Uses SYSTEM_ALERT_WINDOW permission for the overlay.
 * Communicates with the PC via UDP commands (mirrors ConnectionManager).
 */
public class DynamicBarService extends Service {

    // ── Window Manager & Views ──
    private WindowManager windowManager;
    private View collapsedView, expandedView;
    private WindowManager.LayoutParams collapsedParams, expandedParams;
    private boolean isExpanded = false;

    // ── Connection Info ──
    private String laptopIp;
    private boolean isConnected = false;
    private static final int PORT_COMMAND = 5005;

    // ── Notification ──
    private static final String CHANNEL_ID = "dynamic_bar_channel";
    private static final int NOTIFICATION_ID = 2001;

    // ── Drag Tracking ──
    private float initialTouchX, initialTouchY;
    private int initialX, initialY;

    // ── Singleton for status updates from MainActivity ──
    private static DynamicBarService instance;
    private static boolean isRunning = false;

    public static DynamicBarService getInstance() {
        return instance;
    }

    public static boolean isServiceRunning() {
        return isRunning;
    }

    // ═══════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isRunning = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Check for stop action
            String action = intent.getAction();
            if ("STOP_DYNAMIC_BAR".equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            }

            // Extract connection data
            laptopIp = intent.getStringExtra("laptop_ip");
            isConnected = intent.getBooleanExtra("is_connected", false);

            // Check for update-only action (don't recreate views)
            if ("UPDATE_STATUS".equals(action)) {
                refreshUI();
                return START_STICKY;
            }
        }

        // Create notification channel and start foreground
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        // Setup the floating views
        setupViews();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isRunning = false;
        try {
            if (collapsedView != null && collapsedView.isAttachedToWindow())
                windowManager.removeView(collapsedView);
            if (expandedView != null && expandedView.isAttachedToWindow())
                windowManager.removeView(expandedView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ═══════════════════════════════════════════════════
    // NOTIFICATION (Required for Foreground Service)
    // ═══════════════════════════════════════════════════

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Dynamic Bar",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Floating PC control bar");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // Tap notification → open main app
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingMain = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Stop action
        Intent stopIntent = new Intent(this, DynamicBarService.class);
        stopIntent.setAction("STOP_DYNAMIC_BAR");
        PendingIntent pendingStop = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String statusText = isConnected
                ? "Connected to " + (laptopIp != null ? laptopIp : "...")
                : "Not connected";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Dynamic Bar Active")
                .setContentText(statusText + " • Tap pill to expand")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingMain)
                .addAction(android.R.drawable.ic_delete, "Stop Bar", pendingStop)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    // ═══════════════════════════════════════════════════
    // VIEW SETUP
    // ═══════════════════════════════════════════════════

    private void setupViews() {
        // Don't create duplicate views
        if (collapsedView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        // ── Collapsed Pill ──
        collapsedView = inflater.inflate(R.layout.layout_dynamic_bar_collapsed, null);
        collapsedParams = createLayoutParams(190, 38);
        collapsedParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        collapsedParams.y = dpToPx(20); // Just below status bar

        // ── Expanded Panel ──
        expandedView = inflater.inflate(R.layout.layout_dynamic_bar_expanded, null);
        expandedParams = createLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        expandedParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        expandedParams.y = dpToPx(20);

        // Add collapsed pill to screen
        windowManager.addView(collapsedView, collapsedParams);

        // Wire up all interactions
        setupCollapsedTouch();
        setupExpandedButtons();
        updateCollapsedUI();
    }

    private WindowManager.LayoutParams createLayoutParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int w = (width == WindowManager.LayoutParams.WRAP_CONTENT) ? width : dpToPx(width);
        int h = (height == WindowManager.LayoutParams.WRAP_CONTENT) ? height : dpToPx(height);

        return new WindowManager.LayoutParams(
                w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    // ═══════════════════════════════════════════════════
    // COLLAPSED PILL — Touch (Tap to expand, Drag to move)
    // ═══════════════════════════════════════════════════

    private void setupCollapsedTouch() {
        collapsedView.setOnTouchListener(new View.OnTouchListener() {
            private long touchStartTime;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        initialX = collapsedParams.x;
                        initialY = collapsedParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                            collapsedParams.x = initialX + (int) dx;
                            collapsedParams.y = initialY + (int) dy;
                            try {
                                windowManager.updateViewLayout(collapsedView, collapsedParams);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long elapsed = System.currentTimeMillis() - touchStartTime;
                        if (!isDragging && elapsed < 250) {
                            // Quick tap → expand
                            expandBar();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // ═══════════════════════════════════════════════════
    // EXPAND / COLLAPSE ANIMATIONS
    // ═══════════════════════════════════════════════════

    private void expandBar() {
        if (isExpanded) return;
        isExpanded = true;

        // Position expanded view where the pill was
        expandedParams.x = collapsedParams.x;
        expandedParams.y = collapsedParams.y;

        // Swap views
        try {
            windowManager.removeView(collapsedView);
        } catch (Exception ignored) {}

        // CRITICAL: Remove FLAG_NOT_FOCUSABLE so buttons can receive clicks
        expandedParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        expandedParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        windowManager.addView(expandedView, expandedParams);

        // Morph-in animation (scale + fade)
        AnimationSet animIn = new AnimationSet(true);
        ScaleAnimation scaleIn = new ScaleAnimation(
                0.4f, 1f, 0.2f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0f
        );
        scaleIn.setDuration(280);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(200);

        animIn.addAnimation(scaleIn);
        animIn.addAnimation(fadeIn);
        animIn.setFillAfter(true);
        expandedView.startAnimation(animIn);

        updateExpandedUI();
    }

    private void collapseBar() {
        if (!isExpanded) return;
        isExpanded = false;

        // Morph-out animation
        AnimationSet animOut = new AnimationSet(true);
        ScaleAnimation scaleOut = new ScaleAnimation(
                1f, 0.4f, 1f, 0.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0f
        );
        scaleOut.setDuration(220);

        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(180);

        animOut.addAnimation(scaleOut);
        animOut.addAnimation(fadeOut);
        animOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                try {
                    windowManager.removeView(expandedView);
                } catch (Exception ignored) {}

                // Restore pill at same position (with FLAG_NOT_FOCUSABLE for passthrough)
                collapsedParams.x = expandedParams.x;
                collapsedParams.y = expandedParams.y;
                collapsedParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                windowManager.addView(collapsedView, collapsedParams);
                updateCollapsedUI();
            }
        });
        expandedView.startAnimation(animOut);
    }

    // ═══════════════════════════════════════════════════
    // EXPANDED PANEL — Button Handlers
    // ═══════════════════════════════════════════════════

    private void setupExpandedButtons() {
        // ── Close / Collapse ──
        expandedView.findViewById(R.id.dbClose).setOnClickListener(v -> collapseBar());

        // ── Media Controls ──
        expandedView.findViewById(R.id.dbBtnPrev).setOnClickListener(v -> {
            sendCommand("MEDIA_PREV");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnPlayPause).setOnClickListener(v -> {
            sendCommand("MEDIA_PLAY_PAUSE");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnNext).setOnClickListener(v -> {
            sendCommand("MEDIA_NEXT");
            flashButton(v);
        });

        // ── Volume ──
        expandedView.findViewById(R.id.dbBtnVolDown).setOnClickListener(v -> {
            sendCommand("VOL_DOWN");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnMute).setOnClickListener(v -> {
            sendCommand("MUTE_TOGGLE");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnVolUp).setOnClickListener(v -> {
            sendCommand("VOL_UP");
            flashButton(v);
        });

        // ── Quick Actions ──
        expandedView.findViewById(R.id.dbBtnDesktop).setOnClickListener(v -> {
            sendCommand("SHOW_DESKTOP");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnScreenshot).setOnClickListener(v -> {
            sendCommand("SCREENSHOT_LOCAL");
            Toast.makeText(this, "📸 Screenshot saved on PC", Toast.LENGTH_SHORT).show();
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnAppSwitch).setOnClickListener(v -> {
            sendCommand("APP_SWITCHER");
            flashButton(v);
        });
        expandedView.findViewById(R.id.dbBtnLock).setOnClickListener(v -> {
            sendCommand("LOCK_PC");
            Toast.makeText(this, "🔒 PC Locked", Toast.LENGTH_SHORT).show();
            flashButton(v);
            collapseBar();
        });

        // ── Drag support on expanded view header area ──
        expandedView.findViewById(R.id.dbExpandedStatus).setOnTouchListener(new View.OnTouchListener() {
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = false;
                        initialX = expandedParams.x;
                        initialY = expandedParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true;
                            expandedParams.x = initialX + (int) dx;
                            expandedParams.y = initialY + (int) dy;
                            try {
                                windowManager.updateViewLayout(expandedView, expandedParams);
                            } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return isDragging; // consume if dragged
                }
                return false;
            }
        });
    }

    /** Visual feedback: quick flash on button press */
    private void flashButton(View v) {
        AlphaAnimation flash = new AlphaAnimation(1f, 0.4f);
        flash.setDuration(100);
        flash.setRepeatMode(Animation.REVERSE);
        flash.setRepeatCount(1);
        v.startAnimation(flash);
    }

    // ═══════════════════════════════════════════════════
    // UI UPDATES
    // ═══════════════════════════════════════════════════

    private void updateCollapsedUI() {
        if (collapsedView == null) return;

        // Connection dot
        View dot = collapsedView.findViewById(R.id.dbConnectionDot);
        setDotColor(dot, isConnected);

        // Status text
        TextView statusText = collapsedView.findViewById(R.id.dbStatusText);
        statusText.setText(isConnected ? "🎵 Connected" : "⚠ Offline");

        // Mini action icon
        TextView miniAction = collapsedView.findViewById(R.id.dbMiniAction);
        miniAction.setText(isConnected ? "▶" : "•");
    }

    private void updateExpandedUI() {
        if (expandedView == null) return;

        // Connection dot
        View dot = expandedView.findViewById(R.id.dbExpandedDot);
        setDotColor(dot, isConnected);

        // Status text
        TextView status = expandedView.findViewById(R.id.dbExpandedStatus);
        if (isConnected && laptopIp != null) {
            status.setText("Connected to " + laptopIp);
        } else {
            status.setText("Disconnected — tap Connect in app");
        }
    }

    private void refreshUI() {
        if (!isExpanded) {
            if (collapsedView != null && collapsedView.isAttachedToWindow()) {
                updateCollapsedUI();
            }
        } else {
            if (expandedView != null && expandedView.isAttachedToWindow()) {
                updateExpandedUI();
            }
        }
    }

    private void setDotColor(View dot, boolean connected) {
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(connected
                ? Color.parseColor("#66BB6A")   // Green
                : Color.parseColor("#EF5350")); // Red
        dotBg.setSize(dpToPx(8), dpToPx(8));
        dot.setBackground(dotBg);
    }

    // ═══════════════════════════════════════════════════
    // PUBLIC — Called from MainActivity to update state
    // ═══════════════════════════════════════════════════

    public void updateConnectionStatus(boolean connected, String ip) {
        this.isConnected = connected;
        this.laptopIp = ip;
        refreshUI();
    }

    // ═══════════════════════════════════════════════════
    // UDP COMMAND SENDER (mirrors ConnectionManager logic)
    // ═══════════════════════════════════════════════════

    private void sendCommand(String command) {
        if (laptopIp == null || laptopIp.isEmpty()) {
            Toast.makeText(this, "No server connected", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
                String processedCommand = command;

                // Encrypt if enabled
                if (SecurityUtils.USE_ENCRYPTION) {
                    processedCommand = SecurityUtils.encryptAES(command);
                }

                // Sign the packet
                String messageToSign = processedCommand + "|" + timestamp;
                String signature = SecurityUtils.calculateHMAC(messageToSign);
                String finalPacket = messageToSign + "|" + signature;

                // Send via UDP
                DatagramSocket udpSocket = new DatagramSocket();
                InetAddress serverAddr = InetAddress.getByName(laptopIp);
                byte[] buf = finalPacket.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, PORT_COMMAND);
                udpSocket.send(packet);
                udpSocket.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
