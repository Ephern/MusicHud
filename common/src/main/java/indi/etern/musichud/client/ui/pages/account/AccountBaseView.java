package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class AccountBaseView extends LinearLayout {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    @Getter
    static volatile AccountBaseView instance;
    private Status status = null;

    public AccountBaseView(Context context) {
        super(context);
        try {
            instance = this;
            var baseParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            setLayoutParams(baseParams);
            setOrientation(LinearLayout.HORIZONTAL);

            refresh();
        } catch (Exception e) {
            instance = null;
            throw e;
        }
    }

    public void refresh() {
        Context context = getContext();
        boolean enabled = clientConfig.getEnable();

        Status status1;
        if (MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
            status1 = Status.UNAVAILABLE;
        } else if (LoginService.getInstance().isLogined()) {
            status1 = Status.LOGGED;
        } else {
            status1 = Status.UNLOGGED;
        }

        if (status != status1) {
            status = status1;
            removeAllViews();

            var scrollView = new ScrollView(context);
            scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            scrollView.setFillViewport(true);
            addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout view = new LinearLayout(context);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setGravity(Gravity.CENTER_HORIZONTAL);
            scrollView.addView(view, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            if (status == Status.UNAVAILABLE) {
                view.setGravity(Gravity.CENTER);
                TextView textView = Theme.getNotificationTextView(context, enabled);
                view.addView(textView);
            } else {
                setGravity(Gravity.CENTER_HORIZONTAL);
                if (status == Status.LOGGED) {
                    AccountView accountView = new AccountView(context);
                    view.addView(accountView);
                } else {
                    LoginView loginView = new LoginView(context);
                    LayoutParams loginParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
                    loginParams.setMargins(0, loginView.dp(120), 0, 0);
                    loginView.setLayoutParams(loginParams);
                    view.addView(loginView);
                }
            }
        }
    }


    private enum Status {
        UNAVAILABLE,
        UNLOGGED,
        LOGGED
    }
}