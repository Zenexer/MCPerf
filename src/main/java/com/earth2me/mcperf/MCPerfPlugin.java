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
	private ValidityManager validityManager;

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

		validityManager.setMaxLoreLineLength(config.getInt("validityManager.maxLoreLineLength", validityManager.getMaxLoreLineLength()));
		validityManager.setMaxLoreLines(config.getInt("validityManager.maxLoreLines", validityManager.getMaxLoreLines()));
		validityManager.setMaxNameLength(config.getInt("validityManager.maxNameLength", validityManager.getMaxNameLength()));
		validityManager.setFullUnicodeAllowed(config.getBoolean("validityManager.fullUnicodeAllowed", validityManager.isFullUnicodeAllowed()));
	}

	@Override
	public void onEnable()
	{
		entityManager = new EntityManager(getServer());
		validityManager = new ValidityManager(getServer(), getLogger());

		// Listeners must already be instantiated.
		loadConfiguration();

		PluginManager pluginManager = getServer().getPluginManager();
		pluginManager.registerEvents(entityManager, this);
		pluginManager.registerEvents(validityManager, this);

		super.onEnable();
	}

	@Override
	public void onDisable()
	{
		entityManager = null;
		validityManager = null;

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
