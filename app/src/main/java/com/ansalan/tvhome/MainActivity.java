package com.ansalan.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String TAG = "TvHome";
    private WebView web;
    private NoteServer server;
    private int retries = 0;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF070B1A);
        web.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override public void onReceivedError(WebView v, int code, String desc, String url) {
                if (url == null || !url.startsWith("http://127.0.0.1")) return;
                Log.e(TAG, "page error " + code + " " + desc);
                if (retries++ < 5) v.postDelayed(MainActivity.this::loadHome, 1000);
                else v.loadData("<body style='background:#070b1a;color:#fff;font:28px sans-serif;padding:40px'>TV Home could not load the page.<br>Restart the TV or reinstall the app.</body>", "text/html", "UTF-8");
            }
        });
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public String ip() {
                try {
                    for (java.util.Enumeration<java.net.NetworkInterface> e = java.net.NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();)
                        for (java.util.Enumeration<java.net.InetAddress> a = e.nextElement().getInetAddresses(); a.hasMoreElements();) {
                            java.net.InetAddress i = a.nextElement();
                            if (!i.isLoopbackAddress() && i instanceof java.net.Inet4Address) return i.getHostAddress();
                        }
                } catch (Exception ex) { }
                return "TV-IP";
            }
            @JavascriptInterface public void launch(String key) {
                Intent i = intentFor(key);
                try {
                    if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
                } catch (Exception e) { Log.e(TAG, "launch failed", e); }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "App not installed", Toast.LENGTH_SHORT).show());
            }
            @JavascriptInterface public void jarvis() { runOnUiThread(MainActivity.this::startJarvis); }
        }, "Android");
        setContentView(web);
        web.requestFocus();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) { tts.setLanguage(Locale.US); ttsReady = true; }
            else Log.e(TAG, "TTS init failed " + status);
        });

        server = new NoteServer(this, () -> runOnUiThread(this::loadHome));
        server.start();
    }

    private void loadHome() { web.loadUrl("http://127.0.0.1:" + server.port() + "/"); }

    // ---------------- Jarvis ----------------
    private void ui(String state, String text) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.jarvisUI&&jarvisUI(" + JSONObject.quote(state) + "," + JSONObject.quote(text) + ")", null));
    }

    private void reply(String text) {
        ui("speaking", text);
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    private void startJarvis() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reply("Speech recognition is not installed on this TV.");
            return;
        }
        if (tts != null) tts.stop();
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(android.os.Bundle p) { }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float v) { }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() { }
                public void onEvent(int t, android.os.Bundle p) { }
                public void onPartialResults(android.os.Bundle p) {
                    ArrayList<String> r = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty()) ui("listening", r.get(0));
                }
                public void onResults(android.os.Bundle p) {
                    ArrayList<String> r = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty()) handle(r.get(0));
                    else reply("I didn't catch that.");
                }
                public void onError(int e) {
                    Log.w(TAG, "speech error " + e);
                    if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        reply("I didn't hear anything. Press the mic and try again.");
                    else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                        reply("I need microphone permission.");
                    else if (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                        reply("I need an internet connection to understand you.");
                    else reply("Sorry, something went wrong. Error " + e);
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        ui("listening", "Listening…");
        sr.startListening(i);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startJarvis();
            else reply("I need microphone permission to hear you.");
        }
    }

    private static final String[] JOKES = {
        "Why did the TV go to school? To get a little brighter.",
        "I told my remote a secret. Now it keeps changing the subject.",
        "Why don't scientists trust stairs? They're always up to something."
    };

    private void handle(String raw) {
        ui("heard", raw);
        String t = raw.toLowerCase(Locale.US).trim().replaceAll("^(hey |ok |okay )?jarvis[, ]*", "").replaceAll("[.?!]+$", "");
        Matcher m;
        if (t.isEmpty()) { reply("Yes?"); return; }
        if ((m = Pattern.compile("^(?:open|launch|start|run|go to)\\s+(.+)$").matcher(t)).find()) { openApp(m.group(1)); return; }
        if ((m = Pattern.compile("^(?:search\\s+)?(?:on\\s+)?youtube\\s+(?:for\\s+)?(.+)$").matcher(t)).find()) { youtubeSearch(m.group(1)); return; }
        if ((m = Pattern.compile("^play\\s+(.+?)(?:\\s+on youtube)?$").matcher(t)).find()) { youtubeSearch(m.group(1)); return; }
        if ((m = Pattern.compile("^(?:search|look up|google|find)(?:\\s+for)?\\s+(.+)$").matcher(t)).find()) {
            String q = m.group(1);
            if (q.endsWith(" on youtube")) { youtubeSearch(q.substring(0, q.length() - 11)); return; }
            webSearch(q); return;
        }
        if (t.contains("time")) { reply("It's " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date())); return; }
        if (t.contains("date") || t.contains("what day") || t.contains("today")) {
            reply("Today is " + new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(new Date())); return; }
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (t.contains("volume up") || t.contains("louder")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); reply("Volume up."); return; }
        if (t.contains("volume down") || t.contains("quieter")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); reply("Volume down."); return; }
        if (t.equals("mute") || t.contains("be quiet")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); reply("Muted."); return; }
        if (t.equals("unmute")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); reply("Unmuted."); return; }
        if (t.contains("settings")) { openApp("settings"); return; }
        if (t.contains("who are you") || t.contains("your name")) { reply("I'm Jarvis, your TV assistant."); return; }
        if (t.contains("joke")) { reply(JOKES[new Random().nextInt(JOKES.length)]); return; }
        if (t.contains("thank")) { reply("You're welcome."); return; }
        if (t.equals("hello") || t.equals("hi") || t.equals("hey")) { reply("Hello! How can I help?"); return; }
        if (t.contains("help") || t.contains("what can you do")) {
            reply("Try: open YouTube, open browser, open Play Store, search for something, what time is it, or volume up."); return; }
        reply("Sorry, I can't do that yet. Try saying open YouTube, or search for something.");
    }

    private void openApp(String name) {
        name = name.replaceAll("^the ", "").replaceAll(" app$", "").trim();
        String key;
        switch (name) {
            case "play store": case "google play": case "google play store": case "playstore": case "store":
                key = "com.android.vending|name:play store"; break;
            case "browser": case "web browser": case "internet": case "chrome": case "the web":
                key = "#browser"; break;
            case "settings": case "setting": key = "#settings"; break;
            case "youtube": key = "com.google.android.youtube.tv|name:youtube"; break;
            case "netflix": key = "com.netflix.ninja|name:netflix"; break;
            case "prime": case "prime video": case "amazon prime": key = "com.amazon.amazonvideo.livingroom|name:prime"; break;
            case "spotify": key = "com.spotify.tv.android|name:spotify"; break;
            case "apple tv": key = "com.apple.atve.androidtv.appletv|name:apple tv"; break;
            case "hd streamz": case "hdstreamz": key = "name:hdstreamz"; break;
            default: key = "name:" + name;
        }
        Intent i = intentFor(key);
        if (i == null) { reply("I couldn't find " + name + " on this TV."); return; }
        reply("Opening " + name + ".");
        final Intent go = i;
        web.postDelayed(() -> {
            try { go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(go); }
            catch (Exception e) { Log.e(TAG, "open failed", e); }
        }, 900);
    }

    private void youtubeSearch(String q) {
        reply("Searching YouTube for " + q + ".");
        final Uri u = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q));
        web.postDelayed(() -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e2) { ui("speaking", "No app can open YouTube search."); }
            }
        }, 900);
    }

    private void webSearch(String q) {
        final Uri u = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q));
        Intent v = new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v.resolveActivity(getPackageManager()) == null) { reply("There is no browser to search with."); return; }
        reply("Searching for " + q + ".");
        web.postDelayed(() -> { try { startActivity(v); } catch (Exception e) { Log.e(TAG, "search failed", e); } }, 900);
    }

    // ---------------- App launching ----------------
    // key may hold several options separated by "|"; the first that works is used
    private Intent intentFor(String keys) {
        for (String k : keys.split("\\|")) {
            Intent i = intentForOne(k.trim());
            if (i != null) return i;
        }
        return null;
    }

    private Intent named(String n) {
        PackageManager pm = getPackageManager();
        n = n.toLowerCase().replace(" ", "");
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String label = ri.loadLabel(pm).toString().toLowerCase().replace(" ", "");
                if (label.contains(n) || ri.activityInfo.packageName.toLowerCase().contains(n)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                    i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return i;
                }
            }
        }
        return null;
    }

    private Intent intentForOne(String key) {
        PackageManager pm = getPackageManager();
        if (key.equals("#settings")) return new Intent(Settings.ACTION_SETTINGS);
        if (key.equals("#browser")) {
            for (String n : new String[]{"browser", "puffin", "chrome", "firefox"}) {
                Intent i = named(n);
                if (i != null) return i;
            }
            Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            return v.resolveActivity(pm) != null ? v : null;
        }
        if (key.startsWith("name:")) return named(key.substring(5));
        Intent i = pm.getLeanbackLaunchIntentForPackage(key);
        return i != null ? i : pm.getLaunchIntentForPackage(key);
    }

    // Mic / search / assistant buttons on the remote start Jarvis; Back does nothing on a home screen
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOICE_ASSIST || k == KeyEvent.KEYCODE_ASSIST || k == KeyEvent.KEYCODE_SEARCH) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) startJarvis();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed() { }

    @Override protected void onPause() { super.onPause(); if (sr != null) sr.cancel(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (sr != null) sr.destroy();
        if (tts != null) tts.shutdown();
    }
}
