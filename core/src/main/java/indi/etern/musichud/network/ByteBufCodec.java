package indi.etern.musichud.network;

import indi.etern.musichud.network.suppliers.*;
import io.netty.buffer.ByteBuf;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface ByteBufCodec<V> {
    static <V> ByteBufCodec<V> unit(final V object) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                return object;
            }

            public void encode(ByteBuf byteBuf, V object2) {
                if (!object2.equals(object)) {
                    throw new IllegalStateException("Can't encode '" + object2 + "', expected '" + object + "'");
                }
            }
        };
    }

    static <V, T1> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec, final Function<V, T1> supplier1,
            final Function<T1, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec.decode(object);
                return factory.apply(object2);
            }

            public void encode(ByteBuf byteBuf, V object2) {
                byteBufCodec.encode(byteBuf, supplier1.apply(object2));
            }
        };
    }

    static <V, T1, T2> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final BiFunction<T1, T2, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                return factory.apply(object2, object3);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final Function3<T1, T2, T3, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                return factory.apply(object2, object3, object4);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final Function4<T1, T2, T3, T4, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 object2 = byteBufCodec1.decode(byteBuf);
                T2 object3 = byteBufCodec2.decode(byteBuf);
                T3 object4 = byteBufCodec3.decode(byteBuf);
                T4 object5 = byteBufCodec4.decode(byteBuf);
                return factory.apply(object2, object3, object4, object5);
            }

            public void encode(ByteBuf byteBuf, V object2) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(object2));
                byteBufCodec2.encode(byteBuf, supplier2.apply(object2));
                byteBufCodec3.encode(byteBuf, supplier3.apply(object2));
                byteBufCodec4.encode(byteBuf, supplier4.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final Function5<T1, T2, T3, T4, T5, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                return factory.apply(object2, object3, object4, object5, object6);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6> ByteBufCodec<V> composite(

            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final ByteBufCodec<T6> byteBufCodec6, final Function<V, T6> supplier6,
            final Function6<T1, T2, T3, T4, T5, T6, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                T6 object7 = byteBufCodec6.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
                byteBufCodec6.encode(object, supplier6.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6, T7> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final ByteBufCodec<T6> byteBufCodec6, final Function<V, T6> supplier6,
            final ByteBufCodec<T7> byteBufCodec7, final Function<V, T7> supplier7,
            final Function7<T1, T2, T3, T4, T5, T6, T7, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                T6 object7 = byteBufCodec6.decode(object);
                T7 object8 = byteBufCodec7.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7, object8);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
                byteBufCodec6.encode(object, supplier6.apply(object2));
                byteBufCodec7.encode(object, supplier7.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6, T7, T8> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final ByteBufCodec<T6> byteBufCodec6, final Function<V, T6> supplier6,
            final ByteBufCodec<T7> byteBufCodec7, final Function<V, T7> supplier7,
            final ByteBufCodec<T8> byteBufCodec8, final Function<V, T8> supplier8,
            final Function8<T1, T2, T3, T4, T5, T6, T7, T8, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                T6 object7 = byteBufCodec6.decode(object);
                T7 object8 = byteBufCodec7.decode(object);
                T8 object9 = byteBufCodec8.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7, object8, object9);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
                byteBufCodec6.encode(object, supplier6.apply(object2));
                byteBufCodec7.encode(object, supplier7.apply(object2));
                byteBufCodec8.encode(object, supplier8.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6, T7, T8, T9> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final ByteBufCodec<T6> byteBufCodec6, final Function<V, T6> supplier6,
            final ByteBufCodec<T7> byteBufCodec7, final Function<V, T7> supplier7,
            final ByteBufCodec<T8> byteBufCodec8, final Function<V, T8> supplier8,
            final ByteBufCodec<T9> byteBufCodec9, final Function<V, T9> supplier9,
            final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                T6 object7 = byteBufCodec6.decode(object);
                T7 object8 = byteBufCodec7.decode(object);
                T8 object9 = byteBufCodec8.decode(object);
                T9 object10 = byteBufCodec9.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
                byteBufCodec6.encode(object, supplier6.apply(object2));
                byteBufCodec7.encode(object, supplier7.apply(object2));
                byteBufCodec8.encode(object, supplier8.apply(object2));
                byteBufCodec9.encode(object, supplier9.apply(object2));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final ByteBufCodec<T4> byteBufCodec4, final Function<V, T4> supplier4,
            final ByteBufCodec<T5> byteBufCodec5, final Function<V, T5> supplier5,
            final ByteBufCodec<T6> byteBufCodec6, final Function<V, T6> supplier6,
            final ByteBufCodec<T7> byteBufCodec7, final Function<V, T7> supplier7,
            final ByteBufCodec<T8> byteBufCodec8, final Function<V, T8> supplier8,
            final ByteBufCodec<T9> byteBufCodec9, final Function<V, T9> supplier9,
            final ByteBufCodec<T10> byteBufCodec10, final Function<V, T10> supplier10,
            final ByteBufCodec<T11> byteBufCodec11, final Function<V, T11> supplier11,
            final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf object) {
                T1 object2 = byteBufCodec1.decode(object);
                T2 object3 = byteBufCodec2.decode(object);
                T3 object4 = byteBufCodec3.decode(object);
                T4 object5 = byteBufCodec4.decode(object);
                T5 object6 = byteBufCodec5.decode(object);
                T6 object7 = byteBufCodec6.decode(object);
                T7 object8 = byteBufCodec7.decode(object);
                T8 object9 = byteBufCodec8.decode(object);
                T9 object10 = byteBufCodec9.decode(object);
                T10 object11 = byteBufCodec10.decode(object);
                T11 object12 = byteBufCodec11.decode(object);
                return factory.apply(object2, object3, object4, object5, object6, object7, object8, object9, object10, object11, object12);
            }

            public void encode(ByteBuf object, V object2) {
                byteBufCodec1.encode(object, supplier1.apply(object2));
                byteBufCodec2.encode(object, supplier2.apply(object2));
                byteBufCodec3.encode(object, supplier3.apply(object2));
                byteBufCodec4.encode(object, supplier4.apply(object2));
                byteBufCodec5.encode(object, supplier5.apply(object2));
                byteBufCodec6.encode(object, supplier6.apply(object2));
                byteBufCodec7.encode(object, supplier7.apply(object2));
                byteBufCodec8.encode(object, supplier8.apply(object2));
                byteBufCodec9.encode(object, supplier9.apply(object2));
                byteBufCodec10.encode(object, supplier10.apply(object2));
                byteBufCodec11.encode(object, supplier11.apply(object2));
            }
        };
    }

    void encode(ByteBuf byteBuf, V value);

    V decode(ByteBuf byteBuf);
}
