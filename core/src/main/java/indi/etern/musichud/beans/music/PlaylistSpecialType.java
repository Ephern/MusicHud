package indi.etern.musichud.beans.music;

import indi.etern.musichud.interfaces.IntegerCodeEnum;
import lombok.Getter;

public enum PlaylistSpecialType implements IntegerCodeEnum {
    NORMAL(0), LIKE_LIST(5), OFFICIAL(100);

    @Getter
    private final int code;

    PlaylistSpecialType(int code) {
        this.code = code;
    }
}
