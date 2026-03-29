package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Queue;

public record SwitchMusicMessage(MusicDetail musicDetail, MusicDetail nextIdle, String message) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchMusicMessage> CODEC = StreamCodec.composite(
            MusicDetail.CODEC,
            SwitchMusicMessage::musicDetail,
            MusicDetail.CODEC,
            SwitchMusicMessage::nextIdle,
            ByteBufCodecs.STRING_UTF8,
            SwitchMusicMessage::message,
            SwitchMusicMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        private static final ClientConfig clientConfig = ClientConfig.getInstance();

        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    SwitchMusicMessage.class, CODEC,
                    (message, player) -> {
                        MusicHud.EXECUTOR.execute(() -> {
                            if (!clientConfig.getEnable()) {
                                return;
                            }
                            MusicService musicService = MusicService.getInstance();
                            String message1 = message.message;
                            if (message1.startsWith(MusicHud.MOD_ID + ".")) {
                                message1 = I18n.get(message1);
                            }
                            musicService.switchMusic(message.musicDetail, message.nextIdle, null, message1);
                            Queue<MusicDetail> musicQueue = musicService.getMusicQueue();
                            if (musicQueue.isEmpty()) {
                                if (!message.nextIdle.equals(MusicDetail.NONE)) {
                                    ImageUtils.downloadAsync(message.nextIdle.getAlbum().getThumbnailPicUrl(200));
                                }
                            } else {
                                ImageUtils.downloadAsync(musicQueue.peek().getAlbum().getThumbnailPicUrl(200));
                            }
                        });
                    }
            );
        }
    }
}
