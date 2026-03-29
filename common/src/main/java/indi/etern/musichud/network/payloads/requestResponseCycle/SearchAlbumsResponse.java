package indi.etern.musichud.network.payloads.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.client.ui.pages.SearchView;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record SearchAlbumsResponse(int offset,List<AlbumInfo> result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchAlbumsResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SearchAlbumsResponse::offset,
            Codecs.ofList(() -> AlbumInfo.CODEC),
            SearchAlbumsResponse::result,
            SearchAlbumsResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchAlbumsResponse.class, CODEC,
                    (message, player) -> {
                        MuiModApi.postToUiThread(() -> {
                            SearchView.getInstance().setSearchAlbumResult(message.offset,message.result());
                        });
                    }
            );
        }
    }
}
