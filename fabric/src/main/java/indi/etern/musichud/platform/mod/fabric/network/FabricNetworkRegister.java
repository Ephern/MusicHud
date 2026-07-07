package indi.etern.musichud.platform.mod.fabric.network;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.C2SPayload;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.network.vanillaUtils.StreamCodecWrapper;
import indi.etern.musichud.network.vanillaUtils.VanillaPlayerProxy;
import indi.etern.musichud.platform.Environment;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@SuppressWarnings("unused")
public class FabricNetworkRegister implements INetworkRegister {
    private static volatile FabricNetworkRegister instance;

    public static FabricNetworkRegister getInstance() {
        if (instance == null) {
            synchronized (FabricNetworkRegister.class) {
                if (instance == null) {
                    instance = new FabricNetworkRegister();
                }
            }
        }
        return instance;
    }

    @Override
    public <T extends IPayload> void registerC2SPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> serverReceiver
    ) {
        CustomPacketPayload.Type<T> type = getMetaDataOrNew(clazz, serverReceiver).type();
        PayloadTypeRegistry.playC2S().register(type, StreamCodecWrapper.of(codec));

        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();

        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            serverReceiver.receive(payload, VanillaPlayerProxy.ofPlayer(context.player()));
        });
    }

    @Override
    public <T extends IPayload> void registerS2CPayload(
            Class<T> clazz,
            ByteBufCodec<T> codec,
            NetworkReceiver<T> clientReceiver
    ) {
        CustomPacketPayload.Type<T> type = getMetaDataOrNew(clazz, clientReceiver).type();
        PayloadTypeRegistry.playS2C().register(type, StreamCodecWrapper.of(codec));

        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        if (side == Environment.Side.CLIENT) {
            FabricClientNetworkRegisterUtil.register(type, clientReceiver);
        }
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
}