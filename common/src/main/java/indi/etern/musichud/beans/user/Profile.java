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
                    ByteBufCodecs.STRING_UTF8,
                    Profile::getBackgroundUrl,
                    ByteBufCodecs.LONG,
                    Profile::getUserId,
                    Profile::new
            );
    public static final Profile ANONYMOUS = new Profile("anonymous", "", "", 0);
    @Getter
    @Setter
    private static volatile Profile current;
    String nickname;
    String avatarUrl = "";
    String backgroundUrl = "";
    long userId;

    public String getNickname() {
        return Objects.requireNonNullElse(nickname, "");
    }
    public String getAvatarUrl() {
        return Objects.requireNonNullElse(avatarUrl, "");
    }
    private String getBackgroundUrl() {
        return Objects.requireNonNullElse(backgroundUrl, "");
    }
}