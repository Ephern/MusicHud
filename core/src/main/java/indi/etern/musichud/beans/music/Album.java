package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Album implements MusicCollection{
    public static final ByteBufCodec<Album> CODEC = ByteBufCodec.composite(
            Codecs.LONG, Album::getId,
            Codecs.STRING_UTF8, Album::getName,
            Codecs.STRING_UTF8, Album::getPicUrl,
            Codecs.STRING_UTF8, Album::getType,
            Codecs.STRING_UTF8, Album::getCompany,
            Codecs.INT, Album::getMusicTrackCount,
            Codecs.ofCollection(LinkedHashSet::new, () -> MusicDetail.CODEC), Album::getMusicDetails,
            Codecs.ofCollection(LinkedHashSet::new, () -> Artist.CODEC), Album::getArtists,
            PusherInfo.CODEC, Album::getPusherInfo,
            Album::new
    );
    public static final Album NONE = new Album();
    @Getter
    long id;
    String name = "";
    String picUrl = "";
    String type = "";
    String company = "";
    @SerializedName("size")
    @Getter
    int musicTrackCount;
    @SerializedName("songs")
    @Setter
    SequencedSet<MusicDetail> musicDetails = new LinkedHashSet<>();
    SequencedSet<Artist> artists = new LinkedHashSet<>();
    // Not contained in the original API response, set separately
    @Getter
    transient PusherInfo pusherInfo = PusherInfo.EMPTY;

    public String getThumbnailPicUrl(int size) {
        return picUrl + "?param=" + size + "y" + size;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    @Override
    public String getNameI18nKey() {
        return MusicHud.MOD_ID + ".text.album";
    }

    public String getPicUrl() {
        return Objects.requireNonNullElse(picUrl, "");
    }
    public String getType() {
        return Objects.requireNonNullElse(type, "");
    }

    public String getCompany() {
        return Objects.requireNonNullElse(company, "");
    }

    @Override
    public SequencedSet<MusicDetail> getMusicDetails() {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return new LinkedHashSet<>(0);
        }
        return musicDetails.stream().filter(Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, LinkedHashSet::addAll);
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailPicUrl(size);
    }

    public SequencedSet<Artist> getArtists() {
        return Objects.requireNonNullElse(artists, new LinkedHashSet<>());
    }

    public Album shallowCopyBriefInfo() {
        Album album = new Album();
        album.id = this.id;
        album.name = this.name;
        album.picUrl = this.picUrl;
        return album;
    }

    @Override
    public Album copyWithPusherInfo(PusherInfo pusherInfo) {
        Album album = shallowCopyBriefInfo();
        album.pusherInfo = pusherInfo;
        return album;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Album album
                && album.id == id
                && album.name.equals(name)
                && album.picUrl.equals(picUrl)
                && album.pusherInfo.equals(pusherInfo);
    }

    @Override
    public boolean equalsLoose(Object obj) {
        return obj instanceof Album album && id == album.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, picUrl, pusherInfo.getPlayerUUID());
    }
}