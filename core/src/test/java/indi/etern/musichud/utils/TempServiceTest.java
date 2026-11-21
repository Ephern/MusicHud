package indi.etern.musichud.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempServiceTest {
    @TempDir
    Path tempDir;
    private Path root;
    private String originalTempDir;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("music_hud");
        System.setProperty(TempService.TEMP_ROOT_PROPERTY, root.toString());
        TempService.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        TempService.resetForTesting();
        System.clearProperty(TempService.TEMP_ROOT_PROPERTY);
        if (originalTempDir != null) {
            System.setProperty("java.io.tmpdir", originalTempDir);
            originalTempDir = null;
        }
    }

    @Test
    void rootIsCreatedOnDemand() {
        assertEquals(root, TempService.root());
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void sessionDirectoriesAreIsolated() {
        TempService.Session first = TempService.openSession();
        TempService.Session second = TempService.openSession();
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.directory(), second.directory());
        assertEquals(root, first.directory().getParent());
        assertEquals(root, second.directory().getParent());
        first.close();
        second.close();
    }

    @Test
    void closeRemovesSessionDirectory() throws IOException {
        TempService.Session session = TempService.openSession();
        assertNotNull(session);
        Path file = session.allocateAudio("track", ".part");
        assertTrue(Files.isRegularFile(file));
        session.close();
        assertTrue(session.isClosed());
        assertFalse(Files.exists(session.directory()));
        assertFalse(Files.exists(file));
    }

    @Test
    void allocationsStayInsideTheirNamespace() throws IOException {
        TempService.Session session = TempService.openSession();
        assertNotNull(session);
        Path audio = session.allocateAudio("a", ".part");
        Path album = session.allocateAlbum("b", ".png");
        assertEquals(session.audioDirectory(), audio.getParent());
        assertEquals(session.albumDirectory(), album.getParent());
        assertNotEquals(session.audioDirectory(), session.albumDirectory());
        session.close();
    }

    @Test
    void sweepReclaimsAbandonedSession() throws IOException {
        Path dead = Files.createDirectories(root.resolve("s-dead"));
        Files.createFile(dead.resolve(".lock"));
        Path nested = Files.createDirectories(dead.resolve("audio")).resolve("x.part");
        Files.writeString(nested, "data");

        TempService.sweep();

        assertFalse(Files.exists(dead));
    }

    @Test
    void sweepKeepsLiveSession() throws IOException {
        TempService.Session live = TempService.openSession();
        assertNotNull(live);
        Path file = live.allocateAudio("live", ".part");
        Files.writeString(file, "data");

        TempService.sweep();

        assertTrue(Files.isRegularFile(file));
        live.close();
    }

    @Test
    void sweepKeepsUnlockedSessionThatMayStillBeCreated() throws IOException {
        Path creating = Files.createDirectories(root.resolve("s-creating"));
        Files.writeString(creating.resolve("audio.tmp"), "data");

        TempService.sweep();

        assertTrue(Files.isDirectory(creating));
    }

    @Test
    void sharedSessionIsReused() {
        TempService.Session first = TempService.shared();
        TempService.Session second = TempService.shared();
        assertNotNull(first);
        assertSame(first, second);
        first.close();
        TempService.Session third = TempService.shared();
        assertNotNull(third);
        assertNotEquals(first.directory(), third.directory());
    }

    @Test
    void sessionsSurviveWhenTempRootIsUnavailable() throws IOException {
        Path blocker = Files.createFile(tempDir.resolve("blocker"));
        System.setProperty(TempService.TEMP_ROOT_PROPERTY, blocker.resolve("music_hud").toString());
        TempService.resetForTesting();
        assertNull(TempService.root());
        assertNull(TempService.openSession());
        assertNull(TempService.shared());
    }

    @Test
    void legacyLeftoversAreReclaimedOnlyWhenStale() throws Exception {
        System.clearProperty(TempService.TEMP_ROOT_PROPERTY);
        originalTempDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempDir.toString());
        TempService.resetForTesting();

        Path stale = Files.createDirectories(tempDir.resolve("MusicHUD_stale"));
        Files.writeString(stale.resolve("cover.png"), "data");
        Files.setLastModifiedTime(stale, FileTime.fromMillis(System.currentTimeMillis() - 7_200_000L));
        Path fresh = Files.createDirectories(tempDir.resolve("MusicHUD_fresh"));

        assertEquals(tempDir.resolve("music_hud"), TempService.root());

        assertFalse(Files.exists(stale));
        assertTrue(Files.isDirectory(fresh));
    }

    @Test
    void usableSpaceIsQueryable() {
        TempService.Session session = TempService.openSession();
        assertNotNull(session);
        assertTrue(session.usableSpace() >= 0);
        session.close();
    }
}
