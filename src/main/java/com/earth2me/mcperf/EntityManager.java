package com.earth2me.mcperf;

import com.google.common.collect.Iterables;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class EntityManager implements Listener
{
	@Getter
	@Setter
	private int nearbyChunkRadius = 1;
	@Getter
	@Setter
	private int nearbyItemLimit = 400;
	@Getter
	@Setter
	private int nearbyCreatureLimit = 200;
	@Getter
	@Setter
	private int worldItemLimit = 3000;
	@Getter
	@Setter
	private int worldCreatureLimit = 2000;

	private final Server server;
	private final Logger logger;
	private final Plugin plugin;
	private final AtomicBoolean cleanupRunning = new AtomicBoolean(false);

	@EventHandler(priority = EventPriority.HIGH)
	public void onCreatureSpawn(CreatureSpawnEvent event)
	{
		if (event.isCancelled())
		{
			return;
		}

		if (!canSpawn(event.getLocation(), getNearbyCreatureLimit(), getWorldCreatureLimit()))
		{
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onItemSpawn(ItemSpawnEvent event)
	{
		if (event.isCancelled())
		{
			return;
		}

		if (!canSpawn(event.getLocation(), getNearbyItemLimit(), getWorldItemLimit()))
		{
			event.setCancelled(true);
		}
	}

	/*@EventHandler(priority = EventPriority.HIGH)
	public void onChunkLoad(ChunkLoadEvent event)
	{
		Chunk chunk = event.getChunk();
		Location location = new Location(chunk.getWorld(), chunk.getX() << 4, 0, chunk.getZ() << 4);

		// These will start cleanups if they fail.
		canSpawn(location, getWorldCreatureLimit(), getNearbyCreatureLimit());
		canSpawn(location, getWorldItemLimit(), getNearbyItemLimit());
	}*/

	private void cleanupWorld(Location location, int limit)
	{
		List<Entity> entityList = location.getWorld().getEntities();
		final Entity[] entities = entityList.toArray(new Entity[entityList.size()]);  // Clone before async

		cleanup(entities, limit);
	}

	private void cleanupNearby(Location location, int limit)
	{
		World world = location.getWorld();
		int x = location.getBlockX() >> 4;
		int z = location.getBlockZ() >> 4;
		int radius = getNearbyChunkRadius();

		Iterable<Entity> entities = Collections.emptyList();

		for (int offsetX = -radius; offsetX <= radius; offsetX++)
		{
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++)
			{
				Chunk chunk = world.getChunkAt(x + offsetX, z + offsetZ);

				if (chunk == null)
				{
					continue;
				}

				entities = Iterables.concat(entities, Arrays.asList(chunk.getEntities()));
			}
		}

		cleanup(Iterables.toArray(entities, Entity.class), limit);
	}

	private void cleanup(Entity[] entities, int limit)
	{
		server.getScheduler().runTaskAsynchronously(plugin, () -> {
			try
			{
				List<EntityType> problemTypes = getProblematicEntityTypes(Arrays.stream(entities), limit);

				if (problemTypes.isEmpty())
				{
					// Shouldn't typically happen
					return;
				}

				if (problemTypes.get(0) == EntityType.PLAYER)
				{
					// If players are the primary cause, we shouldn't start deleting everything.
					return;
				}
				problemTypes.remove(EntityType.PLAYER);

				// Evaluate immediately
				final Entity[] toRemove = Arrays.stream(entities).filter(entity -> problemTypes.contains(entity.getType())).toArray(Entity[]::new);

				server.getScheduler().callSyncMethod(plugin, () -> {
					Arrays.stream(toRemove).forEach(Entity::remove);
					return null;
				});
			}
			finally
			{
				// Run at most every 10 seconds.
				// Synchronous is probably faster here.
				server.getScheduler().scheduleSyncDelayedTask(plugin, () -> cleanupRunning.set(false), 200);
			}
		});
	}

	private static int getPriority(EntityType entityType)
	{
		// The algorithm will delete entity types with lower priority values first.
		// It's best to avoid adding values here, if possible.
		switch (entityType)
		{
			case WEATHER:
				return 1000;

			case ITEM_FRAME:
				return 100;

			case PAINTING:
				return 90;

			default:
				return 0;
		}
	}

	// Warning: This will include players!
	private List<EntityType> getProblematicEntityTypes(Stream<Entity> entities, int limit)
	{
		Map<EntityType, Long> counts = entities.collect(Collectors.groupingBy(Entity::getType, Collectors.counting()));
		long total = counts.values().stream().collect(Collectors.summingLong(i -> i));

		Stream<Map.Entry<EntityType, Long>> sorted = counts.entrySet().stream().sorted((a, b) -> {
			int priorityA = getPriority(a.getKey());
			int priorityB = getPriority(b.getKey());

			// Sort by priority ascending, then by count descending
			return priorityA == priorityB ? Long.compare(b.getValue(), a.getValue()) : Integer.compare(priorityA, priorityB);
		});  // Reverse sort

		return sorted.filter(new Predicate<Map.Entry<EntityType, Long>>()
		{
			private int removing = 0;

			@Override
			public boolean test(Map.Entry<EntityType, Long> entry)
			{
				if (total - removing <= limit)
				{
					return false;
				}

				removing += entry.getValue();
				return true;
			}
		}).map(Map.Entry::getKey).collect(Collectors.toList());
	}

	@SuppressWarnings("RedundantIfStatement")
	private boolean canSpawn(final Location location, int nearbyLimit, int worldLimit)
	{
		// We want nearby cleanup to take priority, so check locally first.
		int nearbyEntities = countNearbyEntities(location);
		if (nearbyEntities >= nearbyLimit)
		{
			if (!cleanupRunning.getAndSet(true))
			{
				cleanupNearby(location, nearbyLimit);
			}
			return false;
		}

		int worldEntities = countWorldEntities(location);
		if (worldEntities >= worldLimit)
		{
			if (!cleanupRunning.getAndSet(true))
			{
				cleanupWorld(location, worldLimit);
			}
			return false;
		}

		return true;
	}

	private int countWorldEntities(Location location)
	{
		return location.getWorld().getEntities().size();
	}

	private int countNearbyEntities(Location location)
	{
		World world = location.getWorld();
		int x = location.getBlockX() >> 4;
		int z = location.getBlockZ() >> 4;
		int radius = getNearbyChunkRadius();

		int entityCount = 0;

		for (int offsetX = -radius; offsetX <= radius; offsetX++)
		{
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++)
			{
				if (!world.isChunkLoaded(x + offsetX, z + offsetZ))
				{
					continue;
				}

				Chunk chunk = world.getChunkAt(x + offsetX, z + offsetZ);

				if (chunk == null || !chunk.isLoaded())
				{
					continue;
				}

				entityCount += chunk.getEntities().length;
			}
		}

		return entityCount;
	}
}
