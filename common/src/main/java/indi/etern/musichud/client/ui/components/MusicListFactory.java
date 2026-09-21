package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Function;
import java.util.stream.Collectors;

public class MusicListFactory {
    public static MusicTrackItem createItem(ViewGroup parent) {
        return createItem(parent, (view) -> false);
    }
    public static MusicTrackItem createItem(ViewGroup parent, Function<View, Boolean> clickFilter) {
        MusicTrackItem item = new MusicTrackItem(parent.getContext());
        item.setRowAnimationsEnabled(false);
        item.setShowPusherInfo(false);
        if (item.getBackground() == null) {
            InsetBackgroundFactory.builder()
                    .cornerRadius(item.dp(7))
                    .inset(item.dp(1))
                    .padding(new InsetBackgroundFactory.Padding(item.dp(4), item.dp(4), item.dp(4), item.dp(4)))
                    .build()
                    .applyBackgroundTo(item);
        }
        item.setClickable(true);
        Context context = parent.getContext();
        item.setOnClickListener(view -> {
            if (clickFilter.apply(view)) return;
            MusicDetail musicDetail = item.getMusicDetail();
            if (musicDetail == null) return;
            MusicService.getInstance().sendPushMusicToQueue(Traceable.of(musicDetail.getId()));
            String artistsName = musicDetail.getArtists().stream()
                    .map(Artist::getName).collect(Collectors.joining(" / "));
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlayQueue") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        return item;
    }
}
