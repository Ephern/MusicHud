package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MusicDetail implements IdentifiedBeans {
    public static final ByteBufCodec<MusicDetail> CODEC = ByteBufCodec.composite(
            Codecs.LONG, MusicDetail::getId,
            Codecs.STRING_UTF8, MusicDetail::getName,
            Codecs.INT, MusicDetail::getDurationMillis,
            Codecs.ofEnum(Fee.class), MusicDetail::getFee,
            Album.CODEC, MusicDetail::getAlbum,
            Codecs.ofList(() -> Codecs.STRING_UTF8), MusicDetail::getAlias,
            Codecs.ofList(() -> Codecs.STRING_UTF8), MusicDetail::getTranslations,
            Codecs.ofList(() -> Artist.CODEC), MusicDetail::getArtists,
            PusherInfo.CODEC, MusicDetail::getPusherInfo,
            LyricInfo.CODEC, MusicDetail::getLyricInfo,
            Codecs.UUID, MusicDetail::getQueueUniqueID,
            MusicDetail::new
    );
    public static final MusicDetail NONE = new MusicDetail();
    public static final UUID QUEUE_UNIQUE_ID_ZERO = new UUID(0, 0);
    String name = "";
    @Getter
    long id;
    @Setter
    UUID queueUniqueID = QUEUE_UNIQUE_ID_ZERO;
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
    @Getter
    Fee fee = Fee.UNSET;

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
            long id,
            String name,
            int durationMillis,
            Fee fee,
            Album album,
            List<String> alias,
            List<String> translations,
            List<Artist> artists,
            PusherInfo pusherInfo,
            LyricInfo lyricInfo,
            UUID queueUniqueID
    ) {
        this.name = name;
        this.id = id;
        this.artists = artists;
        this.alias = alias;
        this.fee = fee;
        this.album = album;
        this.durationMillis = durationMillis;
        this.translations = translations;
        this.pusherInfo = pusherInfo;
        this.lyricInfo = lyricInfo;
        this.queueUniqueID = queueUniqueID;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    public UUID getQueueUniqueID() {
        return Objects.requireNonNullElseGet(queueUniqueID, () -> QUEUE_UNIQUE_ID_ZERO);
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
        return this == obj || (obj instanceof MusicDetail other && this.id == other.id && this.queueUniqueID == other.queueUniqueID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, queueUniqueID);
    }

    public MusicDetail randomQueueUniqueIDCopy() {
        MusicDetail musicDetail = new MusicDetail(
                id, name, durationMillis, fee, album, alias, translations, artists, pusherInfo, lyricInfo, UUID.randomUUID()
        );
        musicDetail.extraInfo = extraInfo;
        musicDetail.pusherInfo = pusherInfo;
        musicDetail.lyricInfo = lyricInfo;
        return musicDetail;
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