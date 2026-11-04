package io.codex.encofree4;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import android.os.Parcel;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Exercise Android's actual ImageView, RelativeLayout, weights, and RemoteViews reflection. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetNativeLayoutTest {
    private void check(boolean compact,float reportedWidth,float reportedHeight,int actualWidth,int actualHeight) {
        Context context=RuntimeEnvironment.getApplication();
        PanelState state=new PanelState(-1);state.observeMode(EncoProtocol.MODE_ANC);
        EncoProtocol.Battery battery=new EncoProtocol.Battery();battery.left=85;battery.right=80;state.mergeBattery(battery);
        WidgetSnapshot snapshot=new WidgetSnapshot(true,false,state,false);
        RemoteViews remote=EncoWidgets.views(context,compact,reportedWidth,reportedHeight,snapshot,0,1);
        Parcel parcel=Parcel.obtain();
        remote.writeToParcel(parcel,0);parcel.setDataPosition(0);
        remote=RemoteViews.CREATOR.createFromParcel(parcel);parcel.recycle();
        FrameLayout host=new FrameLayout(context);
        View root=remote.apply(context,host);
        host.addView(root,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        host.measure(View.MeasureSpec.makeMeasureSpec(actualWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(actualHeight,View.MeasureSpec.EXACTLY));
        host.layout(0,0,actualWidth,actualHeight);
        View card=root.findViewById(R.id.widget_card),art=root.findViewById(R.id.widget_art);
        assertEquals("artwork uses artificial density",context.getResources().getDisplayMetrics().densityDpi,
                ((BitmapDrawable)((ImageView)art).getDrawable()).getBitmap().getDensity());
        float aspect=compact?WidgetViewport.COMPACT_ASPECT:1;
        float expectedWidth=Math.min(actualWidth,actualHeight*aspect),expectedHeight=expectedWidth/aspect;
        assertEquals("card did not fill actual available width",expectedWidth,card.getWidth(),2);
        assertEquals("card aspect changed",expectedHeight,card.getHeight(),2);
        assertEquals("card not bottom aligned",actualHeight,card.getBottom());
        assertEquals("art/card widths differ",card.getWidth(),art.getWidth());
        assertEquals("art/card heights differ",card.getHeight(),art.getHeight());
        WidgetViewport viewport=new WidgetViewport(reportedWidth,reportedHeight,compact);
        WidgetGeometry g=new WidgetGeometry(viewport.modelWidth,viewport.modelHeight,compact);
        assertTarget(card,R.id.widget_connection,g.statusTouch,g);
        assertTarget(card,R.id.widget_battery,g.batteryTouch,g);
        assertTarget(card,R.id.widget_anc,g.anc,g);
        assertTarget(card,R.id.widget_transparency,g.transparency,g);
        // Exercise update-in-place as the real launcher does on every battery/mode change.
        remote.reapply(context,root);
        if(reportedWidth==162 && actualWidth==514) {
            Bitmap preview=Bitmap.createBitmap(card.getWidth(),card.getHeight(),Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(preview));
            File folder=new File("build/reports/widget-native");folder.mkdirs();
            try(FileOutputStream stream=new FileOutputStream(new File(folder,compact?"fixed-2x1.png":"fixed-2x2.png"))) {
                assertTrue(preview.compress(Bitmap.CompressFormat.PNG,100,stream));
            } catch(Exception error) { throw new AssertionError(error); }
        }
    }
    private void assertTarget(View card,int id,WidgetGeometry.Box box,WidgetGeometry g) {
        View target=card.findViewById(id);
        Rect r=new Rect(0,0,target.getWidth(),target.getHeight());
        ((ViewGroup)card).offsetDescendantRectToMyCoords(target,r);
        assertEquals("tap x",box.x/g.width*card.getWidth(),r.left,4);
        assertEquals("tap y",box.y/g.height*card.getHeight(),r.top,4);
        assertEquals("tap width",box.w/g.width*card.getWidth(),r.width(),4);
        assertEquals("tap height",box.h/g.height*card.getHeight(),r.height(),4);
        assertTrue("tap missing click intent",target.hasOnClickListeners());
    }
    @Test public void screenshotRegressionFills514InsteadOf486() {
        check(false,162,188,514,566);
        check(false,180,180,514,566);
    }
    @Test public void compactMatchesReferenceFolderProportions() {
        assertEquals(514f/204f,WidgetViewport.COMPACT_ASPECT,.01f);
        WidgetGeometry before=new WidgetGeometry(180,180,false);
        assertEquals("approved square radius changed",180*.1556f,before.radius,.001f);
        check(true,162,90,514,278);
    }
    @Test public void compactAlsoUsesMeasuredBounds() {
        check(true,162,90,514,278);
        check(true,180,86,360,172);
    }
    @Test @Config(qualifiers="land-mdpi") public void heightLimitedSlotsKeepTargetsAligned() {
        check(false,269,117,514,220);
        check(true,269,51,514,104);
    }
    @Test public void providersUseFixedCellCounts() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        for(int[] spec:new int[][]{{R.xml.widget_large_info,2},{R.xml.widget_compact_info,1}}) {
            try(android.content.res.XmlResourceParser parser=context.getResources().getXml(spec[0])) {
                while(parser.next()!=org.xmlpull.v1.XmlPullParser.START_TAG) { }
                String ns="http://schemas.android.com/apk/res/android";
                assertEquals("resize must be disabled",0,parser.getAttributeIntValue(ns,"resizeMode",-1));
                assertEquals(2,parser.getAttributeIntValue(ns,"targetCellWidth",-1));
                assertEquals(spec[1],parser.getAttributeIntValue(ns,"targetCellHeight",-1));
            }
        }
    }
    @Test public void fixedSlotsFillTwoColumnsWithDifferentReports() {
        for(boolean compact:new boolean[]{false,true})for(int[] size:new int[][]{{130,160},{360,420},{514,566}})
            check(compact,180,180,size[0],size[1]);
    }
}
