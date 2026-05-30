package indi.etern.musichud.beans.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import net.minecraft.client.resources.language.I18n;

public enum AutoConnectServerFilterType{
    BLACK_LIST, WHITE_LIST;

    @Override
    public String toString() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            return I18n.get(MusicHud.MOD_ID + ".config.externalServer.serverFilterType." + this.name());
        } else {
            return this.name();
        }
    }
}
