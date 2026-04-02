package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.server.api.impl.ncm.MusicApiService;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public interface IMusicApiService {
    static IMusicApiService getInstance(ApiProvider apiProvider) {
        if (Objects.requireNonNull(apiProvider) == ApiProvider.NCM) {
            return MusicApiService.getInstance();
        }
        throw new IllegalArgumentException("Invalid api provider");
    }

    Playlist getPlaylistDetail(long id, @Nullable ServerPlayer serverPlayer);

    List<Album> searchAlbums(String keywords, int offset);

    List<Artist> searchArtists(String keywords, int offset);

    List<MusicDetail> searchMusic(String keywords, int offset);

    List<Playlist> searchPlaylists(String keywords, int offset);

    <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer);

    List<MusicDetail> getMusicDetailByIds(List<Long> ids, ServerPlayer serverPlayer);

    Album getAlbumInfoDetail(long id, ServerPlayer serverPlayer);

    Artist getArtistDetail(long id, ServerPlayer serverPlayer);

    List<MusicDetail> getArtistMoreMusic(long id, int offset, ServerPlayer serverPlayer);

    MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, ServerPlayer serverPlayer);

    List<Playlist> getPlayersUserSubscribedPlaylists(ServerPlayer player);

    List<Album> getPlayersUserSubscribedAlbums(ServerPlayer player);

    List<Artist> getPlayersUserSubscribedArtists(ServerPlayer player);

    LyricInfo getLyricInfo(MusicDetail musicDetail);
}