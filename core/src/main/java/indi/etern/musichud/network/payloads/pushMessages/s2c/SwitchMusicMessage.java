package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;

public record SwitchMusicMessage(MusicDetail musicDetail, MusicDetail nextIdle, String message) implements S2CPayload {
    public static final ByteBufCodec<SwitchMusicMessage> CODEC = ByteBufCodec.composite(
            MusicDetail.CODEC,
            SwitchMusicMessage::musicDetail,
            MusicDetail.CODEC,
            SwitchMusicMessage::nextIdle,
            Codecs.STRING_UTF8,
            SwitchMusicMessage::message,
            SwitchMusicMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        private static ClientConfig clientConfig;
        static {
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                try {
                    clientConfig = ClientConfig.getInstance();
                } catch (UnsupportedOperationException e) {
                    clientConfig = null;
                }
            }
        }

        public void register() {
            NetworkReceiver<SwitchMusicMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> {
                    MusicHud.EXECUTOR.execute(() -> {
                        if (!clientConfig.getEnable()) {
                            return;
                        }
                        String message1 = message.message;
                        if (message1.startsWith(MusicHud.MOD_ID + ".")) {
                            message1 = IClientDistUtil.getInstance().getI18n(message1);
                        }
                        IClientMusicService musicService = IClientMusicService.getInstance();
                        musicService.switchMusic(message.musicDetail, message.nextIdle, null, message1);
                    });
                };
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    SwitchMusicMessage.class, CODEC,
                    receiver
            );
        }
    }
}
