package indi.etern.musichud.client.network.vanilla;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.payloads.IPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StreamCodecWrapper {
    static Map<ByteBufCodec<?>, StreamCodec<?,?>> codecMap = new HashMap<>();
    public static <T extends IPayload> StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayloadWrapper<T>> of(ByteBufCodec<T> codec) {
        //noinspection unchecked
        return (StreamCodec<? super RegistryFriendlyByteBuf, CustomPacketPayloadWrapper<T>>)
                codecMap.computeIfAbsent(codec, (key) -> wrapByteBufCodec(codec));
    }

    private static <T extends IPayload> @NotNull StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayloadWrapper<T>> wrapByteBufCodec(ByteBufCodec<T> codec) {
        return new StreamCodec<>() {
            @Override
            public @NotNull CustomPacketPayloadWrapper<T> decode(@NotNull RegistryFriendlyByteBuf byteBuf) {
                return new CustomPacketPayloadWrapper<>(codec.decode(byteBuf));
            }

            @Override
            public void encode(@NotNull RegistryFriendlyByteBuf byteBuf, @NotNull CustomPacketPayloadWrapper<T> object) {
                codec.encode(byteBuf, object.payload);
            }
        };
    }
}
