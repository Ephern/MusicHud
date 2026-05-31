package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.ConfigItem;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.DynamicIntegerOption;
import indi.etern.musichud.client.ui.components.LyricLineView;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.ApiServerManager;
import indi.etern.musichud.server.api.ILoginApiService;
import indi.etern.musichud.server.api.MusicPlayerServerService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import org.apache.commons.lang3.Range;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class ConfigView extends LinearLayout {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    @Getter
    static volatile ConfigView instance;
    private final LoginService loginService = LoginService.getInstance();

    public ConfigView(Context context) {
        super(context);
        try {
            instance = this;

            var baseParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            setLayoutParams(baseParams);

            var scrollView = new ScrollView(context);
            scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            scrollView.setFillViewport(true);
            addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout view = new LinearLayout(context);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, dp(32), 0, 0);
            scrollView.addView(view, params);

            HudRendererManager hudRendererManager = HudRendererManager.getInstance();

            var commonCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.common"));
            PreferencesFragment.BooleanOption booleanOption = new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.enable"),
                    clientConfig::getEnable,
                    clientConfig::setEnable);
            booleanOption.create(commonCategory);
            booleanOption.setOnChanged(() -> {
                MuiModApi.postToUiThread(MainFragment::refresh);
                if (clientConfig.getEnable()) {
                    loginService.connectToExternalServer();
                } else {
                    loginService.disconnectToExternalOrIntegratedServer();
                }
            });
            PreferencesFragment.BooleanOption translatedLyricOption = new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.showTranslatedCnLyrics"),
                    clientConfig::getShowTranslatedCnLyrics,
                    clientConfig::setShowTranslatedCnLyrics);
            translatedLyricOption.create(commonCategory);
            translatedLyricOption.setOnChanged(() -> {
                HomeView homeView = HomeView.getInstance();
                if (homeView != null) {
                    StaggeredLyricScrollView staggeredLyricScrollView = homeView.getStaggeredLyricScrollView();
                    if (staggeredLyricScrollView != null) {
                        MuiModApi.postToUiThread(() -> {
                            staggeredLyricScrollView.getLyricLineViewList().forEach(LyricLineView::refreshSubLyricLine);
                        });
                    }
                }
            });
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.disableVanillaMusicWhilePlaying"),
                    clientConfig::getDisableVanillaMusic,
                    clientConfig::setDisableVanillaMusic)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.enableHud"),
                    clientConfig::getEnableHud,
                    clientConfig::setEnableHud)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.autoHide"),
                    clientConfig::getHideHudWhenNotPlaying,
                    clientConfig::setHideHudWhenNotPlaying)
                    .create(commonCategory);
            Quality[] qualities = {Quality.STANDARD, Quality.EX_HIGH, Quality.LOSSLESS, Quality.HIRES, Quality.JY_EFFECT, Quality.DOLBY, Quality.JY_MASTER, Quality.SKY};
            List<Quality> qualitiesList = Arrays.stream(qualities).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.primaryChosenQuality"),
                    qualities,
                    qualitiesList::indexOf,
                    clientConfig::getPrimaryChosenQuality,
                    clientConfig::setPrimaryChosenQuality)
                    .setDefaultValue(Quality.LOSSLESS)
                    .create(commonCategory);
            view.addView(commonCategory);
            var positionCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.layout"));
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.verticalAlign"),
                    VerticalAlign.values(),
                    VerticalAlign::ordinal,
                    clientConfig::getHudVerticalPosition,
                    clientConfig::setHudVerticalPosition)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                    })
                    .setDefaultValue(VerticalAlign.TOP)
                    .create(positionCategory);
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.horizontalAlign"),
                    HorizontalAlign.values(),
                    HorizontalAlign::ordinal,
                    clientConfig::getHudHorizontalPosition,
                    clientConfig::setHudHorizontalPosition)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                    })
                    .setDefaultValue(HorizontalAlign.LEFT)
                    .create(positionCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.offsetX"),
                    clientConfig::getHudOffsetX,
                    clientConfig::setHudOffsetX)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                    })
                    .setRange(0, 1920)
                    .setDefaultValue(16)
                    .create(positionCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.offsetY"),
                    clientConfig::getHudOffsetY,
                    clientConfig::setHudOffsetY)
                    .setRange(0, 1920)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                    })
                    .setDefaultValue(16)
                    .create(positionCategory);
            DynamicIntegerOption cornerRadiusOption = new DynamicIntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.hudCornerRadius"),
                    clientConfig::getHudCornerRadius,
                    clientConfig::setHudCornerRadius);
            cornerRadiusOption.setRange(0, clientConfig.getHudHeight() / 2);
            cornerRadiusOption.setOnChanged(() -> {
                hudRendererManager.updateLayoutFromConfig();
                hudRendererManager.refreshStyle();
            });
            cornerRadiusOption.setDefaultValue(8);
            DynamicIntegerOption widthOption = new DynamicIntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.hudWidth"),
                    clientConfig::getHudWidth,
                    clientConfig::setHudWidth);
            widthOption.setOnChanged(() -> {
                hudRendererManager.updateLayoutFromConfig();
                hudRendererManager.refreshStyle();
            });
            widthOption.setRange(clientConfig.getHudHeight(), 800, 4);
            widthOption.setDefaultValue(150);
            PreferencesFragment.IntegerOption heightOption = new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.hudHeight"),
                    clientConfig::getHudHeight,
                    clientConfig::setHudHeight)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                        cornerRadiusOption.updateRange(0, clientConfig.getHudHeight() / 2, 1);
                        widthOption.updateRange(clientConfig.getHudHeight(), 800, 4);
                    })
                    .setRange(16, 256, 2)
                    .setDefaultValue(44);
            widthOption.create(positionCategory);
            heightOption.create(positionCategory);
            cornerRadiusOption.create(positionCategory);
            view.addView(positionCategory);

            var multiplayerCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.externalServer"));
            PreferencesFragment.BooleanOption autoConnectToServerOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.autoConnect"),
                    clientConfig::getEnableAutoConnect,
                    clientConfig::setEnableAutoConnect)
                    .setDefaultValue(true);
            autoConnectToServerOption.create(multiplayerCategory);

            PreferencesFragment.BooleanOption enableIsolatedMode = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.enableIsolatedMode"),
                    clientConfig::getEnableIsolatedMode,
                    clientConfig::setEnableIsolatedMode)
                    .setDefaultValue(true);
            enableIsolatedMode.setOnChanged(() -> {
                if (MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED) {
                    if (clientConfig.getEnableIsolatedMode()) {
                        loginService.switchToIsolate();
                    } else {
                        loginService.disconnectToExternalOrIntegratedServer();
                    }
                }
            });
            enableIsolatedMode.create(multiplayerCategory);

            AutoConnectServerFilterType[] filterTypes = {AutoConnectServerFilterType.BLACK_LIST, AutoConnectServerFilterType.WHITE_LIST};
            List<AutoConnectServerFilterType> filterTypeList = Arrays.stream(filterTypes).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.serverFilterType"),
                    filterTypes,
                    filterTypeList::indexOf,
                    clientConfig::getConnectServerFilterType,
                    clientConfig::setConnectServerFilterType)
                    .setDefaultValue(AutoConnectServerFilterType.BLACK_LIST)
                    .create(multiplayerCategory);

            LinearLayout blackList = PreferencesFragment.createStringListOption(
                    context,
                    MusicHud.MOD_ID + ".config.externalServer.blackList",
                    new ConfigItem<>() {
                        @Override
                        public List<String> getPath() {
                            return List.of(MusicHud.MOD_ID, "config", "externalServer", "blackList");
                        }

                        @Override
                        public void set(List<? extends String> value) {
                            //noinspection unchecked
                            clientConfig.setBlackList((List<String>) value);
                        }

                        @Override
                        public List<? extends String> getDefault() {
                            return List.of();
                        }

                        @Override
                        public @Nullable Range<List<? extends String>> getRange() {
                            return null;
                        }

                        @Override
                        public List<? extends String> get() {
                            return clientConfig.getBlackList();
                        }
                    }, clientConfig::save);
            LinearLayout whiteList = PreferencesFragment.createStringListOption(
                    context,
                    MusicHud.MOD_ID + ".config.externalServer.whiteList",
                    new ConfigItem<>() {
                        @Override
                        public List<String> getPath() {
                            return List.of(MusicHud.MOD_ID, "config", "externalServer", "whiteList");
                        }

                        @Override
                        public void set(List<? extends String> value) {
                            //noinspection unchecked
                            clientConfig.setWhiteList((List<String>) value);
                        }

                        @Override
                        public List<? extends String> getDefault() {
                            return List.of();
                        }

                        @Override
                        public @Nullable Range<List<? extends String>> getRange() {
                            return null;
                        }

                        @Override
                        public List<? extends String> get() {
                            return clientConfig.getWhiteList();
                        }
                    }, clientConfig::save);
            multiplayerCategory.addView(blackList);
            multiplayerCategory.addView(whiteList);
            view.addView(multiplayerCategory);

            var integratedServerCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.integratedServer"));

            PreferencesFragment.BooleanOption enableInIntegratedServerOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.enable"),
                    clientConfig::getEnabledInIntegratedServer,
                    clientConfig::setEnabledInIntegratedServer)
                    .setDefaultValue(true);
            enableInIntegratedServerOption.create(integratedServerCategory);
            ApiServerManager apiServerManager = ApiServerManager.getInstance();
            enableInIntegratedServerOption.setOnChanged(() -> {
                ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
                if (clientConfig.getEnabledInIntegratedServer()) {
                    if (apiServerManager != null) {
                        apiServerManager.restartApiServer();
                    }
                    loginApiService.reconnectAll();
                } else {
                    MusicPlayerServerService.getInstance().reset();
                    loginApiService.disconnectToAll();
                    if (apiServerManager != null) {
                        apiServerManager.stopApiServer();
                    }
                }
            });

            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.integratedServer.serverApiBaseUrl"));
                EditText input = inputBox.findViewById(R.id.input);
                if (input != null) {
                    input.setMinimumWidth(dp(256));
                    input.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
                    input.setText(serverConfig.getServerApiBaseUrl());
                    input.setOnKeyListener((v, c, e) -> {
                        if (c == GLFW.GLFW_KEY_ENTER) {
                            input.clearFocus();
                            return true;
                        }
                        return false;
                    });
                    input.setOnFocusChangeListener((v, b) -> {
                        if (!b) {
                            serverConfig.setServerApiBaseUrl(input.getText().toString());
                        }
                    });
                }
                integratedServerCategory.addView(inputBox);
            }

            PreferencesFragment.BooleanOption startupBinaryApiServerOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.startupBinaryApiServerWhenLaunch"),
                    serverConfig::getStartupBinaryApiServerWhenLaunch,
                    serverConfig::setStartupBinaryApiServerWhenLaunch)
                    .setDefaultValue(true);
            startupBinaryApiServerOption.create(integratedServerCategory);

            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.integratedServer.serverApiBinaryExecutablePath"));
                EditText input = inputBox.findViewById(R.id.input);
                if (input != null) {
                    input.setMinimumWidth(dp(256));
                    input.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
                    input.setText(serverConfig.getServerApiBinaryExecutablePath());
                    input.setOnKeyListener((v, c, e) -> {
                        if (c == GLFW.GLFW_KEY_ENTER) {
                            input.clearFocus();
                            return true;
                        }
                        return false;
                    });
                    input.setOnFocusChangeListener((v, b) -> {
                        if (!b) {
                            serverConfig.setServerApiBinaryExecutablePath(input.getText().toString());
                        }
                    });
                }
                integratedServerCategory.addView(inputBox);
            }

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.LEFT);
            layout.setVerticalGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
            params2.setMargins(dp(6), 0, dp(6), 0);
            layout.setLayoutParams(params2);

            TextView apiStatusLabel = new TextView(context);
            apiStatusLabel.setTextSize(14);
            String string = I18n.get(MusicHud.MOD_ID + ".text.binaryApiStatus");
            apiStatusLabel.setText(string.replace("{}", I18n.get(apiServerManager.getBinaryApiServerStatus().i18nKey())));

            Consumer<ApiServerManager.BinaryApiServerStatus> listener = (apiStatusListener) -> {
                MuiModApi.postToUiThread(() -> {
                    apiStatusLabel.setText(string.replace("{}", I18n.get(apiStatusListener.i18nKey())));
                });
            };
            List<Consumer<ApiServerManager.BinaryApiServerStatus>> apiStatusListeners = apiServerManager.getApiStatusListeners();
            apiStatusListeners.add(listener);

            Button stopApiServerButton = new Button(context);
            stopApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.stopApiServer"));
            stopApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            stopApiServerButton.setTextSize(14);
            Drawable bg1 = ButtonInsetBackgroundFactory.builder().inset(0).padding(new ButtonInsetBackgroundFactory.Padding(dp(8), dp(4), dp(8), dp(4))).build().newBackgroundDrawable();
            stopApiServerButton.setBackground(bg1);
            stopApiServerButton.setOnClickListener((v) -> {
                apiServerManager.stopApiServer();
            });

            Button restartApiServerButton = new Button(context);
            restartApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.restartApiServer"));
            restartApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            restartApiServerButton.setTextSize(14);
            Drawable bg = ButtonInsetBackgroundFactory.builder().inset(0).padding(new ButtonInsetBackgroundFactory.Padding(dp(8), dp(4), dp(8), dp(4))).build().newBackgroundDrawable();
            restartApiServerButton.setBackground(bg);
            restartApiServerButton.setOnClickListener((v) -> {
                apiServerManager.restartApiServer();
            });

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    apiStatusListeners.remove(listener);
                }
            });

            layout.addView(apiStatusLabel, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            layout.addView(stopApiServerButton);
            layout.addView(restartApiServerButton);
            integratedServerCategory.addView(layout);

            PreferencesFragment.FloatOption pusherVoteAdditionalRateOption = new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.pusherVoteAdditionalRate"),
                    serverConfig::getPusherVoteAdditionalRate,
                    serverConfig::setPusherVoteAdditionalRate)
                    .setRange(0, 1)
                    .setDefaultValue(0.5);
            pusherVoteAdditionalRateOption.create(integratedServerCategory);

            PreferencesFragment.BooleanOption useRandomCnIpOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.useRandomCnIp"),
                    serverConfig::getUseRandomCnIp,
                    serverConfig::setUseRandomCnIp)
                    .setDefaultValue(true);
            useRandomCnIpOption.create(integratedServerCategory);
            LinearLayout.LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params1.setMargins(0, dp(6), 0, dp(128));
            view.addView(integratedServerCategory, params1);


            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    Util.ioPool().execute(() -> {
                        clientConfig.save();
                        serverConfig.save();
                    });
                }
            });
        } catch (Exception e) {
            instance = null;
            throw e;
        }
    }
}