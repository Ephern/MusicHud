package indi.etern.musichud.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

public final class ClientDataFileStore {
    private static final Logger logger = MusicHud.getLogger(ClientDataFileStore.class);

    private ClientDataFileStore() {
    }

    public static <T> T read(Path path, Class<T> type) {
        String json = readString(path);
        if (json == null) {
            return null;
        }
        try {
            return JsonUtil.gson.fromJson(json, type);
        } catch (RuntimeException e) {
            logger.warn("Malformed data in {}: {}", path.toAbsolutePath(), e.getClass().getSimpleName());
            return null;
        }
    }

    public static JsonObject readJsonObject(Path path) {
        String json = readString(path);
        if (json == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            logger.warn("Malformed data in {}: {}", path.toAbsolutePath(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static String readString(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }
            if (json.charAt(0) == '\uFEFF') {
                json = json.substring(1);
            }
            return json.isBlank() ? null : json;
        } catch (IOException e) {
            logger.warn("Failed to read {}", path.toAbsolutePath(), e);
            return null;
        }
    }

    public static boolean write(Path path, Object value) {
        try {
            return writeString(path, JsonUtil.gson.toJson(value));
        } catch (RuntimeException e) {
            logger.error("Failed to serialize data for {}", path.toAbsolutePath(), e);
            return false;
        }
    }

    public static boolean writeString(Path path, String json) {
        Path target = path.toAbsolutePath();
        Path dir = target.getParent() != null ? target.getParent() : Paths.get(".").toAbsolutePath();
        Path temp = null;
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                restrictDirectoryPermissions(dir);
            }
            temp = Files.createTempFile(dir, target.getFileName().toString() + ".", ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            restrictFilePermissions(temp);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            restrictFilePermissions(target);
            return true;
        } catch (IOException e) {
            logger.error("Failed to write {}", target, e);
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete {}", path.toAbsolutePath(), e);
            return false;
        }
    }

    public static boolean exists(Path path) {
        return Files.isRegularFile(path);
    }

    private static void restrictFilePermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private static void restrictDirectoryPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }
}
