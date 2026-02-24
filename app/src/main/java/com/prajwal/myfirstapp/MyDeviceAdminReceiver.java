package com.prajwal.myfirstapp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Device Admin Receiver — required for lock screen functionality.
 * The user must enable this as Device Administrator in Settings.
 */
public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "DeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device Admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device Admin disabled");
    }
}
