package indi.etern.musichud.client.ui.pages;

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
import indi.etern.musichud.client.ui.components.AccountView;
import indi.etern.musichud.client.ui.components.QRLoginView;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AccountBaseView extends LinearLayout {
    @Getter
    static volatile AccountBaseView instance;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

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
        removeAllViews();

        var scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        scrollView.setFillViewport(true);
        addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(view, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        boolean enabled = clientConfig.getEnable();
        if (MusicHud.getStatus() != MusicHud.ConnectStatus.CONNECTED || !enabled) {
            view.setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            view.addView(textView);
        } else {
            setGravity(Gravity.CENTER_HORIZONTAL);
            if (LoginService.getInstance().isLogined()) {
                AccountView accountView = new AccountView(context);
                view.addView(accountView);
            } else {
                QRLoginView qrLoginView = new QRLoginView(context);
                LayoutParams loginParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                loginParams.setMargins(0, qrLoginView.dp(120), 0, 0);
                qrLoginView.setLayoutParams(loginParams);
                view.addView(qrLoginView);
            }
        }
    }
}