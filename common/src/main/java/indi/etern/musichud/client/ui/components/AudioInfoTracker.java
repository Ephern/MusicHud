package indi.etern.musichud.client.ui.components;

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.audio.PlaybackTask;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.audio.info.AudioPlaybackInfo;
import indi.etern.musichud.interfaces.Unregister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Delivers the metadata snapshots of one track's playback task on the UI thread. */
final class AudioInfoTracker {
    private static final int RETRY_COUNT = 10;
    private static final int RETRY_DELAY_MS = 100;

    private final View host;
    private final Consumer<AudioPlaybackInfo> listener;
    private PlaybackTask task;
    private Unregister unregister;
    private long lastRevision = -1;
    private int token;

    AudioInfoTracker(View host, Consumer<AudioPlaybackInfo> listener) {
        this.host = host;
        this.listener = listener;
    }

    void bind(long musicId) {
        clear();
        int bindToken = ++token;
        attach(musicId, bindToken, 0);
    }

    void clear() {
        lastRevision = -1;
        task = null;
        token++;
        if (unregister != null) {
            unregister.unregister();
            unregister = null;
        }
    }

    private void attach(long musicId, int bindToken, int attempt) {
        PlaybackTask found = findTask(musicId);
        if (found == null) {
            if (attempt < RETRY_COUNT) {
                host.postDelayed(() -> {
                    if (token == bindToken) {
                        attach(musicId, bindToken, attempt + 1);
                    }
                }, RETRY_DELAY_MS);
            }
            return;
        }
        task = found;
        unregister = found.addAudioInfoListener(info -> {
            if (info.musicId() != musicId) return;
            try {
                MuiModApi.postToUiThread(() -> {
                    if (token == bindToken && task == found && info.revision() > lastRevision) {
                        lastRevision = info.revision();
                        listener.accept(info);
                    }
                });
            } catch (IllegalStateException ignored) {
            }
        });
    }

    private static @Nullable PlaybackTask findTask(long musicId) {
        StreamAudioPlayer player = StreamAudioPlayer.getInstance();
        // A replay of the same track registers its successor as pending before it becomes current.
        PlaybackTask[] tasks = {player.getPendingTask(), player.getCurrentTask(), player.getPreloadedTask()};
        for (PlaybackTask task : tasks) {
            if (task != null && task.getMusicDetail() != null && task.getMusicDetail().getId() == musicId) {
                return task;
            }
        }
        return null;
    }
}
