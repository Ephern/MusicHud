package indi.etern.musichud.beans.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.IClientDistUtil;

public enum AutoConnectServerFilterType{
    BLACK_LIST, WHITE_LIST;

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return IClientDistUtil.getInstance().getI18n(MusicHud.MOD_ID + ".config.externalServer.serverFilterType." + this.name());
        } else {
            return this.name();
        }
    }
}
