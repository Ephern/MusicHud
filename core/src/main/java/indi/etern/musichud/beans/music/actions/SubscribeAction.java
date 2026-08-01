package indi.etern.musichud.beans.music.actions;

import indi.etern.musichud.interfaces.IntegerCodeEnum;
import lombok.Getter;

public enum SubscribeAction implements IntegerCodeEnum {
    SUBSCRIBE(1), UNSUBSCRIBE(0);

    @Getter
    private final int code;

    SubscribeAction(int code) {
        this.code = code;
    }
}
