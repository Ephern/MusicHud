package indi.etern.musichud.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
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
}
