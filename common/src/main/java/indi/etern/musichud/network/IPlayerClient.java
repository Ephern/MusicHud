package indi.etern.musichud.network;

import java.util.UUID;

public interface IPlayerClient {
    enum ClientType {
        LOCAL, REMOTE
    }
    UUID getUUID();
    String getName();
    ClientType getClientType();
}
