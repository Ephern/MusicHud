package indi.etern.musichud.beans.api;

import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import indi.etern.musichud.server.api.playmode.PlayMode;
import indi.etern.musichud.throwable.MusicResourceLoadingException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Getter
@ToString
public final class IdlePlaySource {
    public static final ByteBufCodec<IdlePlaySource> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            IdlePlaySource::getId,
            Codecs.CLASS,
            IdlePlaySource::getType,
            Codecs.ofEnum(PlayMode.class),
            IdlePlaySource::getPlayMode,
            PusherInfo.CODEC,
            IdlePlaySource::getPusherInfo,
            IdlePlaySource::new
    );

    private final long id;
    private final Class<? extends MusicCollection> type;
    @Setter
    private PlayMode playMode;
    @Setter
    transient private PusherInfo pusherInfo;
    @Getter
    transient private boolean dataLoaded = false;
    @Setter
    transient private MusicCollection musicCollection;

    public IdlePlaySource(long id, Class<?> type, PlayMode playMode, PusherInfo pusherInfo) {
        if (!MusicCollection.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Type is not assignable from MusicCollection.class");
        }
        this.id = id;
        //noinspection unchecked
        this.type = (Class<? extends MusicCollection>) type;
        this.playMode = playMode;
        this.pusherInfo = pusherInfo;
    }

    public static IdlePlaySource of(long id, Class<? extends MusicCollection> type, PlayMode playMode) {
        return new IdlePlaySource(id, type, playMode, PusherInfo.EMPTY);
    }

    public static IdlePlaySource of(MusicCollection collection, PlayMode playMode) {
        return new IdlePlaySource(collection.getId(), collection.getClass(), playMode, PusherInfo.EMPTY);
    }

    public void serverLoadMusicCollection(UUID playerUUID) {
        if (musicCollection == null) {
            if (type.equals(Album.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getAlbumInfoDetail(id, true, playerUUID);
            } else if (type.equals(Playlist.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getPlaylistDetail(id, true, playerUUID);
            }
        }
    }

    /**
     * R3 sampling: delegates track selection to this source's play mode, then
     * completes extra info / pusher info and wraps the track with its source meta.
     */
    public @Nullable Traceable<MusicDetail> sampleRandomTrack() {
        MusicDetail sampledTrack = playMode.selectTrack(this);
        if (sampledTrack == null) {
            return null;
        }
        IMusicApiService musicApiService = IMusicApiService.getInstance(ApiProvider.NCM);
        if (sampledTrack.getExtraInfo() == null) {
            List<MusicDetail> detailByIds;
            long id = sampledTrack.getId();
            try {
                detailByIds = musicApiService.getMusicDetailByIds(List.of(id), null);
            } catch (TimeoutException | InterruptedException e) {
                throw new MusicResourceLoadingException(e, id, false);
            }
            if (detailByIds.size() == 1) {
                MusicDetail musicDetail = detailByIds.getFirst();
                if (musicDetail.getId() == id) {
                    sampledTrack.setExtraInfo(musicDetail.getExtraInfo());
                } else {
                    throw new MusicResourceLoadingException(new IllegalStateException("Api returned a music detail with different id"), sampledTrack, false);
                }
            } else {
                throw new MusicResourceLoadingException(new IllegalStateException("Api returned a invalid music detail"), sampledTrack, false);
            }
        }
        LoginApiService.PlayerLoginInfo loginInfo = ILoginApiService.getInstance(ApiProvider.NCM)
                .getLoginInfoByPlayerUUID(pusherInfo.getPlayerUUID());
        sampledTrack.setPusherInfo(loginInfo != null ? pusherInfo : PusherInfo.EMPTY);
        return Traceable.of(sampledTrack, new SourceMeta(id, type));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IdlePlaySource that && id == that.id && Objects.equals(type, that.type) && playMode == that.playMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, playMode);
    }
}
