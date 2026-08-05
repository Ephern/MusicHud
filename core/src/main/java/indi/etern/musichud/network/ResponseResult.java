package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.ApiResponsePayload;

/**
 * Explicit result of a request handler. {@link #ignore()} means "no response should be sent",
 * as opposed to a null response which is ambiguous.
 */
public final class ResponseResult<S extends ApiResponsePayload> {
    private final S response;

    private ResponseResult(S response) {
        this.response = response;
    }

    public static <S extends ApiResponsePayload> ResponseResult<S> of(S response) {
        return new ResponseResult<>(response);
    }

    public static <S extends ApiResponsePayload> ResponseResult<S> ignore() {
        return new ResponseResult<>(null);
    }

    public boolean isPresent() {
        return response != null;
    }

    public S get() {
        if (response == null) {
            throw new IllegalStateException("Response is absent");
        }
        return response;
    }
}
