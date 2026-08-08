package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.CollectionUpdateNotifier;

import java.util.UUID;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.albumsCache;
import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.playlistsCache;

/**
 * Notifies clients that a playlist/album has been modified on the server.
 * Carried over the network layer so that both integrated and external server
 * setups deliver the update through the same channel; the client then
 * dispatches it to local UI subscribers via {@link CollectionUpdateNotifier}.
 */
public record CollectionUpdatedMessage(UUID operatorUUID, long collectionId, boolean album) implements S2CPayload {
    public static final ByteBufCodec<CollectionUpdatedMessage> CODEC = ByteBufCodec.composite(
            Codecs.UUID,
            CollectionUpdatedMessage::operatorUUID,
            Codecs.LONG,
            CollectionUpdatedMessage::collectionId,
            Codecs.BOOL,
            CollectionUpdatedMessage::album,
            CollectionUpdatedMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<CollectionUpdatedMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, context) -> MusicHud.EXECUTOR.execute(() -> {
                    if (message.album()) {
                        albumsCache.invalidate(message.collectionId());
                        CollectionUpdateNotifier.notifyAlbumUpdated(message.operatorUUID, message.collectionId());
                    } else {
                        playlistsCache.invalidate(message.collectionId());
                        CollectionUpdateNotifier.notifyPlaylistUpdated(message.operatorUUID, message.collectionId());
                    }
                });
            }
            INetworkRegister.getInstance().autoRegisterPayload(CollectionUpdatedMessage.class, CODEC, receiver);
        }
    }
}
