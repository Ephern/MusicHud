package indi.etern.musichud.platform.plugin.velocity.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VelocityNetworkManager implements INetworkRegister, IServerNetworkService, AutoCloseable {
    private static volatile VelocityNetworkManager instance;

    private final Logger logger = MusicHud.getLogger(VelocityNetworkManager.class);
    // Keyed by channel id ("music_hud:xxx") so receivers survive identifier recreation
    private final Map<String, RegisteredReceiver<?>> c2sReceivers = new ConcurrentHashMap<>();
    private final Map<String, ByteBufCodec<?>> s2cCodecs = new ConcurrentHashMap<>();
    private final Map<String, MinecraftChannelIdentifier> identifiers = new ConcurrentHashMap<>();
    private volatile ProxyServer proxyServer;

    private VelocityNetworkManager() {
    }

    public static VelocityNetworkManager getInstance() {
        if (instance == null) {
            synchronized (VelocityNetworkManager.class) {
                if (instance == null) {
                    instance = new VelocityNetworkManager();
                }
            }
        }
        return instance;
    }

    public void initialize(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        // Channels registered before initialization must still be listened on
        identifiers.values().forEach(this::registerListener);
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        String channelId = channelIdOf(clazz);
        c2sReceivers.put(channelId, new RegisteredReceiver<>(codec, serverReceiver));
        ensureListenerRegistered(channelId);
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        String channelId = channelIdOf(clazz);
        s2cCodecs.put(channelId, codec);
        ensureListenerRegistered(channelId);
    }

    @Override
    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientOrServerReceiver
    ) {
        if (S2CPayload.class.isAssignableFrom(clazz)) {
            registerS2CPayload(clazz, codec, clientOrServerReceiver);
            return;
        }
        if (C2SPayload.class.isAssignableFrom(clazz)) {
            registerC2SPayload(clazz, codec, clientOrServerReceiver);
            return;
        }
        throw new IllegalArgumentException("Payload class must implement S2CPayload or C2SPayload");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only client -> proxy messages belong to us; backend -> client messages pass through
        if (!(event.getSource() instanceof Player)) {
            return;
        }
        String channelId = event.getIdentifier().getId();
        RegisteredReceiver<?> registered = c2sReceivers.get(channelId);
        if (registered == null) {
            return;
        }
        // Consume the message so backend servers don't handle it twice
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        byte[] data = event.getData();
        VelocityPlayerProxy playerProxy = VelocityPlayerProxy.ofPlayer((Player) event.getSource());
        MusicHud.EXECUTOR.execute(() -> registered.receive(data, playerProxy));
    }

    @Override
    public void sendToPlayer(IPlayerClient playerClient, S2CPayload payload) {
        ProxyServer server = proxyServer;
        if (server == null) {
            logger.warn("Skipping {} because the proxy is not initialized", payload.getClass().getSimpleName());
            return;
        }
        String channelId = channelIdOf(payload.getClass());
        @SuppressWarnings("unchecked")
        ByteBufCodec<S2CPayload> codec = (ByteBufCodec<S2CPayload>) s2cCodecs.get(channelId);
        if (codec == null) {
            logger.warn("Skipping unregistered S2C payload {}", channelId);
            return;
        }
        Player player = server.getPlayer(playerClient.getUUID()).orElse(null);
        if (player == null) {
            logger.warn("Skipping {} because player {} is no longer online", channelId, playerClient.getName());
            return;
        }
        byte[] networkPayload = encode(codec, payload);
        MinecraftChannelIdentifier identifier = ensureIdentifier(channelId);
        MusicHud.EXECUTOR.execute(() -> {
            // The client mod registers our channels before sending any C2S message,
            // so by the time we reply the channel is always registered client-side
            if (player.isActive()) {
                player.sendPluginMessage(identifier, networkPayload);
            } else {
                logger.warn("Skipping {} because player {} went offline before send",
                        channelId, playerClient.getName());
            }
        });
    }

    @Override
    public void close() {
        ProxyServer server = proxyServer;
        if (server == null) {
            return;
        }
        server.getChannelRegistrar()
                .unregister(identifiers.values().toArray(new MinecraftChannelIdentifier[0]));
        identifiers.clear();
        c2sReceivers.clear();
        s2cCodecs.clear();
        proxyServer = null;
    }

    private byte[] encode(ByteBufCodec<S2CPayload> codec, S2CPayload payload) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static String channelIdOf(Class<?> clazz) {
        String[] words = clazz.getSimpleName().split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        String name = String.join("_", words).toLowerCase();
        return "music_hud:" + name;
    }

    private void ensureListenerRegistered(String channelId) {
        MinecraftChannelIdentifier identifier = ensureIdentifier(channelId);
        registerListener(identifier);
    }

    private MinecraftChannelIdentifier ensureIdentifier(String channelId) {
        return identifiers.computeIfAbsent(channelId, id -> MinecraftChannelIdentifier.from(id));
    }

    private void registerListener(MinecraftChannelIdentifier identifier) {
        ProxyServer server = proxyServer;
        if (server != null) {
            server.getChannelRegistrar().register(identifier);
        }
    }

    private record RegisteredReceiver<T extends IPayload>(
            ByteBufCodec<T> codec,
            NetworkReceiver<T> receiver
    ) {
        private void receive(byte[] bytes, IPlayerClient player) {
            T result;
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            try {
                result = codec.decode(buffer);
            } finally {
                buffer.release();
            }
            T payload = result;
            receiver.receive(payload, player);
        }
    }
}
