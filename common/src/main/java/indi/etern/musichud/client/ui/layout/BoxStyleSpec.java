package indi.etern.musichud.client.ui.layout;

public record BoxStyleSpec(
        Insets padding,
        Insets margin,
        float gap,
        SizeSpec width,
        SizeSpec height,
        float flexGrow,
        float flexShrink,
        int overflowOrder,
        boolean hideOnOverflow,
        boolean collapseWhenEmptyAndTrailing,
        float aspectRatio,
        AlignItems alignItems,
        JustifyContent justifyContent,
        AlignSelf alignSelf,
        boolean visible,
        float minWidth,
        float maxWidth,
        float minHeight,
        float maxHeight
) {
    public static final BoxStyleSpec DEFAULT = new BoxStyleSpec(
            Insets.NONE, Insets.NONE, 0, SizeSpec.AUTO, SizeSpec.AUTO, 0, 0, 0, false, false, 0,
            AlignItems.START, JustifyContent.START, AlignSelf.AUTO, true,
            0, Float.MAX_VALUE, 0, Float.MAX_VALUE);
}
