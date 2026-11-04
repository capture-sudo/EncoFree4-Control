package io.codex.encofree4;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.Iterator;
import java.util.LinkedHashMap;

/** Main-thread LRU. Eviction drops references without recycling pixels held by RemoteViews. */
final class WidgetArtworkCache {
    private static final int MAX_IMAGES = 16;
    private static final long MAX_PIXELS = 1_400_000;
    private static final LinkedHashMap<Key, Bitmap> IMAGES = new LinkedHashMap<>(16, .75f, true);
    private static long cachedPixels;

    private record Key(boolean icons, int density, float width, float height, boolean compact,
                       float scale, boolean connected, boolean connecting, int left, int right,
                       boolean leftCharging, boolean rightCharging, boolean refreshing) {
        static Key of(Context context, WidgetGeometry g, WidgetSnapshot s, float scale, boolean icons) {
            // Icons depend on connection and geometry; battery/mode changes only affect the card.
            return new Key(icons, context.getResources().getDisplayMetrics().densityDpi,
                    g.width, g.height, g.compact, scale, s.connected,
                    !icons && s.connecting, icons ? -1 : s.left, icons ? -1 : s.right,
                    !icons && s.leftCharging, !icons && s.rightCharging, !icons && s.refreshing);
        }
    }

    static Bitmap image(Context context, WidgetGeometry g, WidgetSnapshot s, float scale, boolean icons) {
        Key key = Key.of(context, g, s, scale, icons);
        Bitmap image = IMAGES.get(key);
        if (image != null) return image;
        image = icons ? WidgetRenderer.controlIcons(context, g, s, scale)
                : WidgetRenderer.render(context, g, s, 0, scale, true);
        IMAGES.put(key, image);
        cachedPixels += pixels(image);
        Iterator<Bitmap> oldest = IMAGES.values().iterator();
        while ((cachedPixels > MAX_PIXELS || IMAGES.size() > MAX_IMAGES) && IMAGES.size() > 1) {
            cachedPixels -= pixels(oldest.next());
            oldest.remove();
        }
        return image;
    }

    private static long pixels(Bitmap image) { return (long) image.getWidth() * image.getHeight(); }

    static void clear() {
        IMAGES.clear();
        cachedPixels = 0;
    }
}
