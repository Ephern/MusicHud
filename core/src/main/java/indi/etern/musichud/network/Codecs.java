package indi.etern.musichud.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class Codecs {
    public static final ByteBufCodec<Void> VOID = new ByteBufCodec<>() {
        @Override
        public void encode(ByteBuf byteBuf, Void value) {
        }

        @Override
        public Void decode(ByteBuf byteBuf) {
            return null;
        }
    };
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
        public @NonNull Long decode(ByteBuf byteBuf) {
            return byteBuf.readLong();
        }

        public void encode(ByteBuf byteBuf, Long long_) {
            byteBuf.writeLong(long_);
        }
    };
    public static final ByteBufCodec<Long> VAR_LONG = new ByteBufCodec<>() {
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
                @NonNull
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
        @NonNull
        public UUID decode(@NonNull ByteBuf byteBuf) {
            return new UUID(byteBuf.readLong(), byteBuf.readLong());
        }

        @Override
        public void encode(@NonNull ByteBuf byteBuf, @NonNull UUID uuid) {
            byteBuf.writeLong(uuid.getMostSignificantBits());
            byteBuf.writeLong(uuid.getLeastSignificantBits());
        }
    };
    public static final ByteBufCodec<Class<?>> CLASS =
            new ByteBufCodec<>() {
                @Override
                public @NonNull Class<?> decode(@NonNull ByteBuf buf) {
                    try {
                        return Class.forName(VanillaUtf8String.read(buf, STRING_SIZE));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void encode(@NonNull ByteBuf buf, Class<?> clazz) {
                    VanillaUtf8String.write(buf, clazz.getName(), STRING_SIZE);
                }
            };
    private static final int STRING_SIZE = 32767;


    public static  <T> ByteBufCodec<List<T>> ofList(Supplier<ByteBufCodec<T>> codecSupplier) {
        return ofCollection(ArrayList::new, codecSupplier);
    }

    public static  <T> ByteBufCodec<Set<T>> ofSet(Supplier<ByteBufCodec<T>> codecSupplier) {
        return ofCollection(LinkedHashSet::new, codecSupplier);
    }

    public static <T> ByteBufCodec<Queue<T>> ofQueue(Supplier<ByteBufCodec<T>> codecSupplier) {
        return ofCollection(ArrayDeque::new, codecSupplier);
    }

    public static <T, S extends Collection<T>> ByteBufCodec<S>
            ofCollection(Function<Integer, S> collectionSupplier, Supplier<ByteBufCodec<T>> codecSupplier) {
        return new ByteBufCodec<>() {
            @Override
            @NonNull
            public S decode(@NonNull ByteBuf buf) {
                int length = buf.readInt();
                S ts = collectionSupplier.apply(length);
                ByteBufCodec<T> codec = codecSupplier.get();
                for (int i = 0; i < length; i++) {
                    ts.add(codec.decode(buf));
                }
                return ts;
            }

            @Override
            public void encode(@NonNull ByteBuf buf, @NonNull S s) {
                List<T> nonNull = s.stream().filter(Objects::nonNull).toList();
                buf.writeInt(nonNull.size());
                ByteBufCodec<T> codec = codecSupplier.get();
                for (T t : nonNull) {
                    codec.encode(buf, t);
                }
            }
        };
    }

    /** Encodes a one-byte presence flag before the payload to support null values. */
    public static <T> ByteBufCodec<T> ofNullable(ByteBufCodec<T> inner) {
        return new ByteBufCodec<>() {
            @Override
            public T decode(ByteBuf byteBuf) {
                return BOOL.decode(byteBuf) ? inner.decode(byteBuf) : null;
            }

            @Override
            public void encode(ByteBuf byteBuf, T value) {
                BOOL.encode(byteBuf, value != null);
                if (value != null) {
                    inner.encode(byteBuf, value);
                }
            }
        };
    }

    public static <T extends Enum<T>> ByteBufCodec<T> ofEnum(Class<T> enumClass) {
        return new ByteBufCodec<>() {
            @Override
            @NonNull
            public T decode(@NonNull ByteBuf buf) {
                //noinspection unchecked,rawtypes
                return (T) ((Enum[]) enumClass.getEnumConstants())[VanillaVarInt.read(buf)];
            }

            @Override
            public void encode(@NonNull ByteBuf buf, @NonNull T enumInstance) {
                VanillaVarInt.write(buf, enumInstance.ordinal());
            }
        };
    }
}
