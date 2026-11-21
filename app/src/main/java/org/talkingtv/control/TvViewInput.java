package org.talkingtv.control;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Shows the Genie Mini's HDMI picture inside this app with Android's TvView. This is the
 * best option for blind users: the app stays in front, so number keys, channel keys and
 * "what's on" keep working while they watch. Falls back to handing off to the TV's own
 * input screen if this TV does not allow it.
 */
public final class TvViewInput implements InputSwitcher {
    private final Context context;
    private final TvView tvView;
    private final String preferredId;
    private final int hdmiPort;
    private final IntentInput fallback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Callback pending;
    private boolean usingFallback;

    public TvViewInput(Context context, TvView tvView, String preferredId, int hdmiPort) {
        this.context = context;
        this.tvView = tvView;
        this.preferredId = preferredId;
        this.hdmiPort = hdmiPort;
        this.fallback = new IntentInput(context, preferredId, hdmiPort);
        tvView.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String inputId) {
                finish(true);
            }

            @Override public void onConnectionFailed(String inputId) {
                failOver();
            }

            @Override public void onDisconnected(String inputId) {
                failOver();
            }
        });
    }

    @Override public void showBox(Callback callback) {
        String id = TvInputs.findInputId(context, preferredId, hdmiPort);
        if (id == null) {
            callback.onFailed("I could not find HDMI input " + hdmiPort + " on this TV.");
            return;
        }
        if (tvView.getVisibility() == View.VISIBLE && !usingFallback) {
            // Already showing the box; nothing to do.
            callback.onShown();
            return;
        }
        main.removeCallbacksAndMessages(null);
        pending = callback;
        usingFallback = false;
        tvView.setVisibility(View.VISIBLE);
        try {
            tvView.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
        } catch (RuntimeException e) {
            failOver();
            return;
        }
        // Some TVs never report video for a passthrough input; don't wait forever.
        main.postDelayed(() -> finish(true), 4000);
    }

    private void finish(boolean ok) {
        Callback c = pending;
        pending = null;
        if (c != null && ok) c.onShown();
    }

    private void failOver() {
        if (usingFallback) return;
        usingFallback = true;
        tvView.reset();
        tvView.setVisibility(View.GONE);
        Callback c = pending;
        pending = null;
        if (c != null) fallback.showBox(c);
    }

    @Override public void hideBox() {
        main.removeCallbacksAndMessages(null);
        pending = null;
        usingFallback = false;
        tvView.reset();
        tvView.setVisibility(View.GONE);
    }

    @Override public boolean keepsRemote() {
        return !usingFallback;
    }
}
