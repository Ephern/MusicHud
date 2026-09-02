package indi.etern.musichud.beans.user;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;

public enum ScrobbleOption {
    NONE, ONLY_SELF, ALL;

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return IClientDistUtil.getInstance().getI18n(MusicHud.MOD_ID + ".config.common.scrobbleOption." + this.name());
        } else {
            return this.name();
        }
    }
}
