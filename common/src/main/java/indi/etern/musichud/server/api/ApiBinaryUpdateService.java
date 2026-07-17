package indi.etern.musichud.server.api;

import com.google.gson.reflect.TypeToken;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.utils.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class ApiBinaryUpdateService {

    private static final ApiBinaryUpdateService INSTANCE = new ApiBinaryUpdateService();

    public static ApiBinaryUpdateService getInstance() {
        return INSTANCE;
    }

    private ApiBinaryUpdateService() {}

    public CompletableFuture<ApiServerFetcher.ReleaseSummary> fetchLatestRelease() {
        return ApiServerFetcher.listReleaseSummaries().thenApply(summaries -> {
            if (summaries != null && !summaries.isEmpty()) {
                return summaries.getFirst();
            }
            return null;
        });
    }

    public CompletableFuture<Path> downloadToTemp(Path targetDir, String releaseTag, BiConsumer<Long, Long> progress) {
        String tempFileName = ApiServerFetcher.Platform.detect().getAssetName() + "." + releaseTag + ".temp";
        Path tempFile = targetDir.resolve(tempFileName);
        return ApiServerFetcher.downloadLatestForCurrentPlatform(targetDir, tempFileName, progress)
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

    public void updateMhApiJson(Path targetDir, String releaseTag, String fileName) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        Map<String, String> map = new HashMap<>();
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, String> existing = JsonUtil.gson.fromJson(content, new TypeToken<Map<String, String>>(){}.getType());
                if (existing != null) map.putAll(existing);
            }
        } catch (Exception ignored) {}
        map.values().removeIf(fName -> fName.equals(fileName));
        map.put(releaseTag, fileName);
        try {
            Files.writeString(jsonFile, JsonUtil.gson.toJson(map));
        } catch (IOException ignored) {}
    }

    public String checkExistingVersion(Path targetDir, String releaseTag) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, String> map = JsonUtil.gson.fromJson(content, new TypeToken<Map<String, String>>(){}.getType());
                if (map != null && map.containsKey(releaseTag)) {
                    return map.get(releaseTag);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
