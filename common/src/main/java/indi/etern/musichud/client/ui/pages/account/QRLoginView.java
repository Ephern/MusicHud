package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.requestResponseCycle.CancelQRLoginRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.StartQRLoginRequest;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class QRLoginView extends LinearLayout implements ILoginView{
    private final Button loginButton;
    private final UrlImageView urlImageView;
    private final TextView messageTextView;
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();

    public QRLoginView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        TextView textView = new TextView(context);
        textView.setTextSize(Theme.TEXT_SIZE_LARGE);
        textView.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        textView.setText(I18n.get(MusicHud.MOD_ID + ".text.login.qrCode"));
        textView.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        TextView textView1 = new TextView(context);
        textView1.setTextSize(Theme.TEXT_SIZE_NORMAL);
        textView1.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        textView1.setText(I18n.get(MusicHud.MOD_ID + ".text.login.description"));
        LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params1.setMargins(0, dp(4), 0, 0);
        textView1.setLayoutParams(params1);

        urlImageView = new UrlImageView(context);
        LayoutParams imageParams = new LayoutParams(dp(160), dp(160));
        imageParams.setMargins(0, dp(32), 0, 0);
        urlImageView.setLayoutParams(imageParams);

        loginButton = new Button(context);
        loginButton.setTextColor(Theme.PRIMARY_COLOR);
        loginButton.setHeight(dp(36));
        loginButton.setWidth(dp(84));
        loginButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        loginButton.setText(I18n.get(MusicHud.MOD_ID + ".button.loadQRCode"));

        messageTextView = new TextView(context);
        messageTextView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        messageTextView.setMaxWidth(dp(400));
        messageTextView.setMinHeight(36);
        messageTextView.setSingleLine(false);
        LayoutParams messageParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        messageParams.setMargins(0, dp(8), 0, 0);
        messageTextView.setLayoutParams(messageParams);
        messageTextView.setVisibility(View.GONE);
        messageTextView.setGravity(Gravity.CENTER_HORIZONTAL);

        var background = ButtonInsetBackgroundFactory.builder()
                .padding(new ButtonInsetBackgroundFactory.Padding(0,0,0,0))
                .cornerRadius(dp(4)).inset(dp(1)).build().newBackgroundDrawable();
        loginButton.setBackground(background);
        LayoutParams buttonParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        buttonParams.setMargins(0, dp(8), 0, 0);
        loginButton.setLayoutParams(buttonParams);
        loginButton.setOnClickListener((view) -> {
            MuiModApi.postToUiThread(() -> {
                loginButton.setVisibility(GONE);
                messageTextView.setVisibility(GONE);
                urlImageView.setLoading(true);
            });
            LoginService.getInstance().setLoginResponseHandler((qrLoginResponse) -> {
                MuiModApi.postToUiThread(() -> {
                    urlImageView.loadUrl(qrLoginResponse.base64QRImg());
                });
            });
            clientNetworkService.sendToServer(StartQRLoginRequest.REQUEST);
        });

        addView(textView);
        addView(textView1);
        addView(loginButton);
        addView(messageTextView);
        addView(urlImageView);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED) {
                    clientNetworkService.sendToServer(CancelQRLoginRequest.REQUEST);
                }
            }
        });
    }

    @Override
    public void reset() {
        loginButton.setVisibility(VISIBLE);
        urlImageView.clear();
        messageTextView.setVisibility(GONE);
    }

    @Override
    public void errorText(String message) {
        messageTextView.setTextColor(Theme.ERROR_TEXT_COLOR);
        messageTextView.setVisibility(View.VISIBLE);
        messageTextView.setText(message);
    }
}