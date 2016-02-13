package com.earth2me.mcperf;

import com.earth2me.mcperf.config.ConfigSetting;
import com.earth2me.mcperf.ob.ContainsConfig;
import com.google.common.collect.Iterables;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ContainsConfig
public final class EntityManager extends Manager {
    @Getter
    @Setter
    @ConfigSetting
    private boolean chunkLoadScanningEnabled = false;
    @Getter
    @Setter
    @ConfigSetting
    private int nearbyChunkRadius = 1;
    @Getter
    @Setter
    @ConfigSetting
    private int nearbyItemLimit = 250;
    @Getter
    @Setter
    @ConfigSetting
    private int nearbyCreatureLimit = 400;
    @Getter
    @Setter
    @ConfigSetting
    private int worldItemLimit = 1000;
    @Getter
    @Setter
    @ConfigSetting
    private int worldCreatureLimit = 2000;

    private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

    public EntityManager(String id, Server server, Logger logger, MCPerfPlugin plugin) {
        super(id, server, logger, plugin);
    }

    private static boolean isIgnoredEntityType(EntityType entityType) {
        // Much faster than testing the contents of a list/set.
        switch (entityType) {
            case PLAYER:
            case ITEM_FRAME:
            case PAINTING:
            case WEATHER:
                return true;

            default:
                return false;
        }
    }

    private static boolean isUnignoredEntity(Entity entity) {
        return !isIgnoredEntityType(entity.getType());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!canSpawn(event.getLocation(), getNearbyCreatureLimit(), getWorldCreatureLimit())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!canSpawn(event.getLocation(), getNearbyItemLimit(), getWorldItemLimit())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Location location = new Location(chunk.getWorld(), chunk.getX() << 4, 0, chunk.getZ() << 4);

        // These will start cleanups if they fail.
        canSpawn(location, getNearbyCreatureLimit(), getWorldCreatureLimit());
        canSpawn(location, getNearbyItemLimit(), getWorldItemLimit());
    }

    private void cleanupWorld(Location location, int limit) {
        List<Entity> entities = getWorldEntities(location);

        World world = location.getWorld();
        int count = entities.size();
        if (count < limit) {
            getLogger().warning(String.format("Discrepancy: calculated that there were too many entities for world [%s], but, upon cleanup, there were only %d (<= %d).", world.getName(), count, limit));
            return;
        }

        getLogger().warning(String.format("Too many entities (%d > %d) in world [%s]; running cleanup", count, limit, world.getName()));
        cleanup(entities, limit);
    }

    private List<Entity> getWorldEntities(Location location) {
        return getWorldEntities(location.getWorld());
    }

    private List<Entity> getWorldEntities(World world) {
        return world.getEntities();
    }

    private Iterable<Entity> getNearbyEntities(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        int radius = getNearbyChunkRadius();

        Iterable<Entity> entities = Collections.emptyList();

        Chunk[] loadedChunks = world.getLoadedChunks();
        Chunk[] nearbyChunks = Arrays.stream(loadedChunks).filter(c ->
                c.getX() >= x - radius && c.getX() <= x + radius &&
                        c.getZ() >= z - radius && c.getZ() <= z + radius
        ).toArray(Chunk[]::new);

        for (Chunk chunk : nearbyChunks) {
            entities = Iterables.concat(entities, Arrays.asList(chunk.getEntities()));
        }

        return entities;
    }

    private void cleanupNearby(Location location, int limit) {
        Iterable<Entity> entities = getNearbyEntities(location);

        World world = location.getWorld();
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        getLogger().warning(String.format("Too many entities at (%s, %d, %d); running cleanup", world.getName(), x << 4, z << 4));

        cleanup(Iterables.unmodifiableIterable(entities), limit);
    }

    private static Stream<Entity> filteredEntityStream(Iterable<Entity> entities) {
        return StreamSupport.stream(entities.spliterator(), true)
                .filter(EntityManager::isUnignoredEntity);
    }

    private void cleanup(Iterable<Entity> entities, int limit) {
        getServer().getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            try {
                List<EntityType> problemTypes = getProblematicEntityTypes(filteredEntityStream(entities), limit);

                if (problemTypes.isEmpty()) {
                    getLogger().warning("Assertion failed; couldn't find any entities after grouping and sorting");
                    // Shouldn't typically happen
                    return;
                }

                getLogger().warning(String.format("Top problem entity type: %s", problemTypes.get(0).name()));

                if (problemTypes.get(0) == EntityType.PLAYER) {
                    // If players are the primary cause, we shouldn't start deleting everything.
                    return;
                }
                problemTypes.remove(EntityType.PLAYER);

                // Evaluate immediately
                final List<Entity> toRemove = new ArrayList<>(Arrays.asList(filteredEntityStream(entities)
                        .filter(entity -> problemTypes.contains(entity.getType()))
                        .toArray(Entity[]::new)));

                if (toRemove.size() >= 10) {
                    for (int i = 0; i < 5; i++) {
                        toRemove.remove(toRemove.size() - 1);
                    }
                }

                getServer().getScheduler().callSyncMethod(getPlugin(), () -> {
                    toRemove.forEach(Entity::remove);
                    return null;
                });
            } finally {
                // Run at most every 2 seconds.
                // Synchronous is probably faster here.
                getServer().getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> cleanupRunning.set(false), 40);
            }
        });
    }

    private static int getPriority(EntityType entityType) {
        // The algorithm will delete entity types with lower priority values first.
        // It's best to avoid adding values here; it can really mess things up.
        switch (entityType) {
            default:
                return 0;
        }
    }

    // Warning: This will include players!
    private List<EntityType> getProblematicEntityTypes(Stream<Entity> entities, int limit) {
        Map<EntityType, Long> counts = entities.collect(Collectors.groupingBy(Entity::getType, Collectors.counting()));
        long total = counts.values().stream().collect(Collectors.summingLong(i -> i));

        Stream<Map.Entry<EntityType, Long>> sorted = counts.entrySet().stream().sorted((a, b) -> {
            int priorityA = getPriority(a.getKey());
            int priorityB = getPriority(b.getKey());

            // Sort by priority ascending, then by count descending
            return priorityA == priorityB ? Long.compare(b.getValue(), a.getValue()) : Integer.compare(priorityA, priorityB);
        });  // Reverse sort

        return sorted.filter(new Predicate<Map.Entry<EntityType, Long>>() {
            private int removing = 0;
            private boolean first = true;

            @Override
            public boolean test(Map.Entry<EntityType, Long> entry) {
                if (!first && total - removing <= limit) {
                    return false;
                }

                removing += entry.getValue();
                first = false;
                return true;
            }
        }).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean canSpawn(final Location location, int nearbyLimit, int worldLimit) {
        // We want nearby cleanup to take priority, so check locally first.
        int nearbyEntities = countNearbyEntities(location, nearbyLimit);
        if (nearbyEntities >= nearbyLimit) {
            if (!cleanupRunning.getAndSet(true)) {
                cleanupNearby(location, nearbyLimit);
            }
            return false;
        }

        int worldEntities = countWorldEntities(location, worldLimit);
        if (worldEntities >= worldLimit) {
            if (!cleanupRunning.getAndSet(true)) {
                cleanupWorld(location, worldLimit);
            }
            return false;
        }

        return true;
    }

    private int countEntities(Iterable<Entity> entities, int limit) {
        // Attempt quick count when entities is of type List<T> or similar
        if (entities instanceof Collection) {
            int count = ((Collection<Entity>) entities).size();

            if (count < limit) {
                return count;
            }
        }

        return (int) filteredEntityStream(entities).count();
    }

    private int countWorldEntities(Location location, int limit) {
        return countEntities(getWorldEntities(location), limit);
    }

    private int countNearbyEntities(Location location, int limit) {
        return countEntities(getNearbyEntities(location), limit);
    }
}
