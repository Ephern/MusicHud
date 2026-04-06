package indi.etern.musichud.network.payloads.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record SearchPlaylistsResponse(int offset,List<Playlist> result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchPlaylistsResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SearchPlaylistsResponse::offset,
            Codecs.ofList(() -> Playlist.CODEC),
            SearchPlaylistsResponse::result,
            SearchPlaylistsResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchPlaylistsResponse.class, CODEC,
                    (message, player) -> {
                        MuiModApi.postToUiThread(() -> {
                            SearchView.getInstance().setSearchPlaylistResult(message.offset, message.result());
                        });
                    }
            );
        }
    }
}
