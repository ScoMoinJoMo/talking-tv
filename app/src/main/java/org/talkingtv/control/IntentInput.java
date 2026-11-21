package org.talkingtv.control;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;

/**
 * Asks the TV's own input app to show an HDMI input. Works without any network setup,
 * but the TV's input screen then has the remote, not this app.
 */
public final class IntentInput implements InputSwitcher {
    private final Context context;
    private final String preferredId;
    private final int hdmiPort;

    public IntentInput(Context context, String preferredId, int hdmiPort) {
        this.context = context;
        this.preferredId = preferredId;
        this.hdmiPort = hdmiPort;
    }

    @Override public void showBox(Callback callback) {
        String id = TvInputs.findInputId(context, preferredId, hdmiPort);
        if (id == null) {
            callback.onFailed("I could not find HDMI input " + hdmiPort + " on this TV.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(i);
            callback.onShown();
        } catch (ActivityNotFoundException | SecurityException e) {
            callback.onFailed("This TV would not switch to HDMI " + hdmiPort + ".");
        }
    }

    @Override public void hideBox() {}

    @Override public boolean keepsRemote() {
        return false;
    }
}
