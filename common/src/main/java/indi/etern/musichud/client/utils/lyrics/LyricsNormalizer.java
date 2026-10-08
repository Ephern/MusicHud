package indi.etern.musichud.client.utils.lyrics;

public class LyricsNormalizer {
    public static String normalize(String string) {
        return string.replace('\u00A0', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll(",(?! )", ", ")
                .trim();
    }
}
