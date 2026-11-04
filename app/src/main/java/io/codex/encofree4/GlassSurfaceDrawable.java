package io.codex.encofree4;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Glass surface; the card-sized Window supplies local blur, with a stronger tint as fallback. */
final class GlassSurfaceDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF();
    private final RectF inner = new RectF();
    private final float radius, density;
    private final boolean selected;
    private final boolean panel;
    private boolean blurEnabled;
    private LinearGradient fill, edge;
    private int alpha = 255;
    private ColorFilter filter;

    GlassSurfaceDrawable(float radius, float density, boolean selected) {
        this(radius, density, selected, false);
    }

    private GlassSurfaceDrawable(float radius, float density, boolean selected, boolean panel) {
        this.radius = radius;
        this.density = density;
        this.selected = selected;
        this.panel = panel;
    }

    static GlassSurfaceDrawable panel(float radius, float density) {
        return new GlassSurfaceDrawable(radius, density, false, true);
    }

    void setBlurEnabled(boolean enabled) {
        if (blurEnabled == enabled) return;
        blurEnabled = enabled;
        updateGradients();
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float inset = density * .65f;
        face.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        inner.set(face);
        inner.inset(density * 1.3f, density * 1.3f);
        updateGradients();
    }

    private void updateGradients() {
        if (face.isEmpty()) return;
        int[] colors = panel
                ? blurEnabled ? new int[]{0x786B7D94, 0x603C5069, 0x70525A75}
                        : new int[]{0xAA526277, 0xA6384659, 0xAA41475D}
                : selected ? new int[]{0x99FFFFFF, 0x50F3F8FF, 0x70DAE7FA}
                        : new int[]{0x407A8CA3, 0x24576B85, 0x3054647F};
        fill = new LinearGradient(face.left, face.top, face.right, face.bottom,
                colors,
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        edge = new LinearGradient(face.left, face.top, face.right, face.bottom,
                new int[]{selected ? 0xEEFFFFFF : 0xB3FFFFFF, 0x28FFFFFF, 0x70FFFFFF},
                null, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        if (face.isEmpty()) return;
        paint.setColorFilter(filter);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
        paint.setShader(fill);
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setShader(edge);
        canvas.drawRoundRect(face, radius, radius, paint);
        paint.setShader(null);
        paint.setColor(0x22FFFFFF);
        paint.setAlpha(Math.round(0x22 * alpha / 255f));
        paint.setStrokeWidth(density * .5f);
        canvas.drawRoundRect(inner, Math.max(0, radius - density * 1.3f),
                Math.max(0, radius - density * 1.3f), paint);
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(0f);
    }
    @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter value) { filter = value; invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
