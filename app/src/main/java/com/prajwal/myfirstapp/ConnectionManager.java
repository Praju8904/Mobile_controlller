package com.prajwal.myfirstapp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class ConnectionManager {

    private String laptopIp;
    private static final int PORT_COMMAND = 5005;
    private static final int PORT_FILE = 5006;
    private static final int PORT_WATCHDOG = 5007;

    public ConnectionManager(String initialIp) {
        this.laptopIp = initialIp;
    }

    public void setLaptopIp(String ip) {
        this.laptopIp = ip;
    }

    public String getLaptopIp() {
        return laptopIp;
    }

    public void sendCommand(final String command) {
        new Thread(() -> {
            try {
                DatagramSocket udpSocket = new DatagramSocket();
                InetAddress serverAddr = InetAddress.getByName(laptopIp);
                byte[] buf = command.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, PORT_COMMAND);
                udpSocket.send(packet);
                udpSocket.close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void toggleServerState(boolean isRunning, Runnable onSuccess) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                String message = isRunning ? "STOP_MAIN_SERVER" : "START_MAIN_SERVER";
                byte[] buf = message.getBytes();
                InetAddress address = InetAddress.getByName(laptopIp);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, PORT_WATCHDOG);
                socket.send(packet);
                socket.close();
                if (onSuccess != null) onSuccess.run();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void sendFileToLaptop(Context context, Uri uri, Runnable onStart, Runnable onComplete, Runnable onError) {
        new Thread(() -> {
            try {
                if (onStart != null) onStart.run();
                
                try (Socket socket = new Socket(laptopIp, PORT_FILE);
                     OutputStream output = socket.getOutputStream();
                     InputStream input = context.getContentResolver().openInputStream(uri)) {

                    socket.setSoTimeout(10000);

                    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                        String name = (nameIdx >= 0) ? cursor.getString(nameIdx) : "file";
                        long size = (sizeIdx >= 0) ? cursor.getLong(sizeIdx) : 0;
                        cursor.close();

                        String header = name + "|" + size + "\n";
                        output.write(header.getBytes("UTF-8"));
                        output.flush();

                        Thread.sleep(200);

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                        }
                        output.flush();
                        if (onComplete != null) onComplete.run();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onError != null) onError.run();
            }
        }).start();
    }
}
