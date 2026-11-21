package indi.etern.musichud.beans.login;

import indi.etern.musichud.beans.user.ProfileConfigData;

public record ClientUserData(LoginCookieInfo cookie, ProfileConfigData account) {
    public boolean isEmpty() {
        return cookie == null && account == null;
    }
}
