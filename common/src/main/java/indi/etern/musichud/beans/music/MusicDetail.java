package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MusicDetail {
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicDetail> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MusicDetail::getName,
            ByteBufCodecs.LONG,
            MusicDetail::getId,
            Codecs.ofList(() -> Artist.CODEC),
            MusicDetail::getArtists,
            Codecs.ofList(() -> ByteBufCodecs.STRING_UTF8),
            MusicDetail::getAlias,
            Album.CODEC,
            MusicDetail::getAlbum,
            ByteBufCodecs.INT,
            MusicDetail::getDurationMillis,
            Codecs.ofList(() -> ByteBufCodecs.STRING_UTF8),
            MusicDetail::getTranslations,
            PusherInfo.CODEC,
            MusicDetail::getPusherInfo,
            LyricInfo.CODEC,
            MusicDetail::getLyricInfo,
            MusicDetail::new
    );
    public static final MusicDetail NONE = new MusicDetail();
    String name = "";
    @Getter
    long id;
    @SerializedName("ar")
    List<Artist> artists = List.of();
    @SerializedName("alia")
    List<String> alias = List.of();
    @SerializedName("pop")
    @Getter
    int popularity;
    @SerializedName("v")
    @Getter
    int infoVersion;
    @SerializedName("version")
    @Getter
    int musicVersion;
    @SerializedName("al")
    @Setter
    Album album = Album.NONE;
    @SerializedName("dt")
    @Getter
    int durationMillis;
    @SerializedName("hr")
    QualityInfo hiRes = QualityInfo.NONE;
    @SerializedName("sq")
    QualityInfo sq = QualityInfo.NONE;
    @SerializedName("h")
    QualityInfo high = QualityInfo.NONE;
    @SerializedName("m")
    QualityInfo medium = QualityInfo.NONE;
    @SerializedName("l")
    QualityInfo low = QualityInfo.NONE;
    @Getter
    long mark; // bit mask
    @SerializedName("tns")
    List<String> translations = List.of();

    // Not contained in the original API response, set separately
    @Setter
    PusherInfo pusherInfo = PusherInfo.EMPTY;
    @Setter
    LyricInfo lyricInfo = LyricInfo.NONE;

    protected MusicDetail(
            String name,
            long id,
            List<Artist> artists,
            List<String> alias,
            Album album,
            int durationMillis,
            List<String> translations,
            PusherInfo pusherInfo,
            LyricInfo lyricInfo
    ) {
        this.name = name;
        this.id = id;
        this.artists = artists;
        this.alias = alias;
        this.album = album;
        this.durationMillis = durationMillis;
        this.translations = translations;
        this.pusherInfo = pusherInfo;
        this.lyricInfo = lyricInfo;
    }

    public QualityInfo getHiRes() {
        return Objects.requireNonNullElse(hiRes, QualityInfo.NONE);
    }

    public QualityInfo getSq() {
        return Objects.requireNonNullElse(sq, QualityInfo.NONE);
    }

    public QualityInfo getHigh() {
        return Objects.requireNonNullElse(high, QualityInfo.NONE);
    }

    public QualityInfo getMedium() {
        return Objects.requireNonNullElse(medium, QualityInfo.NONE);
    }

    public QualityInfo getLow() {
        return Objects.requireNonNullElse(low, QualityInfo.NONE);
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    public List<Artist> getArtists() {
        if (artists == null || artists.isEmpty()) {
            return List.of();
        }
        return artists.stream().filter(Objects::nonNull).toList();
    }

    public List<String> getAlias() {
        if (alias == null || alias.isEmpty()) {
            return List.of();
        }
        return alias.stream().filter(Objects::nonNull).toList();
    }

    public Album getAlbum() {
        return Objects.requireNonNullElse(album, Album.NONE);
    }

    public List<String> getTranslations() {
        if (translations == null || translations.isEmpty()) {
            return List.of();
        }
        return translations.stream().filter(Objects::nonNull).toList();
    }

    public PusherInfo getPusherInfo() {
        return Objects.requireNonNullElse(pusherInfo, PusherInfo.EMPTY);
    }

    public LyricInfo getLyricInfo() {
        return Objects.requireNonNullElse(lyricInfo, LyricInfo.NONE);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof MusicDetail other && this.id == other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}