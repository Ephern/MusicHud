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

import java.util.Queue;

public record RefreshMusicQueueMessage(Queue<MusicDetail> queue) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshMusicQueueMessage> CODEC = StreamCodec.composite(
            Codecs.ofQueue(() -> MusicDetail.CODEC),
            RefreshMusicQueueMessage::queue,
            RefreshMusicQueueMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    RefreshMusicQueueMessage.class, CODEC,
                    (message,context) -> {
                        MusicHud.EXECUTOR.execute(() -> {
                            MusicService.getInstance().refreshQueue(message.queue);
                        });
                    }
            );
        }
    }
}
