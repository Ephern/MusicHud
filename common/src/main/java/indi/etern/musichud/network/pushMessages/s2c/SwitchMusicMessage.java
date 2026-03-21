package indi.etern.musichud.network.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SwitchMusicMessage(MusicDetail musicDetail, MusicDetail next, String message) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchMusicMessage> CODEC = StreamCodec.composite(
            MusicDetail.CODEC,
            SwitchMusicMessage::musicDetail,
            MusicDetail.CODEC,
            SwitchMusicMessage::next,
            ByteBufCodecs.STRING_UTF8,
            SwitchMusicMessage::message,
            SwitchMusicMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(
                    SwitchMusicMessage.class, CODEC,
                    (message, context) -> {
                        MusicHud.EXECUTOR.execute(() -> {
                            MusicService musicService = MusicService.getInstance();
                            String message1 = message.message;
                            if (message1.startsWith(MusicHud.MOD_ID + ".")) {
                                message1 = I18n.get(message1);
                            }
                            musicService.switchMusic(message.musicDetail, null, message1);
                            if (!message.next.equals(MusicDetail.NONE)) {
                                if (ClientConfigDefinition.enable.get()) {
                                    ImageUtils.downloadAsync(message.next.getAlbum().getThumbnailPicUrl(200));
                                }
                            }
                        });
                    }
            );
        }
    }
}
