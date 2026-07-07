package indi.etern.musichud.network.vanillaUtils;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.payloads.IPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StreamCodecWrapper {
    static Map<ByteBufCodec<?>, StreamCodec<?,?>> codecMap = new HashMap<>();
    public static <T extends IPayload> StreamCodec<? super RegistryFriendlyByteBuf, T> of(ByteBufCodec<T> codec) {
        //noinspection unchecked
        return (StreamCodec<? super RegistryFriendlyByteBuf, T>)
                codecMap.computeIfAbsent(codec, (key) -> wrapByteBufCodec(codec));
    }

    private static <T extends IPayload> @NotNull StreamCodec<RegistryFriendlyByteBuf, T> wrapByteBufCodec(ByteBufCodec<T> codec) {
        return new StreamCodec<>() {
            @Override
            public @NotNull T decode(RegistryFriendlyByteBuf byteBuf) {
                return codec.decode(byteBuf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf byteBuf, T object) {
                codec.encode(byteBuf, object);
            }
        };
    }
}
