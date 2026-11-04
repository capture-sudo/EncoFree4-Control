package io.codex.encofree4;

/** An immutable report from the current process/session, never a persisted live status. */
final class WidgetSnapshot {
    final boolean connected, connecting, leftCharging, rightCharging, refreshing;
    final int mode, pendingMode, left, right;

    WidgetSnapshot(boolean connected, boolean connecting, PanelState state, boolean refreshing) {
        this.connected = connected;
        this.connecting = !connected && connecting;
        mode = connected ? state.confirmedMode : -1;
        pendingMode = connected ? state.pendingMode : -1;
        EncoProtocol.Battery b = connected ? state.battery : null;
        left = b == null ? -1 : b.left;
        right = b == null ? -1 : b.right;
        leftCharging = b != null && b.leftCharging;
        rightCharging = b != null && b.rightCharging;
        this.refreshing = connected && refreshing;
    }

    boolean hasSelection() {
        return connected && (mode == EncoProtocol.MODE_ANC || mode == EncoProtocol.MODE_TRANSPARENCY);
    }

    float position() { return mode == EncoProtocol.MODE_TRANSPARENCY ? 1f : 0f; }
}
