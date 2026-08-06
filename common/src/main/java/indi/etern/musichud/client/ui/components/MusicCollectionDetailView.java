package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.impl.ncm.CommonCaches;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionDetailView extends LinearLayout {
    private static final MusicService musicService = MusicService.getInstance();
    private final ProgressBar progressBar;
    private final LinearLayout tracksListView;
    private final ScrollView scrollView;
    private final UrlImageView imageView;
    private final LayoutTransition tracksListViewTransition;
    private TextView musicTrackCountView;
    private MusicCollection musicCollection;
    private Unregister tracksSyncUnregister = null;
    private Unregister updateNotifierUnregister = null;
    private final AtomicBoolean syncPending = new AtomicBoolean();
    private String currentCoverUrl = null;
    private long collectionId = -1;
    private boolean albumCollection = false;

    public MusicCollectionDetailView(Context context, MusicCollection musicCollection) {
        super(context);

        this.musicCollection = musicCollection;
        setOrientation(VERTICAL);
        String collectionNameI18n = musicCollection.getNameI18nKey();

        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(params);

        ImageButton backButton = new ImageButton(context);
        String tooltipText = I18n.get(MusicHud.MOD_ID + ".button.back");
        Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/arrow_left.png");
        if (image != null) {
            backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), image, dp(16), dp(16)));
        }
        backButton.setTooltipText(tooltipText);
        backButton.setOnClickListener(view -> {
            RouterContainer.getInstance().popNavigate();
            backButton.setOnClickListener(null);
        });
        Drawable drawable = ButtonInsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(16), 0, dp(16), 0))
                .build().newBackgroundDrawable();
        backButton.setBackground(drawable);
        LayoutParams backButtonParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        backButtonParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, backButtonParams);

        imageView = new UrlImageView(context);
        LayoutParams imageParams = new LayoutParams(dp(72), dp(72));
        topBar.addView(imageView, imageParams);
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp(72)));
        imageView.setCornerRadius(dp(8));

        LinearLayout briefInfo = new LinearLayout(context);
        briefInfo.setGravity(Gravity.CENTER_VERTICAL);
        briefInfo.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(briefInfo, params1);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setBaselineAligned(false);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams row1Params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Params.setMargins(0, 0, 0, dp(2));
        briefInfo.addView(row1, row1Params);

        TextView typeText = new TextView(context);
        typeText.setTextSize(Theme.TEXT_SIZE_LARGE);
        typeText.setTextColor(Theme.NORMAL_TEXT_COLOR);
        typeText.setText(I18n.get(collectionNameI18n));
        LayoutParams typeParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeParams.setMargins(0, 0, dp(16), 0);
        row1.addView(typeText, typeParams);

        if (musicCollection instanceof Playlist playlist) {
            {
                musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_LARGE);
                updatePlaylistTrackCountView(playlist);
                LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params3.setMargins(0, 0, dp(12), 0);
                row1.addView(musicTrackCountView, params3);
            }
            {
                TextView playedCountView = new TextView(context);
                playedCountView.setTextSize(Theme.TEXT_SIZE_LARGE);
                SpannableString text = new SpannableString("  " + playlist.getPlayedCount());
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/audio_lines.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                playedCountView.setText(text);
                row1.addView(playedCountView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        } else if (musicCollection instanceof Album album) {
            {
                musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_LARGE);
                updateAlbumTrackCountView(album);
                LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params3.setMargins(0, 0, dp(12), 0);
                row1.addView(musicTrackCountView, params3);
            }
            String type = album.getType();
            if (!type.isBlank()) {
                TextView albumTypeText = new TextView(context);
                albumTypeText.setTextSize(Theme.TEXT_SIZE_LARGE);
                SpannableString text = new SpannableString("  " + mappedAlbumType(type));
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/layout_grid.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                albumTypeText.setText(text);
                row1.addView(albumTypeText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        }

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(musicCollection.getName());
        briefInfo.addView(name);

        briefInfo.addView(new View(context), new LayoutParams(MATCH_PARENT, dp(8), 0));

        row1.addView(new View(context), new LayoutParams(dp(16), MATCH_PARENT, 0));

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        int dp28 = dp(28);
        {
            ImageButton refreshButton = new ImageButton(context);
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            refreshButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            refreshButton.setOnClickListener((v) -> {
                refreshData(true);
            });
            row1.addView(refreshButton, new LayoutParams(dp28, dp28));
        }
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            toggleSubscribeButton.setBackground(backgroundFactory.newBackgroundDrawable());
            row1.addView(toggleSubscribeButton, new LayoutParams(dp28, dp28, 0));
            if (musicCollection instanceof Playlist playlist) {
                if (playlist.getCreator().getUserId() == Profile.getCurrent().getUserId()) {
                    toggleSubscribeButton.setVisibility(GONE);
                } else {
                    var subscribeState = musicService.getPlaylistSubscribeState(playlist);
                    toggleSubscribeButton.bindState(subscribeState);
                }
            } else if (musicCollection instanceof Album album) {
                var subscribeState = musicService.getAlbumSubscribeState(album);
                toggleSubscribeButton.bindState(subscribeState);
            }
        }
        ToggleIdlePlaySourceButton toggleIdleSourceButton = new ToggleIdlePlaySourceButton(context);
        toggleIdleSourceButton.setBackground(backgroundFactory.newBackgroundDrawable());
        toggleIdleSourceButton.bindState(musicService.getIdlePlaySourceState().local().collection(musicCollection));
        row1.addView(toggleIdleSourceButton, new LayoutParams(dp28, dp28, 0));

        LayoutParams topBarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.setMargins(0, dp(24), 0, 0);
        addView(topBar, topBarParams);

        progressBar = new ProgressBar(context);
        LayoutParams progressParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), 0, 0);
        addView(progressBar, progressParams);

        scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setFillViewport(true);
        LayoutParams tracksParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        tracksParams.setMargins(0, dp(24), 0, 0);
        addView(scrollView, tracksParams);

        tracksListView = new LinearLayout(context);
        tracksListView.setOrientation(VERTICAL);
        scrollView.addView(tracksListView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (musicCollection instanceof Playlist playlist) {
            collectionId = playlist.getId();
            albumCollection = false;
            updateNotifierUnregister = CollectionUpdateNotifier.registerPlaylist(collectionId, this::onCollectionUpdateNotified);
        } else if (musicCollection instanceof Album album) {
            collectionId = album.getId();
            albumCollection = true;
            updateNotifierUnregister = CollectionUpdateNotifier.registerAlbum(collectionId, this::onCollectionUpdateNotified);
        }

        refreshData(false);
        tracksListViewTransition = new LayoutTransition();
        tracksListViewTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
        tracksListViewTransition.disableTransitionType(LayoutTransition.APPEARING);
        tracksListViewTransition.enableTransitionType(LayoutTransition.CHANGING);
    }

    private void onCollectionUpdateNotified() {
        // Path 1: list sync from cache (instant, no network round-trip, never
        // overrides the local optimistic state with an in-flight server state)
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) return;
            MusicCollection latest = albumCollection
                    ? CommonCaches.albumsCache.getIfPresent(collectionId)
                    : CommonCaches.playlistsCache.getIfPresent(collectionId);
            if (latest != null && latest != musicCollection) {
                musicCollection = latest;
                unregisterTracksSync();
                registerTracksSync(latest);
            }
            syncTracksView();
        });
        // Path 2: cover refresh from network (latest URL only)
        CompletableFuture<? extends MusicCollection> future;
        if (albumCollection) {
            future = musicService.loadAlbumDetail(collectionId, true);
        } else {
            future = musicService.loadPlaylistDetail(collectionId, true);
        }
        future.whenComplete((latest, throwable) -> {
            if (throwable != null || latest == null) return;
            MuiModApi.postToUiThread(() -> {
                if (!isAttachedToWindow()) return;
                String newCoverUrl = latest.getImageThumbnailUrl(dp(72));
                if (!Objects.equals(currentCoverUrl, newCoverUrl)) {
                    currentCoverUrl = newCoverUrl;
                    imageView.loadUrl(newCoverUrl);
                }
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterTracksSync();
        if (updateNotifierUnregister != null) {
            updateNotifierUnregister.unregister();
            updateNotifierUnregister = null;
        }
    }

    private void updatePlaylistTrackCountView(Playlist playlist) {
        SpannableString text = new SpannableString("  " + playlist.getMusicTrackCount());
        Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/list_music.png");
        if (icon != null) {
            ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
            text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        musicTrackCountView.setText(text);
    }

    private void updateAlbumTrackCountView(Album album) {
        int musicTrackCount = Math.max(album.getMusicTrackCount(), album.getMusicDetails().size());
        if (musicTrackCount <= 0) {
            musicTrackCountView.setVisibility(GONE);
            return;
        }
        musicTrackCountView.setVisibility(VISIBLE);
        SpannableString text = new SpannableString("  " + musicTrackCount);
        Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/disc_album.png");
        if (icon != null) {
            ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
            text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        musicTrackCountView.setText(text);
    }

    private void refreshData(boolean ignoreCache) {
        Context context = getContext();
        tracksListView.setLayoutTransition(null);
        tracksListView.removeAllViews();
        syncPending.set(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        MusicService.getInstance().loadMoreMusicOfCollection(musicCollection, ignoreCache)
                .thenAcceptAsync(result -> {
            MuiModApi.postToUiThread(() -> {
                Collection<MusicDetail> musicDetails = result.musicDetails();
                MusicCollection musicCollection1 = result.musicCollection();
                this.musicCollection = musicCollection1;
                currentCoverUrl = musicCollection1.getImageThumbnailUrl(dp(72));
                this.imageView.loadUrl(currentCoverUrl);
                if (musicTrackCountView != null) {
                    if (musicCollection1 instanceof Album album) {
                        updateAlbumTrackCountView(album);
                    } else if (musicCollection1 instanceof Playlist playlist){
                        updatePlaylistTrackCountView(playlist);
                    }
                }
                progressBar.setVisibility(View.GONE);
                tracksListView.removeAllViews();
                for (MusicDetail musicDetail : musicDetails) {
                    tracksListView.addView(createItem(context, musicDetail));
                }

                tracksListView.setLayoutTransition(tracksListViewTransition);
                unregisterTracksSync();
                registerTracksSync(musicCollection1);
            });
        }, MusicHud.EXECUTOR);
    }

    private void unregisterTracksSync() {
        if (tracksSyncUnregister != null) {
            tracksSyncUnregister.unregister();
            tracksSyncUnregister = null;
        }
    }

    private void registerTracksSync(MusicCollection collection) {
        SequencedSet<MusicDetail> musicDetails = collection.getMusicDetails();
        if (!(musicDetails instanceof ObservableSequencedSet<MusicDetail> tracks)) {
            return;
        }
        Consumer<MusicDetail> onChange = musicDetail -> scheduleTracksSync();
        Unregister addUnregister = tracks.registerOnAdd(onChange);
        Unregister removeUnregister = tracks.registerOnRemove(onChange);
        tracksSyncUnregister = () -> {
            addUnregister.unregister();
            removeUnregister.unregister();
        };
    }

    private void scheduleTracksSync() {
        if (syncPending.compareAndSet(false, true)) {
            MuiModApi.postToUiThread(() -> {
                syncPending.set(false);
                syncTracksView();
            });
        }
    }

    private void syncTracksView() {
        MusicCollection collection = musicCollection;
        if (collection == null) return;
        SequencedSet<MusicDetail> musicDetails = collection.getMusicDetails();
        if (musicDetails instanceof ObservableSequencedSet<MusicDetail> tracks) {
            syncTracksList(tracks);
        }
        String newCoverUrl = collection.getImageThumbnailUrl(dp(72));
        if (!Objects.equals(currentCoverUrl, newCoverUrl)) {
            currentCoverUrl = newCoverUrl;
            imageView.loadUrl(newCoverUrl);
        }
    }

    private void syncTracksList(ObservableSequencedSet<MusicDetail> tracks) {
        List<MusicDetail> ordered = new ArrayList<>(tracks);
        int childCount = tracksListView.getChildCount();
        boolean unchanged = childCount == ordered.size();
        if (unchanged) {
            for (int i = 0; i < childCount; i++) {
                MusicListItem item = (MusicListItem) tracksListView.getChildAt(i);
                if (item.getMusicDetail() != ordered.get(i)) {
                    unchanged = false;
                    break;
                }
            }
        }
        if (unchanged) return;

        List<MusicListItem> current = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) {
            current.add((MusicListItem) tracksListView.getChildAt(i));
        }
        // remove items no longer present in the collection (backwards to keep indexes stable)
        for (int i = current.size() - 1; i >= 0; i--) {
            long id = current.get(i).getMusicDetail().getId();
            boolean exists = ordered.stream().anyMatch(md -> md.getId() == id);
            if (!exists) {
                tracksListView.removeViewAt(i);
                current.remove(i);
            }
        }
        // align remaining items to the collection order: insert missing, move misplaced
        for (int i = 0; i < ordered.size(); i++) {
            MusicDetail md = ordered.get(i);
            int j = -1;
            for (int k = 0; k < current.size(); k++) {
                if (current.get(k).getMusicDetail().getId() == md.getId()) {
                    j = k;
                    break;
                }
            }
            if (j == -1) {
                MusicListItem item = createItem(getContext(), md);
                current.add(i, item);
                tracksListView.addView(item, i);
            } else if (j > i) {
                MusicListItem item = current.remove(j);
                current.add(i, item);
                tracksListView.removeView(item);
                tracksListView.addView(item, i);
            } else {
                MusicListItem item = current.get(i);
                if (item.getMusicDetail() != md) {
                    item.bindData(md);
                }
            }
        }
    }

    private MusicListItem createItem(Context context, MusicDetail musicDetail) {
        var musicLayout = new MusicListItem(context);
        musicLayout.setShowPusherInfo(false);
        musicLayout.bindData(musicDetail);
        var background = ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build().newBackgroundDrawable();
        musicLayout.setBackground(background);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(musicDetail);
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        return musicLayout;
    }

    private String mappedAlbumType(String type) {
        return switch (type) {
            case "专辑" -> I18n.get(MusicHud.MOD_ID +".text.album.type.album");
            case "EP" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.ep");
            case "Single" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.single");
            case "精选集" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.compilation");
            default -> type;
        };
    }
}
