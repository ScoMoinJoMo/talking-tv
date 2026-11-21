package org.talkingtv.control;

import android.content.Context;
import android.media.tv.TvView;

import org.talkingtv.Config;

import java.util.concurrent.ExecutorService;

/** Builds the right controllers from the config. Add new brands here. */
public final class Controls {
    private Controls() {}

    public static ChannelTuner tuner(Config c) {
        if (!c.hasGenie()) return null;
        return new DirecTvShef(c.genieServerIp, c.genieClientAddr);
    }

    public static InputSwitcher inputSwitcher(Config c, Context context, TvView tvView, ExecutorService io) {
        switch (c.inputMethod) {
            case "bravia":
            case "sony":
                return new BraviaIpControl(c.braviaIp, c.braviaPsk, c.hdmiPort, io);
            case "intent":
                return new IntentInput(context, c.inputId, c.hdmiPort);
            case "none":
                return new NoInputSwitch();
            case "tvview":
            default:
                return new TvViewInput(context, tvView, c.inputId, c.hdmiPort);
        }
    }
}
