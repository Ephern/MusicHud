package indi.etern.musichud.client.ui.screen;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class MusicHudScreen extends Screen {
    private static final ClientConfig CLIENT_CONFIG = ClientConfig.getInstance();
    @Nullable
    private final Screen previous;

    public MusicHudScreen(@Nullable Screen previous) {
        super(Component.literal("Music HUD"));
        this.previous = previous;
    }

    public static MusicHudScreen createScreen(@Nullable Screen previousScreen) {
        return new MusicHudScreen(previousScreen);
    }

    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.submit(() -> {
            if (minecraft.screen instanceof MusicHudScreen screen) {
                screen.rebuildWidgets();
            }
        });
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearWidgets();
        int centerX = width / 2;
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(Component.translatable(MusicHud.MOD_ID + ".button.voteForSkip"), button ->
                MusicHud.EXECUTOR.execute(() -> MusicService.getInstance().keyBindsVoteSkipCurrent())
        ).bounds(centerX - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(CLIENT_CONFIG.getEnableHud() ? "Hide HUD" : "Show HUD"), button -> {
            MusicHud.EXECUTOR.execute(() -> {
                CLIENT_CONFIG.setEnableHud(!CLIENT_CONFIG.getEnableHud());
                CLIENT_CONFIG.save();
                refresh();
            });
        }).bounds(centerX + 2, y, 100, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal(CLIENT_CONFIG.getMuted() ? "Unmute" : "Mute"), button -> {
            MusicHud.EXECUTOR.execute(() -> {
                CLIENT_CONFIG.setMuted(!CLIENT_CONFIG.getMuted());
                CLIENT_CONFIG.save();
                refresh();
            });
        }).bounds(centerX - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle Connection"), button ->
                MusicHud.EXECUTOR.execute(LoginService.getInstance()::keyBindsToggleConnection)
        ).bounds(centerX + 2, y, 100, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(centerX - 50, y, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTick) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTick);
        graphics.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTick) {
        super.extractRenderState(graphics, mouseX, mouseY, deltaTick);
        int centerX = width / 2;
        int y = height / 2 - 70;
        graphics.text(font, title, centerX - font.width(title) / 2, y, 0xFFFFFFFF, true);
        y += 22;

        MusicDetail music = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        String musicName = music == null || music == MusicDetail.NONE
                ? I18n.get(MusicHud.MOD_ID + ".text.idle")
                : music.getName();
        drawCentered(graphics, I18n.get(MusicHud.MOD_ID + ".text.currentMusic") + ": " + musicName, y);
        y += 12;
        drawCentered(graphics, "Status: " + MusicHud.getConnectStatus(), y);
        y += 12;
        drawCentered(graphics, "Volume: " + (CLIENT_CONFIG.getMuted() ? 0 : CLIENT_CONFIG.getSoundVolume()), y);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(font, text, width / 2 - font.width(text) / 2, y, 0xFFE0E0E0, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
