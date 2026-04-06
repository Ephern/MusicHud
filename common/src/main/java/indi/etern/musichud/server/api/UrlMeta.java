package indi.etern.musichud.server.api;

import indi.etern.musichud.interfaces.ServerConfig;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record UrlMeta<T>(
        String url,
        Set<String> requiredParams,
        Set<String> optionalParams,
        boolean noCache,
        boolean noCookie,
        boolean anonymous,
        boolean autoRetry,
        Set<Integer> allowedHttpCodes,
        Class<T> responseType) {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();

    @Override
    public @NotNull String toString() {
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
