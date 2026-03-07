package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.animation.MotionEasingUtils;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.OneShotPreDrawListener;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.client.ui.Theme;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class SearchResultTabPage extends FrameLayout {
    @Getter
    private final ViewPager pager;

    public SearchResultTabPage(Context context) {
        super(context);

        pager = new ViewPager(context);
        {
            pager.setAdapter(new ResultAdapter());
            pager.setFocusableInTouchMode(true);
            pager.setKeyboardNavigationCluster(true);

            // 添加进入动画
            OneShotPreDrawListener.add(pager, () -> {
                var animator = ObjectAnimator.ofFloat(pager,
                        View.ROTATION_Y, pager.isLayoutRtl() ? -45 : 45, 0);
                animator.setInterpolator(MotionEasingUtils.MOTION_EASING_EMPHASIZED);
                animator.start();
            });
        }

        TabLayout tabLayout = new TabLayout(context);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NotNull TabLayout.Tab tab) {
                MusicHud.EXECUTOR.execute(() -> {
                    SearchView instance = SearchView.getInstance();
                    if (instance != null) {
                        instance.refreshSearch(false);
                    }
                });
            }
        });
        tabLayout.setElevation(dp(3));
        tabLayout.setTabMode(TabLayout.MODE_AUTO);
        tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabLayout.setupWithViewPager(pager);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0x00000000);
        tabLayout.setBackground(background);

        var lp = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tabLayout.setLayoutParams(lp);
        addView(tabLayout);

        // 添加 ViewPager 到剩余空间
        var pagerLp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        pagerLp.gravity = Gravity.TOP;
        pagerLp.topMargin = dp(48);
        addView(pager, pagerLp);

        addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                clearResult();
            }
        });
    }

    public void clearResult() {
        MuiModApi.postToUiThread(() -> {
            SearchAlbumResultView.setResult(null);
            SearchArtistResultView.setResult(null);
            SearchPlaylistResultView.setResult(null);
            SearchMusicResultView.setResult(null);
            if (pager.getAdapter() != null) {
                pager.getAdapter().notifyDataSetChanged();
            }
        });
    }

    private class ResultAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return 4; // 页面数量
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            final int maxWidth = dp(1000);
            var context = container.getContext();

            ClampingScrollView sv = new ClampingScrollView(context);
            sv.setMaxWidth(maxWidth);
            sv.setTag(position);

            ProgressBar loadingMoreProgressBar = new ProgressBar(context);
            loadingMoreProgressBar.setVisibility(GONE);
            loadingMoreProgressBar.setIndeterminate(true);

            TextView noMoreResultText = new TextView(getContext());
            noMoreResultText.setText(I18n.get("music_hud.text.searchNoMoreResult"));
            noMoreResultText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            noMoreResultText.setTextSize(Theme.TEXT_SIZE_NORMAL);
            noMoreResultText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            noMoreResultText.setVisibility(GONE);

            sv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                checkInfiniteScroll(scrollY, sv);
            });
            container.addView(sv);

            SearchType searchType;
            ViewGroup layout = switch (position) {
                case 0 -> {
                    searchType = SearchType.MUSIC;
                    yield new SearchMusicResultView(context);
                }
                case 1 -> {
                    searchType = SearchType.PLAYLIST;
                    yield new SearchPlaylistResultView(context);
                }
                case 2 -> {
                    searchType = SearchType.ALBUM;
                    yield new SearchAlbumResultView(context);
                }
                case 3 -> {
                    searchType = SearchType.ARTIST;
                    yield new SearchArtistResultView(context);
                }
                default -> {
                    searchType = SearchType.MUSIC;
                    yield new SearchMusicResultView(context);
                }
            };
            SearchView instance = SearchView.getInstance();
            if (instance != null) {
                instance.getSearchRefreshListeners().add((searchMeta) -> {
                    if (searchMeta.getSearchType() == searchType) {
                        CompletableFuture<SearchView.CompletingType> pendingFuture = searchMeta.pendingFuture;
                        checkFuture(noMoreResultText, loadingMoreProgressBar, pendingFuture);
                    }
                });
            }

            LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.VERTICAL);

            var vgParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            ll.addView(layout, vgParams);

            var progressParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            progressParams.setMargins(dp(4), dp(16), dp(4), dp(16));
            ll.addView(loadingMoreProgressBar, progressParams);

            LayoutParams tParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            tParams.setMargins(dp(4), dp(32), dp(4), dp(32));
            ll.addView(noMoreResultText, tParams);

            var llParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
            sv.addView(ll, llParams);

            return sv;
        }

        private void checkInfiniteScroll(int scrollY, ClampingScrollView sv) {
            if (sv.getChildCount() > 0) {
                View child = sv.getChildAt(0);
                int contentHeight = child.getHeight();
                int viewHeight = sv.getHeight();
                int maxScroll = Math.max(0, contentHeight - viewHeight);
                int threshold = sv.dp(100);

                if (maxScroll - scrollY <= threshold) {
                    SearchView instance = SearchView.getInstance();
                    if (instance != null) {
                        instance.loadMoreSearchResult();
                    }
                }
            }
        }

        @SneakyThrows
        private void checkFuture(TextView noMoreResultText, ProgressBar loadingProgressBar, CompletableFuture<SearchView.CompletingType> completableFuture) {
            if (!completableFuture.isDone()) {
                MuiModApi.postToUiThread(() -> {
                    noMoreResultText.setVisibility(GONE);
                    loadingProgressBar.setVisibility(VISIBLE);
                });
                completableFuture.thenAccept(result -> {
                    MuiModApi.postToUiThread(() -> {
                        if (result == SearchView.CompletingType.NO_MORE_RESULT) {
                            noMoreResultText.setVisibility(VISIBLE);
                            loadingProgressBar.setVisibility(GONE);
                        }
                    });
                });
            } else {
                SearchView.CompletingType result = completableFuture.get();
                if (result == SearchView.CompletingType.NO_MORE_RESULT) {
                    noMoreResultText.setVisibility(VISIBLE);
                    loadingProgressBar.setVisibility(GONE);
                }
            }
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            SearchType searchType = switch (position) {
                case 0 -> SearchType.MUSIC;
                case 1 -> SearchType.PLAYLIST;
                case 2 -> SearchType.ALBUM;
                case 3 -> SearchType.ARTIST;
                default -> SearchType.MUSIC;
            };
            SearchView instance = SearchView.getInstance();
            if (instance != null) {
                instance.getSearchMetas().remove(searchType);
            }

            ClampingScrollView view = (ClampingScrollView) object;
            container.removeView(view);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return I18n.get(switch (position) {
                case 0 -> "music_hud.text.page.search.music";
                case 1 -> "music_hud.text.page.search.playlist";
                case 2 -> "music_hud.text.page.search.album";
                case 3 -> "music_hud.text.page.search.artist";
                default -> "";
            });
        }
    }
}
