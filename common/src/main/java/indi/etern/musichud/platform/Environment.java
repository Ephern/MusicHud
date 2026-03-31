package indi.etern.musichud.platform;

import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition;
import lombok.*;

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
    public enum Platform {
        FABRIC(
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition", ServerConfig.class),
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition", ClientConfig.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager", IServerNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager", IClientNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.event.ModServerEventService", IServerEventService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.event.ModClientEventService", IClientEventService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.registry.ModKeyRegistryService", IKeyRegistryService.class)
        ),
        NEOFORGE(
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ServerConfigDefinition", ServerConfig.class),
                () -> load("indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition", ClientConfig.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager", IServerNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.network.ModNetworkManager", IClientNetworkService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.event.ModServerEventService", IServerEventService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.event.ModClientEventService", IClientEventService.class),
                () -> load("indi.etern.musichud.platform.mod.architectury.registry.ModKeyRegistryService", IKeyRegistryService.class)
        ),
        PAPER(
                () -> load("indi.etern.musichud.platform.plugin.paper.config.ServerConfigDefinition", ServerConfigDefinition.class),
                null,
                () -> load("indi.etern.musichud.platform.plugin.paper.network.PaperNetworkManager", IServerNetworkService.class),
                null,
                () -> load("indi.etern.musichud.platform.plugin.paper.event.PaperEventService", IServerEventService.class),
                null,
                null
        );

        private final Supplier<ServerConfig> serverConfigSupplier;
        private final Supplier<ClientConfig> clientConfigSupplier;
        private final Supplier<IServerNetworkService> serverNetworkServiceSupplier;
        private final Supplier<IClientNetworkService> clientNetworkServiceSupplier;
        private final Supplier<IServerEventService> serverEventServiceSupplier;
        private final Supplier<IClientEventService> clientEventServiceSupplier;
        private final Supplier<IKeyRegistryService> keyRegistryServiceSupplier;
        Platform(
                Supplier<ServerConfig> serverConfigSupplier,
                Supplier<ClientConfig> clientConfigSupplier,
                Supplier<IServerNetworkService> serverNetworkServiceSupplier,
                Supplier<IClientNetworkService> clientNetworkServiceSupplier,
                Supplier<IServerEventService> serverEventServiceSupplier,
                Supplier<IClientEventService> clientEventServiceSupplier,
                Supplier<IKeyRegistryService> keyRegistryServiceSupplier
        ) {
            this.serverConfigSupplier = serverConfigSupplier;
            this.clientConfigSupplier = clientConfigSupplier;
            this.serverNetworkServiceSupplier = serverNetworkServiceSupplier;
            this.clientNetworkServiceSupplier = clientNetworkServiceSupplier;
            this.serverEventServiceSupplier = serverEventServiceSupplier;
            this.clientEventServiceSupplier = clientEventServiceSupplier;
            this.keyRegistryServiceSupplier = keyRegistryServiceSupplier;
        }

        @SneakyThrows
        static <T> T load(String className, Class<T> expectedType) {
            Object instance = Class.forName(className).getMethod("getInstance").invoke(null);
            return expectedType.cast(instance);
        }
    }

    @Override
    public String toString() {
        return "Environment{" + platform.name() + "-" + side.name() + "}";
    }
}
