package indi.etern.musichud.client.ui.pages;

import com.google.gson.reflect.TypeToken;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.ConfigItem;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.style.URLSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.components.DynamicIntegerOption;
import indi.etern.musichud.client.ui.components.LyricLineView;
import indi.etern.musichud.client.ui.components.StaggeredLyricScrollView;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.server.api.*;
import indi.etern.musichud.utils.JsonUtil;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import net.minecraft.util.Util;
import net.minecraft.client.resources.language.I18n;
import org.apache.commons.lang3.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

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
                    I18n.get(MusicHud.MOD_ID + ".config.common.enable"),
                    clientConfig::getEnable,
                    clientConfig::setEnable);
            booleanOption.create(commonCategory);
            booleanOption.setOnChanged(() -> {
                MuiModApi.postToUiThread(MainFragment::refresh);
                if (clientConfig.getEnable()) {
                    loginService.connectAsPrevious();
                } else {
                    loginService.disconnectToExternalOrIntegratedServer();
                }
            });
            PreferencesFragment.BooleanOption translatedLyricOption = new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.showTranslatedCnLyrics"),
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
                    I18n.get(MusicHud.MOD_ID + ".config.common.disableVanillaMusicWhilePlaying"),
                    clientConfig::getDisableVanillaMusic,
                    clientConfig::setDisableVanillaMusic)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.enableHud"),
                    clientConfig::getEnableHud,
                    clientConfig::setEnableHud)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.autoHide"),
                    clientConfig::getHideHudWhenNotPlaying,
                    clientConfig::setHideHudWhenNotPlaying)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.enableMarqueeText"),
                    clientConfig::getEnableMarqueeText,
                    clientConfig::setEnableMarqueeText)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.mixWithVanillaSoundVolume"),
                    clientConfig::getMixWithVanillaSoundVolume,
                    clientConfig::setMixWithVanillaSoundVolume)
                    .create(commonCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.soundVolume"),
                    clientConfig::getSoundVolume,
                    clientConfig::setSoundVolume)
                    .setRange(0, 100)
                    .setDefaultValue(100)
                    .create(commonCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.soundVolumeInterval"),
                    clientConfig::getSoundVolumeInterval,
                    clientConfig::setSoundVolumeInterval)
                    .setRange(1, 100)
                    .setDefaultValue(10)
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
            new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.mainScreenAdditionalBackgroundDarken"),
                    clientConfig::getMainScreenAdditionalBackgroundDarken,
                    clientConfig::setMainScreenAdditionalBackgroundDarken)
                    .setRange(0, 1)
                    .setOnChanged(() -> {
                        MusicHudScreen.setDarken(clientConfig.getMainScreenAdditionalBackgroundDarken());
                    })
                    .setDefaultValue(0.5)
                    .create(commonCategory);
            new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.hudBackgroundMixAlpha"),
                    clientConfig::getHudBackgroundMixAlpha,
                    clientConfig::setHudBackgroundMixAlpha)
                    .setRange(0, 1)
                    .setDefaultValue(0.5)
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
                    .setRange(-1920, 1920)
                    .setDefaultValue(16)
                    .create(positionCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.offsetY"),
                    clientConfig::getHudOffsetY,
                    clientConfig::setHudOffsetY)
                    .setRange(-1920, 1920)
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
                    MainFragment.refresh();
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
            view.addView(integratedServerCategory, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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

            PreferencesFragment.FloatOption pusherVoteAdditionalRateOption = new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.pusherVoteAdditionalRate"),
                    serverConfig::getPusherVoteAdditionalRate,
                    serverConfig::setPusherVoteAdditionalRate)
                    .setRange(0, 1)
                    .setDefaultValue(0.5);
            pusherVoteAdditionalRateOption.create(integratedServerCategory);

            var apiCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.apiServer"));
            LinearLayout.LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params1.setMargins(0, dp(6), 0, dp(128));
            view.addView(apiCategory, params1);

            PreferencesFragment.BooleanOption startupBinaryApiServerOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.startupBinaryApiServerWhenLaunch"),
                    serverConfig::getStartupBinaryApiServerWhenLaunch,
                    serverConfig::setStartupBinaryApiServerWhenLaunch)
                    .setDefaultValue(true);
            startupBinaryApiServerOption.create(apiCategory);

            PreferencesFragment.BooleanOption useRandomCnIpOption = new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.useRandomCnIp"),
                    serverConfig::getUseRandomCnIp,
                    serverConfig::setUseRandomCnIp)
                    .setDefaultValue(true);
            useRandomCnIpOption.create(apiCategory);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableGeneralUnblock"),
                    serverConfig::getEnableGeneralUnblock,
                    serverConfig::setEnableGeneralUnblock)
                    .setDefaultValue(true)
                    .create(apiCategory);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableFlac"),
                    serverConfig::getEnableFlac,
                    serverConfig::setEnableFlac)
                    .setDefaultValue(true)
                    .create(apiCategory);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.selectMaxBr"),
                    serverConfig::getSelectMaxBr,
                    serverConfig::setSelectMaxBr)
                    .setDefaultValue(false)
                    .create(apiCategory);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.followSourceOrder"),
                    serverConfig::getFollowSourceOrder,
                    serverConfig::setFollowSourceOrder)
                    .setDefaultValue(true)
                    .create(apiCategory);

            new PreferencesFragment.IntegerOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.port"),
                    serverConfig::getPort,
                    serverConfig::setPort)
                    .setRange(1, 65535)
                    .setDefaultValue(3000)
                    .create(apiCategory);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableProxy"),
                    serverConfig::getEnableProxy,
                    serverConfig::setEnableProxy)
                    .setDefaultValue(false)
                    .create(apiCategory);

            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.apiServer.corsAllowOrigin"));
                EditText input = inputBox.findViewById(R.id.input);
                if (input != null) {
                    input.setMinimumWidth(dp(256));
                    input.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
                    input.setText(serverConfig.getCorsAllowOrigin());
                    input.setOnKeyListener((v, c, e) -> {
                        if (c == GLFW.GLFW_KEY_ENTER) {
                            input.clearFocus();
                            return true;
                        }
                        return false;
                    });
                    input.setOnFocusChangeListener((v, b) -> {
                        if (!b) {
                            serverConfig.setCorsAllowOrigin(input.getText().toString());
                        }
                    });
                }
                apiCategory.addView(inputBox);
            }

            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.apiServer.proxyUrl"));
                EditText input = inputBox.findViewById(R.id.input);
                if (input != null) {
                    input.setMinimumWidth(dp(256));
                    input.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
                    input.setText(serverConfig.getProxyUrl());
                    input.setOnKeyListener((v, c, e) -> {
                        if (c == GLFW.GLFW_KEY_ENTER) {
                            input.clearFocus();
                            return true;
                        }
                        return false;
                    });
                    input.setOnFocusChangeListener((v, b) -> {
                        if (!b) {
                            serverConfig.setProxyUrl(input.getText().toString());
                        }
                    });
                }
                apiCategory.addView(inputBox);
            }


            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.apiServer.serverApiBaseUrl"));
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
                apiCategory.addView(inputBox);
            }

            final EditText[] serverApiBinaryPathInput = {null};

            {
                LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(MusicHud.MOD_ID + ".config.apiServer.serverApiBinaryExecutablePath"));
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
                serverApiBinaryPathInput[0] = input;
                apiCategory.addView(inputBox);
            }

            LinearLayout apiServerStatusLayout = new LinearLayout(context);
            apiServerStatusLayout.setOrientation(LinearLayout.HORIZONTAL);
            apiServerStatusLayout.setGravity(Gravity.LEFT);
            apiServerStatusLayout.setVerticalGravity(Gravity.CENTER);
            LayoutParams params3 = new LayoutParams(MATCH_PARENT, dp(44));
            params3.setMargins(dp(6), 0, dp(6), 0);
            apiServerStatusLayout.setLayoutParams(params3);

            TextView apiStatusLabel = new TextView(context);
            apiStatusLabel.setTextSize(14);
            String binaryApiStatusTemplate = I18n.get(MusicHud.MOD_ID + ".text.binaryApiStatus");
            apiStatusLabel.setText(binaryApiStatusTemplate.replace("{}", I18n.get(apiServerManager.getBinaryApiServerStatus().i18nKey())));

            ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder().inset(0).padding(new ButtonInsetBackgroundFactory.Padding(dp(8), dp(4), dp(8), dp(4))).build();

            Button downloadApiServerButton = createDownloadApiButton(context, backgroundFactory, serverApiBinaryPathInput);

            Button stopApiServerButton = new Button(context);
            stopApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.stopApiServer"));
            stopApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            stopApiServerButton.setTextSize(14);
            stopApiServerButton.setBackground(backgroundFactory.newBackgroundDrawable());
            stopApiServerButton.setOnClickListener((v1) -> {
                apiServerManager.stopApiServer();
            });

            Button restartApiServerButton = new Button(context);
            restartApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.restartApiServer"));
            restartApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            restartApiServerButton.setTextSize(14);
            restartApiServerButton.setBackground(backgroundFactory.newBackgroundDrawable());
            restartApiServerButton.setOnClickListener((v1) -> {
                apiServerManager.restartApiServer();
            });

            apiServerStatusLayout.addView(apiStatusLabel, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            apiServerStatusLayout.addView(downloadApiServerButton);
            apiServerStatusLayout.addView(stopApiServerButton);
            apiServerStatusLayout.addView(restartApiServerButton);
            apiCategory.addView(apiServerStatusLayout);

            LinearLayout apiVersionLinearLayout = new LinearLayout(context);
            apiVersionLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            apiVersionLinearLayout.setGravity(Gravity.LEFT);
            apiVersionLinearLayout.setVerticalGravity(Gravity.CENTER);
            LayoutParams params2 = new LayoutParams(MATCH_PARENT, dp(44));
            params2.setMargins(dp(6), 0, dp(6), 0);
            apiVersionLinearLayout.setLayoutParams(params2);

            TextView apiVersionLabel = new TextView(context);
            apiVersionLabel.setTextSize(14);
            String apiServiceVersionTemplate = I18n.get(MusicHud.MOD_ID + ".text.apiServiceVersion");
            apiVersionLabel.setText(apiServiceVersionTemplate.replace("{}", I18n.get(ApiClient.getVersion())));

            Button checkVersionButton = new Button(context);
            checkVersionButton.setText(I18n.get(MusicHud.MOD_ID + ".button.checkApiServerVersion"));
            checkVersionButton.setTextColor(Theme.PRIMARY_COLOR);
            checkVersionButton.setTextSize(14);
            checkVersionButton.setBackground(backgroundFactory.newBackgroundDrawable());
            checkVersionButton.setOnClickListener((v) -> {
                ApiClient.checkAvailable();
                apiVersionLabel.setText(apiServiceVersionTemplate.replace("{}", I18n.get(ApiClient.getVersion())));
            });

            apiVersionLinearLayout.addView(apiVersionLabel, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            apiVersionLinearLayout.addView(checkVersionButton);
            apiCategory.addView(apiVersionLinearLayout);

            Consumer<ApiServerManager.BinaryApiServerStatus> listener = (apiStatusListener) -> {
                MuiModApi.postToUiThread(() -> {
                    apiStatusLabel.setText(binaryApiStatusTemplate.replace("{}", I18n.get(apiStatusListener.i18nKey())));
                    apiVersionLabel.setText(apiServiceVersionTemplate.replace("{}", I18n.get(ApiClient.getVersion())));
                });
            };
            List<Consumer<ApiServerManager.BinaryApiServerStatus>> apiStatusListeners = apiServerManager.getApiStatusListeners();
            apiStatusListeners.add(listener);
            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    apiStatusListeners.remove(listener);
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

    private @NotNull Button createDownloadApiButton(Context context, ButtonInsetBackgroundFactory backgroundFactory, EditText[] serverApiBinaryPathInput) {
        Button downloadApiServerButton = new Button(context);
        downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
        downloadApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
        downloadApiServerButton.setTextSize(14);
        downloadApiServerButton.setBackground(backgroundFactory.newBackgroundDrawable());

        final String downloadingText = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.downloading");
        final String button1Text = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button1");
        final String button2Text = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button2");
        final String button1YesText = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button1.yes");
        final String button2NoText = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.button2.no");
        final String hideText = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.hide");

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.title"));
        title.setTextSize(Theme.TEXT_SIZE_LARGE);

        TextView description = new TextView(context);
        String desc = I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.description");
        int indexOfUrl = desc.indexOf("{url}");
        String latestReleaseUrl = ApiServerFetcher.LATEST_RELEASE_URL;
        String replace = desc.replace("{url}", latestReleaseUrl);
        SpannableString spannableString = new SpannableString(replace);
        spannableString.setSpan(new URLSpan(latestReleaseUrl), indexOfUrl, indexOfUrl + latestReleaseUrl.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        description.setText(spannableString);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setOnClickListener(v -> Util.getPlatform().openUri(latestReleaseUrl));

        final Path[] targetDir = {Paths.get("music-hud")};
        final ApiServerFetcher.ReleaseSummary[] latestRelease = {null};

        LinearLayout releaseInfoLayout = new LinearLayout(context);
        releaseInfoLayout.setOrientation(LinearLayout.HORIZONTAL);
        releaseInfoLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView existingVersionWarning = new TextView(context);
        existingVersionWarning.setTextSize(Theme.TEXT_SIZE_NORMAL);
        existingVersionWarning.setTextColor(Theme.WARN_TEXT_COLOR);
        existingVersionWarning.setVisibility(GONE);

        TextView releaseNameLabel = new TextView(context);
        releaseNameLabel.setTextSize(Theme.TEXT_SIZE_NORMAL);
        releaseNameLabel.setTextColor(Theme.NORMAL_TEXT_COLOR);
        releaseNameLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.fetching"));

        Button refreshReleaseButton = new Button(context);
        refreshReleaseButton.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.refresh"));
        refreshReleaseButton.setTextColor(Theme.PRIMARY_COLOR);
        refreshReleaseButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        refreshReleaseButton.setBackground(backgroundFactory.newBackgroundDrawable());
        refreshReleaseButton.setOnClickListener(v -> { refreshReleaseInfo(releaseNameLabel, latestRelease, targetDir, existingVersionWarning); });

        releaseInfoLayout.addView(releaseNameLabel, new LayoutParams(0, WRAP_CONTENT, 1));
        releaseInfoLayout.addView(refreshReleaseButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LinearLayout directoryLayout = new LinearLayout(context);
        directoryLayout.setOrientation(LinearLayout.HORIZONTAL);
        EditText directoryTextInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        directoryTextInput.setHint(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.field.hint"));
        directoryTextInput.setTextSize(Theme.TEXT_SIZE_NORMAL);
        directoryTextInput.setTextColor(Theme.NORMAL_TEXT_COLOR);
        directoryTextInput.setText(targetDir[0].toString());

        Button selectDirectoryButton = new Button(context);
        selectDirectoryButton.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.button.select"));
        selectDirectoryButton.setTextColor(Theme.PRIMARY_COLOR);
        selectDirectoryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        selectDirectoryButton.setBackground(backgroundFactory.newBackgroundDrawable());
        selectDirectoryButton.setOnClickListener(v -> {
            Path defaultPath = targetDir[0].toAbsolutePath();
            String folder = TinyFileDialogs.tinyfd_selectFolderDialog(
                    I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.dir.dialog.title"), defaultPath.toString());
            if (folder != null) {
                targetDir[0] = Paths.get(folder);
                directoryTextInput.setText(folder);
                checkExistingVersion(targetDir[0], latestRelease[0], existingVersionWarning);
            }
        });

        directoryLayout.addView(directoryTextInput, new LayoutParams(0, WRAP_CONTENT, 1));
        directoryLayout.addView(selectDirectoryButton, new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 0));

        LinearLayout progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.HORIZONTAL);
        progressLayout.setGravity(Gravity.CENTER_VERTICAL);
        progressLayout.setVisibility(GONE);

        ProgressBar progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
        progressBar.setMin(0);
        progressBar.setMax(100);

        TextView progressText = new TextView(context);
        progressText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        progressText.setTextColor(Theme.NORMAL_TEXT_COLOR);

        LayoutParams progParams = new LayoutParams(0, dp(24), 1);
        progParams.setMargins(0, 0, progressBar.dp(4), 0);
        progressLayout.addView(progressBar, progParams);
        progressLayout.addView(progressText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LayoutParams params4 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params4.setMargins(0, 0, 0, dp(8));
        content.addView(title, params4);
        content.addView(description);
        LayoutParams params5 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params5.setMargins(0, dp(4), 0, dp(4));
        content.addView(directoryLayout, params5);
        content.addView(releaseInfoLayout);
        content.addView(existingVersionWarning);
        content.addView(progressLayout);

        LinearLayout doneContent = new LinearLayout(context);
        doneContent.setOrientation(LinearLayout.VERTICAL);
        doneContent.setVisibility(GONE);

        TextView doneTitle = new TextView(context);
        doneTitle.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.title"));
        doneTitle.setTextSize(Theme.TEXT_SIZE_LARGE);

        TextView doneDesc = new TextView(context);
        doneDesc.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.description"));
        doneDesc.setTextSize(Theme.TEXT_SIZE_NORMAL);
        doneDesc.setTextColor(Theme.NORMAL_TEXT_COLOR);

        doneContent.addView(doneTitle, params4);
        doneContent.addView(doneDesc);
        content.addView(doneContent);

        final String[] downloadState = {"idle"};
        final long[] lastSize = {0, 0};
        final Path[] downloadedTempFile = {null};
        final String[] releaseTag = {""};

        Modal.ActionButton cancelBtn = new Modal.ActionButton(button2Text, (btn, dialog) -> {
            if ("done".equals(downloadState[0])) {
                resetToIdle(downloadState, doneContent, title, description, directoryLayout, progressLayout, releaseInfoLayout, existingVersionWarning);
                downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
            }
            dialog.dismiss();
        });

        Modal.ActionButton confirmButton = new Modal.ActionButton(button1Text, (btn, dialog) -> {
            if ("idle".equals(downloadState[0])) {
                if (latestRelease[0] == null) {
                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.notFetched"));
                    return;
                }
                downloadState[0] = "downloading";
                btn.setText(downloadingText);
                btn.setEnabled(false);
                cancelBtn.setText(hideText);
                progressLayout.setVisibility(VISIBLE);
                releaseInfoLayout.setVisibility(GONE);
                existingVersionWarning.setVisibility(GONE);
                progressBar.setProgress(0);
                progressText.setText("");
                downloadApiServerButton.setText(downloadingText);

                targetDir[0] = Paths.get(directoryTextInput.getText().toString().trim());
                try {
                    Files.createDirectories(targetDir[0]);
                } catch (IOException ignored) {}

                releaseTag[0] = latestRelease[0].tag();
                String tempFileName = ApiServerFetcher.Platform.detect().getAssetName() + "." + releaseTag[0] + ".temp";
                Path tempFile = targetDir[0].resolve(tempFileName);
                tempFile.toFile().deleteOnExit();

                ApiServerFetcher.downloadLatestForCurrentPlatform(targetDir[0], tempFileName, (downloaded, total) -> {
                    MuiModApi.postToUiThread(() -> {
                        lastSize[0] = downloaded;
                        lastSize[1] = total;
                        int pct = (int) (((double) downloaded / total) * 100);
                        progressBar.setProgress(pct);
                        progressText.setText(formatBytes(downloaded) + " / " + formatBytes(total));
                    });
                }).thenRun(() -> {
                    MuiModApi.postToUiThread(() -> {
                        downloadState[0] = "done";
                        downloadedTempFile[0] = tempFile;
                        title.setVisibility(GONE);
                        description.setVisibility(GONE);
                        releaseInfoLayout.setVisibility(GONE);
                        existingVersionWarning.setVisibility(GONE);
                        directoryLayout.setVisibility(GONE);
                        progressLayout.setVisibility(GONE);
                        doneTitle.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.title"));
                        doneDesc.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.done.description").replace("{path}", tempFile.toString()));
                        doneContent.setVisibility(VISIBLE);
                        btn.setText(button1YesText);
                        btn.setEnabled(true);
                        cancelBtn.setText(button2NoText);
                        cancelBtn.getButton().setVisibility(VISIBLE);
                        cancelBtn.getButton().setScaleX(1f);
                        cancelBtn.getButton().setAlpha(1f);
                        downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServerDone"));
                    });
                }).exceptionally(ex -> {
                    MuiModApi.postToUiThread(() -> {
                        ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.error") + ": " + ex.getMessage());
                        downloadState[0] = "idle";
                        progressLayout.setVisibility(GONE);
                        releaseInfoLayout.setVisibility(VISIBLE);
                        btn.setText(button1Text);
                        btn.setEnabled(true);
                        cancelBtn.setText(button2Text);
                        cancelBtn.getButton().setVisibility(VISIBLE);
                        cancelBtn.getButton().setScaleX(1f);
                        cancelBtn.getButton().setAlpha(1f);
                        downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
                    });
                    return null;
                });
            } else if ("done".equals(downloadState[0])) {
                Path finalPath = resolveFinalPath(downloadedTempFile[0], releaseTag[0]);
                if (finalPath == null) {
                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.renameFailed"));
                    return;
                }
                updateMhApiJson(targetDir[0], releaseTag[0], finalPath.getFileName().toString());
                String configPath = relativizeIfChild(finalPath);
                serverConfig.setServerApiBinaryExecutablePath(configPath);
                if (serverApiBinaryPathInput[0] != null) {
                    serverApiBinaryPathInput[0].setText(configPath);
                }
                ApiServerManager apiServer = ApiServerManager.getInstance();
                if (apiServer != null) {
                    apiServer.restartApiServer();
                }
                downloadedTempFile[0] = null;
                downloadState[0] = "resetting";
                resetToIdle(downloadState, doneContent, title, description, directoryLayout, progressLayout, releaseInfoLayout, existingVersionWarning);
                downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
                btn.setText(button1Text);
                cancelBtn.setText(button2Text);
                dialog.dismiss();
            }
        });

        Modal dialog = new Modal(context, content,
                confirmButton, cancelBtn
        );

        dialog.setOnDismissListener(() -> {
            if ("resetting".equals(downloadState[0])) {
                downloadState[0] = "idle";
                downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
            } else if ("done".equals(downloadState[0])) {
                downloadState[0] = "idle";
                resetToIdle(downloadState, doneContent, title, description, directoryLayout, progressLayout, releaseInfoLayout, existingVersionWarning);
                downloadApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.downloadApiServer"));
            }
        });

        downloadApiServerButton.setOnClickListener((v) -> {
            refreshReleaseInfo(releaseNameLabel, latestRelease, targetDir, existingVersionWarning);
            applyContentByState(downloadState[0], doneContent, title, description, directoryLayout, progressLayout, releaseInfoLayout);
            dialog.show();
        });
        return downloadApiServerButton;
    }

    private void refreshReleaseInfo(TextView releaseLabel, ApiServerFetcher.ReleaseSummary[] latest, Path[] targetDir, TextView warning) {
        releaseLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.fetching"));
        ApiServerFetcher.listReleaseSummaries().thenAccept(summaries -> {
            if (!summaries.isEmpty()) {
                ApiServerFetcher.ReleaseSummary r = summaries.getFirst();
                MuiModApi.postToUiThread(() -> {
                    latest[0] = r;
                    releaseLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.label")
                            .replace("{tag}", r.tag()));
                    checkExistingVersion(targetDir[0], r, warning);
                });
            } else {
                MuiModApi.postToUiThread(() -> releaseLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.failed")));
            }
        }).exceptionally(ex -> {
            MuiModApi.postToUiThread(() -> releaseLabel.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.release.failed")));
            return null;
        });
    }

    private void checkExistingVersion(Path targetDir, ApiServerFetcher.ReleaseSummary release, TextView warning) {
        if (release == null) {
            warning.setVisibility(GONE);
            return;
        }
        Path jsonFile = targetDir.resolve("mh-api.json");
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, String> map = JsonUtil.gson.fromJson(content, new TypeToken<Map<String, String>>(){}.getType());
                if (map != null && map.containsKey(release.tag())) {
                    String oldFile = map.get(release.tag());
                    warning.setText(I18n.get(MusicHud.MOD_ID + ".modal.downloadApiServer.existingVersion")
                            .replace("{file}", oldFile).replace("{tag}", release.tag()));
                    warning.setVisibility(VISIBLE);
                    return;
                }
            }
        } catch (Exception ignored) {}
        warning.setVisibility(GONE);
    }

    private Path resolveFinalPath(Path tempFile, String releaseTag) {
        if (tempFile == null || !Files.exists(tempFile)) return null;
        String baseName = ApiServerFetcher.Platform.detect().getAssetName();
        Path targetDir = tempFile.getParent();
        Path namedFile = targetDir.resolve(baseName);

        // proactively stop server if target file is the running executable
        String currentPath = serverConfig.getServerApiBinaryExecutablePath();
        if (namedFile.toAbsolutePath().normalize().toString()
                .equals(Paths.get(currentPath).toAbsolutePath().normalize().toString())
                && ApiServerManager.getInstance().getBinaryApiServerStatus() == ApiServerManager.BinaryApiServerStatus.RUNNING) {
            ApiServerManager.getInstance().stopApiServer();
            // retry with backoff until the lock is released
            for (int retry = 0; retry < 20; retry++) {
                try {
                    Thread.sleep(150);
                    Files.move(tempFile, namedFile, StandardCopyOption.REPLACE_EXISTING);
                    return namedFile;
                } catch (Exception ignored) {}
            }
        }

        try {
            Files.move(tempFile, namedFile, StandardCopyOption.REPLACE_EXISTING);
            return namedFile;
        } catch (IOException ignored) {}
        // fallback: append .n before extension
        String bn = baseName;
        String ext = "";
        int dotIdx = bn.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = bn.substring(dotIdx);
            bn = bn.substring(0, dotIdx);
        }
        for (int n = 1; n < 100; n++) {
            Path numberedFile = targetDir.resolve(bn + "." + n + ext);
            try {
                Files.move(tempFile, numberedFile);
                return numberedFile;
            } catch (IOException ignored) {}
        }
        return null;
    }

    private void updateMhApiJson(Path targetDir, String releaseTag, String fileName) {
        Path jsonFile = targetDir.resolve("mh-api.json");
        Map<String, String> map = new HashMap<>();
        try {
            if (Files.exists(jsonFile)) {
                String content = Files.readString(jsonFile);
                Map<String, String> existing = JsonUtil.gson.fromJson(content, new TypeToken<Map<String, String>>(){}.getType());
                if (existing != null) map.putAll(existing);
            }
        } catch (Exception ignored) {}
        map.put(releaseTag, fileName);
        try {
            Files.writeString(jsonFile, JsonUtil.gson.toJson(map));
        } catch (IOException ignored) {}
    }

    private static String relativizeIfChild(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        if (abs.startsWith(cwd)) {
            Path relative = cwd.relativize(abs);
            return relative.toString();
        }
        return abs.toString();
    }

    private void resetToIdle(String[] state, View doneContent, View title, View description, View directoryLayout, View progressLayout, View releaseInfoLayout, View warning) {
        state[0] = "idle";
        hideDownloadContent(doneContent, title, description, directoryLayout, progressLayout, releaseInfoLayout);
        warning.setVisibility(GONE);
    }

    private static void applyContentByState(String state, LinearLayout doneContent, View title, View description, View directoryLayout, View progressLayout, View releaseInfoLayout) {
        if ("done".equals(state)) {
            title.setVisibility(GONE);
            description.setVisibility(GONE);
            releaseInfoLayout.setVisibility(GONE);
            directoryLayout.setVisibility(GONE);
            progressLayout.setVisibility(GONE);
            doneContent.setVisibility(VISIBLE);
        } else {
            doneContent.setVisibility(GONE);
            title.setVisibility(VISIBLE);
            description.setVisibility(VISIBLE);
            releaseInfoLayout.setVisibility(VISIBLE);
            directoryLayout.setVisibility(VISIBLE);
            progressLayout.setVisibility(GONE);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 100) return String.format("%.1f MiB", mb);
        return String.format("%.0f MiB", mb);
    }

    private static void hideDownloadContent(View doneContent, View title, View description, View directoryLayout, View progressLayout, View releaseInfoLayout) {
        doneContent.setVisibility(GONE);
        title.setVisibility(VISIBLE);
        description.setVisibility(VISIBLE);
        directoryLayout.setVisibility(VISIBLE);
        progressLayout.setVisibility(VISIBLE);
        releaseInfoLayout.setVisibility(VISIBLE);
    }

}