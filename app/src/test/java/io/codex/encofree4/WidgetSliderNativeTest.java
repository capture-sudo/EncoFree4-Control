package io.codex.encofree4;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Test confirmed endpoints and explicit frame updates in an attached, cross-process-style host. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetSliderNativeTest {
    private static PanelState panel(int mode) {
        PanelState panel=new PanelState(-1);panel.observeMode(mode);
        EncoProtocol.Battery battery=new EncoProtocol.Battery();battery.left=100;battery.right=85;
        panel.mergeBattery(battery);return panel;
    }
    private static RemoteViews remote(Context context,boolean compact,PanelState panel,boolean connected) {
        RemoteViews result=EncoWidgets.views(context,compact,162,compact?90:188,
                new WidgetSnapshot(connected,false,panel,false),connected && panel.confirmedMode==EncoProtocol.MODE_TRANSPARENCY?1f:0f,1);
        Parcel parcel=Parcel.obtain();
        try {
            result.writeToParcel(parcel,0);parcel.setDataPosition(0);
            return RemoteViews.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
    }
    private static final class Host implements AutoCloseable {
        final ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup().visible();
        final Activity context=controller.get();
        final FrameLayout host=new FrameLayout(context);
        final boolean compact;
        final View root;
        final ImageView slider;
        Host(boolean compact,int mode) {
            this.compact=compact;
            context.setContentView(host);
            root=remote(context,compact,panel(mode),true).apply(context,host);
            host.addView(root,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
            measure();
            slider=root.findViewById(R.id.widget_slider_still);
            assertTrue("host must propagate aggregated visibility",slider.isAttachedToWindow());
        }
        void measure() {
            int h=compact?278:566;
            host.measure(View.MeasureSpec.makeMeasureSpec(514,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
            host.layout(0,0,514,h);
        }
        void update(PanelState panel,boolean connected) {
            remote(context,compact,panel,connected).reapply(context,root);measure();
        }
        @Override public void close(){controller.pause().stop().destroy();}
    }
    private static void assertStaticEndpoint(View root,boolean compact,int mode) {
        ImageView still=root.findViewById(R.id.widget_slider_still);
        assertEquals("static endpoint layer is hidden",View.VISIBLE,still.getVisibility());
        Drawable drawable=still.getDrawable();
        assertNotNull("static endpoint drawable is missing",drawable);
        assertFalse("static endpoint is animated",drawable instanceof AnimatedVectorDrawable);
        Bitmap image=Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888);
        Rect original=new Rect(drawable.getBounds());
        drawable.setBounds(0,0,image.getWidth(),image.getHeight());drawable.draw(new Canvas(image));drawable.setBounds(original);
        double mass=0,sumX=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int alpha=Color.alpha(image.getPixel(x,y));mass+=alpha;sumX+=x*alpha;
        }
        image.recycle();
        assertTrue("static glass circle is empty",mass>0);
        double center=sumX/mass/1000;
        if(mode==EncoProtocol.MODE_ANC)assertTrue("ANC endpoint is not on the left: "+center,center<.3);
        else assertTrue("transparency endpoint is not on the right: "+center,center>.7);
    }

    @Test public void initialDisplayJumpsToConfirmedEndpointInBothWidgets() {
        for(boolean compact:new boolean[]{false,true})for(int mode:new int[]{EncoProtocol.MODE_ANC,EncoProtocol.MODE_TRANSPARENCY}) {
            try(Host host=new Host(compact,mode)) {

                assertNotNull(host.slider.getDrawable());
                assertStaticEndpoint(host.root,compact,mode);
            }
        }
    }

    @Test public void detachedInitialDisplayAlsoUsesTheCorrectStaticEndpoint() {
        Context context=RuntimeEnvironment.getApplication();
        for(boolean compact:new boolean[]{false,true})for(int mode:new int[]{EncoProtocol.MODE_ANC,EncoProtocol.MODE_TRANSPARENCY}) {
            FrameLayout host=new FrameLayout(context);
            View root=remote(context,compact,panel(mode),true).apply(context,host);
            assertFalse(root.isAttachedToWindow());
            assertStaticEndpoint(root,compact,mode);
        }
    }

    @Test public void explicitFramesMoveVisiblyAndLeaveTheCardUntouched() {
        for(boolean compact:new boolean[]{false,true})try(Host host=new Host(compact,EncoProtocol.MODE_ANC)) {
            ImageView art=host.root.findViewById(R.id.widget_art);
            Drawable background=art.getDrawable();
            ImageView knob=host.root.findViewById(R.id.widget_slider_still);
            double previous=-1;
            for(int frame=0;frame<=30;frame++) {
                float position=frame/30f;
                RemoteViews update=EncoWidgets.views(host.context,compact,162,compact?90:188,
                    new WidgetSnapshot(true,false,panel(EncoProtocol.MODE_TRANSPARENCY),false),position,1);
                Parcel parcel=Parcel.obtain();update.writeToParcel(parcel,0);parcel.setDataPosition(0);
                update=RemoteViews.CREATOR.createFromParcel(parcel);parcel.recycle();
                update.reapply(host.context,host.root);
                assertTrue("frame changed static card pixels",
                    ((android.graphics.drawable.BitmapDrawable)background).getBitmap().sameAs(
                    ((android.graphics.drawable.BitmapDrawable)art.getDrawable()).getBitmap()));
                Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)knob.getDrawable()).getBitmap();
                double mass=0,xsum=0;
                for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++) {
                    int alpha=Color.alpha(bitmap.getPixel(x,y));mass+=alpha;xsum+=alpha*x;
                }
                double center=xsum/mass/bitmap.getWidth();
                assertTrue("consecutive frames did not advance",center>previous);
                if(frame==0)assertTrue(center<.3);
                if(frame==15)assertEquals(.5,center,.04);
                if(frame==30)assertTrue(center>.7);
                previous=center;
            }
            host.update(panel(EncoProtocol.MODE_ANC),true);
            assertStaticEndpoint(host.root,compact,EncoProtocol.MODE_ANC);
        }
    }

    @Test public void motionUsesRealElapsedTimeAndReversesFromCurrentPosition() {
        WidgetMotion motion=new WidgetMotion();motion.snap(0);motion.start(1000,0,1);
        assertEquals(0,motion.position(1000),0);
        assertEquals(.5,motion.position(1120),.001);
        assertTrue(motion.position(1060)<.5);
        assertTrue(motion.running(1239));assertFalse(motion.running(1240));
        assertEquals(1,motion.position(2000),0);
        motion.start(2000,1,0);assertEquals(.5,motion.position(2120),.001);
        motion.start(2120,0,1);assertEquals(.5,motion.position(2120),.001);
        assertEquals(1,motion.position(2360),.001);
        motion.snap(0);assertFalse(motion.running(2361));assertEquals(0,motion.position(2361),0);
    }

    @Test public void fullFrameUpdatesAdvanceResponsiveVariantsWithoutSystemMerge() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        for(boolean responsive:new boolean[]{false,true}) {
            WidgetSnapshot state=new WidgetSnapshot(true,false,panel(EncoProtocol.MODE_TRANSPARENCY),false);
            RemoteViews full=EncoWidgets.views(c,false,162,188,state,0,1);
            RemoteViews frame=EncoWidgets.views(c,false,162,188,state,1,1);
            if(responsive) {
                java.util.Map<android.util.SizeF,RemoteViews> f=new java.util.LinkedHashMap<>(),p=new java.util.LinkedHashMap<>();
                f.put(new android.util.SizeF(162,188),full);p.put(new android.util.SizeF(162,188),frame);
                f.put(new android.util.SizeF(269,117),EncoWidgets.views(c,false,269,117,state,0,2));
                p.put(new android.util.SizeF(269,117),EncoWidgets.views(c,false,269,117,state,1,2));
                full=new RemoteViews(f);frame=new RemoteViews(p);
            }
            View root=full.apply(c,new FrameLayout(c));
            frame.reapply(c,root);
            assertStaticEndpoint(root,false,EncoProtocol.MODE_TRANSPARENCY);
        }
    }

    @Test public void staticPixelsAreReusedAndBatteryChangesInvalidateThem() {
        Context c=RuntimeEnvironment.getApplication();WidgetGeometry g=new WidgetGeometry(180,180,false);
        PanelState state=panel(EncoProtocol.MODE_ANC);
        Bitmap before=WidgetArtworkCache.image(c,g,new WidgetSnapshot(true,false,state,false),2,false);
        state.observeMode(EncoProtocol.MODE_TRANSPARENCY);
        assertSame(before,WidgetArtworkCache.image(c,g,new WidgetSnapshot(true,false,state,false),2,false));
        state.battery.left=99;
        assertNotSame(before,WidgetArtworkCache.image(c,g,new WidgetSnapshot(true,false,state,false),2,false));
    }

    @Test public void pendingFailedOffAndDisconnectedStatesNeverInventASelection() {
        for(boolean compact:new boolean[]{false,true}) {
            try(Host host=new Host(compact,EncoProtocol.MODE_ANC)) {
                PanelState pending=panel(EncoProtocol.MODE_ANC);
                assertTrue(pending.requestMode(EncoProtocol.MODE_TRANSPARENCY));
                host.update(pending,true);
                assertStaticEndpoint(host.root,compact,EncoProtocol.MODE_ANC);
                pending.expireRequest();host.update(pending,true);
                assertStaticEndpoint(host.root,compact,EncoProtocol.MODE_ANC);
                host.update(panel(EncoProtocol.MODE_OFF),true);
                assertEquals("OFF must not select either mode",View.GONE,host.slider.getVisibility());
                assertEquals(View.GONE,host.root.findViewById(R.id.widget_slider_still).getVisibility());
                host.update(panel(EncoProtocol.MODE_TRANSPARENCY),false);
                assertEquals("disconnected widget retained selected circle",View.GONE,host.slider.getVisibility());
                assertEquals(View.GONE,host.root.findViewById(R.id.widget_slider_still).getVisibility());
                host.update(panel(EncoProtocol.MODE_TRANSPARENCY),true);
                assertStaticEndpoint(host.root,compact,EncoProtocol.MODE_TRANSPARENCY);
            }
        }
    }

    @Test public void pendingModeProducesIdenticalArtworkWithoutYellowCircle() {
        Context context=RuntimeEnvironment.getApplication();
        for(boolean compact:new boolean[]{false,true}) {
            WidgetViewport viewport=new WidgetViewport(162,compact?90:188,compact);
            WidgetGeometry g=new WidgetGeometry(viewport.modelWidth,viewport.modelHeight,compact);
            PanelState state=panel(EncoProtocol.MODE_ANC);
            for(boolean local:new boolean[]{false,true}) {
                Bitmap before=WidgetRenderer.render(context,g,new WidgetSnapshot(true,false,state,false),0,2,local);
                state.requestMode(EncoProtocol.MODE_TRANSPARENCY);
                Bitmap pending=WidgetRenderer.render(context,g,new WidgetSnapshot(true,false,state,false),0,2,local);
                assertTrue("pending state drew a separate colored marker",before.sameAs(pending));
                before.recycle();pending.recycle();state.expireRequest();
            }
        }
    }

    @Test public void nativeOverlayUsesTheExactApprovedTrackBounds() {
        for(boolean compact:new boolean[]{false,true}) {
            try(Host host=new Host(compact,EncoProtocol.MODE_ANC)) {
                WidgetViewport viewport=new WidgetViewport(162,compact?90:188,compact);
                WidgetGeometry g=new WidgetGeometry(viewport.modelWidth,viewport.modelHeight,compact);
                ViewGroup card=host.root.findViewById(R.id.widget_card);
                View layer=host.root.findViewById(R.id.widget_slider_layer);
                Rect actual=new Rect(0,0,layer.getWidth(),layer.getHeight());
                card.offsetDescendantRectToMyCoords(layer,actual);
                assertEquals(g.track.x/g.width*card.getWidth(),actual.left,4);
                assertEquals(g.track.y/g.height*card.getHeight(),actual.top,4);
                assertEquals(g.track.w/g.width*card.getWidth(),actual.width(),4);
                assertEquals(g.track.h/g.height*card.getHeight(),actual.height(),4);
                assertEquals("overlay must not consume clicks",false,host.slider.isClickable());
                assertTrue(host.root.findViewById(R.id.widget_anc).hasOnClickListeners());
                assertTrue(host.root.findViewById(R.id.widget_transparency).hasOnClickListeners());
            }
        }
    }

    @Test public void compactThreeDigitBatteryTextFitsInsideTheRing() {
        TextPaint paint=new TextPaint();paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        for(float width:new float[]{121,130,162,180,269,360,514}) {
            WidgetViewport viewport=new WidgetViewport(width,180,true);
            WidgetGeometry g=new WidgetGeometry(viewport.modelWidth,viewport.modelHeight,true);
            paint.setTextSize(g.numberSize);
            Rect bounds=new Rect();paint.getTextBounds("100",0,3,bounds);
            float baseline=-(paint.ascent()+paint.descent())/2;
            float centerX=paint.measureText("100")/2;
            float innerRadius=g.left.w/2-Math.max(2,g.left.w*.07f)-1;
            for(int x:new int[]{bounds.left,bounds.right})for(int y:new int[]{bounds.top,bounds.bottom}) {
                float distance=(float)Math.hypot(x-centerX,y+baseline);
                assertTrue("100 touches compact battery ring at model width "+width+": "+distance+" / "+innerRadius,
                        distance<=innerRadius-.5f);
            }
            assertTrue("compact digits were not reduced",g.numberSize<Math.max(10,g.left.w*.36f));
        }
    }
}
