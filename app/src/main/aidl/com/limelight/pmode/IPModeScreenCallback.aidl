package com.limelight.pmode;

// Reports connection lifecycle for one Productivity-mode screen back to the
// main (XR) process. Kept intentionally small - just enough for the debug
// overlay and for the main process to know when a screen has died so it can
// stop rendering that quad.
oneway interface IPModeScreenCallback {
    void onStageStarting(String stage);
    void onStageComplete(String stage);
    void onStageFailed(String stage, int portFlags, int errorCode);

    void onConnectionStarted();
    void onConnectionTerminated(int errorCode);

    void onDisplayMessage(String message);
}
