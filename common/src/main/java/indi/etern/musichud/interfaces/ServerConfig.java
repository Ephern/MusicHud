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
        }
        throw new UnsupportedOperationException();
    }
}
