package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record UpdateAllIdlePlaySourcesMessage(List<Playlist> playlistSources,
                                              List<AlbumInfo> albumSources) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAllIdlePlaySourcesMessage> CODEC = StreamCodec.composite(
            Codecs.ofList(() -> Playlist.CODEC),
            UpdateAllIdlePlaySourcesMessage::playlistSources,
            Codecs.ofList(() -> AlbumInfo.CODEC),
            UpdateAllIdlePlaySourcesMessage::albumSources,
            UpdateAllIdlePlaySourcesMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<UpdateAllIdlePlaySourcesMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (playSourcesMessage, packetContext) ->
                        MusicService.getInstance().updateAllIdlePlaySources(
                                playSourcesMessage.playlistSources,
                                playSourcesMessage.albumSources
                        );
            }
            INetworkRegister.getInstance().autoRegisterPayload(
                    UpdateAllIdlePlaySourcesMessage.class,
                    CODEC,
                    receiver
            );
        }
    }
}
