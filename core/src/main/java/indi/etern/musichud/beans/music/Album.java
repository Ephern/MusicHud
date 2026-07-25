package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Album implements MusicCollection{
    public static final ByteBufCodec<Album> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            Album::getId,
            Codecs.STRING_UTF8,
            Album::getName,
            Codecs.STRING_UTF8,
            Album::getPicUrl,
            Codecs.ofList(() -> MusicDetail.CODEC),
            Album::getMusicDetails,
            Codecs.ofList(() -> Artist.CODEC),
            Album::getArtists,
            PusherInfo.CODEC,
            Album::getPusherInfo,
            Album::new
    );
    public static final Album NONE = new Album();
    @Getter
    long id;
    String name = "";
    String picUrl = "";
    @SerializedName("songs")
    @Setter
    List<MusicDetail> musicDetails = new ArrayList<>();
    List<Artist> artists = new ArrayList<>();

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

    @Override
    public List<MusicDetail> getMusicDetails() {
        List<MusicDetail> musicDetails = Objects.requireNonNullElse(this.musicDetails, new ArrayList<>());
        return musicDetails.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailPicUrl(size);
    }

    public List<Artist> getArtists() {
        return Objects.requireNonNullElse(artists, new ArrayList<>());
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