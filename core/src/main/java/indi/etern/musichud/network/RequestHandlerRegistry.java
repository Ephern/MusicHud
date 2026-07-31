package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import indi.etern.musichud.utils.ServerDataPacketVThreadExecutor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for server-side request handlers. Every registered request payload class
 * must have a handler registered before receiving, otherwise an IllegalStateException is thrown
 * instead of silently dropping the packet.
 */
public final class RequestHandlerRegistry {
    private static final ConcurrentHashMap<Class<? extends ApiRequestPayload>, RequestHandler<?, ?>> HANDLERS = new ConcurrentHashMap<>();

    private RequestHandlerRegistry() {
    }

    public static <R extends ApiRequestPayload, S extends ApiResponsePayload> void register(
            Class<R> requestType, RequestHandler<R, S> handler) {
        HANDLERS.put(requestType, handler);
    }

    public static <R extends ApiRequestPayload, S extends ApiResponsePayload> RequestHandler<R, S> getHandler(
            Class<R> requestType) {
        @SuppressWarnings("unchecked")
        RequestHandler<R, S> handler = (RequestHandler<R, S>) HANDLERS.get(requestType);
        return handler;
    }

    public static Set<Class<? extends ApiRequestPayload>> getRegisteredTypes() {
        return Set.copyOf(HANDLERS.keySet());
    }

    /**
     * Registers the payload with the network layer and routes incoming requests to the handler.
     * The cycle id is transparently carried between request and response by the codec.
     */
    public static <R extends ApiRequestPayload, S extends ApiResponsePayload> void autoRegisterPayload(
            Class<R> requestType, ByteBufCodec<R> codec, RequestHandler<R, S> handler) {
        register(requestType, handler);
        INetworkRegister.getInstance().autoRegisterPayload(requestType, codec,
                ServerDataPacketVThreadExecutor.execute((request, player) -> {
                    RequestHandler<R, S> requestHandler = getHandler(requestType);
                    if (requestHandler == null) {
                        throw new IllegalStateException("No handler registered for request type: " + requestType.getName());
                    }
                    ResponseResult<? extends S> result = requestHandler.handle(request, player);
                    if (result.isPresent()) {
                        S response = result.get();
                        response.setRequestId(request.getRequestId());
                        IServerNetworkService.getInstance().sendToPlayer(player, response);
                    }
                })
        );
    }
}
