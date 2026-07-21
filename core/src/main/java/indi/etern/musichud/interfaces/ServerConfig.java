package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;

import java.util.function.Supplier;

public interface ServerConfig {
    static ServerConfig getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<ServerConfig> supplier = platform.getServerConfigSupplier();
        if (supplier != null) {
            ServerConfig serverConfig = supplier.get();
            if (serverConfig != null) {
                return serverConfig;
            }
        }
        throw new UnsupportedOperationException();
    }

    String getServerApiBaseUrl();

    void setServerApiBaseUrl(String serverApiBaseUrl);

    boolean getStartupBinaryApiServerWhenLaunch();

    void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch);

    String getServerApiBinaryExecutablePath();

    void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath);

    double getPusherVoteAdditionalRate();

    void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate);

    boolean getUseRandomCnIp();

    void setUseRandomCnIp(boolean useRandomCnIp);

    String getCorsAllowOrigin();

    void setCorsAllowOrigin(String corsAllowOrigin);

    boolean getEnableProxy();

    void setEnableProxy(boolean enableProxy);

    String getProxyUrl();

    void setProxyUrl(String proxyUrl);

    boolean getEnableGeneralUnblock();

    void setEnableGeneralUnblock(boolean enableGeneralUnblock);

    boolean getEnableFlac();

    void setEnableFlac(boolean enableFlac);

    boolean getSelectMaxBr();

    void setSelectMaxBr(boolean selectMaxBr);

    boolean getFollowSourceOrder();

    void setFollowSourceOrder(boolean followSourceOrder);

    int getPort();

    void setPort(int port);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);
}