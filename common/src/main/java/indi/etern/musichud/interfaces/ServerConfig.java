package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;

public interface ServerConfig {
    void setServerApiBaseUrl(String serverApiBaseUrl);
    void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch);
    void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath);
    void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate);
    void setUseRandomCnIp(boolean useRandomCnIp);

    String getServerApiBaseUrl();
    boolean getStartupBinaryApiServerWhenLaunch();
    String getServerApiBinaryExecutablePath();
    double getPusherVoteAdditionalRate();
    boolean getUseRandomCnIp();

    void save();
    void setConfigured(boolean configured);
    boolean isConfigured();

    static ServerConfig getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        switch (platform) {
            case FABRIC, NEOFORGE -> {
                return ServerConfigDefinition.getInstance();
            }
            case PAPER -> {
                return ReflectionHolder.load("indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition", ServerConfig.class);
            }
        }
        throw new UnsupportedOperationException();
    }

    final class ReflectionHolder {
        private ReflectionHolder() {
        }

        static <T> T load(String className, Class<T> expectedType) {
            try {
                Object instance = Class.forName(className).getMethod("getInstance").invoke(null);
                return expectedType.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
