package indi.etern.musichud.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ToastUtil {
    public static void show(CharSequence message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.submit(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(message.toString()), true);
            }
        });
    }
}
