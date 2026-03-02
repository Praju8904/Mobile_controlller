package com.prajwal.myfirstapp.connectivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class BackgroundServices {

    private boolean isPreviewOn = false;
    private final int DISCOVERY_PORT = 37020;
    private final int STATUS_PORT = 37021;
    private final int PREVIEW_PORT = 37022;

    public interface StatusCallback {

        void onStatusUpdate(String batteryText, boolean isPlugged);
    }

    public interface PreviewCallback {

        void onImageReceived(Bitmap bitmap);
    }

    public interface DiscoveryCallback {

        void onServerFound();
    }

    public void setPreviewEnabled(boolean enabled) {
        this.isPreviewOn = enabled;
    }

    public void startAutoDiscovery(DiscoveryCallback callback) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(true); // Allow port sharing
                socket.bind(new java.net.InetSocketAddress(DISCOVERY_PORT));
                byte[] inBuf = new byte[1024];

                while (true) {
                    try {
                        DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
                        socket.receive(inPacket);
                        String reply = new String(inPacket.getData(), 0, inPacket.getLength()).trim();
                        if (reply.equals("LAPTOP_IP_FOUND") || reply.equals("LAPTOP_SERVER_ACTIVE")) {
                            if (callback != null) {
                                callback.onServerFound();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore timeouts
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
//public void startAutoDiscovery(DiscoveryCallback callback) {
//    new Thread(() -> {
//        try {
//            DatagramSocket socket = new DatagramSocket();
//            socket.setBroadcast(true); // Enable broadcasting
//            socket.setSoTimeout(2000); // Don't wait forever for a reply
//
//            // 1. SEND the discovery request to the whole network
//            String message = "DISCOVERY_REQUEST";
//            byte[] sendBuf = message.getBytes();
//            DatagramPacket sendPacket = new DatagramPacket(
//                    sendBuf, sendBuf.length,
//                    InetAddress.getByName("255.255.255.255"), 5007 // Laptop's port
//            );
//            socket.send(sendPacket);
//            Log.d("Discovery", "Sent request to network...");
//
//            // 2. LISTEN for the reply
//            byte[] inBuf = new byte[1024];
//            while (true) {
//                try {
//                    DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
//                    socket.receive(inPacket); // Waits here for "LAPTOP_IP_FOUND"
//
//                    String reply = new String(inPacket.getData(), 0, inPacket.getLength()).trim();
//                    if (reply.equals("LAPTOP_IP_FOUND")) {
//                        if (callback != null) {
//                            // Run on UI Thread if needed
//                            callback.onServerFound();
//                        }
//                        break; // Stop looking once found
//                    }
//                } catch (SocketTimeoutException e) {
//                    // If no reply, re-send the request or exit
//                    socket.send(sendPacket);
//                }
//            }
//            socket.close();
//        } catch (Exception e) { e.printStackTrace(); }
//    }).start();
//}

    public void startStatusListener(StatusCallback callback) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(STATUS_PORT);
                byte[] buf = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength());
                    if (data.startsWith("STATUS:")) {
                        String[] parts = data.split(":")[1].split("\\|");
                        String battery = parts[0] + "%";
                        boolean plugged = parts[1].equals("1");
                        if (callback != null) {
                            callback.onStatusUpdate(battery, plugged);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void startPreviewListener(PreviewCallback callback) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(PREVIEW_PORT);
                byte[] buf = new byte[64000];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    if (isPreviewOn) {
                        String rawData = new String(packet.getData(), 0, packet.getLength());
                        if (rawData.startsWith("IMG:")) {
                            try {
                                byte[] imageBytes = Base64.decode(rawData.substring(4), Base64.DEFAULT);
                                Bitmap decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                if (decodedImage != null && callback != null) {
                                    callback.onImageReceived(decodedImage);
                                }
                            } catch (Exception e) {
                                Log.e("Preview", "Decode Error", e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
