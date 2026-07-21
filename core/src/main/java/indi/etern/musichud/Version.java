package indi.etern.musichud;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.NonNull;

public record Version(long mayor, long minor, long patch, BuildType build) implements Comparable<Version>{
    public static final ByteBufCodec<Version> PACKET_CODEC = ByteBufCodec.composite(
            Codecs.LONG_ARRAY, Version::toLongArray,
            Version::ofLongArray
    );
    public static final Version current = new Version(1,2,15, BuildType.Stable);
    public static final Version leastCapable = new Version(1,2,2,BuildType.Stable);

    private Long[] toLongArray() {
        return new Long[]{mayor, minor, patch, (long) build.ordinal()};
    }

    private static Version ofLongArray(Long[] longs) {
        return new Version(longs[0], longs[1], longs[2], BuildType.ofOrdinal(longs[3].intValue()));
    }

    public enum BuildType {
        Alpha("alpha"), Beta("beta"), PreRelease("pre-release"), Stable("stable");
        final String name;
        BuildType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static BuildType ofOrdinal(int o) {
            switch (o) {
                case 0 -> {
                    return Alpha;
                }
                case 1 -> {
                    return Beta;
                }
                case 2 -> {
                    return PreRelease;
                }
                case 3 -> {
                    return Stable;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    @Override
    public @NonNull String toString() {
        return mayor + "." + minor + "." + patch + "-" + build;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version(
                long mayor1, long minor1, long patch1, BuildType build1
        ) && mayor1 == mayor && minor1 == minor && patch1 == patch && build1 == build;
    }

    @Override
    public int compareTo(@NonNull Version o) {
        if (equals(o)) {
            return 0;
        } else {
            if (mayor > o.mayor) {
                return 4;
            } else if (mayor == o.mayor){
                if (minor > o.minor) {
                    return 3;
                } else if (minor == o.minor){
                    if (patch > o.patch) {
                        return 2;
                    } else if (patch == o.patch){
                        if (build.ordinal() > o.build.ordinal()) {
                            return 1;
                        } else {
                            return -1;
                        }
                    } else {
                        return -2;
                    }
                } else {
                    return -3;
                }
            } else {
                return -4;
            }
        }
    }

    public static boolean compatibleWith(Version v) {
        int i = leastCapable.compareTo(v);
        return i <= 0;
    }
}