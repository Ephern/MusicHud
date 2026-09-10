package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.BuiltinIconDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.ConfigItem;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.AutoConnectServerFilterType;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.beans.user.ScrobbleOption;
import indi.etern.musichud.beans.user.MultichannelMode;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.ApiServerDownloadDialog;
import indi.etern.musichud.client.ui.components.Modal;
import indi.etern.musichud.client.ui.screen.HudConfigFragment;
import indi.etern.musichud.client.ui.screen.HudConfigScreen;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.server.api.*;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.apache.commons.lang3.Range;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@SuppressWarnings("UnstableApiUsage")
public class ConfigView extends LinearLayout {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    @Getter
    static volatile ConfigView instance;
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();

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
            scrollView.addView(view, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            view.addView(new View(context), new LayoutParams(MATCH_PARENT, dp(32)));

            var commonCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.common"));
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.enable"),
                    clientConfig::getEnable,
                    clientConfig::setEnable)
                    .setDefaultValue(clientConfig.getDefaultEnable())
                    .setOnChanged(() -> {
                        MuiModApi.postToUiThread(MainFragment::refresh);
                        if (clientConfig.getEnable()) {
                            connectionManager.connectAsPrevious();
                        } else {
                            connectionManager.disconnect();
                        }
                    })
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.enableLyricsSidebar"),
                    clientConfig::getEnableLyricsSidebar,
                    clientConfig::setEnableLyricsSidebar)
                    .setDefaultValue(clientConfig.getDefaultEnableLyricsSidebar())
                    .setOnChanged(MainFragment::refreshLyricsSidebarVisibility)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.showTranslatedCnLyrics"),
                    clientConfig::getShowTranslatedCnLyrics,
                    clientConfig::setShowTranslatedCnLyrics)
                    .setDefaultValue(clientConfig.getDefaultShowTranslatedCnLyrics())
                    .setOnChanged(MainFragment::refreshLyricViews)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.disableVanillaMusicWhilePlaying"),
                    clientConfig::getDisableVanillaMusic,
                    clientConfig::setDisableVanillaMusic)
                    .setDefaultValue(clientConfig.getDefaultDisableVanillaMusic())
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.mixWithVanillaSoundVolume"),
                    clientConfig::getMixWithVanillaSoundVolume,
                    clientConfig::setMixWithVanillaSoundVolume)
                    .setDefaultValue(clientConfig.getDefaultMixWithVanillaSoundVolume())
                    .create(commonCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.soundVolume"),
                    clientConfig::getSoundVolume,
                    clientConfig::setSoundVolume)
                    .setRange(0, 100)
                    .setDefaultValue(clientConfig.getDefaultSoundVolume())
                    .create(commonCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.soundVolumeInterval"),
                    clientConfig::getSoundVolumeInterval,
                    clientConfig::setSoundVolumeInterval)
                    .setRange(1, 100)
                    .setDefaultValue(clientConfig.getDefaultSoundVolumeInterval())
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
                    .setDefaultValue(clientConfig.getDefaultPrimaryChosenQuality())
                    .create(commonCategory);
            MultichannelMode[] multichannelModes = {MultichannelMode.PREFER_DISCRETE, MultichannelMode.FORCE_DOWNMIX};
            List<MultichannelMode> multichannelModesList = Arrays.stream(multichannelModes).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.multichannelMode"),
                    multichannelModes,
                    multichannelModesList::indexOf,
                    clientConfig::getMultichannelMode,
                    clientConfig::setMultichannelMode)
                    .setDefaultValue(clientConfig.getDefaultMultichannelMode())
                    .create(commonCategory);
            ScrobbleOption[] scrobbleOptions = {ScrobbleOption.NONE, ScrobbleOption.ONLY_SELF, ScrobbleOption.ALL};
            List<ScrobbleOption> scrobbleOptionsList = Arrays.stream(scrobbleOptions).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.scrobbleOption"),
                    scrobbleOptions,
                    scrobbleOptionsList::indexOf,
                    clientConfig::getScrobbleOption,
                    clientConfig::setScrobbleOption)
                    .setDefaultValue(clientConfig.getDefaultScrobbleOption())
                    .create(commonCategory);
            new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.mainScreenAdditionalBackgroundDarken"),
                    clientConfig::getMainScreenAdditionalBackgroundDarken,
                    clientConfig::setMainScreenAdditionalBackgroundDarken)
                    .setRange(0, 1)
                    .setDefaultValue(clientConfig.getDefaultMainScreenAdditionalBackgroundDarken())
                    .setOnChanged(() -> MusicHudScreen.setDarken(clientConfig.getMainScreenAdditionalBackgroundDarken()))
                    .setDefaultValue(0.5)
                    .create(commonCategory);

            Button openHudConfigButton = new Button(context);
            openHudConfigButton.setText(I18n.get(MusicHud.MOD_ID + ".config.openHudConfig"));
            openHudConfigButton.setTextColor(Theme.PRIMARY_COLOR);
            openHudConfigButton.setTextSize(14);
            InsetBackgroundFactory openHudBgFactory = InsetBackgroundFactory.builder().inset(0).cornerRadius(dp(8)).build();
            openHudBgFactory.applyBackgroundTo(openHudConfigButton);
            openHudConfigButton.setOnClickListener((v) -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> minecraft.setScreen(HudConfigScreen.createScreen(
                        new HudConfigFragment(),
                        minecraft.screen,
                        I18n.get(MusicHud.MOD_ID + ".config.hudScreenTitle"))));
            });
            commonCategory.addView(openHudConfigButton, new LayoutParams(MATCH_PARENT, dp(40)));
            view.addView(commonCategory);

            var multiplayerCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.externalServer"));
            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.autoConnect"),
                    clientConfig::getEnableAutoConnect,
                    clientConfig::setEnableAutoConnect)
                    .setDefaultValue(clientConfig.getDefaultEnableAutoConnect())
                    .create(multiplayerCategory);
            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.enableIsolatedMode"),
                    clientConfig::getEnableIsolatedMode,
                    clientConfig::setEnableIsolatedMode)
                    .setDefaultValue(clientConfig.getDefaultEnableIsolatedMode())
                    .setOnChanged(() -> {
                        if (ConnectionStateMachine.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED) {
                            if (clientConfig.getEnableIsolatedMode()) {
                                connectionManager.switchToIsolate();
                            } else {
                                connectionManager.disconnect();
                            }
                            MainFragment.refresh();
                        }
                    }).create(multiplayerCategory);

            AutoConnectServerFilterType[] filterTypes = {AutoConnectServerFilterType.BLACK_LIST, AutoConnectServerFilterType.WHITE_LIST};
            List<AutoConnectServerFilterType> filterTypeList = Arrays.stream(filterTypes).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.externalServer.serverFilterType"),
                    filterTypes,
                    filterTypeList::indexOf,
                    clientConfig::getConnectServerFilterType,
                    clientConfig::setConnectServerFilterType)
                    .setDefaultValue(clientConfig.getDefaultConnectServerFilterType())
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

            ApiServerManager apiServerManager = ApiServerManager.getInstance();
            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.enable"),
                    clientConfig::getEnabledInIntegratedServer,
                    clientConfig::setEnabledInIntegratedServer)
                    .setDefaultValue(clientConfig.getDefaultEnabledInIntegratedServer()).setOnChanged(() -> {
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
                    })
                    .create(integratedServerCategory);

            new PreferencesFragment.FloatOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.integratedServer.pusherVoteAdditionalRate"),
                    serverConfig::getPusherVoteAdditionalRate,
                    serverConfig::setPusherVoteAdditionalRate)
                    .setRange(0, 1)
                    .setDefaultValue(serverConfig.getDefaultPusherVoteAdditionalRate())
                    .create(integratedServerCategory);

            var apiCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.apiServer"));
            LinearLayout.LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params1.setMargins(0, dp(6), 0, dp(6));
            view.addView(apiCategory, params1);

            new PreferencesFragment.BooleanOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.startupBinaryApiServerWhenLaunch"),
                    serverConfig::getStartupBinaryApiServerWhenLaunch,
                    serverConfig::setStartupBinaryApiServerWhenLaunch)
                    .setDefaultValue(serverConfig.getDefaultStartupBinaryApiServerWhenLaunch())
                    .create(apiCategory);

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

            InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder().inset(0).cornerRadius(dp(4))
                    .padding(new InsetBackgroundFactory.Padding(dp(8), dp(4), dp(8), dp(4))).build();

            Button downloadApiServerButton = new ApiServerDownloadDialog(context, backgroundFactory,
                    configPath -> {
                        serverConfig.setServerApiBinaryExecutablePath(configPath);
                        if (serverApiBinaryPathInput[0] != null) {
                            serverApiBinaryPathInput[0].setText(configPath);
                        }
                    }).createButton();

            Button stopApiServerButton = new Button(context);
            stopApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.stopApiServer"));
            stopApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            stopApiServerButton.setTextSize(14);
            backgroundFactory.applyBackgroundTo(stopApiServerButton);
            stopApiServerButton.setOnClickListener((v1) -> apiServerManager.stopApiServer());

            Button restartApiServerButton = new Button(context);
            restartApiServerButton.setText(I18n.get(MusicHud.MOD_ID + ".button.restartApiServer"));
            restartApiServerButton.setTextColor(Theme.PRIMARY_COLOR);
            restartApiServerButton.setTextSize(14);
            backgroundFactory.applyBackgroundTo(restartApiServerButton);
            restartApiServerButton.setOnClickListener((v1) -> apiServerManager.restartApiServer());

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
            backgroundFactory.applyBackgroundTo(checkVersionButton);
            checkVersionButton.setOnClickListener((v) -> {
                ApiClient.checkAvailable();
                apiVersionLabel.setText(apiServiceVersionTemplate.replace("{}", I18n.get(ApiClient.getVersion())));
            });

            apiVersionLinearLayout.addView(apiVersionLabel, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            apiVersionLinearLayout.addView(checkVersionButton);
            apiCategory.addView(apiVersionLinearLayout);

            LinearLayout apiLogLayout = new LinearLayout(context);
            apiLogLayout.setOrientation(LinearLayout.HORIZONTAL);
            apiLogLayout.setGravity(Gravity.LEFT);
            apiLogLayout.setVerticalGravity(Gravity.CENTER);
            LayoutParams logParams = new LayoutParams(MATCH_PARENT, dp(44));
            logParams.setMargins(dp(6), 0, dp(6), 0);
            apiLogLayout.setLayoutParams(logParams);

            TextView apiLogLabel = new TextView(context);
            apiLogLabel.setTextSize(14);
            updateApiLogLabel(apiLogLabel);

            Button refreshApiLogButton = new Button(context);
            refreshApiLogButton.setText(I18n.get(MusicHud.MOD_ID + ".button.refreshApiLog"));
            refreshApiLogButton.setTextColor(Theme.PRIMARY_COLOR);
            refreshApiLogButton.setTextSize(14);
            backgroundFactory.applyBackgroundTo(refreshApiLogButton);
            refreshApiLogButton.setOnClickListener(v -> updateApiLogLabel(apiLogLabel));

            Button openApiLogDirButton = new Button(context);
            openApiLogDirButton.setText(I18n.get(MusicHud.MOD_ID + ".button.openApiLogDir"));
            openApiLogDirButton.setTextColor(Theme.PRIMARY_COLOR);
            openApiLogDirButton.setTextSize(14);
            backgroundFactory.applyBackgroundTo(openApiLogDirButton);
            openApiLogDirButton.setOnClickListener(v -> {
                Path logDir = apiServerManager.getLogDir();
                try {
                    Files.createDirectories(logDir);
                } catch (IOException ignored) {
                }
                Util.getPlatform().openFile(logDir.toFile());
                updateApiLogLabel(apiLogLabel);
            });

            Button clearApiLogButton = new Button(context);
            clearApiLogButton.setText(I18n.get(MusicHud.MOD_ID + ".button.clearApiLogs"));
            clearApiLogButton.setTextColor(Theme.ERROR_TEXT_COLOR);
            clearApiLogButton.setTextSize(14);
            backgroundFactory.applyBackgroundTo(clearApiLogButton);
            clearApiLogButton.setOnClickListener(v -> {
                LinearLayout warnContent = new LinearLayout(context);
                warnContent.setOrientation(LinearLayout.VERTICAL);
                TextView warnText = new TextView(context);
                warnText.setText(I18n.get(MusicHud.MOD_ID + ".modal.clearApiLogs.warning"));
                warnText.setTextSize(Theme.TEXT_SIZE_LARGE);
                warnText.setTextColor(Theme.NORMAL_TEXT_COLOR);
                warnContent.addView(warnText);
                new Modal(context, warnContent,
                        new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".modal.clearApiLogs.button1"), (btn, modal) -> {
                            ApiServerManager.getInstance().clearLogs();
                            updateApiLogLabel(apiLogLabel);
                            modal.dismiss();
                        }),
                        new Modal.ActionButton(I18n.get(MusicHud.MOD_ID + ".modal.clearApiLogs.button2"), (btn, modal) -> modal.dismiss())
                ).show();
            });

            apiLogLayout.addView(apiLogLabel, new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            apiLogLayout.addView(refreshApiLogButton);
            apiLogLayout.addView(openApiLogDirButton);
            apiLogLayout.addView(clearApiLogButton);
            apiCategory.addView(apiLogLayout);

            var envVarCategory = PreferencesFragment.createCategoryList(view, null);
            {
                var transition = new LayoutTransition();
                transition.enableTransitionType(LayoutTransition.CHANGING);
                envVarCategory.setLayoutTransition(transition);

                final Button title = new ToggleButton(context, null, R.attr.borderlessButtonStyle);
                title.setText(I18n.get(MusicHud.MOD_ID + ".config.apiServer.environmentVariables"));
                title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                {
                    var icon = new StateListDrawable();
                    icon.addState(new int[]{R.attr.state_checked}, new BuiltinIconDrawable(
                            context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_UP
                    ));
                    icon.addState(StateSet.WILD_CARD, new BuiltinIconDrawable(
                            context.getResources(), BuiltinIconDrawable.KEYBOARD_ARROW_DOWN
                    ));
                    icon.setTintList(title.getTextColors());
                    title.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
                }
                title.setOnClickListener(new EnvVarAccordion(envVarCategory));
                envVarCategory.addView(title, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            LayoutParams envVarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            envVarParams.setMargins(0, dp(6), 0, dp(128));
            view.addView(envVarCategory, envVarParams);

            Consumer<ApiServerManager.BinaryApiServerStatus> listener = apiServerStatus -> MuiModApi.postToUiThread(
                    () -> {
                        apiStatusLabel.setText(binaryApiStatusTemplate.replace("{}", I18n.get(apiServerStatus.i18nKey())));
                        apiVersionLabel.setText(apiServiceVersionTemplate.replace("{}", I18n.get(ApiClient.getVersion())));
                        updateApiLogLabel(apiLogLabel);
                    }
            );
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

    private static void updateApiLogLabel(TextView label) {
        long[] stats = ApiServerManager.getInstance().getLogStats();
        String template = I18n.get(MusicHud.MOD_ID + ".text.apiLogInfo");
        label.setText(template.replace("{count}", String.valueOf(stats[0]))
                .replace("{size}", formatBytes(stats[1])));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024) return String.format("%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 100) return String.format("%.1f MiB", mib);
        return String.format("%.0f MiB", mib);
    }

    private static final class EnvVarAccordion implements View.OnClickListener {
        final ViewGroup mParent;
        LinearLayout mContent;

        EnvVarAccordion(ViewGroup parent) {
            mParent = parent;
        }

        private static void createEnvVarInputBox(Context context, LinearLayout parent, String i18nKey,
                                                 String currentValue, Consumer<String> setter) {
            LinearLayout inputBox = PreferencesFragment.createInputBox(context, I18n.get(i18nKey));
            EditText input = inputBox.findViewById(R.id.input);
            if (input != null) {
                input.setMinimumWidth(input.dp(256));
                input.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                input.setText(currentValue);
                input.setOnKeyListener((v, c, e) -> {
                    if (c == GLFW.GLFW_KEY_ENTER) {
                        input.clearFocus();
                        return true;
                    }
                    return false;
                });
                input.setOnFocusChangeListener((v, b) -> {
                    if (!b) {
                        setter.accept(input.getText().toString());
                    }
                });
            }
            parent.addView(inputBox);
        }

        @Override
        public void onClick(View v) {
            if (mContent != null) {
                mContent.setVisibility(mContent.getVisibility() == View.GONE
                        ? View.VISIBLE
                        : View.GONE);
                return;
            }
            addContent();
        }

        private void addContent() {
            var context = mParent.getContext();
            mContent = new LinearLayout(context);
            mContent.setOrientation(LinearLayout.VERTICAL);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.useRandomCnIp"),
                    serverConfig::getUseRandomCnIp,
                    serverConfig::setUseRandomCnIp)
                    .setDefaultValue(serverConfig.getDefaultUseRandomCnIp())
                    .create(mContent);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableGeneralUnblock"),
                    serverConfig::getEnableGeneralUnblock,
                    serverConfig::setEnableGeneralUnblock)
                    .setDefaultValue(serverConfig.getDefaultEnableGeneralUnblock())
                    .create(mContent);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableFlac"),
                    serverConfig::getEnableFlac,
                    serverConfig::setEnableFlac)
                    .setDefaultValue(serverConfig.getDefaultEnableFlac())
                    .create(mContent);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.selectMaxBr"),
                    serverConfig::getSelectMaxBr,
                    serverConfig::setSelectMaxBr)
                    .setDefaultValue(serverConfig.getDefaultSelectMaxBr())
                    .create(mContent);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.followSourceOrder"),
                    serverConfig::getFollowSourceOrder,
                    serverConfig::setFollowSourceOrder)
                    .setDefaultValue(serverConfig.getDefaultFollowSourceOrder())
                    .create(mContent);

            new PreferencesFragment.IntegerOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.port"),
                    serverConfig::getPort,
                    serverConfig::setPort)
                    .setRange(1, 65535)
                    .setDefaultValue(serverConfig.getDefaultPort())
                    .create(mContent);

            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.apiServer.enableProxy"),
                    serverConfig::getEnableProxy,
                    serverConfig::setEnableProxy)
                    .setDefaultValue(serverConfig.getDefaultEnableProxy())
                    .create(mContent);

            createEnvVarInputBox(context, mContent, MusicHud.MOD_ID + ".config.apiServer.corsAllowOrigin",
                    serverConfig.getCorsAllowOrigin(), serverConfig::setCorsAllowOrigin);
            createEnvVarInputBox(context, mContent, MusicHud.MOD_ID + ".config.apiServer.proxyUrl",
                    serverConfig.getProxyUrl(), serverConfig::setProxyUrl);

            var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.setMargins(0, mContent.dp(6), 0, 0);
            mParent.addView(mContent, params);
        }
    }
}
