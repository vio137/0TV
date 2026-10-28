package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * 0TV hero artwork plate. Displays only the installed app's banner or real icon.
 * A banner is center-cropped to this 16:9 plate. No banner means an icon on a quiet
 * tinted plate, never a fabricated movie poster. Presentation only, never focusable.
 */
public final class HeroBannerView extends View {
    private Drawable banner, icon;
    private int accent = 0xFF27374D;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    public HeroBannerView(Context c, AttributeSet a) { super(c, a); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void show(Drawable b, Drawable i, int color) { banner=own(b); icon=own(i); accent=color; invalidate(); }
    private Drawable own(Drawable d) { return d == null ? null : d.getConstantState() == null ? d : d.getConstantState().newDrawable(getResources()).mutate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d=getResources().getDisplayMetrics().density, pad=4*d, radius=20*d;
        r.set(pad,pad,getWidth()-pad,getHeight()-pad);
        p.setShader(null); p.setColor(0x33000000); c.drawRoundRect(r,radius,radius,p);
        clip.reset(); clip.addRoundRect(r,radius,radius,Path.Direction.CW); c.save(); c.clipPath(clip);
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,new int[]{0xFF182338,0xFF0E1626},null,Shader.TileMode.CLAMP));
        c.drawRect(r,p); p.setShader(null);
        if(banner!=null) {
            int iw=Math.max(1,banner.getIntrinsicWidth()), ih=Math.max(1,banner.getIntrinsicHeight());
            float scale=Math.max(r.width()/iw,r.height()/ih); int w=Math.round(iw*scale),h=Math.round(ih*scale);
            int x=Math.round(r.centerX()-w/2f),y=Math.round(r.centerY()-h/2f);
            banner.setBounds(x,y,x+w,y+h); banner.draw(c);
        } else {
            p.setColor((accent & 0x00FFFFFF)|0x44000000); c.drawCircle(r.centerX(),r.centerY(),Math.min(r.width(),r.height())*0.38f,p);
            if(icon!=null) { int z=(int)Math.min(84*d,r.height()*0.60f); icon.setBounds((getWidth()-z)/2,(getHeight()-z)/2,(getWidth()+z)/2,(getHeight()+z)/2);icon.draw(c); }
        }
        c.restore(); p.setShader(null); p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(0x44FFFFFF);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
    }
}
