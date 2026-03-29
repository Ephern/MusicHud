package indi.etern.musichud.platform;

import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.IEventService;
import indi.etern.musichud.interfaces.IKeyRegistryService;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;

public final class PlatformServiceRegistry {
    private static volatile ClientConfig clientConfig;
    private static volatile ServerConfig serverConfig;
    private static volatile IEventService eventService;
    private static volatile IKeyRegistryService keyRegistryService;
    private static volatile INetworkRegister networkRegister;
    private static volatile IClientNetworkService clientNetworkService;
    private static volatile IServerNetworkService serverNetworkService;

    private PlatformServiceRegistry() {
    }

    public static ClientConfig getClientConfig() {
        return clientConfig;
    }

    public static void setClientConfig(ClientConfig clientConfig) {
        PlatformServiceRegistry.clientConfig = clientConfig;
    }

    public static ServerConfig getServerConfig() {
        return serverConfig;
    }

    public static void setServerConfig(ServerConfig serverConfig) {
        PlatformServiceRegistry.serverConfig = serverConfig;
    }

    public static IEventService getEventService() {
        return eventService;
    }

    public static void setEventService(IEventService eventService) {
        PlatformServiceRegistry.eventService = eventService;
    }

    public static IKeyRegistryService getKeyRegistryService() {
        return keyRegistryService;
    }

    public static void setKeyRegistryService(IKeyRegistryService keyRegistryService) {
        PlatformServiceRegistry.keyRegistryService = keyRegistryService;
    }

    public static INetworkRegister getNetworkRegister() {
        return networkRegister;
    }

    public static void setNetworkRegister(INetworkRegister networkRegister) {
        PlatformServiceRegistry.networkRegister = networkRegister;
    }

    public static IClientNetworkService getClientNetworkService() {
        return clientNetworkService;
    }

    public static void setClientNetworkService(IClientNetworkService clientNetworkService) {
        PlatformServiceRegistry.clientNetworkService = clientNetworkService;
    }

    public static IServerNetworkService getServerNetworkService() {
        return serverNetworkService;
    }

    public static void setServerNetworkService(IServerNetworkService serverNetworkService) {
        PlatformServiceRegistry.serverNetworkService = serverNetworkService;
    }

    public static void clear() {
        clientConfig = null;
        serverConfig = null;
        eventService = null;
        keyRegistryService = null;
        networkRegister = null;
        clientNetworkService = null;
        serverNetworkService = null;
    }
}
