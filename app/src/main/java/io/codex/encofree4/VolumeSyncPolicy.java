package io.codex.encofree4;

/** Event callbacks update immediately; polling is only a foreground fallback. */
final class VolumeSyncPolicy {
    private boolean active;
    private long fastUntil;

    void resume(long now) {
        active = true;
        fastUntil = now + 600;
    }

    void pause() {
        active = false;
    }

    void noteInteraction(long now) {
        if (active) {
            fastUntil = Math.max(fastUntil, now + 1200);
        }
    }

    boolean isActive() {
        return active;
    }

    long nextDelay(long now) {
        return !active ? -1 : now < fastUntil ? 120 : 1000;
    }
}
