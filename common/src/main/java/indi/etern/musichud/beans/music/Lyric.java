package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@EqualsAndHashCode
public class Lyric {
    public static final ByteBufCodec<Lyric> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8,
            Lyric::getLyric,
            Lyric::new
    );
    public static final Lyric NONE = new Lyric("");
    String lyric;

    public String getLyric() {
        return Objects.requireNonNullElse(lyric, "");
    }
}
