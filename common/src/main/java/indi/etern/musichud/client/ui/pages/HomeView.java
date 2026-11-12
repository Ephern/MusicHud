package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.method.LinkMovementMethod;
import icyllis.modernui.text.style.ClickableSpan;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.PlaybackTask;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.components.buttons.VoteSkipButton;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.layouts.FlexWrapLayout;
import indi.etern.musichud.client.ui.pages.routes.ArtistDetailView;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.shadow.BlurShadowDrawable;
import indi.etern.musichud.client.ui.shadow.BlurShadowSpec;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.LayoutTransitionReflow;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Home page body: the player information column on the left and the large lyric column on the
 * right. {@link #infoColumn} and {@link #staggeredLyricScrollView} are direct children so the
 * layout transition can animate the visible column itself when the lyric column disappears.
 * The play queue and idle sources live in {@link indi.etern.musichud.client.ui.components.SidebarView}.
 */
public class HomeView extends ViewGroup {
    private static final String ICON_SOURCE = "/assets/music_hud/textures/gui/icons/square_arrow_out_up_right.png";
    private static final DateTimeFormatter TIME_WITH_HOUR = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_WITHOUT_HOUR = DateTimeFormatter.ofPattern("mm:ss");

    private static final int INFO_MAX_WIDTH_DP = 520;
    private static final int INFO_MIN_WIDTH_DP = 240;
    private static final int INFO_SAFE_LEFT_DP = 16;
    private static final int INFO_SAFE_RIGHT_DP = 16;
    /** Keeps the info column clear of the floating navigator bar. */
    private static final int INFO_SAFE_TOP_DP = 64;
    private static final int INFO_SAFE_BOTTOM_DP = 16;
    private static final int INFO_LYRICS_GAP_DP = 32;
    private static final int LYRICS_RIGHT_DP = 32;
    private static final int LYRICS_TOP_DP = 64;
    private static final int LYRICS_BOTTOM_DP = 64;
    private static final int INFO_COVER_GAP_DP = 28;
    /** Width and height are coupled through the content height; a few passes settle it. */
    private static final int INFO_FIT_PASSES = 4;
    private static final int BUTTON_SIZE_DP = 36;
    private static final int ACTION_ICON_DP = 16;
    private static final int PUSHER_PADDING_DP = 8;
    /** The action row moves onto its own line above this share of the available width. */
    private static final float BUTTONS_WRAP_RATIO = 0.4f;
    private static final float COVER_MIN_SCALE = 0.8f;
    private static final int COVER_SCALE_DURATION_MS = 350;
    private static final int LAYOUT_TRANSITION_DURATION = 350;
    private static final SpringInterpolator LAYOUT_TRANSITION_INTERPOLATOR =
            new SpringInterpolator((float) LAYOUT_TRANSITION_DURATION / 1000, 1);
    private static final SpringInterpolator COVER_SCALE_INTERPOLATOR =
            new SpringInterpolator((float) COVER_SCALE_DURATION_MS / 1000, 1);

    private final ClientConfig clientConfig = ClientConfig.getInstance();
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    @Getter
    private static HomeView instance;
    @Getter
    private StaggeredLyricScrollView staggeredLyricScrollView;

    private View notificationView;
    private InfoColumn infoColumn;
    private UrlImageView albumImage;
    private FrameLayout coverContainer;
    private BlurShadowDrawable coverShadow;
    private MarqueeTextView name;
    private MarqueeTextView artistsAndAlbum;
    private ImageButton sourceButton;
    private PusherWidget pusherWidget;
    private LinearProgressBar progressBar;
    private TextView played;
    private TextView total;
    private LinearLayout buttonsLayout;
    private ToggleTrackLikeStateButton likeButton;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private VoteSkipButton skipCurrentButton;

    private final AtomicInteger progressUpdaterToken = new AtomicInteger();
    private AnimatorSet coverScaleAnimator;
    private long progressUpdaterStartTime;
    private long progressTextUpdateCount = -1;
    private boolean audioListenerRegistered;
    private final Consumer<StreamAudioPlayer.Status> statusListener =
            status -> MuiModApi.postToUiThread(() -> {
                applyAudioStatus(status);
                updateCoverScale(true);
            });

    private int laidOutInfoX;
    private int laidOutInfoY;
    private int laidOutInfoWidth;
    private int laidOutInfoHeight;
    private int laidOutLyricsX;
    private int laidOutLyricsWidth;

    public HomeView(Context context) {
        super(context);
        refresh();
    }

    public void refresh() {
        instance = this;
        progressUpdaterToken.incrementAndGet();
        cancelCoverScaleAnimator();
        // Drop the old transition before rebuilding, otherwise removed children stay in the
        // disappearing set and the fresh layout is dropped while isChangingLayout() is true.
        setLayoutTransition(null);
        clearDisappearingChildren();
        removeAllViews();
        clearBoundViews();

        Context context = getContext();
        boolean enabled = clientConfig.getEnable();
        if (ConnectionStateMachine.getConnectStatus() != ConnectionStateMachine.ConnectStatus.CONNECTED
                && !clientConfig.getEnableIsolatedMode() || !enabled) {
            notificationView = Theme.getNotificationTextView(context, enabled);
            addView(notificationView, new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            return;
        }

        buildInfoColumn(context);
        staggeredLyricScrollView = new StaggeredLyricScrollView(context);
        staggeredLyricScrollView.setLyricFollowRatio(0.3f);
        staggeredLyricScrollView.setManualScrollFadeOffset(dp(56));
        addView(staggeredLyricScrollView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        applyLyricsVisibility();

        LayoutTransition layoutTransition = new LayoutTransition();
        LayoutTransitionReflow.attach(layoutTransition);
//        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setAnimateParentHierarchy(false);
        layoutTransition.setInterpolator(LayoutTransition.CHANGING, LAYOUT_TRANSITION_INTERPOLATOR);
        layoutTransition.setInterpolator(LayoutTransition.APPEARING, LAYOUT_TRANSITION_INTERPOLATOR);
        layoutTransition.setInterpolator(LayoutTransition.DISAPPEARING, LAYOUT_TRANSITION_INTERPOLATOR);
        layoutTransition.setInterpolator(LayoutTransition.CHANGE_APPEARING, LAYOUT_TRANSITION_INTERPOLATOR);
        layoutTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, LAYOUT_TRANSITION_INTERPOLATOR);
        layoutTransition.setDuration(LAYOUT_TRANSITION_DURATION);
        // Start the reflow together with the lyric fade instead of waiting a whole default cycle.
        layoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        // Attached only after the initial children exist so building the page does not animate.
        setLayoutTransition(layoutTransition);

        bindMusic(nowPlayingInfo.getCurrentlyPlayingMusic());
    }

    private void clearBoundViews() {
        notificationView = null;
        infoColumn = null;
        albumImage = null;
        coverContainer = null;
        if (coverShadow != null) {
            setBackground(null);
            coverShadow.close();
            coverShadow = null;
        }
        name = null;
        artistsAndAlbum = null;
        sourceButton = null;
        pusherWidget = null;
        progressBar = null;
        played = null;
        total = null;
        buttonsLayout = null;
        likeButton = null;
        addToPlaylistButton = null;
        skipCurrentButton = null;
        staggeredLyricScrollView = null;
    }

    private void buildInfoColumn(Context context) {
        LinearLayout infoView = new LinearLayout(context);
        infoView.setOrientation(LinearLayout.VERTICAL);

        FlexWrapLayout row1 = new FlexWrapLayout(context);
        row1.setLineGravity(Gravity.CENTER_VERTICAL);
        infoView.addView(row1, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row1.addView(texts, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

        {
            name = new MarqueeTextView(context);
            name.setTextSize(Theme.TEXT_SIZE_LARGE);
            name.setTextColor(Theme.NORMAL_TEXT_COLOR);
            texts.addView(name, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        {
            artistsAndAlbum = new MarqueeTextView(context);
            artistsAndAlbum.setTextSize(Theme.TEXT_SIZE_LARGE);
            artistsAndAlbum.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            artistsAndAlbum.setMovementMethod(LinkMovementMethod.getInstance());
            texts.addView(artistsAndAlbum, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }

        buttonsLayout = new LinearLayout(context);
        LayoutTransition layoutTransition = new LayoutTransition();
        LayoutTransitionReflow.attach(layoutTransition);
        buttonsLayout.setLayoutTransition(layoutTransition);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.START);
        buttonsLayout.setVisibility(GONE);
        {
            InsetBackgroundFactory buttonsBackground = InsetBackgroundFactory.builder()
                    .backgroundColor(Theme.GHOST_BUTTON_STATES)
                    .cornerRadius(dp(4)).build();
            int buttonSize = dp(BUTTON_SIZE_DP);

            pusherWidget = new PusherWidget(context);
            pusherWidget.setVisibility(GONE);
            buttonsBackground.applyBackgroundTo(pusherWidget);
            int pusherPadding = dp(PUSHER_PADDING_DP);
            pusherWidget.setPadding(pusherPadding, pusherPadding, pusherPadding, pusherPadding);
            buttonsLayout.addView(pusherWidget, new LinearLayout.LayoutParams(buttonSize, buttonSize));

            sourceButton = new ImageButton(context);
            sourceButton.setVisibility(GONE);
            sourceButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Image sourceIcon = ImageUtils.getImageFromResource(ICON_SOURCE);
            if (sourceIcon != null) {
                sourceButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(
                        context.getResources(), sourceIcon, dp(ACTION_ICON_DP), dp(ACTION_ICON_DP)), dp(1)));
            }
            sourceButton.setOnClickListener(this::onSourceClick);
            buttonsBackground.applyBackgroundTo(sourceButton);
            buttonsLayout.addView(sourceButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

            likeButton = new ToggleTrackLikeStateButton(context);
            buttonsBackground.applyBackgroundTo(likeButton);
            buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
            addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
            buttonsBackground.applyBackgroundTo(addToPlaylistButton);
            buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
            skipCurrentButton = new VoteSkipButton(context);
            buttonsBackground.applyBackgroundTo(skipCurrentButton);
            buttonsLayout.addView(skipCurrentButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        }
        row1.addView(buttonsLayout, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        row1.setWrapThresholdRatio(buttonsLayout, BUTTONS_WRAP_RATIO);

        progressBar = new LinearProgressBar(context, Theme.mediaProgressStyle());
        progressBar.setProgressFraction(0f);
        progressBar.setVisibility(GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(6));
        progressParams.setMargins(0, dp(12), 0, 0);
        infoView.addView(progressBar, progressParams);

        LinearLayout progressTexts = new LinearLayout(context);
        progressTexts.setOrientation(LinearLayout.HORIZONTAL);
        infoView.addView(progressTexts, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        {
            played = new TextView(context);
            played.setTextSize(Theme.TEXT_SIZE_NORMAL);
            played.setTextColor(Theme.THIRDERY_TEXT_COLOR);
            LinearLayout.LayoutParams playedParams =
                    new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            playedParams.setMargins(0, dp(8), 0, 0);
            progressTexts.addView(played, playedParams);
        }
        progressTexts.addView(new View(context), new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        {
            total = new TextView(context);
            total.setTextSize(Theme.TEXT_SIZE_NORMAL);
            total.setTextColor(Theme.THIRDERY_TEXT_COLOR);
            LinearLayout.LayoutParams totalParams =
                    new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            totalParams.setMargins(0, dp(8), 0, 0);
            progressTexts.addView(total, totalParams);
        }

        LinearLayout controlBar = new LinearLayout(context);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER);
        infoView.addView(controlBar, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));

        albumImage = new UrlImageView(context);
        albumImage.setCornerRadius(dp(8));
        albumImage.loadUrl(MusicHud.ICON_BASE64);

        coverContainer = new FrameLayout(context);
        coverContainer.addView(albumImage, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        coverShadow = new BlurShadowDrawable(new BlurShadowSpec(
                new BlurShadowSpec.Shape.RoundedRect(8f),
                32f, 0f, 8f, 0x70000000, 1f,
                BlurShadowSpec.CacheMode.STATIC), coverContainer);
        setBackground(coverShadow);

        infoColumn = new InfoColumn(context, coverContainer, infoView);
        addView(infoColumn, new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int contentHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());

        if (notificationView != null) {
            notificationView.measure(
                    MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.AT_MOST));
        } else if (infoColumn != null) {
            boolean lyricsVisible = staggeredLyricScrollView != null
                    && staggeredLyricScrollView.getVisibility() == VISIBLE;
            int split = Math.min(contentWidth,
                    lyricsVisible ? Math.max(dp(INFO_MIN_WIDTH_DP), contentWidth / 2) : contentWidth);

            int slotLeft = getPaddingLeft() + dp(INFO_SAFE_LEFT_DP);
            int slotRight = getPaddingLeft() + split
                    - (lyricsVisible ? dp(INFO_LYRICS_GAP_DP) : dp(INFO_SAFE_RIGHT_DP));
            int slotWidth = Math.max(0, slotRight - slotLeft);
            int infoWidth = Math.min(slotWidth, dp(INFO_MAX_WIDTH_DP));

            int availableInfoHeight = Math.max(0,
                    contentHeight - dp(INFO_SAFE_TOP_DP) - dp(INFO_SAFE_BOTTOM_DP));
            infoColumn.measure(
                    MeasureSpec.makeMeasureSpec(infoWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(availableInfoHeight, MeasureSpec.AT_MOST));
            laidOutInfoWidth = infoColumn.getMeasuredWidth();
            laidOutInfoHeight = infoColumn.getMeasuredHeight();
            laidOutInfoX = slotLeft + Math.max(0, (slotWidth - laidOutInfoWidth) / 2);
            laidOutInfoY = getPaddingTop() + dp(INFO_SAFE_TOP_DP)
                    + Math.max(0, (availableInfoHeight - laidOutInfoHeight) / 2);

            laidOutLyricsX = getPaddingLeft() + split;
            laidOutLyricsWidth = Math.max(0, contentWidth - split - dp(LYRICS_RIGHT_DP));
            if (lyricsVisible) {
                int lyricsHeight = Math.max(0,
                        contentHeight - dp(LYRICS_TOP_DP) - dp(LYRICS_BOTTOM_DP));
                staggeredLyricScrollView.measure(
                        MeasureSpec.makeMeasureSpec(laidOutLyricsWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(lyricsHeight, MeasureSpec.EXACTLY));
            }
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (notificationView != null) {
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int childWidth = notificationView.getMeasuredWidth();
            int childHeight = notificationView.getMeasuredHeight();
            int x = getPaddingLeft() + Math.max(0, (width - childWidth) / 2);
            int y = getPaddingTop() + Math.max(0, (height - childHeight) / 2);
            notificationView.layout(x, y, x + childWidth, y + childHeight);
            return;
        }
        if (infoColumn != null) {
            infoColumn.layout(laidOutInfoX, laidOutInfoY,
                    laidOutInfoX + laidOutInfoWidth, laidOutInfoY + laidOutInfoHeight);
        }
        if (staggeredLyricScrollView != null && staggeredLyricScrollView.getVisibility() == VISIBLE) {
            int lyricsTop = getPaddingTop() + dp(LYRICS_TOP_DP);
            int lyricsBottom = Math.max(lyricsTop,
                    getHeight() - getPaddingBottom() - dp(LYRICS_BOTTOM_DP));
            staggeredLyricScrollView.layout(laidOutLyricsX, lyricsTop,
                    laidOutLyricsX + laidOutLyricsWidth, lyricsBottom);
        }
    }

    private void onSourceClick(View view) {
        Object tag = sourceButton.getTag();
        if (!(tag instanceof SourceMeta sourceMeta)) {
            return;
        }
        Class<?> type = sourceMeta.type();
        if (!MusicCollection.class.isAssignableFrom(type)) {
            return;
        }
        Context context = getContext();
        //noinspection unchecked
        MusicService.getInstance()
                .loadMusicCollectionDetail(sourceMeta.id(), (Class<? extends MusicCollection>) type)
                .thenAccept(collection -> MuiModApi.postToUiThread(() -> {
                    RouterContainer router = MainFragment.getInstance().getRouterContainer();
                    if (router != null) {
                        router.pushNavigate(new MusicCollectionDetailView(context, collection));
                    }
                }));
    }

    public void switchMusic(@Nullable Traceable<MusicDetail> musicDetail, @Nullable Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            bindMusic(musicDetail);
            if (staggeredLyricScrollView == null) {
                return;
            }
            if (hasNormalLyrics(lyricLines)) {
                staggeredLyricScrollView.setVisibility(VISIBLE);
                staggeredLyricScrollView.switchLyrics(
                        musicDetail == null ? null : musicDetail.value(), lyricLines);
            } else {
                staggeredLyricScrollView.setVisibility(GONE);
                staggeredLyricScrollView.switchLyrics(null, null);
            }
        });
    }

    private void applyLyricsVisibility() {
        if (staggeredLyricScrollView == null) {
            return;
        }
        staggeredLyricScrollView.setVisibility(
                hasNormalLyrics(nowPlayingInfo.getLyricLines()) ? VISIBLE : GONE);
    }

    private static boolean hasNormalLyrics(@Nullable Collection<LyricLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        int normalLines = 0;
        for (LyricLine line : lines) {
            if (line.getType() == LyricLine.Type.NORMAL && ++normalLines > 1) {
                return true;
            }
        }
        return false;
    }

    private void bindMusic(@Nullable Traceable<MusicDetail> trace) {
        if (infoColumn == null) {
            return;
        }
        MusicDetail detail = trace == null ? null : trace.value();
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            bindIdle();
            return;
        }

        Album album = detail.getAlbum();
        int coverTarget = albumImage.getWidth();
        if (coverTarget <= 0) {
            coverTarget = dp(INFO_MAX_WIDTH_DP);
        }
        albumImage.loadUrl(album.getPicUrl(), coverTarget, coverTarget);
        updateCoverScale(false);

        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        name.setText(detail.getName());
        artistsAndAlbum.setText(buildMetaText(detail));

        PusherInfo pusherInfo = detail.getPusherInfo();
        if (pusherInfo == null || PusherInfo.EMPTY.equals(pusherInfo)) {
            pusherWidget.clear();
            pusherWidget.setVisibility(GONE);
        } else {
            pusherWidget.bind(pusherInfo);
            pusherWidget.setVisibility(VISIBLE);
        }

        SourceMeta source = trace.source();
        if (source != null) {
            sourceButton.setTag(source);
            sourceButton.setVisibility(VISIBLE);
            Tooltip.attach(sourceButton, buildSourceLabel(source));
        } else {
            sourceButton.setTag(null);
            sourceButton.setVisibility(GONE);
            Tooltip.attach(sourceButton, null);
        }

        Duration duration = Duration.of(detail.getDurationMillis(), ChronoUnit.MILLIS);
        DateTimeFormatter formatter = formatterFor(duration);
        total.setText(formatter.format(LocalTime.MIDNIGHT.plusSeconds(duration.toSeconds())));

        progressBar.setVisibility(VISIBLE);
        progressBar.setProgressFraction(nowPlayingInfo.getProgressRate());
        applyAudioStatus(StreamAudioPlayer.getInstance().getStatus());

        updateActionButtons(detail);
        startProgressUpdater(detail, formatter, duration);
    }

    private void bindIdle() {
        albumImage.loadUrl(MusicHud.ICON_BASE64);
        updateCoverScale(false);
        name.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        name.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
        artistsAndAlbum.setText("");
        pusherWidget.clear();
        pusherWidget.setVisibility(GONE);
        sourceButton.setTag(null);
        sourceButton.setVisibility(GONE);
        Tooltip.attach(sourceButton, null);
        progressBar.setIndeterminate(false);
        progressBar.setProgressFraction(0f);
        progressBar.setVisibility(GONE);
        played.setText("");
        total.setText("");
        buttonsLayout.setVisibility(GONE);
        likeButton.bindMusicList(null);
        addToPlaylistButton.bindMusicDetail(null);
        progressUpdaterToken.incrementAndGet();
    }

    private CharSequence buildMetaText(MusicDetail detail) {
        Context context = getContext();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        List<Artist> artists = detail.getArtists();
        for (int i = 0; i < artists.size(); i++) {
            Artist artist = artists.get(i);
            if (i > 0) {
                builder.append(" / ");
            }
            int start = builder.length();
            builder.append(artist.getName());
            int end = builder.length();
            if (artist.getId() > 0) {
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NotNull View widget) {
                        RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                        if (routerContainer != null) {
                            routerContainer.pushNavigate(new ArtistDetailView(context, artist));
                        }
                    }

                    @Override
                    public void updateDrawState(@NotNull TextPaint ds) {
                        ds.setColor(Theme.SECONDARY_TEXT_COLOR);
                        ds.setUnderline(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.setSpan(new ForegroundColorSpan(Theme.THIRDERY_TEXT_COLOR),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Album album = detail.getAlbum();
        String albumName = album.getName() == null ? "" : album.getName();
        if (!Album.NONE.equals(album) && !albumName.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(" - ");
            }
            int start = builder.length();
            builder.append(albumName);
            int end = builder.length();
            if (album.getId() > 0) {
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NotNull View widget) {
                        RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                        if (routerContainer != null) {
                            routerContainer.pushNavigate(new MusicCollectionDetailView(context, album));
                        }
                    }

                    @Override
                    public void updateDrawState(@NotNull TextPaint ds) {
                        ds.setColor(Theme.SECONDARY_TEXT_COLOR);
                        ds.setUnderline(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.setSpan(new ForegroundColorSpan(Theme.THIRDERY_TEXT_COLOR),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private static SpannableString buildSourceLabel(SourceMeta source) {
        SpannableString text = new SpannableString("    " + source.name());
        String iconPath = Album.class.isAssignableFrom(source.type())
                ? "/assets/music_hud/textures/gui/icons/disc_album.png"
                : "/assets/music_hud/textures/gui/icons/list_music.png";
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            text.setSpan(ImageUtils.getIconSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String playModeIconPath = switch (source.playMode()) {
            case RANDOM -> "/assets/music_hud/textures/gui/icons/shuffle.png";
            case SEQUENTIAL -> "/assets/music_hud/textures/gui/icons/repeat.png";
            case INTELLIGENT -> "/assets/music_hud/textures/gui/icons/heart_pulse.png";
        };
        Image modeIcon = ImageUtils.getImageFromResource(playModeIconPath);
        if (modeIcon != null) {
            text.setSpan(ImageUtils.getIconSpan(modeIcon), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private void updateActionButtons(@Nullable MusicDetail detail) {
        if (buttonsLayout == null) {
            return;
        }
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            buttonsLayout.setVisibility(GONE);
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
            return;
        }
        skipCurrentButton.reset();
        likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(detail).currentUsersLikeList());
        addToPlaylistButton.bindMusicDetail(detail);
        buttonsLayout.setVisibility(VISIBLE);
    }

    private static DateTimeFormatter formatterFor(Duration duration) {
        return duration.toHoursPart() >= 1 ? TIME_WITH_HOUR : TIME_WITHOUT_HOUR;
    }

    private void startProgressUpdater(MusicDetail detail, DateTimeFormatter formatter, Duration duration) {
        int token = progressUpdaterToken.incrementAndGet();
        long now = System.currentTimeMillis();
        long deadline = now + duration.toMillis() + 120_000;
        progressUpdaterStartTime = now;
        progressTextUpdateCount = -1;
        postProgressFrame(detail, formatter, deadline, token);
    }

    private void postProgressFrame(MusicDetail detail, DateTimeFormatter formatter, long deadline, int token) {
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (progressUpdaterToken.get() != token || progressBar == null || !isShown()) {
                return;
            }
            long count = (System.currentTimeMillis() - progressUpdaterStartTime) / 1000;
            if (count > progressTextUpdateCount) {
                progressTextUpdateCount = count;
                Duration playedDuration = nowPlayingInfo.getPlayedDuration();
                played.setText(formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds())));
            }
            progressBar.setProgressFraction(nowPlayingInfo.getProgressRate());
            if (detail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())
                    && nowPlayingInfo.getProgressRate() < 1
                    && System.currentTimeMillis() < deadline) {
                postProgressFrame(detail, formatter, deadline, token);
            }
        });
    }

    private void applyAudioStatus(StreamAudioPlayer.Status status) {
        if (progressBar == null) {
            return;
        }
        progressBar.setIndeterminate(status == StreamAudioPlayer.Status.BUFFERING
                || status == StreamAudioPlayer.Status.RETRYING);
    }

    private static boolean isCoverExpanded() {
        MusicDetail detail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (detail == null || detail.equals(MusicDetail.NONE) || PlaybackTask.getTargetGain() == 0) {
            return false;
        }
        return StreamAudioPlayer.getInstance().getStatus() == StreamAudioPlayer.Status.PLAYING;
    }

    public void updateCoverScale(boolean animate) {
        View cover = coverContainer != null ? coverContainer : albumImage;
        if (cover == null) {
            return;
        }
        float target = isCoverExpanded() ? 1f : COVER_MIN_SCALE;
        cancelCoverScaleAnimator();
        if (!animate) {
            cover.setScaleX(target);
            cover.setScaleY(target);
            return;
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cover, View.SCALE_X, target);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cover, View.SCALE_Y, target);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY}) {
            animator.setInterpolator(COVER_SCALE_INTERPOLATOR);
            animator.setDuration(COVER_SCALE_DURATION_MS);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        coverScaleAnimator = set;
        set.start();
    }

    private void cancelCoverScaleAnimator() {
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
            coverScaleAnimator = null;
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            restartProgressUpdater();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        //noinspection MagicConstant
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            restartProgressUpdater();
        }
    }

    private void restartProgressUpdater() {
        if (progressBar == null) {
            return;
        }
        MusicDetail detail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            return;
        }
        Duration duration = Duration.of(detail.getDurationMillis(), ChronoUnit.MILLIS);
        startProgressUpdater(detail, formatterFor(duration), duration);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        instance = this;
        if (!audioListenerRegistered) {
            StreamAudioPlayer.getInstance().getStatusChangeListener().add(statusListener);
            audioListenerRegistered = true;
        }
        applyAudioStatus(StreamAudioPlayer.getInstance().getStatus());
        updateCoverScale(false);
        restartProgressUpdater();
    }

    @Override
    protected void onDetachedFromWindow() {
        progressUpdaterToken.incrementAndGet();
        cancelCoverScaleAnimator();
        if (audioListenerRegistered) {
            StreamAudioPlayer.getInstance().getStatusChangeListener().remove(statusListener);
            audioListenerRegistered = false;
        }
        if (instance == this) {
            instance = null;
        }
        super.onDetachedFromWindow();
    }

    /**
     * Cover and everything below it share one width. The cover is square, so its height is also
     * that width; the content height depends on it too, hence the small settling loop.
     */
    private static final class InfoColumn extends ViewGroup {
        private final View cover;
        private final LinearLayout content;

        InfoColumn(Context context, View cover, LinearLayout content) {
            super(context);
            this.cover = cover;
            this.content = content;
            // The layout frame lags behind the final child sizes while HomeView animates a
            // column resize, so clipping here would cut the cover mid-transition.
            setClipChildren(false);
            addView(cover, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            addView(content, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? Math.max(getWidth(), 0) : MeasureSpec.getSize(widthMeasureSpec);
            int maxHeight = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? Integer.MAX_VALUE : MeasureSpec.getSize(heightMeasureSpec);
            // The cover is square with the column width as its side, so the column may never be
            // wider than the height the page grants it.
            int heightCap = maxHeight == Integer.MAX_VALUE
                    ? maxWidth : Math.min(maxWidth, maxHeight);
            int minWidth = Math.min(dp(INFO_MIN_WIDTH_DP), heightCap);

            // Content height is non-increasing in width, so shrinking from the widest candidate
            // only moves one way and settles in a couple of passes.
            //noinspection SuspiciousNameCombination
            int columnWidth = heightCap;
            int contentHeight = measureContent(columnWidth);
            for (int i = 0; i < INFO_FIT_PASSES; i++) {
                int fit = maxHeight == Integer.MAX_VALUE
                        ? maxWidth : maxHeight - dp(INFO_COVER_GAP_DP) - contentHeight;
                int next = Math.clamp(fit, minWidth, heightCap);
                if (next == columnWidth) {
                    break;
                }
                columnWidth = next;
                contentHeight = measureContent(columnWidth);
            }

            cover.measure(MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY));
            int totalHeight = columnWidth + dp(INFO_COVER_GAP_DP) + contentHeight;
            setMeasuredDimension(columnWidth, Math.min(totalHeight, maxHeight));
        }

        private int measureContent(int width) {
            content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            return content.getMeasuredHeight();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            cover.layout(0, 0, width, width);
            int contentTop = width + dp(INFO_COVER_GAP_DP);
            content.layout(0, contentTop, width, contentTop + content.getMeasuredHeight());
        }
    }
}
