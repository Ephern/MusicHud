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
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.mc.UIManager;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.renderer.VanillaHudGraphics;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Transparent screen for editing HUD
 */
@SuppressWarnings("UnstableApiUsage")
public class HudConfigScreen extends Screen implements MuiScreen {
    private final UIManager mHost;
    @Nullable
    private final Screen mPrevious;
    private final Fragment mFragment;
    @Nullable
    private final ScreenCallback mCallback;
    @Getter
    private static volatile boolean visible = false;

    HudConfigScreen(UIManager host, Fragment fragment,
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

    public static HudConfigScreen createScreen(@NonNull Fragment fragment,
                                               @Nullable Screen previousScreen,
                                               @Nullable CharSequence title) {
        return new HudConfigScreen(UIManager.getInstance(),
                fragment, null, previousScreen, title);
    }

    @Override
    protected void init() {
        super.init();
        visible = true;
        mHost.initScreen(this);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float deltaTick) {
        if (minecraft.level == null) {
            this.extractPanorama(guiGraphics, deltaTick);
            guiGraphics.nextStratum();
            HudRendererManager.getInstance().renderFrame(new VanillaHudGraphics(guiGraphics));
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick) {
        mHost.render(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void removed() {
        super.removed();
        visible = false;
        mHost.removed(this);
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

    @Override
    public void onBackPressed() {
        mHost.getOnBackPressedDispatcher().onBackPressed();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        mHost.onHoverMove(true);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean bl) {
        return false;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        return false;
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double deltaX, double deltaY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        mHost.onScroll(deltaX, deltaY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        mHost.onKeyPress(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        mHost.onKeyRelease(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return mHost.onCharTyped((char) event.codepoint());
    }
}
