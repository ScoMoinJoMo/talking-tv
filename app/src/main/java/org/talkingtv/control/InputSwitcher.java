package org.talkingtv.control;

/**
 * Puts the cable/satellite box's HDMI picture on screen, and takes it away again.
 * Each TV brand gets its own class (Sony now; Samsung later).
 */
public interface InputSwitcher {
    interface Callback {
        void onShown();

        void onFailed(String spokenReason);
    }

    /** Called on the main thread. */
    void showBox(Callback callback);

    /** Called on the main thread when a streaming app or the menu takes over. */
    void hideBox();

    /** True when the box picture stays inside this app, so the remote still talks to us. */
    boolean keepsRemote();
}
