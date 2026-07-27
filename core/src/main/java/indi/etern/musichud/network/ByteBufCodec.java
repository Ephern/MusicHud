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

            public void encode(ByteBuf byteBuf, V v) {
                if (!v.equals(object)) {
                    throw new IllegalStateException("Can't encode '" + v + "', expected '" + object + "'");
                }
            }
        };
    }

    static <V, T1> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec, final Function<V, T1> supplier1,
            final Function<T1, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec.decode(byteBuf);
                return factory.apply(t1);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec.encode(byteBuf, supplier1.apply(v));
            }
        };
    }

    static <V, T1, T2> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final BiFunction<T1, T2, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                return factory.apply(t1, t2);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
            }
        };
    }

    static <V, T1, T2, T3> ByteBufCodec<V> composite(
            final ByteBufCodec<T1> byteBufCodec1, final Function<V, T1> supplier1,
            final ByteBufCodec<T2> byteBufCodec2, final Function<V, T2> supplier2,
            final ByteBufCodec<T3> byteBufCodec3, final Function<V, T3> supplier3,
            final Function3<T1, T2, T3, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                return factory.apply(t1, t2, t3);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
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
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
                byteBufCodec7.encode(byteBuf, supplier7.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                T8 t8 = byteBufCodec8.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
                byteBufCodec7.encode(byteBuf, supplier7.apply(v));
                byteBufCodec8.encode(byteBuf, supplier8.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                T8 t8 = byteBufCodec8.decode(byteBuf);
                T9 t9 = byteBufCodec9.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9);
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

    static <V, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> ByteBufCodec<V> composite(
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
            final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                T8 t8 = byteBufCodec8.decode(byteBuf);
                T9 t9 = byteBufCodec9.decode(byteBuf);
                T10 t10 = byteBufCodec10.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
                byteBufCodec7.encode(byteBuf, supplier7.apply(v));
                byteBufCodec8.encode(byteBuf, supplier8.apply(v));
                byteBufCodec9.encode(byteBuf, supplier9.apply(v));
                byteBufCodec10.encode(byteBuf, supplier10.apply(v));
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
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                T8 t8 = byteBufCodec8.decode(byteBuf);
                T9 t9 = byteBufCodec9.decode(byteBuf);
                T10 t10 = byteBufCodec10.decode(byteBuf);
                T11 t11 = byteBufCodec11.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
                byteBufCodec7.encode(byteBuf, supplier7.apply(v));
                byteBufCodec8.encode(byteBuf, supplier8.apply(v));
                byteBufCodec9.encode(byteBuf, supplier9.apply(v));
                byteBufCodec10.encode(byteBuf, supplier10.apply(v));
                byteBufCodec11.encode(byteBuf, supplier11.apply(v));
            }
        };
    }

    static <V, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> ByteBufCodec<V> composite(
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
            final ByteBufCodec<T12> byteBufCodec12, final Function<V, T12> supplier12,
            final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, V> factory) {
        return new ByteBufCodec<>() {
            public V decode(ByteBuf byteBuf) {
                T1 t1 = byteBufCodec1.decode(byteBuf);
                T2 t2 = byteBufCodec2.decode(byteBuf);
                T3 t3 = byteBufCodec3.decode(byteBuf);
                T4 t4 = byteBufCodec4.decode(byteBuf);
                T5 t5 = byteBufCodec5.decode(byteBuf);
                T6 t6 = byteBufCodec6.decode(byteBuf);
                T7 t7 = byteBufCodec7.decode(byteBuf);
                T8 t8 = byteBufCodec8.decode(byteBuf);
                T9 t9 = byteBufCodec9.decode(byteBuf);
                T10 t10 = byteBufCodec10.decode(byteBuf);
                T11 t11 = byteBufCodec11.decode(byteBuf);
                T12 t12 = byteBufCodec12.decode(byteBuf);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
            }

            public void encode(ByteBuf byteBuf, V v) {
                byteBufCodec1.encode(byteBuf, supplier1.apply(v));
                byteBufCodec2.encode(byteBuf, supplier2.apply(v));
                byteBufCodec3.encode(byteBuf, supplier3.apply(v));
                byteBufCodec4.encode(byteBuf, supplier4.apply(v));
                byteBufCodec5.encode(byteBuf, supplier5.apply(v));
                byteBufCodec6.encode(byteBuf, supplier6.apply(v));
                byteBufCodec7.encode(byteBuf, supplier7.apply(v));
                byteBufCodec8.encode(byteBuf, supplier8.apply(v));
                byteBufCodec9.encode(byteBuf, supplier9.apply(v));
                byteBufCodec10.encode(byteBuf, supplier10.apply(v));
                byteBufCodec11.encode(byteBuf, supplier11.apply(v));
                byteBufCodec12.encode(byteBuf, supplier12.apply(v));
            }
        };
    }

    void encode(ByteBuf byteBuf, V value);

    V decode(ByteBuf byteBuf);
}
