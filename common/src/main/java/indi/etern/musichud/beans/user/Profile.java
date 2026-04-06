package indi.etern.musichud.beans.user;

import lombok.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

@Getter
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Profile {
    public static final StreamCodec<RegistryFriendlyByteBuf, Profile> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    Profile::getNickname,
                    ByteBufCodecs.STRING_UTF8,
                    Profile::getAvatarUrl,
                    ByteBufCodecs.LONG,
                    Profile::getUserId,
                    Profile::new
            );
    public static final Profile ANONYMOUS = new Profile("anonymous", "", 0, VipType.NORMAL);
    public static final Profile PRIVATE_MASK = new Profile("private_mask", "", 0, VipType.NORMAL);
    @Getter
    @Setter
    private static volatile Profile current;
    String nickname;
    String avatarUrl = "";
    long userId;
    @Setter
    VipType vipType;

    public Profile(String nickname, String avatarUrl, Long userId) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.userId = userId;
        vipType = VipType.NORMAL;
    }

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