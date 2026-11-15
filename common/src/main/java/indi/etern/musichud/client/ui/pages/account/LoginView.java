package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.PagerAdapter;
import icyllis.modernui.widget.ViewPager;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.components.SegmentedBar;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LoginView extends FrameLayout implements ILoginView {
    @Getter
    private static LoginView instance;
    @Getter
    private final ViewPager pager;
    private final ILoginView[] loginViews = new ILoginView[2];
    private final SegmentedBar tabBar;

    public LoginView(Context context) {
        super(context);

        instance = this;
        pager = new ViewPager(context);
        {
            pager.setAdapter(new Adapter());
            pager.setFocusableInTouchMode(true);
            pager.setKeyboardNavigationCluster(true);

            OneShotPreDrawListener.add(pager, () -> {
                //noinspection UnstableApiUsage
                var animator = ObjectAnimator.ofFloat(pager,
                        View.ROTATION_Y, pager.isLayoutRtl() ? -45 : 45, 0);
                animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
                animator.start();
            });
        }

        tabBar = new SegmentedBar(context);
        tabBar.addItem(new SegmentedBar.Item("qrCode", null,
                "/assets/music_hud/textures/gui/icons/qr_code.png",
                I18n.get(MusicHud.MOD_ID + ".text.login.page.qrCode")));
        tabBar.addItem(new SegmentedBar.Item("deviceCode", null,
                "/assets/music_hud/textures/gui/icons/smartphone.png",
                I18n.get(MusicHud.MOD_ID + ".text.login.page.deviceCode")));
        int dp2 = dp(2);
        PanelStyle.applyDynamically(tabBar, dp(16),
                new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        var lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tabBar.setLayoutParams(lp);
        addView(tabBar);

        tabBar.setOnItemSelectedListener((key, index) -> pager.setCurrentItem(index, true));
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                tabBar.setSelectedIndex(position, true);
            }
        });

        var pagerLp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        pagerLp.gravity = Gravity.TOP;
        pagerLp.topMargin = dp(128);
        addView(pager, pagerLp);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
            }
        });
    }

    @Override
    public void reset() {
        ILoginView currentLoginView = loginViews[pager.getCurrentItem()];
        if (currentLoginView != null) {
            currentLoginView.reset();
        }
    }

    @Override
    public void errorText(String message) {
        ILoginView currentLoginView = loginViews[pager.getCurrentItem()];
        if (currentLoginView != null) {
            currentLoginView.errorText(message);
        }
    }

    private class Adapter extends PagerAdapter {
        @Override
        public int getCount() {
            return 2;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            var context = container.getContext();

            //noinspection UnstableApiUsage
            ClampingScrollView sv = new ClampingScrollView(context);

            container.addView(sv);

            ILoginView layout = switch (position) {
                case 0 -> new QRLoginView(context);
                case 1 -> new PhoneCodeLoginView(context);
                default -> new QRLoginView(context);
            };
            loginViews[position] = layout;

            var vgParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            vgParams.gravity = Gravity.CENTER_HORIZONTAL;
            sv.addView((View) layout, vgParams);

            return sv;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            View view = (View) object;
            container.removeView(view);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }
}