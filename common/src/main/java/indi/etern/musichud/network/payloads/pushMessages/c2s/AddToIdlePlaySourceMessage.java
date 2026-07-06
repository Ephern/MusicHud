package indi.etern.musichud.network.payloads.pushMessages.c2s;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

public record AddToIdlePlaySourceMessage(IdlePlaySource idlePlaySource) implements C2SPayload {
    public static final ByteBufCodec<AddToIdlePlaySourceMessage> CODEC = ByteBufCodec.composite(
            IdlePlaySource.CODEC,
            AddToIdlePlaySourceMessage::idlePlaySource,
            AddToIdlePlaySourceMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    AddToIdlePlaySourceMessage.class, CODEC,
                    ServerDataPacketVThreadExecutor.execute((message, player) -> {
                        IdlePlaySource idlePlaySource = message.idlePlaySource;
                        MusicPlayerServerService.getInstance().addIdlePlaySource(idlePlaySource.getId(), idlePlaySource.getType(), player);
                    })
            );
        }
    }
}