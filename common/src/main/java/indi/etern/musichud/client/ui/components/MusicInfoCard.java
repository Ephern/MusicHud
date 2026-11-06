package indi.etern.musichud.client.ui.components;

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
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
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.pages.routes.ArtistDetailView;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.server.api.playmode.PlayMode;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Player header that morphs between the expanded layout (cover above the text) and the compact
 * one (cover beside three lines). Three independent fractions drive the cover size, the text x
 * and the text y, so the caller can give each axis its own spring and keep the path a single
 * continuous curve.
 */
@Getter
public class MusicInfoCard extends ViewGroup {
    private static final String ICON_SOURCE = "/assets/music_hud/textures/gui/icons/square_arrow_out_up_right.png";
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final DateTimeFormatter timeFormatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter timeFormatterWithoutHour = DateTimeFormatter.ofPattern("mm:ss");
    private static final int COMPACT_COVER_DP = 56;
    private static final int COMPACT_GAP_DP = 8;
    private static final int PROGRESS_TOP_GAP_DP = 4;
    private static final int PROGRESS_HEIGHT_DP = 4;
    private static final int TIMES_TOP_GAP_DP = 6;
    private static final int TIMES_HEIGHT_DP = 16;
    private final UrlImageView albumImage;
    private final LinearLayout musicInfo;
    private final MarqueeTextView titleText;
    private final MarqueeTextView artistsText;
    private final MarqueeTextView albumText;
    private final PusherWidget pusherWidget;
    private final ImageButton sourceButton;
    private final ProgressBar progressBar;
    private final LinearLayout progressTexts;
    private final TextView playedTimeText;
    private final TextView totalTimeText;
    private final LayoutTransition layoutTransition;
    private float coverFraction;
    private float xFraction;
    private float yFraction;
    private boolean contentTransitionsEnabled = true;

    // Geometry resolved by onMeasure and reused by onLayout.
    private int laidOutCoverSize;
    private int laidOutInfoX;
    private int laidOutInfoY;
    private int laidOutInfoWidth;
    private int laidOutInfoHeight;
    private int laidOutProgressTop;
    private int laidOutTimesTop;

    public MusicInfoCard(Context context, int imageSize) {
        super(context);

        musicInfo = new LinearLayout(context);
        musicInfo.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new MarqueeTextView(context);
        titleText.setTextSize(Theme.TEXT_SIZE_LARGE);
        titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
        titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
        titleRow.addView(titleText, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));

        sourceButton = new ImageButton(context);
        sourceButton.setVisibility(View.GONE);
        sourceButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image sourceIcon = ImageUtils.getImageFromResource(ICON_SOURCE);
        if (sourceIcon != null) {
            sourceButton.setImageDrawable(new InsetDrawable(
                    new ScaledImageDrawable(context.getResources(), sourceIcon, dp(16), dp(16)), dp(1)));
        }
        sourceButton.setOnClickListener(view -> {
            Object tag = sourceButton.getTag();
            if (tag instanceof SourceMeta sourceMeta) {
                Class<?> type = sourceMeta.type();
                if (MusicCollection.class.isAssignableFrom(type)) {
                    //noinspection unchecked
                    MusicService.getInstance().loadMusicCollectionDetail(sourceMeta.id(), (Class<? extends MusicCollection>) type)
                            .thenAccept((musicCollection) ->
                                    MuiModApi.postToUiThread(() -> {
                                        RouterContainer router = MainFragment.getInstance().getRouterContainer();
                                        if (router != null) {
                                            router.pushNavigate(new MusicCollectionDetailView(context, musicCollection));
                                        }
                                    }));
                }
            }
        });
        InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(4)).build().applyBackgroundTo(sourceButton);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        sourceParams.setMargins(dp(4), 0, 0, 0);
        titleRow.addView(sourceButton, sourceParams);

        pusherWidget = new PusherWidget(context);
        LinearLayout.LayoutParams pusherParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        pusherParams.setMargins(dp(4), 0, 0, 0);
        titleRow.addView(pusherWidget, pusherParams);

        musicInfo.addView(titleRow, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        artistsText = new MarqueeTextView(context);
        artistsText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        artistsText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        artistsText.setGravity(Gravity.CENTER_VERTICAL);
        artistsText.setMinimumHeight(artistsText.dp(16));
        artistsText.setMovementMethod(LinkMovementMethod.getInstance());
        artistsText.setHighlightColor(0x33FFFFFF);
        musicInfo.addView(artistsText);

        albumText = new MarqueeTextView(context);
        albumText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        albumText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        albumText.setMovementMethod(LinkMovementMethod.getInstance());
        albumText.setHighlightColor(0x33FFFFFF);
        albumText.setVisibility(View.GONE);
        musicInfo.addView(albumText);

        progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
        progressBar.setMin(0);
        progressBar.setMax(imageSize);
        progressBar.setVisibility(View.GONE);

        progressTexts = new LinearLayout(context);
        progressTexts.setOrientation(LinearLayout.HORIZONTAL);

        playedTimeText = new TextView(context);
        playedTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        playedTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        progressTexts.addView(playedTimeText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

        progressTexts.addView(new View(context), new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1));

        totalTimeText = new TextView(context);
        totalTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        totalTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        progressTexts.addView(totalTimeText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

        albumImage = new UrlImageView(context);
        albumImage.loadUrl(MusicHud.ICON_BASE64);
        albumImage.setCornerRadius(dp(8));

        // The cover is added last so it is drawn above the text while the two overlap during the
        // morph; the text then appears to slide out from under the shrinking artwork.
        addView(musicInfo);
        addView(progressBar);
        addView(progressTexts);
        addView(albumImage);

        layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setAnimateParentHierarchy(false);
        musicInfo.setLayoutTransition(layoutTransition);
    }

    /**
     * @param coverFraction 0 expanded cover, 1 compact cover
     * @param xFraction     0 text at the left edge, 1 shifted beside the compact cover
     * @param yFraction     0 text below the cover, 1 text aligned with the compact cover
     */
    public void setMorph(float coverFraction, float xFraction, float yFraction) {
        float cover = Math.clamp(coverFraction, 0f, 1f);
        float x = Math.clamp(xFraction, 0f, 1f);
        float y = Math.clamp(yFraction, 0f, 1f);
        if (Math.abs(this.coverFraction - cover) < 1e-5f
                && Math.abs(this.xFraction - x) < 1e-5f
                && Math.abs(this.yFraction - y) < 1e-5f) {
            return;
        }
        this.coverFraction = cover;
        this.xFraction = x;
        this.yFraction = y;
        requestLayout();
    }

    /** Per-frame morphs must not fight the child layout transitions. */
    public void setContentTransitionsEnabled(boolean enabled) {
        if (contentTransitionsEnabled == enabled) {
            return;
        }
        contentTransitionsEnabled = enabled;
        musicInfo.setLayoutTransition(enabled ? layoutTransition : null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int width = widthMode == MeasureSpec.UNSPECIFIED
                ? Math.max(getSuggestedMinimumWidth(), getWidth())
                : MeasureSpec.getSize(widthMeasureSpec);
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());

        int gap = dp(COMPACT_GAP_DP);
        int compactCover = Math.min(dp(COMPACT_COVER_DP), contentWidth);

        int coverSize = Math.round(contentWidth + (compactCover - contentWidth) * coverFraction);
        int infoX = Math.round((compactCover + gap) * xFraction);
        int infoWidth = Math.max(1, contentWidth - infoX);

        measureChildWidth(musicInfo, infoWidth, MeasureSpec.UNSPECIFIED);
        int infoHeight = musicInfo.getMeasuredHeight();
        int compactInfoY = Math.max(0, (compactCover - infoHeight) / 2);
        int expandedInfoY = contentWidth + gap;
        int infoY = Math.round(expandedInfoY + (compactInfoY - expandedInfoY) * yFraction);
        int headerHeight = Math.max(coverSize, infoY + infoHeight);

        int progressTop = headerHeight + dp(PROGRESS_TOP_GAP_DP);
        int progressHeight = dp(PROGRESS_HEIGHT_DP);
        int timesTop = progressTop + progressHeight + dp(TIMES_TOP_GAP_DP);
        int timesHeight = dp(TIMES_HEIGHT_DP);

        laidOutCoverSize = coverSize;
        laidOutInfoX = infoX;
        laidOutInfoY = infoY;
        laidOutInfoWidth = infoWidth;
        laidOutInfoHeight = infoHeight;
        laidOutProgressTop = progressTop;
        laidOutTimesTop = timesTop;

        measureExact(albumImage, coverSize, coverSize);
        measureExact(progressBar, contentWidth, progressHeight);
        measureExact(progressTexts, contentWidth, timesHeight);

        int totalHeight = getPaddingTop() + timesTop + timesHeight + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(Math.max(totalHeight, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int contentLeft = getPaddingLeft();
        int contentTop = getPaddingTop();
        albumImage.layout(contentLeft, contentTop,
                contentLeft + laidOutCoverSize, contentTop + laidOutCoverSize);
        musicInfo.layout(contentLeft + laidOutInfoX, contentTop + laidOutInfoY,
                contentLeft + laidOutInfoX + laidOutInfoWidth, contentTop + laidOutInfoY + laidOutInfoHeight);
        progressBar.layout(contentLeft, contentTop + laidOutProgressTop,
                contentLeft + progressBar.getMeasuredWidth(), contentTop + laidOutProgressTop + progressBar.getMeasuredHeight());
        progressTexts.layout(contentLeft, contentTop + laidOutTimesTop,
                contentLeft + progressTexts.getMeasuredWidth(), contentTop + laidOutTimesTop + progressTexts.getMeasuredHeight());
    }

    private void measureChildWidth(View child, int width, int heightMode) {
        if (child.getVisibility() == GONE) {
            return;
        }
        child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, heightMode));
    }

    private static void measureExact(View child, int width, int height) {
        if (child.getVisibility() == GONE) {
            return;
        }
        child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height), MeasureSpec.EXACTLY));
    }

    /**
     * Resets this card to an empty state without playing its own child transitions, so a
     * recycled (off-screen) card never leaks stale content into its next slide-in.
     */
    public void clear() {
        musicInfo.setLayoutTransition(null);
        try {
            albumImage.clear();
            titleText.setText("");
            artistsText.setText("");
            albumText.setText("");
            albumText.setVisibility(View.GONE);
            pusherWidget.clear();
            pusherWidget.setVisibility(View.GONE);
            sourceButton.setVisibility(View.GONE);
            sourceButton.setTag(null);
            Tooltip.attach(sourceButton, null);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            progressBar.setVisibility(View.GONE);
            playedTimeText.setText("");
            totalTimeText.setText("");
        } finally {
            if (contentTransitionsEnabled) {
                musicInfo.setLayoutTransition(layoutTransition);
            }
        }
    }

    public void bind(Traceable<MusicDetail> musicDetailTrace, int sideWidth) {
        if (musicDetailTrace == null || musicDetailTrace.value() == null || musicDetailTrace.value().equals(MusicDetail.NONE)) {
            albumImage.loadUrl(MusicHud.ICON_BASE64);
            titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
            titleText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            artistsText.setText("");
            albumText.setText("");
            albumText.setVisibility(View.GONE);
            pusherWidget.clear();
            pusherWidget.setVisibility(View.GONE);
            sourceButton.setVisibility(View.GONE);
            sourceButton.setTag(null);
            Tooltip.attach(sourceButton, null);
            progressBar.setVisibility(View.GONE);
            playedTimeText.setText("");
            totalTimeText.setText("");
        } else {
            MusicDetail musicDetail = musicDetailTrace.value();
            titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
            Album album = musicDetail.getAlbum();
            albumImage.loadUrl(album.getPicUrl(), sideWidth, sideWidth);
            titleText.setText(musicDetail.getName());
            PusherInfo pusherInfo = musicDetail.getPusherInfo();
            if (pusherInfo == null || PusherInfo.EMPTY.equals(pusherInfo)) {
                pusherWidget.setVisibility(View.GONE);
                pusherWidget.clear();
            } else {
                pusherWidget.setVisibility(View.VISIBLE);
                pusherWidget.bind(pusherInfo);
            }
            SourceMeta source = musicDetailTrace.source();
            if (source != null) {
                sourceButton.setTag(source);
                SpannableString text = new SpannableString("    " + source.name());
                {
                    String iconPath;
                    Class<?> type = source.type();
                    if (Album.class.isAssignableFrom(type)) {
                        iconPath = "/assets/music_hud/textures/gui/icons/disc_album.png";
                    } else {
                        iconPath = "/assets/music_hud/textures/gui/icons/list_music.png";
                    }
                    Image icon = ImageUtils.getImageFromResource(iconPath);
                    if (icon != null) {
                        ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                        text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                {
                    PlayMode playMode = source.playMode();
                    String playModeIconPath = switch (playMode) {
                        case RANDOM -> "/assets/music_hud/textures/gui/icons/shuffle.png";
                        case SEQUENTIAL -> "/assets/music_hud/textures/gui/icons/repeat.png";
                        case INTELLIGENT -> "/assets/music_hud/textures/gui/icons/heart_pulse.png";
                    };
                    Image icon = ImageUtils.getImageFromResource(playModeIconPath);
                    if (icon != null) {
                        ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                        text.setSpan(iconSpan, 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                sourceButton.setVisibility(View.VISIBLE);
                Tooltip.attach(sourceButton, text);
            } else {
                sourceButton.setVisibility(View.GONE);
                sourceButton.setTag(null);
                Tooltip.attach(sourceButton, null);
            }
            Context context = ModernUI.getInstance();
            SpannableStringBuilder artistsBuilder = new SpannableStringBuilder();
            for (Artist artist : musicDetail.getArtists()) {
                if (!artistsBuilder.isEmpty()) {
                    artistsBuilder.append(" / ");
                }
                int start = artistsBuilder.length();
                artistsBuilder.append(artist.getName());
                int end = artistsBuilder.length();
                if (artist.getId() > 0) {
                    artistsBuilder.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NotNull View widget) {
                            RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                            if (routerContainer != null) {
                                routerContainer.pushNavigate(
                                        new ArtistDetailView(context, artist)
                                );
                            }
                        }

                        @Override
                        public void updateDrawState(@NotNull TextPaint ds) {
                            ds.setColor(Theme.PRIMARY_COLOR);
                            ds.setUnderline(false);
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    artistsBuilder.setSpan(new ForegroundColorSpan(Theme.SECONDARY_TEXT_COLOR),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            artistsText.setText(artistsBuilder);

            String albumName = album.getName() == null ? "" : album.getName();
            SpannableStringBuilder albumBuilder = new SpannableStringBuilder(albumName);
            if (!albumName.isEmpty() && album.getId() > 0) {
                albumBuilder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NotNull View widget) {
                        RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                        if (routerContainer != null) {
                            routerContainer.pushNavigate(
                                    new MusicCollectionDetailView(context, album)
                            );
                        }
                    }

                    @Override
                    public void updateDrawState(@NotNull TextPaint ds) {
                        ds.setColor(Theme.PRIMARY_COLOR);
                        ds.setUnderline(false);
                    }
                }, 0, albumBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            albumText.setText(albumBuilder);
            albumText.setVisibility(albumName.isEmpty() ? View.GONE : View.VISIBLE);

            Duration musicDuration = Duration.of(musicDetail.getDurationMillis(), ChronoUnit.MILLIS);
            DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                    timeFormatterWithHour :
                    timeFormatterWithoutHour;
            String totalTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds()));

            Duration playedDuration = nowPlayingInfo.getPlayedDuration();
            String playedTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds()));
            playedTimeText.setText(playedTimeString);
            totalTimeText.setText(totalTimeString);

            progressBar.setVisibility(View.VISIBLE);
        }
    }
}
