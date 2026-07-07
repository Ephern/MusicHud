package indi.etern.musichud.network;

import indi.etern.musichud.network.vanillaUtils.VanillaUtf8String;
import indi.etern.musichud.network.vanillaUtils.VanillaVarInt;
import indi.etern.musichud.network.vanillaUtils.VanillaVarLong;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;

public class Codecs {
    public static final ByteBufCodec<Boolean> BOOL = new ByteBufCodec<>() {
        public Boolean decode(ByteBuf byteBuf) {
            return byteBuf.readBoolean();
        }

        public void encode(ByteBuf byteBuf, Boolean boolean_) {
            byteBuf.writeBoolean(boolean_);
        }
    };
    public static final ByteBufCodec<Byte> BYTE = new ByteBufCodec<>() {
        public Byte decode(ByteBuf byteBuf) {
            return byteBuf.readByte();
        }

        public void encode(ByteBuf byteBuf, Byte byte_) {
            byteBuf.writeByte(byte_);
        }
    };
    public static final ByteBufCodec<Short> SHORT = new ByteBufCodec<>() {
        public Short decode(ByteBuf byteBuf) {
            return byteBuf.readShort();
        }

        public void encode(ByteBuf byteBuf, Short short_) {
            byteBuf.writeShort(short_);
        }
    };
    public static final ByteBufCodec<Integer> UNSIGNED_SHORT = new ByteBufCodec<>() {
        public Integer decode(ByteBuf byteBuf) {
            return byteBuf.readUnsignedShort();
        }

        public void encode(ByteBuf byteBuf, Integer integer) {
            byteBuf.writeShort(integer);
        }
    };
    public static final ByteBufCodec<Integer> INT = new ByteBufCodec<>() {
        public Integer decode(ByteBuf byteBuf) {
            return byteBuf.readInt();
        }

        public void encode(ByteBuf byteBuf, Integer integer) {
            byteBuf.writeInt(integer);
        }
    };
    public static final ByteBufCodec<Integer> VAR_INT = new ByteBufCodec<>() {
        public Integer decode(ByteBuf byteBuf) {
            return VanillaVarInt.read(byteBuf);
        }

        public void encode(ByteBuf byteBuf, Integer integer) {
            VanillaVarInt.write(byteBuf, integer);
        }
    };
    public static final ByteBufCodec<Long> LONG = new ByteBufCodec<>() {
        public @NotNull Long decode(ByteBuf byteBuf) {
            return byteBuf.readLong();
        }

        public void encode(ByteBuf byteBuf, Long long_) {
            byteBuf.writeLong(long_);
        }
    };
    public static final ByteBufCodec<Long> VAR_LONG = new ByteBufCodec<Long>() {
        @Override
        public void encode(ByteBuf byteBuf, Long value) {
            VanillaVarLong.write(byteBuf, value);
        }

        @Override
        public Long decode(ByteBuf byteBuf) {
            return VanillaVarLong.read(byteBuf);
        }
    };
    public static final ByteBufCodec<Long[]> LONG_ARRAY = new ByteBufCodec<>() {
        @Override
        public void encode(ByteBuf byteBuf, Long[] value) {
            VanillaVarInt.write(byteBuf, value.length);
            for (long l : value) {
                byteBuf.writeLong(l);
            }
        }

        @Override
        public Long[] decode(ByteBuf byteBuf) {
            int i = VanillaVarInt.read(byteBuf);
            int j = byteBuf.readableBytes() / 8;
            if (i > j) {
                throw new DecoderException("LongArray with size " + i + " is bigger than allowed " + j);
            } else {
                Long[] ls = new Long[i];
                for (int i1 = 0; i1 < ls.length; ++i1) {
                    ls[i1] = byteBuf.readLong();
                }
                return ls;
            }
        }
    };
    public static final ByteBufCodec<Float> FLOAT = new ByteBufCodec<>() {
        public Float decode(ByteBuf byteBuf) {
            return byteBuf.readFloat();
        }

        public void encode(ByteBuf byteBuf, Float float_) {
            byteBuf.writeFloat(float_);
        }
    };
    public static final ByteBufCodec<Double> DOUBLE = new ByteBufCodec<>() {
        public Double decode(ByteBuf byteBuf) {
            return byteBuf.readDouble();
        }

        public void encode(ByteBuf byteBuf, Double double_) {
            byteBuf.writeDouble(double_);
        }
    };
    public static final ByteBufCodec<String> STRING_UTF8 = new ByteBufCodec<>() {
        public String decode(ByteBuf byteBuf) {
            return VanillaUtf8String.read(byteBuf, 32767);
        }

        public void encode(ByteBuf byteBuf, String string) {
            VanillaUtf8String.write(byteBuf, string, 32767);
        }
    };

    public static final ByteBufCodec<ZonedDateTime> ZONED_DATE_TIME =
            new ByteBufCodec<>() {
                @Override
                @NotNull
                public ZonedDateTime decode(ByteBuf byteBuf) {
                    int year = byteBuf.readInt();
                    int month = byteBuf.readInt();
                    int dayOfMonth = byteBuf.readInt();
                    int hour = byteBuf.readInt();
                    int minute = byteBuf.readInt();
                    int second = byteBuf.readInt();
                    int zoneIdLength = byteBuf.readInt();
                    return ZonedDateTime.of(
                            year,
                            month,
                            dayOfMonth,
                            hour,
                            minute,
                            second,
                            0,
                            ZoneId.of(byteBuf.readCharSequence(zoneIdLength, StandardCharsets.UTF_8).toString())
                    );
                }

                @Override
                public void encode(ByteBuf byteBuf, ZonedDateTime zonedDateTime) {
                    byteBuf.writeInt(zonedDateTime.getYear());
                    byteBuf.writeInt(zonedDateTime.getMonthValue());
                    byteBuf.writeInt(zonedDateTime.getDayOfMonth());
                    byteBuf.writeInt(zonedDateTime.getHour());
                    byteBuf.writeInt(zonedDateTime.getMinute());
                    byteBuf.writeInt(zonedDateTime.getSecond());
                    String zoneId = zonedDateTime.getZone().getId();
                    byteBuf.writeInt(zoneId.length());
                    byteBuf.writeCharSequence(zoneId, StandardCharsets.UTF_8);
                }
            };

    public static final ByteBufCodec<UUID> UUID = new ByteBufCodec<>() {
        @Override
        @NotNull
        public UUID decode(@NotNull ByteBuf byteBuf) {
            return new UUID(byteBuf.readLong(), byteBuf.readLong());
        }

        @Override
        public void encode(@NotNull ByteBuf byteBuf, @NotNull UUID uuid) {
            byteBuf.writeLong(uuid.getMostSignificantBits());
            byteBuf.writeLong(uuid.getLeastSignificantBits());
        }
    };

    private static final int STRING_SIZE = 32767;
    public static final ByteBufCodec<Class<?>> CLASS =
            new ByteBufCodec<>() {
                @Override
                public @NotNull Class<?> decode(@NotNull ByteBuf buf) {
                    try {
                        return Class.forName(VanillaUtf8String.read(buf, STRING_SIZE));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void encode(@NotNull ByteBuf buf, Class<?> clazz) {
                    VanillaUtf8String.write(buf, clazz.getName(), STRING_SIZE);
                }
            };

    public static <T> ByteBufCodec<List<T>> ofList(Supplier<ByteBufCodec<T>> codecSupplier) {
        return new ByteBufCodec<>() {
            @Override
            @NotNull
            public List<T> decode(@NotNull ByteBuf buf) {
                int length = buf.readInt();
                List<T> tList = new ArrayList<>(length);
                ByteBufCodec<T> codec = codecSupplier.get();
                for (int i = 0; i < length; i++) {
                    tList.add(codec.decode(buf));
                }
                return tList;
            }

            @Override
            public void encode(@NotNull ByteBuf buf, @NotNull List<T> tList) {
                List<T> notNullList = new ArrayList<>(tList);
                notNullList.removeIf(Objects::isNull);
                buf.writeInt(notNullList.size());
                ByteBufCodec<T> codec = codecSupplier.get();
                for (T t : notNullList) {
                    codec.encode(buf, t);
                }
            }
        };
    }

    public static <T> ByteBufCodec<Queue<T>> ofQueue(Supplier<ByteBufCodec<T>> codecSupplier) {
        return new ByteBufCodec<>() {
            @Override
            @NotNull
            public Queue<T> decode(@NotNull ByteBuf buf) {
                int length = buf.readInt();
                Queue<T> tList = new ArrayDeque<>(length);
                ByteBufCodec<T> codec = codecSupplier.get();
                for (int i = 0; i < length; i++) {
                    tList.add(codec.decode(buf));
                }
                return tList;
            }

            @Override
            public void encode(@NotNull ByteBuf buf, @NotNull Queue<T> tList) {
                buf.writeInt(tList.size());
                ByteBufCodec<T> codec = codecSupplier.get();
                for (T t : tList) {
                    codec.encode(buf, t);
                }
            }
        };
    }

    public static <T extends Enum<T>> ByteBufCodec<T> ofEnum(Class<T> enumClass) {
        return new ByteBufCodec<>() {
            @Override
            @NotNull
            public T decode(@NotNull ByteBuf buf) {
                //noinspection unchecked,rawtypes
                return (T)((Enum[])enumClass.getEnumConstants())[VanillaVarInt.read(buf)];
            }

            @Override
            public void encode(@NotNull ByteBuf buf, @NotNull T enumInstance) {
                VanillaVarInt.write(buf, enumInstance.ordinal());
            }
        };
    }
}
