package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;

import java.util.Queue;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Home page body: the large lyric column. The play queue and idle sources live in
 * {@link indi.etern.musichud.client.ui.components.SidebarView}.
 */
public class HomeView extends LinearLayout {
    private final ClientConfig clientConfig = ClientConfig.getInstance();
    @Getter
    private static HomeView instance;
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

        boolean enabled = clientConfig.getEnable();
        if (ConnectionStateMachine.getConnectStatus() != ConnectionStateMachine.ConnectStatus.CONNECTED
                && !clientConfig.getEnableIsolatedMode() || !enabled) {
            staggeredLyricScrollView = null;
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        setGravity(Gravity.START);
        setOrientation(VERTICAL);
        staggeredLyricScrollView = new StaggeredLyricScrollView(context);
        addView(staggeredLyricScrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    public void switchMusic(Traceable<MusicDetail> musicDetail, Queue<LyricLine> lyricLines) {
        MuiModApi.postToUiThread(() -> {
            if (staggeredLyricScrollView != null) {
                staggeredLyricScrollView.switchLyrics(
                        musicDetail == null ? MusicDetail.NONE : musicDetail.value(), lyricLines);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 组件内部已处理资源清理，只需将 instance 置空
        if (instance == this) {
            instance = null;
        }
    }
}
