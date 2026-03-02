package com.prajwal.myfirstapp.vault;


import com.prajwal.myfirstapp.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Secure video player with custom controls overlay.
 */
public class VaultVideoPlayerActivity extends Activity {

    private static final String EXTRA_FILE_ID = "file_id";
    private static final String EXTRA_LIST_TYPE = "file_list_type";
    private static final long HIDE_DELAY_MS = 3000;

    private MediaVaultRepository repo;
    private VaultFileItem currentFile;
    private List<VaultFileItem> fileList = new ArrayList<>();
    private int currentIndex = 0;

    private VideoView videoView;
    private FrameLayout controlsOverlay;
    private LinearLayout topBar, bottomControls;
    private TextView tvFileName, btnBack, btnMore, btnPlayPause;
    private TextView tvCurrentTime, tvTotalTime;
    private SeekBar seekBar, volumeSeekBar;

    private AudioManager audioManager;
    private boolean isLooping = false;
    private float playbackSpeed = 1.0f;
    private boolean controlsVisible = true;
    private float touchStartY;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable seekUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateSeekBar();
            handler.postDelayed(this, 500);
        }
    };

    private GestureDetector gestureDetector;
    private File tempFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        setContentView(R.layout.activity_vault_video_player);
        setImmersive();

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        videoView = findViewById(R.id.videoView);
        controlsOverlay = findViewById(R.id.controlsOverlay);
        topBar = findViewById(R.id.topBar);
        bottomControls = findViewById(R.id.bottomControls);
        tvFileName = findViewById(R.id.tvFileName);
        btnBack = findViewById(R.id.btnBack);
        btnMore = findViewById(R.id.btnMore);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBar = findViewById(R.id.seekBar);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        String listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
        if (fileId == null) { finish(); return; }

        buildFileList(listType);
        currentIndex = indexOfId(fileId);
        if (currentIndex < 0) currentIndex = 0;

        gestureDetector = new GestureDetector(this, new VideoGestureListener());
        setupVolumeBar();
        setupClickListeners();
        loadCurrentFile();
        scheduleHideControls();
    }

    private void buildFileList(String listType) {
        if (listType == null || listType.equals("all")) {
            fileList = repo.getAllFiles();
        } else if (listType.equals("video")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.VIDEO);
        } else {
            fileList = repo.getFilesByAlbum(listType);
        }
        List<VaultFileItem> videos = new ArrayList<>();
        for (VaultFileItem f : fileList) {
            if (f.fileType == VaultFileItem.FileType.VIDEO) videos.add(f);
        }
        if (!videos.isEmpty()) fileList = videos;
    }

    private int indexOfId(String id) {
        for (int i = 0; i < fileList.size(); i++) {
            if (fileList.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void loadCurrentFile() {
        if (fileList.isEmpty()) { finish(); return; }
        currentFile = fileList.get(currentIndex);
        tvFileName.setText(currentFile.originalFileName);
        repo.logFileViewed(currentFile);
        deleteTempFile();

        new Thread(() -> {
            try {
                tempFile = File.createTempFile("vault_vid_", ".tmp", getCacheDir());
                boolean ok = repo.exportFile(currentFile, tempFile);
                if (!ok) throw new Exception("export failed");
                runOnUiThread(this::startPlayback);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startPlayback() {
        videoView.setVideoPath(tempFile.getAbsolutePath());
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(isLooping);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.media.PlaybackParams pp = new android.media.PlaybackParams();
                    pp.setSpeed(playbackSpeed);
                    mp.setPlaybackParams(pp);
                }
            } catch (Exception ignored) {}
            int dur = videoView.getDuration();
            seekBar.setMax(dur > 0 ? dur : 100);
            tvTotalTime.setText(formatTime(dur));
            videoView.start();
            btnPlayPause.setText("⏸");
            handler.post(seekUpdateRunnable);
        });
        videoView.setOnCompletionListener(mp -> {
            btnPlayPause.setText("▶");
            handler.removeCallbacks(seekUpdateRunnable);
        });
        videoView.requestFocus();
    }

    private void setupVolumeBar() {
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeSeekBar.setMax(maxVol);
        volumeSeekBar.setProgress(curVol);
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> showMoreMenu());
        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) videoView.seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                handler.removeCallbacks(hideControlsRunnable);
            }
            @Override public void onStopTrackingTouch(SeekBar s) { scheduleHideControls(); }
        });

        controlsOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchStartY = event.getY();
                toggleControls();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - touchStartY;
                if (dy > 250 && !videoView.isPlaying()) {
                    finish();
                }
            }
            return true;
        });
    }

    private class VideoGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float x = e.getX();
            float mid = controlsOverlay.getWidth() / 2f;
            if (x < mid) {
                // seek back 10s
                videoView.seekTo(Math.max(0, videoView.getCurrentPosition() - 10000));
                Toast.makeText(VaultVideoPlayerActivity.this, "−10s", Toast.LENGTH_SHORT).show();
            } else {
                // seek forward 10s
                int dur = videoView.getDuration();
                videoView.seekTo(Math.min(dur, videoView.getCurrentPosition() + 10000));
                Toast.makeText(VaultVideoPlayerActivity.this, "+10s", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }

    private void togglePlayPause() {
        if (videoView.isPlaying()) {
            videoView.pause();
            btnPlayPause.setText("▶");
            handler.removeCallbacks(seekUpdateRunnable);
        } else {
            videoView.start();
            btnPlayPause.setText("⏸");
            handler.post(seekUpdateRunnable);
        }
        scheduleHideControls();
    }

    private void updateSeekBar() {
        if (videoView.isPlaying()) {
            int pos = videoView.getCurrentPosition();
            seekBar.setProgress(pos);
            tvCurrentTime.setText(formatTime(pos));
        }
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
            scheduleHideControls();
        }
    }

    private void showControls() {
        topBar.setVisibility(View.VISIBLE);
        bottomControls.setVisibility(View.VISIBLE);
        btnPlayPause.setVisibility(View.VISIBLE);
        controlsVisible = true;
    }

    private void hideControls() {
        topBar.setVisibility(View.GONE);
        bottomControls.setVisibility(View.GONE);
        btnPlayPause.setVisibility(View.GONE);
        controlsVisible = false;
        setImmersive();
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, HIDE_DELAY_MS);
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void showMoreMenu() {
        String favLabel = (currentFile != null && currentFile.isFavourited)
                ? "Remove from Favourites" : "Add to Favourites";
        String loopLabel = isLooping ? "Loop: ON" : "Loop: OFF";
        String[] items = {loopLabel, "Speed", favLabel, "Export to Downloads", "Share", "Info", "Delete"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: toggleLoop(); break;
                        case 1: showSpeedPicker(); break;
                        case 2: toggleFavourite(); break;
                        case 3: exportToDownloads(); break;
                        case 4: shareFile(); break;
                        case 5: showInfoDialog(); break;
                        case 6: confirmDelete(); break;
                    }
                }).show();
    }

    private void toggleLoop() {
        isLooping = !isLooping;
        Toast.makeText(this, "Loop: " + (isLooping ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
    }

    private void showSpeedPicker() {
        String[] speeds = {"0.5×", "1.0×", "1.5×", "2.0×"};
        float[] vals = {0.5f, 1.0f, 1.5f, 2.0f};
        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds, (d, w) -> {
                    playbackSpeed = vals[w];
                    // VideoView does not expose MediaPlayer directly; speed is stored and
                    // applied when the video is next loaded (e.g., after rotation or file reload).
                    Toast.makeText(this, "Speed " + speeds[w] + " applies on next load",
                            Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void toggleFavourite() {
        if (currentFile == null) return;
        currentFile.isFavourited = !currentFile.isFavourited;
        repo.updateFile(currentFile);
        Toast.makeText(this, currentFile.isFavourited ? "Added to favourites" : "Removed from favourites",
                Toast.LENGTH_SHORT).show();
    }

    private void showInfoDialog() {
        if (currentFile == null) return;
        String date = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(new Date(currentFile.importedAt));
        String dims = (currentFile.width > 0 && currentFile.height > 0)
                ? currentFile.width + " × " + currentFile.height : "Unknown";
        String msg = "Name: " + currentFile.originalFileName
                + "\nSize: " + currentFile.getFormattedSize()
                + "\nDimensions: " + dims
                + "\nDuration: " + currentFile.getFormattedDuration()
                + "\nImported: " + date;
        new AlertDialog.Builder(this).setTitle("File Info")
                .setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void exportToDownloads() {
        if (currentFile == null) return;
        new Thread(() -> {
            try {
                File dest = new File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS),
                        currentFile.originalFileName);
                boolean ok = repo.exportFile(currentFile, dest);
                runOnUiThread(() -> Toast.makeText(this,
                        ok ? "Exported to Downloads" : "Export failed", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void shareFile() {
        if (currentFile == null) return;
        new Thread(() -> {
            try {
                File sf = File.createTempFile("share_", "_" + currentFile.originalFileName, getCacheDir());
                if (!repo.exportFile(currentFile, sf)) throw new Exception();
                runOnUiThread(() -> {
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", sf);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(currentFile.mimeType != null ? currentFile.mimeType : "video/*");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share via"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete \"" + currentFile.originalFileName + "\" permanently?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteFile(currentFile);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void deleteTempFile() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
            tempFile = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) videoView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        videoView.stopPlayback();
        deleteTempFile();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersive();
    }
}
