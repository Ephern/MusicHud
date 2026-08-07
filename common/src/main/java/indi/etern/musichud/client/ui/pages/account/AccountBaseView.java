package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AccountBaseView extends LinearLayout {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    @Getter
    static volatile AccountBaseView instance;
    private Status status = null;
    private ProgressBar loadingRing;
    private TextView loadingErrorText;
    private Button retryButton;
    private Button logoutButton;

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
        if (ConnectionStateMachine.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED
                && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
            status1 = Status.UNAVAILABLE;
        } else if (LoginService.getInstance().isLogined()) {
            status1 = Status.LOGGED;
        } else if (LoginService.getInstance().hasPreviousLoginInfo()) {
            status1 = Status.LOADING;
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
            } else if (status == Status.LOADING) {
                view.setGravity(Gravity.CENTER);
                buildLoadingView(view);
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

    private void buildLoadingView(LinearLayout view) {
        Context context = getContext();

        loadingRing = new ProgressBar(context);
        loadingRing.setIndeterminate(true);
        view.addView(loadingRing, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        loadingErrorText = new TextView(context);
        loadingErrorText.setText(I18n.get(MusicHud.MOD_ID + ".error.getAccountInfo"));
        loadingErrorText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        loadingErrorText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        loadingErrorText.setVisibility(GONE);
        LayoutParams textParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        textParams.setMargins(0, 0, 0, dp(16));
        view.addView(loadingErrorText, textParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        view.addView(buttons);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        buttons.setLayoutTransition(transition);

        view.addView(new View(context), new LayoutParams(MATCH_PARENT, dp(32)));

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .inset(dp(1)).cornerRadius(dp(4))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(16), dp(8), dp(16), dp(8)))
                .build();

        retryButton = new Button(context);
        retryButton.setText(I18n.get(MusicHud.MOD_ID + ".button.retry"));
        retryButton.setTextColor(Theme.PRIMARY_COLOR);
        retryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        retryButton.setBackground(backgroundFactory.newBackgroundDrawable());
        retryButton.setOnClickListener(v -> {
            loadingErrorText.setVisibility(GONE);
            retryButton.setVisibility(GONE);
            loadingRing.setVisibility(VISIBLE);
            LoginService.getInstance().clearLastLoginErrorMessage();
            LoginService.getInstance().loginToServer();
        });
        buttons.addView(retryButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        logoutButton = new Button(context);
        logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
        logoutButton.setTextColor(Theme.PRIMARY_COLOR);
        logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        logoutButton.setBackground(backgroundFactory.newBackgroundDrawable());
        logoutButton.setOnClickListener(v -> {
            loadingErrorText.setVisibility(GONE);
            logoutButton.setVisibility(GONE);
            loadingRing.setVisibility(VISIBLE);
            LoginService.getInstance().clearLastLoginErrorMessage();
            LoginService.getInstance().logoutAndReloginAsAnonymous();
        });
        buttons.addView(logoutButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        String errorMessage = LoginService.getInstance().getLastLoginErrorMessage();
        if (errorMessage != null) {
            showLoadingError(errorMessage);
        }
    }

    private void showLoadingError(String message) {
        if (loadingRing == null || loadingErrorText == null || retryButton == null || logoutButton == null) {
            return;
        }
        if (message == null || message.isEmpty()) {
            message = I18n.get(MusicHud.MOD_ID + ".error.getAccountInfo");
        }
        loadingErrorText.setText(message);
        loadingRing.setVisibility(GONE);
        loadingErrorText.setVisibility(VISIBLE);
        retryButton.setVisibility(VISIBLE);
        logoutButton.setVisibility(VISIBLE);
    }

    /**
     * Called on UI thread when login fails while in LOADING state,
     * switches the loading ring to an error hint with retry/logout actions.
     */
    public void onLoginFailed(String message) {
        if (status != Status.LOADING) {
            return;
        }
        showLoadingError(message);
    }


    private enum Status {
        UNAVAILABLE,
        UNLOGGED,
        LOADING,
        LOGGED
    }
}