package indi.etern.musichud.client.ui.components;

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
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.pages.routes.ArtistDetailView;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.server.api.playmode.PlayMode;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicTrackItem extends LinearLayout {
    public static final int imageSize = 56;
    private static final int COMPACT_MAX_WIDTH_DP = 400;
    /** Wide enough to keep the arrangement stable when the width sits on the threshold. */
    private static final int COMPACT_EXIT_WIDTH_DP = 416;
    private static final int ACTION_BUTTON_NORMAL_DP = 40;
    private static final int ACTION_BUTTON_COMPACT_DP = 20;
    private static final int ACTION_NORMAL_INSET_DP = 1;
    private static final int ACTION_NORMAL_CORNER_DP = 4;
    private static final int ACTION_COMPACT_INSET_DP = 1;
    private static final int ACTION_COMPACT_CORNER_DP = 2;
    private static final int ACTION_ICON_DP = 16;
    private static final String ICON_SOURCE = "/assets/music_hud/textures/gui/icons/square_arrow_out_up_right.png";
    private static final MusicService musicService = MusicService.getInstance();
    private static final DateTimeFormatter PLAY_RECORD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final DateTimeFormatter timeFormatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("mm:ss");
    @Getter
    private UrlImageView albumImageView;
    private MarqueeTextView musicName;
    private TextView feeLabel;
    private MarqueeTextView row2;
    private LinearLayout row1;
    private TextView durationText;
    @Setter
    @Getter
    private boolean showPusherInfo = true;
    @Getter
    private MusicDetail musicDetail;
    @Getter
    private Traceable<MusicDetail> musicTrace;
    private PusherWidget pusherWidget;
    private ToggleTrackLikeStateButton likeButton;
    private LinearLayout buttonsLayout;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private ImageButton sourceButton;
    private TextView playRecordTimeText;
    @Getter
    private LinearLayout infoRow;
    private boolean compact;
    private InsetBackgroundFactory normalActionButtonBackground;
    private InsetBackgroundFactory compactActionButtonBackground;

    public MusicTrackItem(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(HORIZONTAL);
        LayoutParams musicLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        setLayoutParams(musicLayoutParams);
        setGravity(Gravity.CENTER_VERTICAL);

        albumImageView = new UrlImageView(context);
        albumImageView.setCornerRadius(dp(4));
        albumImageView.setAspectRatio(1);
        addView(albumImageView, new LayoutParams(dp(imageSize), dp(imageSize)));

        LinearLayout musicTexts = new LinearLayout(context);
        musicTexts.setOrientation(VERTICAL);
        musicTexts.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams textsParams = new LayoutParams(0, WRAP_CONTENT, 1);
        textsParams.setMargins(dp(8), 0, 0, 0);
        addView(musicTexts, textsParams);

        row1 = new LinearLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        musicTexts.addView(row1, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        musicName = new MarqueeTextView(context);
        musicName.setTextSize(Theme.TEXT_SIZE_LARGE);
        musicName.setTextColor(Theme.NORMAL_TEXT_COLOR);
        row1.addView(musicName, new LayoutParams(0, WRAP_CONTENT, 1));

        row2 = new MarqueeTextView(context);
        row2.setTextSize(Theme.TEXT_SIZE_NORMAL);
        row2.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setMinimumHeight(dp(16));
        row2.setMovementMethod(LinkMovementMethod.getInstance());
        row2.setHighlightColor(0x33FFFFFF);
        musicTexts.addView(row2, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        infoRow = new LinearLayout(context);
        infoRow.setOrientation(HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        musicTexts.addView(infoRow);

        durationText = new TextView(context);
        durationText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        durationText.setSingleLine(true);
        durationText.setTextColor(Theme.SECONDARY_TEXT_COLOR);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(12), 0);
        infoRow.addView(durationText, params);

        feeLabel = new TextView(context);
        feeLabel.setSingleLine(true);
        feeLabel.setTextSize(Theme.TEXT_SIZE_NORMAL);
        feeLabel.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        feeLabel.setVisibility(GONE);
        LayoutParams params1 = new LayoutParams(WRAP_CONTENT, dp(16));
        params1.setMargins(0, 0, dp(12), 0);
        infoRow.addView(feeLabel, params1);

        sourceButton = new ImageButton(context);
        sourceButton.setVisibility(View.GONE);
        sourceButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image sourceIcon = ImageUtils.getImageFromResource(ICON_SOURCE);
        if (sourceIcon != null) {
            ScaledImageDrawable drawable = new ScaledImageDrawable(context.getResources(), sourceIcon, dp(16), dp(16));
            drawable.setAlpha(0.7f);
            sourceButton.setImageDrawable(new InsetDrawable(
                    drawable, dp(1)));
        }
        sourceButton.setOnClickListener(view -> {
            Object tag = sourceButton.getTag();
            if (tag instanceof SourceMeta sourceMeta) {
                Class<?> type = sourceMeta.type();
                if (MusicCollection.class.isAssignableFrom(type)) {
                    //noinspection unchecked
                    MusicService.getInstance().loadMusicCollectionDetail(sourceMeta.id(), (Class<? extends MusicCollection>) type)
                            .thenAccept((musicCollection) ->
                                    MuiModApi.postToUiThread(() -> MainFragment.getInstance().getRouterContainer().pushNavigate(
                                            new MusicCollectionDetailView(getContext(), musicCollection)))
                            );
                }
            }
        });

        InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .padding(new InsetBackgroundFactory.Padding(0, sourceButton.dp(1), 0, sourceButton.dp(1)))
                .cornerRadius(sourceButton.dp(4)).build().applyBackgroundTo(sourceButton);
        LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params3.setMargins(0, 0, dp(2), 0);
        infoRow.addView(sourceButton, params3);

        pusherWidget = new PusherWidget(context);
        pusherWidget.setVisibility(View.GONE);
        LayoutParams params2 = new LayoutParams(dp(18), dp(18));
        params2.setMargins(0, 0, dp(12), 0);
        infoRow.addView(pusherWidget, params2);

        playRecordTimeText = new TextView(context);
        playRecordTimeText.setSingleLine(true);
        playRecordTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        playRecordTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        playRecordTimeText.setVisibility(GONE);
        LayoutParams recordTimeParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        infoRow.addView(playRecordTimeText, recordTimeParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams buttonsLayoutParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 0);
        buttonsLayoutParams.setMargins(0, 0, dp(10), 0);
        addView(buttonsLayout, buttonsLayoutParams);

        normalActionButtonBackground = InsetBackgroundFactory.builder()
                .inset(dp(ACTION_NORMAL_INSET_DP))
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(ACTION_NORMAL_CORNER_DP))
                .build();
        compactActionButtonBackground = InsetBackgroundFactory.builder()
                .inset(dp(ACTION_COMPACT_INSET_DP))
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(ACTION_COMPACT_CORNER_DP))
                .build();

        addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
        applyActionButtonStyle(addToPlaylistButton);
        buttonsLayout.addView(addToPlaylistButton);

        likeButton = new ToggleTrackLikeStateButton(context);
        applyActionButtonStyle(likeButton);
        buttonsLayout.addView(likeButton);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? getWidth()
                : MeasureSpec.getSize(widthMeasureSpec);
        updateCompact(width);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Decided by the measure pass only: layout animations report interpolated sizes, and reacting to
     * them rearranges the item, which changes its height and restarts the animation. The width keeps
     * a dead zone so a width on the threshold cannot flip the arrangement back and forth.
     * <p>
     * Applying here, before {@code super.onMeasure}, means this very pass already measures the new
     * arrangement, so it does not depend on a later layout pass (which transitions can suppress).
     */
    private void updateCompact(int width) {
        if (width <= 0) {
            return;
        }
        boolean next = compact
                ? width < dp(COMPACT_EXIT_WIDTH_DP)
                : width < dp(COMPACT_MAX_WIDTH_DP);
        if (next == compact) {
            return;
        }
        compact = next;
        applyResponsiveLayout();
    }

    /**
     * Wide items keep the action buttons on the right; narrow ones move the whole container after
     * the name so the title row stays one line high.
     */
    private void applyResponsiveLayout() {
        if (row1 == null || buttonsLayout == null) {
            return;
        }
        ViewGroup target = compact ? row1 : this;
        if (buttonsLayout.getParent() != target) {
            detachActionButtons();
            target.addView(buttonsLayout, actionButtonsContainerParams(compact));
        }
        applyActionButtonMetrics();
    }

    private void detachActionButtons() {
        if (buttonsLayout.getParent() instanceof ViewGroup parent) {
            parent.removeView(buttonsLayout);
        }
    }

    private LinearLayout.LayoutParams actionButtonsContainerParams(boolean compactLayout) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                WRAP_CONTENT, compactLayout ? WRAP_CONTENT : MATCH_PARENT, 0);
        if (compactLayout) {
            lp.setMargins(dp(8), 0, 0, 0);
        } else {
            lp.setMargins(0, 0, dp(10), 0);
        }
        return lp;
    }

    private InsetBackgroundFactory currentActionButtonBackground() {
        return compact ? compactActionButtonBackground : normalActionButtonBackground;
    }

    private int currentActionButtonSize() {
        return dp(compact ? ACTION_BUTTON_COMPACT_DP : ACTION_BUTTON_NORMAL_DP);
    }

    private void applyActionButtonMetrics() {
        int size = currentActionButtonSize();
        InsetBackgroundFactory background = currentActionButtonBackground();
        for (int i = 0; i < buttonsLayout.getChildCount(); i++) {
            View child = buttonsLayout.getChildAt(i);
            background.applyBackgroundTo(child);
            child.setLayoutParams(new LinearLayout.LayoutParams(size, size, 0));
        }
    }

    private void applyActionButtonStyle(View button) {
        currentActionButtonBackground().applyBackgroundTo(button);
        int size = currentActionButtonSize();
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size, 0));
    }

    /** Consistently styled action button; callers only supply icon, tooltip and action. */
    public ImageButton addActionButton(String iconPath, CharSequence tooltip, View.OnClickListener listener) {
        ImageButton button = new ImageButton(getContext());
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            button.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), icon,
                    dp(ACTION_ICON_DP), dp(ACTION_ICON_DP)));
        }
        Tooltip.attach(button, tooltip);
        button.setOnClickListener(listener);
        applyActionButtonStyle(button);
        buttonsLayout.addView(button);
        return button;
    }

    public void setActionButtonsVisible(boolean visible) {
        buttonsLayout.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setNormalActionButtonBackground(InsetBackgroundFactory background) {
        normalActionButtonBackground = Objects.requireNonNull(background, "background");
        if (!compact) {
            applyActionButtonMetrics();
        }
    }

    public void setCompactActionButtonBackground(InsetBackgroundFactory background) {
        compactActionButtonBackground = Objects.requireNonNull(background, "background");
        if (compact) {
            applyActionButtonMetrics();
        }
    }

    public void clearData() {
        albumImageView.clear();
        musicName.setText("");
        feeLabel.setText("");
        feeLabel.setVisibility(GONE);
        durationText.setText("");
        row2.setText("");
        pusherWidget.clear();
        pusherWidget.setVisibility(View.GONE);
        playRecordTimeText.setText("");
        playRecordTimeText.setVisibility(GONE);
        addToPlaylistButton.bindMusicDetail(null);
        likeButton.bindMusicList(null);
        setTag(null);
        musicDetail = null;
        musicTrace = null;
    }

    /**
     * Shows the play-record time at the end of the last row; pass {@code null} or
     * {@link Instant#MIN} to hide it.
     */
    public void setPlayRecordTime(Instant playTime) {
        if (playRecordTimeText == null) {
            return;
        }
        if (playTime == null || playTime.equals(Instant.MIN)) {
            playRecordTimeText.setText("");
            playRecordTimeText.setVisibility(GONE);
        } else {
            playRecordTimeText.setText(PLAY_RECORD_FORMATTER.format(playTime));
            playRecordTimeText.setVisibility(VISIBLE);
        }
    }

    public void bindData(MusicDetail musicDetail) {
        bindData(Traceable.of(musicDetail));
    }

    public void bindData(Traceable<MusicDetail> musicTrace) {
        MusicDetail musicDetail = musicTrace == null ? null : musicTrace.value();
        if (Objects.equals(this.musicDetail, musicDetail) && Objects.equals(this.musicTrace, musicTrace)) {
            return;
        }
        setTag(musicDetail == null ? null : musicDetail.getId());
        this.musicDetail = musicDetail;
        this.musicTrace = musicTrace;
        Album album = musicDetail.getAlbum();
        albumImageView.loadUrl(album.getImageThumbnailUrl(dp(imageSize)));

        musicName.setText(musicDetail.getName());
        Fee fee1 = musicDetail.getFee();
        if (fee1 == Fee.VIP || fee1 == Fee.SEPARATELY_PURCHASE) {
            feeLabel.setText(I18n.get(MusicHud.MOD_ID + ".text.fee." + fee1.name()));
            feeLabel.setVisibility(VISIBLE);
        } else {
            feeLabel.setVisibility(GONE);
        }

        Context context = getContext();
        List<Artist> artists = musicDetail.getArtists();
        SpannableStringBuilder metaBuilder = new SpannableStringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            final Artist artist = artists.get(i);
            if (i > 0) {
                metaBuilder.append(" / ");
            }
            int start = metaBuilder.length();
            metaBuilder.append(artist.getName());
            int end = metaBuilder.length();
            if (artist.getId() > 0) {
                metaBuilder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NotNull View widget) {
                        RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                        if (routerContainer != null) {
                            routerContainer.pushNavigate(new ArtistDetailView(context, artist));
                        }
                    }

                    @Override
                    public void updateDrawState(@NotNull TextPaint ds) {
                        ds.setColor(Theme.PRIMARY_COLOR);
                        ds.setUnderline(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                metaBuilder.setSpan(new ForegroundColorSpan(Theme.SECONDARY_TEXT_COLOR),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String albumName = album.getName();
        albumName = albumName == null ? "" : albumName;
        if (!Album.NONE.equals(album) && !albumName.isBlank()) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append(" - ");
            }
            int start = metaBuilder.length();
            metaBuilder.append(albumName);
            int end = metaBuilder.length();
            if (album.getId() > 0) {
                metaBuilder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NotNull View widget) {
                        RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                        if (routerContainer != null) {
                            routerContainer.pushNavigate(new MusicCollectionDetailView(context, album));
                        }
                    }

                    @Override
                    public void updateDrawState(@NotNull TextPaint ds) {
                        ds.setColor(Theme.PRIMARY_COLOR);
                        ds.setUnderline(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                metaBuilder.setSpan(new ForegroundColorSpan(Theme.SECONDARY_TEXT_COLOR),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        row2.setText(metaBuilder);

        Duration duration = Duration.of(musicDetail.getDurationMillis(), ChronoUnit.MILLIS);
        DateTimeFormatter formatter = duration.toHoursPart() >= 1 ?
                timeFormatterWithHour :
                timeFormatter;
        durationText.setText(formatter.format(
                LocalTime.MIDNIGHT.plusSeconds(duration.toSeconds())
        ));

        if (showPusherInfo) {
            PusherInfo pusherInfo = musicDetail.getPusherInfo();
            if (pusherInfo == null || PusherInfo.EMPTY.equals(pusherInfo)) {
                pusherWidget.clear();
                pusherWidget.setVisibility(GONE);
            } else {
                pusherWidget.bind(pusherInfo);
                pusherWidget.setVisibility(VISIBLE);
            }

            SourceMeta source = musicTrace.source();
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

                Tooltip.attach(sourceButton, text);
                sourceButton.setVisibility(VISIBLE);
            } else {
                sourceButton.setVisibility(GONE);
            }
        } else {
            pusherWidget.setVisibility(GONE);
            pusherWidget.clear();
            sourceButton.setVisibility(GONE);
        }

        addToPlaylistButton.bindMusicDetail(musicDetail);
        likeButton.bindMusicList(musicService.getMusicTrackState(musicDetail).currentUsersLikeList());
    }
}