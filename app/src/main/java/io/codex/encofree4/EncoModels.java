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

    /** Colour keys for the X4 artwork, cycled by the panel's colour swatch. */
    static final String[] X4_COLORS = {"white", "purple", "pink"};

    /** Model artwork for the panel; unknown names fall back to the generic drawing. */
    static int deviceImage(String deviceName, String color) {
        if (!normalize(deviceName).contains("encox4")) {
            return R.drawable.enco_device;
        }
        if ("purple".equals(color)) {
            return R.drawable.enco_x4_device_purple;
        }
        if ("pink".equals(color)) {
            return R.drawable.enco_x4_device_pink;
        }
        return R.drawable.enco_x4_device_white;
    }

    /** Adaptive is a separate major mode only on the model verified to expose it. */
    static boolean hasAdaptiveMode(String deviceName) {
        return normalize(deviceName).contains("encox4");
    }
}
