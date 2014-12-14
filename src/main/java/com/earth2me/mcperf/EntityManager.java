package com.earth2me.mcperf;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

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

	@SuppressWarnings("RedundantIfStatement")
	private boolean canSpawn(Location location, int nearbyLimit, int worldLimit)
	{
		int worldEntities = countWorldEntities(location);
		if (worldEntities >= worldLimit)
		{
			return false;
		}

		int nearbyEntities = countNearbyEntities(location);
		if (nearbyEntities >= nearbyLimit)
		{
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
				Chunk chunk = world.getChunkAt(x + offsetX, z + offsetZ);

				if (chunk == null)
				{
					continue;
				}

				entityCount += chunk.getEntities().length;
			}
		}

		return entityCount;
	}
}
