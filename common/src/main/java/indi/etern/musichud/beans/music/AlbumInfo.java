package indi.etern.musichud.beans.music;

import com.google.gson.annotations.SerializedName;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.network.Codecs;
import lombok.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AlbumInfo implements MusicCollection{
    public static final StreamCodec<RegistryFriendlyByteBuf, AlbumInfo> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            AlbumInfo::getId,
            ByteBufCodecs.STRING_UTF8,
            AlbumInfo::getName,
            ByteBufCodecs.STRING_UTF8,
            AlbumInfo::getPicUrl,
            ByteBufCodecs.LONG,
            AlbumInfo::getPicSize,
            Codecs.ofList(() -> MusicDetail.CODEC),
            AlbumInfo::getMusicDetails,
            Codecs.ofList(() -> Artist.CODEC),
            AlbumInfo::getArtists,
            PusherInfo.CODEC,
            AlbumInfo::getPusherInfo,
            AlbumInfo::new
    );
    public static final AlbumInfo NONE = new AlbumInfo();
    @Getter
    long id;
    String name = "";
    String picUrl = "";
    @SerializedName("pic")
    @Getter
    long picSize;
    @SerializedName("songs")
    @Setter
    List<MusicDetail> musicDetails = new ArrayList<>();
    List<Artist> artists = new ArrayList<>();

    // Not contained in the original API response, set separately
    @Getter
    PusherInfo pusherInfo = PusherInfo.EMPTY;

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

    @Override
    public CompletableFuture<Collection<MusicDetail>> loadMusicDetails(boolean ignoreCache) {
        CompletableFuture<Collection<MusicDetail>> future = new CompletableFuture<>();
        MusicService.getInstance().loadAlbumDetail(id, ignoreCache).thenAccept(albumInfo -> future.complete(albumInfo.musicDetails));
        return future;
    }

    public List<Artist> getArtists() {
        return Objects.requireNonNullElse(artists, new ArrayList<>());
    }

    public AlbumInfo shallowCopyBriefInfo() {
        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.id = this.id;
        albumInfo.name = this.name;
        albumInfo.picUrl = this.picUrl;
        albumInfo.picSize = this.picSize;
        return albumInfo;
    }

    @Override
    public AlbumInfo copyWithPusherInfo(PusherInfo pusherInfo) {
        AlbumInfo albumInfo = shallowCopyBriefInfo();
        albumInfo.pusherInfo = pusherInfo;
        return albumInfo;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AlbumInfo albumInfo && id == albumInfo.id && pusherInfo.equals(albumInfo.pusherInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pusherInfo.playerUUID());
    }
}