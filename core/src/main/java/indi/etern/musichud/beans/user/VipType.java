package indi.etern.musichud.beans.user;

import indi.etern.musichud.interfaces.IntegerCodeEnum;
import lombok.Getter;

public enum VipType implements IntegerCodeEnum {
    NONE(-1), VIP(100), SVIP(300);

    @Getter
    private final int code;
    VipType(int code) {
        this.code = code;
    }
}
