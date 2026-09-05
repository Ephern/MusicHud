package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
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

import java.util.stream.Collectors;

public class MusicListFactory {
    /**
     * 创建并配置一个 MusicListItem (含背景、点击推歌逻辑)。
     * 视图回收/复用由 VirtualizedListLayout 管理, 复用前需 clearData。
     */
    public static MusicTrackItem createItem(ViewGroup parent) {
        MusicTrackItem item = new MusicTrackItem(parent.getContext());
        item.setRowAnimationsEnabled(false);
        item.setShowPusherInfo(false);
        if (item.getBackground() == null) {
            InsetBackgroundFactory.builder()
                    .cornerRadius(item.dp(12))
                    .inset(item.dp(1))
                    .padding(new InsetBackgroundFactory.Padding(item.dp(4), item.dp(4), item.dp(4), item.dp(4)))
                    .build()
                    .applyBackgroundTo(item);
        }
        item.setClickable(true);
        Context context = parent.getContext();
        item.setOnClickListener(view -> {
            MusicDetail musicDetail = item.getMusicDetail();
            if (musicDetail == null) return;
            MusicService.getInstance().sendPushMusicToQueue(Traceable.of(musicDetail.getId()));
            String artistsName = musicDetail.getArtists().stream()
                    .map(Artist::getName).collect(Collectors.joining(" / "));
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        return item;
    }
}
