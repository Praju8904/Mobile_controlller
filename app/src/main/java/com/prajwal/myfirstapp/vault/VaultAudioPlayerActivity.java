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
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Secure audio player with animated waveform.
 */
public class VaultAudioPlayerActivity extends Activity {

    private static final String EXTRA_FILE_ID = "file_id";
    private static final String EXTRA_LIST_TYPE = "file_list_type";

    private MediaVaultRepository repo;
    private VaultFileItem currentFile;
    private List<VaultFileItem> fileList = new ArrayList<>();
    private int currentIndex = 0;

    private VaultWaveformView waveformView;
    private TextView tvTrackTitle, tvArtist, tvCurrentTime, tvTotalTime;
    private TextView btnBack, btnMore, btnPlayPause, btnPrev, btnNext, btnShuffle, btnLoop, btnSpeed;
    private SeekBar seekBar, volumeSeekBar;

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private boolean isLooping = false;
    private boolean isShuffle = false;
    private float playbackSpeed = 1.0f;
    private File tempFile;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable seekUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                int pos = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(pos);
                tvCurrentTime.setText(formatTime(pos));
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        setContentView(R.layout.activity_vault_audio_player);

        repo = MediaVaultRepository.getInstance(this);
        if (!repo.isUnlocked()) { finish(); return; }

        waveformView = findViewById(R.id.waveformView);
        tvTrackTitle = findViewById(R.id.tvTrackTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnBack = findViewById(R.id.btnBack);
        btnMore = findViewById(R.id.btnMore);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnLoop = findViewById(R.id.btnLoop);
        btnSpeed = findViewById(R.id.btnSpeed);
        seekBar = findViewById(R.id.seekBar);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        String fileId = getIntent().getStringExtra(EXTRA_FILE_ID);
        String listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
        if (fileId == null) { finish(); return; }

        buildFileList(listType);
        currentIndex = indexOfId(fileId);
        if (currentIndex < 0) currentIndex = 0;

        setupVolumeBar();
        setupClickListeners();
        loadCurrentFile();
    }

    private void buildFileList(String listType) {
        if (listType == null || listType.equals("all")) {
            fileList = repo.getAllFiles();
        } else if (listType.equals("audio")) {
            fileList = repo.getFilesByType(VaultFileItem.FileType.AUDIO);
        } else {
            fileList = repo.getFilesByAlbum(listType);
        }
        List<VaultFileItem> audio = new ArrayList<>();
        for (VaultFileItem f : fileList) {
            if (f.fileType == VaultFileItem.FileType.AUDIO) audio.add(f);
        }
        if (!audio.isEmpty()) fileList = audio;
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
        tvTrackTitle.setText(currentFile.originalFileName);
        repo.logFileViewed(currentFile);
        releasePlayer();
        deleteTempFile();

        new Thread(() -> {
            try {
                tempFile = File.createTempFile("vault_aud_", ".tmp", getCacheDir());
                boolean ok = repo.exportFile(currentFile, tempFile);
                if (!ok) throw new Exception("export failed");
                runOnUiThread(this::startPlayback);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to load audio", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startPlayback() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempFile.getAbsolutePath());
            mediaPlayer.setLooping(isLooping);
            mediaPlayer.prepare();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.media.PlaybackParams pp = new android.media.PlaybackParams();
                pp.setSpeed(playbackSpeed);
                mediaPlayer.setPlaybackParams(pp);
            }

            int dur = mediaPlayer.getDuration();
            seekBar.setMax(dur > 0 ? dur : 100);
            tvTotalTime.setText(formatTime(dur));

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPause.setText("▶");
                waveformView.setPlaying(false);
                handler.removeCallbacks(seekUpdateRunnable);
                if (!isLooping) navigateNext();
            });

            mediaPlayer.start();
            btnPlayPause.setText("⏸");
            waveformView.setPlaying(true);
            handler.post(seekUpdateRunnable);
        } catch (Exception e) {
            Toast.makeText(this, "Playback error", Toast.LENGTH_SHORT).show();
        }
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

        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setText("▶");
                waveformView.setPlaying(false);
            } else {
                mediaPlayer.start();
                btnPlayPause.setText("⏸");
                waveformView.setPlaying(true);
                handler.post(seekUpdateRunnable);
            }
        });

        btnPrev.setOnClickListener(v -> navigatePrev());
        btnNext.setOnClickListener(v -> navigateNext());

        btnShuffle.setOnClickListener(v -> {
            isShuffle = !isShuffle;
            Toast.makeText(this, "Shuffle: " + (isShuffle ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            if (mediaPlayer != null) mediaPlayer.setLooping(isLooping);
            Toast.makeText(this, "Loop: " + (isLooping ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });

        btnSpeed.setOnClickListener(v -> showSpeedPicker());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void navigateNext() {
        if (fileList.isEmpty()) return;
        if (isShuffle) {
            currentIndex = (int) (Math.random() * fileList.size());
        } else {
            currentIndex = (currentIndex + 1) % fileList.size();
        }
        loadCurrentFile();
    }

    private void navigatePrev() {
        if (fileList.isEmpty()) return;
        currentIndex = (currentIndex - 1 + fileList.size()) % fileList.size();
        loadCurrentFile();
    }

    private void showSpeedPicker() {
        String[] speeds = {"0.5×", "1.0×", "1.5×", "2.0×"};
        float[] vals = {0.5f, 1.0f, 1.5f, 2.0f};
        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds, (d, w) -> {
                    playbackSpeed = vals[w];
                    btnSpeed.setText(speeds[w]);
                    if (mediaPlayer != null && android.os.Build.VERSION.SDK_INT
                            >= android.os.Build.VERSION_CODES.M) {
                        try {
                            android.media.PlaybackParams pp = new android.media.PlaybackParams();
                            pp.setSpeed(playbackSpeed);
                            mediaPlayer.setPlaybackParams(pp);
                        } catch (Exception ignored) {}
                    }
                }).show();
    }

    private void showMoreMenu() {
        String favLabel = (currentFile != null && currentFile.isFavourited)
                ? "Remove from Favourites" : "Add to Favourites";
        String[] items = {"Add to Album", favLabel, "Export to Downloads", "Share", "Delete"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: showAddToAlbumDialog(); break;
                        case 1: toggleFavourite(); break;
                        case 2: exportToDownloads(); break;
                        case 3: shareFile(); break;
                        case 4: confirmDelete(); break;
                    }
                }).show();
    }

    private void showAddToAlbumDialog() {
        List<VaultAlbum> albums = repo.getAlbums();
        if (albums.isEmpty()) { Toast.makeText(this, "No albums found", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) names[i] = albums.get(i).name;
        new AlertDialog.Builder(this).setTitle("Add to Album")
                .setItems(names, (d, w) -> {
                    repo.addFileToAlbum(currentFile.id, albums.get(w).id);
                    Toast.makeText(this, "Added to " + albums.get(w).name, Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void toggleFavourite() {
        if (currentFile == null) return;
        currentFile.isFavourited = !currentFile.isFavourited;
        repo.updateFile(currentFile);
        Toast.makeText(this, currentFile.isFavourited ? "Added to favourites" : "Removed from favourites",
                Toast.LENGTH_SHORT).show();
    }

    private void exportToDownloads() {
        if (currentFile == null) return;
        new Thread(() -> {
            try {
                File dest = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), currentFile.originalFileName);
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
                    intent.setType(currentFile.mimeType != null ? currentFile.mimeType : "audio/*");
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

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    private void releasePlayer() {
        handler.removeCallbacks(seekUpdateRunnable);
        waveformView.setPlaying(false);
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
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
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setText("▶");
            waveformView.setPlaying(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        deleteTempFile();
    }
}
