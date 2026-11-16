package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.music.SourceMeta;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.components.buttons.VoteSkipButton;
import indi.etern.musichud.client.ui.components.volume.VolumeIconButton;
import indi.etern.musichud.client.ui.components.volume.VolumeUiController;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.LayoutTransitionReflow;

/**
 * Per-track action row shared by the Home page and the sidebar: requester avatar, volume, source,
 * like, add-to-playlist and vote-skip. Track binding is all-or-nothing so both hosts stay in sync.
 */
public class MediaControlBar extends LinearLayout {
    private static final String ICON_SOURCE = "/assets/music_hud/textures/gui/icons/square_arrow_out_up_right.png";
    private static final int DEFAULT_BUTTON_SIZE_DP = 36;
    private static final int ACTION_ICON_DP = 16;
    private static final int PUSHER_PADDING_DP = 8;

    private final PusherWidget pusherWidget;
    private final VolumeIconButton volumeButton;
    private final ImageButton sourceButton;
    private final ToggleTrackLikeStateButton likeButton;
    private final ModifyPlaylistTrackModalButton addToPlaylistButton;
    private final VoteSkipButton skipCurrentButton;

    public MediaControlBar(Context context) {
        this(context, DEFAULT_BUTTON_SIZE_DP);
    }

    /**
     * @param buttonSizeDp hit box edge in dp; the docked sidebar uses a slightly tighter row so
     *                     the avatar, volume, source and three actions still fit its fixed width
     */
    public MediaControlBar(Context context, int buttonSizeDp) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.START);
        LayoutTransition layoutTransition = new LayoutTransition();
        LayoutTransitionReflow.attach(layoutTransition);
        setLayoutTransition(layoutTransition);

        InsetBackgroundFactory buttonBackground = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(4))
                .build();
        int buttonSize = dp(buttonSizeDp);

        pusherWidget = new PusherWidget(context);
        pusherWidget.setVisibility(GONE);
        buttonBackground.applyBackgroundTo(pusherWidget);
        int pusherPadding = dp(PUSHER_PADDING_DP);
        pusherWidget.setPadding(pusherPadding, pusherPadding, pusherPadding, pusherPadding);
        addView(pusherWidget, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        volumeButton = new VolumeIconButton(context);
        buttonBackground.applyBackgroundTo(volumeButton);
        addView(volumeButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        sourceButton = new ImageButton(context);
        sourceButton.setVisibility(GONE);
        sourceButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image sourceIcon = ImageUtils.getImageFromResource(ICON_SOURCE);
        if (sourceIcon != null) {
            int iconSize = dp(ACTION_ICON_DP);
            sourceButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(
                    context.getResources(), sourceIcon, iconSize, iconSize), dp(1)));
        }
        sourceButton.setOnClickListener(this::onSourceClick);
        buttonBackground.applyBackgroundTo(sourceButton);
        addView(sourceButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        likeButton = new ToggleTrackLikeStateButton(context);
        buttonBackground.applyBackgroundTo(likeButton);
        addView(likeButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
        buttonBackground.applyBackgroundTo(addToPlaylistButton);
        addView(addToPlaylistButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        skipCurrentButton = new VoteSkipButton(context);
        buttonBackground.applyBackgroundTo(skipCurrentButton);
        addView(skipCurrentButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));

        setVisibility(GONE);
    }

    /** Binds every action to the track that is currently shown; null idles the whole row. */
    public void bind(@Nullable Traceable<MusicDetail> trace) {
        MusicDetail detail = trace == null ? null : trace.value();
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
            bindPusher(null);
            bindSource(null);
            setVisibility(GONE);
            VolumeUiController.getInstance().dismiss();
            return;
        }
        skipCurrentButton.reset();
        likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(detail).currentUsersLikeList());
        addToPlaylistButton.bindMusicDetail(detail);
        bindPusher(detail);
        bindSource(trace.source());
        setVisibility(VISIBLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        VolumeUiController.getInstance().register(volumeButton);
    }

    @Override
    protected void onDetachedFromWindow() {
        VolumeUiController.getInstance().unregister(volumeButton);
        super.onDetachedFromWindow();
    }

    private void bindPusher(@Nullable MusicDetail detail) {
        PusherInfo pusherInfo = detail == null ? null : detail.getPusherInfo();
        if (pusherInfo == null || PusherInfo.EMPTY.equals(pusherInfo)) {
            pusherWidget.clear();
            pusherWidget.setVisibility(GONE);
        } else {
            pusherWidget.bind(pusherInfo);
            pusherWidget.setVisibility(VISIBLE);
        }
    }

    private void bindSource(@Nullable SourceMeta source) {
        if (source == null) {
            sourceButton.setTag(null);
            sourceButton.setVisibility(GONE);
            Tooltip.attach(sourceButton, null);
            return;
        }
        sourceButton.setTag(source);
        sourceButton.setVisibility(VISIBLE);
        Tooltip.attach(sourceButton, buildSourceLabel(source));
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
}
