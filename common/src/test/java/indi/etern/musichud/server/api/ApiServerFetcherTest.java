package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import lombok.SneakyThrows;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApiServerFetcherTest {
    private static final Logger LOG = MusicHud.getLogger(ApiServerFetcherTest.class);

    @SneakyThrows
    @Test
    void testPlatformDetection() {
        ApiServerFetcher.Platform platform = ApiServerFetcher.Platform.detect();
        assertNotNull(platform);
        LOG.info("Detected platform: {} -> asset name: {}", platform, platform.getAssetName());

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            assertEquals(ApiServerFetcher.Platform.WINDOWS_X64, platform);
            assertTrue(platform.getAssetName().endsWith(".exe"));
        } else if (os.contains("mac")) {
            assertEquals(ApiServerFetcher.Platform.MACOS_X64, platform);
            assertFalse(platform.getAssetName().endsWith(".exe"));
        } else {
            assertEquals(ApiServerFetcher.Platform.LINUX_X64, platform);
            assertFalse(platform.getAssetName().endsWith(".exe"));
        }
    }

    // -- zero-quota: Atom feed --

    @SneakyThrows
    @Test
    void testListReleaseSummaries() {
        List<ApiServerFetcher.ReleaseSummary> summaries = ApiServerFetcher.listReleaseSummaries().get();
        assertNotNull(summaries);
        assertFalse(summaries.isEmpty(), "Atom feed should return at least one release");

        for (ApiServerFetcher.ReleaseSummary s : summaries) {
            assertNotNull(s.tag(), "tag must not be null");
            assertNotNull(s.title(), "title must not be null");
            assertFalse(s.tag().isBlank(), "tag must not be blank");
            assertNotNull(s.htmlUrl());
            assertTrue(s.htmlUrl().startsWith("https://github.com/"), "htmlUrl should be a GitHub URL: " + s.htmlUrl());
            LOG.info("Summary: tag={}, title={}, published={}", s.tag(), s.title(), s.publishedAt());
        }

        LOG.info("Total summaries (Atom feed, 0 API calls): {}", summaries.size());
    }

    @SneakyThrows
    @Test
    void testListReleaseSummariesHasLatest() {
        List<ApiServerFetcher.ReleaseSummary> summaries = ApiServerFetcher.listReleaseSummaries().get();
        assertNotNull(summaries);
        assertFalse(summaries.isEmpty());

        ApiServerFetcher.ReleaseSummary latest = summaries.getFirst();
        assertNotNull(latest.publishedAt(), "publishedAt should be populated from Atom feed");
        LOG.info("Latest (Atom): tag={}, published={}", latest.tag(), latest.publishedAt());
    }

    // -- zero-quota: direct download URL --

    @SneakyThrows
    @Test
    void testDownloadLatestForCurrentPlatform() {
        Path tmpDir = Files.createTempDirectory("musichud-test-");
        try {
            ApiServerFetcher.downloadLatestForCurrentPlatform(tmpDir, progress -> {
                if (progress > 0 && Math.abs(progress - Math.round(progress * 10) / 10.0) < 0.001) {
                    LOG.info("Download progress: {}%", Math.round(progress * 100));
                }
            }).get();

            String expectedName = ApiServerFetcher.Platform.detect().getAssetName();
            Path downloaded = tmpDir.resolve(expectedName);
            assertTrue(Files.exists(downloaded), "File should exist: " + downloaded);
            assertTrue(Files.size(downloaded) > 0, "Downloaded file should not be empty");
            LOG.info("Downloaded {} bytes to {} (0 API calls)", Files.size(downloaded), downloaded);

            if (!expectedName.endsWith(".exe")) {
                assertTrue(downloaded.toFile().canExecute(),
                        "Binary should be executable on non-Windows: " + downloaded);
            }
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    // -- REST API (quota-consuming, optional) --

    @SneakyThrows
    @Test
    void testListReleasesViaRestApi() {
        List<ApiServerFetcher.Release> releases = ApiServerFetcher.listReleases().get();
        assertNotNull(releases);
        assertFalse(releases.isEmpty(), "REST API should return at least one release");

        ApiServerFetcher.Release latest = releases.getFirst();
        assertNotNull(latest.getTagName());
        assertNotNull(latest.getHtmlUrl());
        assertNotNull(latest.getAssets());
        assertFalse(latest.getAssets().isEmpty(), "Latest release should have assets");

        boolean hasPlatformAsset = latest.getAssets().stream()
                .map(ApiServerFetcher.Asset::getName)
                .anyMatch(name -> name.startsWith("ncm-api-"));
        assertTrue(hasPlatformAsset, "Latest release should contain platform binary assets");
        LOG.info("REST API latest: tag={}, assets={}", latest.getTagName(), latest.getAssets().size());
    }

    @SneakyThrows
    @Test
    void testListReleasesPopulatesAllFields() {
        List<ApiServerFetcher.Release> releases = ApiServerFetcher.listReleases().get();
        assertNotNull(releases);
        assertFalse(releases.isEmpty());

        ApiServerFetcher.Release latest = releases.getFirst();
        assertNotNull(latest.getPublishedAt(), "published_at should be populated");
        assertNotNull(latest.getName(), "name should be populated");
        assertNotNull(latest.getTagName(), "tag_name should be populated");
        assertNotNull(latest.getHtmlUrl(), "html_url should be populated");
        assertNotNull(latest.getAssets(), "assets should not be null");
    }

    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (IOException ignored) {
            try { dir.toFile().deleteOnExit(); } catch (Exception ignored2) {}
        }
    }

    @SneakyThrows
    @Test
    void testTokenFlow() {
        // token should be null by default
        assertNull(ApiServerFetcher.getGitHubToken());

        // set a dummy token
        ApiServerFetcher.setGitHubToken("ghp_test123");
        assertEquals("ghp_test123", ApiServerFetcher.getGitHubToken());

        // blank clears it
        ApiServerFetcher.setGitHubToken("  ");
        assertNull(ApiServerFetcher.getGitHubToken());

        // null clears it
        ApiServerFetcher.setGitHubToken("ghp_test123");
        ApiServerFetcher.setGitHubToken(null);
        assertNull(ApiServerFetcher.getGitHubToken());

        LOG.info("Token flow works correctly");
    }
}
