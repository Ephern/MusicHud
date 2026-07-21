package indi.etern.musichud.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.AliasEnum;
import indi.etern.musichud.interfaces.IntegerCodeEnum;
import lombok.SneakyThrows;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class JsonUtil {
    public static final Gson gson;
    static {
        gson = new GsonBuilder()
                .registerTypeAdapterFactory(new LenientEnumTypeAdapterFactory())
                .registerTypeAdapter(Class.class, new ClassAdapter())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .create();
    }

    public static class LenientEnumTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> rawType = type.getRawType();
            if (!Enum.class.isAssignableFrom(rawType) || rawType == Enum.class) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Class<Enum<?>> enumClass = (Class<Enum<?>>) rawType;
            Enum<?>[] enumConstants = enumClass.getEnumConstants();

            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    Enum<?> enumValue = (Enum<?>) value;
                    // 如果是 IntegerCodeEnum，输出数字代码
                    if (value instanceof IntegerCodeEnum codeEnum) {
                        out.value(codeEnum.getCode());
                    } else if (value instanceof AliasEnum aliasEnum) {
                        out.value(aliasEnum.getAlias());
                    } else {
                        out.value(enumValue.name());
                    }
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    switch (in.peek()) {
                        case NULL:
                            in.nextNull();
                            return null;
                        case STRING:
                            String input = in.nextString();
                            // 忽略大小写匹配 name
                            for (Enum<?> constant : enumConstants) {
                                if (constant instanceof AliasEnum aliasEnum && aliasEnum.getAlias().equals(input)) {
                                    //noinspection unchecked
                                    return (T) aliasEnum;
                                }
                                if (constant.name().equalsIgnoreCase(input)) {
                                    @SuppressWarnings("unchecked")
                                    T result = (T) constant;
                                    return result;
                                }
                            }
                            throw new JsonSyntaxException("Invalid enum value: " + input);
                        case NUMBER:
                            int code = in.nextInt();
                            // 优先匹配 IntegerCodeEnum 的 code
                            for (Enum<?> constant : enumConstants) {
                                if (constant instanceof IntegerCodeEnum) {
                                    if (((IntegerCodeEnum) constant).getCode() == code) {
                                        @SuppressWarnings("unchecked")
                                        T result = (T) constant;
                                        return result;
                                    }
                                } else if (constant.ordinal() == code) {
                                    @SuppressWarnings("unchecked")
                                    T result = (T) constant;
                                    return result;
                                }
                            }
                            MusicHud.getLogger(JsonUtil.class).debug("Invalid enum value: {}, use default (ordinal:0): {}", code, enumConstants[0]);
                            //noinspection unchecked
                            return (T) enumConstants[0];
                        default:
                            throw new JsonSyntaxException("Expected STRING, NUMBER, or NULL, but got: " + in.peek());
                    }
                }
            };
        }
    }

    public static class ZonedDateTimeAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public JsonElement serialize(ZonedDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(FORMATTER.format(src));
        }

        @Override
        public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return ZonedDateTime.parse(json.getAsString(), FORMATTER);
        }
    }

    public static class ClassAdapter implements JsonSerializer<Class<?>>, JsonDeserializer<Class<?>> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public JsonElement serialize(Class<?> src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getName());
        }

        @SneakyThrows
        @Override
        public Class<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return Class.forName(json.getAsString());
        }
    }
}
