package org.talkingtv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A small web page on the TV, so a sighted helper can set things up from a phone.
 * Runs only while the setup screen is open on the TV, and saving needs the PIN shown there.
 */
public final class SetupServer {
    public static final int PORT = 8765;

    public interface Host {
        String currentConfigJson();

        /** Returns null on success, or a readable error. */
        String saveConfig(String json);

        String defaultConfigJson();

        /** Raw answer from the HS17's location list, or an error message. */
        String genieLocations(String serverIp);

        String tvInputs();
    }

    private final Host host;
    private final String pin;
    private ServerSocket socket;
    private Thread thread;

    public SetupServer(Host host, String pin) {
        this.host = host;
        this.pin = pin;
    }

    public void start() throws IOException {
        socket = new ServerSocket(PORT);
        thread = new Thread(this::loop, "setup-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }

    private void loop() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            try {
                Socket client = s.accept();
                Thread t = new Thread(() -> handle(client), "setup-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(10000);
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = in.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                }
            }
            if (contentLength > 1_000_000) {
                send(c, 413, "text/plain", "Too large");
                return;
            }
            char[] body = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int n = in.read(body, read, contentLength - read);
                if (n < 0) break;
                read += n;
            }
            route(c, method, path, new String(body, 0, read));
        } catch (Exception ignored) {
        }
    }

    private void route(Socket c, String method, String path, String body) throws IOException {
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) {
            query = path.substring(q + 1);
            path = path.substring(0, q);
        }
        if (method.equals("GET") && path.equals("/")) {
            send(c, 200, "text/html; charset=utf-8", page(host.currentConfigJson(), null));
        } else if (method.equals("GET") && path.equals("/config.json")) {
            send(c, 200, "application/json; charset=utf-8", host.currentConfigJson());
        } else if (method.equals("GET") && path.equals("/default.json")) {
            send(c, 200, "application/json; charset=utf-8", host.defaultConfigJson());
        } else if (method.equals("GET") && path.equals("/minis")) {
            String ip = form(query).getOrDefault("ip", "");
            send(c, 200, "text/plain; charset=utf-8",
                    ip.isEmpty() ? "Enter the HS17 address first." : host.genieLocations(ip));
        } else if (method.equals("GET") && path.equals("/inputs")) {
            send(c, 200, "text/plain; charset=utf-8", host.tvInputs());
        } else if (method.equals("POST") && path.equals("/save")) {
            Map<String, String> f = form(body);
            String json = f.getOrDefault("config", "");
            String message;
            if (!pin.equals(f.getOrDefault("pin", "").trim())) {
                message = "Wrong PIN. Use the 4 digits shown on the TV.";
            } else {
                String err = host.saveConfig(json);
                message = err == null ? "Saved. The TV has the new settings." : "Not saved: " + err;
                if (err == null) json = host.currentConfigJson();
            }
            send(c, 200, "text/html; charset=utf-8", page(json, message));
        } else {
            send(c, 404, "text/plain", "Not found");
        }
    }

    private static Map<String, String> form(String s) {
        Map<String, String> m = new HashMap<>();
        for (String pair : s.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                m.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String page(String json, String message) {
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Talking TV setup</title><style>"
                + "body{font:17px system-ui,sans-serif;margin:16px;max-width:900px;background:#fff;color:#111}"
                + "textarea{width:100%;height:55vh;font:14px ui-monospace,monospace;box-sizing:border-box}"
                + "button,input{font-size:17px;padding:8px 12px;margin:4px 0}"
                + ".msg{padding:10px;background:#fff3b0;border:1px solid #c9a400}"
                + "pre{background:#f3f3f3;padding:8px;white-space:pre-wrap;word-break:break-all}"
                + "</style></head><body><h1>Talking TV setup</h1>"
                + (message == null ? "" : "<p class=msg role=status>" + esc(message) + "</p>")
                + "<form method=post action=/save>"
                + "<label for=config>Settings (JSON). See the README for every field.</label>"
                + "<textarea id=config name=config spellcheck=false>" + esc(json) + "</textarea>"
                + "<p><label>PIN shown on the TV <input name=pin inputmode=numeric size=6 autocomplete=off></label> "
                + "<button type=submit>Save to TV</button></p></form>"
                + "<h2>Helpers</h2>"
                + "<p><label>HS17 address <input id=ip placeholder=192.168.1.50></label> "
                + "<button onclick=\"show('/minis?ip='+encodeURIComponent(document.getElementById('ip').value))\">"
                + "Find Genie Minis</button> "
                + "<button onclick=\"show('/inputs')\">List TV HDMI inputs</button></p>"
                + "<pre id=out aria-live=polite></pre>"
                + "<p><a href=/config.json download=talking-tv-config.json>Download these settings</a> "
                + "(to copy to another TV) &middot; <a href=/default.json>Starting example</a></p>"
                + "<script>function show(u){var o=document.getElementById('out');o.textContent='Working...';"
                + "fetch(u).then(function(r){return r.text()}).then(function(t){o.textContent=t})"
                + ".catch(function(e){o.textContent=String(e)})}</script>"
                + "</body></html>";
    }

    private static void send(Socket c, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        OutputStream out = c.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }
}
