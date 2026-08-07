package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExternalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    @Override
    public synchronized void updateAll(List<Playlist> playlistSources, List<Album> albumSources) {
        Set<MusicCollection> toRemove = new HashSet<>();
        Set<MusicCollection> toAdd = new HashSet<>();
        Set<MusicCollection> serverIdlePlaySources = Set.copyOf(sources);
        for (MusicCollection musicCollection : serverIdlePlaySources) {
            //noinspection SuspiciousMethodCalls
            if (!playlistSources.contains(musicCollection) && !albumSources.contains(musicCollection)) {
                toRemove.add(musicCollection);
            }
        }
        Player player = Minecraft.getInstance().player;
        for (MusicCollection musicCollection : playlistSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !isLocalOrOwnSource(musicCollection.getPusherInfo(), player)) {
                toAdd.add(musicCollection);
            }
        }
        for (MusicCollection musicCollection : albumSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !isLocalOrOwnSource(musicCollection.getPusherInfo(), player)) {
                toAdd.add(musicCollection);
            }
        }
        sources.removeAll(toRemove);
        sources.addAll(toAdd);
        toRemove.forEach(musicCollection -> {
            notifyChange(musicCollection);
            notifyRemove(musicCollection);
        });
        toAdd.forEach(musicCollection -> {
            notifyChange(musicCollection);
            notifyAdd(musicCollection);
        });
    }

    private static boolean isLocalOrOwnSource(PusherInfo pusherInfo, Player player) {
        if (pusherInfo == null || pusherInfo == PusherInfo.EMPTY) {
            // Sources without a pusher are local sources; they must not leak into the
            // external (server) layer, otherwise they would show up twice.
            return true;
        }
        return player != null && pusherInfo.getPlayerUUID().equals(player.getUUID());
    }

    @Override
    public void reset() {
        sources.clear();
    }
}
