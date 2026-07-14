package indi.etern.musichud.server.api;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.utils.JsonUtil;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for fetching api-enhanced binary releases from GitHub.
 * <p>
 * Rate-limit strategy:
 * <ul>
 *   <li>{@link #listReleaseSummaries()} — parses the Atom feed ({@code releases.atom}),
 *       which is <b>not</b> subject to REST API rate limits. Zero API quota consumed.</li>
 *   <li>{@link #downloadLatestForCurrentPlatform(Path, Consumer)} — uses GitHub's
 *       direct-download URL ({@code releases/latest/download/&lt;asset&gt;}), which
 *       issues a 302 redirect to the CDN. Zero API quota consumed.</li>
 *   <li>{@link #downloadAssetByTag(String, Path, Consumer)} — uses the direct-download
 *       URL ({@code releases/download/&lt;tag&gt;/&lt;asset&gt;}). Zero API quota consumed.</li>
 *   <li>{@link #listReleases()} — the <b>only</b> method that hits the REST API
 *       ({@code api.github.com/repos/.../releases}). Use sparingly or set
 *       {@code GITHUB_TOKEN} with {@link #setGitHubToken(String)} to raise the limit.</li>
 * </ul>
 * Not yet wired into the main flow — call methods directly as needed.
 */
public class ApiServerFetcher {
    private static final Logger LOG = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");

    public static final String REPO_OWNER = "MOPELotus";
    public static final String REPO_NAME = "api-enhanced";
    public static final String BASE = "https://github.com/" + REPO_OWNER + "/" + REPO_NAME;
    public static final String API_RELEASES = "https://api.github.com/repos/" + REPO_OWNER + "/" + REPO_NAME + "/releases";
    public static final String LATEST_RELEASE_URL = BASE + "/releases/latest";
    private static final String ATOM_URL = BASE + "/releases.atom";
    private static final String LATEST_DOWNLOAD_URL = LATEST_RELEASE_URL + "/download/";
    private static final String TAG_DOWNLOAD_URL = BASE + "/releases/download/";

    // ---- Atom feed regex patterns ----
    private static final Pattern ENTRY_PATTERN = Pattern.compile("<entry>\\s*(.*?)\\s*</entry>", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>\\s*(.*?)\\s*</title>", Pattern.DOTALL);
    private static final Pattern LINK_HREF_PATTERN = Pattern.compile("<link[^>]+href=\"([^\"]+)\"");
    private static final Pattern UPDATED_PATTERN = Pattern.compile("<updated>\\s*(.*?)\\s*</updated>", Pattern.DOTALL);

    @Getter
    private static volatile String gitHubToken;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(MusicHud.EXECUTOR)
            .build();

    // ---- data models ----

    /** Lightweight release summary from Atom feed — zero API quota consumed. */
    public record ReleaseSummary(String tag, String title, String htmlUrl, String publishedAt) {}

    /** Full release info from REST API (includes asset list). Consumes API quota. */
    @Getter
    public static class Release {
        @SerializedName("tag_name")
        private String tagName;
        private String name;
        @SerializedName("published_at")
        private String publishedAt;
        @SerializedName("html_url")
        private String htmlUrl;
        private List<Asset> assets;
    }

    /** A downloadable asset within a release (from REST API). */
    @Getter
    public static class Asset {
        private String name;
        @SerializedName("browser_download_url")
        private String browserDownloadUrl;
        private long size;
    }

    /** Platform constants matching api-enhanced asset naming. */
    public enum Platform {
        WINDOWS_X64("ncm-api-windows-x64.exe"),
        LINUX_X64("ncm-api-linux-x64"),
        MACOS_X64("ncm-api-macos-x64");

        @Getter
        private final String assetName;

        Platform(String assetName) {
            this.assetName = assetName;
        }

        /** Detect the current running platform. */
        public static Platform detect() {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                return WINDOWS_X64;
            } else if (os.contains("mac")) {
                return MACOS_X64;
            } else {
                return LINUX_X64;
            }
        }
    }

    // ---- public API ----

    /**
     * (Optional) Set a GitHub personal access token to raise the REST API
     * rate limit from 60 to 5,000 requests/hour. Only affects {@link #listReleases()}.
     */
    public static void setGitHubToken(String token) {
        gitHubToken = token != null && !token.isBlank() ? token.trim() : null;
    }

    // -- zero-quota methods (prefer these) --

    /**
     * List recent release titles via the Atom feed.
     * <b>Zero REST API quota consumed.</b>
     */
    public static CompletableFuture<List<ReleaseSummary>> listReleaseSummaries() {
        return CompletableFuture.supplyAsync(() -> {
            List<ReleaseSummary> result = new ArrayList<>();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ATOM_URL))
                        .header("User-Agent", "MusicHUD")
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOG.error("Atom feed returned status {}", response.statusCode());
                    return result;
                }
                String body = response.body();
                Matcher entryMatcher = ENTRY_PATTERN.matcher(body);
                while (entryMatcher.find()) {
                    String entry = entryMatcher.group(1);
                    String title = extractFirst(TITLE_PATTERN, entry);
                    String htmlUrl = extractFirst(LINK_HREF_PATTERN, entry);
                    String updated = extractFirst(UPDATED_PATTERN, entry);
                    if (title == null || title.isBlank()) continue;
                    // strip leading repo-name prefix if present (e.g. "api-enhanced: v1.0")
                    String tag = title;
                    if (title.contains(": ")) {
                        tag = title.substring(title.indexOf(": ") + 2).trim();
                    }
                    result.add(new ReleaseSummary(tag, title, htmlUrl, updated));
                }
            } catch (Exception e) {
                LOG.error("Failed to fetch Atom feed", e);
            }
            return result;
        }, MusicHud.EXECUTOR);
    }

    /**
     * Download the latest binary for the current platform.
     * <b>Zero REST API quota consumed</b> — uses GitHub's direct-download redirect URL.
     *
     * @param targetDir directory to save into (the asset's original filename is used)
     * @param progress  optional progress callback (0.0 .. 1.0), may be null
     */
    public static CompletableFuture<Void> downloadLatestForCurrentPlatform(Path targetDir, Consumer<Double> progress) {
        Platform platform = Platform.detect();
        String url = LATEST_DOWNLOAD_URL + platform.getAssetName();
        Path target = targetDir.resolve(platform.getAssetName());
        return downloadFromUrl(url, target, progress);
    }

    /**
     * Download the latest binary for the current platform with detailed byte progress.
     * <b>Zero REST API quota consumed.</b>
     *
     * @param targetDir directory to save into
     * @param progress  optional detailed progress callback (downloaded bytes, total bytes), may be null
     */
    public static CompletableFuture<Void> downloadLatestForCurrentPlatform(Path targetDir, BiConsumer<Long, Long> progress) {
        Platform platform = Platform.detect();
        String url = LATEST_DOWNLOAD_URL + platform.getAssetName();
        Path target = targetDir.resolve(platform.getAssetName());
        return downloadFromUrl(url, target, progress);
    }

    /**
     * Download the latest binary for the current platform to a custom filename.
     * <b>Zero REST API quota consumed.</b>
     *
     * @param targetDir directory to save into
     * @param targetName custom filename to use
     * @param progress optional detailed progress callback (downloaded bytes, total bytes), may be null
     */
    public static CompletableFuture<Void> downloadLatestForCurrentPlatform(Path targetDir, String targetName, BiConsumer<Long, Long> progress) {
        Platform platform = Platform.detect();
        String url = LATEST_DOWNLOAD_URL + platform.getAssetName();
        Path target = targetDir.resolve(targetName);
        return downloadFromUrl(url, target, progress);
    }

    /**
     * Download the binary for a specific release tag on the current platform.
     * <b>Zero REST API quota consumed.</b>
     *
     * @param tag       release tag (e.g. "20260713-201705"), or "latest" to use the latest release
     * @param targetDir directory to save into
     * @param progress  optional progress callback, may be null
     */
    public static CompletableFuture<Void> downloadAssetByTag(String tag, Path targetDir, Consumer<Double> progress) {
        Platform platform = Platform.detect();
        String url;
        if ("latest".equals(tag)) {
            url = LATEST_DOWNLOAD_URL + platform.getAssetName();
        } else {
            url = TAG_DOWNLOAD_URL + tag + "/" + platform.getAssetName();
        }
        Path target = targetDir.resolve(platform.getAssetName());
        return downloadFromUrl(url, target, progress);
    }

    // -- quota-consuming method (REST API) --

    /**
     * Fetch full releases from the REST API (includes asset details).
     * Consumes GitHub API quota; set a token via {@link #setGitHubToken(String)} to raise the limit.
     * Prefer {@link #listReleaseSummaries()} for simple title listing.
     */
    public static CompletableFuture<List<Release>> listReleases() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(API_RELEASES + "?per_page=10"))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "MusicHUD");
                if (gitHubToken != null) {
                    builder.header("Authorization", "Bearer " + gitHubToken);
                }
                HttpRequest request = builder.GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOG.error("GitHub API returned status {}", response.statusCode());
                    return List.<Release>of();
                }
                return JsonUtil.gson.fromJson(response.body(), new TypeToken<List<Release>>() {}.getType());
            } catch (Exception e) {
                LOG.error("Failed to fetch releases", e);
                return List.<Release>of();
            }
        }, MusicHud.EXECUTOR);
    }

    /**
     * Download an asset object to {@code targetPath}.
     *
     * @param asset    the asset to download
     * @param target   destination file path (will be overwritten if exists)
     * @param progress optional progress callback (0.0 .. 1.0), may be null
     */
    public static CompletableFuture<Void> downloadAsset(Asset asset, Path target, Consumer<Double> progress) {
        return downloadFromUrl(asset.getBrowserDownloadUrl(), target, progress);
    }

    // ---- internal ----

    private static CompletableFuture<Void> downloadFromUrl(String url, Path target, Consumer<Double> progress) {
        return downloadFromUrl(url, target, progress != null ? (downloaded, total) -> {
            progress.accept(total > 0 ? (double) downloaded / total : 0);
        } : null);
    }

    private static CompletableFuture<Void> downloadFromUrl(String url, Path target, BiConsumer<Long, Long> progress) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(target.getParent());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "MusicHUD")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new IOException("Download failed with status " + response.statusCode() + " for " + url);
                }
                long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(target,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buf = new byte[8192];
                    long downloaded = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        downloaded += n;
                        if (progress != null && total > 0) {
                            progress.accept(downloaded, total);
                        }
                    }
                }
                // make executable on non-Windows
                if (!Platform.detect().getAssetName().endsWith(".exe")) {
                    target.toFile().setExecutable(true);
                }
                LOG.info("Downloaded {} -> {}", target.getFileName(), target);
            } catch (Exception e) {
                LOG.error("Failed to download from {}", url, e);
                throw new RuntimeException(e);
            }
        }, MusicHud.EXECUTOR);
    }

    private static String extractFirst(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }
}
