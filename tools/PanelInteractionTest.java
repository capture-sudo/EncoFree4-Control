package io.codex.encofree4;

public final class PanelInteractionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        PanelSwipeGesture swipe = new PanelSwipeGesture(8, 24, 900);
        swipe.begin(100, 100, 80);
        check(swipe.move(102, 104) == 0 && !swipe.isDragging(), "Tap jitter does not drag");
        check(!swipe.end(102, 104, 1500), "Tiny fast movement does not dismiss");
        swipe.begin(100, 100, 80);
        check(swipe.move(103, 124) == 24 && swipe.isDragging(), "Downward movement starts dragging");
        check(swipe.end(103, 190, 0), "Long slow downward drag dismisses");
        swipe.begin(100, 100, 80);
        swipe.move(100, 135);
        check(!swipe.end(100, 135, 100), "Short slow drag returns to panel");
        swipe.begin(100, 100, 80);
        swipe.move(100, 135);
        check(swipe.end(100, 140, 1100), "Short deliberate downward fling dismisses");
        swipe.begin(100, 100, 80);
        swipe.move(140, 110);
        check(!swipe.isDragging(), "Horizontal movement locks out panel drag");
        check(!swipe.end(140, 250, 1500), "Horizontal gesture cannot turn into dismissal later");
        swipe.begin(100, 100, 80);
        swipe.move(100, 70);
        check(!swipe.end(100, 200, 1200), "Upward gesture stays rejected");
        swipe.begin(100, 100, 80);
        swipe.move(100, 150);
        check(!swipe.end(100, 135, -1000), "Reversing a short drag does not dismiss");
        swipe.begin(100, 100, 80);
        swipe.move(100, 200);
        swipe.cancel();
        check(!swipe.isDragging() && !swipe.end(100, 250, 2000), "Cancel prevents dismissal");
        swipe.begin(50, 300, 80);
        check(swipe.end(50, 390, 1000), "New gesture works after cancellation");
        check(!swipe.end(50, 500, 2000), "Completed gesture cannot dismiss a second time");
        swipe.begin(0, 0, 120);
        check(!swipe.end(0, 20, 3000), "Fling requires minimum travel");
        swipe.begin(0, 0, 120);
        check(!swipe.end(0, 90, 0), "Taller panel respects its dismissal threshold");

        VolumeSyncPolicy volume = new VolumeSyncPolicy();
        check(!volume.isActive() && volume.nextDelay(0) == -1, "Closed panel has no polling");
        volume.resume(1000);
        check(volume.isActive() && volume.nextDelay(1000) == 120, "Resume briefly checks quickly");
        check(volume.nextDelay(1600) == 1000, "Idle foreground polling slows down");
        volume.noteInteraction(2000);
        check(volume.nextDelay(2100) == 120, "Volume interaction enables fast fallback");
        volume.noteInteraction(3000);
        check(volume.nextDelay(4100) == 120, "Held keys extend the fast interval");
        check(volume.nextDelay(4200) == 1000, "Fast interval ends after interaction stops");
        volume.pause();
        volume.noteInteraction(5000);
        check(!volume.isActive() && volume.nextDelay(5000) == -1,
                "Late events after pause do not restart polling");
        volume.resume(6000);
        check(volume.nextDelay(6600) == 1000, "Resume discards the previous interaction window");
        volume.pause();
        check(volume.nextDelay(7000) == -1, "Dismissal stops polling immediately");
        System.out.println("Panel interactions: " + checks + " checks passed.");
    }
}
