package indi.etern.musichud.utils.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.PostProcessable;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.UrlMeta;
import indi.etern.musichud.server.api.impl.ncm.ApiServerEndpointsMeta;
import indi.etern.musichud.throwable.ApiException;
import indi.etern.musichud.utils.IClientDistUtil;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.Logger;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ApiClient {
    public static final HttpClient CLIENT;
    private static final int maxTrial = 5;
    @SuppressWarnings("SpellCheckingInspection")
    private static final Set<String> COOKIE_ATTRIBUTE_NAMES = Set.of(
            "max-age", "expires", "path", "domain", "secure", "httponly", "samesite"
    );
    private static final Logger LOGGER = MusicHud.getLogger(ApiClient.class);
    @Getter
    private static String version = "unknown";

    static {
        CLIENT = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(3))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    public static boolean checkAvailable() {
        try {
            var response = post(ApiServerEndpointsMeta.API_SERVER_VERSION, null, null, false);
            version = response.data.version;
            return true;
        } catch (Exception e) {
            try {
                String response = get(ApiServerEndpointsMeta.BASE, null, false);
                if (response.contains("NCM API Rust Server")) {// especially adapt to ncm-api-rs due to /inner/version won't work on it
                    version = "ncm-rs-api";
                    return true;
                } else if (response.contains("<title>网易云音乐 API Enhanced</title>")) {// original NodeJS api fallback
                    version = "ncm-js-api-unknown";
                    return true;
                }
            } catch (Exception ignored) {
            }
            version = "unknown";
            return false;
        }
    }

    @SneakyThrows
    public static <T> T post(UrlMeta<T> urlMeta, Object requestBody, String formattedUserCookie, boolean allowAlert) {
        T t = null;
        int trial = 0;
        do {
            try {
                trial++;
                if (trial != 1) {
                    //noinspection BusyWait
                    Thread.sleep(500);
                }
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(urlMeta.toURI())
                        .setHeader("Content-Type", "application/json");
                if (requestBody != null) {
                    JsonElement payload = requestBody instanceof JsonElement element ? element : JsonUtil.gson.toJsonTree(requestBody);
                    if (payload instanceof JsonObject jsonObject) {
                        if (formattedUserCookie != null && !formattedUserCookie.isEmpty()) {
                            String cleanCookie = cleanCookie(formattedUserCookie);
                            if (!cleanCookie.isEmpty()) {
                                requestBuilder.header("Cookie", cleanCookie);
                            }
                        } else {
                            jsonObject.addProperty("noCookie", true);
                        }
                        String payloadString = payload.toString();
                        LOGGER.debug("POST \"{}\" with payload: \"{}\"", urlMeta.toURI().toString(), payloadString);
                        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                                        payloadString,
                                        StandardCharsets.UTF_8
                                )
                        );
                    } else {
                        throw new IllegalStateException();
                    }
                } else {
                    if (formattedUserCookie != null && !formattedUserCookie.isEmpty()) {
                        String cleanCookie = cleanCookie(formattedUserCookie);
                        if (!cleanCookie.isEmpty()) {
                            requestBuilder.header("Cookie", cleanCookie);
                        }
                    }
                    LOGGER.debug("POST \"{}\" without payload", urlMeta.toURI().toString());
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                }
                HttpRequest request = requestBuilder
                        .build();
                Class<?> currentlyParsing = null;
                String responseBody = null;
                try {
                    HttpResponse<?> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    responseBody = response.body().toString();
                    currentlyParsing = CodeOnlyResponse.class;
                    var codeOnlyResponse = JsonUtil.gson.fromJson(responseBody, CodeOnlyResponse.class);
                    Set<Integer> allowedHttpCodes = urlMeta.allowedHttpCodes();
                    if (allowedHttpCodes == null || allowedHttpCodes.contains(codeOnlyResponse.code) || trial == maxTrial || !urlMeta.autoRetry()) {
                        if (urlMeta.responseType().equals(String.class)) {
                            //noinspection unchecked
                            t = (T) responseBody;
                        } else {
                            currentlyParsing = urlMeta.responseType();
                            t = JsonUtil.gson.fromJson(responseBody, urlMeta.responseType());
                        }
                    }
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Failed to parse response as:{}, original response:{}", currentlyParsing, responseBody, e);
                    throw e;
                } catch (ConnectException e) {
                    if (allowAlert) {
                        LOGGER.error("Please check Api server status | 请检查 Api 服务器状态");
                        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                            IClientDistUtil.getInstance().getI18n(MusicHud.MOD_ID + ".error.apiServer");
                        }
                    }
                    throw e;
                }
            } catch (ConnectException e) {
                throw new ApiException(e);
            }
        } while (t == null && trial < maxTrial && urlMeta.autoRetry());
        if (t instanceof PostProcessable postProcessable) {
            postProcessable.postProcess();
        }
        return t;
    }

    @SneakyThrows
    public static <T> T get(UrlMeta<T> urlMeta, String formattedUserCookie, boolean allowAlert) {
        T t = null;
        int trial = 0;
        do {
            try {
                trial++;
                if (trial != 1) {
                    //noinspection BusyWait
                    Thread.sleep(500);
                }
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(urlMeta.toURI());
                if (formattedUserCookie != null && !formattedUserCookie.isEmpty()) {
                    String cleanCookie = cleanCookie(formattedUserCookie);
                    if (!cleanCookie.isEmpty()) {
                        requestBuilder.header("Cookie", cleanCookie);
                    }
                }
                LOGGER.debug("GET \"{}\" without payload", urlMeta.toURI().toString());
                HttpRequest request = requestBuilder
                        .GET()
                        .build();
                try {
                    HttpResponse<?> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    String string = response.body().toString();
                    var codeOnlyResponse = JsonUtil.gson.fromJson(string, CodeOnlyResponse.class);
                    if (codeOnlyResponse.code == 200 || trial == maxTrial || !urlMeta.autoRetry()) {
                        if (urlMeta.responseType().equals(String.class)) {
                            //noinspection unchecked
                            t = (T) string;
                        } else {
                            t = JsonUtil.gson.fromJson(string, urlMeta.responseType());
                        }
                    }
                } catch (ConnectException e) {
                    if (allowAlert) {
                        LOGGER.error("Please check Api server status | 请检查 Api 服务器状态");
                        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                            IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
                            clientDistUtil.showToast(clientDistUtil.getI18n(MusicHud.MOD_ID + ".error.apiServer"));
                        }
                    }
                    throw e;
                }
            } catch (ConnectException e) {
                throw new ApiException(e);
            }
        } while (t == null && trial < maxTrial && urlMeta.autoRetry());
        if (t instanceof PostProcessable postProcessable) {
            postProcessable.postProcess();
        }
        return t;
    }

    public static boolean checkUrlAvailable(String urlString, int timeoutMillis) {
        HttpURLConnection connection = null;
        try {
            URL url = new URI(urlString).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(false); // 不自动重定向

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String cleanCookie(String rawCookie) {
        if (rawCookie == null || rawCookie.isEmpty()) {
            return "";
        }
        return Arrays.stream(rawCookie.split(";+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    int eqIdx = s.indexOf('=');
                    if (eqIdx <= 0) {
                        return false;
                    }
                    String name = s.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT);
                    return !COOKIE_ATTRIBUTE_NAMES.contains(name);
                })
                .collect(Collectors.joining("; "));
    }

    public record ApiVersionResponse(ApiVersionResponseData data) {
    }

    private record ApiVersionResponseData(String version) {
    }

    private record CodeOnlyResponse(int code) {
    }
}
