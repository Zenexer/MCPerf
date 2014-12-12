package com.earth2me.mcperf;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MCPerfPlugin extends JavaPlugin
{
	private EntityManager entityManager;

	private void ensureConfig()
	{
		try
		{
			if (!new File("config.yml").exists())
			{
				saveDefaultConfig();
			}
		}
		catch (Exception ex)
		{
			getLogger().warning("Couldn't save default configuration: " + ex.getMessage());
		}
	}

	private void loadConfiguration()
	{
		ensureConfig();

		FileConfiguration config = getConfig();

		entityManager.setNearbyChunkRadius(config.getInt("entityManager.nearbyChunkRadius", entityManager.getNearbyChunkRadius()));
		entityManager.setNearbyCreatureLimit(config.getInt("entityManager.nearbyCreatureLimit", entityManager.getNearbyCreatureLimit()));
		entityManager.setNearbyItemLimit(config.getInt("entityManager.nearbyItemLimit", entityManager.getNearbyItemLimit()));
		entityManager.setWorldCreatureLimit(config.getInt("entityManager.worldCreatureLimit", entityManager.getWorldCreatureLimit()));
		entityManager.setWorldItemLimit(config.getInt("entityManager.worldItemLimit", entityManager.getWorldItemLimit()));
	}

	@Override
	public void onEnable()
	{
		entityManager = new EntityManager(getServer());

		// Listeners must already be instantiated.
		loadConfiguration();

		PluginManager pluginManager = getServer().getPluginManager();
		pluginManager.registerEvents(entityManager, this);

		super.onEnable();
	}

	@Override
	public void onDisable()
	{
		entityManager = null;

		super.onDisable();
	}

	public void reload()
	{
		reloadConfig();
		loadConfiguration();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if (!sender.hasPermission("mcperf.reload") && !sender.isOp())
		{
			return false;
		}

		reload();
		sender.sendMessage("MCPerf reloaded");
		return true;
	}
}
