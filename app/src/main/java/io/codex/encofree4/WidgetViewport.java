package io.codex.encofree4;

/** The launcher grid is a slot, not the card's aspect ratio. All units are dp. */
final class WidgetViewport {
    // Screenshot reference: the folder card is approximately 514px wide by 204px high.
    static final float COMPACT_ASPECT = 360f / 143f;
    final float hostWidth, hostHeight, width, height, offsetX, offsetY;
    final float modelWidth, modelHeight, displayScale;

    WidgetViewport(float hostWidth, float hostHeight, boolean compact) {
        if (!Float.isFinite(hostWidth) || !Float.isFinite(hostHeight)
                || hostWidth <= 0 || hostHeight <= 0) {
            throw new IllegalArgumentException("Widget slot must have finite positive dimensions");
        }
        this.hostWidth = hostWidth;
        this.hostHeight = hostHeight;
        float aspect = compact ? COMPACT_ASPECT : 1f;
        width = Math.min(hostWidth, hostHeight * aspect);
        height = width / aspect;
        // The reference system card shares the same bottom edge as the current card.
        offsetX = (hostWidth - width) / 2;
        offsetY = hostHeight - height;
        // Preserve geometry's minimum design size, then scale drawing AND targets together.
        float modelScale = Math.max(1, Math.max(100 / width, 48 / height));
        displayScale = 1 / modelScale;
        modelWidth = width * modelScale;
        modelHeight = height * modelScale;
    }

    WidgetGeometry.Box toCard(WidgetGeometry.Box box) {
        return new WidgetGeometry.Box(box.x * displayScale, box.y * displayScale,
                box.w * displayScale, box.h * displayScale);
    }

    WidgetGeometry.Box toHost(WidgetGeometry.Box box) {
        WidgetGeometry.Box card = toCard(box);
        return new WidgetGeometry.Box(card.x + offsetX, card.y + offsetY, card.w, card.h);
    }
}
