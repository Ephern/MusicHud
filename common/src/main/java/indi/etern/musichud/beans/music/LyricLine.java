package indi.etern.musichud.beans.music;

import lombok.*;

import java.time.Duration;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LyricLine {
    public enum Type {
        NORMAL, META_DATA, RHYTHM
    }
    @Setter
    Type type;
    Duration startTime;
    Duration duration;
    String text;
    String translatedText;

    public String getTranslatedText() {
        return Objects.requireNonNullElse(translatedText, "");
    }

    public String getText() {
        return Objects.requireNonNullElse(text, "");
    }

    public Duration getStartTime() {
        return Objects.requireNonNullElse(startTime, Duration.ZERO);
    }
}