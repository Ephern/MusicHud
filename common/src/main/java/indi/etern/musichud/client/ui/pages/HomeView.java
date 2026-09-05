package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.QueueItem;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.MusicTrackItem;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.Unregister;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class HomeView extends LinearLayout {
    /** Card identity: collection id+type per layer; both play modes share one card. */
    private record CardKey(boolean local, long id, Class<?> type) {
        static CardKey of(boolean local, IdlePlaySource idlePlaySource) {
            return new CardKey(local, idlePlaySource.getId(), idlePlaySource.getType());
        }
    }

    private static final MusicService musicService = MusicService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    @Getter
    private static HomeView instance;
    private final Set<IdlePlaySource> serverIdlePlaySources = musicService.getIdlePlaySourceState().external().getSources();
    private final Set<IdlePlaySource> clientIdlePlaySources = musicService.getIdlePlaySourceState().local().getSources();
    private final Map<CardKey, MusicCollectionCard> idlePlaySourceCardMap = new ConcurrentHashMap<>();
    @Getter
    private StaggeredLyricScrollView staggeredLyricScrollView;
    private MusicTrackItem nextToPlayItem;
    private TextView nextToPlayTitle;
    private TextView queueTitle;
    private LinearLayout playQueueListView;
    private LinearLayout clientIdlePlaySourceView;
    private LinearLayout serverIdlePlaySourceView;
    private FlexWrapLayout clientIdlePlaySourceCardsList;
    private final Consumer<IdlePlaySource> localAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            CardKey key = CardKey.of(true, collection);
            if (!idlePlaySourceCardMap.containsKey(key)) {
                addIdlePlaySourceTo(collection, clientIdlePlaySourceCardsList, true);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private final Consumer<IdlePlaySource> localRemoveListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            if (hasSourceWithKey(clientIdlePlaySources, collection)) {
                return;
            }
            MusicCollectionCard view = idlePlaySourceCardMap.remove(CardKey.of(true, collection));
            if (view != null) {
                clientIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private FlexWrapLayout serverIdlePlaySourceCardsList;
    private final Consumer<IdlePlaySource> serverRemoveListener = idlePlaySource -> {
        MuiModApi.postToUiThread(() -> {
            if (hasSourceWithKey(serverIdlePlaySources, idlePlaySource)) {
                return;
            }
            MusicCollectionCard view = idlePlaySourceCardMap.remove(CardKey.of(false, idlePlaySource));
            if (view != null) {
                serverIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };
    private Consumer<QueueItem> musicQueuePushListener;
    private BiConsumer<Integer, QueueItem> musicQueueRemoveListener;
    private Unregister localAddRegister;
    private Unregister localRemoveRegister;
    private Unregister serverAddRegister;
    private Unregister serverRemoveRegister;
    private LocalPlayer localPlayer = Minecraft.getInstance().player;
    private final Consumer<IdlePlaySource> serverAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            CardKey key = CardKey.of(false, collection);
            if ((localPlayer != null && collection.getPusherInfo().getPlayerUUID() != localPlayer.getUUID())
                    && !idlePlaySourceCardMap.containsKey(key)) {
                addIdlePlaySourceTo(collection, serverIdlePlaySourceCardsList, false);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };

    private static boolean hasSourceWithKey(Set<IdlePlaySource> sources, IdlePlaySource idlePlaySource) {
        return sources.stream().anyMatch(c -> c.getId() == idlePlaySource.getId() && c.getType() == idlePlaySource.getType());
    }

    public HomeView(Context context) {
        super(context);
        refresh();
    }

    public void refresh() {
        instance = this;
        if (musicQueuePushListener != null) {
            musicService.getMusicQueuePushListeners().remove(musicQueuePushListener);
        }
        if (musicQueueRemoveListener != null) {
            musicService.getMusicQueueRemoveListeners().remove(musicQueueRemoveListener);
        }
        Context context = getContext();
        removeAllViews();
        idlePlaySourceCardMap.clear();

        boolean enabled = clientConfig.getEnable();
        if (ConnectionStateMachine.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        setOrientation(HORIZONTAL);
        {
            LinearLayout lyricsView = new LinearLayout(context);
            lyricsView.setOrientation(VERTICAL);
            LayoutParams lyricsViewParams = new LayoutParams(0, MATCH_PARENT, 3);
            addView(lyricsView, lyricsViewParams);

            staggeredLyricScrollView = new StaggeredLyricScrollView(context);
            lyricsView.addView(staggeredLyricScrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        {
            LinearLayout queueView = new LinearLayout(context);
            queueView.setOrientation(VERTICAL);
            LayoutParams queueViewParams = new LayoutParams(0, MATCH_PARENT, 2);
            addView(queueView, queueViewParams);

            var scrollView = new ScrollView(context);
            scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            scrollView.setFillViewport(true);
            queueView.addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout scrollViewContainer = new LinearLayout(context);
            scrollViewContainer.setOrientation(VERTICAL);
            scrollView.addView(scrollViewContainer, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
            LayoutTransition transition1 = new LayoutTransition();
            transition1.setAnimateParentHierarchy(false);
            transition1.enableTransitionType(LayoutTransition.CHANGING);
            scrollViewContainer.setLayoutTransition(transition1);

            nextToPlayTitle = new TextView(context);
            nextToPlayTitle.setVisibility(GONE);
            nextToPlayTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nextToPlayTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.nextToPlay"));
            LayoutParams nextToPlayTitleParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            nextToPlayTitleParams.setMargins(0, dp(32), 0, dp(16));
            scrollViewContainer.addView(nextToPlayTitle, nextToPlayTitleParams);

            nextToPlayItem = new MusicTrackItem(context);
            nextToPlayItem.setVisibility(GONE);
            scrollViewContainer.addView(nextToPlayItem, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            queueTitle = new TextView(context);
            queueTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            queueTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.playQueue"));
            LayoutParams queueTitleParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            queueTitleParams.setMargins(0, dp(32), 0, dp(16));
            scrollViewContainer.addView(queueTitle, queueTitleParams);

            playQueueListView = new LinearLayout(context);
            playQueueListView.setOrientation(VERTICAL);
            LayoutParams queueViewParams1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            playQueueListView.setMinimumHeight(dp(256));
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            playQueueListView.setLayoutTransition(transition);
            scrollViewContainer.addView(playQueueListView, queueViewParams1);

            clientIdlePlaySourceView = new LinearLayout(context);
            clientIdlePlaySourceView.setVisibility(GONE);
            clientIdlePlaySourceView.setOrientation(VERTICAL);
            clientIdlePlaySourceView.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            TextView clientIdlePlaySourceViewTitle = new TextView(context);
            clientIdlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            clientIdlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            clientIdlePlaySourceViewTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.idlePlaySources"));
            LayoutParams params2 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params2.setMargins(0, dp(32), 0, 0);
            clientIdlePlaySourceView.addView(clientIdlePlaySourceViewTitle, params2);

            TextView idlePlaySourceViewDescription = new TextView(context);
            idlePlaySourceViewDescription.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            idlePlaySourceViewDescription.setTextSize(Theme.TEXT_SIZE_NORMAL);
            idlePlaySourceViewDescription.setText(I18n.get(MusicHud.MOD_ID + ".text.idlePlaySourcesDescription"));
            clientIdlePlaySourceView.addView(idlePlaySourceViewDescription, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            clientIdlePlaySourceCardsList = new FlexWrapLayout(context);
            LayoutParams params4 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params4.setMargins(0, dp(16), 0, 0);
            clientIdlePlaySourceView.addView(clientIdlePlaySourceCardsList, params4);
            scrollViewContainer.addView(clientIdlePlaySourceView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));


            serverIdlePlaySourceView = new LinearLayout(context);
            serverIdlePlaySourceView.setVisibility(GONE);
            serverIdlePlaySourceView.setOrientation(VERTICAL);
            serverIdlePlaySourceView.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            TextView serverIdlePlaySourceViewTitle = new TextView(context);
            serverIdlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            serverIdlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            serverIdlePlaySourceViewTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.othersIdlePlaySources"));
            LayoutParams params5 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params5.setMargins(0, dp(32), 0, 0);
            serverIdlePlaySourceView.addView(serverIdlePlaySourceViewTitle, params5);

            TextView idlePlaySourceViewDescription1 = new TextView(context);
            idlePlaySourceViewDescription1.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            idlePlaySourceViewDescription1.setTextSize(Theme.TEXT_SIZE_NORMAL);
            idlePlaySourceViewDescription1.setText(I18n.get(MusicHud.MOD_ID + ".text.idlePlaySourcesDescription"));
            serverIdlePlaySourceView.addView(idlePlaySourceViewDescription1, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            serverIdlePlaySourceCardsList = new FlexWrapLayout(context);
            LayoutParams params6 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params6.setMargins(0, dp(16), 0, 0);
            serverIdlePlaySourceView.addView(serverIdlePlaySourceCardsList, params6);
            scrollViewContainer.addView(serverIdlePlaySourceView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            localPlayer = Minecraft.getInstance().player;

            clientIdlePlaySources.forEach(idlePlaySource -> {
                if (!idlePlaySourceCardMap.containsKey(CardKey.of(true, idlePlaySource))) {
                    addIdlePlaySourceTo(idlePlaySource, clientIdlePlaySourceCardsList, true);
                }
            });
            serverIdlePlaySources.forEach(idlePlaySource -> {
                if (localPlayer != null && !idlePlaySource.getPusherInfo().getPlayerUUID().equals(localPlayer.getUUID()) && !idlePlaySourceCardMap.containsKey(CardKey.of(false, idlePlaySource))) {
                    addIdlePlaySourceTo(idlePlaySource, serverIdlePlaySourceCardsList, false);
                }
            });
            checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            checkQueue(musicService.getMusicQueue());

            Queue<QueueItem> queue = musicService.getMusicQueue();

            localAddRegister = musicService.getIdlePlaySourceState().local().onAdd(localAddListener);
            localRemoveRegister = musicService.getIdlePlaySourceState().local().onRemove(localRemoveListener);
            serverAddRegister = musicService.getIdlePlaySourceState().external().onAdd(serverAddListener);
            serverRemoveRegister = musicService.getIdlePlaySourceState().external().onRemove(serverRemoveListener);

            playQueueListView.removeAllViews();

            // Snapshot before iterating: the client queue is a plain ArrayDeque mutated on
            // network threads and its iterator is fail-fast
            for (QueueItem item : List.copyOf(queue)) {
                addMusicQueueItem(item, playQueueListView);
            }

            musicQueuePushListener = item -> {
                MuiModApi.postToUiThread(() -> {
                    addMusicQueueItem(item, playQueueListView);
                    checkQueue(queue);
                });
            };
            musicQueueRemoveListener = (removeIndex, item) -> {
                MuiModApi.postToUiThread(() -> {
                    if (removeIndex >= 0 && removeIndex < playQueueListView.getChildCount()) {
                        playQueueListView.removeViewAt(removeIndex);
                    }
                    checkQueue(queue);
                });
            };
            musicService.getMusicQueuePushListeners().add(musicQueuePushListener);
            musicService.getMusicQueueRemoveListeners().add(musicQueueRemoveListener);

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (localAddRegister != null) localAddRegister.unregister();
                    if (localRemoveRegister != null) localRemoveRegister.unregister();
                    if (serverAddRegister != null) serverAddRegister.unregister();
                    if (serverRemoveRegister != null) serverRemoveRegister.unregister();
                    musicService.getMusicQueuePushListeners().remove(musicQueuePushListener);
                    musicService.getMusicQueueRemoveListeners().remove(musicQueueRemoveListener);
                    instance = null;
                }
            });
        }
    }

    private void addIdlePlaySourceTo(IdlePlaySource idlePlaySource, FlexWrapLayout targetView, boolean local) {
        CardKey key = CardKey.of(local, idlePlaySource);
        MusicCollection musicCollection = idlePlaySource.getMusicCollection();
        if (musicCollection != null) {
            addInternal(idlePlaySource, musicCollection, targetView, key);
        } else {
            // View construction and addView must happen on the UI thread; the future may
            // complete on a network virtual thread (observed: GetPlaylistDetailResponse)
            musicService.loadMusicCollectionDetail(idlePlaySource.getId(), idlePlaySource.getType()).thenAccept(collection ->
                    MuiModApi.postToUiThread(() -> addInternal(idlePlaySource, collection, targetView, key)));
        }
    }

    private void addInternal(IdlePlaySource idlePlaySource, MusicCollection musicCollection, FlexWrapLayout targetView, CardKey key) {
        MusicCollectionCard child = new MusicCollectionCard(getContext(), musicCollection, idlePlaySource.getPusherInfo());
        targetView.addView(child);
        idlePlaySourceCardMap.put(key, child);
    }

    private void checkQueue(Queue<QueueItem> queue) {
        if (queue.isEmpty()) {
            queueTitle.setVisibility(View.GONE);
            playQueueListView.setVisibility(View.GONE);
            checkNextToPlay(NowPlayingInfo.getInstance().getNextToPlayIdleMusic());
        } else {
            queueTitle.setVisibility(View.VISIBLE);
            playQueueListView.setVisibility(View.VISIBLE);
            QueueItem peek = queue.peek();
            checkNextToPlay(peek == null ? Traceable.of(MusicDetail.NONE) : peek.musicDetail());
        }
    }

    private void checkIdlePlaySources(Set<IdlePlaySource> idlePlaySources, View targetView) {
        if (idlePlaySources.isEmpty()) {
            targetView.setVisibility(View.GONE);
        } else {
            targetView.setVisibility(View.VISIBLE);
        }
        checkQueue(MusicService.getInstance().getMusicQueue());
    }

    private void checkNextToPlay(Traceable<MusicDetail> nextIdle) {
        MusicService musicService = MusicService.getInstance();
        Queue<QueueItem> musicQueue = musicService.getMusicQueue();
        boolean hasIdlePlaySources = !musicService.getIdlePlaySourceState().local().getSources().isEmpty() || !musicService.getIdlePlaySourceState().external().getSources().isEmpty();
        MusicDetail next = hasIdlePlaySources && nextIdle != null ? nextIdle.value() : null;
        if (musicQueue.isEmpty() && next != null && !next.equals(MusicDetail.NONE)) {
            nextToPlayTitle.setVisibility(VISIBLE);
            nextToPlayItem.setVisibility(VISIBLE);
            nextToPlayItem.bindData(nextIdle);
        } else {
            nextToPlayTitle.setVisibility(GONE);
            nextToPlayItem.setVisibility(GONE);
        }
    }

    private void addMusicQueueItem(QueueItem item, LinearLayout playQueueView) {
        Traceable<MusicDetail> musicTrace = item.musicDetail();
        MusicDetail musicDetail = musicTrace.value();
        var musicListItem = new MusicTrackItem(getContext());
        musicListItem.bindData(musicTrace);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dp(16));

        assert Minecraft.getInstance().player != null;
        if (musicDetail.getPusherInfo().getPlayerUUID().equals(Minecraft.getInstance().player.getUUID())) {
            ImageButton removeButton = new ImageButton(getContext());
            Image removeIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/trash_2.png");
            removeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            removeButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), removeIcon, dp(16), dp(16)));
            InsetBackgroundFactory.builder()
                    .inset(dp(2))
                    .cornerRadius(dp(4))
                    .build()
                    .applyBackgroundTo(removeButton);
            removeButton.setOnClickListener(v -> {
                MusicService.getInstance().sendRemoveMusicFromQueue(item);
            });
            musicListItem.getButtonsLayout().addView(removeButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));
        }
        musicListItem.setLayoutParams(layoutParams);
        playQueueView.addView(musicListItem, layoutParams);
    }

    public void switchMusic(Traceable<MusicDetail> musicDetail, Traceable<MusicDetail> next, Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            if (staggeredLyricScrollView != null) {
                staggeredLyricScrollView.switchLyrics(musicDetail == null ? MusicDetail.NONE : musicDetail.value(), lyricLines);
                checkNextToPlay(next);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 组件内部已处理资源清理，只需将 instance 置空
        instance = null;
    }
}