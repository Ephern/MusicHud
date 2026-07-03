package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.AutoFlowGridLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.MusicListItem;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class HomeView extends LinearLayout {
    private static final MusicService musicService = MusicService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    @Getter
    private static HomeView instance;
    private final Set<MusicCollection> serverIdlePlaySources = musicService.getServerIdlePlaySources();
    private final Set<MusicCollection> clientIdlePlaySources = musicService.getLocalIdlePlaySources();
    private final Map<MusicCollection, MusicCollectionCard> idlePlaySourceCardMap = new ConcurrentHashMap<>();
    @Getter
    private StaggeredLyricScrollView staggeredLyricScrollView;
    private MusicListItem nextToPlayItem;
    private TextView nextToPlayTitle;
    private TextView queueTitle;
    private LinearLayout playQueueListView;
    private LinearLayout clientIdlePlaySourceView;
    private LinearLayout serverIdlePlaySourceView;
    private AutoFlowGridLayout clientIdlePlaySourceCardsList;
    private final Consumer<MusicCollection> localAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            if (!idlePlaySourceCardMap.containsKey(collection)) {
                addIdlePlaySourceTo(collection, getContext(), clientIdlePlaySourceCardsList);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private final Consumer<MusicCollection> localRemoveListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            MusicCollectionCard view = idlePlaySourceCardMap.remove(collection);
            if (view != null) {
                clientIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            }
        });
    };
    private AutoFlowGridLayout serverIdlePlaySourceCardsList;
    private final Consumer<MusicCollection> serverRemoveListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            MusicCollectionCard view = idlePlaySourceCardMap.remove(collection);
            if (view != null) {
                serverIdlePlaySourceCardsList.removeView(view);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };
    private LocalPlayer localPlayer = Minecraft.getInstance().player;
    private final Consumer<MusicCollection> serverAddListener = collection -> {
        MuiModApi.postToUiThread(() -> {
            if ((localPlayer != null && collection.getPusherInfo().getPlayerUUID() != localPlayer.getUUID())
                    && !idlePlaySourceCardMap.containsKey(collection)) {
                addIdlePlaySourceTo(collection, getContext(), serverIdlePlaySourceCardsList);
                checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            }
        });
    };

    public HomeView(Context context) {
        super(context);
        refresh();
    }

    public void refresh() {
        instance = this;
        Context context = getContext();
        removeAllViews();
        idlePlaySourceCardMap.clear();

        boolean enabled = clientConfig.getEnable();
        if (MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
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
            transition1.enableTransitionType(LayoutTransition.CHANGING);
            scrollViewContainer.setLayoutTransition(transition1);

            nextToPlayTitle = new TextView(context);
            nextToPlayTitle.setVisibility(GONE);
            nextToPlayTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nextToPlayTitle.setText(I18n.get(MusicHud.MOD_ID + ".text.nextToPlay"));
            LayoutParams nextToPlayTitleParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            nextToPlayTitleParams.setMargins(0, dp(32), 0, dp(16));
            scrollViewContainer.addView(nextToPlayTitle, nextToPlayTitleParams);

            nextToPlayItem = new MusicListItem(context);
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

            clientIdlePlaySourceCardsList = new AutoFlowGridLayout(context);
            clientIdlePlaySourceCardsList.setRowMinWidth(dp(143));
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

            serverIdlePlaySourceCardsList = new AutoFlowGridLayout(context);
            serverIdlePlaySourceCardsList.setRowMinWidth(dp(143));
            LayoutParams params6 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params6.setMargins(0, dp(16), 0, 0);
            serverIdlePlaySourceView.addView(serverIdlePlaySourceCardsList, params6);
            scrollViewContainer.addView(serverIdlePlaySourceView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            localPlayer = Minecraft.getInstance().player;

            clientIdlePlaySources.forEach(collection -> {
                if (!idlePlaySourceCardMap.containsKey(collection)) {
                    MusicCollectionCard child = new MusicCollectionCard(context, collection);
                    clientIdlePlaySourceCardsList.addView(child);
                    idlePlaySourceCardMap.put(collection, child);
                }
            });
            serverIdlePlaySources.forEach(collection -> {
                if (localPlayer != null && !collection.getPusherInfo().getPlayerUUID().equals(localPlayer.getUUID()) && !idlePlaySourceCardMap.containsKey(collection)) {
                    MusicCollectionCard child = new MusicCollectionCard(context, collection);
                    serverIdlePlaySourceCardsList.addView(child);
                    idlePlaySourceCardMap.put(collection, child);
                }
            });
            checkIdlePlaySources(clientIdlePlaySources, clientIdlePlaySourceView);
            checkIdlePlaySources(serverIdlePlaySources, serverIdlePlaySourceView);
            checkQueue(musicService.getMusicQueue());

            Queue<MusicDetail> queue = musicService.getMusicQueue();

            musicService.getLocalIdlePlaySourceAddListeners().add(localAddListener);
            musicService.getLocalIdlePlaySourceRemoveListeners().add(localRemoveListener);
            musicService.getServerIdlePlaySourceAddListeners().add(serverAddListener);
            musicService.getServerIdlePlaySourceRemoveListeners().add(serverRemoveListener);

            playQueueListView.removeAllViews();

            for (MusicDetail musicDetail : queue) {
                addMusicQueueItem(musicDetail, playQueueListView);
            }

            Consumer<MusicDetail> musicQueuePushListener = musicDetail -> {
                MuiModApi.postToUiThread(() -> {
                    addMusicQueueItem(musicDetail, playQueueListView);
                    checkQueue(queue);
                });
            };
            BiConsumer<Integer, MusicDetail> musicQueueRemoveListener = (removeIndex, musicDetail) -> {
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
                    musicService.getLocalIdlePlaySourceAddListeners().remove(localAddListener);
                    musicService.getLocalIdlePlaySourceRemoveListeners().remove(localRemoveListener);
                    musicService.getServerIdlePlaySourceAddListeners().remove(serverAddListener);
                    musicService.getServerIdlePlaySourceRemoveListeners().remove(serverRemoveListener);
                    musicService.getMusicQueuePushListeners().remove(musicQueuePushListener);
                    musicService.getMusicQueueRemoveListeners().remove(musicQueueRemoveListener);
                    instance = null;
                }
            });
        }
    }

    private void addIdlePlaySourceTo(MusicCollection idlePlaySource, Context context, AutoFlowGridLayout targetView) {
        MusicCollectionCard child = new MusicCollectionCard(context, idlePlaySource);
        targetView.addView(child);
        idlePlaySourceCardMap.put(idlePlaySource, child);
    }

    private void checkQueue(Queue<MusicDetail> queue) {
        if (queue.isEmpty()) {
            queueTitle.setVisibility(View.GONE);
            playQueueListView.setVisibility(View.GONE);
            checkNextToPlay(NowPlayingInfo.getInstance().getNextToPlayIdleMusicDetail());
        } else {
            queueTitle.setVisibility(View.VISIBLE);
            playQueueListView.setVisibility(View.VISIBLE);
            checkNextToPlay(queue.peek());
        }
    }

    private void checkIdlePlaySources(Set<MusicCollection> idlePlaySources, View targetView) {
        if (idlePlaySources.isEmpty()) {
            targetView.setVisibility(View.GONE);
        } else {
            targetView.setVisibility(View.VISIBLE);
        }
        checkQueue(MusicService.getInstance().getMusicQueue());
    }

    private void checkNextToPlay(MusicDetail nextIdle) {
        MusicService musicService = MusicService.getInstance();
        Queue<MusicDetail> musicQueue = musicService.getMusicQueue();
        boolean hasIdlePlaySources = !musicService.getLocalIdlePlaySources().isEmpty() || !musicService.getServerIdlePlaySources().isEmpty();
        MusicDetail next = hasIdlePlaySources ? nextIdle : null;
        if (musicQueue.isEmpty() && next != null && !next.equals(MusicDetail.NONE)) {
            nextToPlayTitle.setVisibility(VISIBLE);
            nextToPlayItem.setVisibility(VISIBLE);
            nextToPlayItem.bindData(next);
        } else {
            nextToPlayTitle.setVisibility(GONE);
            nextToPlayItem.setVisibility(GONE);
        }
    }

    private void addMusicQueueItem(MusicDetail musicDetail, LinearLayout playQueueView) {
        var musicListItem = new MusicListItem(getContext());
        musicListItem.bindData(musicDetail);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dp(16));
        LinearLayout actions = new LinearLayout(getContext());

        assert Minecraft.getInstance().player != null;
        if (musicDetail.getPusherInfo().getPlayerUUID().equals(Minecraft.getInstance().player.getUUID())) {
            Button removeButton = new Button(getContext());
            removeButton.setText(I18n.get(MusicHud.MOD_ID + ".button.remove"));
            removeButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            removeButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            Drawable background = ButtonInsetBackgroundFactory.builder()
                    .inset(1)
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(8), dp(2), dp(2), dp(8)))
                    .cornerRadius(dp(4))
                    .build().newBackgroundDrawable();
            removeButton.setBackground(background);
            removeButton.setOnClickListener(v -> {
                MusicService.getInstance().sendRemoveMusicFromQueue(playQueueView.indexOfChild(musicListItem), musicDetail);
            });
            actions.addView(removeButton, new LayoutParams(WRAP_CONTENT, dp(MusicListItem.imageSize)));
        }
        musicListItem.addView(actions);
        musicListItem.setLayoutParams(layoutParams);
        playQueueView.addView(musicListItem, layoutParams);
    }

    public void switchMusic(MusicDetail musicDetail, MusicDetail next, Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            if (staggeredLyricScrollView != null) {
                staggeredLyricScrollView.switchLyrics(musicDetail, lyricLines);
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