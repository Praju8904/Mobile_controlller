package com.prajwal.myfirstapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Foreground service placeholder for data-sync foreground service.
 * Referenced in AndroidManifest.xml with foregroundServiceType="dataSync".
 */
public class ConnectionForegroundService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
