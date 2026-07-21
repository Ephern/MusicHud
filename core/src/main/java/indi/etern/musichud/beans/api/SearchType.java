package indi.etern.musichud.beans.api;

import indi.etern.musichud.interfaces.IntegerCodeEnum;
import lombok.Getter;

public enum SearchType implements IntegerCodeEnum {
    MUSIC(1),
    ALBUM(10),
    ARTIST(100),
    PLAYLIST(1000),
    USER(1002),
    MV(1004),
    LYRICS(1006),
    RADIO(1009),
    VIDEO(1014),
    COMPREHENSIVE(1018),
    SOUND(2000);
    @Getter
    private final int code;

    SearchType(int code) {
        this.code = code;
    }
}
