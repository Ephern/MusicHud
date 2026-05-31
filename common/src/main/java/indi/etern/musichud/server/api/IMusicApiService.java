package indi.etern.musichud.server.api;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.server.api.impl.ncm.MusicApiService;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

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

    Playlist getPlaylistDetail(long id, @Nullable Player player);

    List<Album> searchAlbums(String keywords, int offset);

    List<Artist> searchArtists(String keywords, int offset);

    List<MusicDetail> searchMusic(String keywords, int offset);

    List<Playlist> searchPlaylists(String keywords, int offset);

    <T> T search(String keywords, int offset, int limit, SearchType searchType, Function<String, T> transformer);

    List<MusicDetail> getMusicDetailByIds(List<Long> ids, Player player);

    Album getAlbumInfoDetail(long id, Player player);

    Artist getArtistDetail(long id, Player player);

    List<MusicDetail> getArtistMoreMusic(long id, int offset, Player player);

    MusicResourceInfo getResourceInfo(MusicDetail musicDetail, Quality quality, Player player);

    List<Playlist> getPlayersUserSubscribedPlaylists(Player player);

    List<Album> getPlayersUserSubscribedAlbums(Player player);

    List<Artist> getPlayersUserSubscribedArtists(Player player);

    LyricInfo getLyricInfo(MusicDetail musicDetail);
}