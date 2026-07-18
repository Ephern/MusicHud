package indi.etern.musichud.server.api;

import com.google.gson.reflect.TypeToken;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.utils.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiBinaryUpdateService {

    private static final ApiBinaryUpdateService INSTANCE = new ApiBinaryUpdateService();
    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+\\.\\d+\\.\\d+)");

    public static ApiBinaryUpdateService getInstance() {
        return INSTANCE;
    }

    private ApiBinaryUpdateService() {}

    public CompletableFuture<ApiServerFetcher.ReleaseSummary> fetchLatestRelease() {
        return ApiServerFetcher.listReleaseSummaries().thenCompose(summaries -> {
            if (summaries != null && !summaries.isEmpty()) {
                return CompletableFuture.completedFuture(summaries.getFirst());
            }
            return ApiServerFetcher.listReleases().thenApply(releases -> {
                if (releases != null && !releases.isEmpty()) {
                    ApiServerFetcher.Release r = releases.getFirst();
                    return new ApiServerFetcher.ReleaseSummary(
                            r.getTagName(), r.getName(), r.getHtmlUrl(), r.getPublishedAt());
                }
                return null;
            });
        });
    }

    public CompletableFuture<Path> downloadToTemp(Path targetDir, String releaseTag, BiConsumer<Long, Long> progress) {
        return downloadToTemp(targetDir, releaseTag, ApiServerFetcher.DownloadProxy.DIRECT, progress);
    }

    public CompletableFuture<Path> downloadToTemp(Path targetDir, String releaseTag, ApiServerFetcher.DownloadProxy proxy, BiConsumer<Long, Long> progress) {
        return downloadToTemp(targetDir, releaseTag, proxy, progress, new AtomicBoolean(false));
    }

    public CompletableFuture<Path> downloadToTemp(Path targetDir, String releaseTag, ApiServerFetcher.DownloadProxy proxy, BiConsumer<Long, Long> progress, AtomicBoolean cancelled) {
        String tempFileName = ApiServerFetcher.Platform.detect().getAssetName() + "." + releaseTag + ".temp";
        Path tempFile = targetDir.resolve(tempFileName);
        return ApiServerFetcher.downloadLatestForCurrentPlatform(targetDir, tempFileName, proxy, progress, cancelled)
                .thenApply(v -> tempFile);
    }

    public Path resolveFinalPath(Path tempFile, String releaseTag) {
        if (tempFile == null || !Files.exists(tempFile)) return null;
        String baseName = ApiServerFetcher.Platform.detect().getAssetName();
        Path targetDir = tempFile.getParent();
        Path namedFile = targetDir.resolve(baseName);

        // proactively stop server if target file is the running executable
        String currentPath = ServerConfig.getInstance().getServerApiBinaryExecutablePath();
        if (namedFile.toAbsolutePath().normalize().toString()
                .equals(Paths.get(currentPath).toAbsolutePath().normalize().toString())
                && ApiServerManager.getInstance().getBinaryApiServerStatus() == ApiServerManager.BinaryApiServerStatus.RUNNING) {
            ApiServerManager.getInstance().stopApiServer();
            for (int retry = 0; retry < 20; retry++) {
                try {
                    Thread.sleep(150);
                    Files.move(tempFile, namedFile, StandardCopyOption.REPLACE_EXISTING);
                    return namedFile;
                } catch (Exception ignored) {}
            }
        }

        try {
            Files.move(tempFile, namedFile, StandardCopyOption.REPLACE_EXISTING);
            return namedFile;
        } catch (IOException ignored) {}

        // fallback: append .n before extension
        String bn = baseName;
        String ext = "";
        int dotIdx = bn.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = bn.substring(dotIdx);
            bn = bn.substring(0, dotIdx);
        }
        for (int n = 1; n < 100; n++) {
            Path numberedFile = targetDir.resolve(bn + "." + n + ext);
            try {
                Files.move(tempFile, numberedFile);
                return numberedFile;
            } catch (IOException ignored) {}
        }
        return null;
    }

    public String relativizePath(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (abs.startsWith(cwd)) {
            return cwd.relativize(abs).toString();
        }
        return abs.toString();
    }

    public String extractVersion(String tag) {
        if (tag == null) return null;
        Matcher m = VERSION_PATTERN.matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    public record ReleaseMeta(String version, String file) {}

    public void updateMhApiJson(Path targetDir, String releaseTag, String version, String fileName) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        Map<String, ReleaseMeta> map = new HashMap<>();
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, ReleaseMeta> existing = JsonUtil.gson.fromJson(content,
                        new TypeToken<Map<String, ReleaseMeta>>(){}.getType());
                if (existing != null) map.putAll(existing);
            }
        } catch (Exception ignored) {}
        map.values().removeIf(meta -> meta.file.equals(fileName));
        map.put(releaseTag, new ReleaseMeta(version != null ? version : "unknown", fileName));
        try {
            Files.writeString(jsonFile, JsonUtil.gson.toJson(map));
        } catch (IOException ignored) {}
    }

    public void fixUnknownVersion(Path targetDir, String version) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        try {
            if (!Files.exists(jsonFile)) return;
            String content = Files.readString(jsonFile);
            Map<String, ReleaseMeta> map = JsonUtil.gson.fromJson(content,
                    new TypeToken<Map<String, ReleaseMeta>>(){}.getType());
            if (map == null) return;
            boolean changed = false;
            List<String> unknownTags = new ArrayList<>();
            for (var entry : map.entrySet()) {
                if ("unknown".equals(entry.getValue().version)) {
                    unknownTags.add(entry.getKey());
                }
            }
            for (String tag : unknownTags) {
                map.put(tag, new ReleaseMeta(version, map.get(tag).file));
                changed = true;
            }
            if (changed) {
                Files.writeString(jsonFile, JsonUtil.gson.toJson(map));
            }
        } catch (Exception ignored) {}
    }

    public String checkExistingVersion(Path targetDir, String releaseTag) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, ReleaseMeta> map = JsonUtil.gson.fromJson(content,
                        new TypeToken<Map<String, ReleaseMeta>>(){}.getType());
                if (map != null && map.containsKey(releaseTag)) {
                    return map.get(releaseTag).version;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
