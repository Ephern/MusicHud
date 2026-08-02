package indi.etern.musichud.network.payloads;

import java.util.UUID;

public interface IRequestPayload extends C2SPayload {
    UUID getRequestId();
}
