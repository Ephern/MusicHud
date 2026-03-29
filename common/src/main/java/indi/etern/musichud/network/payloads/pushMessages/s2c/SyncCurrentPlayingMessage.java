package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.time.ZonedDateTime;

public record SyncCurrentPlayingMessage(MusicDetail currentPlaying, MusicDetail nextIdle, ZonedDateTime startTime) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCurrentPlayingMessage> CODEC = StreamCodec.composite(
            MusicDetail.CODEC,
            SyncCurrentPlayingMessage::currentPlaying,
            MusicDetail.CODEC,
            SyncCurrentPlayingMessage::nextIdle,
            Codecs.ZONED_DATE_TIME,
            SyncCurrentPlayingMessage::startTime,
            SyncCurrentPlayingMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SyncCurrentPlayingMessage.class, CODEC,
                    (message, player) -> {
                        MusicHud.EXECUTOR.execute(() -> {
                            MusicService musicService = MusicService.getInstance();
                            musicService.switchMusic(message.currentPlaying, message.nextIdle, message.startTime, "");
                        });
                    }
            );
        }
    }
}