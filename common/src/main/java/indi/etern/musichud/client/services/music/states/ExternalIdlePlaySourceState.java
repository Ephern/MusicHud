package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.PusherInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExternalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    @Override
    public synchronized void updateAll(List<IdlePlaySource> playSources) {
        Set<IdlePlaySource> toRemove = new HashSet<>();
        Set<IdlePlaySource> toAdd = new HashSet<>();
        Set<IdlePlaySource> serverIdlePlaySources = Set.copyOf(sources);
        for (IdlePlaySource idlePlaySource : serverIdlePlaySources) {
            boolean stillOnServer = playSources.stream().anyMatch(s ->
                    s.equals(idlePlaySource) && s.getPusherInfo().equals(idlePlaySource.getPusherInfo()));
            if (!stillOnServer) {
                toRemove.add(idlePlaySource);
            }
        }
        Player player = Minecraft.getInstance().player;
        for (IdlePlaySource idlePlaySource : playSources) {
            boolean known = serverIdlePlaySources.stream().anyMatch(s ->
                    s.equals(idlePlaySource) && s.getPusherInfo().equals(idlePlaySource.getPusherInfo()));
            if (!known && !isLocalOrOwnSource(idlePlaySource.getPusherInfo(), player)) {
                toAdd.add(idlePlaySource);
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
