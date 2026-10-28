package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * TV Tidy Home: a thin lit edge and a soft top sheen drawn over an icon tile, so a tile reads as a glass
 * card rather than a flat colour. It only paints inside the tile and never touches layout or focus.
 */
final class TileRim extends Drawable {
    private final float density;
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF inner = new RectF();
    /** Focused only: a dark line just inside the white ring, so the ring still reads on white and pale artwork. */
    private final Paint keyline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;

    TileRim(float density) {
        this.density = density;
        rim.setStyle(Paint.Style.STROKE);
        keyline.setStyle(Paint.Style.STROKE);
        keyline.setColor(0xB3081018);
        keyline.setStrokeWidth(Math.max(1f, density) * 2f);
        rim.setStrokeWidth(Math.max(1f, density));
    }

    private boolean focused;

    /** Set from the card's own drawable state (the focused tile), so no focus or key code is involved. */
    void setFocusedLook(boolean on) {
        if (focused == on) return;
        focused = on;
        rebuild();
        invalidateSelf();
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean f = false;
        for (int a : state) if (a == android.R.attr.state_focused) f = true;
        boolean changed = f != focused;
        setFocusedLook(f);
        return changed;
    }

    @Override protected void onBoundsChange(android.graphics.Rect b) { rebuild(); }

    private void rebuild() {
        android.graphics.Rect b = getBounds();
        if (b.isEmpty()) return;
        // Resting: a thin rim and a faint sheen. Focused: a brighter, thicker lit edge and a stronger sheen.
        rim.setStrokeWidth(Math.max(1f, density) * (focused ? 2.5f : 1f));
        float half = rim.getStrokeWidth() / 2f;
        box.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        radius = AppAdapter.cornerRadius(b.width(), density);
        float inset = rim.getStrokeWidth() / 2f + keyline.getStrokeWidth() / 2f;
        inner.set(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset);
        rim.setShader(new LinearGradient(0, b.top, 0, b.bottom, focused ? 0xEBFFFFFF : 0x66FFFFFF, focused ? 0x66FFFFFF : 0x14FFFFFF, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.45f, focused ? 0x47FFFFFF : 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        if (box.isEmpty()) return;
        c.drawRoundRect(box, radius, radius, sheen);
        c.drawRoundRect(box, radius, radius, rim);
        if (focused) c.drawRoundRect(inner, Math.max(0f, radius - (box.left < inner.left ? inner.left - box.left : 0f)), Math.max(0f, radius - (inner.left - box.left)), keyline);
    }

    @Override public void setAlpha(int alpha) { rim.setAlpha(alpha); sheen.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
