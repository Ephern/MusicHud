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
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicDetail> CODEC = Codecs.composite(
            ByteBufCodecs.STRING_UTF8,
            MusicDetail::getName,
            ByteBufCodecs.VAR_LONG,
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
    @SerializedName("al")
    @Setter
    Album album = Album.NONE;
    @SerializedName("dt")
    @Getter
    int durationMillis;
    @Getter
    long mark; // bit mask
    @SerializedName("tns")
    List<String> translations = List.of();

    // only useful for server, and its a optional api field
    @SerializedName("privilege")
    @Setter
    @Getter
    ExtraInfo extraInfo;
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

    public record ExtraInfo(
            @SerializedName("cs")
            boolean cloudSource,
            @SerializedName("st")
            int copyrightStatus,//0 is normal, less than 0 means no copyright
            @SerializedName("toast")
            boolean disabledAsCopyrightProtect
    ) {
    }
}