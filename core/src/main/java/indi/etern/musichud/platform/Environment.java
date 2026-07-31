package indi.etern.musichud.platform;

import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Environment {
    private Side side;
    private Platform platform;

    public static Environment of(Side side, Platform platform) {
        return new Environment(side, platform);
    }

    public enum Side {
        CLIENT,
        SERVER
    }

    @Getter
    @AllArgsConstructor
    public enum Platform {
        FABRIC(
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition", ServerConfig.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.network.FabricNetworkRegister", INetworkRegister.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.network.FabricServerNetworkService", IServerNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.event.FabricCommonEventService", ICommonEventService.class),
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition", ClientConfig.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.network.FabricClientNetworkService", IClientNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.event.FabricClientEventService", IClientEventServiceDefinition.class),
                () -> load("indi.etern.musichud.platform.mod.fabric.registry.FabricKeyRegistryService", IKeyRegistryServiceDefinition.class),
                () -> load("indi.etern.musichud.client.utils.ClientDistUtil", IClientDistUtil.class),
                () -> load("indi.etern.musichud.client.services.LoginService", IClientLoginService.class),
                () -> load("indi.etern.musichud.client.services.music.MusicService", IClientMusicService.class)),
        NEOFORGE(
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition", ServerConfig.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.network.NeoForgeNetworkManager", INetworkRegister.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.network.NeoForgeNetworkManager", IServerNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.event.NeoForgeCommonEventService", ICommonEventService.class),
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition", ClientConfig.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.network.NeoForgeClientNetworkService", IClientNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.event.NeoForgeClientEventService", IClientEventServiceDefinition.class),
                () -> load("indi.etern.musichud.platform.mod.neoforge.registry.NeoForgeKeyRegistryService", IKeyRegistryServiceDefinition.class),
                () -> load("indi.etern.musichud.client.utils.ClientDistUtil", IClientDistUtil.class),
                () -> load("indi.etern.musichud.client.services.LoginService", IClientLoginService.class),
                () -> load("indi.etern.musichud.client.services.music.MusicService", IClientMusicService.class)),
        PAPER(
                () -> load("indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition", ServerConfig.class),
                () -> load("indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager", INetworkRegister.class),
                () -> load("indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager", IServerNetworkService.class),
                () -> load("indi.etern.musichud.platform.plugin.paper.event.PaperEventService", ICommonEventService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        private final Supplier<ServerConfig> serverConfigSupplier;
        private final Supplier<INetworkRegister> networkRegisterSupplier;
        private final Supplier<IServerNetworkService> serverNetworkServiceSupplier;
        private final Supplier<ICommonEventService> serverEventServiceSupplier;
        private final Supplier<ClientConfig> clientConfigSupplier;
        private final Supplier<IClientNetworkService> clientNetworkServiceSupplier;
        private final Supplier<IClientEventServiceDefinition> clientEventServiceSupplier;
        private final Supplier<IKeyRegistryServiceDefinition> keyRegistryServiceSupplier;
        private final Supplier<IClientDistUtil> clientDistUtilSupplier;
        private final Supplier<IClientLoginService> clientLoginServiceSupplier;
        private final Supplier<IClientMusicService> clientMusicServiceSupplier;

        @SneakyThrows
        static <T> T load(String className, Class<T> expectedType) {
            Class<?> clazz = Class.forName(className);
            Object instance = MethodHandles.lookup()
                    .findStatic(clazz, "getInstance", MethodType.methodType(clazz))
                    .invoke();
            return expectedType.cast(instance);
        }
    }

    @Override
    public String toString() {
        return "Environment{" + platform.name() + "-" + side.name() + "}";
    }
}
