package com.prajwal.myfirstapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Camera Stream Service — captures frames from the phone camera
 * and sends them to the PC as JPEG via UDP.
 *
 * Protocol: Raw JPEG bytes sent to PC on port 37023.
 * The PC's services.py receive_camera_stream() decodes and displays them.
 *
 * Usage:
 *   CameraStreamService.start(context, serverIp);
 *   CameraStreamService.stop();
 */
public class CameraStreamService {

    private static final String TAG = "CameraStream";
    private static final int STREAM_PORT = 37023;
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int JPEG_QUALITY = 60;
    private static final int MAX_UDP_SIZE = 60000; // Safe UDP packet size

    private static CameraStreamService instance;

    private Context context;
    private String serverIp;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private DatagramSocket udpSocket;
    private InetAddress serverAddress;
    private boolean isStreaming = false;

    // Frame rate control
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL_MS = 66; // ~15 FPS

    // ─── PUBLIC API ─────────────────────────────────────────────

    public static void start(Context ctx, String ip) {
        if (instance != null && instance.isStreaming) {
            Log.w(TAG, "Already streaming");
            return;
        }
        instance = new CameraStreamService();
        instance.context = ctx;
        instance.serverIp = ip;
        instance.startStreaming();
    }

    public static void stop() {
        if (instance != null) {
            instance.stopStreaming();
            instance = null;
        }
    }

    public static boolean isActive() {
        return instance != null && instance.isStreaming;
    }

    // ─── CAMERA SETUP ───────────────────────────────────────────

    private void startStreaming() {
        Log.i(TAG, "Starting camera stream to " + serverIp + ":" + STREAM_PORT);

        // Check camera permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted!");
            return;
        }

        try {
            // Setup UDP socket
            udpSocket = new DatagramSocket();
            serverAddress = InetAddress.getByName(serverIp);

            // Start camera thread
            cameraThread = new HandlerThread("CameraStreamThread");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());

            // Open camera
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = getBackCameraId(manager);
            if (cameraId == null) {
                Log.e(TAG, "No back camera found!");
                return;
            }

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.i(TAG, "Camera opened");
                    cameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "Camera disconnected");
                    stopStreaming();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    stopStreaming();
                }
            }, cameraHandler);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera stream: " + e.getMessage());
        }
    }

    private void createCaptureSession() {
        try {
            // Create ImageReader for YUV frames
            imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT,
                    ImageFormat.YUV_420_888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;

                    // Frame rate limiting
                    long now = System.currentTimeMillis();
                    if (now - lastFrameTime < FRAME_INTERVAL_MS) {
                        return;
                    }
                    lastFrameTime = now;

                    // Convert YUV to JPEG and send
                    byte[] jpeg = yuvToJpeg(image);
                    if (jpeg != null && jpeg.length <= MAX_UDP_SIZE) {
                        sendFrame(jpeg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Frame processing error: " + e.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }, cameraHandler);

            Surface surface = imageReader.getSurface();

            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.i(TAG, "Capture session configured");
                            captureSession = session;
                            startRepeatingCapture(surface);
                            isStreaming = true;
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                            stopStreaming();
                        }
                    }, cameraHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access error: " + e.getMessage());
        }
    }

    private void startRepeatingCapture(Surface surface) {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);

            // Auto-focus
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            // Auto-exposure
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
            Log.i(TAG, "Streaming at ~15 FPS (" + PREVIEW_WIDTH + "x" + PREVIEW_HEIGHT + ")");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Repeating capture error: " + e.getMessage());
        }
    }

    // ─── FRAME CONVERSION ───────────────────────────────────────

    private byte[] yuvToJpeg(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            int width = image.getWidth();
            int height = image.getHeight();

            // Get Y, U, V planes
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            // NV21 format: Y + VU interleaved
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), JPEG_QUALITY, out);

            return out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "YUV to JPEG error: " + e.getMessage());
            return null;
        }
    }

    // ─── NETWORK ────────────────────────────────────────────────

    private void sendFrame(byte[] jpeg) {
        try {
            DatagramPacket packet = new DatagramPacket(
                    jpeg, jpeg.length, serverAddress, STREAM_PORT);
            udpSocket.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "UDP send error: " + e.getMessage());
        }
    }

    // ─── CLEANUP ────────────────────────────────────────────────

    private void stopStreaming() {
        Log.i(TAG, "Stopping camera stream");
        isStreaming = false;

        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
        } catch (Exception e) { /* ignore */ }

        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception e) { /* ignore */ }

        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) { /* ignore */ }

        try {
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (Exception e) { /* ignore */ }

        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }

        Log.i(TAG, "Camera stream stopped");
    }

    // ─── HELPERS ────────────────────────────────────────────────

    private String getBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        // Fallback to first camera
        String[] ids = manager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }
}
