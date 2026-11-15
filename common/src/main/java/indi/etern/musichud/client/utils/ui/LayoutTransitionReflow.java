package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/**
 * Replays the layout requests a running CHANGING transition silently drops: {@code ViewGroup.layout}
 * is skipped while {@code isChangingLayout()}, and neither the transition nor {@code suppressLayout}
 * requests a layout afterwards, so the container would keep its stale child positions.
 */
public final class LayoutTransitionReflow {
    private LayoutTransitionReflow() {
    }

    public static void attach(LayoutTransition transition) {
        transition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container,
                                        View view, int transitionType) {
            }

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container,
                                      View view, int transitionType) {
                // The child is removed from the changing set before this callback, so the request
                // is not suppressed by the transition itself.
                container.requestLayout();
            }
        });
    }
}
