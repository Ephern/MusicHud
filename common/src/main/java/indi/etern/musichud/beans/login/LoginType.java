package indi.etern.musichud.beans.login;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

public enum LoginType {
    PHONE_PASSWORD, EMAIL_PASSWORD, QR_CODE, DEVICE_CODE, ANONYMOUS, UNLOGGED;
    public final static ByteBufCodec<LoginType> PACKET_CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8,
                    LoginType::name,
                    LoginType::valueOf
            );
}
