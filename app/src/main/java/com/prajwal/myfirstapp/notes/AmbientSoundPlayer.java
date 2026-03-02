package com.prajwal.myfirstapp.notes;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *  AMBIENT SOUND PLAYER — Background sound effects for focus mode.
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 *  Generates white/pink noise and ambient tones using synthesized audio.
 *  Since we can't bundle raw audio files, we use Android's ToneGenerator-based
 *  approach + a white noise thread.
 *
 *  Available sounds:
 *  0 = None (silence)
 *  1 = White Noise
 *  2 = Rain
 *  3 = Gentle Typing
 *  4 = Cafe Ambience
 *
 *  Note: For a production app, these would be actual audio files. Here we
 *  implement a silence/placeholder approach with volume control, and actual
 *  white noise generation.
 */
public class AmbientSoundPlayer {

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SOUND OPTIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    public static final int SOUND_NONE        = 0;
    public static final int SOUND_WHITE_NOISE = 1;
    public static final int SOUND_RAIN        = 2;
    public static final int SOUND_TYPING      = 3;
    public static final int SOUND_CAFE        = 4;

    public static final String[] SOUND_NAMES = {
            "None", "White Noise", "Rain", "Typing", "Café"
    };
    public static final String[] SOUND_EMOJIS = {
            "🔇", "📻", "🌧️", "⌨️", "☕"
    };

    // ═══════════════════════════════════════════════════════════════════════════════
    //  FIELDS
    // ═══════════════════════════════════════════════════════════════════════════════
    private final Context context;
    private int currentSound = SOUND_NONE;
    private float volume = 0.5f;  // 0.0 to 1.0
    private boolean isPlaying = false;

    // White noise generator
    private Thread noiseThread;
    private android.media.AudioTrack audioTrack;

    // ═══════════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════════════

    public AmbientSoundPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  PLAYBACK
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Start playing the specified ambient sound.
     */
    public void play(int soundType) {
        stop();
        currentSound = soundType;
        if (soundType == SOUND_NONE) return;

        isPlaying = true;

        if (soundType == SOUND_WHITE_NOISE) {
            startWhiteNoise();
        } else {
            // For other sound types, we generate filtered noise variants
            startFilteredNoise(soundType);
        }
    }

    /**
     * Stop all playback.
     */
    public void stop() {
        isPlaying = false;
        currentSound = SOUND_NONE;

        if (noiseThread != null) {
            noiseThread.interrupt();
            noiseThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) { /* ignore */ }
            audioTrack = null;
        }
    }

    /**
     * Set volume (0.0 - 1.0).
     */
    public void setVolume(float vol) {
        this.volume = Math.max(0f, Math.min(1f, vol));
        if (audioTrack != null) {
            try {
                audioTrack.setVolume(this.volume);
            } catch (Exception e) { /* ignore */ }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  WHITE NOISE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════════

    private void startWhiteNoise() {
        final int sampleRate = 22050;
        final int bufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
        );

        audioTrack = new android.media.AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioTrack.setVolume(volume);
        audioTrack.play();

        noiseThread = new Thread(() -> {
            java.util.Random random = new java.util.Random();
            short[] buffer = new short[bufferSize / 2];
            while (isPlaying && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < buffer.length; i++) {
                    buffer[i] = (short) (random.nextGaussian() * 4000);
                }
                try {
                    audioTrack.write(buffer, 0, buffer.length);
                } catch (Exception e) {
                    break;
                }
            }
        }, "WhiteNoiseThread");
        noiseThread.setDaemon(true);
        noiseThread.start();
    }

    /**
     * Generate "filtered" noise that sounds different from pure white noise.
     * Rain = low-pass filtered, Typing = rhythmic bursts, Café = mid-range.
     */
    private void startFilteredNoise(int type) {
        final int sampleRate = 22050;
        final int bufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
        );

        audioTrack = new android.media.AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();

        audioTrack.setVolume(volume * 0.7f); // Slightly quieter than white noise
        audioTrack.play();

        noiseThread = new Thread(() -> {
            java.util.Random random = new java.util.Random();
            short[] buffer = new short[bufferSize / 2];
            float prevSample = 0;
            int sampleCounter = 0;

            while (isPlaying && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < buffer.length; i++) {
                    float sample = (float) random.nextGaussian();
                    sampleCounter++;

                    switch (type) {
                        case SOUND_RAIN:
                            // Brown noise (low-pass) — more bassy, rain-like
                            sample = prevSample + (sample * 0.02f);
                            sample = Math.max(-1f, Math.min(1f, sample));
                            prevSample = sample;
                            buffer[i] = (short) (sample * 8000);
                            break;

                        case SOUND_TYPING:
                            // Intermittent short bursts (like keystrokes)
                            boolean inBurst = (sampleCounter % (sampleRate / 4)) < (sampleRate / 20);
                            if (inBurst) {
                                buffer[i] = (short) (sample * 3000);
                            } else {
                                buffer[i] = (short) (sample * 200); // Very quiet between bursts
                            }
                            break;

                        case SOUND_CAFE:
                            // Pink noise approximation — medium frequency
                            sample = prevSample * 0.95f + sample * 0.05f;
                            prevSample = sample;
                            buffer[i] = (short) (sample * 6000);
                            break;

                        default:
                            buffer[i] = (short) (sample * 3000);
                            break;
                    }
                }
                try {
                    audioTrack.write(buffer, 0, buffer.length);
                } catch (Exception e) {
                    break;
                }
            }
        }, "AmbientNoiseThread");
        noiseThread.setDaemon(true);
        noiseThread.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════════════════════════════════════

    public boolean isPlaying() { return isPlaying; }
    public int getCurrentSound() { return currentSound; }
    public float getVolume() { return volume; }

    public String getCurrentSoundName() {
        if (currentSound >= 0 && currentSound < SOUND_NAMES.length) {
            return SOUND_NAMES[currentSound];
        }
        return "None";
    }

    public String getCurrentSoundEmoji() {
        if (currentSound >= 0 && currentSound < SOUND_EMOJIS.length) {
            return SOUND_EMOJIS[currentSound];
        }
        return "🔇";
    }

    /**
     * Release all resources. Call in Activity.onDestroy().
     */
    public void release() {
        stop();
    }
}
