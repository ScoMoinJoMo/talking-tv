package org.talkingtv;

import android.content.Context;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Saves the config as a JSON file in the app's private storage. */
public final class ConfigStore {
    private static final String FILE = "config.json";
    private final Context context;

    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public Config load() {
        File f = new File(context.getFilesDir(), FILE);
        if (f.exists()) {
            try {
                return Config.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            } catch (IOException | JSONException ignored) {
                // Fall through to the default so the TV is never left without a menu.
            }
        }
        return loadDefault();
    }

    public Config loadDefault() {
        try (InputStream in = context.getAssets().open("default_config.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return Config.parse(out.toString("UTF-8"));
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Built-in default config is broken", e);
        }
    }

    /** Validates, then saves. Throws JSONException with a readable message if invalid. */
    public Config save(String json) throws JSONException, IOException {
        Config c = Config.parse(json);
        File tmp = new File(context.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(c.prettyJson().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(context.getFilesDir(), FILE))) {
            throw new IOException("Could not save settings");
        }
        return Config.parse(c.prettyJson());
    }
}
