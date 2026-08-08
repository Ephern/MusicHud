package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RadioButton;
import icyllis.modernui.widget.RadioGroup;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.image.ImageUtils;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SideMenu extends FrameLayout {
    private final RadioGroup navBarButtonGroup;
    private final RouterContainer routerContainer;
    final LinkedHashMap<RadioButton, NavigationMeta> pagesMap1 = new LinkedHashMap<>();
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
        navBarButtonGroup.setOrientation(LinearLayout.VERTICAL);
        navBarButtonGroup.setGravity(Gravity.TOP);

        Runnable initIndicator = () -> {
            Optional<NavigationMeta> optionalNavigationMeta = pagesMap1.values().stream().skip(defaultSelectedIndex).findFirst();
            optionalNavigationMeta.ifPresent((navigationMeta) -> {
                RadioButton button = navigationMeta.button;
                indicator.setTranslationY(button.getY() / 2 + (float) button.getHeight() / 4 - (float) indicator.getHeight() / 4);
                button.setSelected(true);
                navBarButtonGroup.check(button.getId());
                routerContainer.navigateToRoot(navigationMeta.key, null);
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
                    routerContainer.navigateToRoot(pagesMap1.get(radioButton).key, null);
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
        private final String key;
        private final Function<Context, View> factory;
        private final RadioButton button;
        private final int index;

        public NavigationMeta(String key, Function<Context, View> factory, RadioButton button, int index) {
            this.key = key;
            this.factory = factory;
            this.button = button;
            this.index = index;
        }

        public void select() {
            navBarButtonGroup.check(button.getId());
            defaultSelectedIndex = index;
            routerContainer.navigateToRoot(key);
        }
    }

    private int indexCounter = 0;

    public NavigationMeta createNavigationPage(String key, @Nullable String iconResource, String buttonText, Function<Context, View> factory) {
        synchronized (this) {
            routerContainer.registerPage(key, factory);

            SpannableString text = new SpannableString("      " + buttonText);
            Image image = ImageUtils.getImageFromResource(iconResource);
            if (image != null) {
                ImageSpan span = ImageUtils.getIconSpan(image);
                text.setSpan(span, 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            var button = new RadioButton(Objects.requireNonNull(getContext()), null, null, null);
            button.setId(pagesMap1.size() + 1);
            button.setText(text);
            button.setClickable(true);
            button.setFocusable(true);
            button.setTextSize(Theme.TEXT_SIZE_LARGE);
            button.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            button.setGravity(Gravity.CENTER_VERTICAL);

            button.setHeight(button.dp(40));

            InsetBackgroundFactory.builder()
                    .padding(new InsetBackgroundFactory.Padding(button.dp(2), button.dp(1), button.dp(2), button.dp(1)))
                    .cornerRadius(button.dp(4)).inset(dp(1)).build()
                    .applyBackgroundTo(button);
            button.setLayoutParams(buttonParams);

            navBarButtonGroup.addView(button);
            NavigationMeta navigationMeta = new NavigationMeta(key, factory, button, indexCounter++);
//            pagesMap.put(key, navigationMeta);
            pagesMap1.put(button, navigationMeta);
            return navigationMeta;
        }
    }

}
