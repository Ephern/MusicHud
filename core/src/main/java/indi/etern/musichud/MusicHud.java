package indi.etern.musichud;

import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.RegistrationManager;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.InputStream;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicHud {
    public static final String MOD_ID = "music_hud";
    public static final Random RANDOM = new Random();
    public static final String ICON_BASE64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAB0ElEQVQ4jX2T0YrTQBSGv8mkmaltliJFKCjdyAp6KV4v7IvoUyz4DAu+xr7Hwt4K7gOsBq+KWLGl6SaTZGa8aDPbWPFAYHI455///88c8eb1qQcYqxEAhdly+H8YhdkyVqNQAxADvB0D2pFNJ8AJ33+tSVWM9Z7WOVrrefH0BDghX64gHUJV8qXYA6CHZNMJs1QDMEs1n27z3u2X82cA3H7eYIH5acq4iYh3VB2zVLPYVFzfLQAQQqCUCgAd4EvvMVEU8o8n4PpugZQyNFtrqeuauq7RWqO15ttgwPuLs71ciA4NAXDOoZTCe4+UkiRJSJKEuq6PTO0xuLq574EIIRgZwxNjAAKI1n1/orEaBTqHAMOHB16VJVlVBZDDyJcroJvCX9E0DVYISiFohcDu81LKo9oegLWWON6l1kCeJHigUQrnHFJKmqY59iBfrvh4cQaA955oP6Y1UA4GOOeIoghjDNZaLs8zsumEwmyPJVhrkVIGEGst1u5EtG17JKH3Dj68ex6a2rbFex++rrmrgd2+BAaLTcUs1UHK1c19uBkI+a52N4WIuDBbGEC+BJjQeXI+HwOQTScIAT8Kw9efvx/pViWFAdGtc0fpf/Gvdf4DDx7ZzHsT7GgAAAAASUVORK5CYII=";
    public static final String LOGGER_BASE_NAME = "MusicHud";
    public static final Logger LOGGER = LogManager.getLogger(LOGGER_BASE_NAME);
    public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    @Setter
    private static Environment currentEnvironment;
    private static long initAtMillis;
    private static Level logLevel = Level.INFO;
    private static boolean logLevelRead = false;

    public static Logger getLogger(Class<?> clazz) {
        if (!logLevelRead) {
            logLevelRead = true;
            try {
                String sysLogLevel = System.getProperty("musichud.log.level");
                if (sysLogLevel != null && !sysLogLevel.isEmpty()) {
                    logLevel = Level.valueOf(sysLogLevel.toUpperCase());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load log level from property", e);
            }
        }
        Logger logger = LogManager.getLogger(LOGGER_BASE_NAME + "/" + clazz.getSimpleName());
        Configurator.setLevel(logger, logLevel);
        return logger;
    }

    public static void init() {
        if (currentEnvironment == null) {
            throw new IllegalStateException("Current environment is not set");
        }
        Configurator.setLevel(LOGGER, logLevel);
        LOGGER.debug("Initialized in environment: {}", currentEnvironment);
        RegistrationManager.performCommonAutoRegistration();
        initAtMillis = System.currentTimeMillis();
    }

    /**
     * Opens a resource that is shipped on the mod class path.
     * <p>
     * {@link Class#getResourceAsStream(String)} on a class that lives in a <em>named module</em> only
     * searches the content of that very module (see {@code Module#getResourceAsStream}). In a NeoForge
     * dev launch every mod (and the {@code core} library) is its own module, so loading an asset that
     * lives in another mod would silently return {@code null}. Asking the class loader instead keeps
     * the class-path-wide lookup that Fabric dev and the shaded production jar have.
     *
     * @param path resource path, with or without a leading {@code /}
     * @return the stream, or {@code null} if no loaded mod provides that resource
     */
    public static InputStream getResourceAsStream(String path) {
        String name = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader classLoader = MusicHud.class.getClassLoader();
        return classLoader != null
                ? classLoader.getResourceAsStream(name)
                : ClassLoader.getSystemResourceAsStream(name);
    }

    public static long getRunningMillis() {
        return System.currentTimeMillis() - initAtMillis;
    }

    public static void onConfigLoaded() {
        RegistrationManager.performSideAutoRegistration();
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
}