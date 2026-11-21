# Talking TV

A simple, talking home screen for blind and low-vision TV viewers, for Android TV and
Google TV (built first for Sony Bravia Google TVs). Every button press is spoken.
Pick a favorite and the app changes the channel for you: on a DirecTV Genie Mini, in
YouTube TV, or by opening Netflix or Paramount+.

Free and open source (MIT). Works with TalkBack on or off.

## What it does

| Press on the remote | What happens |
| --- | --- |
| Up / Down | Moves through your favorites and reads each one aloud |
| OK | Goes to that favorite |
| Number keys (2, 0, 6) | Tunes DirecTV channel 206 after a short pause, or jumps to the favorite with that shortcut number |
| Channel + / − | Next or previous DirecTV channel |
| Right (or Info) | Says what is on now, read from the DirecTV Genie |
| Left | Says where you are |
| Back | Brings the favorites back; press twice to leave the app |
| Menu | Opens setup so a helper can change settings from a phone |

When you pick a DirecTV channel, the app tunes the Genie Mini over your home network,
shows the Mini's HDMI picture **inside the app** (so the remote keeps working here),
and then says what is playing.

## What you need

- An Android TV or Google TV (tested target: Sony K75XR70M2 and K-50XR30M2).
- For DirecTV: a Genie server (HS17 or an HR-series Genie DVR) with Genie Minis, and the
  Mini connected to an HDMI port on the TV.
- For YouTube TV, Netflix, Paramount+: those apps installed on the TV.

## Install (one time, about 10 minutes)

1. **Turn on developer options on the TV.** Settings → System → About → click
   *Android TV OS build* seven times. Then Settings → System → Developer options →
   turn on **USB debugging** (and **Network debugging** / wireless debugging if shown).
2. **Download** `talking-tv.apk` from this repo's
   [Releases page](../../releases/latest) onto a computer on the same Wi-Fi.
3. **Install it** with [ADB](https://developer.android.com/tools/releases/platform-tools)
   (find the TV's address in Settings → Network):
   ```
   adb connect 192.168.1.23
   adb install -r talking-tv.apk
   ```
   Accept the "Allow debugging" prompt on the TV the first time.
   (No computer? The *Downloader* app on the TV can install the APK from the Releases link.)
4. **Open Talking TV** from the TV's Apps row. Move it to the front of the Favorites row so
   it is one press from Home.

Updates install the same way; your settings are kept.

## Set up (from a phone)

1. **On the DirecTV system**, allow network control once: Menu → Settings → Whole-Home →
   External Device → **Allow** (wording varies by model). Give the HS17 a fixed address in
   your router so it doesn't change.
2. In Talking TV press **Menu** on the remote. The TV shows (and says) an address such as
   `http://192.168.1.23:8765` and a 4-digit PIN.
3. Open that address on a phone or computer on the same Wi-Fi. You'll see the settings
   as text (JSON) with helper buttons:
   - **Find Genie Minis**: type the HS17's address and press it. Copy this room's Mini
     `clientAddr` into `genie.clientAddr`.
   - **List TV HDMI inputs**: shows the TV's HDMI inputs, if `hdmiPort` alone doesn't pick the right one.
4. Edit, type the PIN, press **Save to TV**. The TV says "New settings saved."
5. Press **Back** on the remote to close setup. Setup only listens while that screen is open.

Use **Download these settings** to copy a working setup to the next TV or household.

### Settings reference

```jsonc
{
  "speechRate": 1.0,          // 0.5 = slower, 1.5 = faster
  "digitWaitMs": 1800,        // pause after the last number key before tuning

  "genie": {
    "serverIp": "192.168.1.50",   // the HS17 (or main Genie DVR)
    "clientAddr": "A1B2C3D4E5F6"  // this room's Mini; "0" or empty = the main receiver
  },

  "tvInput": {
    "method": "tvview",   // tvview | intent | bravia | none   (see below)
    "hdmiPort": 2,        // the HDMI port the Mini is plugged into
    "inputId": "",        // exact id from "List TV HDMI inputs", only if needed
    "braviaIp": "127.0.0.1",
    "braviaPsk": ""       // Sony pre-shared key, only for method "bravia"
  },

  "favorites": [
    { "name": "ESPN",     "type": "genie",     "channel": 206, "number": "" },
    { "name": "NBC 4",    "type": "youtubetv", "url": "https://tv.youtube.com/watch/XXXX", "number": "4" },
    { "name": "Netflix",  "type": "app",       "package": "com.netflix.ninja" }
  ]
}
```

**Favorite types**

- `genie`: a DirecTV channel number.
- `youtubetv`: a YouTube TV live channel. To get the `url`, open
  [tv.youtube.com](https://tv.youtube.com) in a computer browser, start that channel's
  live stream, and copy the address (it looks like `https://tv.youtube.com/watch/...`).
  Leave it empty to just open YouTube TV.
- `app`: opens an app. Common packages: YouTube TV `com.google.android.youtube.tvunplugged`,
  Netflix `com.netflix.ninja`, Paramount+ `com.cbs.ott`, DirecTV `com.att.tv`.
- `link`: any other deep link, with an optional `package`.

`number` is an optional shortcut: typing it on the remote opens that favorite instead of
tuning that DirecTV channel number.

**Ways to show the Genie Mini (`tvInput.method`)**

- `tvview` (default, best): shows the HDMI picture inside Talking TV, so number keys,
  channel keys and "what's on" keep working while watching. If the TV refuses, the app
  falls back to `intent` automatically.
- `intent`: asks the TV to switch to that HDMI input. The TV's own input screen then has
  the remote; press Home and open Talking TV to change channels.
- `bravia`: Sony's network control. On the TV: Settings → Network → Remote device settings
  → Control remotely **on**, Authentication **Normal and Pre-Shared Key**, set a key, and put
  the same key in `braviaPsk`.
- `none`: don't switch inputs (for TVs left on the Mini's input or switched by HDMI-CEC).

## Troubleshooting

- **"I could not reach DirecTV"**: check `serverIp`, that External Device access is
  allowed, and open `http://<HS17 address>:8080/info/getOptions` in a browser on the same Wi-Fi.
- **Picture doesn't switch**: try `"method": "intent"`, then check `hdmiPort` with *List TV HDMI inputs*.
- **YouTube TV opens on its home screen**: the channel link may have changed; copy it again.
  YouTube TV links are not officially supported and can stop working after app updates.
- **Talks twice with TalkBack on**: the app stays quiet on focus moves when TalkBack is
  on and only speaks results; tell us if you still hear doubles.

## For developers

Plain Java, no libraries, `minSdk 26`. Open in Android Studio or run `./gradlew assembleDebug`.
GitHub Actions builds every push and publishes `talking-tv.apk` to the `latest` release.

Device control is pluggable (`app/src/main/java/org/talkingtv/control/`):

- `ChannelTuner`: changes channels on a box. `DirecTvShef` talks to DirecTV's SHEF API on port 8080.
- `InputSwitcher`: puts the box on screen. `TvViewInput`, `IntentInput`, `BraviaIpControl`, `NoInputSwitch`.
  A Samsung (Tizen network remote) switcher for Google TV Streamers is a planned addition.
- `Controls`: picks implementations from the settings.

APKs are signed with a **public** key committed in `app/talkingtv-public.keystore` so each build
can update the last. Anyone can sign with it, so only install builds from this repo's
Releases. A private key will be used for the Play Store.

Planned: voice ("ESPN") with the remote's mic button, Samsung input switching, Play Store listing.
