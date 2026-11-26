package indi.etern.musichud.client.ui.components.options;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.music.QualityPriority;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.modals.Modal;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Config row showing the highest-priority quality. Clicking the ghost button opens a modal where
 * every quality is listed with an enable switch; rows are reordered by dragging the grip.
 */
public class QualityPriorityOption extends LinearLayout {
    private static final String GRIP_ICON = "/assets/music_hud/textures/gui/icons/grip_vertical.png";
    private static final long SHIFT_DURATION_MS = 220L;
    private static final SpringInterpolator SHIFT_SPRING =
            new SpringInterpolator(0.22f, 0.9f, 0f, SHIFT_DURATION_MS / 1000f);
    private static final long SETTLE_DURATION_MS = 360L;
    private static final SpringInterpolator SETTLE_SPRING =
            new SpringInterpolator(0.32f, 0.9f, 0f, SETTLE_DURATION_MS / 1000f);

    private final Supplier<QualityPriority> getter;
    private final Consumer<QualityPriority> setter;
    private final Runnable onChanged;
    private final TextView valueView;

    public QualityPriorityOption(Context context,
                                 Supplier<QualityPriority> getter,
                                 Consumer<QualityPriority> setter,
                                 Runnable onChanged) {
        super(context);
        this.getter = getter;
        this.setter = setter;
        this.onChanged = onChanged;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(44));
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        int dp6 = dp(6);
        params.setMargins(dp6, 0, dp6, 0);
        setLayoutParams(params);

        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".config.common.qualityPriority"));
        title.setTextSize(14);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        addView(title, titleParams);

        valueView = new TextView(context);
        valueView.setTextSize(14);
        valueView.setTextColor(Theme.NORMAL_TEXT_COLOR);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        valueParams.gravity = Gravity.CENTER_VERTICAL;
        valueParams.setMargins(dp6, 0, dp6, 0);
        addView(valueView, valueParams);

        Button edit = new Button(context);
        edit.setText(I18n.get(MusicHud.MOD_ID + ".config.common.qualityPriority.edit"));
        edit.setTextSize(14);
        edit.setTextColor(Theme.PRIMARY_COLOR);
        edit.setOnClickListener(v -> openEditor());
        InsetBackgroundFactory.builder().inset(0).cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(8), dp(4), dp(8), dp(4))).build()
                .applyBackgroundTo(edit);
        addView(edit, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        refreshValue();
    }

    public QualityPriorityOption create(ViewGroup parent) {
        if (parent != null) {
            parent.addView(this);
        }
        return this;
    }

    private void refreshValue() {
        valueView.setText(getter.get().first().toString());
    }

    private void openEditor() {
        Context context = getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(VERTICAL);

        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);

        DragLockScrollView scroll = new DragLockScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(420)));
        scroll.addView(rows, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        root.addView(scroll);

        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".config.common.qualityPriority.title"));

        Modal modal = new Modal(context, title, root,
                new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".config.common.qualityPriority.done"),
                        (button, m) -> m.dismiss()));
        new PriorityEditor(context, rows, scroll);
        modal.show();
    }

    /**
     * The modal's ScrollView must not intercept while a row is being dragged, otherwise the same
     * vertical gesture scrolls the list instead of moving the row.
     */
    private static final class DragLockScrollView extends ScrollView {
        private boolean dragLocked;

        private DragLockScrollView(Context context) {
            super(context);
        }

        private void setDragLocked(boolean locked) {
            dragLocked = locked;
            if (locked) {
                fling(0);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(@NotNull MotionEvent ev) {
            return !dragLocked && super.onInterceptTouchEvent(ev);
        }
    }

    private final class PriorityEditor {
        private final Context context;
        private final LinearLayout rows;
        private final DragLockScrollView scroll;
        private final List<Quality> working;
        private final Set<Quality> disabled;
        private final List<RowHolder> holders = new ArrayList<>();
        private final List<ObjectAnimator> shiftAnimators = new ArrayList<>();
        private boolean refreshPosted;
        private AnimatorSet settleAnimator;

        private PriorityEditor(Context context, LinearLayout rows, DragLockScrollView scroll) {
            this.context = context;
            this.rows = rows;
            this.scroll = scroll;
            this.working = new ArrayList<>(getter.get().order());
            this.disabled = new LinkedHashSet<>(getter.get().disabled());
            for (Quality quality : working) {
                RowHolder holder = new RowHolder(quality);
                holders.add(holder);
                rows.addView(holder.row);
            }
            refreshStates();
        }

        private int enabledCount() {
            return working.size() - disabled.size();
        }

        private void refreshStates() {
            for (RowHolder holder : holders) {
                holder.updateState();
            }
        }

        private void requestRefreshStates() {
            if (refreshPosted) {
                return;
            }
            refreshPosted = true;
            rows.post(() -> {
                refreshPosted = false;
                if (rows.isAttachedToWindow()) {
                    refreshStates();
                }
            });
        }

        private void persistOrder() {
            QualityPriority current = getter.get();
            QualityPriority next = new QualityPriority(
                    working, new ArrayList<>(disabled), current.allowUnblocked());
            setter.accept(next);
            working.clear();
            working.addAll(next.order());
            disabled.clear();
            disabled.addAll(next.disabled());
            refreshValue();
            if (onChanged != null) {
                onChanged.run();
            }
        }

        private void cancelSettle() {
            if (settleAnimator != null) {
                settleAnimator.cancel();
                settleAnimator = null;
            }
        }

        private void cancelShifts() {
            for (ObjectAnimator animator : shiftAnimators) {
                animator.cancel();
            }
            shiftAnimators.clear();
        }

        private void resetRowTransforms() {
            for (RowHolder holder : holders) {
                holder.row.setTranslationY(0);
                holder.row.setAlpha(1f);
                holder.row.setTranslationZ(0);
            }
        }

        /**
         * Flips the row views to their final order and springs every row from its visual position
         * at release to the new slot. Switches stay with their rows, so no state animation fires.
         */
        private void settle(int from, int to) {
            cancelSettle();
            cancelShifts();
            IdentityHashMap<RowHolder, Float> oldVisualTop = new IdentityHashMap<>();
            for (RowHolder holder : holders) {
                oldVisualTop.put(holder, holder.row.getTop() + holder.row.getTranslationY() * 2f);
            }

            RowHolder dragged = holders.get(from);
            if (from != to) {
                holders.remove(from);
                holders.add(to, dragged);
                Quality draggedQuality = working.remove(from);
                working.add(to, draggedQuality);
                rows.removeView(dragged.row);
                rows.addView(dragged.row, to);
            }

            IdentityHashMap<RowHolder, Integer> newTop = new IdentityHashMap<>();
            int y = 0;
            for (RowHolder holder : holders) {
                newTop.put(holder, y);
                y += holder.row.getHeight() > 0 ? holder.row.getHeight() : dp(44);
            }

            List<Animator> animators = new ArrayList<>();
            for (RowHolder holder : holders) {
                float start = (oldVisualTop.get(holder) - newTop.get(holder)) * 0.5f;
                holder.row.setTranslationY(start);
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        holder.row, View.TRANSLATION_Y, start, 0f);
                animator.setDuration(SETTLE_DURATION_MS);
                animator.setInterpolator(SETTLE_SPRING);
                animators.add(animator);
            }
            ObjectAnimator alpha = ObjectAnimator.ofFloat(
                    dragged.row, View.ALPHA, dragged.row.getAlpha(), 1f);
            alpha.setDuration(SETTLE_DURATION_MS);
            alpha.setInterpolator(SETTLE_SPRING);
            animators.add(alpha);
            dragged.row.setTranslationZ(0);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(animators);
            settleAnimator = set;
            set.start();

            persistOrder();
        }

        private void beginDrag(RowHolder holder) {
            cancelSettle();
            cancelShifts();
            resetRowTransforms();
            holder.row.setAlpha(0.85f);
            holder.row.setTranslationZ(dp(4));
        }

        private final class RowHolder {
            private final LinearLayout row;
            private final TextView name;
            private final Switch toggle;
            private final Quality quality;

            private RowHolder(Quality quality) {
                this.quality = quality;
                row = new LinearLayout(context);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(44));
                row.setPadding(dp(6), 0, dp(6), 0);
                row.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                ImageButton handle = new ImageButton(context);
                handle.setClickable(true);
                handle.setFocusable(false);
                handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                Image icon = ImageUtils.getImageFromResource(GRIP_ICON);
                if (icon != null) {
                    handle.setImageDrawable(new ScaledImageDrawable(
                            context.getResources(), icon, dp(16), dp(16)));
                }
                handle.setMinimumWidth(dp(32));
                handle.setMinimumHeight(dp(32));
                InsetBackgroundFactory.builder().cornerRadius(dp(4)).inset(0).build()
                        .applyBackgroundTo(handle);
                row.addView(handle, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

                name = new TextView(context);
                name.setText(quality.toString());
                name.setTextSize(Theme.TEXT_SIZE_NORMAL);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
                nameParams.setMargins(dp(6), 0, dp(8), 0);
                row.addView(name, nameParams);

                VipType mayRequired = quality.getMayRequired();
                if (!mayRequired.equals(VipType.NONE)) {
                    TextView mayRequiredPremiumLevel = new TextView(context);
                    mayRequiredPremiumLevel.setText(I18n.get(MusicHud.MOD_ID + ".text.vip." + mayRequired.name()));
                    mayRequiredPremiumLevel.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    mayRequiredPremiumLevel.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                    row.addView(mayRequiredPremiumLevel, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                }

                toggle = new Switch(context);
                LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params.setMargins(dp(16), 0, dp(8), 0);
                row.addView(toggle, params);

                toggle.setChecked(!disabled.contains(quality));
                toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    boolean enabled = !disabled.contains(quality);
                    if (isChecked == enabled) {
                        return;
                    }
                    if (!isChecked && enabledCount() <= 1) {
                        toggle.setChecked(true);
                        return;
                    }
                    if (isChecked) {
                        disabled.remove(quality);
                    } else {
                        disabled.add(quality);
                    }
                    persistOrder();
                    requestRefreshStates();
                });
                handle.setOnTouchListener(new DragTouchListener(row, this));
                updateState();
            }

            private void updateState() {
                boolean enabled = !disabled.contains(quality);
                name.setTextColor(enabled ? Theme.NORMAL_TEXT_COLOR : Theme.SECONDARY_TEXT_COLOR);
                toggle.setEnabled(!(enabled && enabledCount() == 1));
            }
        }

        private final class DragTouchListener implements View.OnTouchListener {
            private final LinearLayout row;
            private final RowHolder holder;
            private int startIndex;
            private int targetIndex;
            private float startRawY;

            private DragTouchListener(LinearLayout row, RowHolder holder) {
                this.row = row;
                this.holder = holder;
            }

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        startIndex = holders.indexOf(holder);
                        if (startIndex < 0) {
                            return false;
                        }
                        targetIndex = startIndex;
                        startRawY = event.getRawY();
                        beginDrag(holder);
                        view.setPressed(true);
                        scroll.setDragLocked(true);
                        ViewParent parent = rows.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        float dy = event.getRawY() - startRawY;
                        int rowHeight = Math.max(row.getHeight(), dp(44));
                        int target = Math.clamp(
                                startIndex + Math.round(dy / rowHeight),
                                0, holders.size() - 1);
                        if (target != targetIndex) {
                            targetIndex = target;
                            updateShifts(rowHeight);
                        }
                        // ModernUI 3.13 applies x/y translation twice; see StaggeredLyricScrollView.
                        row.setTranslationY(dy * 0.5f);
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.setPressed(false);
                        scroll.setDragLocked(false);
                        ViewParent parent = rows.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                        int from = startIndex;
                        int to = targetIndex;
                        rows.post(() -> {
                            if (rows.isAttachedToWindow()) {
                                settle(from, to);
                            }
                        });
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }

            private void updateShifts(int rowHeight) {
                cancelShifts();
                float shift = rowHeight * 0.5f;
                for (int i = 0; i < holders.size(); i++) {
                    LinearLayout child = holders.get(i).row;
                    if (child == row) {
                        continue;
                    }
                    float translation = 0f;
                    if (targetIndex > startIndex && i > startIndex && i <= targetIndex) {
                        translation = -shift;
                    } else if (targetIndex < startIndex && i >= targetIndex && i < startIndex) {
                        translation = shift;
                    }
                    if (Math.abs(child.getTranslationY() - translation) < 0.1f) {
                        child.setTranslationY(translation);
                        continue;
                    }
                    ObjectAnimator animator = ObjectAnimator.ofFloat(
                            child, View.TRANSLATION_Y, child.getTranslationY(), translation);
                    animator.setDuration(SHIFT_DURATION_MS);
                    animator.setInterpolator(SHIFT_SPRING);
                    shiftAnimators.add(animator);
                    animator.start();
                }
            }
        }
    }
}
