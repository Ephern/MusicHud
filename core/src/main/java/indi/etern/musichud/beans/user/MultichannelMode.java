package indi.etern.musichud.beans.user;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;

public enum MultichannelMode {
    PREFER_DISCRETE, FORCE_DOWNMIX, DISCRETE_ONLY;

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return IClientDistUtil.getInstance().getI18n(MusicHud.MOD_ID + ".config.common.multichannelMode." + this.name());
        } else {
            return this.name();
        }
    }
}
