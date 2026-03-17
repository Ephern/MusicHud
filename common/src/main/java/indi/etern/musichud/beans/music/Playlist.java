package indi.etern.musichud.beans.music;

import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.network.Codecs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Playlist implements MusicCollection {
    public static final StreamCodec<RegistryFriendlyByteBuf, Playlist> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            Playlist::getId,
            ByteBufCodecs.STRING_UTF8,
            Playlist::getName,
            ByteBufCodecs.LONG,
            Playlist::getCoverImgId,
            ByteBufCodecs.STRING_UTF8,
            Playlist::getCoverImgId_str,
            ByteBufCodecs.STRING_UTF8,
            Playlist::getCoverImgUrl,
            Profile.STREAM_CODEC,
            Playlist::getCreator,
            Codecs.ofList(() -> MusicDetail.CODEC),
            Playlist::getTracks,
            Playlist::new
    );

    public static final Playlist EMPTY = new Playlist();

    @Getter
    long id = -1;
    String name = "";
    @Getter
    long coverImgId = -1;
    String coverImgId_str = "";
    String coverImgUrl = "";
    Profile creator = Profile.ANONYMOUS;
    @Setter
    List<MusicDetail> tracks = List.of();
    List<PrivilegeInfo> privileges = List.of();

    protected Playlist(
            long id,
            String name,
            long coverImgId,
            String coverImgId_str,
            String coverImgUrl,
            Profile creator,
            List<MusicDetail> tracks
    ) {
        this.id = id;
        this.name = name;
        this.coverImgId = coverImgId;
        this.coverImgId_str = coverImgId_str;
        this.coverImgUrl = coverImgUrl;
        this.creator = creator;
        this.tracks = tracks;
    }

    public String getName() {
        return Objects.requireNonNullElse(name, "");
    }

    @Override
    public String getNameI18nKey() {
        return "music_hud.text.playlist";
    }

    @Override
    public String getImageThumbnailUrl(int size) {
        return getThumbnailCoverUrl(size);
    }

    @Override
    public List<MusicDetail> getMusicDetails() {
        return getTracks();
    }

    @Override
    public CompletableFuture<Collection<MusicDetail>> loadMusicDetails() {
        CompletableFuture<Collection<MusicDetail>> future = new CompletableFuture<>();
        MusicService.getInstance().loadPlaylistDetail(id).thenAccept(playlist -> future.complete(playlist.tracks));
        return future;
    }

    public String getCoverImgId_str() {
        return Objects.requireNonNullElse(coverImgId_str, "");
    }

    public String getCoverImgUrl() {
        return Objects.requireNonNullElse(coverImgUrl, "");
    }

    public String getThumbnailCoverUrl(int size) {
        return coverImgUrl + "?param=" + size + "y" + size;
    }

    public Profile getCreator() {
        return Objects.requireNonNullElse(creator, Profile.ANONYMOUS);
    }

    public List<MusicDetail> getTracks() {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }
        return tracks.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Playlist playlist && playlist.id == id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
