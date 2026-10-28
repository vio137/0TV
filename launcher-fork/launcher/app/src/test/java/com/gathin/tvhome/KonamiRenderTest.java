package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

/** Sends the real key events through MainActivity and renders before, after the sequence, and after it again. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(qualifiers = "w960dp-h540dp-land-xhdpi")
public class KonamiRenderTest {
    private static void press(Activity a, int code) {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private static void sequence(Activity a) {
        for (int c : KonamiCode.SEQUENCE) press(a, c);
    }

    private static void snap(Activity a, String name) throws Exception {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File out = new File(System.getProperty("konamiOut", "build"), name + ".png");
        try (FileOutputStream f = new FileOutputStream(out)) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }

    private static boolean overlayOn(Activity a) {
        View v = findOverlay(a.getWindow().getDecorView());
        return v != null && v.getVisibility() == View.VISIBLE;
    }

    private static View findOverlay(View v) {
        if (v instanceof CrackedScreenView) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View r = findOverlay(g.getChildAt(i)); if (r != null) return r; }
        }
        return null;
    }

    @Test public void sequenceTogglesTheCrackAndAgainRestores() throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        java.util.Calendar fx = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        fx.clear(); fx.set(2026, java.util.Calendar.OCTOBER, 28, 19, 30, 0);
        android.os.SystemClock.setCurrentTimeMillis(fx.getTimeInMillis());
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        snap(a, "konami-1-before");
        assertFalse(overlayOn(a));
        sequence(a);
        snap(a, "konami-2-cracked");
        assertTrue(overlayOn(a));
        // a wrong sequence must not change anything
        press(a, KeyEvent.KEYCODE_DPAD_UP); press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(overlayOn(a));
        sequence(a);
        snap(a, "konami-3-restored");
        assertFalse(overlayOn(a));
    }
}
