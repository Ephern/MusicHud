package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.MusicListItem;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.stream.Collectors;

public class SearchMusicResultView extends LinearLayout {
    @Getter
    private static SearchMusicResultView instance;
    private static List<MusicDetail> result;
    private final InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
            .cornerRadius(dp(12))
            .inset(dp(1))
            .padding(new InsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build();

    public SearchMusicResultView(Context context) {
        super(context);
        instance = this;
        setOrientation(LinearLayout.VERTICAL);
        refresh();
    }

    public static void setResult(List<MusicDetail> result) {
        SearchMusicResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (MusicDetail musicDetail : result) {
                addItem(getContext(), musicDetail);
            }
        }
    }

    public void append(List<MusicDetail> musicDetails) {
        if (musicDetails == null || musicDetails.isEmpty()) {
            return;
        }
        result.addAll(musicDetails);
        for (MusicDetail musicDetail : musicDetails) {
            addItem(getContext(), musicDetail);
        }
    }

    private void addItem(Context context, MusicDetail musicDetail) {
        var musicLayout = new MusicListItem(context);
        musicLayout.bindData(musicDetail);
        backgroundFactory.applyBackgroundTo(musicLayout);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(musicDetail);
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        addView(musicLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
