package indi.etern.musichud.network.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.pages.SearchView;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.NetworkRegisterUtil;
import indi.etern.musichud.network.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record SearchArtistsResponse(int offset,List<Artist> result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchArtistsResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SearchArtistsResponse::offset,
            Codecs.ofList(() -> Artist.CODEC),
            SearchArtistsResponse::result,
            SearchArtistsResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(SearchArtistsResponse.class, CODEC,
                    (message, context) -> {
                        MuiModApi.postToUiThread(() -> {
                            SearchView.getInstance().setSearchArtistResult(message.offset, message.result());
                        });
                    }
            );
        }
    }
}
