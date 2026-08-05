package indi.etern.musichud.network.payloads;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public abstract class ApiResponsePayload implements IResponsePayload, ApiPayload {
    @Getter
    @Setter
    private UUID requestId;

    protected ApiResponsePayload() {
    }

    protected ApiResponsePayload(UUID requestId) {
        this.requestId = requestId;
    }
}
