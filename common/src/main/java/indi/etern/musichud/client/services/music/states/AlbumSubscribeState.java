package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeResponse;

import java.time.Duration;
import java.util.LinkedHashSet;

public class AlbumSubscribeState extends SubscribeState<Album> {
    private static final MusicService musicService = MusicService.getInstance();

    public AlbumSubscribeState(long id) {
        super(id, Album.class,
                (id1) -> musicService.loadAlbumDetail(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply(MusicService.UserCollections::getSubscribedAlbums),
                ((album, subscribed) -> {
                    musicService.loadUserCollections(false)
                            .thenAccept(userCollections -> {
                                LinkedHashSet<Album> subscribedAlbums = userCollections.getSubscribedAlbums();
                                SubscribeAction action;
                                if (subscribed) {
                                    action = SubscribeAction.SUBSCRIBE;
                                    subscribedAlbums.addFirst(album);
                                } else {
                                    action = SubscribeAction.UNSUBSCRIBE;
                                    subscribedAlbums.remove(album);
                                }
                                RequestResponseManager.send(
                                        new SubscribeRequest(id, SubscribableType.ALBUM, action),
                                        SubscribeResponse.class, Duration.ofSeconds(5)
                                ).thenAccept(subscribeResponse -> {
                                    //TODO
                                });
                            });
                })
        );
    }
}
