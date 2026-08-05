package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.network.payloads.ApiResponsePayload;

/**
 * Server-side handler for a request-response cycle. Implementations must return an explicit
 * {@link ResponseResult}; use {@link ResponseResult#ignore()} to suppress a response.
 * The response type may be any subtype of S, allowing one request type to map to
 * multiple response types (many-to-many).
 */
@FunctionalInterface
public interface RequestHandler<R extends ApiRequestPayload, S extends ApiResponsePayload> {
    ResponseResult<? extends S> handle(R request, indi.etern.musichud.network.IPlayerClient player);
}
