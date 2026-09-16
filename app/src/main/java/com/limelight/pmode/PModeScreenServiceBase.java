package com.limelight.pmode;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Surface;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Runs exactly one moonlight-common-c connection for one Productivity-mode
 * screen. Real isolation comes from where this class runs, not from
 * anything in this file: each concrete subclass is declared in the manifest
 * with its own android:process, so moonlight-common-c's process-global
 * state (see the living doc - Limelight.h explicitly documents one
 * connection per process) is automatically a separate instance per screen,
 * with zero changes to that library.
 *
 * Deliberately NOT reusing Game.java's full setup: no gamepad, no touch
 * contexts, no ControllerHandler - a Productivity screen only ever needs
 * mouse and keyboard, and it never owns a visible Activity/Window, so
 * MediaCodecDecoderRenderer is driven in its "direct Surface" mode
 * (enableVrMode=false, enableGlRenderPath=false) rather than the XR-session
 * mode Gaming mode uses.
 */
public abstract class PModeScreenServiceBase extends Service {
    private volatile NvConnection conn;
    private volatile IPModeScreenCallback callback;

    private final IPModeScreenService.Stub binder = new IPModeScreenService.Stub() {
        @Override
        public void connect(Surface videoSurface, String host, int port, int httpsPort, String uniqueId,
                             byte[] serverCertBytes, String pmodeDisplay,
                             int width, int height, int fps, int bitrate, boolean isAudioOwner,
                             IPModeScreenCallback cb) {
            PModeScreenServiceBase.this.doConnect(videoSurface, host, port, httpsPort, uniqueId,
                    serverCertBytes, pmodeDisplay, width, height, fps, bitrate, isAudioOwner, cb);
        }

        @Override
        public void disconnect() {
            PModeScreenServiceBase.this.doDisconnect();
        }

        @Override
        public void sendMouseMove(int deltaX, int deltaY) {
            NvConnection c = conn;
            if (c != null) {
                c.sendMouseMove((short) deltaX, (short) deltaY);
            }
        }

        @Override
        public void sendMousePosition(int x, int y, int referenceWidth, int referenceHeight) {
            NvConnection c = conn;
            if (c != null) {
                c.sendMousePosition((short) x, (short) y, (short) referenceWidth, (short) referenceHeight);
            }
        }

        @Override
        public void sendMouseButtonDown(int mouseButton) {
            NvConnection c = conn;
            if (c != null) {
                c.sendMouseButtonDown((byte) mouseButton);
            }
        }

        @Override
        public void sendMouseButtonUp(int mouseButton) {
            NvConnection c = conn;
            if (c != null) {
                c.sendMouseButtonUp((byte) mouseButton);
            }
        }

        @Override
        public void sendMouseScroll(int scrollClicks) {
            NvConnection c = conn;
            if (c != null) {
                c.sendMouseScroll((byte) scrollClicks);
            }
        }

        @Override
        public void sendKeyboardInput(int keyMap, int keyDirection, int modifier, int flags) {
            NvConnection c = conn;
            if (c != null) {
                c.sendKeyboardInput((short) keyMap, (byte) keyDirection, (byte) modifier, (byte) flags);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        doDisconnect();
        return false;
    }

    private void doConnect(Surface videoSurface, String host, int port, int httpsPort, String uniqueId,
                            byte[] serverCertBytes, String pmodeDisplay,
                            int width, int height, int fps, int bitrate, boolean isAudioOwner,
                            IPModeScreenCallback cb) {
        this.callback = cb;

        if (conn != null) {
            // Already connected/connecting - a stale connect() call. Tear down
            // first rather than leaking a second NvConnection in this process.
            doDisconnect();
        }

        final X509Certificate serverCert;
        try {
            serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(serverCertBytes));
        } catch (Exception e) {
            LimeLog.severe("PMode screen service: failed to parse server certificate: " + e);
            notifyTerminated(-1);
            return;
        }

        // Each pmode_screenN process has its own static state - Game.java's
        // main process calls this in onCreate(), but that doesn't help here.
        // Without it, MediaCodecDecoderRenderer's constructor throws
        // IllegalStateException immediately, and since this is all inside a
        // oneway AIDL call, the exception just gets logged and swallowed by
        // Binder rather than crashing anything visibly - confirmed on real
        // hardware as the actual reason no video ever showed up.
        MediaCodecHelper.initialize(this, "headless");

        // A headless decode target: no XR session, no SurfaceView, no GL
        // passthrough - just MediaCodec decoding straight into the Surface
        // the main process handed us (backed by its own SurfaceTexture/OES
        // texture, which is what actually gets sampled into the VR scene).
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        prefs.enableVrMode = false;
        prefs.enableGlRenderPath = false;
        prefs.enablePerfOverlay = false;
        prefs.width = width;
        prefs.height = height;

        CrashListener crashListener = new CrashListener() {
            @Override
            public void notifyCrash(Exception e) {
                LimeLog.severe("PMode screen service decoder crash: " + e);
                notifyTerminated(-1);
            }
        };

        MediaCodecDecoderRenderer decoderRenderer = new MediaCodecDecoderRenderer(
                null, prefs, crashListener, 0, false, false, "headless", null);
        decoderRenderer.setRenderTarget(videoSurface);

        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(width, height)
                .setLaunchRefreshRate(fps)
                .setRefreshRate(fps)
                .setApp(new NvApp("Desktop"))
                .setBitrate(bitrate)
                .setEnableSops(false)
                .enableLocalAudioPlayback(isAudioOwner)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                .setSupportedVideoFormats(supportedVideoFormats)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(true)
                .setPmodeDisplay(pmodeDisplay)
                .build();

        NvConnection newConn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, config,
                PlatformBinding.getCryptoProvider(this), serverCert);
        this.conn = newConn;

        AudioRenderer audioRenderer = isAudioOwner
                ? new AndroidAudioRenderer(getApplicationContext(), false)
                : new NoOpAudioRenderer();

        newConn.start(audioRenderer, decoderRenderer, new NvConnectionListener() {
            @Override
            public void stageStarting(String stage) {
                notifyCallback(cb2 -> cb2.onStageStarting(stage));
            }

            @Override
            public void stageComplete(String stage) {
                notifyCallback(cb2 -> cb2.onStageComplete(stage));
            }

            @Override
            public void stageFailed(String stage, int portFlags, int errorCode) {
                notifyCallback(cb2 -> cb2.onStageFailed(stage, portFlags, errorCode));
            }

            @Override
            public void connectionStarted() {
                notifyCallback(IPModeScreenCallback::onConnectionStarted);
            }

            @Override
            public void connectionTerminated(int errorCode) {
                conn = null;
                notifyCallback(cb2 -> cb2.onConnectionTerminated(errorCode));
            }

            @Override
            public void connectionStatusUpdate(int connectionStatus) {
                // Not surfaced yet - Productivity mode has no per-screen network
                // quality indicator today.
            }

            @Override
            public void displayMessage(String message) {
                notifyCallback(cb2 -> cb2.onDisplayMessage(message));
            }

            @Override
            public void displayTransientMessage(String message) {
                notifyCallback(cb2 -> cb2.onDisplayMessage(message));
            }

            @Override
            public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
                // No gamepad passthrough for Productivity screens.
            }

            @Override
            public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
            }

            @Override
            public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
            }

            @Override
            public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
            }

            @Override
            public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
            }
        });
    }

    private void doDisconnect() {
        NvConnection c = conn;
        conn = null;
        if (c != null) {
            c.stop();
        }
        callback = null;
    }

    private void notifyTerminated(int errorCode) {
        notifyCallback(cb2 -> cb2.onConnectionTerminated(errorCode));
    }

    private interface CallbackAction {
        void run(IPModeScreenCallback cb) throws RemoteException;
    }

    private void notifyCallback(CallbackAction action) {
        IPModeScreenCallback cb = callback;
        if (cb == null) {
            return;
        }
        try {
            action.run(cb);
        } catch (RemoteException e) {
            LimeLog.warning("PMode screen service: callback delivery failed: " + e);
        }
    }

    @Override
    public void onDestroy() {
        doDisconnect();
        super.onDestroy();
    }
}
