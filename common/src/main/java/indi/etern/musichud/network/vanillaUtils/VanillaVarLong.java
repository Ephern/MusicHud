package indi.etern.musichud.network.vanillaUtils;

import io.netty.buffer.ByteBuf;

public class VanillaVarLong {
    public static boolean hasContinuationBit(byte b) {
        return (b & 128) == 128;
    }

    public static long read(ByteBuf byteBuf) {
        long l = 0L;
        int i = 0;

        byte b;
        do {
            b = byteBuf.readByte();
            l |= (long) (b & 127) << i++ * 7;
            if (i > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while (hasContinuationBit(b));

        return l;
    }

    public static ByteBuf write(ByteBuf byteBuf, long l) {
        while ((l & -128L) != 0L) {
            byteBuf.writeByte((int) (l & 127L) | 128);
            l >>>= 7;
        }

        byteBuf.writeByte((int) l);
        return byteBuf;
    }
}
