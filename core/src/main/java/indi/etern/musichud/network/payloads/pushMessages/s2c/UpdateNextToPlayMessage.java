package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

/**
 * Pushes a replaced idle "next to play" (e.g. after a reroll) without switching the
 * currently playing track. Everyone receives it so the shared next display stays in sync.
 */
public record UpdateNextToPlayMessage(Traceable<MusicDetail> nextToPlay) implements S2CPayload {
    public static final ByteBufCodec<UpdateNextToPlayMessage> CODEC = ByteBufCodec.composite(
            Traceable.codec(MusicDetail.CODEC),
            UpdateNextToPlayMessage::nextToPlay,
            UpdateNextToPlayMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<UpdateNextToPlayMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> MusicHud.EXECUTOR.execute(() ->
                        IClientMusicService.getInstance().updateNextToPlay(message.nextToPlay()));
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    UpdateNextToPlayMessage.class,
                    CODEC,
                    receiver
            );
        }
    }
}
