package com.prajwal.myfirstapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothFileHelper {

    private static final String TAG = "BluetoothFileHelper";
    // Standard Serial Port Profile UUID — must match the Python server
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface DeviceListCallback {
        void onDevicesFound(List<BluetoothDevice> devices);
        void onError(String message);
    }

    public interface TransferCallback {
        void onStart();
        void onComplete();
        void onError(String message);
    }

    /**
     * Returns paired Bluetooth devices. Requires BLUETOOTH_CONNECT permission.
     */
    @SuppressLint("MissingPermission")
    public static void getPairedDevices(DeviceListCallback callback) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            callback.onError("Bluetooth not supported on this device");
            return;
        }
        if (!adapter.isEnabled()) {
            callback.onError("Bluetooth is disabled. Please enable it first.");
            return;
        }

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        List<BluetoothDevice> deviceList = new ArrayList<>(bonded);
        callback.onDevicesFound(deviceList);
    }

    /**
     * Send a file to the given Bluetooth device using the same protocol
     * as the Wi-Fi sharing service: header "filename|filesize\n" + raw bytes.
     */
    @SuppressLint("MissingPermission")
    public static void sendFile(Context context, BluetoothDevice device, Uri uri, TransferCallback callback) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (callback != null) callback.onStart();

                // Resolve file metadata
                String fileName = "file";
                long fileSize = 0;
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx);
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx);
                    cursor.close();
                }

                // Connect via RFCOMM
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                Log.i(TAG, "Connected to " + device.getName());

                OutputStream output = socket.getOutputStream();
                InputStream input = context.getContentResolver().openInputStream(uri);

                // Send header: filename|filesize\n
                String header = fileName + "|" + fileSize + "\n";
                output.write(header.getBytes("UTF-8"));
                output.flush();

                Thread.sleep(200); // brief pause for server to parse header

                // Stream file data
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
                input.close();

                Log.i(TAG, "File sent: " + fileName + " (" + fileSize + " bytes)");
                if (callback != null) callback.onComplete();

            } catch (Exception e) {
                Log.e(TAG, "Bluetooth transfer failed: " + e.getMessage(), e);
                if (callback != null) callback.onError(e.getMessage());
            } finally {
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }
}
