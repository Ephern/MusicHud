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
import icyllis.modernui.graphics.Color;
import icyllis.modernui.mc.*;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents the GUI screen that receives events from Minecraft.
 * All vanilla methods are completely taken over by Modern UI.
 */
@SuppressWarnings("UnstableApiUsage")
public class MusicHudScreen extends Screen implements MuiScreen {
    static {
        MuiModApi.addOnScreenChangeListener((oldScreen, newScreen) -> {
            if (newScreen instanceof MusicHudScreen || newScreen instanceof HudConfigScreen) {
                BlurHandler.INSTANCE.blur(null);
                BlurHandler.INSTANCE.blur(newScreen);
            }
        });
    }

    private final UIManager mHost;
    @Nullable
    private final Screen mPrevious;
    private final Fragment mFragment;
    @Nullable
    private final ScreenCallback mCallback;

    private final static int FADE_IN_DURATION_MILLIS = 200;
    @Getter
    @Setter
    private static double darken = ClientConfig.getInstance().getMainScreenAdditionalBackgroundDarken();

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
        mHost.initScreen(this);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick) {
        ScreenCallback callback = getCallback();
        if (callback == null || callback.hasDefaultBackground()) {
            if (minecraft.level == null) {
                super.extractBackground(gr, mouseX, mouseY, deltaTick);
            } else {
                BlurHandler.INSTANCE.drawScreenBackground(gr, 0, 0, this.width, this.height);
            }
            float progress = Math.clamp((float) MuiModApi.getElapsedTime() / FADE_IN_DURATION_MILLIS, 0, 1);
            gr.fill(0, 0, this.width, this.height, Color.argb((int) (progress * darken * 255), 0, 0, 0));//additional darken
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick) {
        mHost.render(gr, mouseX, mouseY, deltaTick);
        super.extractRenderState(gr, mouseX, mouseY, deltaTick);
    }

    @Override
    public void removed() {
        super.removed();
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

    // IMPL - GuiEventListener

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        mHost.onHoverMove(true);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        super.mouseReleased(event);
        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        super.mouseDragged(event, dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) {
            return true;
        }
        mHost.onScroll(deltaX, deltaY);
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (getFocused() != null && getFocused().keyPressed(event)) {
            return true;
        }
        mHost.onKeyPress(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent event) {
        if (getFocused() != null && getFocused().keyReleased(event)) {
            return true;
        }
        mHost.onKeyRelease(event.key(), event.scancode(), event.modifiers());
        return false;
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (getFocused() != null && getFocused().charTyped(event)) {
            return true;
        }
        return mHost.onCharTyped(event.codepoint());
    }
}