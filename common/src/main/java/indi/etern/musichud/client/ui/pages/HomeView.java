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
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.AutoFlowGridLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.MusicListItem;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.Queue;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class HomeView extends LinearLayout {
    @Getter
    private static HomeView instance;

    private final HashMap<MusicCollection, MusicCollectionCard> idlePlaySourceCardMap = new HashMap<>();
    @Getter
    private StaggeredLyricScrollView staggeredLyricScrollView;

    public HomeView(Context context) {
        super(context);
        refresh();
    }

    public void refresh() {
        instance = this;
        Context context = getContext();
        removeAllViews();
        MusicService musicService = MusicService.getInstance();

        boolean enabled = ClientConfigDefinition.enable.get();
        if (MusicHud.getStatus() != MusicHud.ConnectStatus.CONNECTED || !enabled) {
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        setOrientation(HORIZONTAL);
        {
            // 左侧歌词区域
            LinearLayout lyricsView = new LinearLayout(context);
            lyricsView.setOrientation(VERTICAL);
            LayoutParams lyricsViewParams = new LayoutParams(0, MATCH_PARENT, 3);
            addView(lyricsView, lyricsViewParams);

//            TextView title = new TextView(context);
//            title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
//            title.setText(I18n.get("music_hud.text.lyrics"));
//            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
//            params.setMargins(0, 0, 0, dp(16));
//            lyricsView.addView(title, params);

            // 使用新的歌词组件
            staggeredLyricScrollView = new StaggeredLyricScrollView(context);
            lyricsView.addView(staggeredLyricScrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        {
            // 右侧播放列表区域（保持不变）
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

            TextView title = new TextView(context);
            title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            title.setText(I18n.get("music_hud.text.playlist"));
            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMargins(0, dp(24), 0, dp(32));
            scrollViewContainer.addView(title, params);

            LinearLayout playQueueListView = new LinearLayout(context);
            playQueueListView.setOrientation(VERTICAL);
            LayoutParams queueViewParams1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            queueViewParams1.setMargins(0, 0, 0, dp(48));
            playQueueListView.setMinimumHeight(dp(256));
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            playQueueListView.setLayoutTransition(transition);
            scrollViewContainer.addView(playQueueListView, queueViewParams1);

            LinearLayout idlePlaySourceView = new LinearLayout(context);
            idlePlaySourceView.setOrientation(VERTICAL);
            LayoutParams idlePlaylistViewParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            idlePlaySourceView.setLayoutParams(idlePlaylistViewParams);

            TextView idlePlaySourceViewTitle = new TextView(context);
            idlePlaySourceViewTitle.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            idlePlaySourceViewTitle.setTextSize(Theme.TEXT_SIZE_LARGE);
            idlePlaySourceViewTitle.setText(I18n.get("music_hud.text.idlePlaySources"));
            LayoutParams params2 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            idlePlaySourceView.addView(idlePlaySourceViewTitle, params2);

            TextView idlePlaySourceViewDescription = new TextView(context);
            idlePlaySourceViewDescription.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            idlePlaySourceViewDescription.setTextSize(Theme.TEXT_SIZE_NORMAL);
            idlePlaySourceViewDescription.setText(I18n.get("music_hud.text.idlePlaySourcesDescription"));
            LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            idlePlaySourceView.addView(idlePlaySourceViewDescription, params3);

            AutoFlowGridLayout idlePlaySourceCardsList = new AutoFlowGridLayout(context);
            idlePlaySourceCardsList.setRowMinWidth(dp(143));
            LayoutParams params4 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params4.setMargins(0, dp(16), 0, 0);
            idlePlaySourceView.addView(idlePlaySourceCardsList, params4);

            scrollViewContainer.addView(idlePlaySourceView, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            musicService.getIdlePlaySources().forEach(playlist -> {
                MusicCollectionCard child = new MusicCollectionCard(context, playlist);
                idlePlaySourceCardsList.addView(child);
                idlePlaySourceCardMap.put(playlist, child);
            });

            Consumer<MusicCollection> addListener = collection -> {
                MuiModApi.postToUiThread(() -> {
                    MusicCollectionCard child = new MusicCollectionCard(context, collection);
                    idlePlaySourceCardsList.addView(child);
                    idlePlaySourceCardMap.put(collection, child);
                });
            };
            Consumer<MusicCollection> removeListener = collection -> {
                MuiModApi.postToUiThread(() -> {
                    MusicCollectionCard view = idlePlaySourceCardMap.get(collection);
                    if (view != null) {
                        idlePlaySourceCardsList.removeView(view);
                        idlePlaySourceCardMap.remove(collection);
                    }
                });
            };
            musicService.getIdlePlaySourceAddListeners().add(addListener);
            musicService.getIdlePlaylistRemoveListeners().add(removeListener);

            playQueueListView.removeAllViews();

            Queue<MusicDetail> queue = musicService.getMusicQueue();
            for (MusicDetail musicDetail : queue) {
                addMusicQueueItem(musicDetail, playQueueListView);
            }

            musicService.getMusicQueuePushListeners().add(musicDetail -> {
                MuiModApi.postToUiThread(() -> {
                    addMusicQueueItem(musicDetail, playQueueListView);
                });
            });
            musicService.getMusicQueueRemoveListeners().add((removeIndex, musicDetail) -> {
                MuiModApi.postToUiThread(() -> {
                    if (removeIndex >= 0 && removeIndex < playQueueListView.getChildCount()) {
                        playQueueListView.removeViewAt(removeIndex);
                    }
                });
            });

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    // 组件内部会自行启动更新循环，无需额外操作
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    musicService.getIdlePlaylistRemoveListeners().remove(removeListener);
                    musicService.getIdlePlaySourceAddListeners().remove(addListener);
                    instance = null;
                }
            });
        }
    }

    private void addMusicQueueItem(MusicDetail musicDetail, LinearLayout playQueueView) {
        var musicListItem = new MusicListItem(getContext());
        musicListItem.bindData(musicDetail);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, dp(16));
        LinearLayout actions = new LinearLayout(getContext());

        assert Minecraft.getInstance().player != null;
        if (musicDetail.getPusherInfo().playerUUID().equals(Minecraft.getInstance().player.getUUID())) {
            Button removeButton = new Button(getContext());
            removeButton.setText(I18n.get("music_hud.button.remove"));
            removeButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            removeButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            Drawable background = ButtonInsetBackground.builder()
                    .inset(1)
                    .padding(new ButtonInsetBackground.Padding(dp(8), dp(2), dp(2), dp(8)))
                    .cornerRadius(dp(4))
                    .build().get();
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

    public void switchMusic(Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            if (staggeredLyricScrollView != null) {
                staggeredLyricScrollView.setLyrics(lyricLines);
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