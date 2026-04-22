package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
@EqualsAndHashCode
public class LyricInfo {
    public static final StreamCodec<RegistryFriendlyByteBuf, LyricInfo> CODEC = StreamCodec.composite(
            Lyric.CODEC,
            LyricInfo::getLyric,
            Lyric.CODEC,
            LyricInfo::getTranslatedLyric,
            Lyric.CODEC,
            LyricInfo::getWordByWordLyric,
            Lyric.CODEC,
            LyricInfo::getWordByWordTranslatedLyric,
            LyricInfo::new
    );
    public static final LyricInfo NONE = new LyricInfo();
    @Getter
    int code = 0;
    @SerializedName("lrc")
    Lyric lyric = Lyric.NONE;
    @SerializedName("tlyric")
    Lyric translatedLyric = Lyric.NONE;
    @SerializedName("yrc")
    Lyric wordByWordLyric = Lyric.NONE;
    @SerializedName("ytlrc")
    Lyric wordByWordTranslatedLyric = Lyric.NONE;

    public LyricInfo(
            Lyric lyric,
            Lyric translatedLyric,
            Lyric wordByWordLyric,
            Lyric wordByWordTranslatedLyric
    ) {
        this.lyric = lyric;
        this.translatedLyric = translatedLyric;
        this.wordByWordLyric = wordByWordLyric;
        this.wordByWordTranslatedLyric = wordByWordTranslatedLyric;
    }

    public Lyric getLyric() {
        return Objects.requireNonNullElse(lyric, Lyric.NONE);
    }

    public Lyric getTranslatedLyric() {
        return Objects.requireNonNullElse(translatedLyric, Lyric.NONE);
    }

    public Lyric getWordByWordLyric() {
        return Objects.requireNonNullElse(wordByWordLyric, Lyric.NONE);
    }

    public Lyric getWordByWordTranslatedLyric() {
        return Objects.requireNonNullElse(wordByWordTranslatedLyric, Lyric.NONE);
    }

    public boolean withWordByWordLyric() {
        return wordByWordLyric != Lyric.NONE && !wordByWordLyric.lyric.isEmpty();
    }
}