package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ClientRemoveMusicFromQueueMessage(int index, long id) implements C2SPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientRemoveMusicFromQueueMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientRemoveMusicFromQueueMessage::index,
            ByteBufCodecs.VAR_LONG,
            ClientRemoveMusicFromQueueMessage::id,
            ClientRemoveMusicFromQueueMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ClientRemoveMusicFromQueueMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                            MusicPlayerServerService.getInstance().removeMusicDetailFromQueue(
                                    message.index, message.id, player
                            );
                    })
            );
        }
    }
}
