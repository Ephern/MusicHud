package indi.etern.musichud.beans.music;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.AliasEnum;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.Getter;

import java.util.List;

public enum Quality implements AliasEnum {
    /**
     * NetEase quality code → documented label:
     * sky=Surround Audio, jymaster=Master, dolby=Dolby Atmos, vivid=Audio Vivid, jyeffect=Spatial Audio,
     * hires=Hi-Res, lossless=SQ, exhigh=HQ, higher=deprecated, standard=Standard
     *
     * <p>{@code higher} and {@code exhigh} resolve to the same tier in practice; the enum keeps
     * both for wire compatibility and {@link #canonical()} normalizes them for the UI/priority list.
     */
    STANDARD(0, VipType.NONE),
    HIGHER(2, VipType.NONE),
    EX_HIGH(2, VipType.NONE),
    LOSSLESS(3, VipType.VIP),
    HIRES(4, VipType.VIP),
    JY_EFFECT(5, VipType.VIP),
    DOLBY(6, VipType.SVIP),
    JY_MASTER(8, VipType.SVIP),
    SKY(9, VipType.SVIP),
    NONE(-1, VipType.NONE),
    VIVID(7, VipType.SVIP);

    public static final ByteBufCodec<Quality> CODEC = Codecs.ofEnum(Quality.class);

    private final int fallbackRank;
    @Getter
    private final VipType mayRequired;

    Quality(int fallbackRank, VipType mayRequired) {
        this.fallbackRank = fallbackRank;
        this.mayRequired = mayRequired;
    }

    /**
     * Normalized quality used by the priority list and UI. {@code HIGHER} is an alias of
     * {@code EX_HIGH}, so it never appears as a separate entry.
     */
    public Quality canonical() {
        return this == HIGHER ? EX_HIGH : this;
    }

    /** Approximate API ladder rank, used only for fallback pruning. */
    public int rank() {
        return fallbackRank;
    }

    /** Qualities selectable in the priority editor, in display order. */
    public static List<Quality> selectable() {
        return List.of(STANDARD, EX_HIGH, LOSSLESS, HIRES, JY_EFFECT, JY_MASTER, SKY, DOLBY, VIVID);
    }

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
