package org.talkingtv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything a helper sets up once: addresses, keys and the favorites list. */
public final class Config {
    public final String rawJson;
    public final float speechRate;
    public final int digitWaitMs;

    public final String genieServerIp;
    public final String genieClientAddr;

    public final String inputMethod;
    public final int hdmiPort;
    public final String inputId;
    public final String braviaIp;
    public final String braviaPsk;

    public final List<Favorite> favorites;

    private Config(String rawJson, JSONObject o) throws JSONException {
        this.rawJson = rawJson;
        speechRate = (float) o.optDouble("speechRate", 1.0);
        digitWaitMs = o.optInt("digitWaitMs", 1800);

        JSONObject g = o.optJSONObject("genie");
        if (g == null) g = new JSONObject();
        genieServerIp = g.optString("serverIp", "").trim();
        genieClientAddr = g.optString("clientAddr", "").trim().replace(":", "").toUpperCase();

        JSONObject t = o.optJSONObject("tvInput");
        if (t == null) t = new JSONObject();
        inputMethod = t.optString("method", "tvview").trim().toLowerCase();
        hdmiPort = t.optInt("hdmiPort", 1);
        inputId = t.optString("inputId", "").trim();
        braviaIp = t.optString("braviaIp", "127.0.0.1").trim();
        braviaPsk = t.optString("braviaPsk", "").trim();

        List<Favorite> list = new ArrayList<>();
        JSONArray arr = o.optJSONArray("favorites");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) list.add(Favorite.fromJson(arr.getJSONObject(i)));
        }
        favorites = Collections.unmodifiableList(list);
    }

    /** Parses and checks a config; throws with a readable message if something is wrong. */
    public static Config parse(String json) throws JSONException {
        return new Config(json, new JSONObject(json));
    }

    public boolean hasGenie() {
        return !genieServerIp.isEmpty();
    }

    public String prettyJson() {
        try {
            return new JSONObject(rawJson).toString(2);
        } catch (JSONException e) {
            return rawJson;
        }
    }
}
