package com.limelight.pmode;

import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

/**
 * Only one Productivity-mode screen "owns" audio at a time (see
 * PModeScreenServiceBase) - decoding and mixing the desktop audio stream
 * from every screen would just produce overlap/echo. The other screens'
 * connections still request/decode audio at the protocol level (host_audio
 * is a per-launch flag, not something this class controls), so their
 * decoded samples need somewhere to go; this just discards them.
 */
class NoOpAudioRenderer implements AudioRenderer {
    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        return 0;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        // Discarded - see class comment.
    }

    @Override
    public void cleanup() {
    }
}
