package org.talkingtv;

import android.app.Activity;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.talkingtv.control.ChannelTuner;
import org.talkingtv.control.Controls;
import org.talkingtv.control.DirecTvShef;
import org.talkingtv.control.InputSwitcher;
import org.talkingtv.control.TvInputs;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The talking home screen.
 *
 * Remote keys:
 *   Up / Down      move through favorites; each one is read aloud
 *   OK             go to the highlighted favorite
 *   0-9            type a channel number (or a favorite's shortcut number); it goes after a short pause
 *   Channel + / -  next / previous DirecTV channel
 *   Right or Info  say what is on now
 *   Left           say where you are
 *   Menu           setup screen for a helper (phone browser)
 *   Back           show the favorites again; press twice to leave the app
 */
public final class MainActivity extends Activity implements SetupServer.Host {
    private static final long PANEL_HIDE_MS = 8000;
    private static final long WHATS_ON_DELAY_MS = 3000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ConfigStore store;
    private volatile Config config;
    private Speaker speaker;
    private AppLauncher launcher;
    private ChannelTuner tuner;
    private InputSwitcher inputSwitcher;

    private TvView tvView;
    private View panel;
    private ListView list;
    private TextView status;
    private TextView setupText;
    private ArrayAdapter<Favorite> adapter;

    private final StringBuilder digits = new StringBuilder();
    private final Runnable commitDigits = this::commitDigits;
    private final Runnable hidePanel = this::hidePanelIfWatching;
    private long lastBackPress;
    private boolean watchingBox;
    private String currentlyOn = "the favorites menu";

    private SetupServer setupServer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvView = findViewById(R.id.tv_view);
        panel = findViewById(R.id.panel);
        list = findViewById(R.id.favorites);
        status = findViewById(R.id.status);
        setupText = findViewById(R.id.setup_text);

        store = new ConfigStore(this);
        speaker = new Speaker(this);
        launcher = new AppLauncher(this);

        adapter = new ArrayAdapter<>(this, R.layout.item_favorite, android.R.id.text1);
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                speaker.sayUnlessScreenReader(adapter.getItem(position).spokenLabel());
                keepPanelUp();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> activate(adapter.getItem(position)));

        applyConfig(store.load());
        speaker.say("Talking TV. " + (adapter.isEmpty()
                ? "No favorites yet. Press Menu to set up."
                : "Up and down to choose, OK to watch."));
    }

    private void applyConfig(Config c) {
        if (inputSwitcher != null) inputSwitcher.hideBox();
        config = c;
        speaker.setRate(c.speechRate);
        tuner = Controls.tuner(c);
        inputSwitcher = Controls.inputSwitcher(c, this, tvView, io);
        watchingBox = false;
        adapter.clear();
        adapter.addAll(c.favorites);
        showPanel();
    }

    @Override protected void onResume() {
        super.onResume();
        // Coming back from YouTube TV or Netflix: show the menu again.
        if (!watchingBox) showPanel();
    }

    @Override protected void onDestroy() {
        stopSetup();
        if (inputSwitcher != null) inputSwitcher.hideBox();
        speaker.shutdown();
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- remote keys

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();

        if (setupServer != null) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) {
                stopSetup();
                speaker.say("Setup closed.");
                return true;
            }
            return true;
        }

        int digit = digitFor(k);
        if (digit >= 0) {
            digits.append(digit);
            speaker.say(String.valueOf(digit));
            main.removeCallbacks(commitDigits);
            main.postDelayed(commitDigits, config.digitWaitMs);
            return true;
        }
        switch (k) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
                channelStep(true);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                channelStep(false);
                return true;
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (e.getRepeatCount() == 0) whatsOn(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (e.getRepeatCount() == 0) speaker.say("You are on " + currentlyOn + ".");
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_PROG_BLUE:
                startSetup();
                return true;
            case KeyEvent.KEYCODE_BACK:
                onBackKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (panel.getVisibility() != View.VISIBLE) {
                    // First press while watching just brings the menu back and reads the highlighted item.
                    showPanel();
                    Favorite f = selected();
                    speaker.say("Favorites. " + (f == null ? "Empty." : f.spokenLabel()));
                    return true;
                }
                keepPanelUp();
                return super.dispatchKeyEvent(e);
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private static int digitFor(int k) {
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return k - KeyEvent.KEYCODE_0;
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return k - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    private void onBackKey() {
        if (panel.getVisibility() != View.VISIBLE) {
            showPanel();
            speaker.say("Favorites.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 3000) {
            speaker.say("Leaving Talking TV.");
            if (inputSwitcher != null) inputSwitcher.hideBox();
            watchingBox = false;
            moveTaskToBack(true);
        } else {
            lastBackPress = now;
            speaker.say("Press back again to leave Talking TV.");
        }
    }

    private void commitDigits() {
        String typed = digits.toString();
        digits.setLength(0);
        if (typed.isEmpty()) return;
        for (Favorite f : config.favorites) {
            if (!f.number.isEmpty() && f.number.equals(typed)) {
                activate(f);
                return;
            }
        }
        if (tuner == null) {
            speaker.say("No favorite has the number " + typed + ".");
            return;
        }
        int channel = Integer.parseInt(typed.length() > 5 ? typed.substring(0, 5) : typed);
        String label = "channel " + channel;
        for (Favorite f : config.favorites) {
            if (Favorite.GENIE.equals(f.type) && f.channel == channel) label = f.name + ", channel " + channel;
        }
        tuneGenie(channel, label);
    }

    // ---------------------------------------------------------------- actions

    private Favorite selected() {
        int pos = list.getSelectedItemPosition();
        if (pos < 0 && !adapter.isEmpty()) pos = 0;
        return pos < 0 ? null : adapter.getItem(pos);
    }

    private void activate(Favorite f) {
        if (f == null) return;
        switch (f.type) {
            case Favorite.GENIE:
                tuneGenie(f.channel, f.name);
                break;
            case Favorite.YOUTUBE_TV:
                leaveBox();
                if (!launcher.isInstalled(AppLauncher.YOUTUBE_TV)) {
                    speaker.say("YouTube TV is not installed on this TV.");
                } else if (f.url.isEmpty()) {
                    speaker.say("Opening YouTube TV. The link for " + f.name + " is not set up yet.");
                    launcher.open(AppLauncher.YOUTUBE_TV);
                } else if (launcher.openLink(f.url, AppLauncher.YOUTUBE_TV)) {
                    speaker.say("Opening " + f.name + " on YouTube TV.");
                } else {
                    speaker.say("The channel link did not work. Opening YouTube TV instead.");
                    launcher.open(AppLauncher.YOUTUBE_TV);
                }
                currentlyOn = f.name + " on YouTube TV";
                break;
            case Favorite.LINK:
                leaveBox();
                if (launcher.openLink(f.url, f.packageName)) {
                    speaker.say("Opening " + f.name + ".");
                    currentlyOn = f.name;
                } else {
                    speaker.say("I could not open " + f.name + ".");
                }
                break;
            case Favorite.APP:
            default:
                leaveBox();
                if (launcher.open(f.packageName)) {
                    speaker.say("Opening " + f.name + ".");
                    currentlyOn = f.name;
                } else {
                    speaker.say(f.name + " is not installed on this TV.");
                }
                break;
        }
    }

    private void leaveBox() {
        main.removeCallbacks(hidePanel);
        inputSwitcher.hideBox();
        watchingBox = false;
    }

    private void tuneGenie(int channel, String label) {
        if (tuner == null) {
            speaker.say("DirecTV is not set up yet. Press Menu to set up.");
            return;
        }
        speaker.say("Tuning " + label + ".");
        setStatus("Tuning " + label + "…");
        final ChannelTuner t = tuner;
        io.execute(() -> {
            try {
                t.tune(channel);
                main.post(() -> {
                    currentlyOn = label;
                    showBoxThenSayWhatsOn();
                });
            } catch (Exception ex) {
                main.post(() -> {
                    setStatus("Could not reach DirecTV");
                    speaker.say("I could not reach DirecTV. Check that the Genie is on.");
                });
            }
        });
    }

    private void channelStep(boolean up) {
        if (tuner == null) {
            speaker.say("DirecTV is not set up yet.");
            return;
        }
        speaker.say(up ? "Channel up." : "Channel down.");
        final ChannelTuner t = tuner;
        io.execute(() -> {
            try {
                if (up) t.channelUp(); else t.channelDown();
                main.post(this::showBoxThenSayWhatsOn);
            } catch (Exception ex) {
                main.post(() -> speaker.say("I could not reach DirecTV."));
            }
        });
    }

    private void showBoxThenSayWhatsOn() {
        inputSwitcher.showBox(new InputSwitcher.Callback() {
            @Override public void onShown() {
                watchingBox = true;
                setStatus("Watching " + currentlyOn);
                if (inputSwitcher.keepsRemote()) keepPanelUp();
                main.postDelayed(() -> whatsOn(false), WHATS_ON_DELAY_MS);
            }

            @Override public void onFailed(String spokenReason) {
                speaker.say(spokenReason);
                setStatus(spokenReason);
            }
        });
    }

    private void whatsOn(boolean asked) {
        if (tuner == null || !watchingBox) {
            if (asked) speaker.say("You are on " + currentlyOn + ".");
            return;
        }
        final ChannelTuner t = tuner;
        io.execute(() -> {
            String text;
            try {
                text = "Now on " + t.whatsOn();
            } catch (Exception ex) {
                text = asked ? "I could not ask DirecTV what is on." : null;
            }
            final String say = text;
            if (say != null) main.post(() -> {
                speaker.say(say);
                setStatus(say);
            });
        });
    }

    // ---------------------------------------------------------------- panel

    private void showPanel() {
        panel.setVisibility(View.VISIBLE);
        if (!list.hasFocus()) list.requestFocus();
        if (list.getSelectedItemPosition() < 0 && !adapter.isEmpty()) list.setSelection(0);
        keepPanelUp();
    }

    private void keepPanelUp() {
        main.removeCallbacks(hidePanel);
        if (watchingBox && inputSwitcher.keepsRemote()) main.postDelayed(hidePanel, PANEL_HIDE_MS);
    }

    private void hidePanelIfWatching() {
        if (watchingBox && inputSwitcher.keepsRemote()) panel.setVisibility(View.GONE);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ---------------------------------------------------------------- setup

    private void startSetup() {
        if (setupServer != null) return;
        String pin = String.format(Locale.US, "%04d", new SecureRandom().nextInt(10000));
        String ip = NetInfo.localIp();
        setupServer = new SetupServer(this, pin);
        try {
            setupServer.start();
        } catch (Exception ex) {
            setupServer = null;
            speaker.say("Setup could not start.");
            return;
        }
        String address = ip == null ? "this TV's address" : "http://" + ip + ":" + SetupServer.PORT;
        setupText.setText("Setup\n\nOn a phone or computer on the same Wi-Fi, open\n" + address
                + "\n\nPIN: " + pin + "\n\nPress Back when finished.");
        setupText.setVisibility(View.VISIBLE);
        setupText.requestFocus();
        String spokenPin = pin.replace("", " ").trim();
        speaker.say("Setup is open. A helper can open " + (ip == null ? "this TV's address" : ip.replace(".", " dot "))
                + ", port " + SetupServer.PORT + ", on a phone. The PIN is " + spokenPin
                + ". Press back when finished.");
    }

    private void stopSetup() {
        if (setupServer == null) return;
        setupServer.stop();
        setupServer = null;
        setupText.setVisibility(View.GONE);
        showPanel();
    }

    @Override public String currentConfigJson() {
        return config.prettyJson();
    }

    @Override public String defaultConfigJson() {
        return store.loadDefault().prettyJson();
    }

    @Override public String saveConfig(String json) {
        try {
            Config saved = store.save(json);
            config = saved;
            main.post(() -> {
                applyConfig(saved);
                speaker.say("New settings saved.");
            });
            return null;
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    @Override public String genieLocations(String serverIp) {
        try {
            return DirecTvShef.locations(serverIp)
                    + "\n\nCopy the clientAddr of this room's Mini into genie.clientAddr.";
        } catch (Exception ex) {
            return "Could not reach the HS17 at " + serverIp + ": " + ex.getMessage()
                    + "\nCheck the address, and that Whole-Home External Device access is allowed.";
        }
    }

    @Override public String tvInputs() {
        return TvInputs.describe(this);
    }
}
