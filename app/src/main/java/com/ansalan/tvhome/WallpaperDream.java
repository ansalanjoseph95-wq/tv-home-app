package com.ansalan.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.dreams.DreamService;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import java.io.InputStream;

public class WallpaperDream extends DreamService {
    private ImageView iv;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try (InputStream in = getAssets().open("wallpaper.jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            iv.setImageBitmap(BitmapFactory.decodeStream(in, null, o));
        } catch (Exception e) { }
        setContentView(iv);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        // very slow zoom so the picture never sits perfectly still
        iv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90000).setInterpolator(new LinearInterpolator()).start();
    }

    @Override public void onDreamingStopped() {
        super.onDreamingStopped();
        iv.animate().cancel();
    }
}
