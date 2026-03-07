package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import net.fabricmc.api.EnvType;

import java.util.HashSet;
import java.util.Set;

public class RegistrationManager {
    private static final String[] CLIENT_REGISTRIES = new String[]{
            "indi.etern.musichud.client.config.Keybinds",
            "indi.etern.musichud.client.services.LoginService$RegisterImpl",
            "indi.etern.musichud.client.services.MusicService$RegisterImpl"
    };

    private static final String[] SERVER_REGISTRIES = new String[]{
            "indi.etern.musichud.server.api.LoginApiService$Register",
            "indi.etern.musichud.server.api.MusicPlayerServerService$Register"
    };

    private static final String[] COMMON_REGISTRIES = new String[]{
            "indi.etern.musichud.network.requestResponseCycle.GetPlaylistDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetPlaylistDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetUserPlaylistRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetUserPlaylistResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.SearchRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.SearchAlbumsResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.SearchArtistsResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.SearchMusicResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.SearchPlaylistsResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.StartQRLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.StartQRLoginResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.ConnectRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.ConnectResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.CancelQRLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.AnonymousLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.CookieLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetAlbumDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetAlbumDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetArtistDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetArtistDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetArtistMoreMusicRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetArtistMoreMusicResponse$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetMusicResourceRequest$RegisterImpl",
            "indi.etern.musichud.network.requestResponseCycle.GetMusicResourceResponse$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.s2c.RefreshMusicQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.s2c.SwitchMusicMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.s2c.LoginResultMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.s2c.SyncCurrentPlayingMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.AddToIdlePlaySourceMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.RemoveFromIdlePlaySourceMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.ClientPushMusicToQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.ClientRemoveMusicFromQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.LogoutMessage$RegisterImpl",
            "indi.etern.musichud.network.pushMessages.c2s.VoteSkipCurrentMusicMessage$RegisterImpl"
    };

    private static final Set<Class<?>> registeredSet = new HashSet<>();

    public static void performAutoRegistration(EnvType envType) {
        MusicHud.LOGGER.info("Starting explicit auto-registration in environment: {}", envType);

        // 根据环境注册特定接口
        if (envType == EnvType.CLIENT) {
            registerClassesFromList(CLIENT_REGISTRIES, "client");
        } else {
            registerClassesFromList(SERVER_REGISTRIES, "server");
        }
        registerClassesFromList(COMMON_REGISTRIES, "common");
    }

    private static void registerClassesFromList(String[] classNames, String typeName) {
        MusicHud.LOGGER.info("Registering {} {} registries", classNames.length, typeName);
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (registeredSet.contains(clazz)) continue;
                if (Register.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Register> regClass = (Class<? extends Register>) clazz;
                    if (!regClass.isInterface()) {
                        Register instance = regClass.getDeclaredConstructor().newInstance();
                        instance.register();
                        registeredSet.add(clazz);
                        MusicHud.LOGGER.debug("Successfully registered: {}", clazz.getCanonicalName());
                    }
                } else {
                    MusicHud.LOGGER.warn("Class {} does not implement Register, skipping", className);
                }
            } catch (Throwable e) {
                MusicHud.LOGGER.error("Failed to register: {}", className, e);
            }
        }
    }
}

