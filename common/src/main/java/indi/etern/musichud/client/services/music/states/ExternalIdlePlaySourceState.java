package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
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
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
                toAdd.add(musicCollection);
            }
        }
        for (MusicCollection musicCollection : albumSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
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

    @Override
    public void reset() {
        sources.clear();
    }
}
