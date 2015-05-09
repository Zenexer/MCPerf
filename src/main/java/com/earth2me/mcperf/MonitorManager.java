package com.earth2me.mcperf;

import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class MonitorManager implements Listener
{
	private static final int MAX_DISPLAY_RECIPIENTS = 6;

	private final Server server;
	private final Logger logger;

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerChat(@SuppressWarnings("deprecation") PlayerChatEvent event)
	{
		String world = event.getPlayer().getWorld().getName();
		String sender = event.getPlayer().getName();
		String displayName = event.getPlayer().getDisplayName();
		String message = event.getMessage();
		Set<Player> recipients = event.getRecipients();
		int recipCount = recipients.size();
		int totalCount = server.getOnlinePlayers().size();

		String format;
		if (recipCount < totalCount)
		{
			format = "<<LIMITED-CHAT=%d/%d:%s:%s>> %s: %s";
		}
		else
		{
			format = "[CHAT=%d/%d:%s:%s] %s: %s";
		}

		List<String> lines = new ArrayList<>(2);

		lines.add(String.format(format, recipCount, totalCount, world, sender, displayName, message));
		if (recipCount < totalCount && recipCount <= MAX_DISPLAY_RECIPIENTS)
		{
			lines.add(Joiner.on(", ").join(recipients));
		}

		server.getConsoleSender().sendMessage(lines.toArray(new String[lines.size()]));
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
