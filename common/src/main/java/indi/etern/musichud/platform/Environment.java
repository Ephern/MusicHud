package indi.etern.musichud.platform;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Environment {
    public enum Side {
        CLIENT,
        SERVER
    }
    public enum Platform {
        FABRIC,
        NEOFORGE
    }
    private Side side;
    private Platform platform;

    public static Environment of(Side side, Platform platform) {
        return new Environment(side, platform);
    }
}