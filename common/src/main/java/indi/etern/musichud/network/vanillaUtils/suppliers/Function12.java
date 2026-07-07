package indi.etern.musichud.network.vanillaUtils.suppliers;

import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> {
    R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8, T9 t9, T10 t10, T11 t11, T12 t12);

    default Function<T1, com.mojang.datafixers.util.Function11<T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R>> curry() {
        return t1 -> (t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default BiFunction<T1, T2, Function10<T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R>> curry2() {
        return (t1, t2) -> (t3, t4, t5, t6, t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default com.mojang.datafixers.util.Function3<T1, T2, T3, com.mojang.datafixers.util.Function9<T4, T5, T6, T7, T8, T9, T10, T11, T12, R>> curry3() {
        return (t1, t2, t3) -> (t4, t5, t6, t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default com.mojang.datafixers.util.Function4<T1, T2, T3, T4, com.mojang.datafixers.util.Function8<T5, T6, T7, T8, T9, T10, T11, T12, R>> curry4() {
        return (t1, t2, t3, t4) -> (t5, t6, t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default com.mojang.datafixers.util.Function5<T1, T2, T3, T4, T5, com.mojang.datafixers.util.Function7<T6, T7, T8, T9, T10, T11, T12, R>> curry5() {
        return (t1, t2, t3, t4, t5) -> (t6, t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function6<T1, T2, T3, T4, T5, T6, Function6<T7, T8, T9, T10, T11, T12, R>> curry6() {
        return (t1, t2, t3, t4, t5, t6) -> (t7, t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function7<T1, T2, T3, T4, T5, T6, T7, Function5<T8, T9, T10, T11, T12, R>> curry7() {
        return (t1, t2, t3, t4, t5, t6, t7) -> (t8, t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function8<T1, T2, T3, T4, T5, T6, T7, T8, Function4<T9, T10, T11, T12, R>> curry8() {
        return (t1, t2, t3, t4, t5, t6, t7, t8) -> (t9, t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, Function3<T10, T11, T12, R>> curry9() {
        return (t1, t2, t3, t4, t5, t6, t7, t8, t9) -> (t10, t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, BiFunction<T11, T12, R>> curry10() {
        return (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10) -> (t11, t12) -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }

    default Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Function<T12, R>> curry11() {
        return (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11) -> t12 -> apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
    }
}
