package indi.etern.musichud;

import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.RegistrationManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MusicHud {
    public static final String MOD_ID = "music_hud";
    public static final Random RANDOM = new Random();
    public static final String ICON_BASE64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAB0ElEQVQ4jX2T0YrTQBSGv8mkmaltliJFKCjdyAp6KV4v7IvoUyz4DAu+xr7Hwt4K7gOsBq+KWLGl6SaTZGa8aDPbWPFAYHI455///88c8eb1qQcYqxEAhdly+H8YhdkyVqNQAxADvB0D2pFNJ8AJ33+tSVWM9Z7WOVrrefH0BDghX64gHUJV8qXYA6CHZNMJs1QDMEs1n27z3u2X82cA3H7eYIH5acq4iYh3VB2zVLPYVFzfLQAQQqCUCgAd4EvvMVEU8o8n4PpugZQyNFtrqeuauq7RWqO15ttgwPuLs71ciA4NAXDOoZTCe4+UkiRJSJKEuq6PTO0xuLq574EIIRgZwxNjAAKI1n1/orEaBTqHAMOHB16VJVlVBZDDyJcroJvCX9E0DVYISiFohcDu81LKo9oegLWWON6l1kCeJHigUQrnHFJKmqY59iBfrvh4cQaA955oP6Y1UA4GOOeIoghjDNZaLs8zsumEwmyPJVhrkVIGEGst1u5EtG17JKH3Dj68ex6a2rbFex++rrmrgd2+BAaLTcUs1UHK1c19uBkI+a52N4WIuDBbGEC+BJjQeXI+HwOQTScIAT8Kw9efvx/pViWFAdGtc0fpf/Gvdf4DDx7ZzHsT7GgAAAAASUVORK5CYII=";
    public static final String LOGGER_BASE_NAME = "MusicHud";
    public static final Logger LOGGER = LogManager.getLogger(LOGGER_BASE_NAME);
    public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    private static ConnectStatus connectStatus = ConnectStatus.NOT_CONNECTED;
    @Getter
    private static final Set<Consumer<ConnectStatus>> connectStatusListeners = new HashSet<>();
    @Getter
    @Setter
    private static Environment currentEnvironment;
    private static long initAtMillis;
    private static Level logLevel = Level.INFO;

    public static Logger getLogger(Class<?> clazz) {
        Logger logger = LogManager.getLogger(LOGGER_BASE_NAME + "/" + clazz.getSimpleName());
        Configurator.setLevel(logger, logLevel);
        return logger;
    }

    public static void init() {
        if (currentEnvironment == null) {
            throw new IllegalStateException("Current environment is not set");
        }
        String sysLogLevel = System.getProperty("musichud.log.level");
        if (sysLogLevel != null && !sysLogLevel.isEmpty()) {
            logLevel = Level.valueOf(sysLogLevel.toUpperCase());
        }
        Configurator.setLevel(LOGGER, logLevel);
        LOGGER.debug("Initialized in environment: {}", currentEnvironment);
        RegistrationManager.performCommonAutoRegistration();
        initAtMillis = System.currentTimeMillis();
    }

    public static long getRunningMillis() {
        return System.currentTimeMillis() - initAtMillis;
    }

    public static void onConfigLoaded() {
        RegistrationManager.performSideAutoRegistration();
    }

    public static Identifier location(String s) {
        return Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, s);
    }

    public enum ConnectStatus {
        CONNECTED,
        INCOMPATIBLE,
        NOT_CONNECTED
    }

    public static ScheduledTask scheduleWithFixedDelay(Runnable task,
                                                       Duration initialDelay,
                                                       Duration delayBetween) {
        AtomicBoolean running = new AtomicBoolean(true);

        Future<?> future = EXECUTOR.submit(() -> {
            try {
                Thread.sleep(initialDelay.toMillis());
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    task.run();
                    //noinspection BusyWait
                    Thread.sleep(delayBetween.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
        });

        // 优雅关闭：停止新任务并等待现有循环结束
        return () -> {
            running.set(false);
            future.cancel(true);   // 中断线程，使sleep立即返回
        };
    }

    @FunctionalInterface
    public interface ScheduledTask {
        void stop();
    }

    public static void setConnectStatus(ConnectStatus status) {
        MusicHud.connectStatus = status;
        connectStatusListeners.forEach(l -> l.accept(status));
    }
}