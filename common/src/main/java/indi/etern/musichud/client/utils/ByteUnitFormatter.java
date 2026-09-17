package indi.etern.musichud.client.utils;

import java.util.Locale;

public class ByteUnitFormatter {
    private ByteUnitFormatter() {}

    public static String formatSize(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must be >= 0");
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int unitIndex = -1;

        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }

        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }
}
