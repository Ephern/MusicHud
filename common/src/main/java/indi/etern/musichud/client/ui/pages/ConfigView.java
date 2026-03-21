package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.music.StreamAudioPlayer;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.components.DynamicIntegerOption;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.client.ui.screen.MainFragment;
import lombok.Getter;
import net.minecraft.util.Util;
import net.minecraft.client.resources.language.I18n;

import java.util.Arrays;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ConfigView extends LinearLayout {
    @Getter
    static volatile ConfigView instance;

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
            params.setMargins(0, dp(24), 0, 0);
            scrollView.addView(view, params);

            HudRendererManager hudRendererManager = HudRendererManager.getInstance();

            var commonCategory = PreferencesFragment.createCategoryList(view, I18n.get(MusicHud.MOD_ID + ".config.category.common"));
            PreferencesFragment.BooleanOption booleanOption = new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.enable"),
                    ClientConfigDefinition.enable,
                    ClientConfigDefinition.enable::set);
            booleanOption.create(commonCategory);
            booleanOption.setOnChanged(() -> {
                MuiModApi.postToUiThread(MainFragment::refresh);
                if (ClientConfigDefinition.enable.get()) {
                    LoginService.getInstance().sendConnectMessageToServer();
                } else {
                    MusicService.RegisterImpl.reset();
                    NowPlayingInfo.getInstance().stop();
                    StreamAudioPlayer.getInstance().stop();
                    LoginService.getInstance().logout();
                }
            });
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.showTranslatedCnLyrics"),
                    ClientConfigDefinition.showTranslatedCnLyrics,
                    ClientConfigDefinition.showTranslatedCnLyrics::set)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.disableVanillaMusicWhilePlaying"),
                    ClientConfigDefinition.disableVanillaMusic,
                    ClientConfigDefinition.disableVanillaMusic::set)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.enableHud"),
                    ClientConfigDefinition.enableHud,
                    ClientConfigDefinition.enableHud::set)
                    .create(commonCategory);
            new PreferencesFragment.BooleanOption(context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.switch.autoHide"),
                    ClientConfigDefinition.hideHudWhenNotPlaying,
                    ClientConfigDefinition.hideHudWhenNotPlaying::set)
                    .create(commonCategory);
            Quality[] qualities = {Quality.STANDARD, Quality.EX_HIGH, Quality.LOSSLESS, Quality.HIRES, Quality.JY_EFFECT, Quality.DOLBY, Quality.JY_MASTER, Quality.SKY};
            List<Quality> qualitiesList = Arrays.stream(qualities).toList();
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.common.primaryChosenQuality"),
                    qualities,
                    qualitiesList::indexOf,
                    () -> Quality.valueOf(ClientConfigDefinition.primaryChosenQuality.get()),
                    (quality) -> ClientConfigDefinition.primaryChosenQuality.set(quality.name()))
                    .setDefaultValue(Quality.LOSSLESS)
                    .create(commonCategory);
            view.addView(commonCategory);
            var positionCategory = PreferencesFragment.createCategoryList(view, I18n.get("music_hud.config.category.layout"));
            new PreferencesFragment.DropDownOption<>(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.verticalAlign"),
                    VerticalAlign.values(),
                    VerticalAlign::ordinal,
                    () -> VerticalAlign.valueOf(ClientConfigDefinition.hudVerticalPosition.get()),
                    (verticalAlign) -> ClientConfigDefinition.hudVerticalPosition.set(verticalAlign.name()))
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
                    () -> HorizontalAlign.valueOf(ClientConfigDefinition.hudHorizontalPosition.get()),
                    (hPosition) -> ClientConfigDefinition.hudHorizontalPosition.set(hPosition.name()))
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                    })
                    .setDefaultValue(HorizontalAlign.LEFT)
                    .create(positionCategory);
            new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.offsetX"),
                    ClientConfigDefinition.hudOffsetX,
                    ClientConfigDefinition.hudOffsetX::set)
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
                    ClientConfigDefinition.hudOffsetY,
                    ClientConfigDefinition.hudOffsetY::set)
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
                    ClientConfigDefinition.hudCornerRadius,
                    ClientConfigDefinition.hudCornerRadius::set);
            cornerRadiusOption.setRange(0, ClientConfigDefinition.hudHeight.get() / 2);
            cornerRadiusOption.setOnChanged(() -> {
                hudRendererManager.updateLayoutFromConfig();
                hudRendererManager.refreshStyle();
            });
            cornerRadiusOption.setDefaultValue(8);
            DynamicIntegerOption widthOption = new DynamicIntegerOption(
                    context,
                    I18n.get(MusicHud.MOD_ID + ".config.layout.hudWidth"),
                    ClientConfigDefinition.hudWidth,
                    ClientConfigDefinition.hudWidth::set);
            widthOption.setOnChanged(() -> {
                hudRendererManager.updateLayoutFromConfig();
                hudRendererManager.refreshStyle();
            });
            widthOption.setRange(ClientConfigDefinition.hudHeight.get(), 512, 4);
            widthOption.setDefaultValue(150);
            PreferencesFragment.IntegerOption heightOption = new PreferencesFragment.IntegerOption(
                    context,
                    I18n.get("music_hud.config.layout.hudHeight"),
                    ClientConfigDefinition.hudHeight,
                    ClientConfigDefinition.hudHeight::set)
                    .setOnChanged(() -> {
                        hudRendererManager.updateLayoutFromConfig();
                        hudRendererManager.refreshStyle();
                        cornerRadiusOption.updateRange(0, ClientConfigDefinition.hudHeight.get() / 2, 1);
                        widthOption.updateRange(ClientConfigDefinition.hudHeight.get(), 512, 4);
                    })
                    .setRange(16, 72)
                    .setDefaultValue(44);
            widthOption.create(positionCategory);
            heightOption.create(positionCategory);
            cornerRadiusOption.create(positionCategory);

            view.addView(positionCategory);

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    Util.ioPool().execute(() -> {
                        ClientConfigDefinition.configure.getRight().save();
                    });
                }
            });
        } catch (Exception e) {
            instance = null;
            throw e;
        }
    }
}
