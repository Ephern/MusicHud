package indi.etern.musichud.client.utils;

import indi.etern.musichud.MusicHud;
import net.minecraft.client.resources.language.I18n;

/**
 * Format a large count into a compact, locale-aware representation using
 * units and a radix read from the i18n language files.
 *
 * <p>Example config (en_us): units "K,M,B", base "1000" → 1500 → "1.5K".
 * Example config (zh_cn): units "万,亿", base "10000" → 15000 → "1.5万".</p>
 */
public final class CountFormatter {
    private static final String KEY_UNITS = MusicHud.MOD_ID + ".text.countUnit";
    private static final String KEY_BASE = MusicHud.MOD_ID + ".text.countBase";

    private CountFormatter() {
    }

    public static String formatCount(long count) {
        String baseString = I18n.get(KEY_BASE);
        if (baseString.equals(KEY_BASE) || baseString.isBlank()) {
            return String.valueOf(count);
        }
        long base;
        try {
            base = Long.parseLong(baseString.trim());
        } catch (NumberFormatException e) {
            return String.valueOf(count);
        }
        if (base <= 1) {
            return String.valueOf(count);
        }

        String unitsString = I18n.get(KEY_UNITS);
        if (unitsString.equals(KEY_UNITS) || unitsString.isBlank()) {
            return String.valueOf(count);
        }
        String[] units = unitsString.split(",");
        if (units.length == 0) {
            return String.valueOf(count);
        }

        double value = count;
        int unitIndex = -1;
        while (value >= base && unitIndex < units.length - 1) {
            value /= base;
            unitIndex++;
        }
        if (unitIndex < 0) {
            return String.valueOf(count);
        }
        return formatOneDecimal(value) + units[unitIndex].trim();
    }

    /**
     * Format to at most one decimal place, stripping trailing zeros
     * (e.g. 1.50 → "1.5", 2.00 → "2").
     */
    private static String formatOneDecimal(double value) {
        long scaled = Math.round(value * 10.0);
        long whole = scaled / 10;
        long fraction = scaled % 10;
        if (fraction == 0) {
            return String.valueOf(whole);
        }
        return whole + "." + fraction;
    }
}