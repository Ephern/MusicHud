package indi.etern.musichud.beans.music.actions;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

public record MessagedResult<T>(ActionResult actionResult, String message, T extraData) {
    public static <T> ByteBufCodec<MessagedResult<T>> codec(ByteBufCodec<T> tCodec) {
        return ByteBufCodec.composite(
                Codecs.ofEnum(ActionResult.class),
                MessagedResult::actionResult,
                Codecs.STRING_UTF8,
                MessagedResult::message,
                tCodec,
                MessagedResult::extraData,
                MessagedResult::new
        );
    }

    public static <T> MessagedResult<T> success(T t) {
        return new MessagedResult<>(ActionResult.SUCCESS, "", t);
    }

    public static <T> MessagedResult<T> success(String message, T t) {
        return new MessagedResult<>(ActionResult.SUCCESS, message, t);
    }

    public static <T> MessagedResult<T> fail(T t) {
        return new MessagedResult<>(ActionResult.FAIL, "", t);
    }

    public static <T> MessagedResult<T> fail(String message, T t) {
        return new MessagedResult<>(ActionResult.FAIL, message, t);
    }
}
