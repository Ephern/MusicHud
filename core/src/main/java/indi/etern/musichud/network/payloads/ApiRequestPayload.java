package indi.etern.musichud.network.payloads;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public abstract class ApiRequestPayload implements IRequestPayload, ApiPayload {
    @Getter
    @Setter
    private UUID requestId;

    protected ApiRequestPayload() {
    }

    protected ApiRequestPayload(UUID requestId) {
        this.requestId = requestId;
    }
}
