package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.interfaces.Register;

import java.util.HashSet;
import java.util.Set;

public class RegistrationManager {
    private static final String[] CLIENT_REGISTRIES = new String[]{
            "indi.etern.musichud.client.config.Keybinds",
            "indi.etern.musichud.client.services.LoginService$RegisterImpl",
            "indi.etern.musichud.client.services.MusicService$RegisterImpl"
    };

    private static final String[] SERVER_REGISTRIES = new String[]{
            "indi.etern.musichud.server.api.ILoginApiService$Register",
            "indi.etern.musichud.server.api.MusicPlayerServerService$Register",
            "indi.etern.musichud.server.api.ApiServerManager",
    };

    private static final String[] COMMON_REGISTRIES = new String[]{
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetPlaylistDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetPlaylistDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserPlaylistRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserPlaylistResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserAlbumsRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserAlbumsResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserArtistsRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetUserArtistsResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.SearchRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.SearchAlbumsResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.SearchArtistsResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.SearchMusicResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.SearchPlaylistsResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.StartQRLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.StartQRLoginResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.ConnectRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.ConnectResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.CancelQRLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.AnonymousLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.CookieLoginRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetAlbumDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetAlbumDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistDetailRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistDetailResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistMoreMusicRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistMoreMusicResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceRequest$RegisterImpl",
            "indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceResponse$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.s2c.RefreshMusicQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.s2c.SwitchMusicMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.s2c.LoginResultMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.s2c.SyncCurrentPlayingMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.s2c.UpdateAllIdlePlaySourcesMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.AddToIdlePlaySourceMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.RemoveFromIdlePlaySourceMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.ClientPushMusicToQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.ClientRemoveMusicFromQueueMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.LogoutMessage$RegisterImpl",
            "indi.etern.musichud.network.payloads.pushMessages.c2s.VoteSkipCurrentMusicMessage$RegisterImpl"
    };

    private static final Set<Class<?>> registeredSet = new HashSet<>();

    public static void performAutoRegistration() {
        Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
        MusicHud.LOGGER.info("Starting explicit auto-registration in environment: {}", side.name());

        // 根据环境注册特定接口
        if (side == Environment.Side.CLIENT) {
            registerClassesFromList(CLIENT_REGISTRIES, "client");
            if (ClientConfig.getInstance().getEnableEmbeddedServer()) {
                registerClassesFromList(SERVER_REGISTRIES, "server");
            }
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

