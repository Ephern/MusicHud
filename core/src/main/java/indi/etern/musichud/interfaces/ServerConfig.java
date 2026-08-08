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

    String getDefaultServerApiBaseUrl();

    void setServerApiBaseUrl(String serverApiBaseUrl);

    boolean getStartupBinaryApiServerWhenLaunch();

    boolean getDefaultStartupBinaryApiServerWhenLaunch();

    void setStartupBinaryApiServerWhenLaunch(boolean startupBinaryApiServerWhenLaunch);

    String getServerApiBinaryExecutablePath();

    String getDefaultServerApiBinaryExecutablePath();

    void setServerApiBinaryExecutablePath(String serverApiBinaryExecutablePath);

    double getPusherVoteAdditionalRate();

    double getDefaultPusherVoteAdditionalRate();

    void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate);

    boolean getUseRandomCnIp();

    boolean getDefaultUseRandomCnIp();

    void setUseRandomCnIp(boolean useRandomCnIp);

    String getCorsAllowOrigin();

    String getDefaultCorsAllowOrigin();

    void setCorsAllowOrigin(String corsAllowOrigin);

    boolean getEnableProxy();

    boolean getDefaultEnableProxy();

    void setEnableProxy(boolean enableProxy);

    String getProxyUrl();

    String getDefaultProxyUrl();

    void setProxyUrl(String proxyUrl);

    boolean getEnableGeneralUnblock();

    boolean getDefaultEnableGeneralUnblock();

    void setEnableGeneralUnblock(boolean enableGeneralUnblock);

    boolean getEnableFlac();

    boolean getDefaultEnableFlac();

    void setEnableFlac(boolean enableFlac);

    boolean getSelectMaxBr();

    boolean getDefaultSelectMaxBr();

    void setSelectMaxBr(boolean selectMaxBr);

    boolean getFollowSourceOrder();

    boolean getDefaultFollowSourceOrder();

    void setFollowSourceOrder(boolean followSourceOrder);

    int getPort();

    int getDefaultPort();

    void setPort(int port);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);
}