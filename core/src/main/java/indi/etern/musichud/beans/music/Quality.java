package indi.etern.musichud.beans.music;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.AliasEnum;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;

public enum Quality implements AliasEnum {
    /**
     * NetEase quality code → documented label:
     * sky=Surround Audio, jymaster=Master, dolby=Audio Vivid, jyeffect=Spatial Audio,
     * hires=Hi-Res, lossless=SQ, exhigh=HQ, higher=deprecated, standard=Standard
     */
    STANDARD, HIGHER, EX_HIGH, LOSSLESS, HIRES, JY_EFFECT, DOLBY, JY_MASTER, SKY, NONE;

    public static final ByteBufCodec<Quality> CODEC = Codecs.ofEnum(Quality.class);

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return IClientDistUtil.getInstance().getI18n(MusicHud.MOD_ID + ".config.common.primaryChosenQuality." + this.name());
        } else {
            return this.name();
        }
    }

    @Override
    public String getAlias() {
        return name().toLowerCase().replace("_", "");
    }
}
