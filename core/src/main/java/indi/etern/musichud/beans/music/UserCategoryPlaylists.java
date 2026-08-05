package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class UserCategoryPlaylists {
    public static final ByteBufCodec<UserCategoryPlaylists> CODEC = ByteBufCodec.composite(
            Playlist.CODEC, UserCategoryPlaylists::getLikeList,
            Codecs.ofCollection(ObservableSequencedSet::new, () -> Playlist.CODEC), UserCategoryPlaylists::getCreatedPlaylist,
            Codecs.ofCollection(ObservableSequencedSet::new, () -> Playlist.CODEC), UserCategoryPlaylists::getSubscribedPlaylist,
            UserCategoryPlaylists::new
    );
    public static final UserCategoryPlaylists EMPTY = new UserCategoryPlaylists(Playlist.EMPTY, new ObservableSequencedSet<>(0), new ObservableSequencedSet<>(0));
    private Playlist likeList;
    private ObservableSequencedSet<Playlist> createdPlaylist;
    private ObservableSequencedSet<Playlist> subscribedPlaylist;
}
