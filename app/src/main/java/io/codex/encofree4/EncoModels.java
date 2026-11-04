package io.codex.encofree4;

import java.util.Locale;

/**
 * Supported headset models.
 *
 * <p>All entries currently share the same Classic Bluetooth SPP transport and the
 * same TLV command set documented in {@code docs/PROTOCOL.md}. Add a model by
 * appending its normalized name token; if a model turns out to need different
 * command parameters or noise bitmaps, branch on the parsed values instead of
 * adding another hard-coded name check in the callers.
 */
final class EncoModels {

    /**
     * Normalized (lowercase, whitespace removed) name fragments.
     *
     * <p>Free4 is the model the original implementation was validated against.
     * X4 is expected to answer the same commands; it is pending on-device
     * verification with firmware 116.116.100.
     */
    private static final String[] NAMES = {
            "encofree4",
            "encox4",
    };

    private EncoModels() {
    }

    static String normalize(String deviceName) {
        return deviceName == null
                ? ""
                : deviceName.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    static boolean isSupported(String deviceName) {
        String normalized = normalize(deviceName);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String model : NAMES) {
            if (normalized.contains(model)) {
                return true;
            }
        }
        return false;
    }

    /** Model artwork for the panel; unknown names fall back to the generic drawing. */
    static int deviceImage(String deviceName) {
        return normalize(deviceName).contains("encox4")
                ? R.drawable.enco_x4_device
                : R.drawable.enco_device;
    }
}
