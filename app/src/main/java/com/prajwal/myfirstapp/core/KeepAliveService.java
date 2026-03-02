package com.prajwal.myfirstapp.core;


import com.prajwal.myfirstapp.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "ConnectionChannel";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Create the Notification Channel (Required for Android 8+)
        createNotificationChannel();

        // 2. Create the Intent to open the app when you click the notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // 3. Build the Notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Controller Active")
                .setContentText("Connected to Laptop...")
                .setSmallIcon(R.mipmap.ic_launcher) // Make sure this icon exists
                .setContentIntent(pendingIntent)
                .build();

        // 4. Start Foreground (This makes the app un-killable by the system)
        startForeground(1, notification);

        // If the system kills the service, restart it automatically
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Connection Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}