package com.prajwal.myfirstapp.notes;


import com.prajwal.myfirstapp.R;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  STUDY SESSION ACTIVITY — Pomodoro timer with ambient sounds.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 */
public class StudySessionActivity extends AppCompatActivity implements StudySessionManager.StudySessionListener {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════

    private StudySessionManager sessionManager;
    private AmbientSoundPlayer soundPlayer;

    // Views
    private TextView tvSessionState, tvTimer, tvTimerLabel;
    private ProgressBar progressTimer;
    private LinearLayout layoutPomodoroDots;
    private MaterialButton btnStartPause;
    private ImageButton btnReset, btnSkip;
    private TextView tvPomodorosCompleted, tvTotalFocusTime, tvStreak;
    private SeekBar seekVolume;

    // Ambient chips
    private Chip chipSoundNone, chipSoundWhiteNoise, chipSoundRain, chipSoundTyping, chipSoundCafe;
    private Chip[] allChips;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_session);
        getWindow().setStatusBarColor(0xFF0A0E21);
        getWindow().setNavigationBarColor(0xFF0A0E21);

        sessionManager = new StudySessionManager(this);
        sessionManager.setListener(this);
        soundPlayer = new AmbientSoundPlayer(this);

        bindViews();
        setupListeners();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        soundPlayer.stop();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SETUP
    // ═══════════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvSessionState = findViewById(R.id.tvSessionState);
        tvTimer = findViewById(R.id.tvTimer);
        tvTimerLabel = findViewById(R.id.tvTimerLabel);
        progressTimer = findViewById(R.id.progressTimer);
        layoutPomodoroDots = findViewById(R.id.layoutPomodoroDots);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnReset = findViewById(R.id.btnReset);
        btnSkip = findViewById(R.id.btnSkip);
        tvPomodorosCompleted = findViewById(R.id.tvPomodorosCompleted);
        tvTotalFocusTime = findViewById(R.id.tvTotalFocusTime);
        tvStreak = findViewById(R.id.tvStreak);
        seekVolume = findViewById(R.id.seekVolume);

        chipSoundNone = findViewById(R.id.chipSoundNone);
        chipSoundWhiteNoise = findViewById(R.id.chipSoundWhiteNoise);
        chipSoundRain = findViewById(R.id.chipSoundRain);
        chipSoundTyping = findViewById(R.id.chipSoundTyping);
        chipSoundCafe = findViewById(R.id.chipSoundCafe);

        allChips = new Chip[]{chipSoundNone, chipSoundWhiteNoise, chipSoundRain, chipSoundTyping, chipSoundCafe};
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnStartPause.setOnClickListener(v -> {
            int state = sessionManager.getCurrentState();
            if (state == StudySessionManager.STATE_IDLE) {
                String noteId = getIntent().getStringExtra("noteId");
                String noteTitle = getIntent().getStringExtra("noteTitle");
                sessionManager.startFocus(noteId, noteTitle);
            } else if (state == StudySessionManager.STATE_PAUSED) {
                sessionManager.resume();
            } else {
                sessionManager.pause();
            }
        });

        btnReset.setOnClickListener(v -> {
            sessionManager.stop();
            soundPlayer.stop();
            updateUI();
        });

        btnSkip.setOnClickListener(v -> {
            sessionManager.skip();
        });

        // Ambient sound chips
        chipSoundNone.setOnClickListener(v -> selectSound(AmbientSoundPlayer.SOUND_NONE));
        chipSoundWhiteNoise.setOnClickListener(v -> selectSound(AmbientSoundPlayer.SOUND_WHITE_NOISE));
        chipSoundRain.setOnClickListener(v -> selectSound(AmbientSoundPlayer.SOUND_RAIN));
        chipSoundTyping.setOnClickListener(v -> selectSound(AmbientSoundPlayer.SOUND_TYPING));
        chipSoundCafe.setOnClickListener(v -> selectSound(AmbientSoundPlayer.SOUND_CAFE));

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) soundPlayer.setVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void selectSound(int type) {
        for (Chip c : allChips) c.setChecked(false);
        switch (type) {
            case AmbientSoundPlayer.SOUND_NONE: chipSoundNone.setChecked(true); break;
            case AmbientSoundPlayer.SOUND_WHITE_NOISE: chipSoundWhiteNoise.setChecked(true); break;
            case AmbientSoundPlayer.SOUND_RAIN: chipSoundRain.setChecked(true); break;
            case AmbientSoundPlayer.SOUND_TYPING: chipSoundTyping.setChecked(true); break;
            case AmbientSoundPlayer.SOUND_CAFE: chipSoundCafe.setChecked(true); break;
        }

        if (type == AmbientSoundPlayer.SOUND_NONE) {
            soundPlayer.stop();
        } else {
            soundPlayer.play(type);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  UI UPDATES
    // ═══════════════════════════════════════════════════════════════════════════════

    private void updateUI() {
        int state = sessionManager.getCurrentState();

        switch (state) {
            case StudySessionManager.STATE_IDLE:
                tvSessionState.setText("READY");
                tvSessionState.setTextColor(0xFF94A3B8);
                tvTimer.setText(String.format("%02d:00", sessionManager.getFocusDurationMinutes()));
                btnStartPause.setText("Start");
                break;
            case StudySessionManager.STATE_FOCUS:
                tvSessionState.setText("FOCUS");
                tvSessionState.setTextColor(0xFFF59E0B);
                btnStartPause.setText("Pause");
                break;
            case StudySessionManager.STATE_SHORT_BREAK:
                tvSessionState.setText("SHORT BREAK");
                tvSessionState.setTextColor(0xFF10B981);
                btnStartPause.setText("Pause");
                break;
            case StudySessionManager.STATE_LONG_BREAK:
                tvSessionState.setText("LONG BREAK");
                tvSessionState.setTextColor(0xFF3B82F6);
                btnStartPause.setText("Pause");
                break;
            case StudySessionManager.STATE_PAUSED:
                tvSessionState.setText("PAUSED");
                tvSessionState.setTextColor(0xFF94A3B8);
                btnStartPause.setText("Resume");
                break;
        }

        tvPomodorosCompleted.setText(String.valueOf(sessionManager.getPomodoroCount()));
        int totalMin = sessionManager.getTotalFocusMinutes();
        tvTotalFocusTime.setText(totalMin >= 60 ? (totalMin / 60) + "h " + (totalMin % 60) + "m" : totalMin + "m");
        tvStreak.setText(String.valueOf(sessionManager.getDailyStreak()));

        updatePomodoroDots();
    }

    private void updatePomodoroDots() {
        layoutPomodoroDots.removeAllViews();
        int completed = sessionManager.getPomodoroCount();
        int total = 4; // Show 4 dots for one cycle

        for (int i = 0; i < total; i++) {
            View dot = new View(this);
            int size = dpToPx(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dot.setLayoutParams(lp);

            if (i < completed % total) {
                dot.setBackgroundColor(0xFFF59E0B);
            } else {
                dot.setBackgroundColor(0xFF1E293B);
            }

            layoutPomodoroDots.addView(dot);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  LISTENER CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════════════

    @Override
    public void onTimerTick(long remainingMillis, int state) {
        long minutes = remainingMillis / 60000;
        long seconds = (remainingMillis % 60000) / 1000;
        runOnUiThread(() -> {
            tvTimer.setText(String.format("%02d:%02d", minutes, seconds));

            // Update progress
            long totalMillis;
            if (state == StudySessionManager.STATE_FOCUS) {
                totalMillis = sessionManager.getFocusDurationMinutes() * 60000L;
            } else if (state == StudySessionManager.STATE_SHORT_BREAK) {
                totalMillis = sessionManager.getShortBreakMinutes() * 60000L;
            } else {
                totalMillis = sessionManager.getLongBreakMinutes() * 60000L;
            }
            int progress = totalMillis > 0 ? (int) (remainingMillis * 100 / totalMillis) : 0;
            progressTimer.setProgress(progress);
        });
    }

    @Override
    public void onStateChanged(int newState, int pomodoroCount) {
        runOnUiThread(this::updateUI);
    }

    @Override
    public void onSessionCompleted(StudySessionManager.StudySession session) {
        runOnUiThread(() -> {
            updateUI();
            tvSessionState.setText("COMPLETED!");
            tvSessionState.setTextColor(0xFF10B981);
        });
    }

    @Override
    public void onPomodoroCompleted(int pomodoroNumber) {
        runOnUiThread(this::updateUI);
    }
}
