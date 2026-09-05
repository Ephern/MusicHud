package indi.etern.musichud.platform.mod.neoforge.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.Version;
import indi.etern.musichud.client.network.vanilla.CustomPacketPayloadWrapper;
import indi.etern.musichud.client.network.vanilla.StreamCodecWrapper;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import indi.etern.musichud.client.network.vanilla.VanillaServerNetworkService;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static indi.etern.musichud.client.network.vanilla.IVanillaNetworkRegister.getMetaDataOrNew;

@SuppressWarnings("unused")
public class NeoForgeNetworkManager implements INetworkRegister, VanillaServerNetworkService {
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
    private <T extends IPayload> void registerPayloadInternal(RegistrationInfo<T> info) {
        if (C2SPayload.class.isAssignableFrom(info.clazz)) { // C2S
            registrar.playToServer(info.type(), StreamCodecWrapper.of(info.codec()), (payload, context) -> {
                if (payload.getPayload() instanceof IPayload payload1) {
                    NetworkReceiver receiver = info.serverReceiver();
                    receiver.receive(payload1, VanillaPlayerProxy.ofPlayer(context.player()));
                } else {
                    MusicHud.LOGGER.error("Client payload not implements IPayload");
                }
            });
        } else if (S2CPayload.class.isAssignableFrom(info.clazz)) { // S2C
            if (side == Environment.Side.CLIENT) {
                registrar.playToClient(info.type(), StreamCodecWrapper.of(info.codec()), (payload, context) -> {
                    if (payload.getPayload() instanceof IPayload payload1) {
                        NetworkReceiver receiver = info.clientReceiver();
                        receiver.receive(payload1, VanillaPlayerProxy.ofPlayer(context.player()));
                    } else {
                        MusicHud.LOGGER.error("Server payload not implements IPayload");
                    }
                });
            } else {
                registrar.playToClient(info.type(), StreamCodecWrapper.of(info.codec()), (payload, context) -> {
                });
            }
        }
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        CustomPacketPayload.Type<CustomPacketPayloadWrapper<T>> type = getMetaDataOrNew(clazz, serverReceiver).type();
        RegistrationInfo<T> registrationInfo = new RegistrationInfo<>(clazz, type, codec, null, serverReceiver);
        pendingRegistrations.add(registrationInfo);
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        CustomPacketPayload.Type<CustomPacketPayloadWrapper<T>> type = getMetaDataOrNew(clazz, clientReceiver).type();
        RegistrationInfo<T> registrationInfo = new RegistrationInfo<>(clazz, type, codec, clientReceiver, null);
        pendingRegistrations.add(registrationInfo);
    }

    @Override
    public <T extends IPayload> void autoRegisterPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
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
        registrar = event.registrar(MusicHud.MOD_ID).versioned(Version.leastCompatible.toString()).optional();
        for (RegistrationInfo<?> info : pendingRegistrations) {
            registerPayloadInternal(info);
        }
        pendingRegistrations.clear(); // 清空，避免重复注册
    }

    @Override
    public void sendToNetworkPlayer(IPlayerClient playerClient, S2CPayload payload) {
        if (playerClient instanceof VanillaPlayerProxy player && player.getPlayer() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new CustomPacketPayloadWrapper<>(payload));
        }
    }

    private record RegistrationInfo<T extends IPayload>(
            Class<T> clazz,
            CustomPacketPayload.Type<CustomPacketPayloadWrapper<T>> type,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver,
            NetworkReceiver<T> serverReceiver
    ) {
    }
}