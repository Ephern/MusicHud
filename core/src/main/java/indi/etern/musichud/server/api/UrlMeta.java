package indi.etern.musichud.server.api;

import indi.etern.musichud.interfaces.ServerConfig;
import lombok.NonNull;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record UrlMeta<T> (
        String url,
        Set<String> requiredParams,
        Set<String> optionalParams,
        boolean noCache,
        boolean noCookie,
        boolean anonymous,
        boolean autoRetry,
        Set<Integer> allowedHttpCodes,
        /*
          Gson deserialization type. For responses without type variables this is just the
          response {@code Class}; for generic responses (e.g. a response wrapping
          {@code List<PlayRecord<T>>}) it must carry the full parameterized
          {@link java.lang.reflect.Type Type} so Gson does not erase the element type into
          {@code LinkedTreeMap}.
         */
        Type deserializationType) {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();

    @Override
    public @NonNull String toString() {
        return serverConfig.getServerApiBaseUrl() + url;
    }

    public URI toURI() {
        String uri = serverConfig.getServerApiBaseUrl() + url;
        List<String> query = new ArrayList<>();
        if (serverConfig.getUseRandomCnIp()) {
            //noinspection SpellCheckingInspection
            query.add("randomCNIP=true");
        }
        if (noCache) {
            query.add("timestamp=" + System.currentTimeMillis());
        }
        if (noCookie) {
            query.add("noCookie=true");
        }
        if (!query.isEmpty()) {
            uri += "?" + String.join("&", query);
        }
        return URI.create(uri);
    }
}
