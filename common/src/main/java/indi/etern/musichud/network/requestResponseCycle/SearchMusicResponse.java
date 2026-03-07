package indi.etern.musichud.network.requestResponseCycle;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.MusicDetail;
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

public record SearchMusicResponse(int offset, List<MusicDetail> result) implements S2CPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchMusicResponse> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SearchMusicResponse::offset,
            Codecs.ofList(() -> MusicDetail.CODEC),
            SearchMusicResponse::result,
            SearchMusicResponse::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkRegisterUtil.autoRegisterPayload(SearchMusicResponse.class, CODEC,
                    (message, context) -> {
                        MuiModApi.postToUiThread(() -> {
                            SearchView.getInstance().setSearchMusicResult(message.offset,message.result());
                        });
                    }
            );
        }
    }
}
