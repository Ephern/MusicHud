package indi.etern.musichud.network;

import com.mojang.datafixers.util.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class Codecs {
    public static final StreamCodec<ByteBuf, ZonedDateTime> ZONED_DATE_TIME =
            new StreamCodec<>() {
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

    public static final StreamCodec<? super RegistryFriendlyByteBuf, UUID> UUID = new StreamCodec<>() {
        @Override
        @NotNull
        public UUID decode(@NotNull RegistryFriendlyByteBuf byteBuf) {
            return new UUID(byteBuf.readLong(), byteBuf.readLong());
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf byteBuf, @NotNull UUID uuid) {
            byteBuf.writeLong(uuid.getMostSignificantBits());
            byteBuf.writeLong(uuid.getLeastSignificantBits());
        }
    };

    private static final int STRING_SIZE = 32767;
    public static final StreamCodec<ByteBuf, Class<?>> CLASS =
            new StreamCodec<>() {
                @Override
                public @NotNull Class<?> decode(@NotNull ByteBuf buf) {
                    try {
                        return Class.forName(Utf8String.read(buf, STRING_SIZE));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void encode(@NotNull ByteBuf buf, Class<?> clazz) {
                    Utf8String.write(buf, clazz.getName(), STRING_SIZE);
                }
            };

    public static <B extends ByteBuf, T> StreamCodec<B, List<T>> ofList(Supplier<StreamCodec<B, T>> codecSupplier) {
        return new StreamCodec<>() {
            @Override
            @NotNull
            public List<T> decode(@NotNull B buf) {
                int length = buf.readInt();
                List<T> tList = new ArrayList<>(length);
                StreamCodec<B, T> codec = codecSupplier.get();
                for (int i = 0; i < length; i++) {
                    tList.add(codec.decode(buf));
                }
                return tList;
            }

            @Override
            public void encode(@NotNull B buf, @NotNull List<T> tList) {
                List<T> notNullList = new ArrayList<>(tList);
                notNullList.removeIf(Objects::isNull);
                buf.writeInt(notNullList.size());
                StreamCodec<B, T> codec = codecSupplier.get();
                for (T t : notNullList) {
                    codec.encode(buf, t);
                }
            }
        };
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Queue<T>> ofQueue(Supplier<StreamCodec<B, T>> codecSupplier) {
        return new StreamCodec<>() {
            @Override
            @NotNull
            public Queue<T> decode(@NotNull B buf) {
                int length = buf.readInt();
                Queue<T> tList = new ArrayDeque<>(length);
                StreamCodec<B, T> codec = codecSupplier.get();
                for (int i = 0; i < length; i++) {
                    tList.add(codec.decode(buf));
                }
                return tList;
            }

            @Override
            public void encode(@NotNull B buf, @NotNull Queue<T> tList) {
                buf.writeInt(tList.size());
                StreamCodec<B, T> codec = codecSupplier.get();
                for (T t : tList) {
                    codec.encode(buf, t);
                }
            }
        };
    }

    public static <T extends Enum<T>> StreamCodec<RegistryFriendlyByteBuf, T> ofEnum(Class<T> enumClass) {
        return new StreamCodec<>() {
            @Override
            @NotNull
            public T decode(@NotNull RegistryFriendlyByteBuf buf) {
                return buf.readEnum(enumClass);
            }

            @Override
            public void encode(@NotNull RegistryFriendlyByteBuf buf, @NotNull T enumInstance) {
                buf.writeEnum(enumInstance);
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> streamCodec, final Function<C, T1> function, final StreamCodec<? super B, T2> streamCodec2, final Function<C, T2> function2, final StreamCodec<? super B, T3> streamCodec3, final Function<C, T3> function3, final StreamCodec<? super B, T4> streamCodec4, final Function<C, T4> function4, final StreamCodec<? super B, T5> streamCodec5, final Function<C, T5> function5, final StreamCodec<? super B, T6> streamCodec6, final Function<C, T6> function6, final StreamCodec<? super B, T7> streamCodec7, final Function<C, T7> function7, final StreamCodec<? super B, T8> streamCodec8, final Function<C, T8> function8, final Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> function82) {
        return new StreamCodec<>() {
            public @NotNull C decode(@NotNull B object) {
                T1 object2 = streamCodec.decode(object);
                T2 object3 = streamCodec2.decode(object);
                T3 object4 = streamCodec3.decode(object);
                T4 object5 = streamCodec4.decode(object);
                T5 object6 = streamCodec5.decode(object);
                T6 object7 = streamCodec6.decode(object);
                T7 object8 = streamCodec7.decode(object);
                T8 object9 = streamCodec8.decode(object);
                return function82.apply(object2, object3, object4, object5, object6, object7, object8, object9);
            }

            public void encode(@NotNull B object, @NotNull C object2) {
                streamCodec.encode(object, function.apply(object2));
                streamCodec2.encode(object, function2.apply(object2));
                streamCodec3.encode(object, function3.apply(object2));
                streamCodec4.encode(object, function4.apply(object2));
                streamCodec5.encode(object, function5.apply(object2));
                streamCodec6.encode(object, function6.apply(object2));
                streamCodec7.encode(object, function7.apply(object2));
                streamCodec8.encode(object, function8.apply(object2));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> streamCodec, final Function<C, T1> function, final StreamCodec<? super B, T2> streamCodec2, final Function<C, T2> function2, final StreamCodec<? super B, T3> streamCodec3, final Function<C, T3> function3, final StreamCodec<? super B, T4> streamCodec4, final Function<C, T4> function4, final StreamCodec<? super B, T5> streamCodec5, final Function<C, T5> function5, final StreamCodec<? super B, T6> streamCodec6, final Function<C, T6> function6, final StreamCodec<? super B, T7> streamCodec7, final Function<C, T7> function7, final StreamCodec<? super B, T8> streamCodec8, final Function<C, T8> function8, final StreamCodec<? super B, T9> streamCodec9, final Function<C, T9> function9, final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> function92) {
        return new StreamCodec<>() {
            public @NotNull C decode(@NotNull B object) {
                T1 object2 = streamCodec.decode(object);
                T2 object3 = streamCodec2.decode(object);
                T3 object4 = streamCodec3.decode(object);
                T4 object5 = streamCodec4.decode(object);
                T5 object6 = streamCodec5.decode(object);
                T6 object7 = streamCodec6.decode(object);
                T7 object8 = streamCodec7.decode(object);
                T8 object9 = streamCodec8.decode(object);
                T9 object10 = streamCodec9.decode(object);
                return function92.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10);
            }

            public void encode(@NotNull B object, @NotNull C object2) {
                streamCodec.encode(object, function.apply(object2));
                streamCodec2.encode(object, function2.apply(object2));
                streamCodec3.encode(object, function3.apply(object2));
                streamCodec4.encode(object, function4.apply(object2));
                streamCodec5.encode(object, function5.apply(object2));
                streamCodec6.encode(object, function6.apply(object2));
                streamCodec7.encode(object, function7.apply(object2));
                streamCodec8.encode(object, function8.apply(object2));
                streamCodec9.encode(object, function9.apply(object2));
            }
        };
    }
}