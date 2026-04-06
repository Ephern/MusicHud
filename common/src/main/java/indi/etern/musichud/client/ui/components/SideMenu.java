package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RadioButton;
import icyllis.modernui.widget.RadioGroup;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SideMenu extends FrameLayout {
    private final RadioGroup navBarButtonGroup;
    private final RouterContainer routerContainer;
    LinkedHashMap<String, NavigationMeta> pagesMap = new LinkedHashMap<>();
    LinkedHashMap<RadioButton, NavigationMeta> pagesMap1 = new LinkedHashMap<>();
    private int defaultSelectedIndex = 0;
    private final
    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);


    public SideMenu(Context context, RouterContainer routerContainer) {
        super(context);
        this.routerContainer = routerContainer;
        var indicator = new View(context);
        indicator.setTranslationX(indicator.dp(2));
        ShapeDrawable indicatorShape = new ShapeDrawable();
        int indicatorWidth = indicator.dp(3);
        int indicatorHeight = indicator.dp(20);
        indicatorShape.setSize(indicatorWidth,indicatorHeight);
        indicatorShape.setCornerRadius(1000);
        indicatorShape.setColor(Theme.PRIMARY_COLOR);
        indicator.setBackground(indicatorShape);

        navBarButtonGroup = new RadioGroup(getContext());
        navBarButtonGroup.setOrientation(LinearLayout.VERTICAL);  // 竖向按钮组
        navBarButtonGroup.setGravity(Gravity.TOP);

        Runnable initIndicator = () -> {
            Optional<NavigationMeta> optionalNavigationMeta = pagesMap.values().stream().skip(defaultSelectedIndex).findFirst();
            optionalNavigationMeta.ifPresent((navigationMeta) -> {
                RadioButton button = navigationMeta.button;
                indicator.setTranslationY(button.getY() / 2 + (float) button.getHeight() / 4 - (float) indicator.getHeight() / 4);
                button.setSelected(true);
                navBarButtonGroup.check(button.getId());
                routerContainer.navigateToRoot(navigationMeta.name, null);
            });

            navBarButtonGroup.setOnCheckedChangeListener((group, checkedId) -> {
                View viewById = group.findViewById(checkedId);
                if (viewById instanceof RadioButton radioButton) {
                    ObjectAnimator animator = ObjectAnimator.ofFloat(
                            indicator,
                            View.TRANSLATION_Y,
                            indicator.getTranslationY(),
                            radioButton.getY() / 2 + (float) radioButton.getHeight() / 4 - (float) indicator.getHeight() / 4
                    );
                    animator.setDuration(300);
                    animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
                    animator.start();
                    animator.addUpdateListener(animation -> indicator.invalidate());
                    routerContainer.navigateToRoot(pagesMap1.get(radioButton).name, null);
                }
            });
        };

        OneShotPreDrawListener.add(this, initIndicator);
        var params1 = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        addView(navBarButtonGroup, params1);
        var params2 = new LayoutParams(indicatorWidth, indicatorHeight);
        addView(indicator, params2);
    }



    @Getter
    public class NavigationMeta {
        private final String name;
        private final Function<Context, View> factory;
        private final RadioButton button;
        private final int index;

        public NavigationMeta(String name, Function<Context, View> factory, RadioButton button, int index) {
            this.name = name;
            this.factory = factory;
            this.button = button;
            this.index = index;
        }

        public void select() {
            navBarButtonGroup.check(button.getId());
            defaultSelectedIndex = index;
            routerContainer.navigateToRoot(name);
        }
    }

    private int indexCounter = 0;

    public NavigationMeta createNavigationPage(String name, Function<Context, View> factory) {
        synchronized (this) {
            routerContainer.registerPage(name, factory);
            RadioButton button = createNavButton(pagesMap.size() + 1, name);
            navBarButtonGroup.addView(button);
            NavigationMeta navigationMeta = new NavigationMeta(name, factory, button, indexCounter++);
            pagesMap.put(name, navigationMeta);
            pagesMap1.put(button, navigationMeta);
            return navigationMeta;
        }
    }

    private RadioButton createNavButton(int id, String text) {
        var button = new RadioButton(Objects.requireNonNull(getContext()), null, null, null);
        button.setFocusable(true);
        button.setClickable(true);
        button.setId(id);
        button.setText("   " + text);
        button.setTextSize(Theme.TEXT_SIZE_LARGE);
        button.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        button.setGravity(Gravity.CENTER_VERTICAL);

        button.setHeight(button.dp(40));

        var background = ButtonInsetBackgroundFactory.builder()
                .padding(new ButtonInsetBackgroundFactory.Padding(button.dp(2),button.dp(1),button.dp(2),button.dp(1)))
                .cornerRadius(button.dp(4)).inset(dp(1)).build().newBackgroundDrawable();
        button.setBackground(background);
        button.setLayoutParams(buttonParams);

        return button;
    }

}
