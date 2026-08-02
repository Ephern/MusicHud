package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.Register;
import indi.etern.musichud.platform.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RegistrationManager {
    private static final String REGISTRIES_RESOURCE_PREFIX = "META-INF/musichud-registries";
    private static final List<String> REGISTRY_MODULES = List.of("core", "common");

    private static final Set<Class<?>> registeredSet = new HashSet<>();

    public static void performCommonAutoRegistration() {
        MusicHud.LOGGER.info("Starting auto-registration for common");
        registerFromResources("COMMON");
    }

    public static void performSideAutoRegistration() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        MusicHud.LOGGER.info("Starting auto-registration in environment: {}", side.name());

        if (side == Environment.Side.CLIENT) {
            registerFromResources("CLIENT");
        }
        registerFromResources("SERVER");
    }

    private static void registerFromResources(String category) {
        List<String> classNames = loadClassNames(category);
        if (classNames.isEmpty()) {
            MusicHud.LOGGER.warn("No {} registries found in resources", category);
            return;
        }

        MusicHud.LOGGER.info("Registering {} {} registries from resources", classNames.size(), category);
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (registeredSet.contains(clazz)) continue;
                if (Register.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Register> regClass = (Class<? extends Register>) clazz;
                    if (!regClass.isInterface()) {
                        Register instance = regClass.getDeclaredConstructor().newInstance();
                        instance.register();
                        registeredSet.add(clazz);
                        MusicHud.LOGGER.debug("Successfully registered: {}", clazz.getCanonicalName());
                    }
                } else {
                    MusicHud.LOGGER.warn("Class {} does not implement Register, skipping", className);
                }
            } catch (Throwable e) {
                MusicHud.LOGGER.error("Failed to register: {}", className, e);
            }
        }
    }

    private static List<String> loadClassNames(String category) {
        List<String> classNames = new ArrayList<>();
        ClassLoader classLoader = RegistrationManager.class.getClassLoader();
        for (String module : REGISTRY_MODULES) {
            String resource = REGISTRIES_RESOURCE_PREFIX + "." + module + ".properties";
            try {
                Enumeration<URL> resources = classLoader.getResources(resource);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) continue;
                            int eqIdx = line.indexOf('=');
                            if (eqIdx <= 0) continue;
                            String lineCategory = line.substring(0, eqIdx);
                            String className = line.substring(eqIdx + 1);
                            if (category.equals(lineCategory)) {
                                classNames.add(className);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                MusicHud.LOGGER.error("Failed to load registries resource {}", resource, e);
            }
        }
        return classNames;
    }
}
