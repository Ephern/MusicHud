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

public record ClientPushMusicToQueueMessage(long id) implements C2SPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, ClientPushMusicToQueueMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG,
            ClientPushMusicToQueueMessage::id,
            ClientPushMusicToQueueMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    ClientPushMusicToQueueMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        MusicPlayerServerService.getInstance().pushMusicToQueue(message.id, player);
                    })
            );
        }
    }
}
