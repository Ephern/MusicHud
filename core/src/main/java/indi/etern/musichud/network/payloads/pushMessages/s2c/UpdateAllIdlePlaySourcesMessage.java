package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

import java.util.List;

public record UpdateAllIdlePlaySourcesMessage(List<IdlePlaySource> idlePlaySources) implements S2CPayload {
    public static final ByteBufCodec<UpdateAllIdlePlaySourcesMessage> CODEC = ByteBufCodec.composite(
            Codecs.ofList(() -> IdlePlaySource.CODEC),
            UpdateAllIdlePlaySourcesMessage::idlePlaySources,
            UpdateAllIdlePlaySourcesMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<UpdateAllIdlePlaySourcesMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (playSourcesMessage, packetContext) -> {
                    IIdlePlaySourceState idlePlaySourceState = IClientMusicService.getInstance().getIdlePlaySourceState();
                    idlePlaySourceState.external().updateAll(playSourcesMessage.idlePlaySources);
                    idlePlaySourceState.local().removeMissingFromServer(playSourcesMessage.idlePlaySources);
                };
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    UpdateAllIdlePlaySourcesMessage.class,
                    CODEC,
                    receiver
            );
        }
    }
}
