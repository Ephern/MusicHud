package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
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

public class NavigatorWidget extends FrameLayout {
    private static final int INDICATOR_LENGTH_DP = 16;
    private static final int INDICATOR_THICKNESS_DP = 3;
    private static final int INDICATOR_EDGE_INSET_DP = 4;
    private static final int INDICATOR_SWITCH_DURATION = 300;

    private final RadioGroup navBarButtonGroup;
    private final RouterContainer routerContainer;
    private final View indicator;
    private final ShapeDrawable indicatorShape;
    final LinkedHashMap<RadioButton, NavigationMeta> pagesMap1 = new LinkedHashMap<>();
    private int defaultSelectedIndex = 0;
    private final
    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);

    public NavigatorWidget(Context context, RouterContainer routerContainer) {
        super(context);
        this.routerContainer = routerContainer;
        setMinimumWidth(dp(40));

        navBarButtonGroup = new RadioGroup(context);
        navBarButtonGroup.setGravity(Gravity.TOP);

        indicator = new View(context);
        indicatorShape = new ShapeDrawable();
        indicatorShape.setCornerRadius(1000);
        indicatorShape.setColor(Theme.PRIMARY_COLOR);
        indicator.setBackground(indicatorShape);
        applyOrientation(LinearLayout.HORIZONTAL);

        addView(navBarButtonGroup, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        addView(indicator);

        OneShotPreDrawListener.add(this, () -> {
            Optional<NavigationMeta> optionalNavigationMeta = pagesMap1.values().stream().skip(defaultSelectedIndex).findFirst();
            optionalNavigationMeta.ifPresent((navigationMeta) -> {
                RadioButton button = navigationMeta.button;
                button.setSelected(true);
                navBarButtonGroup.check(button.getId());
                routerContainer.navigateToRoot(navigationMeta.key, null);
                moveIndicatorTo(button, false);
            });

            navBarButtonGroup.setOnCheckedChangeListener((group, checkedId) -> {
                View viewById = group.findViewById(checkedId);
                if (viewById instanceof RadioButton radioButton) {
                    moveIndicatorTo(radioButton, true);
                    routerContainer.navigateToRoot(pagesMap1.get(radioButton).key, null);
                }
            });
        });
    }

    public int getOrientation() {
        return navBarButtonGroup.getOrientation();
    }

    /**
     * Switches the navigation bar to {@link LinearLayout#HORIZONTAL} (indicator below) or
     * {@link LinearLayout#VERTICAL} (indicator on the left).
     */
    public void setOrientation(int orientation) {
        if (orientation != LinearLayout.HORIZONTAL && orientation != LinearLayout.VERTICAL) {
            throw new IllegalArgumentException("Unsupported orientation: " + orientation);
        }
        if (getOrientation() == orientation) {
            return;
        }
        applyOrientation(orientation);
        // Button bounds are only accurate after the layout pass requested above.
        OneShotPreDrawListener.add(this, () -> moveIndicatorToChecked(false));
    }

    private void applyOrientation(int orientation) {
        navBarButtonGroup.setOrientation(orientation);
        boolean horizontal = orientation == LinearLayout.HORIZONTAL;
        int width = dp(horizontal ? INDICATOR_LENGTH_DP : INDICATOR_THICKNESS_DP);
        int height = dp(horizontal ? INDICATOR_THICKNESS_DP : INDICATOR_LENGTH_DP);
        indicatorShape.setSize(width, height);
        indicator.setLayoutParams(new LayoutParams(width, height));
    }

    private void moveIndicatorToChecked(boolean animate) {
        View checked = navBarButtonGroup.findViewById(navBarButtonGroup.getCheckedId());
        if (checked != null) {
            moveIndicatorTo(checked, animate);
        }
    }

    private void moveIndicatorTo(View button, boolean animate) {
        boolean horizontal = getOrientation() == LinearLayout.HORIZONTAL;
        int inset = dp(INDICATOR_EDGE_INSET_DP);
        float x = horizontal
                ? button.getX() + (button.getWidth() - indicator.getWidth()) / 2f
                : button.getX() + inset;
        float y = horizontal
                ? button.getY() + button.getHeight() - indicator.getHeight() - inset
                : button.getY() + (button.getHeight() - indicator.getHeight()) / 2f;
        applyIndicatorOffset(x, y, animate);
    }

    /**
     * ModernUI applies view translations twice when drawing, so only half of the visual offset is stored.
     */
    private void applyIndicatorOffset(float x, float y, boolean animate) {
        float targetX = x / 2f;
        float targetY = y / 2f;
        if (!animate) {
            indicator.setTranslationX(targetX);
            indicator.setTranslationY(targetY);
            return;
        }
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(indicator,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, indicator.getTranslationX(), targetX),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, indicator.getTranslationY(), targetY));
        animator.setDuration(INDICATOR_SWITCH_DURATION);
        animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
        animator.start();
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

    public NavigationMeta createNavigationPage(String key, @Nullable String iconResource, String description, Function<Context, View> factory) {
        synchronized (this) {
            routerContainer.registerPage(key, factory);

            SpannableString text = new SpannableString(" ");
            Image image = ImageUtils.getImageFromResource(iconResource);
            if (image != null) {
                ImageSpan span = ImageUtils.getIconSpan(image);
                text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            var button = new RadioButton(Objects.requireNonNull(getContext()), null, null, null);
            button.setId(pagesMap1.size() + 1);
            button.setText(text);
            button.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            Tooltip.attach(button, description);
            button.setClickable(true);
            button.setFocusable(true);
            button.setTextSize(Theme.TEXT_SIZE_LARGE);
            button.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            button.setGravity(Gravity.CENTER_VERTICAL);

            button.setHeight(dp(40));
            button.setWidth(dp(40));

            InsetBackgroundFactory.builder()
                    .padding(new InsetBackgroundFactory.Padding(dp(2), dp(1), dp(2), dp(1)))
                    .cornerRadius(dp(14)).inset(dp(1)).build()
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
