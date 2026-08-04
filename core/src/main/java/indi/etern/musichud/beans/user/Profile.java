package indi.etern.musichud.beans.user;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import lombok.*;

import java.util.Objects;

@Getter
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Profile {
    public static final ByteBufCodec<Profile> CODEC =
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8, Profile::getNickname,
                    Codecs.STRING_UTF8, Profile::getAvatarUrl,
                    Codecs.LONG, Profile::getUserId,
                    Codecs.ofEnum(VipType.class), Profile::getVipType,
                    Profile::new
            );
    public static final Profile ANONYMOUS = new Profile("anonymous", "", -1, VipType.NORMAL);
    public static final Profile PRIVATE_MASK = new Profile("private_mask", "", 0, VipType.NORMAL);
    @Getter
    @Setter
    private static volatile Profile current;
    String nickname;
    String avatarUrl = "";
    long userId;
    @Setter
    VipType vipType;

    public String getNickname() {
        return Objects.requireNonNullElse(nickname, "");
    }
    public String getAvatarUrl() {
        return Objects.requireNonNullElse(avatarUrl, "");
    }
    public VipType getVipType() {
        return Objects.requireNonNullElse(vipType, VipType.NORMAL);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Profile profile && profile.userId == userId;
    }
}