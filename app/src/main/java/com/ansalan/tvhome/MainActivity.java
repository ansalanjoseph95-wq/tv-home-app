package com.ansalan.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new NoteServer(this).start();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setBackgroundColor(0xFF070B1A);
        web.setWebViewClient(new WebViewClient());
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
                } catch (Exception e) { }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "App not installed", Toast.LENGTH_SHORT).show());
            }
        }, "Android");
        setContentView(web);
        web.postDelayed(() -> web.loadUrl("http://127.0.0.1:8080/"), 400);
        web.requestFocus();
    }

    private Intent intentFor(String key) {
        PackageManager pm = getPackageManager();
        if (key.equals("#settings")) return new Intent(Settings.ACTION_SETTINGS);
        if (key.startsWith("name:")) {
            String n = key.substring(5).toLowerCase().replace(" ", "");
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
        Intent i = pm.getLeanbackLaunchIntentForPackage(key);
        return i != null ? i : pm.getLaunchIntentForPackage(key);
    }

    // Home screens must not exit on Back
    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(k, e);
    }
    @Override public void onBackPressed() { }
}
