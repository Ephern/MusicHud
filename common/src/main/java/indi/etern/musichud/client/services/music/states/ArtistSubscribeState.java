package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.actions.SubscribableType;
import indi.etern.musichud.beans.music.actions.SubscribeAction;
import indi.etern.musichud.client.dto.UserCollections;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.SubscribeResponse;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;

public class ArtistSubscribeState extends SubscribeState<Artist> {
    private static final MusicService musicService = MusicService.getInstance();

    public ArtistSubscribeState(long id) {
        super(id, Artist.class,
                (id1) -> musicService.loadArtist(id1, false),
                () -> musicService.loadUserCollections(false)
                        .thenApply(UserCollections::getSubscribedArtists),
                ((artist, subscribed) -> {
                    musicService.loadUserCollections(false)
                            .thenAccept(userCollections -> {
                                ObservableSequencedSet<Artist> subscribedArtists = userCollections.getSubscribedArtists();
                                ObservableSequencedSet.EditHandle<Artist> editHandle = subscribedArtists.beginEdit();
                                SubscribeAction action;
                                if (subscribed) {
                                    action = SubscribeAction.SUBSCRIBE;
                                    subscribedArtists.addFirst(artist);
                                } else {
                                    action = SubscribeAction.UNSUBSCRIBE;
                                    subscribedArtists.remove(artist);
                                }
                                RequestResponseManager.send(
                                        new SubscribeRequest(id, SubscribableType.ARTIST, action),
                                        SubscribeResponse.class, Duration.ofSeconds(10)
                                ).thenAccept(subscribeResponse -> {
                                    if (subscribeResponse.isSuccess()) {
                                        editHandle.commit();
                                    } else {
                                        editHandle.rollback();
                                        ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".error.subscribe"));
                                    }
                                }).exceptionally(e -> {
                                    editHandle.rollback();
                                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".error.subscribe"));
                                    return null;
                                });
                            });
                })
        );
    }
}
