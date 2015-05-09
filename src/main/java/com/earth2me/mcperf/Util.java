package com.earth2me.mcperf;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;


public class Util
{
	private Util()
	{
		throw new UnsupportedOperationException("Static class");
	}

	public static boolean denyPermission(CommandSender sender)
	{
		sender.sendMessage(ChatColor.RED + "Permission denied.");
		return true;
	}
}