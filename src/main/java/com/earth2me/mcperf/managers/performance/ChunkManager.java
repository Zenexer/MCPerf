package com.earth2me.mcperf.managers.performance;

import com.earth2me.mcperf.Tuple;
import com.earth2me.mcperf.annotation.ContainsConfig;
import com.earth2me.mcperf.annotation.Service;
import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.managers.Manager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Stream;

@Service
@ContainsConfig
public class ChunkManager extends Manager {
    @Getter
    @Setter
    @ConfigSetting
    private boolean debugEnabled = false;
    @Getter
    @Setter
    @ConfigSetting
    private int forceUnloadRadius = 8;
    @Getter
    @Setter
    @ConfigSetting
    private int forceUnloadInterval = 300;
    @Getter
    @Setter
    @ConfigSetting
    private boolean forceUnloadThresholdEnabled = true;
    @Getter
    @Setter
    @ConfigSetting
    private int syncTimeout = 5_000;  // ms

    private BukkitTask forceUnloadTask = null;
    private BukkitScheduler scheduler;

    public ChunkManager() {
        super("Znw4Y2h1bmsz");
    }

    @Override
    protected void onInit() {
        if (forceUnloadTask != null) {
            forceUnloadTask.cancel();
        }

        scheduler = getServer().getScheduler();
        forceUnloadTask = scheduler.runTaskTimerAsynchronously(
                getPlugin(),
                this::forceUnloadAsync,
                getForceUnloadInterval(),
                getForceUnloadInterval()
        );

        if (scheduler.isCurrentlyRunning(forceUnloadTask.getTaskId())) {
            getLogger().info(String.format("[%s] Running chunk cleanup every %d ticks", getId(), getForceUnloadInterval()));
        } else {
            getLogger().severe(String.format("[%s] Failed to schedule chunk cleanup every %d ticks", getId(), getForceUnloadInterval()));
        }

        super.onInit();
    }

    @Override
    protected void onDeinit() {
        if (forceUnloadTask != null) {
            forceUnloadTask.cancel();
            forceUnloadTask = null;
        }

        super.onDeinit();
    }

    private <T> T sync(Callable<T> method) throws InterruptedException, ExecutionException, TimeoutException {
        return scheduler.callSyncMethod(getPlugin(), method).get(syncTimeout, TimeUnit.MILLISECONDS);
    }

    private void forceUnloadAsync() {
        if (debugEnabled) {
            getLogger().info(String.format("[%s] Running chunk cleanup", getId()));
        }

        int playerViewSize = forceUnloadRadius * forceUnloadRadius;
        int spawnSize = playerViewSize * 2;
        int forceUnloadPlayerOverlap = 2;
        int radius = forceUnloadRadius;

        try {
            List<World> worlds = sync(() -> new LinkedList<>(getServer().getWorlds()));

            for (World world : worlds) {
                Chunk[] chunks = sync(world::getLoadedChunks);
                List<Player> players = sync(() -> new ArrayList<>(world.getPlayers()));

                if (forceUnloadThresholdEnabled && chunks.length - spawnSize < playerViewSize * players.size() / forceUnloadPlayerOverlap) {
                    if (debugEnabled) {
                        getLogger().info(String.format("[%s] Skipping world %s", getId(), world.getName()));
                    }
                    continue;
                }

                if (debugEnabled) {
                    getLogger().info(String.format("[%s] Running chunk cleanup for %s", getId(), world.getName()));
                }

                @SuppressWarnings("unchecked")
                Tuple<Chunk, Tuple<Integer, Integer>>[] chunkLocs = (Tuple<Chunk, Tuple<Integer, Integer>>[]) sync(() -> Arrays.stream(chunks)
                        .map(c -> new Tuple<>(c, new Tuple<>(c.getX(), c.getZ())))
                        .toArray(Tuple[]::new)
                );

                @SuppressWarnings("unchecked")
                Tuple<Integer, Integer>[] viewers = (Tuple<Integer, Integer>[]) sync(() ->
                        Stream.concat(
                                Stream.of(world.getSpawnLocation()),
                                players.stream()
                                        .map(Entity::getLocation)
                                        .filter(l -> world.equals(l.getWorld()))
                        )
                                .map(l -> new Tuple<>(l.getBlockX() >> 4, l.getBlockZ() >> 4))
                                .toArray(Tuple[]::new)
                );

                List<Chunk> unloadChunks = new LinkedList<>();

                chunk:
                for (Tuple<Chunk, Tuple<Integer, Integer>> chunkLoc : chunkLocs) {
                    Tuple<Integer, Integer> chunkLocation = chunkLoc.b;
                    int chunkX = chunkLocation.a;
                    int chunkZ = chunkLocation.b;

                    for (Tuple<Integer, Integer> viewer : viewers) {
                        int viewerX = viewer.a;
                        int viewerZ = viewer.b;

                        int diffX = Math.abs(chunkX - viewerX);
                        int diffZ = Math.abs(chunkZ - viewerZ);

                        if (diffX <= radius && diffZ <= radius) {
                            break chunk;
                        }
                    }

                    unloadChunks.add(chunkLoc.a);
                }

                if (unloadChunks.isEmpty()) {
                    if (debugEnabled) {
                        getLogger().info(String.format("[%s] Didn't find any chunks that need to be unloaded", getId()));
                    }
                    continue;
                }

                if (debugEnabled) {
                    getLogger().info(String.format("[%s] Found chunks that need to be unloaded; scheduling for unload", getId()));
                }

                scheduler.scheduleSyncDelayedTask(getPlugin(), () -> {
                    getLogger().info(String.format("[%s] Forcefully unloading %d chunks from %s", getId(), unloadChunks.size(), world.getName()));

                    for (Chunk chunk : unloadChunks) {
                        if (!chunk.isLoaded()) {
                            continue;
                        }

                        if (debugEnabled) {
                            getLogger().info(String.format("[%s] Unloading chunk from %s at (%d, %d)", getId(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
                        }

                        if (!chunk.unload(true, false)) {
                            getLogger().info(String.format("[%s] Failed to unload chunk from %s at (%d, %d)", getId(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
                        }
                    }
                });
            }
        } catch (CancellationException e) {
            if (debugEnabled) {
                getLogger().info(String.format("[%s] Chunk cleanup canceled", getId()));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            getLogger().log(Level.WARNING, String.format("[%s] Async exception: %s", getId(), e.getMessage()));
        }

        if (debugEnabled) {
            getLogger().info(String.format("[%s] Chunk cleanup finished", getId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!isEnabled() || !isDebugEnabled() || !event.isCancelled()) {
            return;
        }

        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (listener.getListener() == this) {
                continue;
            }

            ChunkUnloadEvent e = new ChunkUnloadEvent(event.getChunk());

            try {
                listener.callEvent(e);
            } catch (EventException ex) {
                getLogger().log(Level.SEVERE, "Error invoking ChunkUnloadEvent listener", ex);
                continue;
            }

            if (e.isCancelled()) {
                getLogger().log(Level.WARNING, String.format(
                        "Plugin %s canceled ChunkUnloadEvent from %s",
                        listener.getPlugin().getDescription().getName(),
                        listener.getClass().getCanonicalName()
                ));
                break;
            }
        }
    }
}
