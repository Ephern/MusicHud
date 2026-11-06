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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Segmented control with a sliding indicator, used by both the top navigation bar and in-page tabs.
 * Purely presentational: user clicks are reported to the listener, and programmatic selection never
 * emits an event, so the owner of the content (router or pager) stays the single source of truth.
 */
public class SegmentedBar extends FrameLayout {
    private static final int ITEM_HEIGHT_DP = 40;
    private static final int ITEM_MIN_WIDTH_DP = 40;
    private static final int TEXT_ITEM_PADDING_DP = 12;
    private static final int INDICATOR_LENGTH_DP = 16;
    private static final int INDICATOR_THICKNESS_DP = 3;
    private static final int INDICATOR_EDGE_INSET_DP = 4;
    private static final int INDICATOR_SWITCH_DURATION = 300;

    private final RadioGroup buttonGroup;
    private final View indicator;
    private final ShapeDrawable indicatorShape;
    private final List<RadioButton> buttons = new ArrayList<>();
    private final Map<RadioButton, Item> itemsByButton = new LinkedHashMap<>();
    private final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);

    private @Nullable OnItemSelectedListener listener;
    @Getter
    private int selectedIndex = 0;
    private boolean laidOut;
    private boolean suppressCallback;

    public record Item(String key, @Nullable CharSequence label, @Nullable String iconResource,
                       @Nullable CharSequence tooltip) {
    }

    public interface OnItemSelectedListener {
        void onItemSelected(String key, int index);
    }

    public SegmentedBar(Context context) {
        super(context);
        setMinimumWidth(dp(ITEM_MIN_WIDTH_DP));

        buttonGroup = new RadioGroup(context);
        buttonGroup.setGravity(Gravity.TOP);

        indicator = new View(context);
        indicatorShape = new ShapeDrawable();
        indicatorShape.setCornerRadius(1000);
        indicatorShape.setColor(Theme.PRIMARY_COLOR);
        indicator.setBackground(indicatorShape);
        applyOrientation(LinearLayout.HORIZONTAL);

        addView(buttonGroup, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        addView(indicator);

        OneShotPreDrawListener.add(this, () -> {
            laidOut = true;
            if (!buttons.isEmpty()) {
                applyChecked(Math.min(selectedIndex, buttons.size() - 1), false);
            }
            buttonGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (suppressCallback) {
                    return;
                }
                View viewById = group.findViewById(checkedId);
                if (!(viewById instanceof RadioButton button)) {
                    return;
                }
                int index = buttons.indexOf(button);
                if (index < 0) {
                    return;
                }
                selectedIndex = index;
                moveIndicatorTo(button, true);
                if (listener != null) {
                    listener.onItemSelected(itemsByButton.get(button).key(), index);
                }
            });
        });
    }

    /**
     * Adds an item at the end. Items must be added during construction, before the first draw.
     *
     * @return the index of the new item
     */
    public int addItem(Item item) {
        int index = buttons.size();
        RadioButton button = new RadioButton(Objects.requireNonNull(getContext()), null, null, null);
        button.setId(index + 1);
        button.setText(buildLabel(item));
        button.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setTextSize(Theme.TEXT_SIZE_LARGE);
        button.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        button.setHeight(dp(ITEM_HEIGHT_DP));
        if (item.label() == null) {
            button.setWidth(dp(ITEM_MIN_WIDTH_DP));
            button.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            button.setMinWidth(dp(ITEM_MIN_WIDTH_DP));
            button.setGravity(Gravity.CENTER);
        }

        InsetBackgroundFactory.builder()
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(1), dp(2), dp(1)))
                .cornerRadius(dp(14)).inset(dp(1)).build()
                .applyBackgroundTo(button);
        if (item.label() != null) {
            button.setPadding(dp(TEXT_ITEM_PADDING_DP), 0, dp(TEXT_ITEM_PADDING_DP), 0);
        }
        if (item.tooltip() != null) {
            Tooltip.attach(button, item.tooltip());
        }
        button.setLayoutParams(buttonParams);

        buttons.add(button);
        itemsByButton.put(button, item);
        buttonGroup.addView(button);
        return index;
    }

    private CharSequence buildLabel(Item item) {
        Image image = item.iconResource() == null ? null : ImageUtils.getImageFromResource(item.iconResource());
        CharSequence label = item.label();
        if (image == null) {
            return label == null ? "" : label;
        }
        ImageSpan span = ImageUtils.getIconSpan(image);
        SpannableString text = new SpannableString(label == null ? " " : " " + label);
        text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener listener) {
        this.listener = listener;
    }

    public int getItemCount() {
        return buttons.size();
    }

    public @Nullable String getItemKey(int index) {
        if (index < 0 || index >= buttons.size()) {
            return null;
        }
        return itemsByButton.get(buttons.get(index)).key();
    }

    /**
     * Selects an item without emitting {@link OnItemSelectedListener}; callers that own the content
     * use this to mirror their own state.
     */
    public void setSelectedIndex(int index, boolean animate) {
        if (index < 0 || index >= buttons.size()) {
            return;
        }
        if (laidOut && index == selectedIndex && buttonGroup.getCheckedId() == buttons.get(index).getId()) {
            return;
        }
        selectedIndex = index;
        applyChecked(index, animate && laidOut);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Bounds may move without a selection change (e.g. GUI scale or width changes).
        if (changed) {
            moveIndicatorToChecked(false);
        }
    }

    private void applyChecked(int index, boolean animate) {
        RadioButton button = buttons.get(index);
        suppressCallback = true;
        try {
            if (buttonGroup.getCheckedId() != button.getId()) {
                buttonGroup.check(button.getId());
            }
        } finally {
            suppressCallback = false;
        }
        moveIndicatorTo(button, animate);
    }

    public int getOrientation() {
        return buttonGroup.getOrientation();
    }

    /**
     * Switches the bar to {@link LinearLayout#HORIZONTAL} (indicator below) or
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
        buttonGroup.setOrientation(orientation);
        boolean horizontal = orientation == LinearLayout.HORIZONTAL;
        int width = dp(horizontal ? INDICATOR_LENGTH_DP : INDICATOR_THICKNESS_DP);
        int height = dp(horizontal ? INDICATOR_THICKNESS_DP : INDICATOR_LENGTH_DP);
        indicatorShape.setSize(width, height);
        indicator.setLayoutParams(new LayoutParams(width, height));
    }

    private void moveIndicatorToChecked(boolean animate) {
        View checked = buttonGroup.findViewById(buttonGroup.getCheckedId());
        if (checked != null) {
            moveIndicatorTo(checked, animate);
        }
    }

    private void moveIndicatorTo(View button, boolean animate) {
        if (button.getWidth() <= 0 || indicator.getWidth() <= 0) {
            return;
        }
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
}
