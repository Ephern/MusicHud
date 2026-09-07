package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.utils.IClientDistUtil;

public record CommonNotificationMessage(MessagedResult<Void> messagedResult) implements S2CPayload {
    public static final ByteBufCodec<CommonNotificationMessage> CODEC = ByteBufCodec.composite(
            MessagedResult.codec(Codecs.VOID), CommonNotificationMessage::messagedResult,
            CommonNotificationMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            INetworkRegister.getInstance().autoRegisterPayload(
                    CommonNotificationMessage.class, CODEC,
                    ((payload, player) -> {
                        String message = payload.messagedResult.message();
                        if ((MusicHud.MOD_ID + ".text.idleSourceLoadFailed").equals(message)) {
                            // The client names the failed source itself when the snapshot
                            // reconciliation removes it; the keyless generic toast would be
                            // redundant and unactionable.
                            return;
                        }
                        IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
                        if (message.startsWith(MusicHud.MOD_ID + ".")) {
                            message = clientDistUtil.getI18n(message);
                        }
                        clientDistUtil.showToast(message);
                    })
            );
        }
    }
}