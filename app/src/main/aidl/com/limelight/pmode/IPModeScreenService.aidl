package com.limelight.pmode;

import android.view.Surface;
import com.limelight.pmode.IPModeScreenCallback;

// Each Productivity-mode screen runs as its own bound Service in its own
// process (see PModeScreenServiceBase). This is the whole reason a
// multi-process design was chosen: moonlight-common-c only supports one
// connection per process, so process isolation is what gives each screen
// its own independent connection, with zero changes to that library.
//
// oneway everywhere: connect()/disconnect() fire off work on the service's
// own background thread rather than blocking the caller, and the input
// methods are latency-sensitive enough that a synchronous Binder round trip
// per mouse-move would be a real problem.
oneway interface IPModeScreenService {
    void connect(in Surface videoSurface, String host, int port, int httpsPort, String uniqueId,
                 in byte[] serverCertBytes, String pmodeDisplay,
                 int width, int height, int fps, int bitrate, boolean isAudioOwner,
                 IPModeScreenCallback callback);

    void disconnect();

    void sendMouseMove(int deltaX, int deltaY);
    // Absolute position within this screen's own resolution (0,0 top-left) -
    // what a VR ray hit-test naturally produces, unlike the relative deltas
    // sendMouseMove takes.
    void sendMousePosition(int x, int y, int referenceWidth, int referenceHeight);
    void sendMouseButtonDown(int mouseButton);
    void sendMouseButtonUp(int mouseButton);
    void sendMouseScroll(int scrollClicks);
    void sendKeyboardInput(int keyMap, int keyDirection, int modifier, int flags);
}
