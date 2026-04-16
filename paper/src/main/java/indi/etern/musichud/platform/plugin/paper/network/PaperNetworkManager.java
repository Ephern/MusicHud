package indi.etern.musichud.platform.plugin.paper.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperNetworkManager implements INetworkRegister, IServerNetworkService, AutoCloseable {
    private static final int MAX_PLUGIN_MESSAGE_RETRY_TICKS = 40;
    private static volatile PaperNetworkManager instance;

    private final Logger logger = MusicHud.getLogger(PaperNetworkManager.class);
    private final Map<Class<? extends IPayload>, CustomPacketPayload.Type<? extends IPayload>> typeMap = new ConcurrentHashMap<>();
    private final Map<String, RegisteredReceiver<?>> c2sReceivers = new ConcurrentHashMap<>();
    private final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ? extends IPayload>> s2cCodecs =
            new ConcurrentHashMap<>();
    private final Set<String> incomingChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> outgoingChannels = ConcurrentHashMap.newKeySet();
    private JavaPlugin plugin;

    private PaperNetworkManager() {
    }

    public static PaperNetworkManager getInstance() {
        if (instance == null) {
            synchronized (PaperNetworkManager.class) {
                if (instance == null) {
                    instance = new PaperNetworkManager();
                }
            }
        }
        return instance;
    }

    public void initialize(JavaPlugin plugin) {
        if (this.plugin == plugin) {
            return;
        }
        this.plugin = plugin;
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        String channel = getType(clazz).id().toString();
        c2sReceivers.put(channel, new RegisteredReceiver<>(codec, serverReceiver));
        ensureIncomingChannelRegistered(channel);
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        CustomPacketPayload.Type<T> type = getType(clazz);
        s2cCodecs.put(type, codec);
        ensureOutgoingChannelRegistered(type.id().toString());
    }

    @Override
    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
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

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IPayload> CustomPacketPayload.Type<T> getType(Class<T> customPacketPayloadClass) {
        CustomPacketPayload.Type<? extends IPayload> cached = typeMap.get(customPacketPayloadClass);
        if (cached != null) {
            return (CustomPacketPayload.Type<T>) cached;
        }
        CustomPacketPayload.Type<T> created = new CustomPacketPayload.Type<>(
                MusicHud.location(toPayloadName(customPacketPayloadClass))
        );
        typeMap.put(customPacketPayloadClass, created);
        return created;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, S2CPayload payload) {
        StreamCodec<? super RegistryFriendlyByteBuf, ? extends IPayload> codec = s2cCodecs.get(payload.type());
        if (codec == null) {
            logger.warn("Skipping unregistered S2C payload {}", payload.type().id());
            return;
        }
        Player bukkitPlayer = Bukkit.getPlayer(player.getUUID());
        if (bukkitPlayer == null) {
            logger.warn("Skipping {} because player {} is no longer online", payload.type().id(), player.getScoreboardName());
            return;
        }
        byte[] networkPayload = encodePayload(codec, payload, player);
        String channel = payload.type().id().toString();
        ensureOutgoingChannelRegistered(channel);
        MusicHud.EXECUTOR.execute(() -> sendPluginMessageWhenReady(bukkitPlayer, channel, networkPayload, 0));
    }

    @Override
    public void sendToPlayers(Collection<ServerPlayer> players, S2CPayload payload) {
        for (ServerPlayer player : players) {
            sendToPlayer(player, payload);
        }
    }

    @Override
    public void close() {
        if (plugin == null) {
            return;
        }
        Messenger messenger = plugin.getServer().getMessenger();
        for (String channel : incomingChannels) {
            messenger.unregisterIncomingPluginChannel(plugin, channel);
        }
        for (String channel : outgoingChannels) {
            messenger.unregisterOutgoingPluginChannel(plugin, channel);
        }
        incomingChannels.clear();
        outgoingChannels.clear();
        c2sReceivers.clear();
        s2cCodecs.clear();
        typeMap.clear();
        plugin = null;
    }

    private void ensureIncomingChannelRegistered(String channel) {
        if (incomingChannels.add(channel)) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, createListener(channel));
        }
    }

    private void ensureOutgoingChannelRegistered(String channel) {
        if (outgoingChannels.add(channel)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        }
    }

    private PluginMessageListener createListener(String expectedChannel) {
        return (channel, player, message) -> {
            if (!expectedChannel.equals(channel)) {
                return;
            }
            RegisteredReceiver<?> registered = c2sReceivers.get(channel);
            if (registered == null) {
                return;
            }
            try {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                registered.receive(message, serverPlayer);
            } catch (Exception e) {
                logger.error("Failed to process payload on channel {}", channel, e);
            }
        };
    }

    private void sendPluginMessageWhenReady(Player player, String channel, byte[] payload, int attempt) {
        if (!player.isOnline()) {
            logger.warn("Skipping {} because player {} went offline before send", channel, player.getName());
            return;
        }
        if (player.getListeningPluginChannels().contains(channel)) {
            player.sendPluginMessage(plugin, channel, payload);
            return;
        }
        if (attempt >= MAX_PLUGIN_MESSAGE_RETRY_TICKS) {
            logger.warn("Skipping {} because client {} never registered the channel after {} tick(s)", channel, player.getName(), attempt);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPluginMessageWhenReady(player, channel, payload, attempt + 1), 1L);
    }

    private String toPayloadName(Class<?> payloadClass) {
        return payloadClass.getSimpleName()
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private <T extends IPayload> byte[] encodePayload(
            StreamCodec<? super RegistryFriendlyByteBuf, ? extends IPayload> codec,
            T payload,
            ServerPlayer player
    ) {
        return PayloadCodec.encode(
                (StreamCodec<? super RegistryFriendlyByteBuf, T>) codec,
                payload,
                player
        );
    }

    private record RegisteredReceiver<T extends IPayload>(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> receiver
    ) {
        private void receive(byte[] bytes, ServerPlayer player) {
            T payload = PayloadCodec.decode(codec, bytes, player);
            receiver.receive(payload, player);
        }
    }
}
