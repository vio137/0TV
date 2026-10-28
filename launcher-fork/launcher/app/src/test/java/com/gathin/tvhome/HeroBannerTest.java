package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Synthetic, plainly labelled fixture artwork. Not an installed real app or device evidence. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HeroBannerTest {
    private BitmapDrawable art(int w,int h) {
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShader(new LinearGradient(0,0,w,h,0xFF3D276F,0xFF137987,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
        p.setShader(null);p.setColor(Color.WHITE);p.setTextSize(h/7f);p.setTextAlign(Paint.Align.CENTER);c.drawText("BANNER FIXTURE",w/2f,h/2f,p);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(),b);
    }
    private Bitmap render(Drawable banner, Drawable icon,String name) throws Exception {
        HeroBannerView v=new HeroBannerView(RuntimeEnvironment.getApplication(),null);
        v.layout(0,0,656,368);v.show(banner,icon,0xFF137987);
        Bitmap out=Bitmap.createBitmap(656,368,Bitmap.Config.ARGB_8888);v.draw(new Canvas(out));
        try(FileOutputStream f=new FileOutputStream("build/"+name+".png")){out.compress(Bitmap.CompressFormat.PNG,100,f);}
        return out;
    }
    @Test public void actualBannerPathDrawsAndDoesNotMutateSourceBounds() throws Exception {
        Drawable b=art(640,360); b.setBounds(1,2,31,42); Rect before=new Rect(b.getBounds());
        Bitmap out=render(b,null,"hero-banner-fixture"); assertEquals(before,b.getBounds());
        assertNotEquals("banner is visible",Color.TRANSPARENT,out.getPixel(200,100));
        assertEquals("outside rounded plate stays clear",Color.TRANSPARENT,out.getPixel(0,0));
    }
    @Test public void iconFallbackDoesNotMutateSharedTileDrawable() throws Exception {
        Drawable i=art(256,256);i.setBounds(0,0,48,48);Rect before=new Rect(i.getBounds());
        render(null,i,"hero-icon-fixture");assertEquals(before,i.getBounds());
    }
    @Test public void missingArtIsSafeAndLeavesQuietPlate() throws Exception {
        Bitmap out=render(null,null,"hero-empty-fixture");assertNotEquals(Color.TRANSPARENT,out.getPixel(328,184));
    }
}
