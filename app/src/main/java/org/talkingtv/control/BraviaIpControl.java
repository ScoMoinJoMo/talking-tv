package org.talkingtv.control;

import android.os.Handler;
import android.os.Looper;

import org.talkingtv.Http;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

/**
 * Switches a Sony Bravia to an HDMI input with Sony's IP control (REST) API.
 * On the TV: Settings, Network, Remote device settings, Control remotely = on,
 * Authentication = Normal and Pre-Shared Key, then set the key.
 * Because the app runs on the TV, "127.0.0.1" usually reaches it.
 */
public final class BraviaIpControl implements InputSwitcher {
    private final String ip;
    private final String psk;
    private final int port;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());

    public BraviaIpControl(String ip, String psk, int hdmiPort, ExecutorService io) {
        this.ip = ip.isEmpty() ? "127.0.0.1" : ip;
        this.psk = psk;
        this.port = hdmiPort;
        this.io = io;
    }

    @Override public void showBox(Callback callback) {
        io.execute(() -> {
            try {
                String body = "{\"method\":\"setPlayContent\",\"id\":101,\"version\":\"1.0\","
                        + "\"params\":[{\"uri\":\"extInput:hdmi?port=" + port + "\"}]}";
                String reply = Http.postJson("http://" + ip + "/sony/avContent", body,
                        Collections.singletonMap("X-Auth-PSK", psk));
                if (reply.contains("\"error\"")) {
                    main.post(() -> callback.onFailed("The TV refused the input change. Check the TV key in setup."));
                } else {
                    main.post(callback::onShown);
                }
            } catch (Exception e) {
                main.post(() -> callback.onFailed("I could not reach the TV to change the input."));
            }
        });
    }

    @Override public void hideBox() {
        // Opening another app takes the TV off the HDMI input by itself.
    }

    @Override public boolean keepsRemote() {
        return false;
    }
}
