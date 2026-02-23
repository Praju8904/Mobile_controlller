package com.prajwal.myfirstapp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;
import android.os.Environment;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;   // To listen for the laptop's connection
import java.io.IOException;     // To handle network errors
import android.util.Log;        // For debugging in Logcat

import java.io.DataInputStream;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Formatter;

public class ConnectionManager {

    private String laptopIp;
    private static final int PORT_COMMAND = 5005;
    private static final int PORT_FILE = 5006;
    private static final int PORT_WATCHDOG = 5007;
    private static final int PORT_PING = 5008;
    private static final String HMAC_KEY = "my_super_secret_project_key";


    public interface PingCallback {
        void onSuccess(long responseTime);
        void onFailure();
    }

    public interface DiscoveryCallback {
        void onServersFound(java.util.List<String> serverIPs);
    }

    public static class ServerInfo {
        public String ipAddress;
        public String hostname;

        public ServerInfo(String ip, String hostname) {
            this.ipAddress = ip;
            this.hostname = hostname;
        }
    }

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
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
                String processedCommand = command;

                // 1. Check the Toggle
                if (SecurityUtils.USE_ENCRYPTION) {
                    processedCommand = SecurityUtils.encryptAES(command);
                }

                // 2. Prepare the message for signing
                String messageToSign = processedCommand + "|" + timestamp;

                // 3. Generate HMAC Signature
                String signature = SecurityUtils.calculateHMAC(messageToSign);

                // 4. Final Packet: ENCRYPTED_CMD|TIMESTAMP|SIGNATURE
                String finalPacket = messageToSign + "|" + signature;

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

    public void startFileReceiver(Context context) {
        new Thread(() -> {
            Log.i("RE_SYSTEM", "--- STARTING FILE RECEIVER THREAD ---");

            // Use Public Documents folder so you can see the files easily
            File docFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File receivedFolder = new File(docFolder, "Received_Files");
            if (!receivedFolder.exists()) receivedFolder.mkdirs();

            try (ServerSocket serverSocket = new ServerSocket(5006)) {
                while (true) {
                    try (Socket socket = serverSocket.accept()) {
                        InputStream is = socket.getInputStream();

                        // 1. Read the Header (Byte by byte until newline '\n')
                        // We can't use BufferedReader because it might steal file bytes!
                        StringBuilder headerBuilder = new StringBuilder();
                        int b;
                        while ((b = is.read()) != -1) {
                            if (b == '\n') break; // End of header found
                            headerBuilder.append((char) b);
                        }

                        String header = headerBuilder.toString();
                        if (!header.contains("|")) continue; // Invalid header

                        // 2. Parse Filename and Size
                        String[] parts = header.split("\\|");
                        String filename = parts[0];
                        long filesize = Long.parseLong(parts[1]);

                        Log.i("RE_SYSTEM", "Incoming File: " + filename + " Size: " + filesize);

                        // 3. Create the file with the REAL name
                        File targetFile = new File(receivedFolder, filename);

                        // 4. Write the Data
                        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalRead = 0;

                            // Read until we get the full file size
                            while (totalRead < filesize && (bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                totalRead += bytesRead;
                            }
                            Log.i("RE_SYSTEM", "SAVED: " + targetFile.getAbsolutePath());

                            // 👇 ADD THIS BLOCK TO NOTIFY UI 👇
                            // 1. Tell MediaStore (so it shows in Gallery)
                            android.media.MediaScannerConnection.scanFile(context,
                                    new String[]{targetFile.toString()}, null, null);

                            // 2. Broadcast to Chat Activity (So bubble appears)
                            android.content.Intent intent = new android.content.Intent("com.prajwal.myfirstapp.CHAT_EVENT");
                            intent.putExtra("type", "file");
                            intent.putExtra("content", targetFile.getAbsolutePath()); // Pass full path
                            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                            // 👆 END ADDITION

                            // Optional: Tell the MediaScanner about the new file so it appears in Gallery immediately
                            android.media.MediaScannerConnection.scanFile(context,
                                    new String[]{targetFile.toString()}, null, null);
                        }
                    } catch (Exception e) {
                        Log.e("RE_SYSTEM", "Transfer Error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void startListening(Context context) {
        new Thread(() -> {
            Log.d("ConnectionManager", "--- STARTING UDP LISTENER ON PORT 6000 ---");
            DatagramSocket socket = null;
            try {
                // Critical: This matches the port in reverse_commands.py (_phone_port = 6000)
                socket = new DatagramSocket(6000);
                byte[] buffer = new byte[65535]; // Max UDP size

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Blocks until data arrives

                    String message = new String(packet.getData(), 0, packet.getLength()).trim();
                    Log.d("ConnectionManager", "Received from PC: " + message);

                    // --- 1. HANDLE CHAT MESSAGES ---
                    if (message.startsWith("CHAT_MSG:")) {
                        String chatContent = message.substring(9); // Remove prefix

                        // Broadcast to ChatActivity
                        Intent intent = new Intent("com.prajwal.myfirstapp.CHAT_EVENT");
                        intent.putExtra("type", "text");
                        intent.putExtra("content", chatContent);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

                        // Optional: Save to repository here if you want it saved even when app is closed
                    }

                    // --- 2. HANDLE TOASTS (Example) ---
                    else if (message.startsWith("TOAST:")) {
                        String msg = message.substring(6);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        );
                    }

                    // --- 3. HANDLE NOTIFICATIONS ---
                    else if (message.startsWith("NOTIFY:")) {
                        // You can add notification logic here later
                    }
                }
            } catch (Exception e) {
                Log.e("ConnectionManager", "Listener Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
            }
        }).start();
    }

    public void wakeUpWatchdog() {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                byte[] buf = "START_MAIN_SERVER".getBytes(); // Matches your watchdog.py command
                InetAddress address = InetAddress.getByName(this.laptopIp);

                // IMPORTANT: Sending to 5007 (Watchdog Port)
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 5007);
                socket.send(packet);
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    public void testConnection(PingCallback callback) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(2000); // 2 second timeout

                // Send PING
                String message = "PING";
                byte[] sendBuf = message.getBytes();
                InetAddress address = InetAddress.getByName(laptopIp);
                DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, address, PORT_COMMAND);
                socket.send(sendPacket);

                // Wait for PONG response
                byte[] receiveBuf = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                if (response.equals("PONG")) {
                    long responseTime = System.currentTimeMillis() - startTime;
                    socket.close();
                    if (callback != null) callback.onSuccess(responseTime);
                } else {
                    socket.close();
                    if (callback != null) callback.onFailure();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) callback.onFailure();
            }
        }).start();
    }

    public void toggleServerState(boolean isRunning, Runnable onSuccess) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                // Send to watchdog server on port 5007
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

    //

    // 1. Add this Interface inside ConnectionManager class
    public interface FileListCallback {
        void onReceived(String fileList);
        void onError();
    }

    // 2. Add this Function inside ConnectionManager class
    public void fetchLaptopFiles(FileListCallback callback) {
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(3000); // Wait up to 3 seconds for reply

                // --- A. Prepare the Secure Command ---
                String command = "REQUEST_FILE_LIST";
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
                String processedCommand = command;

                // Apply Encryption if enabled
                if (SecurityUtils.USE_ENCRYPTION) {
                    processedCommand = SecurityUtils.encryptAES(command);
                }

                // Sign the message
                String messageToSign = processedCommand + "|" + timestamp;
                String signature = SecurityUtils.calculateHMAC(messageToSign);
                String finalPacket = messageToSign + "|" + signature;

                // --- B. Send to Laptop ---
                InetAddress serverAddr = InetAddress.getByName(laptopIp);
                byte[] buf = finalPacket.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, PORT_COMMAND);
                socket.send(packet);

                // --- C. Listen for the List ---
                byte[] recvBuf = new byte[8192]; // Big buffer for long file lists
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(recvPacket);

                String response = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();

                // Check if it's the file list
                if (response.startsWith("FILE_LIST:")) {
                    String listData = response.substring(10); // Remove "FILE_LIST:" prefix
                    if (callback != null) callback.onReceived(listData);
                }

                socket.close();

            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) callback.onError();
            }
        }).start();
    }

    public void discoverServers(DiscoveryCallback callback) {
        new Thread(() -> {
            java.util.Set<String> discoveredIPs = new java.util.HashSet<>();
            DatagramSocket listenSocket = null;
            DatagramSocket sendSocket = null;

            try {
                // Create listening socket on port 37020
                listenSocket = new DatagramSocket(null);
                listenSocket.setReuseAddress(true);
                listenSocket.bind(new java.net.InetSocketAddress(37020));
                listenSocket.setSoTimeout(500); // Short timeout for frequent checks

                // Send broadcast discovery
                sendSocket = new DatagramSocket();
                sendSocket.setBroadcast(true);
                byte[] sendBuf = "DISCOVERY_REQUEST".getBytes();
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, broadcast, PORT_WATCHDOG);
                sendSocket.send(sendPacket);
                sendSocket.close();

                System.out.println("Discovery broadcast sent, listening on port 37020...");

                // Listen for 3 seconds total
                long endTime = System.currentTimeMillis() + 3000;
                byte[] receiveBuf = new byte[1024];

                while (System.currentTimeMillis() < endTime) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                        listenSocket.receive(receivePacket);

                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                        String senderIP = receivePacket.getAddress().getHostAddress();

                        System.out.println("Received: '" + response + "' from " + senderIP);

                        if (response.equals("LAPTOP_IP_FOUND") || response.equals("LAPTOP_SERVER_ACTIVE")) {
                            discoveredIPs.add(senderIP);
                            System.out.println("Added server: " + senderIP);
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        // Short timeout, continue loop
                    }
                }

                listenSocket.close();

                System.out.println("Discovery complete. Found " + discoveredIPs.size() + " server(s)");

                java.util.List<String> servers = new java.util.ArrayList<>(discoveredIPs);
                if (callback != null) callback.onServersFound(servers);

            } catch (Exception e) {
                System.err.println("Discovery error: " + e.getMessage());
                e.printStackTrace();
                if (listenSocket != null && !listenSocket.isClosed()) listenSocket.close();
                if (sendSocket != null && !sendSocket.isClosed()) sendSocket.close();
                if (callback != null) callback.onServersFound(new java.util.ArrayList<>());
            }
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

    private String calculateHMAC(String data) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(HMAC_KEY.getBytes("UTF-8"), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));

        // Convert bytes to Hex string
        Formatter formatter = new Formatter();
        for (byte b : rawHmac) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}