package io.codex.encofree4;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;

/** Runs the shipped Java implementation; no Android runtime or copied parser is used. */
public final class EncoProtocolTest {
    private static int checks;

    public static void main(String[] args) {
        frames();
        varintBoundaries();
        batteries();
        modes();
        caseStatus();
        malformedInputs();
        System.out.println("Production EncoProtocol: " + checks + " checks passed.");
    }

    private static void frames() {
        hexEquals("aa0a000004044a0300010101", EncoProtocol.setNoiseModeFrame(EncoProtocol.MODE_OFF, 0x4a));
        hexEquals("aa0a00000404480300010104", EncoProtocol.setNoiseModeFrame(EncoProtocol.MODE_TRANSPARENCY, 0x48));
        hexEquals("aa0a00000404460300010102", EncoProtocol.setNoiseModeFrame(EncoProtocol.MODE_ANC, 0x46));
        hexEquals("aa0900000c014702000101", EncoProtocol.queryNoiseModeFrame(0x47));
        hexEquals("aa0900000c014702000201", EncoProtocol.legacyQueryNoiseModeFrame(0x47));
        hexEquals("aa0700000601700000", EncoProtocol.requestBatteryFrame(0x70));
        hexEquals("aa0c00002f017005000106010000", EncoProtocol.multiBatteryRequestFrame(0x70));
        hexEquals("aa0700000001520000", EncoProtocol.protocolHelloFrame(0x52));
        hexEquals("aa07000000025c0000", EncoProtocol.queryNotificationCapabilityFrame(0x5c));
        hexEquals("aa11000005026e0a0009f1f2f301020304080b", EncoProtocol.registerNotificationsFrame(0x6e));
        hexEquals("aa0700003412ff0000", EncoProtocol.singleFrame(0x1234, null, 0x1ff));
        try {
            EncoProtocol.setNoiseModeFrame(99, 0);
            throw new AssertionError("Unsupported noise mode was accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void varintBoundaries() {
        int[] sizes = {0, 1, 120, 121, 248, 249, 16376, 16377};
        String[] lengths = {"07", "08", "7f", "8001", "ff01", "8002", "ff7f", "808001"};
        for (int n = 0; n < sizes.length; n++) {
            byte[] payload = new byte[sizes[n]];
            Arrays.fill(payload, (byte) 0x5a);
            byte[] actual = EncoProtocol.singleFrame(0x1234, payload, 0xab);
            byte[] length = hex(lengths[n]);
            equal(1 + length.length + 7 + payload.length, actual.length, "total frame size");
            equal(0xaa, actual[0] & 0xff, "sync byte");
            check(Arrays.equals(length, Arrays.copyOfRange(actual, 1, 1 + length.length)), "varint boundary " + sizes[n]);
            int header = 1 + length.length;
            equal(0, actual[header], "control");
            equal(0, actual[header + 1], "reserved");
            equal(0x34, actual[header + 2] & 0xff, "command low");
            equal(0x12, actual[header + 3] & 0xff, "command high");
            equal(0xab, actual[header + 4] & 0xff, "sequence");
            equal(payload.length & 0xff, actual[header + 5] & 0xff, "payload length low");
            equal(payload.length >>> 8, actual[header + 6] & 0xff, "payload length high");
            check(Arrays.equals(payload, Arrays.copyOfRange(actual, header + 7, actual.length)), "payload preserved");
        }
    }

    private static void batteries() {
        battery(hex("aa0f00000402ff08000103016402640314"), 100, 100, 20, false, false, false);
        battery(hex("aa0f00000402ff0800010301d502ba0394"), 85, 58, 20, true, true, true);
        // Partial notifications must preserve absence as -1 so the UI can merge fields.
        battery(hex("aa0b00000402ff04000101012a"), 42, -1, -1, false, false, false);
        battery(hex("aa0b00000402ff0400010102b2"), -1, 50, -1, false, true, false);
        battery(hex("aa0b00000402ff040001010300"), -1, -1, 0, false, false, false);
        battery(hex("aa0f000017060008000003016402640314"), 100, 100, 20, false, false, false);
        // Batch response with an unrelated subcommand before the battery response.
        battery(EncoProtocol.singleFrame(0x812f, hex("00023412010042060108000003016402640314"), 0x70),
                100, 100, 20, false, false, false);
        battery(EncoProtocol.singleFrame(0x812f, hex("0001060108000003016402640314"), 0x70),
                100, 100, 20, false, false, false);
        check(EncoProtocol.parseBattery(EncoProtocol.singleFrame(0x0204, hex("01010432"), 0)) == null,
                "unknown device type ignored");
        check(EncoProtocol.parseBattery(EncoProtocol.singleFrame(0x0204, hex("0203010402040304"), 0)) == null,
                "case event cannot become battery data");
    }

    private static void modes() {
        String[] payloads = {"03010101", "03010102", "03010104", "0301010800", "0301011000", "0301010001"};
        int[] expected = {EncoProtocol.MODE_OFF, EncoProtocol.MODE_ANC, EncoProtocol.MODE_TRANSPARENCY,
                EncoProtocol.MODE_OFF, EncoProtocol.MODE_ANC, EncoProtocol.MODE_TRANSPARENCY};
        for (int i = 0; i < payloads.length; i++) {
            equal(expected[i], EncoProtocol.parseNoiseMode(EncoProtocol.singleFrame(0x0204, hex(payloads[i]), 0xff)), "mode bitmap");
        }
        String[] replies = {"010101", "010102", "010104", "01010800", "01011000", "01010001", "0001011000"};
        int[] replyModes = {EncoProtocol.MODE_OFF, EncoProtocol.MODE_ANC, EncoProtocol.MODE_TRANSPARENCY,
                EncoProtocol.MODE_OFF, EncoProtocol.MODE_ANC, EncoProtocol.MODE_TRANSPARENCY,
                EncoProtocol.MODE_ANC};
        for (int i = 0; i < replies.length; i++) {
            equal(replyModes[i], EncoProtocol.parseNoiseMode(
                    EncoProtocol.singleFrame(0x810C, hex(replies[i]), 0x47)), "mode query response");
        }
        for (String variant : new String[]{"01012000", "01014000", "01018000", "01010008"}) {
            equal(EncoProtocol.MODE_ANC, EncoProtocol.parseNoiseMode(
                    EncoProtocol.singleFrame(0x810C, hex(variant), 0x47)),
                    "Free4 ANC depth or adaptive query response");
        }
        for (String variant : new String[]{"03010120", "03010140", "03010180", "0301010008"}) {
            equal(EncoProtocol.MODE_ANC, EncoProtocol.parseNoiseMode(
                    EncoProtocol.singleFrame(0x0204, hex(variant), 0x47)),
                    "Free4 ANC depth or adaptive notification");
        }
        for (String invalid : new String[]{"0101", "010100", "02010001", "01010010", "0001010000", "7f010104"}) {
            equal(-1, EncoProtocol.parseNoiseMode(EncoProtocol.singleFrame(0x810C, hex(invalid), 0)),
                    "malformed or unknown mode query response");
        }
        for (String invalid : new String[]{"03010100", "0301010010", "03020102", "03010202", "01010102"}) {
            equal(-1, EncoProtocol.parseNoiseMode(EncoProtocol.singleFrame(0x0204, hex(invalid), 0)), "unknown mode event");
        }
        equal(-1, EncoProtocol.parseNoiseMode(EncoProtocol.singleFrame(0x1234, hex("03010102"), 0)), "unknown command");
    }

    private static void caseStatus() {
        EncoProtocol.EarStatus inCase = EncoProtocol.parseEarStatus(hex("aa0f00000402ff08000203010402040304"));
        check(inCase != null && inCase.bothInCase(), "both buds in case");
        EncoProtocol.EarStatus oneOut = EncoProtocol.parseEarStatus(hex("aa0f00000402ff08000203010502040304"));
        check(oneOut != null && !oneOut.bothInCase() && !oneOut.leftInBox && oneOut.rightInBox, "one bud out of case");
        check(EncoProtocol.parseEarStatus(EncoProtocol.singleFrame(0x0204, hex("02010104"), 0)) == null,
                "incomplete ear status must not assert both in case");
    }

    private static void malformedInputs() {
        check(EncoProtocol.parseBattery(null) == null, "null battery input");
        check(EncoProtocol.parseEarStatus(null) == null, "null ear status input");
        equal(-1, EncoProtocol.parseNoiseMode(null), "null noise input");
        byte[][] frames = {
                hex("aa0f00000402ff08000103016402640314"),
                hex("aa0c00000402ff05000301010001"),
                hex("aa0f00000402ff08000203010402040304")
        };
        for (byte[] frame : frames) {
            for (int length = 0; length < frame.length; length++) {
                byte[] truncated = Arrays.copyOf(frame, length);
                check(EncoProtocol.parseBattery(truncated) == null, "truncated battery frame");
                equal(-1, EncoProtocol.parseNoiseMode(truncated), "truncated mode frame");
                check(EncoProtocol.parseEarStatus(truncated) == null, "truncated case frame");
            }
        }
        for (String payload : new String[]{"01000164", "01050164", "01030164", "03010102"}) {
            check(EncoProtocol.parseBattery(EncoProtocol.singleFrame(0x0204, hex(payload), 0)) == null,
                    "invalid notification count or type");
        }
        for (String payload : new String[]{"01010601040000010132", "00000601040000010132", "00010601ffff00010132", "00010601010000"}) {
            check(EncoProtocol.parseBattery(EncoProtocol.singleFrame(0x812f, hex(payload), 0)) == null,
                    "malformed batch response");
        }
        // Deterministic robustness exercise: malformed incoming data must not crash a reader.
        Random random = new Random(0x181);
        for (int i = 0; i < 2000; i++) {
            byte[] input = new byte[random.nextInt(257)];
            random.nextBytes(input);
            EncoProtocol.parseBattery(input);
            EncoProtocol.parseNoiseMode(input);
            EncoProtocol.parseEarStatus(input);
            checks++;
        }
    }

    private static void battery(byte[] frame, int left, int right, int box,
                                boolean leftCharging, boolean rightCharging, boolean boxCharging) {
        EncoProtocol.Battery battery = EncoProtocol.parseBattery(frame);
        check(battery != null, "expected battery frame");
        equal(left, battery.left, "left battery");
        equal(right, battery.right, "right battery");
        equal(box, battery.caseBattery, "case battery");
        check(leftCharging == battery.leftCharging, "left charging");
        check(rightCharging == battery.rightCharging, "right charging");
        check(boxCharging == battery.caseCharging, "case charging");
    }

    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static void hexEquals(String expected, byte[] actual) {
        check(expected.equals(HexFormat.of().formatHex(actual)), "frame vector " + expected);
    }
    private static void equal(int expected, int actual, String label) {
        check(expected == actual, label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean result, String label) {
        checks++;
        if (!result) throw new AssertionError(label);
    }
}
