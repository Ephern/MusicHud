package indi.etern.musichud.beans.music;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.AliasEnum;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.ClientDistUtil;

public enum Quality implements AliasEnum {
    /**
     * 传参         文档        实际
     * sky      => 沉浸环绕声   沉浸环绕声 Surround Audio
     * jymaster => 超清母带     超清母带 Master
     * dolby    => 杜比全景声   臻音全景声 Audio Vivid
     * jyeffect => 高清环绕声   高清臻音 Spatial Audio
     * hires    => Hi-Res     高解析度无损 Hi-Res
     * lossless => 无损        无损 SQ
     * exhigh   => 极高        极高 HQ
     * higher   => 较高(弃用？) 极高 HQ
     * standard => 标准        标准
     *
     */
    STANDARD, HIGHER, EX_HIGH, LOSSLESS, HIRES, JY_EFFECT, SKY, DOLBY, JY_MASTER, NONE;

    public static final ByteBufCodec<Quality> CODEC = Codecs.ofEnum(Quality.class);

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return ClientDistUtil.getI18n(MusicHud.MOD_ID + ".config.common.primaryChosenQuality." + this.name());
        } else {
            return this.name();
        }
    }

    @Override
    public String getAlias() {
        return name().toLowerCase().replace("_", "");
    }
}
