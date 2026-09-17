package indi.etern.musichud.client.ui.layouts;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.ViewGroup;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.ui.components.MusicListFactory;
import indi.etern.musichud.client.ui.components.MusicTrackItem;

/** {@link MusicDetail} specialization used by music collection pages. */
public class MusicVirtualizedListLayout extends VirtualizedListLayout<MusicDetail, MusicTrackItem> {
    public MusicVirtualizedListLayout(Context context) {
        super(context, new Adapter<>() {
            @Override
            public long idOf(MusicDetail item) {
                return item.getId();
            }

            @Override
            public MusicTrackItem createItem(ViewGroup parent) {
                return MusicListFactory.createItem(parent);
            }

            @Override
            public void clearItem(MusicTrackItem view) {
                view.clearData();
            }

            @Override
            public void bindItem(MusicTrackItem view, MusicDetail item) {
                view.bindData(item);
            }

            @Override
            public long boundIdOf(MusicTrackItem view) {
                return view.getMusicDetail() == null ? -1 : view.getMusicDetail().getId();
            }
        });
    }
}
