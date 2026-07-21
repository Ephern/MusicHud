package indi.etern.musichud.platform.mod.fabric.network;

import indi.etern.musichud.client.network.vanilla.CustomPacketPayloadWrapper;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.IPayload;
import indi.etern.musichud.client.network.vanilla.VanillaPlayerProxy;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class FabricClientNetworkRegisterUtil {
    public static <T extends IPayload> void register(CustomPacketPayload.Type<CustomPacketPayloadWrapper<T>> type, NetworkReceiver<T> clientReceiver) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            clientReceiver.receive(payload.getPayload(), VanillaPlayerProxy.ofPlayer(context.player()));
        });
    }
}
