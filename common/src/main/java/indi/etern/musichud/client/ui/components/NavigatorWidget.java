package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.client.ui.components.SegmentedBar.Item;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class NavigatorWidget extends FrameLayout {
    private final RouterContainer routerContainer;
    private final SegmentedBar bar;
    private final List<NavigationMeta> metas = new ArrayList<>();
    private int defaultSelectedIndex = 0;

    public NavigatorWidget(Context context, RouterContainer routerContainer) {
        super(context);
        this.routerContainer = routerContainer;
        setMinimumWidth(dp(40));

        bar = new SegmentedBar(context);
        addView(bar, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        bar.setOnItemSelectedListener((key, index) -> routerContainer.navigateToRoot(key, null));

        OneShotPreDrawListener.add(this, () -> {
            if (metas.isEmpty()) {
                return;
            }
            NavigationMeta meta = metas.get(Math.min(defaultSelectedIndex, metas.size() - 1));
            bar.setSelectedIndex(meta.index, false);
            routerContainer.navigateToRoot(meta.key, null);
        });
    }

    public int getOrientation() {
        return bar.getOrientation();
    }

    /**
     * Switches the navigation bar to {@link LinearLayout#HORIZONTAL} (indicator below) or
     * {@link LinearLayout#VERTICAL} (indicator on the left).
     */
    public void setOrientation(int orientation) {
        bar.setOrientation(orientation);
    }

    @Getter
    public class NavigationMeta {
        private final String key;
        private final Function<Context, View> factory;
        private final int index;

        public NavigationMeta(String key, Function<Context, View> factory, int index) {
            this.key = key;
            this.factory = factory;
            this.index = index;
        }

        public void select() {
            bar.setSelectedIndex(index, false);
            defaultSelectedIndex = index;
            routerContainer.navigateToRoot(key);
        }
    }

    public NavigationMeta createNavigationPage(String key, @Nullable String iconResource, String description,
                                               Function<Context, View> factory) {
        routerContainer.registerPage(key, factory);
        int index = bar.addItem(new Item(key, null, iconResource, description));
        NavigationMeta navigationMeta = new NavigationMeta(key, factory, index);
        metas.add(navigationMeta);
        return navigationMeta;
    }
}
