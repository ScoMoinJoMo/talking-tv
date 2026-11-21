package org.talkingtv.control;

import org.json.JSONException;
import org.json.JSONObject;
import org.talkingtv.Http;

import java.io.IOException;

/**
 * Talks to a DirecTV Genie (HS17 or HR-series) with its built-in "SHEF" web interface on
 * port 8080. A Genie Mini is chosen with clientAddr (its MAC address without colons);
 * "0" or empty means the main receiver itself.
 *
 * Needs, on the DirecTV system: Settings, Whole-Home, External Device, set to Allow.
 */
public final class DirecTvShef implements ChannelTuner {
    private final String base;
    private final String clientAddr;

    public DirecTvShef(String serverIp, String clientAddr) {
        this.base = "http://" + serverIp + ":8080";
        this.clientAddr = clientAddr == null || clientAddr.isEmpty() ? "0" : clientAddr;
    }

    @Override public void tune(int channel) throws IOException {
        check(Http.get(base + "/tv/tune?major=" + channel + "&clientAddr=" + clientAddr));
    }

    @Override public void channelUp() throws IOException {
        key("chanup");
    }

    @Override public void channelDown() throws IOException {
        key("chandown");
    }

    private void key(String key) throws IOException {
        check(Http.get(base + "/remote/processKey?key=" + key + "&hold=keyPress&clientAddr=" + clientAddr));
    }

    @Override public String whatsOn() throws IOException {
        try {
            JSONObject o = new JSONObject(Http.get(base + "/tv/getTuned?clientAddr=" + clientAddr));
            StringBuilder s = new StringBuilder();
            String callsign = o.optString("callsign", "");
            int major = o.optInt("major", 0);
            String title = o.optString("title", "");
            String episode = o.optString("episodeTitle", "");
            if (!callsign.isEmpty()) s.append(callsign).append(", ");
            if (major > 0) s.append("channel ").append(major).append(". ");
            if (!title.isEmpty()) s.append(title);
            if (!episode.isEmpty() && !episode.equals(title)) s.append(", ").append(episode);
            return s.length() == 0 ? "DirecTV did not say what is on" : s.toString();
        } catch (JSONException e) {
            throw new IOException("DirecTV sent an answer I could not read", e);
        }
    }

    /** Lists the receivers and Minis the HS17 knows about, as raw JSON, for the setup page. */
    public static String locations(String serverIp) throws IOException {
        return Http.get("http://" + serverIp + ":8080/info/getLocations");
    }

    private static void check(String response) throws IOException {
        try {
            JSONObject status = new JSONObject(response).optJSONObject("status");
            if (status != null && status.optInt("code", 200) != 200) {
                throw new IOException("DirecTV said: " + status.optString("msg", "error"));
            }
        } catch (JSONException ignored) {
            // Some firmware answers with an empty body on success.
        }
    }
}
