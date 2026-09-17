package indi.etern.musichud.client.services.cloud;

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.result.ActionResult;
import indi.etern.musichud.beans.user.cloud.CloudTrackInfo;
import indi.etern.musichud.beans.user.cloud.UploadTaskMeta;
import indi.etern.musichud.client.dto.CloudEntryState;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.utils.CloudUploadClient;
import indi.etern.musichud.client.utils.HashUtils;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.CloudUploadUpdateNotifier;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.CompleteCloudUploadRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.CompleteCloudUploadResponse;
import indi.etern.musichud.network.payloads.requestResponseCycle.GenerateUploadUrlRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.GenerateUploadUrlResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Client-side upload queue. Tasks are processed strictly sequentially on a virtual thread and
 * survive page navigation, so progress/state can be restored when the cloud page is reopened.
 * The file bytes never leave the client except for the direct object-storage upload.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CloudUploadService {
    public static final long COMPLETION_PRESENTATION_MILLIS = 2000L;
    /** Files above this size are uploaded with {@code offset} chunks instead of a single request. */
    private static final long CHUNK_THRESHOLD_BYTES = 200L * 1024 * 1024;
    /** Wait before scanning the listing for a MATCHING task that never received a push. */
    private static final long MATCH_FALLBACK_MILLIS = 5L * 60 * 1000;
    private static final int MATCH_SCAN_LIMIT = 100;

    private static final Logger logger = MusicHud.getLogger(CloudUploadService.class);
    private static volatile CloudUploadService instance;

    private final Object lock = new Object();
    private final Map<Long, CloudUploadTask> tasks = new LinkedHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final Map<Long, CompletableFuture<Void>> activeUploads = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(-1);
    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private final AtomicLong lastProgressNotifyMillis = new AtomicLong();
    private final Map<String, Long> songIdToTask = new ConcurrentHashMap<>();
    private final Map<String, Long> md5ToTask = new ConcurrentHashMap<>();
    private final Set<Long> matchingFallbackScheduled = ConcurrentHashMap.newKeySet();

    public static CloudUploadService getInstance() {
        if (instance == null) {
            synchronized (CloudUploadService.class) {
                if (instance == null) {
                    instance = new CloudUploadService();
                }
            }
        }
        return instance;
    }

    private static String extractArtwork(Tag tag) {
        if (tag == null) {
            return null;
        }
        try {
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) {
                return null;
            }
            byte[] data = artwork.getBinaryData();
            if (data == null || data.length == 0) {
                return null;
            }
            String mimeType = artwork.getMimeType();
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/jpeg";
            }
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (Throwable t) {
            logger.warn("Failed to extract embedded artwork", t);
            return null;
        }
    }

    private static void showCompletionToast(CloudUploadTask task) {
        String fileName = task.getFileName();
        MuiModApi.postToUiThread(() -> {
            try {
                //noinspection UnstableApiUsage
                var decorView = UIManager.getInstance().getDecorView();
                if (decorView == null) {
                    return;
                }
                ToastUtil.show(Toast.makeText(decorView.getContext(),
                        I18n.get(MusicHud.MOD_ID + ".text.cloudUploadSuccess") + "\n" + fileName,
                        Toast.LENGTH_SHORT));
            } catch (Throwable t) {
                logger.warn("Failed to show upload completion toast", t);
            }
        });
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String messageOf(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    public CloudUploadTask enqueue(Path file) {
        CloudUploadTask task = new CloudUploadTask(idSequence.getAndDecrement(), file);
        synchronized (lock) {
            tasks.put(task.getId(), task);
        }
        MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("V Uploader");
            try {
                prepareMetadata(task);
            } catch (Throwable t) {
                task.setState(CloudEntryState.FAILED);
                task.setErrorMessage(messageOf(t));
                logger.error("Cloud upload prepare failed: {}", task.getFileName(), t);
                notifyChanged();
                return;
            }
            ensureWorker();
        });
        return task;
    }

    public List<CloudUploadTask> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(tasks.values());
        }
    }

    public void cancel(long id) {
        CloudUploadTask task;
        synchronized (lock) {
            task = tasks.get(id);
        }
        if (task == null) {
            return;
        }
        task.setCancelRequested(true);
        if (task.getState() == CloudEntryState.QUEUED) {
            task.setState(CloudEntryState.CANCELLED);
        }
        CompletableFuture<Void> active = activeUploads.get(id);
        if (active != null) {
            active.cancel(true);
        }
        clearTaskMappings(task);
        notifyChanged();
    }

    public void retry(long id) {
        CloudUploadTask task;
        synchronized (lock) {
            task = tasks.get(id);
        }
        if (task == null) {
            return;
        }
        task.setCancelRequested(false);
        task.setBytesUploaded(0);
        task.setErrorMessage("");
        task.setResolvedTrackId(null);
        task.setState(CloudEntryState.QUEUED);
        MusicHud.EXECUTOR.submit(() -> {
            if (!task.isPrepared()) {
                try {
                    prepareMetadata(task);
                } catch (Throwable t) {
                    task.setState(CloudEntryState.FAILED);
                    task.setErrorMessage(messageOf(t));
                    logger.error("Cloud upload prepare failed: {}", task.getFileName(), t);
                    notifyChanged();
                    return;
                }
            } else {
                notifyChanged();
            }
            ensureWorker();
        });
    }

    public void remove(long id) {
        CloudUploadTask task;
        synchronized (lock) {
            task = tasks.get(id);
        }
        if (task == null) {
            return;
        }
        task.setCancelRequested(true);
        CompletableFuture<Void> active = activeUploads.get(id);
        if (active != null) {
            active.cancel(true);
        }
        synchronized (lock) {
            tasks.remove(id);
        }
        clearTaskMappings(task);
        notifyChanged();
    }

    /**
     * Marks the inline completion indication as already shown.
     */
    public void markPresented(long id) {
        CloudUploadTask task;
        synchronized (lock) {
            task = tasks.get(id);
        }
        if (task != null && task.getState() == CloudEntryState.COMPLETED) {
            task.setState(CloudEntryState.COMPLETED_PRESENTED);
            notifyChanged();
        }
    }

    /**
     * Drops all already-presented tasks; called when the listing becomes authoritative again.
     */
    public void prunePresented() {
        boolean changed;
        synchronized (lock) {
            changed = tasks.values().removeIf(task -> task.getState() == CloudEntryState.COMPLETED_PRESENTED);
        }
        if (changed) {
            notifyChanged();
        }
    }

    public Unregister addOnChange(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private void ensureWorker() {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            CloudUploadTask next;
            while ((next = nextQueued()) != null) {
                process(next);
            }
        } catch (Throwable t) {
            logger.error("Cloud upload worker crashed", t);
        } finally {
            workerRunning.set(false);
            if (nextQueued() != null) {
                ensureWorker();
            }
        }
    }

    private CloudUploadTask nextQueued() {
        synchronized (lock) {
            for (CloudUploadTask task : tasks.values()) {
                if (task.getState() == CloudEntryState.QUEUED && task.isPrepared()) {
                    return task;
                }
            }
            return null;
        }
    }

    private void process(CloudUploadTask task) {
        task.setState(CloudEntryState.UPLOADING);
        task.setCancelRequested(false);
        task.setBytesUploaded(0);
        notifyChanged();
        long id = task.getId();
        try {
            if (task.isCancelRequested()) {
                task.setState(CloudEntryState.CANCELLED);
                return;
            }

            GenerateUploadUrlResponse generateResponse = RequestResponseManager.send(
                            new GenerateUploadUrlRequest(task.getFileName(), task.getMd5(), task.getFileSize()),
                            GenerateUploadUrlResponse.class,
                            Duration.ofSeconds(20))
                    .join();
            if (generateResponse.getResult().actionResult() != ActionResult.SUCCESS) {
                throw new RuntimeException(generateResponse.getResult().message());
            }
            UploadTaskMeta meta = generateResponse.getResult().extraData();
            if (meta == null || !meta.available()) {
                throw new RuntimeException("Cloud upload token request failed");
            }
            task.setSongId(meta.songId());
            task.setResourceId(meta.resourceId());
            logger.debug("Cloud upload task {} token: needUpload={} songId={} resourceId={}",
                    id, meta.uploadable(), meta.songId(), meta.resourceId());

            if (meta.uploadable()) {
                uploadFile(task, meta);
            }

            if (task.isCancelRequested()) {
                task.setState(CloudEntryState.CANCELLED);
                return;
            }

            // Publishing on the NCM side can take a long time (and even outlive this request), so
            // move to MATCHING and let the S2C push (or the fallback listing scan) finish the task.
            task.setState(CloudEntryState.MATCHING);
            if (meta.songId() != null) {
                songIdToTask.put(meta.songId(), id);
            }
            if (task.getMd5() != null) {
                md5ToTask.put(task.getMd5().toLowerCase(Locale.ROOT), id);
            }
            notifyChanged();
            sendComplete(task, meta);
            scheduleMatchFallback(id);
        } catch (Throwable t) {
            if (task.isCancelRequested()) {
                task.setState(CloudEntryState.CANCELLED);
                logger.info("Cloud upload task {} cancelled", id);
            } else {
                task.setState(CloudEntryState.FAILED);
                task.setErrorMessage(httpStatus(t) == 413
                        ? I18n.get(MusicHud.MOD_ID + ".text.cloudUploadTooLarge")
                        : messageOf(t));
                logger.error("Cloud upload task {} failed: {}", id, task.getFileName(), t);
            }
            notifyChanged();
        } finally {
            activeUploads.remove(id);
        }
    }

    /**
     * Uploads the file: single request by default, {@code offset} chunks for very large files,
     * and a chunked retry as a safety net when a single request is rejected with HTTP 413.
     */
    private void uploadFile(CloudUploadTask task, UploadTaskMeta meta) throws Exception {
        long id = task.getId();
        task.setChunked(false);
        task.setBytesUploaded(0);
        LongConsumer progress = bytes -> {
            task.setBytesUploaded(task.getBytesUploaded() + bytes);
            long now = System.currentTimeMillis();
            long last = lastProgressNotifyMillis.get();
            if (task.getBytesUploaded() < task.getFileSize() && now - last < 200) {
                return;
            }
            if (lastProgressNotifyMillis.compareAndSet(last, now)) {
                notifyChanged();
            }
        };
        if (task.getFileSize() > CHUNK_THRESHOLD_BYTES) {
            task.setChunked(true);
            notifyChanged();
            CloudUploadClient.uploadChunked(meta.uploadUrl(), task.getFile(), meta.uploadToken(),
                    CloudUploadClient.contentTypeOf(task.getFileName()), task.getFileSize(),
                    CloudUploadClient.CHUNK_SIZE_BYTES, progress, task::isCancelRequested);
            return;
        }
        try {
            CompletableFuture<Void> upload = CloudUploadClient.upload(
                    meta.uploadUrl(), task.getFile(), meta.uploadToken(), task.getMd5(),
                    task.getFileSize(), CloudUploadClient.contentTypeOf(task.getFileName()),
                    progress, task::isCancelRequested);
            activeUploads.put(id, upload);
            try {
                upload.join();
            } finally {
                activeUploads.remove(id);
            }
        } catch (RuntimeException t) {
            if (httpStatus(t) == 413) {
                logger.warn("Single request upload rejected with HTTP 413 for {}, retrying with {} byte chunks",
                        task.getFileName(), CloudUploadClient.CHUNK_SIZE_BYTES);
                task.setChunked(true);
                task.setBytesUploaded(0);
                notifyChanged();
                CloudUploadClient.uploadChunked(meta.uploadUrl(), task.getFile(), meta.uploadToken(),
                        CloudUploadClient.contentTypeOf(task.getFileName()), task.getFileSize(),
                        CloudUploadClient.CHUNK_SIZE_BYTES, progress, task::isCancelRequested);
            } else {
                throw t;
            }
        }
    }

    private static int httpStatus(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof CloudUploadClient.CloudUploadHttpException http) {
                return http.statusCode();
            }
            cause = cause.getCause();
        }
        return -1;
    }

    /** Sends the publish request but only waits for the server's quick ack; the result arrives by push. */
    private void sendComplete(CloudUploadTask task, UploadTaskMeta meta) {
        long id = task.getId();
        RequestResponseManager.send(
                        new CompleteCloudUploadRequest(meta.songId(), meta.resourceId(), task.getMd5(),
                                task.getFileName(), task.getSong(), task.getArtist(), task.getAlbum()),
                        CompleteCloudUploadResponse.class,
                        Duration.ofSeconds(20))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        // Keep MATCHING: the publish may still have been accepted server-side; the
                        // push or the fallback listing scan will reconcile it.
                        logger.warn("Cloud upload complete ack failed for task {}: {}", id, throwable.toString());
                    }
                });
    }

    /** Called when the server pushes the publish result for {@code songId}. */
    public void onUploadCompleted(String songId, String resolvedTrackId, boolean success, String message) {
        Long taskId = songId == null ? null : songIdToTask.remove(songId);
        if (taskId == null) {
            return;
        }
        CloudUploadTask task;
        synchronized (lock) {
            task = tasks.get(taskId);
        }
        if (task == null) {
            return;
        }
        if (task.getMd5() != null) {
            md5ToTask.remove(task.getMd5().toLowerCase(Locale.ROOT));
        }
        matchingFallbackScheduled.remove(taskId);
        if (success) {
            task.setResolvedTrackId(resolvedTrackId == null || resolvedTrackId.isBlank() ? null : resolvedTrackId);
            task.setState(CloudEntryState.COMPLETED);
            showCompletionToast(task);
            fetchResolvedDetail(task);
        } else {
            task.setErrorMessage(message == null || message.isBlank() ? "Upload publish failed" : message);
            task.setState(CloudEntryState.FAILED);
        }
        notifyChanged();
    }

    /** Loads the real detail for a completed task on demand and swaps the row to it. */
    private void fetchResolvedDetail(CloudUploadTask task) {
        Long trackId = parseId(task.getResolvedTrackId());
        if (trackId == null) {
            return;
        }
        MusicService.getInstance().loadMusicDetails(List.of(trackId), true).thenAccept(details -> {
            if (details == null || details.isEmpty()) {
                return;
            }
            CloudUploadTask current;
            synchronized (lock) {
                current = tasks.get(task.getId());
            }
            if (current == null) {
                return;
            }
            current.setResolvedDetail(details.getFirst());
            notifyChanged();
        });
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** After 5 minutes without a push, refresh the listing once and try to match pending tasks. */
    private void scheduleMatchFallback(long id) {
        if (!matchingFallbackScheduled.add(id)) {
            return;
        }
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(MATCH_FALLBACK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            matchingFallbackScheduled.remove(id);
            CloudUploadTask task;
            synchronized (lock) {
                task = tasks.get(id);
            }
            if (task == null || task.getState() != CloudEntryState.MATCHING) {
                return;
            }
            MusicService.getInstance().loadCloudTracks(0, MATCH_SCAN_LIMIT).thenAccept(result -> {
                if (result.actionResult() == ActionResult.SUCCESS && result.extraData() != null) {
                    reconcileMatches(result.extraData().tracks());
                }
                CloudUploadTask current;
                synchronized (lock) {
                    current = tasks.get(id);
                }
                if (current != null && current.getState() == CloudEntryState.MATCHING) {
                    current.setErrorMessage(I18n.get(MusicHud.MOD_ID + ".text.cloudUploadMatchUncertain"));
                    notifyChanged();
                }
            });
        });
    }

    /**
     * Matches MATCHING tasks against freshly loaded cloud tracks (by private-cloud MD5 first,
     * then file size + song name) and completes the ones found. Also used on every listing load.
     */
    public void reconcileMatches(List<CloudTrackInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (CloudTrackInfo info : infos) {
            if (info == null || info.detail() == null) {
                continue;
            }
            Long taskId = info.md5() == null ? null : md5ToTask.get(info.md5().toLowerCase(Locale.ROOT));
            if (taskId == null && info.fileSize() > 0) {
                synchronized (lock) {
                    for (CloudUploadTask candidate : tasks.values()) {
                        if (candidate.getState() == CloudEntryState.MATCHING
                                && candidate.getFileSize() == info.fileSize()
                                && candidate.getSong() != null
                                && candidate.getSong().equals(info.detail().getName())) {
                            taskId = candidate.getId();
                            break;
                        }
                    }
                }
            }
            if (taskId == null) {
                continue;
            }
            CloudUploadTask task;
            synchronized (lock) {
                task = tasks.get(taskId);
            }
            if (task != null && task.getState() == CloudEntryState.MATCHING) {
                clearTaskMappings(task);
                task.setResolvedTrackId(Long.toString(info.detail().getId()));
                task.setResolvedDetail(info.detail());
                task.setState(CloudEntryState.COMPLETED);
                showCompletionToast(task);
                changed = true;
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    private void clearTaskMappings(CloudUploadTask task) {
        if (task.getSongId() != null) {
            songIdToTask.remove(task.getSongId());
        }
        if (task.getMd5() != null) {
            md5ToTask.remove(task.getMd5().toLowerCase(Locale.ROOT));
        }
        matchingFallbackScheduled.remove(task.getId());
    }

    private void prepareMetadata(CloudUploadTask task) {
        Path file = task.getFile();
        try {
            task.setFileSize(Files.size(file));
        } catch (Throwable t) {
            task.setFileSize(file.toFile().length());
        }
        task.setMd5(HashUtils.computeMd5(file));
        if (task.getMd5() == null) {
            throw new IllegalStateException("Failed to compute MD5 of " + task.getFileName());
        }
        String fallbackSong = task.getFileName().replaceFirst("\\.[^.]+$", "");
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            task.setSong(firstNonBlank(tag == null ? null : tag.getFirst(FieldKey.TITLE), fallbackSong));
            task.setArtist(firstNonBlank(tag == null ? null : tag.getFirst(FieldKey.ARTIST), ""));
            task.setAlbum(firstNonBlank(tag == null ? null : tag.getFirst(FieldKey.ALBUM), ""));
            task.setCoverDataUri(extractArtwork(tag));
            try {
                AudioHeader header = audioFile.getAudioHeader();
                if (header != null) {
                    double precise = header.getPreciseTrackLength();
                    int millis = precise > 0 ? (int) Math.round(precise * 1000) : header.getTrackLength() * 1000;
                    task.setDurationMillis(Math.max(0, millis));
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            logger.warn("Failed to read audio tags from {}, falling back to file name", task.getFileName(), t);
            task.setSong(fallbackSong);
            task.setArtist("");
            task.setAlbum("");
            task.setCoverDataUri(null);
        }
        task.setPrepared(true);
        notifyChanged();
    }

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            CloudUploadUpdateNotifier.register(completion ->
                    getInstance().onUploadCompleted(completion.songId(), completion.resolvedTrackId(),
                            completion.success(), completion.message()));
        }
    }
}
