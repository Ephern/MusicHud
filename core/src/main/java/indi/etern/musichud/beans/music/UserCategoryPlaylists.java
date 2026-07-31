package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class UserCategoryPlaylists {
    public static final ByteBufCodec<UserCategoryPlaylists> CODEC = ByteBufCodec.composite(
            Playlist.CODEC, UserCategoryPlaylists::getLikeList,
            Codecs.ofCollection(LinkedHashSet::new, () -> Playlist.CODEC), UserCategoryPlaylists::getCreatedPlaylist,
            Codecs.ofCollection(LinkedHashSet::new, () -> Playlist.CODEC), UserCategoryPlaylists::getSubscribedPlaylist,
            UserCategoryPlaylists::new
    );
    public static final UserCategoryPlaylists EMPTY = new UserCategoryPlaylists(Playlist.EMPTY, new LinkedHashSet<>(0), new LinkedHashSet<>(0));
    private Playlist likeList;
    private LinkedHashSet<Playlist> createdPlaylist;
    private LinkedHashSet<Playlist> subscribedPlaylist;
}
