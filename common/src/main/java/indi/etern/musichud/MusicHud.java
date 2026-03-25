package indi.etern.musichud;

import dev.architectury.event.EventHandler;
import dev.architectury.platform.Platform;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.server.config.ServerConfigDefinition;
import indi.etern.musichud.utils.RegistrationManager;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.EnvType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.IConfigSpec;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MusicHud {
    public static final String MOD_ID = "music_hud";
    public static final Random RANDOM = new Random();
    public static final String ICON_BASE64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAB0ElEQVQ4jX2T0YrTQBSGv8mkmaltliJFKCjdyAp6KV4v7IvoUyz4DAu+xr7Hwt4K7gOsBq+KWLGl6SaTZGa8aDPbWPFAYHI455///88c8eb1qQcYqxEAhdly+H8YhdkyVqNQAxADvB0D2pFNJ8AJ33+tSVWM9Z7WOVrrefH0BDghX64gHUJV8qXYA6CHZNMJs1QDMEs1n27z3u2X82cA3H7eYIH5acq4iYh3VB2zVLPYVFzfLQAQQqCUCgAd4EvvMVEU8o8n4PpugZQyNFtrqeuauq7RWqO15ttgwPuLs71ciA4NAXDOoZTCe4+UkiRJSJKEuq6PTO0xuLq574EIIRgZwxNjAAKI1n1/orEaBTqHAMOHB16VJVlVBZDDyJcroJvCX9E0DVYISiFohcDu81LKo9oegLWWON6l1kCeJHigUQrnHFJKmqY59iBfrvh4cQaA955oP6Y1UA4GOOeIoghjDNZaLs8zsumEwmyPJVhrkVIGEGst1u5EtG17JKH3Dj68ex6a2rbFex++rrmrgd2+BAaLTcUs1UHK1c19uBkI+a52N4WIuDBbGEC+BJjQeXI+HwOQTScIAT8Kw9efvx/pViWFAdGtc0fpf/Gvdf4DDx7ZzHsT7GgAAAAASUVORK5CYII=";
    public static final String LOGGER_BASE_NAME = "MusicHud";
    public static final Logger LOGGER = LogManager.getLogger(LOGGER_BASE_NAME);
    public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    @Setter
    @Getter
    private static ConnectStatus status = ConnectStatus.NOT_CONNECTED;

    public static Logger getLogger(Class<?> clazz) {
        Logger logger = LogManager.getLogger(LOGGER_BASE_NAME + "/" + clazz.getSimpleName());
        logger.atLevel(Level.ALL);
        return logger;
    }

    public static void checkConfigAndInit(IConfigSpec modConfigSpec) {
        if (modConfigSpec.equals(ServerConfigDefinition.configure.getRight())) {
            ServerConfigDefinition.configured = true;
        } else if (modConfigSpec.equals(ClientConfigDefinition.configure.getRight())) {
            ClientConfigDefinition.configured = true;
        }
        if ((Platform.getEnv() == EnvType.CLIENT && ClientConfigDefinition.configured && ServerConfigDefinition.configured)
                || (Platform.getEnv() == EnvType.SERVER && ServerConfigDefinition.configured)) {
            LOGGER.atLevel(Level.ALL);
            EventHandler.init();
            RegistrationManager.performAutoRegistration(Platform.getEnv());
        }
    }

    public static ResourceLocation location(String s) {
        return ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, s);
    }

    public enum ConnectStatus {
        CONNECTED,
        INCAPABLE,
        NOT_CONNECTED
    }
}