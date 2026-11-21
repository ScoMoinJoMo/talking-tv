package org.talkingtv;

import org.json.JSONException;
import org.json.JSONObject;

/** One item on the talking home screen. */
public final class Favorite {
    public static final String GENIE = "genie";
    public static final String YOUTUBE_TV = "youtubetv";
    public static final String APP = "app";
    public static final String LINK = "link";

    public final String name;
    public final String type;
    /** Genie channel number, for type genie. */
    public final int channel;
    /** Deep link, for type youtubetv or link. */
    public final String url;
    /** Android package to open, for type app (or the app that should handle a link). */
    public final String packageName;
    /** Optional digits on the remote that jump straight to this favorite. */
    public final String number;

    Favorite(String name, String type, int channel, String url, String packageName, String number) {
        this.name = name;
        this.type = type;
        this.channel = channel;
        this.url = url;
        this.packageName = packageName;
        this.number = number;
    }

    static Favorite fromJson(JSONObject o) throws JSONException {
        String name = o.getString("name").trim();
        String type = o.optString("type", APP).trim().toLowerCase();
        if (name.isEmpty()) throw new JSONException("A favorite has an empty name");
        switch (type) {
            case GENIE:
                if (o.optInt("channel", 0) <= 0) {
                    throw new JSONException("Favorite '" + name + "' needs a channel number");
                }
                break;
            case APP:
                if (o.optString("package", "").isEmpty()) {
                    throw new JSONException("Favorite '" + name + "' needs a package");
                }
                break;
            case YOUTUBE_TV:
            case LINK:
                break;
            default:
                throw new JSONException("Favorite '" + name + "' has unknown type '" + type + "'");
        }
        return new Favorite(name, type, o.optInt("channel", 0), o.optString("url", "").trim(),
                o.optString("package", "").trim(), o.optString("number", "").trim());
    }

    /** What gets spoken when this favorite is highlighted. */
    public String spokenLabel() {
        switch (type) {
            case GENIE: return name + ", DirecTV channel " + channel;
            case YOUTUBE_TV: return name.toLowerCase().contains("youtube") ? name : name + ", YouTube TV";
            default: return name;
        }
    }

    @Override public String toString() {
        return name;
    }
}
