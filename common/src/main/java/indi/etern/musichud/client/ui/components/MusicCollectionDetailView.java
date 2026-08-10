package indi.etern.musichud.client.ui.components;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
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
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionDetailView extends LinearLayout {
    private static final MusicService musicService = MusicService.getInstance();
    private final ProgressBar progressBar;
    private final VirtualizedListLayout virtualList;
    private final UrlImageView imageView;
    private final AtomicBoolean syncPending = new AtomicBoolean();
    private TextView musicTrackCountView;
    private MusicCollection musicCollection;
    private Unregister tracksSyncUnregister = null;
    private Unregister updateNotifierUnregister = null;
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
        InsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new InsetBackgroundFactory.Padding(dp(16), 0, dp(16), 0))
                .build()
                .applyBackgroundTo(backButton);
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

        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        int dp28 = dp(28);
        {
            ImageButton refreshButton = new ImageButton(context);
            backgroundFactory.applyBackgroundTo(refreshButton);
            refreshButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
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
            backgroundFactory.applyBackgroundTo(toggleSubscribeButton);
            row1.addView(toggleSubscribeButton, new LayoutParams(dp28, dp28, 0));
            if (musicCollection instanceof Playlist playlist) {
                Profile current = Profile.getCurrent();
                if (current == null || current.equals(Profile.ANONYMOUS) || playlist.getCreator().getUserId() == current.getUserId()) {
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
        backgroundFactory.applyBackgroundTo(toggleIdleSourceButton);
        toggleIdleSourceButton.bindState(musicService.getIdlePlaySourceState().local().collection(musicCollection));
        row1.addView(toggleIdleSourceButton, new LayoutParams(dp28, dp28, 0));

        LayoutParams topBarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.setMargins(0, dp(24), 0, 0);
        addView(topBar, topBarParams);

        progressBar = new ProgressBar(context);
        LayoutParams progressParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), 0, 0);
        addView(progressBar, progressParams);

        virtualList = new VirtualizedListLayout(context);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        LayoutParams tracksParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        tracksParams.setMargins(0, dp(24), 0, 0);
        addView(scrollView, tracksParams);
        scrollView.addView(virtualList, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                virtualList.updateWindow(scrollY, v.getHeight()));
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height > 0) {
                virtualList.updateWindow(v.getScrollY(), height);
            }
        });
        scrollView.post(() -> virtualList.updateWindow(0, scrollView.getHeight()));

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
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && changedView == this) {
            syncTracksView();
        }
    }

    private void onCollectionUpdateNotified(boolean operateByRemoteSelf) {
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
                if (!operateByRemoteSelf) {
                    if (!isAttachedToWindow()) return;
                    if (latest != musicCollection) {
                        musicCollection = latest;
                    }
                    unregisterTracksSync();
                    registerTracksSync(latest);
                    syncTracksView();
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
                            } else if (musicCollection1 instanceof Playlist playlist) {
                                updatePlaylistTrackCountView(playlist);
                            }
                        }
                        progressBar.setVisibility(View.GONE);
                        virtualList.resetItems(musicDetails instanceof ObservableSequencedSet<MusicDetail> observable
                                ? observable.snapshot()
                                : new ArrayList<>(musicDetails));
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
        tracksSyncUnregister = tracks.registerOnChange(this::scheduleTracksSync);
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
        virtualList.syncItems(tracks.snapshot());
    }

    private String mappedAlbumType(String type) {
        return switch (type) {
            case "专辑" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.album");
            case "EP" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.ep");
            case "Single" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.single");
            case "精选集" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.compilation");
            default -> type;
        };
    }
}
