package io.codex.encofree4;

/** Direction lock keeps horizontal/upward gestures from becoming a dismissal. */
final class PanelSwipeGesture {
    private enum Phase { IDLE, PENDING, DRAGGING, REJECTED }
    private final float slop;
    private final float flingDistance;
    private final float flingVelocity;
    private Phase phase = Phase.IDLE;
    private float downX, downY, dismissDistance, distance;

    PanelSwipeGesture(float slop, float flingDistance, float flingVelocity) {
        this.slop = slop;
        this.flingDistance = flingDistance;
        this.flingVelocity = flingVelocity;
    }

    void begin(float x, float y, float dismissDistance) {
        downX = x;
        downY = y;
        this.dismissDistance = dismissDistance;
        distance = 0;
        phase = Phase.PENDING;
    }

    float move(float x, float y) {
        float dy = y - downY;
        float dx = Math.abs(x - downX);
        if (phase == Phase.PENDING && Math.max(dx, Math.abs(dy)) > slop) {
            phase = dy > slop && dy > dx * 1.3f ? Phase.DRAGGING : Phase.REJECTED;
        }
        distance = phase == Phase.DRAGGING ? Math.max(0, dy) : 0;
        return distance;
    }

    boolean isDragging() {
        return phase == Phase.DRAGGING;
    }

    boolean end(float x, float y, float velocityY) {
        move(x, y);
        boolean dismiss = isDragging() && (distance >= dismissDistance
                || (distance >= flingDistance && velocityY >= flingVelocity));
        cancel();
        return dismiss;
    }

    void cancel() {
        phase = Phase.IDLE;
        distance = 0;
    }
}
