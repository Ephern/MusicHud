package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeResponse;

import java.time.Duration;
import java.util.LinkedHashSet;

public class ArtistSubscribeState extends SubscribeState<Artist> {
    private static final MusicService musicService = MusicService.getInstance();

    public ArtistSubscribeState(long id) {
        super(id, Artist.class,
                (id1) -> musicService.loadArtist(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply(MusicService.UserCollections::getSubscribedArtists),
                ((artist, subscribed) -> {
                    musicService.loadUserCollections(false)
                            .thenAccept(userCollections -> {
                                LinkedHashSet<Artist> subscribedAlbums = userCollections.getSubscribedArtists();
                                SubscribeAction action;
                                if (subscribed) {
                                    action = SubscribeAction.SUBSCRIBE;
                                    subscribedAlbums.addFirst(artist);
                                } else {
                                    action = SubscribeAction.UNSUBSCRIBE;
                                    subscribedAlbums.remove(artist);
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
