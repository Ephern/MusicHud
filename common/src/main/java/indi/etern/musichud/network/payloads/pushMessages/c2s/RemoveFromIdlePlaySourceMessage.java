package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record RemoveFromIdlePlaySourceMessage(IdlePlaySource idlePlaySource) implements C2SPayload {
    public static StreamCodec<RegistryFriendlyByteBuf, RemoveFromIdlePlaySourceMessage> CODEC = StreamCodec.composite(
            IdlePlaySource.CODEC,
            RemoveFromIdlePlaySourceMessage::idlePlaySource,
            RemoveFromIdlePlaySourceMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    RemoveFromIdlePlaySourceMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        IdlePlaySource idlePlaySource = message.idlePlaySource;
                        MusicPlayerServerService.getInstance().removeIdlePlaySource(idlePlaySource.getId(), idlePlaySource.getType(), player);
                    })
            );
        }
    }
}