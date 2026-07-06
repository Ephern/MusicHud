package indi.etern.musichud.network.payloads.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;

import java.util.List;

public record SearchArtistsResponse(int offset,List<Artist> result) implements S2CPayload {
    public static final ByteBufCodec<SearchArtistsResponse> CODEC = ByteBufCodec.composite(
            Codecs.INT,
            SearchArtistsResponse::offset,
            Codecs.ofList(() -> Artist.CODEC),
            SearchArtistsResponse::result,
            SearchArtistsResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(SearchArtistsResponse.class, CODEC,
                    (message, player) -> {
                        MuiModApi.postToUiThread(() -> {
                            SearchView.getInstance().setSearchArtistResult(message.offset, message.result());
                        });
                    }
            );
        }
    }
}
