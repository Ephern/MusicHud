package indi.etern.musichud.utils.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.PostProcessable;
import indi.etern.musichud.server.api.ServerApiMeta;
import indi.etern.musichud.throwable.ApiException;
import indi.etern.musichud.utils.JsonUtil;
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
import java.util.concurrent.Executors;

public class ApiClient {
    public static final HttpClient CLIENT;
    private static final int maxTrial = 5;
    private static final Logger LOGGER = MusicHud.getLogger(ApiClient.class);

    static {
        CLIENT = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(3))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    /*public static boolean checkAvailable() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ServerConfigDefinition.serverApiBaseUrl.get()))
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream is = response.body()) {
                int statusCode = response.statusCode();
                return statusCode >= 200 && statusCode < 400;
            }
        } catch (Exception e) {
            return false;
        }
    }*/

    @SneakyThrows
    public static <T> T post(ServerApiMeta.UrlMeta<T> urlMeta, Object requestBody, String formattedUserCookie) {
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
                            for (String cookieItem : formattedUserCookie.split(";;")) {
                                requestBuilder.header("Cookie", cookieItem);
                            }
                        } else {
                            jsonObject.addProperty("noCookie", true);
                        }
                        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                                        payload.toString(),
                                        StandardCharsets.UTF_8
                                )
                        );
                    } else {
                        throw new IllegalStateException();
                    }
                } else {
                    if (formattedUserCookie != null && !formattedUserCookie.isEmpty()) {
                        for (String cookieItem : formattedUserCookie.split(";;")) {
                            requestBuilder.header("Cookie", cookieItem);
                        }
                    }
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
                    if (codeOnlyResponse.code == 200 || trial == maxTrial || !urlMeta.autoRetry()) {
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
                    LOGGER.error("Please check Api server status | 请检查 Api 服务器状态");
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
    public static <T> T get(ServerApiMeta.UrlMeta<T> urlMeta, String formattedUserCookie) {
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
                    for (String cookieItem : formattedUserCookie.split(";;")) {
                        requestBuilder.header("Cookie", cookieItem);
                    }
                }
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
                    MusicHud.getLogger(ApiClient.class).error("Please check Api server status");
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

    //    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CodeOnlyResponse(int code) {
    }
}
