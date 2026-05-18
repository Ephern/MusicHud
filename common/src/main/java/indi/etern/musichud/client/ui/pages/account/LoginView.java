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
import icyllis.modernui.widget.TabLayout;
import icyllis.modernui.widget.ViewPager;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LoginView extends FrameLayout implements ILoginView{
    @Getter
    private static LoginView instance;
    @Getter
    private final ViewPager pager;
    private final ILoginView[] loginViews = new ILoginView[2];

    public LoginView(Context context) {
        super(context);

        instance = this;
        pager = new ViewPager(context);
        {
            pager.setAdapter(new Adapter());
            pager.setFocusableInTouchMode(true);
            pager.setKeyboardNavigationCluster(true);

            OneShotPreDrawListener.add(pager, () -> {
                var animator = ObjectAnimator.ofFloat(pager,
                        View.ROTATION_Y, pager.isLayoutRtl() ? -45 : 45, 0);
                animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
                animator.start();
            });
        }

        TabLayout tabLayout = new TabLayout(context);
        tabLayout.setElevation(dp(3));
        tabLayout.setTabMode(TabLayout.MODE_AUTO);
        tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabLayout.setupWithViewPager(pager);
        tabLayout.setBackground(null);

        var lp = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tabLayout.setLayoutParams(lp);
        addView(tabLayout);

        var pagerLp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        pagerLp.gravity = Gravity.TOP;
        pagerLp.topMargin = dp(64);
        addView(pager, pagerLp);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

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

        @Override
        public CharSequence getPageTitle(int position) {
            return I18n.get(switch (position) {
                case 0 -> "music_hud.text.login.page.qrCode";
                case 1 -> "music_hud.text.login.page.deviceCode";
                default -> "";
            });
        }
    }
}