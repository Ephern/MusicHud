package indi.etern.musichud.client.ui.screen;

/*
 * Modified from Modern UI
 */

/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.hud.renderer.BackgroundRenderer;
import indi.etern.musichud.client.ui.hud.renderer.BackgroundTarget;
import indi.etern.musichud.client.ui.render.HorizontalAlign;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.VanillaSceneGraphics;
import indi.etern.musichud.client.ui.render.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents the GUI screen that receives events from Minecraft.
 * All vanilla methods are completely taken over by Modern UI.
 */
@SuppressWarnings("UnstableApiUsage")
public class MusicHudScreen extends Screen implements MuiScreen {
    private static final ClientConfig CLIENT_CONFIG = ClientConfig.getInstance();
    private static final Logger LOGGER = MusicHud.getLogger(MusicHudScreen.class);
    private static final BackgroundRenderer BACKGROUND_RENDERER =
            new BackgroundRenderer(BackgroundTarget.GUI);
    @Getter
    private static volatile boolean hudOccluded;
    private final UIManager mHost;
    @Nullable
    private final Screen mPrevious;
    private final Fragment mFragment;
    @Nullable
    private final ScreenCallback mCallback;
    private final SceneStyle backgroundStyle = new SceneStyle(
            0, 0, 0, 0, 0, HorizontalAlign.CENTER, VerticalAlign.CENTER);

    private final static int FADE_IN_DURATION_MILLIS = 200;

    MusicHudScreen(UIManager host, Fragment fragment,
                   @Nullable ScreenCallback callback, @Nullable Screen previous,
                   @Nullable CharSequence title) {
        super(title == null || title.isEmpty()
                ? CommonComponents.EMPTY
                : Component.literal(title.toString()));
        mHost = host;
        mPrevious = previous;
        mFragment = Objects.requireNonNull(fragment);
        mCallback = callback != null ? callback :
                fragment instanceof ScreenCallback cbk ? cbk : null;
    }

    public static MusicHudScreen createScreen(@NonNull Fragment fragment,
                                              @Nullable ScreenCallback callback,
                                              @Nullable Screen previousScreen,
                                              @Nullable CharSequence title) {
        return new MusicHudScreen(UIManager.getInstance(),
                fragment, callback, previousScreen, title);
    }

    @Override
    protected void init() {
        super.init();
        hudOccluded = false;
        mHost.initScreen(this);
    }

    @Override
    public void resize(@NonNull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        ScreenCallback callback = getCallback();
        if (callback != null && !callback.hasDefaultBackground()) {
            hudOccluded = true;
            return;
        }
        if (minecraft != null && minecraft.level == null) {
            renderPanorama(gr, deltaTick);
        }
        float progress = Math.clamp((float) MuiModApi.getElapsedTime() / FADE_IN_DURATION_MILLIS, 0, 1);
        hudOccluded = progress >= 1f;
        renderBlurredBackground(gr, progress);
    }

    private void renderBlurredBackground(GuiGraphics gr, float progress) {
        try {
            backgroundStyle.setAlpha(progress);
            backgroundStyle.setBounds(0, 0, this.width, this.height);
            gr.nextStratum();
            RenderRuntime.getInstance().renderFrame(
                    new VanillaSceneGraphics(gr),
                    context -> {
                        if (!BACKGROUND_RENDERER.renderFluidBackground(context, backgroundStyle)) {
                            renderSolidBackground(gr, progress);
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.error("While rendering main GUI background", e);
        }
    }

    private void renderSolidBackground(GuiGraphics gr, float progress) {
        float alpha = (float) CLIENT_CONFIG.getGuiBackgroundSamplingBlackOverlay() * progress;
        if (alpha <= 1e-4f) {
            return;
        }
        fillBlack(gr, alpha);
    }

    private void fillBlack(GuiGraphics gr, float alpha) {
        int alphaByte = Math.clamp((int) (alpha * 255), 0, 255);
        gr.fill(0, 0, this.width, this.height, (alphaByte << 24));
    }

    @Override
    public void render(@NonNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        mHost.render(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void removed() {
        super.removed();
        hudOccluded = false;
        mHost.removed(this);
        BackgroundState.getInstance().getOverrides().clear();
    }

    @Override
    public boolean isPauseScreen() {
        ScreenCallback callback = getCallback();
        return callback == null || callback.isPauseScreen();
    }

    @NonNull
    @Override
    public Screen self() {
        return this;
    }

    @NonNull
    @Override
    public Fragment getFragment() {
        return mFragment;
    }

    @Nullable
    @Override
    public ScreenCallback getCallback() {
        return mCallback;
    }

    @Nullable
    @Override
    public Screen getPreviousScreen() {
        return mPrevious;
    }

    @Override
    public boolean isMenuScreen() {
        return false;
    }

    // IMPL - GuiEventListener

    @Override
    public void onBackPressed() {
        mHost.getOnBackPressedDispatcher().onBackPressed();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        mHost.onHoverMove(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double deltaX, double deltaY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        mHost.onScroll(deltaX, deltaY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        mHost.onKeyPress(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        mHost.onKeyRelease(keyCode, scanCode, modifiers);
        return false;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return mHost.onCharTyped(ch);
    }
}

