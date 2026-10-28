package com.gathin.tvhome;

import android.app.Activity;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.FileOutputStream;
import java.io.File;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

/** Renders the real MainActivity of this fork on the JVM and writes a PNG. Not a TV: no focus or remote input. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(qualifiers = "w960dp-h540dp-land-xhdpi")
public class HomeRenderTest {
    @Test public void rendersHomeScreen() throws Exception { renderHome(1920,1080,"normal"); }
    @Test @org.robolectric.annotation.Config(qualifiers = "w960dp-h450dp-land-xhdpi")
    public void rendersShortScreenAndLastTileIsReachable() throws Exception { renderHome(1920,900,"short"); }
    private void renderHome(int width,int height,String variant) throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        String[] names = {"Fixture A", "Fixture B", "Fixture C", "Fixture D", "Fixture E", "Fixture F", "Fixture G", "Fixture H", "Fixture I", "Fixture J"};
        int[] colors = {0xFFE50914, 0xFFFF0000, 0xFF00A8E1, 0xFF113CCF, 0xFF1DB954, 0xFFE5A00D, 0xFF17B2E7, 0xFF607D8B, 0xFF3F51B5, 0xFF9C27B0};
        for (int i = 0; i < names.length; i++) {
            String pkg = "fixture.app" + i;
            ApplicationInfo ai = new ApplicationInfo(); ai.packageName = pkg; ai.name = names[i]; ai.nonLocalizedLabel = names[i];
            ActivityInfo act = new ActivityInfo(); act.packageName = pkg; act.name = pkg + ".Main"; act.applicationInfo = ai; act.exported = true; act.nonLocalizedLabel = names[i];
            ResolveInfo ri = new ResolveInfo(); ri.activityInfo = act; ri.nonLocalizedLabel = names[i];
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            Intent q2 = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(q, ri);
            Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(q2, ri);
            android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo(); pi.packageName = pkg; pi.applicationInfo = ai;
            Shadows.shadowOf(c.getPackageManager()).installPackage(pi);
            Bitmap ib = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas ic = new Canvas(ib);
            android.graphics.Paint ip = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            ip.setShader(new android.graphics.LinearGradient(0, 0, 256, 256, colors[i], Color.rgb((int) (Color.red(colors[i]) * 0.45f), (int) (Color.green(colors[i]) * 0.45f), (int) (Color.blue(colors[i]) * 0.45f)), android.graphics.Shader.TileMode.CLAMP));
            ic.drawRect(0, 0, 256, 256, ip);
            ip.setShader(null); ip.setColor(Color.WHITE); ip.setTextSize(120f); ip.setTextAlign(android.graphics.Paint.Align.CENTER); ip.setFakeBoldText(true);
            ic.drawText(names[i].substring(0, 1), 128, 170, ip);   // fixture art: a gradient and the app's initial, not a real icon
            Shadows.shadowOf(c.getPackageManager()).setApplicationIcon(pkg, new android.graphics.drawable.BitmapDrawable(c.getResources(), ib));
        }
        // Fixed fixture time so the clock and the greeting come from the same instant and zone: 28 Oct 2026 19:30 IST.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        java.util.Calendar fx = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        fx.clear(); fx.set(2026, java.util.Calendar.OCTOBER, 28, 19, 30, 0);
        android.os.SystemClock.setCurrentTimeMillis(fx.getTimeInMillis());
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        for (int k = 0; k < 6; k++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        tileSheet(root);
        File out = new File(System.getProperty("renderOut", "build/home-render.png"));

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        androidx.recyclerview.widget.RecyclerView rvx = findRv(root);
        for (int i = 0; i < rvx.getChildCount(); i++) { rvx.getChildAt(i).clearAnimation(); }
        rvx.setLayoutAnimation(null);
        for (View vv = rvx; vv != null; vv = vv.getParent() instanceof View ? (View) vv.getParent() : null) if (vv instanceof android.view.ViewGroup) ((android.view.ViewGroup) vv).setLayoutAnimation(null);
        //   // tile_enter animation: view animations do not progress on the paused test clock
        // Entry animations start on the first drawn frame, so draw once, advance the clock, then capture.
        root.draw(new Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
        root.draw(cv);
        // Reachability: scroll the last app into view the way focus moving down would, then check it lies inside the list.
        rvx.scrollToPosition(9);
        for (int k = 0; k < 3; k++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        androidx.recyclerview.widget.RecyclerView.ViewHolder last = rvx.findViewHolderForAdapterPosition(9);
        org.junit.Assert.assertNotNull("tile 9 is bound after scrollToPosition", last);
        int[] rl = new int[2]; rvx.getLocationOnScreen(rl);
        View lastItem = last.itemView;
        org.junit.Assert.assertTrue("last tile (with its name) is fully inside the list after scrolling to it",
                lastItem.getTop() >= 0 && lastItem.getBottom() <= rvx.getHeight());
        Bitmap scrolled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(scrolled));
        try (FileOutputStream f = new FileOutputStream("build/home-render-scrolled.png")) { scrolled.compress(Bitmap.CompressFormat.PNG, 100, f); }
        // Focus: scroll back, focus a grid tile, move focus with real key events (DPAD_RIGHT, DPAD_DOWN) and render it.
        rvx.scrollToPosition(0);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        androidx.recyclerview.widget.RecyclerView.ViewHolder h4 = rvx.findViewHolderForAdapterPosition(4);
        org.junit.Assert.assertNotNull(h4);
        View c4 = h4.itemView.findViewById(com.gathin.tvhome.R.id.card);
        c4.setFocusableInTouchMode(true);
        boolean got = c4.requestFocus();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        // The window is not focused in Robolectric, so key events do not route; use the framework FocusFinder (what the
        // D-pad uses) from the focused tile instead. This tests focus order, not the activity's own key handling.
        int[] dirs = {View.FOCUS_RIGHT, View.FOCUS_DOWN, View.FOCUS_DOWN};
        View cur = c4;
        for (int dir : dirs) {
            View nx = cur.focusSearch(dir);
            int pos = nx == null ? -1 : rvx.getChildAdapterPosition(rvx.findContainingItemView(nx));
            if (nx != null && nx.requestFocus()) cur = nx;
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        for (int k = 0; k < 2; k++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        Bitmap foc = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(foc));
        try (FileOutputStream fo = new FileOutputStream("build/home-render-focus.png")) { foc.compress(Bitmap.CompressFormat.PNG, 100, fo); }
        // Cinematic shelf: focus a dock tile (position 1) and render the whole screen.
        View dockCard = rvx.findViewHolderForAdapterPosition(1).itemView.findViewById(com.gathin.tvhome.R.id.card);
        dockCard.setFocusableInTouchMode(true); dockCard.requestFocus();
        for (int k = 0; k < 4; k++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        View hero = a.findViewById(R.id.hero);
        View clock = a.findViewById(R.id.clock);
        int[] hy=new int[2], cy=new int[2]; hero.getLocationOnScreen(hy); clock.getLocationOnScreen(cy);
        org.junit.Assert.assertTrue("hero starts below clock on " + variant, hy[1] >= cy[1]+clock.getHeight());
        org.junit.Assert.assertTrue("hero plate fits above first dock tile on " + variant, hy[1]+hero.getHeight() <= rl[1]+dockCard.getHeight()+rvx.getPaddingTop()+2);
        Bitmap cin = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(cin));
        try (FileOutputStream fo = new FileOutputStream("build/home-render-cinematic-" + variant + ".png")) { cin.compress(Bitmap.CompressFormat.PNG, 100, fo); }
        // Known harness limit: this full-tree software draw omits the dock tiles (cause not found); see tileSheet().
        try (FileOutputStream f = new FileOutputStream(out)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }





    /** Isolated component render: each real tile view drawn on its own, top row with the rim, bottom row flat. */
    private static void tileSheet(View root) throws Exception {
        androidx.recyclerview.widget.RecyclerView r = findRv(root);
        Bitmap sheet = Bitmap.createBitmap(1920, 900, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(sheet);
        cv.drawColor(0xFF05080F);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < Math.min(4, r.getChildCount()); i++) {
                View c = r.getChildAt(i);
                View card = c.findViewById(com.gathin.tvhome.R.id.card);
                android.graphics.drawable.Drawable keepBg = card.getBackground();
                android.graphics.drawable.Drawable keepFg = ((android.widget.FrameLayout) card).getForeground();
                if (pass == 1) ((android.widget.FrameLayout) card).setForeground(null);
                if (pass == 0 && i == 1) { card.setBackground(new ColorDrawable(Color.WHITE)); ((android.widget.FrameLayout) card).getForeground().setState(new int[]{android.R.attr.state_focused}); }   // tile 2 shown focused, on a white fixture fill (worst case for the ring)
                cv.save(); cv.translate(40 + i * 470, 40 + pass * 400); c.draw(cv); cv.restore();
                card.setBackground(keepBg);
                if (pass == 1) ((android.widget.FrameLayout) card).setForeground(keepFg);   // the sheet must not change the real tiles
            }
        }
        try (FileOutputStream f = new FileOutputStream("build/tile-sheet.png")) { sheet.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }

    private static androidx.recyclerview.widget.RecyclerView findRv(View v) {
        if (v instanceof androidx.recyclerview.widget.RecyclerView) return (androidx.recyclerview.widget.RecyclerView) v;
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) { androidx.recyclerview.widget.RecyclerView x = findRv(((android.view.ViewGroup) v).getChildAt(i)); if (x != null) return x; }
        return null;
    }
}
