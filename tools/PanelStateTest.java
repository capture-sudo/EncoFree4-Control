package io.codex.encofree4;

public final class PanelStateTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        PanelState state = new PanelState(EncoProtocol.MODE_ANC);
        check(state.confirmedMode == -1, "A cached mode is never current on opening");
        check(state.cachedMode == EncoProtocol.MODE_ANC, "Keep cache separately");
        check(state.requestMode(EncoProtocol.MODE_TRANSPARENCY), "Accept first request");
        check(state.confirmedMode == -1, "A tap does not confirm the mode");
        check(state.cachedMode == EncoProtocol.MODE_ANC, "A tap does not overwrite cache");
        check(!state.requestMode(EncoProtocol.MODE_OFF), "Coalesce rapid taps while waiting");
        state.observeMode(EncoProtocol.MODE_ANC);
        check(state.pendingMode == EncoProtocol.MODE_TRANSPARENCY,
                "An older mode notification must not finish a different request");
        check(state.expireRequest() == EncoProtocol.MODE_TRANSPARENCY, "Timeout remembers retry target");
        check(state.confirmedMode == EncoProtocol.MODE_ANC, "Timeout retains last confirmed mode");
        check(state.pendingMode == -1, "Timeout enables another attempt");
        check(state.requestMode(EncoProtocol.MODE_TRANSPARENCY), "Allow retry");
        state.observeMode(EncoProtocol.MODE_TRANSPARENCY);
        check(state.pendingMode == -1 && state.confirmedMode == EncoProtocol.MODE_TRANSPARENCY,
                "Matching device notification confirms the request");
        check(state.cachedMode == EncoProtocol.MODE_TRANSPARENCY, "Cache actual device state");
        state.observeMode(99);
        check(state.confirmedMode == EncoProtocol.MODE_TRANSPARENCY, "Ignore invalid mode");
        check(!state.requestMode(-1), "Reject invalid request");

        byte[] full = EncoProtocol.singleFrame(EncoProtocol.NOTIFICATION_EVENT,
                new byte[]{1, 3, 1, 80, 2, 70, 3, (byte) 0x94}, 1);
        state.mergeBattery(EncoProtocol.parseBattery(full));
        check(state.battery.left == 80 && state.battery.right == 70
                && state.battery.caseBattery == 20 && state.battery.caseCharging,
                "Full parsed notification initializes component values");
        byte[] leftOnly = EncoProtocol.singleFrame(EncoProtocol.NOTIFICATION_EVENT,
                new byte[]{1, 1, 1, (byte) 0xCF}, 2);
        state.mergeBattery(EncoProtocol.parseBattery(leftOnly));
        check(state.battery.left == 79 && state.battery.leftCharging, "Apply single ear update");
        check(state.battery.right == 70 && state.battery.caseBattery == 20
                && state.battery.caseCharging, "Single ear update retains absent components");
        byte[] emptyCase = EncoProtocol.singleFrame(EncoProtocol.NOTIFICATION_EVENT,
                new byte[]{1, 1, 3, 0}, 3);
        state.mergeBattery(EncoProtocol.parseBattery(emptyCase));
        check(state.battery.caseBattery == 0 && !state.battery.caseCharging,
                "Zero battery and charging=false must update, not be treated as absent");
        check(state.battery.count == 3, "Merged count includes retained components");
        state.mergeBattery(null);
        check(state.battery.left == 79, "Null update preserves state");

        state.requestMode(EncoProtocol.MODE_OFF);
        state.disconnect();
        check(state.battery == null && state.confirmedMode == -1 && state.pendingMode == -1,
                "Disconnect clears all live and pending state");
        check(state.cachedMode == EncoProtocol.MODE_TRANSPARENCY,
                "Disconnect retains only separately labelled last confirmed mode");
        check(new PanelState(999).cachedMode == -1, "Reject invalid preference value");
        PanelState display = new PanelState(EncoProtocol.MODE_ANC);
        check(display.modeDisplay(false, false, false) == PanelState.ModeDisplay.WAITING_CONNECTION,
                "Disconnected panel must not claim it is reading");
        check(display.modeDisplay(true, false, false) == PanelState.ModeDisplay.READING,
                "Connected unknown mode is reading");
        check(display.modeDisplay(true, true, false) == PanelState.ModeDisplay.READ_FAILED,
                "Unknown mode timeout is visible");
        check(display.modeDisplay(true, true, true) == PanelState.ModeDisplay.ISSUE,
                "Actionable error replaces reading text");
        display.observeMode(EncoProtocol.MODE_OFF);
        check(display.modeDisplay(true, false, false) == PanelState.ModeDisplay.CURRENT,
                "Device reply is current");
        check(display.modeDisplay(true, false, true) == PanelState.ModeDisplay.ISSUE,
                "Failed switch remains actionable even with an older confirmed mode");
        display.requestMode(EncoProtocol.MODE_ANC);
        check(display.modeDisplay(true, false, true) == PanelState.ModeDisplay.SWITCHING,
                "New request takes precedence over an old issue");
        check(display.modeDisplay(false, true, true) == PanelState.ModeDisplay.WAITING_CONNECTION,
                "Disconnect takes precedence over stale requests and issues");
        display.disconnect();
        check(display.modeDisplay(false, false, false) == PanelState.ModeDisplay.WAITING_CONNECTION,
                "Return to disconnected display after a completed session");
        System.out.println("PanelState: " + checks + " checks passed.");
    }
}
