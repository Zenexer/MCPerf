package com.earth2me.mcperf.validity;

import lombok.Value;
import org.bukkit.configuration.file.FileConfiguration;

@Value
public class ValidityConfiguration
{
	boolean enabled;
	int maxLoreLines;
	int maxLoreLineLength;
	int maxNameLength;
	boolean fullUnicodeAllowed;
	boolean enchantmentCheckingEnabled;
	boolean potionCheckingEnabled;
	boolean splashPotionCheckingEnabled;
	boolean splashPotionsBanned;
	boolean zeroQuantityBanned;

	public ValidityConfiguration(FileConfiguration config)
	{
		enabled                     = config.getBoolean("validityManager.enabled",                     false);
		maxLoreLineLength           = config.getInt    ("validityManager.maxLoreLineLength",           5);
		maxLoreLines                = config.getInt    ("validityManager.maxLoreLines",                127);
		maxNameLength               = config.getInt    ("validityManager.maxNameLength",               63);
		fullUnicodeAllowed          = config.getBoolean("validityManager.fullUnicodeAllowed",          false);
		enchantmentCheckingEnabled  = config.getBoolean("validityManager.enchantmentCheckingEnabled",  false);
		potionCheckingEnabled       = config.getBoolean("validityManager.potionCheckingEnabled",       false);
		splashPotionCheckingEnabled = config.getBoolean("validityManager.splashPotionCheckingEnabled", false);
		splashPotionsBanned         = config.getBoolean("validityManager.splashPotionsBanned",         false);
		zeroQuantityBanned          = config.getBoolean("validityManager.zeroQuantityBanned",          false);
	}
}
