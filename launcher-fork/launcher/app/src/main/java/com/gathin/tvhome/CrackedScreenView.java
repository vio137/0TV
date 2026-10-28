package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Easter egg overlay: makes the screen look cracked. It is an ordinary view inside the launcher's own window,
 * so it needs no special permission. It never takes focus or touches, so the remote keeps working underneath.
 */
final class CrackedScreenView extends View {
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private final Path[] cracks = new Path[0 + 9];
    private int builtW, builtH;

    CrackedScreenView(Context c) {
        super(c);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
        dark.setStyle(Paint.Style.STROKE);
        dark.setColor(0xFF000000);
        dark.setStrokeCap(Paint.Cap.ROUND);
        light.setStyle(Paint.Style.STROKE);
        light.setColor(0xCCFFFFFF);
        light.setStrokeCap(Paint.Cap.ROUND);
        shade.setColor(0x22FFFFFF);
    }

    boolean isShown_() { return getVisibility() == VISIBLE; }

    void toggle() { setVisibility(getVisibility() == VISIBLE ? GONE : VISIBLE); }

    /** Fixed pseudo-random shape so the crack looks the same every time. */
    private void build(int w, int h) {
        builtW = w; builtH = h;
        java.util.Random r = new java.util.Random(137);
        float ox = w * 0.62f, oy = h * 0.38f;
        for (int i = 0; i < cracks.length; i++) {
            Path p = new Path();
            p.moveTo(ox, oy);
            double a = (Math.PI * 2 * i / cracks.length) + r.nextDouble() * 0.5;
            float x = ox, y = oy;
            int steps = 7 + r.nextInt(5);
            float len = Math.max(w, h) * 0.11f;
            for (int s = 0; s < steps; s++) {
                a += (r.nextDouble() - 0.5) * 0.7;
                x += (float) Math.cos(a) * len * (0.6f + r.nextFloat() * 0.8f);
                y += (float) Math.sin(a) * len * (0.6f + r.nextFloat() * 0.8f);
                p.lineTo(x, y);
                if (s % 3 == 1) {   // small side branch
                    float bx = x + (float) Math.cos(a + 1.1) * len * 0.7f;
                    float by = y + (float) Math.sin(a + 1.1) * len * 0.7f;
                    p.lineTo(bx, by);
                    p.moveTo(x, y);
                }
            }
            cracks[i] = p;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (w != builtW || h != builtH) build(w, h);
        float u = Math.max(1f, w / 960f);
        canvas.drawRect(0, 0, w, h, shade);
        for (Path p : cracks) {
            dark.setStrokeWidth(3.2f * u);
            canvas.drawPath(p, dark);
            light.setStrokeWidth(1.2f * u);
            canvas.save();
            canvas.translate(1.5f * u, 1.5f * u);
            canvas.drawPath(p, light);
            canvas.restore();
        }
        canvas.drawCircle(w * 0.62f, h * 0.38f, 9f * u, dark);
    }
}
