package indi.etern.musichud.platform.mod.neoforge.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class NeoForgeNetworkManager implements INetworkRegister, IServerNetworkService, IClientNetworkService {
    private static volatile NeoForgeNetworkManager instance;
    private final Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
    private final Map<Class<? extends IPayload>, CustomPacketPayload.Type<?>> typeMap = new ConcurrentHashMap<>();
    private final List<RegistrationInfo<?>> pendingRegistrations = new ArrayList<>();
    private PayloadRegistrar registrar;

    public static NeoForgeNetworkManager getInstance() {
        if (instance == null) {
            synchronized (NeoForgeNetworkManager.class) {
                if (instance == null) {
                    instance = new NeoForgeNetworkManager();
                }
            }
        }
        return instance;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerPayloadInternal(RegistrationInfo<?> info) {
        if (C2SPayload.class.isAssignableFrom(info.clazz)) { // C2S
            registrar.playToServer(info.type(), (StreamCodec) info.codec(), (payload, context) -> {
                NetworkReceiver receiver = info.serverReceiver();
                receiver.receive(payload, context.player());
            });
        } else if (S2CPayload.class.isAssignableFrom(info.clazz)) { // S2C
            if (side == Environment.Side.CLIENT) {
                registrar.playToClient(info.type(), (StreamCodec) info.codec(), (payload, context) -> {
                    NetworkReceiver receiver = info.clientReceiver();
                    receiver.receive(payload, context.player());
                });
            } else {
                registrar.playToClient(info.type(), (StreamCodec) info.codec(), (payload, context) -> {
                });
            }
        }
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        CustomPacketPayload.Type<T> type = getMetaDataOrNew(clazz, serverReceiver).type();
        RegistrationInfo<T> registrationInfo = new RegistrationInfo<>(clazz, type, codec, null, serverReceiver);
        pendingRegistrations.add(registrationInfo);
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        CustomPacketPayload.Type<T> type = getMetaDataOrNew(clazz, clientReceiver).type();
        RegistrationInfo<T> registrationInfo = new RegistrationInfo<>(clazz, type, codec, clientReceiver, null);
        pendingRegistrations.add(registrationInfo);
    }

    @Override
    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientOrServerReceiver
    ) {
        if (S2CPayload.class.isAssignableFrom(clazz)) {
            registerS2CPayload(clazz, codec, clientOrServerReceiver);
        } else if (C2SPayload.class.isAssignableFrom(clazz)) {
            registerC2SPayload(clazz, codec, clientOrServerReceiver);
        } else {
            throw new IllegalArgumentException("Payload class must implements S2CPayload or C2SPayload");
        }
    }

    public void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        registrar = event.registrar(MusicHud.MOD_ID).versioned(Version.leastCapable.toString()).optional();
        for (RegistrationInfo<?> info : pendingRegistrations) {
            registerPayloadInternal(info);
        }
        pendingRegistrations.clear(); // 清空，避免重复注册
    }

    @Override
    public void sendToNetworkServer(C2SPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToNetworkPlayer(ServerPlayer serverPlayer, S2CPayload payload) {
        PacketDistributor.sendToPlayer(serverPlayer, payload);
    }

    private record RegistrationInfo<T extends IPayload>(
            Class<T> clazz,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            NetworkReceiver<T> clientReceiver,
            NetworkReceiver<T> serverReceiver
    ) {
    }
}