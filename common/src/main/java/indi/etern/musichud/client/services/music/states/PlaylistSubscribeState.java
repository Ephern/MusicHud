package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeResponse;

import java.time.Duration;
import java.util.LinkedHashSet;

public class PlaylistSubscribeState extends SubscribeState<Playlist> {
    private static final MusicService musicService = MusicService.getInstance();

    public PlaylistSubscribeState(long id) {
        super(id, Playlist.class,
                (id1) -> musicService.loadPlaylistDetail(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply((MusicService.UserCollections userCollections) ->
                                userCollections.getUserCategoryPlaylists().getSubscribedPlaylist()
                        ),
                ((playlist, subscribed) -> {
                    musicService.loadUserCollections(false)
                            .thenAccept(userCollections -> {
                                UserCategoryPlaylists categoryPlaylists = userCollections.getUserCategoryPlaylists();
                                if (categoryPlaylists == null) {
                                    return;
                                }
                                LinkedHashSet<Playlist> subscribedPlaylist = categoryPlaylists.getSubscribedPlaylist();
                                if (subscribedPlaylist == null) {
                                    return;
                                }
                                SubscribeAction action;
                                if (subscribed) {
                                    action = SubscribeAction.SUBSCRIBE;
                                    subscribedPlaylist.addFirst(playlist);
                                } else {
                                    action = SubscribeAction.UNSUBSCRIBE;
                                    subscribedPlaylist.remove(playlist);
                                }
                                RequestResponseManager.send(
                                        new SubscribeRequest(id, SubscribableType.PLAYLIST, action),
                                        SubscribeResponse.class, Duration.ofSeconds(5)
                                ).thenAccept(subscribeResponse -> {
                                    //TODO
                                });
                            });
                })
        );
    }
}
