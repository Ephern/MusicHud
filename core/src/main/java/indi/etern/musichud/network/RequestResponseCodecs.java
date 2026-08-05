package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.ApiPayload;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * Codec helpers for the implicit request-response cycle metadata.
 * A UUID (cycle id) is transparently prepended to the wire format of every
 * ApiPayload; the payload class itself never needs to handle it explicitly.
 */
public final class RequestResponseCodecs {
    private RequestResponseCodecs() {
    }

    public static <T extends ApiPayload> ByteBufCodec<T> withCycleId(ByteBufCodec<T> inner) {
        return new ByteBufCodec<>() {
            @Override
            public void encode(ByteBuf byteBuf, T payload) {
                Codecs.UUID.encode(byteBuf, payload.getRequestId());
                inner.encode(byteBuf, payload);
            }

            @Override
            public T decode(ByteBuf byteBuf) {
                UUID requestId = Codecs.UUID.decode(byteBuf);
                T payload = inner.decode(byteBuf);
                payload.setRequestId(requestId);
                return payload;
            }
        };
    }
}
