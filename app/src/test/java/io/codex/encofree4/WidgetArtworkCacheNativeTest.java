package io.codex.encofree4;

import android.content.Context;
import android.graphics.Bitmap;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetArtworkCacheNativeTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void clearCache() { WidgetArtworkCache.clear(); }
    private WidgetSnapshot snapshot(int battery, boolean connected) {
        PanelState panel=new PanelState(-1);panel.observeMode(EncoProtocol.MODE_ANC);
        EncoProtocol.Battery b=new EncoProtocol.Battery();b.left=battery;b.right=85;panel.mergeBattery(b);
        return new WidgetSnapshot(connected,false,panel,false);
    }
    private Object cacheField(String name) throws Exception {
        Field f=WidgetArtworkCache.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }

    @Test public void iconsSurviveBatteryUpdatesButConnectionChangesInvalidateThem() {
        WidgetGeometry g=new WidgetGeometry(180,180,false);
        Bitmap icons=WidgetArtworkCache.image(context,g,snapshot(80,true),2,true);
        Bitmap card=WidgetArtworkCache.image(context,g,snapshot(80,true),2,false);
        assertSame(icons,WidgetArtworkCache.image(context,g,snapshot(79,true),2,true));
        assertNotSame(card,WidgetArtworkCache.image(context,g,snapshot(79,true),2,false));
        assertNotSame(icons,WidgetArtworkCache.image(context,g,snapshot(79,false),2,true));
    }

    @Test public void recentlyUsedImageSurvivesEntryEvictionAndPixelBudgetIsBounded() throws Exception {
        WidgetGeometry small=new WidgetGeometry(100,100,false);
        Bitmap hot=WidgetArtworkCache.image(context,small,snapshot(0,true),1,false);
        Bitmap old=WidgetArtworkCache.image(context,small,snapshot(1,true),1,false);
        for(int i=2;i<16;i++)WidgetArtworkCache.image(context,small,snapshot(i,true),1,false);
        assertSame(hot,WidgetArtworkCache.image(context,small,snapshot(0,true),1,false));
        WidgetArtworkCache.image(context,small,snapshot(16,true),1,false);
        assertSame(hot,WidgetArtworkCache.image(context,small,snapshot(0,true),1,false));
        assertNotSame(old,WidgetArtworkCache.image(context,small,snapshot(1,true),1,false));
        assertFalse("eviction recycled a bitmap retained by a launcher",old.isRecycled());
        assertTrue(((Map<?,?>)cacheField("IMAGES")).size()<=16);
        WidgetGeometry large=new WidgetGeometry(600,600,false);
        for(int i=0;i<6;i++)WidgetArtworkCache.image(context,large,snapshot(i,true),1,false);
        assertTrue((long)cacheField("cachedPixels")<=1_400_000);
    }

    @Test public void removingLastWidgetReleasesReferencesWithoutRecyclingLivePixels() throws Exception {
        WidgetGeometry g=new WidgetGeometry(180,180,false);
        Bitmap held=WidgetArtworkCache.image(context,g,snapshot(80,true),2,false);
        new EncoWidgetLarge().onDisabled(context);
        assertTrue(((Map<?,?>)cacheField("IMAGES")).isEmpty());
        assertEquals(0L,cacheField("cachedPixels"));
        assertFalse(held.isRecycled());
        assertNotSame(held,WidgetArtworkCache.image(context,g,snapshot(80,true),2,false));
    }
}
