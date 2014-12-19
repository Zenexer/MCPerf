package com.earth2me.mcperf;

import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;

import java.util.logging.Logger;

@RequiredArgsConstructor
public class MonitorManager implements Listener
{
	private final Server server;
	private final Logger logger;

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChat(PlayerChatEvent event)
	{
		String world = event.getPlayer().getWorld().getName();
		String sender = event.getPlayer().getName();
		String displayName = event.getPlayer().getDisplayName();
		String message = event.getMessage();

		server.getConsoleSender().sendMessage(String.format("[CHAT:%s:%s] %s: %s", world, sender, displayName, message));
	}

	// Method kept for compatibility

	/**
	 * Async chat event; unfortunately it can be intercepted.
	 *
	 * @param event AsyncPlayerChatEvent event details
	 * @deprecated
	 */
	@Deprecated
	public void onAsyncPlayerChat(AsyncPlayerChatEvent event)
	{
		String world = event.getPlayer().getWorld().getName();
		String sender = event.getPlayer().getName();
		String displayName = event.getPlayer().getDisplayName();
		String message = event.getMessage();

		server.getConsoleSender().sendMessage(String.format("[CHAT:%s:%s] %s: %s", world, sender, displayName, message));
	}
}
