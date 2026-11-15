package indi.etern.musichud.client.ui.layout;

import java.util.ArrayList;
import java.util.List;

public final class Layouts {
    private Layouts() {
    }

    public static BoxSpec hbox(java.util.function.Consumer<BoxBuilder> spec) {
        return box(FlexDirection.ROW, spec);
    }

    public static BoxSpec vbox(java.util.function.Consumer<BoxBuilder> spec) {
        return box(FlexDirection.COLUMN, spec);
    }

    public static BoxSpec box(FlexDirection direction, java.util.function.Consumer<BoxBuilder> spec) {
        BoxBuilder builder = new BoxBuilder(direction);
        spec.accept(builder);
        return builder.build();
    }

    public static LeafSpec leaf(String elementId) {
        return new LeafSpec(elementId, BoxStyleSpec.DEFAULT);
    }

    public static LeafSpec leaf(String elementId, java.util.function.Consumer<LeafBuilder> spec) {
        LeafBuilder builder = new LeafBuilder(elementId);
        spec.accept(builder);
        return builder.build();
    }

    public static final class BoxBuilder extends StyleBuilder<BoxBuilder> {
        private final FlexDirection direction;
        private final List<LayoutSpec> children = new ArrayList<>();

        private BoxBuilder(FlexDirection direction) {
            this.direction = direction;
        }

        public BoxBuilder child(LayoutSpec child) {
            children.add(child);
            return this;
        }

        public BoxSpec build() {
            return new BoxSpec(direction, style.build(), children);
        }

        @Override
        protected BoxBuilder self() {
            return this;
        }
    }

    public static final class LeafBuilder extends StyleBuilder<LeafBuilder> {
        private final String elementId;

        private LeafBuilder(String elementId) {
            this.elementId = elementId;
        }

        public LeafSpec build() {
            return new LeafSpec(elementId, style.build());
        }

        @Override
        protected LeafBuilder self() {
            return this;
        }
    }

    public abstract static class StyleBuilder<B extends StyleBuilder<B>> {
        protected final BoxStyleBuilder style = new BoxStyleBuilder();

        public B padding(float value) {
            style.padding(value);
            return self();
        }

        public B padding(float left, float top, float right, float bottom) {
            style.padding(left, top, right, bottom);
            return self();
        }

        public B padding(Insets value) {
            style.padding(value);
            return self();
        }

        public B margin(float value) {
            style.margin(value);
            return self();
        }

        public B margin(float left, float top, float right, float bottom) {
            style.margin(left, top, right, bottom);
            return self();
        }

        public B margin(Insets value) {
            style.margin(value);
            return self();
        }

        public B gap(float value) {
            style.gap(value);
            return self();
        }

        public B width(SizeSpec value) {
            style.width(value);
            return self();
        }

        public B width(float value) {
            style.width(value);
            return self();
        }

        public B widthPercent(float value) {
            style.widthPercent(value);
            return self();
        }

        public B widthContent() {
            style.widthContent();
            return self();
        }

        public B height(SizeSpec value) {
            style.height(value);
            return self();
        }

        public B height(float value) {
            style.height(value);
            return self();
        }

        public B heightPercent(float value) {
            style.heightPercent(value);
            return self();
        }

        public B heightContent() {
            style.heightContent();
            return self();
        }

        public B grow(float value) {
            style.grow(value);
            return self();
        }

        public B shrink(float value) {
            style.shrink(value);
            return self();
        }

        public B overflowOrder(int value) {
            style.overflowOrder(value);
            return self();
        }

        public B hideOnOverflow(boolean value) {
            style.hideOnOverflow(value);
            return self();
        }

        public B collapseWhenEmptyAndTrailing(boolean value) {
            style.collapseWhenEmptyAndTrailing(value);
            return self();
        }

        public B aspectRatio(float value) {
            style.aspectRatio(value);
            return self();
        }

        public B alignItems(AlignItems value) {
            style.alignItems(value);
            return self();
        }

        public B justifyContent(JustifyContent value) {
            style.justifyContent(value);
            return self();
        }

        public B alignSelf(AlignSelf value) {
            style.alignSelf(value);
            return self();
        }

        public B visible(boolean value) {
            style.visible(value);
            return self();
        }

        public B minWidth(float value) {
            style.minWidth(value);
            return self();
        }

        public B maxWidth(float value) {
            style.maxWidth(value);
            return self();
        }

        public B minHeight(float value) {
            style.minHeight(value);
            return self();
        }

        public B maxHeight(float value) {
            style.maxHeight(value);
            return self();
        }

        protected abstract B self();
    }

    public static final class BoxStyleBuilder {
        private Insets padding = Insets.NONE;
        private Insets margin = Insets.NONE;
        private float gap;
        private SizeSpec width = SizeSpec.AUTO;
        private SizeSpec height = SizeSpec.AUTO;
        private float flexGrow;
        private float flexShrink;
        private int overflowOrder;
        private boolean hideOnOverflow;
        private boolean collapseWhenEmptyAndTrailing;
        private float aspectRatio;
        private AlignItems alignItems = AlignItems.START;
        private JustifyContent justifyContent = JustifyContent.START;
        private AlignSelf alignSelf = AlignSelf.AUTO;
        private boolean visible = true;
        private float minWidth;
        private float maxWidth = Float.MAX_VALUE;
        private float minHeight;
        private float maxHeight = Float.MAX_VALUE;

        public BoxStyleBuilder padding(float value) {
            return padding(value, value, value, value);
        }

        public BoxStyleBuilder padding(float left, float top, float right, float bottom) {
            padding = new Insets(left, top, right, bottom);
            return this;
        }

        public BoxStyleBuilder padding(Insets value) {
            padding = value;
            return this;
        }

        public BoxStyleBuilder margin(float value) {
            return margin(value, value, value, value);
        }

        public BoxStyleBuilder margin(float left, float top, float right, float bottom) {
            margin = new Insets(left, top, right, bottom);
            return this;
        }

        public BoxStyleBuilder margin(Insets value) {
            margin = value;
            return this;
        }

        public BoxStyleBuilder gap(float value) {
            gap = value;
            return this;
        }

        public BoxStyleBuilder width(SizeSpec value) {
            width = value;
            return this;
        }

        public BoxStyleBuilder width(float value) {
            width = SizeSpec.fixed(value);
            return this;
        }

        public BoxStyleBuilder widthPercent(float value) {
            width = SizeSpec.percent(value);
            return this;
        }

        public BoxStyleBuilder widthContent() {
            width = SizeSpec.CONTENT;
            return this;
        }

        public BoxStyleBuilder height(SizeSpec value) {
            height = value;
            return this;
        }

        public BoxStyleBuilder height(float value) {
            height = SizeSpec.fixed(value);
            return this;
        }

        public BoxStyleBuilder heightPercent(float value) {
            height = SizeSpec.percent(value);
            return this;
        }

        public BoxStyleBuilder heightContent() {
            height = SizeSpec.CONTENT;
            return this;
        }

        public BoxStyleBuilder grow(float value) {
            flexGrow = value;
            return this;
        }

        public BoxStyleBuilder shrink(float value) {
            flexShrink = value;
            return this;
        }

        public BoxStyleBuilder overflowOrder(int value) {
            overflowOrder = value;
            return this;
        }

        public BoxStyleBuilder hideOnOverflow(boolean value) {
            hideOnOverflow = value;
            return this;
        }

        public BoxStyleBuilder collapseWhenEmptyAndTrailing(boolean value) {
            collapseWhenEmptyAndTrailing = value;
            return this;
        }

        public BoxStyleBuilder aspectRatio(float value) {
            aspectRatio = value;
            return this;
        }

        public BoxStyleBuilder alignItems(AlignItems value) {
            alignItems = value;
            return this;
        }

        public BoxStyleBuilder justifyContent(JustifyContent value) {
            justifyContent = value;
            return this;
        }

        public BoxStyleBuilder alignSelf(AlignSelf value) {
            alignSelf = value;
            return this;
        }

        public BoxStyleBuilder visible(boolean value) {
            visible = value;
            return this;
        }

        public BoxStyleBuilder minWidth(float value) {
            minWidth = value;
            return this;
        }

        public BoxStyleBuilder maxWidth(float value) {
            maxWidth = value;
            return this;
        }

        public BoxStyleBuilder minHeight(float value) {
            minHeight = value;
            return this;
        }

        public BoxStyleBuilder maxHeight(float value) {
            maxHeight = value;
            return this;
        }

        public BoxStyleSpec build() {
            return new BoxStyleSpec(padding, margin, gap, width, height, flexGrow, flexShrink,
                    overflowOrder, hideOnOverflow, collapseWhenEmptyAndTrailing, aspectRatio,
                    alignItems, justifyContent, alignSelf, visible, minWidth, maxWidth, minHeight, maxHeight);
        }
    }
}
