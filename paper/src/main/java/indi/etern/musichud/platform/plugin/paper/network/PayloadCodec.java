package indi.etern.musichud.platform.plugin.paper.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

final class PayloadCodec {
    private PayloadCodec() {
    }

    static <T> byte[] encode(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            T payload,
            Player player
    ) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, player.registryAccess());
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, T> castCodec = (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
            castCodec.encode(friendlyByteBuf, payload);
            byte[] bytes = new byte[friendlyByteBuf.readableBytes()];
            friendlyByteBuf.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    static <T> T decode(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            byte[] bytes,
            Player player
    ) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, player.registryAccess());
            return codec.decode(friendlyByteBuf);
        } finally {
            buffer.release();
        }
    }
}