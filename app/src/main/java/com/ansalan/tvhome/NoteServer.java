package com.ansalan.tvhome;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class NoteServer extends Thread {
    private final Context ctx;
    private final Runnable onReady;
    private volatile String img = "";
    private volatile int ver = 0;
    private volatile int port = 8080;

    NoteServer(Context c, Runnable ready) { ctx = c; onReady = ready; setDaemon(true); }

    int port() { return port; }

    @Override public void run() {
        ServerSocket ss = null;
        for (int p = 8080; p <= 8089 && ss == null; p++) {
            try {
                ServerSocket t = new ServerSocket();
                t.setReuseAddress(true);
                t.bind(new InetSocketAddress(p));
                ss = t; port = p;
            } catch (Exception e) { Log.w("TvHome", "port " + p + " busy: " + e); }
        }
        if (ss == null) { Log.e("TvHome", "no free port"); return; }
        Log.i("TvHome", "server on " + port);
        if (onReady != null) onReady.run();
        try {
            while (true) { final Socket s = ss.accept(); new Thread(() -> handle(s)).start(); }
        } catch (Exception e) { Log.e("TvHome", "server stopped: " + e); }
    }

    private String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) != -1 && c != '\n') if (c != '\r') sb.append((char) c);
        return (c == -1 && sb.length() == 0) ? null : sb.toString();
    }

    private byte[] asset(String n) throws IOException {
        InputStream in = ctx.getAssets().open(n);
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int r;
        while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
        in.close(); return b.toByteArray();
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String first = line(in);
            if (first == null) { s.close(); return; }
            String[] p = first.split(" ");
            int len = 0; String h;
            while ((h = line(in)) != null && !h.isEmpty())
                if (h.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(h.substring(15).trim());
            if (len > 3000000) { s.close(); return; }
            byte[] body = new byte[len]; int r = 0;
            while (r < len) { int n = in.read(body, r, len - r); if (n < 0) break; r += n; }
            String path = p[1].split("\\?")[0], type = "text/html; charset=utf-8"; byte[] out;
            if (p[0].equals("POST") && path.equals("/note")) {
                String d = new String(body, "UTF-8");
                if (d.matches("data:image/png;base64,[A-Za-z0-9+/=]+")) { img = d; ver++; }
                out = "ok".getBytes(); type = "text/plain";
            } else if (path.equals("/note")) {
                out = ("{\"v\":" + ver + ",\"img\":\"" + img + "\"}").getBytes("UTF-8"); type = "application/json";
            } else if (path.equals("/wallpaper.jpg")) {
                out = asset("wallpaper.jpg"); type = "image/jpeg";
            } else {
                out = asset(path.equals("/draw") ? "draw.html" : "index.html");
            }
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + out.length
                    + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes());
            o.write(out); o.flush(); s.close();
        } catch (Exception e) { try { s.close(); } catch (Exception x) { } }
    }
}
