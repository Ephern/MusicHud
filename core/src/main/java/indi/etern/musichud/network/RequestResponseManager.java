package indi.etern.musichud.network;

import indi.etern.musichud.network.payloads.ApiPayload;
import indi.etern.musichud.network.payloads.IRequestPayload;
import indi.etern.musichud.network.payloads.IResponsePayload;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Centralized request-response correlation for the custom network protocol.
 * Each request carries a UUID, the server echoes it back in the response, and
 * the pending future is completed by {@link #complete(IResponsePayload)}.
 */
public final class RequestResponseManager {
    private static final ConcurrentHashMap<UUID, CompletableFuture<?>> PENDING = new ConcurrentHashMap<>();

    private RequestResponseManager() {
    }

    /**
     * Sends a request and returns a future completed when the matching response arrives.
     * If the request does not yet carry a requestId, one is generated automatically and
     * injected into the payload (for {@link ApiPayload} instances). On timeout the pending
     * entry is removed.
     */
    public static <R extends IResponsePayload> CompletableFuture<R> send(
            IRequestPayload request, Class<R> responseType, Duration timeout) {
        UUID requestId = request.getRequestId();
        if (requestId == null) {
            requestId = UUID.randomUUID();
            if (request instanceof ApiPayload apiPayload) {
                apiPayload.setRequestId(requestId);
            }
        }
        CompletableFuture<R> future = new CompletableFuture<>();
        PENDING.put(requestId, future);
        IClientNetworkService.getInstance().sendToServer(request);
        UUID finalRequestId = requestId;
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        PENDING.remove(finalRequestId, future);
                    }
                });
        return future;
    }

    /**
     * Completes the pending future matching the response's requestId.
     * Called from the S2C receiver of each request-response payload.
     */
    @SuppressWarnings("unchecked")
    public static <R extends IResponsePayload> void complete(R response) {
        UUID requestId = response.getRequestId();
        if (requestId == null) {
            return;
        }
        CompletableFuture<?> future = PENDING.remove(requestId);
        if (future != null) {
            ((CompletableFuture<R>) future).complete(response);
        }
    }
}
