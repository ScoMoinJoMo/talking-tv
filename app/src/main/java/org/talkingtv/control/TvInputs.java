package org.talkingtv.control;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the TV's HDMI inputs through Android's TV input framework. */
public final class TvInputs {
    private TvInputs() {}

    public static List<TvInputInfo> hdmiInputs(Context context) {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getType() == TvInputInfo.TYPE_HDMI) out.add(info);
        }
        return out;
    }

    /** Uses the exact input id if set, else the input whose label or id names the HDMI port number. */
    public static String findInputId(Context context, String preferredId, int hdmiPort) {
        List<TvInputInfo> inputs = hdmiInputs(context);
        if (preferredId != null && !preferredId.isEmpty()) {
            for (TvInputInfo i : inputs) if (i.getId().equals(preferredId)) return preferredId;
        }
        // HDMI-CEC devices show up as child inputs of a port; prefer the port itself.
        Pattern digits = Pattern.compile("(\\d+)\\D*$");
        for (TvInputInfo i : inputs) {
            if (i.getParentId() != null) continue;
            CharSequence label = i.loadLabel(context);
            if (label != null && endsWithNumber(digits, label.toString(), hdmiPort)) return i.getId();
        }
        for (TvInputInfo i : inputs) {
            if (i.getParentId() == null && endsWithNumber(digits, i.getId(), hdmiPort)) return i.getId();
        }
        List<TvInputInfo> ports = new ArrayList<>();
        for (TvInputInfo i : inputs) if (i.getParentId() == null) ports.add(i);
        if (hdmiPort >= 1 && hdmiPort <= ports.size()) return ports.get(hdmiPort - 1).getId();
        return null;
    }

    private static boolean endsWithNumber(Pattern p, String s, int n) {
        Matcher m = p.matcher(s);
        return m.find() && m.group(1).equals(String.valueOf(n));
    }

    /** A readable list for the setup page. */
    public static String describe(Context context) {
        StringBuilder sb = new StringBuilder();
        List<TvInputInfo> inputs = hdmiInputs(context);
        if (inputs.isEmpty()) return "No HDMI inputs were reported by this TV.";
        for (TvInputInfo i : inputs) {
            CharSequence label = i.loadLabel(context);
            sb.append(label).append("  ->  inputId: ").append(i.getId());
            if (i.getParentId() != null) sb.append("  (device on ").append(i.getParentId()).append(")");
            sb.append('\n');
        }
        return sb.toString();
    }
}
