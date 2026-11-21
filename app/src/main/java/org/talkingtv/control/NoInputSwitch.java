package org.talkingtv.control;

/** For rooms where the TV is left on the box's input, or HDMI-CEC switches it. */
public final class NoInputSwitch implements InputSwitcher {
    @Override public void showBox(Callback callback) {
        callback.onShown();
    }

    @Override public void hideBox() {}

    @Override public boolean keepsRemote() {
        return false;
    }
}
