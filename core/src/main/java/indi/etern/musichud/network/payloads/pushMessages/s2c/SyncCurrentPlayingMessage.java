package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;

import java.time.ZonedDateTime;

public record SyncCurrentPlayingMessage(Traceable<MusicDetail> currentPlaying, Traceable<MusicDetail> nextIdle, ZonedDateTime startTime) implements S2CPayload {
    public static final ByteBufCodec<SyncCurrentPlayingMessage> CODEC = ByteBufCodec.composite(
            Traceable.codec(MusicDetail.CODEC),
            SyncCurrentPlayingMessage::currentPlaying,
            Traceable.codec(MusicDetail.CODEC),
            SyncCurrentPlayingMessage::nextIdle,
            Codecs.ZONED_DATE_TIME,
            SyncCurrentPlayingMessage::startTime,
            SyncCurrentPlayingMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<SyncCurrentPlayingMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, player) -> MusicHud.EXECUTOR.execute(() -> {
                    IClientMusicService musicService = IClientMusicService.getInstance();
                    musicService.switchMusic(message.currentPlaying, message.nextIdle, message.startTime, "");
                });
            }
            INetworkRegister.getInstance().autoRegisterPayload(SyncCurrentPlayingMessage.class, CODEC, receiver);
        }
    }
}
