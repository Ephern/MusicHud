package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session-scoped temp storage under one fixed tree. Each JVM session owns an
 * {@code s-<uuid>} directory guarded by an exclusive lock; a session whose lock
 * can be acquired belongs to a dead process and is reclaimed on the next start.
 */
public final class TempService {
    /** Overrides the music_hud temp root. */
    public static final String TEMP_ROOT_PROPERTY = "music_hud.temp.root";
    private static final Logger logger = MusicHud.getLogger(TempService.class);
    private static final String ROOT_DIR_NAME = "music_hud";
    private static final String SESSION_PREFIX = "s-";
    private static final String LOCK_FILE_NAME = ".lock";
    private static final String LEGACY_PREFIX = "MusicHUD_";
    /** A session directory without a lock file may still be mid-creation. */
    private static final long UNLOCKED_SESSION_GRACE_MS = 120_000L;
    /** Pre-session directories carry no lock, so only their age can be probed. */
    private static final long LEGACY_GRACE_MS = 3_600_000L;

    private static final Object INIT_LOCK = new Object();
    private static final Object SHARED_LOCK = new Object();
    private static final Set<SessionImpl> liveSessions = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean();

    private static volatile boolean initialized;
    private static volatile Path root;
    private static volatile Path legacyBase;
    private static volatile SessionImpl sharedSession;

    private TempService() {
    }

    /** Resolved temp root, or {@code null} when the file system refuses it. */
    public static @Nullable Path root() {
        if (initialized) return root;
        synchronized (INIT_LOCK) {
            if (initialized) return root;
            // Set first: sweep() must not re-enter the initialization.
            initialized = true;
            try {
                resolveRoot();
                Files.createDirectories(root);
                registerShutdownHook();
                sweepRoot(root);
                sweepLegacy(legacyBase);
                logger.debug("Temp root: {}", root);
            } catch (Exception e) {
                logger.warn("Temporary storage unavailable under {}, dependent features stay disabled",
                        root == null ? System.getProperty("java.io.tmpdir") : root, e);
                root = null;
            }
            return root;
        }
    }

    private static void resolveRoot() {
        String override = System.getProperty(TEMP_ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            try {
                root = Paths.get(override).toAbsolutePath().normalize();
                return;
            } catch (InvalidPathException e) {
                logger.warn("Ignoring invalid {} value: {}", TEMP_ROOT_PROPERTY, override);
            }
        }
        Path base = Paths.get(System.getProperty("java.io.tmpdir", ".")).toAbsolutePath().normalize();
        legacyBase = base;
        root = base.resolve(ROOT_DIR_NAME);
    }

    /** Creates an independent session; lives until {@link Session#close()} or JVM exit. */
    public static @Nullable Session openSession() {
        Path dir = root();
        if (dir == null) return null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Path sessionDir = dir.resolve(SESSION_PREFIX + UUID.randomUUID());
            try {
                Files.createDirectory(sessionDir);
            } catch (IOException e) {
                logger.warn("Failed to create temp session under {}", dir, e);
                return null;
            }
            SessionImpl session = SessionImpl.lock(sessionDir);
            if (session != null) {
                liveSessions.add(session);
                return session;
            }
            deleteTree(sessionDir);
        }
        logger.warn("Failed to lock a temp session under {}", dir);
        return null;
    }

    /** Process-wide session. */
    public static @Nullable Session shared() {
        SessionImpl current = sharedSession;
        if (current != null && !current.isClosed()) return current;
        synchronized (SHARED_LOCK) {
            current = sharedSession;
            if (current != null && !current.isClosed()) return current;
            Session opened = openSession();
            if (opened == null) return null;
            sharedSession = (SessionImpl) opened;
            return opened;
        }
    }

    /** Reclaims session directories whose owner died, plus stale pre-session leftovers. */
    public static void sweep() {
        Path dir = root();
        if (dir == null) return;
        sweepRoot(dir);
        sweepLegacy(legacyBase);
    }

    private static void sweepRoot(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, SESSION_PREFIX + "*")) {
            for (Path sessionDir : stream) {
                if (!Files.isDirectory(sessionDir, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!isDeadSession(sessionDir)) continue;
                logger.debug("Reclaiming abandoned temp session {}", sessionDir.getFileName());
                deleteTree(sessionDir);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan temp sessions under {}", dir, e);
        }
    }

    private static void sweepLegacy(Path base) {
        if (base == null) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base, LEGACY_PREFIX + "*")) {
            for (Path legacy : stream) {
                if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) continue;
                if (ageMillis(legacy) < LEGACY_GRACE_MS) continue;
                deleteTree(legacy);
            }
        } catch (IOException e) {
            logger.debug("Legacy temp sweep skipped: {}", e.toString());
        }
    }

    /** Dead when nobody holds the lock; a lockless directory is only taken once old. */
    private static boolean isDeadSession(Path sessionDir) {
        Path lockPath = sessionDir.resolve(LOCK_FILE_NAME);
        if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            return ageMillis(sessionDir) > UNLOCKED_SESSION_GRACE_MS;
        }
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) return false;
            lock.release();
            return true;
        } catch (OverlappingFileLockException e) {
            // Another session in this JVM holds it.
            return false;
        } catch (IOException e) {
            return ageMillis(sessionDir) > UNLOCKED_SESSION_GRACE_MS;
        }
    }

    private static long ageMillis(Path path) {
        try {
            return System.currentTimeMillis() - Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void registerShutdownHook() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) return;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(TempService::shutdown, "MH-TempCleanup"));
        } catch (IllegalStateException | SecurityException e) {
            logger.warn("Failed to register temp cleanup hook", e);
        }
    }

    private static void shutdown() {
        for (SessionImpl session : List.copyOf(liveSessions)) {
            try {
                session.close();
            } catch (Throwable ignored) {
            }
        }
        Path dir = root;
        if (dir != null) {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
            }
        }
    }

    static void deleteTree(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path visited, IOException exc) throws IOException {
                    Files.deleteIfExists(visited);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to delete {}", dir, e);
        }
    }

    /** Drops all sessions and re-arms initialization; test-only. */
    static void resetForTesting() {
        for (SessionImpl session : List.copyOf(liveSessions)) {
            session.close();
        }
        synchronized (INIT_LOCK) {
            root = null;
            legacyBase = null;
            sharedSession = null;
            initialized = false;
        }
    }

    public interface Session extends AutoCloseable {
        Path directory();

        Path audioDirectory();

        Path albumDirectory();

        Path allocateAudio(String prefix, String suffix) throws IOException;

        Path allocateAlbum(String prefix, String suffix) throws IOException;

        /** Free bytes on the volume backing this session; {@code 0} when unknown. */
        long usableSpace();

        boolean isClosed();

        @Override
        void close();
    }

    private static final class SessionImpl implements Session {
        private final Path directory;
        private final FileChannel lockChannel;
        private final FileLock lock;
        private volatile boolean closed;

        private SessionImpl(Path directory, FileChannel lockChannel, FileLock lock) {
            this.directory = directory;
            this.lockChannel = lockChannel;
            this.lock = lock;
        }

        private static @Nullable SessionImpl lock(Path directory) {
            Path lockPath = directory.resolve(LOCK_FILE_NAME);
            FileChannel channel = null;
            try {
                channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    channel.close();
                    return null;
                }
                return new SessionImpl(directory, channel, lock);
            } catch (OverlappingFileLockException | IOException e) {
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                    }
                }
                return null;
            }
        }

        @Override
        public Path directory() {
            return directory;
        }

        @Override
        public Path audioDirectory() {
            return ensure("audio");
        }

        @Override
        public Path albumDirectory() {
            return ensure("albums");
        }

        private Path ensure(String name) {
            if (closed) throw new IllegalStateException("Temp session already closed");
            Path dir = directory.resolve(name);
            try {
                Files.createDirectories(dir);
                ClientDataFileStore.restrictDirectoryPermissions(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create " + dir, e);
            }
            return dir;
        }

        @Override
        public Path allocateAudio(String prefix, String suffix) throws IOException {
            return Files.createTempFile(audioDirectory(), sanitizePrefix(prefix), suffix);
        }

        @Override
        public Path allocateAlbum(String prefix, String suffix) throws IOException {
            return Files.createTempFile(albumDirectory(), sanitizePrefix(prefix), suffix);
        }

        @Override
        public long usableSpace() {
            try {
                return Files.getFileStore(directory).getUsableSpace();
            } catch (Exception e) {
                return 0;
            }
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
            }
            liveSessions.remove(this);
            if (sharedSession == this) sharedSession = null;
            try {
                lock.release();
            } catch (Exception ignored) {
            }
            try {
                lockChannel.close();
            } catch (Exception ignored) {
            }
            deleteTree(directory);
        }
    }

    private static String sanitizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return "";
        StringBuilder builder = new StringBuilder(prefix.length());
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.';
            builder.append(safe ? c : '_');
        }
        return builder.toString();
    }
}
